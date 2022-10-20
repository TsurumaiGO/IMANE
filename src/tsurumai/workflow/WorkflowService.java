package tsurumai.workflow;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;
import javax.security.auth.login.LoginException;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.json.JSONObject;

//import com.fasterxml.jackson.core.JsonFactory;
//import com.fasterxml.jackson.core.JsonGenerator;
//import com.fasterxml.jackson.core.json.JsonGeneratorImpl;
import com.fasterxml.jackson.databind.ObjectMapper;

import tsurumai.workflow.model.CardData;
import tsurumai.workflow.model.Member;
import tsurumai.workflow.model.PhaseData;
import tsurumai.workflow.model.PointCard;
import tsurumai.workflow.model.ReplyData;
import tsurumai.workflow.model.ScenarioData;
import tsurumai.workflow.model.SessionData;
import tsurumai.workflow.model.StateData;
import tsurumai.workflow.model.ValidationResult;
import tsurumai.workflow.model.ValidationResultSet;
import tsurumai.workflow.util.ServiceLogger;
import tsurumai.workflow.util.Util;
import tsurumai.workflow.vtime.WorldService;

/**Webサービスの外部インタフェースを実装する*/
@WebListener
@ApplicationPath("/workflow/*")
@Path("/")
public class WorkflowService extends Application implements ServletContextListener{
	
	protected static ServiceLogger logger = ServiceLogger.getLogger();
	
	
	/**シナリオセット配備先ディレクトリ*/
	protected static String scenarioBase = "";
	/**現在アクティブなシナリオセットの名前*/
	protected static String activeScenario = "_default_"; 
	
	protected static String resourceBase = System.getProperty("workflow.resource", "$CONTEXT/resource");
	/**map<sessionkey, ProcessInstanceManager>*/
//	protected static Map<String, ProcessInstanceManager> sessionCache = Collections
//			.synchronizedMap(new HashMap<String, ProcessInstanceManager>());
//	String server = System.getProperty("workflow.server", "localhost");
	
	protected static Map<String, ProcessInstanceManager.Session> sessionCache = Collections
			.synchronizedMap(new HashMap<String, ProcessInstanceManager.Session>());
	String server = System.getProperty("workflow.server", "localhost");
	
	
	/**URL相対パスをローカルパスに変換する
	 * フルパスの場合はそのまま返す
	 * @param relative 相対URL
	 * */
	public static String getContextRelativePath(final String relative){
		if(Util.isAbsolutePath(relative))
			return relative;
		
		if(context != null )
			return context.getRealPath(relative);
		else{
			logger.warn("サーブレットコンテキストが初期化されていません。リソースは正常にロードできない可能性があります。" + relative);
			return relative;
		}
	}
	public static String getContextRelativePath(final String relative, final String alternative){
		if(context != null )
			return context.getRealPath(relative);
		else{
			logger.warn("サーブレットコンテキストが初期化されていません。リソースは正常にロードできない可能性があります。" + relative);
			return relative;
		}
	}
	@Singleton
	protected static ServletContext context;
	
	@PostConstruct
	protected void postConstruct(){
		//reload();
	}
	/**サーブレット初期化時に呼び出される。シナリオデータの検証を行う。*/
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		logger.info("contextInitialized called.");

		ServletContextListener.super.contextInitialized(sce);

		context = sce.getServletContext();//ctx;
		Notifier.setContext(context);

		logger.info("コンテキストルートのパス: " + context.getRealPath("."));
		logger.info("カレントディレクトリ: " + java.nio.file.Path.of(".").toAbsolutePath());
		
		String basedir = System.getProperty("scenarioBase", context.getRealPath("data"));
		if(!new File(basedir).exists())
			throw new RuntimeException("シナリオデータの配備先が存在しません:" + basedir);
		if(!basedir.equals(WorkflowService.scenarioBase)){

		}
		scenarioBase = basedir;
		changeScenario(null);
	}
	@Override
	public Set<Class<?>> getClasses() {
	
		Set<Class<?>> ret = new HashSet<>();
		ret.add(this.getClass());
		ret.add(WorldService.class);
		return ret;
		
	}
	protected String resolveScenarioName(String relname) {
		String dir = (relname != null && relname.length() != 0) ? (scenarioBase + File.separator + relname) :scenarioBase;
		return dir;

	}
	/**シナリオセットを変更する*/
	protected void changeScenario(final String relname){
		String dir = resolveScenarioName(relname);
		logger.info("シナリオセットが変更されました:" +dir);

		ReplyData. reload(dir + File.separator + "replies.json");
		CardData.reload(dir  + File.separator + "actions.json");
		Member.reload(dir + File.separator + "contacts.json");
		StateData.reload(dir + File.separator + "states.json");
		PhaseData.reload(dir + File.separator + "setting.json");
		PointCard.reload(dir + File.separator + "points.json");
		//validateScenarioSet();
		activeScenario = relname == null ? "_default_" : relname;
		initializeProcesses();

	}
//	public PhaseData getActiveScenario() {
//		List<PhaseData> p = PhaseData.loadAll();
////TODO:
//		return null;
//	}

	/**ユーザIDに対応するセションデータを取得する*/
	public static SessionData getSessionByUserId(final String userId){
		try{

			for(ProcessInstanceManager.Session session : sessionCache.values()){
				if(session.getUserName().equalsIgnoreCase(userId))
					return new SessionData(session, null, null, null);//context.getRealPath("data"));
			}
			return null;
		}catch(Throwable t){
			return null;
		}
	}
	/**セションキーを保持するHTTP拡張ヘッダの名前(X-ssc-sessionkey)*/
	protected static String AUTH_HEADER = "X-ssc-sessionkey";

	@Context
	HttpSession session;
	@Context
	HttpServletRequest request;
	/**HTTPリクエストヘッダからセションキーを取得する*/
	protected ProcessInstanceManager getSession() throws WorkflowSessionException {
		try {
			String skey = request.getHeader(AUTH_HEADER);
			if (skey == null) {
				throw new WorkflowException("ログインしていません。", HttpServletResponse.SC_UNAUTHORIZED,true);
			}
			
			
			ProcessInstanceManager mgr = ProcessInstanceManager.getSession();
			ProcessInstanceManager.Session session = sessionCache.get(skey);
			if(session != null && session.validate())
				return mgr;
			
			throw new WorkflowSessionException("ログインしていません。",HttpServletResponse.SC_UNAUTHORIZED, null, true);
		} catch (Throwable t) {
			if(t instanceof WorkflowSessionException || t instanceof WorkflowWarning)	throw t;//ログ出力抑制のため再スロー
			throw new WorkflowSessionException("セションが無効です。",HttpServletResponse.SC_INTERNAL_SERVER_ERROR,null, true);
		}
	}
	protected SessionData getSessionData() throws WorkflowSessionException{
		ProcessInstanceManager mgr  = ProcessInstanceManager.getSession();
		ProcessInstanceManager.Session session = sessionCache.get(request.getHeader(AUTH_HEADER));
		SessionData s = new SessionData(session, request.getHeader(AUTH_HEADER), null, null);// context.getRealPath("data"));

		return s;
	}

	static {
		logger.info("started");
	}
	
	/**ログインする。
	 *<p class =”interface">POST /session</p>
	 * 
	 *@param userid ユーザID(メールアドレス形式)
	 *@param passwd パスワード。contacts.jsonでpasswd属性が設定されていない場合はパスワードなしとしてログインする。
	 * */
	@Path("/session")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes({MediaType.APPLICATION_FORM_URLENCODED,MediaType.APPLICATION_JSON})
	@POST
	public SessionData login(@FormParam("userid") String userid, @FormParam("password") String passwd,
			@FormParam("server") String server, @FormParam("admin") boolean asAdmin) throws WorkflowException {
		try {
			enter();
			logger.enter(userid, server, asAdmin);
			String sv = server == null || server.length() == 0 ? this.server : server;
			ProcessInstanceManager mgr = ProcessInstanceManager.getSession();
			
			ProcessInstanceManager.Session session = mgr.login(sv, userid, passwd, asAdmin);
			String skey = Util.random();
			sessionCache.put(skey, session);
			SessionData ret = new SessionData(session, skey, passwd, null);
			logger.info("user " + userid + " logged in as " + skey);
			return ret;
		}catch(WorkflowException t){
			throw t;
		} catch (LoginException t) {
			throw new WorkflowException(t.getMessage(), HttpServletResponse.SC_UNAUTHORIZED, t);
		} finally {
			exit();
		}
	}
	/**アクティブなWFプロセスを返す
	 * @param session プロセスが割り当てられたチームの参加者のセションを指定する
	 * */
	protected long[] getActiveProcess(SessionData session)  throws WorkflowException{

		ProcessInstance[] procs = getSession().listProcesses();
		if(procs == null || procs.length == 0) throw new WorkflowException("ワークフローのプロセスが見つかりません。",HttpServletResponse.SC_NOT_FOUND);
		long[]  ids =  new long[procs.length];
		for(int i = 0; i < procs.length; ids[i] = procs[i].getId(),i++);
		
		return ids;
	}
	/**マップを外部データで上書きする*/
	protected Map<String, String> mapParamValues(Map<String, String> params, final String teamname) throws WorkflowException{
		try{
			Properties mapping = new Properties();
			mapping.load(ClassLoader.getSystemResourceAsStream("mappings." + teamname + ".conf"));
			
			for(String key : params.keySet()){
				String val = mapping.getProperty(key);
				if(val != null){
					params.put(key, val);
				}
			}
			return params;
		}catch(Throwable t){
			throw new WorkflowException("ワークフローのパラメタの設定に失敗しました。" + teamname, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, t);
		}
	}
	/**
	 * セションキーに対応するセションを返す。
	 * @param skey セションキー。ログインに成功したときに返却されるSessionDataに格納されている。
	 * */
	public static synchronized SessionData getSession(final String skey) throws WorkflowException {
		try{
			if(skey == null) throw new WorkflowException(HttpServletResponse.SC_BAD_REQUEST);
			if(!sessionCache.containsKey(skey)) throw new WorkflowException("セションキーが無効です。", HttpServletResponse.SC_UNAUTHORIZED);
			
			//ProcessInstanceManager.Session ws = sessionCache.get(skey).session;
			ProcessInstanceManager.Session ws = sessionCache.get(skey);
			return new SessionData(ws, skey, null, null);//context.getRealPath("data"));
		}catch(Throwable t){
			throw new WorkflowException("ログインしていません。", HttpServletResponse.SC_UNAUTHORIZED, t);
		}
	}
	
	/**現在のセションを返す。*/
	public SessionData getCurrentSession() throws WorkflowException{
		String skey = request.getHeader(AUTH_HEADER);
		return getSession(skey);
	}
	/**ログインユーザの情報を返す。ログインしていなければnull。*/
	public Member getCurrentMember() throws WorkflowException{
		SessionData s = getCurrentSession();
		if(s == null) return null;
		return Member.getMember(s.userid);
	}

	/**セションを破棄する*/
	@Path("/session")
	@DELETE
	public void logout() {
		String skey = request.getHeader(AUTH_HEADER);

		if (skey != null && sessionCache.containsKey(skey)) {
			//TODO:修正中
			//ProcessInstanceManager mgr = sessionCache.get(skey);
			ProcessInstanceManager mgr = ProcessInstanceManager.getSession();
			sessionCache.remove(skey);
			mgr.logout();
		}
	}
	/**アクティブなシナリオの格納先を返す*/
	public String getScenarioDirectory(){
		if("_default_".equals(activeScenario))	
			return scenarioBase ;
		else if(activeScenario != null && activeScenario.length() != 0)
			return scenarioBase + File.separator + activeScenario;
		else{
			logger.error("シナリオセットの格納先が不正です。("+activeScenario +") 既定のシナリオセットを使用します。");
			return scenarioBase ;
		}
		
	}
	/**setting.jsonからチーム情報を取得**/
	protected Map<String, String> listTeams() throws WorkflowException{
		try{

			JSONObject configured = new JSONObject(Util.readAll(getScenarioDirectory() + File.separator + "setting.json"));
			
//			JSONObject configured = new JSONObject(Util.readAll(getContextRelativePath("setting.json")));
			HashMap<String, String>teams = new HashMap<>();
			for(Object o : configured.getJSONArray("teams")){
				JSONObject oo = (JSONObject)o;
				if(oo.has("id") && oo.has("name"))
					teams.put(oo.getString("id"),oo.getString("name"));
				else
					logger.warn("チーム定義は無視されました:" + oo.toString());
			}
			return teams;
		//}catch(JSONException |IOException t){
		}catch(Throwable t){
			logger.error(t);
			throw new WorkflowException("チーム定義をロードできません。", t);
		}
	}
	/**プロセスインスタンスを検索する。
	 * 
	 * <p class = "interface">
	 * GET /process<br>
	 * </p>
	 * */

	@Path("/process")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public /*ProcessInstance[] */WorkflowInstance[] list() throws WorkflowException{
		try {

			logger.enter();
			ProcessInstanceManager mgr = getSession();

			Map<String, String>teamsdef = listTeams();

			//ArrayList<ProcessInstance> ret = new ArrayList<>();
			ArrayList<WorkflowInstance> ret = new ArrayList<>();
			
			for(String team : teamsdef.keySet()){
				WorkflowInstance inst = getWorkflowInstance(team);
				ret.add(inst);
//				if(mgr.isValidProcess(inst.pid)) {
//					ProcessInstance p = mgr.getProcessInstance(inst.pid);
//					p.workflow = inst;
//					p.name = activeScenario;
//					ret.add(p);
//				}else {
//					logger.warn("プロセスインスタンスが不正です。pid=" + String.valueOf(inst.pid));
//				}
			}
			WorkflowInstance[] wf = ret.toArray(new WorkflowInstance[ret.size()]);
			String str = new ObjectMapper().writeValueAsString(wf);
			return wf;
	//		ProcessInstance[] r = ret.toArray(new ProcessInstance[ret.size()]);
			
//			return r;
			

		}catch(WorkflowException t) {
			throw t;
		}catch(Throwable t) {
			throw new WorkflowException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,t);
		}finally {
			exit();
		}
	}

	/**プロセスインスタンスをチームに割り当て、ワークフローを開始する。
	 * 
	 * <p class="interface">
	 * GET /process/{team}/start 
	 * </p>
	 * @param team チームID
	 * @param phase フェーズ番号
	 * @return プロセスインスタンス状態を格納したjsonオブジェクト
	 
	 * */
	@Path("/process/{team}/start")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public /*ProcessInstance*/WorkflowInstance assign(@PathParam("team") final String team,	@QueryParam("phase") final String phase) throws WorkflowException{
		if(team == null){throw new WorkflowException("パラメタが無効です。", HttpServletResponse.SC_BAD_REQUEST);}
		try {
			logger.enter(team);
//			ProcessInstanceManager mgr = getSession();
//			ProcessInstance proc = mgr.getProcessInstance(id);
//			if(proc == null) throw new WorkflowException("ワークフロープロセスが見つかりません。 プロセスID:" + String.valueOf(id) + " not found.", HttpServletResponse.SC_NOT_FOUND );

			Map<String,String> teams = listTeams();
			if(teams != null && !teams.containsKey(team)){
				throw new WorkflowException("チームIDが正しくありません。ページを再読み込みしてください。" + team,HttpServletResponse.SC_BAD_REQUEST);
			}

			WorkflowInstance wf = getWorkflowInstance(team);
			if(wf == null){
				wf= WorkflowInstance.newInstance(this, team);
				workflow.add(wf);
			}
			
//			if(!wf.isRunning()){
				wf.start(Integer.parseInt(phase));
				postTeamNotification(team, "演習ワークフローを開始します。フェーズ:" + (phase !=null ? phase : ""), NotificationMessage.LEVEL_CONTROL);
//			}else{
//				logger.warn("workflow is already started. team=" + team, null);
//				throw new WorkflowException("フェーズの実行中はフェーズを開始できません。実行中のフェーズを中止してください。", HttpServletResponse.SC_BAD_REQUEST);
//			}
			//return proc;
			return wf;
		}finally{exit();}
	}
	/**ワークフローを中断する。
	 * <p>
	 * GET /process/{app}/{id}/abort
	 * </p>
	 * 
	 * @param app アプリケーション名
	 * @param id ワークフロープロセスのID
	 * @param team チームID
	 * */
	@Path("/process/{team}/abort")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public WorkflowInstance abort(@PathParam("team") final String team) throws WorkflowException{
		enter();
		if(team == null){throw new WorkflowException("パラメタが無効です。", HttpServletResponse.SC_BAD_REQUEST);}
		try{
			WorkflowInstance wf = getWorkflowInstance(team);
			postTeamNotification(team, "演習ワークフローが中断されました。" , NotificationMessage.LEVEL_CRITICAL);
			wf.abort();
			return wf;
		}finally{exit();}
	}
	
	/**プロセスインスタンスを一時停止する。
	 * 
	 * <p class="interface">
	 * GET /{team}/resume
	 * */
	@Path("/process/{team}/pause")
	@Produces(MediaType.APPLICATION_JSON)
	@GET
	public WorkflowInstance suspend(@PathParam("team") final String team) throws WorkflowException{
		logger.enter(team);
		try{
			WorkflowInstance inst = getWorkflowInstance(team);
			if(inst== null){
				throw new WorkflowException("ワークフローインスタンスが見つかりません。");
			}
			inst.suspend();
			return inst;
		}finally{exit();}
	}
	
	
	/**プロセスインスタンスを再開する。
	 * 
	 * <p class="interface">
	 * GET /process/{app}/{id}/reume
	 * </p>

	 * */
	@Path("/process/{team}/resume")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public WorkflowInstance resume(@PathParam("team") final String team) throws WorkflowException{
		logger.enter(team);
		try{
			WorkflowInstance inst = getWorkflowInstance(team);
			if(inst== null)
				throw new WorkflowException("ワークフローインスタンスが見つかりません。");
			inst.resume();
			return inst;
		}finally{exit();}
	}
//	/**ワークフローインスタンスを返す。
//	 * @param processid ワークフロープロセスのID
//	 * */
//	public static WorkflowInstance getWorkflowInstance(long processid){
//		if(workflow == null || workflow.size() == 0)return null;
//		for(WorkflowInstance i : workflow){
//			if(i.pid == processid)return i;
//		}
//		return null;
//	}
//	

	protected static Collection<WorkflowInstance> workflow = new ArrayList<>();
	/**チームメンバへの通知メッセージを送信します。*/
	protected void  postTeamNotification(final String team, final String message, int level) throws WorkflowException{

		WorkflowInstance inst = getWorkflowInstance(team);
		if(inst != null)inst.sendTeamNotification(message, level, null);
	}
	
	/**指定されたIDをもつプロセスインスタンスを取得する。
	 * <p class="interface">
	 * GET /process/{app}/{id}
	 * </p>
	 * @param app アプリケーション名
	 * @param id ワークフロープロセスのID
	 * */
	@Path("/process/{team}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public WorkflowInstance get(@PathParam("team") String team)  throws WorkflowException{
		try {

			logger.enter(team);
//			ProcessInstanceManager mgr = getSession();
//			ProcessInstance proc = mgr.getProcessInstance(id);
//			return proc;
			return this.getWorkflowStatus(team);
		} finally {
			exit();
		}
	}
	
//	/**プロセスインスタンスの状態を更新する。
//	 * <p class = "interface">
//	 * PUT /process/{team}
//	 * </p>
//	 * @param team
//	 * 
//	 * @deprecated
//
//	 * */
//	@Path("/process/{team}")
//	@Produces(MediaType.APPLICATION_JSON)
//	@PUT
//	public ProcessInstance update(@PathParam("team") String team) throws WorkflowException {
//		try {
//			logger.enter(team);
//
//			ProcessInstanceManager mgr = getSession();
//
//			ProcessInstance proc = mgr.getProcessInstance(id);
//			if (abort != null)
//				proc.abort();
//			if (suspend != null)
//				proc.suspend();
//			if (resume != null)
//				proc.resume();
//
//			Map<String, String> udas = new HashMap<>();
//			Map<String, String[]> params = request.getParameterMap();
//			for (Iterator<String> i = params.keySet().iterator(); i.hasNext();) {
//				String key = i.next();
//				if (key.startsWith("uda.")) {
//					String[] vals = params.get(key);
//					udas.put(key.replace("uda\\.", ""), vals[0]);
//				}
//			}
//
//
//			return get(app, id);
//		} catch (WorkflowException t) {
//			throw new WorkflowException("ワークフロープロセスの更新に失敗しました。", t);
//		} finally {
//			exit();
//		}
//	}

	/**ワークフロープロセスインスタンスを開始する。
	 * <p class = "interface">
	 * POST /process/{app}
	 * </p>
	 * @param app アプリケーション名
	 * */
	@Path("/process/{team}")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public WorkflowInstance start(@PathParam("team") String team) throws WorkflowException {
		try {
			logger.enter(team);

			Map<String, String> udas = new HashMap<>();
			Map<String, String[]> params = request.getParameterMap();
			for (Iterator<String> i = params.keySet().iterator(); i.hasNext();) {
				String key = i.next();
				if (key.startsWith("uda.")) {
					String[] vals = params.get(key);
					udas.put(key.replace("uda\\.", ""), vals[0]);
				}
			}
			ProcessInstanceManager mgr = getSession();
			ProcessInstance p = mgr.startProcessInstance(udas);//proc, app, udas);
			return getWorkflowInstance(team);
//			return p;
		} finally {
			exit();
		}
	}

	/**シナリオセットのリストを返します。
	 * シナリオセットはシナリオデータ格納先ディレクトリ配下に配置します。
	 * 格納先ディレクトリ直下は"_default_"という名前の既定のシナリオセットとして処理されます。
	 * 格納先ディレクトリ配下のサブディレクトリがそれぞれ1つのシナリオセットとして処理されます。ディレクトリ名がそのままシナリオ名となります。
	 * 
	 * <p class = "interface">GET diag/scenario</p>
	 * @return シナリオ名とシナリオデータのハッシュテーブル 
	 * */
	@GET
	@Path("diag/scenario")
	@Produces(MediaType.APPLICATION_JSON)

	
	public Hashtable<String, ScenarioData> listScenarios() throws WorkflowException{
		Hashtable<String, ScenarioData> ret = new Hashtable<>();
		try {
			Hashtable<String, ScenarioData> scenarios = ScenarioData.loadAll(false);
			scenarios.keys().asIterator().forEachRemaining((k->{
				ScenarioData cur = (ScenarioData) scenarios.get(k);
				
				String name = cur.name == null || cur.name.length() == 0 ? k.toString() : cur.name;
				
				//TODO: 恐らくデフォルトシナリオが正しく処理されていない
				String dir = context.getRealPath("data") + ("_default_".equals(k) ? "" : (File.separator + k));
				
				ValidationResultSet validationResult = validateScenarioSet(dir);
				
				//TODO: シナリオが同名の場合に上書きされてアクティブなシナリオがなくなる場合がある
				cur.validationResult = validationResult;
				cur.active = k.equals(activeScenario);
//				ret.put(name, cur);
				ret.put(k, cur);//TODO: こうか？？
			}));
			

			//TODO： activeプロパティが更新されないまま返却されている
			//scenarios.entrySet().forEach(e -> {
//			ret.entrySet().forEach(e -> {
//				Object key = e.getKey();
//				e.getValue().active = key.equals(activeScenario);
//			});
			

			return ret;
		}catch(Throwable t){
			logger.error("シナリオセットの処理に失敗しました。", t);
			throw new WorkflowException("シナリオセットのロードに失敗しました。", t);
		}
		
	}

	/***
	 * @deprecated 
	 * */
	public Response listScenarios_back() throws WorkflowException{
	
		//TODO: シナリオデータのロード処理を新ロジックに移行
		Response r = _listScenarios_new();
		Response rr = _listScenarios();

		return r;
		
		//return _listScenarios();
	
	}
	
	public Response _listScenarios_new() throws WorkflowException{		

		JSONObject ret = new JSONObject();
		Hashtable<?,?> scenarios = ScenarioData.loadAll(false);
		scenarios.keys().asIterator().forEachRemaining((k->{
			ScenarioData cur = (ScenarioData) scenarios.get(k);
			
			String name = cur.name == null || cur.name.length() == 0 ? k.toString() : cur.name;
			
			String dir = context.getRealPath("data") + ("_default_".equals(k) ? "" : (File.separator + k));
			ValidationResultSet validationResult = validateScenarioSet(dir);
			
			cur.validationResult = validationResult;
			cur.active = name.equals(activeScenario);
			try {
				ret.put(name , new JSONObject(Util.getObjectMapper().writeValueAsString(cur)));
			}catch (IOException t) {
				logger.error("シナリオセットをロードできません。", t);
			}
			
		}));

		try{
			String s = ret.toString();
			return Response.ok(s).build();
		}catch(Throwable t){
			logger.error("シナリオセットの処理に失敗しました。", t);
			return Response.status(HttpServletResponse.SC_BAD_REQUEST).entity("シナリオセットのロードに失敗しました。").build();
		}
	}

	
	
	/***
	 * @deprecated 
	 * */
	public Response _listScenarios() throws WorkflowException{		

		
		String dir = context.getRealPath("data");
		String[] dirs = new File(dir).list(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return new File(dir.getAbsolutePath() + File.separator + name).isDirectory();
			}
		});

		JSONObject ret = new JSONObject();
		String path = dir + File.separator + "setting.json";
		try{
			JSONObject def = new JSONObject(Util.readAll(path));
			def.put("active","_default_".equals(activeScenario) || activeScenario.length() == 0);
			if(!def.has("name"))def.put("name", "_default_");
			ret.put("_default_", def);
		}catch(IOException t){
			throw new ScenarioException("既定のシナリオセットのロードに失敗しました。", path, t);
		}
		for(String b : dirs){
			try{
				String setting =dir + File.separator + b + File.separator + "setting.json";
				if(!new File(setting).exists())
					logger.warn("setting.jsonがありません。" + dir + File.separator + b);
				else{
					JSONObject cur = new JSONObject(Util.readAll(setting));
					
					String validationResult = validateScenarioSet(dir + File.separator + b).toString();
					cur.put("validationResult", validationResult);
					
					cur.put("active",b.equals(activeScenario));
					ret.put(b , cur);
				}
			}catch(Throwable t){
				JSONObject err = new JSONObject();
				err.put("error",t.getMessage());
				ret.put(b, err);
				logger.error("シナリオセットの読み込みに失敗しました。",t);
			}
		}

		try{
			String s = ret.toString();
			return Response.ok(s).build();
		}catch(Throwable t){
			logger.error("シナリオセットの処理に失敗しました。", t);
			return Response.status(HttpServletResponse.SC_BAD_REQUEST).entity("シナリオセットのロードに失敗しました。").build();
		}
	}
	
	
	/**指定された名前のシナリオを活性化します。
	 * <p class ="interface">
	 * POST diag/scenario
	 * </p>
	 * 
	 * @param params name=シナリオ名を含むハッシュマップ
	 * */
	@POST
	@Path("diag/scenario")
	@Consumes(MediaType.APPLICATION_JSON)
	public void activateScenarioSet(final HashMap<String,String> params){
		if(params == null) throw new ScenarioException("シナリオセットが指定されていません。");
		String name = params.get("name");
		if(name == null || name.length() == 0) throw new ScenarioException("シナリオセットが指定されていません。");
		if(name.equals("_default_"))name = "";
		String dir = context.getRealPath("data")  +File.separator + name;
		File d = new File(dir);
		if(!d.exists() || !d.isDirectory())
			throw new ScenarioException("シナリオセットが見つかりません。" + name);


		for(WorkflowInstance i : workflow){
			i.abort();
			logger.info("ワークフローを中断しました。" + i.toString());
		}
		//ログイン中のユーザに通知?
		activeScenario = name;
		this.changeScenario(activeScenario);
		initializeProcesses();
		logger.info("シナリオセットを変更しました。" + activeScenario);

		Notifier.broadcast(NotificationMessage.makeBroadcastMessage("シナリオセットが変更されました。ページを再読み込みしてください。", 
				CardData.Types.alert));
	
	}
	/**ワークフロープロセスを初期化する。*/
	protected void initializeProcesses() throws WorkflowException{
		try{
			workflow = new ArrayList<>();
			ProcessInstanceManager mgr = ProcessInstanceManager.getSession();
			for(ProcessInstance i : mgr.listProcesses()) {
				mgr.deleteInstance(i.getId());
			}
			
			for(String team : listTeams().keySet()){
//				ProcessInstance inst = mgr.startProcessInstance();
//				long pid = inst.getId();
				workflow.add(WorkflowInstance.newInstance(this,  team));
				logger.info(String.format("ワークフロープロセスを生成しました。チーム:%s", team));
			}
			for(WorkflowInstance i : workflow){
				i.initialize(null);
			}
		}catch(Throwable t){
			throw new WorkflowException("ワークフロープロセスの起動に失敗しました。",t);
		}
	}

	/**シナリオ要素をアップロードします。
	 * <p class = "interface">PUT diag/scenario/{name}/{filename}</p>
	 * 
	 * @param name シナリオ名
	 * @param filename　ファイル名
	 * 
	 * シナリオ要素のファイルをapplication/octet-stream形式で受け付ける。
	 * */
	@PUT
	@Path("diag/scenario/{name}/{filename}")
	@Consumes(MediaType.APPLICATION_OCTET_STREAM)
	public void uploadScenarioData(@PathParam("name") String name, @PathParam("filename") String filename){
		try{

			byte[] data = _uploadResource();
			String path = Util.canonicalizePath(scenarioBase +File.separator + 
					("_default_".equals(name) ? filename : (name + File.separator + filename)));// + File.separator + fname);

			Util.save(path, data, true);
			logger.info("シナリオデータを保存しました。" + path);
		}catch(IOException t){
			throw new WorkflowException("アップロードに失敗しました。", t);
		}
	}
	
	/**シナリオセットをアップロードします。
	 * <p class = "interface">PUT diag/scenario/{name}</p>
	 * 
	 * @param name シナリオ名
	 * zipアーカイブ形式のシナリオデータ(application/octet-stream)を受け付ける。
	 * */
	@PUT
	@Path("diag/scenario/{name}")
	@Consumes(MediaType.APPLICATION_OCTET_STREAM)
	public Collection<ValidationResult> uploadScenarioSet(@PathParam("name") String name) throws WorkflowException{
		String disp = request.getHeader("Content-Transfer-Encoding");
		try{
	
			byte[] data = _uploadResource();
			String fname = name.endsWith(".zip") ? name.replaceAll(".zip$", ""):name;

			String path = Util.canonicalizePath(scenarioBase +File.separator + fname);// + File.separator + fname);
			Util.uncompress(data, path);
			ValidationResultSet res = validateScenarioSet(path);
			if(res.isValid()){
				logger.info("シナリオを登録しました:" + path);
			}else{
				logger.error("シナリオデータにエラーが見つかりました。" + path + ": " + res.toString());
			}
			return res;
		}catch(IOException t){
			throw new WorkflowException("アップロードに失敗しました。", t);
		}
	}
	/***シナリオデータのアーカイブをダウンロードします。
	 * 
	 * <p class = "interface">
	 * GET diag/scenario/{name}
	 * </p>
	 * @param name シナリオ名
	 * @return zipアーカイブされたシナリオデータをapplication/octet-stream形式で返す。
	 * */
	@GET
	@Path("diag/scenario/{name}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public Response downloadScenarioSet(@PathParam("name") String name) throws WorkflowException{
		try{
			logger.enter(name);
			if(name == null || name.length() == 0)
				throw new WorkflowParameterException("シナリオセット名が指定されていません。");
			
			String dir = Util.canonicalizePath(scenarioBase + ("_default_".equals(name) ? "" :File.separator + name));
			byte[] out = Util.compress(dir);
			
			String zipname = name;
			if(zipname.endsWith(".zip")) zipname += ".zip";
			String encoded = "filename=\"" + URLEncoder.encode(zipname, "UTF-8") + "\";";
			encoded += "filename*=UTF-8''" + URLEncoder.encode(zipname, "UTF-8") + ";";
			Response resp = Response.ok().header("Content-Disposition", "attachment;"+encoded).entity(out).build();
			return resp;
		}catch(IOException t){
			throw new WorkflowException("ダウンロードに失敗しました。", t);
		}
	}
	/**シナリオセットを検証します。
	 * <p class = "interface">
	 * GET diag/scenario/{name}/validate
	 * </p>
	 * @param name シナリオ名
	 * @return シナリオ検証結果を格納したjson形式データを含むレスポンス。
	 * */
	@GET
	@Path("diag/scenario/{name}/validate")
	@Produces(MediaType.APPLICATION_JSON)
	public ValidationResultSet validate(@PathParam("name") String name){
		try{
			if(name == null || name.length() == 0)
				throw new WorkflowParameterException("シナリオセット名が指定されていません。");
			
			String dir = Util.canonicalizePath(scenarioBase + ("_default_".equals(name) ? "" :File.separator + name));
			ValidationResultSet res = validateScenarioSet(dir);
			return res;
		}catch (Throwable t) {
			throw new ScenarioException("シナリオセットの検証に失敗しました。", name, t);
		}
	}
	/**選択されたシナリオデータをロードしてテストする
	 * @parma dir シナリオ名
	 * */
	protected ValidationResultSet validateScenarioSet(final String name) throws WorkflowException{
		String  dir = java.nio.file.Path.of(name).isAbsolute() ? name : getContextRelativePath(name);
		ValidationResultSet res = ValidationResultSet.validateScenarioSet(dir);
		return res;
	}
	
	/**指定されたディレクトリにあるjsonファイルを検証します。*/
	protected JSONArray validateJsonData(final String dir){
		String[] required = {"contacts.json","actions.json","replies.json","states.json","setting.json", "points.json"};
		JSONArray ret = new JSONArray();
		for(String f : required){
			String path = dir  + File.separator + f;
			try{
				JSONObject c = new JSONObject();
				c.put("name",path);
				ret.put(c);
			}catch(Throwable t){
				JSONObject o = new JSONObject();
				o.put("error",t.getMessage());
				o.put("name", path);
				ret.put(o);
			}
		}
		return ret;
	}
	
	/**typeがactionであるアクションカードを取得する。
	 * 
	 * <p class = "interface">GET /process/{app}/{id}/cards</p>
	 * @param app アプリケーション名
	 * @param id ワークフロープロセスのID
	 * @return json形式のアクションカードの配列
	 * */
	@Path("/process/cards")
	@Produces(MediaType.APPLICATION_JSON)
	@GET
	public CardData[] getActionCards(@QueryParam("all") boolean all) throws WorkflowException{
		try{
			return getCards(null, all);

		}catch(Throwable t){
			throw new WorkflowException("アクションカードの読み込みに失敗しました。", t);
		}
	}
	
	
	/**アクションカードを取得する。
	 * @param type アクションの種類。nullを指定するとすべての種類を返す。
	 * @param all trueを指定するとすべてのアクション、falseならログオン中のユーザがrolesに含まれるアクションを返す
	 * */
	protected CardData[] getCards(String type, boolean all) throws WorkflowException{
		try{
			String role = null;
			if(!all){
				SessionData s = getSessionData();
				role = s.role;
			}
			List<CardData> ret =new ArrayList<>();

			final Collection<CardData> cards = type != null ? CardData.load(type) : CardData.flatten(CardData.loadAll().values());
			if(cards != null){
				for(CardData cur : cards){
					if(all ||  cur.roles != null && 
					(Arrays.asList(cur.roles).contains("all") || Arrays.asList(cur.roles).contains(role))){
						//if(ret.indexOf(cur) == -1)
						ret.add(cur);
					}
				}
			}else{
				logger.warn("アクションカードがありません。");
			}

			return ret.toArray(new CardData[ret.size()]);
		}catch(WorkflowSessionException t){
			throw new WorkflowException("アクションカードの読み込みに失敗しました。", HttpServletResponse.SC_UNAUTHORIZED, t, true);
		}catch(Throwable e){
			throw new WorkflowException("アクションカードの読み込みに失敗しました。", HttpServletResponse.SC_NOT_FOUND, e);}
	}
	/**定義済のポイントカードを取得する。
	 * <p class = "interface">
	 * GET  /diag/point
	 * </p>
	 * */
	@Path("/diag/point")
	@Produces(MediaType.APPLICATION_JSON)
	@GET
	public PointCard[] getPointCards(@PathParam("app") String app, @PathParam("id") long id,
			@QueryParam("phase") int phase) throws WorkflowException{
		try{
			return PointCard.list(phase);
		}catch(Throwable t){
			throw new WorkflowException("ポイントカードの読み込みに失敗しました。", t);
		}
	}	  
	/**アクションカードを実行する。
	 * <p class = "interface">POST /process/{app}/{id}/act</p>
	 * @param app アプリケーション名
	 * @param id ワークフロープロセスのID
	 * @param data json形式のアクションカードの文字列。
	 * */
	@Path("/process/act")
	@Consumes({MediaType.APPLICATION_JSON})
	@POST
	public void doAction(final String data) throws WorkflowException{
		try{
			JSONObject content = new JSONObject(data);
			ObjectMapper mapper = new ObjectMapper();
			if(content.isNull("action"))	throw new WorkflowException("パラメタが不正です。(action)");
			JSONObject cont = content.getJSONObject("action");
			if(cont.isNull("card"))	throw new WorkflowException("パラメタが不正です。(card)");
			JSONObject action = cont.getJSONObject("card");
			if(cont.isNull("to"))	throw new WorkflowException("パラメタが不正です。(to)");
			JSONObject to = cont.getJSONObject("to");

			JSONArray cc= !cont.isNull("cc") ? cont.getJSONArray("cc")  : null;
			JSONObject replyto = !cont.isNull("replyto") ? cont.getJSONObject("replyto") : null;
		
			CardData actobj = mapper.readValue(action.toString(), CardData.class);
			
			Member toobj = mapper.readValue(to.toString(), Member.class);
			Member[] ccobj  = cc != null ? mapper.readValue(cc.toString(), Member[].class) : null;

			Member from = getCurrentMember();
			NotificationMessage rep = replyto != null ? mapper.readValue(replyto.toString(), NotificationMessage.class) : null;
			getCurrentWorkflow().requestAction(actobj, from, toobj, ccobj , rep);
		}catch(IOException t ){
			throw new WorkflowException("アクションリクエストが無効です。", HttpServletResponse.SC_BAD_REQUEST, t);
		}
	}
	/**現在のユーザのワークフローインスタンスを取得*/
	protected WorkflowInstance getCurrentWorkflow() throws WorkflowException{
		String team = getCurrentSession().team;
		for(WorkflowInstance inst : workflow){
			if(inst.team.equals(team))
				return inst;
		}
		throw new WorkflowException("ワークフローが開始されていません。", HttpServletResponse.SC_NOT_ACCEPTABLE );
	}
	
	/**メンバ情報を取得する。
	 * <p class = "interface">GET /contacts/{teamname}</p>
	 * @param teamname チーム名。nullを指定するとすべてのチームのメンバを返す。
	 * @return json形式のメンバ情報の配列。
	 * */
	@Path("/contacts/{teamname}")
	@Produces(MediaType.APPLICATION_JSON)
	@GET
	public Collection<Member> getTeamMembers(@PathParam("teamname") String teamname, @QueryParam("presence") boolean presence) throws WorkflowException{
		try{

			logger.enter(teamname, presence);
			Collection<Member> ret = null;
			if(!presence){
				
				if(teamname != null)
					ret = Member.getTeamMembers(teamname);
				else
					ret= Member.loadAll().values();
			
			}else{
				ret = Notifier.getTeamMembers(teamname);
			}
			
			//所有ステートカードの情報をマージ
			WorkflowInstance inst = getWorkflowInstance(teamname);
			for(Member m : ret){
				m.availableStates = inst.memberStates.get(m.email); 
			}
			return ret;
		}catch(WorkflowException t){
			throw t;
		}finally{
			exit();
		}
	}

	/**
	 * ワークフローの状態を返す。
	 *<p class ="interface">GET /diag/{teamname}/workflow</p> 
	 * 
	 * @param teamname チーム名
	 * @return ワークフローインスタンスの状態を格納したjsonデータ
	 * */
	@Path("/diag/{teamname}/workflow")
	@Produces(MediaType.APPLICATION_JSON)
	@GET
	public WorkflowInstance getWorkflowStatus(@PathParam("teamname") String teamname) throws WorkflowException{
		try{
			//enter();
			WorkflowInstance inst =  getWorkflowInstance(teamname);
			if(inst != null)
				return inst;
			
			throw new WorkflowException("ワークフローが開始されていません。チーム:"+ teamname, HttpServletResponse.SC_NOT_FOUND, true);

		}catch(WorkflowException t){
			throw t;
		}
	}

	/**チーム名からワークフローインスタンスを探索*/
	protected static WorkflowInstance getWorkflowInstance(final String team) throws WorkflowException{
		for(WorkflowInstance i : workflow){
			if(i.team.equals(team))
				return i;
		}
		return null;
	}
	/**イベント履歴を取得する。
	 * <p class = "interface">/diag/{teamname}/history</p>
	 * @param teamname チーム名
	 * @return イベント履歴の配列をjson形式で返す
	 * */
	@Path("/diag/{teamname}/history")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Collection<NotificationMessage> getHistories(@PathParam("teamname") String teamname,  @QueryParam("userrole") String userrole){
		try{
			logger.enter(teamname, userrole);
			Collection<NotificationMessage> ret = new ArrayList<NotificationMessage>();
			for(WorkflowInstance i : workflow){
				if(/*i == null || */i.team.equals(teamname)){
					if(userrole == null)
						ret.addAll(i.history);
					else{
						Collection<NotificationMessage> hist = i.getUserEventHistory(userrole);
						if(hist != null)
							ret.addAll(hist);
					}
				}
			}
			return ret;
		}catch(WorkflowException t){
			throw t;
		}
	}

	/**トリガーイベントのリストを取得する
	 * <p class = "interface">GET /diag/{teamname}/trigger</p>
	 * 
	 * @param teamname チーム名
	 * @return トリガーイベント(リプライデータ)の配列。
	 * */
	@Path("/diag/{teamname}/trigger")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Collection<ReplyData> getTriggerEvents(@PathParam("teamname") String teamname){
		try{
			logger.enter(teamname);
			Collection<ReplyData> ret = new ArrayList<ReplyData>();
			WorkflowInstance inst = getWorkflowInstance(teamname);
			if(inst == null) throw new WorkflowWarning("ワークフローが開始されていません。");
			if(inst.phase <=0) throw new WorkflowWarning("フェーズが開始されていません。");

			Collection<ReplyData> rr = ReplyData.loadReply(inst.phase);
			Collection <ReplyData> replies = Collections.synchronizedList(new ArrayList<ReplyData>(rr));
			ReplyData[] reps = replies.toArray(new ReplyData[replies.size()]);
			for(int i = 0; i < reps.length; i ++){
				ReplyData r = reps[i];
				if(r.actionid == null){
					logger.error("リプライにアクションIDがありません。" +r.toString());
					continue;
				}
				if(!r.isTriggerAction())	continue;

				TriggerEvent[] triggers = inst.getTriggerEvent();
				for(TriggerEvent e : triggers){
					if(r.state == null){
						throw new ScenarioException("トリガーイベントにステートカードが定義されていません。" +  r.toString());
					}
					if(e.state .equals(r.state)){
						r.fireWhen = e.date;break;
					}
				}
				ret.add(r);
			}
			return ret;
		
		}catch(WorkflowWarning t) {
			logger.warn(t.getMessage());
			return new ArrayList<>();
		}catch(WorkflowException t){
			throw new WorkflowException("トリガーイベントの処理に失敗しました。" + t.getMessage(), HttpServletResponse.SC_NOT_FOUND, t);
		}	
	}
	
	/**キューに蓄積されたアクションのリストを取得する。
	 * <p class = "interface">
	 * GET /diag/{teamname}/action
	 * </p>
	 * @param teamname チーム名
	 * @return キューに蓄積されたアクションのリストをjson形式で返す。
	 * */
	@Path("/diag/{teamname}/action")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Collection<NotificationMessage> getActionQueue(@PathParam("teamname") String teamname){
		try{
			enter();
			Collection<NotificationMessage> ret = new ArrayList<NotificationMessage>();
			for(WorkflowInstance i : workflow){
				if(i == null || i.team.equals(teamname))
					ret.addAll(i.actionQueue);
			}
			return ret;
		}catch(WorkflowException t){
			throw t;
		}finally{
			exit();
		}
	}
	
	/**リプライカードのリストを取得する。
	 * 
	 * <p class ="interface">GET /diag/reply</p>
	 * @param phase フェース番号
	 * @return リプライカードのリストを返す。
	 * 
	 * */
	@Path("/diag/reply")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Collection<ReplyData> getReply(@QueryParam("phase") int phase){
		try{
			
			Collection<ReplyData> ret  = ReplyData.loadReply(phase == 0 ? -1 : phase, getScenarioDirectory() + File.separator + "replies.json");
			return ret;
		}catch(WorkflowException t){
			throw t;
		}finally{
			exit();
		}

	}
	/**ステートカードのリストを取得する。
	 * 	 * <p class ="interface">GET /diag/state</p>
	 * @param team チーム名
	 * @return ステートカードのコレクションをjson形式で返す。
	 * */
	@Path("/diag/state")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Collection<StateData> getState(@QueryParam("team") String team){
		try{
			logger.enter(team);
			if(team == null){
				Collection<StateData> ret  = StateData.loadAll(getScenarioDirectory() + File.separator + "states.json");
				return ret;
			}else{
				WorkflowInstance inst = getWorkflowInstance(team);
				if(inst != null && inst.isRunning()){
					Collection<StateData> ret = inst.getSystemStateData();
					return ret;
				}else{				
					Collection<StateData> ret  = StateData.loadAll();
					return ret;
				}
			}
		}catch(WorkflowException t){
			throw t;
		}finally{
			exit();
		}
	}
	
	/**ワークフローインスタンスデータをダウンロードします。
	 * <p class = "interface">GET /diag/{teamname}/download</p>
	 * @param teamname チーム名
	 * @return json形式のワークフローインスタンスデータを返す。
	 * 
	 * */
	@Path("/diag/{teamname}/download")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response download(@PathParam("teamname") String teamname){
		logger.enter(teamname);
		if("all".equals(teamname)){
			//StringBuffer buff  = new StringBuffer();
			ArrayList<String> buff = new ArrayList<>();
			for(WorkflowInstance inst : workflow){
				String json = inst.save();
				buff.add(json);
			}
			return Response.ok("[" + Util.join(buff, ",") + "]").build();
		}else{
			WorkflowInstance inst = getWorkflowInstance(teamname);
			if(inst != null){
				String json = inst.save();
				return Response.ok(json).build();
			}
		}
		return Response.status(Response.Status.NOT_FOUND).build();
	}
	
	/**ワークフローインスタンスデータをアップロードし、状態を復元します。インスタンスは中断状態になります。
	 * <p class = "interface">POST /diag/{teamname}/upload</p>
	 * @param teamname チーム名
	 * 
	 * @return json形式のワークフローインスタンスデータを含むレスポンスを返す。
	 * **/
	@Path("/diag/{teamname}/upload")
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response upload(@PathParam("teamname") String teamname){
		String data = "";
		try{
			logger.enter(teamname);
			byte[] bytes = _uploadResource();
			data = new String(bytes, "utf-8");
		}catch(IOException t){
			throw new  WorkflowException("データの読み込みに失敗しました。", HttpServletResponse.SC_BAD_REQUEST, t);
		}
		if("all".equals(teamname)){
			JSONArray arr = new JSONArray(data);
			for(int i = 0; i < arr.length(); i ++){
				restoreProcess(arr.getJSONObject(i).toString());
			}
		}else{
			restoreProcess(data);
		}

		WorkflowInstance[] ret = workflow.toArray(new WorkflowInstance[workflow.size()]);
		exit();
		return Response.ok().entity(ret).build();
	}

	class ResourceSpec{
		public String name;
		public String type;
		public String url;
		public Date modified;
		public String description;
	}
	/**リソースファイルをアップロードする。
	 * @deprecated 未実装/未検証
	 * **/
	@Path("/resource/{typename}/{resourcename}")
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public ResourceSpec uploadResource(@PathParam("typename") String typename, @PathParam("resourcename") String resourcename) throws WorkflowException{
		try{
			byte[] data = _uploadResource();
			String name = resourcename.endsWith(".json") ? resourcename : (resourcename + ".json");
			String path =  Util.canonicalizePath(resourceBase.replace("$CONTEXT", getContextRelativePath("")) + 
					File.separator + typename + File.separator + name);
			
			Util.save(path, data, true);
			
			ResourceSpec spec = new ResourceSpec(){
				{this.name = resourcename;this.type = typename;
				this.url = "resource/"+ typename + "/" + name;}};
			return spec;
		}catch(Throwable t){
			String msg = "データの保存に失敗しました。" + typename + "/"+resourcename;
			logger.error(msg, t);
			throw new WorkflowException(msg, HttpServletResponse.SC_BAD_REQUEST ,t);
		}
	}
	
	/**指定されたパスに対応するローカルリソースをレスポンスbodyとして返す。
	 * @deprecated 未実装/未検証
	 * <p class = "interface">GET /resource/{typename}/{resourcename}</p>
	 * @param typename リソースの種類。{@link ResourceSpec}
	 * @param resourcename リソースファイル名
	 * @return リソースファイルデータを格納したレスポンス 
	 * **/
	@Path("/resource/{typename}/{resourcename}")
	@GET
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public Response downloadResource(@PathParam("typename") String typename, @PathParam("resourcename") String resourcename) throws WorkflowException{
		try{
			String path =  Util.canonicalizePath(resourceBase.replace("$CONTEXT", getContextRelativePath("")) + File.separator + typename + File.separator + resourcename);
			String data = Util.loadTextFile(path);
			return Response.ok(data).build();
	}catch(Throwable t){
			String msg = "データの出力に失敗しました。" + resourcename;
			logger.error(msg, t);
			throw new WorkflowException(msg, HttpServletResponse.SC_BAD_REQUEST ,t);
		}
	}
	
	/***
	 * @deprecated 未実装/未検証
	 * */
	@Path("/resource/{typename}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public ResourceSpec[] listResource(@PathParam("typename") String typename) throws WorkflowException{
		try{
			String path =  Util.canonicalizePath(resourceBase.replace("$CONTEXT", getContextRelativePath("")) + 
					File.separator + typename);
			Collection<ResourceSpec> ret = new ArrayList<>();
			String[] files = new File(path).list();
			if(files != null){
					
				for(String str : new File(path).list()){
					ResourceSpec spec = new ResourceSpec(){
						{this.name = str;this.type = typename;
						this.url = "resource/"+ typename + "/" + str;}};
					ret.add(spec);
				};
			}
			return ret.toArray(new ResourceSpec[ret.size()]);
		}catch(Throwable t){
			throw new WorkflowException("リソース一覧の出力に失敗しました。", HttpServletResponse.SC_BAD_REQUEST, t);
		}
	}
	
	/**汎用:リクエストからバイトストリームを読み込む*/
	protected byte[] _uploadResource(){
		try{
			enter();
			byte[] buff = new byte[1024*1024];
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			InputStream strm = request.getInputStream();
			for(int len = 0; (len = strm.read(buff)) > 0; ){
				out.write(buff, 0, len);
			}
			out.close();
			byte[] data = out.toByteArray();
			return data;
		}catch(IOException t){
			throw new  WorkflowException("データの読み込みに失敗しました。", HttpServletResponse.SC_BAD_REQUEST, t);
		}finally{
			exit();
		}
	}

	
	
	
	
	/**シリアライズされたデータからプロセスをリストアする*/
	protected void restoreProcess(String data){
		WorkflowInstance i  = WorkflowInstance.load(data);
		final String teamname = i.team;
		logger.info("restoring " + teamname);

		WorkflowInstance inst = getWorkflowInstance(teamname);
		if(inst != null){
			inst.abort();
			workflow.remove(inst);
		}
		inst.suspend();
		workflow.add(i);
		
	}

	/**ロギング*/
	public void enter() {
		if (request != null) {
			logger.info("enter: " + request.getMethod() + " " + request.getRequestURI() + "; " + request.getRemoteHost());
		}
	}
	/**ロギング*/
	protected void exit() {
		if (request != null) {
			logger.info("exit: " + request.getMethod() + " " + request.getRequestURI() + "; " + request.getRemoteHost());
		}
	}

	/**ログインユーザに新しいセションを割り当てる
	 * 
	 * @deprecated
	 * */
	public static synchronized void newSession(final String skey, ProcessInstanceManager mgr) throws WorkflowException {
		if (skey == null || skey.length() == 0 || mgr == null)
			throw new WorkflowSessionException("セションの割り当てに失敗しました。");
		//TODO:修正中
		//sessionCache.put(skey, mgr);
		
	}

	/**シナリオリソースを取得する
	 * @param name アクティブなシナリオ格納ディレクトリからの相対パス
	 * @remark JSONがSJISのときにブラウザで文字化けするため、サーバ側で文字コード自動変換
	 * @deprecated 未実装/未検証
	 * */
	@Path("scenario/{name}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public String getJsonResource(@PathParam("name") String name){
		try{
			String path = Util.canonicalizePath(getScenarioDirectory() +File.separator +  name);

			return Util.loadTextFile(path);
		}catch(FileNotFoundException t){
			throw new WorkflowException("リソースが見つかりません:" + name, HttpServletResponse.SC_NOT_FOUND, t);
		}catch(IOException t){
			throw new WorkflowException("リソースが読み込めません:" + name, HttpServletResponse.SC_BAD_REQUEST, t);
		}
	}
	
	
	
	@Path("freeze")
	@GET
	/**イベントループを一時停止する(デバッグ用)*/
	public synchronized boolean freeze() {
		WorkflowService.workflow.forEach(w->{
			w.freeze();
		});
		return true;
	}
	@Path("melt")
	@GET
	/**イベントループを再開する(デバッグ用)*/
	public synchronized boolean melt() {
		WorkflowService.workflow.forEach(w->{
			w.melt();
		});
		return true;
	}
	
	
	
	
}
