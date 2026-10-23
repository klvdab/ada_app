// LVD 방송(듣는 앱) 아이폰 — 이용 기록 (2.4.1판, 빌드 261023-LI6, 새방클, 이사장님 지시 2026-10-22 「청취자가 들어가고 나가는 내용 확인」)
// 듣기 상태가 바뀔 때마다(찾는 중·BYOD 방송 연결·벗어남·인터넷 방송 연결·끊김·멈춤) 그 말과 듣는 자리를 남김.
// 이사장님 원칙(2026-10-13): 누가 어떤 판을 깔고 쓰는지 알아 이용자가 겪는 어려움을 찾아 풀어 드림.
// 앱이 겪은 일을 협회 서버(jeom/applog.php → 나스 applog/날짜.txt)에 남깁니다.
// 남기는 것: 앱 판·빌드, 아이폰 판, 폰마다 뽑은 구별 번호(8자리, 이름·전화와 잇지 않음), 상태 말과 듣는 자리.
// 남기지 않는 것: 이름, 전화번호, 위치.
// 통신이 끊기면 폰에 쌓아 두었다가(앱을 껐다 켜도 남음) 이어지면 보냅니다.
import Foundation
import UIKit

final class BfbGirok {
    static let shared = BfbGirok()

    private let q = DispatchQueue(label: "bfb.girok")
    private var jul: [[String: Any]] = []
    private var bonaeneunJung = false
    private var sigye: Timer?
    private let pail: URL
    private let url = URL(string: "https://lvd.ada.or.kr/jeom/applog.php")!
    private let ios = UIDevice.current.systemVersion
    let dev: String = {
        let k = "bfbGirokDev"
        if let v = UserDefaults.standard.string(forKey: k) { return v }
        let v = String(UUID().uuidString.prefix(8))
        UserDefaults.standard.set(v, forKey: k)
        return v
    }()

    init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("bfb", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        pail = dir.appendingPathComponent("girok_jul.json")
        if let d = try? Data(contentsOf: pail),
           let a = try? JSONSerialization.jsonObject(with: d) as? [[String: Any]] {
            jul = a
        }
    }

    /// 앱이 켜질 때 한 번 — 30초마다 보내고, 앱이 뒤로 갈 때 폰에 저장
    func sijak() {
        guard sigye == nil else { return }
        namgi("bfb_kyeojim")
        sigye = Timer.scheduledTimer(withTimeInterval: 30, repeats: true) { [weak self] _ in self?.bonaegi() }
        NotificationCenter.default.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in
            self?.jeojang(); self?.bonaegi()
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) { [weak self] in self?.bonaegi() }
    }

    func namgi(_ e: String, _ d: [String: Any] = [:]) {
        var row: [String: Any] = ["t": Date().timeIntervalSince1970, "e": e]
        for (k, v) in d where JSONSerialization.isValidJSONObject([k: v]) { row[(k == "t" || k == "e") ? k + "_" : k] = v }
        q.async {
            self.jul.append(row)
            if self.jul.count > 1000 { self.jul.removeFirst(self.jul.count - 1000) }
        }
    }

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
            let body: [String: Any] = ["dev": self.dev, "app": "bfb-ios " + Pan.PAN + "(" + Pan.BILD + ")",
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
                }
            }.resume()
        }
    }
}
