package tsurumai.workflow;

import java.util.Date;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
//@JsonInclude(JsonInclude.Include.NON_NULL)
@Deprecated 
public class ProcessInstance{
	public String name;
	public int state;
	public Date date = new Date();
	public long pid ;
	public String pdname = "";
	public String appname = "";
	public Map<String, String> params;
	public WorkflowInstance workflow;
	public  ProcessInstance(long pid, Map<String, String> params){
		this.pid = pid;
		this.params = params;
	}
	public long getId() {return this.pid;}
	public long start() {
		this.state = STATE_RUNNING;
		return this.state;
	}
	

	public static final int STATE_RUNNING = 0x10;
	public static final int STATE_STOPPED = 0x20;
	public static final int STATE_SUSPENDED = 0x21;
	public static final int STATE_ABORTED = 0x22;
	public void abort() {this.state = STATE_ABORTED;}
	public void resume() {this.state = STATE_RUNNING;}
	public void suspend() {this.state = STATE_SUSPENDED;}


}