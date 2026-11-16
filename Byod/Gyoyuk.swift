// 1.6.0 (261019-BI8) 녹화 지킴이(NokhwaJikim) — 녹화가 멈추면 3초에 한 번 알림, 화면 쪽은 녹화 다시 잇기
// LVD 송출 아이폰 — 라이브 현장영상해설 방송·현장영상해설교육 녹화 화면 (1.4.0판, 빌드 261016-BI6, 방클, 이사장님 지시 「지금 앱으로 완성」 2026-10-16)
// 배프 교육 녹화 화면(https://lvd.ada.or.kr/bfblive/gyoyuk.html)을 앱 안에서 엽니다. 녹화·중계·나스 담기는 그 화면이 그대로 합니다.
// 앱이 하는 일: 카메라·마이크 허락을 화면에 넘겨 줌, 이 화면에 있는 동안 화면이 꺼지지 않게 붙잡음,
//   화면 쪽 뒤로 단추(배프 대문으로 가는 것)를 감추고 앱의 뒤로만 남김, 뒤로는 한 번 더 물어 녹화가 실수로 끊기지 않게 함.
import SwiftUI
import WebKit
import UIKit
import AVFoundation

/// 1.6.0 (261019-BI8, 방클, 이사장님 지시 2026-10-19 「녹화 끊김 막기」) 녹화 지킴이 — 화면(nokhwa_jikim.js)이 1초마다 녹화 상태를 알려 줌.
/// 녹화가 멈추거나, 화면이 잠기거나 앱이 뒤로 가 녹화 소식이 8초 넘게 끊기면 3초에 한 번 조용한 알림 소리(뒤에서도 울림).
final class NokhwaJikim: NSObject, WKScriptMessageHandler {
    static let shared = NokhwaJikim()
    private(set) var jung = false
    private var meomchum = false
    private var majimak = Date().timeIntervalSince1970
    private var boinda = true
    private lazy var jikim = Jikim(meomchumInga: { [weak self] in
        guard let s = self else { return false }
        return s.jung && (s.meomchum || Date().timeIntervalSince1970 - s.majimak > 8 || !s.boinda)
    }, bakkwim: { m in if m { UIAccessibility.post(notification: .announcement, argument: "녹화가 멈췄을 수 있습니다. 화면을 켜고 녹화를 살펴 주십시오.") } })
    private override init() {
        super.init()
        let nc = NotificationCenter.default
        nc.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in self?.boinda = false; self?.jikim.dangjang() }   // 뒤로 가면 곧바로 살핌(멈추기 전에 알림을 켬)
        nc.addObserver(forName: UIApplication.willEnterForegroundNotification, object: nil, queue: .main) { [weak self] _ in self?.boinda = true; self?.majimak = Date().timeIntervalSince1970 }
        jikim.sijak()
    }
    private var jeonSangtae = ""
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let s = message.body as? String else { return }
        majimak = Date().timeIntervalSince1970
        if s != "salaitda" && s != jeonSangtae {   // 1.16.5 교육녹화도 이용 기록에(1초마다 오는 살아 있음은 빼고 바뀔 때만)
            jeonSangtae = s
            ByodGirok.shared.namgi("gyoyuk_nokhwa", ["sangtae": s])
        }
        switch s {
        case "sijak", "salaitda": jung = true; meomchum = false
        case "meomchum": jung = true; meomchum = true
        case "kkeut": jung = false; meomchum = false
        default: break
        }
    }
}

/// 1.16.5 (261109, 새방클, 이사장님 승인 「지금 말한 4가지부터」) 녹화 화면에서 일어난 일을 이용 기록에 — 화면 안내 말(숫자는 #로 가림)·오류·열림.
/// 11-08 선정릉에서 교육녹화가 안 됐을 때 어디서 막혔는지 기록이 없었음.
final class GyoyukGirok: NSObject, WKScriptMessageHandler, WKNavigationDelegate {
    static let shared = GyoyukGirok()
    static let JS = "(function(){if(window.__lvdGirok)return;window.__lvdGirok=1;function s(t){t=String(t||'').replace(/[0-9]+/g,'#').slice(0,200);try{if(window.webkit&&webkit.messageHandlers&&webkit.messageHandlers.lvdGirok)webkit.messageHandlers.lvdGirok.postMessage(t);else if(window.LVDSongchul&&LVDSongchul.girok)LVDSongchul.girok(t);}catch(e){}}var last='';function bogi(){var t=[];document.querySelectorAll('[aria-live],[role=status],[role=alert]').forEach(function(e){var x=(e.innerText||'').trim();if(x)t.push(x);});var j=t.join(' / ').replace(/[0-9]+/g,'#');if(j&&j!==last){last=j;s('화면: '+j);}}new MutationObserver(function(){clearTimeout(window.__lvdT);window.__lvdT=setTimeout(bogi,400);}).observe(document.documentElement,{subtree:true,childList:true,characterData:true});window.addEventListener('error',function(e){s('오류: '+(e.message||''));});window.addEventListener('unhandledrejection',function(e){s('오류: '+((e.reason&&e.reason.message)||e.reason||''));});s('열림 '+location.pathname);})();"
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let s = message.body as? String else { return }
        ByodGirok.shared.namgi("gyoyuk_hwamyeon", ["mal": s])
    }
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        ByodGirok.shared.namgi("gyoyuk_hwamyeon", ["mal": "열지 못함: " + error.localizedDescription])
    }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        ByodGirok.shared.namgi("gyoyuk_hwamyeon", ["mal": "열지 못함: " + error.localizedDescription])
    }
}

/// 1.17.1 (261116, 새방클, 대표님 지시 「막히면 알려 주고 그 자리에서 바로 고치러 갈 수 있게」) 화면의 「설정 열기」 단추 → 이 앱의 설정 화면을 곧장 엶(카메라·마이크 켜기)
final class SeoljeongYeolgi: NSObject, WKScriptMessageHandler {
    static let shared = SeoljeongYeolgi()
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        ByodGirok.shared.namgi("seoljeong_yeolgi", ["mu": (message.body as? String) ?? ""])
        SeoljeongDoragagi.gagi()
    }
}

/// 1.17.2 (261116-BI28, 새방클, 대표님 지시 261116 「설정에서 켜고 돌아오면 하던 화면으로」) 아이폰은 설정에서 허락을 바꾸면 앱을 껐다 켬 —
/// 설정을 열 때 어느 화면이었는지 적어 두고, 15분 안에 앱이 다시 켜지면 그 화면을 곧바로 엶(교육 녹화·인터넷 방송·TV해설방송)
enum SeoljeongDoragagi {
    static var jigeum = ""   // 지금 보고 있는 화면(gyoyuk·live·tv)
    static func gagi() {
        let d = UserDefaults.standard
        d.set(jigeum, forKey: "seoljeong_hwamyeon"); d.set(Date().timeIntervalSince1970, forKey: "seoljeong_ttae")
        if let u = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(u) }
    }
    /// 앱이 켜질 때 한 번 — 15분 안에 설정에 다녀왔으면 그 화면(한 번 쓰면 지움)
    static func doraon() -> Hwamyeon? {
        let d = UserDefaults.standard
        let hw = d.string(forKey: "seoljeong_hwamyeon") ?? "", t = d.double(forKey: "seoljeong_ttae")
        d.removeObject(forKey: "seoljeong_hwamyeon"); d.removeObject(forKey: "seoljeong_ttae")
        guard Date().timeIntervalSince1970 - t < 15 * 60 else { return nil }
        ByodGirok.shared.namgi("seoljeong_doraom", ["hwamyeon": hw])
        switch hw { case "gyoyuk": return .gyoyuk; case "live": return .live; case "tv": return .tv; default: return nil }
    }
    static let DORAOGI = " 켜신 뒤 화면 맨 왼쪽 위의 LVD 송출을 누르시면 이 화면으로 돌아옵니다."
}

/// 1.17.1 (261116) 카메라·마이크 허락 살피기 — 처음이면 앱이 먼저 묻고, 꺼져 있으면 무엇이 꺼졌는지 알리고 「설정 열기」를 내놓음.
/// 11-14·11-16 센터용 아이폰에서 교육 녹화가 「허락 없음」으로 막혔는데 무엇을 켜야 하는지 아무 안내가 없었음.
enum HeorakSalpim {
    static func ireum(_ s: AVAuthorizationStatus) -> String {
        switch s { case .authorized: return "heorak"; case .denied: return "geojeol"; case .restricted: return "jehan"; case .notDetermined: return "anmureum"; @unknown default: return "moreum" }
    }
    /// 다 묻고 난 뒤 꺼진 것 목록(마이크·카메라)을 돌려줌
    static func salpigi(kamera: Bool = true, _ kkeut: @escaping (_ kkeojin: [String]) -> Void) {
        let a = AVCaptureDevice.authorizationStatus(for: .audio)
        if a == .notDetermined { AVCaptureDevice.requestAccess(for: .audio) { _ in DispatchQueue.main.async { salpigi(kamera: kamera, kkeut) } }; return }
        let v = AVCaptureDevice.authorizationStatus(for: .video)
        if kamera && v == .notDetermined { AVCaptureDevice.requestAccess(for: .video) { _ in DispatchQueue.main.async { salpigi(kamera: kamera, kkeut) } }; return }
        ByodGirok.shared.namgi("gyoyuk_heorak", ["mic": ireum(a), "cam": ireum(v)])
        var k: [String] = []
        if a != .authorized { k.append("마이크") }
        if kamera && v != .authorized { k.append("카메라") }
        kkeut(k)
    }
}

struct GyoyukView: View {
    var jemok = "현장영상해설교육 녹화"
    var juso = GyoyukPan.JUSO
    var kamera = true   // 1.17.1 TV해설방송은 소리만이라 카메라를 묻지 않음
    let dwiro: () -> Void
    @State private var mutgi = false
    @State private var heorakBoim = false
    @State private var heorakGeul = ""
    var body: some View {
        VStack(spacing: 10) {
            Danchu(geul: "뒤로") { mutgi = true }
            GyoyukPan(juso: juso)
                .alert("허락이 꺼져 있습니다", isPresented: $heorakBoim) {   // 1.17.1
                    Button("설정 열기") { ByodGirok.shared.namgi("seoljeong_yeolgi", ["mu": "alrim"]); SeoljeongDoragagi.gagi() }
                    Button("그대로 쓰기", role: .cancel) { }
                } message: { Text(heorakGeul) }
            Danchu(geul: "뒤로") { mutgi = true }
        }
        .padding(12)
        .accessibilityAction(.escape) { mutgi = true }
        .onAppear {
            UIApplication.shared.isIdleTimerDisabled = true; ByodGirok.shared.namgi("gyoyuk_yeolgi", ["hwamyeon": jemok])    // 녹화하는 동안 화면이 꺼지지 않게
            SeoljeongDoragagi.jigeum = juso == GyoyukPan.LIVE ? "live" : (juso == GyoyukPan.TV ? "tv" : "gyoyuk")   // 1.17.2
            HeorakSalpim.salpigi(kamera: kamera) { k in   // 1.17.1 마이크·카메라 허락 살피기
                guard !k.isEmpty else { return }
                if k.contains("마이크") {
                    heorakGeul = "이 폰에서 LVD 송출의 " + k.joined(separator: "와 ") + " 허락이 꺼져 있어 소리를 받을 수 없습니다. 설정 열기를 누르고 " + k.joined(separator: "와 ") + "를 켜 주십시오." + SeoljeongDoragagi.DORAOGI
                } else {
                    heorakGeul = "이 폰에서 LVD 송출의 카메라 허락이 꺼져 있어 영상은 담기지 않고 소리만 녹음됩니다. 영상도 담으시려면 설정 열기를 누르고 카메라를 켜 주십시오." + SeoljeongDoragagi.DORAOGI
                }
                heorakBoim = true
            }
        }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false; SeoljeongDoragagi.jigeum = ""; ByodGirok.shared.namgi("gyoyuk_datgi", ["hwamyeon": jemok]) }
        .alert(jemok + "에서 나가기", isPresented: $mutgi) {
            Button("나가기", role: .destructive) { dwiro() }
            Button("머무르기", role: .cancel) { }
        } message: {
            Text("방송이나 녹화 중이면 멈춥니다. 나가시겠습니까?")
        }
    }
}

struct GyoyukPan: UIViewRepresentable {
    static let JUSO = "https://lvd.ada.or.kr/bfblive/gyoyuk.html"
    static let LIVE = "https://lvd.ada.or.kr/bfblive/bang2.html"
    static let TV = "https://lvd.ada.or.kr/lvds/lvds_live.html"   // 1.5.0 TV해설방송 하기
    var juso = GyoyukPan.JUSO
    final class Dowumi: NSObject, WKUIDelegate {
        // 카메라·마이크를 화면에 넘겨 줌(아이폰이 앱에 처음 한 번 허락을 물음)
        @available(iOS 15.0, *)
        func webView(_ webView: WKWebView, requestMediaCapturePermissionFor origin: WKSecurityOrigin, initiatedByFrame frame: WKFrameInfo, type: WKMediaCaptureType, decisionHandler: @escaping (WKPermissionDecision) -> Void) {
            decisionHandler(origin.host.hasSuffix("ada.or.kr") ? .grant : .prompt)
        }
    }
    func makeCoordinator() -> Dowumi { Dowumi() }
    func makeUIView(context: Context) -> WKWebView {
        let c = WKWebViewConfiguration()
        c.allowsInlineMediaPlayback = true
        c.mediaTypesRequiringUserActionForPlayback = []
        c.websiteDataStore = .default()
        let gamchugi = "(function(){var s=document.createElement('style');s.textContent='#btnDwi1,#btnDwi2,.dwiro-btn,#homeBtn{display:none!important}';(document.head||document.documentElement).appendChild(s);})();"
        c.userContentController.addUserScript(WKUserScript(source: gamchugi, injectionTime: .atDocumentEnd, forMainFrameOnly: true))
        c.userContentController.add(NokhwaJikim.shared, name: "lvdNokhwa")   // 1.6.0 녹화 지킴이
        c.userContentController.add(GyoyukGirok.shared, name: "lvdGirok")    // 1.16.5 녹화 화면 이용 기록
        c.userContentController.add(SeoljeongYeolgi.shared, name: "lvdSeoljeong")   // 1.17.1 화면의 「설정 열기」 단추
        c.userContentController.addUserScript(WKUserScript(source: GyoyukGirok.JS, injectionTime: .atDocumentEnd, forMainFrameOnly: true))
        let w = WKWebView(frame: .zero, configuration: c)
        w.uiDelegate = context.coordinator
        w.navigationDelegate = GyoyukGirok.shared
        w.isOpaque = false
        BangsongsaMakgi.butigi(w) { if let u = URL(string: juso) { w.load(URLRequest(url: u, cachePolicy: .reloadIgnoringLocalCacheData)) } }   // 1.17.1 (261116) 폰이 담아 둔 옛 화면 말고 늘 새 화면을 받음 — 11-16 고친 화면이 폰에 안 보이던 일   // 261109 방송사 채널 막기
        return w
    }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
