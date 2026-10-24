// LVD 송출 아이폰 1.9.0(빌드 261024-BI11) — 현장 기능 서버 길 (새방송클, 대조표 송출 21·22·23번)
// 이사장님이 9월 24·27·28일에 승인하신 노트북판 현장 해설방송(hyeonjang.cs 3.3판)의 길을, 아이피타임 방식에서 방송 서버인 이 아이폰으로 옮겼습니다.
// 같은 길·같은 인자·같은 답(JSON 칸 이름)·같은 403 글·같은 시간 잣대를 써서, 노트북판 화면(deut.html·pd.html·bang.html)이 고치지 않고 돕니다.
// Seobeo 가 요청을 읽어 여기로 넘기고(cheori), 여기서 만든 답을 Seobeo 가 보냅니다. 듣기 줄기(/stream)와 듣는 화면(/)은 Seobeo 가 직접 맡습니다.
//   뺀 것: /naemic(윈도우 마이크), /saepan_batgi(노트북 새 판), DNS(앱은 못 함), 자물쇠(https) 문.
//   아이폰에는 자물쇠 문이 없어 /bang·/mujeon 도 http 로 그대로 냅니다. 그래서 다른 폰의 마이크·카메라(무대 카메라, 관계자 무전, 폰 해설)는
//   열리지 않습니다 — 피디 화면 상태 글(sjuso·siheom)과 도움말에 「안드로이드 LVD 송출이나 플린트 2에서」라고 알립니다.
//   새로 넣은 것(9월 28일 「지금 하기로」): 여러 갈래 해설(gal, /gallae_ireum), 구역별·공유기별 인원(/chulseok guyeok, /status mang),
//   만족도 묻기(/manjok_sijak, /manjok, /manjok_mok). 소리 나누어 조절은 듣는 화면(deut.html) 안에서만 하므로 서버 일이 없습니다.
// 상황(Sang)은 잠금(jam) 하나 안에서만 만지고, 파일 쓰기는 한 줄(fq)에서 차례로 합니다.
// 1분마다 행사 폴더(이 아이폰 행사 녹음과 같은 폴더)에 상황·결과보고를 저장하고, 앱이 꺼졌다 다시 켜지면 오늘 저장한 상황을 이어 받습니다.
import Foundation

/// 받은 요청 하나(Seobeo 가 읽어 넘김)
struct HjYocheong {
    let method: String
    let gil: String
    let mul: String      // 물음표 뒤(풀지 않은 그대로 — 노트북판 Gap 과 같음)
    let host: String
    let ua: String
    let mom: Data
    let ip: String       // 들어온 쪽 주소
    let loop: Bool       // 이 아이폰 자신(127.0.0.1)
}

/// 보낼 답 하나
struct HjDap {
    let code: Int
    let type: String
    let mom: Data
    static func json(_ t: String, _ code: Int = 200) -> HjDap { HjDap(code: code, type: "application/json", mom: Data(t.utf8)) }
    static func geul(_ t: String, _ code: Int = 200, type: String = "text/plain; charset=utf-8") -> HjDap { HjDap(code: code, type: type, mom: Data(t.utf8)) }
}

// MARK: 도우미(노트북판 Gap·Pul·Jsn2·Susja·Ds·Nanseu 와 같은 일)

fileprivate func hjPyo(_ f: String) -> DateFormatter {
    let d = DateFormatter()
    d.locale = Locale(identifier: "en_US_POSIX")
    d.timeZone = TimeZone(identifier: "Asia/Seoul")
    d.dateFormat = f
    return d
}
fileprivate let hjNal = hjPyo("yyyyMMdd")
fileprivate let hjSi = hjPyo("HH:mm:ss")
fileprivate let hjBun = hjPyo("HH:mm")
fileprivate let hjModu = hjPyo("yyyy-MM-dd HH:mm:ss")
fileprivate let hjPailSi = hjPyo("yyyyMMdd_HHmmss")
fileprivate let hjBogoTtae = hjPyo("yyyy년 M월 d일 HH:mm")

fileprivate func hjPul(_ s: String) -> String {
    let t = s.replacingOccurrences(of: "+", with: " ")
    return t.removingPercentEncoding ?? t
}
fileprivate func hjJsn(_ s: String) -> String {
    var b = ""
    for c in s.unicodeScalars {
        if c == "\"" || c == "\\" { b.append("\\"); b.unicodeScalars.append(c) }
        else if c.value < 32 { b.append(" ") }
        else { b.unicodeScalars.append(c) }
    }
    return b
}
fileprivate func hjSusja(_ n: String) -> Bool {
    if n.isEmpty || n.count > 4 { return false }
    for ch in n.unicodeScalars where ch.value < 48 || ch.value > 57 { return false }
    return true
}
fileprivate func hjApgi(_ n: String) -> String {
    let t = String(n.drop(while: { $0 == "0" }))
    return t.isEmpty ? "0" : t
}
fileprivate func hjJareugi(_ t: String, _ n: Int) -> String { t.count > n ? String(t.prefix(n)) : t }
fileprivate func hjDs(_ v: Double) -> String {
    var t = String(format: "%.2f", v)
    if t.contains(".") {
        while t.hasSuffix("0") { t.removeLast() }
        if t.hasSuffix(".") { t.removeLast() }
    }
    if t == "-0" { t = "0" }
    return t
}
fileprivate func hjNanseu(_ n: Int) -> String {
    let a = Array("abcdefghjkmnpqrstuvwxyz23456789")
    var s = ""
    for _ in 0..<n { s.append(a[Int.random(in: 0..<a.count)]) }
    return s
}
/// 기록 파일 한 칸 — 쉼표와 줄바꿈을 빈칸으로
fileprivate func hjCsv(_ t: String) -> String {
    t.replacingOccurrences(of: ",", with: " ").replacingOccurrences(of: "\r", with: " ").replacingOccurrences(of: "\n", with: " ")
}
/// 상황 파일 한 칸 — 가름표(|)와 줄바꿈을 빈칸으로
fileprivate func hjKan(_ t: String) -> String {
    t.replacingOccurrences(of: "|", with: " ").replacingOccurrences(of: "\r", with: " ").replacingOccurrences(of: "\n", with: " ")
}
fileprivate func hjEuckr(_ t: String) -> Data {
    let e = String.Encoding(rawValue: CFStringConvertEncodingToNSStringEncoding(CFStringEncoding(CFStringEncodings.EUC_KR.rawValue)))
    if let b = t.data(using: e, allowLossyConversion: true) { return b }
    return Data([0xEF, 0xBB, 0xBF]) + Data(t.utf8)
}
fileprivate func hjCho(_ s: String) -> Date? {
    guard let v = Double(s), v > 0 else { return nil }
    return Date(timeIntervalSince1970: v)
}

final class HyeonjangGil {
    static let shared = HyeonjangGil()
    static let JARYO_URL = "https://lvd.ada.or.kr/bfblive/hyeonjang/jaryo.json"
    static let GAL_GIBON: [Int: String] = [1: "주 해설", 2: "두 번째 해설", 3: "세 번째 해설"]
    static let MANJOK_GIBON = "오늘 현장영상해설은 어떠셨습니까?"
    static let MANJOK_MAL = ["", "매우 아쉬움", "아쉬움", "보통", "좋음", "매우 좋음"]
    /// 피디 화면 상태 글 — 아이폰에는 자물쇠(https) 문이 없음
    static let JAMULSOE_EOPSEUM = "이 아이폰 방송 서버는 자물쇠(https) 주소가 없어 다른 폰의 마이크와 카메라가 열리지 않습니다. 무대 카메라와 관계자 무전, 폰 해설 마이크는 안드로이드 LVD 송출이나 플린트 2 공유기 방식에서 쓰십시오. 이 아이폰의 사운드카드 현장영상해설과 피디 화면의 다른 기능은 그대로 됩니다."
    private static let ANGWAN = "{\"ok\":false,\"mal\":\"관계자로 붙은 뒤에 쓸 수 있습니다\"}"

    /// 노트북판과 같은 이름으로 — 노트북판의 지역 변수 이름을 그대로 따름
    static func gap(_ q: String, _ k: String) -> String {
        for p in q.split(separator: "&", omittingEmptySubsequences: false) {
            if let e = p.firstIndex(of: "=") {
                if e > p.startIndex && p[p.startIndex..<e] == Substring(k) { return String(p[p.index(after: e)...]) }
            } else if p == Substring(k) {
                return ""
            }
        }
        return ""
    }

    // MARK: 상황

    struct Bh { var cheo: Date; var maji: Date }
    struct Deutnun { var ttae: Date; var kkeun: Int; var n: String }
    struct Yc { var seq: Int; var t: String; var n: String; var k: String; var cheori: Bool; var ttae: Date }
    struct Mal { var seq: Int; var t: String; var nm: String; var mal: String }
    struct Kam { var jpeg: Data; var ttae: Date; var nm: String; var zmin: Double; var zmax: Double; var z: Double; var zreq: Double; var zseq: Int }
    struct Gwan { var r: String; var nm: String; var cheo: Date; var maji: Date }
    struct Mj { var id: Int; var ttae: Date; var nae: String; var nm: String; var to: String; var jong: String; var mime: String; var mom: Data? }
    struct Jiyeon { var ms: Double; var ttae: Date }
    struct Girok { var ttae: Date; var n: String; var mal: String }
    struct Pyosi { var ttae: Date; var pail: String; var cho: Int; var ss: String; var nm: String; var t: String }
    struct MjDap { var n: String; var p: Int; var t: String; var ttae: Date }
    struct MjHoe { var seq: Int; var q: String; var sijak: Date; var dap: [String: MjDap] }

    /// 현장 상황 한 덩어리 — 값이라 그대로 베껴 두면 스스로 점검 앞 상황이 됨
    struct Sang {
        var nal = ""
        var gidari: [String: String] = [:]
        var gidariTtae: [String: Date] = [:]
        var nasMal = ""
        var micTtae = Date.distantPast
        var bangsik = 1
        var cue = true                       // 피디 화면의 해설 시작·방송중지(듣는 분께 보내는 것만 막음, 녹음은 그대로)
        var bhMok: [String: Bh] = [:]
        var bhGuyeok: [String: String] = [:]
        var dangMok: [String] = []
        var dangcheom = ""
        var dangSik = ""
        var dangSeq = 0
        var dangGirok: [Girok] = []
        var sunseoI = 0
        var sunseoT: Date? = nil
        var deutMok: [String: Deutnun] = [:]
        var ycMok: [Yc] = []
        var ycSeq = 0
        var ycGeuje: [String: Date] = [:]
        var malMok: [Mal] = []
        var malSeq = 0
        var kamMok: [String: Kam] = [:]
        var deutChoeda = 0
        var deutChoedaT: Date? = nil
        var ginMal = ""
        var ginSeq = 0
        var ginGirok: [Girok] = []
        var gwanKey = ""
        var gwanMok: [String: Gwan] = [:]
        var mjMok: [Mj] = []
        var mjSeq = 0
        var jiyeon: [String: Jiyeon] = [:]
        var pyosiMok: [Pyosi] = []
        var galIreum: [Int: String] = HyeonjangGil.GAL_GIBON
        var galBaiteu: [Int: Int] = [:]
        var manjokSeq = 0
        var manjokQ = ""                     // 지금 묻는 물음(끝나면 빈 글)
        var manjokHoe: [MjHoe] = []
    }

    private let jam = NSLock()
    private var s = Sang()
    private var mideum = Set<String>()       // 피디 화면이 받아 준 폰(노트북판 mideum.txt)
    private var jg: Sang? = nil              // 스스로 점검 앞 상황
    private var jgTtae = Date.distantPast
    private var dolgo = false
    private var tm: DispatchSourceTimer?
    private var jadongMal = ""
    private var jadongIeo = ""
    private var nokOlrimMal = ""
    private let fq = DispatchQueue(label: "byod.hyeonjang.pail")   // 파일 쓰기 한 줄
    private var galF: [Int: FileHandle] = [:]                     // fq 안에서만 — 갈래 2·3 녹음
    private var galFTtae: [Int: Date] = [:]
    private var galSync: [Int: Date] = [:]
    /// 피디 번호(네 자리) — 그날 처음 켤 때 정하고, 같은 날 다시 켜도 그대로(붙어 있던 피디 화면이 끊기지 않게)
    let pdKey: String

    private init() {
        let ud = UserDefaults.standard
        let nal = hjNal.string(from: Date())
        if let k = ud.string(forKey: "hj_pdkey"), k.count == 4, ud.string(forKey: "hj_pdkey_nal") == nal {
            pdKey = k
        } else {
            let k = String(Int.random(in: 1000...9998))
            ud.set(k, forKey: "hj_pdkey")
            ud.set(nal, forKey: "hj_pdkey_nal")
            pdKey = k
        }
    }

    /// jam 안에서 — 스스로 점검 중(길어도 5분)
    private var jgNow: Bool { jg != nil && Date().timeIntervalSince(jgTtae) < 300 }

    /// 피디 화면이 해설 시작을 눌러 둔 상태인가(Seobeo 가 듣는 분께 보낼지 볼 때)
    var cueKyeojim: Bool { jam.lock(); defer { jam.unlock() }; return s.cue }

    static var gibonTeo: URL {
        let u = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("byod_hyeonjang", isDirectory: true)
        try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true)
        return u
    }

    /// 행사 폴더 — 이 아이폰이 행사 녹음 중이면 그 폴더, 아니면 앱 안 현장 폴더
    private func teo() -> URL {
        let sid = GigiNokeum.shared.sid
        if !sid.isEmpty {
            let u = HaengsaOlligi.teo.appendingPathComponent(sid)
            try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true)
            return u
        }
        return HyeonjangGil.gibonTeo
    }

    // MARK: 켜고 끄기

    /// 방송 서버를 열 때(Seobeo.sijak) — 날이 바뀌었으면 새로, 메모리가 비었으면 오늘 저장한 상황을 이어 받고, 1분마다 저장
    func sijak() {
        let nal = hjNal.string(from: Date())
        jam.lock()
        if s.nal != nal { s = Sang(); s.nal = nal; jg = nil; jadongIeo = "" }
        s.cue = true
        let bium = s.bhMok.isEmpty && s.ycMok.isEmpty && s.dangMok.isEmpty
        dolgo = true
        jam.unlock()
        mideumBulleo()
        if bium { sangtaeBulleo() }
        let t = DispatchSource.makeTimerSource(queue: fq)
        t.schedule(deadline: .now() + 5, repeating: 60)
        t.setEventHandler { [weak self] in self?.jeojang() }
        jam.lock(); let ye = tm; tm = t; jam.unlock()
        ye?.cancel()
        t.resume()
    }

    /// 방송 서버를 닫을 때(Seobeo.meomchugi) — 마지막으로 한 번 저장(행사 마감 결과보고에 들어감), 갈래 녹음 닫기
    func kkeut() {
        jam.lock()
        let t = tm
        tm = nil
        let d = dolgo
        dolgo = false
        jam.unlock()
        t?.cancel()
        guard d else { return }
        fq.sync {
            self.jeojang()
            for (_, f) in self.galF { f.synchronizeFile(); f.closeFile() }
            self.galF.removeAll()
            self.galFTtae.removeAll()
            self.galSync.removeAll()
        }
    }

    // MARK: 권한(heoga) — 이 아이폰 자신, 피디 번호, 받아 준 폰, 붙은 관계자

    private func heogaInga(_ q: String, loop: Bool) -> Bool {
        if loop || HyeonjangGil.gap(q, "k") == pdKey { return true }
        let tok = HyeonjangGil.gap(q, "t")
        var gt = HyeonjangGil.gap(q, "gt")
        if gt.isEmpty { gt = tok }
        jam.lock(); defer { jam.unlock() }
        if tok.count >= 16 && mideum.contains(tok) { return true }
        if gt.count >= 10 && s.gwanMok[gt] != nil { s.gwanMok[gt]?.maji = Date(); return true }
        return false
    }

    private func j(_ t: String, _ code: Int = 200) -> HjDap { HjDap.json(t, code) }

    // MARK: 길 나누기

    func cheori(_ rq: HjYocheong) -> HjDap {
        let g = rq.gil
        let q = rq.mul
        let heoga = heogaInga(q, loop: rq.loop)
        let maeum: Set<String> = ["/yocheong_mok", "/yocheong_cheori", "/hs_mal", "/hs_mal_mok", "/kamera_mok", "/kamera_bogi", "/pyosi"]
        if !heoga && (maeum.contains(g) || (g == "/sunseo" && !HyeonjangGil.gap(q, "i").isEmpty)) { return j(HyeonjangGil.ANGWAN, 403) }
        switch g {
        case "/bang": return hwamyeon("bang.html")       // 아이폰에는 자물쇠 문이 없어 http 로 그대로
        case "/pd", "/hs", "/gwan", "/mujeon": return hwamyeon("pd.html")
        case "/status": return sangtae(heoga)
        case "/cue": return cue(q, heoga)
        case "/mic":
            if !heoga { return HjDap.geul("없는 주소입니다.", 404) }
            jam.lock(); s.micTtae = Date(); let k = s.cue; jam.unlock()
            return j("{\"ok\":true,\"keu\":\(Bang.shared.keu && k)}")
        case "/nas":
            if !heoga { return HjDap.geul("없는 주소입니다.", 404) }
            let t = hjPul(HyeonjangGil.gap(q, "t"))
            jam.lock(); s.nasMal = t; jam.unlock()
            return j("{\"ok\":true}")
        case "/bangsik":
            if !heoga { return HjDap.geul("없는 주소입니다.", 404) }
            jam.lock(); s.bangsik = HyeonjangGil.gap(q, "v") == "1" ? 1 : 0; let v = s.bangsik; jam.unlock()
            return j("{\"ok\":true,\"bangsik\":\(v)}")
        case "/siheom":
            if !heoga { return HjDap.geul("없는 주소입니다.", 404) }
            return j("{\"ok\":false,\"msg\":\"이 아이폰 방송 서버에서는 버티기 시험을 하지 않습니다.\"}")
        case "/deulgi": return deulgi(q, rq.ua, heoga)
        case "/batgi":
            if !heoga { return HjDap.geul("없는 주소입니다.", 404) }
            return batgi(q)
        case "/beonho":
            if rq.loop && HyeonjangGil.gap(q, "n").isEmpty { return HjDap.geul(pdKey, 200, type: "text/plain") }
            return beonho(q)
        case "/chulseok", "/chucheom", "/dangcheom":
            if !heoga { return j("{\"ok\":false}", 403) }
            if g == "/chulseok" { return chulseok() }
            if g == "/chucheom" { return chucheom(q) }
            return dangcheom(q)
        case "/deutnun": return deutnun(q)
        case "/yocheong": return yocheong(q)
        case "/yocheong_cheori": return yocheongCheori(q)
        case "/yocheong_mok": return yocheongMok()
        case "/hs_mal", "/hs_mal_mok": return malCheori(g, q)
        case "/pyosi": return pyosi(q)
        case "/nokeum_sangtae": return nokeumSangtae()
        case "/nokeum_olligi":
            if !heoga { return j("{\"ok\":false}", 403) }
            return nokeumOlligi()
        case "/kamera", "/kamera_zoom", "/kamera_bogi", "/kamera_mok": return kamera(g, q, rq, heoga)
        case "/gwan_qr", "/gwan_deung", "/gwan_mok": return gwanCheori(g, q, rq, heoga)
        case "/mj_bonae", "/mj_hochul", "/mj_mok", "/mj_deul", "/mj_saram": return mujeon(g, q, rq, heoga)
        case "/gingeup": return gingeup(q, heoga)
        case "/jeomgeom_sijak", "/jeomgeom_kkeut": return jeomgeom(g, q, loop: rq.loop)
        case "/jadong":
            jam.lock(); let m = jadongMal; jam.unlock()
            return j("{\"mal\":\"\(hjJsn(m))\",\"teo\":\"\(hjJsn("LVD 송출 앱 안 행사 폴더 " + teo().lastPathComponent))\"}")
        case "/bogoseo", "/bogoseo.txt": return bogoseo(g, heoga)
        case "/jaryo", "/jaryo_badgi", "/sunseo": return haeseol(g, q)
        case "/jiyeon": return jiyeon(q)
        case "/pcm":
            if rq.method != "POST" { return HjDap.geul("없는 주소입니다.", 404) }
            return pcm(rq, heoga)
        case "/gallae_ireum": return gallaeIreum(q, heoga)
        case "/manjok_sijak", "/manjok", "/manjok_mok": return manjok(g, q, heoga)
        default: return HjDap.geul("없는 주소입니다.", 404)
        }
    }

    /// 화면 파일(나스에서 받아 담아 둔 것) — 없으면 받아 두는 법을 알림
    private func hwamyeon(_ nm: String) -> HjDap {
        if let d = Jaryo.pail(nm) { return HjDap(code: 200, type: "text/html; charset=utf-8", mom: d) }
        return HjDap.geul("<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>LVD 송출 피디 화면</title></head>" +
                          "<body style=\"font-family:sans-serif;font-size:20px;padding:16px;line-height:1.7\"><h1 style=\"font-size:24px\">화면 파일이 아직 없습니다</h1>" +
                          "<p>이 아이폰에 \(nm) 파일이 아직 담기지 않았습니다. 인터넷이 되는 곳에서 LVD 송출 앱의 더 보기, 새로고침을 누른 뒤 다시 여십시오.</p></body></html>",
                          200, type: "text/html; charset=utf-8")
    }

    // MARK: 상태

    private func sangtae(_ heoga: Bool) -> HjDap {
        let sb = Seobeo.shared
        let n = sb.su
        let galSu = sb.galSu
        let mang = heoga ? sb.mang : [:]
        let gon = [sb.galKyeojim(1), sb.galKyeojim(2), sb.galKyeojim(3)]
        let bangKeu = Bang.shared.keu
        let djuso = Juso.deutgiJuso()
        let jy = heoga ? jiyeonMal() : ""
        let now = Date()
        jam.lock()
        let keu = bangKeu && s.cue
        let mic = gon[0] || now.timeIntervalSince(s.micTtae) < 4
        var extra = ""
        if heoga {
            let nalgeun = s.gidariTtae.filter { now.timeIntervalSince($0.value) > 20 }.map { $0.key }
            for x in nalgeun { s.gidari[x] = nil; s.gidariTtae[x] = nil }
            let gb = s.gidari.map { "{\"t\":\"\(hjJsn($0.key))\",\"nm\":\"\(hjJsn($0.value))\"}" }.joined(separator: ",")
            let mb = mang.keys.sorted().map { "{\"m\":\"\(hjJsn($0))\",\"su\":\(mang[$0] ?? 0)}" }.joined(separator: ",")
            extra = ",\"sjuso\":\"\(hjJsn(HyeonjangGil.JAMULSOE_EOPSEUM))\",\"djuso\":\"\(hjJsn(djuso))\",\"gidari\":[\(gb)]" +
                ",\"bangsik\":\(s.bangsik),\"dns\":0,\"hwakin\":0,\"jiyeon\":\"\(hjJsn(jy))\",\"siheom\":\"\(hjJsn(HyeonjangGil.JAMULSOE_EOPSEUM))\",\"sij\":false" +
                ",\"mang\":[\(mb)]"
        }
        var galL: [String] = []
        for i in 1...3 { galL.append("{\"n\":\(i),\"nm\":\"\(hjJsn(s.galIreum[i] ?? ""))\",\"on\":\(gon[i - 1]),\"su\":\(galSu[i] ?? 0)}") }
        let t = "{\"hyeonjang\":true,\"byod\":true,\"keu\":\(keu),\"mic\":\(mic),\"n\":\(n),\"pan\":\"LVD 송출 아이폰 \(Bang.PAN)\",\"bild\":\"\(Bang.BILD)\",\"nas\":\"\(hjJsn(s.nasMal))\"" + extra +
            ",\"dang\":\"\(s.dangcheom)\",\"dseq\":\(s.dangSeq),\"dsik\":\"\(s.dangSik)\",\"dmok\":[\(dangJson())],\"gin\":\"\(hjJsn(s.ginMal))\",\"gseq\":\(s.ginSeq),\"saepan\":\"\"" +
            ",\"gal\":[\(galL.joined(separator: ","))],\"mj\":\"\(hjJsn(s.manjokQ))\",\"mjseq\":\(s.manjokSeq)}"
        jam.unlock()
        return j(t)
    }

    /// jam 안에서
    private func dangJson() -> String { s.dangMok.map { "\"\($0)\"" }.joined(separator: ",") }

    private func cue(_ q: String, _ heoga: Bool) -> HjDap {
        if !heoga { return j("{\"ok\":false,\"msg\":\"해설사 번호가 맞지 않습니다.\"}", 403) }
        let v = HyeonjangGil.gap(q, "v") == "1"
        jam.lock(); s.cue = v; jam.unlock()
        return j("{\"ok\":true,\"keu\":\(Bang.shared.keu && v)}")
    }

    private func jiyeonMal() -> String {
        let now = Date()
        var hap = 0.0, keun = 0.0
        var su = 0
        jam.lock()
        for (k, v) in s.jiyeon {
            if now.timeIntervalSince(v.ttae) > 20 { s.jiyeon[k] = nil; continue }
            hap += v.ms; su += 1
            if v.ms > keun { keun = v.ms }
        }
        jam.unlock()
        if su == 0 { return "" }
        let p = hap / Double(su) + 200
        return "소리 늦음 — 듣는 폰 \(su)대 평균 약 " + String(format: "%.1f", p / 1000) + "초, 가장 늦은 폰 약 " + String(format: "%.1f", (keun + 200) / 1000) + "초."
    }

    private func jiyeon(_ q: String) -> HjDap {
        let id = HyeonjangGil.gap(q, "i")
        if id.count >= 6 && id.count <= 40, let ms = Double(HyeonjangGil.gap(q, "d")), ms >= 0 && ms < 60000 {
            jam.lock()
            if s.jiyeon[id] != nil || s.jiyeon.count < 3000 { s.jiyeon[id] = Jiyeon(ms: ms, ttae: Date()) }
            jam.unlock()
        }
        return j("{\"ok\":true}")
    }

    // MARK: 폰 받아들이기

    private func deulgi(_ q: String, _ ua: String, _ heoga: Bool) -> HjDap {
        if heoga { return j("{\"ok\":true}") }
        let tok = HyeonjangGil.gap(q, "t")
        if tok.count < 16 || tok.count > 64 { return j("{\"ok\":false}") }
        let nm = ua.contains("iPhone") ? "아이폰" : (ua.contains("iPad") ? "아이패드" : (ua.contains("Android") ? "안드로이드 폰" : "컴퓨터"))
        jam.lock(); defer { jam.unlock() }
        if s.gidari[tok] == nil && s.gidari.count > 20 { return j("{\"ok\":false}") }
        s.gidari[tok] = nm
        s.gidariTtae[tok] = Date()
        return j("{\"ok\":false,\"daegi\":true}")
    }

    private func batgi(_ q: String) -> HjDap {
        let b = HyeonjangGil.gap(q, "b")
        jam.lock()
        var sae = false
        if s.gidari[b] != nil {
            s.gidari[b] = nil; s.gidariTtae[b] = nil
            mideum.insert(b); sae = !jgNow
        }
        jam.unlock()
        if sae {
            fq.async {
                let u = HyeonjangGil.gibonTeo.appendingPathComponent("mideum.txt")
                let fm = FileManager.default
                if !fm.fileExists(atPath: u.path) { fm.createFile(atPath: u.path, contents: nil) }
                if let f = try? FileHandle(forWritingTo: u) { _ = f.seekToEndOfFile(); f.write(Data((b + "\r\n").utf8)); f.closeFile() }
            }
        }
        return j("{\"ok\":true}")
    }

    private func mideumBulleo() {
        guard let t = try? String(contentsOf: HyeonjangGil.gibonTeo.appendingPathComponent("mideum.txt"), encoding: .utf8) else { return }
        var m = Set<String>()
        for l in t.components(separatedBy: "\n") {
            let x = l.trimmingCharacters(in: .whitespacesAndNewlines)
            if x.count >= 16 { m.insert(x) }
        }
        jam.lock(); mideum.formUnion(m); jam.unlock()
    }

    // MARK: 입장 번호·참석·추첨

    private func beonho(_ q: String) -> HjDap {
        let n0 = HyeonjangGil.gap(q, "n")
        guard hjSusja(n0) else { return j("{\"ok\":false}", 400) }
        let n = hjApgi(n0)
        let gy = hjJareugi(hjPul(HyeonjangGil.gap(q, "g")).trimmingCharacters(in: .whitespacesAndNewlines), 10)
        let now = Date()
        jam.lock()
        if !gy.isEmpty { s.bhGuyeok[n] = gy }
        var sae = false
        if s.bhMok[n] != nil { s.bhMok[n]?.maji = now } else { s.bhMok[n] = Bh(cheo: now, maji: now); sae = true }
        jam.unlock()
        if sae { girok("chulseok", hjModu.string(from: now) + "," + n + ",처음 연결") }
        return j("{\"ok\":true,\"n\":\"\(n)\"}")
    }

    private func chulseok() -> HjDap {
        let now = Date()
        jam.lock()
        let ks = s.bhMok.keys.sorted { (Int($0) ?? 0) < (Int($1) ?? 0) }
        var modu = 0, jigeum = 0
        var l: [String] = []
        var gyJi: [String: Int] = [:]
        var gyMo: [String: Int] = [:]
        for k in ks {
            guard let v = s.bhMok[k] else { continue }
            let on = now.timeIntervalSince(v.maji) < 75
            modu += 1
            if on { jigeum += 1 }
            let bun = Int((v.maji.timeIntervalSince(v.cheo) / 60).rounded())
            l.append("{\"n\":\"\(k)\",\"cheo\":\"\(hjBun.string(from: v.cheo))\",\"majimak\":\"\(hjBun.string(from: v.maji))\",\"bun\":\(bun),\"jigeum\":\(on)}")
            if let g = s.bhGuyeok[k], !g.isEmpty {   // 1.9.0 구역별 — 번호 카드의 구역
                gyMo[g, default: 0] += 1
                if on { gyJi[g, default: 0] += 1 }
            }
        }
        let gj = gyMo.keys.sorted().map { g in "{\"g\":\"\(hjJsn(g))\",\"jigeum\":\(gyJi[g] ?? 0),\"modu\":\(gyMo[g] ?? 0)}" }.joined(separator: ",")
        let dj = dangJson()
        jam.unlock()
        return j("{\"ok\":true,\"modu\":\(modu),\"jigeum\":\(jigeum),\"dang\":[\(dj)],\"guyeok\":[\(gj)],\"mok\":[\(l.joined(separator: ","))]}")
    }

    private func chucheom(_ q: String) -> HjDap {
        let beom = HyeonjangGil.gap(q, "beom")
        let kkeut = Int(HyeonjangGil.gap(q, "kkeut")) ?? 0
        let ppae = HyeonjangGil.gap(q, "ppae") != "0"
        let now = Date()
        jam.lock()
        var pool: [String] = []
        if beom == "modu" {
            if kkeut >= 1 { for i in 1...min(kkeut, 9999) { pool.append(String(i)) } }
        } else {
            for (k, v) in s.bhMok where now.timeIntervalSince(v.maji) < 75 { pool.append(k) }
        }
        if ppae { let d = Set(s.dangMok); pool.removeAll { d.contains($0) } }
        var ppob = ""
        let mal = "추첨 당첨(" + (beom == "modu" ? "1번부터 \(kkeut)번" : "지금 듣는 번호") + " \(pool.count)개 중)"
        if !pool.isEmpty {
            ppob = pool[Int.random(in: 0..<pool.count)]   // 아이폰의 안전한 난수
            s.dangcheom = ppob; s.dangSik = "r"; s.dangMok.append(ppob); s.dangSeq += 1
            s.dangGirok.append(Girok(ttae: now, n: ppob, mal: mal))
        }
        let seq = s.dangSeq
        jam.unlock()
        if ppob.isEmpty { return j("{\"ok\":false,\"mal\":\"뽑을 번호가 없습니다\"}") }
        girok("chulseok", hjModu.string(from: now) + "," + ppob + "," + mal)
        return j("{\"ok\":true,\"n\":\"\(ppob)\",\"mo\":\(pool.count),\"seq\":\(seq)}")
    }

    private func dangcheom(_ q: String) -> HjDap {
        let n0 = HyeonjangGil.gap(q, "n")
        if !hjSusja(n0) { return j("{\"ok\":false,\"mal\":\"번호를 숫자로 적어 주십시오\"}") }
        let n = hjApgi(n0)
        let now = Date()
        jam.lock()
        if s.dangMok.contains(n) { jam.unlock(); return j("{\"ok\":false,\"mal\":\"\(n)번은 이미 당첨된 번호입니다\"}") }
        s.dangMok.append(n); s.dangcheom = n; s.dangSik = "m"; s.dangSeq += 1
        s.dangGirok.append(Girok(ttae: now, n: n, mal: "직접 호명 당첨"))
        let seq = s.dangSeq
        let deunna = s.bhMok[n].map { now.timeIntervalSince($0.maji) < 75 } ?? false
        jam.unlock()
        girok("chulseok", hjModu.string(from: now) + "," + n + ",직접 호명 당첨")
        return j("{\"ok\":true,\"n\":\"\(n)\",\"seq\":\(seq),\"deunna\":\(deunna)}")
    }

    // MARK: 행사 자료와 순서

    private func haeseol(_ g: String, _ q: String) -> HjDap {
        if g == "/jaryo_badgi" { return j("{\"ok\":\(Jaryo.jaryoBatgi())}") }
        if g == "/jaryo" {
            if Jaryo.pail("jaryo.json") == nil { _ = Jaryo.jaryoBatgi() }
            if let d = Jaryo.pail("jaryo.json") { return HjDap(code: 200, type: "application/json", mom: d) }
            return j("{}")
        }
        let i = HyeonjangGil.gap(q, "i")
        jam.lock()
        if !i.isEmpty && i.count < 4, let n = Int(i), n >= 0 { s.sunseoI = n; s.sunseoT = Date() }
        let si = s.sunseoI
        let jn = s.sunseoT.map { Int(Date().timeIntervalSince($0)) } ?? -1
        jam.unlock()
        return j("{\"i\":\(si),\"jinan\":\(jn)}")
    }

    // MARK: 듣는 분 상태·요청

    /// jam 안에서 — 가장 많이 함께 들은 때
    private func deutSegi() {
        let now = Date()
        var n = 0
        for (_, v) in s.deutMok where now.timeIntervalSince(v.ttae) < 45 { n += 1 }
        if n > s.deutChoeda { s.deutChoeda = n; s.deutChoedaT = now }
    }

    private func deutnun(_ q: String) -> HjDap {
        let i = HyeonjangGil.gap(q, "i")
        if i.isEmpty || i.count > 20 { return j("{\"ok\":false}", 400) }
        var n = HyeonjangGil.gap(q, "n")
        if !hjSusja(n) { n = "" }
        let now = Date()
        jam.lock()
        s.deutMok[i] = Deutnun(ttae: now, kkeun: HyeonjangGil.gap(q, "s") == "1" ? 1 : 0, n: n)
        deutSegi()
        if s.deutMok.count > 3000 { s.deutMok = s.deutMok.filter { now.timeIntervalSince($0.value.ttae) <= 600 } }
        jam.unlock()
        return j("{\"ok\":true}")
    }

    static func ycIreum(_ k: String) -> String {
        switch k {
        case "dowum": return "도움이 필요합니다"
        case "jakda": return "소리가 작아요"
        case "cheoncheon": return "조금 천천히"
        case "gunggeum": return "무엇이 보이는지 궁금해요"
        case "kkeunkim": return "소리가 끊겨요"
        default: return k
        }
    }

    private func yocheong(_ q: String) -> HjDap {
        let i = HyeonjangGil.gap(q, "i")
        var n = HyeonjangGil.gap(q, "n")
        let k = HyeonjangGil.gap(q, "k")
        if !hjSusja(n) { n = "" }
        if !["dowum", "jakda", "cheoncheon", "gunggeum", "kkeunkim"].contains(k) { return j("{\"ok\":false}", 400) }
        let key = i + "|" + k
        let now = Date()
        jam.lock()
        var neomu = false
        if let d = s.ycGeuje[key], now.timeIntervalSince(d) < 20 { neomu = true }
        else {
            s.ycGeuje[key] = now
            s.ycSeq += 1
            s.ycMok.append(Yc(seq: s.ycSeq, t: hjSi.string(from: now), n: n, k: k, cheori: false, ttae: now))
            if s.ycMok.count > 300 { s.ycMok.removeFirst() }
        }
        let jgJung = jgNow
        jam.unlock()
        if !neomu {
            girok("yocheong", hjSi.string(from: now) + "," + n + "," + HyeonjangGil.ycIreum(k))
            if !jgJung { _ = Bang.shared.yocheongBatgi(beonho: n, jongryu: k) }   // 첫 화면 한 줄 알림과 행사 녹음 기록(스스로 점검 시험은 알리지 않음)
        }
        return j("{\"ok\":\(!neomu)}")
    }

    private func yocheongCheori(_ q: String) -> HjDap {
        let id = HyeonjangGil.gap(q, "id")
        jam.lock()
        for x in 0..<s.ycMok.count where String(s.ycMok[x].seq) == id { s.ycMok[x].cheori = true }
        jam.unlock()
        girok("yocheong", hjSi.string(from: Date()) + ",,도와드림 확인 " + id)
        return j("{\"ok\":true}")
    }

    private func yocheongMok() -> HjDap {
        let now = Date()
        jam.lock()
        var deut = 0, kkeun = 0
        for (_, v) in s.deutMok where now.timeIntervalSince(v.ttae) < 45 { deut += 1; if v.kkeun == 1 { kkeun += 1 } }
        var hp: [String] = []
        var jom: [String: Int] = [:]
        for y in s.ycMok {
            if y.k == "dowum" {
                if !y.cheori {
                    hp.append("{\"id\":\(y.seq),\"t\":\"\(y.t)\",\"n\":\"\(hjJsn(y.n))\",\"g\":\"\(hjJsn(y.n.isEmpty ? "" : (s.bhGuyeok[y.n] ?? "")))\",\"bun\":\(Int(now.timeIntervalSince(y.ttae) / 60))}")
                }
            } else if now.timeIntervalSince(y.ttae) < 120 { jom[y.k, default: 0] += 1 }
        }
        let maji = s.ycSeq
        jam.unlock()
        let js = jom.map { "\"\($0.key)\":\($0.value)" }.joined(separator: ",")
        return j("{\"deut\":\(deut),\"kkeun\":\(kkeun),\"seq\":\(maji),\"dowum\":[\(hp.joined(separator: ","))],\"jom\":{\(js)}}")
    }

    // MARK: 현장영상해설사끼리 글

    private func malCheori(_ g: String, _ q: String) -> HjDap {
        if g == "/hs_mal" {
            let nm = hjJareugi(hjPul(HyeonjangGil.gap(q, "nm")), 20)
            let t = hjPul(HyeonjangGil.gap(q, "t"))
            if t.isEmpty || t.count > 300 { return j("{\"ok\":false}", 400) }
            jam.lock()
            s.malSeq += 1
            s.malMok.append(Mal(seq: s.malSeq, t: hjSi.string(from: Date()), nm: nm, mal: t))
            if s.malMok.count > 50 { s.malMok.removeFirst() }
            jam.unlock()
            return j("{\"ok\":true}")
        }
        jam.lock()
        let l = s.malMok.map { "{\"id\":\($0.seq),\"t\":\"\($0.t)\",\"nm\":\"\(hjJsn($0.nm))\",\"mal\":\"\(hjJsn($0.mal))\"}" }.joined(separator: ",")
        let maji = s.malSeq
        jam.unlock()
        return j("{\"seq\":\(maji),\"mok\":[\(l)]}")
    }

    // MARK: 녹음 자리 표시·녹음 상태·올리기 — 녹음은 이 아이폰의 행사 녹음(GigiNokeum)을 그대로 씀

    private func pyosi(_ q: String) -> HjDap {
        let nm = hjJareugi(hjPul(HyeonjangGil.gap(q, "nm")), 20)
        let t = hjJareugi(hjPul(HyeonjangGil.gap(q, "t")), 100)
        let ss = hjJareugi(hjPul(HyeonjangGil.gap(q, "ss")), 60)
        let sid = GigiNokeum.shared.sid
        let pail = sid.isEmpty ? "(녹음 없음)" : sid + ".ul"
        let ofs = sid.isEmpty ? 0 : GigiNokeum.shared.sseunBaiteu / 16000
        let now = Date()
        jam.lock()
        s.pyosiMok.append(Pyosi(ttae: now, pail: pail, cho: ofs, ss: ss, nm: nm, t: t))
        if s.pyosiMok.count > 500 { s.pyosiMok.removeFirst() }
        jam.unlock()
        girok("pyosi", hjModu.string(from: now) + "," + pail + ",\(ofs)," + hjCsv(ss) + "," + hjCsv(nm) + "," + hjCsv(t))
        return j("{\"ok\":true,\"pail\":\"\(hjJsn(pail))\",\"bun\":\(ofs / 60),\"cho\":\(ofs % 60)}")
    }

    private func nokeumSangtae() -> HjDap {
        let l = HaengsaOlligi.mokrok()
        let gn = GigiNokeum.shared
        let jigeum = !gn.sid.isEmpty
        let olrinun = HaengsaOlligi.shared.olligineunJung
        jam.lock()
        if !olrinun && nokOlrimMal == "올리는 중입니다" { nokOlrimMal = "올리기를 마쳤습니다. 못 올린 것은 인터넷이 되면 저절로 다시 올립니다" }
        let m = nokOlrimMal
        jam.unlock()
        return j("{\"su\":\(l.count + (jigeum ? 1 : 0)),\"olrim\":\(l.filter { $0.olim }.count),\"jigeum\":\(jigeum),\"bun\":\(gn.sseunBaiteu / 16000 / 60),\"olrinun\":\(olrinun),\"mal\":\"\(hjJsn(m))\"}")
    }

    private func nokeumOlligi() -> HjDap {
        jam.lock(); nokOlrimMal = "올리는 중입니다"; jam.unlock()
        HaengsaOlligi.shared.sijak(maegam: false)   // 지난 행사 녹음을 다시 듣기에(지금 녹음은 방송을 멈추면 저절로 올라감)
        return j("{\"ok\":true}")
    }

    // MARK: 무대 카메라와 줌 (아이폰 서버는 자물쇠 문이 없어 다른 폰 카메라가 열리지 않지만, 길은 노트북판과 같게 둠)

    private func kamera(_ g: String, _ q: String, _ rq: HjYocheong, _ heoga: Bool) -> HjDap {
        let now = Date()
        var c = HyeonjangGil.gap(q, "c")
        if c != "1" && c != "2" && c != "3" { c = "1" }
        if g == "/kamera" {
            if !heoga { return j("{\"ok\":false}", 403) }
            if rq.method != "POST" || rq.mom.count < 100 || rq.mom.count > 3_000_000 { return j("{\"ok\":false}", 400) }
            let nm = hjJareugi(hjPul(HyeonjangGil.gap(q, "nm")), 20)
            let zmin = Double(HyeonjangGil.gap(q, "zmin")) ?? 0, zmax = Double(HyeonjangGil.gap(q, "zmax")) ?? 0, z = Double(HyeonjangGil.gap(q, "z")) ?? 0
            jam.lock()
            let ye = s.kamMok[c]
            let zreq = ye?.zreq ?? -1, zseq = ye?.zseq ?? 0
            s.kamMok[c] = Kam(jpeg: rq.mom, ttae: now, nm: nm, zmin: zmin, zmax: zmax, z: z, zreq: zreq, zseq: zseq)
            jam.unlock()
            return j("{\"ok\":true,\"zreq\":\(hjDs(zreq)),\"zseq\":\(zseq)}")
        }
        if g == "/kamera_zoom" {
            if !heoga { return j("{\"ok\":false}", 403) }
            let d = HyeonjangGil.gap(q, "d")
            var nz = -1.0, mx = 0.0, mn = 0.0
            var ok = false
            jam.lock()
            if let v = s.kamMok[c], v.zmax > v.zmin {
                mn = v.zmin; mx = v.zmax
                let cur = v.zreq > 0 ? v.zreq : (v.z > 0 ? v.z : mn)
                if d == "in" { nz = cur * 1.5 } else if d == "out" { nz = cur / 1.5 } else { nz = mn }
                if nz > mx { nz = mx }
                if nz < mn { nz = mn }
                s.kamMok[c]?.zreq = nz
                s.kamMok[c]?.zseq = v.zseq + 1
                ok = true
            }
            jam.unlock()
            return j("{\"ok\":\(ok),\"z\":\(hjDs(nz)),\"max\":\(hjDs(mx)),\"min\":\(hjDs(mn))}")
        }
        if g == "/kamera_bogi" {
            jam.lock()
            let img: Data? = s.kamMok[c].flatMap { now.timeIntervalSince($0.ttae) < 10 ? $0.jpeg : nil }
            jam.unlock()
            guard let im = img else { return j("{\"ok\":false}", 404) }
            return HjDap(code: 200, type: "image/jpeg", mom: im)
        }
        jam.lock()
        var l: [String] = []
        for k in s.kamMok.keys.sorted() {
            guard let v = s.kamMok[k], now.timeIntervalSince(v.ttae) <= 10 else { continue }
            l.append("{\"c\":\"\(k)\",\"nm\":\"\(hjJsn(v.nm))\",\"zmin\":\(hjDs(v.zmin)),\"zmax\":\(hjDs(v.zmax)),\"z\":\(hjDs(v.z))}")
        }
        jam.unlock()
        return j("{\"mok\":[\(l.joined(separator: ","))]}")
    }

    // MARK: 관계자 붙이기

    static func gwanIreum(_ r: String) -> String {
        switch r {
        case "haeseol": return "해설사"
        case "jinhaeng": return "진행요원"
        case "kamera": return "무대 카메라"
        case "pd": return "피디 보조"
        default: return r
        }
    }

    private func gwanKey() -> String {
        jam.lock(); defer { jam.unlock() }
        if s.gwanKey.isEmpty { s.gwanKey = hjNanseu(8) }
        return s.gwanKey
    }

    /// 관계자 붙이기 주소 — 이 아이폰 안에서 물으면 듣는 폰이 들어오는 쪽 주소로(첫 화면 큐알도 이것을 씀)
    func gwanJuso(host: String) -> String {
        var h = host
        if h.isEmpty || h.hasPrefix("localhost") || h.hasPrefix("127.") {
            h = Juso.deutgiJuso().replacingOccurrences(of: "http://", with: "").replacingOccurrences(of: "/", with: "")
            if h.isEmpty { h = "192.168.0.2:\(Bang.PORT)" }
        }
        return "http://" + h + "/gwan?gk=" + gwanKey()
    }

    private func gwanCheori(_ g: String, _ q: String, _ rq: HjYocheong, _ heoga: Bool) -> HjDap {
        if g == "/gwan_qr" {
            if !heoga { return j("{\"ok\":false}", 403) }
            if HyeonjangGil.gap(q, "sae") == "1" { jam.lock(); s.gwanKey = hjNanseu(8); s.gwanMok.removeAll(); jam.unlock() }
            return j("{\"ok\":true,\"juso\":\"\(hjJsn(gwanJuso(host: rq.host)))\"}")
        }
        if g == "/gwan_deung" {
            let gk = HyeonjangGil.gap(q, "gk"), r = HyeonjangGil.gap(q, "r")
            let nm = hjJareugi(hjPul(HyeonjangGil.gap(q, "nm")), 20).trimmingCharacters(in: .whitespacesAndNewlines)
            if gk.isEmpty || gk != gwanKey() { return j("{\"ok\":false,\"mal\":\"큐알이 바뀌었습니다. 피디 화면의 큐알을 다시 찍어 주십시오.\"}") }
            if !["haeseol", "jinhaeng", "kamera", "pd"].contains(r) { return j("{\"ok\":false,\"mal\":\"역할을 골라 주십시오.\"}") }
            if nm.isEmpty { return j("{\"ok\":false,\"mal\":\"이름을 적어 주십시오.\"}") }
            let tok = hjNanseu(16)
            let now = Date()
            jam.lock(); s.gwanMok[tok] = Gwan(r: r, nm: nm, cheo: now, maji: now); jam.unlock()
            girok("gwangye", hjModu.string(from: now) + "," + HyeonjangGil.gwanIreum(r) + "," + hjCsv(nm))
            return j("{\"ok\":true,\"tok\":\"\(tok)\",\"r\":\"\(r)\",\"yeokhal\":\"\(HyeonjangGil.gwanIreum(r))\"}")
        }
        if !heoga { return j("{\"ok\":false}", 403) }
        let now = Date()
        jam.lock()
        let l = s.gwanMok.values.sorted { $0.cheo < $1.cheo }.map {
            "{\"yeokhal\":\"\(HyeonjangGil.gwanIreum($0.r))\",\"nm\":\"\(hjJsn($0.nm))\",\"cheo\":\"\(hjBun.string(from: $0.cheo))\",\"jigeum\":\(now.timeIntervalSince($0.maji) < 90)}"
        }.joined(separator: ",")
        jam.unlock()
        return j("{\"ok\":true,\"mok\":[\(l)]}")
    }

    // MARK: 관계자 무전 (아이폰 서버는 자물쇠 문이 없어 다른 폰 마이크가 열리지 않지만, 길은 노트북판과 같게 둠)

    private func mujeon(_ g: String, _ q: String, _ rq: HjYocheong, _ heoga: Bool) -> HjDap {
        if !heoga { return j(HyeonjangGil.ANGWAN, 403) }
        var nae = HyeonjangGil.gap(q, "gt")
        if nae.isEmpty { nae = HyeonjangGil.gap(q, "t") }
        if nae.isEmpty { nae = "pd" }
        let now = Date()
        if g == "/mj_bonae" || g == "/mj_hochul" {
            var to = HyeonjangGil.gap(q, "to")
            if to.isEmpty { to = "ALL" }
            var mime = hjPul(HyeonjangGil.gap(q, "mime"))
            if mime.count > 40 || !mime.hasPrefix("audio") { mime = "audio/webm" }
            var b: Data? = nil
            if g == "/mj_bonae" {
                if rq.method != "POST" || rq.mom.count < 200 || rq.mom.count > 1_500_000 { return j("{\"ok\":false}", 400) }
                b = rq.mom
            }
            jam.lock()
            let nm: String = s.gwanMok[nae].map { HyeonjangGil.gwanIreum($0.r) + " " + $0.nm } ?? (rq.loop ? "피디(이 아이폰)" : "피디")
            s.mjSeq += 1
            s.mjMok.append(Mj(id: s.mjSeq, ttae: now, nae: nae, nm: nm, to: to, jong: g == "/mj_hochul" ? "hochul" : "mal", mime: mime, mom: b))
            if s.mjMok.count > 40 { s.mjMok.removeFirst() }
            let id = s.mjSeq
            jam.unlock()
            return j("{\"ok\":true,\"id\":\(id)}")
        }
        if g == "/mj_mok" {
            let after = Int(HyeonjangGil.gap(q, "after")) ?? 0
            jam.lock()
            var l: [String] = []
            for m in s.mjMok where m.id > after && (m.to == "ALL" || m.to == nae || m.nae == nae) {
                l.append("{\"id\":\(m.id),\"t\":\"\(hjSi.string(from: m.ttae))\",\"nm\":\"\(hjJsn(m.nm))\",\"nae\":\(m.nae == nae),\"modu\":\(m.to == "ALL"),\"jong\":\"\(m.jong)\"}")
            }
            let maji = s.mjSeq
            jam.unlock()
            return j("{\"seq\":\(maji),\"mok\":[\(l.joined(separator: ","))]}")
        }
        if g == "/mj_deul" {
            let id = Int(HyeonjangGil.gap(q, "id")) ?? 0
            jam.lock()
            var b: Data? = nil
            var mime = "audio/webm"
            for m in s.mjMok where m.id == id && (m.to == "ALL" || m.to == nae || m.nae == nae) { b = m.mom; mime = m.mime }
            jam.unlock()
            guard let bb = b else { return j("{\"ok\":false}", 404) }
            return HjDap(code: 200, type: mime, mom: bb)
        }
        jam.lock()
        var l: [String] = []
        for (k, v) in s.gwanMok where k != nae {
            l.append("{\"tok\":\"\(k)\",\"nm\":\"\(hjJsn(HyeonjangGil.gwanIreum(v.r) + " " + v.nm))\",\"jigeum\":\(now.timeIntervalSince(v.maji) < 90)}")
        }
        jam.unlock()
        if nae != "pd" { l.append("{\"tok\":\"pd\",\"nm\":\"피디(이 아이폰)\",\"jigeum\":true}") }
        return j("{\"mok\":[\(l.joined(separator: ","))]}")
    }

    // MARK: 긴급 안내

    private func gingeup(_ q: String, _ heoga: Bool) -> HjDap {
        if !heoga { return j("{\"ok\":false}", 403) }
        let gt = hjJareugi(hjPul(HyeonjangGil.gap(q, "t")).trimmingCharacters(in: .whitespacesAndNewlines), 200)
        let now = Date()
        jam.lock()
        s.ginMal = gt
        s.ginSeq += 1
        s.ginGirok.append(Girok(ttae: now, n: "", mal: gt.isEmpty ? "긴급 안내 끝" : gt))
        let seq = s.ginSeq
        let jgJung = jgNow
        jam.unlock()
        girok("gingeup", hjModu.string(from: now) + "," + (gt.isEmpty ? "긴급 안내 끝" : hjCsv(gt)))
        if !jgJung { Bang.shared.gingeupBatgi(gt) }   // 첫 화면 한 줄로 한 번 알림
        return j("{\"ok\":true,\"gseq\":\(seq)}")
    }

    // MARK: 스스로 점검 — 담았다가 되돌림, 점검 중(길어도 5분)에는 파일에 쓰지 않음

    private func jeomgeom(_ g: String, _ q: String, loop: Bool) -> HjDap {
        if !loop && HyeonjangGil.gap(q, "k") != pdKey { return j("{\"ok\":false,\"mal\":\"피디 화면에서만 쓸 수 있습니다\"}", 403) }
        let bk = Bang.shared.keu
        jam.lock(); defer { jam.unlock() }
        if g == "/jeomgeom_sijak" {
            if bk && s.cue { return j("{\"ok\":false,\"mal\":\"해설이 나가는 중에는 점검할 수 없습니다. 해설을 멈춘 뒤 눌러 주십시오\"}") }
            if jgNow { return j("{\"ok\":false,\"mal\":\"점검이 이미 진행 중입니다\"}") }
            jg = s
            jgTtae = Date()
            return j("{\"ok\":true,\"pan\":\"LVD 송출 아이폰 \(Bang.PAN)\",\"bild\":\"\(Bang.BILD)\"}")
        }
        if let a = jg { doerigi(a); jg = nil }
        return j("{\"ok\":true}")
    }

    /// jam 안에서 — 점검 앞 상황으로. 번호 차례(seq)는 그대로 이어 가서 화면이 새 글을 놓치지 않게
    private func doerigi(_ a: Sang) {
        var n = a
        n.nal = s.nal
        n.deutMok = s.deutMok.filter { !$0.key.hasPrefix("jg") }
        n.jiyeon = s.jiyeon.filter { !$0.key.hasPrefix("jg") }
        n.cue = s.cue
        n.bangsik = s.bangsik
        n.nasMal = s.nasMal
        n.micTtae = s.micTtae
        n.gidari = s.gidari
        n.gidariTtae = s.gidariTtae
        n.galBaiteu = s.galBaiteu
        n.ycSeq = s.ycSeq
        n.malSeq = s.malSeq
        n.mjSeq = s.mjSeq
        n.dangSeq = s.dangSeq
        n.manjokSeq = s.manjokSeq
        n.ginSeq = s.ginSeq + (s.ginMal != a.ginMal ? 1 : 0)
        s = n
    }

    // MARK: 1.9.0 새 기능 가 — 여러 갈래 해설

    /// 관계자 폰 bang.html 이 보내는 해설 소리. 갈래 1은 이 아이폰 사운드카드가 쓰는 동안 받지 않음
    private func pcm(_ rq: HjYocheong, _ heoga: Bool) -> HjDap {
        if !heoga { return j("{\"ok\":false}", 403) }
        let g0 = Int(HyeonjangGil.gap(rq.mul, "gal")) ?? 1
        let gal = (1...3).contains(g0) ? g0 : 1
        let bk = Bang.shared.keu
        jam.lock(); s.micTtae = Date(); let keu = bk && s.cue; jam.unlock()
        var bappeum = false
        if keu && !rq.mom.isEmpty {
            if Seobeo.shared.bakkatSori(rq.mom, gal: gal) {
                if gal == 1 { GigiNokeum.shared.sseugi(rq.mom) } else { galSseugi(rq.mom, gal: gal) }   // 녹음 — 갈래 1은 행사 녹음, 2·3은 같은 행사 폴더에 따로
                jam.lock(); s.galBaiteu[gal, default: 0] += rq.mom.count; jam.unlock()
            } else { bappeum = true }
        }
        return j("{\"ok\":true,\"keu\":\(keu)" + (bappeum ? ",\"gal1\":\"bappeum\",\"mal\":\"갈래 1은 이 아이폰의 사운드카드 현장영상해설이 쓰고 있습니다. 갈래 2나 3으로 보내 주십시오.\"" : "") + "}")
    }

    /// 갈래 2·3 녹음 — gal2_년월일_시분초.ul(뮤 8비트 16000), 5분 넘게 조용하면 새 파일, 10초마다 확실히 씀
    private func galSseugi(_ b: Data, gal: Int) {
        fq.async {
            let now = Date()
            if let f = self.galF[gal], now.timeIntervalSince(self.galFTtae[gal] ?? .distantPast) > 300 {
                f.synchronizeFile(); f.closeFile()
                self.galF[gal] = nil
            }
            if self.galF[gal] == nil {
                let u = self.teo().appendingPathComponent("gal\(gal)_" + hjPailSi.string(from: now) + ".ul")
                if !FileManager.default.fileExists(atPath: u.path) { FileManager.default.createFile(atPath: u.path, contents: nil) }
                if let f = try? FileHandle(forWritingTo: u) { _ = f.seekToEndOfFile(); self.galF[gal] = f }
                self.galSync[gal] = now
            }
            self.galF[gal]?.write(b)
            self.galFTtae[gal] = now
            if now.timeIntervalSince(self.galSync[gal] ?? .distantPast) >= 10 {
                self.galSync[gal] = now
                self.galF[gal]?.synchronizeFile()
            }
        }
    }

    private func gallaeIreum(_ q: String, _ heoga: Bool) -> HjDap {
        if !heoga { return j(HyeonjangGil.ANGWAN, 403) }
        guard let gal = Int(HyeonjangGil.gap(q, "gal")), (1...3).contains(gal) else { return j("{\"ok\":false}", 400) }
        var nm = hjJareugi(hjPul(HyeonjangGil.gap(q, "nm")).trimmingCharacters(in: .whitespacesAndNewlines), 20)
        if nm.isEmpty { nm = HyeonjangGil.GAL_GIBON[gal] ?? "" }
        jam.lock(); s.galIreum[gal] = nm; jam.unlock()
        return j("{\"ok\":true,\"gal\":\(gal),\"nm\":\"\(hjJsn(nm))\"}")
    }

    // MARK: 1.9.0 새 기능 라 — 만족도 묻기

    private func manjok(_ g: String, _ q: String, _ heoga: Bool) -> HjDap {
        if g == "/manjok" {   // 누구나 — 듣는 분 폰
            let i = HyeonjangGil.gap(q, "i")
            var n = HyeonjangGil.gap(q, "n")
            if !hjSusja(n) { n = "" } else { n = hjApgi(n) }
            guard i.count >= 1 && i.count <= 64, let p = Int(HyeonjangGil.gap(q, "p")), p >= 1 && p <= 5 else { return j("{\"ok\":false}", 400) }
            let t = hjJareugi(hjPul(HyeonjangGil.gap(q, "t")).trimmingCharacters(in: .whitespacesAndNewlines), 200)
            let now = Date()
            jam.lock()
            guard !s.manjokHoe.isEmpty else { jam.unlock(); return j("{\"ok\":false}") }
            let x = s.manjokHoe.count - 1
            s.manjokHoe[x].dap[i] = MjDap(n: n, p: p, t: t, ttae: now)   // 같은 폰·같은 물음은 덮어씀
            let seq = s.manjokHoe[x].seq
            jam.unlock()
            girok("manjok", hjModu.string(from: now) + ",\(seq)," + n + ",\(p)," + hjCsv(t))
            return j("{\"ok\":true}")
        }
        if !heoga { return j(HyeonjangGil.ANGWAN, 403) }
        if g == "/manjok_sijak" {
            jam.lock()
            if HyeonjangGil.gap(q, "kkeut") == "1" {
                s.manjokQ = ""
                let seq = s.manjokSeq
                jam.unlock()
                return j("{\"ok\":true,\"seq\":\(seq),\"q\":\"\"}")
            }
            var mul = hjJareugi(hjPul(HyeonjangGil.gap(q, "q")).trimmingCharacters(in: .whitespacesAndNewlines), 200)
            if mul.isEmpty { mul = HyeonjangGil.MANJOK_GIBON }
            s.manjokSeq += 1
            s.manjokQ = mul
            s.manjokHoe.append(MjHoe(seq: s.manjokSeq, q: mul, sijak: Date(), dap: [:]))
            let seq = s.manjokSeq
            jam.unlock()
            return j("{\"ok\":true,\"seq\":\(seq),\"q\":\"\(hjJsn(mul))\"}")
        }
        jam.lock()
        let h = s.manjokHoe.last
        jam.unlock()
        guard let hoe = h else { return j("{\"seq\":0,\"q\":\"\",\"su\":0,\"pyeong\":0,\"gak\":[0,0,0,0,0],\"mal\":[]}") }
        let (su, pyeong, gak) = HyeonjangGil.manjokSem(hoe)
        let mal = hoe.dap.values.sorted { $0.ttae < $1.ttae }.filter { !$0.t.isEmpty }
            .map { "{\"n\":\"\(hjJsn($0.n))\",\"p\":\($0.p),\"t\":\"\(hjJsn($0.t))\"}" }.joined(separator: ",")
        return j("{\"seq\":\(hoe.seq),\"q\":\"\(hjJsn(hoe.q))\",\"su\":\(su),\"pyeong\":" + String(format: "%.1f", pyeong) +
                 ",\"gak\":[\(gak.map { String($0) }.joined(separator: ","))],\"mal\":[\(mal)]}")
    }

    /// 답한 수, 평균, 점수별 수(1점부터 5점)
    static func manjokSem(_ h: MjHoe) -> (Int, Double, [Int]) {
        var gak = [0, 0, 0, 0, 0]
        var hap = 0
        for d in h.dap.values where d.p >= 1 && d.p <= 5 { gak[d.p - 1] += 1; hap += d.p }
        let su = gak.reduce(0, +)
        return (su, su == 0 ? 0 : Double(hap) / Double(su), gak)
    }

    // MARK: 결과보고 — 이 아이폰 행사 녹음의 결과보고(GigiNokeum)에 노트북판 결과보고 항목을 더함

    /// 노트북판 결과보고 항목(입장 번호 청취, 요청, 추첨, 식순·순간 표시, 긴급 안내, 여러 갈래, 만족도, 관계자) — 낭독기로 읽히게 기호 없이
    private func bogoJul() -> [String] {
        let jaryo = Jaryo.pail("jaryo.json")
        jam.lock()
        let x = s
        jam.unlock()
        var L: [String] = []
        L.append("")
        L.append("현장 기능 기록 (피디 화면)")
        L.append("")
        L.append("1. 입장 번호와 들으신 시간")
        let bh = Array(x.bhMok.values)
        if bh.isEmpty { L.append("입장 번호로 들으신 분: 없음") } else {
            var hap = 0.0
            var a30 = 0, a60 = 0, a60p = 0
            for v in bh {
                let m = v.maji.timeIntervalSince(v.cheo) / 60
                hap += m
                if m < 30 { a30 += 1 } else if m < 60 { a60 += 1 } else { a60p += 1 }
            }
            L.append("입장 번호로 들으신 분: \(bh.count)명")
            L.append("한 분이 들으신 시간: 평균 \(Int((hap / Double(bh.count)).rounded()))분 (30분 안 \(a30)명, 30분에서 1시간 \(a60)명, 1시간 넘게 \(a60p)명)")
        }
        L.append("가장 많이 함께 들은 때: \(x.deutChoeda)명" + (x.deutChoedaT.map { " (" + hjBun.string(from: $0) + ")" } ?? ""))
        var gy: [String: Int] = [:]
        for k in x.bhMok.keys { if let g = x.bhGuyeok[k], !g.isEmpty { gy[g, default: 0] += 1 } }
        if !gy.isEmpty {
            L.append("구역별 입장 번호:")
            for g in gy.keys.sorted() { L.append("  구역 \(g) \(gy[g] ?? 0)명") }
        }
        L.append("")
        L.append("2. 듣는 분 요청")
        var dw = 0, dwc = 0
        var dwMok: [String] = []
        var jong: [String: Int] = [:]
        for y in x.ycMok {
            if y.k == "dowum" {
                dw += 1
                if y.cheori { dwc += 1 }
                dwMok.append(y.t + " " + (y.n.isEmpty ? "번호 없음" : y.n + "번") + (y.cheori ? " (도와드림)" : " (확인 안 됨)"))
            } else { jong[y.k, default: 0] += 1 }
        }
        L.append("도움 요청: \(dw)건 (도와드림 \(dwc)건)")
        for d in dwMok { L.append("  " + d) }
        if jong.isEmpty { L.append("현장영상해설사에게 한 요청: 없음") } else {
            L.append("현장영상해설사에게 한 요청:")
            for k in jong.keys.sorted() { L.append("  " + HyeonjangGil.ycIreum(k) + " \(jong[k] ?? 0)건") }
        }
        L.append("")
        L.append("3. 행운권 추첨")
        if x.dangGirok.isEmpty { L.append("추첨 없음") } else { for d in x.dangGirok { L.append("  " + hjSi.string(from: d.ttae) + " " + d.n + "번 " + d.mal) } }
        L.append("")
        L.append("4. 식순과 중요한 순간 표시")
        var sunseo: [String] = []
        if let d = jaryo, let o = (try? JSONSerialization.jsonObject(with: d)) as? [String: Any], let l = o["sunseo"] as? [[String: Any]] {
            for v in l { if let n = v["n"] as? String { sunseo.append(n) } }
        }
        for (i, n) in sunseo.enumerated() { L.append("  식순 \(i + 1). " + n) }
        for p in x.pyosiMok {
            L.append("  표시 " + hjSi.string(from: p.ttae) + " (녹음 \(p.cho / 60)분 \(p.cho % 60)초) " + p.ss + " " + p.t + (p.nm.isEmpty ? "" : ", " + p.nm))
        }
        if sunseo.isEmpty && x.pyosiMok.isEmpty { L.append("식순과 표시 없음") }
        L.append("")
        L.append("5. 긴급 안내")
        if x.ginGirok.isEmpty { L.append("긴급 안내 없음") } else { for g in x.ginGirok { L.append("  " + hjSi.string(from: g.ttae) + " " + g.mal) } }
        L.append("")
        L.append("6. 여러 갈래 해설")
        L.append("  갈래 1 " + (x.galIreum[1] ?? "") + ": 이 아이폰의 사운드카드 현장영상해설, 행사 녹음에 담김")
        for i in 2...3 {
            let b = x.galBaiteu[i] ?? 0
            if b > 0 { L.append("  갈래 \(i) " + (x.galIreum[i] ?? "") + ": 녹음 \(b / 16000 / 60)분 \(b / 16000 % 60)초, 행사 폴더의 gal\(i) 파일") }
        }
        L.append("")
        L.append("7. 만족도")
        if x.manjokHoe.isEmpty { L.append("묻지 않음") }
        for h in x.manjokHoe {
            let (su, pyeong, gak) = HyeonjangGil.manjokSem(h)
            L.append("물음 \(h.seq): " + h.q)
            L.append("  답하신 분: \(su)명, 평균 " + String(format: "%.1f", pyeong) + "점 (5점이 가장 좋음)")
            L.append("  점수별: 매우 좋음 \(gak[4])명, 좋음 \(gak[3])명, 보통 \(gak[2])명, 아쉬움 \(gak[1])명, 매우 아쉬움 \(gak[0])명")
            let mal = h.dap.values.sorted { $0.ttae < $1.ttae }.filter { !$0.t.isEmpty }
            if !mal.isEmpty {
                L.append("  한마디:")
                for d in mal {
                    let pm = d.p >= 1 && d.p <= 5 ? HyeonjangGil.MANJOK_MAL[d.p] : "\(d.p)점"
                    L.append("    " + (d.n.isEmpty ? "번호 없음" : d.n + "번") + ", " + pm + ", " + d.t)
                }
            }
        }
        L.append("")
        L.append("8. 관계자")
        if x.gwanMok.isEmpty { L.append("붙은 관계자 없음") } else {
            var su: [String: Int] = [:]
            for v in x.gwanMok.values { su[HyeonjangGil.gwanIreum(v.r), default: 0] += 1 }
            L.append("붙은 관계자: " + su.keys.sorted().map { $0 + " \(su[$0] ?? 0)명" }.joined(separator: ", "))
        }
        L.append("")
        L.append("참고: 이 보고서는 듣는 분의 입장 번호만 모았으며 이름과 전화번호 같은 개인정보는 모으지 않았습니다.")
        L.append("LVD 송출 아이폰 \(Bang.PAN)판 현장 기능(노트북판 현장 해설방송 3.3판과 같은 길)")
        return L
    }

    /// 지금까지의 결과보고 전체 줄
    private func moduBogo() -> [String] {
        var l: [String] = []
        let ap = GigiNokeum.shared.jigeumBogo()
        if ap.isEmpty {
            let jm = (UserDefaults.standard.string(forKey: "haengsa_jemok") ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            l = ["현장영상해설 방송 결과보고", "사단법인 한국시각장애인현장영상해설협회", "", "행사 이름: " + (jm.isEmpty ? "적지 않음" : jm),
                 "보고서 만든 때: " + hjBogoTtae.string(from: Date())]
        } else {
            l = ap.components(separatedBy: "\n")
            while l.last == "" { l.removeLast() }
        }
        return l + bogoJul()
    }

    private func bogoseo(_ g: String, _ heoga: Bool) -> HjDap {
        if !heoga { return HjDap.geul("피디 화면에서만 열 수 있습니다.", 403) }
        let L = moduBogo()
        if g == "/bogoseo.txt" {
            return HjDap(code: 200, type: "text/plain; charset=euc-kr", mom: hjEuckr(L.joined(separator: "\r\n") + "\r\n"))
        }
        var h = "<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>현장영상해설 방송 결과보고</title>" +
            "<style>body{font-family:sans-serif;max-width:180mm;margin:10mm auto;padding:0 4mm;font-size:11pt;line-height:1.7}h1{font-size:18pt}p{margin:0}@page{size:A4;margin:15mm}@media print{.an{display:none}}</style></head><body>" +
            "<p class=\"an\"><button onclick=\"print()\" style=\"font-size:18px;padding:12px\">인쇄하기</button> <a href=\"/bogoseo.txt\" download=\"결과보고.txt\" style=\"font-size:18px\">텍스트로 저장</a></p>"
        var cheot = true
        for l in L {
            let e = l.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;").replacingOccurrences(of: ">", with: "&gt;")
            if cheot { h += "<h1>" + e + "</h1>"; cheot = false; continue }
            if e.isEmpty { continue }
            let c = Array(e.prefix(2))
            if e.count > 2 && c[0].isNumber && c[1] == "." { h += "<h2 style=\"font-size:13pt;margin:10pt 0 4pt\">" + e + "</h2>" }
            else { h += "<p>" + e.replacingOccurrences(of: "  ", with: "&nbsp;&nbsp;") + "</p>" }
        }
        h += "</body></html>"
        return HjDap.geul(h, 200, type: "text/html; charset=utf-8")
    }

    // MARK: 기록 파일과 1분마다 저장

    /// 기록 한 줄 덧붙이기(행사 폴더, UTF-8, 처음 만들 때 BOM) — 스스로 점검 중이면 쓰지 않음
    private func girok(_ ireum: String, _ jul: String) {
        jam.lock(); let an = jgNow; jam.unlock()
        if an { return }
        fq.async {
            let u = self.teo().appendingPathComponent(ireum + "_" + hjNal.string(from: Date()) + ".csv")
            let fm = FileManager.default
            if !fm.fileExists(atPath: u.path) { fm.createFile(atPath: u.path, contents: Data([0xEF, 0xBB, 0xBF])) }
            guard let f = try? FileHandle(forWritingTo: u) else { return }
            _ = f.seekToEndOfFile()
            f.write(Data((jul + "\r\n").utf8))
            f.closeFile()
        }
    }

    /// 새 파일에 다 쓴 뒤 바꿔 끼움(앞 것은 .bak 으로) — 쓰다 전기가 끊겨도 앞 것이 남게
    private func anjeon(_ u: URL, _ b: Data) {
        let fm = FileManager.default
        let tmp = u.appendingPathExtension("tmp")
        try? fm.removeItem(at: tmp)
        guard (try? b.write(to: tmp)) != nil else { return }
        if fm.fileExists(atPath: u.path) {
            let bak = u.appendingPathExtension("bak")
            try? fm.removeItem(at: bak)
            try? fm.moveItem(at: u, to: bak)
        }
        try? fm.moveItem(at: tmp, to: u)
    }

    /// fq 안에서 — 상황(sangtae_날짜.txt), 더할 결과보고(hyeonjang_bogo.txt, 행사 마감 때 bogo.txt 에 붙음), 결과보고 전체(결과보고_날짜.txt, EUC-KR)
    private func jeojang() {
        jam.lock(); let an = jgNow; let ieo = jadongIeo; jam.unlock()
        if an { return }
        let d = teo()
        let nal = hjNal.string(from: Date())
        anjeon(d.appendingPathComponent("sangtae_\(nal).txt"), Data(sangtaeGeul().utf8))
        let deo = bogoJul()
        anjeon(d.appendingPathComponent("hyeonjang_bogo.txt"), Data((deo.joined(separator: "\n") + "\n").utf8))
        anjeon(d.appendingPathComponent("결과보고_\(nal).txt"), hjEuckr(moduBogo().joined(separator: "\r\n") + "\r\n"))
        var m = (ieo.isEmpty ? "" : ieo + ". ") + "마지막 저절로 저장 " + hjSi.string(from: Date())
        if m.count > 120 { m = "마지막 저절로 저장 " + hjSi.string(from: Date()) }
        jam.lock(); jadongMal = m; jam.unlock()
    }

    /// 상황을 한 줄씩(가름표 |) — 노트북판 sangtae 파일과 같은 생각, 때는 1970년부터의 초
    private func sangtaeGeul() -> String {
        jam.lock()
        let x = s
        jam.unlock()
        func c(_ d: Date?) -> String { d.map { String(format: "%.3f", $0.timeIntervalSince1970) } ?? "0" }
        var b = ""
        for (k, v) in x.bhMok { b += "B|\(k)|\(c(v.cheo))|\(c(v.maji))\n" }
        for (k, v) in x.bhGuyeok { b += "U|\(k)|\(hjKan(v))\n" }
        for y in x.ycMok { b += "Y|\(y.seq)|\(y.t)|\(y.n)|\(y.k)|\(y.cheori ? 1 : 0)|\(c(y.ttae))\n" }
        for d in x.dangMok { b += "D|\(d)\n" }
        for d in x.dangGirok { b += "W|\(c(d.ttae))|\(d.n)|\(hjKan(d.mal))\n" }
        for (k, v) in x.gwanMok { b += "G|\(k)|\(v.r)|\(hjKan(v.nm))|\(c(v.cheo))|\(c(v.maji))\n" }
        b += "K|\(x.gwanKey)\n"
        b += "S|\(x.dangSeq)|\(x.dangcheom)|\(x.dangSik)\n"
        b += "C|\(x.deutChoeda)|\(c(x.deutChoedaT))\n"
        b += "Q|\(x.ycSeq)\n"
        b += "O|\(x.sunseoI)|\(c(x.sunseoT))\n"
        b += "I|\(x.ginSeq)|\(hjKan(x.ginMal))\n"
        for g in x.ginGirok { b += "J|\(c(g.ttae))|\(hjKan(g.mal))\n" }
        for p in x.pyosiMok { b += "X|\(c(p.ttae))|\(hjKan(p.pail))|\(p.cho)|\(hjKan(p.ss))|\(hjKan(p.nm))|\(hjKan(p.t))\n" }
        for i in 1...3 { b += "L|\(i)|\(hjKan(x.galIreum[i] ?? ""))|\(x.galBaiteu[i] ?? 0)\n" }
        b += "P|\(x.manjokSeq)|\(hjKan(x.manjokQ))\n"
        for h in x.manjokHoe {
            b += "H|\(h.seq)|\(c(h.sijak))|\(hjKan(h.q))\n"
            for (i, d) in h.dap { b += "A|\(h.seq)|\(hjKan(i))|\(d.n)|\(d.p)|\(c(d.ttae))|\(hjKan(d.t))\n" }
        }
        return b
    }

    /// 앱이 꺼졌다 켜졌을 때 — 오늘 저장한 상황 가운데 가장 새것을 이어 받음
    private func sangtaeBulleo() {
        let fm = FileManager.default
        let ireum = "sangtae_\(hjNal.string(from: Date())).txt"
        var hubo: [URL] = []
        for sid in (try? fm.contentsOfDirectory(atPath: HaengsaOlligi.teo.path)) ?? [] {
            hubo.append(HaengsaOlligi.teo.appendingPathComponent(sid).appendingPathComponent(ireum))
        }
        hubo.append(HyeonjangGil.gibonTeo.appendingPathComponent(ireum))
        var choe: URL? = nil
        var choeTtae = Date.distantPast
        for u0 in hubo {
            for u in [u0, u0.appendingPathExtension("bak")] {
                guard let a = try? fm.attributesOfItem(atPath: u.path), let t = a[.modificationDate] as? Date else { continue }
                if t > choeTtae { choe = u; choeTtae = t }
                break
            }
        }
        guard let u = choe, let tx = try? String(contentsOf: u, encoding: .utf8) else { return }
        var su = 0
        jam.lock()
        for l in tx.components(separatedBy: "\n") {
            let f = l.components(separatedBy: "|")
            guard f.count >= 2 else { continue }
            switch f[0] {
            case "B" where f.count >= 4:
                if let a = hjCho(f[2]), let b = hjCho(f[3]) { s.bhMok[f[1]] = Bh(cheo: a, maji: b); su += 1 }
            case "U" where f.count >= 3: s.bhGuyeok[f[1]] = f[2]
            case "Y" where f.count >= 7:
                if let q = Int(f[1]), let t = hjCho(f[6]) { s.ycMok.append(Yc(seq: q, t: f[2], n: f[3], k: f[4], cheori: f[5] == "1", ttae: t)) }
            case "D": s.dangMok.append(f[1])
            case "W" where f.count >= 4:
                if let t = hjCho(f[1]) { s.dangGirok.append(Girok(ttae: t, n: f[2], mal: f[3])) }
            case "G" where f.count >= 6:
                if let a = hjCho(f[4]), let b = hjCho(f[5]) { s.gwanMok[f[1]] = Gwan(r: f[2], nm: f[3], cheo: a, maji: b) }
            case "K": s.gwanKey = f[1]
            case "S" where f.count >= 4: s.dangSeq = Int(f[1]) ?? 0; s.dangcheom = f[2]; s.dangSik = f[3]
            case "C" where f.count >= 3: s.deutChoeda = Int(f[1]) ?? 0; s.deutChoedaT = hjCho(f[2])
            case "Q": s.ycSeq = Int(f[1]) ?? 0
            case "O" where f.count >= 3: s.sunseoI = Int(f[1]) ?? 0; s.sunseoT = hjCho(f[2])
            case "I" where f.count >= 3: s.ginSeq = Int(f[1]) ?? 0; s.ginMal = f[2]
            case "J" where f.count >= 3:
                if let t = hjCho(f[1]) { s.ginGirok.append(Girok(ttae: t, n: "", mal: f[2])) }
            case "X" where f.count >= 7:
                if let t = hjCho(f[1]) { s.pyosiMok.append(Pyosi(ttae: t, pail: f[2], cho: Int(f[3]) ?? 0, ss: f[4], nm: f[5], t: f[6])) }
            case "L" where f.count >= 4:
                if let i = Int(f[1]), (1...3).contains(i) { if !f[2].isEmpty { s.galIreum[i] = f[2] }; s.galBaiteu[i] = Int(f[3]) ?? 0 }
            case "P" where f.count >= 3: s.manjokSeq = Int(f[1]) ?? 0; s.manjokQ = f[2]
            case "H" where f.count >= 4:
                if let q = Int(f[1]) { s.manjokHoe.append(MjHoe(seq: q, q: f[3], sijak: hjCho(f[2]) ?? Date(), dap: [:])) }
            case "A" where f.count >= 7:
                if let q = Int(f[1]), let x = s.manjokHoe.lastIndex(where: { $0.seq == q }) {
                    s.manjokHoe[x].dap[f[2]] = MjDap(n: f[3], p: Int(f[4]) ?? 0, t: f[6], ttae: hjCho(f[5]) ?? Date())
                }
            default: break
            }
        }
        jadongIeo = "앞서 저장된 상황을 이어 받았습니다(입장 번호 \(su)개)"
        jadongMal = jadongIeo
        jam.unlock()
    }
}
