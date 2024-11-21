package org.lumencor.targa;

import org.micromanager.data.Image;

public interface AcqRunnerListener {
	void notifyWorkStarted();
	void notifyWorkCompleted();
	void notifyWorkFailed(String msg);
	void notifyStatusUpdate(int curr, int total, Image image, int bufffree, int bufftotal, double storems);
}
