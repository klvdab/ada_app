// LVD 송출 아이폰 — 아이피타임 방식 행사 녹음 (1.7.0판, 빌드 261021-BI9, 이사장님 승인 2026-10-21, 방클)
// 안드로이드 1.7.0(GigiNokeum.kt)과 같습니다. 아이피타임 공유기는 녹음을 할 수 없어, 방송 서버인 이 아이폰이 해설 소리를 직접 녹음합니다(녹음·녹화는 무조건 나스에).
// 플린트 2 공유기 서버의 행사 녹음과 똑같은 꼴로 담아, 같은 길(HaengsaOlligi)로 LVD 방송 다시 듣기에 올립니다.
//   1. 방송을 켜면 행사 하나를 엶 — 이름 byod년월일시분초(이 아이폰 시각, 한국 시간)
//   2. 해설 소리(뮤 8비트, 1초에 16000바이트)를 1분 조각(0001.ul …)으로 씀, 10초마다 저장 장치에 확실히 씀
//   3. 1분마다 듣는 분 수, 도움 요청이 오면 그때를 기록(girok_jinhaeng.json)
//   4. 방송을 멈추면 조각을 이어 nokeum.ul, 결과보고 bogo.txt(EUC-KR, CRLF), 마지막에 girok.json — girok.json 이 있어야 목록·올리기에 나옴
//   5. 방송 중에 아이폰이 꺼졌으면, 다음에 올릴 때 남은 조각으로 마감해 올림
// 소리 일꾼(오디오 줄기)에서 부르므로, 파일 쓰기는 따로 한 줄기(q)에서 차례로 합니다.
import Foundation

final class GigiNokeum {
    static let shared = GigiNokeum()
    private let q = DispatchQueue(label: "byod.giginokeum")
    private var teo: URL?
    private var g: [String: Any]?
    private var f: FileHandle?
    private var fTtae = 0.0
    private var jeojangTtae = 0.0
    private var bunTtae = 0.0
    private var _sid = ""
    private var _sseun = 0
    private let jamgeum = NSLock()
    var su = 0                                 // 듣는 분 수(Bang.suGochigi 가 알려 줌)

    /// 지금 녹음 중인 행사(없으면 빈 글)와 이번 행사에 쓴 바이트 — 현장 점검용
    var sid: String { jamgeum.lock(); defer { jamgeum.unlock() }; return _sid }
    var sseunBaiteu: Int { jamgeum.lock(); defer { jamgeum.unlock() }; return _sseun }

    private static let kstCal: Calendar = { var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(identifier: "Asia/Seoul")!; return c }()

    static func sigakMal(_ cho: Double) -> String {
        let c = kstCal.dateComponents([.hour, .minute], from: Date(timeIntervalSince1970: cho))
        let h = c.hour ?? 0
        let h12 = h % 12 == 0 ? 12 : h % 12
        return (h < 12 ? "오전 " : "오후 ") + "\(h12)시 \(c.minute ?? 0)분"
    }
    static func nalMal(_ cho: Double) -> String {
        let c = kstCal.dateComponents([.year, .month, .day, .weekday], from: Date(timeIntervalSince1970: cho))
        let yoil = ["일", "월", "화", "수", "목", "금", "토"][max(0, (c.weekday ?? 1) - 1)]
        return "\(c.year ?? 0)년 \(c.month ?? 0)월 \(c.day ?? 0)일 \(yoil)요일"
    }
    static func gileeMal(_ cho0: Double) -> String {
        let cho = Int(cho0.rounded())
        let h = cho / 3600, m = (cho % 3600) / 60, s = cho % 60
        if h > 0 { return "\(h)시간 \(m)분" }
        if m > 0 { return s > 0 ? "\(m)분 \(s)초" : "\(m)분" }
        return "\(s)초"
    }

    /// 방송을 켤 때 — 행사를 엶
    func sijak(jemok jemok0: String, haeseolsa: String) {
        q.sync { () -> Void in
            if !self.sid.isEmpty { return }
            let now = Date().timeIntervalSince1970
            let c = GigiNokeum.kstCal.dateComponents([.year, .month, .day, .hour, .minute, .second], from: Date(timeIntervalSince1970: now))
            let s = String(format: "byod%02d%02d%02d%02d%02d%02d", (c.year ?? 0) % 100, c.month ?? 0, c.day ?? 0, c.hour ?? 0, c.minute ?? 0, c.second ?? 0)
            let d = HaengsaOlligi.teo.appendingPathComponent(s)
            try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
            let jemok = jemok0.isEmpty ? "현장영상해설 방송 " + GigiNokeum.nalMal(now) + " " + GigiNokeum.sigakMal(now) : jemok0
            self.g = ["sid": s, "jemok": jemok, "haeseolsa": haeseolsa, "sijak": now, "kkeut": 0, "majimak": now,
                      "bun": [[Any]](), "yocheong": [[Any]](), "kkeunkim": [[now, "butum", "아이피타임 방식"] as [Any]] as [[Any]],
                      "jogak": [String](), "sangtae": "jinhaeng", "olim": 0, "hyeongsik": "mulaw",
                      "pan": "LVD 송출 아이폰 " + Bang.PAN, "bangsik": "iptime", "chobeon_deut": 0]
            self.teo = d; self.f = nil; self.fTtae = 0; self.jeojangTtae = now; self.bunTtae = now
            self.jamgeum.lock(); self._sid = s; self._sseun = 0; self.jamgeum.unlock()
            self.jeojang()
        }
    }

    /// 해설 소리 한 조각(0.1초)
    func sseugi(_ b: Data) {
        q.async {
            guard var gg = self.g, let d = self.teo else { return }
            let now = Date().timeIntervalSince1970
            if self.f == nil || now - self.fTtae >= 60 {
                self.jogakDatgi()
                var l = gg["jogak"] as? [String] ?? []
                let nm = String(format: "%04d.ul", l.count + 1)
                let u = d.appendingPathComponent(nm)
                if !FileManager.default.fileExists(atPath: u.path) { FileManager.default.createFile(atPath: u.path, contents: nil) }
                self.f = try? FileHandle(forWritingTo: u)
                _ = self.f?.seekToEndOfFile()
                self.fTtae = now
                l.append(nm); gg["jogak"] = l
                self.g = gg
                self.jeojang()
            }
            self.f?.write(b)
            self.jamgeum.lock(); self._sseun += b.count; self.jamgeum.unlock()
            gg = self.g ?? gg
            gg["majimak"] = now
            if now - self.bunTtae >= 60 {
                self.bunTtae = now
                var bun = gg["bun"] as? [[Any]] ?? []
                bun.append([now, self.su]); gg["bun"] = bun
            }
            self.g = gg
            if now - self.jeojangTtae >= 10 {
                self.jeojangTtae = now
                self.f?.synchronizeFile()
                self.jeojang()
            }
        }
    }

    /// 도움 요청이 오면(Bang.yocheongBatgi 가 부름)
    func yocheong(jongryu: String, beonho: String) {
        q.async {
            guard var gg = self.g else { return }
            var l = gg["yocheong"] as? [[Any]] ?? []
            l.append([Date().timeIntervalSince1970, jongryu, beonho]); gg["yocheong"] = l
            self.g = gg
            self.jeojang()
        }
    }

    private func jogakDatgi() {
        guard let x = f else { return }
        x.synchronizeFile(); x.closeFile()
        f = nil
    }

    private func jeojang() {
        guard let gg = g, let d = teo, let b = try? JSONSerialization.data(withJSONObject: gg) else { return }
        try? b.write(to: d.appendingPathComponent("girok_jinhaeng.json"), options: .atomic)
    }

    /// 방송을 멈출 때 — 마감. 돌려주는 값: 마감했는가
    func maegam() -> Bool {
        q.sync { () -> Bool in
            guard let gg = self.g, let d = self.teo else { return false }
            self.jogakDatgi()
            self.g = nil; self.teo = nil
            self.jamgeum.lock(); self._sid = ""; self.jamgeum.unlock()
            return GigiNokeum.maegamHagi(d, gg)
        }
    }

    /// 조각을 이어 녹음 한 덩어리로, 결과보고와 girok.json 을 씀
    private static func maegamHagi(_ d: URL, _ g0: [String: Any]) -> Bool {
        var gg = g0
        let fm = FileManager.default
        gg["kkeut"] = gg["majimak"] as? Double ?? (gg["sijak"] as? Double ?? 0)
        gg["sangtae"] = "kkeut"
        let tmp = d.appendingPathComponent("nokeum.ul.tmp")
        fm.createFile(atPath: tmp.path, contents: nil)
        guard let o = try? FileHandle(forWritingTo: tmp) else { return false }
        var hap = 0
        for nm in gg["jogak"] as? [String] ?? [] {
            if let b = try? Data(contentsOf: d.appendingPathComponent(nm)) { o.write(b); hap += b.count }
        }
        o.synchronizeFile(); o.closeFile()
        let nok = d.appendingPathComponent("nokeum.ul")
        try? fm.removeItem(at: nok)
        guard (try? fm.moveItem(at: tmp, to: nok)) != nil else { return false }
        gg["gilee"] = Double(hap) / 16000.0
        let euckr = String.Encoding(rawValue: CFStringConvertEncodingToNSStringEncoding(CFStringEncoding(CFStringEncodings.EUC_KR.rawValue)))
        // 1.9.0 (261024-BI11) 피디 화면 현장 기능 기록(HyeonjangGil 이 1분마다 써 둔 것)을 결과보고 뒤에 붙임
        var bogoGeul = bogoMandeul(gg)
        if let hb = try? String(contentsOf: d.appendingPathComponent("hyeonjang_bogo.txt"), encoding: .utf8), !hb.isEmpty { bogoGeul += hb }
        guard let bogo = bogoGeul.replacingOccurrences(of: "\n", with: "\r\n").data(using: euckr, allowLossyConversion: true),
              (try? bogo.write(to: d.appendingPathComponent("bogo.txt"))) != nil,
              let gb = try? JSONSerialization.data(withJSONObject: gg),
              (try? gb.write(to: d.appendingPathComponent("girok.json"), options: .atomic)) != nil else { return false }   // 마지막에 써서, 다 된 것만 목록에 나오게
        try? fm.removeItem(at: d.appendingPathComponent("girok_jinhaeng.json"))
        return true
    }

    /// 1.9.0 (261024-BI11) 지금까지의 결과보고(방송 중 — 피디 화면 결과보고와 1분마다 저장에 씀). 녹음 중이 아니면 빈 글
    func jigeumBogo() -> String {
        q.sync { () -> String in
            guard var gg = self.g else { return "" }
            gg["kkeut"] = gg["majimak"] as? Double ?? (gg["sijak"] as? Double ?? 0)
            gg["gilee"] = Double(self.sseunBaiteu) / 16000.0
            return GigiNokeum.bogoMandeul(gg)
        }
    }

    /// 방송 중에 아이폰이 꺼져 마감하지 못한 행사를 남은 조각으로 마감(올리기 전에 부름)
    func namunGeotMaegam() {
        q.sync {
            let fm = FileManager.default
            for nm in (try? fm.contentsOfDirectory(atPath: HaengsaOlligi.teo.path)) ?? [] where nm != self.sid {
                let d = HaengsaOlligi.teo.appendingPathComponent(nm)
                guard fm.fileExists(atPath: d.appendingPathComponent("girok_jinhaeng.json").path),
                      !fm.fileExists(atPath: d.appendingPathComponent("girok.json").path),
                      let b = try? Data(contentsOf: d.appendingPathComponent("girok_jinhaeng.json")),
                      var gg = (try? JSONSerialization.jsonObject(with: b)) as? [String: Any] else { continue }
                var kk = gg["kkeunkim"] as? [[Any]] ?? []
                kk.append([gg["majimak"] as? Double ?? 0, "kkeunkim", "기기가 꺼짐"]); gg["kkeunkim"] = kk
                _ = GigiNokeum.maegamHagi(d, gg)
            }
        }
    }

    /// 한 장짜리 결과보고 — 공유기 서버의 결과보고와 같은 꼴, 낭독기와 점자정보단말기로 읽히게 기호 없이
    private static func bogoMandeul(_ g: [String: Any]) -> String {
        var L: [String] = []
        let sijak = g["sijak"] as? Double ?? 0, kkeut = g["kkeut"] as? Double ?? 0
        L.append("현장영상해설 방송 결과보고")
        L.append("사단법인 한국시각장애인현장영상해설협회")
        L.append("")
        L.append("행사 이름: " + (g["jemok"] as? String ?? ""))
        L.append("날짜: " + nalMal(sijak))
        let hs = g["haeseolsa"] as? String ?? ""
        L.append("현장영상해설사: " + (hs.isEmpty ? "적지 않음" : hs))
        L.append("시작: " + sigakMal(sijak))
        L.append("끝: " + sigakMal(kkeut))
        L.append("방송 시간: " + gileeMal(max(0, kkeut - sijak)))
        let bun = (g["bun"] as? [[Any]] ?? []).compactMap { x -> (Double, Int)? in
            guard x.count >= 2, let t = x[0] as? Double, let n = (x[1] as? Int) ?? (x[1] as? NSNumber)?.intValue else { return nil }; return (t, n)
        }
        if let mx = bun.max(by: { $0.1 < $1.1 }) {
            let pyeong = Double(bun.reduce(0) { $0 + $1.1 }) / Double(bun.count)
            L.append("듣는 분: 가장 많을 때 \(mx.1)명(\(sigakMal(mx.0))), 평균 \(Int(pyeong.rounded()))명")
            L.append("")
            L.append("10분 간격 듣는 분 수")
            var dah = sijak
            for (t, n) in bun where t >= dah { L.append(sigakMal(t) + " \(n)명"); dah = t + 600 }
        } else { L.append("듣는 분: 기록 없음") }
        let yc = (g["yocheong"] as? [[Any]] ?? []).compactMap { x -> (Double, String, String)? in
            guard x.count >= 3, let t = x[0] as? Double, let k = x[1] as? String else { return nil }; return (t, k, x[2] as? String ?? "")
        }
        L.append("")
        if yc.isEmpty { L.append("도움 요청: 없음") } else {
            var se: [(String, Int)] = []
            for (_, k, _) in yc { if let i = se.firstIndex(where: { $0.0 == k }) { se[i].1 += 1 } else { se.append((k, 1)) } }
            L.append("도움 요청: 모두 \(yc.count)번 (" + se.map { Bang.jongryuMal($0.0) + " \($0.1)번" }.joined(separator: ", ") + ")")
            for (t, k, n) in yc { L.append(sigakMal(t) + " " + Bang.jongryuMal(k) + " (" + (n.isEmpty ? "번호 없음" : n + "번") + ")") }
        }
        let kk = (g["kkeunkim"] as? [[Any]] ?? []).filter { $0.count >= 2 && ($0[1] as? String) == "kkeunkim" }
        L.append("")
        L.append("방송 기기 끊김: " + (kk.isEmpty ? "없음" : "\(kk.count)번"))
        for x in kk { L.append(sigakMal(x[0] as? Double ?? 0) + " 끊김 (" + (x.count > 2 ? (x[2] as? String ?? "") : "") + ")") }
        L.append("")
        L.append("녹음: " + (g["sid"] as? String ?? "") + ".ul, 길이 " + gileeMal(g["gilee"] as? Double ?? 0))
        L.append("LVD 송출 아이폰 " + Bang.PAN + "판, 아이피타임 방식(이 아이폰이 녹음)")
        return L.joined(separator: "\n") + "\n"
    }
}
