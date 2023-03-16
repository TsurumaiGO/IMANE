package tsurumai.workflow.model;
 
import java.beans.Transient;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;
import javax.websocket.Session;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

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

@JsonIgnoreProperties({"comment","","//","#"})
//@JsonInclude(JsonInclude.Include.NON_NULL)
@XmlRootElement
public class Member {
	protected static ServiceLogger logger = ServiceLogger.getLogger();

//	protected static String defaultDir;
//	public static void setDefaultDirectory(final String def){defaultDir = def;}
	protected static String targetFile;
	protected static CacheControl<Map<String, Member>> cache = new CacheControl<>();
	public static void reload(final String file){
		cache.reload(targetFile = file);

		Map<String, Member> def = Util.dataExists("sys/contacts.json") ?
				loadAll(WorkflowService.getContextRelativePath("sys") + File.separator + "contacts.json") : new HashMap<>();
		Map<String, Member> cust = loadAll(Member.cache.getTargetFile());
		Map<String,Member> all = new HashMap<String, Member>();
		all.putAll(def);
		all.putAll(cust);
		cache.set(all);

		logger.info("ユーザデータを再ロードしました。" + file);
	}
	/**名前。*/
	@XmlAttribute
	public String name;
	/**ロールを示す識別子。*/
	@XmlAttribute
	public String role;
	/**ロールの表示名。*/
	@XmlAttribute
	public String rolename;
	/**ロールのログインID。*/
	@XmlAttribute
	public String email;
	/**ロールの説明。*/
	@XmlAttribute
	public String desc;
	/**各フェーズで自動応答ユーザかどうかを示す。配列のインデックスはフェーズ番号に対応する。*/
	@XmlAttribute
	public boolean[] system;
	/**このロールが属するチーム名。*/
	@XmlAttribute
	public String team;
	/**このロールが現在オンラインかどうかを示す。演習中のみ有効。*/
	@XmlAttribute
	public boolean online;
	/**演習中にロールの状態を保持する。演習中のみ有効。*/	
	@XmlAttribute
	public String state;
	@XmlAttribute
	public boolean hidden=false;

	/**このロールがアクションを実行する際の宛先リストのソート順を指定する。*/	
	@XmlAttribute
	public String[] recipients;
	/**ロールのリストを返すときの(デフォルトの)ソート順を指定する。*/
	@XmlAttribute
	public int order;
	
	/**ファシリテータかどうかを示す。*/
	@XmlAttribute
	public boolean isadmin = false;
	
	/**このロールが入手済みのステートカードのIDのリストを保持する。演習中のみ有効。*/
	//TODO: WorkflowInstance.memberStatesと同期すると、演習開始からステートカードをメンバに持たせることができる。
	@XmlAttribute
	public Set<String> availableStates = new HashSet<>();

	/**カードのｈｔｍｌ要素にcssクラス名として展開される。カードにアイコンやスタイルを指定するために使用する。*/
	@XmlAttribute
	public String icon;
	
	/**experimental*/
	/**属するグループ(サブチーム)*/
	@XmlAttribute
	public String group;
//	/**experimental*/
//	@XmlAttribute
//	public String[] groupRole;
	
	/**ログイン時のパスワード。未設定の場合は空文字でログイン可能。*/
	@XmlTransient
	public String passwd;
	
	

	/**宛先に指定可能な特殊なロール*/
	public interface SpecialRole{
		/**全員*/
		public String ALL = "all";
		/**チームメンバ全員*/
		public String TEAM = "team";
		/**グループメンバ全員*/
		public String GROUP = "group";
		/**システム*/
		public String SYSTEM = "system";
	}
	
	protected javax.websocket.Session session;
	
	public Member online(Session session){
		this.online = session == null ?  false : session.isOpen();this.session = session;return this;}
	public Member(){}

	public static Member SYSTEM = new Member(){{this.name=this.rolename="システム";this.desc="システム";this.system = new boolean[]{true, true,true,true};this.role=SpecialRole.SYSTEM ;}};
	public static Member ALL = new Member(){{this.name=this.rolename="全員";this.desc="全員";this.system = new boolean[]{true, true,true,true};this.role=SpecialRole.ALL;}};
	public static Member TEAM = new Member(){{this.name=this.rolename="チーム全員";this.desc="チーム全員";this.system = new boolean[]{true, true,true,true};this.role=SpecialRole.TEAM;}};
	public static Member GROUP = new Member(){{this.name=this.rolename="チーム全員";this.desc="グループ全員";this.system = new boolean[]{true, true,true,true};this.role=SpecialRole.GROUP;}};
	
	/**すべてのメンバのリストを取得する。<br>前回ロードしたcontacts.jsonファイルの内容を返す*/
	public static Map<String,Member> loadAll() throws WorkflowException{
		if(cache.load(targetFile) != null)return cache.get();

		reload(targetFile);
		return cache.get();

	}
	/**
	 * 指定されたファイルでjson定義されたすべてのメンバのリストを取得する。
	 * @param path contacts.jsonファイルのパス*/
	public static Map<String, Member> loadAll(final String path) throws WorkflowException{
		try{
			
			Map<String, Member> ret = new HashMap<String, Member>();
			ObjectMapper mapper = new ObjectMapper();
			String contents = Util.readAll(path);
			JSONArray arr  = new JSONObject(contents).getJSONArray("contacts");
			for(java.util.Iterator<Object> i = arr.iterator();i.hasNext();){
				Object o = i.next();
				Member cur = (Member)mapper.readValue(o.toString(), Member.class);
				ret.put(cur.email, cur);
			}
		//	cached = ret;
			return ret;
		}catch(JsonProcessingException t){
			throw new WorkflowException("JSONデータの処理に失敗しました。", HttpServletResponse.SC_NOT_FOUND, t);
		}catch(IOException t){
			throw new WorkflowException("シナリオデータの読み込みに失敗しました。", HttpServletResponse.SC_NOT_FOUND, t);
		}
	}
	/**パスワードを検証する。<br>
	 * passwdプロパティに指定されたハッシュ値との照合により比較する。
	 * @param passwd 平文のパスワード
	 * */
	public  boolean validateCredential(final String passwd){
		if(this.passwd == null)return true;
		if(passwd == null && this.passwd == null)return true;
		return this.passwd == null ? false : Util.matchHash(this.passwd, passwd);//this.passwd.equals(passwd);
	}
	/**
	 * チームに属するメンバのリストを取得する。<br>
	 * リストはメンバのorderプロパティによってソートされる。
	 * @param teamname チーム名*/
	@Transient
	public static List<Member> getTeamMembers( final String teamname) throws WorkflowException{
		List<Member> ret = new ArrayList<>();
		Map<String, Member> all = loadAll();
		for(Member m : all.values()){
			if(m.team.equals(teamname))ret.add(m);
		}
		Collections.sort(ret, new Comparator<Member>() {
			@Override public int compare(Member o1, Member o2) {
				if(o1.order != 0 || o2.order != 0){
					return Integer.compare(o1.order,  o2.order);
				}else{
					return o1.rolename.compareTo(o2.rolename);
				}
			}
		});
		return ret;
	}
	/**指定されたロールをもつチームメンバを返す*/
	public static Member roleToMember(final String teamname, final String userrole) throws WorkflowException{
		if(Member.TEAM.role.equals(userrole))return Member.TEAM;
		if(Member.ALL.role.equals(userrole))return Member.ALL;
		if(Member.SYSTEM.role.equals(userrole))return Member.SYSTEM;
//		Map<String, Member> all = loadAll(Member.defaultDir);
		
		Map<String, Member> all = loadAll();
		for(Member m : all.values()){
			if(!teamname.equals(m.team)) continue;
			if(m.role.equals(userrole))return m;
		}
		return null;
	}
	/***ユーザIDを指定してメンバを検索する*/
	public static Member getMember(final String userid) throws WorkflowException{
		Map<String, Member> all = loadAll();
		return all.get(userid);
	}
	/**指定されたチームとロール名をもつメンバを検索する*/
	public static Member getMemberByRole(final String teamname, final String rolename){
		if(rolename.equalsIgnoreCase(Member.SYSTEM.role))	return Member.SYSTEM;
		if(rolename.equalsIgnoreCase(Member.ALL.role))return Member.ALL;
		if(rolename.equalsIgnoreCase(Member.TEAM.role))return Member.TEAM;
		List<Member> members =getTeamMembers( teamname);
		for(Member m : members){
			if(m.role.equalsIgnoreCase(rolename))
				return m;
		}
		return null;
	}
	/**チーム名のリストを取得する。*/
	public static String[] getTeams(){
		Map<String, Member> all = loadAll();
		Collection<String>groups = new ArrayList<>();
		for(Member m : all.values()){
			if(!groups.contains(m.team))groups.add(m.team);
		}
		String[] ret =  groups.toArray(new String[groups.size()]);
		Arrays.sort(ret);
		return ret;
	}
//	public static Member newInstance(final String userid, Session s) throws WorkflowException{
//		Member me = getMember(userid);
//		me.online(s);
//		return me;
//	}
	public String toString(){
		try{
		return new ObjectMapper().writeValueAsString(this);
		}catch(JsonProcessingException t){return "parse error";}
	}
	/**Memberの配列からロールIDのコレクションを抽出*/
	public static Collection<String> getRoles(Member[] src){
		Collection<String> ret = new ArrayList<>();
		for(Member m : src){
			ret.add(m.role);
		}
		return ret;
	}
	
	/**ロールID文字列の集合からMemberのリストに変換*/
	public static List<Member> getMembers(String team, String[] roles){
		List<Member> ret = new ArrayList<>();
		if(!(roles != null && roles.length > 0))
			return ret;

		for(int i = 0; i < roles.length; i ++){
			Member tmp = Member.getMemberByRole(team, roles[i]);
			if(tmp == null){
				logger.warn(String.format("指定されたロールが見つかりません。%s", roles[i]));
			}else{
				ret.add(tmp);
			}
		}
		return ret;
	}
	/**システムユーザかどうかを返す。<br>
	 * systemプロパティが設定されており、指定されたフェーズの値がtrueである場合にシステムユーザとして判断される。
	 * 
	 * @param phase フェースを指定する。-1を指定すると、フェーズ1として処理する*/
	public boolean isSystemUser(int phase){
		if(this.system == null || this.system.length == 0)
			return false;
		if(phase != -1 && this.system.length >= phase)
			return this.system[phase-1];
		return this.system[0];
	}
	/**指定されたロールがマッチするか<br>
	 * <ul>
	 * <li>自分がALL|TEAMの場合、すべてにマッチ
	 * <li>自分がALL|TEAMでなく、roleが一致するならマッチ
	 * <li>roleがnullならマッチ  これでいいか？
	 * </ul>
	 * */
	public boolean matches(final String role){
		if(this.equals(ALL) || this.equals(TEAM)) return true;
		if(role != null && (role.equals(ALL.role) || role.equals(TEAM.role))) return true;
		if(role == null)return true;
		return this.role.equals(role);
		
	}
	
//	/**指定されたロールがマッチするか<br>
//	 * <ul>
//	 * <li>自分がALL|TEAMの場合、すべてにマッチ
//	 * <li>自分がALL|TEAMでなく、roleが一致するならマッチ
//	 * <li>roleがnullならマッチ  これでいいか？
//	 * </ul>
//	 * */
//	@Override
//	public boolean equals(Object obj) {
//		if(obj instanceof String)
//			return matches((String)obj);
//		else if(!(obj instanceof Member))
//			return false;
//		//return matches(((Member)obj).role);
//
//
//	}

}
