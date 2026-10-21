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
        GigiNokeum.shared.namunGeotMaegam()   // 1.7.0 방송 중 꺼진 아이피타임 방식 녹음을 먼저 마감
        let flint = Flint.flintInga            // 1.7.0 아이피타임 방식이면 플린트 2 공유기에 묻지 않음(없는 주소를 기다리지 않게)
        // 1. 공유기에서 마감하고 받기
        if flint && maegam { _ = batgi("/haengsa_kkeut?k=") }
        if flint, let m = batgi("/haengsa_mokrok"), let j = (try? JSONSerialization.jsonObject(with: m)) as? [String: Any],
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
                if flint { _ = batgi("/haengsa_olim?sid=\(h.sid)") }
                mal("\(h.jemok) 녹음과 결과보고를 LVD 방송 다시 듣기에 올렸습니다.")
            } else {
                mal("LVD 방송 다시 듣기에 아직 올리지 못했습니다. 인터넷이 되면 저절로 다시 올립니다.")
                break
            }
        }
        // 3. 1.5.0 공유기 녹음이 다 올라갔으면, 같은 때 인터넷으로 나간 녹음은 다시 듣기 목록에서 감춤(지우지 않음)
        let olin = HaengsaOlligi.mokrok().filter { $0.olim }
        for g in GamchumDaegi.mokrok {
            // 인터넷 방을 연 때가 올라간 공유기 녹음의 시간 안(앞뒤 2분 여유)에 들 때만
            guard olin.contains(where: { g.ttae.timeIntervalSince($0.sijak) > -120 && g.ttae.timeIntervalSince($0.sijak) < $0.gilee + 120 }) else { continue }
            if bonae("\(HaengsaOlligi.NAS)?k=\(HaengsaOlligi.YEOLSOE)&step=gamchum&inteonet=\(g.sid)", Data()) { GamchumDaegi.ppaegi(g.sid) } else { break }
        }
    }
}

/// 지난 행사 녹음 듣기
final class NokeumDeutgi {
    static let shared = NokeumDeutgi()
    private var p: AVAudioPlayer?
    var deutneunJung: Bool { p?.isPlaying ?? false }
    func teulgi(_ sid: String) -> Bool {
        let d = HaengsaOlligi.teo.appendingPathComponent(sid)
        let mp3 = d.appendingPathComponent("nokeum.mp3")
        let u = FileManager.default.fileExists(atPath: mp3.path) ? mp3 : (NokeumDeutgi.wav(sid) ?? mp3)   // 1.7.0 아이피타임 방식 뮤 녹음도
        guard let pl = try? AVAudioPlayer(contentsOf: u) else { return false }
        p = pl
        return pl.play()
    }
    func meomchum() { p?.stop(); p = nil }

    /// 1.7.0 뮤 녹음(nokeum.ul)에 웨이브 머리를 붙인 사본(임시 폴더) — 원본은 그대로 둠
    static func wav(_ sid: String) -> URL? {
        let ul = HaengsaOlligi.teo.appendingPathComponent(sid).appendingPathComponent("nokeum.ul")
        guard let b = try? Data(contentsOf: ul) else { return nil }
        var h = Data()
        func u32(_ v: Int) { var x = UInt32(v).littleEndian; h.append(Data(bytes: &x, count: 4)) }
        func u16(_ v: Int) { var x = UInt16(v).littleEndian; h.append(Data(bytes: &x, count: 2)) }
        h.append(Data("RIFF".utf8)); u32(36 + b.count); h.append(Data("WAVE".utf8))
        h.append(Data("fmt ".utf8)); u32(16); u16(7); u16(1); u32(16000); u32(16000); u16(1); u16(8)   // 7 = 뮤(mu-law), 한 줄
        h.append(Data("data".utf8)); u32(b.count)
        let w = FileManager.default.temporaryDirectory.appendingPathComponent("deutgi_\(sid).wav")
        guard (try? (h + b).write(to: w)) != nil else { return nil }
        return w
    }
}
