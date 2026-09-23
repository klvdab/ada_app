// 웹 화면 — 나스의 웹을 그대로 보여 주고, 뒤로 가기로 앱 밖에 나가지 않게 지킵니다.
// 1.0.1판 빌드 260923-3 — 대문 주소를 앱마다 따로 두게 고쳤습니다(협회 앱 / 자봉 앱).
import SwiftUI
import WebKit

// 앱 대문(나스). 각 앱의 Info.plist 에 적힌 AdaHomeURL 을 씁니다. 없으면 협회 앱 대문으로 엽니다.
let ADA_HOME: URL = {
    if let s = Bundle.main.object(forInfoDictionaryKey: "AdaHomeURL") as? String,
       let u = URL(string: s) {
        return u
    }
    return URL(string: "https://lvd.ada.or.kr/app/")!
}()

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
