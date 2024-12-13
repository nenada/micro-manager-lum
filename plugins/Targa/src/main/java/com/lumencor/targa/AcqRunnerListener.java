package org.lumencor.targa;

import org.micromanager.data.Image;

public interface AcqRunnerListener {
	void notifyAcqStarted();
	void notifyAcqCompleted();
	void notifyAcqFailed(String msg);
	void notifyAcqStatusUpdate(int curr, int total, Image image, int bufffree, int bufftotal, double storems);
	void logMessage(String msg);
	void logError(String msg);
}
