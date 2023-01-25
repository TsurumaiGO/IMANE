package tsurumai.workflow;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import tsurumai.workflow.model.CardData;
import tsurumai.workflow.model.Member;
import tsurumai.workflow.model.PointCard;
import tsurumai.workflow.model.ReplyData;
import tsurumai.workflow.model.ScenarioData;
import tsurumai.workflow.model.StateData;
import tsurumai.workflow.util.Pair;
import tsurumai.workflow.util.ServiceLogger;
import tsurumai.workflow.util.Util;
import tsurumai.workflow.vtime.Task;
import tsurumai.workflow.vtime.Task.TaskListener;
import tsurumai.workflow.vtime.World;
@JsonIgnoreProperties({"comment","","//","#"})
//@JsonInclude(JsonInclude.Include.NON_NULL)

/**操業レベル定義
 * @deprecated 操業レベルに関する処理は廃止されました*/
@Deprecated
class OperationLevelDef implements Comparable<OperationLevelDef>{
	
	/**フェーズ開始からの経過時間(秒)**/public int time;
	/**操業レベル*/public int level;
	
	
	//降順でソート
	@Override
	public int compareTo(OperationLevelDef o) {
		return -Integer.compare(this.time, o.time);
	}
	
}
@Deprecated 
@JsonIgnoreProperties({"comment","","//","#"})
/**フェーズにおける操業状態の定義*/
 class OperationStateDef{
	public OperationStateDef(){}
	/**フェーズ*/public int phase;
	/**このフェーズの操業状態の定義*/public OperationLevelDef[] state;
	/**経過時間の降順でソートした操業レベルを返す*/
	public OperationLevelDef[] getState(){
		Collections.sort(Arrays.asList(this.state));
		return this.state;
	}
	/**現在のフェーズの開始からの経過時間にもとづいて、現在の操業レベルを返す*/
	public OperationLevelDef getOperationLevel(int phase, final Date started){//tag:virtualdate
		if(phase != this.phase) return null;
		long elapsed = (new Date().getTime() - started.getTime())/1000 ;//経過時間(s)
		OperationLevelDef[] def = getState();
		for(OperationLevelDef d : def){//降順でソートされている
			if(elapsed > d.time){
				return d;
			}
		}
		return null;
	}
}
/**トリガーイベントを表現する
 * 
 * */
@JsonIgnoreProperties({"comment","","//","#"})
class TriggerEvent{
	public TriggerEvent(){}
	public String state;
	public Date date;
	public String from;
	public String to;
	
	public TriggerEvent(String state, final String from, final String to){
		this(state, from, to, null);
	}
	public TriggerEvent (String state, final String from, final String to, Date date){
		this.state = state;
		this.date = date == null ? date : new Date(); //new Date();
		this.from = from;
		this.to= to;
	}
	
}
/**ワークフローのインスタンスを管理する
 * 
 * チーム別のワークフロー状態、アクションキュー、履歴などを管理
 * */
@JsonIgnoreProperties({"comment","","//","#"})
@XmlRootElement
public class WorkflowInstance {
	//追加：単位行列・接続行列の作成
	public static int elem = 17 ;
	public static int[][] C = new int[elem][elem];
	public static int[][] C0 =new int[elem][elem];
	public static int[][] D = new int[elem][elem];
	public static int[][] D_inf = new int[elem][elem];
	public static int[][] B = new int[elem][elem];
	public static int[][] eye = new int [elem][elem];
	int fwI ;
	int fw0 ;
	int vpn ;
	int ra ;
		
	
	public WorkflowInstance() {}
	protected OperationStateDef[] operationStateDef = new OperationStateDef[0];
	static ServiceLogger logger =  ServiceLogger.getLogger();
	int interval = Integer.parseInt(System.getProperty("check.interval","1000"));
	protected CopyOnWriteArrayList<TriggerEvent> triggerEvents =  new CopyOnWriteArrayList<>();
	/**アクション要求のキュー*/
	protected CopyOnWriteArrayList<NotificationMessage> actionQueue = new CopyOnWriteArrayList<>();
	/**アクション実行結果の履歴*/
	protected CopyOnWriteArrayList<NotificationMessage> history = new CopyOnWriteArrayList<>();

	/**システム状態(ステートカード) Map&lt;ステートID、登録日時&gt; 登録日時はシリアライズ/でシリアライズの関係でDateまたはLongとなる*/
	protected Map<String, Object> systemState = new HashMap<>();
	
	protected synchronized void addState(String state, Object data, final String msgid){
		StateData s = StateData.getStateData(state);
		if(s == null){
			logger.warn("存在しないシステムステートを追加しようとしました。" + state.toString());
			s = new StateData(state);
		}
		logger.info("システムステートを追加します。" + s.toString());
		systemState.put(state, data);
		onAddState(state, msgid);

		//addstate関数に入れるもの
		String ATK = "ATK";
		String CON = "CON";
		String VUL = "VUL";
		String INF = "INF";
		String add = "add";
		
		if(state.contains("/")) {
			try {
				//state="ゾーン名orFW/機器ID(X01など)/ATKorCONorINForVUL"
				String[] str = state.split("/");
				int asset_number = Integer.parseInt(str[1].substring(str[1].length()-2),10);
				if(str[2].equals(ATK)){
					D_calculate(asset_number, add);
				}else if(str[2].equals(CON)){
					C_calculate(asset_number, add);
				}else if(str[2].equals(VUL)){
					B_calculate(asset_number,add);
				}else if(str[2].equals(INF)){
					D_inf_calculate(asset_number,add);
				}
		
				//感染型の処理を追加
				if(str[2].equals(INF) || str[2].equals(CON) || str[2].equals(VUL)){
					INF_calculate();
				}
			}catch(Throwable t){
				logger.warn("攻撃パス判定に関わるステートである場合、ステートの入力ルールを満たしているか確認してください：" + state.toString());
			}
		}

	}
	/**指定されたシステムステートが登録されているか*/
	protected synchronized boolean hasState(String stateid){
		for(String cur : this.systemState.keySet()){
			if(cur.equals(stateid)) return true;
		}
		return false;
	}
	/**システムステートを持つか、OR評価。statesが空ならtrue(無条件)*/
	protected synchronized boolean hasStates(String[] states){
		if(states == null || states.length == 0)return true;
		for(String i : states){
			if(hasState(i)) return true;
		}
		return false;
	}
	/**システムステートを持つか、AND評価。statesが空ならtrue(無条件)*/
	protected synchronized boolean hasAllStates(String[] states){
		if(states == null || states.length == 0) return true;
		for(String i : states){
			if(!hasState(i)) return false;
		}
		return true;
	}
	/**システムステートを削除する*/
	protected synchronized void removeState(final String state){
		StateData s = StateData.getStateData(state);
		if(s == null){
			logger.warn("存在しないシステムステートを削除しようとしました。" + state.toString());return;
		}
		if(hasState(state)){
			logger.info("システムステートを削除します。" + s.toString());
			systemState.remove(state);
			onRemoveState(state);

			if(state.contains("/")) {
				try {
					//state="ゾーン名orFW/機器ID(X01など)/ATKorCONorINForVUL"
					String[] str = state.split("/");
					int asset_number = Integer.parseInt(str[1].substring(str[1].length()-2),10);
					//removestate関数に入れるもの
					String ATK = "ATK";
					String CON = "CON";
					String VUL = "VUL";
					String INF = "INF";
					String remove = "remove";
					if(str[2].equals(ATK)){
						D_calculate(asset_number, remove);
					}else if(str[2].equals(CON)){
						C_calculate(asset_number, remove);
					}else if(str[2].equals(VUL)){
						B_calculate(asset_number,remove);
					}else if(str[2].equals(INF)){
						D_inf_calculate(asset_number,remove);
					}
		
					//感染型の処理を追加
					if(str[2].equals(CON) || str[2].equals(VUL)){
						INF_calculate();
					}
				}catch(Throwable t){
					logger.warn("攻撃パス判定に関わるステートである場合、ステートの入力ルールを満たしているか確認してください：" + state.toString());
				}
			}

		}else{
			logger.info("指定されたシステムステートは割り当てられていません。" + s.toString());
		}
	}
	/**ワークフローの状態*/
	public interface State{
		public static String STARTED ="Started";
		public static String ENDED ="Ended";
		public static String SUSPENDED ="Suspended";
		public static String ABORTED = "Aborted";
		public static String NONE = "None";
	}
	/**操業レベルの定義*/
	public interface OperationLevel{
		/**最高*/		public static int HIGHEST = 8;
		/**良好*/		public static int HIGH = 7;
		/**通常*/		public static int NORMAL= 6;
		/**やや低*/	public static int  LOW= 5;
		/**低*/			public static int LOWER = 4;
		/**最低*/		public static int LOWEST = 3;
		/**致命的*/	public static int CRITICAL = 2;
		/**緊急事態*/	public static int FATAL = 1;
		/**緊急停止(演習終了)*/	public static int  FAIL = 0;
	}
	/**ワークフローの割当て先チーム名
	 */
	@XmlAttribute
	public String team = "";
	/**ワークフローのフェーズ*/
	@XmlAttribute
	public int phase = 0;
	/**インスタンスの状態*/
	@XmlAttribute
	public String state = State.NONE;

	/**WFインスタンスの開始時間*/
	@XmlAttribute
	@JsonFormat(shape = JsonFormat.Shape.NUMBER)
	public Date start = null;
	@XmlAttribute
	@JsonFormat(shape = JsonFormat.Shape.NUMBER)
	public Date saved = null;
	
	/**現在の操業レベル*/
	@XmlAttribute
	public int operationlevel = 0;
	@XmlAttribute
	public long pid = 0;
	
	@XmlAttribute
	public int score = 0;
	@XmlAttribute
	public Hashtable<String, String> properties = new Hashtable<>();
	
	/**獲得したポイントカード: イベントのIDとポイントカードのハッシュ*/
	@XmlElement
	public Hashtable<String, Collection<PointCard>>pointchest = new Hashtable<>();

	/**定義済ポイントカード*/
	@XmlElement
	public PointCard[] pointcards = null; 
	
	@XmlElement
	public String scenarioName = ""; 
	@XmlElement
	public boolean frozen = false;
	
	
	/**ユーザの所有ステートカード。ユーザIDとステートIDのマップ。*/
	protected Map<String, Set<String>> memberStates =new HashMap<>();
	/**自動応答ユーザのステートカード状態を初期化する*/
	protected void initMemberStatus(){
		memberStates.clear();
		List<Member> members = Member.getTeamMembers(this.team);
		for(Member m : members){
			memberStates.put(m.email, new HashSet<>());
		}
	}
	/**自動応答ユーザのステートカードを更新する。*/
	protected void updateUserStates(final NotificationMessage msg){
		Set<String> states = msg.fetchStatecards();
		if(states == null || states.isEmpty()) return;
		
		Set<String> members = msg.fetchRecipients(this.team);
		if(members == null || members.isEmpty())return;
		
		for(String m : members){
			Set<String> cur = this.memberStates.get(m);
			for(String s : states){
				if(!cur.contains(s)){
					logger.info(String.format("[%s]がステートカード[%s]を獲得", m, s));
					cur.add(s);
				}
			}
		}
	}
	
	protected WorkflowService caller = null;
	protected Worker worker = null;
	public static WorkflowInstance newInstance(WorkflowService caller, final String team/*, long pid*/){
		WorkflowInstance inst = newInstance(caller.getScenarioDirectory(), team);
		inst.caller = caller;
		inst.team = team;
		return inst;
	}

	/**
	 * ワークフローインスタンスを初期化
	 * @param basedir シナリオセット(チーム定義)の格納先
	 * @param team チームID
	 * */
	public static WorkflowInstance newInstance(String basedir, final String team, long pid){
		
		WorkflowInstance inst = new WorkflowInstance();
		inst.team = team;
		inst.initialize(basedir);
		return inst;
	}
	public static WorkflowInstance newInstance(String basedir, final String team){
		return newInstance(basedir, team, -1);
	}
	
	public String toString(){
		return String.format("ワークフローインスタンス: %s phase %d; %s",  this.team, this.phase, 
				this.start != null ? new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(this.start) : "not started");
	}
	/**フローを初期化
	 *@param basedir データファイル(operationstate.json)の格納先
	 * */
	protected void initialize(final String basedir){
		try{
			logger.info("initialize:" +this.toString());
			this.scenarioName = basedir;
			ObjectMapper mapper = new ObjectMapper();

			String path = basedir + File.separator + "operationstate.json";
			if(new File(path).exists()){
				String  c = Util.readAll(path);
				OperationStateDef[] def = mapper.readValue(c,  OperationStateDef[].class);
				this.operationStateDef =def;
			}
		}catch(Throwable t){
			throw new RuntimeException("failed to load operation state.", t);
		}	
	}

	/**フェーズを開始*/
	public void start(int phase){
		logger.info("start:" +this.toString());
		
		int i,j;
		String state_elem;
		
		//行列を初期化
		for(i=0; i < elem; i++){
			for(j=0; j < elem; j++){
				C[i][j] = 0;
				C0[i][j] = 0;
				D[i][j] = 0;
				D_inf[i][j] = 0;
				B[i][j] = 0;
				eye[i][j] = 0;
			}
		}

		//単位行列eye
		for(i=0; i < elem; i++){
			for(j=0; j < elem; j++){
				if (i == j) {
					eye[i][j] = 1;
				}
			}
		}
		

		//接続行列C0の対角成分以外の接続を設定
		C0[0][0] = 1;
		C0[1][0] = 1;
		C0[2][0] = 1;

		C0[0][1] = 1;
		C0[1][1] = 1;
		C0[2][1] = 1;

		C0[0][2] = 1;
		C0[1][2] = 1;
		C0[2][2] = 1;
		C0[3][2] = 1;
		C0[4][2] = 1;
		C0[5][2] = 1;
		C0[6][2] = 1;
		C0[7][2] = 1;
		C0[8][2] = 1;
		C0[9][2] = 1;
		C0[10][2] = 1;
		
		C0[2][3] = 1;
		C0[3][3] = 1;
		C0[4][3] = 1;
		C0[5][3] = 1;
		C0[6][3] = 1;
		C0[7][3] = 1;
		C0[8][3] = 1;
		C0[9][3] = 1;
		C0[10][3] = 1;

		C0[2][4] = 1;
		C0[3][4] = 1;
		C0[4][4] = 1;
		C0[5][4] = 1;
		C0[6][4] = 1;
		C0[7][4] = 1;
		C0[8][4] = 1;
		C0[9][4] = 1;
		C0[10][4] = 1;

		C0[2][5] = 1;
		C0[3][5] = 1;
		C0[4][5] = 1;
		C0[5][5] = 1;
		C0[6][5] = 1;
		C0[7][5] = 1;
		C0[8][5] = 1;
		C0[9][5] = 1;
		C0[10][5] = 1;

		C0[2][6] = 1;
		C0[3][6] = 1;
		C0[4][6] = 1;
		C0[5][6] = 1;
		C0[6][6] = 1;
		C0[7][6] = 1;
		C0[8][6] = 1;
		C0[9][6] = 1;
		C0[10][6] = 1;

		C0[2][7] = 1;
		C0[3][7] = 1;
		C0[4][7] = 1;
		C0[5][7] = 1;
		C0[6][7] = 1;
		C0[7][7] = 1;
		C0[8][7] = 1;
		C0[9][7] = 1;
		C0[10][7] = 1;

		C0[2][8] = 1;
		C0[3][8] = 1;
		C0[4][8] = 1;
		C0[5][8] = 1;
		C0[6][8] = 1;
		C0[7][8] = 1;
		C0[8][8] = 1;
		C0[9][8] = 1;
		C0[10][8] = 1;

		C0[2][9] = 1;
		C0[3][9] = 1;
		C0[4][9] = 1;
		C0[5][9] = 1;
		C0[6][9] = 1;
		C0[7][9] = 1;
		C0[8][9] = 1;      
		C0[9][9] = 1;
		C0[10][9] = 1;


		C0[2][10] = 1;
		C0[3][10] = 1;
		C0[4][10] = 1;
		C0[5][10] = 1;
		C0[6][10] = 1;
		C0[7][10] = 1;
		C0[8][10] = 1;
		C0[9][10] = 1;
		C0[10][10] = 1;
		C0[11][10] = 1;
		C0[12][10] = 1;
		C0[13][10] = 1;
		C0[14][10] = 1;
		C0[15][10] = 1;

		C0[10][11] = 1;
		C0[11][11] = 1;
		C0[12][11] = 1;
		C0[13][11] = 1;
		C0[14][11] = 1;
		C0[15][11] = 1;

		C0[10][12] = 1;
		C0[11][12] = 1;
		C0[12][12] = 1;
		C0[13][12] = 1;
		C0[14][12] = 1;
		C0[15][12] = 1;

		C0[10][13] = 1;
		C0[11][13] = 1;
		C0[12][13] = 1;
		C0[13][13] = 1;
		C0[14][13] = 1;
		C0[15][13] = 1;
		C0[16][13] = 1;

		C0[10][14] = 1;
		C0[11][14] = 1;
		C0[12][14] = 1;
		C0[13][14] = 1;
		C0[14][14] = 1;
		C0[15][14] = 1;
		C0[16][14] = 1;

		C0[10][15] = 1;
		C0[11][15] = 1;
		C0[12][15] = 1;
		C0[13][15] = 1;
		C0[14][15] = 1;
		C0[15][15] = 1;
		C0[16][15] = 1;

		C0[13][16] = 1;
		C0[14][16] = 1;
		C0[15][16] = 1;
		C0[16][16] = 1;

		//C0 = C　にする
		for(i=0; i < elem; i++){
			for(j=0; j < elem; j++){
				C[i][j] = C0[i][j];
			}
		}

		D[0][0] = 1;
		B[0][0] = 1;

		fwI = 2;
		fw0 = 10;
		vpn = 3;
		ra = 2;
		/*
		try {
			//internetからの通信を通すfwの位置を設定	
			state_elem = "FW_INTERNET/";
			String state_FW = StateData.getStateID(state_elem);
			if(state_FW != null){
				String[] str = state_FW.split("/");
				fwI = Integer.parseInt(str[1].substring(str[1].length()-2),10);
				logger.info("FW_INTERNETの位置を" + fwI + "に設定しました。");
			}
		}catch(Throwable t) {
			logger.error("FW_INTERNETの位置が解析出来ませんでした。",t);
		}
		
		try {
			//internetからの通信を通さないfwの位置を設定	
			state_elem = "FW_0/";
			String state_FW0 = StateData.getStateID(state_elem);
			if(state_FW0 != null){
				String[] str = state_FW0.split("/");
				fw0 = Integer.parseInt(str[1].substring(str[1].length()-2),10);
				logger.info("FW_INTERNETの位置を" + fw0 + "に設定しました。");
			}
		}catch(Throwable t) {
			logger.error("FW_INTERNETの位置が解析出来ませんでした。",t);
		}

		try {		
			//vpnの位置を設定
			state_elem = "VPN/";
			String state_VPN = StateData.getStateID(state_elem);
			if(state_VPN != null){
				String[] str = state_VPN.split("/");
				vpn = Integer.parseInt(str[1].substring(str[1].length()-2),10);
				logger.info("VPNの位置を" + vpn + "に設定しました。");
			}
		}catch(Throwable t) {
			logger.error("VPNの位置が解析出来ませんでした。",t);
		}
		
		try {	
			//remoteaccessの位置を設定
			state_elem = "REMOTEACCESS/";
			String state_REMOTEACCESS = StateData.getStateID(state_elem);
			if(state_REMOTEACCESS != null){
				String[] str = state_REMOTEACCESS.split("/");
				ra = Integer.parseInt(str[1].substring(str[1].length()-2),10);
				logger.info("REMOTEACCESSの位置を" + ra + "に設定しました。");
			}
		}catch(Throwable t) {
			logger.error("REMOTEACCESSの位置が解析出来ませんでした。",t);
		}
		*/
				
		//確認のため出力
		System.out.print("C["+"\r\n");
		for(i=0; i < elem; i++){
			for(j=0; j < elem; j++){
				System.out.print( C[i][j] + ",");
			}
			System.out.print("\r\n");
		}
		System.out.print("]" + "\r\n");    

		
		this.clearAutoActionTimers();
		this.state = State.STARTED;
		this.phase = phase;
		this.score = 0;
		this.start = new Date();
		this.actionQueue.clear();
		this.history.clear();
		this.triggerEvents.clear();
		this.systemState.clear();
		this.pointchest.clear();
		this.pointHistory.clear();
		this.autoActionHistory.clear();
		this.pointcards = PointCard.list(this.phase);
		this.initMemberStatus();
		if(this.worker != null){
			this.worker.stop();
		}
//		try {
//			Thread.sleep(1000);
//		}catch(Throwable t) {}
//		
		
//		this.world = new World(this.team);
		this.startWorld();
		
		this.worker = new Worker();
		this.worker.start();

		logger.info("workflow started:" +this.toString());
	}
	/**フェーズを終了*/
	public void end(){
		logger.info("end:" +this.toString());
		if(this.worker != null){
			this.worker.stop();
			this.worker = null;
		}
		this.state =  State.ENDED;
		logger.info("workflow ended:" +this.toString());
	}
	
//	/**一時停止(使用するかどうか？)*/
//	public void suspend(){
//		logger.info("suspend:" +this.toString());
//		this.state = State.SUSPENDED;
//		this.saved  = new Date();
//		if(this.worker != null){
//			this.worker.stop();
//		}
//	}
//
//	/**一時停止から復帰(使用するかどうか？)*/
//	public void resume(){
//		logger.info("resume:" +this.toString());
//		this.state = State.STARTED;
//
//		this.worker = new Worker();
//		this.worker.start();
//		logger.info("resumed:" +this.toString());
//	}
	
	/**フェーズを中断・異常終了*/
	public void abort(){
		logger.info("abort:" +this.toString());
		this.state = State.ABORTED;
		this.world.stop();
		logger.info("workflow aborted:" +this.toString());

	}
	/**フェーズ状態変更のステートカードIDを返す
	 * アクションIDは、10XXYY の形式(XX:フェーズ、YY: phaseState: PhaseStateのいずれか)*/
	public static int makePhaseStateID(int phase, int phaseState){
		int r = Integer.parseInt(String.format("10%02d%02d", phase, phaseState));
		logger.info("ステート変更:" + String.valueOf(r));
		return r;
	}
	public int makePhaseState(int phaseState){
		return makePhaseStateID(this.phase, phaseState);
	}

	protected boolean isRunning(){return State.STARTED.equals(this.state);}
	protected boolean isSuspending(){

		boolean ret = State.SUSPENDED.equals(this.state);
		if(this.world.isPaused() != ret) {logger.error("内部状態が矛盾しています。", new WorkflowException("ワークフロー状態が矛盾"));}

		return ret;
	}
	
	/**ユーザからの手動アクション要求を処理する
	 * */
	public void requestAction(CardData action, Member from, Member to, Member[] cc, NotificationMessage reply) throws WorkflowException{
		prepareAction(action, from, to, cc, reply);
	}

	/**アクション要求を受け付ける
	 * */
	protected void acceptAction(CardData action, Member from, Member to, Member[] cc, NotificationMessage reply){

		logger.info("action accepted. from:" +
				(from !=null ? from.toString() : "nul") +",to:"+ (to != null ? to.toString()  : "null") + 
					",cc:" + Util.toString(cc!= null ?  cc.toString() : "null") + ",action:"+action.toString());
		
		NotificationMessage msg  = new NotificationMessage(/*this.pid,*/ action, to, from, cc, reply);
		msg.sentDate = this.getTime();
		msg.team = this.team;
		enqueueAction(msg);

	}
	/**アクション要求を拒否する*/
	protected void rejectAction(CardData action, Member from, Member to, Member[] cc, NotificationMessage reply, String reason){
		logger.info("action rejected:" + action.toString());
		throw new WorkflowException("このアクションは実行できません。" + reason, HttpServletResponse.SC_BAD_REQUEST);
	}
	
	/**受信したアクションの事前チェック
	 *  パスしたらキューに入れて復帰
	 * */
	protected void prepareAction(CardData action, Member from, Member to, Member[] cc, NotificationMessage reply) throws WorkflowException{

		if(!this.isRunning() && !this.isSuspending()){
			rejectAction(action, from, to, cc,reply,  "演習が実行されていません。");return;
		}

		//type:controlのアクション以外は拒否する
		//TODO: ただし、自動アクションやリプライは停止するはずなので、意図した動作はしないかもしれない
		if(this.getActiveScenario().isFeatureEnabled("virtualtime")) {
			if(action.is(CardData.Types.control) && this.isSuspending()) {
				rejectAction(action, from, to, cc,reply,  "演習が一時停止されています。");return;
			}
		}

		//		システムユーザ以外または汎用アクションでないならすぐにリプライ送信
		if(!to.isSystemUser(this.phase) || !action.is(CardData.Types.action)){
			NotificationMessage msg  = new NotificationMessage(/*this.pid, */action, to, from, cc, reply);
			msg.sentDate = this.getTime();
			executeAction(msg);
			return;
		}

		if(action.abortaction  != null && action.abortaction.length() != 0){
			int c = countQueuedAction(new String[]{action.abortaction});
			if(c ==  0){
				rejectAction(action, from, to, cc, reply, "アクションが実行中でないため、中止することはできません。");
				return;
			}
		}

		Collection<ReplyData> rep = ReplyData.findReply(this.phase, action.id, to, action.attachments);
		if(rep.isEmpty()){
			logger.warn("アクションに対する応答が見つかりません。" + action.toString());
		}
		acceptAction(action, from, to, cc,reply);
	}
	/**受け付けたアクションをキューに入れ、受付通知を発行*/
	protected void enqueueAction(NotificationMessage msg){
		
		history.add(msg);
		actionQueue.add(msg);
		logger.info("action queued." +  msg.toString());
		Notifier.dispatch(msg);
	}
	/**受け付けたアクションを即時実行*/
	protected void executeAction(NotificationMessage msg){
		msg.replyDate = this.getTime();//tag:virtualtime
		history.add(msg);
		updateSystemState(msg.action, msg.id);
		Notifier.dispatch(msg);
		logger.info("action executed." +  msg.toString());
	}
	/**操業状態を更新*/
	protected void updateOperationState(){
		for(OperationStateDef od : operationStateDef){
			if(this.phase != od.phase)	continue;
			
			OperationLevelDef lv = od.getOperationLevel(this.phase, this.start);
			if(lv != null && this.operationlevel != lv.level){
				logger.info("operation state changed:"  + String.valueOf(this.operationlevel) + " to "+ String.valueOf(lv.level) + ":: "+this.toString());
				this.operationlevel = lv.level;
				onChangeOperationState();
			}
		}
	}
	/***/
	public void onChangeOperationState(){
		String message = "操業レベルが変化しました。現在のレベル:"+String.valueOf(this.operationlevel);
	//	postTeamNotification(message, NotificationMessage.LEVEL_HIDDEN, null);
	}
	/**システムからの通知を送信します。*/
	public void sendTeamNotification(String message, int level, String[] state){
//		CardData data = CardData.find(CardData.Types.notification);
//		
//		NotificationMessage msg = constructNotification(this.pid, data,  Member.TEAM, Member.SYSTEM, null, null);
//		msg.team = team;
//		msg.message = message;
//		msg.action.statecards = state;
//		msg.level = level;
//		logger.info("システムからの通知メッセージを送信:" + msg.toString());
//		Notifier.dispatch(msg);
		NotificationMessage msg = Notifier.sendTeamNotification(this.team, message, level, state);
		this.addHistory(msg);
		
	}
	/**リプライの内容に従ってシステムステートを追加または削除する*/
	protected void updateSystemState(final ReplyData rep, final String msgid){
		
		if(rep.addstate != null){
			for(String cur : rep.addstate){
				addState(cur, new Date(), msgid);
			}
		}
		if(rep.removestate != null){
			for(String cur : rep.removestate){
				removeState(cur);
			}
		}
	}
	/**アクションの内容に従ってシステムステートを追加または削除する*/
	protected void updateSystemState(final CardData action, final String msgid){
		if(action.addstate != null){
			for(String cur : action.addstate){
				addState(cur, new Date(), msgid);
			}
		}
		if(action.removestate != null){
			for(String cur : action.removestate){
				removeState(cur);
			}
		}
	}
	
	/**自動アクション履歴(アクションID、発火日時)*/
	public HashMap<String, Date> autoActionHistory =  new HashMap<String, Date>();
	
	/***自動アクションを発火する*/
	public synchronized void processTriggerAction(){
		CardData[] auto = CardData.findList(CardData.Types.auto);
		for(CardData cur : auto){
			
			//2022.8 自動アクション複数回実行
			//if(autoActionHistory.containsKey(cur.id))continue;
			if(!this.isAutoActionExecutable(cur))continue;
			
			Member from = Member.getMemberByRole(this.team, cur.from == null ? Member.SYSTEM.role: cur.from);
			Member to = Member.getMemberByRole(this.team, cur.to == null ? Member.TEAM.role  : cur.to);

			if(from == null){
				logger.error("自動アクションのfromに指定されたユーザが定義されていません。"+ cur.toString());
				return;
			}
			if(to == null){
				logger.error("自動アクションのtoに指定されたユーザが定義されていません。"+ cur.toString());
				return;
			}
			
			if(!evaluateAutoAction(cur))
				continue;
			//2021.6.7 実行待機中のアクションを多重登録しないよう修正
			//TODO: 再実行判定が正しくない
			if((!autoActionHistory.containsKey(cur.id) || 
					cur.rerunstate != null) &&
					!this.isActionPending(cur.id)){
				logger.trace("自動アクションを受付:" + cur.name +  ":" + (cur.delay)+"秒後");
				//2022.11.09 自動アクション受付時にrerunstateをadd
				if(cur.rerunstate != null) {
					addState(cur.rerunstate,  new Date(), "gyoza");
				}
				
				List<Member> cc = Member.getMembers(this.team, cur.cc);
				registerTrigger(cur, to, from, cc);
			}
		}
	}
	
	/**自動アクションが実行可能かを評価する*/
	protected synchronized boolean isAutoActionExecutable(CardData act) {
		
		if(act.rerunstate != null && act.rerunstate.length() != 0 &&  
				!this.hasState(act.rerunstate)) {
			//logger.trace("自動アクションの再実行が有効:" + act.id);
			return true;
		}
		
		return !autoActionHistory.containsKey(act.id);
	}
	
	
	
	class AutoActionTimer extends Timer {
		String actionid;
		AutoActionTimer(String actionid){
			super();
			this.actionid = actionid;
		}
	}

	//TODO: tag:virtualtime 仮想時刻ベースのスケジューリングに変更
	/**自動アクションをスケジュール登録する*/
	protected void registerTrigger(CardData cur, Member to, Member from, List<Member> cc) {

		if(isFeatureEnabled("virtualtime")) {
			logger.debug("virtualtime enabled.");
			__registerTrigger(cur, to, from, cc);return;
		}
		
		TimerTask handler = new TimerTask(){
			@Override
			public synchronized  void run() {
				//自動アクション複数実行対応
				//if(!autoActionHistory.containsKey(cur.id)){
				//2022.11.09 自動アクション実行前にrerunかを判定しない
				//if(isAutoActionExecutable(cur)) {
					logger.info("自動アクションを実行:" + cur.name);
					acceptAction(cur,  from, to, cc.toArray(new Member[cc.size()]), null);
					autoActionHistory.put(cur.id, WorkflowInstance.this.getTime());//new Date()); tag:virtualtime
				//}
			}
		};
		AutoActionTimer tm = new AutoActionTimer(cur.id);tm.schedule(handler, cur.delay*1000);
		
		this.autoActionTimers.add(tm);
	}
	/**自動アクションをスケジュール登録する(仮想時刻対応版)*/
	protected void __registerTrigger(CardData cur, Member to, Member from, List<Member> cc) {
		TaskListener l = new TaskListener() {
			@Override
			public void onSkipped(Task t, Date when, String reason) {}
			
			@Override
			public void onExecuted(Task t, Date when) {
				//自動アクション複数実行対応
				//if(!autoActionHistory.containsKey(cur.id)){
				if(cur.rerunstate != null) {
					int dammy = 0;
				}
				//2022.11.09 自動アクション実行前にrerunかを判定しない
				//if(isAutoActionExecutable(cur)) {
					logger.info("自動アクションを実行:" + cur.name);
					acceptAction(cur,  from, to, cc.toArray(new Member[cc.size()]), null);
					autoActionHistory.put(cur.id, WorkflowInstance.this.getTime());//new Date()); tag:virtualtime
				//}
			}
		};
		
		
		if(cur.rerunstate != null) {//for debug
			int dammy = 0;
			
		}
		if(isActionPending(cur.id)) {
			logger.debug("specified action are waiting to be execution." + cur.toString());
			return;
		}
		
		Task task = new Task(cur.id, this.getTime(this.getTime().getTime() + cur.delay * 1000));
		//TODO: 多重実行許可
		//if(cur.rerunstate != null)			task.setMaxCount(10);//仮に
		task.addListener(l);
		//logger.trace("registering new schedule task "+ task.toString());
		this.world.getScheduler().register(task);
	}
	
	
	
	boolean isActionPending(String id) {
		for(AutoActionTimer tm : this.autoActionTimers){
			if(tm.actionid.equals(id))
					return true;
		}
		return false;
	}
	protected void clearAutoActionTimers() {
		for (Timer t: this.autoActionTimers) {
			t.cancel();
		}
		this.autoActionTimers.clear();
	}
	
	ArrayList<AutoActionTimer> autoActionTimers = new ArrayList<AutoActionTimer>();
	/**ステート条件の演算子*/
	public static enum Operator{
		AND,
		NAND,
		OR,
		NOR,
		NOT
	};

	/**システムステートを拡張条件式で評価。
	 * 整数文字列はステート番号として解釈する。
	 * 次の文字列は論理演算子として解釈する。
	 * AND
	 * NOT
	 * OR
	 * NAND
	 * NOR
	 * 論理演算子はいずれか1つだけ定義できる。どれも定義されなければ既定の動作となる。
	 * NOTはオペランドをひとつだけ受け付ける。その他は1つ以上のオペランドを受け付ける(オペランドが1つしかない場合は、常に真(演算子なし)または常に偽(NOT)になる)。
	 * (NAND/NORでも代用できるが、簡単のため実装)
	 * 条件が空の場合は条件なしに真を返す
	 * */
	public static Operator[] OperatorList = new Operator[]{Operator.AND,Operator.NAND, Operator.OR,Operator.NOR,Operator.NOT};

	/**statecondition系プロパティの解析<br>
	 * 演算子とステート番号の配列に分解する
	 * */
	protected Pair<Operator, String[]> extractStateNumbers(final String[] cond){
		try{
			if(cond == null)return new Pair<Operator, String[]>(Operator.OR, new String[0]);
			Collection<String> states = new ArrayList<>();
			Operator operator = null;
			for(String i : cond){
				if(Util.contains(OperatorList, i)){
					if(operator != null) throw new WorkflowException("演算子を複数回使用することはできません。");
					operator = Operator.valueOf(i);
				}else{
					if(i == null || i.length() == 0)continue;
					states.add(i);
				}
			}
			return new Pair<Operator, String[]>(operator, states.toArray(new String[states.size()]));
		}catch(Throwable t){
			throw new WorkflowException("ステート条件の書式が不正です。" + Util.toString(cond), t);
		}
	}
 
	//改修１行目
    public String[] SplitElm(String str0)  {
        int p0 = str0.indexOf(",");
	    List<String> buf = new ArrayList<String>();
        
        while (p0 != -1) {
            String str1;
            List<Integer> P0 = new ArrayList<Integer>();
            List<Integer> P1 = new ArrayList<Integer>();
            List<Integer> P2 = new ArrayList<Integer>();
            p0= str0.indexOf(",");
            int p1 = str0.indexOf("(");
            int p2 = str0.indexOf(")");

            while( p0 !=-1){
            	P0.add(p0);
            	p0 = str0.indexOf(",",p0+1);
            }
            while( p1 !=-1){
                P1.add(p1);
                p1 = str0.indexOf("(",p1+1);
            }
            while( p2 !=-1){
                P2.add(p2);
                if( p2 != str0.lastIndexOf(")")){
                    p2 = str0.indexOf(")",p2+1);
                }else{
                    p2 = -1;
                }

            }

            Integer[] aP0 = P0.toArray(new Integer[P0.size()]);
            Integer[] aP1 = P1.toArray(new Integer[P1.size()]);
            Integer[] aP2 = P2.toArray(new Integer[P2.size()]);
            
            //【】対応エラー
            if (aP1.length != aP2.length ) {
            	System.out.println("カッコ対応エラー：条件式が不正です。 ");
            };

            
            //【】が含まれる
            if (aP1.length !=0){
                //【】の方がコンマより前にある
                if (aP0[0] > aP1[0]){
                	int count = 0 ;
	                int a = 0 ;
	                int b = 0 ;
	                while(count != -1){
	                	if(aP1[a] < aP2[b]){
	                		if(aP1.length > a+1){
	                			a = a+1 ;
	                			count = count +1 ;
	                		}else{
	                			b = a ;
	                			count = -1;
	                			}
	                		}
	                	else if(aP1[a] > aP2[b]){
	                		if(count != 0){
	                			b = b + count -1;
	                			count = 0;
	                		}else{
	                			count = -1;
        					}
        				}
        			}
	                str1 = str0.substring(0,aP2[b]+1);
	                buf.add(str1);
	                if( str0.length() != aP2[b]+1){
	                	str0 = str0.substring(aP2[b]+1+1);
	                }else{
						str0 = null;
					}

                //コンマが【】より前にある時、str0のコンマまでの部分をbufに追加
                }else{
                    str1 = str0.substring(0, aP0[0]);
                    str0 = str0.substring(aP0[0]+1);
                    buf.add(str1);                    
                }
            //コンマはあるけど【】がないとき、
            }else{
                str1 = str0.substring(0, aP0[0]);
                str0 = str0.substring(aP0[0]+1);
                buf.add(str1);

        		}
            
            //whileの処理の一番最後にp0の値を更新            
            if (str0 != null){
                p0 = str0.indexOf(",");
            }else{
                p0 = -1;
        	}
         }
        
        //while文を抜けた後(p0=-1になった時)
        if(str0 != null){
        buf.add(str0);
        }
        return buf.toArray(new String[buf.size()]);
	}
	//終わり
    
	public boolean memberEvalEQ(String userid,String state){
	    String buf[];
	    String logic;
	    boolean result,elm;
	    String str0;
	    int ic;
	    result = true;
	    str0=state.trim();  //すべての空白を除く
	    str0=str0.toUpperCase(); //すべて大文字に変換する
	    
	    int P1=str0.indexOf("(");
	    logic=str0.substring(0,P1-1);
	    int P2=str0.lastIndexOf(")");
	    str0=str0.substring(P1+1,P2-1);
	    
	    buf=SplitElm(str0);
	
	    ic=0;
	    switch (logic){
	    case "AND":
	    case "NOR":
	    case "NOT":
	        result=true;
	        break;
	    case "OR":
	    case "NAND":
	        result=false;
	        break;
	    default:
	        System.out.println("Logic Error "+logic);
	    }
	    while (buf[ic]!=null) {
	        if (buf[ic].indexOf("(")>=0){
	            elm=memberEvalEQ(userid,buf[ic]);
	        }else{
	            elm=memberHasState(userid,buf[ic]);
	        }
	        switch (logic){
	        case "AND":
	            result=elm && result;
	            break;
	        case "NOR":
	            result= !(elm) && result;
	            break;
	        case "NOT":
	            result= !(elm) && result;
	            break;
	        case "OR":
	            result= elm || result;
	            break;
	        case "NAND":
	            result= !(elm) || result;
	            break;
	        
	        }
		ic+=1;
	    }
	    return result;
	}
	/**ステート条件を評価する
	 * @return 条件にヒットしたかどうか
	 * */
	protected  boolean evaluateMemberStateCondition(final String userid, final String cond[], final Operator defaultOperation){
		boolean fl=false;
		if(cond != null) {
	     	if(cond.length == 1) {
	     		if(cond[0].indexOf("(")>=0) {
	     			fl=true;
	     		}
	 		}
		}
	    if (fl) {
	    	return memberEvalEQ (userid, cond[0]) ;	
	    }else {
			Pair<Operator, String[]> p = extractStateNumbers(cond);
			if(p.leader == null) p.leader = defaultOperation;
			boolean and = p.leader == Operator.AND || p.leader == Operator.NAND;
			boolean not = p.leader == Operator.NAND || p.leader == Operator.NOR || p.leader == Operator.NOT;
			boolean result = and ? memberHasAllStates(userid, p.trailer) : memberHasStates(userid, p.trailer);
			boolean ret =  (not ? !result : result);
			if(ret){
				logger.debug("ユーザステート条件がヒットしました: " + (and ? " and " : " or ") + (not ? " not " : " ") + Util.toString(p.trailer) + ":" + ret);
			}
		    return ret;
		}
	}

	/**メンバがいずれかのステートを所有しているか。(OR)*/
	protected boolean memberHasStates(final String userid, final String[] states){
		if(states == null)return true;
		for(String s : states){
			if(memberHasState(userid,s))
				return true;
		}
		return false;
	}
	/**メンバがすべてのステートを所有しているか。(AND)*/
	protected boolean memberHasAllStates(final String userid, final String[] states){
		if(states == null)return true;
		for(String s : states){
			if(!memberHasState(userid, s))
				return false;
		}
		return true;
	}
	/**メンバが指定されたステートを所有しているか。stateがnullならtrue*/
	protected boolean memberHasState(final String userid, final String state){
		if(state == null)return true;
		Set<String> list = this.memberStates.get(userid);
		if(list == null)return false;
		return list.contains(state);
	}
   
	public boolean EvalEQ(String state){
	    boolean result = true;
	    boolean elm = false;
	    String str0 = state;
	    int P1=str0.indexOf("(");
	    String logic=str0.substring(0,P1);
	    int P2=str0.lastIndexOf(")");
	    str0=str0.substring(P1+1,P2);
	    
	    String[] buf=SplitElm(str0);

	    int ic=0;
	    switch (logic){
	    case "AND":
	    case "NOR":
	    case "NOT":
	        result=true;
	        break;
	    case "OR":
	    case "NAND":
	        result=false;
	        break;
	    default:
	        System.out.println("Logic Error "+logic);
	    }
	    while (ic < buf.length && buf[ic]!=null) {//2020/10/7 仮対処:　bufにnull要素がない場合にArrayIndexOutOfBoundsException
	        if (buf[ic].indexOf("(")>=0){
	            elm=EvalEQ(buf[ic]);
	        }else{
	            elm=hasState(buf[ic]);
	        }
	        switch (logic){
	        case "AND":
	            result=elm && result;
	            break;
	        case "NOR":
	            result= !(elm) && result;
	            break;
	        case "NOT":
	            result= !(elm) && result;
	            break;
	        case "OR":
	            result= elm || result;
	            break;
	        case "NAND":
	            result= !(elm) || result;
	            break;
	        
	        }
		ic+=1;
	    }
	    return result;
	}

	/**ステート条件を評価する*/
protected boolean evaluateStateCondition(final String cond[], final Operator defaultOperation) {
	boolean fl=false;
	if(cond != null) {
	 	if(cond.length == 1) {
	 		if(cond[0].indexOf("(")>=0) {
	 			fl=true;
	 		}
			}
	}
	if (fl) {
		return EvalEQ (cond[0]) ;	
	}else {		
			Pair<Operator, String[]> p = extractStateNumbers(cond);
			if(p.leader == null) p.leader = defaultOperation;
	//		if(p.trailer == null || p.trailer.length == 0) return true;
			boolean and = p.leader == Operator.AND || p.leader == Operator.NAND;
			boolean not = p.leader == Operator.NAND || p.leader == Operator.NOR || p.leader == Operator.NOT;
	
			boolean result = and ? hasAllStates(p.trailer) : hasStates(p.trailer);
			
			boolean ret =  (not ? !result : result);
			if(ret){
				logger.debug("ステート条件がヒットしました: " + (and ? " and " : " or ") + (not ? " not " : " ") + Util.toString(p.trailer) + ":" + ret);
			}
			return ret;
		}
	  }

	/**自動アクションのステート条件を評価
	 * 
	 * */
	protected boolean evaluateAutoAction(final CardData action){
		
		if(action.breakpoint) {
			//
			logger.debug("ブレークポイントに到達", action);
		}
		
		boolean hasStates = evaluateMemberStateCondition(action.from, action.statecondition, Operator.AND);
		if(hasStates){
			return true;
		}
		
		if(action.systemstatecondition == null || action.systemstatecondition.length == 0)
			return true;
		return evaluateStateCondition(action.systemstatecondition, Operator.OR);
		
	}
	/***フェーズ開始からの経過時間による(アクションのない)イベントを発火する*/
	public synchronized /*ここでConcurrentModificationExceptionが上がる*/void processTriggerEvent(){
		try{
			CardData data = CardData.find(CardData.Types.notification);
			Collection <ReplyData> replies = ReplyData.loadReply(this.phase);
			
			for(Iterator<ReplyData> i = replies.iterator(); i.hasNext();){
				final ReplyData r = i.next();
				if(!r.isTriggerAction()) continue;
				if(r.elapsed == -1)	continue;
			
				
				//<!--TAG:feature-virtualtime
				
//				Date notbefore = new Date(this.start.getTime() + r.elapsed * 1000);
//				if(!new Date().after(notbefore)){
				Date notbefore = this.getTime(this.start.getTime() + r.elapsed * 1000);
				//TODO: 上の行は変更しなくても良いかもしれない。(startが仮想時刻か実時刻か？)
				if(!this.getTime().after(notbefore)){
					logger.debug(String.format("トリガーイベントは待機中です。宛先:%s, 名前:%s, メッセージ:%s,予定時刻:%s",r.to,r.name,r.message, new SimpleDateFormat("MM/dd HH:mm:ss").format(notbefore)));
					continue;
				}
				//TAG:feature-virtualtime-->
				
				
				//すでに発火済ならスキップ
				if((r.state != null && r.state.length() != 0) &&  triggerIsFired(/*Integer.valueOf(r.state)*/r.state, r.to)){//, r.to))
					continue;
				}
				
				if(!evaluateStateCondition(r.statecondition, Operator.OR))
					continue;
	
				logger.info(String.format("トリガーイベントを送信します。宛先:%s@%s, 名前:%s, メッセージ:%s",r.to, this.team, r.name, r.message));
				//アクションデータをクローンしている。こうしないとアクションキューから取り出したインスタンスを書き換えたときにおかしなことになる。
				CardData data2 = new ObjectMapper().readValue(data.toString(), CardData.class);
				Member to = Member.roleToMember(this.team, r.to);
				Member from = Member.roleToMember(this.team, r.from);
				NotificationMessage msg = Notifier.constructNotification(/*this.pid,*/ data2,  to, from, null, null);
				msg.message = r.message;
				msg.team = this.team;
				msg.sentDate = this.getTime();//new Date(); //tag: virtualtime
	
				String[] statecards = null;
	
				if(msg.action.statecards != null && msg.action.statecards.length != 0){
					statecards = Util.concat(statecards, msg.action.statecards); 
				}
				
				if(r.state != null && r.state.length() != 0){
					statecards = Util.concat(statecards, r.state);
				}else{
					logger.warn("トリガーイベントにステートカードが設定されていません。このイベントは破棄されます。" + r.toString());
					continue;
				}
				
				msg.action.statecards = statecards;
				msg.message = r.message != null ? r.message : "";
				updateSystemState(r, msg.id);
	
				fireTrigger(msg);
	
				Notifier.dispatch(msg);
				this.addHistory(msg);
	
				logger.info(String.format("トリガーイベントを送信しました。宛先:%s, 名前:%s, メッセージ:%s",r.to,r.name, r.message));
	
			}
		}catch(Throwable t ){
			logger.error("トリガーイベント通知に失敗しました。", t);
		}
		
	}
	/**トリガーイベントを発火する*/
	protected void fireTrigger(NotificationMessage msg){
		if(msg == null || msg.action == null || msg.action.statecards == null || msg.action.statecards.length == 0){
			logger.error("トリガーにステートカードがありません。" + msg != null ? msg.toString() : "null");
		}
		for(String state : msg.action.statecards){
			if(msg.to == null || msg.to.role == null){
				logger.warn("トリガーイベントにtoが指定されていません。イベントは無視されます。" +String.valueOf(msg.toString()));
				continue;
			}
			TriggerEvent ev  =findTriggerEvent(state, msg.to.role);
			if(ev != null){
				logger.warn("送信済のトリガーイベントを送信しようとしました。イベントは無視されます。ステート:" +String.valueOf(state));
				continue;
			}
			TriggerEvent newEvent = new TriggerEvent(state, msg.from != null ? msg.from.role : null, msg.to != null ? msg.to.role : null, this.getTime());//tag:virtualtime
			logger.info(String.format("トリガーイベントを履歴に保存しました。メッセージ:%s, from:%s,to:%s, ステート:%s",msg.message, newEvent.from, newEvent.to, newEvent.state));
			logger.info(String.valueOf(this.triggerEvents.size())+ "のトリガーイベントが履歴にあります。");
			
			
			this.triggerEvents.add(newEvent);
		}
		if(msg.reply != null){
			updateSystemState(msg.reply, msg.id);
		}
	}
	
	protected TriggerEvent findTriggerEvent(String state, String to){
		for(TriggerEvent e : this.triggerEvents){
			if(e.state == state && e.to.equals(to))
				return e;
		}
		return null;
	}

	/**自動アクション、トリガーイベントなどの発生条件をスキャンし必要に応じて起動する
	 * */
	class Worker extends TimerTask{
		Object lock = new Object();
		protected Timer timer = new Timer();
		int interval = Integer.parseInt(System.getProperty("check.interval","1000"));
		public Worker(int interval){this.interval = interval;}
		public Worker(){}
		public void start(){
			this.timer.schedule(this, this.interval, this.interval);
			logger.info(String.format("worker started.team=%s, phase=%d", WorkflowInstance.this.team, WorkflowInstance.this.phase));
		}
		public void stop(){
			this.timer.cancel();
			logger.info("worker stoped.");
		}
		
		/**定期的に実行される処理**/
		@Override
		public void run() {
			
			if(frozen) return;
			
			//経過時間に応じて操業レベル変化
			if(!WorkflowInstance.this.isRunning()){
				return;
			}
			updateOperationState();
			try{
				//自動アクションを検査
				processTriggerAction();
				//トリガーイベントを検査
				processTriggerEvent();
				//キューを検査してディスパッチ
				dispatchAction();
			}catch(WorkflowException t){
				logger.error("failed to dispatch action.", t);
			}catch(Throwable t){
				logger.error("failed to dispatch action.", t);
			}
		}
	}
	
	
	
	/**履歴からアクション実行履歴を探索*/
	protected Collection<NotificationMessage> getActionHistory(String actionid){
		Collection<NotificationMessage> ret = new ArrayList<>();
		for(NotificationMessage n : history){
			if(n.action.id.equals(actionid)){
				ret.add(n);
			}
		}
		return ret;
	}
	/**キューからアクション実行要求を探索*/
	protected Collection<NotificationMessage> getActionQueue(String actionid, int order){
		Collection<NotificationMessage> ret = new ArrayList<>();
		for(NotificationMessage n : actionQueue){
			if(n.action.id.equals(actionid) && n.action.currentorder == order){
				ret.add(n);
			}
		}
		return ret;
	}
	
	protected boolean checkStateCondition(NotificationMessage request, ReplyData rep){
		return evaluateStateCondition(rep.statecondition, Operator.OR);
	}
	/**アクションに対するリプライが実行条件を満たすか確認
	 * @param request アクション要求イベント
	 * @param rep リプライ
	 * */
	protected boolean checkReply(NotificationMessage request, ReplyData rep){

		if(!checkStateCondition(request, rep)){
			logger.info(rep.name + ":システムステート条件(statecondition)を満たしていません。");
			return false;
		}
		if(rep.cardcondition != null){
			
			for(String id : rep.cardcondition){
				if(getElapsedTime(id)<0){
					logger.info(rep.name + ":実行条件(cardcondition)を満たしていません。");

					return false;
				}
			}
		}
		if(!rep.checkTimeCondition((act)->getElapsedTime(act)))
			return false;
		
		//リソース制約を判定
		if(rep.constraints != null){
			try{
				Collection<String> actions = (Collection<String>)rep.constraints.get("actionid");
				Integer multiplicity = (Integer)rep.constraints.get("multiplicity");
				if(actions != null && multiplicity != null){
					int c = countQueuedAction(actions.toArray(new String[actions.size()]));
					int m = multiplicity.intValue();
					if(c <=m){
						logger.info(String.format(rep.name + ":多重度条件(constraints)を満たしていません。キューの数:%d, 上限:%d, リプライ:%s", c, m ,rep.toString()));
						return false;
					}
				}
			}catch(Throwable t){
				logger.error(rep.name + ":リソース制約条件の解析に失敗しました。リプライ定義を確認してください。." + rep.toString(), t);
			}
		}
				
		//攻撃パスの判定
		if(rep.attackpath != null) {
			try {
				String[] pathelem = rep.attackpath.split("/");
				int pathelem1;
				int pathelem2;
				pathelem1 = Integer.parseInt(pathelem[1].substring(pathelem[1].length()-2),10);
				pathelem2 = Integer.parseInt(pathelem[2].substring(pathelem[2].length()-2),10);
				if (!evaluateAttackPath(pathelem[0],pathelem1,pathelem2)) {
					logger.info(rep.name + ":パスの条件を満たしていないので失敗");
					return false ;
				}
			}catch(Throwable t) {
				logger.error(rep.name + ":attackpath要素の解析が不可能なため、攻撃パス演算を行いません。attackpathの入力ルールに反していないか確認してください。" + rep.toString(),t);
			}
		}
		
		//確率演算
		if(0 <= rep.probability && rep.probability <= 1) {
			if (!evaluateProbability(rep.probability)) {
				logger.info(rep.name + ":条件を満たしているが確率で失敗");
				return false ;
			}
		}else {
			logger.warn(rep.name + ":確率は0~1の間で設定してください。確率:1として計算します。" + rep.toString());
		}
		
		
		logger.info("リプライを確定しました。" + rep.name + ":"+rep.toString());
		return true;
	}
	
	/**リプライの決定処理で使用するコールバックインタフェース*/
	public interface Validatable<T1, T2> {
		/**
		 * @param t1 チェック処理に必要なパラメタのキー
		 * @return チェック処理に必要なパラメタの値
		 * */
		public T2 get(T1 t1);
	}
	/**アクションキュー内にある指定されたIDのアクションの数を返す*/
	protected int countQueuedAction(final String actionIds[]){
		if(actionIds == null || actionIds.length == 0)return 0;
		int ret = 0;
		for(Iterator<NotificationMessage> i  = actionQueue.iterator(); i.hasNext();){
			NotificationMessage m = i.next();
			for(String id : actionIds){
				if(m.action.id.equals(id))	ret ++;
			}
		}
		return ret;
	}
	
	/**指定されたステートカードのトリガが発火済かを検査**/
	protected boolean triggerIsFired(final String state, final String to){//, final String role){
		for(TriggerEvent ev : this.triggerEvents){
			if(ev.state != null && ev.state.equals(state) && ev.to.equals(to)){
				return true;
			}
		}
		return false;

	}
	/**履歴からアクションが実行されたあとの経過秒数を返す。実行されていなければ-1**/
	protected long getElapsedTime(final String actionId){
		if(actionId == null)return 0;
		for(Iterator<NotificationMessage> i = history.iterator(); i.hasNext();){
			NotificationMessage m = i.next();
			
			if(!m.action.id.equals(actionId))
				continue;
		
			logger.info("履歴にアクションがあります。" + m.action.name);
			if(m.replyTo != null && m.replyDate == null) 
				throw new RuntimeException("バグ: 履歴にreplyDateが設定されていない");
			else if(m.replyDate != null){//tag:virtualtime
				long ret = ((this.getTime().getTime() - m.replyDate.getTime())/1000);
				logger.info(String.format("アクション[%s]は%d秒前に実行されました。",  m.action.name, ret));
				return ret;
			}else if(m.sentDate != null){
				long ret = ((this.getTime().getTime() - m.sentDate.getTime())/1000);
				logger.info(String.format("アクション[%s]は%d秒前に送信されました。",  m.action.name, ret));
				return ret;
			}else{
				throw new RuntimeException("バグ: 履歴にreplyDateが設定されていない(2)");
			}
		}
		logger.info(String.format("アクション[%s]はまだ実行されていません。", actionId));

		return -1;
	}
	/**{@link WorkflowInstance#getElapsedTime(String)}の配列バージョン**/
	protected Map<String, Long> getElapsedTimes(final String[] actions){
		Map<String, Long> ret = new HashMap<String, Long>();
		if(actions == null || actions.length == 0)return ret;
		for(String act : actions) {
			ret.put(act, Long.valueOf(getElapsedTime(act)));
		}
		return ret;
	}
	
	
	/**アクション要求からリプライを作成
	 *   fromとtoを入れ替え
	 *    該当するリプライを設定
	 *    replyDateに現在時刻を設定
	 *    
	 * */
	protected NotificationMessage makeReplyMessage(final NotificationMessage actionRequest){
		NotificationMessage reply = new NotificationMessage(/*this.pid,*/ actionRequest.action, 
				actionRequest.from, actionRequest.to, actionRequest.cc, null);
		reply.sentDate = actionRequest.sentDate;
		int nextorder = 0;
		Collection<NotificationMessage> queued = getActionQueue(actionRequest.action.id, actionRequest.action.currentorder);
		if(queued != null && queued.size() != 0){
			logger.debug("要求されたアクションは実行待ちです。オーダー:" +String.valueOf(actionRequest.action.currentorder));
			nextorder = queued.iterator().next().action.currentorder;
		}
		Collection<ReplyData> replies = ReplyData.findReply(this.phase,  actionRequest.action.id, actionRequest.to, nextorder,  
				actionRequest.action.attachments);//Util.toIntArray(actionRequest.action.attachments));
		if(replies.size() == 0){
			logger.debug(String.format("リプライ候補が見つかりません。エラーリプライを返します。action=%s, phase=%s, to=%s", 
					actionRequest.action.id, String.valueOf(phase), actionRequest.to.toString()));
			reply.reply = ReplyData.getErrorReply(phase, null);
			reply.replyDate = this.getTime();//new Date(); tag:virtualtime
			return reply; 
		}
		logger.debug(replies.size() + "個のリプライ候補がヒットしました。オーダー:" +String.valueOf(nextorder));
		
		
		ReplyData rep = null;
		
		//priorityでリプライ候補をソートする
		ReplyData[] arr=replies.toArray(new ReplyData[replies.size()]);
		List<ReplyData> list =Arrays.asList(arr);
		Collections.sort(list, new Comparator<ReplyData>() {
			@Override
			public int compare(ReplyData o1, ReplyData o2) {
				if(o1.priority ==o2.priority) return 0;
				else if(o1.priority <o2.priority) return 1;
				else return -1;
			}
		});

		for(ReplyData r : list){//TODO: 最初にヒットした応答を返す-
			if(checkReply(actionRequest, r)){rep = r;break;}
		}

		//送信待ちのときは続行、そうでないときはエラー
		if(rep==null){
			logger.warn(String.format("該当するリプライデータが見つかりません。action=%s, phase=%s, to=%s", 
					actionRequest.action.id, String.valueOf(phase), actionRequest.to.toString()));
			return null;
		}else{
			reply.reply = rep;
			logger.debug(String.format("該当するリプライデータが見つかりました。reply=%s, action=%s, phase=%s, to=%s : %s",
					reply.id, actionRequest.action.id, String.valueOf(phase), actionRequest.to.toString(), reply.reply.name));
		}
		reply.replyDate = this.getTime();//new Date(); tag:virtualtime
		return reply;
		
	}
	/**後続リプライがあるか*/
	protected boolean isLastAction(NotificationMessage msg){

		try{	
			Collection<ReplyData> reps = ReplyData.findReply(phase,  msg.action.id, msg.from, msg.action.currentorder + 1, 
					msg.action.attachments);//Util.toIntArray(msg.action.attachments));
			boolean ret = (reps.size() == 0);
			for(ReplyData d : reps.toArray(new ReplyData[reps.size()])){
				if(ReplyData.Types.NOT_FOUND.equalsIgnoreCase(d.type)){
					logger.debug(String.format("アクション%s(%s)には後続リプライがありません(notfound)。", msg.action.id, msg.action.name));
					ret = true;break;//見つからないが候補にあったらもう無理でしょう、たぶん
				}
			}
			
			//リプライ候補がエラーリプライしかないなら後続アクションなしで返す
			if(!ret){
				String desc = "";
				for(Iterator<ReplyData> i = reps.iterator(); i.hasNext();){
					desc += i.next().name + ";";
				}
				logger.debug(String.format("アクション%s(%s)には後続リプライがあります。[%s]", msg.action.id, msg.action.name, desc));
				return ret;
			}else{
				logger.debug(String.format("アクション%s(%s)には後続リプライがありません(空)。", msg.action.id, msg.action.name));
				return ret;
			}
		}catch(WorkflowException t){
			logger.warn(String.format("アクション%s(%s)には後続リプライがありません。(エラー)", msg.action.id, msg.action.name));
			return true;
		}
	}
	/**応答を送信
	 * 
	 * キューにリプライが空のエントリがあれば
	 * 		応答を作成してキューに格納し、元のエントリは削除
	 * キューにリプライが設定されたエントリがあれば、
	 * リプライが空のエントリがあったらリプライを探索
	 * 
	 * */
	protected synchronized void dispatchAction() throws WorkflowException{
		//キューを探索
		if(actionQueue.size() != 0){
			logger.debug(String.format("チーム %s: %d個のアクションが待機中",team, actionQueue.size()));
		}
		
		for(NotificationMessage n: actionQueue){
			try{
				if(n.reply != null) {logger.warn("???? リプライ付きアクションがキューにある...");}

				String[] attachments = n.action.attachments;//Util.toIntArray(n.action.attachments);
				Collection<ReplyData> candidates = ReplyData.findReply(this.phase,  n.action.id, n.to, n.action.currentorder, attachments);

				//キューになければ受付完了通知を送信
				NotificationMessage rep = null;
				
				if(candidates == null || candidates.size() == 0){
					if(!n.to.isSystemUser(phase)){
						NotificationMessage err = new NotificationMessage(/*this.pid, */n.action, n.from, n.to, n.cc, null);
						err.sentDate = n.sentDate;
						err.reply = ReplyData.getNullReply(phase);
						rep = err;
						logger.debug("自動応答ユーザでないため空のリプライを返します。" + n.toString());
					}else{
						NotificationMessage err = new NotificationMessage(/*this.pid, */n.action, n.from, n.to, n.cc, null);
						err.sentDate = n.sentDate;
						err.reply = ReplyData.getErrorReply(phase, null);
						rep = err;
						logger.debug("リプライ候補がないためエラーリプライを返します。" + n.toString());
					}
				}else{
					rep = makeReplyMessage(n);
					if(rep == null){
						logger.debug("リプライ候補の実行条件が満足されていないため保留します。" + n.toString());
						continue;
					}
				}

				//応答可能なら応答
				//if(!new Date().after(new Date(rep.sentDate.getTime() + rep.reply.delay*1000))){
				//tag:virtualtime
				if(!this.getTime().after(this.getTime(rep.sentDate.getTime() + rep.reply.delay*1000))){
//					Date sent = new Date(rep.sentDate.getTime());
					//Date when = new Date(rep.sentDate.getTime() + rep.reply.delay*1000);
					Date when = this.getTime(rep.sentDate.getTime() + rep.reply.delay*1000);
					logger.debug("遅延条件(delay)が満たされていないためリプライを保留します。[" + rep.reply.name + "],予定時刻:" + new SimpleDateFormat("MM/dd HH:mm:ss").format(when));
					continue;
				}
				rep.replyTo = n;
				rep.replyDate = this.getTime();//new Date();
				rep.team = this.team;
				
				updateSystemState(n.action, rep.id);
				updateSystemState(rep.reply, rep.id);
				Notifier.dispatch(rep);
				//履歴に追加
				addHistory(rep);
				if(rep.reply.abort || isLastAction(rep)){
				//アクションキューから削除
					actionQueue.remove(n);
					//2022/12/17 分かりにくいから一旦削除
					//logger.info("キューからアクションを削除しました。"+n.action.name + ":"+toString());
				}else{
					n.action.currentorder ++;
					logger.info("アクションに後続リプライがあるためキューに残します。"+ n.action.name + ":" + toString());
				}
				processAbortAction(rep);
				
				
				logger.info(String.format("リプライを送信しました。%s", rep.toString()));
			}catch (WorkflowException e) {
				throw e;
			}
		}

	}
	protected void processAbortAction(NotificationMessage msg){

		if(msg.action == null ||msg.action.abortaction == null || msg.action.abortaction.length() == 0)
			return;
		for(NotificationMessage act : actionQueue){
			if(msg.action.abortaction.equals(act.action.id)){
				logger.info("アクションを中止しました。"+ act.action.name);
				actionQueue.remove(act);
				return;
			}
		}
		logger.warn("中止対象アクションがキューにありません。" + msg.action.name);
	}
	/**イベント履歴にイベントを追加*/
	protected void addHistory(NotificationMessage m){
		m.replyDate = this.getTime();//tag: virtualtime //new Date();//送信
		history.add(m);

	}
	/**履歴から指定されたイベントを取得*/
	public NotificationMessage getEventHistory(final String id){
		if(id == null || this.history == null || this.history.isEmpty()) return null;
		for(NotificationMessage m : this.history){
			if(m.id.equals(id))return m;
		}
		return null;
	}
	/**指定されたユーザのイベント履歴を取得*/
	public Collection<NotificationMessage> getUserEventHistory(final String role){
		Collection<NotificationMessage> ret = new ArrayList<>();
		if(role == null || this.history == null || this.history.isEmpty()) return null;
		for(NotificationMessage m : this.history){
			if(m.to != null && m.to.matches(role)) ret.add(m);

			if(m.cc != null){
				for(Member mm : m.cc){
					if(mm.role.equals(role))ret.add(m);
				}
			}

			if(m.from.role.equals(role))ret.add(m);
		}
		return ret;
	}
	/**システムステートを返す*/
	protected Collection<StateData>  getSystemStateData(){
		Collection<StateData> ret = new ArrayList<>();
		Collection<StateData> all = StateData.loadAll();

		for(Iterator<StateData> i = all.iterator(); i.hasNext();){
			StateData cur = i.next();
			Object d = systemState.get(String.valueOf(cur.id));
			if(d != null){
				if(d instanceof Date)
					cur.when = (Date)d;
				else if(d instanceof Long)//tag:virtualtime
					cur.when = this.getTime(((Long)d).longValue());
			}
			ret.add(cur);
		}
		return ret;
	}
	
	//for serialize
	public NotificationMessage[] getActionQueue(){
		return this.actionQueue.toArray(new NotificationMessage[this.actionQueue.size()]);
	}
	public NotificationMessage[] getHistory(){
		return this.history.toArray(new NotificationMessage[this.history.size()]);
	}
	public Map<String, Object> getSystemState(){
		return this.systemState;
	}
	//TODO:仮想時刻機能を有効にすると正しいデータを取得できない
	public TriggerEvent[] getTriggerEvent(){
		return this.triggerEvents.toArray(new TriggerEvent[this.triggerEvents.size()]);
	}
	/**アクションキューのリストを置き換える*/
	public void setActionQueue(NotificationMessage[] m){
		this.actionQueue.clear();
		if(m == null || m.length == 0)return;
		for(NotificationMessage msg : m){
			this.actionQueue.add(msg);
		}
	}
	/**イベント履歴のリストを置き換える*/
	public void setHistory(NotificationMessage[] m){
		this.history.clear();
		if(m == null || m.length == 0)return;
		for(NotificationMessage msg : m){
			this.history.add(msg);
		}
		
	}
	/**システムステートを追加*/
	public void setSystemState(Map<String,Object> s){
		this.systemState.clear();
		if(s == null || s.size() == 0)return;
		for(String state: s.keySet()){
			Object val = s.get(state);
			this.systemState.put(state, val);
		}
	}
	
	/**@deprecated
	 *  not used*/
	public void setTriggerEvent(TriggerEvent[] e){
		this.triggerEvents.clear();
		if(e == null || e.length == 0)return;
		for(TriggerEvent t : e){
			t.date = adjustDate(t.date);
			this.triggerEvents.add(t);
		}
	}
	
	
	/**
	 * not used
	 * */
	/**時刻情報を矯正*/
	protected NotificationMessage adjustDate(NotificationMessage org){
		if(org == null)return null;
		NotificationMessage ret = org.clone();
		ret.sentDate = adjustDate(ret.sentDate);
		ret.replyDate = adjustDate(ret.replyDate);
		if(ret.replyTo != null){
			ret.replyTo = adjustDate(ret.replyTo);//再帰する
		}

		return ret;
				
	}
	
	
	/**this.savedから現在時刻の差分だけ時刻を補正する*/
	protected Date adjustDate(Date org){
		if(org == null)return null;
		Date ret = (Date)org.clone();
		if(this.saved == null){
			logger.warn("saved date not avairable.");
			this.saved = this.getTime();//tag:virtualtime //new Date();
		}
		//long offset = new Date().getTime() - this.saved.getTime();//開始時刻じゃなくて
		long offset = this.getTime().getTime() - this.saved.getTime();//開始時刻じゃなくて

		ret.setTime(ret.getTime() + offset);
		return ret;
	}
	/**ワークフローの状態を保存のためにシリアライズする*/
	public String save(){
		try{
			if(this.saved == null)
				this.saved = this.getTime();//new Date(); tag:virtualtime
			String ret = new ObjectMapper().writeValueAsString(this);
			return ret;
		}catch(Throwable t){
			logger.error("ワークフローインスタンスのシリアライズに失敗しました。", t);
			return "{}";
		}
	}
	/**ワークフローの状態を復元する*/
	public static WorkflowInstance load(final String data) throws WorkflowException{
		try{
				WorkflowInstance inst = new ObjectMapper().readValue(data, WorkflowInstance.class);
				inst.shift();
				//inst.saved = null;
				inst.resume();
			return inst;
		}catch(Throwable t){
			logger.error("ワークフローデータのロードに失敗しました。", t);
			throw new WorkflowException("データ形式が不正です。" + t.getMessage(), HttpServletResponse.SC_BAD_REQUEST, t);
		}
	}
	/**
	 * 日時をシフトする
	 * */
	protected void shiftDate(Date target, long diff){
		if(target == null)return;
		
		long val = target.getTime() + diff;
		target.setTime(val);
	}
	/**
	 * ワークフローの日時をシフトする<br>
	 * 保存されたワークフロー状態を復元したときに日時情報を補正するために使用する
	 * */
	protected void shift() throws WorkflowException{
		long now = new Date().getTime();//今

		if(this.saved == null)
			throw new WorkflowException("保存日時が設定されていません。");
		long saved = this.saved.getTime();
		
		for(String l : this.systemState.keySet()){
			Object when = this.systemState.get(l);
			if(when != null){
				Long value = Long.valueOf(now - saved +((Long)when).longValue());
				this.systemState.put(l, value);
			}
		}
		long diff = now - saved;
		for(TriggerEvent e : this.triggerEvents){
			shiftDate(e.date, diff);
		}
		for(NotificationMessage m : this.history){
			shiftRecursive(m, diff);
		}
		for(NotificationMessage m : this.actionQueue){
			shiftRecursive(m, diff);
		}
		shiftDate(this.start, diff);
	}
	/**イベント履歴の日時をシフトする<br>
	 * イベントの返信元イベントに対してもシフト処理を行う
	 * */
	protected void shiftRecursive(NotificationMessage m, long diff){
		if(m == null)return;
		shiftDate(m.sentDate, diff);
		shiftDate(m.replyDate, diff);
		if(m.reply != null){
			shiftDate(m.reply.fireWhen, diff);
		}
		if(m.replyTo != null)
			shiftRecursive(m.replyTo, diff);
	}
	
	
	

	/**ステートカードが追加されたときの処理<br>
	 * 現在の実装ではポイントカードを評価する
	 * */
	protected void onAddState(final String state, final String msgid){
		processControlState(state, true);
		evaluateScore(state, msgid);
	}
	/**ステートカードが削除されたときの処理<br>
	 * 現在の実装では何もしない
	 * */
	protected void onRemoveState(final String state){
		//
		processControlState(state, false);
	}
	/**発行されたポイントカードのIDと、発行の引き金になったリプライIDのマップ*///発行日時がいいのか
	protected HashMap<String, Collection<String>> pointHistory = new HashMap<>();
	
	/**ポイントカードを評価し、ポイントカード取得履歴を更新する*/
	protected synchronized void evaluateScore(final String state, final String msgid){
		PointCard[] points = PointCard.list(this.phase);
		for(PointCard  cur : points){
			if(checkPointcards(state, cur)){
				//付与
				this.score += cur.point;
				Collection<String> replies = this.pointHistory.get(cur.id);
				if(replies == null){this.pointHistory.put(cur.id, replies = new ArrayList<String>());}
				replies.add(msgid);
				if(!this.pointchest.contains(msgid)){this.pointchest.put(msgid,new ArrayList<PointCard>());}
				this.pointchest.get(msgid).add(cur);
				logger.info("ポイントカードを取得:" + this.team + ";" + cur.toString());
			}
		}
	}
	/**ポイントカード取得時の追加処理。現在の実装ではNOP
	 * */
	protected void onGetPointCard(final PointCard point, final ReplyData src){}
	/**ポイントカード多重度をチェック。パスしたらtrue。*/
	protected synchronized boolean checkPointcards(final String state, final PointCard pt){
		if(!this.isRunning())return false;
		
		if(pt.state!= null && !pt.state.equals(state)){
			return false;
		}
		logger.info("システムステート" + state.toString() + "に対するポイントカードを評価中");
		//多重度チェック
		
		Collection<String> hist = pointHistory.get(pt.id);
		if(hist != null && !hist.isEmpty() && hist.size() >= pt.multiplicity){
			logger.info(pt.name  + ":ポイント多重度超過:" + String.valueOf(pt.multiplicity));
			return false;
		}
		
		//期間チェック
		if(this.start == null)return false;
		Date now = this.getTime();//tag:virtualtime //new Date();
		if(pt.before != 0 && (this.start.getTime() + pt.before*1000 > now.getTime())){
			logger.info(pt.name + ":ポイント期限切れ:" + pt.toString());
			return false;
		}
		if(pt.after != 0&& (this.start.getTime() + pt.after*1000 < now.getTime())){
			logger.info(pt.name + ":ポイント期間未達:" + pt.toString());
			return false;
		}

		if(pt.statecondition != null){
			if(!evaluateStateCondition(pt.statecondition,Operator.AND))
				return false;
		}
		logger.info("ポイント獲得条件をパス:" + this.team + ";" + pt.toString());
		return true;
	}

	
	/**イベントループを一時停止する(デバッグ用)*/
	public WorkflowInstance freeze() {
		if(this.worker == null) return this;
		this.frozen = true;
		logger.info("Workflow worker suspended.");
		return this;
	}
	/**イベントループを再開する(デバッグ用)*/
	public synchronized WorkflowInstance melt() {
		if(this.worker == null) return this;
		this.frozen = false;
		logger.info("Workflow worker resumed.");
		return this;
	}

	
	//-------------------feature virtual-time-------------------------------------------------
	/***/
	@XmlElement
	public World world  = null;
	public WorkflowInstance startWorld() {
		logger.enter();
		this.world = new World(this.team).initialize();
		return this;
	}
	public boolean validateCurrentWorld() {
		if(this.world == null)return false;
		return true;
	}

//	public boolean procesSystemAction(String actionid) {
//		if(Util.contains(CardData.SYSTEM_ACTIONS.class.getFields(), actionid)) {
//			return true;
//		}
//		
//		return false;
//	}
	
	/**一時停止*/
	public boolean suspend(){
		logger.info("suspend:" +this.toString());

		if(!validateCurrentWorld()){
			logger.error("world not initialized.");
			return false;
		}
		if(this.world.isPaused()) {
			logger.warn("system already suspended.");
			return false;
		}
		
		this.world.pause();
		NotificationMessage msg = NotificationMessage.makeBroadcastMessage("演習が一時停止されました。", CardData.Types.notification);
		msg.sentDate = this.world.getTime();
		Notifier.broadcast(msg);
		
		this.state = State.SUSPENDED;
		this.saved  = new Date();
		if(this.worker != null){
			this.worker.stop();
		}
		return true;
	}

	/**一時停止から復帰*/
	public boolean resume(){
		
		logger.info("resume:" +this.toString());

		if(!this.world.isPaused()) {
			logger.warn("system not paused.");
			return false;
		}

		if(!validateCurrentWorld()){
			logger.info("world not initialized.");
			return false;
		}else {
			this.world.resume();
			NotificationMessage msg = NotificationMessage.makeBroadcastMessage("演習が再開されました。", CardData.Types.notification);
			msg.sentDate = this.world.getTime();//tag: virtualtime
			Notifier.broadcast(msg);
		}
		
		this.state = State.STARTED;

		this.worker = new Worker();
		this.worker.start();

		logger.info("resumed:" +this.toString());
		return true;
	}

	
	protected void processControlState(String id, boolean add) {
		if(id.equals(StateData.SYSTEM_STATES.PAUSE)) {
			if(add) {
				logger.info("コントロールステートにより演習を一時停止します。");
				this.suspend();
			}else {
				logger.info("コントロールステートにより演習を再開します。");
				this.resume();
			}
		}else if(id.equals(StateData.SYSTEM_STATES.START)){
			logger.info("コントロールステートにより演習を開始します。");
			this.start(this.phase);
		}else if(id.equals(StateData.SYSTEM_STATES.ABORT)) {
			logger.info("コントロールステート演習を停止します。");
			this.abort();
		}
	}
	
	
	protected Date getTime() {
		if(this.world == null) {
			throw new WorkflowException("仮想時刻が初期化されていません。");
		}
		return this.world.getTime();
	}
	protected Date getTime(long tm) {
		if(this.world == null) {
			throw new WorkflowException("仮想時刻が初期化されていません。");
		}
		return this.world.getTime(tm);
	}
//	public PhaseData getActivePhase() {
//		List<PhaseData> p = PhaseData.loadAll();
//		for(PhaseData d : p) {
//			if(d.phase == this.phase) return d;
//		}
//		return null;
//	}
	
	public ScenarioData getActiveScenario() {
		
		return ScenarioData.load(this.scenarioName);
	}
	public boolean isFeatureEnabled(String feature) {
		ScenarioData d = getActiveScenario();
		if(d == null) return false;
		return d.isFeatureEnabled(feature);
		
	}

	//成功確率の計算　2022/11/03
	public boolean evaluateProbability(double p) {
		if (p > Math.random()) {
			return true ;
		}else{
			return false ;
		}
    }

	//C行列の要素を変える関数　2022/12/20
	public void C_calculate(int asset_number, String change){
		int i,j;
		int as = asset_number;
		String add = "add";
		String remove = "remove";

		if(change.equals(add)){
			for(i=0; i < elem; i++){
				C[i][as] = 0;
				C[as][i] = 0;
			}
		}else if(change.equals(remove)){
			for (i=0; i < elem; i++) {
				if(C0[as][i] != 0 && C[i][i] == 1) {  
					C[as][i] = 1;
				}
				if(C0[i][as] != 0 && C[i][i] == 1){
					C[i][as] = 1;
				}
			}
			C[as][as] = 1;
		}
		//結果確認(接続行列C)
		System.out.print("C["+"\r\n");
		for(i=0; i < elem; i++){
			for(j=0; j < elem; j++){
				if (j==elem){
					System.out.print( C[i][j] );
				}else{
					System.out.print( C[i][j] + ",");
				}
			}
			System.out.print("\r\n");
		}
		System.out.print("]" + "\r\n");
		
	}

	//D行列の要素を変える関数　2022/12/20
	public void D_calculate(int asset_number, String change){
		int i,j;
		String add = "add";
		String remove = "remove";
		int as = asset_number;

		if(change.equals(add)){
			D[as][as] = 1;
		}else if(change.equals(remove)){
			D[as][as] = 0;
		}

		//結果確認(陥落行列D)
		System.out.print("D["+"\r\n");
		for(i=0; i < elem; i++){
			for(j=0; j < elem; j++){
				System.out.print( D[i][j] + ",");
			}
			System.out.print("\r\n");
		}
		System.out.print("]" + "\r\n");
	}

	//B行列の要素を変える関数　2022/12/20
	public void B_calculate(int asset_number, String change){
		int i,j;
		String add = "add";
		String remove = "remove";
		int as = asset_number;

		if(change.equals(add)){
			B[as][as] = 1;
		}else if(change.equals(remove)){
			B[as][as] = 0;
		}

		//結果確認(脆弱性行列B)
		System.out.print("B["+"\r\n");
		for(i=0; i < elem; i++){
			for(j=0; j < elem; j++){
				System.out.print( B[i][j] + ",");
			}
			System.out.print("\r\n");
		}
		System.out.print("]" + "\r\n");
	}

	//D_inf行列の要素を変える関数　2022/12/20
	public void D_inf_calculate(int asset_number, String change){
		int i,j;
		String add = "add";
		String remove = "remove";
		int as = asset_number;

		if(change.equals(add)){
			D_inf[as][as] = 1;
		}else if(change.equals(remove)){
			D_inf[as][as] = 0;
		}

		//結果確認(陥落行列D_inf)
		System.out.print("D_inf["+"\r\n");
		for(i=0; i < elem; i++){
			for(j=0; j < elem; j++){
				System.out.print( D_inf[i][j] + ",");
			}
			System.out.print("\r\n");
		}
		System.out.print("]" + "\r\n");
	}
	
	//感染拡大の計算(B,C,D_infの行列の要素が変化する度に計算)　2022/12/20
	public void INF_calculate(){
		int i,j,k,n;
		String state_elem ;
		int[][] v = new int[elem][1];
		int[][] r = new int[elem][1];
		int[][] RE = new int[elem][elem];
		int[][] R = new int[elem][elem];
		int[][] R0 = new int[elem][elem];
		int[][] R1 = new int[elem][elem];

		for(i=0; i<elem ; i++){
			v[i][0] = 1 ;
		}
		//2022.01.26　リモートPCが感染しても被害が広がらない問題:今のところデータの作り方で対処

		//R0,R = (B+D_inf)*C
		for(i=0; i < elem; i++){
			for(j=0; j < elem; j++){
				for(k=0; k < elem; k++){
					R0[i][j] += (B[i][k]+D_inf[i][k] )* C[k][j];
				}
				R[i][j] = R0[i][j];
			}
		}

		//for n=1 to elem-1
		//RE=(R+eye(elem))
		//R=RE*R0
		for(n=1; n <elem; n ++){
			for(i=0; i < elem; i++){
				for(j=0; j < elem; j++){
					RE[i][j] = (R[i][j] + eye[i][j] );
				}
			}
			for(i=0; i < elem; i++){
				for(j=0; j < elem; j++){  
					for(k=0; k < elem; k++){
						if(k==0){
							R[i][j] = RE[i][k] * R0[k][j];
						}else{
							R[i][j] += RE[i][k] * R0[k][j];
						}
					}
				}
			}
		}

		//R1=R*D
		for(i=0; i < elem; i++){
			for(j=0; j < elem; j++){
				for(k=0; k < elem; k++){
					if(k==0){
						R1[i][j] = R[i][k] * D_inf[k][j];
					}else{        
						R1[i][j] += R[i][k] * D_inf[k][j];
					}
				}
			}
		}
		
		//R=R1*vベクトル
		for(i=0; i < elem; i++){
			for(k=0; k < elem; k++){
				if(k==0){
					r[i][0] = R1[i][k]* v[k][0];
				}else{
					r[i][0] += R1[i][k]* v[k][0];
				}      
			}
		}

		//攻撃対象が攻撃可能かを判定する
		for(i=1; i<elem; i++){
			//新しく感染した機器をステート追加
			if (r[i][0]!=0 && D_inf[i][i]==0){
				state_elem = String.format("%02d",i) + "/INF";
				String INF_state = StateData.getStateID(state_elem);
				if(INF_state == null){
					logger.warn("存在しないINFステートを追加しようとしました。" + state.toString());
				}				
				addState(INF_state,  new Date(), "ikura");
			}
		}
	}
	
	//attackpath の計算
	public boolean evaluateAttackPath(String str,int from, int to){
		int i,j,k,n,result;
		int[][] v = new int[1][elem];
		int[][] u = new int[elem][1];
		int[][] r = new int[elem][1];
		int[][] RE = new int[elem][elem];
		int[][] RD = new int[elem][elem];
		int[][] R = new int[elem][elem];
		int[][] BR = new int[elem][elem];
		u[from][0] = 1 ;
		v[0][to] = 1 ;
		
		int fwI_diag;
		int vpn_diag;
		
		//元のFWの状態を保存
		fwI_diag =D[fwI][fwI] ;	
		//元のvpnの状態を保存
		vpn_diag =D[vpn][vpn] ;

		
		if(str.contains("D")){
			if(D[ra][ra] == 1 && C[ra][ra] == 1){
				//リモートアクセスPCが攻略済みだった場合、VPNを攻略済みとして扱う。
				//D行列のVPNの対角成分を1にする
				D[vpn][vpn] = 1;
			}
			/*フィッシング等、内部者の過失によってネットワーク内部の機器を侵害された場合、侵害された機器からインターネットまでの道の途中にあるFWは攻撃者の通信を通してしまうため、
	    	攻撃パスの判定中はそのFWを攻撃済みのものとして扱う */
			for(j = fwI; j < fw0; j++){
				if(j != ra && D[j][j] == 1 && C[j][j] == 1){
					//D行列のFWの対角成分を1にする
					D[fwI][fwI] = 1;
					break;
				}
			}
		}

		//R = C or C*D
		for(i=0; i < elem; i++){
			for(j=0; j < elem; j++){
				if(str.contains("D")){
					for(k=0; k < elem; k++){
						R[i][j] += C[i][k]*D[k][j];
					}
				}else{
					R[i][j] = C[i][j];
				}				
			}
		}

		//for n=1:elem-1
		// R=(R+eye(elem))*C
		for(n=1; n <elem; n ++){
			for(i=0; i < elem; i++){
				for(j=0; j < elem; j++){
					RE[i][j] = (R[i][j] + eye[i][j] );
				}
			}
			for(i=0; i < elem; i++){
				for(j=0; j < elem; j++){     
					for(k=0; k < elem; k++){
						if(k==0){
							if(str.contains("D")){
								RD[i][j] = RE[i][k] * D[k][j];
								R[i][j] = RD[i][k] * C[k][j];
							}else{
								R[i][j] = RE[i][k] * C[k][j];
							}							
						}else{
							if(str.contains("D")){
								RD[i][j] += RE[i][k] * D[k][j];
								R[i][j] += RD[i][k] * C[k][j];
							}else{
								R[i][j] += RE[i][k] * C[k][j];
							}							
						}
					}
				}
			}
		}

		//BR=B*R or R
		for(i=0; i < elem; i++){
			for(j=0; j < elem; j++){
				if(str.contains("B")){
					for(k=0; k < elem; k++){
						if(k==0){
							BR[i][j] = B[i][k] * R[k][j];
						}else{        
							BR[i][j] += B[i][k] * R[k][j];
						}
					}
				}else{
					BR[i][j] = R[i][j];
				}
			}
		}

		//r=BR*v 
		for(i=0; i < elem; i++){
			for(k=0; k < elem; k++){
				if(k==0){
					r[i][0] = BR[i][k]* u[k][0];
				}else{
					r[i][0] += BR[i][k]* u[k][0];
				}      
			}
		}

		if(str.contains("D")){
			//fwのD行列をもとに戻す
			D[fwI][fwI] = fwI_diag;	
			//vpnのD行列をもとに戻す
			D[vpn][vpn] = vpn_diag;
		}

		result =0;
		for(k=0; k < elem; k++){
			result += v[0][k] * r[k][0];
		}

		//攻撃対象が攻撃可能かを判定する
		if (result==0){
			return false;
		}else{
			return true;
		}
	}
	
}
