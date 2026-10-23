// LVD 송출 아이폰 — 현장 방송을 인터넷 방송으로도 함께 내보내기 (1.5.0판, 빌드 261018-BI7, 방클, 이사장님 승인 「1」 — LVD 방송 정리안)
// 이사장님 필수조건: 공유기방송(현장 방송)을 하면 라이브방송(인터넷 방송)도 저절로 함께 나가야 함.
// 하는 일
//   1. 현장 방송을 켜면, 같은 해설 소리(뮤 8비트, 1초에 16000)를 LVD 방송 중계(lvd.ada.or.kr:2053)로도 보냅니다.
//      중계에 방을 열 때 현장영상해설사 전화번호를 협회 명단에 대조합니다(인터넷 방송 하기 화면과 같음).
//   2. 2초마다 한 덩이씩 보냅니다. 첫 덩이 앞에는 웨이브 머리를 붙여, 중계가 듣는 분께 mp3 로 바꿔 흘려 줍니다.
//   3. 플린트 2 공유기 와이파이에는 인터넷이 없으므로, 플린트 2 방식에서는 휴대전화망(셀룰러)으로 먼저 보내고
//      안 되면 와이파이 쪽으로 바꿔 봅니다(공유기에 인터넷이 있을 때). 끊기면 저절로 다시 붙습니다.
//   4. 인터넷이 안 되어도 현장 방송은 그대로 나갑니다. 상태는 첫 화면 한 줄로 바뀔 때만 알립니다.
//   5. 방송이 끝나면 중계 방을 닫습니다. 플린트 2 방식이면 공유기의 온전한 녹음이 다시 듣기에 올라간 뒤,
//      같은 때 인터넷으로 나간 녹음은 다시 듣기 목록에서 감춥니다(지우지 않음, 같은 행사가 두 번 나오지 않게).
import Foundation
import Network
import Security

final class InteonetBonaegi {
    static let shared = InteonetBonaegi()
    static let HOST = "lvd.ada.or.kr"
    static let PORT: UInt16 = 2053
    static let RATE = 16000

    /// 현장 방송과 함께 인터넷 방송 (더 보기에서 켜고 끔, 처음에는 켜짐)
    static var hamkke: Bool {
        get { UserDefaults.standard.object(forKey: "inteonet_hamkke") as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: "inteonet_hamkke") }
    }
    static var jeonhwa: String { (UserDefaults.standard.string(forKey: "haeseolsa_phone") ?? "").filter { $0.isNumber } }

    private let q = DispatchQueue(label: "byod.inteonet")
    private var dolgo = false
    private var yeollim = false
    private var geobu = false            // 명단에 없는 번호 — 다시 열지 않음
    private var sid = "", tok = ""
    private var moum = Data()
    private var cheot = true
    private var cell = false             // 휴대전화망으로만 보낼 것인가
    private var flintBangsik = false
    private var silpae = 0
    private var bonaeneun = false
    private var yeolgiJung = false
    private var yeolgiTtae = Date.distantPast
    private var jemok = "", phone = "", who = ""
    private var bonaenCho: Double = 0
    private var yeollinTtae = Date()
    private var timer: DispatchSourceTimer?

    // MARK: 켜고 끄기 — 방송 일(BangIl)이 부름

    func sijak(jemok: String, who: String) {
        let p = InteonetBonaegi.jeonhwa
        q.async {
            guard !self.dolgo else { return }
            guard InteonetBonaegi.hamkke else { self.allim("인터넷 방송: 꺼 두셨습니다. BYOD 방송만 나갑니다. 더 보기에서 켤 수 있습니다."); return }
            guard p.count >= 9 else { self.allim("인터넷 방송: 첫 화면에 현장영상해설사 전화번호를 넣으시면 BYOD 방송과 함께 인터넷으로도 나갑니다."); return }
            self.dolgo = true; self.yeollim = false; self.geobu = false
            self.jemok = jemok; self.who = who; self.phone = p
            self.flintBangsik = true; self.cell = true   // 1.7.0 두 방식 모두 현장 녹음이 다시 듣기에 올라가므로 감춤 대기에 넣음(올라간 것을 확인한 뒤에만 감춤), 아이피타임 와이파이에도 인터넷이 없으므로 휴대전화망 먼저(안 되면 바꿈)
            self.silpae = 0; self.bonaenCho = 0; self.moum = Data(); self.cheot = true
            self.yeolgiTtae = .distantPast
            let t = DispatchSource.makeTimerSource(queue: self.q)
            t.schedule(deadline: .now() + 2, repeating: 2)
            t.setEventHandler { [weak self] in self?.dolgi() }
            t.resume()
            self.timer = t
            self.allim("인터넷 방송: 중계에 방을 열고 있습니다.")
            self.yeolgi()
        }
    }

    /// 0.1초 조각 — 현장 방송으로 보내는 것과 같은 소리
    func neoki(_ d: Data) {
        q.async {
            guard self.dolgo, !self.geobu else { return }
            self.moum.append(d)
            let mak = InteonetBonaegi.RATE * 10   // 10초 넘게 밀리면 오래된 것을 버림(늦어지지 않게)
            if self.moum.count > mak { self.moum = self.moum.suffix(mak) }
        }
    }

    func meomchugi() {
        q.async {
            guard self.dolgo else { return }
            self.dolgo = false
            self.timer?.cancel(); self.timer = nil
            let s = self.sid, t = self.tok, yeol = self.yeollim, nameun = self.moum, cheot = self.cheot
            self.yeollim = false; self.moum = Data()
            if yeol {
                let datgi = { self.yocheong("GET", "/datgi?sid=\(s)&tok=\(t)", nil) { _, _ in } }
                if !nameun.isEmpty {
                    self.yocheong("POST", "/jogak?sid=\(s)&tok=\(t)", cheot ? InteonetBonaegi.wavMeori() + nameun : nameun) { _, _ in datgi() }
                } else { datgi() }
                if self.flintBangsik && self.bonaenCho > 5 { GamchumDaegi.deohagi(s, self.yeollinTtae) }
            }
            self.allim("")
        }
    }

    // MARK: 일꾼 줄기

    private func allim(_ t: String) { Bang.shared.juge { b in if b.inteonet != t { b.inteonet = t; if !t.isEmpty { ByodGirok.shared.namgi("inteonet", ["mal": t]) } } } }   // 1.8.0 기록

    private static func mal(_ s: String) -> String { s.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? "" }
    private static func nanSu(_ n: Int) -> String { String((0..<n).map { _ in "abcdefghijklmnopqrstuvwxyz0123456789".randomElement()! }) }

    private func yeolgi() {
        guard dolgo, !geobu, !yeolgiJung, Date().timeIntervalSince(yeolgiTtae) > 12 else { return }
        yeolgiJung = true; yeolgiTtae = Date()
        let s = "lvd" + InteonetBonaegi.nanSu(9), tk = InteonetBonaegi.nanSu(10)
        let gil = "/yeolgi?sid=\(s)&tok=\(tk)&phone=\(phone)&mime=\(InteonetBonaegi.mal("audio/wav"))&jong=live&title=\(InteonetBonaegi.mal(jemok))&who=\(InteonetBonaegi.mal(who))"
        yocheong("GET", gil, nil) { code, d in
            self.yeolgiJung = false
            let j = d.flatMap { (try? JSONSerialization.jsonObject(with: $0)) as? [String: Any] }
            let ok = code == 200 && (j?["ok"] as? Bool ?? false)
            if !self.dolgo {   // 그사이 방송을 멈췄으면 연 방을 바로 닫음
                if ok { self.yocheong("GET", "/datgi?sid=\(j?["sid"] as? String ?? s)&tok=\(j?["tok"] as? String ?? tk)", nil) { _, _ in } }
                return
            }
            if ok {
                self.sid = j?["sid"] as? String ?? s
                self.tok = j?["tok"] as? String ?? tk
                self.yeollim = true; self.cheot = true; self.silpae = 0; self.yeollinTtae = Date()
                self.moum = self.moum.suffix(InteonetBonaegi.RATE)   // 밀린 것은 1초만 남김
                self.allim("인터넷 방송: 함께 나가고 있습니다. LVD 방송 앱과 누리집의 지금 현장영상해설 듣기에서 들립니다.")
            } else if code == 403 {
                self.geobu = true; self.moum = Data()
                self.allim("인터넷 방송: 이 전화번호가 협회 현장영상해설사 명단에 없어 인터넷으로는 나가지 않습니다. BYOD 방송은 그대로 나갑니다.")
            } else {
                self.silpae += 1
                if self.silpae >= 2 { self.cell.toggle(); self.silpae = 0 }
                self.allim("인터넷 방송: 인터넷에 닿지 않습니다. 저절로 다시 시도합니다. BYOD 방송은 그대로 나갑니다.")
            }
        }
    }

    private func dolgi() {
        guard dolgo, !geobu else { return }
        if !yeollim { yeolgi(); return }
        guard !moum.isEmpty, !bonaeneun else { return }
        let meori = cheot
        let body = meori ? InteonetBonaegi.wavMeori() + moum : moum
        let cho = Double(moum.count) / Double(InteonetBonaegi.RATE)
        moum = Data()
        bonaeneun = true
        let s = sid
        yocheong("POST", "/jogak?sid=\(sid)&tok=\(tok)", body) { code, _ in
            self.bonaeneun = false
            guard self.dolgo, s == self.sid else { return }
            if code == 200 {
                if meori { self.cheot = false }
                self.silpae = 0
                self.bonaenCho += cho
                self.allim("인터넷 방송: 함께 나가고 있습니다. LVD 방송 앱과 누리집의 지금 현장영상해설 듣기에서 들립니다.")
            } else if code == 404 || code == 403 {   // 중계가 다시 켜져 방이 없어짐 — 새로 엶
                if self.flintBangsik && self.bonaenCho > 5 { GamchumDaegi.deohagi(s, self.yeollinTtae) }
                self.yeollim = false; self.bonaenCho = 0; self.yeolgiTtae = .distantPast
                self.allim("인터넷 방송: 중계가 다시 켜져 방을 새로 엽니다.")
            } else {
                self.silpae += 1
                if self.silpae >= 3 { self.cell.toggle(); self.silpae = 0 }
                self.allim("인터넷 방송: 인터넷에 닿지 않습니다. 저절로 다시 시도합니다. BYOD 방송은 그대로 나갑니다.")
            }
        }
    }

    /// 뮤 8비트 한 줄기 1초에 16000 웨이브 머리(길이는 모름 — 흘려보내는 방식)
    static func wavMeori() -> Data {
        var d = Data()
        func u32(_ v: UInt32) { var x = v.littleEndian; d.append(Data(bytes: &x, count: 4)) }
        func u16(_ v: UInt16) { var x = v.littleEndian; d.append(Data(bytes: &x, count: 2)) }
        d.append(contentsOf: Array("RIFF".utf8)); u32(0xFFFFFFFF); d.append(contentsOf: Array("WAVE".utf8))
        d.append(contentsOf: Array("fmt ".utf8)); u32(16); u16(7); u16(1); u32(UInt32(RATE)); u32(UInt32(RATE)); u16(1); u16(8)
        d.append(contentsOf: Array("data".utf8)); u32(0xFFFFFFFF)
        return d
    }

    // MARK: 짧은 https 요청 — 휴대전화망을 고를 수 있게 Network 로 직접(URLSession 은 길을 고를 수 없음)

    private func yocheong(_ m: String, _ gil: String, _ mom: Data?, _ kkeut: @escaping (Int, Data?) -> Void) {
        HttpsHana(m: m, gil: gil, mom: mom, cell: cell, q: q, kkeut: kkeut).sijak()
    }
}

/// https 요청 하나 — 연결·보내기·받기·풀기를 한 곳에서, 10초 넘으면 그만둠
final class HttpsHana {
    private let m: String, gil: String, mom: Data?, cell: Bool
    private let q: DispatchQueue
    private let kkeut: (Int, Data?) -> Void
    private var c: NWConnection?
    private var batum = Data()
    private var kkeutnam = false
    private var nama: HttpsHana?   // 끝날 때까지 스스로를 붙잡아 둠

    init(m: String, gil: String, mom: Data?, cell: Bool, q: DispatchQueue, kkeut: @escaping (Int, Data?) -> Void) {
        self.m = m; self.gil = gil; self.mom = mom; self.cell = cell; self.q = q; self.kkeut = kkeut
    }

    func sijak() {
        nama = self
        let tls = NWProtocolTLS.Options()
        sec_protocol_options_set_tls_server_name(tls.securityProtocolOptions, InteonetBonaegi.HOST)
        let p = NWParameters(tls: tls, tcp: NWProtocolTCP.Options())
        if cell { p.requiredInterfaceType = .cellular }
        let c = NWConnection(host: NWEndpoint.Host(InteonetBonaegi.HOST), port: NWEndpoint.Port(rawValue: InteonetBonaegi.PORT)!, using: p)
        self.c = c
        c.stateUpdateHandler = { [weak self] st in
            guard let s = self else { return }
            switch st {
            case .ready: s.bonaegi()
            case .failed, .waiting, .cancelled: s.machim(0, nil)
            default: break
            }
        }
        c.start(queue: q)
        q.asyncAfter(deadline: .now() + 10) { [weak self] in self?.machim(0, nil) }
    }

    private func bonaegi() {
        guard let c = c else { return }
        var h = "\(m) \(gil) HTTP/1.1\r\nHost: \(InteonetBonaegi.HOST):\(InteonetBonaegi.PORT)\r\nUser-Agent: Mozilla/5.0 (iPhone) LVDsongchul/\(Bang.PAN)\r\nAccept: */*\r\nConnection: close\r\n"
        if let b = mom { h += "Content-Type: application/octet-stream\r\nContent-Length: \(b.count)\r\n" }
        h += "\r\n"
        var out = Data(h.utf8)
        if let b = mom { out.append(b) }
        c.send(content: out, completion: .contentProcessed { [weak self] e in if e != nil { self?.machim(0, nil) } })
        batgi()
    }

    private func batgi() {
        c?.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] d, _, done, e in
            guard let s = self else { return }
            if let d = d { s.batum.append(d) }
            if done || e != nil {
                let (code, mom) = HttpsHana.pulgi(s.batum)
                s.machim(code, mom)
            } else if let r = HttpsHana.dadoem(s.batum) {
                s.machim(r.0, r.1)   // 길이만큼 다 받았으면 닫히기를 기다리지 않음
            } else { s.batgi() }
        }
    }

    private func machim(_ code: Int, _ d: Data?) {
        guard !kkeutnam else { return }
        kkeutnam = true
        c?.stateUpdateHandler = nil
        c?.cancel(); c = nil
        kkeut(code, d)
        nama = nil
    }

    /// 머리와 몸 나누기 — (상태 번호, 몸)
    static func pulgi(_ b: Data) -> (Int, Data?) {
        guard let r = b.range(of: Data("\r\n\r\n".utf8)) else { return (0, nil) }
        let meori = String(data: b.subdata(in: 0..<r.lowerBound), encoding: .utf8) ?? ""
        let code = Int(meori.split(separator: " ").dropFirst().first ?? "") ?? 0
        var mom = b.subdata(in: r.upperBound..<b.count)
        if meori.lowercased().contains("transfer-encoding: chunked") { mom = dechunk(mom) }
        return (code, mom)
    }

    /// Content-Length 가 있고 그만큼 다 왔으면 끝난 것으로 봄
    static func dadoem(_ b: Data) -> (Int, Data?)? {
        guard let r = b.range(of: Data("\r\n\r\n".utf8)) else { return nil }
        let meori = (String(data: b.subdata(in: 0..<r.lowerBound), encoding: .utf8) ?? "").lowercased()
        guard let k = meori.components(separatedBy: "\r\n").first(where: { $0.hasPrefix("content-length:") }),
              let n = Int(k.dropFirst("content-length:".count).trimmingCharacters(in: .whitespaces)),
              b.count - r.upperBound >= n else { return nil }
        return pulgi(b)
    }

    static func dechunk(_ b: Data) -> Data {
        var out = Data(), i = b.startIndex
        let crlf = Data("\r\n".utf8)
        while i < b.endIndex {
            guard let r = b.range(of: crlf, in: i..<b.endIndex) else { break }
            let hex = String(data: b.subdata(in: i..<r.lowerBound), encoding: .ascii)?.split(separator: ";").first ?? ""
            guard let n = Int(hex.trimmingCharacters(in: .whitespaces), radix: 16), n > 0 else { break }
            let s = r.upperBound, e = min(b.endIndex, s + n)
            out.append(b.subdata(in: s..<e))
            i = min(b.endIndex, e + 2)
        }
        return out
    }
}

/// 공유기 녹음이 다시 듣기에 올라간 뒤 감출 인터넷 녹음(중계 방 번호)
/// 한 줄 「방 번호|연 때(초)」. 그 때를 품은 공유기 녹음이 다시 듣기에 올라간 것을 확인한 뒤에만 감춤
enum GamchumDaegi {
    static let KEY = "gamchum_daegi"
    static var mokrok: [(sid: String, ttae: Date)] {
        (UserDefaults.standard.stringArray(forKey: KEY) ?? []).compactMap { j in
            let p = j.split(separator: "|")
            guard p.count == 2, let t = Double(p[1]) else { return nil }
            return (String(p[0]), Date(timeIntervalSince1970: t))
        }
    }
    static func deohagi(_ sid: String, _ ttae: Date) {
        var l = UserDefaults.standard.stringArray(forKey: KEY) ?? []
        if !l.contains(where: { $0.hasPrefix(sid + "|") }) { l.append("\(sid)|\(Int(ttae.timeIntervalSince1970))") }
        UserDefaults.standard.set(Array(l.suffix(50)), forKey: KEY)
    }
    static func ppaegi(_ sid: String) {
        UserDefaults.standard.set((UserDefaults.standard.stringArray(forKey: KEY) ?? []).filter { !$0.hasPrefix(sid + "|") }, forKey: KEY)
    }
}
