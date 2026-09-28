// 소리 엔진 — 길눈이 하는 모든 말과 알림 소리는 여기 한 곳을 거칩니다.
// ① 말을 한 줄로 세워 차례대로 내보냄(겹치지 않음) ② 경고는 줄 맨 앞으로, 말소리를 꺼도 늘 말함
// ③ 말하는 동안만 다른 소리(음악·라디오)를 잠시 낮추고, 끝나면 되돌림 ④ 폰이 잠겨도 말함(UIBackgroundModes audio)
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

    override init() {
        super.init()
        synth.delegate = self
        NotificationCenter.default.addObserver(self, selector: #selector(kkeunkim(_:)),
                                               name: AVAudioSession.interruptionNotification, object: nil)
    }

    // MARK: 말하기

    /// 길눈이 하는 모든 말은 여기를 거칩니다.
    func mal(_ t: String, _ geup: MalGeup = .annae) {
        let t = t.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return }
        DispatchQueue.main.async {
            if self.tonghwaJung { self.majimak = t; return }
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
                self.jul.insert(m, at: 0)
                if self.synth.isSpeaking && self.jigeumGeup != .gyeonggo {
                    self.synth.stopSpeaking(at: .word)   // 끊긴 뒤 didCancel 에서 경고부터 냄
                    return
                }
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
        let m = jul.removeFirst()
        naerigiJakeop?.cancel()
        sesyeonKyeogi()
        let u = AVSpeechUtterance(string: m.t)
        u.voice = moksori()
        u.rate = Seoljeong.shared.malBbareugi
        u.preUtteranceDelay = 0.05
        jigeumGeup = m.geup
        majimak = m.t
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
        if jul.isEmpty { sesyeonNaerigi(1.0) } else { naeboenda() }
    }

    // MARK: 알림 소리

    func sori(_ j: SoriJong) {
        if tonghwaJung { return }
        let data: Data
        switch j {
        case .hwaksin: data = SoriEngine.wav([(880, 0.08)])
        case .gyeonggo: data = SoriEngine.wav([(440, 0.15), (0, 0.08), (440, 0.15)])
        case .dochak: data = SoriEngine.wav([(660, 0.12), (0, 0.04), (880, 0.12), (0, 0.04), (1100, 0.22)])
        }
        DispatchQueue.main.async {
            self.naerigiJakeop?.cancel()
            self.sesyeonKyeogi()
            self.player = try? AVAudioPlayer(data: data)
            self.player?.volume = 0.8
            self.player?.play()
            if !self.synth.isSpeaking && self.jul.isEmpty { self.sesyeonNaerigi(1.5) }
        }
    }

    // MARK: 소리 설정(세션)

    /// 말할 때만 다른 소리를 낮추고 켬
    private func sesyeonKyeogi() {
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
