// 기록 — 폰이 겪은 일을 나스(applog.php → /volume1/lvt_data/applog/날짜.txt)에 남깁니다.
// 클이 그 자리에 없어도 실물로 확인하려는 것입니다. 말씀하신 내용이나 걸으신 길은 남기지 않습니다.
// 통신이 끊기면 폰에 쌓아 두었다가(앱을 껐다 켜도 남음) 이어지면 보냅니다.
import Foundation
import Combine
import UIKit
import CryptoKit

/// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 한 폰 표(gd) — 같은 폰에 길눈과 자봉 앱을 함께 둔 분이
/// 내 길눈의 긴급통화 요청에 내 자봉 앱이 울리지 않게, 두 앱이 같은 값을 냄.
/// identifierForVendor(같은 팀 앱끼리 한 폰에서 같음)에 "klvdab-gd" 를 섞은 SHA-256 앞 16자.
/// 길눈(GinGeup a=call)과 자봉(JabongDaegi a=daegi)이 함께 씀 — 자봉 앱도 이 파일을 싣습니다.
/// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 빈 값은 담아 두지 않음 — 첫 잠금 해제 전에 알림으로 깨어 identifierForVendor 가 없을 때
///   빈 표를 앱이 꺼질 때까지 쓰던 일. 얻을 때까지 다음에 다시 셈(안드로이드 HanPon 과 같음)
enum HanPon {
    private static var gap = ""
    private static let jamgeum = NSLock()

    static var gd: String {
        jamgeum.lock()
        defer { jamgeum.unlock() }
        if !gap.isEmpty { return gap }
        guard let v = UIDevice.current.identifierForVendor?.uuidString, !v.isEmpty else { return "" }
        let h = SHA256.hash(data: Data(("klvdab-gd" + v).utf8))
        let r = String(h.map { String(format: "%02x", $0) }.joined().prefix(16))
        gap = r
        return r
    }
}

final class Girok {
    static let shared = Girok()

    private let q = DispatchQueue(label: "gilnun.girok")
    private var jul: [[String: Any]] = []
    private var bonaeneunJung = false
    private var sigye: Timer?
    private var ssak = Set<AnyCancellable>()
    private let pail: URL
    private let url = URL(string: "https://lvd.ada.or.kr/jeom/applog.php")!
    private let ios = UIDevice.current.systemVersion
    let dev: String = {
        let k = "adaDiagDev"
        if let v = UserDefaults.standard.string(forKey: k) { return v }
        let v = String(UUID().uuidString.prefix(8))
        UserDefaults.standard.set(v, forKey: k)
        return v
    }()

    init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("gilnun", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        pail = dir.appendingPathComponent("girok_jul.json")
        if let d = try? Data(contentsOf: pail),
           let a = try? JSONSerialization.jsonObject(with: d) as? [[String: Any]] {
            jul = a
        }
    }

    func sijak() {
        guard sigye == nil else { return }
        sigye = Timer.scheduledTimer(withTimeInterval: 20, repeats: true) { [weak self] _ in self?.bonaegi() }
        Tongsin.shared.yeongyeolBakkwim
            .filter { $0 }
            .sink { [weak self] _ in self?.bonaegi() }
            .store(in: &ssak)
    }

    func namgi(_ e: String, _ d: [String: Any] = [:]) {
        var row: [String: Any] = ["t": Date().timeIntervalSince1970, "e": e]
        for (k, v) in d where JSONSerialization.isValidJSONObject([k: v]) { row[k] = v }
        q.async {
            self.jul.append(row)
            if self.jul.count > 2000 { self.jul.removeFirst(self.jul.count - 2000) }
        }
    }

    var ssainSu: Int { q.sync { jul.count } }

    func jeojang() {
        q.async {
            if let d = try? JSONSerialization.data(withJSONObject: self.jul) {
                try? d.write(to: self.pail, options: .atomic)
            }
        }
    }

    func bonaegi() {
        q.async {
            guard !self.bonaeneunJung, !self.jul.isEmpty else { return }
            let mukeum = Array(self.jul.prefix(100))
            // 2.59.0 (점검) 자봉 앱도 이 기록 부품을 함께 써서 「gilnun-app 길눈 판」으로 잘못 찍혔음 — 앱 번호로 가림
            let jabong = (Bundle.main.bundleIdentifier ?? "").contains("jabong")
            let ireum = jabong ? "jabong-app " + ((Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "")
                               : "gilnun-app " + Pan.pan + "(" + Pan.bild + ")"
            let body: [String: Any] = ["dev": self.dev, "app": ireum,
                                       "ios": self.ios, "ev": mukeum]
            guard let data = try? JSONSerialization.data(withJSONObject: body) else {
                self.jul.removeFirst(mukeum.count)
                return
            }
            self.bonaeneunJung = true
            var r = URLRequest(url: self.url)
            r.httpMethod = "POST"
            r.setValue("application/json", forHTTPHeaderField: "Content-Type")
            r.httpBody = data
            r.timeoutInterval = 15
            URLSession.shared.dataTask(with: r) { _, res, _ in
                let ok = (res as? HTTPURLResponse)?.statusCode == 200
                self.q.async {
                    self.bonaeneunJung = false
                    guard ok else { return }
                    self.jul.removeFirst(min(mukeum.count, self.jul.count))
                    if let d = try? JSONSerialization.data(withJSONObject: self.jul) {
                        try? d.write(to: self.pail, options: .atomic)
                    }
                    if !self.jul.isEmpty { DispatchQueue.main.async { self.bonaegi() } }
                }
            }.resume()
        }
    }
}
