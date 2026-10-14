// BYOD 방송 아이폰 — 행사 녹음과 결과보고 받아 올리기 (1.3.0판, 빌드 261014-BI4, 이사장님 승인 「1」, 방송클)
// 안드로이드 1.3.0(Haengsa.kt)과 같습니다. 방송을 멈추면
//   1. 공유기에 행사 마감을 알리고  2. 녹음·결과보고·기록을 받아 이 아이폰에 담고  3. 배프 다시듣기(나스 byod_olligi.php)에 올립니다.
// 공유기 와이파이에는 인터넷이 없어 바로 못 올리면, 담아 둔 채 1분마다 다시 올려 봅니다(와이파이를 벗어나면 올라감).
// 공유기도 인터넷이 생기면 스스로 올립니다(둘 중 먼저 된 쪽, 두 번 올리지 않음).
import Foundation
import AVFoundation

final class HaengsaOlligi {
    static let shared = HaengsaOlligi()
    static let NAS = "https://lvd.ada.or.kr/bfblive/byod_olligi.php"
    static let YEOLSOE = "byod-261014-k7m2"
    private let q = DispatchQueue(label: "byod.haengsa")
    private var dolgo = false
    var allim: (String) -> Void = { _ in }

    struct Jul: Identifiable { let sid: String; let jemok: String; let haeseolsa: String; let sijak: Date; let gilee: Double; let olim: Bool; var id: String { sid } }

    static var teo: URL {
        let u = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("byod_haengsa")
        try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true)
        return u
    }

    /// 이 아이폰에 담아 둔 행사(새것 먼저)
    static func mokrok() -> [Jul] {
        let fm = FileManager.default
        let dl = (try? fm.contentsOfDirectory(atPath: teo.path)) ?? []
        var l: [Jul] = []
        for sid in dl {
            let d = teo.appendingPathComponent(sid)
            guard let b = try? Data(contentsOf: d.appendingPathComponent("girok.json")),
                  let g = (try? JSONSerialization.jsonObject(with: b)) as? [String: Any] else { continue }
            l.append(Jul(sid: sid, jemok: g["jemok"] as? String ?? "", haeseolsa: g["haeseolsa"] as? String ?? "",
                         sijak: Date(timeIntervalSince1970: g["sijak"] as? Double ?? 0), gilee: g["gilee"] as? Double ?? 0,
                         olim: fm.fileExists(atPath: d.appendingPathComponent("olim").path)))
        }
        return l.sorted { $0.sid > $1.sid }
    }

    static func bogo(_ sid: String) -> String {
        guard let b = try? Data(contentsOf: teo.appendingPathComponent(sid).appendingPathComponent("bogo.txt")) else { return "결과보고가 아직 없습니다." }
        let euckr = String.Encoding(rawValue: CFStringConvertEncodingToNSStringEncoding(CFStringEncoding(CFStringEncodings.EUC_KR.rawValue)))
        return (String(data: b, encoding: euckr) ?? "").replacingOccurrences(of: "\r\n", with: "\n")
    }

    static var nameum: Bool { mokrok().contains { !$0.olim } }

    // MARK: 받고 올리기 — 일꾼 줄기에서 차례로(기다리는 줄 세마포)

    private func batgi(_ gil: String) -> Data? {
        guard let u = URL(string: "http://\(Flint.IP):\(Flint.WEB)\(gil)") else { return nil }
        var rq = URLRequest(url: u); rq.timeoutInterval = 60; rq.cachePolicy = .reloadIgnoringLocalCacheData
        let s = DispatchSemaphore(value: 0)
        var jo: Data? = nil
        URLSession.shared.dataTask(with: rq) { d, r, _ in
            if (r as? HTTPURLResponse)?.statusCode == 200 { jo = d }
            s.signal()
        }.resume()
        _ = s.wait(timeout: .now() + 65)
        return jo
    }

    private func bonae(_ q: String, _ mom: Data) -> Bool {
        guard let u = URL(string: q) else { return false }
        var rq = URLRequest(url: u); rq.httpMethod = "POST"; rq.timeoutInterval = 120
        let s = DispatchSemaphore(value: 0)
        var ok = false
        URLSession.shared.uploadTask(with: rq, from: mom) { d, _, _ in
            ok = (d.flatMap { String(data: $0, encoding: .utf8) } ?? "").contains("\"ok\":true")
            s.signal()
        }.resume()
        _ = s.wait(timeout: .now() + 125)
        return ok
    }

    /// 나스로 4메가씩 나눠 올림
    private func nasBonaegi(_ sid: String, _ jong: String, _ b: Data) -> Bool {
        let q0 = "\(HaengsaOlligi.NAS)?k=\(HaengsaOlligi.YEOLSOE)&sid=\(sid)&jong=\(jong)"
        let K = 4 * 1024 * 1024
        var su = 0, i = 0
        repeat {
            let j = b.subdata(in: i..<min(b.count, i + K))
            if !bonae("\(q0)&step=jogak&n=\(su)", j) { return false }
            su += 1; i += K
        } while i < b.count
        return bonae("\(q0)&step=kkeut&su=\(su)&gil=\(b.count)", Data())
    }

    /// 방송을 멈춘 뒤(maegam: true), 또는 1분마다 다시 올려 볼 때(false) 부름
    func sijak(maegam: Bool) {
        q.async {
            guard !self.dolgo else { return }
            self.dolgo = true
            defer { self.dolgo = false }
            self.dolgi(maegam: maegam)
        }
    }

    private func mal(_ t: String) { DispatchQueue.main.async { self.allim(t) } }

    private func dolgi(maegam: Bool) {
        let fm = FileManager.default
        // 1. 공유기에서 마감하고 받기
        if maegam { _ = batgi("/haengsa_kkeut?k=") }
        if let m = batgi("/haengsa_mokrok"), let j = (try? JSONSerialization.jsonObject(with: m)) as? [String: Any],
           let l = j["mokrok"] as? [[String: Any]] {
            for h in l {
                guard let sid = h["sid"] as? String, h["sangtae"] as? String == "kkeut",
                      sid.range(of: "^byod[0-9]{12}$", options: .regularExpression) != nil else { continue }
                let d = HaengsaOlligi.teo.appendingPathComponent(sid)
                if fm.fileExists(atPath: d.appendingPathComponent("girok.json").path) {
                    if (h["olim"] as? Int ?? 0) == 0 && fm.fileExists(atPath: d.appendingPathComponent("olim").path) { _ = batgi("/haengsa_olim?sid=\(sid)") }
                    continue
                }
                try? fm.createDirectory(at: d, withIntermediateDirectories: true)
                let ext = (h["hyeongsik"] as? String) == "mp3" ? "mp3" : "ul"
                guard let nok = batgi("/haengsa/\(sid)/nokeum.\(ext)"), let bogo = batgi("/haengsa/\(sid)/bogo.txt"),
                      let gir = batgi("/haengsa/\(sid)/girok.json") else { continue }
                try? nok.write(to: d.appendingPathComponent("nokeum.\(ext)"))
                try? bogo.write(to: d.appendingPathComponent("bogo.txt"))
                try? gir.write(to: d.appendingPathComponent("girok.json"))   // 마지막에 써서, 다 받은 것만 목록에 나오게
                if (h["olim"] as? Int ?? 0) == 1 { try? Data("1".utf8).write(to: d.appendingPathComponent("olim")) }
                mal("\(h["jemok"] as? String ?? "") 녹음과 결과보고를 이 아이폰에 담았습니다. 더 보기의 지난 행사 기록에서 들으실 수 있습니다.")
            }
        }
        // 2. 아직 올리지 않은 것을 배프에 올리기
        for h in HaengsaOlligi.mokrok() where !h.olim {
            let d = HaengsaOlligi.teo.appendingPathComponent(h.sid)
            let mp3 = d.appendingPathComponent("nokeum.mp3")
            let nokU = fm.fileExists(atPath: mp3.path) ? mp3 : d.appendingPathComponent("nokeum.ul")
            guard let nok = try? Data(contentsOf: nokU), let bogo = try? Data(contentsOf: d.appendingPathComponent("bogo.txt")),
                  let gir = try? Data(contentsOf: d.appendingPathComponent("girok.json")) else { continue }
            if nasBonaegi(h.sid, nokU.pathExtension == "mp3" ? "mp3" : "ul", nok) && nasBonaegi(h.sid, "bogo", bogo) && nasBonaegi(h.sid, "girok", gir) {
                try? Data("1".utf8).write(to: d.appendingPathComponent("olim"))
                _ = batgi("/haengsa_olim?sid=\(h.sid)")
                mal("\(h.jemok) 녹음과 결과보고를 배프 다시듣기에 올렸습니다.")
            } else {
                mal("배프 다시듣기에 아직 올리지 못했습니다. 인터넷이 되면 저절로 다시 올립니다.")
                break
            }
        }
    }
}

/// 지난 행사 녹음 듣기
final class NokeumDeutgi {
    static let shared = NokeumDeutgi()
    private var p: AVAudioPlayer?
    var deutneunJung: Bool { p?.isPlaying ?? false }
    func teulgi(_ sid: String) -> Bool {
        let u = HaengsaOlligi.teo.appendingPathComponent(sid).appendingPathComponent("nokeum.mp3")
        guard let pl = try? AVAudioPlayer(contentsOf: u) else { return false }
        p = pl
        return pl.play()
    }
    func meomchum() { p?.stop(); p = nil }
}
