package tsurumai.workflow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import javax.xml.bind.annotation.XmlElement;

import org.apache.commons.lang3.ArrayUtils;

import tsurumai.workflow.model.ReplyData;
import tsurumai.workflow.model.ScenarioData;
import tsurumai.workflow.model.StateData;
import tsurumai.workflow.util.ServiceLogger;

public class AttackPathEvaluator implements AttackEvaluator {
	WorkflowInstance wf = null;
	ServiceLogger logger = ServiceLogger.getLogger();

	/**構成行列*/
	//ScenarioData経由でsetting.jsonからロードするよう修正
	public static int [][] C0 =null;//{{1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
//				                {1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
//				                {1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0},
//								{0,0,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0},
//								{0,0,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0},
//								{0,0,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0},
//								{0,0,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0},
//						        {0,0,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0},
//								{0,0,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0},
//								{0,0,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0},
//								{0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0},
//								{0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,0},
//								{0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,0},
//								{0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1},
//								{0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1},
//								{0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1},
//								{0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1}};
					
	public static int [][] eye=null;//{{1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
//			       				{0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
//							    {0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
//							    {0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
//							    {0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
//							    {0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0},
//							    {0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0},
//							    {0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0},
//							    {0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0},
//							    {0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0},
//							    {0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0},
//							    {0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0},
//							    {0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0},
//							    {0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0},
//							    {0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0},
//							    {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0},
//							    {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1}};

	//追加：単位行列・接続行列の作成
	public static int elem = 0;//C0.length;//17 ;

	public int NumberFW_I ;
	public int NumberFW_0;
	public int NumberVPN;
	public int NumberREMOTEACCESS;
	

	public static int[][] C = null;//new int[elem][elem];
	/**陥落行列*/
	public static int[][] D = null;//new int[elem][elem];
	public static int[][] F = null;//new int[elem][elem];
	public static int[][] B = null;//new int[elem][elem];

	
	
	
	@Override public boolean evaluateReply(ReplyData rep) {
		//攻撃パスの判定
		if(rep.attackpath != null) {
			try {
				String[] pathelem = rep.attackpath.split("/");
				int pathelem1;
				int pathelem2;
				pathelem1 = Integer.parseInt(pathelem[1].substring(pathelem[1].length()-2),10);
				pathelem2 = Integer.parseInt(pathelem[2].substring(pathelem[2].length()-2),10);
				if (!evaluateAttackPath(pathelem[0],pathelem1,pathelem2)) {
					logger.info(rep.name + ":パスの条件を満たしていないので失敗");
					return false ;
				}
			}catch(Throwable t) {
				logger.error(rep.name + ":attackpath要素の解析が不可能なため、攻撃パス演算を行いません。attackpathの入力ルールに反していないか確認してください。" + rep.toString(),t);
			}
		}
		return AttackEvaluator.super.evaluateReply(rep);
	}
	@Override public void onInitialized(WorkflowInstance inst) {
		this.wf = inst;

	}
	@Override public void onStarted(WorkflowInstance inst) {

		ScenarioData d = ScenarioData.getActiveScenario(); 
		AttackPathEvaluator.C0 = listToArray((ArrayList<?>)d.params.get("attackpath.C0"));
		AttackPathEvaluator.eye = listToArray((ArrayList<?>)d.params.get("attackpath.eye"));
		AttackPathEvaluator.elem = C0.length;
		NumberFW_I = (int)d.params.get("attackpath.NumberFW_I");
		NumberFW_0 = (int)d.params.get("attackpath.NumberFW_0");
		NumberVPN = (int)d.params.get("attackpath.NumberVPN");
		NumberREMOTEACCESS = (int)d.params.get("attackpath.NumberREMOTEACCESS");
		AttackPathEvaluator.C = new int[elem][elem];
		AttackPathEvaluator.D = new int[elem][elem];
		AttackPathEvaluator.F = new int[elem][elem];
		AttackPathEvaluator.B = new int[elem][elem];
		
		matrix_init();
		AttackEvaluator.super.onStarted(inst);
	}
	@Override  public void onAddState(String state) {
		//addstate関数に入れるもの
		String ATK = "ATK";
		String CON = "CON";
		String VUL = "VUL";
		String INF = "INF";
		String add = "add";
		
		if(state.contains("/")) {
			try {
				//state="ゾーン名orFW/機器ID(X01など)/ATKorCONorINForVUL"
				String[] str = state.split("/");
				int asset_number = Integer.parseInt(str[1].substring(str[1].length()-2),10);
				if(str[2].equals(ATK)){
					D_calculate(asset_number, add);
				}else if(str[2].equals(CON)){
					C_calculate(asset_number, add);
				}else if(str[2].equals(VUL)){
					B_calculate(asset_number,add);
				}else if(str[2].equals(INF)){
					F_calculate(asset_number,add);
				}
		
				//感染型の処理を追加
				if(str[2].equals(INF) || str[2].equals(CON) || str[2].equals(VUL)){
					INF_calculate();
				}
			}catch(Throwable t){
				logger.warn("攻撃パス判定に関わるステートである場合、ステートの入力ルールを満たしているか確認してください：" + state.toString());
			}
		}
		AttackEvaluator.super.onAddState(state);
	}
	
	@Override public void onRemoveState(String state) {
		if(state.contains("/")) {
			try {
				//state="ゾーン名orFW/機器ID(X01など)/ATKorCONorINForVUL"
				String[] str = state.split("/");
				int asset_number = Integer.parseInt(str[1].substring(str[1].length()-2),10);
				//removestate関数に入れるもの
				String ATK = "ATK";
				String CON = "CON";
				String VUL = "VUL";
				String INF = "INF";
				String remove = "remove";
				if(str[2].equals(ATK)){
					D_calculate(asset_number, remove);
				}else if(str[2].equals(CON)){
					C_calculate(asset_number, remove);
				}else if(str[2].equals(VUL)){
					B_calculate(asset_number,remove);
				}else if(str[2].equals(INF)){
					F_calculate(asset_number,remove);
				}
	
				//感染型の処理を追加
				if(str[2].equals(CON) || str[2].equals(VUL)){
					INF_calculate();
				}
			}catch(Throwable t){
				logger.warn("攻撃パス判定に関わるステートである場合、ステートの入力ルールを満たしているか確認してください：" + state.toString());
			}
		}
		AttackEvaluator.super.onRemoveState(state);
	}
	
	
	
	
	//C行列の要素を変える関数　2022/12/20
	public void C_calculate(int asset_number, String change){
		int i,j;
		int as = asset_number;
		String add = "add";
		String remove = "remove";

		if(change.equals(add)){
			for(i=0; i < elem; i++){
				C[i][as] = 0;
				C[as][i] = 0;
			}
		}else if(change.equals(remove)){
			for (i=0; i < elem; i++) {
				if(C0[as][i] != 0 && C[i][i] == 1) {  
					C[as][i] = 1;
				}
				if(C0[i][as] != 0 && C[i][i] == 1){
					C[i][as] = 1;
				}
			}
			C[as][as] = 1;
		}
		//結果確認(接続行列C)
//		System.out.print("C["+"\r\n");
//		for(i=0; i < elem; i++){
//			for(j=0; j < elem; j++){
//				if (j==elem){
//					System.out.print( C[i][j] );
//				}else{
//					System.out.print( C[i][j] + ",");
//				}
//			}
//			System.out.print("\r\n");
//		}
//		System.out.print("]" + "\r\n");
		dumpMatrix(C, "C=");

	}

	//D行列の要素を変える関数　2022/12/20
	public void D_calculate(int asset_number, String change){
		int i,j;
		String add = "add";
		String remove = "remove";
		int as = asset_number;

		if(change.equals(add)){
			D[as][as] = 1;
		}else if(change.equals(remove)){
			D[as][as] = 0;
		}

		//結果確認(陥落行列D)
//		System.out.print("D["+"\r\n");
//		for(i=0; i < elem; i++){
//			for(j=0; j < elem; j++){
//				System.out.print( D[i][j] + ",");
//			}
//			System.out.print("\r\n");
//		}
//		System.out.print("]" + "\r\n");
		dumpMatrix(D, "D=");

	}

	//B行列の要素を変える関数　2022/12/20
	public void B_calculate(int asset_number, String change){
		int i,j;
		String add = "add";
		String remove = "remove";
		int as = asset_number;

		if(change.equals(add)){
			B[as][as] = 1;
		}else if(change.equals(remove)){
			B[as][as] = 0;
		}

		//結果確認(脆弱性行列B)
//		System.out.print("B["+"\r\n");
//		for(i=0; i < elem; i++){
//			for(j=0; j < elem; j++){
//				System.out.print( B[i][j] + ",");
//			}
//			System.out.print("\r\n");
//		}

//		System.out.print("]" + "\r\n");
		dumpMatrix(B, "B=");
	}

	//F行列の要素を変える関数　2022/12/20
	public void F_calculate(int asset_number, String change){
		int i,j;
		String add = "add";
		String remove = "remove";
		int as = asset_number;

		if(change.equals(add)){
			F[as][as] = 1;
		}else if(change.equals(remove)){
			F[as][as] = 0;
		}

		//結果確認(陥落行列F)
//		System.out.print("F["+"\r\n");
//		for(i=0; i < elem; i++){
//			for(j=0; j < elem; j++){
//				System.out.print( F[i][j] + ",");
//			}
//			System.out.print("\r\n");
//		}
//		System.out.print("]" + "\r\n");
		dumpMatrix(F, "F=");

	}
	
	//感染拡大の計算(B,C,Fの行列の要素が変化する度に計算)　2022/12/20
	public void INF_calculate(){
		int i,j,k,n;
		String state_elem ;
		int[][] u = new int[elem][1];
		int[][] r = new int[elem][1];
		int[][] RE = new int[elem][elem];
		int[][] R = new int[elem][elem];
		int[][] R0 = new int[elem][elem];
		int[][] R1 = new int[elem][elem];

//		ScenarioData d = wf.getActiveScenario();
		int vpn = NumberVPN;
		int ra = NumberREMOTEACCESS;
		
		for(i=0; i<elem ; i++){
			u[i][0] = 1 ;
		}
		
		//raの状態を行ベクトルと列ベクトルで保存する
		int[][] VR = new int[elem][1];
		int[][] VC = new int[1][elem];

		//raの接続状態を保存
		for(i=0; i < elem; i++){
			VR[i][0] = C[i][ra];
			VC[0][i] = C[ra][i];
		}
				
		//vpn及びraが隔離されていないとき、計算中は一時的にraをvpnと同じ接続状態にする
		if(C[ra][ra] != 0 && C[vpn][vpn] != 0) {
			for(i=0; i < elem; i++){
				C[i][ra] = C[i][vpn];
				C[ra][i] = C[vpn][i];
			}
			C[ra][ra] = 1;
		}
		
		//R0,R = (B+F)*C
		for(i=0; i < elem; i++){
			for(j=0; j < elem; j++){
				for(k=0; k < elem; k++){
					R0[i][j] += (B[i][k]+F[i][k] )* C[k][j];
				}
				R[i][j] = R0[i][j];
			}
		}

		//for n=1 to elem-1
		//RE=(R+eye(elem))
		//R=RE*R0
		for(n=1; n <elem; n ++){
			for(i=0; i < elem; i++){
				for(j=0; j < elem; j++){
					RE[i][j] = (R[i][j] + eye[i][j] );
				}
			}
			for(i=0; i < elem; i++){
				for(j=0; j < elem; j++){  
					for(k=0; k < elem; k++){
						if(k==0){
							R[i][j] = RE[i][k] * R0[k][j];
						}else{
							R[i][j] += RE[i][k] * R0[k][j];
						}
					}
				}
			}
		}

		//R1=R*D
		for(i=0; i < elem; i++){
			for(j=0; j < elem; j++){
				for(k=0; k < elem; k++){
					if(k==0){
						R1[i][j] = R[i][k] * F[k][j];
					}else{        
						R1[i][j] += R[i][k] * F[k][j];
					}
				}
			}
		}
		
		//R=R1*uベクトル
		for(i=0; i < elem; i++){
			for(k=0; k < elem; k++){
				if(k==0){
					r[i][0] = R1[i][k]* u[k][0];
				}else{
					r[i][0] += R1[i][k]* u[k][0];
				}      
			}
		}
		
		//raの接続状態を元の状態に戻す
		for(i=0; i < elem; i++){
			C[i][ra]= VR[i][0];
			C[ra][i] = VC[0][i];
		}
		

		//攻撃対象が攻撃可能かを判定する
		for(i=1; i<elem; i++){
			//新しく感染した機器をステート追加
			if (r[i][0]!=0 && F[i][i]==0){
				state_elem = String.format("%02d",i) + "/INF";
				String INF_state = StateData.getStateID(state_elem);
				
				StateData s = StateData.getStateData(INF_state);
				if(s == null){
					logger.warn("存在しないシステムステートを追加しようとしました。" + INF_state.toString());
					s = new StateData(INF_state);
				}
				logger.info("システムステートを追加します。" + s.toString());
				
				//TODO: これはよろしくないかもしれない
				wf.systemState.put(INF_state, new Date());
				wf.onAddState(INF_state, "ikura");
			}
		}
	}
	
	//attackpath の計算
	public boolean evaluateAttackPath(String str,int from, int to){
		int i,j,k,n,result;
		int[][] v = new int[1][elem];
		int[][] u = new int[elem][1];
		int[][] r = new int[elem][1];
		int[][] RE = new int[elem][elem];
		int[][] RD = new int[elem][elem];
		int[][] R = new int[elem][elem];
		int[][] BR = new int[elem][elem];
		u[from][0] = 1 ;
		v[0][to] = 1 ;
		
//		ScenarioData d = wf.getActiveScenario();
		int fwI = NumberFW_I ;
		int fw0 = NumberFW_0;
		int vpn = NumberVPN;
		int ra = NumberREMOTEACCESS;
		
		int fwI_diag;
		int vpn_diag;
		
		//元のFWの状態を保存
		fwI_diag =D[fwI][fwI] ;	
		//元のvpnの状態を保存
		vpn_diag =D[vpn][vpn] ;

		
		if(str.contains("D")){
			if(D[ra][ra] == 1 && C[ra][ra] == 1){
				//リモートアクセスPCが攻略済みだった場合、VPNを攻略済みとして扱う。
				//D行列のVPNの対角成分を1にする
				D[vpn][vpn] = 1;
			}
			/*フィッシング等、内部者の過失によってネットワーク内部の機器を侵害された場合、侵害された機器からインターネットまでの道の途中にあるFWは攻撃者の通信を通してしまうため、
	    	攻撃パスの判定中はそのFWを攻撃済みのものとして扱う */
			for(j = fwI; j < fw0; j++){
				if(j != ra && D[j][j] == 1 && C[j][j] == 1){
					//D行列のFWの対角成分を1にする
					D[fwI][fwI] = 1;
					break;
				}
			}
		}

		//R = C or C*D
		for(i=0; i < elem; i++){
			for(j=0; j < elem; j++){
				if(str.contains("D")){
					for(k=0; k < elem; k++){
						R[i][j] += C[i][k]*D[k][j];
					}
				}else{
					R[i][j] = C[i][j];
				}				
			}
		}

		//for n=1:elem-1
		// R=(R+eye(elem))*C
		for(n=1; n <elem; n ++){
			for(i=0; i < elem; i++){
				for(j=0; j < elem; j++){
					RE[i][j] = (R[i][j] + eye[i][j] );
				}
			}
			for(i=0; i < elem; i++){
				for(j=0; j < elem; j++){     
					for(k=0; k < elem; k++){
						if(k==0){
							if(str.contains("D")){
								RD[i][j] = RE[i][k] * D[k][j];
								R[i][j] = RD[i][k] * C[k][j];
							}else{
								R[i][j] = RE[i][k] * C[k][j];
							}							
						}else{
							if(str.contains("D")){
								RD[i][j] += RE[i][k] * D[k][j];
								R[i][j] += RD[i][k] * C[k][j];
							}else{
								R[i][j] += RE[i][k] * C[k][j];
							}							
						}
					}
				}
			}
		}

		//BR=B*R or R
		for(i=0; i < elem; i++){
			for(j=0; j < elem; j++){
				if(str.contains("B")){
					for(k=0; k < elem; k++){
						if(k==0){
							BR[i][j] = B[i][k] * R[k][j];
						}else{        
							BR[i][j] += B[i][k] * R[k][j];
						}
					}
				}else{
					BR[i][j] = R[i][j];
				}
			}
		}

		//r=BR*v 
		for(i=0; i < elem; i++){
			for(k=0; k < elem; k++){
				if(k==0){
					r[i][0] = BR[i][k]* u[k][0];
				}else{
					r[i][0] += BR[i][k]* u[k][0];
				}      
			}
		}

		if(str.contains("D")){
			//fwのD行列をもとに戻す
			D[fwI][fwI] = fwI_diag;	
			//vpnのD行列をもとに戻す
			D[vpn][vpn] = vpn_diag;
		}

		result =0;
		for(k=0; k < elem; k++){
			result += v[0][k] * r[k][0];
		}

		//攻撃対象が攻撃可能かを判定する
		if (result==0){
			return false;
		}else{
			return true;
		}
	}
	
	public void matrix_init(){
		int i,j;

		//行列を初期化
		for(i=0; i < elem; i++){
			for(j=0; j < elem; j++){
				C[i][j] = 0;
				D[i][j] = 0;
				F[i][j] = 0;
				B[i][j] = 0;
			}
		}


		//C0 = C　にする
		for(i=0; i < elem; i++){
			for(j=0; j < elem; j++){
				C[i][j] = C0[i][j];
			}
		}

		D[0][0] = 1;
		B[0][0] = 1;
				
		//確認のため出力
//		System.out.print("C["+"\r\n");
//		for(i=0; i < elem; i++){
//			for(j=0; j < elem; j++){
//				System.out.print( C[i][j] + ",");
//			}
//			System.out.print("\r\n");
//		}
//		System.out.print("]" + "\r\n");    
		dumpMatrix(C, "C=");
	}
	
	void dumpMatrix(int[][]arr, String label) {
		StringBuffer buff = new StringBuffer();
		buff.append(label + Arrays.deepToString(arr));
		logger.info(buff.toString());
	}
	/**ArrayList<ArrayList<Integer>>をint[][]に変換する*/
	static int[][] listToArray(ArrayList<?> arr){
		int size = arr.size();
		ArrayList<int[]> buff = new ArrayList<int[]>();
		arr.iterator().forEachRemaining(e->{
			ArrayList<?> cur = (ArrayList<?>)e;
			Integer[] c = (Integer[])cur.toArray(new Integer[cur.size()]);
			buff.add(ArrayUtils.toPrimitive(c));
		});
		int[][] ret = buff.toArray(new int[size][]);
		return ret;
	}
}
