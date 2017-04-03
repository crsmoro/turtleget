package com.shuffle.turtleget;

public interface TurtleGetListener {
	
	/**
	 * Called when download manager is initialized
	 */
	void initialized();

	/**
	 * Called when download is started
	 * 
	 * @param download
	 */
	void started(Download download);

	/**
	 * Called when download is paused
	 * 
	 * @param download
	 */
	void paused(Download download);

	/**
	 * Called when download is finished
	 * 
	 * @param download
	 */
	void finished(Download download);

	/**
	 * Called when the progress (%) is changed
	 * 
	 * @param download
	 */
	void progress(Download download);
	
	/**
	 * Called when a error occurs
	 */
	void error(Download download, Exception exception);
}
