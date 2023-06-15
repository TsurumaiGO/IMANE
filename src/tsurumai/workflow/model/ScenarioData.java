package tsurumai.workflow.model;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import tsurumai.workflow.WorkflowException;
import tsurumai.workflow.WorkflowService;
import tsurumai.workflow.util.ServiceLogger;
import tsurumai.workflow.util.Util;

final class Team{
	public Team() {}
	public String id = "";
	public String name = "";
	public String icon = "";
}
final class Group{
	public Group() {}
	public String id = "";
	public String name = "";
	public String icon = "";
	public boolean isDefault = true;
}

@JsonIgnoreProperties(value={"comment","","//","#"}, ignoreUnknown=true)
@XmlRootElement
public class ScenarioData implements Cloneable{
	/**シナリオデータの著作権情報。*/
	@XmlAttribute
	public String copyright;
	/**シナリオデータのバージョン情報。*/
	@XmlAttribute
	public String version;
	/**シナリオデータの作者。*/
	@XmlAttribute
	public String author;
	/**シナリオデータの作成日時。*/
	@XmlAttribute
	public String created;
	/**シナリオデータの更新日時。*/
	@XmlAttribute
	public String updated;
	/**シナリオ名。*/
	@XmlAttribute
	public String name;
	/**チームのリスト*/
	@XmlElement
	public Team[] teams;
	/**グループのリスト
	 * experimental
	 * */
	@XmlElement
	public Group[] groups;
	/**フェーズのリスト*/
	@XmlElement
	public PhaseData[] phases;	
	
	protected String path;
	
//	@XmlElement
//	public int NumberFW_I ;
//	public int NumberFW_0;
//	public int NumberVPN;
//	public int NumberREMOTEACCESS;
	
	@XmlElement
	public String[] evaluators;
	@XmlAttribute
	@JsonProperty("invisible-action")
	public String[] invisibleAction;

	/**有効にする追加機能名。*/
	@XmlAttribute
	public String[] features;
	
	/**デフォルトのシナリオ(シナリオ格納ディレクトリ直下にシナリオデータがある)ならtrue*/
	@XmlAttribute
	public boolean isDefault = false;
	/**シナリオ固有の拡張プロパティ*/
	@XmlAttribute
	public Hashtable<String, Object> params;

	/**このシナリオがアクティブな場合はtrue。実行時のみ有効。*/
	@XmlAttribute
	public boolean active = false;
	public static String activeScenario = "_default_";
	public static String getActiveScenarioName() {return activeScenario;}
	public static ScenarioData getActiveScenario() {
		Hashtable<String, ScenarioData> scenarios = ScenarioData.loadAll(false);
		return scenarios.get(activeScenario);
	}
	public static void activateScenario(final String scenarioName) {
		Hashtable<String, ScenarioData> scenarios = ScenarioData.loadAll(false);
		scenarios.keys().asIterator().forEachRemaining((k->{
			ScenarioData cur = scenarios.get(k);
			if(cur == null) throw new WorkflowException("シナリオデータが見つかりません。"+scenarioName, new Throwable());
			if(k.equals(scenarioName)) {
				cur.active = true;
				activeScenario = scenarioName;
				logger.info("シナリオデータがアクティブ化されました。"+scenarioName);
			}else {
				cur.active = false;
			}
		}));
	}
	
	
	/**このシナリオの検証結果*/
	@XmlAttribute
	public ValidationResultSet validationResult = null;
	
	protected static ServiceLogger logger = ServiceLogger.getLogger();

	protected static Hashtable<String, ScenarioData> scenarios = new Hashtable<>();

	
	/**
	 * すべてのシナリオデータをロードする。
	 * [コンテキストルート]/data/直下はデフォルトシナリオ(シナリオ名"_default_")、[コンテキストルート]/data/[シナリオ名]はそれ以外のシナリオデータを保持する。
	 * ロードしたデータは静的クラスフィールドに保持し、以降再利用する。
	 * 
	 * @return Hashtable<シナリオ名,シナリオデータ>
	 * */
	public static synchronized Hashtable<String, ScenarioData> loadAll(boolean reload) throws WorkflowException{

 		if(!reload && !scenarios.isEmpty())
			return scenarios;
		
		Hashtable<String, ScenarioData> all = new Hashtable<>();
		String scenarioBase = WorkflowService.getContextRelativePath("data");
		
		String[] dirs = new File(WorkflowService.getContextRelativePath("data")).list(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				boolean isdir = new File(dir.getAbsolutePath() + File.separator + name).isDirectory();
				String confFilePath = dir.getAbsolutePath() + File.separator + name + File.separator + "setting.json";
				boolean settingExists =  new File(confFilePath).exists();
				return isdir && settingExists;
			}
		});
		String[] dirlist = Util.concat(dirs, new String[] {"."});//デフォルトのシナリオデータも加える
		if(dirs == null)
			throw new WorkflowException("シナリオデータがロードできません。", HttpServletResponse.SC_NOT_FOUND);
		for(String f : dirlist) {
			String path = scenarioBase + File.separator + f;
			if(!isUpdated(path)) {
				logger.trace(path + " not updated.");
				continue;
			}
			ScenarioData dat = loadData(path);

			//TODO:比較結果が正しくない
			java.nio.file.Path s1 = java.nio.file.Path.of(path).normalize();
			java.nio.file.Path s2 = java.nio.file.Path.of(scenarioBase).normalize();
			
			if(java.nio.file.Path.of(path).normalize().equals(java.nio.file.Path.of(scenarioBase).normalize()))
				dat.isDefault = true;
			//all.put(f.equals(".") ? "_default_" : f, dat);
			all.put(dat.isDefault ? "_default_" : f, dat);
				
			timestamps.put(f, new Date());
		}
		return scenarios = all;
	}

	@Override
	protected Object clone() throws CloneNotSupportedException {
		try{
			String str = Util.getObjectMapper().writeValueAsString(this);
			Object o = Util.getObjectMapper().readValue(str, ScenarioData.class);
			return o;
			
		}catch (JsonProcessingException e) {
			logger.error("failed to deserialize object.", e);
		}
		return super.clone();
	}
	/**設定項目を上書きする。
	 * srcから非nullのフィールドのみをthisに上書きする。*/
	public ScenarioData override(ScenarioData src) {
		if(src == null)	return this;
		
		if(src.author != null)	this.author = src.author;
		if(src.created != null)	this.created = src.created;
		if(src.copyright != null)	this.copyright = src.copyright;
		if(src.features != null)	this.features = src.features;
		if(src.invisibleAction != null)	this.invisibleAction = src.invisibleAction;
		if(src.name != null)	this.name = src.name;
		if(src.groups != null)	this.groups = src.groups;
		if(src.teams != null)	this.teams = src.teams;
		if(src.updated != null)	this.updated = src.updated;
		if(src.version != null)	this.version = src.version;
		return this;
	}
	/**シナリオ設定をロードしインスタンスを生成する。
	 * @param pathname シナリオデータが格納されたディレクトリのwebアプリケーション相対パス。nullの場合はデフォルト。*/
	static synchronized ScenarioData loadData(final String pathname) throws WorkflowException{
		try {
			String src = WorkflowService.getContextRelativePath(pathname == null ? "data" : pathname); 
			String contents = Util.loadTextFile(src + File.separator + "setting.json");
			ScenarioData dat = new ObjectMapper().readValue(contents.getBytes(), ScenarioData.class);
			return dat;
		}catch (IOException e) {
			throw new WorkflowException("failed to load scenario data " + pathname, e);
 		}
	}
	/**シナリオ設定をロードし、システム設定をマージしてインスタンスを生成する。
	 * */
	public static ScenarioData load(String pathname) {
		ScenarioData dat =  loadData(pathname);
		ScenarioData def =  loadData("sys");
		ScenarioData ret =  dat.override(def);
		ret.path = pathname+ File.separator + "setting.json";

		return ret;
		
	}
	@Override
	public String toString() {
		try {
			String ret = Util.getObjectMapper().writeValueAsString(this);
			return ret;
		}catch (IOException e) {
			return "json parsing error: "+super.toString();
		}
	}
	
	/**指定された機能が有効ならtrueを返す*/
	public boolean isFeatureEnabled(String feature) {
		if(this.features != null)
			return Util.contains(this.features, feature);
		return false;
	}
	
	public static void main(String[] args) {
		
		while(true) {
			Hashtable<String, ScenarioData> data = ScenarioData.loadAll(true);
			for(Enumeration<?> keys = data.keys(); keys.hasMoreElements();) {
				ScenarioData d = data.get(keys.nextElement());
				System.out.println(d);
				
				try {
					Thread.sleep(1000);
				}catch (Throwable e) {}
			
			}
		}
	}
	
	/**シナリオセットの更新日時を示すハッシュ。
	 * key=格納先相対ディレクトリ、value=最終ロード日時*/
	static ConcurrentHashMap<String, Date> timestamps =  new ConcurrentHashMap<String, Date>();

	/**指定されたディレクトリが前回呼び出し時以降更新されているかどうか*/
	public static boolean isUpdated(String relpath) {
		String key  = relpath == null || relpath.length() ==  0 ? "" : relpath;
		String path = WorkflowService.getContextRelativePath(relpath);

		if(!timestamps.containsKey(key))return true;
		Date d = timestamps.get(key);
		Date prev = getLastUpdated(path);
		return d.after(prev);
	}
	/***指定されたディレクトリにある最新のファイルのダイムスタンプを返す*/
	public static Date getLastUpdated(String basedir){
		Date newest = null;
		for(File f : new File(basedir).listFiles()) {
			Date ts = new Date(f.lastModified());
			if(ts.after(newest)) {
				newest = ts;
			}
		}
		return newest;
	}
}
	

	
	
	
	
