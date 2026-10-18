// AI점자도서관 앱 — 이용 기록 (판 0.6.3, 빌드 261014-L15, 도서관 창 클, 이사장님 승인 2026-10-13 「1」·2026-10-14 「그래」)
// 앱이 겪은 일(켜짐, 책 열기, 소리를 오래 기다림, 소리를 못 만듦, 글자를 못 가져옴)을 나스(jeom/applog.php → /volume1/lvt_data/applog/날짜.txt)에 남깁니다.
// 길눈 앱의 기록(Gilnun/Girok.swift)과 같은 자리·같은 모양이라 아침 보고에서 모든 앱을 함께 봅니다.
// 남기는 것: 앱 판·빌드, 아이폰 판, 폰마다 뽑은 구별 번호(8자리, 이름·전화와 잇지 않음), 일의 이름과 걸린 초, 글자책·소리책 갈래.
// 남기지 않는 것: 이름, 전화번호, 책 이름, 책 내용, 위치.
// 통신이 끊기면 폰에 쌓아 두었다가(앱을 껐다 켜도 남음) 이어지면 보냅니다.
import Foundation
import UIKit

final class LibGirok {
    static let shared = LibGirok()

    private let q = DispatchQueue(label: "lib.girok")
    private var jul: [[String: Any]] = []
    private var bonaeneunJung = false
    private var sigye: Timer?
    private let pail: URL
    private let url = URL(string: "https://lvd.ada.or.kr/jeom/applog.php")!
    private let ios = UIDevice.current.systemVersion
    let dev: String = {
        let k = "libGirokDev"
        if let v = UserDefaults.standard.string(forKey: k) { return v }
        let v = String(UUID().uuidString.prefix(8))
        UserDefaults.standard.set(v, forKey: k)
        return v
    }()

    init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("lib", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        pail = dir.appendingPathComponent("girok_jul.json")
        if let d = try? Data(contentsOf: pail),
           let a = try? JSONSerialization.jsonObject(with: d) as? [[String: Any]] {
            jul = a
        }
    }

    /// 앱이 켜질 때 한 번 부름 — 20초마다 보내고, 앱이 뒤로 갈 때 폰에 저장
    func sijak() {
        guard sigye == nil else { return }
        namgi("lib_kyeojim")
        sigye = Timer.scheduledTimer(withTimeInterval: 20, repeats: true) { [weak self] _ in self?.bonaegi() }
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
            if self.jul.count > 2000 { self.jul.removeFirst(self.jul.count - 2000) }
        }
    }

    func jeojang() {
        LibIyong.shared.jeojang()   // 0.8.2 이용 장부도 함께
        q.async {
            if let d = try? JSONSerialization.data(withJSONObject: self.jul) {
                try? d.write(to: self.pail, options: .atomic)
            }
        }
    }

    func bonaegi() {
        LibIyong.shared.bonaegi()   // 0.8.2 이용 장부도 같은 때(20초마다) 보냄
        q.async {
            guard !self.bonaeneunJung, !self.jul.isEmpty else { return }
            let mukeum = Array(self.jul.prefix(100))
            let body: [String: Any] = ["dev": self.dev, "app": "lib-ios " + Pan.ver + "(" + Pan.build + ")",
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

// MARK: 0.8.2 (261019-L21) 이용 장부 — 실적용(이사장님 승인 2026-10-18 「1」)
// 책·작품을 연 것(yeol), 들은 시간(deut, 1분마다 초), 끝까지 들은 것(kkeut)을 협회 서버(nas/iyong.php)에 남깁니다.
// 회원 열쇠로 보내면 서버가 이름·전화 대신 가명 번호로만 적습니다. 운영실 통계에는 숫자만 나갑니다.
// 통신이 끊기면 폰에 쌓아 두었다가(앱을 껐다 켜도 남음) 이어지면 보냅니다.
final class LibIyong {
    static let shared = LibIyong()
    private let q = DispatchQueue(label: "lib.iyong")
    private var jul: [[String: Any]] = []
    private var bonaeneunJung = false
    private let pail: URL
    private let url = URL(string: "https://lvd.ada.or.kr/nas/iyong.php")!

    init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("lib", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        pail = dir.appendingPathComponent("iyong_jul.json")
        if let d = try? Data(contentsOf: pail),
           let a = try? JSONSerialization.jsonObject(with: d) as? [[String: Any]] {
            jul = a
        }
    }

    func namgi(_ e: String, i: Int, k: String, s: Int = 0) {
        guard i >= 0 else { return }
        var row: [String: Any] = ["t": Int(Date().timeIntervalSince1970), "e": e, "i": i, "k": k]
        if s > 0 { row["s"] = s }
        q.async {
            self.jul.append(row)
            if self.jul.count > 5000 { self.jul.removeFirst(self.jul.count - 5000) }
        }
    }

    func jeojang() {
        q.async {
            if let d = try? JSONSerialization.data(withJSONObject: self.jul) { try? d.write(to: self.pail, options: .atomic) }
        }
    }

    func bonaegi() {
        q.async {
            let tk = Hoewon.shared.yeolsoe
            guard !self.bonaeneunJung, !self.jul.isEmpty, !tk.isEmpty else { return }
            let mukeum = Array(self.jul.prefix(200))
            let body: [String: Any] = ["tk": tk, "gi": "ios", "app": "lib-ios " + Pan.ver + "(" + Pan.build + ")", "ev": mukeum]
            guard let data = try? JSONSerialization.data(withJSONObject: body) else { self.jul.removeFirst(mukeum.count); return }
            self.bonaeneunJung = true
            var r = URLRequest(url: self.url)
            r.httpMethod = "POST"
            r.setValue("application/json", forHTTPHeaderField: "Content-Type")
            r.httpBody = data
            r.timeoutInterval = 15
            URLSession.shared.dataTask(with: r) { _, res, _ in
                let code = (res as? HTTPURLResponse)?.statusCode ?? 0
                self.q.async {
                    self.bonaeneunJung = false
                    // 200 받음, 400·413 은 다시 보내도 안 되므로 버림. 403(열쇠 없음)·통신 끊김은 남겨 두었다가 다시
                    guard code == 200 || code == 400 || code == 413 else { return }
                    self.jul.removeFirst(min(mukeum.count, self.jul.count))
                    if let d = try? JSONSerialization.data(withJSONObject: self.jul) { try? d.write(to: self.pail, options: .atomic) }
                    if !self.jul.isEmpty { DispatchQueue.main.async { self.bonaegi() } }
                }
            }.resume()
        }
    }
}
