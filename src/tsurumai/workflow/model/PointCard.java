package tsurumai.workflow.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;

import javax.xml.bind.annotation.XmlRootElement;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import tsurumai.workflow.CacheControl;
import tsurumai.workflow.WorkflowException;
import tsurumai.workflow.WorkflowService;
import tsurumai.workflow.util.ServiceLogger;
import tsurumai.workflow.util.Util;
@JsonIgnoreProperties({"comment","","//","#"})
//@JsonInclude(JsonInclude.Include.NON_NULL)

/**演習中に参加者のアクションに応じて付与されるポイントを表現します。*/
@XmlRootElement
public class PointCard {
	/**対象とするフェーズ番号。省略値:-1(すべてのフェーズ)*/
	public int phase = -1;
	/**説明。*/
	public String description ="";
	/**フェーズ開始からの経過時間(秒)の上限。省略値:0(常に真となる)*/
	public int before = 0;
	/**フェーズ開始からの経過時間(秒)の下限。省略値:0(常に真となる)*/
	public int after = 0;
	/**システムステート条件。省略値:なし(システムステートによらず常に真となる)*/
	public String state;
	/**システムステート前提条件(AND条件)。省略値:無制限*/
	public String[] statecondition;
	/**許可する重複回数。省略値:1*/
	public int multiplicity = 1;
	/**得点(マイナスは減点)。省略値:0。*/
	public int point = 0;
	/**名前*/
	public String name = "";
	/**ポイントカードの識別子。*/
	public String id;
	
//	protected static String basedir = "data";
//	
//	public static ArrayList<PointCard> cards;
//	public static void setBaseDir(final String base){
//		PointCard.basedir = base;
//	}
//	
	
	protected static CacheControl<PointCard[]> cache = new CacheControl<>();
	protected static ServiceLogger logger = ServiceLogger.getLogger();
	public static void reload(final String path){
		cache.reload(targetFile=path);
		logger.info("ポイントデータを再ロードしました。" + path);
	}
	protected static String targetFile;
	public static synchronized PointCard[] load() throws WorkflowException{
		PointCard[] def = Util.dataExists("sys/points.json") ?
				loadAll(WorkflowService.getContextRelativePath("sys/points.json")) : new PointCard[0];
		PointCard[] cust = loadAll(targetFile);
		List<PointCard> all = new ArrayList<>();
		all.addAll(Arrays.asList(def));
		all.addAll(Arrays.asList(cust));
		return all.toArray(new PointCard[all.size()]);
	}
	public static synchronized PointCard[] loadAll(final String file) throws WorkflowException{
//		return load(null);
//	}
//
//	/**ポイント定義をロードする*/
//	public static synchronized PointCard[] load(final String basedir) throws WorkflowException{
		try{
//			PointCard.setBaseDir(basedir != null ? basedir : PointCard.basedir);
 			if(cache.load(file) != null)return cache.get();
			String contents = Util.readAll(cache.getTargetFile());
			ArrayList<PointCard> ret = new ArrayList<>();
			JSONArray arr  = new JSONObject(contents).getJSONArray("pointcard");
			for(java.util.Iterator<Object> i = arr.iterator();i.hasNext();){
				Object o = i.next();
				PointCard cur = (PointCard)new ObjectMapper().readValue(o.toString(), PointCard.class);
				ret.add(cur);
			}
			Collections.sort(ret, new Comparator<PointCard>() {
				@Override
				public int compare(PointCard o1, PointCard o2) {
					if(o1.phase == o2.phase)return 0;
					else if(o1.phase > o2.phase)return 1;
					else	return -1;
				}
			});
//			PointCard.cards = ret;
//			return PointCard.cards.toArray(new PointCard[ret.size()]);
			return cache.set(ret.toArray(new PointCard[ret.size()]));
		}catch(Throwable t){
			ServiceLogger.getLogger().warn("ポイントカードの初期化に失敗しました。ポイント機能は無効化されます。");
			return new PointCard[0];
		}
	}
	/**指定されたフェーズのポイントカードを返す。
	 * ＠param phasenum 取得するフェーズ。 -1を指定するとすべてのフェーズ。
	 * */
	public static PointCard[] list(int phasenum){
//		if(PointCard.cards == null || PointCard.cards.size() == 0){
//			load();
//		}
//		
//		cache.get();
		Collection<PointCard> ret = new Vector<>();
		if(cache.get() == null || cache.get().length == 0)
			load();
		if(cache == null || cache.get() == null)return new PointCard[0];
		for(PointCard c : cache.get()){
			if(c.phase == -1 || c.phase == phasenum || phasenum == -1){
				ret.add(c);
			}
		}
	
		return ret.toArray(new PointCard[ret.size()]);
	}
	@Override
	public String toString(){
		try{
		String str = new ObjectMapper().writeValueAsString(this);
		return str;
		}catch(Throwable t){
			return "serialization failed:" + ((Object)this).toString();
		}
	}
}
