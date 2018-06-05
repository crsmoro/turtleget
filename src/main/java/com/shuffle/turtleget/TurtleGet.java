package com.shuffle.turtleget;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.provider.ftp.FtpFileSystemConfigBuilder;

public class TurtleGet {

	private static final transient Log log = LogFactory.getLog(TurtleGet.class);

	private FileSystemManager fileSystemManager;

	private FileSystemOptions ftpFileSystemOptions;

	private Map<String, FileSystemOptions> fileSystemOptions = new HashMap<>();

	private TurtleGetData data;

	private SortedSet<Download> queue = new TreeSet<>();

	private SortedSet<Download> history = new TreeSet<>();
	
	private static final File defaultDataFile = new File(System.getProperty("user.home") + File.separator + ".turtleget" + File.separator + "data.tg");

	private File dataFile;
	
	private FileInputStream dataFileInputStream;

	private ExecutorService executorService = Executors.newSingleThreadExecutor();

	private TurtleGetListener downloadCallback = new DownloadCallback(this);

	private List<TurtleGetListener> listener = new ArrayList<>();

	public enum StartType {
		AUTOMATICALLY, MANUALLY, SCHEDULE
	}
	
	public TurtleGet() {
		this(defaultDataFile);
	}

	public TurtleGet(File dataFile) {
		try {
			fileSystemManager = VFS.getManager();
		} catch (FileSystemException e) {
			throw new RuntimeException(e);
		}
		
		this.dataFile = Optional.ofNullable(dataFile).orElse(defaultDataFile);
		setupFtpFileSystemOptions();
		initFileSystemOptions();
		loadData();
	}

	//TODO Verify if file still exists on source (kinda fixed on thread error)
	private void loadDataQueue() {
		log.info("Loading saved queue");
		for (DownloadData downloadData : data.getQueue()) {
			try {
				queue.add(new Download(fileSystemManager.resolveFile(downloadData.getSource(), getFileSystemOptions(downloadData.getSource())),
						fileSystemManager.resolveFile(downloadData.getDestination(), getFileSystemOptions(downloadData.getDestination()))));
			} catch (FileSystemException e) {
				log.error("Error loading saved download queue");
			}
		}
		log.info(queue);
		log.info("Finished loading saved queue");
	}

	private void loadDataHistory() {
		log.info("Loading download history");
		for (DownloadData downloadData : data.getHistory()) {
			try {
				Download download = new Download(fileSystemManager.resolveFile(downloadData.getSource(), getFileSystemOptions(downloadData.getSource())),
						fileSystemManager.resolveFile(downloadData.getDestination(), getFileSystemOptions(downloadData.getDestination())));
				download.setStatus(DownloadStatus.COMPLETE);
				history.add(download);
			} catch (FileSystemException e) {
				log.error("Error loading download history", e);
			}
		}
		log.info(history);
		log.info("Finished loading download history");
	}

	private void loadData() {
		try {
			log.debug("dataFile : " + dataFile.getAbsolutePath());
			if (!dataFile.exists()) {
				dataFile.getParentFile().mkdirs();
				dataFile.createNewFile();
			}
			dataFileInputStream = new FileInputStream(dataFile);
			ObjectInputStream objectInputStream = new ObjectInputStream(dataFileInputStream);
			data = (TurtleGetData) objectInputStream.readObject();
			objectInputStream.close();
			loadDataHistory();
			loadDataQueue();
		} catch (IOException | ClassNotFoundException e) {
			log.error("Error loading download manager data, creating new one", e);
			data = new TurtleGetData();
		}
	}

	private void saveDataQueue() {
		data.getQueue().clear();
		for (Download download : queue) {
			data.getQueue().add(new DownloadData(download.getSource().getName().getURI(), download.getDestination().getName().getURI(), download.getAdded()));
		}
	}

	private void saveDataHistory() {
		data.getHistory().clear();
		for (Download download : history) {
			data.getHistory().add(new DownloadData(download.getSource().getName().getURI(), download.getDestination().getName().getURI(), download.getAdded()));
		}
	}

	private void saveData() {
		try {
			saveDataQueue();
			saveDataHistory();
			ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(dataFile));
			objectOutputStream.writeObject(data);
			objectOutputStream.flush();
			objectOutputStream.close();
		} catch (IOException e) {
			log.error("Error while saving download manager data, all data will be lost", e);
		}
	}

	private void initFileSystemOptions() {
		fileSystemOptions.put("ftp://", ftpFileSystemOptions);
	}

	private void setupFtpFileSystemOptions() {
		FtpFileSystemConfigBuilder ftpFileSystemConfigBuilder = FtpFileSystemConfigBuilder.getInstance();
		ftpFileSystemOptions = new FileSystemOptions();
		ftpFileSystemConfigBuilder.setControlEncoding(ftpFileSystemOptions, "UTF-8");
		ftpFileSystemConfigBuilder.setPassiveMode(ftpFileSystemOptions, true);
		//TODO get timeout from properties
		ftpFileSystemConfigBuilder.setSoTimeout(ftpFileSystemOptions, 10000);
		ftpFileSystemConfigBuilder.setConnectTimeout(ftpFileSystemOptions, 10000);
		ftpFileSystemConfigBuilder.setDataTimeout(ftpFileSystemOptions, 10000);
		//FIX the not found file sometimes
		ftpFileSystemConfigBuilder.setUserDirIsRoot(ftpFileSystemOptions, true);
	}

	private FileSystemOptions getFileSystemOptions(String path) {
		for (String protocol : fileSystemOptions.keySet()) {
			if (startsWithIgnoreCase(path, protocol)) {
				return fileSystemOptions.get(protocol);
			}
		}
		return null;
	}

	private List<FileObject> addFolderSubfolders(FileObject sourceFileObject) throws FileSystemException {
		log.debug("add folder subfolders");
		List<FileObject> sourcesFileObject = new ArrayList<>();
		log.trace("sourceFileObject childrens : " + sourceFileObject.getChildren().length);
		for (FileObject sourceFileObjectItem : sourceFileObject.getChildren()) {
			log.trace("sourceFileObjectItem : " + sourceFileObjectItem);
			log.trace("sourceFileObjectItem is folder : " + sourceFileObjectItem.isFolder());
			if (!sourceFileObjectItem.isFolder()) {
				sourcesFileObject.add(sourceFileObjectItem);
			} else {
				sourcesFileObject.addAll(addFolderSubfolders(sourceFileObjectItem));
			}
		}
		return sourcesFileObject;
	}

	private FileObject resolveDestinationPathFromSource(FileObject sourceFileObject, FileObject destinationFileObject, String basePath) throws FileSystemException {
		log.debug("resolving destination path from source");
		String destinationPath = destinationFileObject.getName().getFriendlyURI();
		if (destinationFileObject.isFile()) {
			destinationPath = destinationFileObject.getParent().getName().getFriendlyURI();
		}
		return fileSystemManager.resolveFile(destinationPath + sourceFileObject.getName().getFriendlyURI().replace(basePath, ""));
	}

	private void createDestinationPath(FileObject destinationFileObject) throws FileSystemException {
		log.debug("creating destination path");
		log.trace("destinationFileObject : " + destinationFileObject);
		log.trace("destinationFileObject is folder : " + destinationFileObject.isFolder());
		log.trace("destinationFileObject exists : " + destinationFileObject.exists());
		if (destinationFileObject.isFolder()) {
			destinationFileObject.createFolder();
		} else if (!destinationFileObject.exists()) {
			destinationFileObject.createFolder();
		} else {
			destinationFileObject.getParent().createFolder();
		}
	}

	private List<FileObject> setupSourceFiles(FileObject sourceFileObject) throws FileSystemException {
		log.debug("setting up source files");
		List<FileObject> sourcesFileObject = new ArrayList<>();
		log.trace("sourceFileObject : " + sourceFileObject);
		log.trace("sourceFileObject is folder : " + sourceFileObject.isFolder());
		if (sourceFileObject.isFolder()) {
			sourcesFileObject.addAll(addFolderSubfolders(sourceFileObject));
		} else {
			sourcesFileObject.add(sourceFileObject);
		}
		return sourcesFileObject;
	}

	private void setupDownloadFiles(FileObject sourceFileObject, FileObject destinationFileObject, StartType startType) throws FileSystemException {
		log.debug("setting up download files");
		String basePath = sourceFileObject.getParent().getName().getFriendlyURI();
		log.trace("basePath : " + basePath);
		List<FileObject> sourcesFileObject = setupSourceFiles(sourceFileObject);
		createDestinationPath(destinationFileObject);
		for (FileObject sourceFileObjectItem : sourcesFileObject) {
			log.debug("addding " + sourceFileObject);
			addDownload(sourceFileObjectItem, resolveDestinationPathFromSource(sourceFileObjectItem, destinationFileObject, basePath), startType);
		}

	}

	public void addDownload(String source, String destination) {
		addDownload(source, destination, StartType.AUTOMATICALLY);
	}

	public void addDownload(String source, String destination, StartType startType) {
		log.debug("Adding new download with startType");
		log.debug(source + " -> " + destination);
		log.debug(startType);
		try {
			FileObject sourceFileObject = fileSystemManager.resolveFile(source, getFileSystemOptions(source));
			FileObject destinationFileObject = fileSystemManager.resolveFile(destination, getFileSystemOptions(source));
			setupDownloadFiles(sourceFileObject, destinationFileObject, startType);
		} catch (FileSystemException e) {
			throw new RuntimeException(e);
		}
	}

	public void addDownload(FileObject source, FileObject destination) {
		addDownload(source, destination, StartType.AUTOMATICALLY);
	}

	public void addDownload(FileObject source, FileObject destination, StartType startType) {
		log.debug("creating new Download");
		Download download = new Download(source, destination);
		log.debug("new Download created");
		
		if (queue.stream().filter(d -> d.equals(download)).findFirst().isPresent() || history.stream().filter(d -> d.equals(download)).findFirst().isPresent()) {
			log.info(download.getName() + " was already downloaded and/or in queue");
		} else {
			addDownload(download, startType);
		}
	}

	public void addDownload(Download download) {
		addDownload(download, StartType.AUTOMATICALLY);
	}

	public void addDownload(Download download, StartType startType) {
		log.debug("adding to queue" + download);
		if (!history.stream().filter(d -> d.equals(download)).findFirst().isPresent() && !getQueue().stream().filter(d -> d.equals(download)).findFirst().isPresent()) {
			getQueue().add(download);
			saveData();
			log.info("Added to queue : " + download);
			startDownload(download, startType);
		}
	}

	public SortedSet<Download> getQueue() {
		return queue;
	}

	public void startDownload(Download download) {
		start(download, StartType.AUTOMATICALLY);
	}

	public void startDownload(Download download, StartType startType) {
		download.setDownloadManager(this);
		if (startType.equals(StartType.AUTOMATICALLY)) {
			download.schedule();
			if (!isDownloading()) {
				download.start();
				log.debug("added " + download + " to executor");
				executorService.submit(download);
			}
		} else if (startType.equals(StartType.SCHEDULE)) {
			throw new IllegalArgumentException("StartType not yet implemented");
		}

	}

	public void pause(Download download) {
		download.pause();
		log.trace(download);
	}
	
	public boolean isDownloading() {
		return getQueue().stream().filter(d -> d.getStatus().equals(DownloadStatus.IN_PROGRESS)).count() > 0;
	}

	public void pause() {
		if (getQueue().isEmpty()) {

		} else {
			pause(getQueue().first());
		}
	}

	public void pauseAll() {
		for (Download download : getQueue()) {
			if (download.getStatus().equals(DownloadStatus.IN_PROGRESS)) {
				pause(download);
			}
		}
	}

	public void start(Download download) {
		start(download, StartType.AUTOMATICALLY);
	}

	public void start(Download download, StartType startType) {
		startDownload(download, startType);
	}

	public void start() {
		if (getQueue().isEmpty()) {

		} else {
			log.trace("queue");
			log.trace(getQueue());
			Download nextDownload = getQueue().stream().sorted().filter(d -> d.getStatus().equals(DownloadStatus.SCHEDULED)).findFirst().orElse(null);
			log.debug("nextDownload : " + nextDownload);
			if (nextDownload != null) {
				start(nextDownload);
			}
		}
	}

	public void startAll() {
		for (Download download : getQueue()) {
			startDownload(download);
		}
	}
	
	public void removeDownload(Download download) {
		getQueue().remove(download);
		saveData();
	}

	public TurtleGetListener getDownloadCallback() {
		return downloadCallback;
	}

	public List<TurtleGetListener> getListener() {
		return listener;
	}

	public void addListener(TurtleGetListener listener) {
		this.listener.add(listener);
	}

	public void removeListener(TurtleGetListener listener) {
		this.listener.remove(listener);
	}

	private class DownloadCallback implements TurtleGetListener {

		private TurtleGet downloadManager;

		public DownloadCallback(TurtleGet downloadManager) {
			this.downloadManager = downloadManager;
		}

		@Override
		public void initialized() {

		}

		@Override
		public void started(Download download) {
			log.info("started : " + download.getName());
			for (TurtleGetListener downloadManagerListener : this.downloadManager.listener) {
				downloadManagerListener.started(download);
			}
		}

		@Override
		public void paused(Download download) {
			log.info("paused : " + download.getName());
			for (TurtleGetListener downloadManagerListener : this.downloadManager.listener) {
				downloadManagerListener.paused(download);
			}
		}

		@Override
		public void finished(Download download) {
			log.info("finished : " + download.getName());
			getQueue().remove(download);
			history.add(download);
			saveData();
			log.trace(getQueue());
			for (TurtleGetListener downloadManagerListener : this.downloadManager.listener) {
				downloadManagerListener.finished(download);
			}
			start();
		}

		@Override
		public void progress(Download download) {
			log.info("Download " + download.getName() + " progress : " + download.getPercent());
			for (TurtleGetListener downloadManagerListener : this.downloadManager.listener) {
				downloadManagerListener.progress(download);
			}
		}

		@Override
		public void error(Download download, Exception exception) {
			log.info("Download " + download.getName() + " error");
			for (TurtleGetListener downloadManagerListener : this.downloadManager.listener) {
				downloadManagerListener.error(download, exception);
			}
		}
	}

	/**
	 * from apache commons 3.4
	 * 
	 * @param str
	 * @param prefix
	 * @return
	 */
	private boolean startsWithIgnoreCase(final CharSequence str, final CharSequence prefix) {
		return startsWith(str, prefix, true);
	}

	private boolean startsWith(final CharSequence str, final CharSequence prefix, final boolean ignoreCase) {
		if (str == null || prefix == null) {
			return str == null && prefix == null;
		}
		if (prefix.length() > str.length()) {
			return false;
		}
		return regionMatches(str, ignoreCase, 0, prefix, 0, prefix.length());
	}

	private boolean regionMatches(final CharSequence cs, final boolean ignoreCase, final int thisStart, final CharSequence substring, final int start, final int length) {
		if (cs instanceof String && substring instanceof String) {
			return ((String) cs).regionMatches(ignoreCase, thisStart, (String) substring, start, length);
		}
		int index1 = thisStart;
		int index2 = start;
		int tmpLen = length;

		while (tmpLen-- > 0) {
			final char c1 = cs.charAt(index1++);
			final char c2 = substring.charAt(index2++);

			if (c1 == c2) {
				continue;
			}

			if (!ignoreCase) {
				return false;
			}

			// The same check as in String.regionMatches():
			if (Character.toUpperCase(c1) != Character.toUpperCase(c2) && Character.toLowerCase(c1) != Character.toLowerCase(c2)) {
				return false;
			}
		}

		return true;
	}
}
