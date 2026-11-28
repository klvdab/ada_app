// 2.8.0(빌드 261028-LI11, 새방클, 이사장님 승인 2026-10-28 「1」): 행사장에서 듣는 동안 1초마다 Chucheom.salpigi(입장 번호 알리기·당첨 보기).
// LVD 방송 아이폰(듣는 앱) — 지금 현장영상해설 듣기 (2.2.0판, 빌드 261018-LI3, 방클, 이사장님 승인 「1」 — LVD 방송 정리안)
// 2.2.0: 단추 하나로 — 행사장(행사장 와이파이)이면 현장 방송을, 아니면 인터넷 방송을 저절로 골라 앱 안에서 틉니다.
//   현장 방송: 192.168.8.2:7880/stream (뮤 8비트 1초에 16000, 늦음이 가장 적음) — 행사장 와이파이로만
//   인터넷 방송: LVD 방송 중계 lvd.ada.or.kr:2053 의 /moklok 에서 지금 나가는 방송을 찾아 /mp3 로 틂
//     하나면 바로 틀고, 여럿이면 첫 화면에 고를 단추를 띄움. 없으면 알리고, 방송이 열리거나 행사장에 들어서면 저절로 틂
//   인터넷으로 듣다가 행사장 와이파이에 붙으면 현장 방송으로 바꿈(더 빠름)
// 2.5.0(빌드 261024-LI7): 듣는 인터넷 방송에 영상이 실려 있으면(중계 /moklok·/sangtae 의 hls·yeongsang) bogiBang 에 두어 첫 화면에 「영상으로 보기」.
//   영상을 보는 동안(bogiJung)은 소리(mp3)를 쉬어 겹치지 않게 하고, 나오면 같은 방송을 지금 자리부터 이어 들려 드림.
// 2.0.0 그대로: 화면을 잠가도 들림(뒤에서 소리, 잠금 화면 멈춤·재생), 전화가 오면 멈췄다가 끝나면 다시, 행사장을 벗어나면 한 번만 알림
import Foundation
import AVFoundation
import AudioToolbox
import MediaPlayer
import UIKit

struct InteonetBang: Identifiable, Equatable {
    let id: String        // 중계 방 번호
    let jemok: String
    let who: String
    let cho: Int          // 시작한 지 몇 초
    var yeongsang: Bool = false   // 2.5.0 영상으로 볼 수 있는가(중계의 hls)
    /// 고르기 단추 글 — 제목, 현장영상해설사, 몇 분째
    var ireum: String {
        let jm: String = jemok.isEmpty ? "제목 없는 방송" : jemok
        let w: String = who.isEmpty ? "" : ", 현장영상해설사 \(who)"
        return "\(jm)\(w), \(max(1, cho / 60))분째"
    }
}

final class Deut: NSObject, ObservableObject, URLSessionDataDelegate {
    static let shared = Deut()
    static let JUNGYE = "https://lvd.ada.or.kr:2053"
    /// 2.13.0 (261123 새방클, 이사장님 승인 「1」) 해설 소리를 영상과 따로 보내는 방송(중계 1.7.0 소리 따로 줄기) — 이 방송은 소리 줄기로 들음(영상이 밀려도 해설은 안 끊김)
    static var soriBang = Set<String>()

    // 2.11.0 (261105-LI14, 새방클, 이사장님 승인 2026-11-05) 웹 듣기 화면과 맞춤 — 소리 크기 나누어 조절, 해설 고르기, 만족도
    struct Gal: Equatable, Identifiable { let n: Int; let nm: String; let on: Bool; var id: Int { n } }
    @Published private(set) var galMok: [Gal] = []
    @Published private(set) var mjQ = ""
    @Published private(set) var mjSeq = -1
    static let KG: [Float] = [0, 0.1, 0.2, 0.3, 0.4, 0.55, 0.7, 0.85, 1, 1.25, 1.6]
    static var kgH: Int {
        get { let v = UserDefaults.standard.object(forKey: "kg_h") as? Int ?? 8; return min(10, max(1, v)) }
        set { UserDefaults.standard.set(newValue, forKey: "kg_h") }
    }
    static var kgM: Int {
        get { let v = UserDefaults.standard.object(forKey: "kg_m") as? Int ?? 8; return min(10, max(0, v)) }
        set { UserDefaults.standard.set(newValue, forKey: "kg_m") }
    }
    static var gal: Int {
        get { let v = UserDefaults.standard.integer(forKey: "gal"); return (v >= 1 && v <= 3) ? v : 1 }
        set { UserDefaults.standard.set(newValue, forKey: "gal") }
    }
    private var jyTtae = Date.distantPast
    func galGoreum(_ n: Int) {
        guard n != Deut.gal else { return }
        Deut.gal = n
        task?.cancel(); task = nil
        objectWillChange.send()
    }

    @Published private(set) var kyeojim = false
    @Published private(set) var sangtae = "듣기가 꺼져 있습니다."
    @Published private(set) var gorgi: [InteonetBang] = []   // 2.2.0 인터넷 방송이 여럿일 때 고를 것
    @Published private(set) var bogiBang: InteonetBang? = nil  // 2.5.0 듣는 인터넷 방송이 영상으로도 볼 수 있으면 그 방송
    @Published private(set) var jigeumMok: [InteonetBang] = []  // 2.13.0 (261123 새방클, 이사장님 승인 「1」) 듣기를 켜지 않아도 첫 화면에 늘 보이는 지금 나가는 인터넷 방송
    private(set) var bogiJung = false                           // 2.5.0 영상으로 보는 중 — 소리(mp3)는 쉼
    var mal: ((String) -> Void)?
    /// 2.6.1 (261026-LI9) 현장영상해설 소리 더 크게 — 1 보통, 2 두 배, 4 네 배(BYOD 방송에 적용), 기억함
    static var keugi: Int {
        get { let k = UserDefaults.standard.integer(forKey: "deo_keuge"); return (k == 2 || k == 4) ? k : 1 }
        set { UserDefaults.standard.set(newValue, forKey: "deo_keuge") }
    }
    var sonMeomchum = false      // 손으로 멈춤 — 행사장을 벗어날 때까지 저절로 다시 켜지 않음
    private(set) var jemok = ""

    enum Gil: Equatable { case eopseum, hyeonjang, inteonet(String) }
    private(set) var gil: Gil = .eopseum

    private let engine = AVAudioEngine()
    private let player = AVAudioPlayerNode()
    private let fmt = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: 16000, channels: 1, interleaved: false)!
    private var session: URLSession?
    private var task: URLSessionDataTask?
    private var avp: AVPlayer?          // 2.2.0 인터넷 방송(mp3)
    private var avpMeomchumTtae: Date?
    private var jamsi = false
    private var beoseona = false
    private var cheot = true
    private var dasiTimer: Timer?
    private var daegiTimer: Timer?      // 2.11.0 (261105-LI14, 이사장님 승인 2026-11-05 「1」) 웹 듣기 화면과 같은 대기 안내와 대기 음악
    private var daegiEumak: AVAudioPlayer?
    private var daegiMent: AVAudioPlayer?
    private var daegiJung = false
    private var mentTtae = Date.distantPast
    private var mutneunJung = false
    private var sojaeBatneun = false
    private var eumakData: Data?
    private var mentData: Data?
    private var chucheomTimer: Timer?   // 2.8.0 (261028-LI11) 입장 번호 알리기·당첨 보기 — 행사장에서 듣는 동안만
    private var namjin = Data()
    private var gameun = false   // 엔진을 붙였는가
    // 2.7.0 (261026-LI10, 새방클, 이사장님 승인 2026-10-26) 오푸스(시험) — 송출이 /opus 를 내보내면 그것으로(48000), 없으면 지금 방식(/stream)
    private let playerOpus = AVAudioPlayerNode()
    private let fmt48 = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: 48000, channels: 1, interleaved: false)!
    private var opusJung = false            // 지금 붙은 줄기가 오푸스인가(화면 줄기에서 정한 뒤 받는 줄기가 읽음)
    private var opusSwim = Date.distantPast // 이때까지는 오푸스를 묻지 않음(없었거나 못 풀었을 때)
    private var opusMal = ""                // 오푸스가 안 되면 지금 방식으로 붙으며 알릴 말
    private var opusDc: AVAudioConverter?   // 받는 줄기에서만
    private var opusHeotdoem = 0            // 받는 줄기에서만 — 풀지 못한 덩이가 이어진 수
    private var opusPullim = false          // 소리가 한 번이라도 풀려 나왔는가
    private var opusDapTtae: Date?          // 오푸스 대답(200)을 받은 때
    private weak var opusTask: URLSessionDataTask?   // 오푸스로 연 줄기(넘어간 뒤 늦게 온 것은 버림)
    private static func opusKkol() -> AVAudioFormat? {
        var d = AudioStreamBasicDescription(mSampleRate: 48000, mFormatID: kAudioFormatOpus, mFormatFlags: 0, mBytesPerPacket: 0,
                                            mFramesPerPacket: 960, mBytesPerFrame: 0, mChannelsPerFrame: 1, mBitsPerChannel: 0, mReserved: 0)
        return AVAudioFormat(streamDescription: &d)
    }
    private var salpinTtae = Date.distantPast
    // 2.12.1 (261115-LI19, 새방클, 이사장님 승인 2026-11-15 「1」) 플 와이파이에 붙어 있어도 플에 송출이 없으면 인터넷 방송을 들려 드림
    //   (11-15 밤 시험: 듣는 아이폰이 플에 붙어 대기 음악만 나오고, 그때 나가던 인터넷 방송은 찾지도 않았음)
    private var keuEopseumTtae: Date?      // 플에 해설(송출 소리)이 없기 시작한 때
    private var flintBium = false          // 플에 송출이 없어 인터넷 방송을 듣는 중 — 플에 송출이 들어오면 현장으로 돌아감
    private var bimMutTtae = Date.distantPast
    // 2.12.1 (이사장님 승인 2026-11-15 「1」) 인터넷으로 들을 때도 지금 나가는 방송이 없으면 대기 음악(협회 서버의 행사장 대기 음악과 같은 것)
    private var inetEumak: AVAudioPlayer?
    private var inetEumakData: Data?
    private var inetEumakBatneun = false
    // 2.12.1 송출 1.17.0 은 화면이 꺼지면(영상 → 소리) 방을 닫고 새 방을 엶 — 같은 현장영상해설사·같은 제목의 새 방이 15초 안에 나오면 「이어집니다」로 곧바로 붙음
    private var teunWho = ""
    private var kkeunBang: (who: String, jemok: String, ttae: Date)?
    private static let INET_EUMAK = "https://lvd.ada.or.kr/bfblive/hyeonjang/daegi.mp3"

    private static let DEC: [Float] = (0..<256).map { i in
        let u = ~i & 0xFF, s = u & 0x80, e = (u >> 4) & 7, m = u & 0x0F
        let x = (((m << 3) + 0x84) << e) - 0x84
        return Float(s != 0 ? -x : x) / 32768
    }

    override init() {
        super.init()
        let nc = NotificationCenter.default
        nc.addObserver(self, selector: #selector(kkeunkim(_:)), name: AVAudioSession.interruptionNotification, object: nil)
        nc.addObserver(self, selector: #selector(gilBakkwim(_:)), name: AVAudioSession.routeChangeNotification, object: nil)
    }

    private func bakkum(_ t: String, malhal: Bool) {
        DispatchQueue.main.async {
            guard t != self.sangtae else { return }
            self.sangtae = t
            BfbGirok.shared.namgi("deut", ["mal": t, "jari": Hyeonjang.jari])   // 2.4.1 이용 기록
            if malhal { self.mal?(t) }
        }
    }

    // MARK: 켜고 끄기

    func kyeogi() {
        guard !kyeojim else { return }
        sonMeomchum = false
        kyeojim = true
        jamsi = false
        cheot = true
        beoseona = false
        gil = .eopseum
        gorgi = []
        do {
            let s = AVAudioSession.sharedInstance()
            try s.setCategory(.playback, mode: .spokenAudio, options: [.mixWithOthers])   // 2.10.0 알림 소리에 끊기지 않게
            try s.setActive(true)
        } catch { }
        if !gameun {
            engine.attach(player)
            engine.connect(player, to: engine.mainMixerNode, format: fmt)
            engine.attach(playerOpus)   // 2.7.0
            engine.connect(playerOpus, to: engine.mainMixerNode, format: fmt48)
            gameun = true
        }
        try? engine.start()
        player.play()
        playerOpus.play()   // 2.7.0
        jamgeumHwamyeon()
        bakkum("현장영상해설을 찾고 있습니다.", malhal: false)
        butgi()
        dasiTimer?.invalidate()
        dasiTimer = Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] _ in self?.salpigi() }
        daegiTimer?.invalidate()   // 2.11.0
        eumakData = nil; mentData = nil
        daegiTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in self?.daegiSalpigi() }
        chucheomTimer?.invalidate()   // 2.8.0
        chucheomTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in if let s = self, s.gil == .hyeonjang { Chucheom.salpigi() } }
    }

    /// 2.8.0 행운권 당첨 — 상태 줄에 두고 한 번 말함
    func dangcheomMal(_ t: String) { bakkum(t, malhal: true) }

    func kkeugi(son: Bool) {
        guard kyeojim else { return }
        if son { sonMeomchum = true }
        kyeojim = false
        flintBium = false; keuEopseumTtae = nil; kkeunBang = nil   // 2.12.1
        inetDaegiKkeugi()   // 2.12.1
        dasiTimer?.invalidate(); dasiTimer = nil
        chucheomTimer?.invalidate(); chucheomTimer = nil   // 2.8.0
        daegiTimer?.invalidate(); daegiTimer = nil; daegiKkeugi()   // 2.11.0
        task?.cancel(); task = nil
        session?.invalidateAndCancel(); session = nil
        avpKkeugi()
        gil = .eopseum
        gorgi = []
        bogiBang = nil   // 2.5.0
        player.stop()
        playerOpus.stop()   // 2.7.0
        engine.stop()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        bakkum("듣기를 멈추었습니다.", malhal: false)
    }

    // MARK: 2.11.0 대기 안내와 대기 음악 — 해설이 없으면 음악을 깔고 60초마다 안내말, 음악이 없으면 안내말을 25초마다

    private func daegiKkeugi() {
        daegiEumak?.stop(); daegiEumak = nil
        daegiMent?.stop(); daegiMent = nil
        daegiJung = false
        mentTtae = Date.distantPast
    }

    private func sojaeBatgi(_ kkeut: @escaping () -> Void) {
        guard !sojaeBatneun else { return }
        sojaeBatneun = true
        let c = URLSessionConfiguration.ephemeral
        c.allowsCellularAccess = false
        c.timeoutIntervalForRequest = 8
        let ses = URLSession(configuration: c)
        let muk = DispatchGroup()
        var m: Data?, a: Data?
        for (ireum, i) in [("daegi.mp3", 0), ("ment.mp3", 1)] {
            guard let u = URL(string: Hyeonjang.jari + "/" + ireum) else { continue }
            muk.enter()
            ses.dataTask(with: u) { d, res, _ in
                let ok = (res as? HTTPURLResponse)?.statusCode == 200 ? d : nil
                DispatchQueue.main.async { if i == 0 { m = ok } else { a = ok }; muk.leave() }
            }.resume()
        }
        muk.notify(queue: .main) { [weak self] in
            ses.finishTasksAndInvalidate()
            guard let s = self else { return }
            s.eumakData = m; s.mentData = a; s.sojaeBatneun = false
            kkeut()
        }
    }

    private func mentHagi() {
        mentTtae = Date()
        guard let d = mentData, let p = try? AVAudioPlayer(data: d) else { return }
        p.volume = Deut.KG[Deut.kgM]
        daegiMent = p
        p.play()
    }

    private func daegiSijak() {
        guard kyeojim, gil == .hyeonjang, !daegiJung else { return }
        daegiJung = true
        if let d = eumakData, let p = try? AVAudioPlayer(data: d) {
            p.numberOfLoops = -1
            p.volume = 0.45 * Deut.KG[Deut.kgM]
            daegiEumak = p
            p.play()
        }
        bakkum("해설을 준비하고 있습니다. 음악과 안내가 나갑니다.", malhal: false)
        mentHagi()
    }

    private func daegiSalpigi() {
        guard kyeojim, !mutneunJung else { return }
        guard gil == .hyeonjang else {
            if daegiJung { daegiKkeugi() }
            if !galMok.isEmpty { galMok = [] }
            if !mjQ.isEmpty { mjQ = "" }
            return
        }
        let tt0 = Date()
        mutneunJung = true
        Hyeonjang.mutgi("/status", ttae: 1.5) { [weak self] d in
            DispatchQueue.main.async {
                guard let s = self else { return }
                s.mutneunJung = false
                guard s.kyeojim, let d = d, let j = (try? JSONSerialization.jsonObject(with: d)) as? [String: Any] else { return }
                if let l = j["gal"] as? [[String: Any]] {
                    let m = l.map { Gal(n: $0["n"] as? Int ?? 1, nm: $0["nm"] as? String ?? "", on: $0["on"] as? Bool ?? false) }
                    if m != s.galMok { s.galMok = m }
                }
                let q = j["mj"] as? String ?? "", sq = j["mjseq"] as? Int ?? -1
                if q != s.mjQ || sq != s.mjSeq { s.mjQ = q; s.mjSeq = sq }
                let keu = (j["keu"] as? Bool) ?? true
                // 2.12.1 플린트 2 방식에서만, 플에 붙은 송출 폰·수신기·무대 폰이 하나도 없고 해설도 없을 때만 셈(아이피타임 방식은 송출 폰이 곧 방송 서버)(송출 시작을 기다리는 동안은 다른 방송으로 넘기지 않음)
                let biom = ((j["flint"] as? Bool) ?? false) && !keu && (j["bonaem"] as? String ?? "").isEmpty && !((j["hyeon"] as? Bool) ?? false)
                if !biom { s.keuEopseumTtae = nil } else if s.keuEopseumTtae == nil { s.keuEopseumTtae = Date() }
                s.bimSalpigi()   // 2.12.1
                if keu, s.gil == .hyeonjang, Date().timeIntervalSince(s.jyTtae) >= 5 {   // 소리 늦음 알리기(피디 화면이 봄)
                    s.jyTtae = Date()
                    Hyeonjang.mutgi("/jiyeon?i=" + Chucheom.jid + "&d=\(600 + Int(Date().timeIntervalSince(tt0) * 500))", ttae: 1.5) { _ in }
                }
                s.daegiEumak?.volume = 0.45 * Deut.KG[Deut.kgM]
                s.daegiMent?.volume = Deut.KG[Deut.kgM]
                if s.jamsi || s.bogiJung || Hyeonjang.jeomgeom {
                    if s.daegiJung { s.daegiKkeugi() }
                    return
                }
                if keu {
                    if s.daegiJung {
                        s.daegiEumak?.setVolume(0, fadeDuration: 1)
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1) { [weak s] in s?.daegiKkeugi() }
                        s.daegiJung = false
                        s.bakkum("해설이 시작되었습니다.", malhal: true)
                    }
                } else if !s.daegiJung {
                    if s.eumakData == nil && s.mentData == nil { s.sojaeBatgi { if s.eumakData != nil || s.mentData != nil { s.daegiSijak() } } } else { s.daegiSijak() }
                } else if Date().timeIntervalSince(s.mentTtae) >= (s.daegiEumak != nil ? 60 : 25) {
                    s.mentHagi()
                }
            }
        }
    }

    // MARK: 받기 — 먼저 행사장, 아니면 인터넷

    private var butneunJung = false
    private var majimakBatgi = Date.distantPast
    private var wifiHaebon = Date.distantPast   // 2.5.0 방송 와이파이에 붙어 본 때
    private var waeMal = ""      // 2.11.0 방송을 못 찾은 까닭
    private var waeNatmal = ""
    private func waeSalpigi() {
        MangWae.salpigi { [weak self] a, b in
            guard let s = self, a != s.waeNatmal else { return }
            s.waeNatmal = a; s.waeMal = b
            BfbGirok.shared.namgi("bangsong_mot_chatgi", ["wae": a, "mal": b])   // 인터넷이 될 때 나스로 올라감
            BfbGirok.shared.jeojang()
        }
    }

    private func butgi() {
        guard kyeojim, !jamsi, !butneunJung else { return }
        butneunJung = true
        salpinTtae = Date()
        Hyeonjang.salpigi { [weak self] jm in
            guard let s = self else { return }
            guard s.kyeojim, !s.jamsi else { s.butneunJung = false; return }
            // 2.5.0 (261024-LI7, 이사장님 지시 2026-10-23) 행사장 방송을 못 찾으면, 방송 와이파이(현장영상해설방송·아이피타임 등)에 붙어 봄 —
            // 아이폰이 「연결하시겠습니까」를 물으면 연결 한 번. 붙으면 다시 찾고, 아니면 인터넷 방송으로(2분에 한 번만 해 봄)
            // 2.11.3 (261114-LI17, 대클, 이사장님 긴급 지시 2026-11-14) 저절로 와이파이 붙어 보기를 껐음 —
            // 행사장 밖에서 2분마다 아이폰 「와이파이에 연결할 수 없음」 창이 끝없이 떠서 폰을 쓸 수 없게 됨(이사장님 아이폰).
            // 와이파이 붙기는 이용자가 직접 할 때만: 큐알·엔에프시 「한 번에 듣기」, 더 보기 「행사장 와이파이 저절로 붙기」.
            // 등록해 둔 와이파이는 아이폰이 행사장 범위 안에서 스스로 붙으므로 앱이 따로 붙지 않아도 됨.
            if false && jm == nil && Date().timeIntervalSince(s.wifiHaebon) > 120 {
                s.wifiHaebon = Date()
                Hyeonjang.wifiBuchigi { buteum in
                    guard buteum else { s.waeSalpigi(); s.inteonetSalpigi(); return }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 3) { s.butneunJung = false; s.butgi() }
                }
                return
            }
            guard let jm = jm else { s.flintBium = false; s.waeSalpigi(); s.inteonetSalpigi(); return }
            if s.flintBium {   // 2.12.1 플에 송출이 없어 인터넷 방송을 듣는 중 — 플에 송출이 들어왔으면 현장으로, 아니면 인터넷 방송을 이어 감
                Hyeonjang.mutgi("/status", ttae: 1.5) { d in
                    let fj = d.flatMap { (try? JSONSerialization.jsonObject(with: $0)) as? [String: Any] }
                    // 플에 해설이 나오거나 송출 폰·수신기·무대 폰이 붙으면 현장으로(현장이 먼저)
                    let keu = (fj?["keu"] as? Bool ?? false) || !(fj?["bonaem"] as? String ?? "").isEmpty || (fj?["hyeon"] as? Bool ?? false)
                    DispatchQueue.main.async {
                        guard s.kyeojim, !s.jamsi, s.flintBium else { s.butneunJung = false; return }
                        if keu {
                            s.flintBium = false; s.keuEopseumTtae = nil
                            s.avpKkeugi(); s.bogiBang = nil; s.gil = .eopseum
                            s.butneunJung = false
                            s.bakkum("행사장 방송이 시작되어 현장 방송으로 바꿉니다.", malhal: true)
                            s.butgi()
                        } else if case .inteonet = s.gil {
                            s.inteonetSalpigi()
                        } else {   // 인터넷 방송이 끝났으면 플(대기 음악·안내)로 돌아감
                            s.inteonetInna { inna in
                                if inna { s.inteonetSalpigi() }
                                else { s.flintBium = false; s.keuEopseumTtae = nil; s.butneunJung = false; s.butgi() }
                            }
                        }
                    }
                }
                return
            }
            s.waeNatmal = ""; s.waeMal = ""
            s.butneunJung = false
            if s.bogiJung { s.butneunJung = false; return }   // 2.5.0 영상으로 보는 동안은 현장 방송으로 바꾸지 않음
            if case .inteonet = s.gil { s.avpKkeugi(); s.bogiBang = nil }   // 2.2.0 행사장에 들어서면 현장 방송으로(더 빠름)
            s.gil = .hyeonjang
            s.inetDaegiKkeugi()   // 2.12.1
            s.gorgi = []
            s.jemok = jm
            let c = URLSessionConfiguration.default
            c.allowsCellularAccess = false
            c.timeoutIntervalForRequest = 8
            c.requestCachePolicy = .reloadIgnoringLocalCacheData
            let ses = URLSession(configuration: c, delegate: s, delegateQueue: nil)
            s.session?.invalidateAndCancel()
            s.session = ses
            let m = (jm.isEmpty ? "" : jm + " 행사 ") + "현장영상해설에 연결되었습니다."
            // 2.7.0 오푸스(시험)를 먼저 물어봄 — 알림은 대답이 온 뒤에(오푸스면 오푸스라고, 없으면 지금 방식으로 붙으며)
            if !Hyeonjang.jeomgeom, Deut.gal == 1, Date() >= s.opusSwim, let uo = Hyeonjang.opus {
                s.opusMal = s.beoseona && !s.cheot ? "다시 연결되었습니다. " + m : m
                s.opusJung = true
                s.namjin = Data()
                s.majimakBatgi = Date()
                let t = ses.dataTask(with: uo)
                s.task = t
                s.opusTask = t
                s.opusPullim = false
                s.opusDapTtae = nil
                t.resume()
                s.beoseona = false
                s.cheot = false
                s.jamgeumHwamyeon()
                return
            }
            guard let u = Hyeonjang.stream else { return }   // 2.4.0 찾아 둔 자리(플린트 2 또는 아이피타임 방식 송출 폰)
            s.opusJung = false   // 2.7.0
            s.namjin = Data()
            s.majimakBatgi = Date()
            let t = ses.dataTask(with: u)
            s.task = t
            t.resume()
            s.bakkum(s.beoseona && !s.cheot ? "다시 연결되었습니다. " + m : m, malhal: true)
            s.beoseona = false
            s.cheot = false
            s.jamgeumHwamyeon()
        }
    }

    /// 2.2.0 행사장이 아닐 때 — 인터넷 방송을 살핌(듣는 중이면 그 방송이 이어지는지, 아니면 지금 나가는 것 찾기)
    private func inteonetSalpigi() {
        if case .hyeonjang = gil {   // 현장 방송을 듣다가 행사장을 벗어남
            task?.cancel(); task = nil
            gil = .eopseum
            mangEopseum()
        }
        if case .inteonet(let sid) = gil {
            mutgi("/sangtae?sid=\(sid)") { [weak self] j in
                guard let s = self else { return }
                s.butneunJung = false
                guard s.kyeojim, case .inteonet(let sid2) = s.gil, sid2 == sid else { return }
                // 2.12.0 (261115-LI18, 이사장님 원칙 2026-11-15) 마지막 자료가 30초 넘게 끊긴 방송은 끝난 것으로 봄
                if let j = j, j["on"] as? Bool == false || Deut.majiCho(j) > 30 {
                    s.avpKkeugi(); s.gil = .eopseum; s.bogiBang = nil
                    s.salpinTtae = .distantPast   // 2.12.1 10초를 기다리지 않고 곧바로 다음 방송을 찾음(송출이 영상·소리를 바꿔 방을 새로 열 때 빈틈을 줄임)
                    s.kkeunBang = (s.teunWho, s.jemok, Date())   // 2.12.1 같은 방송이 새 방으로 이어지는지 먼저 봄(끝났다는 말은 아닐 때만)
                    s.bakkum("방송을 다시 찾고 있습니다.", malhal: false)
                    return
                }
                // 2.5.0 처음엔 영상 조각이 준비되지 않았다가 뒤에 준비될 수 있음
                if let j = j, (j["hls"] as? Bool ?? false) || (j["yeongsang"] as? Bool ?? false), s.bogiBang?.id != sid {
                    let b = InteonetBang(id: sid, jemok: j["title"] as? String ?? s.jemok, who: j["who"] as? String ?? "", cho: j["cho"] as? Int ?? 0, yeongsang: true)
                    s.bogiDugi(b)   // 2.12.0 영상 길이 실제로 열릴 때만 「영상으로 보기」
                }
                if s.bogiJung { return }   // 2.5.0 영상으로 보는 동안은 소리를 다시 붙지 않음
                s.avpDanggigi()   // 2.12.1 늦어졌으면 앞으로 당김
                // 소리가 10초 넘게 멈춰 있으면 다시 붙음
                if let p = s.avp, p.timeControlStatus != .playing {
                    if s.avpMeomchumTtae == nil { s.avpMeomchumTtae = Date() }
                    if let t = s.avpMeomchumTtae, Date().timeIntervalSince(t) > 10 { s.avpTeulgi(sid) }
                } else { s.avpMeomchumTtae = nil }
            }
            return
        }
        mutgi("/moklok") { [weak self] j in
            guard let s = self else { return }
            s.butneunJung = false
            guard s.kyeojim, s.gil == .eopseum else { return }
            guard let j = j else {
                s.bakkum("인터넷에 닿지 않습니다. " + (s.waeMal.isEmpty ? "행사장에서는 행사장 와이파이에 붙으면 저절로 들립니다." : s.waeMal), malhal: s.cheot)
                s.cheot = false
                return
            }
            // 2.12.0 마지막 자료가 30초 넘게 끊긴 방송(끝난 방송)은 목록에서 뺌 — 남은 것이 하나면 곧바로 틂
            let rows = ((j["rows"] as? [[String: Any]]) ?? []).filter { ["live", "bangsong"].contains($0["jong"] as? String ?? "") && Deut.majiCho($0) <= 30 }
            Deut.soriBang = Set(rows.filter { $0["sori"] as? Bool ?? false }.compactMap { $0["sid"] as? String })   // 2.13.0
            let l = rows.map { InteonetBang(id: $0["sid"] as? String ?? "", jemok: $0["title"] as? String ?? "", who: $0["who"] as? String ?? "", cho: $0["cho"] as? Int ?? 0,
                                            yeongsang: ($0["hls"] as? Bool ?? false)) }.filter { !$0.id.isEmpty }   // 2.14.1 영상 묶음(hls)이 실제로 있을 때만 영상 방송으로 — 없으면 소리부터 틀고, 준비되면 저절로 영상 화면(11-27 이어서 방송에서 영상 화면이 비고 소리도 안 나던 일)
            // 2.12.1 듣던 방송이 새 방으로 이어졌으면(같은 현장영상해설사·같은 제목) 곧바로 붙음. 15초 기다려도 없으면 그제야 끝났다고 알림
            var ap = ""
            if let k = s.kkeunBang {
                if let b = l.first(where: { $0.who == k.who && $0.jemok == k.jemok }) {
                    s.kkeunBang = nil
                    s.inteonetGoreum(b, ieoMal: true)
                    s.cheot = false
                    return
                }
                if Date().timeIntervalSince(k.ttae) < 15 { s.salpinTtae = .distantPast; return }   // 새 방이 열리는 동안 15초까지 기다림(2초마다 다시 봄)
                s.kkeunBang = nil
                ap = "그 현장영상해설 방송이 끝났습니다. "
            }
            if l.isEmpty {
                if !s.gorgi.isEmpty { s.gorgi = [] }   // 2.12.2 10초마다 같은 빈 목록을 다시 넣어 화면을 다시 그리던 것(보이스오버 방해)을 막음
                s.inetDaegiKyeogi()   // 2.12.1
                s.bakkum(ap + "지금 나가는 현장영상해설이 없습니다. 방송이 열리거나 행사장에 들어서시면 저절로 들려 드립니다. 그동안 첫 화면의 라디오와 화면해설방송 듣기로 다른 방송을 들으실 수 있습니다." + ((s.waeNatmal.isEmpty || s.waeNatmal == "wifi_eopseum") ? "" : " 행사장이시면, " + s.waeMal), malhal: true)
            } else if l.count == 1 {
                s.inetDaegiKkeugi()   // 2.12.1
                s.inteonetGoreum(l[0], apMal: ap)
            } else if !ap.isEmpty || s.gorgi.map({ $0.id + ($0.yeongsang ? "v" : "") }) != l.map({ $0.id + ($0.yeongsang ? "v" : "") }) {
                s.gorgi = l
                s.jigeumMok = l   // 2.13.0 첫 화면 목록도 곧바로
                s.inetDaegiKkeugi()   // 2.12.1
                s.bakkum(ap + "지금 나가는 현장영상해설이 \(l.count)개 있습니다. 아래에서 들으실 것을 고르십시오.", malhal: true)
            }
            s.cheot = false
        }
    }

    /// 2.12.1 플에 붙어 듣는데 송출이 10초 넘게 없으면, 지금 나가는 인터넷 방송이 있는지 10초마다 보고 있으면 그리로 바꿈
    private func bimSalpigi() {
        guard gil == .hyeonjang, !flintBium, !Hyeonjang.jeomgeom, !bogiJung, let t = keuEopseumTtae,
              Date().timeIntervalSince(t) > 10, Date().timeIntervalSince(bimMutTtae) > 10 else { return }
        bimMutTtae = Date()
        inteonetInna { [weak self] inna in
            guard let s = self, inna, s.kyeojim, !s.jamsi, s.gil == .hyeonjang, !s.flintBium, s.keuEopseumTtae != nil else { return }
            s.flintBium = true
            s.task?.cancel(); s.task = nil
            s.daegiKkeugi()
            s.gil = .eopseum
            s.bakkum("행사장 공유기에 방송하는 기기가 없어, 지금 나가는 인터넷 방송을 들려 드립니다. 행사장 방송이 시작되면 저절로 바꿉니다.", malhal: true)
            s.butneunJung = true
            s.inteonetSalpigi()
        }
    }

    /// 2.12.1 지금 나가는(마지막 자료가 30초 안인) 인터넷 방송이 하나라도 있는가 — 인터넷에 닿지 않으면 없음
    private func inteonetInna(_ kkeut: @escaping (Bool) -> Void) {
        mutgi("/moklok") { j in
            let rows = ((j?["rows"] as? [[String: Any]]) ?? []).filter { ["live", "bangsong"].contains($0["jong"] as? String ?? "") && Deut.majiCho($0) <= 30 && !($0["sid"] as? String ?? "").isEmpty }
            kkeut(!rows.isEmpty)
        }
    }

    /// 2.13.0 (261123 새방클, 이사장님 승인 「1」 — 앱을 열면 방송 목록이 나오고 고르면 곧바로) 듣기와 상관없이 지금 나가는 인터넷 방송 목록을 받아 둠
    ///   첫 화면이 3초마다 부름. 목록이 바뀔 때만 고쳐 화면을 다시 그리지 않음(보이스오버 방해 막기). 바뀐 방송이 새로 생기면 그 방송들을 돌려줌
    func mokSalpigi(_ kkeut: @escaping ([InteonetBang]) -> Void = { _ in }) {
        mutgi("/moklok") { [weak self] j in
            guard let s = self, let j = j else { return }
            let rows = ((j["rows"] as? [[String: Any]]) ?? []).filter { ["live", "bangsong"].contains($0["jong"] as? String ?? "") && Deut.majiCho($0) <= 30 }
            for r in rows where r["sori"] as? Bool ?? false { if let id = r["sid"] as? String { Deut.soriBang.insert(id) } }
            let l = rows.map { InteonetBang(id: $0["sid"] as? String ?? "", jemok: $0["title"] as? String ?? "", who: $0["who"] as? String ?? "", cho: $0["cho"] as? Int ?? 0,
                                            yeongsang: ($0["hls"] as? Bool ?? false)) }.filter { !$0.id.isEmpty }   // 2.14.1 영상 묶음(hls)이 실제로 있을 때만 영상 방송으로 — 없으면 소리부터 틀고, 준비되면 저절로 영상 화면(11-27 이어서 방송에서 영상 화면이 비고 소리도 안 나던 일)
            let yet = Set(s.jigeumMok.map { $0.id })
            if l.map({ $0.id + ($0.yeongsang ? "v" : "") }) != s.jigeumMok.map({ $0.id + ($0.yeongsang ? "v" : "") }) { s.jigeumMok = l }
            kkeut(l.filter { !yet.contains($0.id) })
        }
    }

    /// 2.13.0 지금 이 방송을 듣는 중인가(목록 단추 글)
    func deunneun(_ sid: String) -> Bool { if kyeojim, case .inteonet(let s) = gil { return s == sid }; return false }

    /// 2.13.0 목록에서 고른 방송을 곧바로 — 듣기가 꺼져 있으면 켜고 그 방송을 틂(행사장 방송을 듣던 중이어도 고른 방송으로)
    ///   영상이 있는 방송은 영상 화면이 소리까지 맡으므로, 듣기가 꺼져 있으면 켜지 않음(행사장 소리와 겹치지 않게). 듣던 중이면 그 방송으로 바꿔 두어 영상을 닫으면 이어 들음
    func baroDeutgi(_ b: InteonetBang) {
        if deunneun(b.id) { return }
        if b.yeongsang {
            guard kyeojim else { return }
            bogiJung = true
            inteonetGoreum(b)
            return
        }
        if !kyeojim { kyeogi() }
        inteonetGoreum(b)
    }

    /// 인터넷 방송 하나를 골라 틂(여럿일 때 화면 단추가 부름)
    func inteonetGoreum(_ b: InteonetBang, ieoMal: Bool = false, apMal: String = "") {
        guard kyeojim else { return }
        teunWho = b.who   // 2.12.1
        inetDaegiKkeugi()   // 2.12.1
        task?.cancel(); task = nil
        gil = .inteonet(b.id)
        gorgi = []
        jemok = b.jemok
        bogiBang = nil   // 2.5.0, 2.12.0 영상 길이 열리는지 본 뒤에만 「영상으로 보기」(안 열리면 소리만 그대로)
        if b.yeongsang { bogiDugi(b) }
        if !bogiJung { avpTeulgi(b.id) }
        let jm: String = b.jemok.isEmpty ? "" : "\(b.jemok) "
        let w: String = b.who.isEmpty ? "" : " 현장영상해설사 \(b.who)."
        let m = ieoMal ? "같은 현장영상해설이 이어집니다." : apMal + "인터넷으로 \(jm)현장영상해설을 들려 드립니다.\(w)"   // 2.12.1
        bakkum(m, malhal: true)
        jamgeumHwamyeon()
    }

    // MARK: 2.5.0 영상으로 보기 — 보는 동안 소리를 쉬고, 나오면 지금 자리부터 이어 들려 드림

    func bogiSijak() {
        bogiJung = true
        inetDaegiKkeugi()   // 2.12.1
        avp?.pause()
    }

    func bogiKkeut() {
        guard bogiJung else { return }
        bogiJung = false
        guard kyeojim, !jamsi else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio, options: [.mixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        if case .inteonet(let sid) = gil { avpTeulgi(sid) }   // 생방송이라 다시 붙어 지금 것부터
    }

    private func avpTeulgi(_ sid: String) {
        avp?.pause()
        guard let u = URL(string: Deut.JUNGYE + "/mp3?sid=\(sid)" + (Deut.soriBang.contains(sid) ? "&gil=sori" : "") + "&_c=\(Int(Date().timeIntervalSince1970))") else { return }   // 2.13.0 소리 줄기가 있으면 그것을
        // 2.12.1 (11-15 밤 시험: 인터넷 방송이 20~30초 늦게 들림 — 서버까지는 제 속도였고, 재생기가 끊기지 않으려고 소리를 길게 모았다가 틂)
        //   모아 두는 양을 2초로 줄이고, 들어온 대로 곧바로 틂. 늦어지면 avpDanggigi 가 앞으로 당김
        let it = AVPlayerItem(url: u)
        it.preferredForwardBufferDuration = 2
        let p = AVPlayer(playerItem: it)
        p.automaticallyWaitsToMinimizeStalling = false
        avp = p
        avpMeomchumTtae = nil
        p.playImmediately(atRate: 1)
    }

    /// 2.12.2 (261116, 이사장님 11-16 0시 45분 시험: 10~15초마다 소리가 잠깐씩 끊김) — 앞으로 당기기를 고침
    ///   2.12.1 은 6초 넘게 늦으면 곧바로 「앞으로 감기」를 했는데, 중계는 감기를 받지 않아 재생기가 그때마다 새로 붙어 소리가 끊겼음
    ///   (보내는 쪽이 조각을 묶어 보내 소리가 몰려 들어오면 늦은 것처럼 보여 10초마다 되풀이됨)
    ///   이제는 감지 않고, 15초 넘게 늦은 것이 20초(두 번) 이어질 때만 한 번 새로 붙어 지금 나가는 곳부터 들음. 새로 붙은 뒤 2분은 다시 하지 않음
    private var neujeumSu = 0
    private var danggin = Date.distantPast
    private func avpDanggigi() {
        guard let p = avp, let it = p.currentItem, let r = it.loadedTimeRanges.last?.timeRangeValue,
              case .inteonet(let sid) = gil else { neujeumSu = 0; return }
        let kkeut = CMTimeGetSeconds(CMTimeRangeGetEnd(r)), jigeum = CMTimeGetSeconds(p.currentTime())
        guard kkeut.isFinite, jigeum.isFinite, kkeut - jigeum > 15 else { neujeumSu = 0; return }
        neujeumSu += 1
        guard neujeumSu >= 2, Date().timeIntervalSince(danggin) > 120 else { return }
        neujeumSu = 0; danggin = Date()
        avpTeulgi(sid)
    }

    /// 2.12.1 인터넷으로 들을 때 지금 나가는 방송이 없으면 대기 음악 — 방송이 열리면 끔
    private func inetDaegiKyeogi() {
        guard kyeojim, !jamsi, !bogiJung, gil == .eopseum, inetEumak == nil else { return }
        guard let d = inetEumakData else {
            guard !inetEumakBatneun, let u = URL(string: Deut.INET_EUMAK) else { return }
            inetEumakBatneun = true
            var r = URLRequest(url: u, cachePolicy: .returnCacheDataElseLoad, timeoutInterval: 15)
            r.allowsCellularAccess = true
            URLSession.shared.dataTask(with: r) { [weak self] d, res, _ in
                let ok = (res as? HTTPURLResponse)?.statusCode == 200 ? d : nil
                DispatchQueue.main.async {
                    guard let s = self else { return }
                    s.inetEumakBatneun = false
                    if let ok = ok { s.inetEumakData = ok; s.inetDaegiKyeogi() }
                }
            }.resume()
            return
        }
        guard let p = try? AVAudioPlayer(data: d) else { return }
        p.numberOfLoops = -1
        p.volume = 0.35 * Deut.KG[Deut.kgM]
        inetEumak = p
        p.play()
    }

    private func inetDaegiKkeugi() {
        guard let p = inetEumak else { return }
        inetEumak = nil
        p.setVolume(0, fadeDuration: 1)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1) { p.stop() }
    }

    private func avpKkeugi() { avp?.pause(); avp = nil; avpMeomchumTtae = nil }

    /// 중계에 묻기 — 인터넷 길(휴대전화망 포함)
    /// 2.12.0 중계 목록·상태의 maji(마지막 자료 뒤 지난 초) — 없으면 0
    static func majiCho(_ j: [String: Any]) -> Int { (j["maji"] as? NSNumber)?.intValue ?? 0 }

    /// 2.12.0 영상 조각 목록(HLS)이 실제로 열리면 그때 「영상으로 보기」를 띄움. 안 열리면 소리만 그대로 들려 드림
    private func bogiDugi(_ b: InteonetBang) {
        guard let u = URL(string: Deut.JUNGYE + "/hls/" + (b.id.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? b.id) + "/index.m3u8") else { return }
        var r = URLRequest(url: u, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 8)
        r.allowsCellularAccess = true
        URLSession.shared.dataTask(with: r) { [weak self] d, res, _ in
            let ok = (res as? HTTPURLResponse)?.statusCode == 200 && (d.map { String(decoding: $0, as: UTF8.self).contains("#EXTM3U") } ?? false)
            DispatchQueue.main.async {
                guard let s = self, ok, case .inteonet(let sid) = s.gil, sid == b.id, s.bogiBang?.id != b.id else { return }
                s.bogiBang = b
            }
        }.resume()
    }

    private func mutgi(_ gil: String, _ kkeut: @escaping ([String: Any]?) -> Void) {
        guard let u = URL(string: Deut.JUNGYE + gil) else { kkeut(nil); return }
        var r = URLRequest(url: u, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 8)
        r.allowsCellularAccess = true
        URLSession.shared.dataTask(with: r) { d, res, _ in
            let j = (res as? HTTPURLResponse)?.statusCode == 200 ? d.flatMap { (try? JSONSerialization.jsonObject(with: $0)) as? [String: Any] } : nil
            DispatchQueue.main.async { kkeut(j) }
        }.resume()
    }

    private var eopseumTtae: Date?
    private func mangEopseum() {
        let now = Date()
        if eopseumTtae == nil { eopseumTtae = now }
        if !beoseona, let t = eopseumTtae, now.timeIntervalSince(t) > 8 {
            beoseona = true
            bakkum("BYOD 방송에서 벗어났습니다. 다시 들어오시면 저절로 이어서 들려 드립니다. 그사이 인터넷 방송이 있으면 그것을 들려 드립니다.", malhal: true)
        }
    }

    /// 2초마다 — 현장 방송은 끊겼으면 곧바로 다시 붙고, 인터넷 방송과 찾기는 10초마다 살핌
    private func salpigi() {
        guard kyeojim, !jamsi else { return }
        switch gil {
        case .hyeonjang:
            // 2.7.0 오푸스 대답은 왔는데 3초 넘게 소리가 하나도 안 풀리면 지금 방식으로
            if opusJung, !opusPullim, let t = opusDapTtae, Date().timeIntervalSince(t) > 3 {
                opusSwim = Date().addingTimeInterval(30)
                opusDaesin()
                return
            }
            if task == nil || Date().timeIntervalSince(majimakBatgi) > 6 {
                task?.cancel(); task = nil
                butgi()
            }
        case .inteonet, .eopseum:
            if Date().timeIntervalSince(salpinTtae) >= 10 { butgi() }
        }
    }

    // 2.7.0 오푸스를 물었을 때의 대답 — 오푸스면 풀개를 만들고 알림, 아니면 30초(풀개를 못 만들면 10분) 동안 묻지 않고 지금 방식으로
    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
                    completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        guard opusJung else { completionHandler(.allow); return }
        let r = response as? HTTPURLResponse
        let ok = r?.statusCode == 200 && (r?.value(forHTTPHeaderField: "Content-Type") ?? "").contains("x-lvd-opus")
        var dc: AVAudioConverter? = nil
        if ok, let of = Deut.opusKkol() { dc = AVAudioConverter(from: of, to: fmt48) }
        if let dc = dc {
            opusDc = dc
            opusHeotdoem = 0
            opusPullim = false
            opusDapTtae = Date()
            completionHandler(.allow)
            DispatchQueue.main.async { self.bakkum(self.opusMal + " 맑은 소리(오푸스 시험)로 듣습니다.", malhal: true) }
            return
        }
        opusSwim = Date().addingTimeInterval(ok ? 600 : 30)
        completionHandler(.cancel)
        DispatchQueue.main.async { self.opusDaesin() }
    }

    /// 2.7.0 오푸스가 안 될 때 — 지금 방식(/stream)으로 곧바로 붙음(화면 줄기)
    private func opusDaesin() {
        guard kyeojim, !jamsi, gil == .hyeonjang, opusJung, let ses = session, let u = Hyeonjang.stream else { return }
        opusJung = false
        task?.cancel()
        namjin = Data()
        majimakBatgi = Date()
        let t = ses.dataTask(with: u)
        task = t
        t.resume()
        bakkum(opusMal, malhal: true)
    }

    /// 2.7.0 오푸스 줄기 — 길이 2바이트 + 덩이(길이 0은 살아 있음)를 풀어 48000으로 틂(받는 줄기)
    private func opusBatgi(_ data: Data) {
        namjin.append(data)
        while namjin.count >= 2 {
            let h = [UInt8](namjin.prefix(2))
            let len = Int(h[0]) << 8 | Int(h[1])
            if len == 0 { namjin.removeFirst(2); continue }
            if namjin.count < 2 + len { break }
            let pk = Data(namjin.dropFirst(2).prefix(len))
            namjin.removeFirst(2 + len)
            guard let dc = opusDc, let of = Deut.opusKkol() else { continue }
            let cb = AVAudioCompressedBuffer(format: of, packetCapacity: 1, maximumPacketSize: len)
            pk.withUnsafeBytes { (p: UnsafeRawBufferPointer) in if let b = p.baseAddress { cb.data.copyMemory(from: b, byteCount: len) } }
            cb.byteLength = UInt32(len)
            cb.packetCount = 1
            cb.packetDescriptions?[0] = AudioStreamPacketDescription(mStartOffset: 0, mVariableFramesInPacket: 0, mDataByteSize: UInt32(len))
            guard let out = AVAudioPCMBuffer(pcmFormat: fmt48, frameCapacity: 5760) else { continue }
            var juem = false
            var err: NSError?
            let st = dc.convert(to: out, error: &err) { _, s in
                if juem { s.pointee = .noDataNow; return nil }
                juem = true
                s.pointee = .haveData
                return cb
            }
            if st == .error || out.frameLength == 0 {
                opusHeotdoem += 1
                if opusHeotdoem == 100 {   // 2초 넘게 하나도 못 풂 — 이 폰은 10분 동안 지금 방식으로
                    opusSwim = Date().addingTimeInterval(600)
                    DispatchQueue.main.async { self.opusDaesin() }
                }
                continue
            }
            opusHeotdoem = 0
            opusPullim = true
            if let ch = out.floatChannelData?[0] {
                let kg = Float(Deut.keugi) * Deut.KG[Deut.kgH]   // 2.6.1 소리 더 크게와 같게
                if kg > 1 {
                    for i in 0..<Int(out.frameLength) {
                        var x = ch[i] * kg
                        let ax = abs(x); if ax > 0.85 { x = (x < 0 ? -1 : 1) * min(1, 0.85 + (ax - 0.85) * 0.2) }
                        ch[i] = x
                    }
                }
            }
            playerOpus.scheduleBuffer(out, completionHandler: nil)
        }
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        guard kyeojim, !jamsi, gil == .hyeonjang else { return }
        if !opusJung, let ot = opusTask, ot === dataTask { return }   // 2.7.0 지금 방식으로 넘어간 뒤 옛 오푸스 줄기에서 늦게 온 것
        majimakBatgi = Date()
        eopseumTtae = nil
        if opusJung { opusBatgi(data); return }   // 2.7.0
        namjin.append(data)
        while namjin.count >= 1600 {
            let jogak = namjin.prefix(1600)
            namjin.removeFirst(1600)
            guard let b = AVAudioPCMBuffer(pcmFormat: fmt, frameCapacity: 1600), let ch = b.floatChannelData?[0] else { continue }
            var i = 0
            let kg = Float(Deut.keugi) * Deut.KG[Deut.kgH]   // 2.6.1 (261026-LI9) 현장영상해설 소리 더 크게 — 넘치는 소리는 부드럽게 눌러 찢어지지 않게
            for v in jogak {
                var x = Deut.DEC[Int(v)] * kg
                if kg > 1 { let ax = abs(x); if ax > 0.85 { x = (x < 0 ? -1 : 1) * min(1, 0.85 + (ax - 0.85) * 0.2) } }
                ch[i] = x; i += 1
            }
            b.frameLength = 1600
            player.scheduleBuffer(b, completionHandler: nil)
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        DispatchQueue.main.async { if self.task === task { self.task = nil } }
    }

    // MARK: 전화 따위 — 멈췄다가 끝나면 저절로 다시

    @objc private func kkeunkim(_ n: Notification) {
        guard let v = n.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              let t = AVAudioSession.InterruptionType(rawValue: v), kyeojim else { return }
        if t == .began {
            jamsi = true
            task?.cancel(); task = nil
            avp?.pause()
            inetDaegiKkeugi()   // 2.12.1
            bakkum("전화나 다른 소리 때문에 잠시 멈추었습니다. 끝나면 저절로 다시 들려 드립니다.", malhal: false)
        } else {
            jamsi = false
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
                guard let s = self, s.kyeojim, !s.bogiJung else { return }   // 2.5.0 영상으로 보는 중이면 영상 화면이 맡음
                try? AVAudioSession.sharedInstance().setActive(true)
                try? s.engine.start()
                s.player.play()
                s.playerOpus.play()   // 2.7.0
                s.bakkum("다시 들려 드립니다.", malhal: true)
                if case .inteonet(let sid) = s.gil { if !s.bogiJung { s.avpTeulgi(sid) } }   // 생방송이라 다시 붙어 지금 것부터(영상으로 보는 중이면 그대로 쉼)
                else { s.butgi() }
            }
        }
    }

    @objc private func gilBakkwim(_ n: Notification) {
        // 이어폰을 빼면 아이폰이 멈춤 — 엔진을 다시 돌림
        guard kyeojim, !jamsi else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
            guard let s = self, s.kyeojim else { return }
            if !s.engine.isRunning { try? s.engine.start(); s.player.play(); s.playerOpus.play() }   // 2.7.0 오푸스도
            if case .inteonet = s.gil, !s.bogiJung, let p = s.avp, p.timeControlStatus != .playing { p.play() }
        }
    }

    // MARK: 잠금 화면

    private var ricomGam = false
    private func jamgeumHwamyeon() {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: jemok.isEmpty ? "현장영상해설" : jemok,
            MPMediaItemPropertyArtist: "LVD 방송",
            MPNowPlayingInfoPropertyIsLiveStream: true,
        ]
        guard !ricomGam else { return }
        ricomGam = true
        let rc = MPRemoteCommandCenter.shared()
        rc.pauseCommand.addTarget { [weak self] _ in self?.kkeugi(son: true); return .success }
        rc.playCommand.addTarget { [weak self] _ in self?.kyeogi(); return .success }
        rc.togglePlayPauseCommand.addTarget { [weak self] _ in
            guard let s = self else { return .commandFailed }
            if s.kyeojim { s.kkeugi(son: true) } else { s.kyeogi() }
            return .success
        }
    }
}
