/* app_bridge.js — 앱이 웹에 심는 다리 (0.3.1판, 빌드 260927-7)
   ★0.3.1 (260927-7, 이사장님 승인) 길눈 말이 끝난 뒤 쉬는 시간 0.3초 → 0.15초
   ★0.3.0 (260927-6, 이사장님 승인 — 음성비서 수정) 길눈이 말하는 중에는 마이크를 열지 않습니다.
      길눈 목소리(소리 문)가 말하고 있거나 말할 차례를 기다리는 말이 있으면 다 끝날 때까지(최대 8초) 기다렸다가 0.3초 쉬고 엽니다.
      마이크가 먼저 열리면 아이폰이 통화처럼 소리를 낮추고, 길눈이 제 목소리를 듣고 엉뚱하게 알아들었습니다.
      진단 기록: 마이크를 청한 때와 기다린 시간을 앱에 넘겨 서버에 남깁니다(말씀하신 내용은 남기지 않음).
   웹(gigi.js 등)은 window.adaApp 이 있으면 앱의 손발을 쓰고, 없으면 지금처럼 웹 방식으로 갑니다.
   ★0.2.0 (2026-09-26 이사장님 승인) — 웹의 음성 인식(SpeechRecognition)을 앱 받아쓰기(SttService)로 갈음합니다.
   모양은 웹 것과 같아서(start·stop·abort, onresult·onerror·onend 등) 길눈의 말로 시키기·말로 넣기가 고칠 것 없이 앱 받아쓰기를 씁니다.
   음악과 함께 틀 안의 화면(iframe)도 맨 위 창을 거쳐 답을 받습니다. */
(function(){
  if (window.adaApp) return;
  var deul = {};
  window.adaApp = {
    pan: "0.3.1",
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
  /* 0.2.2 (260926-3) — 더 잘 알아듣게: 지금 화면에 보이는 단추 이름과 자주 쓰는 명령을 받아쓰기에 미리 알려 줍니다 */
  var GIBON = ["뒤로", "첫 화면", "말로 하기", "무엇이 있어", "지금 어디야", "몇 정거장 남았어", "새로고침", "설정", "도움말",
    "걸어갈까요", "차로갈까요", "즐겨찾기", "지하철", "버스", "택시", "기차", "여정 끝내기", "그만", "다시", "네", "아니요", "길 찾기", "둘러보기", "음악", "라디오", "TV", "지금 가는 길 알려 줘"];
  function moaHints(){
    var out = [], bon = {};
    function neot(s){ s = String(s || "").split(/[—\-·(]/)[0].replace(/\s+/g, " ").trim(); if (s && s.length <= 20 && !bon[s]) { bon[s] = 1; out.push(s); } }
    try {
      var d = document, bs = d.querySelectorAll("button, a[href], [role=button], [role=tab]");
      for (var i = 0; i < bs.length && out.length < 70; i++) { var b = bs[i]; if (b.offsetParent === null) continue; neot(b.getAttribute("aria-label") || b.textContent); }
    } catch(e){}
    for (var j = 0; j < GIBON.length; j++) neot(GIBON[j]);
    return out.slice(0, 100);
  }
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
  /* 0.3.0 길눈이 지금 말하는 중인지 — 소리 문(SORI.jul)과 기기 목소리를 함께 봅니다 */
  function malJung(){
    var ws = [window]; try { if (TOP !== window) ws.push(TOP); } catch(e){}
    for (var i = 0; i < ws.length; i++) {
      var w = ws[i];
      try { var j = w.SORI && w.SORI.jul && w.SORI.jul(); if (j && (j.jung || (j.gidarim && j.gidarim.length))) return true; } catch(e){}
      try { if (w.speechSynthesis && w.speechSynthesis.speaking) return true; } catch(e){}
    }
    return false;
  }
  AppSR.prototype.start = function(){
    if (this._on) { var er = new Error("이미 듣는 중입니다"); er.name = "InvalidStateError"; throw er; }
    this._on = true; this._mal = false; this._cheong = false;
    this._id = "s" + Date.now() + "_" + (++sun) + "_" + Math.floor(Math.random() * 1e6);
    REG[this._id] = this;
    var me = this, myId = this._id, t0 = Date.now(), swim = 0;
    (function gidarim(){
      if (!me._on || me._id !== myId) return;
      var maljung = malJung();
      if (maljung && Date.now() - t0 < 8000) { swim = 0; setTimeout(gidarim, 100); return; }
      if (swim === 0) { swim = 1; setTimeout(gidarim, 150); return; }   /* 말이 끝나고 0.15초 쉬고 (260927-7) */
      me._cheong = true;
      try { window.adaApp.bureugi("jindan", { e: "micCheong", gidarimMs: Date.now() - t0, maljung: maljung }); } catch(e){}
      window.adaApp.bureugi("deutgiSijak", { id: myId, lang: me.lang || "ko-KR", continuous: !!me.continuous, hints: moaHints() });
    })();
  };
  AppSR.prototype.stop = function(){
    if (!this._on) return;
    if (!this._cheong) { this._batda("deutgiKkeut", { id: this._id }); return; }   /* 아직 마이크를 열기 전이면 그냥 끝냄 */
    window.adaApp.bureugi("deutgiMeom", { id: this._id, abort: false });
  };
  AppSR.prototype.abort = function(){
    if (!this._on) return;
    if (this._cheong) window.adaApp.bureugi("deutgiMeom", { id: this._id, abort: true });
    this._ssoda("error", { error: "aborted", message: "aborted" });
    if (!this._cheong) this._batda("deutgiKkeut", { id: this._id });
  };
  AppSR.prototype._batda = function(n, d){
    if (n === "deutgiSijak") { this._ssoda("start"); this._ssoda("audiostart"); return; }
    if (n === "deutgiGyeolgwa") {
      var fin = !!d.final;
      if (!this._mal) { this._mal = true; this._ssoda("soundstart"); this._ssoda("speechstart"); }
      if (!fin && !this.interimResults) return;
      var res = [], al = (fin && d.alts && d.alts.length) ? d.alts : [d.t];
      for (var q = 0; q < al.length && q < Math.max(1, this.maxAlternatives || 1); q++) res.push({ transcript: String(al[q] || ""), confidence: q === 0 ? (fin ? 0.9 : 0.5) : 0.5 });
      if (!res.length) res.push({ transcript: String(d.t || ""), confidence: 0.5 });
      res.isFinal = fin; res.item = function(i){ return this[i]; };
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
