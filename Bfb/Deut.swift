// LVD 방송 아이폰(듣는 앱) — 지금 현장영상해설 듣기 (2.2.0판, 빌드 261018-LI3, 방클, 이사장님 승인 「1」 — LVD 방송 정리안)
// 2.2.0: 단추 하나로 — 행사장(행사장 와이파이)이면 현장 방송을, 아니면 인터넷 방송을 저절로 골라 앱 안에서 틉니다.
//   현장 방송: 192.168.8.2:7880/stream (뮤 8비트 1초에 16000, 늦음이 가장 적음) — 행사장 와이파이로만
//   인터넷 방송: LVD 방송 중계 lvd.ada.or.kr:2053 의 /moklok 에서 지금 나가는 방송을 찾아 /mp3 로 틂
//     하나면 바로 틀고, 여럿이면 첫 화면에 고를 단추를 띄움. 없으면 알리고, 방송이 열리거나 행사장에 들어서면 저절로 틂
//   인터넷으로 듣다가 행사장 와이파이에 붙으면 현장 방송으로 바꿈(더 빠름)
// 2.0.0 그대로: 화면을 잠가도 들림(뒤에서 소리, 잠금 화면 멈춤·재생), 전화가 오면 멈췄다가 끝나면 다시, 행사장을 벗어나면 한 번만 알림
import Foundation
import AVFoundation
import MediaPlayer
import UIKit

struct InteonetBang: Identifiable, Equatable {
    let id: String        // 중계 방 번호
    let jemok: String
    let who: String
    let cho: Int          // 시작한 지 몇 초
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

    @Published private(set) var kyeojim = false
    @Published private(set) var sangtae = "듣기가 꺼져 있습니다."
    @Published private(set) var gorgi: [InteonetBang] = []   // 2.2.0 인터넷 방송이 여럿일 때 고를 것
    var mal: ((String) -> Void)?
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
    private var namjin = Data()
    private var gameun = false   // 엔진을 붙였는가
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
            try s.setCategory(.playback, mode: .spokenAudio, options: [])
            try s.setActive(true)
        } catch { }
        if !gameun {
            engine.attach(player)
            engine.connect(player, to: engine.mainMixerNode, format: fmt)
            gameun = true
        }
        try? engine.start()
        player.play()
        jamgeumHwamyeon()
        bakkum("현장영상해설을 찾고 있습니다.", malhal: false)
        butgi()
        dasiTimer?.invalidate()
        dasiTimer = Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] _ in self?.salpigi() }
    }

    func kkeugi(son: Bool) {
        guard kyeojim else { return }
        if son { sonMeomchum = true }
        kyeojim = false
        dasiTimer?.invalidate(); dasiTimer = nil
        task?.cancel(); task = nil
        session?.invalidateAndCancel(); session = nil
        avpKkeugi()
        gil = .eopseum
        gorgi = []
        player.stop()
        engine.stop()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        bakkum("듣기를 멈추었습니다.", malhal: false)
    }

    // MARK: 받기 — 먼저 행사장, 아니면 인터넷

    private var butneunJung = false
    private var majimakBatgi = Date.distantPast

    private func butgi() {
        guard kyeojim, !jamsi, !butneunJung else { return }
        butneunJung = true
        salpinTtae = Date()
        Hyeonjang.salpigi { [weak self] jm in
            guard let s = self else { return }
            guard s.kyeojim, !s.jamsi else { s.butneunJung = false; return }
            guard let jm = jm else { s.inteonetSalpigi(); return }
            s.butneunJung = false
            if case .inteonet = s.gil { s.avpKkeugi() }   // 2.2.0 행사장에 들어서면 현장 방송으로(더 빠름)
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
            guard let u = Hyeonjang.stream else { return }   // 2.4.0 찾아 둔 자리(플린트 2 또는 아이피타임 방식 송출 폰)
            s.namjin = Data()
            s.majimakBatgi = Date()
            let t = ses.dataTask(with: u)
            s.task = t
            t.resume()
            let m = (jm.isEmpty ? "" : jm + " 행사 ") + "현장영상해설에 연결되었습니다."
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
                    s.avpKkeugi(); s.gil = .eopseum
                    s.bakkum("그 현장영상해설 방송이 끝났습니다. 다른 방송을 찾고 있습니다.", malhal: true)
                    return
                }
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
                s.bakkum("인터넷에 닿지 않습니다. 행사장에서는 행사장 와이파이에 붙으면 저절로 들립니다.", malhal: s.cheot)
                s.cheot = false
                return
            }
            let rows = ((j["rows"] as? [[String: Any]]) ?? []).filter { ["live", "bangsong"].contains($0["jong"] as? String ?? "") }
            let l = rows.map { InteonetBang(id: $0["sid"] as? String ?? "", jemok: $0["title"] as? String ?? "", who: $0["who"] as? String ?? "", cho: $0["cho"] as? Int ?? 0) }.filter { !$0.id.isEmpty }
            if l.isEmpty {
                s.gorgi = []
                s.bakkum("지금 나가는 현장영상해설이 없습니다. 방송이 열리거나 행사장에 들어서시면 저절로 들려 드립니다.", malhal: true)
            } else if l.count == 1 {
                s.inteonetGoreum(l[0])
            } else if s.gorgi.map({ $0.id }) != l.map({ $0.id }) {
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
        avpTeulgi(b.id)
        let jm: String = b.jemok.isEmpty ? "" : "\(b.jemok) "
        let w: String = b.who.isEmpty ? "" : " 현장영상해설사 \(b.who)."
        let m = "인터넷으로 \(jm)현장영상해설을 들려 드립니다.\(w)"
        bakkum(m, malhal: true)
        jamgeumHwamyeon()
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
            if task == nil || Date().timeIntervalSince(majimakBatgi) > 6 {
                task?.cancel(); task = nil
                butgi()
            }
        case .inteonet, .eopseum:
            if Date().timeIntervalSince(salpinTtae) >= 10 { butgi() }
        }
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        guard kyeojim, !jamsi, gil == .hyeonjang else { return }
        majimakBatgi = Date()
        eopseumTtae = nil
        namjin.append(data)
        while namjin.count >= 1600 {
            let jogak = namjin.prefix(1600)
            namjin.removeFirst(1600)
            guard let b = AVAudioPCMBuffer(pcmFormat: fmt, frameCapacity: 1600), let ch = b.floatChannelData?[0] else { continue }
            var i = 0
            for v in jogak { ch[i] = Deut.DEC[Int(v)]; i += 1 }
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
                guard let s = self, s.kyeojim else { return }
                try? AVAudioSession.sharedInstance().setActive(true)
                try? s.engine.start()
                s.player.play()
                s.bakkum("다시 들려 드립니다.", malhal: true)
                if case .inteonet(let sid) = s.gil { s.avpTeulgi(sid) }   // 생방송이라 다시 붙어 지금 것부터
                else { s.butgi() }
            }
        }
    }

    @objc private func gilBakkwim(_ n: Notification) {
        // 이어폰을 빼면 아이폰이 멈춤 — 엔진을 다시 돌림
        guard kyeojim, !jamsi else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
            guard let s = self, s.kyeojim else { return }
            if !s.engine.isRunning { try? s.engine.start(); s.player.play() }
            if case .inteonet = s.gil, let p = s.avp, p.timeControlStatus != .playing { p.play() }
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
