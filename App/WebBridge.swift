// 웹과 앱 사이의 다리 — 웹이 window.webkit.messageHandlers.ada.postMessage({a:"…"}) 로 부르고,
// 앱은 window.adaApp.batda("이름", {…}) 를 불러 웹에 돌려줍니다.
import WebKit
import UIKit

final class WebBridge: NSObject, WKScriptMessageHandler, WKNavigationDelegate, WKUIDelegate {
    weak var web: WKWebView?
    static let allowedHosts: Set<String> = ["lvd.ada.or.kr", "ada.or.kr", "www.ada.or.kr"]

    override init() {
        super.init()
        LocationService.shared.onUpdate = { [weak self] lat, lon, acc, head, speed in
            self?.send("wichi", ["lat": lat, "lon": lon, "acc": acc, "head": head, "speed": speed])
        }
        RemoteCommandService.shared.onCommand = { [weak self] name in
            self?.send("gigi", ["danchu": name])
        }
        WatchLink.shared.onRequest = { [weak self] what in
            self?.send("watch", ["what": what])
        }
        NotificationService.shared.onTap = { [weak self] url in
            self?.send("allimTap", ["url": url])
        }
    }

    // 앱 → 웹
    func send(_ name: String, _ data: [String: Any]) {
        guard let web = web,
              let json = try? JSONSerialization.data(withJSONObject: data),
              let s = String(data: json, encoding: .utf8) else { return }
        let esc = s.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: "'", with: "\\'")
        let js = "window.adaApp && window.adaApp.batda('\(name)', JSON.parse('\(esc)'));"
        DispatchQueue.main.async { web.evaluateJavaScript(js, completionHandler: nil) }
    }

    // 웹 → 앱
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard message.name == "ada", let m = message.body as? [String: Any], let a = m["a"] as? String else { return }
        switch a {
        case "pan":
            send("pan", ["pan": AppInfo.pan, "build": AppInfo.build])
        case "wichi":                                   // 위치 받기 켜기/끄기. always:true 면 잠겨도 계속
            let on = (m["on"] as? Bool) ?? true
            let always = (m["always"] as? Bool) ?? false
            on ? LocationService.shared.start(always: always) : LocationService.shared.stop()
        case "allim":                                   // 알림 띄우기(잠긴 폰·워치로)
            NotificationService.shared.show(title: (m["title"] as? String) ?? "길눈",
                                            body: (m["body"] as? String) ?? "",
                                            url: (m["url"] as? String) ?? "",
                                            tag: (m["tag"] as? String) ?? "annae")
        case "allimHeorak":
            NotificationService.shared.requestPermission { ok in self.send("allimHeorak", ["ok": ok]) }
        case "jindong":                                 // 진동 무늬: left / right / arrive / short / long
            HapticService.play((m["mu"] as? String) ?? "short")
        case "mal":                                     // 마지막 안내 — 워치로 넘김
            WatchLink.shared.push(["mal": (m["t"] as? String) ?? ""])
        case "daeum":                                   // 다음 갈림길 — 워치로 넘김
            WatchLink.shared.push(["daeum": (m["t"] as? String) ?? ""])
        case "jaesaeng":                                // 기기 단추 받기: 제목과 켜기/끄기
            RemoteCommandService.shared.setTitle((m["title"] as? String) ?? "길눈 안내")
            if let on = m["on"] as? Bool { on ? RemoteCommandService.shared.activate() : RemoteCommandService.shared.deactivate() }
        case "yeolgi":                                  // 바깥 주소 열기(전화·사파리)
            if let s = m["url"] as? String, let u = URL(string: s) { UIApplication.shared.open(u) }
        case "malhagi":                                 // 앱 음성으로 읽기(웹 음성이 끊길 때 대비)
            SpeechService.shared.speak((m["t"] as? String) ?? "", rate: (m["rate"] as? Double) ?? 0.55)
        case "dwiro":                                   // 뒤로 — 앱 밖으로는 절대 나가지 않음
            if let w = web, w.canGoBack { w.goBack() } else { web?.load(URLRequest(url: ADA_HOME)) }
        default: break
        }
    }

    // 우리 주소 밖으로 나가는 링크는 사파리로, 전화·문자는 폰 기능으로
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = navigationAction.request.url else { decisionHandler(.allow); return }
        if let scheme = url.scheme, ["tel", "sms", "mailto"].contains(scheme) {
            UIApplication.shared.open(url); decisionHandler(.cancel); return
        }
        if let host = url.host, !WebBridge.allowedHosts.contains(host), navigationAction.targetFrame?.isMainFrame ?? true {
            UIApplication.shared.open(url); decisionHandler(.cancel); return
        }
        decisionHandler(.allow)
    }

    // 웹이 새 창을 열려 하면 같은 창에서 엽니다
    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                 for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if let url = navigationAction.request.url { webView.load(URLRequest(url: url)) }
        return nil
    }

    // 통신이 끊겨 못 열면 안내 글을 보여 줍니다
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        let html = """
        <html lang="ko"><meta name="viewport" content="width=device-width,initial-scale=1"><body style="font-size:1.3em;padding:1.5em">
        <p role="alert">통신이 끊겨 열지 못했습니다. 잠시 뒤 다시 시도합니다.</p>
        <button style="font-size:1.1em;padding:.8em 1.2em" onclick="location.href='\(ADA_HOME.absoluteString)'">다시 열기</button></body></html>
        """
        webView.loadHTMLString(html, baseURL: ADA_HOME)
    }
}

enum AppInfo {
    static let pan = (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "0.1.0"
    static let build = (Bundle.main.infoDictionary?["CFBundleVersion"] as? String) ?? "260909001"
}
