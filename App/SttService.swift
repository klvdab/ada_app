// 앱 받아쓰기 — 웹의 음성 인식(SpeechRecognition)을 아이폰 자체 받아쓰기로 갈음합니다.
// 1.0판 빌드 260926-1 (2026-09-26 이사장님 승인)
// 웹 방식 마이크는 켤 때마다 소리 장치를 새로 잡아 보이스오버와 다투고, 화면을 옮기면 끊겼습니다.
// 앱에서는 듣는 동안만 "녹음·재생 겸용(보이스오버·음악과 함께, 음악은 잠깐 줄임)"으로 바꾸고,
// 다 들으면 원래(재생 전용, 기기 단추 받기)로 되돌립니다. 마이크·받아쓰기 허락은 처음 한 번만 받습니다.
// 웹 쪽(app_bridge.js)이 SpeechRecognition 과 같은 모양으로 감싸 주므로 길눈 화면은 고칠 것이 없습니다.
import Foundation
import Speech
import AVFoundation

final class SttService: NSObject {
    static let shared = SttService()
    /// 웹으로 보낼 일: deutgiSijak / deutgiGyeolgwa / deutgiOryu / deutgiKkeut
    var onEvent: ((String, [String: Any]) -> Void)?

    private let engine = AVAudioEngine()
    private var req: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var recognizer: SFSpeechRecognizer?
    private var id = ""
    private var done = true
    private var sawText = false
    private var lastText = ""
    private var finalSent = false
    private var quietTimer: Timer?
    private var capTimer: Timer?

    private func emit(_ name: String, _ data: [String: Any]) {
        onEvent?(name, data)
    }

    /// 듣기 시작. continuous 여도 한 마디(말이 멎을 때까지)를 받고 끝냅니다 — 길눈 화면들이 끝나면 다시 켜는 방식이라 그대로 맞습니다.
    func start(id newId: String, lang: String, continuous: Bool) {
        DispatchQueue.main.async {
            if !self.done { self.finish(reason: nil) }
            self.id = newId
            self.done = false
            self.sawText = false
            self.lastText = ""
            self.finalSent = false
            SFSpeechRecognizer.requestAuthorization { st in
                AVAudioSession.sharedInstance().requestRecordPermission { mic in
                    DispatchQueue.main.async {
                        guard self.id == newId, !self.done else { return }
                        if st != .authorized || !mic { self.finish(reason: "not-allowed"); return }
                        self.begin(lang: lang, continuous: continuous)
                    }
                }
            }
        }
    }

    /// 멈춤. abort 면 들은 것을 버리고, 아니면 여태 들은 말을 마무리해 넘기고 끝냅니다.
    func stop(id stopId: String, abort: Bool) {
        DispatchQueue.main.async {
            guard !self.done, stopId.isEmpty || stopId == self.id else { return }
            if abort {
                self.finish(reason: "aborted")
            } else {
                self.req?.endAudio()
                self.armCap(1.5)
            }
        }
    }

    private func begin(lang: String, continuous: Bool) {
        let loc = Locale(identifier: lang.isEmpty ? "ko-KR" : lang)
        guard let rec = SFSpeechRecognizer(locale: loc), rec.isAvailable else {
            finish(reason: "service-not-allowed"); return
        }
        recognizer = rec
        let s = AVAudioSession.sharedInstance()
        do {
            try s.setCategory(.playAndRecord, mode: .default,
                              options: [.mixWithOthers, .duckOthers, .defaultToSpeaker, .allowBluetooth, .allowBluetoothA2DP])
            try s.setActive(true)
        } catch {
            finish(reason: "audio-capture"); return
        }
        let r = SFSpeechAudioBufferRecognitionRequest()
        r.shouldReportPartialResults = true
        req = r
        let input = engine.inputNode
        input.removeTap(onBus: 0)
        let fmt = input.outputFormat(forBus: 0)
        guard fmt.sampleRate > 0, fmt.channelCount > 0 else { finish(reason: "audio-capture"); return }
        input.installTap(onBus: 0, bufferSize: 1024, format: fmt) { [weak r] buf, _ in r?.append(buf) }
        engine.prepare()
        do { try engine.start() } catch { finish(reason: "audio-capture"); return }

        let myId = id
        emit("deutgiSijak", ["id": myId])
        task = rec.recognitionTask(with: r) { [weak self] result, error in
            DispatchQueue.main.async {
                guard let self = self, self.id == myId, !self.done else { return }
                if let res = result {
                    let t = res.bestTranscription.formattedString
                    if !t.isEmpty { self.sawText = true; self.lastText = t }
                    if res.isFinal {
                        self.sendFinal(t.isEmpty ? self.lastText : t)
                        self.finish(reason: self.sawText ? nil : "no-speech")
                        return
                    }
                    if !t.isEmpty { self.emit("deutgiGyeolgwa", ["id": myId, "t": t, "final": false]) }
                    self.armQuiet()
                } else if error != nil {
                    self.sendFinal(self.lastText)
                    self.finish(reason: self.sawText ? nil : "no-speech")
                }
            }
        }
        // 아무 말이 없으면 8초(늘 듣기는 12초) 뒤 끝냅니다
        armCap(continuous ? 12 : 8)
    }

    private func sendFinal(_ t: String) {
        guard !finalSent, !t.isEmpty else { return }
        finalSent = true
        emit("deutgiGyeolgwa", ["id": id, "t": t, "final": true])
    }

    /// 말이 1.2초 멎으면 그 말로 마무리합니다
    private func armQuiet() {
        quietTimer?.invalidate()
        quietTimer = Timer.scheduledTimer(withTimeInterval: 1.2, repeats: false) { [weak self] _ in
            guard let self = self, !self.done else { return }
            self.req?.endAudio()
            self.armCap(1.5)
        }
    }

    private func armCap(_ sec: TimeInterval) {
        capTimer?.invalidate()
        capTimer = Timer.scheduledTimer(withTimeInterval: sec, repeats: false) { [weak self] _ in
            guard let self = self, !self.done else { return }
            self.sendFinal(self.lastText)
            self.finish(reason: self.sawText ? nil : "no-speech")
        }
    }

    private func finish(reason: String?) {
        if done { return }
        done = true
        quietTimer?.invalidate(); quietTimer = nil
        capTimer?.invalidate(); capTimer = nil
        if engine.isRunning { engine.stop() }
        engine.inputNode.removeTap(onBus: 0)
        req?.endAudio()
        task?.cancel()
        req = nil
        task = nil
        recognizer = nil
        RemoteCommandService.shared.restoreSession()
        let myId = id
        if let why = reason, why != "aborted" { emit("deutgiOryu", ["id": myId, "error": why]) }
        emit("deutgiKkeut", ["id": myId])
    }
}
