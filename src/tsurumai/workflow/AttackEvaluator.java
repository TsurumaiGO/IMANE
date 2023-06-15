package tsurumai.workflow;

import tsurumai.workflow.model.ReplyData;

/**攻撃判定モジュールが実装するインタフェース。<br>
 * 攻撃判定モジュールは、攻撃アクションに対してシステムの動的状態を評価し、応答および後続アクションを制御する他の処理を追加するための仕組みを提供する。<br>
 * <br>
 * 攻撃判定モジュールを組み込むには:
 * <ul>
 * <li>AttackEvaluatorを実装したクラスを作成する。
 * <li>実装クラスをWebアプリケーション実行環境に配備する。
 * <li>setting.jsonのevaluatorsプロパティに実装クラスの完全修飾クラス名を記述する。
 * </ul>
 * 攻撃判定モジュール固有の外部パラメータを使用するには:
 * <ul>
 * <li>setting.jsonのparamsプロパティに任意のパラメータ(キー・値のセット)を定義する
 * <li>実装クラスの初期化処理(onInitialized())で、ScenarioDataのparamsフィールドからパラメタを取り出し、実装固有の初期化処理を行う。
 * </ul>
 * 
 * 
 * */
public interface AttackEvaluator{
	/**リプライを評価し、マッチしないならfalseを返す*/
	default public boolean evaluateReply(final ReplyData rep) {return true;};
	/**ステートが追加されたときに呼び出される*/
	default public void onAddState(String state) {};
	/**ステートが削除されたときに呼び出される*/
	default public void onRemoveState(String state) {};
	/**インスタンス初期化時に呼び出される*/
	default public void onInitialized(WorkflowInstance  inst) {};
	/**インスタンス終了時に呼び出される*/
	default public void onFinalized() {};
	/**演習開始時に呼び出される*/
	default public void onStarted(WorkflowInstance  inst) {};
	/**演習終了時に呼び出される*/
	default public void onEnded() {};

}