// AI점자도서관 앱 — 독서기 (판 0.1.0, 빌드 260930-1)
// 글자책: 문단마다 서버 일꾼이 구운 사람 목소리를 받아 차례로 튼다. 앞 문단을 읽는 동안 뒤 세 문단을 미리 받는다.
// 소리책·동영상: 나스에서 이어 받기로 튼다.
// 화면을 끄거나 주머니에 넣어도 계속 읽고, 이어폰 단추와 잠금 화면으로 멈춤·다음·앞을 쓴다.
import Foundation
import AVFoundation
import MediaPlayer
import CryptoKit

@MainActor
final class Reader: NSObject, ObservableObject, AVAudioPlayerDelegate {
    static let shared = Reader()

    @Published var i: Int = -1
    @Published var title: String = ""
    @Published var kind: String = "geul"
    @Published var modu: Int = 0
    @Published var pos: Int = 0
    @Published var text: String = ""
    @Published var playing = false
    @Published var waiting = false
    @Published var status: String = ""

    // 소리책·동영상
    @Published var player: AVPlayer?
    private var timeObs: Any?

    private var paras: [Int: String] = [:]
    private var pageTasks: [Int: Task<Void, Never>] = [:]
    private var audioTasks: [Int: Task<URL?, Never>] = [:]
    private var audio: AVAudioPlayer?
    private var token = 0
    private var store: Store { Store.shared }

    override init() {
        super.init()
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio, options: [])
        setupRemote()
    }

    // MARK: 책 열기
    func open(i: Int, title: String, kind: String) async {
        if self.i == i { return }
        stop()
        self.i = i; self.title = title; self.kind = kind
        paras = [:]; pageTasks = [:]; audioTasks = [:]; text = ""; modu = 0
        let saved = store.rec(i)
        if kind == "geul" {
            pos = Int(saved?.pos ?? 0)
            if let local = Offline.loadParas(i) {
                for (k, v) in local.enumerated() { paras[k] = v }
                modu = local.count
            } else {
                await loadPage(pos)
            }
            text = paras[pos] ?? ""
            store.remember(i: i, t: title, kind: kind, pos: Double(pos), modu: modu)
        } else {
            let p = AVPlayer(playerItem: AVPlayerItem(asset: Offline.asset(i) ?? AVURLAsset(url: API.mediaURL(i), options: ["AVURLAssetHTTPHeaderFieldsKey": ["User-Agent": API.userAgent]])))
            p.automaticallyWaitsToMinimizeStalling = true
            player = p
            let start = saved?.pos ?? 0
            if start > 1 { await p.seek(to: CMTime(seconds: start, preferredTimescale: 600)) }
            timeObs = p.addPeriodicTimeObserver(forInterval: CMTime(seconds: 5, preferredTimescale: 1), queue: .main) { [weak self] t in
                Task { @MainActor in
                    guard let self, self.playing else { return }
                    let dur = self.player?.currentItem?.duration.seconds ?? 0
                    self.store.remember(i: self.i, t: self.title, kind: self.kind, pos: t.seconds, modu: dur.isFinite ? Int(dur) : 0)
                }
            }
            NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: p.currentItem, queue: .main) { [weak self] _ in
                Task { @MainActor in
                    guard let self else { return }
                    self.playing = false
                    self.store.remember(i: self.i, t: self.title, kind: self.kind, pos: 0, modu: self.modu, done: true)
                    self.store.say("끝까지 들었습니다.")
                }
            }
        }
        updateNowPlaying()
    }

    // MARK: 글자 받기
    private func pageStart(_ o: Int) -> Int { (o / 60) * 60 }

    private func loadPage(_ o: Int) async {
        let pg = pageStart(o)
        if paras[pg] != nil { return }
        if let t = pageTasks[pg] { await t.value; return }
        let book = i
        let t = Task { [weak self] in
            guard let self else { return }
            do {
                let r = try await API.gul(book, pg)
                guard book == self.i else { return }
                if let m = r.modu { self.modu = m }
                for (k, v) in (r.mun ?? []).enumerated() { self.paras[pg + k] = v }
                if r.ok == false { self.status = r.msg ?? "이 책의 글자를 가져오지 못했습니다." }
            } catch {
                self.status = "글자를 가져오지 못했습니다. 인터넷을 확인해 주십시오."
            }
        }
        pageTasks[pg] = t
        await t.value
    }

    private func para(_ o: Int) async -> String? {
        if paras[o] == nil { await loadPage(o) }
        return paras[o]
    }

    // MARK: 소리 받기(구운 소리는 폰에 보관)
    private func audioFile(_ o: Int) async -> URL? {
        let v = store.voice
        if let f = Offline.audioURL(i, o, v), FileManager.default.fileExists(atPath: f.path) { return f }
        let key = o * 10 + v
        if let t = audioTasks[key] { return await t.value }
        let book = i
        let t = Task<URL?, Never> { [weak self] in
            guard let self, let txt = await self.para(o), !txt.isEmpty else { return nil }
            let cut = String(txt.prefix(600))
            let h = Reader.hash(cut, v)
            let cache = Offline.cacheDir.appendingPathComponent("\(h).mp3")
            if FileManager.default.fileExists(atPath: cache.path) { return cache }
            do {
                let y = try await API.yocheong(cut, voice: v)
                let hh = y.h ?? h
                for n in 0..<90 {
                    if book != self.i { return nil }
                    if let d = try await API.sori(hh) {
                        try d.write(to: cache)
                        return cache
                    }
                    try await Task.sleep(nanoseconds: n < 10 ? 700_000_000 : 1_000_000_000)
                }
            } catch { }
            return nil
        }
        audioTasks[key] = t
        let r = await t.value
        if r == nil { audioTasks[key] = nil }
        return r
    }

    static func hash(_ t: String, _ v: Int) -> String {
        let d = Insecure.SHA1.hash(data: Data("st3|\(v)|\(t)".utf8))
        return d.map { String(format: "%02x", $0) }.joined()
    }

    // MARK: 읽기
    func toggle() {
        if kind != "geul" {
            guard let p = player else { return }
            if playing { p.pause(); playing = false } else { p.play(); p.rate = store.rate; playing = true }
            updateNowPlaying(); return
        }
        if playing { pause() } else { play(at: pos) }
    }

    func pause() {
        token += 1
        audio?.pause(); player?.pause(); Offline.synth.stopSpeaking(at: .immediate)
        playing = false; waiting = false
        updateNowPlaying()
    }

    func stop() {
        token += 1
        audio?.stop(); audio = nil; Offline.synth.stopSpeaking(at: .immediate)
        if let o = timeObs { player?.removeTimeObserver(o) }
        timeObs = nil
        player?.pause(); player = nil
        playing = false; waiting = false
    }

    func play(at o: Int) {
        guard kind == "geul", i >= 0 else { return }
        token += 1
        let my = token
        let target = max(0, modu > 0 ? min(o, modu - 1) : o)
        pos = target
        playing = true
        audio?.stop()
        Task {
            text = await para(target) ?? ""
            store.remember(i: i, t: title, kind: kind, pos: Double(target), modu: modu)
            updateNowPlaying()
            if !Offline.online && Offline.audioURL(i, target, store.voice).map({ FileManager.default.fileExists(atPath: $0.path) }) != true { waiting = false; speakPhone(text, my); return }   // 0.3.0 — 인터넷이 없으면 바로 폰 목소리
            if audioTasks[target * 10 + store.voice] == nil && Offline.audioURL(i, target, store.voice).map({ FileManager.default.fileExists(atPath: $0.path) }) != true {
                waiting = true
            }
            let f = await audioFile(target)
            guard my == token else { return }
            waiting = false
            guard let f else {
                if !Offline.online { speakPhone(text, my); return }   // 0.3.2 — 폰 목소리는 인터넷이 완전히 끊겼을 때만(이사장님: 도서관 목소리가 바뀌면 안 됨)
                playing = false
                status = "소리를 만들지 못했습니다. 잠시 뒤 다시 읽기를 눌러 주십시오."
                store.say(status)
                return
            }
            do {
                let a = try AVAudioPlayer(contentsOf: f)
                a.enableRate = true
                a.rate = store.rate
                a.delegate = self
                audio = a
                try? AVAudioSession.sharedInstance().setActive(true)
                a.play()
                status = ""
                updateNowPlaying()
            } catch {
                playing = false
                status = "소리를 틀지 못했습니다."
            }
            // 뒤 세 문단 미리 받기
            for k in 1...3 where target + k < max(modu, target + 1) {
                let n = target + k
                Task { _ = await self.audioFile(n) }
            }
        }
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in
            guard self.playing else { return }
            if self.pos + 1 < self.modu {
                self.play(at: self.pos + 1)
            } else {
                self.playing = false
                self.store.remember(i: self.i, t: self.title, kind: self.kind, pos: Double(self.pos), modu: self.modu, done: true)
                self.store.say("책을 끝까지 읽었습니다.")
                self.updateNowPlaying()
            }
        }
    }

    func next() {
        if kind != "geul" { skip(30); return }
        guard pos + 1 < max(modu, 1) else { store.say("마지막 문단입니다."); return }
        if playing { play(at: pos + 1) } else { move(to: pos + 1) }
    }
    func prev() {
        if kind != "geul" { skip(-30); return }
        guard pos > 0 else { store.say("첫 문단입니다."); return }
        if playing { play(at: pos - 1) } else { move(to: pos - 1) }
    }
    func fromStart() { if kind == "geul" { if playing { play(at: 0) } else { move(to: 0) } } else { player?.seek(to: .zero) } }
    func move(to o: Int) {
        pos = o
        Task {
            text = await para(o) ?? ""
            store.remember(i: i, t: title, kind: kind, pos: Double(o), modu: modu)
            updateNowPlaying()
        }
    }
    func skip(_ s: Double) {
        guard let p = player else { return }
        let t = max(0, p.currentTime().seconds + s)
        p.seek(to: CMTime(seconds: t, preferredTimescale: 600))
    }
    func applyRate() {
        audio?.rate = store.rate
        if playing { player?.rate = store.rate }
    }
    var mediaPos: Double { player?.currentTime().seconds ?? 0 }

    // MARK: 잠금 화면·이어폰 단추
    private func setupRemote() {
        let c = MPRemoteCommandCenter.shared()
        c.playCommand.addTarget { [weak self] _ in Task { @MainActor in if self?.playing == false { self?.toggle() } }; return .success }
        c.pauseCommand.addTarget { [weak self] _ in Task { @MainActor in if self?.playing == true { self?.toggle() } }; return .success }
        c.togglePlayPauseCommand.addTarget { [weak self] _ in Task { @MainActor in self?.toggle() }; return .success }
        c.nextTrackCommand.addTarget { [weak self] _ in Task { @MainActor in self?.next() }; return .success }
        c.previousTrackCommand.addTarget { [weak self] _ in Task { @MainActor in self?.prev() }; return .success }
    }

    func updateNowPlaying() {
        guard i >= 0 else { MPNowPlayingInfoCenter.default().nowPlayingInfo = nil; return }
        var info: [String: Any] = [MPMediaItemPropertyTitle: title, MPMediaItemPropertyArtist: "AI점자도서관"]
        if kind == "geul" { info[MPMediaItemPropertyAlbumTitle] = "전체 \(modu)문단 가운데 \(pos + 1)번째" }
        info[MPNowPlayingInfoPropertyPlaybackRate] = playing ? Double(store.rate) : 0.0
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    var wichiMal: String {
        if kind == "geul" {
            let pct = modu > 0 ? Int(Double(pos) * 100 / Double(modu)) : 0
            return "전체 \(modu)문단 가운데 \(pos + 1)번째, \(pct)퍼센트"
        }
        let s = Int(mediaPos); return "\(s / 60)분 \(s % 60)초"
    }
}

// MARK: 폰에 내려받기(인터넷 없는 곳에서 듣기)
@MainActor

// 0.3.0 — 인터넷이 없거나 서버 소리를 받지 못하면 폰 목소리로 읽는다(내려받은 글자책을 인터넷 없이)
extension Reader: AVSpeechSynthesizerDelegate {
    func speakPhone(_ t: String, _ my: Int) {
        guard my == token else { return }
        let u = AVSpeechUtterance(string: t.isEmpty ? " " : t)
        u.voice = AVSpeechSynthesisVoice(language: "ko-KR")
        u.rate = min(AVSpeechUtteranceMaximumSpeechRate, max(AVSpeechUtteranceMinimumSpeechRate, AVSpeechUtteranceDefaultSpeechRate * store.rate))
        Offline.speakToken = my
        Offline.synth.delegate = self
        try? AVAudioSession.sharedInstance().setActive(true)
        if !Offline.online && !Offline.saidPhone { Offline.saidPhone = true; store.say("인터넷이 없어 폰 목소리로 읽습니다.") }
        status = ""
        Offline.synth.speak(u)
        updateNowPlaying()
    }
    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor in
            guard Offline.speakToken == self.token, self.playing else { return }
            if self.pos + 1 < self.modu {
                self.play(at: self.pos + 1)
            } else {
                self.playing = false
                self.store.remember(i: self.i, t: self.title, kind: self.kind, pos: Double(self.pos), modu: self.modu, done: true)
                self.store.say("책을 끝까지 읽었습니다.")
                self.updateNowPlaying()
            }
        }
    }
}

// 0.4.0 — 재생 위치(퍼센트), 앞으로 30초와 뒤로 30초
extension Reader {
    var gulChoPerMundan: Double {
        let ls = paras.values.map { Double($0.count) }
        let avg = ls.isEmpty ? 120 : ls.reduce(0, +) / Double(ls.count)
        return max(2, avg / (7.0 * Double(store.rate)))
    }
    var jeonche: Double {
        if kind == "geul" { return Double(max(modu, 1)) * gulChoPerMundan }
        let d = player?.currentItem?.duration.seconds ?? 0
        return d.isFinite ? d : 0
    }
    var jigeum: Double {
        if kind == "geul" { return Double(pos) * gulChoPerMundan }
        let c = player?.currentTime().seconds ?? 0
        return c.isFinite ? c : 0
    }
    var peosenteu: Double { let t = jeonche; return t > 0 ? min(100, max(0, jigeum / t * 100)) : 0 }
    static func sigan(_ s: Double) -> String {
        let n = Int(max(0, s)); let h = n / 3600, m = (n % 3600) / 60, c = n % 60
        return h > 0 ? "\(h)시간 \(m)분" : (m > 0 ? "\(m)분 \(c)초" : "\(c)초")
    }
    var wichiPeosenteuMal: String {
        "전체 " + (kind == "geul" ? "약 " : "") + Reader.sigan(jeonche) + " 가운데 " + Reader.sigan(jigeum) + ", " + String(Int(peosenteu.rounded())) + "퍼센트"
    }
    func gaPeosenteu(_ p: Double) {
        let q = min(100, max(0, p))
        if kind == "geul" {
            let o = min(max(modu - 1, 0), Int(Double(modu) * q / 100))
            if playing { play(at: o) } else { move(to: o) }
        } else if let pl = player {
            pl.seek(to: CMTime(seconds: jeonche * q / 100, preferredTimescale: 600))
        }
        objectWillChange.send()
    }
    func gaCho(_ s: Double) {
        if kind != "geul" { skip(s); objectWillChange.send(); return }
        let n = max(1, Int((abs(s) / gulChoPerMundan).rounded()))
        let o = s > 0 ? min(max(modu - 1, 0), pos + n) : max(0, pos - n)
        if o == pos { store.say(s > 0 ? "마지막 문단입니다." : "첫 문단입니다."); return }
        if playing { play(at: o) } else { move(to: o) }
    }
}
