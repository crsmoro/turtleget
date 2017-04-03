package com.shuffle.turtleget;

public interface DownloadListener {

	/**
	 * Called when download is started
	 * 
	 * @param download
	 */
	void started();

	/**
	 * Called when download is paused
	 * 
	 * @param download
	 */
	void paused();

	/**
	 * Called when download is finished
	 * 
	 * @param download
	 */
	void finished();

	/**
	 * Called when the progress (%) is changed
	 * 
	 * @param download
	 */
	void progress();
	
	/**
	 * Called when a error occurs
	 */
	void error(Exception exception);
}
