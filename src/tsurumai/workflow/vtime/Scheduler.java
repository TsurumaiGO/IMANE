package tsurumai.workflow.vtime;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import tsurumai.workflow.util.ServiceLogger;
import tsurumai.workflow.util.Util;

/**仮想時刻に基づくタスクスケジューラ*/
@XmlRootElement
public class Scheduler {
	
//	/**システムプロパティvtime.suspendloopを設定するとスケジュール実行を一時休止する*/
//	public static String KEY_SUSPEND_LOOP = "vtime.suspendloop";
	/**システムプロパティvtime.pollingintervalでポーリング間隔(ms)を設定する*/
	public static String KEY_POLLING_INTERVAL = "vtime.pollinginterval"; 

	public static int POLLING_INTERVAL = System.getProperty(KEY_POLLING_INTERVAL) != null ? Integer.valueOf(System.getProperty(KEY_POLLING_INTERVAL)) : 200;
	
	ServiceLogger logger = ServiceLogger.getLogger();

	@XmlAttribute
	boolean abort = false;
	Thread worker = null;
	@XmlElement
	CopyOnWriteArrayList<Task> taskPool = new CopyOnWriteArrayList<>();
	@XmlElement
	CopyOnWriteArrayList<Task> taskHistory = new CopyOnWriteArrayList<>();

	public class TaskHistory{
		public Date executed = null;
		public Date executed_v = null;
		public Task task = null;
		public TaskHistory(Task task, Date executed){
			this.task = task;
			this.executed = executed;
//			Date rnow = new Date();
//			this.executed_v = Scheduler.this.vdm.getRealTime(executed);
//			if(!Scheduler.this.vdm.getRealTime(executed).equals(rnow))
//				Util.warn("vtime/rtime is not matched." + task.toString());
		}
	}
	@XmlElement
	VirtualTimeManager vdm = null;
	
	Collection<TaskHistory> history = new ArrayList<Scheduler.TaskHistory>();
	
	/**@param vdm 仮想時刻 nullなら実時刻ベースで動作*/
	public Scheduler initialize(VirtualTimeManager vdm) {
		this.vdm = vdm;
		return this;
	}
	public void initialize() {
		this.initialize(null);
	}

	public Scheduler start() {
		worker = new Thread() {
			@Override public void run() {
				mainLoop();
			}
		};
		worker.start();
		return this;
	}
	/**待機中のスケジュールタスクを停止する*/
	public void stop() {
		if(this.worker != null)
			this.worker.interrupt();
		this.taskHistory.clear();
		this.history.clear();
		this.taskPool.clear();
		this.worker = null;
	}
	/**スケジュールタスクを登録する。*/
	public Scheduler register(Task task) {
		if(taskPool.contains(task)) {
			logger.warn("registering same schedule task, ignored." + task.toString());
			return this;
		}
		taskPool.add(task);
		return this;
	}
	/**スレッド停止のために内部で使用*/
	synchronized void abort() {
		this.abort = true;
	} 
	public void dumpTasks() {
		StringBuffer buff = new StringBuffer();
		this.taskPool.iterator().forEachRemaining(new Consumer<Task>() {
		@Override public void accept(Task t) {
			if(buff.length() != 0) buff.append(";");
			buff.append(t.toString());
		}});
		logger.info(this.toString() + "{" + buff.toString() + "}");
	}
	/**待機中のタスクをスキャンし実行する*/
	void mainLoop() {
		boolean once = false;
		while(true) {
			try {
				Thread.sleep(POLLING_INTERVAL);
				
				if(this.vdm.isPaused()) {
					if(!once) {
						logger.info("scheduler loop paused.");
						once = true;
					}
					continue;
				}
				once = false;
				
				for(Iterator<Task> i = taskPool.iterator(); i.hasNext();) {
					if(this.abort) {
						logger.info("scheduler loop aborted.");
						return;
					}
					Date now = this.vdm != null ? vdm.now() : new Date();
					Task t = i.next();
					if(t.eval(this.vdm)) {
						this.history.add(new TaskHistory(t, now));

						if(!t.remains())
							this.taskPool.remove(t);
					}
				}
			
			}catch (InterruptedException e) {}
		}
	}
	
	/**テスト用*/
	static String loadFile(final File path) throws IOException{
		try(FileInputStream strm =  new FileInputStream(path)){
			byte[] buff = strm.readAllBytes();
			return new String(buff, Charset.forName("UTF-8"));
		}catch(IOException t) {
			throw new IOException("failed to load json " + path.toString());
		}		
		
	}
	/**ファイルからデシリアライズ*/
	public static Scheduler load(final String path) throws IOException{
		String buff = loadFile(new File(path)); 
		ObjectMapper mapper = new ObjectMapper();
		Scheduler me = mapper.readValue(buff,  Scheduler.class);
		return me;
	}
	/**ファイルにシリアライズ*/
	public void save(final File out) throws IOException{
		ObjectMapper mapper = new ObjectMapper();
		try(FileOutputStream o = new FileOutputStream(out)) {
			String data = mapper.writeValueAsString(this);
			o.write(data.getBytes(Charset.forName("UTF-8")));
		}catch (JsonProcessingException e) {
			throw new IOException("failed to save object", e);
		}

	}
	public static void main(String[] args) {
		
	}
}
