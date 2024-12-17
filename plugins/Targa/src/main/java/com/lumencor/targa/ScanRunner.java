package org.lumencor.targa;

import mmcorej.CMMCore;
import mmcorej.LongVector;
import mmcorej.StorageDataType;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONObject;
import org.micromanager.AutofocusPlugin;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.Studio;
import org.micromanager.data.Image;
import org.scijava.log.LogMessage;

import java.util.Set;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArraySet;

import static java.lang.System.currentTimeMillis;

/**
 * Recursion type plate scan assay
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
    private double[] zStack_ = {0.0, 5.0, 10.0};
    private Studio studio_;
	private final boolean autoFocus_;
	private boolean demoMode_;


    public ScanRunner(Studio std, String dataDir, String name, Vector<ChannelInfo> channels, int chunkSize,
                      boolean directIO, int flushCycle, boolean autoFocus) {
        super();
		active_ = false;
		core_ = std.getCMMCore();
		location_ = dataDir;
		name_ = name;
		buffFree_ = 0;
		buffTotal_ = 0;
		channels_ = channels;
		chunkSize_ = chunkSize;
		directIo_ = directIO;
		flushCycle_ = flushCycle;
		stateMutex_ = new Object();
        studio_ = std;
		autoFocus_ = autoFocus;
		demoMode_ = false;
    }

    /**
	 * Thread body
	 */
	@Override
	public void run() {
		String storageDriver = core_.getStorageDevice();
		if (storageDriver.isEmpty()) {
			notifyLogError("The configuration does not have any storage devices");
			return;
		}

        PositionList positions = studio_.positions().getPositionList();
		if (positions.getNumberOfPositions() == 0) {
			notifyLogError("No positions selected, aborting acquisition");
			return;
		}
		if (location_.isEmpty() || name_.isEmpty()) {
			notifyLogError("Output directory or dataset name not set, aborting acquisition");
			return;
		}
		total_ = channels_.size() * zStack_.length * positions.getNumberOfPositions();
		if(total_ <= 0) {
			notifyLogError("Dataset length is zero, aborting acquisition");
			return;
		}

        String handle = "";
		active_ = true;
        long startT = currentTimeMillis();
		PositionList newPositionList = new PositionList(); // this is used only if autofocus is enabled
		notifyLogMsg("Starting acquisition of " + positions.getNumberOfPositions() + " wells...");
		try {
			// Apply selected storage driver configuration
			setStorageConfig(storageDriver, chunkSize_, directIo_, flushCycle_);

			// Acquire images
            String xyStage = core_.getXYStageDevice();
            String zStage = core_.getFocusDevice();
            String camera = core_.getCameraDevice();
			demoMode_ = isDemo(camera);
			if (demoMode_)
				notifyLogMsg("System is in demo mode.");


            // start the camera
			// set camera configuration
			if (!autoFocus_) {
				// Normal acquisition
				// -----------------
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

				// Create summary metadata
				JSONObject summaryMeta = new JSONObject();
				JSONObject summaryObj = new JSONObject();
				JSONArray chNames = new JSONArray();
				for (ChannelInfo c : channels_)
					chNames.put(c.name);
				summaryObj.put("ChNames", chNames);
				summaryMeta.put("Summary", summaryMeta);

				handle = core_.createDataset(location_, name_, shape, pixType, summaryMeta.toString(3));

				// program the TTL sequence
				StringBuilder sequenceCmd = new StringBuilder();
				for (ChannelInfo channelInfo : channels_) {
					sequenceCmd.append(channelInfo.name).append(" ");
					core_.setProperty(TTL_SWITCH_DEV_NAME, channelInfo.getIntensityProperty(), channelInfo.intensity);
					core_.setProperty(TTL_SWITCH_DEV_NAME, channelInfo.getExposureProperty(), channelInfo.ttlExposureMs);
				}
				core_.setProperty(TTL_SWITCH_DEV_NAME, "ChannelSequence", sequenceCmd.toString());
				setSequenceMode(core_.getCameraDevice());

				core_.clearCircularBuffer();
				if (!demoMode_)
					core_.startContinuousSequenceAcquisition(0);
			} else {
				// AF acquisition
				// --------------
				LongVector shape = new LongVector();
				shape.add(positions.getNumberOfPositions());        // positions
				shape.add(1);										// single z
				shape.add(1);										// single channel
				shape.add((int)core_.getImageWidth());
				shape.add((int)core_.getImageHeight());
				long bpp = core_.getBytesPerPixel();
				StorageDataType pixType = bpp == 2 ? StorageDataType.StorageDataType_GRAY16 : (bpp == 1 ? StorageDataType.StorageDataType_GRAY8 : StorageDataType.StorageDataType_UNKNOWN);
				if(pixType == StorageDataType.StorageDataType_UNKNOWN)
					throw new Exception("Unsupported pixel depth of " + bpp + " bytes.");

				handle = core_.createDataset(location_, name_, shape, pixType, "");

				core_.setConfig("CHANNEL", "CYAN");
				core_.waitForConfig("CHANNEL", "CYAN");
			}

            // iterate over the positions
			int siteCounter = 0;
            for (int p=0; p<positions.getNumberOfPositions(); p++) {
				// Check cancellation token
				synchronized(stateMutex_) {
					if(!active_)
						break;
				}
                MultiStagePosition pos = positions.getPosition(p);
				long startPosTime = System.currentTimeMillis();
				long totalSaveTime = 0;
				long totalImageTime = 0;
				core_.setXYPosition(pos.getX(), pos.getY());
				double focusZ = pos.getZ();
                for (int z=0; z<(autoFocus_ ? 1 : zStack_.length); z++) {
					if (z == 0) {
						// special processing for the first z-stack position
						if (autoFocus_) {
							core_.waitForDevice(xyStage);
							long startFocusT = System.currentTimeMillis();
							AutofocusPlugin afPlugin = studio_.getAutofocusManager().getAutofocusMethod();
							if (afPlugin != null) {
								afPlugin.fullFocus();
								core_.waitForDevice(zStage);
								focusZ = core_.getPosition(zStage);
								long focusTime = System.currentTimeMillis() - startFocusT;
								notifyLogMsg(String.format("AF at %s: %.2f um in %d ms",
										pos.getLabel(), focusZ, focusTime));
								// add new position with modified focus
								MultiStagePosition newPos = new MultiStagePosition(xyStage, pos.getX(), pos.getY(), zStage, focusZ);
								newPos.setLabel(pos.getLabel());
								newPositionList.addPosition(newPos);
							} else {
								notifyLogMsg(String.format("%s: No AF device",
										pos.getLabel()));
								newPositionList.addPosition(pos);
							}
						} else {
							focusZ = pos.getZ();
							core_.setPosition(zStage, focusZ);
						}
					} else
						core_.setPosition(zStage, focusZ + zStack_[z]);

                    core_.waitForDevice(zStage);
                    core_.waitForDevice(xyStage);

					if (!autoFocus_) {
						long startImageTime = System.currentTimeMillis();

						triggerAcquisition(); // run images

						// wait for images to arrive in the circular buffer
						int retries = 0;
						int maxRetries = 300;
						while ((core_.getRemainingImageCount() < channels_.size()) && (retries < maxRetries)) {
							core_.sleep(1);
							retries++;
						}
						if (retries >= maxRetries)
							throw new Exception("Timeout waiting for images");
						totalImageTime += (System.currentTimeMillis() - startImageTime);

						// save the images
						long startSaveTime = System.currentTimeMillis();
						for (int c = 0; c < channels_.size(); c++) {
							// create coordinates for the image
							LongVector coords = new LongVector();
							coords.add(p); // position
							coords.add(z); // z
							coords.add(c); // channel
							coords.add(0);
							coords.add(0);
							core_.saveNextImage(handle, coords, "");
						} // end of channel loop
						totalSaveTime += (System.currentTimeMillis() - startSaveTime);
					} else {
						// AF
						long startSaveTime = System.currentTimeMillis();
						LongVector coords = new LongVector();
						coords.add(p);  // position
						coords.add(0);  // z position
						coords.add(0);  // channel
						coords.add(0);
						coords.add(0);
						core_.snapAndSave(handle, coords, "");
						totalSaveTime += (System.currentTimeMillis() - startSaveTime);
					}
                } // end of z-stack loop
				long wellTime = System.currentTimeMillis() - startPosTime;
				long moveTime = wellTime - totalSaveTime - totalImageTime;
				notifyLogMsg(String.format("%s processed in %d ms: image=%d, save=%d, move=%d.", pos.getLabel(),
						wellTime,
						totalImageTime,
						totalSaveTime,
						moveTime
						));
				siteCounter++;
            } // end of position loop
			long totalTimeMs = currentTimeMillis() - startT;
			notifyLogMsg(String.format("Acquisition of %d wells, completed in %.2f s", siteCounter,
					totalTimeMs/1000.0));
			notifyLogMsg(String.format("Time per well: %d ms", (int)((double)totalTimeMs/siteCounter + 0.5)));

		} catch(Exception | Error e) {
			notifyListenersFail(e.getMessage());
            notifyLogError("Error in ScanRunner: " + e.getMessage());
			active_ = false;
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
            notifyLogError("Error in ScanRunner: " + e.getMessage());
        }

		if (active_) {
			notifyListenersComplete();
		} else {
			String msg = "Acquisition failed or cancelled by the user";
			notifyLogError(msg);
			notifyListenersFail(msg);
		}

		if (autoFocus_) {
			// update the position list
			studio_.getPositionListManager().setPositionList(newPositionList);
			notifyLogMsg(String.format("Position list updated with %d XYZ positions.", newPositionList.getNumberOfPositions()));
		}

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

	private synchronized void notifyLogMsg(String msg) {
		for(AcqRunnerListener listener : listeners_)
			listener.logMessage(msg);
	}

	private synchronized void notifyLogError(String msg) {
		for(AcqRunnerListener listener : listeners_)
			listener.logError(msg);
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

	/**
	 * Cancel job
	 */
	public void cancel() {
		synchronized(stateMutex_) {
			if(!active_)
				return;
			active_ = false;
			notifyLogMsg("User requested to cancel.");
		}
	}

	/**
	 * Sets camera into sequence acquisition mode driven by the external TTL switch
	 * @param cam
	 * @throws Exception
	 */
    private void setSequenceMode(String cam) throws Exception {
		if (!demoMode_) {
			core_.setProperty(cam, "TRIGGER SOURCE", "EXTERNAL");
			core_.setProperty(cam, "TRIGGER ACTIVE", "LEVEL");
			core_.setProperty(cam, "TriggerPolarity", "POSITIVE");
		}
    }

	/**
	 * Sets the camera into standard software trigger mode
	 * @param cam
	 * @throws Exception
	 */
    void setStandardMode(String cam) throws Exception {
		if (!demoMode_) {
			core_.setProperty(cam, "TRIGGER SOURCE", "SOFTWARE");
			core_.setProperty(cam, "TRIGGER ACTIVE", "EDGE");
			core_.setProperty(cam, "TriggerPolarity", "NEGATIVE");
		}
    }

	/**
	 * Detects if we are using demo configuration
	 * @param cam - camera name
	 * @return - true if demo
	 */
	boolean isDemo(String cam) {
        try {
            String descr = core_.getProperty(cam, "Description");
			return descr.contains("Demo Camera");
        } catch (Exception e) {
            return false;
        }
    }

	/**
	 * Starts acquisition
	 * @throws Exception
	 */
	void triggerAcquisition() throws Exception {
		if (demoMode_)
			core_.startSequenceAcquisition(channels_.size(), 10.0, true);
		else
			core_.setProperty(TTL_SWITCH_DEV_NAME, TTL_SWITCH_PROP_RUN, "1"); // run images
	}


}
