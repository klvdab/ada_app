// 폰 ↔ 워치 — 마지막 안내와 다음 갈림길을 워치로 보내고, 워치가 누른 단추를 웹에 넘깁니다.
import WatchConnectivity

final class WatchLink: NSObject, WCSessionDelegate {
    static let shared = WatchLink()
    var onRequest: ((String) -> Void)?
    private var last: [String: Any] = [:]

    func activate() {
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    // 최근 상태를 워치에 넘김 — 워치가 꺼져 있어도 다음에 켜질 때 받습니다(applicationContext)
    func push(_ data: [String: Any]) {
        for (k, v) in data { last[k] = v }
        last["ttae"] = Date().timeIntervalSince1970
        guard WCSession.isSupported(), WCSession.default.activationState == .activated else { return }
        try? WCSession.default.updateApplicationContext(last)
        if WCSession.default.isReachable { WCSession.default.sendMessage(last, replyHandler: nil, errorHandler: nil) }
    }

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {}
    func sessionDidBecomeInactive(_ session: WCSession) {}
    func sessionDidDeactivate(_ session: WCSession) { session.activate() }

    // 워치 → 폰: {"what":"jari"|"daeum"|"mal"|"yudogi"}
    func session(_ session: WCSession, didReceiveMessage message: [String: Any], replyHandler: @escaping ([String: Any]) -> Void) {
        let what = (message["what"] as? String) ?? ""
        DispatchQueue.main.async { self.onRequest?(what) }
        replyHandler(last)
    }
    func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        let what = (message["what"] as? String) ?? ""
        DispatchQueue.main.async { self.onRequest?(what) }
    }
}
