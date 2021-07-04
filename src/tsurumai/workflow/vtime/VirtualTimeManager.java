package tsurumai.workflow.vtime;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;

import tsurumai.workflow.util.ServiceLogger;
import tsurumai.workflow.util.Util;

/**
 * 
 * 仮想時刻を管理する
 * 
 * インスタンスは実時刻との差分(オフセット)を保持する。
 * 仮想時刻を使用するには、new Date()のかわりにこのクラスのnewDate()メソッドを呼び出す。生成されるVirtualDateインスタンスは仮想時刻を表現する。
 * メソッド呼び出しにより時間進行を休止、再開することができる。休止・再開によって、このクラスから生成されたVirtualDateクラスのインスタンスも影響を受ける。
 * 
 * 
 * VirtualDateManagerはVM上で複数のインスタンスを持つ。それぞれのVirtualDateManagerインスタンスは独立した時間軸を持つ。
 * インスタンスの状態はVM上で共有される。
 * VirtualDateManagerのインスタンスは現在時刻と仮想時刻の差分(オフセット)を保持する。オフセットはVirtualDateManagerから生成した時刻オブジェクト(VirtualDate)に
 * 対して同期され、時刻オブジェクトにアクセスするたびに更新される。
 * VirtualDateManagerのインスタンスは時間スケール(実時間に対する時間進行速度の比)を保持する。仮想時刻は時間スケールに従って早く(遅く)進行する。
 * VirtualDateManagerインスタンスを中断すると、そこから生成された仮想時刻も中断される(時計が止まる)。VirtualDateManagerの再開によって仮想時刻はふたたび進行する。
 * 
 * VirtualDateManagerは最後に中断された時刻を保持する。シリアライズしたインスタンスをデシリアライズすると、中断された時刻と現在時刻の差がオフセットに加算され、現在時刻に対する相対時間という形で復元される。
 * 
 * 
 * 
 * 
 * */
@XmlRootElement
@JsonPropertyOrder({"time", "baseTime", "elapsed", "paused", "timescale", "lastUpdated"})
public class VirtualTimeManager{
	
	/**baseTimeからの経過時間(実時間,ms)*/
	@XmlAttribute
	public double elapsed = 0;

	/**基準時刻(仮想時刻)*/
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", locale="ja_JP")
	@XmlAttribute
	public Date baseTime = null;
	
	/**最終更新日時(実時刻)*/
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", locale="ja_JP")
	@XmlAttribute
	public Date lastUpdated = null;
	
	/**時間進行速度*/
	@XmlAttribute
	public double timescale = 1;
	
	ServiceLogger logger = ServiceLogger.getLogger();
	
	public boolean paused = false;
	
	/**現在時刻からのオフセットを基準に初期化*/
	public VirtualTimeManager(long offsetMillis, double timescale) {
		initialize(offsetMillis, timescale);
	}
	/**指定された時刻を起点に初期化*/
	public VirtualTimeManager(Date baseDate, double timescale) {
		initialize(baseDate.getTime() - (this.lastUpdated = new Date()).getTime(), timescale);
	}
	/**最終更新日時、または現在時刻を起点に初期化*/
	public VirtualTimeManager() {
		initialize(0, 1);
	}
	public void initialize(long offsetMillis, double timescale) {
		this.baseTime = new Date(new Date().getTime() + offsetMillis);
		this.timescale = timescale;
		this.lastUpdated = new Date();
	}
	
	public void start(long offsetMillis, double timescale) {
		initialize(offsetMillis, timescale);
		lastUpdated = new Date();
	}
	
	public boolean isPaused() {
		return this.paused;
	}

	/**
	 * ポーズする(時計を止める)
	 * <br>すでにポーズ中なら何もしない
	 * */
	synchronized public void pause() {
		if(this.paused) {
			logger.warn("this world is already paused.");
		}
		this.paused = true;
	}
	/**ポーズ状態を解除する
	 * <br>前回のポーズからの経過時間を時刻オフセット値に加える
	 * <br>ポーズ中でなければ何もしない
	 * */
	synchronized public void resume() {
		if(!this.paused) {
			logger.warn("this world is not paused.");
			return;
		}
		this.paused = false;
		lastUpdated = new Date();
	}
	
	/**指定された時刻を仮想時刻に変換して返す*/
	synchronized public Date getTime(Date date) {
		//ポーズ中はポーズされた時刻、そうでなければ現在時刻を起点に、指定された時刻との差分を加算
		
		if(paused) {
			return new Date((long)(this.baseTime.getTime() +  this.elapsed* this.timescale));
		}
		
		//現在の仮想時刻をまず出す
		Date now = new Date();
		long diff = now.getTime() - this.lastUpdated.getTime();//前回の状態更新からの経過時間
		this.elapsed += diff;//累積経過時間を更新
		double tm = this.baseTime.getTime() + elapsed*this.timescale;//現在の仮想時刻
		Date vnow =  new Date((long)tm);
		
		long indiff = date.getTime() - now.getTime();//引数の日時と現在の日時の差分; dateが現在時刻なら0になる
		
		this.lastUpdated = now;
		
		return new Date(vnow.getTime() + indiff);//仮想時刻に変換
		

	}
	
	
	
	/**現在時刻を仮想時刻に変換*/
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", locale="ja_JP")
	public Date getTime() {
		return this.getTime(new Date());
	}
	/**getTime()と同じ*/
	public Date now() {
		return getTime(new Date());
	}
	/**指定した時刻にミリ秒数を加算した時刻を返す*/
	public Date getTime(Date base, long add) {
		long tm = base.getTime() + add ;
		return new Date(tm);
	}
	/**指定した時刻(Date.getTime()と同等の通算ミリ秒)を仮想時刻に変換した時刻を返す*/
	public Date getTime(long base) {return getTime(new Date(base));}
	
	private static String format(Date date) {
		if(date == null) return "";
		return new SimpleDateFormat("MM/dd HH:mm:ss").format(date);	
	}
	
	@Override public String toString() {
		String ret = String.format("%s %s %s %f %f",  format(this.getTime()), 
				format(this.baseTime), format(this.lastUpdated), this.elapsed, this.timescale);
		return ret;
	}

	
	
	
	public String json() throws IOException{
		return new ObjectMapper().writeValueAsString(this);
	}
	
	public static void main(String[] args) {
		VirtualTimeManager vdm = new VirtualTimeManager(0, 1.5);
		new Timer().schedule(new TimerTask() {
			@Override
			public void run() {
				try {
				System.out.println(vdm.json());
				System.out.println("");
				}catch (Exception e) {
				}
			}
		}, 0, 1000);
		
		
		while(true) {
			try {
				int r = System.in.read();
				if(r == 'p') {
					vdm.pause();
				}else if(r == 'r') {
					vdm.resume();
				}
			}catch (Throwable t) {
				t.printStackTrace();
			}
		}
	}
	
	/** 仮想日時を保持する
	 * 
	 * 変換処理は生成元のVirtualDateManagerに移譲する
	 * それ以外はDateそのもの(生成元のVirtualDateManagerインスタンスにバインドするためにネストクラスにしている)
	 * */
	public class VirtualDate extends Date{
		private static final long serialVersionUID = 1L;
		private VirtualDate() {
			super();
			long now = VirtualTimeManager.this.now().getTime();
			super.setTime(now);
		}
//		/**実時刻に変換*/
//		public Date toRealTime() {
//			return VirtualDateManager.this.getRealTime(this);
//		}
	}

}
