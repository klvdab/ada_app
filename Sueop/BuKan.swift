// LVD 수업 아이폰 0.2.0 「함께하기 칸」 (빌드 261028-SI3, 방클, 이사장님 승인 2026-10-28 「1」 앱 안에 웹 기능 칸 붙이기)
// 수업 화면 안에 웹 「함께하기」(lvd.ada.or.kr/sueop/bu.html)를 끼워, 웹 수업 보기와 똑같은 질문 손들기·예상문제 풀이·검정 시험·화상 수업을 씀.
// 장 그림과 글은 지금처럼 앱이 직접 보여 줌. 웹을 고치면 앱도 함께 따라 고쳐짐(기능이 어긋나지 않게).
// 칸은 하나만 만들어 두고 도움말을 보고 와도 그대로 살려 둠(화상 수업이 끊기지 않게). 수업에서 나가면 닫음.
// 칸 높이는 웹이 알려 주는 내용 높이에 맞춤(바깥 화면과 함께 쓸어 넘김). 카메라·마이크는 lvd.ada.or.kr 에만 허락.
import SwiftUI
import WebKit

final class BuKan: NSObject, ObservableObject, WKUIDelegate, WKNavigationDelegate, WKScriptMessageHandler {
    static let shared = BuKan()
    static let JUSO = "https://lvd.ada.or.kr/sueop/bu.html"

    @Published var nopi: CGFloat = 90
    private(set) var bang = ""

    lazy var web: WKWebView = {
        let c = WKWebViewConfiguration()
        c.allowsInlineMediaPlayback = true
        c.mediaTypesRequiringUserActionForPlayback = []
        c.applicationNameForUserAgent = "LVDSueop-ios/\(Pan.PAN)"
        c.userContentController.add(JjokjiDari(self), name: "nopi")
        let js = "(function(){function n(){try{var h=Math.ceil(document.body.getBoundingClientRect().height)+12;window.webkit.messageHandlers.nopi.postMessage(h);}catch(e){}}" +
                 "try{new ResizeObserver(n).observe(document.body);}catch(e){}window.addEventListener('load',n);setInterval(n,1500);n();})();"
        c.userContentController.addUserScript(WKUserScript(source: js, injectionTime: .atDocumentEnd, forMainFrameOnly: true))
        let w = WKWebView(frame: .zero, configuration: c)
        w.uiDelegate = self
        w.navigationDelegate = self
        w.isOpaque = false
        w.backgroundColor = .clear
        w.scrollView.backgroundColor = .clear
        w.scrollView.isScrollEnabled = false   // 바깥 수업 화면과 함께 쓸어 넘김
        return w
    }()

    /// 이 방의 함께하기 칸을 엶(같은 방이면 그대로 둠)
    func yeolgi(_ b: String) {
        guard !b.isEmpty, b != bang, let u = URL(string: BuKan.JUSO + "?bang=\(b)&app=ios") else { return }
        bang = b
        web.load(URLRequest(url: u))
    }

    /// 수업에서 나가면 닫음 — 화상에 들어가 있었으면 나가기도 웹이 알아서 함(pagehide)
    func datgi() {
        guard !bang.isEmpty else { return }
        bang = ""
        nopi = 90
        if let u = URL(string: "about:blank") { web.load(URLRequest(url: u)) }
    }

    func userContentController(_ u: WKUserContentController, didReceive m: WKScriptMessage) {
        guard m.name == "nopi", let n = m.body as? NSNumber else { return }
        let v = max(60, min(CGFloat(truncating: n), 6000))
        if abs(v - nopi) > 2 { DispatchQueue.main.async { self.nopi = v } }
    }

    // 화상 수업 — 교육생이 「화상 수업 들어가기」를 누를 때 폰이 한 번 여쭘(협회 주소에만)
    func webView(_ w: WKWebView, requestMediaCapturePermissionFor origin: WKSecurityOrigin, initiatedByFrame frame: WKFrameInfo,
                 type: WKMediaCaptureType, decisionHandler: @escaping (WKPermissionDecision) -> Void) {
        decisionHandler(origin.host == "lvd.ada.or.kr" ? .grant : .deny)   // 앱의 카메라·마이크 허락은 아이폰이 처음 한 번 여쭘
    }

    // 협회 밖 주소는 사파리로
    func webView(_ w: WKWebView, decidePolicyFor a: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        if let u = a.request.url, let h = u.host, h != "lvd.ada.or.kr", a.navigationType == .linkActivated {
            UIApplication.shared.open(u); decisionHandler(.cancel); return
        }
        decisionHandler(.allow)
    }
    func webView(_ w: WKWebView, createWebViewWith c: WKWebViewConfiguration, for a: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if let u = a.request.url { UIApplication.shared.open(u) }
        return nil
    }
}

/// 웹 칸이 BuKan 을 붙잡아 놓지 않게 건너 주는 다리
final class JjokjiDari: NSObject, WKScriptMessageHandler {
    weak var d: WKScriptMessageHandler?
    init(_ d: WKScriptMessageHandler) { self.d = d }
    func userContentController(_ u: WKUserContentController, didReceive m: WKScriptMessage) { d?.userContentController(u, didReceive: m) }
}

/// 수업 화면에 끼우는 칸 — 같은 웹 칸을 옮겨 붙여 화상이 끊기지 않게
struct BuKanView: UIViewRepresentable {
    func makeUIView(context: Context) -> UIView {
        let v = UIView()
        v.backgroundColor = .clear
        return v
    }
    func updateUIView(_ v: UIView, context: Context) {
        let w = BuKan.shared.web
        if w.superview !== v {
            w.removeFromSuperview()
            w.translatesAutoresizingMaskIntoConstraints = false
            v.addSubview(w)
            NSLayoutConstraint.activate([
                w.leadingAnchor.constraint(equalTo: v.leadingAnchor), w.trailingAnchor.constraint(equalTo: v.trailingAnchor),
                w.topAnchor.constraint(equalTo: v.topAnchor), w.bottomAnchor.constraint(equalTo: v.bottomAnchor)
            ])
        }
    }
}
