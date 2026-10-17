// LVD 방송 아이폰(듣는 앱) — 인터넷 생방송·지난 방송 다시 듣기·TV해설방송·TV·라디오 화면 (2.1.0판, 빌드 261016-LI2, 방클, 이사장님 승인 「1」)
// 2.1.0: 누리집 쪽 뒤로 단추(대문으로 가는 것)를 감추어 앱의 뒤로만 남김.
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
        // 누리집 화면이 스스로 붙이는 뒤로·첫 화면 단추(배리어프리방송국 대문으로 감)를 감춤 — 보이스오버에도 안 읽힘
        let gamchugi = "(function(){var s=document.createElement('style');s.textContent='.dwiro-btn,#btnDwi1,#btnDwi2,#homeBtn{display:none!important}';(document.head||document.documentElement).appendChild(s);})();"
        c.userContentController.addUserScript(WKUserScript(source: gamchugi, injectionTime: .atDocumentEnd, forMainFrameOnly: true))
        let w = WKWebView(frame: .zero, configuration: c)
        w.isOpaque = false
        if let u = URL(string: juso) { w.load(URLRequest(url: u)) }
        return w
    }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
