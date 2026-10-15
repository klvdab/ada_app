// LVD 방송 아이폰(듣는 앱) — 현장영상해설 듣기 (2.0.0판, 빌드 261015-LI1, 방송클, 이사장님 승인 「1」)
// 행사장 와이파이로만 현장 방송(192.168.8.2:7880/stream, 뮤 8비트 1초에 16000)을 받아 틉니다.
// - 화면을 잠가도 들립니다(앱 설정 「뒤에서 소리」, 잠금 화면에 LVD 방송·멈춤과 재생).
// - 전화가 오거나 걸면 아이폰이 소리를 멈추고, 통화가 끝나면 저절로 다시 들려 드립니다(이사장님 원칙).
// - 행사장을 벗어나면 한 번만 알리고 기다리다가, 다시 들어오면 저절로 이어 들려 드립니다.
import Foundation
import AVFoundation
import MediaPlayer
import UIKit

final class Deut: NSObject, ObservableObject, URLSessionDataDelegate {
    static let shared = Deut()

    @Published private(set) var kyeojim = false
    @Published private(set) var sangtae = "듣기가 꺼져 있습니다."
    var mal: ((String) -> Void)?
    var sonMeomchum = false      // 손으로 멈춤 — 행사장을 벗어날 때까지 저절로 다시 켜지 않음
    private(set) var jemok = ""

    private let engine = AVAudioEngine()
    private let player = AVAudioPlayerNode()
    private let fmt = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: 16000, channels: 1, interleaved: false)!
    private var session: URLSession?
    private var task: URLSessionDataTask?
    private var jamsi = false
    private var beoseona = false
    private var cheot = true
    private var dasiTimer: Timer?
    private var namjin = Data()
    private var gameun = false   // 엔진을 붙였는가

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
        player.stop()
        engine.stop()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        bakkum("듣기를 멈추었습니다.", malhal: false)
    }

    // MARK: 받기

    private var butneunJung = false
    private var majimakBatgi = Date.distantPast

    private func butgi() {
        guard kyeojim, !jamsi, !butneunJung else { return }
        butneunJung = true
        Hyeonjang.salpigi { [weak self] jm in
            guard let s = self else { return }
            s.butneunJung = false
            guard s.kyeojim, !s.jamsi else { return }
            guard let jm = jm else { s.mangEopseum(); return }
            s.jemok = jm
            let c = URLSessionConfiguration.default
            c.allowsCellularAccess = false
            c.timeoutIntervalForRequest = 8
            c.requestCachePolicy = .reloadIgnoringLocalCacheData
            let ses = URLSession(configuration: c, delegate: s, delegateQueue: nil)
            s.session?.invalidateAndCancel()
            s.session = ses
            guard let u = URL(string: "http://\(Hyeonjang.IP):\(Hyeonjang.WEB)/stream") else { return }
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

    private var eopseumTtae: Date?
    private func mangEopseum() {
        let now = Date()
        if eopseumTtae == nil { eopseumTtae = now }
        if !beoseona, let t = eopseumTtae, now.timeIntervalSince(t) > 8 {
            beoseona = true
            bakkum(cheot ? "행사장 와이파이를 찾고 있습니다. 행사장에 들어서면 저절로 들립니다."
                         : "현장 방송에서 벗어났습니다. 다시 들어오시면 저절로 이어서 들려 드립니다.", malhal: true)
        }
    }

    /// 2초마다 — 받는 길이 끊겼으면 다시 붙음
    private func salpigi() {
        guard kyeojim, !jamsi else { return }
        if task == nil || Date().timeIntervalSince(majimakBatgi) > 6 {
            task?.cancel(); task = nil
            butgi()
        }
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        guard kyeojim, !jamsi else { return }
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
            bakkum("전화나 다른 소리 때문에 잠시 멈추었습니다. 끝나면 저절로 다시 들려 드립니다.", malhal: false)
        } else {
            jamsi = false
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
                guard let s = self, s.kyeojim else { return }
                try? AVAudioSession.sharedInstance().setActive(true)
                try? s.engine.start()
                s.player.play()
                s.bakkum("다시 들려 드립니다.", malhal: true)
                s.butgi()
            }
        }
    }

    @objc private func gilBakkwim(_ n: Notification) {
        // 이어폰을 빼면 아이폰이 멈춤 — 엔진을 다시 돌림
        guard kyeojim, !jamsi else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
            guard let s = self, s.kyeojim else { return }
            if !s.engine.isRunning { try? s.engine.start(); s.player.play() }
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
