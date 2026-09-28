// 기록 — 폰이 겪은 일을 나스(applog.php → /volume1/lvt_data/applog/날짜.txt)에 남깁니다.
// 클이 그 자리에 없어도 실물로 확인하려는 것입니다. 말씀하신 내용이나 걸으신 길은 남기지 않습니다.
// 통신이 끊기면 폰에 쌓아 두었다가(앱을 껐다 켜도 남음) 이어지면 보냅니다.
import Foundation
import Combine
import UIKit

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
            let body: [String: Any] = ["dev": self.dev, "app": "gilnun-app " + Pan.pan + "(" + Pan.bild + ")",
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
