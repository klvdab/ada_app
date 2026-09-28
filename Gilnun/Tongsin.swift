// 통신 — 나스 자료 창고(https://lvd.ada.or.kr/jeom/…)로 가는 통로 하나.
// ① 한 번 실패해도 두 번 더 해 봄 ② 받은 것은 폰에 담아 두었다가 통신이 끊기면 그것으로 버팀
// ③ 통신이 끊기고 이어지는 것을 알아채 다른 엔진에 알림
import Foundation
import Network
import Combine
import CryptoKit

final class Tongsin: ObservableObject {
    static let shared = Tongsin()

    let bon = URL(string: "https://lvd.ada.or.kr/jeom/")!
    @Published private(set) var yeongyeol = true
    let yeongyeolBakkwim = PassthroughSubject<Bool, Never>()
    private(set) var kkeunkimSu = 0

    private let mon = NWPathMonitor()
    private let ses: URLSession
    private let gamchum: URL

    struct Dap {
        let data: Data
        let badadunGeot: Bool   // 통신이 안 되어 폰에 받아 둔 것을 드림
    }

    init() {
        let c = URLSessionConfiguration.default
        c.timeoutIntervalForRequest = 12
        c.timeoutIntervalForResource = 30
        c.waitsForConnectivity = false
        ses = URLSession(configuration: c)
        gamchum = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("gilnun_tongsin", isDirectory: true)
        try? FileManager.default.createDirectory(at: gamchum, withIntermediateDirectories: true)
    }

    func sijak() {
        mon.pathUpdateHandler = { [weak self] p in
            let ok = (p.status == .satisfied)
            DispatchQueue.main.async {
                guard let s = self, s.yeongyeol != ok else { return }
                s.yeongyeol = ok
                if !ok { s.kkeunkimSu += 1 }
                Girok.shared.namgi("tongsin", ["ok": ok])
                s.yeongyeolBakkwim.send(ok)
            }
        }
        mon.start(queue: DispatchQueue(label: "gilnun.tongsin"))
    }

    /// 나스 자료 창고에서 받기. 예: get("yeok.php", ["a": "chatgi", "q": "약수"])
    func get(_ pail: String, _ q: [String: String] = [:]) async throws -> Dap {
        guard var c = URLComponents(url: bon.appendingPathComponent(pail), resolvingAgainstBaseURL: false) else {
            throw URLError(.badURL)
        }
        if !q.isEmpty {
            c.queryItems = q.sorted { $0.key < $1.key }.map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        guard let url = c.url else { throw URLError(.badURL) }
        let k = Tongsin.kiMandeulgi(url.absoluteString)
        var majimakOryu: Error = URLError(.unknown)
        for beon in 0..<3 {
            do {
                let (d, r) = try await ses.data(from: url)
                if let h = r as? HTTPURLResponse, h.statusCode == 200 {
                    try? d.write(to: gamchum.appendingPathComponent(k), options: .atomic)
                    return Dap(data: d, badadunGeot: false)
                }
                majimakOryu = URLError(.badServerResponse)
            } catch {
                majimakOryu = error
            }
            if beon < 2 { try? await Task.sleep(nanoseconds: UInt64(beon + 1) * 800_000_000) }
        }
        if let d = try? Data(contentsOf: gamchum.appendingPathComponent(k)) {
            return Dap(data: d, badadunGeot: true)
        }
        Girok.shared.namgi("tongsin_oryu", ["pail": pail])
        throw majimakOryu
    }

    /// 새로고침 — 받아 둔 자료를 비움(설정과 여정은 그대로)
    func gamchumBiugi() {
        let fm = FileManager.default
        if let l = try? fm.contentsOfDirectory(at: gamchum, includingPropertiesForKeys: nil) {
            for u in l { try? fm.removeItem(at: u) }
        }
        URLCache.shared.removeAllCachedResponses()
        Girok.shared.namgi("saerogochim", [:])
    }

    static func kiMandeulgi(_ s: String) -> String {
        SHA256.hash(data: Data(s.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}
