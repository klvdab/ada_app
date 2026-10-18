// LVD 송출 아이폰 — 라이브 현장영상해설 방송·현장영상해설교육 녹화 화면 (1.4.0판, 빌드 261016-BI6, 방클, 이사장님 지시 「지금 앱으로 완성」 2026-10-16)
// 배프 교육 녹화 화면(https://lvd.ada.or.kr/bfblive/gyoyuk.html)을 앱 안에서 엽니다. 녹화·중계·나스 담기는 그 화면이 그대로 합니다.
// 앱이 하는 일: 카메라·마이크 허락을 화면에 넘겨 줌, 이 화면에 있는 동안 화면이 꺼지지 않게 붙잡음,
//   화면 쪽 뒤로 단추(배프 대문으로 가는 것)를 감추고 앱의 뒤로만 남김, 뒤로는 한 번 더 물어 녹화가 실수로 끊기지 않게 함.
import SwiftUI
import WebKit
import UIKit

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
        let w = WKWebView(frame: .zero, configuration: c)
        w.uiDelegate = context.coordinator
        w.isOpaque = false
        if let u = URL(string: juso) { w.load(URLRequest(url: u)) }
        return w
    }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
