// 새 알아듣기 부품 — 애플 SpeechAnalyzer·SpeechTranscriber(iOS 26, 2025년 새 부품) — 앱 2.27.0 (빌드 261001-4, 대표님 승인 1)
// 옛 받아쓰기(SFSpeechRecognizer, 2016년)의 후속. 한국어를 지원하고, 인터넷 없이 폰 안에서 돌며, 계속 듣기에 맞게 만들어짐.
// 마이크는 지금처럼 MalDeutgi 가 한 번 열어 둔 것을 그대로 쓰고, 소리 조각만 이 부품으로 넘겨 받음(마이크 자리는 건드리지 않음).
// 안전장치: iOS 26 폰에서 한국어 모델이 준비됐을 때만 쓰고, 세 번 잇달아 막히면 그날은 옛 부품으로 돌아감.
// 말로 하기 설정의 "새 알아듣기 부품"을 끄면 언제든 옛 부품으로.
import Foundation
import Speech
import AVFoundation

@available(iOS 26.0, *)
final class SaeDeutgi {
    /// 한국어 모델이 폰에 준비됨
    private(set) static var junbi = false
    private static var locale: Locale?
    private static var junbiJung = false

    /// 앱을 켤 때 한 번 — 한국어를 지원하는지 보고, 모델이 없으면 뒤에서 받아 둠
    static func junbiHagi() {
        guard !junbi, !junbiJung else { return }
        junbiJung = true
        Task {
            defer { junbiJung = false }
            guard let loc = await SpeechTranscriber.supportedLocale(equivalentTo: Locale(identifier: "ko-KR")) else {
                Girok.shared.namgi("sae_deutgi", ["junbi": "hangugeo_eopseum"])
                return
            }
            let tr = SpeechTranscriber(locale: loc, transcriptionOptions: [], reportingOptions: [.volatileResults], attributeOptions: [])
            do {
                if let rq = try await AssetInventory.assetInstallationRequest(supporting: [tr]) {
                    try await rq.downloadAndInstall()
                }
                locale = loc
                junbi = true
                Girok.shared.namgi("sae_deutgi", ["junbi": "ok"])
            } catch {
                Girok.shared.namgi("sae_deutgi", ["junbi": "mot", "code": (error as NSError).code])
            }
        }
    }

    private let jamgeum = NSLock()
    private var builder: AsyncStream<AnalyzerInput>.Continuation?
    private var mokpyo: AVAudioFormat?
    private var conv: AVAudioConverter?
    private var analyzer: SpeechAnalyzer?
    private var dollimTask: Task<Void, Never>?
    private var kkeutnam = false
    private var hwakjeong = ""   // 굳은 말(앞 마디들)

    /// 듣기 시작 — 알아들은 말(굳은 말 + 지금 말)을 ttui 로, 막히면 oryu 로
    func sijak(ttui: @escaping (String) -> Void, oryu: @escaping () -> Void) {
        guard let loc = SaeDeutgi.locale else { oryu(); return }
        dollimTask = Task { [weak self] in
            let tr = SpeechTranscriber(locale: loc, transcriptionOptions: [], reportingOptions: [.volatileResults], attributeOptions: [])
            let an = SpeechAnalyzer(modules: [tr])
            let fmt = await SpeechAnalyzer.bestAvailableAudioFormat(compatibleWith: [tr])
            let (st, bd) = AsyncStream.makeStream(of: AnalyzerInput.self)
            guard let self = self else { return }
            self.jamgeum.lock()
            if self.kkeutnam { self.jamgeum.unlock(); bd.finish(); return }
            self.builder = bd
            self.mokpyo = fmt
            self.analyzer = an
            self.jamgeum.unlock()
            let batgi = Task { [weak self] in
                do {
                    for try await r in tr.results {
                        guard let s = self else { return }
                        let t = String(r.text.characters)
                        var modu = ""
                        s.jamgeum.lock()
                        if r.isFinal { s.hwakjeong += (s.hwakjeong.isEmpty ? "" : " ") + t; modu = s.hwakjeong }
                        else { modu = s.hwakjeong + (s.hwakjeong.isEmpty ? "" : " ") + t }
                        let kkeut = s.kkeutnam
                        s.jamgeum.unlock()
                        if kkeut { return }
                        ttui(modu)
                    }
                } catch {
                    guard let s = self else { return }
                    s.jamgeum.lock(); let kkeut = s.kkeutnam; s.jamgeum.unlock()
                    if !kkeut { oryu() }
                }
            }
            do {
                try await an.start(inputSequence: st)
            } catch {
                batgi.cancel()
                self.jamgeum.lock(); let kkeut = self.kkeutnam; self.jamgeum.unlock()
                if !kkeut { oryu() }
            }
        }
    }

    /// 마이크 소리 조각을 넣음(소리 줄에서 부름) — 부품이 바라는 소리 모양으로 바꿔서
    func neoki(_ buf: AVAudioPCMBuffer) {
        jamgeum.lock()
        guard !kkeutnam, let bd = builder else { jamgeum.unlock(); return }
        let fmt = mokpyo
        if let f = fmt, conv == nil || conv?.inputFormat != buf.format {
            conv = AVAudioConverter(from: buf.format, to: f)
        }
        let cv = conv
        jamgeum.unlock()
        guard let f = fmt else { bd.yield(AnalyzerInput(buffer: buf)); return }
        if buf.format == f { bd.yield(AnalyzerInput(buffer: buf)); return }
        guard let c = cv else { return }
        let bi = f.sampleRate / max(1, buf.format.sampleRate)
        let cap = AVAudioFrameCount(Double(buf.frameLength) * bi + 64)
        guard let out = AVAudioPCMBuffer(pcmFormat: f, frameCapacity: cap) else { return }
        var jum = false
        var err: NSError?
        c.convert(to: out, error: &err) { _, st in
            if jum { st.pointee = .noDataNow; return nil }
            jum = true
            st.pointee = .haveData
            return buf
        }
        if err == nil && out.frameLength > 0 { bd.yield(AnalyzerInput(buffer: out)) }
    }

    /// 그만 들음
    func kkeut() {
        jamgeum.lock()
        kkeutnam = true
        let bd = builder, an = analyzer
        builder = nil
        analyzer = nil
        jamgeum.unlock()
        bd?.finish()
        dollimTask?.cancel()
        if let an = an { Task { try? await an.cancelAndFinishNow() } }
    }
}
