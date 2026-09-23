/* app_bridge.js — 앱이 웹에 심는 다리 (0.1.0판, 빌드 260909-1)
   웹(gigi.js 등)은 window.adaApp 이 있으면 앱의 손발을 쓰고, 없으면 지금처럼 웹 방식으로 갑니다. */
(function(){
  if (window.adaApp) return;
  var deul = {};
  window.adaApp = {
    pan: "0.1.0",
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
})();
