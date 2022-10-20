package tsurumai.workflow.vtime;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EventListener;
import java.util.Hashtable;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;

import tsurumai.workflow.NotificationMessage;
import tsurumai.workflow.Notifier;
import tsurumai.workflow.util.ServiceLogger;

/**
仮想時刻を持つスペースを表現する
*/
@XmlRootElement
@JsonPropertyOrder({"name", "time", "offset", "paused", "lastUpdated"})
public class World {
	@XmlElement
	VirtualTimeManager clock;
	public VirtualTimeManager getClock() {return this.clock;}
	@XmlElement
	Scheduler sch;
	@XmlAttribute
	public String name = "[not initialized]";
	static protected ServiceLogger logger = ServiceLogger.getLogger();
	@Override
	public String toString() {
		String ret = this.name + " - " + 
				clock == null ? "[not initialized]" : new SimpleDateFormat("MM/dd HH:mm").format(this.getTime());
		return ret;
	}
	/**組み込みステート: 一時停止*/
	public static final String EVENT_PAUSED = "system.paused";
	/**組み込みステート: 再開*/
	public static final String EVENT_RESUMED = "system.resumed";
	/**組み込みステート: リロード*/
	public static final String EVENT_RELOADED = "system.reloaded";
	
	public World(String name) {
		this.name = name;
	}
	/**指定された日時を起点に初期化
	 * @param base 起点となる時刻。nullなら現在時刻
	 * @param timescale タイムスケール。初期化済なら初期化時の値、未初期化なら指定された値、-1なら1.0を設定する
	 * */
	public World initialize(Date base, double timescale) {
		logger.enter(base, timescale);
		double ts = this.clock != null && this.clock.timescale >= 0 ? this.clock.timescale : 1.0;
		this.clock = new VirtualTimeManager(base == null ? new Date() : base, ts);
		this.sch = new Scheduler().initialize(this.clock).start();
		worlds.put(this.name, this);
		logger.info("World initialized: "+  this.toString());
		return this;
	}
	public World initialize(Date base) {
		return initialize(base, -1);
	}
	public World initialize() {
		return initialize(new Date(), -1);
	}
	@XmlAttribute
	//@JsonSerialize(as = Date.class)
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
	//TODO: jaxrs-ri-2.24ではJsonFormatは無視され、不正な(パースできない)フォーマットの文字列が返される。
	//というかjacksonが呼ばれていないのか。
	/**ワールドの現在時刻を返す
	 * 初期化されていない場合は基準時刻(Date.setTime(0))を返す
	 * */
	public Date getTime(long base){
		if(this.clock == null) {
			Date ret =  new Date(base);ret.setTime(0);return ret;
		}
		Date vnow = clock.getTime(new Date(base));
		return vnow;
	}
	/**現在実時刻をワールド時刻に変換した値を返す
	 * 初期化されていない場合は基準時刻(Date.setTime(0))を返す
	 * */
	public Date getTime(){
		return this.getTime(new Date().getTime());
	}
	

	
//	@XmlAttribute(name="timeOffset")
//	public long getTimeOffset() {
//		if(this.vdm == null)return -1;
//		return this.vdm.offset;
//	}
//	
//	@XmlAttribute(name="lastPaused")
//	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
//	//同上。
//	public Date getLastPaused() {
//		if(this.vdm == null)return null;
//		return this.vdm.lastPaused;
//	}
//	@XmlAttribute(name="paused")
//	public boolean isPaused() {
//		return this.getLastPaused() != null;
//	}
	@XmlAttribute
	public double getTimescale() {
		return this.clock != null ? this.clock.timescale : -1;
	}
	static Hashtable<String, World> worlds = new Hashtable<>();
	public static World resolve(String name) {
		logger.enter(name);
		if("all".equals(name))	return null;
		
		if(worlds.containsKey(name))return worlds.get(name);

		return null;
	}
	/**初期化済のすべてのWorldインスタンスを返す*/
	public static World[] getWorlds() {
		logger.enter();
		return worlds.values().toArray(new World[worlds.values().size()]);
	}
	/**Worldデータをロードして初期化する。初期化パラメタはインスタンスの状態に従う*/
	public static void load(World w) {
		logger.enter(w);
		World world = resolve(w.name);
		if(world != null) {
			logger.warn("World instance deleted: " + w.name);
			world.stop();
		}
		worlds.put(w.name, w.initialize());
	}
	public static void loadWorlds(World[] w) {
		logger.enter(w);
		clear();
		for(World world : w) {
			load(world);
		}
	}
	public static void clear() {
		logger.enter();
		for(World world : worlds.values()) {
			world.stop();
		}
		worlds.clear();
	}
	public static World[] initializeAll(Date base, double timescale) {
		logger.enter(base, timescale);
		worlds.values().forEach(w->{
			w.initialize(base, timescale);
		});
		return worlds.values().toArray(new World[worlds.size()]);
	}
	
	
	/**ワールドの時間を停止*/
	public Date pause() {
		if(this.clock == null) {
			logger.error("world clock not initialized.");
			return null;
		}
		
		clock.pause();
		try {
			Notifier.sendSystemNotification(this.name, "演習が一時停止されました。", NotificationMessage.LEVEL_CONTROL, EVENT_PAUSED);
		}catch (IOException e) {
			logger.error("failed to send system notification: " + e.getMessage());
		}
		
		return this.clock.now();
	}
	/**ワールドの時間を再開*/
	public Date resume() {
		if(this.clock == null) {
			logger.error("world clock not initialized.");
			return null;
		}
		clock.resume();
		try {
			Notifier.sendSystemNotification(this.name, "演習が再開されました。", NotificationMessage.LEVEL_CONTROL, EVENT_RESUMED);
		}catch (IOException e) {
			logger.error("failed to send system notification: " + e.getMessage());
		}
		return this.clock.now();
	}
	public void stop() {
		this.getScheduler().stop();
	}
	public boolean isPaused() {
		if(this.clock == null) {
			logger.error("world clock not initialized.");
			return false;
		}
		return this.clock.isPaused();
	}
	/**ワールドのタイムスケールを設定*/
	public World setTimescale(double scale) {
		if(this.clock == null) {
			logger.error("world clock not initialized.");
			return this;
		}
		clock.timescale = scale;
		return this;
	}
	public Scheduler getScheduler() {
		return this.sch;
	}
	
	

	//<!-- not implemented
	public static interface WORLD_EVENT{
		public static final String TICK = "tick";
		public static final String PAUSED = "paused";
		public static final String RESUMED = "resumed";
	}
	Hashtable<String, CopyOnWriteArrayList<WorldEventListener>> listeners = new Hashtable<String, CopyOnWriteArrayList<WorldEventListener>>();
	public interface WorldEventListener extends EventListener{
		public boolean onEvent(Object eventData, World src);
	}
	public void addEventListener(String type, WorldEventListener listener) {
		if(!listeners.get(type).contains(listener))
			listeners.get(type).add(listener);
	}
	public void registerEvent(String type, WorldEventListener listener) {
		if(!listeners.contains(type))
			this.listeners.put(type, new CopyOnWriteArrayList<World.WorldEventListener>());
		this.listeners.get(type).add(listener);
	}
	public void raiseEvent(String type, Object eventData) {
		for(WorldEventListener l : this.listeners.get(type)) {
			if(l.onEvent(eventData, this))break;
		}
	}
	void registerDefaultEvents() {
		this.registerEvent(WORLD_EVENT.TICK, new WorldEventListener() {
			@Override public boolean onEvent(Object eventData, World src) {
				// TODO Auto-generated method stub
				return false;
			}
		});
		this.registerEvent(WORLD_EVENT.PAUSED, new WorldEventListener() {
			@Override public boolean onEvent(Object eventData, World src) {
				// TODO Auto-generated method stub
				return false;
			}
		});
		this.registerEvent(WORLD_EVENT.RESUMED, new WorldEventListener() {
			@Override public boolean onEvent(Object eventData, World src) {
				// TODO Auto-generated method stub
				return false;
			}
		});
	}
	
	//------------------>
	
	
	
	public static void main(String[] args) {
		Date now = new Date();
		World[] worlds = new World[] {new World("w1").initialize(now), new World("w2").initialize(now, 2)};
		
		while(true) {
				try {
					Thread.sleep(1000);
					for(World w : worlds) {
						String s = new ObjectMapper().writeValueAsString(w);
						System.out.println(s);
					
					}
				}catch (InterruptedException e) {
					e.printStackTrace();
				}catch (Throwable t) {
					t.printStackTrace();
				}
				
			}
		}
	
}
