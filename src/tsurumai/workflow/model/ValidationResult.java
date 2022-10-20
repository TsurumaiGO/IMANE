package tsurumai.workflow.model;

import java.util.Map;
/**シナリオを構成するjsonファイルの検証結果を保持する。*/
public class ValidationResult {
	public String label;
	protected Throwable error;
	public String getError(){return error == null ? "" : error.toString();}
	public ValidationResult(){}
	public Map<String, String> params;
	public  boolean isValid(){return error == null;}
	public ValidationResult(final String label, final Throwable t, Map<String, String> params){this.label = label;this.error = t;this.params = params;}
	public ValidationResult(final String label, Map<String, String> params){this(label,null, params);}
}
