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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import tsurumai.workflow.WorkflowException;
import tsurumai.workflow.WorkflowService;
import tsurumai.workflow.util.ServiceLogger;
import tsurumai.workflow.util.Util;

final class Team{
	public Team() {}
	public String id;
	public String name;
}
@JsonIgnoreProperties({"comment","","//","#"})
public class ScenarioData {
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
	
	@XmlElement
	public Team[] teams;
	
	@XmlElement
	public PhaseData[] phases;	
	
	
	@XmlAttribute
	@JsonProperty("invisible-action")
	public String[] invisibleAction;
	

	/**有効にする追加機能名。*/
	@XmlAttribute
	public String[] features;
	
	protected static ServiceLogger logger = ServiceLogger.getLogger();

	protected static Hashtable<String, ScenarioData> scenarios = new Hashtable<>();


	public static synchronized Hashtable<String, ScenarioData> loadAll(boolean reload) throws WorkflowException{

 		if(!reload && !scenarios.isEmpty())
			return scenarios;
		
		Hashtable<String, ScenarioData> all = new Hashtable<>();

//		ScenarioData def = Util.dataExists("sys/setting.json") ?
//				loadData(WorkflowService.getContextRelativePath("sys/setting.json")) : null;
//TODO:merge?
		
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
		String[] dirlist = Util.concat(dirs, new String[] {"."});//デフォルトのシナリオデータも加える(必要あるか?)
		if(dirs == null)
			throw new WorkflowException("シナリオデータがロードできません。", HttpServletResponse.SC_NOT_FOUND);
		
		for(String f : dirlist) {
			try {
				File path = new File(scenarioBase + File.separator + "setting.json");
				
				if(isUpdated(scenarioBase)) {
					//TODO: implements caching
				}
				
				
				String contents = Util.loadTextFile(path.getAbsolutePath());
				ScenarioData dat = new ObjectMapper().readValue(contents.getBytes(), ScenarioData.class);
				all.put(f.equals(".") ? "_default_" : f, dat);
			}catch(IOException t) {
				logger.error("failed to load scenario data: " + f, t);
			}
		}
		return scenarios = all;
	}

	
	/**シナリオ設定をロードする*/
	public static synchronized ScenarioData loadData(final String name) throws WorkflowException{
		return loadData(name);
	}
	/**指定された名前のシナリオをロードする。
	 * 名前が空または_defauilt_は既定のシナリオを示す。
	 * */
	public static ScenarioData load(String scenarioName, boolean reload) {
		Hashtable<String, ScenarioData> data = loadAll(reload);
		String key  = scenarioName == null || scenarioName.length() ==  0 ? "_default_" : scenarioName; 
		return data.get(key);
	}
	public static ScenarioData load(String scenarioName) {
		return load(scenarioName, false);
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
