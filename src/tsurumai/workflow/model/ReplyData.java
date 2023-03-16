package tsurumai.workflow.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import tsurumai.workflow.CacheControl;
import tsurumai.workflow.WorkflowException;
import tsurumai.workflow.WorkflowInstance;
import tsurumai.workflow.WorkflowService;
import tsurumai.workflow.util.ServiceLogger;
import tsurumai.workflow.util.Util;

@JsonIgnoreProperties({"comment","","//","#"})
//@JsonInclude(JsonInclude.Include.NON_NULL)

/**アクションに対する応答メッセージを定義します。
 * 
 * 基本形
 * 
 * 
 * 1つのアクションに対して1つの応答が返される
 * 
 * 応用系
 * 
 *1つのアクションに対して2つの応答が返される
 * 
 * 
 * 開始、終了
 * 
 * */
@XmlRootElement
public class ReplyData{


	/**応答元アクションID*/
	@XmlAttribute
	public String actionid;

	/**宛先ロールID。*/
	@XmlAttribute
	public String to;
	
	/**表示用の重要度レベル。0=低,1=中,2=高,3=重要*/
	@XmlAttribute
	public int level;
	
	/**表示用の短いラベル。*/
	@XmlAttribute
	public String name;
	
	/**表示用のメッセージ。*/
	@XmlAttribute
	public String message;//表示用メッセージ
	
	/**この応答の適用前提条件となるアクションIDの配列。(ここで指定されたアクションが実行済であれば適用される)*/
	@XmlAttribute
	public String[] cardcondition;
//	/**この応答の適用条件となる返信元アクションID。(このIDのアクションに対する返信である場合に適用される)*/
//	@XmlAttribute
//	public String replycondition;//指定されたIDのアクションに対するリプライであれば適用

	/**この応答の適用条件となるアクションIDと経過時間のセットの配列。*/
	@Deprecated
	@XmlElement
	public Map<String, Object>[] timecondition;

	/**この応答の多重度制約。actionidに指定されたいずれかのアクションをmultiplicity回実行しようとした場合に適用される。*/
	@Deprecated
	@XmlElement
	public Map<String, Object> constraints;

	/**この応答に添付するステートカードのID。*/
	@XmlAttribute
	public String state;

	/**アクションが実行されてから応答を返すまでの待ち時間(秒数)。*/
	@XmlAttribute
	public int delay = 0;

	/**リプライの評価順。*/
	@XmlAttribute
	public int order = 0;
	
	/**アクション中断を示すリプライの場合にtrue*/
	@Deprecated
	@XmlAttribute
	public boolean abort = false;
	
	/**特殊リプライの種類を示す。
	 * "notfound":対応するリプライが見つからない場合に返す。
	 * */
	@XmlAttribute
	public String type;

	/**このリプライが有効となるまでの、フェーズ開始からの経過時間(秒数)。*/
	@XmlAttribute
	public int elapsed = -1;
	
	/**このリプライが有効となる送信元のロール。*/
	@XmlAttribute
	public String from;
	
	/***/
	/**このリプライが適用されるために必要なシステムステートの条件式を指定する。<br>
	 * ステートカードのID、演算子(AND,OR,NOT,NORのいずれか)を指定可能。
	 * 例:["state1"]<br>
	 * 例:["state1", "state2"]<br>
	 * 例:["state1", "state2", "state3", "AND"]<br>
	 */
	@XmlAttribute
	public String[] statecondition;
	
	/**このリプライの実行時にシステムにステートカードを追加する。*/
	@XmlAttribute
	public String[] addstate;
	/**このリプライの実行時にシステムが持つステートカードを破棄する。*/
	@XmlAttribute
	public String[] removestate;
	
	/**情報共有アクションにこのステートカードが添付されていればこのリプライが適用される。*/
	@XmlAttribute
	public String attachmentcondition;
	
	/**リプライ候補の評価優先度。*/
//	@XmlAttribute
	public int priority;

	/**トリガイベントが発火した日時。演習中のみ有効。*/
	@XmlAttribute
	public Date fireWhen;
	
	/**固定(既定)のオブジェクト*/
	@Deprecated
	@XmlAttribute
	public boolean fixed = false;

	/**ステートの識別子。*/
	@XmlAttribute
	public String id;
	
	/**条件評価スクリプト式。function evaluate(arg){...}が呼び出される。*/
	@Deprecated
	@XmlAttribute
	public String evalator;
	
	/*成功確率の定義。 2022/11/03　追加 */
    @XmlAttribute
	public double probability = 1.0 ;
    
    //攻撃パスの判定　　2022/12/20　追加
    @XmlAttribute
	public String attackpath ;

    	
	@XmlTransient

	public boolean isTriggerAction(){
		if(type != null && type.equals("trigger"))	return true;
		if(actionid != null && actionid.equals("s0000"))	return true;//互換性のため
		return false;
	}
	//protected static CacheControl<Collection<ReplyData>> cache = new CacheControl<>();
	public interface Types{
		/**既定の応答*/
		public static final String NOT_FOUND = "notfound";
		public static final String NULL = "null";
		/**ダミーの空応答(非表示)*/
		public static final String HIDDEN="hidden";
		/**トリガーイベント*/
		public static final String TRIGGER ="trigger";
	}
	

	protected static ServiceLogger logger = ServiceLogger.getLogger();
	/**エラー応答を返す*/
	public static ReplyData getErrorReply(int phase, final String actionid){
		return getReplyByType(phase, actionid, Types.NOT_FOUND);
	}
	/**「空応答」を返す*/
	public static ReplyData getNullReply(int phase){
		return getReplyByType(phase, null, "null");
	}
	/**指定された種類のリプライを返す。
	 * */
	public static ReplyData getReplyByType(int phase, final String actionid, final String type) throws WorkflowException{
		Collection<ReplyData> all = ReplyData.loadReply(phase);
		for(Iterator<ReplyData> i = all.iterator();i.hasNext();){
			ReplyData e = i.next();
			if(actionid != null && !e.actionid.equals(actionid)){
				continue;
			}
			if(type.equals(e.type)){
				return e;
			}
		}
		throw new WorkflowException(String.format("リプライが見つかりません。フェーズ:%d、種類:%s", phase, type));
	}
	public static Collection<ReplyData> findReply(final int phase, final String actionid, final Member to, String[] attachment) throws WorkflowException{
		return findReply(phase, actionid, to, 0, attachment);
	}
	
	
	public static Collection<ReplyData> findReply(final int phase, final String actionid, final Member to, int order, String[] attachments) throws WorkflowException{
		return findReply(phase, actionid, to, null, order, attachments);
	}
	
	/**アクションに対するリプライの候補を返す
	 * */
	public static Collection<ReplyData> findReply(final int phase, final String actionid, final Member to, final Member from, int order,  String[] attachments) throws WorkflowException{
		Collection<ReplyData> ret = new ArrayList<>();
		Collection<ReplyData> rep = loadReply(phase);
		
		for(Iterator<ReplyData> i = rep.iterator();i.hasNext();){//ループの最後で候補に追加するので、非該当なら途中でcontinueする。
			ReplyData e = i.next();
			if(actionid != null && e != null && e.actionid != null && !e.actionid.equals(actionid)){
				logger.debug(String.format("リプライは候補から除外されました(アクションIDが非該当)。アクション %s:宛先:%s へのリプライ [%s]", actionid, to.role, e.name ));
				continue;
			}

			if(e.type != null && e.type.equals(Types.NOT_FOUND)){
				continue;
			}
			
			if(order >= 50){
				logger.error("オーダーが50を超えました(バグ)。", new Throwable());
				return ret;
			}
			if(order != 0 && e.order != order){
				logger.info(String.format("リプライは候補から除外されました(オーダーが非該当(%d,%d))。アクション %s:宛先:%s へのリプライ [%s]", order, e.order, actionid, to.role, e.name ));
				continue;
			}

			if(!e.checkAttachmentCondition(attachments))
				continue;
			if(!e.checkRecipients(to, from, phase))
				continue;
			
			ret.add(e);
		}
		
		if(ret.size() == 0){

			if(to.isSystemUser(phase) ){
				ReplyData errorReply = ReplyData.getErrorReply(phase, null);
				ret.add(errorReply);
			}
		}
		return ret;
	}
	/**
	 * to,fromを評価する
	 * */
	public boolean checkRecipients(Member to, Member from, int phase) {

		if(to != null && !to.isSystemUser(phase)){
			logger.info(String.format("リプライは候補から除外されました(宛先が自動応答ユーザではありません)。アクション %s:宛先:%s へのリプライ [%s]",
					actionid, to.role, this.name ));
			return false;
		}
		
		if(to != null && !to.matches(this.to)){
			logger.info(String.format("リプライは候補から除外されました(宛先が非該当)。アクション %s:宛先:%s へのリプライ [%s]",
					actionid, to.role, this.name ));
			return false;
		}
		//fromが指定されていたら条件評価
		if(from != null && !from.matches(this.from)){
			logger.info(String.format("リプライは候補から除外されました(送信元が非該当)。アクション %s:送信元:%s へのリプライ [%s]",
					actionid, from.role , this.name ));
			return false;//未検証
		}
		return true;
	}
	/**attachmentconditionを評価する*/
	public boolean checkAttachmentCondition(String[] attachments) {
		if(attachments != null && this.attachmentcondition != null && 
				this.attachmentcondition.length() != 0){
			boolean found = false;
			for(String s : attachments){
				if(s.equals(this.attachmentcondition)){	
					found = true;break;
				}
			}
			if(!found){
				logger.info(String.format("リプライは候補から除外されました(添付ステートカードが非該当)。アクション %s:添付ステートカード:%s へのリプライ [%s]", 
						actionid, String.valueOf(this.attachmentcondition), this.name ));
				return false;
			}else{
				logger.info(String.format("リプライ候補がヒット(添付ステートカードが非該当)。アクション %s:添付ステートカード:%s へのリプライ [%s]", 
						actionid, String.valueOf(this.attachmentcondition), this.name ));
				return true;
			}
		}
		return true;
	}
	/**timeconditionを満たしていればtrueを返す*/
	public boolean checkTimeCondition(WorkflowInstance.Validatable<String, Long> v) {
		if(this.timecondition == null)
			return true; 
		try{
			for(Map<String, Object> m : this.timecondition){
				String actionId = (String)m.get("prerequisite");
				Integer elapsed = (Integer)m.get("elapsed");
				
				if(!(actionId != null && elapsed != null))continue;

				long hist = v.get(actionId);// getElapsedTime(actionId);
				if(!(actionId != null && elapsed != null)) continue;

				if(!(Integer.valueOf(elapsed)>hist)) continue;

				if(hist == -1){
					logger.info(this.name + ":実行後経過時間条件(timecondition)で指定された前提アクションが履歴にありません。");
				}else{
					long rest = elapsed.intValue() - hist;
					logger.info(this.name + ":実行後経過時間条件(timecondition)で指定された前提アクションからの経過時間を待っています。(" + String.valueOf(rest) + ")");
				}
				return false;
				
			}
		}catch(Throwable t){
			logger.error(this.name + ":時間条件の解析に失敗しました。定義を見直してください。" + this.toString(), t);
		}
		return true;
	}
	
	/**すべてのリプライ定義を返す*/
	public static Collection<ReplyData> loadAll() throws WorkflowException{
		return loadReply(-1);
	}

	/**フェーズ番号とリプライデータの配列のマップ*/
	protected static CacheControl<ConcurrentHashMap<Integer, Collection<ReplyData>>> cache = new CacheControl<>();

	protected ConcurrentHashMap<Integer, Collection<ReplyData>> _load(final String file) throws IOException{

		String contents1 = Util.readAll(file);
		Map<Integer, Collection<ReplyData>> r1=mapReplyData(new JSONObject(contents1));

		ConcurrentHashMap<Integer, Collection<ReplyData>> arr =null;
		if(Util.dataExists("sys/replies.json")){
			String contents2 = Util.readAll(WorkflowService.getContextRelativePath("sys/replies.json"));
			Map<Integer, Collection<ReplyData>> r2=mapReplyData(new JSONObject(contents2));
			//既定のオブジェクトをマージ
			 arr = merge(r2, r1);
		}else{
			arr = merge(r1, new HashMap<Integer, Collection<ReplyData>>());
		}
		//全フェーズのオブジェクトをフェーズ=-1に格納
		Collection<ReplyData> all  = new ArrayList<ReplyData>();
		for(Collection<ReplyData> c : arr.values()){
			all.addAll(c);
		}
		arr.put(Integer.valueOf(-1), all);
		return arr;
	}
	public static void reload(final String file) throws WorkflowException {
		try {
			if(!file.equals(targetFile))
				logger.warn(String.format("リプライデータのパスが変更されました。%s >%s", targetFile, file));
			
			cache.reload(targetFile=file);
			String contents1 = Util.readAll(file);
			Map<Integer, Collection<ReplyData>> r1=mapReplyData(new JSONObject(contents1));
	
			ConcurrentHashMap<Integer, Collection<ReplyData>> arr =null;
			if(Util.dataExists("sys/replies.json")){
				String contents2 = Util.readAll(WorkflowService.getContextRelativePath("sys/replies.json"));
				Map<Integer, Collection<ReplyData>> r2=mapReplyData(new JSONObject(contents2));
				//既定のオブジェクトをマージ
				 arr = merge(r2, r1);
			}else{
				arr = merge(r1, new HashMap<Integer, Collection<ReplyData>>());
			}
			//全フェーズのオブジェクトをフェーズ=-1に格納
			Collection<ReplyData> all  = new ArrayList<ReplyData>();
			for(Collection<ReplyData> c : arr.values()){
				all.addAll(c);
			}
			arr.put(Integer.valueOf(-1), all);
	
			cache.set(arr);
			
			logger.info("リプライデータを再ロードしました。" + file);
		}catch(JsonProcessingException t){
			throw new WorkflowException("JSONデータの処理に失敗しました。", HttpServletResponse.SC_NOT_FOUND, t);
		}catch(IOException t){
			throw new WorkflowException("シナリオデータの読み込みに失敗しました。", HttpServletResponse.SC_NOT_FOUND, t);
		}

	}
	
	protected static ConcurrentHashMap<Integer, Collection<ReplyData>> merge(final Map<Integer,Collection<ReplyData>> arr1, final Map<Integer,Collection<ReplyData>> arr2){
		Set<Integer> s1 = arr1.keySet();
		Set<Integer> s2 = arr2.keySet();
		ArrayList<Integer> all = new ArrayList<>();
		all.addAll(s1);
		all.addAll(s2);
		ConcurrentHashMap<Integer, Collection<ReplyData>> ret = new ConcurrentHashMap<Integer, Collection<ReplyData>>();
		for(Integer i : all){
			Collection<ReplyData > r1 = arr1.get(i);
			Collection<ReplyData> r2 = arr2.get(i);
			Collection<ReplyData> r = new ArrayList<>();
			if(r1!=null)r.addAll(r1);
			if(r2 != null)r.addAll(r2);
			ret.put(i, r);
		}
		return ret;
	}
	
	/**reply.jsonをフェーズでマップ化する*/
	protected static HashMap<Integer, Collection<ReplyData>> mapReplyData(final JSONObject  src) 
			throws JsonParseException,JsonMappingException, IOException{
		HashMap<Integer, Collection<ReplyData>> ret = new HashMap<>();
		JSONArray arr  = src.getJSONArray("states");
//		ObjectMapper mapper = new ObjectMapper();
		for(java.util.Iterator<Object> i = arr.iterator();i.hasNext();){
			JSONObject o = (JSONObject)i.next();
			int phase = o.getInt("phase");
			JSONArray r = o.getJSONArray("reply");
			if(!ret.containsKey(Integer.valueOf(phase))){
				ret.put(Integer.valueOf(phase), new ArrayList<ReplyData>());
			}
			for(int c = 0; c < r.length(); c ++){
				ReplyData cur = (ReplyData)mapper.readValue(r.get(c).toString(), ReplyData.class);
				ret.get(Integer.valueOf(phase)).add(cur);
			}
		}
		return ret;
	}
	protected static String targetFile;
	public static Collection<ReplyData> loadReply(final int phase/*, final String basedir*/) throws WorkflowException{
	
		return loadReply(phase, targetFile);
	}
	/**
	 * リプライをロードする。
	 * @param phase フェーズを指定する。-1を指定するとすべてのフェーズのリプライの集合を返す。
	 * @param file リプライデータのファイル名*/
	public static Collection<ReplyData> loadReply(final int phase, final String file) throws WorkflowException{
			if(cache.load(file) != null && cache.get().containsKey(Integer.valueOf(phase))) 
				return cache.get().get(Integer.valueOf(phase));
			if(file == null) {
				logger.warn("シナリオのロード元がnullです。" + file);
			}
			reload(file);
			return cache.get().get(Integer.valueOf(phase));
	}
	
	public interface CommonReplyID{
		/**アクションに対するリプライが定義されていない*/
		public static final String REPLY_NOT_FOUND = "E0000";
		/**その他エラー*/
		public static final String REPLY_ERROR = "E0001";
		
	}

	protected static ObjectMapper mapper = new ObjectMapper()
			.setSerializationInclusion(Include.NON_NULL)
			.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

	public static String stringify(ReplyData data) throws WorkflowException{
		try{
			return mapper.writeValueAsString(data);
		}catch(Throwable t){
			throw new WorkflowException("failed to serialize message.", 500, t);
		}
	}
	public String toString(){
		return stringify(this);
	}
	@Override
	public boolean equals(Object obj) {
		if(!(obj instanceof ReplyData))return false;
	
		String str1 = stringify(this);
		String str2 = stringify((ReplyData)obj);
		return str1.equals(str2);
	}

	
}
