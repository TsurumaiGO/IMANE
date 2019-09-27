package tsurumai.workflow;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import tsurumai.workflow.model.CardData;
import tsurumai.workflow.model.CardData.Types;
import tsurumai.workflow.model.Member;
import tsurumai.workflow.model.ReplyData;
import tsurumai.workflow.util.ServiceLogger;
import tsurumai.workflow.util.Util;
@JsonIgnoreProperties({"comment","","//","#"})
//@JsonInclude(JsonInclude.Include.NON_NULL)

/** イベント情報を表現する*/
@XmlRootElement
public class NotificationMessage implements Cloneable{

	@XmlAttribute
	public Date sentDate;
	@XmlAttribute
	public Date visibleDate;
	@XmlAttribute
	public int level;
	@XmlAttribute
	public long processId;
	@XmlAttribute 
	public String id;
	@XmlAttribute
	public String message;
	
	@XmlElement
	public CardData action;
	@XmlElement
	public Member to;
	@XmlElement
	public Member from;
	@XmlElement
	public Member[] cc;
	@XmlElement
	public ReplyData reply;
	
	@XmlElement
	public NotificationMessage replyTo;

	@XmlAttribute
	public Date replyDate;
	
	@XmlAttribute
	public String team;
	
	public static final int LEVEL_TRACE=0;
	public static final int LEVEL_NORMAL=1;
	public static final int LEVEL_WARN=2;
	public static final int LEVEL_CRITICAL=3;
	public static final int LEVEL_EMERGENCY=4;
	public static final int LEVEL_HIDDEN=0x10;

	
	public NotificationMessage(){}
	public NotificationMessage(long pid, CardData action, Member to, Member from, Member[] cc) throws WorkflowException{
		this(pid, action, to, from, cc, null);
	}

	public NotificationMessage(long pid, CardData action, Member to, Member from, Member[] cc, NotificationMessage replyTo) throws WorkflowException{

		this.action = action;this.to= to; this.from = from;this.replyTo = replyTo;
		this.processId = pid;
		this.id = String.valueOf(pid) + "-" + Util.random();
		this.sentDate = new Date();
		this.cc = cc;
		this.team = this.from.team;
//		Calendar c = new GregorianCalendar();c.add(Calendar.SECOND,action.responseTime);
//		this.visibleDate = c.getTime();
		this.message = constructMessage(action, replyTo);
	}
	protected static ServiceLogger logger = ServiceLogger.getLogger();
	public static NotificationMessage makeBroadcastMessage(final String message, final CardData.Types actiontype){
		NotificationMessage ret = new NotificationMessage();
		ret.action = CardData.find(actiontype);
		if(ret.action == null){
			logger.error("通知アクションのデータが定義されていません。種類:" + actiontype);
		}
		ret.id = Util.random();
		ret.sentDate = new Date();
		ret.to  = Member.TEAM;
		ret.from = Member.SYSTEM;
		ret.message = message;
		return ret;
	}	
	protected String constructMessage(CardData data,  NotificationMessage replyTo) throws WorkflowException{
		StringBuffer buff = new StringBuffer();

		if(data.type.equals("query")){
			buff.append(data.name + " " + (data.message == null ? "" : data.message));
		}else if(data.type.equals("inform")){
			buff.append(data.name + " " +(data.message == null ? "" : data.message) + ":" + data.comment);
		}else if(data.type.equals("action") || data.type.equals("auto")){
			buff.append(/*data.name + " " +*/(data.message == null ? "" : data.message));
		}else if(data.type.equals("response")){
			if(replyTo == null)throw new WorkflowException("問い合わせ先が指定されていません。", HttpServletResponse.SC_BAD_REQUEST );
			buff.append(data.name + " " +(data.message == null ? "" : data.message));
		}else{
			buff.append((data.message == null ? "" : data.message));
		}
		
		return buff.toString();

	}
	
	public String stringify(){
		try{
			return new ObjectMapper().writeValueAsString(this);
		}catch(Throwable t){
			return "serialization failed:" + ((Object)this).toString();
		}
	}
	public String toString(){return stringify();}
	public NotificationMessage clone(){
		try{
			String org = this.stringify();
			NotificationMessage m = new ObjectMapper().readValue(org, NotificationMessage.class);
			return m;
		}catch(Throwable t){
			throw new WorkflowException("serialization failed:" + ((Object)this).toString(), t);
		}
	}

	/**通知先ユーザIDのリストを抽出
	 * @param team チーム名。
	 * @return ユーザIDのリスト*/
	protected Set<String> fetchRecipients(final String team){
		Set<String> ret = new HashSet<>();
		if(to.role.equals(Member.TEAM.role) || to.role.equals(Member.ALL.role)){
			List<Member> all = Member.getTeamMembers(team);
			if(all != null){
				for(Member cur : all){ret.add(cur.email);}
			}
		}else{
			if(this.to != null && this.to.email != null)
				ret.add(this.to.email);
			if(this.from != null && this.from.email != null)
				ret.add(this.from.email);
		}
		return ret;
	}
	/**添付されたステートカードのIDのリストを返す。
	 * @return 添付されたステートカードのIDリスト
	 * */
	protected Set<String> fetchStatecards(){
		Set<String> ret = new HashSet<>();
		if(this.action != null && this.action.attachments != null){
			ret.addAll(Arrays.asList(this.action.attachments));
		}
		if(this.reply != null && this.reply.state != null){
			ret.add(this.reply.state);
		}
		return ret;
	}

}
