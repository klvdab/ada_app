// 말 듣기 — 아이폰 자체 받아쓰기를 폰 안에서 씁니다(돈이 들지 않고, 통신이 끊겨도 됨).
// 두 가지로 듣습니다.
//   명령 듣기: 말로 하기 단추나 "하이 길눈" 뒤에 한 번 — 말이 멈추면 곧 끝내고 알아들은 말들을 넘김
//   부름 듣기: "하이 길눈"을 기다리며 계속 — 음악을 막지 않고(섞어 틀기), 길눈이 말하는 동안에는 쉼
// 이어폰(블루투스·유선)의 마이크가 붙어 있으면 그 마이크로 듣습니다.
// 나중에 다른 받아쓰기(애저 등)로 바꿀 때는 이 파일만 바꿔 끼우면 됩니다.
import Foundation
import Speech
import AVFoundation

final class MalDeutgi: NSObject {
    static let shared = MalDeutgi()

    enum Bangsik { case myeongryeong, bureum }

    private let recog = SFSpeechRecognizer(locale: Locale(identifier: "ko-KR"))
    private let engine = AVAudioEngine()
    private var req: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var beon = 0
    private(set) var bangsik: Bangsik = .myeongryeong
    private(set) var dolgoItda = false

    private var alts: [String] = []
    private var malHam = false
    private var jamjamSigye: Timer?
    private var handoSigye: Timer?
    private var myeongryeongKkeut: (([String]) -> Void)?
    private var bureumDeureum: (() -> Void)?
    private var bureumKkeunkim: (() -> Void)?

    // MARK: 허락

    static var heorakItda: Bool {
        SFSpeechRecognizer.authorizationStatus() == .authorized
            && AVAudioSession.sharedInstance().recordPermission == .granted
    }

    static func heorak(_ f: @escaping (Bool) -> Void) {
        SFSpeechRecognizer.requestAuthorization { s in
            let ok1 = (s == .authorized)
            AVAudioSession.sharedInstance().requestRecordPermission { ok2 in
                DispatchQueue.main.async { f(ok1 && ok2) }
            }
        }
    }

    // MARK: 듣기 시작과 멈춤

    /// 명령 한 번 듣기 — 말이 1.3초 멈추면 끝, 아무 말이 없으면 6초, 길어도 12초
    func myeongryeong(_ kkeut: @escaping ([String]) -> Void) -> Bool {
        meomchugi()
        bangsik = .myeongryeong
        myeongryeongKkeut = kkeut
        alts = []
        malHam = false
        guard sijak(.myeongryeong) else {
            myeongryeongKkeut = nil
            return false
        }
        handoSigye = Timer.scheduledTimer(withTimeInterval: 6, repeats: false) { [weak self] _ in
            guard let s = self, !s.malHam else { return }
            s.myeongryeongMaechim()
        }
        return true
    }

    /// "하이 길눈" 기다리기
    func bureum(deureum: @escaping () -> Void, kkeunkim: @escaping () -> Void) -> Bool {
        meomchugi()
        bangsik = .bureum
        bureumDeureum = deureum
        bureumKkeunkim = kkeunkim
        guard sijak(.bureum) else { return false }
        // 한 번 듣기가 너무 길어지지 않게 50초마다 새로 엶
        handoSigye = Timer.scheduledTimer(withTimeInterval: 50, repeats: false) { [weak self] _ in
            guard let s = self, s.bangsik == .bureum, s.dolgoItda else { return }
            s.meomchugi()
            s.bureumKkeunkim?()
        }
        return true
    }

    /// 모두 멈춤(넘길 것 없이)
    func meomchugi() {
        beon += 1
        jamjamSigye?.invalidate()
        jamjamSigye = nil
        handoSigye?.invalidate()
        handoSigye = nil
        task?.cancel()
        task = nil
        req?.endAudio()
        req = nil
        if engine.isRunning { engine.stop() }
        engine.inputNode.removeTap(onBus: 0)
        dolgoItda = false
    }

    // MARK: 속

    private func sesyeon(_ b: Bangsik) throws {
        let s = AVAudioSession.sharedInstance()
        var o: AVAudioSession.CategoryOptions = [.defaultToSpeaker, .allowBluetooth, .allowBluetoothA2DP]
        // 부름을 기다릴 때는 음악을 그대로 두고, 명령을 들을 때만 잠시 낮춤
        // 2.6.0 안내 중 이어폰 단추를 받을 때는 섞지 않음(단추가 길눈으로 오게)
        if !RemoteDanchu.shared.kyeojim { o.insert(b == .bureum ? .mixWithOthers : .duckOthers) }
        try s.setCategory(.playAndRecord, mode: .default, options: o)
        try s.setActive(true)
    }

    private func sijak(_ b: Bangsik) -> Bool {
        guard MalDeutgi.heorakItda, let r = recog, r.isAvailable else {
            Girok.shared.namgi("maldeutgi_mot", ["kkadak": "heorak_ttoneun_bappeum"])
            return false
        }
        do {
            try sesyeon(b)
        } catch {
            Girok.shared.namgi("maldeutgi_mot", ["kkadak": "sesyeon", "code": (error as NSError).code])
            return false
        }
        let q = SFSpeechAudioBufferRecognitionRequest()
        q.shouldReportPartialResults = true
        if r.supportsOnDeviceRecognition { q.requiresOnDeviceRecognition = true }
        q.addsPunctuation = false
        let inp = engine.inputNode
        let fmt = inp.outputFormat(forBus: 0)
        guard fmt.sampleRate > 0, fmt.channelCount > 0 else {
            Girok.shared.namgi("maldeutgi_mot", ["kkadak": "mike_eopseum"])
            return false
        }
        inp.removeTap(onBus: 0)
        inp.installTap(onBus: 0, bufferSize: 1024, format: fmt) { [weak q] buf, _ in q?.append(buf) }
        engine.prepare()
        do {
            try engine.start()
        } catch {
            inp.removeTap(onBus: 0)
            Girok.shared.namgi("maldeutgi_mot", ["kkadak": "engine", "code": (error as NSError).code])
            return false
        }
        req = q
        beon += 1
        let b0 = beon
        task = r.recognitionTask(with: q) { [weak self] res, err in
            let ls: [String] = res.map { Array($0.transcriptions.prefix(3)).map { $0.formattedString } } ?? []
            let final = res?.isFinal ?? false
            let oryu = (err != nil)
            DispatchQueue.main.async { self?.gyeolgwa(b0, ls, final, oryu) }
        }
        dolgoItda = true
        return true
    }

    private func gyeolgwa(_ b0: Int, _ ls: [String], _ final: Bool, _ oryu: Bool) {
        guard b0 == beon else { return }
        if bangsik == .bureum {
            if let t = ls.first, MalDeutgi.bureumMal(t) {
                meomchugi()
                Girok.shared.namgi("hai_gilnun", [:])
                bureumDeureum?()
                return
            }
            if final || oryu {
                meomchugi()
                bureumKkeunkim?()
            }
            return
        }
        // 명령 듣기
        if !ls.isEmpty, !(ls.first ?? "").trimmingCharacters(in: .whitespaces).isEmpty {
            let cheotMal = !malHam
            alts = ls
            malHam = true
            jamjamSigye?.invalidate()
            jamjamSigye = Timer.scheduledTimer(withTimeInterval: 1.3, repeats: false) { [weak self] _ in
                self?.myeongryeongMaechim()
            }
            if cheotMal {
                // 말을 시작했으면 길어도 12초에서 끊음
                handoSigye?.invalidate()
                handoSigye = Timer.scheduledTimer(withTimeInterval: 12, repeats: false) { [weak self] _ in
                    self?.myeongryeongMaechim()
                }
            }
        }
        if final || oryu { myeongryeongMaechim() }
    }

    private func myeongryeongMaechim() {
        guard bangsik == .myeongryeong, let f = myeongryeongKkeut else { return }
        myeongryeongKkeut = nil
        let a = alts
        meomchugi()
        f(a)
    }

    /// "하이 길눈"(또는 "길눈아")을 들었는가 — 끝부분만 보고, 발음이 조금 달라도 알아들음
    static func bureumMal(_ t: String) -> Bool {
        let z = MalSajeon.ttuk(t)
        if z.isEmpty { return false }
        for m in ["하이길눈", "하이길는", "하이기룬", "하이길론", "헤이길눈", "길눈아"] where z.contains(m) { return true }
        let kkori = MalSajeon.jamo(String(z.suffix(8)))
        for (bon, heo) in [(MalSajeon.jamo("하이길눈"), 2), (MalSajeon.jamo("길눈아"), 1)] {
            let n = bon.count
            if kkori.count < n - 1 { continue }
            var s = 0
            while s + n - 1 <= kkori.count {
                let e = min(kkori.count, s + n)
                if MalSajeon.geori(Array(kkori[s..<e]), bon) <= heo { return true }
                s += 1
            }
        }
        return false
    }
}
