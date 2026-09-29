// 소리 엔진 — 길눈이 하는 모든 말과 알림 소리는 여기 한 곳을 거칩니다.
// ① 말을 한 줄로 세워 차례대로 내보냄(겹치지 않음) ② 경고는 줄 맨 앞으로, 말소리를 꺼도 늘 말함
// ③ 말하는 동안만 다른 소리(음악·라디오)를 잠시 낮추고, 끝나면 되돌림 — 길눈 자체 방송은 소리를 줄이고 경고만 멈춤(2.8.0) ④ 폰이 잠겨도 말함(UIBackgroundModes audio)
// ⑤ 전화가 오면 멈췄다가 끝나면 이어 말함 ⑥ 같은 말을 3초 안에 되풀이하지 않음
import AVFoundation
import UIKit

enum MalGeup: Int {
    case jeongbo = 0   // 알려 드리는 말 — 줄이 길면 먼저 버림
    case annae = 1     // 안내
    case gyeonggo = 2  // 경고 — 줄 맨 앞, 끌 수 없음
}

enum SoriJong {
    case hwaksin   // 제대로 가고 있음
    case gyeonggo  // 조심
    case dochak    // 도착
    case deutgi    // 말로 하기 — 이제 말씀하십시오
    case ttaeng    // 말로 하기 — 대답을 마치고 실행에 들어감
    // 2.10.0 점지도 따라 걷기(웹 hwaksin.js 와 같은 소리)
    case jeomOk    // 점지도 위를 제대로 디딤 — 맑고 높은 띵
    case bikyeo    // 반 걸음 비켜남 — 가운데 소리
    case beoseo    // 한 걸음 벗어남 — 낮은 두 소리
    case doraom    // 점지도 위로 돌아옴 — 오르는 두 소리
}

final class SoriEngine: NSObject, ObservableObject, AVSpeechSynthesizerDelegate {
    static let shared = SoriEngine()

    private struct Mal {
        let t: String
        let geup: MalGeup
    }

    private let synth = AVSpeechSynthesizer()
    private var jul: [Mal] = []
    private var jigeumGeup: MalGeup?
    private var naerigiJakeop: DispatchWorkItem?
    private var dunmal: [String: Date] = [:]
    private var player: AVAudioPlayer?
    private var meomchum = false
    /// 화상통화 중 — 길눈 말소리를 내지 않음(통화 소리 보호, 2026-09-11 이사장님 지시: 통화 중에는 화면 글로만)
    var tonghwaJung = false
    private(set) var majimak = ""
    private(set) var malHaneunSu = 0

    // MARK: 말로 하기와 함께 쓰기 (2.5.0)
    /// "하이 길눈"을 기다리는 중 — 말을 마친 뒤 소리 세션을 닫지 않음(말로 하기가 이어서 씀)
    var deutgiKyeojim = false
    /// 명령을 듣는 동안 — 다른 말은 줄에 세워 두고(경고만 빼고), 듣기가 끝나면 이어서 냄
    var myeongryeongDeutneunJung = false {
        didSet {
            let t = myeongryeongDeutneunJung
            DispatchQueue.main.async { BangsongEngine.shared.deutgiMeomchum(t) }   // 2.8.0 명령을 듣는 동안 방송 멈춤
            if !t { DispatchQueue.main.async { self.naeboenda() } }
        }
    }
    /// 길눈이 막 말을 시작하려 할 때 — 말로 하기가 마이크를 잠시 닫음
    var malSijakHook: (() -> Void)?
    /// 2.12.2 막 하려는 말(알림 소리면 빈칸) — 말로 하기가 들을지 쉴지 가르는 데 씀
    private(set) var hanunMal = ""
    private var kkeutJul: [() -> Void] = []
    /// 2.6.0 최근에 한 말(이어폰 이전 단추 — 앞 안내)
    private var malGirok: [String] = []
    private var apJari: Int?   // 2.12.0 앞 안내를 거듭 누르면 한 말씩 더 앞으로

    /// 앞 안내 — 방금 한 말의 앞 말을 다시(거듭 누르면 더 앞의 말)
    func apDeutgi() {
        DispatchQueue.main.async {
            guard !self.malGirok.isEmpty else { return }
            let i = max(0, (self.apJari ?? (self.malGirok.count - 1)) - 1)
            self.apJari = i
            let t = self.malGirok[i]
            guard !t.isEmpty else { return }
            self.dunmal[t] = nil
            self.jul.insert(Mal(t: "앞 안내. " + t, geup: .annae), at: 0)
            self.naeboenda()
        }
    }

    /// 말하고 있거나 줄에 선 말이 있는가
    var bappeum: Bool { synth.isSpeaking || !jul.isEmpty }

    /// 지금 줄에 선 말까지 다 하고 나면 한 번 부름(말이 없으면 곧바로)
    func kkeutnamyeon(_ f: @escaping () -> Void) {
        DispatchQueue.main.async {
            if !self.synth.isSpeaking && self.jul.isEmpty && !(self.player?.isPlaying ?? false) {
                f()
            } else {
                self.kkeutJul.append(f)
            }
        }
    }

    private func kkeutBoda() {
        guard !synth.isSpeaking, jul.isEmpty, !(player?.isPlaying ?? false), !kkeutJul.isEmpty else { return }
        let fs = kkeutJul
        kkeutJul = []
        fs.forEach { $0() }
    }

    override init() {
        super.init()
        synth.delegate = self
        NotificationCenter.default.addObserver(self, selector: #selector(kkeunkim(_:)),
                                               name: AVAudioSession.interruptionNotification, object: nil)
        // 2.12.0 끊김이 끝났다는 알림이 오지 않아도 앱으로 돌아오면 다시 말함
        NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
            guard let self = self, self.meomchum else { return }
            self.meomchum = false
            Girok.shared.namgi("sori_kkeunkim", ["dan": "dasi"])
            self.naeboenda()
        }
    }

    // MARK: 말하기

    /// 길눈이 하는 모든 말은 여기를 거칩니다.
    func mal(_ t: String, _ geup: MalGeup = .annae) {
        let t = t.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return }
        DispatchQueue.main.async {
            if self.tonghwaJung {
                self.majimak = t
                if geup == .gyeonggo { Jindong.hagi("long") }   // 2.12.0 통화 중 경고는 진동으로
                return
            }
            if geup != .gyeonggo && !Seoljeong.shared.malKyeojim {
                self.majimak = t
                return
            }
            let now = Date()
            if let d = self.dunmal[t], now.timeIntervalSince(d) < 3 { return }
            self.dunmal[t] = now
            if self.dunmal.count > 60 {
                self.dunmal = self.dunmal.filter { now.timeIntervalSince($0.value) < 10 }
            }
            let m = Mal(t: t, geup: geup)
            if geup == .gyeonggo {
                // 2.12.0 끊김이 끝났다는 알림을 못 받아 멈춰 있으면 — 경고는 소리 자리를 다시 잡아 봄
                if self.meomchum {
                    do { try AVAudioSession.sharedInstance().setActive(true); self.meomchum = false } catch {}
                }
                self.jul.insert(m, at: 0)
                if self.synth.isSpeaking && self.jigeumGeup != .gyeonggo {
                    self.synth.stopSpeaking(at: .word)   // 끊긴 뒤 didCancel 에서 경고부터 냄
                    return
                }
            } else if geup == .annae && Seoljeong.shared.malJaru && self.synth.isSpeaking && self.jigeumGeup != .gyeonggo {
                // 2.9.0 말 자르고 새로 말하기(설정에서 켬) — 하던 말을 끊고 새 안내부터
                self.jul.insert(m, at: 0)
                self.synth.stopSpeaking(at: .word)
                return
            } else {
                if self.jul.count >= 4, let i = self.jul.firstIndex(where: { $0.geup == .jeongbo }) {
                    self.jul.remove(at: i)
                }
                self.jul.append(m)
            }
            self.naeboenda()
        }
    }

    /// 방금 한 말 다시 듣기
    func dasiDeutgi() {
        DispatchQueue.main.async {
            guard !self.majimak.isEmpty else { return }
            let t = self.majimak
            self.dunmal[t] = nil
            self.jul.insert(Mal(t: t, geup: .annae), at: 0)
            self.naeboenda()
        }
    }

    /// 줄에 선 말을 모두 거두고 지금 말도 멈춤(경고는 남김)
    func modu_geodugi() {
        DispatchQueue.main.async {
            self.jul.removeAll { $0.geup != .gyeonggo }
            if self.jigeumGeup != .gyeonggo { self.synth.stopSpeaking(at: .immediate) }
        }
    }

    private func naeboenda() {
        guard !meomchum, !synth.isSpeaking, !jul.isEmpty else { return }
        if myeongryeongDeutneunJung && jul.first?.geup != .gyeonggo { return }
        hanunMal = jul.first?.t ?? ""
        malSijakHook?()
        let m = jul.removeFirst()
        naerigiJakeop?.cancel()
        sesyeonKyeogi()
        BangsongEngine.shared.malSijak(m.geup)   // 2.8.0 방송 소리를 작게(경고는 멈춤)
        let u = AVSpeechUtterance(string: m.t)
        u.voice = moksori()
        u.rate = Seoljeong.shared.malBbareugi
        u.preUtteranceDelay = 0.05
        jigeumGeup = m.geup
        majimak = m.t
        if !m.t.hasPrefix("앞 안내. ") {   // 다시 들려 드린 앞 안내는 기록에 넣지 않음
            malGirok.append(m.t)
            if malGirok.count > 12 { malGirok.removeFirst(malGirok.count - 12) }
            apJari = nil
        }
        if m.t.count > 2 { WatchLink.shared.malBonae(m.t) }
        malHaneunSu += 1
        synth.speak(u)
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        DispatchQueue.main.async { self.daeum() }
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        DispatchQueue.main.async { self.daeum() }
    }

    private func daeum() {
        jigeumGeup = nil
        if jul.isEmpty {
            sesyeonNaerigi(1.0)
            bangsongDoedollim()
            kkeutBoda()
        } else {
            naeboenda()
            // 명령을 듣는 중이라 줄에 세워 둔 말은 나중에 — 기다리던 일은 그대로 둠
        }
    }

    /// 2.8.0 말을 다 마치면 방송 소리를 되돌림
    private func bangsongDoedollim() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            if self.synth.isSpeaking || !self.jul.isEmpty || (self.player?.isPlaying ?? false) { return }
            BangsongEngine.shared.malKkeut()
        }
    }

    // MARK: 알림 소리

    func sori(_ j: SoriJong) {
        if tonghwaJung { return }
        let data: Data
        var gil = 0.3
        switch j {
        case .hwaksin: data = SoriEngine.wav([(880, 0.08)])
        case .gyeonggo: data = SoriEngine.wav([(440, 0.15), (0, 0.08), (440, 0.15)]); gil = 0.4
        case .dochak: data = SoriEngine.wav([(660, 0.12), (0, 0.04), (880, 0.12), (0, 0.04), (1100, 0.22)]); gil = 0.6
        case .deutgi: data = SoriEngine.wav([(660, 0.07), (0, 0.03), (990, 0.1)])
        case .ttaeng: data = SoriEngine.wav([(1320, 0.2)])
        case .jeomOk: data = SoriEngine.wav([(1320, 0.07)]); gil = 0.1
        case .bikyeo: data = SoriEngine.wav([(700, 0.12)]); gil = 0.15
        case .beoseo: data = SoriEngine.wav([(330, 0.2), (0, 0.07), (247, 0.26)]); gil = 0.55
        case .doraom: data = SoriEngine.wav([(880, 0.08), (0, 0.04), (1320, 0.1)]); gil = 0.25
        }
        DispatchQueue.main.async {
            // 명령을 듣는 동안에는 걷는 안내의 작은 소리를 내지 않음(마이크를 지킴) — 경고와 말로 하기 소리는 냄
            if self.myeongryeongDeutneunJung && j != .gyeonggo && j != .deutgi && j != .ttaeng { return }
            // 2.12.0 짧은 확신음은 하이 길눈 듣기를 닫지 않고, 듣는 중이면 소리 자리도 건드리지 않음
            let jjalbeun = (j == .jeomOk || j == .bikyeo || j == .hwaksin)
            let deutneun = jjalbeun && MalDeutgi.shared.dolgoItda
            if j != .deutgi && j != .ttaeng && !jjalbeun { self.hanunMal = ""; self.malSijakHook?() }
            if deutneun {
                self.player = try? AVAudioPlayer(data: data)
                self.player?.volume = 0.8
                self.player?.play()
                return
            }
            self.naerigiJakeop?.cancel()
            self.sesyeonKyeogi()
            BangsongEngine.shared.malSijak(j == .gyeonggo ? .gyeonggo : .annae)
            self.player = try? AVAudioPlayer(data: data)
            self.player?.volume = 0.8
            self.player?.play()
            if !self.synth.isSpeaking && self.jul.isEmpty { self.sesyeonNaerigi(1.5) }
            DispatchQueue.main.asyncAfter(deadline: .now() + gil + 0.1) { self.kkeutBoda(); self.bangsongDoedollim() }
        }
    }

    // MARK: 소리 설정(세션)

    /// 말할 때만 다른 소리를 낮추고 켬
    private func sesyeonKyeogi() {
        // 2.12.1 하이 길눈 마이크가 열려 있으면 소리 자리를 바꾸지 않음(바꾸면 마이크가 멎고, 잠긴 폰에서는 다시 못 엶)
        if MalDeutgi.shared.dolgoItda && AVAudioSession.sharedInstance().category == .playAndRecord {
            try? AVAudioSession.sharedInstance().setActive(true)
            return
        }
        // 2.6.0 안내 중 이어폰 단추를 받을 때는 소리 자리를 섞지 않고 그대로 쥠
        if RemoteDanchu.shared.kyeojim { RemoteDanchu.shared.sesyeonJapgi(); return }
        // 2.8.0 길눈 방송(음악·라디오·TV·기사)이 소리 자리를 쥐고 있으면 그대로 두고 방송 소리만 줄임
        if BangsongEngine.shared.itda {
            let s0 = AVAudioSession.sharedInstance()
            if s0.category != .playback && s0.category != .playAndRecord { BangsongEngine.shared.sesyeonJapgi() } else { try? s0.setActive(true) }
            return
        }
        let s = AVAudioSession.sharedInstance()
        do {
            try s.setCategory(.playback, mode: .voicePrompt,
                              options: [.duckOthers, .interruptSpokenAudioAndMixWithOthers])
            try s.setActive(true)
        } catch {
            Girok.shared.namgi("sori_oryu", ["dan": "kyeogi", "code": (error as NSError).code])
        }
    }

    /// 다 말하고 나면 다른 소리를 되돌려 줌
    private func sesyeonNaerigi(_ dwi: Double) {
        let w = DispatchWorkItem { [weak self] in
            guard let self = self else { return }
            if self.synth.isSpeaking || !self.jul.isEmpty || (self.player?.isPlaying ?? false) { return }
            if self.deutgiKyeojim || self.myeongryeongDeutneunJung || MalDeutgi.shared.dolgoItda { return }   // 말로 하기가 이어서 씀(2.12.1 마이크가 열려 있으면 그대로)
            if RemoteDanchu.shared.kyeojim { return }   // 이어폰 단추를 받는 중
            if BangsongEngine.shared.itda { return }     // 2.8.0 길눈 방송이 소리 자리를 씀
            do {
                try AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
            } catch {
                Girok.shared.namgi("sori_oryu", ["dan": "naerigi", "code": (error as NSError).code])
            }
        }
        naerigiJakeop = w
        DispatchQueue.main.asyncAfter(deadline: .now() + dwi, execute: w)
    }

    @objc private func kkeunkim(_ n: Notification) {
        guard let v = n.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              let ty = AVAudioSession.InterruptionType(rawValue: v) else { return }
        DispatchQueue.main.async {
            if ty == .began {
                self.meomchum = true
                Girok.shared.namgi("sori_kkeunkim", ["dan": "sijak"])
            } else {
                self.meomchum = false
                Girok.shared.namgi("sori_kkeunkim", ["dan": "kkeut"])
                self.naeboenda()
            }
        }
    }

    // MARK: 목소리

    static func hangukMoksori() -> [AVSpeechSynthesisVoice] {
        AVSpeechSynthesisVoice.speechVoices()
            .filter { $0.language == "ko-KR" }
            .sorted { $0.quality.rawValue > $1.quality.rawValue }
    }

    static func moksoriIreum(_ v: AVSpeechSynthesisVoice?) -> String {
        guard let v = v else { return "기본 목소리" }
        switch v.quality {
        case .premium: return v.name + " (가장 좋은 음질)"
        case .enhanced: return v.name + " (좋은 음질)"
        default: return v.name
        }
    }

    func moksori() -> AVSpeechSynthesisVoice? {
        let id = Seoljeong.shared.moksoriId
        if !id.isEmpty, let v = AVSpeechSynthesisVoice(identifier: id) { return v }
        return SoriEngine.hangukMoksori().first ?? AVSpeechSynthesisVoice(language: "ko-KR")
    }

    // MARK: 소리 만들기

    /// (높이 Hz, 길이 초) 조각들로 짧은 소리를 그 자리에서 만듭니다. 높이 0 은 쉼.
    static func wav(_ jogak: [(Double, Double)]) -> Data {
        let sr = 22050.0
        var samples: [Int16] = []
        for (f, len) in jogak {
            let n = Int(sr * len)
            for i in 0..<n {
                if f == 0 { samples.append(0); continue }
                let gajangjari = Double(min(i, n - i)) / (sr * 0.01)
                let env = min(1.0, gajangjari)
                let v = sin(2 * Double.pi * f * Double(i) / sr) * 0.5 * env
                samples.append(Int16(v * 32767))
            }
        }
        var d = Data()
        func u32(_ v: UInt32) {
            var x = v.littleEndian
            withUnsafeBytes(of: &x) { d.append(contentsOf: $0) }
        }
        func u16(_ v: UInt16) {
            var x = v.littleEndian
            withUnsafeBytes(of: &x) { d.append(contentsOf: $0) }
        }
        let bytes = UInt32(samples.count * 2)
        d.append(contentsOf: Array("RIFF".utf8)); u32(36 + bytes); d.append(contentsOf: Array("WAVE".utf8))
        d.append(contentsOf: Array("fmt ".utf8)); u32(16); u16(1); u16(1)
        u32(UInt32(sr)); u32(UInt32(sr) * 2); u16(2); u16(16)
        d.append(contentsOf: Array("data".utf8)); u32(bytes)
        for s in samples { u16(UInt16(bitPattern: s)) }
        return d
    }
}
