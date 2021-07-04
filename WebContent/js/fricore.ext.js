/*var Consts=new function(){
	this.INTERVAL=500;
	this.SYNC=2;
}();

var sync_counter = 0;


function ontick(){
	if(window.STOP_TICK)
		return;
	
	var now = new Date();
	var counter = 0;
	$("#clock").html(now.toLocaleString());
	$("#input-base").val(toDateTimeString(now));
	if(sync_counter > Consts.SYNC){

		_.each($(".worldname"), w=>{
			refresh($(w).text());
		});
		
//		$(".btn-refresh").click();
		sync_counter = 0;
	}else
		sync_counter ++;
}
*/
/**日付をinput datetime-local要素が許容する文字列形式(ローカル時刻・タイムゾーンなしのISO-8601)にフォーマットする*/
function toDateTimeString(date){
	return [	date.getFullYear(),
		("00" + date.getMonth()).slice(-2), 
		("00" + date.getDate()).slice(-2)].join("-") + "T" + 
		[("00" + date.getHours()).slice(-2), 
		("00" + date.getMinutes()).slice(-2), 
		("00" + date.getSeconds()).slice(-2)].join(":");
}
/*
function initPage(){
	
	window.setInterval(ontick, Consts.INTERVAL);
		
	get("workflow/vtime/worlds").then(resp=>{
		resp.json().then(r=>{
			var container = $("#world-container");
			var tmpl = _.template($("#tmpl-worldinfo").html());
			r.forEach(w=>{
				var e = $(tmpl(w));
				e.children(".btn-pause").on("click", pause);
				e.children(".btn-refresh").on("click", refresh);
				e.children(".btn-init").on("click", initialize);
				e.appendTo(container);
			});
			
		}).catch(t=>{
			console.log("err:" + t);
		});
		
	});
	
	
	$("#input-base").val(new Date().toISOString().replace(/Z$/, ""));
}
*/
function truncate(str){
	var len = Math.max(str.length,1024);
	return str.substr(0, len) + str.length >= 1024 ? "..." : "";
}
function pause(evt){
	//get("workflow/vtime/" + currentWorld(evt) + "/pause").then(e=>{
	get("workflow/process/" + currentWorld(evt) + "/pause").then(e=>{
		refresh(evt);
		//$(".btn-pause").removeClass("icon-pause").addClass("icon-resume").text("再開");
		getProcess();
	});
}

function resume(evt){
	//get("workflow/vtime/" + currentWorld(evt) + "/resume").then(e=>{
	get("workflow/process/" + currentWorld(evt) + "/resume").then(e=>{
		refresh(evt);
		$(".btn-pause").removeClass("icon-resume").addClass("icon-pause").text("一時停止");
		getProcess();
	});
}

/**http getリクエストを送信する*/
function get(url, params){
	var querystring = params ? Object.keys(params)
		.map(k => encodeURIComponent(k) + '=' + encodeURIComponent(params[k]))
		.join('&') : undefined;
	
	return request(querystring ? url + "?" + querystring : url, "GET", {});
}
//http postリクエストでjson形式のデータを送信する
function postJSON(url, params){
	return request(url, "POST", {headers:{"Content-Type":"application/json"},body:JSON.stringify(params)});
}
//http postリクエストでフォームデータを送信する
function post(url, params){
	var data = new URLSearchParams();
	_.keys(params).forEach(
			k=>{data.append(k, adjustFormData(params[k]));
	});
	
	return request(url, "POST", {headers:{"Content-Type":"application/x-www-form-urlencoded"},
		body:data});
}
//ESのDate.toString()の形式はJAX-RSでは解釈できない
function adjustFormData(data){
	if(!data)return data;
	if(data instanceof Date)return data.toUTCString();
	else return data;
}
//HTTPリクエストを送信する
function request(url, method, params){
	
	console.log("requesting:" + method + " " + url);
	
	var data = Object.assign({method:method},params);
	
	return fetch(url, data)
	.then(resp=>{
		console.log(method + " " + resp.url + " > " + resp.status + "; " + resp.statusText);
	
		if(resp.status != 200){
				console.log("failed to parse response. status: " + resp.status + "; " + resp.statusText);
		}
		var ct = (resp.headers.get("Content-Type"));
		if(ct != "application/json"){
			resp.text().then(t=>{
				console.log("failed to parse response: " + t);
			});
		}
		return resp;
	}).catch(err=>{
		console.log(err);
		$("#info").empty().append(err);
	});
}

function currentWorld(evt){
	if(typeof evt == "string")
		return evt;
		
	//var world =  $(evt.currentTarget).parents(".world").attr("id");
	var world = $(evt.currentTarget).parents("tr").children(".assign-team").text();
	if(!world){
		console.log("failed to resolve current world.");return null;
	}
	return world;
}
//恐らく使用しない
function refresh(evt){
	var world = "";
	if(typeof evt == "string"){
		world = evt;
	}else{
		world = currentWorld(evt);
	}

	if(!world){
		console.log("internal error: empty world cannot be refreshed.");return;
	}
	
	get("workflow/vtime/" + world).then(e=>{
		e.json().then(body=>{
			var t = $(_.template($("#tmpl-worldinfo").html())(body));
			t.children(".btn-refresh").on("click", refresh);
			t.children(".btn-init").on("click", initialize);
			if(body.clock && body.clock.paused){
				t.children(".btn-pause").addClass("resume").removeClass("pause").text("resume");
				//t.children(".btn-pause").on("click", resume);
			}else{
				t.children(".btn-pause").addClass("pause").removeClass("resume").text("pause");
				//t.children(".btn-pause").on("click", pause);
			}
			
			$("#" + body.name).replaceWith(t);
		});
		
	});
}
//(暫定)jax-rs-ri組み込みシリアライザが不正な日時文字列を返すので矯正
function adjustDateTimeString(str){
	return str.replace("Z[UTC]", "Z");
}
function scheduler(evt){
	
	get("workflow/vtime/scheduler");
}
function initialize(evt){
	var base = $("#input-base").val();
	var world = $(evt.currentTarget).parent().prop("id");
	var timescale = $(evt.currentTarget).parent().children(".timescale").val();
	var params = {base:new Date(base), timescale:timescale};
	var url = "workfloww/vtime/"+world+"/init";

	post(url, params)
	.catch(err=>{
		console.log(err);
		$("#info").empty();
		$("#info").append(err);
	}).then(resp=>{
		console.log(resp);
		if(resp.ok){
			var ct = (resp.headers.get("Content-Type"));
			if(ct != "application/json"){
				resp.text().then(msg=>{console.log(resp.staus + " " + msg)});					
			}else{
				resp.json().then(body=>{
					$("#info").empty();
					var t = _.template($("#info").html())(body);
					$("#info").empty().append($(t));
				});
			}
		}else{
			resp.text().then(msg=>{console.log(msg)});
		}
	});
}
function dump(obj){
	if(_.isArray(obj)){
		var buff = [];
		obj.forEach(e => {
			var v = dump(e);
			buff.push(v);
		});	
		return "[" + buff.join(",") + "]";
	}else if(typeof(val) == "object"){
		var buff = [];
		Object.keys(obj).forEach(k=>{
			var v = dump(obj[k]);
			buff.push(k + ":" + v);
		});
		return "{" + buff.join(",") + "}";
	}else{
		return obj;		
	}
}

function onchangeslider(src){
	var tip = $(src).next(".infotip");
	tip.text("x " + $(src).val());// = "visible";
}





/**
現在の演習シナリオでオプション機能が有効化されているかを判定する。

現在ロードされているシナリオデータで、setting.jsonのfeaturesプロパティに指定されているかどうかを確認する。
*/
function isFeatureEnabled(feature){
	if(FRICORE.setting && FRICORE.setting.features &&
	 FRICORE.setting.features.indexOf(feature) >= 0)
		return true;
	return false;
}


//TAG:feature-virtualtime
/**演習ワークフロー一覧のコントロールを演習の状態に応じて更新する*/
function adjustProcessList(elm, proc){
	if(!isFeatureEnabled("virtualtime"))
		return;
	
	var obj = proc;	
	Object.assign(obj, {time: new Date(proc.world && proc.world.time),
				paused: proc.world && proc.world.paused,
				timesacle: proc.world && proc.world.timescale,
				basetime: new Date(proc.world ? proc.world.clock.baseTime : 0),
				offset: proc.world ? proc.world.clock.offset : 0,
				});
	
	elm.children(".worldinfo").empty().html();
	
	if(obj.started && !obj.aborted){
		if(obj.paused){
			elm.find(".btn-pause").removeClass("ui-disabled").removeClass("btn-pause fa-pause pause").addClass("btn-resume resume fa-play-circle").prop("title", "演習を再開します。").text("再開").off("click").on("click", ()=>{resume(proc.world.name);});
		}else{
			elm.find(".btn-pause").removeClass("ui-disabled").addClass("btn-pause fa-pause pause").removeClass("btn-resume resume fa-play-circle").prop("title", "演習を一時停止します。").text("一時停止").off("click").on("click", ()=>{pause(proc.world.name);});
		}
		elm.find(".btn-abort").removeClass("ui-disabled").off("click").on("click", abort);
	}else{
		elm.find(".btn-pause").removeClass("ui-disabled").addClass("ui-disabled").prop("title", "演習が開始されていません。");
		elm.find(".btn-abort").addClass("ui-disabled").off("click");
	}
	
	
	$(".slider-bar").slider();
	$(".clock").clockticker();
	$(".togglemenu-container").togglemenu();
	
}

function onClickPopup(evt){
	var logarea = $(evt.currentTarget).next(".logarea");
	if(logarea.is(":visible")){
		logarea.css("display", "none");
	}else{
		logarea.css("display", "inline-block").css("z-index", 1000);
	}	
}





/**Dateをhtml input type=datetime-localに対応した形式(yyyy-MM-ddTHH:mm:ss)に変換する。
  (input要素が許容する日時文字列形式はESのDate型として認識されない)
*/
function formatISOString(date){
	var d = toDateObject(date); 
	var str = [
	("0000"+d.getFullYear()).slice(-4),
	("00"+(d.getMonth()+1)).slice(-2),
	("00"+d.getDate()).slice(-2)].join("-")+"T"+
	[("00"+d.getHours()).slice(-2),
	("00"+d.getMinutes()).slice(-2),
	("00"+d.getSeconds()).slice(-2)].join(":");
	return str;
}
/**Date、Number、String型の日時情報をDate型に変換する。*/
function toDateObject(obj){
	if(typeof obj == "object"){//Date
		return obj;		
	}else if(typeof obj == "string"//string 
			|| typeof obj == number){//millisec
		return new Date();
	}else{
		throw new Error("invalid date object: "+obj);
	}
}
/*
function initSpeedBar(){
	$(".slider-bar").on("change, input", evt=>{
		var ctrl = $(evt.currentTarget);
		var digit = ctrl.next(".slider-digit");
		digit	.val(ctrl.val());
	});
	$(".slider-digit").on("change, input", evt=>{
		var ctrl = $(evt.currentTarget);
		var slider = ctrl.prev(".slider-bar");
		slider.val(ctrl.val());
	});
}
*/
/**隣接するスライダーコントロールと数値コントロールの値を同期する。
・以下を定義
  <input type="range" class="slider-bar">
  <input type="number" class="slider-digit">
・以下で初期化
  $(".slider-bar").slider();

*/
$.prototype.slider = function(){
	var me = $(this);
	me.on("change, input", evt=>{
		var ctrl = $(evt.currentTarget);
		var digit = ctrl.siblings(".slider-digit");
		digit	.val(ctrl.val());
	});
	me.next(".slider-digit").on("change, input", evt=>{
		var ctrl = $(evt.currentTarget);
		var slider = ctrl.siblings(".slider-bar");
		slider.val(ctrl.val());
	});
	me.siblings(".togglebutton").on("click", e=>{
		
	});

}


/**時計表示・制御のためのJQuery要素。
 * 日時を表示・設定するためのinput要素を子要素に持つJQuery要素に対し、$(セレクタ).clockticker()で有効化する。
   input要素は逐次現在時刻を表示するよう更新されるが、入力中は表示を更新しない。
 */
$.prototype.clockticker = function(){
	var me = $(this);
	var tid = window.setInterval(()=>{
		if(me.is(":focus"))
			return;//入力中は値を更新しない
		if(me.is("input"))
			me.val(formatISOString(new Date()));
		if(me.not("input"))
			me.text(formatISOString(new Date()));
	},2000);
	
}
/**ボタン押下により2種類のコンテンツの表示・非表示を切り替えるJQuery要素。
$(セレクタ).togglemenu(コンテナ要素のセレクタ)で有効化する。
 ボタンクリックでtogglemenu-target、togglemenu-altクラスを持つ子要素をトグルで表示する。
 */
$.prototype.togglemenu = function(){
	var me = $(this);
	me.children(".togglemenu-button").on("click", evt => {
		var menu = this.children(".togglemenu-target");
		var alt = this.children(".togglemenu-alt");
		if(menu.is(":visible")){
			menu.css("display", "none");
			alt.css("display", "inline-block");
		}else{
			menu.css("display", "inline-block");
			alt.css("display", "none");
		}
	});
}
/**指定された座標(クライアント座標)が要素の領域内にあるか*/
function inRangeOf(element, pos){
	var offset = $(element).offset();
	var width = $(element).width();
	var height = $(element).height();
	var range = {left:offset.left, top:offset.top, right:offset.left + width, bottom:offset.top + height};
	
	var ret = range.left <= pos.x && range.right >= pos.x && 
		range.top <= pos.y && range.bottom >= pos.y;

	return ret;
}
/**メニューバー。クラスsubmenuを持つ子要素にはJQuery UIのmenuを適用*/
$.prototype.menubar = function(){
	var me = $(this);
	me.find(".submenu").menu().hide();
	$(document).on("click", e=>{
		var pos = {x:e.clientX,	y:e.clientY}
		if(!inRangeOf(me, pos)){
			me.find(".submenu").hide();
		}
	});


	// チェック付きメニュー項目	
	//同一階層で.checkgroupクラスを持つ要素は単一選択チェックグループとして扱う
	me.find(".checkable").on("click", e=>{
		var item = $(e.currentTarget)
		var checkgroup = item.closest(".ui-menu").find(".checkable.checkgroup");
		if(checkgroup.length > 0){
			checkgroup.data("checked", false).removeClass("checked fa-check");
		}
		if(item.hasClass("checked")){
			item.data("checked", false).removeClass("checked fa-check");
		}else{
			item.data("checked", true).addClass("checked fa-check");
		}
	});

	//サブメニューの展開
	me.find(".menuitem").on("click", evt =>{
		var cur = $(evt.currentTarget);
		cur.siblings(".menuitem").find(".submenu").hide();//隣接メニューを閉じる
		if(cur.find(".submenu").is(":visible")){
			cur.parent().find(".menuitem").removeClass("menuitem-active");
			cur.find(".submenu").hide();
		}else{
			cur.parent().find(".menuitem").removeClass("menuitem-active");
			cur.addClass("menubar-active");
			cur.find(".submenu").show();
//			cur.find(".submenu").offset({top:"1.5em",left:"1em"});//サブアイテムを表示
			
		}
	});
}



/**振り返り画面;シーケンス図でアクターが表示されたときに呼び出される。*/
function onDrawActor(evt){
	if(e.hasOwnProperty("detail")){
		FRICORE.warn("no data attached to Actor SVG element.");
		return;
	}
	
	var data = e.detail;//アクターのボックス、アクターを結ぶラインを含むg要素の配列が返る。
	var role = e.detail.attr("data-role");//ロール名が設定されている
	
	//TODO: シーケンス図をカスタマイズする場合はここにコードを追加
	FRICORE.trace("on draw actor "+ role);
}
/**振り返り画面;シーケンス図でメッセージ(イベント)が表示されたときに呼び出される。*/
function onDrawMessage(evt){
	if(e.hasOwnProperty("detail")){
		FRICORE.warn("no data attached to Actor SVG element.");
		return;
	}
	
	var data = e.detail;//アクターのボックス、アクターを結ぶラインを含むg要素の配列が返る。
	var eid = e.detail.attr("id");//イベントIDが設定されている。
	var eventdata = _.filter(FRICORE.events, {id:data.eid});

	//TODO: シーケンス図をカスタマイズする場合はここにコードを追加 
	FRICORE.trace("on draw message: "+ eventdata || "");

}
/**振り返り画面;シーケンス図でノートが表示されたときに呼び出される。*/
function onDrawNote(evt){
	if(e.hasOwnProperty("detail")){
		FRICORE.warn("no data attached to Note SVG element.");
		return;
	}
	
	var data = e.detail;//ノートを含むg要素の配列が返る。
	var eid = e.detail.attr("id");//イベントIDが設定されている。
	var eventdata = _.filter(FRICORE.events, {id:data.eid});

	//TODO: シーケンス図をカスタマイズする場合はここにコードを追加
	
	FRICORE.trace("on draw note: "+ eventdata || "");
}


$(()=>{
//	$(document).on("click", ".resume", resume);
//	$(document).on("click", ".pause", pause);
	
//	$(".slider-bar").slider();
//	$(".clock").clockticker();
//	$(".togglemenu-container").togglemenu();
	
	document.addEventListener("drawactor", onDrawActor);
	document.addEventListener("drawnote", onDrawNote);
	document.addEventListener("drawmessage", onDrawMessage);
	
	//$("#popupctrl").on("click", onClickPopup);
	$(".menubar").menubar();
	
	

});
