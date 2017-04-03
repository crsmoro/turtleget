package com.shuffle.turtleget;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Date;

class DownloadData implements Serializable, Comparable<DownloadData>, Comparator<DownloadData> {

	private static final long serialVersionUID = 604934233087452169L;

	private String source;

	private String destination;

	private Date added;

	public DownloadData() {

	}

	public DownloadData(String source, String destination, Date added) {
		super();
		this.source = source;
		this.destination = destination;
		this.added = added;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getDestination() {
		return destination;
	}

	public void setDestination(String destination) {
		this.destination = destination;
	}

	public Date getAdded() {
		return added;
	}

	public void setAdded(Date added) {
		this.added = added;
	}

	@Override
	public int compare(DownloadData o1, DownloadData o2) {
		return o1.compareTo(o2);
	}

	@Override
	public int compareTo(DownloadData o) {
		return this.added.compareTo(o.getAdded());
	}
}