package tsurumai.workflow;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Singleton;
import javax.servlet.ServletContext;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import tsurumai.workflow.model.CardData;
import tsurumai.workflow.model.Member;
import tsurumai.workflow.model.SessionData;
import tsurumai.workflow.util.ServiceLogger;


/**ユーザへのイベント通知を行うWebSocketエンドポイント*/
@ServerEndpoint(value="/notification/{userid}")
public class Notifier{

	static ServletContext context;

	
	public static void setContext(ServletContext ctx){
		context = ctx;
	}
	@Singleton
	static Thread watchdog = null;

	protected static ServiceLogger logger =ServiceLogger.getLogger();

	/**Map&lt;id,Session&gt;*/
	protected static ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<String, Session>();

	/**WebSocketイベントハンドラ*/
	@OnOpen	
	public void onOpen(@PathParam("userid") String userid, Session session, EndpointConfig config) {
		try {
			cleanupSessions();
			System.out.println(session.getId() + " was connect as " + String.valueOf(userid));
			sessions.put(userid != null ? userid : "anonymous", session);
			pushPresence();
			
		} catch (Throwable t) {
			WorkflowException e = new WorkflowException("failed to close session.", 500, t);
			e.clientId = session.getId();
			t.printStackTrace();
			session.getAsyncRemote().sendText(t.toString());
		}
	}
	/**WebSocketイベントハンドラ*/
	@OnError
    public void onError(Session session, Throwable cause) {
        System.out.println("error : " + session.getId() + ", " + cause.getMessage());
    }
	public static void sendSystemNotification(String team, String message, int level, String state)
			throws WorkflowException, IOException{
		CardData c = CardData.find(CardData.Types.notification);
		NotificationMessage notif = new NotificationMessage(c, 
				Member.TEAM, Member.SYSTEM, null, null);
		notif.message = message;
		notif.level = level;
		
		notif.sentDate = WorkflowService.getWorkflowInstance(team).getTime();
		
		if(state != null)notif.action.attachments = new String[] {state};
		String str = new ObjectMapper().writeValueAsString(notif);
		findMemberSessions(team).forEach((m, s)->{
			s.getAsyncRemote().sendText(str);	
		});
		
	}
	
	//move from WorkflowInstance
	public static  NotificationMessage constructNotification(CardData data, Member to, Member from, Member[] cc, NotificationMessage replyTo) throws WorkflowException{
		if(data.is(CardData.Types.talk)){
		}else if(data.is(CardData.Types.notification)){
		}else if(data.is(CardData.Types.action)){
		}
		NotificationMessage msg = new NotificationMessage(data, to, from, cc, replyTo);
		return msg;
	}
	
	//move from WorkflowInstance
	public static NotificationMessage sendTeamNotification(String team, String message, int level, String[] states) {
		CardData data = CardData.find(CardData.Types.notification);
		NotificationMessage msg = constructNotification(data,  Member.TEAM, Member.SYSTEM, null, null);
		msg.team = team;
		msg.message = message;
		msg.level = level;
		msg.action.statecards = states;
		//msg.level = level;
		
		msg.sentDate = WorkflowService.getWorkflowInstance(team).getTime();
		
		logger.info("システムからの通知メッセージを送信:" + msg.toString());
		Notifier.dispatch(msg);
		return msg;
	}
	/**WebSocketイベントハンドラ*/
	@OnClose
	public void onClose(Session session) {
		try {

			System.out.println(session.getId() + " was disconnnet.");
			
			for(String k : sessions.keySet()){
				if(session.equals(sessions.get(k)))
					sessions.remove(k);
			}
			cleanupSessions();
			pushPresence();
		} catch (Throwable t) {
			WorkflowException e = new WorkflowException("failed to close session.", 500, t);
			e.clientId = session.getId();
			t.printStackTrace();
		}
	}
	/**WebSocketイベントハンドラ*/
	@OnMessage
	public void onMessage(String message, Session session) {
		try {
			if (message == null || message.length() == 0)
				return;
			
		} catch (Throwable t) {
			t.printStackTrace();
			session.getAsyncRemote().sendText(t.toString());
		}
	}
	public void disconnectAll(){
		if(sessions == null) return;
		for(Session session : sessions.values()){
			try{
				session.close();
			}catch(IOException t){
			}
		}
	}

	public void fail(String msg) {
		System.err.println(msg);
	}

	/** 全セションにメッセージを送信 */
	public static void broadcast(Object message) {
		for (Session session : sessions.values()) {
			session.getAsyncRemote().sendText(message.toString());
		}
	}

	/** toとfrom、Ccに指定された宛先にメッセージを送信
	 * 自動応答ユーザからのメッセージではCCを付けない
	 *  */
	public static void dispatch(Object message) {
		if(!(message instanceof NotificationMessage))
			return;
		
		NotificationMessage m = (NotificationMessage)message;
		history.add(m);
		
		Map<String, Session> recpts = null;
		if(m.to.role.equals(Member.TEAM.role) || m.to.role.equals(Member.ALL.role)){
			recpts = findMemberSessions(m.team);
		}else{
			recpts = findSessions(new Member[]{m.to, m.from});
		}
		
		WorkflowInstance inst = WorkflowService.getWorkflowInstance(m.team);
		if(inst == null){
			logger.error("worklow instance vanished!");
			return;
		}
		//通知先のステートカードを更新
		inst.updateUserStates(m);
		
		int phase = inst.phase;
		if(m.from == null) m.from = Member.SYSTEM;
		if(!m.from.isSystemUser(phase) 
				|| m.reply == null){//<--リプライでない場合(自動アクションのCc)はCcを付けることにする

			if(m.cc != null && m.cc.length != 0){
				for(Member c : m.cc){
					Session sss = findSession(c);
					if(sss != null)
						recpts.put(c.email, sss);
				}
			}
		}else{
			m.cc = null;
			logger.info("自動応答ユーザからのリプライのためCCは除外");
		}
		
		if(recpts == null){
			logger.info("通知先ユーザが誰もいません。");
			recpts = new HashMap<>();
		}

		Session me  = findSession(m.from);
		if(me != null)	
			recpts.put(m.from.email, me);

		String body = m.toString();
		for(String uid : recpts.keySet()){
			logger.info("send message to client " + uid);
			recpts.get(uid).getAsyncRemote().sendText(body);
		}
	}
	protected static Session findSession(final Member user){
		Session me  = findSessions(new Member[]{user}).get(user.email);
		return me;
	}
	
	/**チームメンバの Map(userId, Session)を返す。*/
	public static Map<String, Session> findMemberSessions(final String team){
		return groupSessions().get(team);
	}
	
	/**userIdsに含まれるユーザIDの Map&lt;userId, Session&gt;を返す*/
	protected static Map<String, Session> findSessions(final Member[] users){
		Map<String, Session> to = new HashMap<String, Session>();
		for(int i = 0; users != null && i < users.length; i ++){
			Member user = users[i];
			if(user == null)
				continue;
			SessionData s = WorkflowService.getSessionByUserId(user.email);
			if(s != null && user.email.equalsIgnoreCase(s.userid)){
				Session ss = sessions.get(s.userid);
				if(ss != null) to.put(user.email, ss);
				else logger.error("session not found:"+ user.email);
			}
		}
		return to;
	}
	/**セションをチーム別にグループ化
	 * @return Map&lt;team, Map&lt;userid, session&gt;&gt;*/
	protected static Map<String, Map<String, Session>> groupSessions(){
		Map<String, Map<String, Session>> teams = new HashMap<>();
		for(String uid : sessions.keySet()){
			String team = "all";
			if(uid.contains("@")){
				team = uid.split("@")[1];
			}
			if(!teams.containsKey(team))
				teams.put(team,  new HashMap<String, Session>());
			Session session = sessions.get(uid);
			teams.get(team).put(uid, session);
		}
		return teams;
	}
	
	/**すべてのチームメンバのプレゼンス情報を取得*/
	public static  Map<String, Collection<Member>> getAllMembers(){
		Map<String, Member> members = Member.loadAll();
		Map<String, Collection<Member>> teamSessions = new HashMap<>();//チーム別のプレゼンス情報の配列

		for(String uid : members.keySet()){
			Session s = sessions.get(uid);
			Member m = members.get(uid);
			Member data = m.online(s);
			if(!teamSessions.containsKey(m.team))
				teamSessions.put(m.team, new ArrayList<Member>());
			teamSessions.get(m.team).add(data);
		}
		return teamSessions;
	}
	/**指定されたチームのメンバのプレゼンス情報を取得*/
	/**
	 * @param team チーム名
	 * @return チームメンバのコレクション
	 */
	public static Collection<Member> getTeamMembers(final String team){
		return getAllMembers().get(team);
	}
	
	/**チームメンバの接続情報をプッシュ*/
	protected synchronized void pushPresence() throws WorkflowException{

		Map<String, Collection<Member>> teamSessions = getAllMembers();

		try{
		for(String uid : sessions.keySet()){
			if(uid.contains("@")){
				String team = uid.split("@")[1];
				Collection<Member> p = teamSessions.get(team);
				Map<String, Object> notif = new HashMap<>();
				notif.put("team", team);
				notif.put("when", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm.ss").format(new Date()));
//				String m = new ObjectMapper().writeValueAsString(p);//(arg0)(p.toArray(new Member[p.size()]), Member[].class);
				notif.put("member", p);
				String str = new ObjectMapper().writeValueAsString(notif);

				try{
				sessions.get(uid).getBasicRemote().sendText("{\"presence\":"+str+"}");
				}catch(Throwable t){
					logger.error("通知の送信に失敗しました。" , t);
				}
			}
		}
		}catch (JsonProcessingException t) {
			throw new WorkflowException("failed to serialize member data.", t);
		}

	}
	/**全てのセションを破棄する*/
	protected void cleanupSessions(){
		for(String key :sessions.keySet()){
			if(!sessions.get(key).isOpen())
				sessions.remove(key);
		}
	}
	/**NotificationMessageの履歴を保持*/
	protected static Set<Object> history = Collections.newSetFromMap(new ConcurrentHashMap<Object, Boolean>());



}