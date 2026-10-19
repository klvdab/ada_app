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
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let s = message.body as? String else { return }
        majimak = Date().timeIntervalSince1970
        switch s {
        case "sijak", "salaitda": jung = true; meomchum = false
        case "meomchum": jung = true; meomchum = true
        case "kkeut": jung = false; meomchum = false
        default: break
        }
    }
}

struct GyoyukView: View {
    var jemok = "현장영상해설교육 녹화"
    var juso = GyoyukPan.JUSO
    let dwiro: () -> Void
    @State private var mutgi = false
    var body: some View {
        VStack(spacing: 10) {
            Danchu(geul: "뒤로") { mutgi = true }
            GyoyukPan(juso: juso)
            Danchu(geul: "뒤로") { mutgi = true }
        }
        .padding(12)
        .accessibilityAction(.escape) { mutgi = true }
        .onAppear { UIApplication.shared.isIdleTimerDisabled = true }    // 녹화하는 동안 화면이 꺼지지 않게
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
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
        let w = WKWebView(frame: .zero, configuration: c)
        w.uiDelegate = context.coordinator
        w.isOpaque = false
        if let u = URL(string: juso) { w.load(URLRequest(url: u)) }
        return w
    }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
