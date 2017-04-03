package com.shuffle.turtleget;

import java.io.Serializable;
import java.util.SortedSet;
import java.util.TreeSet;

class TurtleGetData implements Serializable {

	private static final long serialVersionUID = 2259402738926777546L;

	private SortedSet<DownloadData> queue = new TreeSet<>();

	private SortedSet<DownloadData> history = new TreeSet<>();

	public SortedSet<DownloadData> getQueue() {
		return queue;
	}

	public void setQueue(SortedSet<DownloadData> queue) {
		this.queue = queue;
	}

	public SortedSet<DownloadData> getHistory() {
		return history;
	}

	public void setHistory(SortedSet<DownloadData> history) {
		this.history = history;
	}
}
