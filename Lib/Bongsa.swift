// AI점자도서관 앱 — 봉사자 방과 카카오 손끝편지 (판 0.8.9, 빌드 261125-L32, 도클)
// 이사장님 승인 2026-11-25 21시 14분 「1」: 앱을 떠나지 않고 앱 안에서 봉사자 방(점자 배우기·카카오 손끝편지)을 연다.
//  - 앱의 회원 열쇠를 봉사자 방에 넘겨, 회원 등록을 다시 하지 않아도 점역과 손끝편지를 쓴다.
//  - 「카카오톡으로 보내기」가 부르는 카카오톡 주소(kakaolink: 등)는 앱이 받아 카카오톡을 연다. 카카오톡이 없으면 앱스토어의 카카오톡을 연다.
//  - 목소리 녹음은 마이크 허락을 묻고, 막혀 있으면 웹 화면의 「설정 열기」가 이 앱 설정을 연다(다리 libApp, ham=seoljeong).
//  - 수료증·카드 그림·점자 파일 저장은 공유 창으로 넘겨 「이미지 저장」이나 「파일에 저장」으로 둔다(다리 libApp, ham=jeojang).
//  - 받은 손끝편지의 「AI점자도서관 앱으로 열기」(adalib://pyeonji?p=편지번호)는 RootView 의 onOpenURL 이 이 화면으로 연다.
//  - 도서관 주소(ailib.ada.or.kr 의 /doseogwan/·/nas/) 밖의 주소는 사파리로 연다(카카오톡 선물하기·홈페이지 후원 등).
import SwiftUI
import WebKit
import UIKit

enum BongsaJuso {
    static let host = "ailib.ada.or.kr"
    static func url(_ gil: String) -> URL { URL(string: "https://\(host)/doseogwan/\(gil)") ?? URL(string: "https://\(host)/doseogwan/bongsa.html")! }
    /// 받은 손끝편지 주소 adalib://pyeonji?p=편지번호 → 앱 안 길(pyeonji.html?p=…)
    static func deulgil(_ u: URL) -> String? {
        guard u.scheme?.lowercased() == "adalib", u.host?.lowercased() == "pyeonji" else { return nil }
        let p = URLComponents(url: u, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "p" })?.value ?? ""
        let id = p.lowercased().filter { ("a"..."z").contains($0) || ("0"..."9").contains($0) }
        return id.count == 10 ? "pyeonji.html?p=\(id)" : nil
    }
    /// 웹 화면이 열리기 전에 회원 열쇠와 이름을 봉사자 방 저장소(dsg_tk, dsg_ireum)에 넣는 글
    static func juip() -> String {
        func js(_ s: String) -> String {
            let d = (try? JSONSerialization.data(withJSONObject: [s])) ?? Data("[\"\"]".utf8)
            let a = String(data: d, encoding: .utf8) ?? "[\"\"]"
            return String(a.dropFirst().dropLast())
        }
        let tk = Hoewon.shared.yeolsoe, ir = Hoewon.shared.ireum
        var s = "window.LIB_APP={pan:'ios'};"
        if !tk.isEmpty { s += "try{localStorage.setItem('dsg_tk',JSON.stringify(\(js(tk))));" + (ir.isEmpty ? "" : "if(!localStorage.getItem('dsg_ireum'))localStorage.setItem('dsg_ireum',JSON.stringify(\(js(ir))));") + "}catch(e){}" }
        return s
    }
}

struct BongsaView: View {
    let gil: String
    var body: some View {
        BongsaWeb(url: BongsaJuso.url(gil))
            .navigationTitle(gil.hasPrefix("pyeonji") ? "손끝편지" : "봉사자 방")
            .navigationBarTitleDisplayMode(.inline)
    }
}

struct BongsaWeb: UIViewRepresentable {
    let url: URL
    func makeCoordinator() -> BongsaKoordi { BongsaKoordi() }
    func makeUIView(context: Context) -> WKWebView {
        let cfg = WKWebViewConfiguration()
        cfg.allowsInlineMediaPlayback = true
        cfg.mediaTypesRequiringUserActionForPlayback = []
        let uc = cfg.userContentController
        uc.add(context.coordinator, name: "libApp")
        uc.addUserScript(WKUserScript(source: BongsaJuso.juip(), injectionTime: .atDocumentStart, forMainFrameOnly: true))
        let w = WKWebView(frame: .zero, configuration: cfg)
        w.navigationDelegate = context.coordinator
        w.uiDelegate = context.coordinator
        w.allowsBackForwardNavigationGestures = true
        w.isOpaque = false
        context.coordinator.web = w
        w.load(URLRequest(url: url))
        return w
    }
    func updateUIView(_ w: WKWebView, context: Context) {}
    static func dismantleUIView(_ w: WKWebView, coordinator: BongsaKoordi) {
        w.configuration.userContentController.removeScriptMessageHandler(forName: "libApp")
    }
}

final class BongsaKoordi: NSObject, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler {
    weak var web: WKWebView?

    private func anieGil(_ u: URL) -> Bool {
        u.host?.lowercased() == BongsaJuso.host && (u.path.hasPrefix("/doseogwan/") || u.path.hasPrefix("/nas/"))
    }

    func webView(_ w: WKWebView, decidePolicyFor a: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let u = a.request.url else { decisionHandler(.cancel); return }
        let s = (u.scheme ?? "").lowercased()
        if s == "about" || s == "blob" || s == "data" { decisionHandler(.allow); return }
        if s == "http" || s == "https" {
            if anieGil(u) || a.targetFrame?.isMainFrame == false { decisionHandler(.allow); return }
            UIApplication.shared.open(u)   // 도서관 밖 주소는 사파리로
            decisionHandler(.cancel); return
        }
        // 카카오톡(kakaolink:, kakaotalk:)·앱스토어 등 — 앱이 받아 연다
        UIApplication.shared.open(u, options: [:]) { ok in
            if !ok && s.hasPrefix("kakao"), let st = URL(string: "itms-apps://apps.apple.com/app/id362057947") {
                UIApplication.shared.open(st)   // 카카오톡이 없으면 앱스토어의 카카오톡
            }
        }
        decisionHandler(.cancel)
    }

    /// target=_blank — 도서관 화면이면 이 화면에서, 밖이면 사파리로
    func webView(_ w: WKWebView, createWebViewWith c: WKWebViewConfiguration, for a: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if let u = a.request.url { if anieGil(u) { w.load(URLRequest(url: u)) } else { UIApplication.shared.open(u) } }
        return nil
    }

    /// 목소리 녹음 — 도서관 화면이 마이크를 청하면 허락(처음 한 번은 폰이 마이크 허락을 묻는다)
    func webView(_ w: WKWebView, requestMediaCapturePermissionFor origin: WKSecurityOrigin, initiatedByFrame frame: WKFrameInfo, type: WKMediaCaptureType, decisionHandler: @escaping (WKPermissionDecision) -> Void) {
        decisionHandler(origin.host.lowercased() == BongsaJuso.host ? .grant : .deny)
    }

    func webView(_ w: WKWebView, runJavaScriptAlertPanelWithMessage m: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
        UIAccessibility.post(notification: .announcement, argument: m); completionHandler()
    }

    func userContentController(_ uc: WKUserContentController, didReceive m: WKScriptMessage) {
        guard let d = m.body as? [String: Any], let ham = d["ham"] as? String else { return }
        if ham == "seoljeong" {
            if let u = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(u) }
            return
        }
        if ham == "jeojang", let b = d["b64"] as? String, let data = Data(base64Encoded: b) {
            var nm = (d["nm"] as? String ?? "").replacingOccurrences(of: "/", with: " ").trimmingCharacters(in: .whitespaces)
            if nm.isEmpty { nm = "AI점자도서관_저장" }
            let f = FileManager.default.temporaryDirectory.appendingPathComponent(nm)
            do { try data.write(to: f, options: .atomic) } catch { malhagi("저장하지 못했습니다."); return }
            let av = UIActivityViewController(activityItems: [f], applicationActivities: nil)
            guard let top = BongsaKoordi.topVC() else { malhagi("저장 창을 열지 못했습니다."); return }
            top.present(av, animated: true) { UIAccessibility.post(notification: .announcement, argument: "저장 창을 열었습니다. 그림은 「이미지 저장」, 파일은 「파일에 저장」을 고르십시오.") }
        }
    }

    private func malhagi(_ t: String) { UIAccessibility.post(notification: .announcement, argument: t) }

    static func topVC() -> UIViewController? {
        let sc = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        var v = sc.flatMap { $0.windows }.first { $0.isKeyWindow }?.rootViewController
        while let p = v?.presentedViewController { v = p }
        return v
    }
}
