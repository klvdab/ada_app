// LVD 방송 아이폰(듣는 앱) — 인터넷 생방송·지난 방송 다시 듣기·TV해설방송·TV·라디오 화면 (2.3.0판, 빌드 261019-LI4, 방클, 이사장님 결정 2026-10-19)
// 2.3.0: ① 다시 듣기의 내려받기(소리 mp3·받아쓰기 글)를 앱 안에서 받아 「파일」 앱 등에 저장 — 와이파이가 아니면 「데이터로 ○○메가를 받습니다. 받을까요?」를 먼저 물음
//        ② 화면을 잠가도 다시 듣기가 이어짐(뒤에서 소리), 전화가 오면 멈춤(아이폰이 소리를 끊어 줌)
//        ③ 누리집이 앱을 알아보도록 「LVDBangsong」 표지를 붙임 — 앱에서는 영상 내려받기 대신 「영상은 컴퓨터의 웹에서 받으실 수 있습니다」
// 2.1.0: 누리집 쪽 뒤로 단추(대문으로 가는 것)를 감추어 앱의 뒤로만 남김.
// LVD 방송 누리집 화면을 앱 안에서 엽니다. 맨 위와 맨 아래에 「뒤로」, 두 손가락 문지르기로도 돌아옵니다.
import SwiftUI
import WebKit
import AVFoundation
import Network

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

/// 와이파이인지 — 셀룰러(데이터)면 isExpensive
final class MangSalpim {
    static let shared = MangSalpim()
    private let mon = NWPathMonitor()
    private(set) var deiteo = false
    private init() {
        mon.pathUpdateHandler = { [weak self] p in self?.deiteo = p.isExpensive || p.usesInterfaceType(.cellular) }
        mon.start(queue: DispatchQueue(label: "lvd.mang"))
    }
}

struct WebPan: UIViewRepresentable {
    let juso: String
    func makeCoordinator() -> Batgi { Batgi() }
    func makeUIView(context: Context) -> WKWebView {
        _ = MangSalpim.shared
        // 화면을 잠가도 다시 듣기가 이어지게(뒤에서 소리). 전화가 오면 아이폰이 소리를 끊어 멈춤
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio, options: [])
        let c = WKWebViewConfiguration()
        c.allowsInlineMediaPlayback = true
        c.mediaTypesRequiringUserActionForPlayback = []
        c.applicationNameForUserAgent = "LVDBangsong/2.3.0"
        // 누리집 화면이 스스로 붙이는 뒤로·첫 화면 단추(누리집 대문으로 감)를 감춤 — 보이스오버에도 안 읽힘
        // 2.5.0 앱 안에서는 누리집의 「앱으로 더 편하게 듣기」(#btnAp·#apBox)도 감춤 — 이미 앱이므로
        let gamchugi = "(function(){var s=document.createElement('style');s.textContent='.dwiro-btn,#btnDwi1,#btnDwi2,#homeBtn,#btnAp,#apBox{display:none!important}';(document.head||document.documentElement).appendChild(s);})();"
        c.userContentController.addUserScript(WKUserScript(source: gamchugi, injectionTime: .atDocumentEnd, forMainFrameOnly: true))
        let w = WKWebView(frame: .zero, configuration: c)
        w.isOpaque = false
        w.navigationDelegate = context.coordinator
        context.coordinator.web = w
        if let u = URL(string: juso) { w.load(URLRequest(url: u)) }
        return w
    }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

/// 내려받기 — 첨부(attachment)로 오는 파일은 물어본 뒤 받아 공유 창(「파일에 저장」)을 띄움
final class Batgi: NSObject, WKNavigationDelegate, WKDownloadDelegate {
    weak var web: WKWebView?
    private var dst: URL?
    private var irum = "파일"

    private func malhagi(_ t: String) { UIAccessibility.post(notification: .announcement, argument: t) }
    private func wi() -> UIViewController? {
        let s = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var v = s?.windows.first(where: { $0.isKeyWindow })?.rootViewController
        while let p = v?.presentedViewController { v = p }
        return v
    }
    func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse, decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
        guard let h = navigationResponse.response as? HTTPURLResponse,
              let cd = h.value(forHTTPHeaderField: "Content-Disposition"), cd.lowercased().contains("attachment") else {
            decisionHandler(.allow); return
        }
        irum = navigationResponse.response.suggestedFilename ?? "파일"
        let n = navigationResponse.response.expectedContentLength
        if MangSalpim.shared.deiteo {
            let meg = n > 0 ? max(1, Int((Double(n) / 1048576).rounded())) : 0
            let al = UIAlertController(title: "데이터로 받기", message: meg > 0 ? "데이터로 \(meg)메가를 받습니다. 받을까요?" : "데이터로 받습니다. 받을까요?", preferredStyle: .alert)
            al.addAction(UIAlertAction(title: "받기", style: .default) { _ in decisionHandler(.download) })
            al.addAction(UIAlertAction(title: "그만두기", style: .cancel) { _ in decisionHandler(.cancel) })
            if let v = wi() { v.present(al, animated: true) } else { decisionHandler(.cancel) }
        } else {
            decisionHandler(.download)
        }
    }
    func webView(_ webView: WKWebView, navigationResponse: WKNavigationResponse, didBecome download: WKDownload) {
        download.delegate = self
        malhagi("내려받기를 시작합니다.")
    }
    func download(_ download: WKDownload, decideDestinationUsing response: URLResponse, suggestedFilename: String, completionHandler: @escaping (URL?) -> Void) {
        let d = FileManager.default.temporaryDirectory.appendingPathComponent("lvd_batgi", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        let u = d.appendingPathComponent(suggestedFilename.isEmpty ? irum : suggestedFilename)
        try? FileManager.default.removeItem(at: u)
        dst = u
        completionHandler(u)
    }
    func downloadDidFinish(_ download: WKDownload) {
        guard let u = dst else { return }
        malhagi("다 받았습니다. 저장할 곳을 고르십시오. 파일에 저장을 고르시면 파일 앱에 남습니다.")
        let a = UIActivityViewController(activityItems: [u], applicationActivities: nil)
        if let v = wi() {
            a.popoverPresentationController?.sourceView = v.view
            v.present(a, animated: true)
        }
    }
    func download(_ download: WKDownload, didFailWithError error: Error, resumeData: Data?) {
        malhagi("내려받기가 끊겼습니다. 다시 눌러 주십시오.")
    }
}
