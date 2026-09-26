/* app_bridge.js — 앱이 웹에 심는 다리 (0.2.1판, 빌드 260926-2)
   웹(gigi.js 등)은 window.adaApp 이 있으면 앱의 손발을 쓰고, 없으면 지금처럼 웹 방식으로 갑니다.
   ★0.2.0 (2026-09-26 이사장님 승인) — 웹의 음성 인식(SpeechRecognition)을 앱 받아쓰기(SttService)로 갈음합니다.
   모양은 웹 것과 같아서(start·stop·abort, onresult·onerror·onend 등) 길눈의 말로 시키기·말로 넣기가 고칠 것 없이 앱 받아쓰기를 씁니다.
   음악과 함께 틀 안의 화면(iframe)도 맨 위 창을 거쳐 답을 받습니다. */
(function(){
  if (window.adaApp) return;
  var deul = {};
  window.adaApp = {
    pan: "0.2.1",
    isApp: true,
    /* 웹 → 앱 */
    bureugi: function(a, m){ try { var o = m || {}; o.a = a; window.webkit.messageHandlers.ada.postMessage(o); } catch(e){} },
    /* 앱 → 웹: 웹이 adaApp.deutgi("wichi", fn) 로 듣습니다 */
    deutgi: function(name, fn){ (deul[name] = deul[name] || []).push(fn); },
    batda: function(name, data){ var fs = deul[name] || []; for (var i = 0; i < fs.length; i++) { try { fs[i](data); } catch(e){} } }
  };
  /* 뒤로 가기로 앱 밖에 나가지 않게 — 웹의 history.back 이 첫 화면이면 앱 대문으로 */
  var origBack = history.back.bind(history);
  history.back = function(){ if (history.length <= 1) { window.adaApp.bureugi("dwiro"); } else { origBack(); } };
  document.documentElement.setAttribute("data-ada-app", "1");

  /* ── 앱 받아쓰기 ── */
  var TOP = window;
  try { if (window.top && window.top.adaApp) TOP = window.top; } catch(e){ TOP = window; }
  var REG = TOP.__adaStt || (TOP.__adaStt = {});
  if (TOP === window && !window.__adaSttDal) {
    window.__adaSttDal = 1;
    ["deutgiSijak", "deutgiGyeolgwa", "deutgiOryu", "deutgiKkeut"].forEach(function(n){
      window.adaApp.deutgi(n, function(d){
        try { var who = d && REG[d.id]; if (who) who._batda(n, d); } catch(e){}
      });
    });
  }
  var sun = 0;
  function AppSR(){
    this.lang = "ko-KR"; this.continuous = false; this.interimResults = false; this.maxAlternatives = 1;
    this.grammars = null;
    this._H = {}; this._L = {}; this._id = ""; this._on = false; this._mal = false;
  }
  AppSR.isApp = true;
  /* onresult 같은 손잡이는 웹 것처럼 속 칸(_H)에 둡니다 — 길눈 app.js 가 손잡이를 덮어 감싸도 두 번 불리지 않게 */
  ["start", "end", "result", "error", "nomatch", "audiostart", "audioend", "soundstart", "soundend", "speechstart", "speechend"].forEach(function(n){
    Object.defineProperty(AppSR.prototype, "on" + n, {
      configurable: true,
      get: function(){ return (this._H && this._H[n]) || null; },
      set: function(f){ if (!this._H) this._H = {}; this._H[n] = (typeof f === "function") ? f : null; }
    });
  });
  AppSR.prototype.addEventListener = function(t, f){ (this._L[t] = this._L[t] || []).push(f); };
  AppSR.prototype.removeEventListener = function(t, f){ var a = this._L[t] || []; var i = a.indexOf(f); if (i >= 0) a.splice(i, 1); };
  AppSR.prototype._ssoda = function(t, ev){
    ev = ev || {}; ev.type = t; ev.target = this; ev.currentTarget = this; ev.timeStamp = Date.now();
    var h = this._H && this._H[t];
    try { if (typeof h === "function") h.call(this, ev); } catch(e){ setTimeout(function(){ throw e; }, 0); }
    var a = (this._L[t] || []).slice();
    for (var i = 0; i < a.length; i++) { try { a[i].call(this, ev); } catch(e2){ (function(x){ setTimeout(function(){ throw x; }, 0); })(e2); } }
  };
  AppSR.prototype.start = function(){
    if (this._on) { var er = new Error("이미 듣는 중입니다"); er.name = "InvalidStateError"; throw er; }
    this._on = true; this._mal = false;
    this._id = "s" + Date.now() + "_" + (++sun) + "_" + Math.floor(Math.random() * 1e6);
    REG[this._id] = this;
    window.adaApp.bureugi("deutgiSijak", { id: this._id, lang: this.lang || "ko-KR", continuous: !!this.continuous });
  };
  AppSR.prototype.stop = function(){ if (this._on) window.adaApp.bureugi("deutgiMeom", { id: this._id, abort: false }); };
  AppSR.prototype.abort = function(){
    if (!this._on) return;
    window.adaApp.bureugi("deutgiMeom", { id: this._id, abort: true });
    this._ssoda("error", { error: "aborted", message: "aborted" });
  };
  AppSR.prototype._batda = function(n, d){
    if (n === "deutgiSijak") { this._ssoda("start"); this._ssoda("audiostart"); return; }
    if (n === "deutgiGyeolgwa") {
      var fin = !!d.final;
      if (!this._mal) { this._mal = true; this._ssoda("soundstart"); this._ssoda("speechstart"); }
      if (!fin && !this.interimResults) return;
      var alt = { transcript: String(d.t || ""), confidence: fin ? 0.9 : 0.5 };
      var res = [alt]; res.isFinal = fin; res.item = function(i){ return this[i]; };
      var all = [res]; all.item = function(i){ return this[i]; };
      this._ssoda("result", { resultIndex: 0, results: all });
      if (fin) { this._ssoda("speechend"); this._ssoda("soundend"); }
      return;
    }
    if (n === "deutgiOryu") { this._ssoda("error", { error: String(d.error || "network"), message: String(d.error || "") }); return; }
    if (n === "deutgiKkeut") {
      this._on = false; try { delete REG[this._id]; } catch(e){}
      this._ssoda("audioend"); this._ssoda("end");
    }
  };
  /* 0.2.1 (260926-2) — 웹 쪽(길눈 app.js)이 소리 설정(navigator.audioSession)을 "녹음 겸용"으로 바꾸면
     아이폰이 소리를 귀 대는 쪽(수화기)으로 보내 작게 들렸습니다. 앱에서는 소리 설정을 앱(SttService)이 맡으므로 웹의 바꾸기는 받지 않습니다. */
  try {
    var AS = navigator.audioSession;
    if (AS) Object.defineProperty(AS, "type", { configurable: true, get: function(){ return "auto"; }, set: function(v){} });
  } catch(e){}
  try { window.SpeechRecognition = AppSR; } catch(e){}
  try { window.webkitSpeechRecognition = AppSR; } catch(e){}
})();
