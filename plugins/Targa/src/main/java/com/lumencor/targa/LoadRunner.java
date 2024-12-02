package org.lumencor.targa;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import mmcorej.CMMCore;
import mmcorej.StorageDataType;
import org.micromanager.Studio;
import org.micromanager.data.*;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.DefaultMetadata;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.internal.propertymap.NonPropertyMapJSONFormats;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class LoadRunner extends Thread {
	private final Set<LoadRunnerListener> listeners_ = new CopyOnWriteArraySet<>();
	private final Studio mmstudio_;
	private final CMMCore core_;
	private final Object stateMutex_;
	private final String dataPath_;
	private boolean active_;

	/**
	 * Class constructor
	 * @param studio Micro-manager app handle
	 * @param path Dataset path
	 */
	LoadRunner(Studio studio, String path) {
		mmstudio_ = studio;
		core_ = studio.getCMMCore();
		dataPath_ = path;
		active_ = false;
		stateMutex_ = new Object();
	}

	/**
	 * Thread body
	 */
	@Override
	public void run() {
		if(dataPath_.isEmpty()) {
			notifyListenersFail("Invalid data path");
			return;
		}

		String handle = "";
		active_ = true;
		try {
			// Load dataset and check dataset shape / pixel data type
			handle = core_.loadDataset(dataPath_);
			mmcorej.LongVector shape = core_.getDatasetShape(handle);
			mmcorej.StorageDataType type = core_.getDatasetPixelType(handle);
			if(shape == null || type == null || shape.size() != 5) {
				core_.closeDataset(handle);
				notifyListenersFail("Selected file is not a valid dataset");
				return;
			}
			// Check cancellation token
			synchronized(stateMutex_) {
				if(!active_) {
					core_.closeDataset(handle);
					return;
				}
			}

			// Get image parameters
			int w = shape.get((int)shape.size() - 1);
			int h = shape.get((int)shape.size() - 2);
			int bpp = type == StorageDataType.StorageDataType_GRAY16 ? 2 : 1;
			int numImages = 1;
			for(int i = 0; i < shape.size() - 2; i++)
				numImages *= shape.get(i);

			// Obtain summary metadata
			String dsmeta = core_.getSummaryMeta(handle);
			if(dsmeta != null && !dsmeta.isEmpty()) {
				try {
					JsonElement je = new JsonParser().parse(dsmeta);
					SummaryMetadata smeta = DefaultSummaryMetadata.fromPropertyMap(NonPropertyMapJSONFormats.metadata().fromGson(je));
					notifyListenersMetadata(smeta);
				} catch(Exception e) {
					mmstudio_.getLogManager().logError(e);
				}
			}

			// Check cancellation token
			synchronized(stateMutex_) {
				if(!active_) {
					core_.closeDataset(handle);
					return;
				}
			}

			// Load images
			for(int i = 0; i < shape.get(0); i++) {
				for(int j = 0; j < shape.get(1); j++) {
					for(int k = 0; k < shape.get(2); k++) {
						// Check cancellation token
						synchronized(stateMutex_) {
							if(!active_) {
								core_.closeDataset(handle);
								return;
							}
						}
						mmcorej.LongVector coords = new mmcorej.LongVector();
						coords.add(i);
						coords.add(j);
						coords.add(k);
						Object pixdata = core_.getImage(handle, coords);
						if(pixdata == null)
							continue;
						Metadata meta = null;
						String metastr = core_.getImageMeta(handle, coords);
						if(metastr != null && !metastr.isEmpty()) {
							try {
								JsonElement je = new JsonParser().parse(metastr);
								meta = DefaultMetadata.fromPropertyMap(NonPropertyMapJSONFormats.metadata().fromGson(je));
							} catch(Exception e) {
								mmstudio_.getLogManager().logError(e);
							}
						}
						Coords.CoordsBuilder builder = mmstudio_.data().coordsBuilder();
						builder.time(j).channel(k);
						Image img = new DefaultImage(pixdata, w, h, bpp, 1, builder.build(), meta);
						notifyListenersImage(img);
					}
				}
			}
			core_.closeDataset(handle);
			notifyListenersComplete(w, h, numImages, type);
		} catch(Exception ex) {
			mmstudio_.getLogManager().logError(ex);
			// Close the dataset
			try {
				if(!handle.isEmpty())
					core_.closeDataset(handle);
			} catch(Exception e) {
				mmstudio_.getLogManager().logError(e);
			}
			notifyListenersFail(ex.getMessage());
		} catch(Error ex) {
			mmstudio_.getLogManager().logError(ex.getMessage());
			// Close the dataset
			try {
				if(!handle.isEmpty())
					core_.closeDataset(handle);
			} catch(Exception e) {
				mmstudio_.getLogManager().logError(e);
			}
			notifyListenersFail(ex.getMessage());
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
	 * Add event listener
	 * @param listener Listener object
	 */
	public final synchronized void addListener(final LoadRunnerListener listener) {
		listeners_.add(listener);
	}

	/**
	 * Remove event listener
	 * @param listener Listener object
	 */
	public final synchronized void removeListener(final LoadRunnerListener listener) {
		listeners_.remove(listener);
	}

	/**
	 * Notify event listeners that the loading has been completed
	 * @param w Image width
	 * @param h Image height
	 * @param imgcnt Number of images
	 * @param type Pixel data type
	 */
	private synchronized void notifyListenersComplete(int w, int h, int imgcnt, mmcorej.StorageDataType type) {
		for(LoadRunnerListener listener : listeners_)
			listener.notifyLoadCompleted(w, h, imgcnt, type);
	}

	/**
	 * Notify event listeners of loading error
	 * @param msg Error message
	 */
	private synchronized void notifyListenersFail(String msg) {
		for(LoadRunnerListener listener : listeners_)
			listener.notifyLoadFailed(msg);
	}

	/**
	 * Notify event listeners of loading status update
	 * @param img Image handle
	 */
	private synchronized void notifyListenersImage(Image img) {
		for(LoadRunnerListener listener : listeners_)
			listener.notifyLoadImage(img);
	}

	/**
	 * Notify event listeners of loading status update
	 * @param meta Metadata
	 */
	private synchronized void notifyListenersMetadata(SummaryMetadata meta) {
		for(LoadRunnerListener listener : listeners_)
			listener.notifyLoadSummaryMetadata(meta);
	}
}
