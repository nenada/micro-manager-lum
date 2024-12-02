package org.lumencor.targa;

import org.micromanager.data.Image;
import org.micromanager.data.SummaryMetadata;

public interface LoadRunnerListener {
	void notifyLoadCompleted(int w, int h, int imgcnt, mmcorej.StorageDataType type);
	void notifyLoadFailed(String msg);
	void notifyLoadImage(Image img);
	void notifyLoadSummaryMetadata(SummaryMetadata meta);
}
