package tsurumai.workflow.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import tsurumai.workflow.CacheControl;
import tsurumai.workflow.WorkflowException;
import tsurumai.workflow.WorkflowService;
import tsurumai.workflow.util.ServiceLogger;
import tsurumai.workflow.util.Util;
@JsonIgnoreProperties({"comment","","//","#"})
@JsonInclude(JsonInclude.Include.NON_NULL)

@XmlRootElement
public class StateData{
//	@XmlTransient
//	protected static String basedir;
//	public static void setDefaultDirectory(final String dir){
//		StateData.basedir = dir;
//	}
	protected static CacheControl<Collection<StateData>> cache = new CacheControl<>();
	public StateData(){}
	public StateData(String i){
		if(i != null){
			this.id=i;
			if(this.name == null)
				this.name="ステート(" + i.toString() +")";
		}
	}
	public interface SYSTEM_STATES{
		public static String START = "system.started";
		public static String PAUSE = "system.paused";
		public static String ABORT = "system.aborted";
	}
	
	/**ステートを一意に識別するID。*/
	@XmlAttribute
	public String id;
	/**ステートの表示名。*/
	@XmlAttribute
	public String name;
	@XmlAttribute
	/**ステートの説明。*/
	public String description;
	@XmlAttribute
	/**システムステートが設定された日時。*/
	public Date when;
	@Deprecated
	/**ステートの種類。(廃止)*/
	@XmlAttribute
	public int type = 0;
	/**固定(既定)のオブジェクト。*/
	@Deprecated
	@XmlAttribute
	public boolean fixed = false;
	
	/**演出効果。 href:相対URL、またはjavascript:スクリプトブロックを指定する。*/
	@Deprecated
	@XmlAttribute
	public String effect;
	/**カードのｈｔｍｌ要素にcssクラス名として展開される。カードにアイコンやスタイルを指定するために使用する。*/
	@XmlAttribute
	public String icon;

	/**@deprecated not used.*/
	public interface Type{
		/**通常*/
		public static final int NORMAL=0;
		/**フェーズ開始*/
		public static final int START_PHASE = 1;
		/**フェーズ終了*/
		public static final int END_PHASE = 2;
		/**フェーズ中断*/
		public static final int ABORT_PHASE = 3;
	}
	
//	protected static int CACHE_LIFETIME = 10000;
//	protected static Collection<StateData>  cached = null;
//	protected static Date cachedDate = new Date();
//	/**最後にロードされてCACHE_LIFETIME秒以内ならキャッシュ時刻を更新してfalseを返す、そうでないならtrueを返す*/
//	protected static boolean checkCache(){
//		if(cached  == null ||  cachedDate.getTime() + CACHE_LIFETIME < new Date().getTime()){
//			cachedDate = new Date();
//			return false;
//		}
//		return true;
//	}
	protected static String targetFile;
	public static Collection<StateData> loadAll() throws WorkflowException{
		if(cache.load(targetFile) != null) return cache.get();

		reload(targetFile);
		
		return cache.get();
	}
	public static Collection<StateData> loadAll(final String file) throws WorkflowException{
		try{
			
//			String path = basedir+ File.separator + "states.json";
			String contents = Util.readAll(file);

			StateData[] ret = new ObjectMapper().readValue(contents, StateData[].class);
			//
			return Arrays.asList(ret);
//			return (Collection<StateData>)cached;
		}catch(JsonProcessingException t){
			throw new WorkflowException("JSONデータの処理に失敗しました。"+file, HttpServletResponse.SC_NOT_FOUND, t);
		}catch(IOException t){
			throw new WorkflowException("シナリオデータの読み込みに失敗しました。", HttpServletResponse.SC_NOT_FOUND, t);
		}
	}
	public static StateData getStateData(String id){
		Collection<StateData> all = loadAll();
		for(Iterator<StateData> i = all.iterator(); i.hasNext();){
			
			StateData cur = i.next();
			if(id.equals(cur.id))return cur;
		}
		return null;
	}
	public static String getStateID(String id){
		Collection<StateData> all = loadAll();
		for(Iterator<StateData> i = all.iterator(); i.hasNext();){
			
			StateData cur = i.next();
			if(cur.id.contains(id))return cur.id;
		}
		return null;
	}
	protected static ServiceLogger logger = ServiceLogger.getLogger();

	public static void reload(final String file){
		cache.reload(targetFile = file);
		
		Collection<StateData>  def = Util.dataExists("sys/states.json") ?
				loadAll(WorkflowService.getContextRelativePath("sys/states.json") ) : new ArrayList<>();
		Collection<StateData> cust = loadAll(cache.getTargetFile());//DefaultDirectory());
		Collection<StateData> all = new ArrayList<>();
		all.addAll(def);
		all.addAll(cust);

		cache.set(all);
		logger.info("ステートデータを再ロードしました。" + file); 
	}
	public String toString(){
		return this.name != null ? this.name : "null" + "("+String.valueOf(id) + ")";
	}
}
