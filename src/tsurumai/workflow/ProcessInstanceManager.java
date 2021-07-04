package tsurumai.workflow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import javax.inject.Singleton;
import javax.security.auth.login.LoginException;

import tsurumai.workflow.model.Member;
@Deprecated
public class ProcessInstanceManager {
	public class Session{
		
		@Override
		public String toString() {
			return "user:" + userName;
			
		}
		public Session() {}
		public Session(final String username, final String password, boolean asAdmin)  throws WorkflowException{
			login(username, password, asAdmin);
		}
		public void login(final String username, final String password, boolean asAdmin) throws WorkflowException{
			this.userName = username;
			this.isAdmin = asAdmin;
			this.online = true;
		
			Member m = Member.getMember(username);
			if(m == null) throw new WorkflowException("ユーザIDまたはパスワードが誤っています。" + username);
			//passwdプロパティが設定されていない場合は必ず成功する
			if(!m.validateCredential(password))
				throw new WorkflowException("ユーザIDまたはパスワードが誤っています。" + username);
			return;
		}
		public String userName;
		public Locale locale;
		public boolean isAdmin = false;
		public boolean online = false;
		public boolean validate() {
			return this.online;
		}
		public String getUserName() {
			return this.userName;
		}
		public boolean isAdmin() {
			return false;
		}
		public Locale getLocale() {
			return this.locale;
		}
		public void logout() {}
	}
	public Session session;
	public ProcessInstanceManager() {
	}
	@Singleton
	static ProcessInstanceManager me  = null;
	long pidbase = new Date().getTime();
	public static synchronized ProcessInstanceManager getSession() {
		if(me == null) me = new ProcessInstanceManager();
		return me;
	}
	synchronized long assignInstanceId() {
		return ++getSession().me.pidbase;
	}
	Collection<ProcessInstance> instances = new ArrayList<>();
	public Session login(String sv, String userid, String passwd, boolean asAdmin) throws LoginException {
		//this.session = new Session(userid, passwd, asAdmin);
		return new Session(userid, passwd, asAdmin);
	}
	public ProcessInstance[] listProcesses() {
		return instances.toArray(new ProcessInstance[this.instances.size()]);
	}
	public synchronized void deleteInstance(long pid) {
		for(ProcessInstance i : this.instances.toArray(new ProcessInstance[this.instances.size()])) {
			if(i.getId() == pid) {
				this.instances.remove(i);return;
			}
		}
	}
	public void logout() {
		// TODO Auto-generated method stub
		
	}
	public ProcessInstance startProcessInstance(Map<String, String> params) {
		ProcessInstance ret = new ProcessInstance(assignInstanceId(), params);
		ret.start();
		this.instances.add(ret);
		return ret;
	}
	public ProcessInstance startProcessInstance() {
		return startProcessInstance(null);
	}

	public ProcessInstance getProcessInstance(long id) throws WorkflowException{
		ProcessInstance ret = _getProcessInstance(id);
		if(ret == null)
			throw new WorkflowException("プロセスインスタンスが見つかりません。id="+ String.valueOf(id));
		else
			return ret;
	}
	
	ProcessInstance _getProcessInstance(long id) throws WorkflowException{
		for(ProcessInstance i : this.instances.toArray(new ProcessInstance[this.instances.size()])) {
			if(i.getId() == id) {
				return i;
			}
		}
		return null;
	}
//	public boolean isValidProcess(long id) {
//		return _getProcessInstance(id) != null;
//	}

}
