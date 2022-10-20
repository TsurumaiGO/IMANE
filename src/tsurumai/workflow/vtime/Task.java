package tsurumai.workflow.vtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import tsurumai.workflow.util.Util;

/**仮想時刻にもとづくスケジュールタスクを表現する*/
@XmlRootElement
@JsonPropertyOrder({"name", "notBefore", "interval", "maxCount", "lastExecuted"})
public class Task {
	
	/**実行予定時刻*/
	@XmlAttribute
	public Date getNotBefore() {return this.notBefore;}
	Date notBefore = null;
	/**繰り返し実行回数*/
	@XmlAttribute
	public int getMaxCount() {return this.maxCount;}
	int maxCount = 1;
	public int setMaxCount(int count) {return this.maxCount = count;}
	
	
	/**繰り返し実行間隔(ms)*/
	@XmlAttribute
	public long getInterval() {return this.interval;}
	long interval = 0;
	
	/**実行された回数*/
	@XmlAttribute
	public int getProceeded() {return this.proceeded;}
	int proceeded = 0;
	
	@XmlAttribute
	public String getName() {return this.name;}
	String name = ""; 
	
	@XmlAttribute
	public Date getLastExecuted() {return this.lastExecuted;}
	Date lastExecuted = null;
	

	/**タスク実行イベントを受信するリスナ*/
	public interface TaskListener{
		/**タスク実行後に呼び出される*/
		public void onExecuted(Task t, Date when);
		/**タスク実行が保留された場合に呼び出される*/
		public void onSkipped(Task t, Date when, String reason);
		
	}
	private Collection<TaskListener> listeners = new ArrayList<Task.TaskListener>();
	/**タスク実行イベントのリスナを登録する*/
	public Task addListener(TaskListener l) {
		if(!this.listeners.contains(l))	this.listeners.add(l);
		return this;
	}

	@Override
	public String toString() {
		
//		SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss");
//		String ret = String.format("%s %s %d %d/%d %s %s", this.name, 
//				this.notBefore != null ? f.format(this.notBefore) : "N/A",
//				this.interval, this.proceeded, this.maxCount, 
//				this.lastExecuted != null ? f.format(lastExecuted) : "N/A", 
//				f.format(new Date()));
//		return ret;
		return Util.toJson(this);
	}

	@Override public boolean equals(Object obj) {
		if(obj == null) return false;
		Task task = (Task)obj;
		if(!(task.name.equals(this.name)))return false;
		//TODO:同期ずれで実行時間がずれてしまう
		//if(!(task.notBefore != null && !task.notBefore.equals(this.notBefore)))return false;
		//if(task.interval != this.interval) return false;
		return true;
		
	}
	/**初期化
	 * @param name ラベル
	 * @param task 実行する処理
	 * @param notBefore 初回実行日時
	 * @param repeat 繰り返し実行回数
	 * @param interval 繰り返し実行間隔(ms)
	 * */
	public Task(String name, Date notBefore, long interval, int repeat){
		this.name = name; this.notBefore = notBefore; this.maxCount = repeat; this.interval = interval;
	}
	public Task(Date notBefore, long interval, int repeat){
		this("", notBefore, interval, repeat);
		this.name = "task-" + String.valueOf(this.hashCode());
	}
	public Task(String name, Date notBefore){
		this(name, notBefore, 0, 1);
	}
	public Task(String name, long interval, int repeat){
		this(name, new Date(), interval, repeat);
	}
	
	/**実行条件を検査し、条件を満たしていたら処理を実行
	 * @param now 現在時刻を指定(仮想時刻でも実時刻でも良い)。nullなら実時刻での現在時間として処理
	 * @return 実行したらtrue
	 * */
	public boolean eval(VirtualTimeManager vdm) {
		//Date d = now != null ? now : new Date();
		Date d = vdm.now();
		if(this.proceeded >= this.maxCount) {
			notifyToListeners(false, d, "max count exceeded");
			return false;
		}
		if(this.notBefore != null && d.before(this.notBefore)) {
			notifyToListeners(false, d, "waiting for timeout");
			return false;
		}
		if(this.maxCount > 1 && 
				(lastExecuted != null && 
				(d.getTime() < (lastExecuted.getTime() + this.interval)))){
			notifyToListeners(false, d, "waiting for interval");
			return false;
		}

		exec(vdm);
		return true;
	}
	/**このタスクの実行回数が残っているか*/
	public boolean remains() {
		if(this.maxCount > this.proceeded) return true;
		return false;
	}
	public interface TaskExecutor{
		public int exec();
	}
	/**タスクを実行しスケジュール状態を更新*/
	protected void exec(VirtualTimeManager vdm) {
		this.proceeded ++;
		Date when = vdm.now();
		notifyToListeners(when);
		this.lastExecuted = when;
		
	}
	private void notifyToListeners(Date when) {
		notifyToListeners(true, when, "");
	}
	private void notifyToListeners(boolean executed, Date when, String reason) {
		for(TaskListener l : this.listeners) {
			if(!executed)
				l.onSkipped(this, when, reason);
			else
				l.onExecuted(this, when);
		};
		
	}
	
}
