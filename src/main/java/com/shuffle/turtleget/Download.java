package com.shuffle.turtleget;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.RandomAccessContent;
import org.apache.commons.vfs2.util.RandomAccessMode;

public class Download implements Comparator<Download>, Comparable<Download>, Callable<Boolean> {

	private static final transient Log log = LogFactory.getLog(Download.class);

	private FileObject source;

	private FileObject destination;

	private Date added;

	private DownloadStatus status;
	
	//TODO Add schdule date

	private long size;

	private long downloaded;

	private double percent;

	private List<DownloadListener> listener = new ArrayList<>();

	private TurtleGet downloadManager;

	public Download() {
		setAdded(new Date());
		this.status = DownloadStatus.SCHEDULED;
	}

	public Download(FileObject source, FileObject destination) {
		this();
		this.source = source;
		this.destination = destination;
	}
	
	public String getName() {
		return source.getName().getBaseName();
	}

	public FileObject getSource() {
		return source;
	}

	public void setSource(FileObject source) {
		this.source = source;
	}

	public FileObject getDestination() {
		return destination;
	}

	public void setDestination(FileObject destination) {
		this.destination = destination;
	}

	public Date getAdded() {
		return added;
	}

	public void setAdded(Date added) {
		this.added = added;
	}

	public double getPercent() {
		return percent;
	}

	protected void setStatus(DownloadStatus status) {
		this.status = status;
	}

	public DownloadStatus getStatus() {
		return status;
	}

	public void pause() {
		this.status = DownloadStatus.STOPPED;
	}

	public void start() {
		this.status = DownloadStatus.IN_PROGRESS;
		if (this.downloadManager == null) {
			startsItsOwnThread();
		}
	}
	
	public void schedule() {
		this.status = DownloadStatus.SCHEDULED;
	}
	
	public void schedule(Date when) {
		throw new IllegalArgumentException("Not yet implemented");
	}

	private void startsItsOwnThread() {
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		executorService.submit(this);
		executorService.shutdown();
	}

	public long getSize() {
		return this.size;
	}

	public long getDownload() {
		return this.downloaded;
	}

	public TurtleGet getDownloadManager() {
		return downloadManager;
	}

	public void setDownloadManager(TurtleGet downloadManager) {
		this.downloadManager = downloadManager;
	}

	public List<DownloadListener> getListener() {
		return listener;
	}

	public void addListener(DownloadListener listener) {
		this.listener.add(listener);
	}

	public void removeListener(DownloadListener listener) {
		this.listener.remove(listener);
	}

	@Override
	public int compare(Download o1, Download o2) {
		return o1.compareTo(o2);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((destination == null) ? 0 : destination.hashCode());
		result = prime * result + ((source == null) ? 0 : source.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Download other = (Download) obj;
		if (destination == null) {
			if (other.destination != null)
				return false;
		} else if (!destination.getName().getURI().equals(other.destination.getName().getURI()))
			return false;
		if (source == null) {
			if (other.source != null)
				return false;
		} else if (!source.getName().getURI().equals(other.source.getName().getURI()))
			return false;
		return true;
	}

	@Override
	public int compareTo(Download o) {
		return this.getAdded().compareTo(o.getAdded());
	}

	@Override
	public Boolean call() throws Exception {
		log.trace("START-" + destination.getPublicURIString());
		
		for (DownloadListener downloadListener : listener) {
			downloadListener.started();
		}
		if (downloadManager != null && downloadManager.getDownloadCallback() != null) {
			downloadManager.getDownloadCallback().started(this);
		}
		InputStream sourceFileIn = null;
		OutputStream destinationFileOut = null;
		
		try {
			this.size = source.getContent().getSize();
			long existingFileSize = destination.exists() ? destination.getContent().getSize() : 0;
			RandomAccessContent randomAccessContentRemote = source.getContent().getRandomAccessContent(RandomAccessMode.READ);
			randomAccessContentRemote.seek(existingFileSize);
			sourceFileIn = randomAccessContentRemote.getInputStream();
			destinationFileOut = destination.getContent().getOutputStream(true);
			
			copyStream(sourceFileIn, destinationFileOut, existingFileSize);
			
		}
		catch (Exception e) {
			log.error("Something went wrong when downloading", e);
			this.status = DownloadStatus.STOPPED;
			for (DownloadListener downloadListener : listener) {
				downloadListener.error(e);
			}
			if (downloadManager != null && downloadManager.getDownloadCallback() != null) {
				downloadManager.getDownloadCallback().error(this, e);
			}
		}
		finally {
			if (sourceFileIn != null) {
				sourceFileIn.close();				
			}
			if (destinationFileOut != null) {
				destinationFileOut.close();				
			}
		}
		if (!this.status.equals(DownloadStatus.STOPPED) && !this.status.equals(DownloadStatus.SCHEDULED)) {
			this.status = DownloadStatus.COMPLETE;
			log.trace("FINISHED-" + destination.getPublicURIString());
			for (DownloadListener downloadListener : listener) {
				downloadListener.finished();
			}
			if (downloadManager != null && downloadManager.getDownloadCallback() != null) {
				downloadManager.getDownloadCallback().finished(this);
			}
		} else {
			log.trace("PAUSED-" + destination.getPublicURIString());
			for (DownloadListener downloadListener : listener) {
				downloadListener.paused();
			}
			if (downloadManager != null && downloadManager.getDownloadCallback() != null) {
				downloadManager.getDownloadCallback().paused(this);
			}
		}
		return true;
	}

	private final int DEFAULT_COPY_BUFFER_SIZE = 1024;

	// stolen from commons.net.io.Util =D
	private long copyStream(InputStream source, OutputStream dest, long existingFileSize) throws IOException {
		int numBytes;
		long total = 0;
		byte[] buffer = new byte[DEFAULT_COPY_BUFFER_SIZE];

		while ((numBytes = source.read(buffer)) != -1) {
			if (this.status.equals(DownloadStatus.STOPPED)) {
				break;
			}
			// Technically, some read(byte[]) methods may
			// return 0 and we cannot
			// accept that as an indication of EOF.
			if (numBytes == 0) {
				int singleByte = source.read();
				if (singleByte < 0) {
					break;
				}
				dest.write(singleByte);
				dest.flush();
				++total;
				continue;
			}

			dest.write(buffer, 0, numBytes);
			dest.flush();
			total += numBytes;

			this.downloaded = (total + existingFileSize);
			double percent = this.downloaded * 100 / size;
			if (this.percent != percent) {
				this.percent = percent;
				for (DownloadListener downloadListener : listener) {
					downloadListener.progress();
				}
				if (downloadManager != null && downloadManager.getDownloadCallback() != null) {
					downloadManager.getDownloadCallback().progress(this);
				}
			}
		}

		return total;
	}

	@Override
	public String toString() {
		return "Download [name=" + destination.getName().getBaseName() + ", destination=" + destination.getPublicURIString() + ", added=" + added + ", status=" + status + ", size=" + size + ", downloaded=" + downloaded + ", percent="
				+ percent + "]";
	}
}
