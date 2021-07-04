package tsurumai.workflow.model;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import tsurumai.workflow.CacheControl;
import tsurumai.workflow.WorkflowService;
import tsurumai.workflow.util.ServiceLogger;
import tsurumai.workflow.util.Util;
import tsurumai.workflow.WorkflowException;
/**アクションカードを表現します。
 * */
@JsonIgnoreProperties({"comment","","//","#"})
//@JsonInclude(JsonInclude.Include.NON_NULL)
@XmlRootElement
public class CardData implements Cloneable{
	
	/**アクションの種類。有効な値は{@link Types}のフィールドで定義されます。*/
	@XmlAttribute
	public String type;
	/**アクションの表示用の名前。*/
	@XmlAttribute
	public String name;
	/**アクションの種類がautoのとき、アクションの宛先。*/
	@XmlAttribute
	public String to;
	/**アクションの種類がautoのとき、アクションの送信元。*/
	@XmlAttribute
	public String from;

	/**アクションの種類がautoのとき、アクションの同報先。*/
	@XmlAttribute
	public String[] cc;
	
	/**アクションに付加される既定のメッセージ文字列。*/
	@XmlAttribute
	public String message;
	/**アクションの説明。*/
	@XmlAttribute
	public String description;
	
	//public String[] cc;
	
	/**固定(既定)のオブジェクトであることを示します。*/
	@XmlAttribute
	public boolean fixed = false;
//	@XmlAttribute
//	public Date sentDate;
//	@XmlAttribute
//	public Date visibleDate;
//	@XmlAttribute
//	public int level;
	/**シナリオセットにおいてアクションを一意に識別する文字列。*/
	@XmlAttribute
	public String id;
	/**アクションを実行可能なロールのID。*/
	@XmlElement
	public String[] roles;
	/**アクションの宛先として指定可能なロールのID。*/
	@Deprecated //恐らく未使用
	@XmlAttribute
	public String[] assignTo;
	/**デフォルトのコメント。*/
	@XmlAttribute
	public String comment;
	/**未使用*/
	@Deprecated
	@XmlAttribute
	public String[] result;
	/**アクションを使用可能なフェーズ*/
	@XmlAttribute
	public int[] phase;
	/**アクションが実行可能となる条件*/
	@Deprecated
	@XmlAttribute
	public String[] prerequisite;
	/**アクションに任意のステートカードを添付可能かどうか*/
	@XmlAttribute
	public boolean share;
	
	@Deprecated
	@XmlElement
	public Map<String, Object>timecondition;

	/**追加するステートのID*/
	@XmlElement
	public String[] addstate;
	/**削除するステートのID*/
	@XmlElement
	public String[] removestate;
	/**未使用*/
	@Deprecated
	@XmlElement
	public Map<String, String> extension;
	/**添付するステートカードのID。*/
	@XmlElement
	public String[] attachments;
	/**?*/
	@XmlAttribute
	public String replyTo;
//	@XmlAttribute
//	public String replyOption;
//	@XmlAttribute
//	public int responseTime;
//	@XmlAttribute
//	public int cost;
	/**キュー内での現在の評価順。*/
	@XmlAttribute
	public int currentorder = 0;
	/**アクションの実行条件となるステート。*/
	@XmlAttribute
	public String[] statecondition;
	/**trueなら演習画面に表示されない。内部的なフロー制御を行うためにアクションに使用する。*/
	@XmlAttribute
	public boolean hidden = false;
	/**アクションの実行条件となるシステムステートのID。*/
	@XmlAttribute
	public String[] systemstatecondition;
	/**自動アクションの場合、実行条件が成立してから実際に実行されるまでの待ち時間(秒)。*/
	@XmlAttribute
	public int delay = 5;
	/**カードのｈｔｍｌ要素にcssクラス名として展開される。カードにアイコンやスタイルを指定するために使用する。*/
	@XmlAttribute
	public String icon;
	
	/**新規アクションで宛先に選択可能な宛先ロールのリスト。自動アクションの場合は無効。*/
	@XmlAttribute
	public String[] recipients;
	
	/**トリガーイベントにステートカードを添付するときに使用する。*/
	@XmlAttribute
	public String[] statecards;

	/**中止系アクションのときに、中止するアクションのIDを指定。
	 * @deprecated
	 * */
	@XmlAttribute
	public String abortaction;
	
	/**このアクションにステートカードの添付を許可するかどうかを示す。*/
	@XmlAttribute
	public boolean attach = false;

	
	/**デバッグ用*/
	@XmlAttribute public boolean breakpoint = false;
	
	public enum Types{
		action,
		talk,
		share,
		notification,
		auto,
		alert,
		control
	};

	public boolean is(Types t) {
		try{
			return this.type.equalsIgnoreCase(t.toString());
		}catch (Throwable e) {return false;}
	}
	protected static ServiceLogger logger = ServiceLogger.getLogger();
	
	/**すべてのアクション定義を再ロードする*/
	public static void reload(final String file){
		cache.reload(targetFile = file);
		
		Map<String, Collection<CardData>> def = //loadAll( WorkflowService.getContextRelativePath("sys"));
				Util.dataExists("sys/actions.json") ?
				loadAll(WorkflowService.getContextRelativePath("sys/actions.json"))
				: new HashMap<>();
		Map<String, Collection<CardData>> cust = loadAll(cache.getTargetFile());
		String[] types1 = def.keySet().toArray(new String[def.keySet().size()]);
		String[] types2 = cust.keySet().toArray(new String[cust.keySet().size()]);
		Collection<String> types = Util.merge(types1,  types2);
		Map<String, Collection<CardData>> all = new HashMap<>();

		for(String t : types){
			Collection<CardData> vals = Util.merge(cust.get(t), def.get(t));
			all.put(t, vals);
		}
		logger.debug(String.format("アクションカード: 合計%d; カスタム%d; 既定%d", all.size() , cust.size(), def.size()));

		cache.set(all);

		logger.info("アクションデータを再ロードしました。" + file);

	}
	/**現在アクティブなアクション定義を保持する。*/
	protected static CacheControl<Map<String, Collection<CardData>>> cache = new CacheControl<>();
	
	
	
	
	public CardData(){}
	protected String generateId(){
		String rand = UUID.randomUUID().toString();
		return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new Date()) + rand;
	}
	
	/**現在アクティブなシナリオのすべてのアクションを返します。
	 * */
	public static Collection<CardData> all(){
		Map<String, Collection<CardData>> list = loadAll();
		Collection<CardData> ret = new ArrayList<>();
		if(list == null)return ret;

		for(Collection<CardData> d : list.values()){
			ret.addAll(d);
		}
		return ret;
	}
	protected static String targetFile;
	/**
	 * 現在アクティブなシナリオのすべてのアクションを返します。
	 * @return typeとアクションのリストのマップ*/
	public synchronized static Map<String, Collection<CardData>>  loadAll() throws WorkflowException{

		if(cache.load(targetFile) != null) return cache.get();
//		Map<String, Collection<CardData>> def = //loadAll( WorkflowService.getContextRelativePath("sys"));
//				Util.dataExists("sys/actions.json") ?
//				loadAll(WorkflowService.getContextRelativePath("sys/actions.json"))
//				: new HashMap<>();
//		Map<String, Collection<CardData>> cust = loadAll(cache.getTargetFile());
//		String[] types1 = def.keySet().toArray(new String[def.keySet().size()]);
//		String[] types2 = cust.keySet().toArray(new String[cust.keySet().size()]);
//		Collection<String> types = Util.merge(types1,  types2);
//		Map<String, Collection<CardData>> all = new HashMap<>();
//
//		for(String t : types){
//			Collection<CardData> vals = Util.merge(cust.get(t), def.get(t));
//			all.put(t, vals);
//		}
//		logger.debug(String.format("アクションカード: 合計%d; カスタム%d; 既定%d", all.size() , cust.size(), def.size()));
//
//		cache.set(all);
		reload(targetFile);return cache.get();
//		return all;
		
	}
	
	/***
	 * 	指定された場所にあるシナリオのすべてのアクションを返します。
	 * @param path シナリオ格納ファイル
	 * @return  Map(Type, Collection(CardData))*/
	public synchronized static Map<String, Collection<CardData>> loadAll(final String path) throws WorkflowException{
		try{
			Map<String, Collection<CardData>> ret = new HashMap<String, Collection<CardData>>();
			ObjectMapper mapper = new ObjectMapper();
			String contents = Util.readAll(path);
			JSONArray arr  = new JSONObject(contents).getJSONArray("actions");
			for(java.util.Iterator<Object> i = arr.iterator();i.hasNext();){
				Object o = i.next();
				CardData cur = (CardData)mapper.readValue(o.toString(), CardData.class);
				if(!ret.containsKey(cur.type)){
					ret.put(cur.type, new ArrayList<CardData>());
				}
				ret.get(cur.type).add(cur);
			}
			return ret;
		}catch(JsonProcessingException t){
			throw new WorkflowException("JSONデータの処理に失敗しました。"+t.getMessage(), HttpServletResponse.SC_NOT_FOUND, t);
		}catch(IOException t){
			throw new WorkflowException("シナリオデータの読み込みに失敗しました。", HttpServletResponse.SC_NOT_FOUND, t);
		}
	}
	/***convert Collection<Collection T> to Collection<T>
	 * 重複していた場合は先行するオブジェクトを優先する
	 * @param src
	 * @return
	 */
	public static <T> Collection<T> flatten(Collection<Collection<T>> src){
		Collection<T> ret = new ArrayList<>();
		for(Iterator<Collection<T>> i = src.iterator(); i.hasNext();){
			Collection<T> t =i.next();
			for(T cur :t ){
				if(!ret.contains(cur))
					ret.add(cur);
			}
//			ret.addAll(t);
		}
		return ret;
	}
	/**指定された種類のアクションを返す*/
	public static Collection<CardData> load(final String type) throws WorkflowException{
		return loadAll().get(type);
	}
	/**種類を指定してアクションを取得(最初のエントリのみ)*/
	public static CardData find(final CardData.Types type) throws WorkflowException{
		Collection<CardData> c = loadAll().get(type.toString());
		return c == null || c.size() == 0 ? null  : c.iterator().next();
	}
	/**現在のシナリオから種類を指定してアクションのリストを取得*/
	public static CardData[] findList(final CardData.Types type) throws WorkflowException{
		Collection<CardData> ret = loadAll().get(type.toString());
		if(ret == null)return new CardData[0];
		else return ret.toArray(new CardData[ret.size()]);
	}
	/**シナリオ格納先と種類を指定してアクションのリストを取得*/
	public static CardData[] findList(final CardData.Types type, final String basedir) throws WorkflowException{
		Collection<CardData> ret = loadAll(basedir).get(type.toString());
		if(ret == null)return new CardData[0];
		else return ret.toArray(new CardData[ret.size()]);
	}
	/**IDを指定してアクションを取得*/
	public static CardData get(final String id) throws WorkflowException{
		loadAll().values();
		for(Iterator<CardData> i = flatten(loadAll().values()).iterator(); i.hasNext();){
			CardData c = i.next();
			if(c.id == id)return c;
		}
		return null;
	}
	public static CardData newInstance(final String type) throws WorkflowException{
		return load(type).iterator().next();
	}
	
	public static String stringify(Object notif) throws WorkflowException{
		try{
			ObjectMapper mapper = new ObjectMapper();
			return mapper.writeValueAsString(notif);
		}catch(Throwable t){
			throw new WorkflowException("failed to serialize message.", 500, t);
		}
	}
	public String toString(){
		try{
			return stringify(this);
		}catch(WorkflowException t){
			return "serialization failed:" + ((Object)this).toString();
		}
	}
	@Override
	public boolean equals(Object obj) {
		if(this.id.equals(((CardData)obj).id))return true;
		return false;
//		return super.equals(obj);
	}

	public static <T extends CardData> T  parse(final String msg) throws WorkflowException{
		try{
			ObjectMapper mapper = new ObjectMapper();
			CardData  o =  mapper.readValue(msg,  CardData.class);
			return (T) o;
		}catch(JsonProcessingException t){
			throw new WorkflowException("JSONデータの処理に失敗しました。", HttpServletResponse.SC_NOT_FOUND, t);
		}catch(IOException t){
			throw new WorkflowException("シナリオデータの解析に失敗しました。", HttpServletResponse.SC_INTERNAL_SERVER_ERROR, t);
		}

	}
//	/**toにメンバのユーザIDを設定*/
//	public CardData setMembers(Collection<Member> members){
//		Collection<String> arr = new ArrayList<>();
//		for(Iterator<Member> i = members.iterator(); i.hasNext();arr.add(i.next().email));
//		this.to = arr.toArray(new String[arr.size()]);
//		return this;
//	}

	/**インスタンスのコピーを作成する*/
	@Override public CardData clone(){
		String s = stringify(this);
		CardData d = parse(s);
		return d;
	}
	
	
	
	public static void main(String[] args){
		try{
			
			
			Map<String, Collection<CardData>> m = CardData.loadAll("WebContent/data/actions.json");
			Collection<CardData> all = CardData.flatten(m.values());
			for(CardData c : all){
				if(c.is(Types.action)){
					System.out.println("type is action");
				}
				String s = 	CardData.stringify(c);
				System.out.println(s);
			}
		}catch (Throwable t) {
			t.printStackTrace();
		}
	}
}
