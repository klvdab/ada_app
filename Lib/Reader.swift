// AI점자도서관 앱 — 독서기 (판 0.1.0, 빌드 260930-1)
// 0.8.0 (261018-L19) 엔터테인먼트 이어 듣기·이어 보기 — 곡·회가 끝나면 듣는 방식대로 다음 것을 저절로(곡 하나 끝나면 멈추던 것, 이사장님 지적·승인 「1」), 이어폰·잠금 화면 다음 곡, 다음 곡 미리 받기
// 0.7.1 (261014-L17) 앱이 점점 무거워지고 멈춤(이사장님 지적) — 미리 받기를 세 문단 차례로, 묻기 줄이기, 멀어진 미리 받기는 그만둠(이사장님 승인 23시 17분 「1」)
// 0.6.3 (261014-L15) 이용 기록 — 책 열기, 소리를 5초 넘게 기다림, 소리를 못 만듦, 글자를 못 가져옴을 LibGirok 에 남김(책 이름·내용은 남기지 않음, 이사장님 승인)
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
    private var wons: [Int: String] = [:]   // 0.6.1 (261012) 한자음 읽기 — 문단 원문(음과 원문 함께 볼 때)
    @Published var won: String = ""
    @Published var hjgal = false            // 0.6.1 이 책이 한자음 읽기 대상인가(서버가 알려 줌)
    private var pageTasks: [Int: Task<Void, Never>] = [:]
    private var audioTasks: [Int: Task<URL?, Never>] = [:]
    private var audio: AVAudioPlayer?
    private var token = 0
    private var apsu: [Int: Int] = [:]   // 0.6.3 문단마다 앞에 기다리는 문단 수(서버가 알려 줌)
    private var store: Store { Store.shared }
    // 0.8.0 이어 듣기
    var ieoSijak: Int = -1                 // 화면에서 연 첫 작품(이어 들은 뒤 화면에 돌아와도 그 자리로 되돌아가지 않게)
    @Published var aelbeom: String = ""
    private var miri: DaeumResp?
    private var miriUi = -1
    private var miriBang = ""
    private var ieoJung = false
    private var ieoGiNotis: [NSObjectProtocol] = []

    override init() {
        super.init()
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio, options: [])
        setupRemote()
    }

    // MARK: 책 열기
    func open(i: Int, title: String, kind: String, cheoum: Bool = false, ieo: Bool = false) async {
        if self.i == i { return }
        stop()
        self.i = i; self.title = title; self.kind = kind
        miri = nil; miriUi = -1
        if !ieo { ieoSijak = i; aelbeom = "" }
        LibGirok.shared.namgi("lib_chaek_yeolgi", ["kind": kind, "ent": API.isEnt(i), "naeryeo": Offline.loadParas(i) != nil || Offline.asset(i) != nil])   // 0.6.3 이용 기록
        paras = [:]; pageTasks = [:]; audioTasks = [:]; text = ""; modu = 0; wons = [:]; won = ""; hjgal = false
        if kind == "geul" { await store.gulPan4Omgigi(i) }   // 0.5.2 (261012) 글 판 4 — 옛 판 자리·책갈피를 새 판 자리로(처음 열 때 한 번)
        let saved = store.rec(i)
        // 0.7.1 (261014-L17) 드라마가 나오다 멈췄다 함(이사장님 0시 21분) — 「책 읽는 소리」(spokenAudio) 설정은 보이스오버가 말할 때 소리를 멈춤.
        // 소리책·동영상·엔터테인먼트는 「영화 소리」로 틀어 멈추지 않게 하고, 글자책 낭독은 그대로 둠
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: kind == "geul" ? .spokenAudio : .moviePlayback, options: [])
        if kind == "geul" {
            pos = Int(saved?.pos ?? 0)
            if let local = Offline.loadParas(i) {
                for (k, v) in local.enumerated() { paras[k] = v }
                modu = local.count
            } else {
                await loadPage(pos)
            }
            text = paras[pos] ?? ""; won = wons[pos] ?? ""
            store.remember(i: i, t: title, kind: kind, pos: Double(pos), modu: modu)
        } else {
            let p = AVPlayer(playerItem: AVPlayerItem(asset: Offline.asset(i) ?? AVURLAsset(url: API.mediaURL(i), options: ["AVURLAssetHTTPHeaderFieldsKey": ["User-Agent": API.userAgent]])))
            p.automaticallyWaitsToMinimizeStalling = true
            player = p
            let start = cheoum ? 0 : (saved?.pos ?? 0)
            if start > 1 { await p.seek(to: CMTime(seconds: start, preferredTimescale: 600)) }
            timeObs = p.addPeriodicTimeObserver(forInterval: CMTime(seconds: 5, preferredTimescale: 1), queue: .main) { [weak self] t in
                Task { @MainActor in
                    guard let self, self.playing else { return }
                    let dur = self.player?.currentItem?.duration.seconds ?? 0
                    self.store.remember(i: self.i, t: self.title, kind: self.kind, pos: t.seconds, modu: dur.isFinite ? Int(dur) : 0)
                }
            }
            // 0.7.1 멈춤 까닭을 이용 기록에 — 소리가 모자라 멈춤(stalled)과 다른 소리에 끊김(전화·알림 등). 끊김이 끝나 이어 들어도 되면 저절로 이어 틂
            NotificationCenter.default.addObserver(forName: .AVPlayerItemPlaybackStalled, object: p.currentItem, queue: .main) { [weak self] _ in
                Task { @MainActor in
                    guard let self else { return }
                    LibGirok.shared.namgi("lib_sori_meomchum", ["kka": "moja", "ent": API.isEnt(self.i), "cho": Int(self.player?.currentTime().seconds ?? 0)])
                }
            }
            ieoGiNotis.forEach { NotificationCenter.default.removeObserver($0) }; ieoGiNotis = []
            ieoGiNotis.append(NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] n in
                let ty = (n.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt).flatMap { AVAudioSession.InterruptionType(rawValue: $0) }
                let op = (n.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt).map { AVAudioSession.InterruptionOptions(rawValue: $0) } ?? []
                Task { @MainActor in
                    guard let self, self.kind != "geul", let pl = self.player else { return }
                    if ty == .began { LibGirok.shared.namgi("lib_sori_meomchum", ["kka": "kkeunkim", "ent": API.isEnt(self.i)]) }
                    if ty == .ended && op.contains(.shouldResume) && self.playing { try? AVAudioSession.sharedInstance().setActive(true); pl.play(); pl.rate = self.store.rate }
                }
            })
            NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: p.currentItem, queue: .main) { [weak self] _ in
                Task { @MainActor in
                    guard let self else { return }
                    self.store.remember(i: self.i, t: self.title, kind: self.kind, pos: 0, modu: self.modu, done: true)
                    if API.isEnt(self.i) && self.store.ieo != "han" {   // 0.8.0 이어 듣기
                        if self.store.ieo == "ban" { self.player?.seek(to: .zero); self.player?.play(); self.player?.rate = self.store.rate; return }
                        self.ieoGa(1); return
                    }
                    self.playing = false
                    self.store.say("끝까지 들었습니다.")
                }
            }
            if API.isEnt(i) { ieoMiri() }
        }
        updateNowPlaying()
    }

    // MARK: 0.8.0 이어 듣기·이어 보기(엔터테인먼트)
    private func ieoBang() -> String { let s = store.ieo; return (s == "han" || s == "ban") ? "ap" : s }
    /// 다음 것을 미리 알아 두고, 소리를 미리 열어 두어 곡 사이 기다림을 줄인다
    func ieoMiri() {
        let ui = i, b = store.ieo
        guard API.isEnt(ui), b == "ap" || b == "gal" else { return }
        Task {
            guard let d = try? await API.daeum(ui, b, dwi: false), d.ok, self.i == ui else { return }
            self.miri = d; self.miriUi = ui; self.miriBang = b
            if let ni = d.i, Offline.asset(ni) == nil {
                let a = AVURLAsset(url: API.mediaURL(ni), options: ["AVURLAssetHTTPHeaderFieldsKey": ["User-Agent": API.userAgent]])
                _ = try? await a.load(.isPlayable)
            }
        }
    }
    /// b = 1 다음, -1 앞. 듣는 방식대로 다음(앞) 곡·회를 열어 바로 튼다
    func ieoGa(_ b: Int) {
        guard API.isEnt(i), !ieoJung else { return }
        ieoJung = true
        let ui = i
        Task {
            defer { self.ieoJung = false }
            let bang = ieoBang()
            var d: DaeumResp? = nil
            if b > 0, miriUi == ui, miriBang == bang { d = miri }
            if d == nil { d = try? await API.daeum(ui, bang, dwi: b < 0) }
            guard let d, d.ok, let ni = d.i else {
                if b > 0 && player?.timeControlStatus != .playing { playing = false }
                store.say(d?.msg ?? "다음 것을 받지 못했습니다. 인터넷을 확인해 주십시오.")
                updateNowPlaying(); return
            }
            if store.gogmal {
                let ap = d.sae_aelbeom == true ? (b > 0 ? "다음 앨범, " : "앞 앨범, ") + (d.aelbeom ?? "") + ", " : (b > 0 ? "다음, " : "앞, ")
                store.say(ap + (d.t ?? ""))
            }
            LibGirok.shared.namgi("lib_ieo", ["bang": bang, "dwi": b < 0])
            await open(i: ni, title: d.t ?? "", kind: d.k == "yeongsang" ? "yeongsang" : "sori", cheoum: true, ieo: true)
            aelbeom = d.aelbeom ?? ""
            if !playing { toggle() }
            updateNowPlaying()
        }
    }

    // MARK: 글자 받기
    // 0.6.1 (261012) 한자음 읽기 바꾸기 — 음만 듣기 → 음과 원문 함께 보기 → 꺼짐 → 음만 듣기 (이사장님 승인 10시 53분 「1」)
    func hanjaeumBakkugi() {
        let sae = [1: 2, 2: 0, 0: 1][store.hanjaeum] ?? 1
        store.hanjaeum = sae; store.save()
        let dasi = playing
        if dasi { pause() }
        paras = [:]; wons = [:]; pageTasks = [:]; audioTasks = [:]
        let o = pos
        Task { @MainActor [weak self] in
            guard let self else { return }
            await self.loadPage(o)
            self.text = self.paras[o] ?? ""; self.won = self.wons[o] ?? ""
            self.store.say("한자음 읽기, " + Store.hanjaeumIreum(sae))
            if dasi { self.play(at: o) }
        }
    }

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
                for (k, v) in (r.won ?? []).enumerated() { self.wons[pg + k] = v }   // 0.6.1
                if let g = r.hjgal { self.hjgal = g }
                if r.ok == false { self.status = r.msg ?? "이 책의 글자를 가져오지 못했습니다."; LibGirok.shared.namgi("lib_geulja_silpae", ["msg": String((r.msg ?? "").prefix(60))]) }   // 0.6.3 이용 기록
            } catch {
                self.status = "글자를 가져오지 못했습니다. 인터넷을 확인해 주십시오."
                LibGirok.shared.namgi("lib_geulja_silpae", ["e": String(error.localizedDescription.prefix(80))])   // 0.6.3 이용 기록
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
                if let a = y.ap { self.apsu[o] = a }   // 0.6.3
                let hh = y.h ?? h
                // 0.7.1 (261014-L17) 묻기 줄이기 — 4분까지 기다리되, 처음 여섯 번만 0.7초마다 묻고 그 뒤로는 앞에 기다리는 문단 수만큼 느긋하게(1~3초).
                // 미리 받던 문단은 듣는 자리에서 멀어지면(건너뛰기·뒤로) 바로 그만둠 — 나스가 받는 물음이 한 분당 1초 일곱 번에서 한두 번으로 줆
                let kkeut = Date().addingTimeInterval(240)
                var n = 0
                while Date() < kkeut {
                    if book != self.i { return nil }
                    if o != self.pos && (o < self.pos || o > self.pos + 4) { return nil }
                    if let d = try await API.sori(hh) {
                        try d.write(to: cache)
                        return cache
                    }
                    let ap = Double(self.apsu[o] ?? 0)
                    let swim = n < 6 ? 0.7 : min(3.0, 1.0 + ap * 0.25)
                    n += 1
                    try await Task.sleep(nanoseconds: UInt64(swim * 1_000_000_000))
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
            text = await para(target) ?? ""; won = wons[target] ?? ""
            store.remember(i: i, t: title, kind: kind, pos: Double(target), modu: modu)
            updateNowPlaying()
            if !Offline.online && Offline.audioURL(i, target, store.voice).map({ FileManager.default.fileExists(atPath: $0.path) }) != true { waiting = false; speakPhone(text, my); return }   // 0.3.0 — 인터넷이 없으면 바로 폰 목소리
            if audioTasks[target * 10 + store.voice] == nil && Offline.audioURL(i, target, store.voice).map({ FileManager.default.fileExists(atPath: $0.path) }) != true {
                waiting = true
            }
            // 0.6.3 기다림 알리기 — 10초와 45초가 지나도 소리가 안 오면 한 번씩만 알림(이사장님 승인: 만들지 못했다로 끝내지 않음)
            for (cho, mal) in [(10, "소리를 만들고 있습니다. 잠시만 기다려 주십시오."), (45, "여러 분이 함께 들으셔서 조금 더 걸립니다. 그대로 기다려 주십시오.")] {
                Task { @MainActor [weak self] in
                    try? await Task.sleep(nanoseconds: UInt64(cho) * 1_000_000_000)
                    guard let self, my == self.token, self.waiting else { return }
                    let ap = self.apsu[target] ?? 0
                    self.status = ap > 0 ? mal + " 앞에 \(ap)문단이 기다리고 있습니다." : mal
                    self.store.say(self.status)
                }
            }
            let gidarimSijak = Date(); let gidarimJung = waiting   // 0.6.3 이용 기록 — 소리를 기다린 초
            let f = await audioFile(target)
            guard my == token else { return }
            waiting = false
            let gidarimCho = Int(Date().timeIntervalSince(gidarimSijak))
            if f != nil && gidarimJung && gidarimCho >= 5 { LibGirok.shared.namgi("lib_sori_gidarim", ["cho": gidarimCho, "moksori": store.voice]) }
            if f == nil { LibGirok.shared.namgi("lib_sori_silpae", ["cho": gidarimCho, "moksori": store.voice, "inteonet": Offline.online]) }
            guard let f else {
                if !Offline.online { speakPhone(text, my); return }   // 0.3.2 — 폰 목소리는 인터넷이 완전히 끊겼을 때만(이사장님: 도서관 목소리가 바뀌면 안 됨)
                playing = false
                status = "소리를 만들지 못했습니다. 잠시 뒤 다시 읽기를 눌러 주십시오."   // 0.6.3 — 4분을 기다려도 안 될 때만
                store.say(status)
                return
            }
            if status.hasPrefix("소리를 만들고") || status.hasPrefix("여러 분이") { status = "" }   // 0.6.3
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
            // 0.7.1 (261014-L17) 뒤 세 문단을 차례로 하나씩 미리 받기(전에는 여섯 문단을 한꺼번에 — 나스가 버티지 못함). 다른 문단으로 옮기면 바로 그만둠
            Task { @MainActor [weak self] in
                for k in 1...3 {
                    guard let self, my == self.token, target + k < max(self.modu, target + 1) else { return }
                    _ = await self.audioFile(target + k)
                }
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
            text = await para(o) ?? ""; won = wons[o] ?? ""
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
        c.nextTrackCommand.addTarget { [weak self] _ in Task { @MainActor in guard let self else { return }; if API.isEnt(self.i) { self.ieoGa(1) } else { self.next() } }; return .success }   // 0.8.0 엔터테인먼트는 다음 곡
        c.previousTrackCommand.addTarget { [weak self] _ in Task { @MainActor in guard let self else { return }; if API.isEnt(self.i) { self.ieoGa(-1) } else { self.prev() } }; return .success }
    }

    func updateNowPlaying() {
        guard i >= 0 else { MPNowPlayingInfoCenter.default().nowPlayingInfo = nil; return }
        var info: [String: Any] = [MPMediaItemPropertyTitle: title, MPMediaItemPropertyArtist: "AI점자도서관"]
        if kind == "geul" { info[MPMediaItemPropertyAlbumTitle] = "전체 \(modu)문단 가운데 \(pos + 1)번째" } else if !aelbeom.isEmpty { info[MPMediaItemPropertyAlbumTitle] = aelbeom }
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
