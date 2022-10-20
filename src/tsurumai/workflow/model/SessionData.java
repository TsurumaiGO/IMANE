package tsurumai.workflow.model;

import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tsurumai.workflow.ProcessInstanceManager;
import tsurumai.workflow.WorkflowException;

@XmlRootElement
@JsonIgnoreProperties({"comment","","//","#"})
//@JsonInclude(JsonInclude.Include.NON_NULL)
/***ログインユーザのション情報を保持する。システム内部でのみ使用される。*/
public class SessionData {
	@XmlAttribute
	public String username;
	@XmlAttribute
	public String userid;
	@XmlAttribute
	public String locale;
	@XmlAttribute
	public String sessionkey;
	@XmlAttribute
	public boolean isadmin;
	@XmlAttribute
	public String clientId;
	@XmlElement
	public String role;
	@XmlAttribute
	public String team;
	
//	@XmlTransient
//	//protected WFSession wfsession;
//	protected ProcessInstanceManager.Session wfsession;
//	@XmlTransient
////	public WFSession getWFSession(){return wfsession;}
//	public ProcessInstanceManager.Session getWFSession(){return wfsession;}
	
	public SessionData(){}
	
	public void enterSession(final String clientId){
		this.clientId = clientId;
	}

	public SessionData(final ProcessInstanceManager.Session s, final String sessionkey, final String passwd, final String clientId) throws WorkflowException{
		try{
			this.userid = s.getUserName();
			this.sessionkey = sessionkey;
			this.isadmin = s.isAdmin();
			this.locale = s.getLocale() != null?s.getLocale().getDisplayName():"";
			this.clientId = clientId;
//			this.roles = roles;
			
//			this.wfsession = s;
			Member me = Member.getMember(this.userid);
			if(me == null){throw new WorkflowException("ログオンに失敗しました。IDまたはパスワードが一致しません。(n)", HttpServletResponse.SC_UNAUTHORIZED);}
			
			this.role = me.role;
			this.username = me.name;
			this.team = me.team;
			if(passwd != null){
				if(!me.validateCredential(passwd))
					throw new WorkflowException("ログオンに失敗しました。IDまたはパスワードが一致しません。", HttpServletResponse.SC_UNAUTHORIZED);
			}
			
		}catch(Throwable t){
			throw new WorkflowException("セションの生成に失敗しました。", t);
		}
	}
}
