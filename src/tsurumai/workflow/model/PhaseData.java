package tsurumai.workflow.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import tsurumai.workflow.CacheControl;
import tsurumai.workflow.WorkflowException;
import tsurumai.workflow.WorkflowService;
import tsurumai.workflow.util.ServiceLogger;
import tsurumai.workflow.util.Util;
@XmlRootElement
@JsonIgnoreProperties({"comment","","//","/*","*/","#","<!--","-->"})
//@JsonInclude(JsonInclude.Include.NON_NULL)
/**演習フェーズの定義を表現します。settings.jsonのオブジェクト表現です。*/
public class PhaseData {
	
	/**1から始まるフェーズ番号。現在のバージョンでは1で固定。*/
	@XmlAttribute
	public int phase;
	/**フェーズの説明。*/
	@XmlAttribute
	public String description;
	/**このフェーズの制限時間。表示にのみ使用される。*/
	@XmlAttribute
	public int timelimit = 1200;
	/**終了状態を示すステートカードのID。表示にのみ使用される。*/
	@XmlAttribute
	public int[] endstate;

//	/**シナリオデータの著作権情報。*/
//	@XmlAttribute
//	public String copyright;
//	/**シナリオデータのバージョン情報。*/
//	@XmlAttribute
//	public String version;
//	/**シナリオデータの作者。*/
//	@XmlAttribute
//	public String author;
//	/**シナリオデータの作成日時。*/
//	@XmlAttribute
//	public String created;
//	/**シナリオデータの更新日時。*/
//	@XmlAttribute
//	public String updated;
//	/**フェーズ名。*/
//	@XmlAttribute
//	public String name;
//
//	/**有効にする追加機能名。*/
//	@XmlAttribute
//	public String[] features;

	protected static CacheControl<List<PhaseData>> cache = new CacheControl<>();
	protected static ServiceLogger logger = ServiceLogger.getLogger();

	
	public static void reload(final String file){
		cache.reload(file);
		logger.info("フェーズデータを再ロードしました。"+ file);
	}
	public static synchronized List<PhaseData> loadAll() throws WorkflowException{

		List<PhaseData> def =Util.dataExists("sys/setting.json") ?
				load(WorkflowService.getContextRelativePath("sys/setting.json") ) : new ArrayList<>();
		List<PhaseData> cust = load(cache.getTargetFile());
		List<PhaseData> all = new ArrayList<>();
		all.addAll(def);
		all.addAll(cust);
		return all;
	}
	/**フェーズ定義をロードする*/
	public static synchronized List<PhaseData> load(final String path) throws WorkflowException{
		try{
			cache.reload(path);
			String contents = Util.readAll(path);
			ArrayList<PhaseData> ret = new ArrayList<>();
			JSONArray arr  = new JSONObject(contents).getJSONArray("phases");
			for(java.util.Iterator<Object> i = arr.iterator();i.hasNext();){
				Object o = i.next();
				PhaseData cur = (PhaseData)new ObjectMapper().readValue(o.toString(), PhaseData.class);
				ret.add(cur);
			}
			Collections.sort(ret, new Comparator<PhaseData>() {
				@Override
				public int compare(PhaseData o1, PhaseData o2) {
					if(o1.phase == o2.phase)return 0;
					else if(o1.phase > o2.phase)return 1;
					else	return -1;
				}
			});
			cache.set(ret);
			return ret;
			//return ret.toArray(new PhaseData[ret.size()]);
		}catch(Throwable t){
			throw new WorkflowException("フェーズの初期化に失敗しました。" , t);
		}
	}


}
