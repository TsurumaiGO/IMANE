 
//globals
/**演習ワークフロー状態のコンテナ*/
//window.FRICORE = {};
var FrICORE = function(){
	/**管理者モード。*/
	this.isadmin =false;
	/**デバッグモード。'.debugmenu'クラスを指定された要素はデバッグモードのときに表示される。*/
	this.debug = false;
	/**ログイン中のユーザのセションキー。リクエストヘッダに設定*/
	this.wfsession = null;
	/**ログイン中のユーザのセション情報*/
	this.usersession = null;
	/**web socketインスタンス*/
	this.socket = null;
	/**チームメンバ定義情報*/
	this.members = null;
	/**アクションカード情報*/
	this.actions = null;
	/**チームメンバのプレゼンス情報*/
	this.presence = null;
	/**受信済イベント*/
	this.events = [];
	/**すべてのステートカード*/
	this.states = [];
	/**入手済ステートカード*/
	this.availableStates= [];
	/**現在のワークフロー状態(ファシリテータ画面)*/
	this.workflowstate={};
	/**ワークフロープロセスリスト(ファシリテータ画面)*/
//	this.wfprocesses=[];
	this.count=[];
	/**シーケンス図データ*/
	this.sequence={};

	/**実験的機能の有効化*/
	this.experimental = false;
	/**デバッグ用プロパティ;コンソールログの最大行数*/
	this.MAX_LOG_LINES = 5;
	/**デバッグ用プロパティ;コンソールログの最大文字数*/
	this.MAX_LOG_CHARS = 300;
	/**デバッグ用プロパティ;トレース情報の出力ON/OFF*/
	this.enableTrace = true;
	/**デバッグ用プロパティ;演習状態の自動更新を無効にする*/
	this.disableAutoRefresh = false;	
	this.skin = "#theme-fancy";
	this.level = {
		NORMAL:0,
		TRACE:1,
		WARN:2,
		CRITICAL:4,
		EMERGENCY:8,
		HIDDEN:0x10,
		CONTROL:0x20
	};
	/*
	this.markerMapping=[
		{selector:".data-imane-status-normal",matcher:"(HEA)([0-9]+)", resetState:true},
		{selector:".data-imane-status-vulnerable",matcher:"(VUL)([0-9]+)"},
		{selector:".data-imane-status-compromised",matcher:"(ATK)([0-9]+)"},
		{selector:".data-imane-status-infected",matcher:"(INF)([0-9]+)"},
		{selector:".data-imane-status-quarantined",matcher:"(CON)([0-9]+)"}
	]*/
	this.markerLabel = {
		HEA: "正常",
		VUL: "脆弱性あり",
		ATK: "乗っ取られている", 
		INF: "感染している", 
		CON: "隔離されている"
	};

};

FrICORE.info = function(msg){
	console.log("[info] " + digestText(msg.toString()));
};
FrICORE.error = function(msg, xhr){
	try{
		console.error("[error] " + digestText(msg) + " " + xhrToString(xhr));
		console.error(parseStackTrace("Function.FrICORE.error"));
	}catch(t){
		console.error("unknown error " + t);
	}
}
FrICORE.warn = function(msg){
	console.error("[warn] " + digestText(msg.toString()));
};
FrICORE.trace = function(msg){
	if(this.enableTrace || this.debug)
		this.info("[trace] " + msg);
};

/**boolean型の設定プロパティを切り替える*/
FrICORE.prototype.toggleConfig = function(classname, propertyname){
	var checked = $(classname).data("checked");
	this.applyConfig(classname, propertyname, checked);
};

/**設定項目をチェックメニュー項目に適用する*/
FrICORE.prototype.applyConfig = function(classname, propertyname, val){
	var checked = !val;
	$(classname).data("checked",!checked)
		.removeClass(checked ? "checked" : "").addClass(!checked ? "checked" : "");
	if(this.hasOwnProperty(propertyname)){
		this[propertyname] = !checked;
		FrICORE.info("config property changed: " + propertyname);
	}else
		FrICORE.warn("no config property found: " + propertyname);
};

/**ローカル設定を保存(試験的)*/
FrICORE.prototype.saveConfig = function(){
	
	this.syncConfig(false);
	var config = {debug:this.debug, 
		experimental: this.experimental, 
		enableTrace: this.enableTrace, 
		disableAutoRefresh: this.disableAutoRefresh,
		skin: this.skin
	};
	window.localStorage.setItem("FrICORE.config", JSON.stringify(config));
};
/**ローカル設定をロード(試験的)*/
FrICORE.prototype.loadConfig = function(){
	var config = JSON.parse(window.localStorage.getItem("FrICORE.config"));
	if(!config){
		config = {};
		FrICORE.warn("local config data not found.");
	}
	 
	this.debug = config.debug || false; 
	this.experimental = config.experimental || false;
	this.enableTrace = config.enableTrace || false;
	this.disableAutoRefresh = config.disableAutoRefresh || false;
	this.skin = config.skin;
	this.syncConfig(true);
};
/**設定をビューに反映
load : false:ビュー>ローカル、true:ローカル>ビュー
*/
FrICORE.prototype.syncConfig = function(load){
	if(load){
		changeTheme(this.skin || "");
		this.applyConfig('.config-experimental', 'experimental', this.experimental);
		this.applyConfig('.config-debug', 'debug', this.debug);
		this.applyConfig('.config-autorefresh', 'disableAutoRefresh', this.disableAutoRefresh);
		this.applyConfig('.config-trace', 'enableTrace', this.enableTrace);
	}else{
		this.skin = $(".theme-custom[disabled!=false]").prop("id");
		this.experimental = $('.config-experimental').data("checked");
		this.debug = $('.config-debug').data("checked");
		this.disableAutoRefresh = $('.config-autorefresh').data("checked");
		this.enableTrace = $('.config-trace').data("checked");
	}
}

window.FRICORE = new FrICORE();

var versioninfo={SERVER_VERSION:'unknown', SERVER_DESC:'unknown',
		CLIENT_VERSION:'unknown', CLIENT_DESC:"unknown"}

//utilities------------------------------------------------------------------

/**スタックトレース情報を分解する

errがErrorオブジェクトの場合、Error.stackプロパティからスタックトレースを取得する。そうでない場合は新規にスタックトレースを生成する。
baseがで指定された関数(Function.関数名)、この関数自身(Function.getStackTrace)が出現するまでのスタックトレース情報は捨てる。

@return [{label:関数名,line:ファイル名:行数}]
*/
function parseStackTrace(base, err){
	var e = err || new Error();

	var arr = e.stack.split("\n").slice(1);
	var basepoint = "";
	if(typeof base == "object" && base.hasOwnProperty("stack"))
		basepoint = base.stack;
	else if(typeof base == "string")
		basepoint = base || "Function.getStackTrace";

	var regex = new RegExp("\\([ ]+at " + base + "\\) (.*)");
	var buff = [];
	for(var i = 0; i < arr.length; i ++){
		var g = regex.exec(arr[i]);
		if(!g) continue;
		if(g.length < 2) continue;
		buff.push({label:g[1],line:g[2]});
	}
	return buff.map(e=>{return "  " + e}).join("\n");
}
/**parseStackTrace()の復帰値をエラーコンソールに出力する*/
function printStackTrace(base, err){
	var st = parseStackTrace(base, err);
	if(_.isArray(st)){
		console.error(st.reduce(e=>{"  at " + e.label + " " + e.line;}));
	}else{
		console.error(st);
	}
}

/** URLパラメタを分解*/
function parseURL(){
	var arr=location.search.substring(1).split('&');
	var params = {};
	for(var i=0;arr[i];i++) {
	    var kv = arr[i].split('=');
	    params[kv[0]]=kv.length > 1 ? kv[1] : true;
	}
	return params;
}
/***/
var Monitor = function(){
	this.last = new Date().getTime();
}
Monitor.prototype.lap = function(str){
	var now = new Date().getTime();
	console.log(formatDate(now) + ": " + (str||'') + " :" + (now - this.last)/1000 + " sec");
	this.last=now;
}
var monitor = new Monitor();


/**管理者メニューを表示*/
function wfview(){
	$("#container").hide();
	$("#history").hide();
	$("#wfview").show();
	$(window).trigger('resize');
}
/**演習メニューを表示*/
function dashboard(){
	$("#container").show();
	$("#history").hide();
	$("#wfview").hide();
	$(window).trigger('resize');
}
/**演習振り返りメニュー(シーケンス図)を表示*/
function history(){
	$("#container").hide();
	$("#history").show();
	$('#svgui').show();
	$("#wfview").hide();
	$(window).trigger('resize');
	updateTeamPicker();
	//演習ユーザがログオン中なら所属グループの履歴を表示
	if(FRICORE.usersession && FRICORE.usersession.team && !FRICORE.usersession.isadmin){
		$('#teampicker').trigger('change');
	}
}
/**振り返り画面のチームリストを更新する*/
function updateTeamPicker(){
	if(!FRICORE.workflowstate){return;}	
//	if(!FRICORE.wfprocesses){
		getProcess();
//	}
	$('#teampicker').empty();
	$('#teampicker').append('<option value=""/>');
	
	_.each(FRICORE.wfprocesses, function(e){
		var elem = _.template('<option value="<%=team%>"><%=team%></option>', e);
		$('#teampicker').append($(elem));
	});
	var selected = getSelectedTeam();// || window.workflowstate.team;
	if(selected && $('#teampicker').val() != selected){
		$('#teampicker').val(selected).trigger('update');
		$('#teampicker').trigger('change');
	}

}

/**汎用ダイアログ
 * @param {string} msg メッセージ
 * @param {string} type ダイアログの種類。okcancel|yesno|yesocancel|ok(デフォルト)
 * @param {string} level レベル。error|warning|normal(デフォルト)
 * @param {string} title ダイアログのキャプション
 * @param {boolean} editable 入力ボックスを有効にする
 * @param {function} callback ボタンが押されたときに呼び出される。thisを引数として受け取る
 * */
function dialog(msg, type, level, title, editable, callback){
	var buttons = [];
	switch(type){
	case 'okcancel':
		buttons.push({text:'OK',click:function(){this.result = 'OK';$(this).dialog("close");if(callback)callback(this);}});
		buttons.push({text:'キャンセル',click:function(){this.result = 'Cancel';$(this).dialog("close");if(callback)callback(this);}});
		break;
	case 'yesno':
		buttons.push({text:'はい',click:function(){this.result = 'Yes';$(this).dialog("close");if(callback)callback(this);}});
		buttons.push({text:'いいえ',click:function(){this.result = 'No';$(this).dialog("close");if(callback)callback(this);}});
		break;
	case 'yesnocancel':
		buttons.push({text:'はい',click:function(){this.result = 'Yes';$(this).dialog("close");if(callback)callback(this);}});
		buttons.push({text:'いいえ',click:function(){this.result = 'No';$(this).dialog("close");if(callback)callback(this);}});
		buttons.push({text:'キャンセル',click:function(){this.result = 'Cancel';$(this).dialog("close");if(callback)callback(this);}});
		break;
	case 'ok':
	default:
		buttons.push({text:'OK',click:function(){result = 'OK';$(this).dialog("close");if(callback)callback(this);}});
		break;
	}
	
	$("#dialogtype").removeClass('warning').removeClass('error');
	
	$("#dialogtype").removeClass("ui-icon-notice").removeClass("ui-icon-alert").removeClass("ui-icon-info");
	switch(level){
		case 'warning':
			$("#genericdialog p").addClass('warning')
			$("#dialogtype").addClass("ui-icon ui-icon-notice");
			break;
		case 'error':
			$("#genericdialog p").addClass('error');
			$("#dialogtype").addClass("ui-icon ui-icon-alert");
			break;
		case 'normal':
		default:
			$("#genericdialog p").addClass('normal');
			$("#dialogtype").addClass("ui-icon ui-icon-info");
			break;
	}
	var m = (msg || "").replace(/\n/g,"<br>");
	$("#dialogmessage").html(m);
	if(editable)		$("#freetext").show();		
	else		$("#freetext").hide();
		
	var dlg = $("#genericdialog").dialog({
		modal:true,
		title:title,
		width:"40em",
		buttons:buttons});	
}

/**汎用エラーダイアログ*/
function error(msg, xhr){

	/*
	var m = msg;
	if(typeof(msg) == "object"){
		m = msg.message || msg.responseText;
		//m.cause;
	}
	if(xhr){
		m += "\n"+(xhr.statusText || "") + "\n" + (xhr.responseText||"")
	}
	*/
	var m = msg + "\n" + xhrToString(xhr);
	dialog(m,"ok",  "error","エラー");
}
/**汎用メッセージボックス*/
function info(msg){
	var m = msg;
	dialog(m,"ok", "情報");
}

/**オンラインかつフェーズ進行中になったときの処理*/
function onOnline(){
	$('.live').removeClass('offline').addClass('online');
}
/**オフラインまたはフェーズ未開始になったときの処理*/
function onOffline(){
	$('.live').removeClass('online').addClass('offline');
}
/**マウスオーバーするとポップアップするメッセージを要素に割り当てる
 * @param {string} selector  ポップアップメッセージの表示位置の基点となる要素のjqueryセレクタを指定する。
 * @param {string} message メッセージ
 * */
function balloon(selector, text){
	$(selector).on('mouseout',  function(e){
		$('#balloon').hide();
	});
	$(selector).on('mouseover', function(e){
		$('#balloon').text(text);
		var pos = e.target.getBoundingClientRect();
		var top = pos.y-40;if(top<0)top = 0;
		var left = pos.x-100;if(left<0)left=0;
 		$('#balloon').css('left',left).css('top',top);
		$('#balloon').show();	
	});
}
function adjustTimestamp(){
	_.forEach($("[data-timestamp]"), function(e){
		var tm = $(e).attr("data-timestamp");
		$(e).text(new Date(Date.parse(tm)).toLocaleString());
	});
};


/** */
function onCardClicked(ev){
	var e = $(ev.target).closest('.card');
	var container = e.closest(".subpanel");
	var multi = container.attr("data-multiselect");
	var siblings = e.siblings('.card');
	if(e.hasClass('selected')){
		e.removeClass('selected');
	}else{
		if(!multi){
			siblings.removeClass('selected');
		}
		e.addClass('selected');		
	}
	FrICORE.trace("card clicked:" + e);
	//validateAction();
	return false;
}

/**ファシリテータ画面のシナリオ概要(アクション、ステート、コンタクト、リプライ)の表示を更新する
 * @param {object} resp 各シナリオデータ(jsonオブジェクト)
 * */
function renderCards(resp){
	if(resp.hasOwnProperty("actions")){
		if(resp.actions)
			window.FRICORE.actions = resp.actions;
		resp.actions.forEach(function(c){
			if(c.hidden)return;
			var t = _.template("<a data-cardtype = 'action' href = '#' class='card clickable'><%=name%></a>", c);
			$("#actions .panelcontent").append($(t).on('click', function(e){
				onCardClicked(e);
			}));
		});
		FrICORE.trace("fetched:actions");
	}
	if(resp.hasOwnProperty("contacts")){
		if(resp.contacts)
			window.FRICORE.contacts = resp.contacts;
		resp.contacts.forEach(function(c){

			if(c.hidden)return;

			var t = _.template("<a data-cardtype = 'contact'  href = '#' class='card clickable'><%=name%></a>", c);
			$("#contacts .panelcontent").append($(t).on('click', function(e){
				onCardClicked(e);
			}));
		});
		FrICORE.info("fetched:contacts");
		
	}
	if(resp.hasOwnProperty("queries")){
		resp.queries.forEach(function(c){
			if(c.hidden)return;

			var t = _.template("<a   data-cardtype = 'query' href = '#' class='card clickable'><%=name%></a>", c);
			$("#queries .panelcontent").append($(t).on('click', function(e){
				onCardClicked(e);
			}));
		});

		FrICORE.info("fetched:queries");
	}
	if(resp.hasOwnProperty("contexts")){
		resp.contexts.forEach(function(c){
			if(c.hidden)return;

			var t = _.template("<span  data-cardtype = 'context'><%=desc%></span><br>", c);
			$("#contexts .panelcontent").append($(t));
		});
		FrICORE.info("fetched:contexts");
	}
	if(resp.hasOwnProperty("informations")){
		var ul = $("<ul/>");
		resp.informations.forEach(function(c){
			if(c.hidden)return;
			var t = _.template($('#statecard-template').html(), c);
			ul.append($(t).on('click', function(e){
				onCardClicked(e);
			}));
		});
		$("#informations .panelcontent").append(ul);
		FrICORE.info("fetched:informations");
		
		adjustTimestamp();
	}
}

/**静的なファイルをGETでロード
 * 
 */
function getDef(url, data, cb){

	var dfd = $.Deferred();
	var ret = null;
	$.ajax({url:url, data:(data || {}),  scriptCharset: 'utf-8', dataType: 'json'})
	.done(function(resp, msg, xhr){
		FrICORE.info(url + ":" + msg);if(cb)cb(resp);
		renderCards(resp);
	}).fail(function(xhr, msg){
		FrICORE.error("シナリオファイルのロードに失敗しました。", xhr);	
	}).always(function(){
		dfd.resolve();
	});
	return dfd.promise();
}




///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//  シナリオセット管理
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/**シナリオセットをロード*/
function loadCards(){
	var defs = {};
	reloadScenarioSet().done(function(){
		var datadir = "workflow/scenario";
	});
}
/**シナリオセットの選択が変更されたときの処理*/
function onScenarioChanged(){
	var selected = $("select.scenarioset").val();
	var buff=[""];
	var set = window.FRICORE.scenario.hasOwnProperty(selected) ? window.FRICORE.scenario[selected] : {};
	//やることがなかった
}
/**シナリオセットの詳細を表示*/
function showScenarioSet(){
	var selected = $("select.scenarioset").val();
	if(!selected)return;
	
	var sset = window.FRICORE.scenario.hasOwnProperty(selected) ? window.FRICORE.scenario[selected] : {};
	
	var o = {id:selected, name:selected,description:"",author:"",copyright:"",version:"",created:"",modified:""};
	var h = _.template($('#scenarioset-template').html(), Object.assign(o, sset));
	
	_.each(sset.phases, function(p){
		var o = {phase:"",name:"",description:"",author:"",copyright:"",version:"",created:"",modified:"",timelimit:-1};
		var sdesc = _.template("#phase-template",Object.assign(o,p));
		$(h).children("ul.phases").append($(sdesc));
	});
	$("#dialogcontainer .dialogcontent").empty().append(h);
	$("#dialogcontainer").dialog({modal:true,title:(sset.id || selected),
		buttons:{
			"検証":function(){validateScenarioSet(o.id);},
			"閉じる":function(){$(this).dialog('close');},
			
		}});

	//dialog(buff.join("<br>\n"), "ok","シナリオセット");
}
function validateScenarioSet(name){
	var n = name == "既定" ? "_default_" : name;
	var url = "workflow/diag/scenario/"+n  +"/validate";
	doRequest(url, "GET",{})
	.done(function(resp, msg, xhr){
		var r = resp;
		var buff  = [];
		_.each(resp, function(e){
			var v = _.template($("#validate-scenario-content-template").html(), e);
			buff.push(v);
		});
		
		
		var content = $($("#validate-scenario-template").html()).children	("ul").append(buff.join("\n"));
		if(_.find(resp, function(e){return !e.valid;})){
			content.prepend("<span class='error'>シナリオデータにエラーがあります。</span>");
		}
		$("#dialogcontainer .dialogcontent").empty().append(content);
	})
	.fail(function(msg, xhr){
		error("シナリオセットの検証に失敗しました。", xhr);
	});
}
/**選択されているシナリオセットの識別子を返す。選択されていなければ_default_を返す*/
function getScenarioDir(){
	var selected = $("select.senarioset").val();
	if(!selected)
		"_default_";
}
/**アクティブなシナリオセットの名前(nameプロパティではなくディレクトリ名)を返す*/
function getActiveScenarioName(){
	if(!window.FRICORE.scenario)return "_default_";
	var active  = getActiveScenario();
	
	var activeName = _.find(_.keys(window.FRICORE.scenario), 
			function(e){return active == window.FRICORE.scenario[e] });
	
	if(!active || !activeName) return "_default_";
	return activeName;
}
/**アクティブなシナリオセットを返す*/
function getActiveScenario(){
	var active  = _.findWhere(window.FRICORE.scenario,{active:true});
	return active;
}
/**選択されたシナリオセットを有効化*/
function activateScenarioSet(){
	var selected = $("select.scenarioset").val();
	var active = getActiveScenarioName();
	if(selected != active){
		if(!confirm("シナリオセットを変更すると、進行中のフェーズが破棄され、ログイン中のユーザは切断されます。\n続行しますか?"))return;
	}
	doRequest("workflow/diag/scenario","POST", JSON.stringify({name:selected}), {contentType:"application/json"})
		.done(function(resp){
			reloadScenarioSet().done(function(){
				alert("シナリオセットを変更しました。ページを再読み込みしてください");
				getProcess();
			});

	}).fail(function(xhr){
		error("シナリオセットの有効化に失敗しました。" + xhrToString(xhr));
	});
	
}
/**シナリオセットの一覧を更新*/
function reloadScenarioSet(){
	return doRequest("workflow/diag/scenario/")
	.done(function(resp){
		window.FRICORE.scenario = resp;
		$('select.scenarioset').empty();
		var keys = _.keys(resp);
		
		_.each(keys, function(k){
			var name = k;
			var set = resp[k];
			var label = k == "_default_" ? "既定" : k;
			var o = {name:"",description:"",author:"",copyright:"",version:"",created:"",modified:"",timelimit:-1};

			//if(set.hasOwnProperty("error")){
			if(!set.hasOwnProperty("validationResult") && set.validationResult){
				var setdesc = label + " : エラー\n" + set.validationResult;
			}else{
				var setdesc = _.template("シナリオセット:<%=name%>\n説明:<%=description%>\n作成者:<%=author%>\n著作権:<%=copyright%>\nバージョン:<%=version %>\n作成:<%=created%>\n更新:<%=modified%>", Object.assign(o,set));
				setdesc +="\n"+ (set.phases ? (set.phases.length+"個のフェーズ") : "フェーズなし") ;
			}

			var opt =$('<option/>').text(label).val(k).attr('title',setdesc).appendTo($('select.scenarioset'));
		});
		
		window.FRICORE.setting = getActiveScenario();
		var active = getActiveScenarioName();
		var scenario = _.find($(".scenarioset option"),function(e){return e.value==active;});
		$("select.scenarioset option").removeClass("scenario-active");
		if(scenario)	$(scenario).addClass("scenario-active");
		$("select.scenarioset").val(active);
		$('select.scenarioset').trigger("change");

		window.FRICORE.activeScenario = active;
		makePhaseChooser($('.assign-phase-container-all'), "all");

		var datadir = "workflow/scenario";

		$.when(
			getDef(datadir  +"/actions.json"), getDef(datadir + "/contacts.json"))
		.done(function(){
			adjustGroupList();
			adjustUserList();
			FrICORE.info("done");
		});
		
	}).fail(function (xhr,msg){
		error(msg, xhrToString(xhr));
	});

}
/**シナリオセットをアップロードする
 * @param {object} evt input type=fileで選択されたFileオブジェクト*/
function uploadScenarioSet(evt){
 	var file = evt.files[0];
	var reader = new FileReader();
	reader.onload = function(evt) {
		var d = evt.target.result;
		if(!d){
			error("アップロードするファイルが指定されていません。");return;
		}
		var name = file.name;
		if(new RegExp(/.+\.zip$/).test()){
			var url = "workflow/diag/scenario/" + name;
			if(!confirm("シナリオデータをアップロードします。" + name))return;
		}else{
			var url = "workflow/diag/scenario/" + FRICORE.activeScenario + "/"+ name;
			if(!confirm("シナリオセットをアップロードします。" + name))return;
		}
		doRequest(url, "PUT", d,  {contentType: "application/octet-stream", processData: false})
			.done(function(resp, msg, xhr){
				info("シナリオセットまたはシナリオデータをアップロードしました。");
				reloadScenarioSet();
				FrICORE.info("upload completed.file:" + name);
			})
			.fail(function(xhr){
				FrICORE.error("failed to upload.", xhr);
				error("アップロードに失敗しました。"+ xhr.responseText);
			});
	};
	if(reader){
		reader.readAsArrayBuffer(file);
	}
}
/**選択されたシナリオセットをダウンロードする*/
function downloadScenarioSet(){
	var name = $("select.scenarioset").val();
	var url = "workflow/diag/scenario/" + name;
	//
	window.open(url);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//振り返り画面
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/**振り返り画面のSVG要素のサイズを内容に合わせる(無駄な余白を出力させないため)*/
function fitSVG(){
	var xs = _.min($("svg").children("g,text"), function(e){return e.getBBox().x;}).getBBox()
	var xm = _.max($("svg").children("g,text"), function(e){return e.getBBox().x+e.getBBox().width;}).getBBox()
	var ys = _.min($("svg").children("g,text"), function(e){return e.getBBox().y}).getBBox()
	var ym = _.max($("svg").children("g,text"), function(e){return e.getBBox().y + e.getBBox().height;}).getBBox()

	var height = ym.y + ym.height  - ys.y;
	var width = xm.x + xm.width  - xs.x;
	var scale  = $('#mermaidChart0')[0].currentScale;
	$('svg').height(height * scale);
}
/**振り返り画面:シーケンス図を描画する
 * @param {string} v mermaid.jsのマークダウンデータ*/
function render(v){
	mermaid.sequenceConfig = {
		    diagramMarginX:20,
		    diagramMarginY:5,
		    boxTextMargin:5,
		    noteMargin:5,
		    messageMargin:5,
		    mirrorActors:false,
		    arrowMarkerAbsolute: true
		    
		    };
	$("#mermaidChart0").remove();
	$("#svgcontainer").empty();
	mermaid.initialize({startOnLoad:false, htmlLabels:false,logLevel:1,});
	if(!v){
		v = $("#chartdef").val();
	}else{
		$("#chartdef").val(v);
	}
	mermaid.render("mermaidChart0", v, function(svg){
		$("#svgcontainer").append(svg);
		fitSVG();
		//イベントハンドラーを登録する
		$("g").on("contextmenu",setContextMenu2);
	});

};
/**ファシリテータ画面:イベント履歴の表示を更新する(サーバからデータをリロードする)
 * @param {string} team チームID
 * */
function renderHistory(team){
	var flag = $("#svgcontainer").is(":visible")
	showWFHistory(team, flag);
}
/**SVGの text要素を折り返す。
 * @param {string} elem 対象とするtext要素のセレクタ
 * @param {string} eol 折り返し位置を示す区切り文字列。
 * */
function foldDiagramText(elem, eol){
	var splitter = eol ? eol : "<br/>";
	var text = $(elem).text();
	if(text.indexOf(splitter) == -1) return elem;

	var rect = elem.getBBox();
	var height = rect.height;

	var arr = text.split(splitter);
	var buff = [];
	var lines = arr.length;

	_.each(arr, function(line, index){
		var org = $(elem);
		var d = {x:org.attr('x'),y:Number(org.attr('y'))+height*index,style:org.attr('style'),"classname":org.attr('class'),text:line};
		d.exstyle=index==0?"":"font-size:small;";
		var l = _.template("<tspan class='<%=classname%>' style='<%=style%> <%exstyle%>' x='<%=x%>' y='<%=y%>'><%=text%></tspan>", d);
		buff.push(l);
	});
	$(elem).text("");
	$(elem).html(buff.join(''));
	
}
/**SVG要素中の.messageTextクラスをもつ要素に対して文字列の折り返しを適用する。*/
function formatSVG(){
	_.each($('svg>>.messageText'), function(text){
		foldDiagramText(text)
	});
	
}

/**振り返り画面: SVGのviewBoxのサイズを拡張する
 * @param {Rect} rect x,y,width、heightで移動量と拡張サイズを指定する
 * */
function expandViewBox(rect){
	var vbox = $('#mermaidChart0')[0].viewBox;
	vbox.baseVal.x += rect.x;
	vbox.baseVal.y += rect.y;
	vbox.baseVal.width += rect.width;
	vbox.baseVal.height += rect.height;
	$('#mermaidChart0')[0].viewBox.baseVal = vbox;
}
/**シーケンス図を描画する。
 * @param{array} data
 * showWFHistoryから呼ばれる*/
function drawDiagram(arr, data){

	showFilter();
	var header ="";
	var phase = FRICORE.workflowstate ? FRICORE.workflowstate.phase : '未開始';
	var team = $("teampicker").val() || FRICORE.usersession.team;
	var score = FRICORE.workflowstate ? FRICORE.workflowstate.score : '';
	var hideInactive = $('#hide-inactive').prop('checked');
	var pt = ["participant システム"];

	FRICORE.sequence = {};
	FRICORE.sequence.team = team;
	FRICORE.sequence.roles = pt;
	FRICORE.sequence.events = data;
	
		var roles = {};
		if(!FRICORE.setting["roles-order"]){
			roles = FRICORE.members;
		}else{
			roles = _.sortBy(FRICORE.setting["roles-order"], "order");
		}
		
		_.each(_.sortBy(roles, "order"), function(e){
			
			var role=_.find(FRICORE.members, function(ee){
				return ee.role == e.role;
			});
			if(role){
				//フィルタで無効化されていたらスキップ
				var hidden = !validateRoleFilter(role.role);
				//やりとりがなかったらスキップ。
				var exists=_.find(arr, function(e){
					var tmp = e.split(/:/, 1)[0].split(/[/\=\->]+/);
					if(tmp.length == 2 && (role.rolename == tmp[0] || role.rolename == tmp[1]))
						return true;
					else
						return false;
				});
		
				if(!hidden && (exists || !hideInactive)){
					pt.push("participant " + escape4vga(role.rolename));
				}else{
					FrICORE.trace("participant " + role.rolename +" skipped.");
				}
			}else
				FrICORE.info("warn: role not found: " + e);
		});
		
		header = "sequenceDiagram\n" + pt.join("\n");

		var s = arr;
		var str = s.join("\n");
	
		if(pt && pt.length >= 2){
			str =str.replace(/<PH_FROM>/g,pt[0].replace("participant ", "")).replace(/<PH_TO>/g,pt[1].replace("participant ", ""));
		}else
			str =str.replace(/<PH_FROM>/g,pt[0].replace("participant ", "")).replace(/<PH_TO>/g,pt[0].replace("participant ", ""));
			
		render(header+"\n"+str);

	var title = {team:team, phase:phase, date:formatDate(new Date()), score:score};
	$('#svgcontainer').prepend(_.template('<div class="diagram-header" ><%=team%> フェーズ:<%=phase%> (<%=date%>) スコア:<%=score%></div>', title));

	$('#mermaidChart0')[0].setAttribute("preserveAspectRatio","xMidYMin")

	formatSVG();

	window.dragger.init('#mermaidChart0');
	$('#svgctrl').draggable();	
	$('svg')[0].addEventListener('touchstart', function(e){
		FrICORE.trace("touch start");
	});
	$('svg')[0].addEventListener('touchmove', function(e){
		FrICORE.trace("touch move");
	});
	$('svg')[0].addEventListener('touchend', function(e){
		FrICORE.trace("touch end");
	});
}

/**振り返り画面: ドラッグによる領域選択を制御する*/
var Dragger = function(){};
Dragger.prototype.init = function(selector){
	this.target = $(selector);
	
	this.target.on('mousedown', function(e){
		svgmouseevent(e, 'mousedown');

		this.start(e);
		return false;
	}.bind(this));
	this.target.on('mouseup', function(e){
		svgmouseevent(e, 'mouseup');
		this.end(e);
		return false;
	}.bind(this));
	this.target.on('mousemove', function(e){
		window.getSelection().removeAllRanges();
		if(e.buttons == 0)	return;

		svgmouseevent(e, 'mousemove');
		if(!this.dragTarget)return false;
		
		var currentOffset = {x:e.offsetX, y:e.offsetY};
		var currentScreen = {x:e.screenX, y:e.screenY};
		var pos = tosvg	({x:currentOffset.x, y:currentOffset.y});
		
		FrICORE.trace("("+ currentOffset.x  + "," + currentOffset.y + ")-(" +  pos.x + "," + pos.y + ")");
		
		
		if($('#range-selection')){
			$('#range-selection').remove();
		}
		
		var box = $(this.target)[0].viewBox.animVal;
		var ratio = box.width/$(this.target).width();
		
		var param = {id:'range-selection', 'stroke-width':2, stroke:'blue', fill:'lightblue', style:'fill-opacity:0.3'};
 		param.x=(this.startOffset.x)*ratio+box.x  ;param.y = 0-ratio*box.y;
		param.width = (currentOffset.x - this.startOffset.x)*ratio;
		param.y = 0;
		param.height = $(this.target)[0].getBBox().height;
		if(param.width < 0){
			param.width = Math.abs(param.width);
			param.x -= param.width;
		}
		
		var scale = $(this.target)[0].currentScale;
		var trans = $(this.target)[0].currentTranslate;
		param.x /= scale;
		param.x -= trans.x;
		var box = createSVG('rect', param);
		$(this.target).prepend(box);
		return false;
	}.bind(this));
	this.target.on('keypress', function(e){
		return false;
	}.bind(this));
	this.target.on('click', function(e){
		window.getSelection().removeAllRanges();
 		svgmouseevent(e, 'mouseclick');
 	}.bind(this));
	this.target.on('contextmenu', function(e){
		window.getSelection().removeAllRanges();
 		svgmouseevent(e, 'contextmenu');
	}.bind(this));
};
/**ドラッグ開始時に呼ばれる*/
Dragger.prototype.start = function(e){
	this.startOffset = {x:e.offsetX, y:e.offsetY};
	this.startScreen = {x:e.screenX, y:e.screenY};
	this.dragTarget = e.target;		
	this.startEvent = e;
	
	if($('#range-selection')){
		$('#range-selection').remove();
	}
	FrICORE.trace("drag start");
};
/**ドラッグ終了時に呼ばれる*/
Dragger.prototype.end = function(e){
	this.endOffset = {x:e.offsetX, y:e.offsetY};
	this.endScreen = {x:e.screenX, y:e.screenY};
	this.dragTarget = null;
	this.endEvent = e;
	
	FrICORE.trace("drag stop");
};
window.dragger = new Dragger('#mermaidChart0');
/**SVG要素のマウスイベントハンドラ*/
function svgmouseevent(e, msg){
	var svg=tosvg({x:e.offsetX, y:e.offsetY});
	var containerpos=$('#mermaidChart0').position();
	FrICORE.trace((msg||'') + '(' + e.buttons + '): ' + e.target.tagName + ', evt:('+e.offsetX + ","+e.offsetY + "), evt(svg):("+
			Math.round(svg.x) + ","+Math.round(svg.y) + "), svgcontainer:(" + 
			Math.round(containerpos.left) + ","+ Math.round(containerpos.top) + ")");
}
/**
 *SVG要素を生成する
 *注:jqueryではSVG要素は作成できない。*/
function createSVG(name, attrs){
	var e=document.createElementNS('http://www.w3.org/2000/svg', name);
    for (var k in attrs)
        e.setAttribute(k, attrs[k]);
	if(!attrs.id){
		var rand = Math.floor(Math.random() * 0xffffffff);
		e.setAttribute('id', 'svgelement_'+ String(rand));
	}
    return e;
}
/**
 * SVG要素を追加する。
 * @param {string} selector 追加先要素のセレクタ
 * @param {string} name 要素名
 * @param {Object} attrs 属性マップ
 * */
function appendSVG(selector, name, attrs){
	_.each($(selector), function(e){
		e.append(createSVG(name, attrs));
	});
}
/**動的コンテンツの変更後に呼び出す*/
function updateui(){
	/**実験的機能*/
	balloon(".experimental",'実験的機能です。このバージョンでは正しく動作しない可能性があります。');
	/**フェーズ中のみ利用可能なUI部品*/
	balloon(".live.offline", 'この機能は演習フェーズ中のみ使用できます。');
	
	$(".feature").hide();
	_.each(FRICORE.setting.features, e=>{
		var str = ".feature-"+e;
		$(str).show();
	});
	
	
	
}

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//		初期化処理
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
$(function(){
	showProductInfo();
	
	FRICORE.loadConfig();
	
	
	//モード設定
	var params = parseURL();
	FRICORE.isadmin = params['isadmin'] ? true : false;
	FRICORE.debug = params['debug'] ? true : false;
		this.MAX_LOG_LINES = 5;

	FRICORE.params = params;
	
	FrICORE.info("start. admin="+(FRICORE.isadmin ? "true" : "false") + ",debug:" + (FRICORE.debug ? "true":"false"));
	
	if(!FRICORE.debug)	$('.debugmenu').hide();
	else	$('.debugmenu').show();
	
	//UI部品初期化
	$('.picker').button({icons:{primary:'ui-icon-circle-plus'}});
	$('select').select2({
	  placeholder: '選択してください。',
	  containerCssClass:'select2-custom'
	});
	$('.set-state select').select2({
		  placeholder: '選択してください。',
		  templateResult:formatStateSelection
		});
	$('.set-to select,.set-cc select').select2({
		  placeholder: '選択してください。',
		  templateResult:formatUserSelection
		});
	$('select.role,user-picker').select2({
		  placeholder: '選択してください。',
		  templateResult:formatUserSelection
		});
	$('.set-action select').select2({
		  placeholder: '選択してください。',
		  templateResult:formatActionSelection
		});

	$('.tabs').tabs({
			heightStyle:'fill',
			activate:function(evt, ui){
			ui.newPanel.height($('#wfview').height() - ui.newPanel.offset().top);
		}
	});
	
	$('.accordion').accordion({
		heightStyle:'content',
		collapsible:true,
		active:false,
		activate:function(evt, ui){
			var panel = $('#facilitator-main .ui-tabs-panel:visible');
			var content = $('#wfstatus .ui-accordion-content:visible');
			$('#wfstatus .ui-accordion').height($('#wfstatus').innerHeight() - 4);

		}
	});
	
	$('.menu').menu();
	$( "button" ).button();
	var tables=$(".tablesorter");
	tables.tablesorter(
		{
			headers:{0:{sorder:'digit'},1:{sorder:'text'},2:{sorder:'text'},3:{sorder:'text'},4:{sorder:'text'},5:{sorder:'text'},},
			headerTemplate : '{content} {icon}',
			widgets: ['zebra', 'stickyHeaders','scroller'],
			widgetOptions: {
				stickyHeaders_attachTo: '.wrapper',
				stickyHeaders_offset: 0,
			}});
	
	$('#wfhistory .tablesorter').tablesorter(
			{
				widgets: ['zebra', 'stickyHeaders','scroller'],
				sortList:[[1,1]],
				widgetOptions: {
					stickyHeaders_attachTo: '.wrapper',
					stickyHeaders_offset: 0,
				}});
	$('#event-list .tablesorter').tablesorter(
			{
				widgets: ['zebra', 'stickyHeaders','scroller'],
				sortList:[[1,1]],
				widgetOptions: {
					stickyHeaders_attachTo: '.wrapper',
					stickyHeaders_offset: 0,
				}});

	
	setContextMenu($('#event-list'), true);

	//演習コンテンツ初期化
	preinit();
	$("#history").hide();	
	if(FRICORE.isadmin){
		$("select.role").val("facilitator").trigger("change");
		wfview();
	}else{
		$("select.role").trigger("change");
		dashboard();
	}
	$(window).on('resize', function () {
		var mainform = '#event-list';
		if($('#wfview').is(':visible')){
			mainform = '#wfview';
		}else if($('#history').is(':visible')){
			mainform = '#history';
		}
		var validarea= window.innerHeight-$(mainform).offset().top; - $('#header').height() - $('#product-info').outerHeight() ;
		$(mainform).height(validarea);

		$('#facilitator-main').height(validarea- $('#facilitator-main').offset().top);

		if($('#wfstatus').length != 0)
			$('#wfstatus').height($('#facilitator-main').height() - $('#wfstatus').position().top - 7);
		
		var rects = {'window':$(window).height(),'#wfview':$('#wfview').height(), '#facilitator-main':$('#facilitator-main').height(),
				'.ui-accordion':$('#facilitator-main .ui-accordion').height(),};

		var panel = $('#facilitator-main .ui-tabs-panel:visible');
		if(panel.length != 0){
			panel.trigger('update');
		}
	});
	
	$(window).trigger('resize');
	window.timer.addHandler(updateWorkflowInfoCallback, 20000);
	window.timer.addHandler(updateClockCallback, 1000);
	window.timer.start();

	updateui();
});


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/**プルダウンリスト項目の書式化(ユーザ)*/
function formatUserSelection(el){
	if(!el.id)return el.text;
	var user = _.find(FRICORE.contacts,function(e){return e.rolename==el.id});
	if(user && user.hasOwnProperty("icon")){
		try{
		var v = _.template($("#select2-user-template").html(), user);
		return $(v);
		}catch(t){
			FrICORE.error(t);
			return el.text;
		}
	}
	return el.text;
}
/**プルダウンリスト項目の書式化(アクション)*/
function formatActionSelection(el){
	if(!el.id)return el.text;
	var action =_.find(FRICORE.actions,function(e){return e.id==el.id});
	if(action && action.hasOwnProperty("icon")){
		try{
			var v = _.template($("#select2-action-template").html(), action);
			if(action.type=="talk"||action.type=="share")	{		
			return $(v);
			}
			if(action.id=="i0009"){
				return $(v);
				
			}if(!FRICORE.count[action.id]){
				return $(v).css("color","green");
			}else if(FRICORE.count[action.id]==1){
				return $(v).css("color","black");
			}else{ 
				return $(v).css("color","orange");
			}
		}catch(t){
			FrICORE.error(t);
			return el.text;
		}
	}
	return el.text;
}
/**プルダウンリスト項目の書式化(ステート)*/
function formatStateSelection(el){
	if(!el.id)return el.text;
	var state = _.find(FRICORE.states,function(e){return e.id==el.id});
	if(state && state.hasOwnProperty("icon")){
		try{
			var v = _.template($("#select2-state-template").html(), state);
			return $(v);
		}catch(t){
			FrICORE.error(t);
			return el.text;
		}
	}
	return el.text;
}
function surviveMark(tgt){
	toggleMark(this);
}
/**イベント一覧; コンテキストメニュー生成*/
function setContextMenu(tgt){
	tgt.contextMenu({
		selector:'tr',
		items:{
			'show':{name:'表示'},
			'reply':{name:'返信/転送'},
			'mark':{name:'マーク設定/解除'},
		},
		callback:function(key, options, evt){
			switch(key){
			case 'show':
				showRow(this);
				break;
			case 'reply':
				showActionDialog('reply', this);
				break;
			case 'mark':
				toggleMark(this);
				break;
			}
		}
	});
}
function setContextMenu2(a){
	var b=a.currentTarget;
	$(b).contextMenu({
		selector:'rect,text',
		items:{
			'mark':{name:'マーク設定/解除'},
		},
		callback:function(key, options, evt){
			switch(key){
			case 'mark':
				toggleMark(this);
				break;
			}
		}
	});
	return false;
}
/**イベント一覧; マークのON/OFFを制御*/
function toggleMark(el){
	if(el.hasClass('marked')){//マークが解除されたらイベントIDを削除
		el.removeClass('marked');
		var ev1 = el.attr("data-eventid");
		//ローカルストレージからイベントIDの配列の文字列を取り出す
		var ka1 = window.localStorage.getItem("kagi");
		//文字列を配列オブジェクトに変換する
		var gi1 = JSON.parse(ka1);
		//配列がnullだったら空の配列を用意
		if(!gi1){
			 gi1=[];
		}
		//配列からイベントIDを探す
		var idx1 = gi1.indexOf(ev1);
		if(idx1 == -1){
					
		}else{
			gi1.splice(idx1,1);
			
		}
		//見つからなければ配列に追加する
		//配列をローカルストレージに書き込む
		var ke1 =JSON.stringify(gi1);
		window.localStorage.setItem("kagi",ke1);	
	}else{
		el.addClass('marked');//マークしたらイベントIDを保存
		var ev2 = el.attr("data-eventid");
		//ローカルストレージからイベントIDの配列の文字列を取り出す
		var ka2 = window.localStorage.getItem("kagi");
		//文字列を配列オブジェクトに変換する
		var gi2 = JSON.parse(ka2);
		
		//配列がnullだったらからの配列を用意
		if(!gi2){
			gi2=[];
		}
		//配列からイベントIDを探す
		var idx2 = gi2.indexOf(ev2);
		if(idx2 == -1){
			gi2.push(ev2);
		}else{
			
		}
		//見つからなければ配列に追加する
		//配列をローカルストレージに書き込む
		var ke2 =JSON.stringify(gi2);
		window.localStorage.setItem("kagi",ke2);
		//見つかったら何もしない
		el.children("td").first().append('<span style="color:red"></span>');;
	}
}

/**定期的にワークフロー状態を更新*/
function updateWorkflowInfoCallback(){
	updateWorkflowInfo();
	updateLoginInfo();
};
/**
 * 秒数を日:時:分:秒に書式化
 * */
function formatSeconds(sec){

	if(!sec)	return "";
	var min = Math.floor(sec/60);
	var second = Math.floor(sec % 60);
	var hour = Math.floor(min/60);

	var d = {second:second,min:min%60,hour:hour};
	var buff = [];
	if(d.hour){buff.push(("00"+d.hour).slice(-2));}
	buff.push(("00"+d.min).slice(-2));
	buff.push(("00"+d.second).slice(-2));
	return buff.join(":");
}


/**
一定間隔で画面を更新する処理。時刻情報の更新、サーバとの情報同期など
*/
function updateClockCallback(){
		if(FRICORE.workflowstate && FRICORE.workflowstate.start > 0){
			var txt = summerizeWorkflowState();
			$('#event-list span.wfstatusinfo').text(txt);
		}

		if($("#facilitator-main").is(":visible")){
			var txt = summerizeWorkflowState(true);
			$(".flowstatus .message").text(txt);
		}

		if(!FRICORE.wfprocesses)return;

		_.each(FRICORE.wfprocesses, function(proc){
			if(proc.start>0){
				var elapsed = (new Date().getTime() - proc.start)/1000;
				_.find($('#processlist tr'), function(p){
					var cur = $(p).find('td.assign-team:contains("'+proc.team+'")');
					if(cur.length != 0){

						//TODO: 仮想時刻の考慮: コードが汚い。要リファクタリング
						try{
							if(isFeatureEnabled("virtualtime")){
								elapsed = (new Date(proc.world.clock.time).getTime() 
									- new Date(proc.world.clock.baseTime).getTime())/1000;
							}
						}catch(t){
							FrICORE.error(t);
						}
						var txt = Math.floor(elapsed / 60) + ":" + Math.floor(elapsed % 60);
						if(isFeatureEnabled("virtualtime") && proc.world.paused)
							txt += "(一時停止中)";
							 
						
						//$(p).find('td.elapsed').text(txt );
						$(p).find('.elapsed').text(txt);
					}
				})
			}
		});
}
/**定期的にワークフロー状態を更新*/
function updateWorkflowInfo(){
	if(!FRICORE.usersession){
		popupWarning("ログインしていません。");
		onOffline();
		return;
	}
	
	if(FRICORE.disableAutoRefresh)	return;//for debug
	
	var url = "workflow/diag/"+FRICORE.usersession.team + "/workflow";
	if(!FRICORE.wfsession){onOffline();error("ログインしていません。");return;}
	doRequest(url, "GET", {})
	.done(function(resp, msg, xhr){
		FRICORE.workflowstate = resp;
		onOnline();
	})
	.fail(function(xhr){
	});
	
	refreshSystemView();
	
	if($('#facilitator-main').is(":visible") && FRICORE.isadmin && FRICORE.usersession){
		//if($('#processlist-autoupdate:checked').length != 0){
		if(!FRICORE.disabAutoRefresh){
			getProcess({},true);
		}
		//}
		if($('#wf-autoupdate:checked').length != 0 && !FRICORE.disabAutoRefresh){
				updateWF();
		}
	}
}
/**
 * 通信エラーダイアログを表示
 *   silent: ダイアログを表示しない*/
function onError(xhr, silent){
	var str = xhr.responseText;
	if(xhr.responseJSON){
		str = xhr.responseJSON.message;
	}
	if(!silent)	error(str);
	else popupWarning(str);
	return str;
}
function showProductInfo(){
	doRequest('sys/product.json', 'GET').done(function(resp, msg, xhr){
		window.versioninfo.SERVER_VERSION=resp.SERVER_VERSION || "";
		window.versioninfo.SERVER_DESC=resp.SERVER_DESC || "";
		window.versioninfo.COPYRIGHT=resp.COPYRIGHT || "";
		window.versioninfo.CAPTION=resp.CAPTION || "";
		window.versioninfo.CLIENT_DESC=resp.CLIENT_DESC || "";
		window.versioninfo.CLIENT_VERSION=resp.CLIENT_VERSION || "";
		
		$('#server-version').text(window.versioninfo.SERVER_VERSION);
		$('#server-desc').text(window.versioninfo.SERVER_DESC);
		$('#copyright').text(window.versioninfo.COPYRIGHT);
		
		$('#client-desc').text(window.versioninfo.CLIENT_DESC);
		$('#client-version').text(window.versioninfo.CLIENT_VERSION);
    	$(document).attr('title', window.versioninfo.CAPTION);

	});
}


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// 自動更新処理のためのタイマー
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
var Timer =function(interval){
	this.handlers = [];
	this.interval = interval || 200;
	this.timerid = null;
};
Timer.prototype.addHandler = function(handler, interval){
	this.handlers.push({interval:interval,handler:handler,previous:0});
};
Timer.prototype.start = function(){
	if(this.timerid){window.clearInterval(this.timerid);}
	this.timerid = window.setInterval('processTimerProc()', this.interval);
	FrICORE.trace("timer started.interval=" + this.interval);
};
Timer.prototype.stop = function(){
	if(this.timerid){
		clearInterval(this.timerid);
		this.timerid = null;
	}
	FrICORE.trace("timer stopped.");
};
/**タイマハンドラを実行して前回実行日時を更新*/
function processTimerProc(){
	if(!window.timer) return;
	_.each(window.timer.handlers, function(e){
		if(e.previous + e.interval  <= new Date().getTime()){
			e.handler();
			e.previous = new Date().getTime();
		}
	});
};
var timer = new Timer();

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////


function selectRow(el){

	if(!$(el).parent().attr("multiple")){//単一選択なら
		$(el).parent().find('.listitem-selectable').removeClass('selected');
		$(el).addClass('selected');
	}else{
		if($(el).hasClass('selected'))$(el).removeClass('selected');
		else $(el).addClass('selected');
	}
};

/**members.recipientsプロパティの内容に従って宛先リストを作成*/
function sortMembers(){
	var all = FRICORE.members;
	var r = FRICORE.usersession.role;
	var me =_.find(FRICORE.members, function(e){return e.role==r});
	if(!me || !me.recipients){return all;}
	var ret = [];
	var sorted = _.each(me.recipients, function(role){
		var def = _.find(FRICORE.members, function(ee){
			return ee.role == role;
		});
		if(def)
			ret.push(def);
	});
	return ret;
}
/**新規アクション画面を表示する
*/
function showActionDialog(type, tgt, toaddr){
	if(!FRICORE.usersession){
		dialog("ログインしていません。", "ok", "warning");
		return;
	}

	var target = [];
	var injection = FRICORE.isadmin;
	switch(type){
	case 'talk':
	case 'reply':
	case 'forward':
		target=tgt || $('#event-list').find('.selected');
		break;
	case 'injection':
		injection=true;break;
	default:
	
	}
	if(injection){
		var team = getSelectedTeam();
		if(FRICORE.usersession.team != team){
			if(!confirm("ログイン中のユーザは選択されているチームに所属していません。このインジェクションは" +FRICORE.usersession.team+ "に送信されます。\n続行しますか?"))
				return;
		}
	}

	var to = $('.set-to').find('select');to.empty();to.append('<option selected value=0></option>');
	var cc = $('.set-cc').find('select');cc.empty();cc.append('<option value=0></option>');
	
	refreshPresence();
	var recipients = injection ? FRICORE.presence : sortMembers();
	var phase = FRICORE.workflowstate.phase; 
	if(recipients){
		recipients.forEach(function(el){
			var pres = _.find(FRICORE.presence, function(e){return e.name==el.name;}) || {};

			var isonline = true;//pres.online;
			var issystem = isSystemUser(el, phase);
			if(issystem){
				isonline = true;
			}else
				isonline = pres.online;
			
			el.state = isonline ? "":"(オフライン)";
			//console.log(el.name + "is " + (el.isonline ? 'online' : 'offline'));
			var e = $(_.template("<option  value = '<%=role%>' title='<%=rolename%><%=state%>\n<%=desc||''%>'><%=rolename%></option>", el));
			if(!isonline){e.attr('disabled', true);}else{FrICORE.info(el.name + " is online");}
			if(!FRICORE.debug && (!el.hidden)){
				to.append(e);
				cc.append(e.clone());
			}
		});
	}
	if(injection){
		var e = $("<option  value = 'ALL' title='チーム全員'>チーム全員</option>").appendTo(to);
		$("<option  value = 'system' title='システム'>システム</option>").appendTo(to);
	}
	
	var action = $('.set-action').find('select');action.empty();action.append('<option selected value=0></option>');
	if(type=="injection" && FRICORE.allActions){
		FRICORE.allActions.forEach(function(el){
			var e = _.template("<option title='<%=description%>' value = '<%=id%>'><%=name%></option>", el);
			action.append($(e));
		});
		
	}else{
		if(FRICORE.actions){
			FRICORE.actions.forEach(function(el){
				var e = _.template("<option title='<%=description%>' value = '<%=id%>'><%=name%></option>", el);
	
				var a = $(e);//.attr('data-carddata', JSON.stringify(el));
				if(isActionAvailable(el)){
					if(!el.hidden || FRICORE.debug)
						action.append(a);
					else
						FrICORE.trace("アクションは非表示： " + el.name);
				}else if(FRICORE.debug){
					action.append(a);
				}else if(el.hidden){
	
				}else{
					FrICORE.trace("アクションは利用不可: " + el.name);
				}
			});
		}
	}
	
	$('.set-state').find('select').val([]).trigger('change');
	var statecards = (FRICORE.debug || type=='injection') ? FRICORE.states : FRICORE.availableStates;
	if(statecards){
		var container = $('.set-state').find('select');container.empty();
		_.each(statecards, function(s){
			var isavailable = _.findWhere(statecards, {id:s.id});
			var e = _.template("<option value = '<%=id%>' title='<%=hasOwnProperty('description')?description:''%>'><%=name%></option>", s);
			if(!isavailable){
				if(!FRICORE.debug){
					$(e).prop('disabled', 'disabled');
				}
				var a = $(e);
				container.append(a);
			}else{
				var a = $(e);
				container.append(a);
			}
		});
	}
	
	$(".set-comment>textarea").val("");

	var events = $('#event-list').find('.listitem-selectable');
	var replyto = $('.set-reply-to').find('select');replyto.empty();replyto.append('<option selected value=0></option>');

	for(var i = 0; i < FRICORE.events.length; i ++){
		var data = FRICORE.events[i];
		var msg = compressMessage(data);
		var d = {date:formatDate(new Date(data.sentDate)), from: data.from.rolename, to: data.to.rolename,  
			action:data.action.name, message:msg,id:data.id};
		d.title = _.template("日時:<%=date%>\nFrom:<%=from%>\nTo:<%=to%>\n\n<%=action%>:<%=message%>", d);
		var e = _.template("<option value='<%=id%>' title = '<%=title%>'><%=date%>:<%=from%> > <%=to%>  [<%=action%>] <%=message%>", d);
		replyto.append(e);
	}
	
	action.change(function(e){
		var id = $(e.target).val();
		var actions = FRICORE.isadmin ? FRICORE.allActions : FRICORE.actions;
		var action = _.findWhere(actions, {id:id});
		actionChangeHandler(action);	});

	if(type == 'reply'){
		var selected = target || $('#event-list').find('.listitem-selectable.selected');
		if(selected.length==0){error("返信先が選択されていません。");return;}
		var eventid = selected.data("eventid");
		var data = _.findWhere(FRICORE.events, {id:eventid});
		if(data){
			$('.set-reply-to select').val(data.id)
			//replyChangeHandler(d);
			replyChangeHandler(data);
			var to = data.from.role;
			if(!canSendTo(to)){//system, allなど特殊ユーザは宛先に指定できない
				FrICORE.trace("返信できないユーザが選択されたため、宛先から除外されました。"+to);
			}else{
				$('.set-to select').val(to).trigger("change");
			}
		}
	}
	
	if(toaddr){
		$('.set-to select').val(toaddr).trigger("change");
	}
	
	replyto.change(function(e){
		var id = $(e.target).val();
		var src = _.findWhere(FRICORE.events, {id:id});
		replyChangeHandler(src);	});
	
	

	
	$("#action-dialog").dialog({modal:true,title:"アクション",width:"60em", buttons:{
		"実行": function(){
			if(validateActionInput()){
			$(this).dialog('close');
			requestAction();
			}
		},
		"キャンセル": function(){
			$(this).dialog('close');
		}
	}});
}

/**アクションが実行可能かを評価*/
function isActionAvailable(act){
	if(FRICORE.debug || FRICORE.isadmin)	return true;
	if(FRICORE.workflowstate){
		var phase = FRICORE.workflowstate.phase;
		if(act.phase && act.phase.indexOf(phase) == -1)
			return false;
	}
	
	if(!act.statecondition || act.statecondition.length == 0)	return true;
	var statecards = FRICORE.debug ? FRICORE.states : FRICORE.availableStates;
	var ret = evaluateStates(statecards, act.statecondition, "AND");

	if(ret && act.attachments){
		var buff=[];
		_.each(act.attachments, function(i){
			var cur=_.findWhere(FRICORE.states, {id:i});
			if(cur && buff.indexOf(cur) == -1){
				buff.push(cur);//重複除去
			}
		});
		FRICORE.availableStates = _.union(FRICORE.availableStates, buff);
	}
	return ret;
}

/**ステート条件を演算子とステートの配列に分解
 * @return {condition:array,operator:operator}*/
function parseStateCondition(cond, defaultOperator){
	
	if(cond.indexOf("AND") != -1){
		matched = {condition:cond, operator:"AND"};
	}else if(cond.indexOf("OR") != -1){
		matched = {condition:cond, operator:"OR"};
	}else if(cond.indexOf("NAND") != -1){
		matched = {condition:cond, operator:"NAND"};
	}else if(cond.indexOf("NOR") != -1){
		matched = {condition:cond, operator:"NOR"};
	}else if(cond.indexOf("NOT") != -1){
		matched = {condition:cond, operator:"NOT"};
	}else{
		matched = {condition:cond, operator:defaultOperator};
	}
	return matched;
}
/**配列condからopを探索し、見つかったら{operator:op,condition:condからopを除いた配列}、見つからなかったら{operator:null,condition:cond}}を返す*/
function _parseStateCondition(cond, op){
	if(cond.indexOf(op) == -1){
		return {operator:null, condition:cond};
	}else{
		return {operator:op, condition:_.without(cond, op)};
	}
}
/**いずれかを含む(OR)*/
function hasStates(target, arg){
	for(var i = 0; i < arg.length; i ++){
		if(!_.findWhere(target, {id:arg[i]}))
			return true;
	}
	return false;
}
/**すべてを含む(AND)*/
function hasAllStates(target, arg){
	var ret = true;
	for(var i = 0; i < arg.length; i ++){
		if(!_.findWhere(target, {id:arg[i]}))
			return false;
	}
	return true;
}
function HasState(statecards,arg){
	var statecards = FRICORE.debug ? FRICORE.states : FRICORE.availableStates;
		if(!_.findWhere(statecards, {id:arg}))
			return false;
	return true;
}

/**ステート条件を評価(演算子対応)
 * @param{array} memberStates メンバが所有するステート
 * @param{array} condition ステート条件
 * @param{string} defaultOperation デフォルトの演算
 * */

 
 	//改修１行目
function SplitElm(str0){
		var p0 = str0.indexOf(",");
		var buf = [];
        
        while (p0 != -1) {
            var str1;
            var P0 = [];
            var P1 = [];
            var P2 = [];
            p0= str0.indexOf(",");
            var p1 = str0.indexOf("(");
            var p2 = str0.indexOf(")");

            while( p0 !=-1){
            	P0.push(p0);
            	p0 = str0.indexOf(",",p0+1);
            }
            while( p1 !=-1){
                P1.push(p1);
                p1 = str0.indexOf("(",p1+1);
            }
            while( p2 !=-1){
                P2.push(p2);
                if( p2 != str0.lastIndexOf(")")){
                    p2 = str0.indexOf(")",p2+1);
                }else{
                    p2 = -1;
                }

            }

            
            //【】対応エラー
            if (P1.length != P2.length ) {
            	System.out.println("カッコ対応エラー：条件式が不正です。 ");
            };

            
            //【】が含まれる
            if (P1.length !=0){
                //【】の方がコンマより前にある
                if (P0[0] > P1[0]){
                	var count = 0 ;
	                var a = 0 ;
	                var b = 0 ;
	                while(count != -1){
	                	if(P1[a] < P2[b]){
	                		if(P1.length > a+1){
	                			a = a+1 ;
	                			count = count +1 ;
	                		}else{
	                			b = a ;
	                			count = -1;
	                			}
	                		}
	                	else if(P1[a] > P2[b]){
	                		if(count != 0){
	                			b = b + count -1;
	                			count = 0;
	                		}else{
	                			count = -1;
	                			}
	                		}
	                	}
	                str1 = str0.substring(0,P2[b]+1);
	                buf.push(str1);
	                if( str0.length() != P2[b]+1){
	                	str0 = str0.substring(P2[b]+1+1);
	                }else{
	                	str0 = null;
	                }

                //コンマが【】より前にある時、str0のコンマまでの部分をbufに追加
                }else{
                    str1 = str0.substring(0, P0[0]);
                    str0 = str0.substring(P0[0]+1);
                    buf.push(str1);                    
                }
            //コンマはあるけど【】がないとき、
            }else{
                str1 = str0.substring(0, P0[0]);
                str0 = str0.substring(P0[0]+1);
                buf.push(str1);

            }
            
            //whileの処理の一番最後にp0の値を更新            
            if (str0 != null){
                p0 = str0.indexOf(",");
            }else{
                p0 = -1;
            }
        }
        
        //while文を抜けた後(p0=-1になった時)
        if(str0 != null){
            buf.push(str0);
        }
        buf.push(str0);
        return buf;
}
//終わり
	
function EvalEQ(memberStates, state){
		var buf=[];
		var logic;
		var ret,elm;
		var str0;
		var ic;
		result = true;
		str0=state.trim();  //すべての空白を除く
		str0=str0.toUpperCase(); //すべて大文字に変換する
		
		var P1=str0.indexOf("(");
		logic=str0.substring(0,P1);
		var P2=str0.lastIndexOf(")");
		str0=str0.substring(P1+1,P2);
		
		buf=SplitElm(str0);
		
		ic=0;
		switch (logic){
		case "AND":
		case "NOR":
		case "NOT":
		    ret=true;
		    break;
		case "OR":
		case "NAND":
		    ret=false;
		    break;
		default:
		    console.log("Logic Error "+logic);
		}
		while (buf[ic]!=null) {
		    if (buf[ic].indexOf("(")>=0){
		        elm=EvalEQ(memberStates,buf[ic]);
		   }else{
		        elm=HasState(memberStates,buf[ic]);
		        
		   }
			
		    switch (logic){
		    case "AND":	
		    	//elm=hasAllStates(userid,buf[ic]);
		    	//return ret;
		    	ret=elm && ret;
		        break;
		    case "NOR":
		    	//elm=hasStates(userid,buf[ic]);
		    	//return ret;
		    	ret= !(elm) && ret;          
		        break;
		    case "NOT":
		    	//elm=hasStates(userid,buf[ic]);
		    	//return ret;
		    	ret= !(elm) && ret;
		        break;
		    case "OR":
		    	//elm=hasStates(userid,buf[ic]);
		    	//return ret;
		    	ret= elm || ret;  
		        break;
		    case "NAND":
		    	//elm=hasAllStates(userid,buf[ic]);
		    	//return ret;
		    	ret= !(elm) || ret;  
		        break;
		    }
		     
		ic+=1;
		}
		return ret;
}

/**所有ステートカードに対してステート条件を評価する。*/
function evaluateStates(memberStates, condition, defaultOperation){
	var fl=false;
	if(condition != null) {
     	if(condition.length == 1) {
     		if(condition[0].indexOf("(")>=0) {
     			fl=true;
     		}
 		}
	}
    if (fl) {
    	return EvalEQ (userid, condition[0]) ;	
	}else{
		var p = parseStateCondition(condition, defaultOperation);
	
		var and = p.operator== "AND" || p.operator == "NAND";
		var not = p.operator=="NAND" || p.operator== "NOR" || p.operator== "NOT";
	
		var result = and ? hasAllStates(memberStates, p.condition) : hasStates(memberStates, p.condition);
		
		var ret =  (not ? !result : result);
		return ret;
    }
}

/**ダイアログの入力エラー情報を表示*/
function showInputError(sel, message){
	var msg = '<span class="input-action-errormessage" ><br>'+message + '</span>';
	$(sel).append(msg);
	$(sel).addClass('input-action-error');
}
/**ダイアログの入力エラー情報をクリア*/
function clearInputError(){
	$('.action-input').removeClass('input-action-error');
	$('.action-input').children('.input-action-errormessage').remove();
}
/**アクション実行直前のチェック*/
function validateActionInput(){
	clearInputError();
	var action = $(".set-action>select").val();
	if(!action || action=="0"){
		showInputError('.set-action',"アクションを選択してください。");
		return false;
	}
	var action = FRICORE.isadmin ? 
			_.findWhere(FRICORE.allActions,{id:action}) :
			_.findWhere(FRICORE.actions,{id:action});
	var type = action.type;
	var actionid  = action.id; 
	var result = true;
	var to = $(".set-to>select").val();
	if(!to || to == "0"){
		showInputError('.set-to',"[To]を選択してください。");
		result = false;
	}
	if(type == 'talk'){
		if(!$(".set-comment>textarea").val()){
			showInputError('.set-comment',"「話し合い」を行うには、メッセージを入力してください。");
			result = false;
		}
	}else if(type == 'share' || actionid == 'i0009'){
		var state = $('.set-state>select').val();
		if(!state || state.length == 0){
			showInputError('.set-state',"「インシデント情報共有」を行うには、共有したいインシデント情報を選択してください。");
			result = false;
		}
	}
	return result;
}
/**新規アクションダイアログ; 返信情報が変更されたときの処理*/
function replyChangeHandler(d){
	if(!d){
		$(".set-comment textarea").text("");	return;
	}
	var data = d;

	var el = {sentDate:formatDate(data.sentDate), type:data.action.type, message:data.message || data.action.message, subj:data.action.name,from:data.from.name, to:data.to.name, id:data.id}
	var msg = _.template("\n>返信元イベント:\n>\n>日時:<%=sentDate%>\n>From:<%=from%>\n>To:<%=to%>\n>アクション:<%=subj%>\n><%=message%>", el);
	$(".set-comment textarea").val(msg);
	var to = data.from.role;
	if(!canSendTo(to)){//system, allなど特殊ユーザは宛先に指定できない
		FrICORE.trace("返信できないユーザが選択されたため、宛先から除外されました。"+to);
	}else{
	//	$('.set-to select').val(to).trigger("change");
	}
}

function canSendTo(role){
	var roles = _.map(FRICORE.members, function(e){return e.role;})
	return roles.indexOf(role) != -1;
}
function makeReplyMessage(data){
	var data = JSON.parse(d);
	var el = {sentDate:formatDate(data.sentDate), type:data.action.type, message:data.message, subj:data.action.name,from:data.from.name, to:data.to.name, id:data.id}
	var msg = _.template("\n>返信元イベント:\n\n>日時:<%=sentDate%>\n>From:<%=from%>\n>To:<%=to%>\n>件名:<%=subj%>\n>\n><%=message%>", el);
}

/**新規アクションダイアログ; アクションの選択が変更されたときの処理*/
function actionChangeHandler(data){
	var enableStates = true;
	if(!data || !data.type){
		enableStates =	false;
		//TODO:戻すときはここを戻す
	}else if(data.type=='action' || data.type=='control'){
		enableStates =	data.attach;
	}else if(data.type=='share' || data.id == 'i0009'){//#17
		
	}else if(data.type == 'notification'){
		
	}else if(data.type == 'talk'){
		enableStates = false;
	}else{
		FrICORE.error("ERROR:invalid action type " + data.type);
	}
	$(".set-state select").val([]).trigger('change');//選択をクリア
	
	if(data.attachments && data.attachments.length != 0){
		$(".action-input.set-state select").val(data.attachments);
	}	
	$(".set-state select").prop("disabled", !enableStates).trigger('change');
	
	if(data.message){
		var v = $(".action-input.set-comment textarea").val();
		if(!v || v.length == 0){
			$(".action-input.set-comment textarea").val(data.message);
		}
	}
	
	if(data.hasOwnProperty("recipients") && data.recipients && data.recipients.length > 0){
		_.each($(".set-to select option"), function(el){
			if(data.recipients.indexOf($(el).val()) == -1){
				$(el).addClass("not-acceptable");
			}
		});
		$(".set-to select").trigger("update");
	}
	if(data.hasOwnProperty("to"))
		$(".set-to select").val(data.to);

}

function expandList(ev){
	
	var cc = $(ev).parent().next(".cccontent");
	if(cc.is(":visible")){
//		$(ev).text("(+)");
		$(ev).removeClass("expanded");
		cc.css("display","none");
	}else{
		$(ev).addClass("expanded");
		cc.css("display","inline-block");
	}
}

function loadHistory(all){
	if(!FRICORE.usersession){
		error("ログインしていません。");return;
	}
	
	var team = FRICORE.usersession.team;
	var rolename = FRICORE.usersession.role;
	var params = all ? {}: {userrole:rolename};
	
	updateWorkflowInfo();
	
	var url = 	"workflow/diag/"+team+"/history";
	doRequest(url, "GET", params)
	.done(function(resp, msg, xhr){
		//イベントとステートカードをクリア
		FRICORE.states = [];
		FRICORE.events = [];
		getState(team);
		
		$('#event-list tbody').empty();
		
		_.each(resp, function(e){
			onLoadEvent(e);
		});
		$(window).trigger("resize");
		FrICORE.trace(resp.length + " events received.");
	})
	.fail(function(xhr){
		var str = xhr.responseText || xhr.statusText;
		if(xhr.responseJSON){str = xhr.responseJSON.message;}
		error("履歴の取得に失敗しました。" + str, xhr);
		FrICORE.error(str);
	});
}



function viewSystem(){
	var url = "configurations.html";
	window.open(url, '	configurations','width=800,height=500');
}
function viewOrganizations(){
	var url = "organizations.html";
	window.open(url, 'oraniations','width=800,height=500');
}
function viewActions(){
	getHelpContent();
}

function viewActionCards(){
	var txt = "";
	if(!FRICORE.actions){
		alert("アクションカードは読み込まれていません。");return;
	}
	_.each(FRICORE.actions, function(el){
		var t = _.template('<%=id%>:<%=name%>\n', el);
		txt += t;
	});
	alert(FRICORE.actions.length + "枚のアクションカードが使用可能:\n\n"+txt);
}
function viewStateCards(){
	var txt = "";
	if(!FRICORE.states){
		alert("ステートカードは読み込まれていません。");return;
	}
	_.each(FRICORE.states, function(el){
		var t = _.template('<%=id%>:<%=name%>\n', el);
		txt += t;
	});
	alert(FRICORE.states.length +"枚のステートカードが使用可能:\n\n"+txt);
}

function onActionChanged(evt){
	var selected = $(".set-action").find("select>option[selected]");
	
//	var data = selected.attr("data-carddata");
}
function showActionCards(){
	var container = $('#actioncard-dialog');
}
function showContacts(){
	var container = $('#contact-dialog');
	FRICORE.members.forEach(function(el){
		var state = el.online;
		var e = _template("<span class='card'><%=name%>(<%=%>)</span>", el);
		container.append(e);
		
	});
}

/**リストボックスの表示形式を制御*/
function formatOption(option) {
	var ret = _.template("<span title='<%=title%>' class='<%=class%>'><%=text%></span>");
	return ret;
};



var zoomratio=1;
function zoomin(){
	zoom(window.zoomratio=window.zoomratio+0.1);
}
function zoom(ratio){
 	var c = $("#svgcontainer");
	var scale = $('#mermaidChart0')[0].currentScale = ratio;

 	$("#zoomratio").text(Math.round(window.zoomratio*100)+"%");
 	fitSVG();

 }
function zoomout(){
	zoom(window.zoomratio=window.zoomratio-0.1);
}

/**DOM座標をSVG座標に変換*/
function tosvg(point){
	var svg = $('#mermaidChart0')[0];
	var ret = svg.createSVGPoint();
	ret.x = point.x;
	ret.y = point.y;
	return ret.matrixTransform(svg.getScreenCTM().inverse());

}
function resume(){

	var scale = $('#mermaidChart0')[0].currentScale;
	$('#mermaidChart0')[0].currentScale=1;
	$('#mermaidChart0')[0].currentTranslate.x=0;
	fitSVG();

	window.zoomratio=1;
 	$("#zoomratio").text(Math.round(window.zoomratio*100)+"%");
 
}
function fit(){
	
}
function trim(){
	
	var xs = _.min($("svg").children("g"), function(e){return e.getBBox().x;}).getBBox()
	var xm = _.max($("svg").children("g"), function(e){return e.getBBox().x+e.getBBox().width;}).getBBox()
	var ys = _.min($("svg").children("g"), function(e){return e.getBBox().y}).getBBox()
	var ym = _.max($("svg").children("g"), function(e){return e.getBBox().y + e.getBBox().height;}).getBBox()
	
	var orgrect = {left:xs, top:ys, right:xm, bottom:ym};
	var svgpos = $('#svgcontainer').position();

	doTrim(true);
}
function doTrim(exec){
	if($('#range-selection').length <=0 )return;
	
	var box = $('#range-selection')[0].getBBox();

	var svg = $(window.dragger.target)[0];
	var all = svg.getBBox();
	
	var trans = svg.createSVGPoint();trans.x=all.x-box.x;
	var scale = all.width/box.width;
	if(Math.abs(scale- svg.currentScale) < 0.01 || scale > 10000){ 
		FrICORE.error("拡大率が許容範囲外です: " + scale);
		return;//
	}
	svg.currentScale = scale;
	svg.currentTranslate.x = trans.x;
	fitSVG();
}

//////////////////////////////////////////////////////////////////



//ログイン前の初期化処理
function preinit(){
	loadCards();
	onIDchanged();
}
//ログイン後の初期化処理
function init(){
	FRICORE.wfsession = null;//ログイン中のユーザのセションキー。リクエストヘッダに設定
	FRICORE.usersession =null;//ログイン中のユーザのセション情報
	FRICORE.socket = null;//web socket
	FRICORE.members = null;//チームメンバ定義情報
	FRICORE.actions = null;//アクションカード情報
	FRICORE.presence = null;//チームメンバのプレゼンス情報
	FRICORE.events = [];//受信済イベント
	FRICORE.states = [];//すべてのステートカード
	FRICORE.availableStates = [];//入手済ステートカード
	$('#event-list table tbody').empty();
	$('#svgcontainer').empty();
	
}
function isSystemUser(member, phase){
	if(!member.system)return false;

	if(phase >= 0){
		if(phase >= member.system.length){
			return member.system[phase - 1];
		}else{
			return member.system[0];
		}
	}
		
	return true;
}
function adjustUserList(){

	$('#loginpanel select.role').empty();
	var team = $('#loginpanel select.team').val();
	var teamRoles = _.groupBy(window.FRICORE.contacts, function(e){return e.team;});
	var phase = 1;
	var users = _.filter(teamRoles[team], function(e){return !isSystemUser(e, phase);}) ;

	$('#loginpanel select.role').append($("<option/>").val(null));	
	_.each(users, function(e){
		
		
		//$('#loginpanel select.role').append($("<option/>").val(e.role).text(e.rolename).attr('title', e.email));
		//
		var tmpl = _.template($("#userpicker-template").html());
		var u = tmpl(e);
		$('#loginpanel select.role').append(u);
		
		
	});	
	if(window.FRICORE.isadmin){
		var admin = _.findWhere(users, {isadmin:true});
		if(admin)
			$('#loginpanel select.role').val(admin.role);
		
	}else{
		$('#loginpanel select.role').prop('selectedIndex',1);
	}
	$('#loginpanel select.role').trigger('update');
	onIDchanged();
}
function adjustGroupList(team, teamname){

	$('#loginpanel select.team ').empty();
	var allusers = window.FRICORE.contacts;
	if(!window.FRICORE.setting){return;}
	var teams = window.FRICORE.setting.teams;
	$('#loginpanel select.team').append($("<option/>").val(null));
	_.each(teams, function(team){
		$('#loginpanel select.team').append($("<option/>").val(team.id).text(team.name));
	});
	
	$('#loginpanel select.team').prop('selectedIndex',1);
	$('#loginpanel select.team').trigger('update');
	onIDchanged();

}
function doLogin(){
	init();
	block();
	var url = "workflow/session";
	var data = {userid:$('#userid').val(), password:$('#password').val()};
	$.ajax({async:false,method:"post", dataType:'json', url:url, data:data})
	.done(function(resp, msg, xhr){
		FRICORE.wfsession = resp.sessionkey;
		FrICORE.info("logged in.");
		
		$("#sessioninfo").html(
			_.template("<%=username%>:<%=isadmin%>@<%=team%>", resp)
		);
		
		var me = _.findWhere(FRICORE.contacts, {email:resp.userid});
		if(me && me.hasOwnProperty("icon"))
			$("#sessioninfo").prepend(_.template("<span class = 'fa fa-icon <%=icon%>'></span>", me));

		window.FRICORE.setting = getActiveScenario();

		FRICORE.usersession = resp;
		var name = resp.username || "UNKNOWN";
		var team = resp.team || "";
		var rolename = resp.rolename || name || "";
		var info = rolename + "("+resp.userid+")";
		$("#loginuser").html(info);
		document.title = [(window.versioninfo||{}).CAPTION,info].join(" -- ");
		monitor.lap('start login');
		connect();
		monitor.lap('connect');
		
		
		loadContacts(team);
		monitor.lap('loadContacts');
		getCards();
		monitor.lap('getCards');

		getState(team);
		monitor.lap('getState');

		loadHistory();
		monitor.lap('loadHistory');
		
		refreshSystemView();

		if(FRICORE.isadmin){
			getProcess({},true);
			//updateWF();
			monitor.lap('getProcess');

		}
		$(window).trigger("resize");
		unblock();
	})
	.fail(function(xhr){
		$("#sessioninfo").html("login failed!");
		var msg = xhr.responseJSON ? xhr.responseJSON.message : xhr.responseText;
		error("ログオンに失敗しました。<br><br>" + (msg || ""));
		unblock();
	});
	
}
function doLogout(){
	init();
	doRequest("workflow/session", "DELETE", {})
	.done(function(resp, msg, xhr){
		$("#sessioninfo").empty();
		if(FRICORE.socket){FRICORE.socket.close();}
		
		updateLoginInfo();
		FrICORE.info("logged out");		
	})
	.fail(function(xhr){
		FrICORE.error("logout failed.");
	});
}
/**ファシリテータ画面:チーム参加者情報を表示*/
function loadContacts(team){
	var url = "workflow/contacts/" + team;
	doRequest(url, "GET", {})
	.done(function(resp, msg, xhr){
		FRICORE.members = resp;
		$("#contacts .panelcontent").empty();
		resp.forEach(function(c){
			var t = _.template("<a title='<%=name%>(<%=email%>) <%=(hasOwnProperty('desc')?desc:'')%>' data-userid='<%=email%>' data-cardtype = 'contact'  href = '#' class='card clickable'><%=rolename%></a>", c);
			$("#contacts .panelcontent").append($(t).data('card', c).on('click', function(e){
				onCardClicked(e);
			}));
		});
		FrICORE.trace("fetched:contacts");
	})
	.fail(function(xhr){
		var msg = xhr.responseJSON ? xhr.responseJSON.message : xhr.responseText;
		error("連絡先の取得に失敗しました。<br><br>" + msg, xhr);
	});
	
}

function expandRecipient(to){
	var ret = null;
		if(to == "ALL")
			ret = {name:"チーム全員",rolename:"チーム全員",desc:"チーム全員",team:FRICORE.usersession.team, role:"all"}
		else if(to == "system")
			ret = {name:"システム",rolename:"system",team:FRICORE.usersession.team, role:"system"};
		else
			ret = _.findWhere(FRICORE.members, {role:to});

	return ret;
}
/**新規アクションダイアログの入力に従ってアクション要求を送信*/
function requestAction(){

	var url = "workflow/process/act"; 
	var to = $(".set-to>select").val();
	var cc = $(".set-cc>select").val();
	var replyto = $(".set-reply-to>select").val();
	var action = $(".set-action>select").val();
	var state = $('.set-state>select').val();
	var message = $(".set-comment>textarea").val();
	var action = _.findWhere(FRICORE.isadmin ? FRICORE.allActions : FRICORE.actions,{id:action});
	var act = Object.assign(action);
	if(act.hasOwnProperty("available")) delete act.available;
	var data = {card:act,
		to:expandRecipient(to), //(to == "ALL" ? {name:"チーム全員",rolename:"チーム全員",desc:"チーム全員",team:FRICORE.usersession.team, role:"all"} : _.findWhere(FRICORE.members, {role:to})),
		cc:[],replyto:null
	};
	
	data.card.message=message || "";
	if(cc){
		_.each(cc, function(e){
			var tmp = _.findWhere(FRICORE.members, {role:e});
			if(tmp){data.cc.push(tmp);}
		});
	}
	if(replyto){
		var rep = _.findWhere(FRICORE.events, {id:$(".set-reply-to>select").val()});
		if(rep){	data.replyto = rep;}
	}
	if(state){
		data.card.attachments=[];
		_.each(state, function(e){data.card.attachments.push(e);})
	}
	
	doRequest(url, "POST", JSON.stringify({action:data}), {contentType:"application/json"})
		.done(function(resp, msg, xhr){
			FrICORE.info("done:action "+resp);
		})
		.fail(function(xhr){
			var msg = xhr.responseJSON ? xhr.responseJSON : xhr.responseText;
			error(msg, xhr);
		});
}
/**汎用: RESTリクエストを送信
 * @param {string} url URL
 * @param {string} method HTTPメソッド
 * @param {object} params リクエストパラメタ
 * @param {object} args ajax()に指定する引数
 * @param {object} headers リクエストヘッダ 
 * */
function doRequest(url, method, params, args, headers){
	block();
	var arg = {async:false, headers:{'X-ssc-sessionkey':FRICORE.wfsession},method:method, dataType:'json', url:url, data:params};
	if(args){
		arg=Object.assign(arg, args);
	}

	if(headers){
		arg.headers = Object.assign(arg.headers, headers);
	}

	//disable cache
	arg.headers['Pragma']='no-cache';
	arg.headers['Cache-Control']= 'no-cache';                    
	arg.headers['If-Modified-Since']='Sat, 01 Jan 2000 00:00:00 GMT';

	var ret = $.ajax(arg)
	.done(function(resp, msg, xhr){
		unblock();
		FrICORE.trace(url + ":完了。");
	})
	.fail(function(xhr){
		unblock();
		FrICORE.error(url + ":失敗。\n", xhr);
	});
	
	return ret;
}
/**読み込み中イメージを表示*/
function block(){
	$("#loading")[0].style.display='block';
	
}
/**読み込み中イメージを非表示*/
function unblock(){
	$("#loading")[0].style.display='none'
}
/**イベント一覧で選択されているtr要素を返す*/
function getSelectedEventElement(){
	if(isFacilitator())
		return $("#wfhistory tr.selected");
	else
		return $("#event-list tr.selected");
}
/**ファシリテータ画面を表示中かどうかを返す*/
function isFacilitator(){
	return $("#facilitator-main").is(":visible");
}


/**汎用:日時を書式化
 */
function formatDate(insec){
	if(!insec) return "";

	//省略時は時刻の起点を本日の12:00とする
 	var offset = window.FRICORE.params['timeoffset'] || new Date();
	var acceleration = window.FRICORE.params['acceleration'] || 1;
	var sec = offset + insec + offset;

	var sec = insec;
	if(sec <= 0)return "N/A"
	
	var d =new Date(sec);
	var str = [
//	("0000"+d.getFullYear()).slice(-4),
	("00"+(d.getMonth()+1)).slice(-2),
	("00"+d.getDate()).slice(-2)].join("/")+" "+
	[("00"+d.getHours()).slice(-2),
	("00"+d.getMinutes()).slice(-2),
	("00"+d.getSeconds()).slice(-2)].join(":");
	return str;
}
function formatMemberStateCard(states){
	var tmpl = $('#statelist-template').html();
	var wrapper = "<span class='statecard-ref state'></span>";
	var buff = [];
	_.each(states, function(el){
		var c = _.findWhere(FRICORE.states,{id:el}) || {id:el,name:"不明なステート:"+el};
		
		var line = _.template(tmpl, c);
		var l = $(wrapper).prepend($(line));
		var ll = l.prop("outerHTML");
		buff.push(ll);
	});
	return buff.join("\n");
}
/**ステートIDまたはその配列を書式化*/
function formatStateCard(obj, type){
	var tmpl = $('#statelist-template').html();
	var buff=[];
	if(!obj)return "";

	var icon = {"addstate":"<span class = 'fa fa-icon fa-plus' title='システムステートに追加されます。'></span>",
			"removestate":"<span class='fa fa-icon fa-minus' title='システムステートから削除されます。'></span>",
			"condition":"<span class='fa fa-icon fa-question' title='システムステートの条件です。'></span>",
			"state":"<span class='fa fa-paperclip' title='このステートがイベントに添付されます。'></span>"}[type || 'state'];
	var wrapper = {"addstate":"<span class='statecard-ref add'></span>",
			"removestate":"<span class='statecard-ref remove'></span>",
			"condition":"<span class='statecard-ref condition'></span>",
			"state":"<span class='statecard-ref state'></span>"}[type||'state'];
	
	if(_.isArray(obj)){
		_.each(obj, function(el){
			if(["AND","NAND","OR","NOR","NOT"].indexOf(el) != -1){
				buff.push("<span/><span class='operator'>" + el + "</span>");
			}else{
				var cur=_.findWhere(FRICORE.states, {id:el});
				if(cur){
					var content = _.template(tmpl, (cur ? cur : {id:el,name:"?"}));
					var h = $(wrapper).prepend($(content)).prepend($(icon));
					buff.push(h[0].outerHTML);
				}
			}
		});
		return buff.join("");
	}else{
		var cur=_.findWhere(FRICORE.states, {id:obj});
		var content = _.template(tmpl, (cur ? cur : {id:obj,name:"?"}));
		var h = $(wrapper).prepend($(content)).prepend($(icon));
		buff.push(h[0].outerHTML);
		return h[0].outerHTML;
	}
}
function formatActionCard(a){

	if(!a || a.length == 0)return "";

	var buff = [];
	if(_.isArray(a)){
		buff = a; 	
	}else{
		buff.push(a);
	}
	var ret = [];
	_.each(buff, function(data){
			ret.push(_.template($("#actioncard-template").html(), data));
	});
	return ret.join("");
}
function formatReplyCard(a){
	if(!a || a.length == 0)return "";
	return formatByTemplate(a,  "#replycard-simple-template");
}
function formatByTemplate(a, tmplid){
	if(!a || a.length == 0)return "";
	var buff = [];
	if(_.isArray(a)){
		buff = _.union(buff, a);
	}else
		buff.push(a);
	var ret = [];
	if(buff.length == 0)	return "";
	_.each(buff, function(e){
		ret.push(_.template($(tmplid).html(), e));
	});
	return ret.join("\n");
}

/**ワークフロー状態から特定の種類のステートカードを探す*/
function hasState(wf, type){
	var statecards = _.flatten(_.pluck(_.pluck(wf.history, 'action'), 'statecards'));
	if(!statecards || statecards.length == 0) return;
	
	_.each(statecards, function(e){
		var s = _.find(FRICORE.states, function(cur){
			return String(cur.id)==String(e);
			});
		if(s && s.hasOwnProperty('type') && type == s.type)return s;
	});
	return null;
}
function makePhaseChooser(container, team){
	if($(container).length == 0) return;
	$(container).empty();
	_.each(window.FRICORE.setting.phases, function(phasedef){
		var p = JSON.parse(JSON.stringify(phasedef));
		p.team=team;
		p.id = ["assign-phase",p.phase,p.team].join('-');
		var opt = _.template("<input type='radio' name='assign-phase-<%=team%>'  class='assign-phase'  value = '<%=phase%>' id = '<%=id%>'/><label for='<%=id%>' title='<%=description%>'><%=phase%></label>", p);
		container.append(opt);
	});
	if(window.FRICORE.setting.phases && window.FRICORE.setting.phases.length == 1){
		$(container).children("input[type='radio']").prop("checked",true);
	}
	$(container).buttonset();
}
/**ワークフロープロセス一覧を取得して一覧を更新する
 * @param{object} param リクエストパラメタ
 @param{boolean} select グループ一覧とプロセス一覧を再描画する(性能に影響)
 */
function getProcess(params, select){

	makePhaseChooser($('.assign-phase-container-all'), "all");

	var url = "workflow/process/";
	//var params = {"duration":3};
	var params = {};
	var re = doRequest(url, "GET", params)
		.done(function(resp, msg, xhr){
			FrICORE.trace("done.");
			var selected = getSelectedTeam();
			
			var container = $("#processlist tbody");
			container.empty();
			var dat = FRICORE.wfprocesses = resp;
			

			for(var i = 0; i < dat.length; i ++){
				
				var summary = makeWorkflowStatusInfo(dat[i]);
				
				var row = _.template($('#processlist-template').html(), summary);
				var rr=$(row).data('processdata', dat[i]).appendTo(container);

				makePhaseChooser($(rr).find('.assign-phase-container'), summary.team);

				$(rr).find("select").on("click",function(e){e.stopPropagation()});
				$(rr).find("input").on("click",function(e){e.stopPropagation()});
				$(rr).find("button").on("click",function(e){e.stopPropagation()});
				$(rr).find("a").on("click",function(e){e.stopPropagation()});

				$(rr).find(".ignoreclicked").on("click",function(e){e.stopPropagation()});
				
				if(Number(summary.phase)> 0){
					rr.find("input[type=radio]").prop('checked', false);
					var selector = "input[type=radio][value="+(summary.phase)+"]";
					rr.find(selector).prop('checked',true).trigger('change');
				}
				
				adjustProcessList($(rr), summary);
				
			}

			if(dat.length == 1){
				selectTeam(dat[0].team);
				updateWF();
			}else if(select){
				updateTeamPicker();
				var grp = _.find($("#processlist .listitem-selectable"),function(e){var s = $(e).find('.assign-team');return s && s.text()==selected});
				onSelectProcess(grp);
			}
		})
		.fail(function(xhr){
			var msg =xhr.responseJSON ? xhr.responseJSON : xhr.responseText;
			error("演習状態の参照に失敗しました。", xhr);
			FrICORE.error("failed."+ msg, xhr);
		});
}

function summerizeWorkflowState(forAdmin){
	var wf = forAdmin ? (getSelectedTeamWorkflow() ||{}) : FRICORE.workflowstate;

	if(_.isEmpty(wf)){
		return "シナリオが選択されていません。";
	}
	var dat = JSON.parse(JSON.stringify(wf));
		
	var txt = _.template($("#flowstatus-template").html(),makeWorkflowStatusInfo(dat));
	return txt;
}

/**ファシリテータ画面: チーム一覧と概要メッセージ用のオブジェクトを生成*/
function makeWorkflowStatusInfo(wf){
	if(!wf){
		FrICORE.error("WorkflowInstance is null.");
	 	return {};
	 }

	var setting = _.findWhere(FRICORE.setting.phases,{phase:Number(wf.phase)});
	
	var summary = {state:wf.state,created:formatDate(wf.created), 
			team:wf.team, score:wf.score||'', phase: wf.phase};
	//summary.elapsed = wf.start != null  ? (new Date().getTime() - wf.start)/1000 : 0;
	summary.timelimit = setting ? setting.timelimit:null;
	summary.scenario = FRICORE.activeScenario;
	//summary.rest = wf.timelimit - wf.elapsed;
	summary.start = wf.start ? formatDate(new Date(wf.start)) : "";
	summary.started = !!summary.start;
	summary.aborted = wf.state == "Aborted";
	summary.paused = wf.state == "Paused";
	summary.time = wf.world ? formatDate(wf.world.time) : undefined;
	summary.elapsed = wf.start != null  && wf.world ? (wf.world.time - wf.start)/1000 : 0;
	summary.rest = wf.timelimit - wf.elapsed;
	summary.world = wf.world;
	summary.stateText = stateToText(summary.state);
	summary.name = wf.activeScenario ? (wf.activeScenario.name || "") : "";
	return summary;
}

function stateToText(state){
	if(!state)	return "N/A";
	var tbl={'Started':'実行中', 'Suspended':'一時休止', 'Aborted':'中断', 'None':'未開始', '':'未開始',undefined:'不明', Paused : '一時停止'};
	return tbl[state] || state;
}



function selectTeam(name){
	var sel = $(_.find($("#processlist  td"), function(e){return $(e).text()==name}));
	$("#processlist  tr").removeClass('seleted');
	$(sel).parent().addClass('selected');
	if(sel.text() != name){//TODO；冗長な更新処理
		$(sel).click();
	}
	return $(sel).parent();
}

function abort(evt, all){
	getCards();
	getState();
	assign(evt.currentTarget, "abort", all);
}
/*function resumePhase(evt, team){
	var url = "";
	if(all){
		url = "workflow/process/" + team + "/resume";
	}
	
	doRequest(url).done(function(resp, stat, xhr){
		getProcess();
	}).fail(function(xhr){
		error("再開に失敗しました。", xhr.responseText);
	});
}*/
function storeAll(evt){
	if(!FRICORE.wfprocesses){
		alert("演習シナリオがロードされていません。");return;
	}
	
	var url = "workflow/diag/all/download";
	var name = "workflow.all."+new Date().toISOString()+".json";
	storeProcess(url, name);
}
 function storeProcess(url, name){
		doRequest(url, "GET").done(function(resp, msg, xhr){
			var blob = new Blob([ JSON.stringify(resp) ], { "type" : "application/json" });
			var anchor  = window.URL.createObjectURL(blob);
			var a = document.createElement("a");
			a.download = name;
			a.href = anchor;
			a.click();
			FrICORE.trace("download link: " + anchor);
		}).fail(function(xhr){
			FrICORE.error("failed to store workflow instance.", xhr);		
			error("状態の保存に失敗しました。", xhr.responseText);

		});
 }
function store(evt){
	
	//var pid = $(evt).parents('tr').find('.process-id').text();
	var team = $(evt).parents('tr').find('.assign-team').text();
	var url = "workflow/diag/"+team + "/download";
	var name = "workflow." + team + "."+new Date().toISOString() + ".json";
	
	storeProcess(url, name);
}
function restore(evt){
	
 	var file = evt.files[0];
	var reader = new FileReader();

	reader.onload = function(evt) {
		var d = evt.target.result;
		var data = JSON.parse(d);
		var url = "workflow/diag/all/upload";
		doRequest(url, "POST", d,  {dataType:'json',contentType: "application/json"})
			.done(function(resp, msg, xhr){
				FrICORE.info("upload completed.");
				getProcess({},true);
				info("状態を復元しました。");
			})
			.fail(function(xhr){
				FrICORE.error("failed to upload.", xhr);
				error("状態の復元に失敗しました。"+ xhr.responseText);
			});
	};
	if(reader)
		reader.readAsText(file);
 }
 
 /**一覧で選択されたワークフロープロセスを開始または中断する*/
function assign(evt, action, all){
	
	if(action == "abort" && !confirm("演習フェーズを中断しますか?"))return;
	
	var team = $(evt).parents('tr').find('.assign-team').text() || $(evt).parents('tr').find('.assign-team').find("input").val();
	if(!team && !all){alert("グループを指定してください。");return;}
	var phase= !all ? $(evt).parents('tr').find('.assign-phase:checked').val():
		$(evt).parent().find('.assign-phase:checked').val();
	
	if(action == "start" && !(Number(phase)>0)){
		alert("開始するフェーズを選択してください。");
		return;
	}

	if(all){
		assignProcess("all", phase, action);
		getProcess();
	}else{
		assignProcess(team, phase, action);
		getProcess();
	}
}
function assignProcess(team, phase, action, select){
	var url = "workflow/process/" + team +  "/" + action;
	var params={"team":team, "phase":phase};
	doRequest(url, "GET", params)
		.done(function(resp, msg, xhr){
			if(select){
				getProcess({},false);
				selectProcess(team);
			}

			updateWF();
			
			FrICORE.info(abort ? "process aborted." : "process assigned.");	
		})
		.fail(function(xhr){
			var str = xhr.responseText || xhr.statusText;
			if(xhr.responseJSON){str = xhr.responseJSON.message;}
			error("失敗しました。" + str, xhr);
		});
}
function getProcessData(proc, name){
	var data =  _.find(proc.dataItems, function(e){return e.name===name;}) || "";
	if(data){
		return data.value;
	}else
		return null;
}
function getCards(evt){
	getActionCards(evt);
}
function getActionCards(evt){
	var processid = 0;
	if(evt){
		var id = $(evt).parents('tr').find('.process-id');
		if(!id){
			alert("error: process id not found.");
			return;
		}
		processid =  id.text();
	}
	
	var url = "workflow/process/cards";
	$("#actions .panelcontent").empty();
	doRequest(url, "GET")
	.done(function(resp, msg, xhr){
		FRICORE.actions = resp;
		resp.forEach(function(c){
			if(c.hidden) return;
			var t = _.template("<a data-cardtype = 'action' href = '#' class='card clickable' title='<%=hasOwnProperty('description')?description:''%>'><%=name%></a>", c);
			$("#actions .panelcontent").append($(t).data('card', c).on('click', function(e){
				onCardClicked(e);
			}));
		});
	})
	.fail(function(xhr){
		var str = xhr.responseText || xhr.statusText;
		if(xhr.responseJSON){str = xhr.responseJSON.message;}
		error("アクションカードの読み込みに失敗しました。"+str, xhr);
	});
}
function getState(team){
	var url = "workflow/diag/state";
	var param = team ? {team:team} : {};
	doRequest(url, "GET", param)
	.done(function(resp, msg, xhr){
		$('#wfstatus .state .empty').hide();
		$('#wfstatus').find('.state').find("tbody").empty(); 
		FRICORE.states = resp;
		_.each(resp, function(e){
			var tr = _.template($("#statedef-template").html(), e);		
			$('#wfstatus').find('.state').find("tbody").append($(tr)); 
		});
	})
	.fail(function(xhr){
		var str = xhr.responseText || xhr.statusText;
		if(xhr.responseJSON){str = xhr.responseJSON.message;}
		error("ステートカードの読み込みに失敗しました。", xhr);
	});
}
function getQueries(evt){
	
	var id = $(evt).parents('tr').find('.process-id');
	if(!id){
		alert("プロセスIDが見つかりません。");
		return;
	}
	var url = "workflow/process/queries";
	$("#queries .panelcontent").empty();
	doRequest(url, "GET")
	.done(function(resp, msg, xhr){
		resp.forEach(function(c){
			if(c.hidden) return;

			var t = _.template("<a data-cardtype = 'query' href = '#' class='card clickable'  title='<%=description%>'><%=name%></a>", c);
			$("#queries .panelcontent").append($(t).data('card', c).on('click', function(e){
				onCardClicked(e);
			}));
		});
	})
	.fail(function(xhr){
		var str = xhr.responseText || xhr.statusText;
		if(xhr.responseJSON){str = xhr.responseJSON.message;}
		error("失敗しました。", xhr);
	});
}
function refreshPresence(){
	if(!FRICORE.usersession.team){ FrICORE.error("未ログインのためプレゼンス情報を更新できません。");return;}

	var url = "workflow/contacts/" + FRICORE.usersession.team;

	doRequest(url, "GET", {presence:true})
	.done(function(resp, msg, xhr){
		FRICORE.presence = resp;
	})
	.fail(function(xhr){
		var str = xhr.responseText || xhr.statusText;
		if(xhr.responseJSON){str = xhr.responseJSON.message;}
		error("チームメンバ情報の読み込みに失敗しました。", xhr);
	});
	
}
function parseURI(anchor){
	var m = anchor.match(/^(?:([^:\/?#]+):)?(?:\/\/([^\/?#]*))?([^?#]*)(?:\?([^#]*))?(?:#(.*))?/);
	var path = _.compact(m[3].split("/"));
	var url = {scheme:m[1], origin:m[2],path:path,fragment:m[4],query:m[5]};

	return url;
}
/**未使用*/
function send(){
	if(!FRICORE.socket)	throw "not connected";
	
	var msg = $("#wsmessage").val();
	var data = {
		"type":"action",
		"to":["hogehoge@foobar"],
		"from":"hugahuga@foobar",
		"message":msg,
		"description":"aaaa",
		"sentDate":"2017-01-01T12:20:00.000Z",
		"level":1,
		"extension":{"key1":"val1","key2":"val2"},
		"attachments":["1111", "2222"],
		"replyTo":"rererere",
		"replyOption":"rararara"};	
	FRICORE.socket.send(data);
	
}

/**webソケット接続*/
function connect(){
	var url = parseURI( document.location.href);
	var sv = ["ws:/",url.origin,url.path.slice(0, url.path.length - 1), "notification",  FRICORE.usersession.userid].join("/");
	FRICORE.socket = new WebSocket(sv);
	FRICORE.socket.onopen = processSocketEvent;
	FRICORE.socket.onclose = processSocketEvent;
	FRICORE.socket.onmessage =processSocketEvent;
	FRICORE.socket.onerror = processSocketEvent;
	

}
/**webソケットからのイベント受信処理*/
function processSocketEvent(ev){
	if(ev.type === "message"){
		if(!ev.data)	return;

		var data = JSON.parse(ev.data);
		FrICORE.trace("socket event received." + ev.data);

		if(data.presence){
			renderPresence(data.presence);
		}else if(data.action){
		
			if(data.level && (data.level & FRICORE.level.CONTROL)){
				notify("(通知) ", data);
				console.log("control message "+ data.action);
			}else{
				onLoadEvent(data);
			}
//			$(window).trigger("resize");
		}
	}else if(ev.type === "open"){
		notify("(通知) サーバに接続しました。", data);
		updateLoginInfo();
	}else if(ev.type === "close"){
		notify("(通知) サーバから切断されました。", data);
		error("サーバとの接続が解除されました。");
		
		updateLoginInfo();
	}
}
/**一時的な通知メッセージを表示する*/
function notify(msg, notif){
	var str = msg;
	if(notif && notif.message){
		str += notif.message;
	}
	if(notif && notif.action){
	 	str = [str, notif.action.name || "", notif.action.message || ""].join(" ");
	 }
	 popupWarning(str); 
} 
/**控え目にエラー情報を表示*/
function popupWarning(str){
//	if($('#warningarea') == str)return;//同じメッセージなら何もしない
	$('#warningarea').text(str);
	
	if(!$('#statusbar').is(':visible')){
		$('#statusbar').show();
		$(window).trigger('resize');
		window.setTimeout(hideStatusbar,10000);
	}
}
function hideStatusbar(){
	if($('#statusbar').is(':visible')){$('#statusbar').hide();$(window).trigger('resize');}
}


var MAX_MESSAGE_LEN = 80;//イベントリストに表示するメッセージの最大長
/**イベントのメッセージを短縮した文字列を返す(ヘッダなし)*/
function compressMessage(data){
	if(!data)return "";
	var msg = data.reply ? (data.reply.name + ":" + (data.reply.message || "")) : ((data.message||data.action.message||""));
	if(msg.length > MAX_MESSAGE_LEN + 3){
		msg = msg.substring(0, MAX_MESSAGE_LEN) + "...";
	}
	return msg;
}
/**イベントを要約した文字列を返す(ヘッダ付き)*/
function summerizeMessage(data){
	if(!data)	return "";
	var msg = compressMessage(data);
	var el = {sentDate:formatDate(data.sentDate), type:data.action.name, to:data.to.rolename, message:msg, from:data.from.rolename, id:data.id}
	var e = _.template("<%=sentDate%>: [<%=type%>] <%=from%>から<%=to%>: <%=message%>", el);
	return e;
}
/**イベントをやや詳細な複数行テキストに変換*/
function formatMessage(data){
	if(!data) return "";
	var msg = data.reply ? data.reply.name : (data.message||data.action.message||"");

	var cclist = data.cc ? _.map(data.cc, function(e){return e.rolename;}).join(";") : "";
	var el = {sentDate:formatDate(data.sentDate), type:data.action.name, to:data.to.rolename, cc:cclist,message:msg, from:data.from.rolename, id:data.id}
	var e = _.template("送信日時:<%=sentDate%>\nアクション: [<%=type%>]\nFrom:<%=from%>\nTo:<%=to%>\n\n<%=message%>", el);
	return e;
}
function escape(str){
	return _.escape(str);
}

/**イベント受信/取得処理
 */
//一覧更新用
function onLoadEvent(data, showHidden){
	var effects = [];
	FRICORE.events.push(data);
//	if(!showHidden && data.level & 0xf0)	return;//演習中は非表示のイベント
	if(!showHidden && 
		(data.type == "null" || data.reply && data.reply.type=="null"))	
		return;//演習中は非表示のイベント
	if(data.reply && data.reply.type=="hidden")
		return;//空応答
    var ida = window.localStorage.getItem("kagi");//localStorageの中身を取得
    var hairetsu = JSON.parse(ida);//localStorageの中身を配列に変換
    if(!hairetsu){
    	hairetsu=[];
    }
	if(data.reply){
		var exists = $('#event-list tr[data-eventid=' + data.reply.id + ']');
		if(exists && exists.length>0){
			FrICORE.trace("重複メッセージを破棄(0):" + data.message);
			return;//重複
		}
	}
	if(data.action && data.action.type == "alert"){
		popupWarning("システムからの警告:" + data.message);
		return;
	}
	
	var tbl = $('#event-list').find('tbody');
	var ftbl = $('#wfhistory').find('tbody');
	var cclist = data.cc ? _.map(data.cc, function(e){return e.rolename;}).join(";") : "";

	var msg = compressMessage(data);
	var d = {date:formatDate(new Date(data.replyDate ? data.replyDate : data.sentDate)), 
			from: data.from.rolename, to: data.to.rolename, cc:cclist, 
		action:data.action, message:msg, id:data.id};

	d.title = escape(_.template("日時:<%=date%>\nFrom:<%=from%>\nTo:<%=to%>\n\n<%=action.name%>:<%=message%>", d));
	var e = _.template($('#eventlist-template').html(), d);
	var tr = $(e);
	if(data.replyTo){
		var r = summerizeMessage(data.replyTo);
		r  ="<br><span class='replyTo' title='"+summerizeMessage(data.replyTo)+"'>返信元:" + r + "</span>"
		$(tr).children("td").last().append(r);
	}

	if(data.from.email==FRICORE.usersession.userid){//fromが自分でない
		$(tr).children("td").first().append($('<span class="fa fa-level-up" style="color:blue" tytle="送信"></span>'));
	   if(!FRICORE.count[data.action.id])
		   {
		   FRICORE.count[data.action.id]=1;
		   }else{FRICORE.count[data.action.id]++;}
	  
	   }else{
		$(tr).children("td").first().append($('<span class="fa fa-level-down" style="color:green" title="受信"></span>'));
	}
	if(hairetsu.length == 0){
	}else{
		while (hairetsu.length>0){
		  var atai = hairetsu[0];
		  if(data.id==atai){
			  toggleMark(tr);
			}	
		
		  hairetsu.shift();
		}
	}
	var cards = [];
	
	if(data.action.attachments)	cards = _.union(cards, data.action.attachments);
	if(data.reply && data.reply.state)	cards.push(data.reply.state);
	if(data.action.statecards)	cards = _.union(cards,data.action.statecards);
	
	if(cards && cards.length != 0){//ステートカードGET
		_.each(cards, function(i){
			var org=_.findWhere(FRICORE.states, {id:i});
			var current=_.findWhere(FRICORE.availableStates, {id:i});
			if(current){
				FRICORE.availableStates = _.without(FRICORE.availableStates, current);
				FrICORE.trace('重複するステートカードを獲得:' + org.name);
			}
			if(org){
				FrICORE.trace('新しいステートカードを獲得:' + org.name);
				FRICORE.availableStates.push(org);
				if(org.type != 0){
					//onSpecialState(org.type);
				}
				if(org.effect){
					effects.push(org);
				}
			}else{
				FrICORE.trace("不明なステートカード:" + i);
			}
			
			if(!org){
				org = {name:"不明なステートカード:"+i, id:i, description:"未定義のカードがイベントで通知されました。", type:0};
			}
		//	var el = _.template("<a title='<%=name%>\n<%=description%>' class='card list-card list-card-state list-card-state-type<%=type%>'><%=name%></a>", org);
			var el = _.template($("#state-template").html(), org);
			tr.find(".states").append($(el));
		});
	}
	
	if(data.reply && data.reply.state){
		console.log("WARN: リプライにステートカードが入っている");
	}
	//warn(formatDate(new Date()) + ": 新着イベントがあります。");

	//2020.11.20 重複メッセージの処理
	//tbl.append($(tr));//.attr('data-carddata', JSON.stringify(data)));
	if(data.id){
		var exists = $('#event-list tr[data-eventid=' + data.id + ']');
		if(exists && exists.length>0){
			FrICORE.trace("重複メッセージを破棄:" + data.message);return;//重複
		}else{
			tbl.append($(tr).clone());
		}
    }else{
    	FrICORE.trace("重複メッセージを破棄(網張り)" + data.message);return;//重複
    }
	//2020.11.20 重複メッセージの処理
	if(data.id){
		var exists = $('#wfhistory tr[data-eventid=' + data.id + ']');
		if(exists && exists.length>0){
			FrICORE.trace("重複メッセージを破棄(ファシリテータ画面):" + data.message);return;//重複
		}else{
			ftbl.append($(tr).clone());
		}
    }else{
    
    }

	$('#event-list .searchbox').quicksearch('#event-list table tbody tr');
	$('#event-list').find('.tablesorter').trigger('update');
	
	
	if(effects){
		effects.forEach(function(e){
			playEffect(e);
		});
	}
	
	refreshSystemView();
}



/*
//showWFHistoryとほぼ共通
//一覧更新用
function onLoadEvent2(data, showHidden){
	var effects = [];
	FRICORE.events.push(data);
//	if(!showHidden && data.level & 0xf0)	return;//演習中は非表示のイベント
	if(!showHidden && 
		(data.type == "null" || data.reply && data.reply.type=="null"))	
		return;//演習中は非表示のイベント
	if(data.reply && data.reply.type=="hidden")
		return;//空応答
    var ida = window.localStorage.getItem("kagi");//localStorageの中身を取得
    var hairetsu = JSON.parse(ida);//localStorageの中身を配列に変換
    if(!hairetsu){
    	hairetsu=[];
    }

    if(data.reply){
		var exists = $('#event-list tr[data-eventid=' + data.reply.id + ']');
		if(exists && exists.length>0){
			console.log("重複リプライメッセージを破棄:"+data.message);
			return;//重複
		}
	}
    //TODO
    if(data.id){
		var exists = $('#event-list tr[data-eventid=' + data.id + ']');
		if(exists && exists.length>0){
			console.log("重複メッセージを破棄:"+data.message);
			return;
		}
		//重複
	}
    //
	if(data.action && data.action.type == "alert"){
		warn("システムからの警告:" + data.message);
		return;
	}
	
	var tbl = $('#event-list').find('tbody');
	var ftbl = $('#wfhistory').find('tbody');
	var cclist = data.cc ? _.map(data.cc, function(e){return e.rolename;}).join(";") : "";

	var msg = compressMessage(data);
	var d = {date:formatDate(new Date(data.replyDate ? data.replyDate : data.sentDate)), 
			from: data.from.rolename, to: data.to.rolename, cc:cclist, 
		action:data.action, message:msg, id:data.id};

	d.title = escape(_.template("日時:<%=date%>\nFrom:<%=from%>\nTo:<%=to%>\n\n<%=action.name%>:<%=message%>", d));
	var e = _.template($('#eventlist-template').html(), d);
	var tr = $(e);
	if(data.replyTo){
		var r = summerizeMessage(data.replyTo);
		r  ="<br><span class='replyTo' title='"+summerizeMessage(data.replyTo)+"'>返信元:" + r + "</span>"
		$(tr).children("td").last().append(r);
	}

	if(data.from.email==FRICORE.usersession.userid){//fromが自分でない
		$(tr).children("td").first().append($('<span class="fa fa-level-up" style="color:blue" tytle="送信"></span>'));
		
	   }else{
		$(tr).children("td").first().append($('<span class="fa fa-level-down" style="color:green" title="受信"></span>'));
	}
	if(hairetsu.length == 0){
	}else{
		while (hairetsu.length>0){
		  var atai = hairetsu[0];
		  if(data.id==atai){
			  toggleMark(tr);
			}	
		
		  hairetsu.shift();
		}
	}
	var cards = [];
	if(data.action.attachments)	cards = _.union(cards, data.action.attachments);
	if(data.reply && data.reply.state)	cards.push(data.reply.state);
	if(data.action.statecards)	cards = _.union(cards,data.action.statecards);
	
	if(cards && cards.length != 0){//ステートカードGET
		_.each(cards, function(i){
			var org=_.findWhere(FRICORE.states, {id:i});
			var current=_.findWhere(FRICORE.availableStates, {id:i});
			if(!current){
				if(org){
					console.log('新しいステートカードを獲得:' + org.name);
					FRICORE.availableStates.push(org);
					if(org.type != 0){
						//onSpecialState(org.type);
					}
					if(org.effect){
						effects.push(org);
					}
				}else{
					console.log("不明なステートカード:" + i);
				}
			}
			if(!org){
				org = {name:"不明なステートカード:"+i, id:i, description:"未定義のカードがイベントで通知されました。", type:0};
			}
		//	var el = _.template("<a title='<%=name%>\n<%=description%>' class='card list-card list-card-state list-card-state-type<%=type%>'><%=name%></a>", org);
			var el = _.template($("#state-template").html(), org);
			tr.find(".states").append($(el));
		});
	}
	
	if(data.reply && data.reply.state){
		console.log("WARN: リプライにステートカードが入っている");
	}
	//warn(formatDate(new Date()) + ": 新着イベントがあります。");
//	tbl.append($(tr).attr('data-carddata', JSON.stringify(data)));

	tbl.append($(tr));//.attr('data-carddata', JSON.stringify(data)));
//	ftbl.append($(tr).clone().attr('data-carddata', JSON.stringify(data)));
	
	ftbl.append($(tr).clone());//.attr('data-carddata', JSON.stringify(data)));

	$('#event-list .searchbox').quicksearch('#event-list table tbody tr');
	$('#event-list').find('.tablesorter').trigger('update');
	
	if(effects){
		effects.forEach(function(e){
			playEffect(e);
		});
	}
}
*/
/**ステートカードの演出効果を再生*/
function playEffect(state){
	var scr = state.effect;
	if(!scr)return;
	if(scr.indexOf("href:")==0){
		var val = scr.substring("href:".length)
		doRequest(val, "GET", {},{dataType:"html"})
		.done(function(resp, msg, xhr){
			$("#effect-container").empty().append($(resp)).dialog({
				modal:true,
				title:"イベント発生",
				width:"40em"});
		})
		.fail(function(resp, xhr){
			error("演出効果の再生に失敗しました。");
		});
	}else if(scr.indexOf("javascript:") == 0){
		var val = scr.substring("javascript:".length);
		eval(val);
	}else if(scr.indexOf("html:") == 0){
		var val = scr.substring("html:".length);
		$("#effect-container").empty().append($(val)).dialog({
			modal:true,
			title:"イベントエフェクト",
			width:"40em"});
	}else return;
}
/**ステートカードの詳細を表示(未実装)*/
function showStateCard(ev){

	var stateid = $(ev).attr("data-stateid");
	if (stateid) { 
		var statecard = _.findWhere(FRICORE.states, { id: stateid });

		var d = JSON.parse(JSON.stringify(statecard));//deep copy

		d.addStateAction = _.filter(FRICORE.allActions,function(e){return e.addstate != null && e.addstate.indexOf(stateid) != -1;});
		d.removeStateAction = _.filter(FRICORE.allActions,function(e){return e.removestate != null && e.removestate.indexOf(stateid) != -1;});
		d.attachmentAction   = _.filter(FRICORE.allActions,function(e){return e.attachments != null && e.attachments.indexOf(stateid) != -1;});
		d.systemStateConditionAction   = _.filter(FRICORE.allActions,function(e){return e.systemstatecondition != null && e.systemstatecondition.indexOf(stateid) != -1;});

		d.addStateReply = _.filter(FRICORE.allReplies,function(e){return e.addstate != null && e.addstate.indexOf(stateid) != -1;});
		d.removeStateReply = _.filter(FRICORE.allReplies,function(e){return e.removestate != null && e.removestate.indexOf(stateid) != -1;});
		d.attachmentReply = _.filter(FRICORE.allReplies,function(e){return e.state != null && e.state.indexOf(stateid) != -1;});
		d.stateConditionReply = _.filter(FRICORE.allReplies,function(e){return e.statecondition != null && e.statecondition.indexOf(stateid) != -1;});

		var t = _.template($("#statecard-def-template").html(), d);
		$("#dialog-container").empty().append(t);
		$("#dialog-container").dialog({title:"ステートカード情報"});
	}
	
}
function showActionCard(ev){
	var dummy = 0;
}
function showReplyCard(ev){
	var dummy = 0;
}
function showRow(evt){
	showEventDetail(evt);
}
function showEventDetail(evt){
	
	var id = $(evt).data("eventid");
	var d = FRICORE.isadmin ? 
		_.findWhere(getSelectedTeamWorkflow().history, {id:id}) : 
		_.findWhere(FRICORE.events, {id:id});

	if(!d){FrICORE.error("!!no data binded.");return;}
	
	var sentDate = new Date(d.sentDate).toLocaleString();
	var replyDate = d.replyDate ? new Date(d.replyDate).toLocaleString() : "";
	var e = formatActionCard(d.action);
	$("#event-dialog").find(".event-action").empty().append(e);
	if(d.replyDate){
		$("#event-dialog").find(".event-sentdate").text(sentDate);
	}else{
		$("#event-dialog").find(".event-sentdate").text(sentDate);
	}
	var to = d.to.rolename + (d.to.email ? ("<"+d.to.email + ">") : "");
	$('#event-dialog').find(".event-to").text(to);
	
	var cc = [];
	if(d.cc){
		_.each(d.cc, function(cur){
			
			cc.push(cur.rolename + (cur.hasOwnProperty("email") ? ("<"+cur.email + ">") : ""));
		});
	}
	$("#event-dialog").find(".event-cc").text(cc.join(";"));
	
	var from = d.from ? (d.from.rolename + (d.from.email ? ("<"+d.from.email + ">") : "")) : "";
	$("#event-dialog").find(".event-from").text(from);
	
	var message = d.message|| d.action.message;
	
	if(d.reply)
		message  = d.reply.name + ("\n" + (d.reply.message || ""));
	$("#event-dialog").find(".event-comment textarea").val(message);
	$("#event-dialog").find(".event-comment textarea").attr('selectionEnd', 0).attr('selectionStart', 0);

	var cards = [];
	$("#event-dialog").find(".event-state").empty();
	if(d.action.attachments)	cards = _.union(cards, d.action.attachments);
	if(d.reply && d.reply.state)	cards.push(d.reply.state);
	if(d.action && d.action.statecards)	cards = _.union(cards, d.action.statecards);
	
	_.each(cards, function(sid){
		var statecard = _.findWhere(FRICORE.states, {id:sid});
		if(statecard){
			var el = _.template($("#state-template").html(), statecard);
			$("#event-dialog").find(".event-state").append($(el));
		}else{
			FrICORE.trace("statecard not owned.id:" + sid);
		}
	});
	
	var wf = FRICORE.workflowstate;
	if(FRICORE.isadmin){
		var team = getSelectedTeam();
		wf = _.find(FRICORE.wfprocesses, function(e){return e.team == team;});
	}
	var pointkeys = _.keys(wf.pointchest);
	_.each(pointkeys, function(k){
		if(k==d.id){
			var pointdata = wf.pointchest[d.id];//array
			_.each(pointdata, function(p){
				var pe = _.template("<span class='card list-card list-card-point' title='<%=description%>'><%=name%>: <%=point%>点</span>", p);
				$("#event-dialog").find(".event-state").append($(pe));
			})
		}
	});
	

	var dlg =$('#event-dialog').dialog({modal:true,title:"イベント",width:"60em", buttons:{
		"閉じる": function(){
			$(this).dialog('close');
		}
	}});
}
function updateLoginInfo(){
	var connected = false;
	if(FRICORE.socket && FRICORE.socket.readyState == 1){
		connected = true;
	}
	var info = FRICORE.usersession ? (FRICORE.usersession.username+ "("+FRICORE.usersession.userid+")" ) : "ログインしていません。";
	document.title = [(window.versioninfo.CAPTION||""), info].join("--");
	if(connected){
		$("#loginuser").html(info);
	}else{
		$("#loginuser").html("ログインしていません。");
	}
}
function renderPresence(presence){
	$("#contacts .panelcontent").empty();
	if(!presence.member)return;
	FRICORE.presence = presence;
	FRICORE.presence.member.forEach(function(c){
		var self = c.email == FRICORE.usersession.username;
		var online = c.online;
		var system = c.system;
		var t = _.template($("#presence-template").html(), c);
		var m = $(t).data('card', c).on('click', function(e){
			onCardClicked(e);
		});
		
		$("#contacts .panelcontent").append(m);
		var row = $(".panelcontent [data-userid='"+c.email+"']");
		if(online){
			row.children(".online").addClass('ui-icon ui-icon-signal-diag presence-online').removeClass('ui-icon-alert presence-offline');
			row.children(".online").attr("title", "ユーザはオンラインです。");
		}else{
			row.children(".online").removeClass('ui-icon-signal-diag presence-online').addClass('ui-icon ui-icon-alert presence-offline');
			row.children(".online").attr("title", "ユーザはオフラインです。");
		}
		if(self){
			row.children(".self").addClass('ui-icon ui-icon-person presence-self');
			row.children(".self").attr("title", "ログイン中のユーザです。");
		}else{
			row.children(".self").removeClass('ui-icon ui-icon-person presence-self');
			row.children(".self").attr("title", "");
		}
		if(system){
			row.children(".system").addClass('ui-icon ui-icon-gear presence-system');
			row.children(".system").attr("title", "システムが自動応答します。");
		}else{
			row.children(".system").removeClass('ui-icon ui-icon-gear  presence-system');
			row.children(".system").attr("title",""); 
		}
	});
}

///////////////////////////////////////////////////////////



/**ファシリテータ画面:選択されたワークフロープロセスの概要を更新*/
function showWFStatus(){
	if(!FRICORE.wfsession){error("ログオンしていません。");return;}

	if(!isOnline()){
		FrICORE.info("not online.");
		return;
	}
	
	var team = getSelectedTeam();
	if(!team)return;

	var m = $('.flowstatus .message');
	m.empty();
	var pid = getSelectedProcess();
	var stat = null;
	var url = 	"workflow/diag/"+team+"/workflow";
	doRequest(url, "GET", {})
	.done(function(resp, msg, xhr){
		stat = resp;
		var phasedef = _.where(window.FRICORE.setting.phases, {phase:stat.phase});
		var str = _.template("<%=team%>; フェーズ:<%=phase>=0?phase:'未開始'%>; 状態:<%=state%>; 開始日時:<%=hasOwnProperty('start')?formatDate(new Date(start)):''%>; スコア:<%=score%>", stat);
		m.append("<span>"+str + "</span><br>");
		_.each(window.FRICORE.wfprocesses, function(e){
			if(e && e.team == team)
				e = resp;
		}, this);

	})
	.fail(function(xhr){
		var str = xhr.responseText;
		if(xhr.responseJSON){
			str = xhr.responseJSON.message;
		}
		m.append("<span>"+str + "</span><br>");
		FrICORE.error("ワークフロー状態の参照に失敗しました。", xhr);
	});
	
	url = "workflow/process/cards";
	doRequest(url, "GET", {all:true})
	.done(function(resp, msg, xhr){
		var c = $("#wfstatus").find(".content .actions").find("tbody");
		c.empty();
		var phase = _.find(FRICORE.wfprocesses, function(t){return t.team == team;}).phase;
		
		var inf = c.parents('div .ui-accordion-content').children('.empty');
		if(!resp || resp.length == 0){	inf.html("表示情報がありません。");}else{inf.html('');}
		FRICORE.allActions = [];
		resp.forEach(function(e){
			//action.phaseの省略値を「全フェーズで有効」に変更
			e.available = e.phase ? (e.phase.indexOf(phase) != -1) : true ;
			window.FRICORE.allActions.push(e);
			
			if(e.type == "auto"){
				if(getSelectedTeamWorkflow().autoActionHistory.hasOwnProperty(e.id)){
					var fired  = getSelectedTeamWorkflow().autoActionHistory[e.id];
					e.fired = formatDate(new Date(fired));
				}

			}
			var el = _.template($('#cardlist-template').html(), e);
			if(!el){
				var dummy = 0;
			}
			c.append(el);
		});
	})
	.fail(function(xhr){
		var str = xhr.responseText;
		if(xhr.responseJSON)	str = xhr.responseJSON.message;
		m.append("<span>アクションカードの状態が不明です。" + str + "</span><br>");
		FrICORE.error("ワークフロー状態の参照に失敗しました。", xhr);
	});

	var url = 	"workflow/contacts/"+team;
	doRequest(url, "GET", {"presence":true})
	.done(function(resp, msg, xhr){
		var phase = _.find(FRICORE.wfprocesses, function(t){return t.team == team;}).phase;
		
		var c = $("#wfstatus").find(".content .members").find("tbody");
		c.empty();
		
		var inf = c.parents('div .ui-accordion-content').children('.empty');
		if(!resp || resp.length == 0){
			inf.html("表示情報がありません。");
		}else{
			inf.html('');
			resp.forEach(function(e){
				e.phase = phase;
				e.issystem = false;
				if(e.system == null || e.system.length == 0){
					e.systemphase = '';
				}else{
					var f = [];
					_.each(e.system,function(s, index){
						if(s) f.push(index + 1);
					});
					e.systemphase = f;
					if(f.indexOf(phase) != -1)
						e.issystem = true;
				}
				var el = _.template($('#contact-template').html(), e);
				var a = c.append(el);
	
			});
		}
		$('.member-online td:first-child').append("<span class='icon-online fa fa-wifi' title='オンライン'></span>");
		$('.member-offline td:first-child').append("<span class='icon-offline fa fa-times-circle' title='オフライン'></span>");
		$('.member-system td:first-child').append("<span class='icon-system fa fa-user-o'  title='自動応答ユーザ'></span>");
		$('.member-manual td:first-child').append("<span class='icon-system fa fa-user' title='参加者'></span>");
	})
	.fail(function(xhr){
		var str = xhr.responseText;
		if(xhr.responseJSON)	str = xhr.responseJSON.message;
		m.append("<span>メンバーの状態が不明です。" + str + "</span>");
		FrICORE.error("メンバー状態の参照に失敗しました。", xhr);
	});
	getState(team);
	
	var url = 	"workflow/diag/reply/";
	var params = (stat != null && stat.phase > 0) ? {phase:stat.phase} : {phase:-1}
	doRequest(url, "GET", params)
	.done(function(resp, msg, xhr){
		var c = $("#wfstatus").find(".content .reply").find("tbody");
		c.empty();
		
		if(resp){
			window.FRICORE.allReplies = resp;
			var inf = c.parents('div .ui-accordion-content').children('.empty');
			if(resp.length == 0){	inf.html("表示情報がありません。");}else{
				inf.html('');
				resp.forEach(function(e){
					var el = _.template($("#reply-template").html(),e);
					c.append(el);
				});
			}
		}
	})
	.fail(function(xhr){
		var str = xhr.responseText;
		if(xhr.responseJSON)	str = xhr.responseJSON.message;
		m.append("<span>応答カードが取得できません。" + str + "</span>");
		FrICORE.error("リプライデータの参照に失敗しました。", xhr);
	});
	
	//ポイントカードを取得
	var url = 	"workflow/diag/point/";
	doRequest(url, "GET", params)
	.done(function(resp, msg, xhr){
		var c = $("#wfstatus").find(".content .point").find("tbody");
		c.empty();
		
		if(resp){
			var inf = c.parents('div .ui-accordion-content').children('.empty');
			if(resp.length == 0){	inf.html("表示情報がありません。");}else{
				inf.html('');
				resp.forEach(function(e){
					var el = _.template("<tr><td><%=id%></td><td><%=name%> <%=description%></td><td><%=point%></td><td><%=state?state:''%></td><td><%=statecondition?statecondition : ''%></td><td><%=phase <=0 ? 'すべて': phase%></td><td><%=after == 0 ? '' : after%>~<%=before == 0 ? '' : before%></td></tr>",e);
					var e =c.append(el);
					
					var wf = _.find(FRICORE.wfprocesses, function(e){return e.team == team;});
					_.each(_.keys(wf.pointchest), function(k){
						var event = _.find(wf.history, function(ev){return ev.id == k;});
						//ポイントが発行されたイベント
						
					});
				});
			}
		}
	})
	.fail(function(xhr){
		var str = xhr.responseText;
		if(xhr.responseJSON)	str = xhr.responseJSON.message;
		m.append("<span>応答カードが取得できません。" + str + "</span>");
		FrICORE.error("リプライデータの参照に失敗しました。", xhr);
	});
	
	updateui();
}
/**シーケンス図のメッセージ行を作成(イベントデータを要約)*/
function summerizeDiagramLine(data){
	var msg = compressMessage(data);
	var d = {date:formatDate(new Date(data.replyDate ? data.replyDate : data.sentDate)),// from: data.from.rolename, to: data.to.rolename, 
		action:data.action.name, message:msg};
	var line =  _.template("<%=message%>", d);
	if(data.replyTo){
		var r = "(返信元:" + summerizeMessage(data.replyTo) + ")";
		line += r;
	}
	var full = escape4vga(line.replace(/\r?\n/g, " "));

	return full;
}

/**イベントデータに添付されたステートカードを取得*/
 function extractStateCard(data, systemstate){
		var cards = [],addstate=[],removestate=[];
		if(data.action.attachments)	cards = _.union(cards, data.action.attachments);
		if(data.action.statecards)	cards = _.union(cards, data.action.statecards);
		if(data.reply && data.reply.state)	cards.push(data.reply.state);

		if(data.reply && data.reply.addstate)
			addstate.push(data.reply.addstate);
		if(data.reply && data.reply.removestate)
			removestate.push(data.reply.removestate);
		
		var ret = [];
		if(cards && cards.length != 0){//ステートカードGET
			_.each(cards, function(el){
				var s = _.findWhere(FRICORE.states, {id:el});
				if(s){
					ret.push('['+s.name+']');
				}else{
					ret.push('['+el+']')
				}
			});
		}
		
		_.each(addstate, function(el){
			var s = _.findWhere(FRICORE.states, {id:el});
			if(s){
				ret.push('+[' + s.name + ']');
			}else{
				ret.push('+['+el+']')
			}
		});
		_.each(removestate, function(el){
			var s = _.findWhere(FRICORE.states, {id:el});
			if(s){
				ret.push('-[' + s.name + ']');
			}else{
				ret.push('-['+el+']')
			}
		});
		return ret.join(' ');
 }

 /**イベントデータに紐づいたポイントカードを取得*/
 function extractPointCard(data){
	var wf = getSelectedTeamWorkflow()||FRICORE.workflowstate;

	var p = wf.pointchest ? wf.pointchest[data.id] : [];
	var names = _.map(p, function(e){return e.name;});
 	return names;
}
 function escape4svg(str){
	 if(!str)return str;
	 var s = str.replace(/\-/g,'_').replace(/\//g,'／');
	 return s;
 }
 /**シーケンス図のイベント行を作成*/
function makeDiagramLine(hist){
	var toIsVisible = validateRoleFilter(hist.to.role) ;
	var fromIsVisible = validateRoleFilter(hist.from.role);
	if(!toIsVisible || !fromIsVisible){
		try{
			FrICORE.trace("イベント表示がフィルタされました。" + hist.from.role + "->"+hist.to.role);
		}catch(t){}
		return null;
	}
	
	var statecards = extractStateCard(hist)||'';
 	var pointcards = extractPointCard(hist)||'';
 	
	var when = formatDate(hist.replyDate ? hist.replyDate : hist.sentDate);
	var online = $("#hide-detail").is(":checked");
	var msg = summerizeDiagramLine(hist);
	if(!hist.from || !hist.to){
		FrICORE.error("DANGER!");
	}
	var data = {from:escape4vga(hist.from.rolename),to:escape4vga(hist.to.rolename),sentDate:when,action:escape4vga(hist.action.name),message:msg, inf:statecards,statecards:statecards,pointcards:pointcards,id:hist.id};
	var ccarr=[];
	if(hist.cc){
		_.each(hist.cc, function(e){
			ccarr.push(escape4vga(e.rolename));
		});
		data.cc=ccarr;
	}
	var seq = [];
	if(hist.action.type=="notification" && hist.to.role == "all"){
		data.from="<PH_FROM>";//仮にプレースホルダを設定しておき、描画直前に変更する
		data.to="<PH_TO>";
		
		var str = online ?
				_.template('Note over <%=from%>,<%=to%>: {"date":"<%=sentDate%>","message":"<%=action%>","title":"<%=message%><wbr/><%=inf%>","statecards":"<%=statecards%>","pointcards":"<%=pointcards%>","id":"<%=id%>"}', data):
				_.template('Note over <%=from%>,<%=to%>: <%=sentDate%> <%=action%>:<%=message%> <br/><%=inf%>', data);
		seq.push(str);
	}else{
		var str = online ? 
				_.template('<%=from%>->><%=to%>:{"date":"<%=sentDate%>","message":"<%=action%>","title":"<%=message%><wbr/><%=inf%>","statecards":"<%=statecards%>","pointcards":"<%=pointcards%>","id":"<%=id%>"}', data) : 
				_.template('<%=from%>->><%=to%>:<%=sentDate%> <%=action%>:<%=message%> <br/><%=inf%>', data);
		seq.push(str);
		if(data.cc){
			_.each(data.cc,function(e){
				var s = online ?
					_.template('<%=from%>-->>{PF}:{"date":"<%=sentDate%>", "message":"<%=action%>","title":"<%=message%><wbr/><%=inf%>","statecards":"<%=statecards%>","pointcards":"<%=pointcards%>","id":"<%=id%>"}'.replace("{PF}",e), data) : 
					_.template('<%=from%>-->>{PF}:<%=sentDate%> <%=action%>:<%=message%> <br/><%=inf%>'.replace("{PF}",e), data);
				seq.push(s);
			});
		}
	}
	return seq;
}
 /**
 シーケンス図のフィルタ設定を初期化して表示/非表示を切り替えます。
 */
 function showFilter(){
	var members = FRICORE.members;
	var container = $('#sequence-filter');
	var roles = _.union([{role:"all",rolename:"チーム全員"}/*,{role:"system",rolename:"システム"}*/],members);
	
	_.each(roles, function(m){
		if($("#filter-role-"+m.role).length != 0) return;
		var tmpl = "<span class='filter-item'><label for = 'filter-role-<%=role%>'><%=rolename%></label><input type='checkbox' id='filter-role-<%=role%>' checked /></span>";
		var cb = $(_.template(tmpl, m));
		$('#sequence-role-filter').append(cb);
		var cls = isSystemUser(m, 0) ? "member-system" : "member-manual member-online";
		cb.next("span").addClass(cls);
	});
	
	if(container.is(':visible')){
		container.prev("button").text('絞り込み').attr('title', '絞り込み表示条件を設定項目を表示します。');
		container.hide();
	}else{
		container.prev("button").text('絞り込み条件を非表示').attr('title', '絞り込み表示条件を非表示にします。');
		container.show();
	}
 }
 function checkAllFilter(show){
	$('#sequence-role-filter>>input[type=checkbox]').prop('checked', show);
 }
 function applyFilter(){
	 showWFHistory(null, true);
 }
 /**フィルタ設定を評価し、ロールが表示対象ならtrueを返します。all/systemは常にtrueを返します。*/
 function validateRoleFilter(role){
	 
	 if(role == "system") return true;
	 
	 var id = "#filter-role-" + role;
	 
	 if($(id).length == 0)return false;
	 if($(id).is(':checked'))return true;
	 return false;
 }
 function validateEventFilter(ev){
	if(isVisibleEvent(ev))
		return true;

	return false;
	
 } 
 function isVisibleEvent(ev){
	 return true;
 }
 
/**ファシリテータ/参加者兼用: 表示用の履歴データを作成
 *@param renderDiagram{boolean} シーケンス図表示用にデータを加工 
 */
function processWFHistory(resp, renderDiagram){
	var seq = [];
	var c = $("#wfhistory").find(".content").find("tbody");
	c.empty();

	var msg = !resp || resp.length == 0?'情報がありません。':resp.length+'個のイベントがあります。';
	$('#wfhistory').children('.content').children('.empty').html(msg);

	_.each(resp, function(e){
		var data = e;

		if(data.reply && (data.reply.type=="hidden" || data.reply.type=="null"))
			return;//空応答

		if(data.id){
			var exists = $('#wfhistory tr[data-eventid=' + data.id + ']');
			if(exists && exists.length>0){	return;//重複
				FrICORE.trace("重複メッセージを破棄：" + data.message);
			}
		}
		
		if(data.reply){
			var exists = $('#wfhistory tr[data-eventid=' + data.reply.id + ']');
			if(exists && exists.length>0){	return;//重複
				FrICORE.trace("重複メッセージ(リプライ)を破棄：" + data.message);
			}
		}
		
		var tbl = $('#wfhistory').find('tbody');
		var cclist = data.cc ? _.map(data.cc, function(e){return e.rolename;}).join(";") : "";

		var msg = compressMessage(data);
		var d = {date:formatDate(new Date(data.sentDate)), from: data.from.rolename, to: data.to.rolename, cc:cclist, 
			action:data.action, message:msg, id:data.id};
		d.title = escape(_.template("日時:<%=date%>\nFrom:<%=from%>\nTo:<%=to%>\n\n<%=action.name%>:<%=message%>", d));
		var e = _.template($('#eventlist-template').html(), d);
		var tr = $(e);
		
		if(data.replyTo){
			var r = summerizeMessage(data.replyTo);
			r  ="<br><span class='replyTo' title='"+formatMessage(data.replyTo)+"'><wbr/>返信元:" + r + "</span>"
			$(tr).children("td").last().append(r);
		}

		var cards = [];
		if(data.action.attachments)	cards = _.union(cards, data.action.attachments);
		if(data.reply && data.reply.state)	cards.push(data.reply.state);
		if(data.action.statecards)	cards = _.union(cards, data.action.statecards);
		if(cards){//ステートカードGET
			_.each(cards, function(i){
				var org=_.findWhere(FRICORE.states, {id:i});
				if(org){
				}else{
					org = {name:"不明なステートカード:"+i, id:i, description:"未定義のカードがイベントで通知されました。", type:0};
				}
				var el = _.template($("#state-template").html(), org);
				tr.find(".states").append($(el));
			});
		}

		tbl.append($(tr));
		
		var wf = FRICORE.workflowstate;
		if(FRICORE.usersession.isadmin){
			var team = getSelectedTeam();
			wf = _.find(FRICORE.wfprocesses, function(e){return e.team == team;});
		}
		
		var pointkeys = _.keys(wf.pointchest);
		_.each(pointkeys, function(k){
			if(k==data.id){
				var pointdata = wf.pointchest[data.id];//array
				_.each(pointdata, function(p){
					var pe = _.template("<span class='card list-card list-card-point' title='<%=description%>'><%=name%>: <%=point%>点</span>", p);
					tr.find('.states').append($(pe));
				})
				FrICORE.info("got a point card." + data);
			}
		});
		
		if(renderDiagram){
			if(!validateEventFilter(data))	return;
			
			var arr = makeDiagramLine(data);
			if(arr)
				seq = _.union(seq, arr);
			
		}
	});
	if(renderDiagram){drawDiagram(seq, resp);}
	
	$("#wfhistory").find('.tablesorter').trigger('update');
}

/**ファシリテータ画面:イベント履歴データを取得
 * @param {boolean} renderDiagram シーケンス図表示用にデータを加工
*/
function showWFHistory(teamname, renderDiagram){

	var selected = getSelectedTeam();//$('#teampicker').val();
	var team = teamname  ? teamname : selected;
	
	if(!team && FRICORE.usersession){
		team = FRICORE.usersession.team;
	}
	$('#wfhistory .content tbody').empty();
	var url = 	"workflow/diag/"+team+"/history";
	doRequest(url, "GET", {})
	.done(function(resp, msg, xhr){

		var filterIsVisible = $('#sequence-filter').is(":visible");//フィルタ表示状態がクリアされるため
		processWFHistory(resp, renderDiagram);
		if(filterIsVisible)
			$('#sequence-filter').show();

		FRICORE.events = resp;
		
		setContextMenu($('#wfhistory'), true);
	})
	.fail(function(xhr){
		var str = xhr.responseText || xhr.statusText;
		if(xhr.responseJSON){str = xhr.responseJSON.message;}
		error("履歴の取得に失敗しました。" + str);
		FrICORE.error("履歴の参照に失敗しました。", xhr);
	});
}
/**一覧から選択されているチームのワークフロー情報を取得し、表示領域に表示する。
 * 一覧未選択ならエラー。
 * @param{string} url. この相対URLに対してGETリクエストを送信する。url中の"{team}"はチーム名に置換される。
 @param {string} selector 表示領域tableのセレクタ。
 @done {function} リクエスト成功時のハンドラ。XHR.done(response, message, xhr)から呼び出される。
 @failed {function} リクエスト失敗時のハンドラ。XHR.fail(xhr)から呼び出される。
 */
function showWFSubpanel(urltmpl, selector, done, failed){

	if(!selector || $(selector).length == 0)return;
	
	var c = $(selector).find(".content").find("tbody");
	c.empty();
	
	var team = getSelectedTeam();
	if(!team){
		$(selector).children('.content').children('.empty').html('演習チームが選択されていません。');	return;
	}
	var url = urltmpl.replace(/{team}/g, team);
	doRequest(url, "GET", {})
	.done(function(resp, msg, xhr){
		var msg = !resp || resp.length == 0?'データがありません。':resp.length+'個のデータがあります。';
		$(selector).children('.content').children('.empty').html(msg);
		if(done)	done(resp, msg, xhr);
	})
	.fail(function(xhr){
		var str = xhr.responseText || xhr.statusText;
		if(xhr.responseJSON){str = xhr.responseJSON.message;}
		FrICORE.error("シナリオ状態の参照に失敗しました。", xhr);
		$(selector).children('.content').children('.empty').html('データが取得できません。' + str);	return;

		if(failed)	failed(xhr, str);
	});
}

/**ファシリテータ画面: キュー一覧を更新*/
function showWFQueue(){
	showWFSubpanel('workflow/diag/{team}/action', '#wfqueue', 
		function(resp, msg, xhr){	_.each(resp, function(e){
				var data ={sentDate:formatDate(new Date(e.sentDate)),message:e.message,action:e.action.name,from:e.from, to:e.to, inf:e.reply||""};
				var l = _.template("<tr><td><%=sentDate%></td><td><%=action%></td><td><%=message%></td><td><%=from.rolename%></td><td><%=to.rolename%></td><td><%=inf%></td></tr>", data);
				$("#wfqueue .content tbody").append(l);			
			});
		},
		function(xhr, str){
			error("キューの取得に失敗しました。", xhr);
		}
	);
}

/**フェーズが開始されているか()*/
function isActive(){
	var wf = getSelectedTeamWorkflow();
	if(!wf) return false;
	return wf.state != "None";
}
/**ログイン中かどうか*/
function isOnline(){
	return FRICORE.wfsession ? true : false;
}
/**ファシリテータ画面:トリガ一覧を更新*/
function showWFTrigger(){

	if(!isOnline()){
		$('#wftrigger .empty').show();
		return;
	}
	
	showWFSubpanel('workflow/diag/{team}/trigger', '#wftrigger', 
		function(resp, msg, xhr){
			_.each(resp, function(e){
				var wf = getSelectedTeamWorkflow();
				var started = wf ? wf.start : 0;
				var estim = new Date(started + e.elapsed*1000);
				var data ={elapsed:e.elapsed,estim:estim, fireWhen:formatDate(new Date(e.fireWhen)),message:e.message,name:e.name, state:e.state,from:e.from, to:e.to, statecondition:e.statecondition};
				var l = _.template($("#trigger-template").html(), data);
				$("#wftrigger .content tbody").append(l);	
			});
		},
		function(xhr, str){
			//error("トリガの取得に失敗しました。"+str);
		}
	);
}
/**ファシリテータ画面:チーム状態を更新*/
function updateWF(){
	var team = getSelectedTeam();
	if(!team)return;
	showWFStatus();
	showWFQueue();
	showWFHistory();
	showWFTrigger();

	if(getSelectedTeamWorkflow().state != 'None')
		onOnline();
}
/**ファシリテータ画面:ワークフロープロセス一覧から項目を選択*/
function selectProcess(teamname){
	var list = $("#processlist").find(".listitem-selectable");
	_.each(list, function(e){
		if($(e).find('.assign-team').text() == teamname){
			$("#processlist").find(".listitem-selectable").removeClass('selected');
			$(e).addClass('selected');
		}
	});
	updateWF();
}
/**ファシリテータから参加者へのメッセージ送信*/
function sendMessage(msg, to, cc, actionid){
	var url = "workflow/process/act";
	var action = actionid || "i0001";//話し合い
	var message = $(".set-comment>textarea").val();
	
	var data = {card:$.extend(_.findWhere(FRICORE.actions, {id:action})),
			to:_.findWhere(FRICORE.members, {role:to})};

	doRequest(url, "POST", JSON.stringify({action:data}), {contentType:"application/json"})
	.done(function(resp, msg, xhr){
		FrICORE.trace("done:action "+resp);
	})
	.fail(function(xhr){
		var msg = xhr.responseJSON ? xhr.responseJSON : xhr.responseText;
		error(msg, xhr);
	});
}

/**プロセス一覧から選択してプロセス情報を更新する
 * @param{object} evt プロセス一覧の項目セレクタ
 */
function onSelectProcess(evt){
	 
	 if(window.event && window.event.target.tagName != "TD")return; 
	$("#processlist").find(".listitem-selectable").removeClass('selected');
	$(evt).addClass('selected');
	updateWF();
}
/**
 *チーム一覧プルダウンが表示中なら、選択されたチーム名を返す
 * ワークフロー一覧が表示中なら、選択されたワークフローの割当先チーム名を返す
 */
function getSelectedTeam(){
	if($('#teampicker').is(':visible')){
		if($('#teampicker').val())
			return $('#teampicker').val();
	}
	
	var selected = $("#processlist").find('.selected');
	if(!selected){alert("演習シナリオを選択してください。");return null;}
	var team = $(selected).find(".assign-team").text();
	return team;
}
/**一覧から選択されたワークフロープロセスのプロセスIDを取得する*/
function getSelectedProcess(){
	var selected = $("#processlist").find('.selected');
	if(!selected){alert("演習シナリオを選択してください。");return null;}
	var pid = $(selected).find(".process-id").text();
	return pid;
}
function getTeamWorkflow(team){
	var wf = _.find(FRICORE.wfprocesses,function(e){return e.team==team;});
	return wf ||{};
}
function getSelectedTeamWorkflow(){
	return getTeamWorkflow(getSelectedTeam());
}



function toggle(selector){
	$(selector).toggle();
}

function showNotificationDialog(to){
	showActionDialog('injection', null, to);
}








/////////////////////////////////////////////////////////////
//ヘルプ情報関連

function getAllActionCards(){
	var pid = FRICORE.workflowstate ? FRICORE.workflowstate.pid : 0;
	var url = "workflow/process/cards";
	return doRequest(url, "GET", {all:true})
	.done(function(resp, msg, xhr){
		FRICORE.allActions =resp;
	})
	.fail(function(xhr){
		var str = xhr.responseText;
		if(xhr.responseJSON)	str = xhr.responseJSON.message;
		FrICORE.error(xhr.responseText);
	});
}
var helpwindow = null;

function getHelpContent(){
	if(!FRICORE.usersession){
		error("ログインしてください。");return;
	}

 	var holders = [];
 	getAllActionCards().done(function(){
		var acts = FRICORE.allActions;
		_.each(acts, function(a){
			var data = {name:a.name||"N/A", desc:a.description||"", holder:"",id:a.id};
			if(_.contains(a.roles,"all")){
				data.holder="すべてのユーザ";				
			}else{
				var holder = _.filter(FRICORE.members, function(m){
					if(_.contains(a.roles, m.role))
						return true;
					else	return false;
				});
				data.holder=_.map(holder, function(e){return e.rolename;});
			}
			holders.push(data);
		});
		var buff=[];
		_.each(holders, function(d){
			if(!_.contains(window.FRICORE.setting["invisible-action"], d.id)){
				var data = {name:d.name,desc:(d.desc||'').replace(/\r?\n/g,'<br>'), holder:d.holder};
				line = _.template("<tr class='holderinfo'><td><%=name%></td><td><%=desc%></td><td><%=holder%></td><td><%=id%></td></tr>", d);
				buff.push(line);
			}
		});

		$('#help-actions').empty();
		$('#help-actions').append(buff.join("\n"));
		

		$('#help-roles').empty();
		_.each(FRICORE.members, function(role){
			var data = {name:role.name, desc:role.desc?role.desc.replace(/\r?\n/g, '<br>'):"",system:(role.system ? role.system[0] : false)};
			var tr = _.template("<tr><td><%=name%></td><td><%=desc%></td><td><%=system?'はい':'いいえ'%></td><td></td></tr>", data);
			if(data.system){$(tr).addClass('system-role');}
			$('#help-roles').append(tr);
		});

		
		$('#help .searchbox').quicksearch('#help table tbody tr');
		
		$('#helpcontent').find('.tablesorter').trigger('update');
		$('#helpcontent').dialog({
			modal:false,
			title:"アクションと役割",
			height:"500",
			width:"800", 
			});	
	});
}
/**アクションの種類のラベルを返す
 * @param actionオブジェクト
 * */
function actionType(a){
	if("i0000"==a.id){return "トリガー";}
	return {"action":"通常","notification":"通知","auto":"自動", "talk":"話し合い", "share":"連絡/報告"}[a] || ("不明な種類:" + a);
}
 
/**リストをコンパクト表示*/
function setCompact(selector){
	$(selector + " tr").css("max-height", "1.5em").css("display","block").on("click",function(){
		$(selector + " tr").css("max-height", "1.5em").css("display","block");
		$(this).css("max-height", "none").css("height", "auto").css("display","table-row");
	});
}
function setFull(selector){
	$(selector + " tr").css("max-height", "none").css("height", "auto").css("display","table-row");

}
function escape4vga(str){
	var ret = (str || "");
	return ret.replace(/\-/g,'_').replace(/\//g, ' ');
}
function refreshResource(){
	doRequest("workflow/resource/result","GET")
	.then(function(resp, msg, xhr){
		$("#resource-picker").empty();
		_.each(resp, function(e){
			var el = _.template("<option value = '<%=name%>'><%=name%></option>", e);
			$("#resource-picker").append($(el));
		});
	})
	.fail(function(xhr){
		error("失敗しました。", xhr);
	});
}

function loadResource(){
	var name = $("#resource-picker").val();
	if(!name){warn("no resource specified.");return;}
	
	doRequest("workflow/resource/result/"+name,"GET")
	.then(function(resp, msg, xhr){
		var data = resp.sequence;
		$("#chartdef").val(data);
		render();
	})
	.fail(function(xhr){
		error("失敗しました。", xhr);
	});
}

function storeResource(){
	var seq = $("#chartdef").val();
	var wf= getSelectedTeamWorkflow();

	var data = {team:wf.team,	date: wf.start, scenario:FRICORE.scenario[FRICORE.activeScenario],sequence:seq};
	var name = data.scenario.name  + formatDateForFilename(new Date(data.date));
	doRequest("workflow/resource/result/"+name,"POST", JSON.stringify(data), {processData:false, dataType:'json',contentType:"application/json"})
	.then(function(resp, msg, xhr){
		warn("アップロードしました。");
		refreshResource();
	})
	.fail(function(xhr){
		error("アップロードに失敗しました。", xhr);
	});

}
function formatDateForFilename(date){
    return ("0000"+String(date.getFullYear())).slice(-4) +
    '-' + ("00"+String(date.getMonth() + 1)).slice(-2) +
    '-' + ("00"+String(date.getDate())).slice(-2) +
    '-' + ("00"+String(date.getHours())).slice(-2) +
    '-' + ("00"+String(date.getMinutes())).slice(-2);
}
function changeTheme(name){
	
	$(".theme-custom").prop("disabled", true);
	$("[data-checkgroup=skin]").prop("disabled", "true");
	
	if(!name){
		FrICORE.info("既定のスキンを選択しました。");
	}
	if(name && $(name).length > 0){
		$(name).prop("disabled", false);
		//TODO:対応するメニュー項目を変更
		//$("[data-checkgroup=skin]").prop("disabled", "true");

	}else{
		FrICORE.warn("スキンが見つかりません:"+name);
	}
};
function isOnline(){
	return FRICORE.socket && FRICORE.socket.readyState == WebSocket.OPEN
}

function digestText(txt){
	if(!txt)	return txt;
	
	var t = txt;
	if(typeof(txt) == "string")
		t = txt.split("\n").slice(0, FrICORE.MAX_LOG_LINES);
	else
		t = xhrToString(txt);
	if(t.length > FrICORE.MAX_LOG_CHARS){
		t = t.substring(0, FrICORE.MAX_LOG_CHARS - 3) + "...";
	}
	return t;
	
}
function xhrToString(err){
	if(!err)
		return "";
	else if(err instanceof String)
		ret =  err;
	else if(err.hasOwnProperty("responseJSON") && err.responseJSON){//恐らくXHR、サーバ側でハンドルされている例外
		var json = err.responseJSON;
		ret =  [err.status, json.kind, json.message].join(" ");
		return ret;
	}else if(err.hasOwnProperty("responseText") && err.responseText){//恐らくXHR、サーバ側でハンドルされていない例外
		ret =  [err.statusText, err.responseURL || "<url unknown>", (err.responseText||"")].join(" ");
		return ret;
	}else if(err.hasOwnProperty("statusText") && err.statusText){//恐らくXHR、クライアント側の例外
		return [err.status, err.statusText].join(" ");
	}else if(err instanceof Error){
		ret = err.name + " " + err.message + ": " + err.stack;
		return ret;	
	}else if(!err)
		return "";
	else
		return err.toString();
}


//experimental: システムビュー
function showSystemView(){
	var c = $("#systemview-container");
	c.dialog({
		position:{my:"right bottom", at:"right bottom"},
		height: 600,width:800,
	}).on("dialogresize",(e)=>{svgFitToContainer("#systemview-container svg");});
	var s = $("#systemview-placeholder");
	if(s.children("svg").length == 0){
		s.load("systemview.svg", null,
			(e)=>{svgFitToContainer("#systemview-container svg");refreshSystemView();});
	}
}
function refreshSystemView(){
	
	//ユーザステートとシステムステートをスキャンし、FRICORE.markerMappingに定義されたセレクタとマッチする要素にスタイルを適用する

	$("[data-imane-device]").removeClass("data-imane-status-quarantined").
		removeClass("data-imane-status-infected").
		removeClass("data-imane-status-compromised").
		removeClass("data-imane-status-vulnerable").
		removeClass("data-imane-status-normal");

	//tooltipのイベントハンドラをリセット
	$("#systemview-tooltip").text("");
	$("[data-imane-device]").on("click", null);

	
	setDeviceMarker("data-imane-status-normal", "HEA");
	setDeviceMarker("data-imane-status-vulnerable", "VUL");
	setDeviceMarker("data-imane-status-compromised", "ATK");
	setDeviceMarker("data-imane-status-infected", "INF");
	setDeviceMarker("data-imane-status-quarantined", "CON");
	

/*//TODO: ユーザステートを反映	
	var userStates = FRICORE.availableStates;
	var history = FRICORE.events;
	var attachedEvents = _.filter(FRICORE.events, (e)=>{return e.action.attachments != null;});
	
*/
	
}
function setDeviceMarker(attr, exp){
	
	var isSystemState = $("#systemview-systemstate").is(":checked");
	var keys = isSystemState ? 
		Object.keys(FRICORE.workflowstate.systemState) : FRICORE.availableStates.map(e=>{return e.id;});

	keys.forEach(e=>{
		var s = parseDeviceStatus(e);
		if(s.status != exp) return;
		var ss = [s.zone,s.deviceid,s.status].join("/");
		var elm = $("["+attr+"='"+ss+"']");
		if(s.verb == "OFF"){
			elm.removeClass(attr);
			elm.on("click", null);
		}else{
			elm.addClass(attr);
			elm.on("click", (e)=>{showDeviceStatusText(exp, e);});
		}
	})
	/*
	var target = _.filter(keys,(e)=>{return e.endsWith(exp)});
	target.forEach((e)=>{
		var elm = $("["+attr+"='"+e+"']");
		elm.addClass(attr);
		elm.on("click", (e)=>{showDeviceStatusText(exp, e);});

   });
   */

}
function parseDeviceStatus(str){
	var buff = str.split("/");
	var ret = {
		zone:buff[0],
		deviceid:buff[1],
		status:buff[2],
		verb:buff[3]
	};
	return ret;
}

function showDeviceStatusText(exp, evt){
	var elm = $("#systemview-tooltip");
	if(elm.is(":visible")){
		elm.hide().text("");
	}else{
		var label = exp.replace("/","");
		var txt = label ? FRICORE.markerLabel[label] : "異常あり";
		elm.text(txt).css("left", evt.pageX).css("top", evt.pageY).css("z-index", 1000).show();
	}
}


//TODO: implementing now...
function groupStateCards(){
	
	var sys = _.filter(FRICORE.workflowstate.systemState, (e)=>{return /(\w+)\/(\w+)\/(\w+)/.exec(e)});
	var usr = _.filter(FRICORE.availableStates,  (e)=>{});

	var r = /(\w+)\/(\w+)\/(\w+)/.exec(s);
	
	
	
}



function svgZoom(svgselector, ratio){
/*	var s = $(svgselector)[0];
	var w = $(svgselector).width();
	var h = $(svgselector).height();
	var scale = s.currentScale + ratio;
	$(s).width(w*scale).height(h*scale);*/
	var s = $(svgselector)[0];
	s.currentScale = s.currentScale + ratio;
}

//SVG要素を縮小
function svgZoomout(svgselector){
	svgZoom(svgselector, -0.1)
}
//SVG要素を拡大
function svgZoomin(svgselector){
	svgZoom(svgselector, 0.1);
}
//SVG要素を親要素のサイズに合わせる
function svgFitToContainer(svgselector){
	var c = $(svgselector).parent();
	var h = c.innerHeight() - 20;
	var w = c.innerWidth();
	$(svgselector)[0].setAttribute("currentScale", 1);
	$(svgselector).width(w).height(h);
}










$( function() {
    $( "#help" ).tabs();
  } );

