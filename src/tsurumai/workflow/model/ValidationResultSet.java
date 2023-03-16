package tsurumai.workflow.model;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import tsurumai.workflow.WorkflowException;
import tsurumai.workflow.WorkflowService;
import tsurumai.workflow.util.ServiceLogger;
import tsurumai.workflow.util.Util;


/**シナリオセットの検証結果を表現する*/
public class ValidationResultSet extends ArrayList<ValidationResult>{

	
	/**シナリオデータの検証結果*/

	static ServiceLogger logger = ServiceLogger.getLogger();
	
	private static final long serialVersionUID = 1L;
	public ValidationResultSet(){}
	String name = "";
	public ValidationResultSet(final String name){this.name = name;}
	public boolean isValid(){
		for(ValidationResult r : this){
			if(!r.isValid()) return false;
		}
		return true;
	}
	public String toString(){
		StringBuffer buff  = new StringBuffer();
		for(Iterator<ValidationResult> i = this.iterator(); i.hasNext();){
			ValidationResult r = i.next();
			if(!r.isValid()){
				buff.append(r.label + ":無効:" + r.error.toString() + "\n");
			}
		}
		if(buff.length() == 0)
			return (name != null ? name : "null")+ ":有効";
		return buff.toString();
	}
	
	
	
	
	/**選択されたシナリオデータをロードしてテストする
	 * @parma dir シナリオ格納先ディレクトリの相対パス
	 * */
	public static ValidationResultSet validateScenarioSet(String name) {
		//TODO: この処理でシナリオデータのパスが一時的に変更されるため、複数のシナリオセットがインストールされているとタイミングによってデータが不正に書き換えられる。
		
		final String dir = Path.of(name).isAbsolute() ? name : WorkflowService.getContextRelativePath("data") + File.separator + name;
		
		Map<String, ScenarioValidator> validators = new HashMap<String, ScenarioValidator>(){{
			put("リプライ", new ScenarioValidator() {
				@Override public Map<String, String>  validate() {
					Collection<ReplyData> reps = ReplyData.loadReply(-1, dir+ File.separator + "replies.json");
					Map<String, String> ret = new HashMap<>();
					ret.put("count", reps== null ? "0" : String.valueOf(reps.size()));
					return ret;
					}});
			put("アクション", new ScenarioValidator() {
				@Override public Map<String, String>  validate()  {
					Collection<CardData> actions = CardData.flatten(CardData.loadAll(dir + File.separator + "actions.json").values());
					Map<String, String> ret = new HashMap<>();
					ret.put("count", actions == null ? "0" : String.valueOf(actions.size()));
					ret.put("actions", String.valueOf(CardData.findList(CardData.Types.action, dir+ File.separator + "actions.json").length));
					ret.put("autoaction",String.valueOf(CardData.findList(CardData.Types.auto, dir + File.separator + "actions.json").length));
					return ret;
					}});
			put("ステート", new ScenarioValidator() {
				@Override public Map<String, String>  validate()  {
					Collection<StateData> states = StateData.loadAll(dir+ File.separator + "states.json");
					Map<String, String> ret = new HashMap<>();
					ret.put("count", states== null ? "0" : String.valueOf(states.size()));
					return ret;
					}});
			put("フェーズ", new ScenarioValidator() {
				@Override public Map<String, String>  validate()  {
					List<PhaseData> phases = PhaseData.load(dir + File.separator + "setting.json");
					Map<String, String> ret = new HashMap<>();
					ret.put("count", phases == null ? "0" : String.valueOf(phases.size()));
					return ret;
					}});
			put("ポイント", new ScenarioValidator() {
				@Override public Map<String, String>  validate()  {
					PointCard[] points = PointCard.loadAll(dir+ File.separator + "points.json");
					Map<String, String> ret = new HashMap<>();
					ret.put("count", points == null ? "0" : String.valueOf(points.length));
					return ret;
					}});
			put("メンバ", new ScenarioValidator() {
				@Override public Map<String, String>  validate() {
					Map<String, Member> members = Member.loadAll(dir + File.separator + "contacts.json");
					Map<String, String> ret = new HashMap<>();
					ret.put("count",  members == null ? "0" : String.valueOf(members.size()));
					return ret;
					}});
		}};

		ValidationResultSet res = new ValidationResultSet();
		logger.info("シナリオデータを検証します。 " + dir);
		for(String key : validators.keySet()){
			try{
				Map<String, String> params = validators.get(key).validate();
				params.put("basedir", dir);
				res.add(new ValidationResult(key, params));
				logger.info("シナリオデータを検証しました。" + key + "@" + dir + ":" + res.toString());
				
			}catch(Throwable t){
				res.add(new ValidationResult(key, t, null));
				logger.error("シナリオデータの検証に失敗しました。"  + key + "@" + dir + ":" + res.toString());
			}
		}
		return res;
	}

	/**シナリオセットの検証処理を実装する*/
	interface ScenarioValidator{
		public Map<String, String> validate();
	}
}