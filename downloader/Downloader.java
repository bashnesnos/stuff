import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Downloader {
	
	public static void main(String[] args) {
		ExecutorService downloadThread = Executors.newSingleThreadExecutor(new ThreadFactory() {
			
			@Override
			public Thread newThread(Runnable arg0) {
				Thread newThread = new Thread(arg0);
				newThread.setDaemon(true);
				return newThread;
			}
		});
		
		try {
			InputStream from = new FileInputStream(new File("bla.txt"));
			OutputStream to = new FileOutputStream(new File("bla_bla.txt"));
			
			FutureDownload download = new FutureDownload(from, to);
			downloadThread.execute(download);
			
			download.pause();
			
			System.out.println("Paused");
			Thread.sleep(10000);
			
			download.resume();
			System.out.println("Resumed to wait");
			Thread.sleep(100);
			
			download.pause();
			
			System.out.println("Paused");
			Thread.sleep(10000);

			download.cancel(true);
			
			System.out.println("Cancelled");
			
//			download.resume();
//			System.out.println("Resumed to get");
//			
//			download.get();
//			
//			System.out.println("Finished");
			
			downloadThread.shutdown();
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}

class FutureDownload implements RunnableFuture<Boolean> {
	private final InterruptibleDownloader downloaderDelegate;
	private final FutureTask<Boolean> taskDelegate;
	
	public FutureDownload(InputStream is, OutputStream os) {
		this.downloaderDelegate = new InterruptibleDownloader(is, os);
		this.taskDelegate = new FutureTask<Boolean>(downloaderDelegate);
	}
	
	@Override
	public boolean cancel(boolean arg0) {
		return taskDelegate.cancel(arg0);
	}

	@Override
	public Boolean get() throws InterruptedException, ExecutionException {
		return taskDelegate.get();
	}

	@Override
	public Boolean get(long arg0, TimeUnit arg1) throws InterruptedException,
			ExecutionException, TimeoutException {
		return taskDelegate.get(arg0, arg1);
	}

	@Override
	public boolean isCancelled() {
		return taskDelegate.isCancelled();
	}

	@Override
	public boolean isDone() {
		return taskDelegate.isDone();
	}
	
	public void pause() {
		if (!isDone()) {
			downloaderDelegate.pause();
		}
	}
	
	public void resume() {
		if (!isDone()) {
			downloaderDelegate.resume();
		}
	}

	@Override
	public void run() {
		taskDelegate.run();
	}
	
}

class InterruptibleDownloader implements Callable<Boolean> {
	private final InputStream is;
	private final OutputStream os;
	
	private final Lock downloaderMutex = new ReentrantLock();
	private final Condition pauseCondition = downloaderMutex.newCondition(); 
	
	private volatile boolean paused = false;
	
	public InterruptibleDownloader(InputStream is, OutputStream os) {
		this.is = is;
		this.os = os;
	}

	@Override
	public Boolean call() {
		try {
			if (downloaderMutex.tryLock()) {
				int available = 0;
				while (!Thread.currentThread().isInterrupted() && (available = is.available()) > 0) {
					if (paused) {
						pauseCondition.await();
					}
					
					byte[] buffer = new byte[available > 8096 ? 8096 : available];
					is.read(buffer);
					os.write(buffer);
					os.flush();
				}
			}
			else {
				throw new RuntimeException("A downloader instance should executed once and in a single thread!");
			}
		}
		catch (IOException ioe) {
			ioe.printStackTrace();
		}
		catch (InterruptedException ie) {
			ie.printStackTrace();
		}
		finally {
			downloaderMutex.unlock();
			
			try {
				is.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			try {
				os.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return true;
	}

	public void pause() {
		paused = true;
	}
	
	public void resume() {
		if (paused) {
			downloaderMutex.lock();
			try {
				paused = false;
				pauseCondition.signal();
			}
			finally {
				downloaderMutex.unlock();
			}
		}
	}
	
}