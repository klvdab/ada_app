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
            guard let jm = jm else { s.waeSalpigi(); s.inteonetSalpigi(); return }
            s.waeNatmal = ""; s.waeMal = ""
            s.butneunJung = false
            if s.bogiJung { s.butneunJung = false; return }   // 2.5.0 영상으로 보는 동안은 현장 방송으로 바꾸지 않음
            if case .inteonet = s.gil { s.avpKkeugi(); s.bogiBang = nil }   // 2.2.0 행사장에 들어서면 현장 방송으로(더 빠름)
            s.gil = .hyeonjang
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
                if let j = j, j["on"] as? Bool == false {
                    s.avpKkeugi(); s.gil = .eopseum; s.bogiBang = nil
                    s.bakkum("그 현장영상해설 방송이 끝났습니다. 다른 방송을 찾고 있습니다.", malhal: true)
                    return
                }
                // 2.5.0 처음엔 영상 조각이 준비되지 않았다가 뒤에 준비될 수 있음
                if let j = j, (j["hls"] as? Bool ?? false) || (j["yeongsang"] as? Bool ?? false), s.bogiBang?.id != sid {
                    s.bogiBang = InteonetBang(id: sid, jemok: j["title"] as? String ?? s.jemok, who: j["who"] as? String ?? "", cho: j["cho"] as? Int ?? 0, yeongsang: true)
                }
                if s.bogiJung { return }   // 2.5.0 영상으로 보는 동안은 소리를 다시 붙지 않음
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
            let rows = ((j["rows"] as? [[String: Any]]) ?? []).filter { ["live", "bangsong"].contains($0["jong"] as? String ?? "") }
            let l = rows.map { InteonetBang(id: $0["sid"] as? String ?? "", jemok: $0["title"] as? String ?? "", who: $0["who"] as? String ?? "", cho: $0["cho"] as? Int ?? 0,
                                            yeongsang: ($0["hls"] as? Bool ?? false) || ($0["yeongsang"] as? Bool ?? false)) }.filter { !$0.id.isEmpty }
            if l.isEmpty {
                s.gorgi = []
                s.bakkum("지금 나가는 현장영상해설이 없습니다. 방송이 열리거나 행사장에 들어서시면 저절로 들려 드립니다." + ((s.waeNatmal.isEmpty || s.waeNatmal == "wifi_eopseum") ? "" : " 행사장이시면, " + s.waeMal), malhal: true)
            } else if l.count == 1 {
                s.inteonetGoreum(l[0])
            } else if s.gorgi.map({ $0.id + ($0.yeongsang ? "v" : "") }) != l.map({ $0.id + ($0.yeongsang ? "v" : "") }) {
                s.gorgi = l
                s.bakkum("지금 나가는 현장영상해설이 \(l.count)개 있습니다. 아래에서 들으실 것을 고르십시오.", malhal: true)
            }
            s.cheot = false
        }
    }

    /// 인터넷 방송 하나를 골라 틂(여럿일 때 화면 단추가 부름)
    func inteonetGoreum(_ b: InteonetBang) {
        guard kyeojim else { return }
        task?.cancel(); task = nil
        gil = .inteonet(b.id)
        gorgi = []
        jemok = b.jemok
        bogiBang = b.yeongsang ? b : nil   // 2.5.0
        if !bogiJung { avpTeulgi(b.id) }
        let jm: String = b.jemok.isEmpty ? "" : "\(b.jemok) "
        let w: String = b.who.isEmpty ? "" : " 현장영상해설사 \(b.who)."
        let m = "인터넷으로 \(jm)현장영상해설을 들려 드립니다.\(w)"
        bakkum(m, malhal: true)
        jamgeumHwamyeon()
    }

    // MARK: 2.5.0 영상으로 보기 — 보는 동안 소리를 쉬고, 나오면 지금 자리부터 이어 들려 드림

    func bogiSijak() {
        bogiJung = true
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
        guard let u = URL(string: Deut.JUNGYE + "/mp3?sid=\(sid)&_c=\(Int(Date().timeIntervalSince1970))") else { return }
        let p = AVPlayer(url: u)
        p.automaticallyWaitsToMinimizeStalling = true
        avp = p
        avpMeomchumTtae = nil
        p.play()
    }

    private func avpKkeugi() { avp?.pause(); avp = nil; avpMeomchumTtae = nil }

    /// 중계에 묻기 — 인터넷 길(휴대전화망 포함)
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
