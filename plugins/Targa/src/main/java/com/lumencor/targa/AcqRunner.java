package org.lumencor.targa;

import mmcorej.CMMCore;
import mmcorej.LongVector;
import mmcorej.StorageDataType;
import mmcorej.TaggedImage;
import org.micromanager.data.Image;
import org.micromanager.data.internal.DefaultImage;

import java.util.Set;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArraySet;

public class AcqRunner extends Thread {
	// Readout configuration must match the configuration in Micro-manager
	private static final String READOUT_CONFIG_GROUP = "Readout";
	private static final String READOUT_STANDARD_CONFIG = "Standard";
	private static final String READOUT_FAST_CONFIG = "Fast";
	private static final String TTL_SWITCH_DEV_NAME = "TTLSwitch";
	private static final String TTL_SWITCH_PROP_SEQUENCE = "ChannelSequence";

	private final Set<AcqRunnerListener> listeners_ = new CopyOnWriteArraySet<>();
	private final CMMCore core_;
	private final String location_;
	private final String name_;
	private final Vector<String> channels_;
	private final boolean timeLapse_;
	private final int timePoints_;
	private final int timeInterval_;
	private final Object stateMutex_;
	private boolean active_;
	private int current_;
	private int total_;
	private int buffFree_;
	private int buffTotal_;

	/**
	 * Class constructor
	 * @parma core MMCore interface
	 * @param location Save location
	 * @param name Acquisition / Dataset name
	 * @param timelapse Run time-lapse acquisition
	 * @param timepoints Number of timepoints
	 * @param channels Channels list
	 * @param timeintervalms Time-lapse interval
	 */
	AcqRunner(CMMCore core, String location, String name, boolean timelapse, int timepoints, Vector<String> channels, int timeintervalms) {
		active_ = false;
		core_ = core;
		location_ = location;
		name_ = name;
		timeLapse_ = timelapse;
		timePoints_ = timepoints;
		timeInterval_ = timeintervalms;
		buffFree_ = 0;
		buffTotal_ = 0;
		channels_ = channels;
		stateMutex_ = new Object();
	}

	/**
	 * Thread body
	 */
	@Override
	public void run() {
		active_ = true;
		current_ = 0;
		total_ = (channels_.isEmpty() ? 1 : channels_.size()) * timePoints_;
		if(total_ <= 0 || timeInterval_ < 0 || name_.isEmpty() || location_.isEmpty()) {
			System.out.println("Acquisition cancelled. Invalid parameters");
			return;
		}

		try {
			// Create the dataset
			LongVector shape = new LongVector();
			shape.add(1);
			shape.add(timePoints_);
			shape.add(channels_.isEmpty() ? 1 : channels_.size());
			shape.add((int)core_.getImageWidth());
			shape.add((int)core_.getImageHeight());
			long bpp = core_.getBytesPerPixel();
			StorageDataType pixType = bpp == 2 ? StorageDataType.StorageDataType_GRAY16 : (bpp == 1 ? StorageDataType.StorageDataType_GRAY8 : StorageDataType.StorageDataType_UNKNOWN);
			if(pixType == StorageDataType.StorageDataType_UNKNOWN)
				throw new Exception("Unsupported pixel depth of " + bpp + " bytes.");
			String handle = core_.createDataset(location_, name_, shape, pixType, "");

			// save the initial state
			double exposureMs = core_.getExposure();
			String chGroup = core_.getChannelGroup();
			String currentChannel = chGroup.isEmpty() ? "" : core_.getCurrentConfig(chGroup);
			String readoutMode = core_.getCurrentConfig(READOUT_CONFIG_GROUP);

			// Acquire images
			String error = "";
			try {
				buffTotal_ = core_.getBufferTotalCapacity();
				if(timeLapse_)
					runTimeLapse(handle, pixType);
				else
					runAcquisition(handle, pixType);
			} catch(Exception | Error ex) {
				core_.stopSequenceAcquisition();
				error = ex.getMessage();
			}

			// Close the dataset
			core_.closeDataset(handle);

			// restore the initial state
			if(!currentChannel.isEmpty())
				core_.setConfig(chGroup, currentChannel);
			if(core_.getExposure() != exposureMs)
				core_.setExposure(exposureMs);
			if(!readoutMode.equals(core_.getCurrentConfig(READOUT_CONFIG_GROUP)))
				core_.setConfig(READOUT_CONFIG_GROUP, readoutMode);

			// Notify that the acquisition is complete
			if(error.isEmpty())
				notifyListenersComplete();
			else
				notifyListenersFail(error);
		} catch(Exception | Error e) {
			notifyListenersFail(e.getMessage());
		}
		active_ = false;
	}

	/**
	 * Cancel job
	 */
	public void cancel() {
		synchronized(stateMutex_) {
			if(!active_)
				return;
			active_ = false;
		}
	}

	/**
	 * Run time-lapse sequence acquisition
	 * In this mode we are acquiring one image, waiting for the time interval and then acquiring the next image.
	 * The camera operates in the "single snap" mode.
	 *
	 * @param handle  Dataset UID
	 * @param pixType Pixel data type
	 */
	protected void runTimeLapse(String handle, StorageDataType pixType) throws Exception {
		int numberOfSpecifiedChannels = channels_.size();
		int numberOfChannels = channels_.isEmpty() ? 1 : numberOfSpecifiedChannels;
		if (numberOfSpecifiedChannels == 1) {
			core_.setConfig(core_.getChannelGroup(), channels_.get(0));
			core_.waitForConfig(core_.getChannelGroup(), channels_.get(0));
		}

		// setup appropriate readout mode
		// timelapse requires standard readout mode
		String currentConfig = core_.getCurrentConfig(READOUT_CONFIG_GROUP);
		if(!currentConfig.equals(READOUT_STANDARD_CONFIG))
			core_.setConfig(READOUT_CONFIG_GROUP, READOUT_STANDARD_CONFIG);

      notifyListenersStarted();
		for(int j = 0; j < timePoints_; j++) {
			long tpStart = System.nanoTime();
			for(int k = 0; k < numberOfChannels; k++) {
				// Check cancellation token
				synchronized(stateMutex_) {
					if(!active_)
						break;
				}
				// switch to the next channel if we have more than one
				if(numberOfSpecifiedChannels > 1) {
					core_.setConfig(core_.getChannelGroup(), channels_.get(k));
					core_.waitForConfig(core_.getChannelGroup(), channels_.get(k));
				}

				core_.snapImage();
				buffFree_ = core_.getBufferFreeCapacity();
				TaggedImage img = core_.getTaggedImage();
				processImage(img, handle, pixType, j, k);
			}
			if(!active_)
				break;
			long tpEnd = System.nanoTime();
			int sleepMs = timeInterval_ - (int)((tpEnd - tpStart) / 1000000.0 + core_.getExposure());
			if(sleepMs > 0)
				Thread.sleep(sleepMs);
		}
	}

	/**
	 * Run continuous sequence acquisition
	 *
	 * @param handle  Dataset UID
	 * @param pixType Pixel data type
	 */
	protected void runAcquisition(String handle, StorageDataType pixType) throws Exception {
		int numberOfSpecifiedChannels = channels_.size();
		int numberOfChannels = channels_.isEmpty() ? 1 : numberOfSpecifiedChannels;
		String currentReadout = core_.getCurrentConfig(READOUT_CONFIG_GROUP);

		if (numberOfSpecifiedChannels == 1) {
			// if we have only one channel we will set it before starting acquisition
			core_.setConfig(core_.getChannelGroup(), channels_.get(0));
			core_.waitForConfig(core_.getChannelGroup(), channels_.get(0));
		} else if (numberOfSpecifiedChannels > 1) {
			// if more than one channel we must use standard readout mode...
			if (!currentReadout.equals(READOUT_STANDARD_CONFIG))
				core_.setConfig(READOUT_CONFIG_GROUP, READOUT_STANDARD_CONFIG);

			// ...and set the channel sequence for the TTL switch device
			if (core_.hasProperty(TTL_SWITCH_DEV_NAME, TTL_SWITCH_PROP_SEQUENCE)) {

				// create a string with the sequence of channels
				StringBuilder sequence = new StringBuilder();
				for (int i = 0; i < numberOfSpecifiedChannels; i++) {
					sequence.append(channels_.get(i)).append(" ");
				}

				// set the "Channel Sequence" property
				core_.setProperty(TTL_SWITCH_DEV_NAME, TTL_SWITCH_PROP_SEQUENCE, sequence.toString());
			} else {
				throw new Exception("Device " + TTL_SWITCH_DEV_NAME + " does not support property " + TTL_SWITCH_PROP_SEQUENCE);
			}
		}
		int totalcbuff = core_.getBufferTotalCapacity();

		core_.startSequenceAcquisition(total_, 0.0, true);
		notifyListenersStarted();
		for(int j = 0; j < timePoints_; j++) {
			buffFree_ = core_.getBufferFreeCapacity();
			if(buffFree_ < totalcbuff / 10)
				System.out.printf("\nWARNING!!! Low buffer space %d / %d\n\n", buffFree_, totalcbuff);

			for(int k = 0; k < numberOfChannels; k++) {
				if(core_.isBufferOverflowed()) {
					notifyListenersFail("Buffer overflow!!");
					break;
				}

				// Check cancellation token
				synchronized(stateMutex_) {
					if(!active_)
						break;
				}
				while(core_.getRemainingImageCount() == 0) { }

				// fetch the image
				TaggedImage img = core_.popNextTaggedImage();
				processImage(img, handle, pixType, j, k);
			}
			if(core_.isBufferOverflowed() || !active_)
				break;
		}

		// we are done so stop sequence acquisition
		core_.stopSequenceAcquisition();
	}

	/**
	 * Store image and report progress
	 * @param img Image
	 * @param handle Dataset handle (UID)
	 * @param pixType Pixel type
	 * @param timep Time point (index)
	 * @param chind Channel index
	 * @throws Exception
	 */
	private void processImage(TaggedImage img, String handle, StorageDataType pixType, int timep, int chind) throws Exception {
		// create coordinates for the image
		LongVector coords = new LongVector();
		coords.add(0);
		coords.add(timep);
		coords.add(chind);
		coords.add(0);
		coords.add(0);

		// Add image index to the image metadata
		img.tags.put("Image-index", current_);

		// Add image to stream
		long tsstart = System.nanoTime();
		if(pixType == StorageDataType.StorageDataType_GRAY16) {
			short[] bx = (short[])img.pix;
			core_.addImage(handle, bx.length, bx, coords, img.tags.toString());
		} else if (pixType == StorageDataType.StorageDataType_GRAY8) {
			byte[] bx = (byte[])img.pix;
			core_.addImage(handle, bx.length, bx, coords, img.tags.toString());
		} else {
			throw new Exception("Unsupported pixel depth");
		}
		long tsend = System.nanoTime();
		double storetimems = (tsend - tsstart) / 1000000.0;

		// Update acquisition progress
		current_++;
		notifyListenersStatusUpdate(new DefaultImage(img), storetimems);
	}

	/**
	 * Add event listener
	 * @param listener Listener object
	 */
	public final synchronized void addListener(final AcqRunnerListener listener) {
		listeners_.add(listener);
	}

	/**
	 * Remove event listener
	 * @param listener Listener object
	 */
	public final synchronized void removeListener(final AcqRunnerListener listener) {
		listeners_.remove(listener);
	}

	/**
	 * Notify event listeners that the acquisition has been completed
	 */
	private synchronized void notifyListenersComplete() {
		for(AcqRunnerListener listener : listeners_)
			listener.notifyAcqCompleted();
	}

	/**
	 * Notify event listeners that the acquisition has been started
	 */
	private synchronized void notifyListenersStarted() {
		for(AcqRunnerListener listener : listeners_)
			listener.notifyAcqStarted();
	}

	/**
	 * Notify event listeners of acquisition error
	 * @param msg Error message
	 */
	private synchronized void notifyListenersFail(String msg) {
		for(AcqRunnerListener listener : listeners_)
			listener.notifyAcqFailed(msg);
	}

	/**
	 * Notify event listeners of acquisition status update
	 * @param img Acquired image
	 * @param storetimems Storage write time (in ms)
	 */
	private synchronized void notifyListenersStatusUpdate(Image img, double storetimems) {
		for(AcqRunnerListener listener : listeners_)
			listener.notifyAcqStatusUpdate(current_, total_, img, buffFree_, buffTotal_, storetimems);
	}
}
