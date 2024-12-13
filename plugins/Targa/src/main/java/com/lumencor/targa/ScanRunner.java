package org.lumencor.targa;

import mmcorej.CMMCore;
import mmcorej.LongVector;
import mmcorej.StorageDataType;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.Studio;
import org.micromanager.data.Image;

import java.util.Set;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArraySet;

import static java.lang.System.currentTimeMillis;

/**
 *
 */
public class ScanRunner extends Thread {
	private static final String TTL_SWITCH_DEV_NAME = "TTLSwitch";
	private static final String TTL_SWITCH_PROP_RUN = "RunSequence";

	private final Set<AcqRunnerListener> listeners_ = new CopyOnWriteArraySet<>();
	private final CMMCore core_;
	private final String location_;
	private final String name_;
	private final Vector<ChannelInfo> channels_;
	private final Object stateMutex_;
	private boolean active_;
	private int current_;
	private int total_;
	private int buffFree_;
	private int buffTotal_;
	private int flushCycle_;
	private int chunkSize_;
	private boolean directIo_;
    private double[] zStack_ = {0.0, -0.5, 0.5};
    private Studio studio_;


    public ScanRunner(Studio std, String dataDir, String name, Vector<ChannelInfo> channels, int chunkSize,
                      boolean directio, int flushCycle) {
        super();
		active_ = false;
		core_ = std.getCMMCore();
		location_ = dataDir;
		name_ = name;
		buffFree_ = 0;
		buffTotal_ = 0;
		channels_ = channels;
		chunkSize_ = chunkSize;
		directIo_ = directio;
		flushCycle_ = flushCycle;
		stateMutex_ = new Object();
        studio_ = std;
    }

    /**
	 * Thread body
	 */
	@Override
	public void run() {
		active_ = true;
		current_ = 0;
        PositionList positions = studio_.positions().getPositionList();
		total_ = channels_.size() * zStack_.length * positions.getNumberOfPositions();
		if(total_ <= 0 || name_.isEmpty() || location_.isEmpty()) {
			System.out.println("Acquisition cancelled. Invalid parameters");
			return;
		}

		String storageDriver = core_.getStorageDevice();
		if (storageDriver.isEmpty()) {
			notifyListenersFail("The configuration does not have any storage devices");
			return;
		}

        String handle = "";
        long startT = currentTimeMillis();
		try {
            // set camera configuration
            setSequenceMode(core_.getCameraDevice());

			// Apply selected storage driver configuration
			setStorageConfig(storageDriver, chunkSize_, directIo_, flushCycle_);

			// Create the dataset
			LongVector shape = new LongVector();
			shape.add(positions.getNumberOfPositions());        // positions
            shape.add(zStack_.length);      // z-stack
			shape.add(channels_.size());    // channels
			shape.add((int)core_.getImageWidth());
			shape.add((int)core_.getImageHeight());
			long bpp = core_.getBytesPerPixel();
			StorageDataType pixType = bpp == 2 ? StorageDataType.StorageDataType_GRAY16 : (bpp == 1 ? StorageDataType.StorageDataType_GRAY8 : StorageDataType.StorageDataType_UNKNOWN);
			if(pixType == StorageDataType.StorageDataType_UNKNOWN)
				throw new Exception("Unsupported pixel depth of " + bpp + " bytes.");

			handle = core_.createDataset(location_, name_, shape, pixType, "");

			// Acquire images
            String xyStage = core_.getXYStageDevice();
            String zStage = core_.getFocusDevice();
            String camera = core_.getCameraDevice();

            // program the TTL sequence
            StringBuilder sequenceCmd = new StringBuilder();
            for (ChannelInfo channelInfo : channels_) {
                sequenceCmd.append(channelInfo.name).append(" ");
                core_.setProperty(TTL_SWITCH_DEV_NAME, channelInfo.getIntensityProperty(), channelInfo.intensity);
                core_.setProperty(TTL_SWITCH_DEV_NAME, channelInfo.getExposureProperty(), channelInfo.ttlExposureMs);
            }
            core_.setProperty(TTL_SWITCH_DEV_NAME, "ChannelSequence", sequenceCmd.toString());

            // start the camera
            core_.clearCircularBuffer();
            core_.startContinuousSequenceAcquisition(0);

            // iterate over the positions

            for (int p=0; p<positions.getNumberOfPositions(); p++) {
                MultiStagePosition pos = positions.getPosition(p);
                core_.setXYPosition(pos.getX(), pos.getY());
                for (int z=0; z<zStack_.length; z++) {
                    if (z==0)
                        core_.setPosition(zStage, pos.getZ());
                    else
                        core_.setPosition(zStage, pos.getZ() + zStack_[z]);

                    core_.waitForDevice(zStage);
                    core_.waitForDevice(xyStage);
                    core_.setProperty(TTL_SWITCH_DEV_NAME, TTL_SWITCH_PROP_RUN, "1"); // run images

                    // wait for images to arrive in the circular buffer
                    int retries = 0;
                    int maxRetries = 300;
                    while ((core_.getRemainingImageCount() < channels_.size()) && (retries < maxRetries)) {
                        core_.sleep(1);
                        retries++;
                    }
                    if (retries >= maxRetries)
                        throw new Exception("Timeout waiting for images");

                    // save the images
                    for (int c=0; c<channels_.size(); c++) {
                        // create coordinates for the image
                        LongVector coords = new LongVector();
                        coords.add(p);
                        coords.add(z);
                        coords.add(c);
                        coords.add(0);
                        coords.add(0);
                        core_.saveNextImage(handle, coords, "");
                    } // end of channel loop
                } // end of z-stack loop
            } // end of position loop

		} catch(Exception | Error e) {
			notifyListenersFail(e.getMessage());
            core_.logMessage("Error in ScanRunner: " + e.getMessage());
		}

        // clean up
        try {
            // Close the dataset
            if (!handle.isEmpty())
                core_.closeDataset(handle);

            // restore the initial state
            core_.stopSequenceAcquisition();
            setStandardMode(core_.getCameraDevice());

        } catch (Exception e) {
            notifyListenersFail(e.getMessage());
            core_.logMessage("Error in ScanRunner: " + e.getMessage());
        }
        notifyListenersComplete();
        long totalTimeMs = currentTimeMillis() - startT;
        core_.logMessage(String.format("Acquisition of %d wells, completed in %d ms", positions.getNumberOfPositions(),
                totalTimeMs));
        core_.logMessage(String.format("Time per well: %d ms", (int)((double)totalTimeMs/positions.getNumberOfPositions() + 0.5)));

		active_ = false;
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

    	/**
	 * Restore previous driver configuration
	 * @param driver Driver name
	 * @param chunksz Chunk size
	 * @param dirio Direct I/O
	 * @param flushcyc Flush cycle
	 * @throws Exception
	 */
	protected void setStorageConfig(String driver, int chunksz, boolean dirio, int flushcyc) throws Exception {
		core_.setProperty(driver, "ChunkSize", chunksz);
		core_.setProperty(driver, "DirectIO", dirio);
		core_.setProperty(driver, "FlushCycle", flushcyc);
	}


    private void setSequenceMode(String cam) throws Exception {
	    core_.setProperty(cam, "TRIGGER SOURCE", "EXTERNAL");
	    core_.setProperty(cam, "TRIGGER ACTIVE", "LEVEL");
	    core_.setProperty(cam, "TriggerPolarity", "POSITIVE");
    }

    void setStandardMode(String cam) throws Exception {
	    core_.setProperty(cam, "TRIGGER SOURCE", "SOFTWARE");
	    core_.setProperty(cam, "TRIGGER ACTIVE", "EDGE");
	    core_.setProperty(cam, "TriggerPolarity", "NEGATIVE");
    }



}
