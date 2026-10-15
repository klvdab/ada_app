// LVD 방송 아이폰(듣는 앱) — 인터넷 생방송·지난 방송 다시 듣기 화면 (2.0.0판, 빌드 261015-LI1, 방송클, 이사장님 승인 「1」)
// 배리어프리방송국 누리집 화면을 앱 안에서 엽니다. 맨 위와 맨 아래에 「뒤로」, 두 손가락 문지르기로도 돌아옵니다.
import SwiftUI
import WebKit

struct WebHwamyeon: View {
    let jemok: String
    let juso: String
    let dwiro: () -> Void
    var body: some View {
        VStack(spacing: 10) {
            Danchu("뒤로") { dwiro() }
            WebPan(juso: juso)
            Danchu("뒤로") { dwiro() }
        }
        .padding(12)
        .accessibilityAction(.escape) { dwiro() }
    }
}

struct WebPan: UIViewRepresentable {
    let juso: String
    func makeUIView(context: Context) -> WKWebView {
        let c = WKWebViewConfiguration()
        c.allowsInlineMediaPlayback = true
        c.mediaTypesRequiringUserActionForPlayback = []
        let w = WKWebView(frame: .zero, configuration: c)
        w.isOpaque = false
        if let u = URL(string: juso) { w.load(URLRequest(url: u)) }
        return w
    }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
