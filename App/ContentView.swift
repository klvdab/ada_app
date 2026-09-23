// 웹 화면 — 나스의 웹을 그대로 보여 주고, 뒤로 가기로 앱 밖에 나가지 않게 지킵니다.
import SwiftUI
import WebKit

let ADA_HOME = URL(string: "https://lvd.ada.or.kr/app/")!   // 앱 대문(나스). 이 주소만 바꾸면 안의 내용은 나스에서 정합니다.

struct ContentView: View {
    var body: some View {
        WebScreen()
            .background(Color(.systemBackground))
    }
}

struct WebScreen: UIViewRepresentable {
    func makeCoordinator() -> WebBridge { WebBridge() }

    func makeUIView(context: Context) -> WKWebView {
        let cfg = WKWebViewConfiguration()
        cfg.allowsInlineMediaPlayback = true
        cfg.mediaTypesRequiringUserActionForPlayback = []
        cfg.websiteDataStore = .default()
        let uc = WKUserContentController()
        uc.add(context.coordinator, name: "ada")
        if let url = Bundle.main.url(forResource: "app_bridge", withExtension: "js"),
           let js = try? String(contentsOf: url) {
            uc.addUserScript(WKUserScript(source: js, injectionTime: .atDocumentStart, forMainFrameOnly: false))
        }
        cfg.userContentController = uc
        let web = WKWebView(frame: .zero, configuration: cfg)
        web.navigationDelegate = context.coordinator
        web.uiDelegate = context.coordinator
        web.allowsBackForwardNavigationGestures = true
        web.scrollView.contentInsetAdjustmentBehavior = .never
        web.isAccessibilityElement = false
        context.coordinator.web = web
        web.load(URLRequest(url: ADA_HOME))
        return web
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
