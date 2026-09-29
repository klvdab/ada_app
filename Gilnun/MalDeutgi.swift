// 말 듣기 — 아이폰 자체 받아쓰기를 폰 안에서 씁니다(돈이 들지 않고, 통신이 끊겨도 됨).
// 두 가지로 듣습니다.
//   명령 듣기: 말로 하기 단추나 "하이 길눈" 뒤에 한 번 — 말이 멈추면 곧 끝내고 알아들은 말들을 넘김
//   부름 듣기: "하이 길눈"을 기다리며 계속 — 음악을 막지 않고(섞어 틀기), 길눈이 말하는 동안에는 쉼
// 이어폰(블루투스·유선)의 마이크가 붙어 있으면 그 마이크로 듣습니다.
// 2.12.1 (빌드 260929-1, 이사장님 승인) 마이크를 한 번 열면 닫지 않고 알아듣기만 쉬었다가 새로 엽니다.
//   폰이 잠기면 아이폰이 마이크를 새로 여는 것을 막아 하이 길눈이 멈추던 것(9/29 기록: 잠긴 채 세 번 실패)을 고침.
//   마이크를 아주 닫는 것은 meomchugi() 하나뿐 — 하이 길눈을 끌 때, 긴급통화, 전화, 이어폰을 바꿀 때.
// 2.12.2 (빌드 260929-2, 이사장님 승인 1) 걸러 듣기 — 폰이 내는 소리(방송·안내 말)를 마이크 소리에서 빼고 사람 목소리를 들음(통화 때 쓰는 아이폰 기능).
//   라디오가 나오는 동안 하이 길눈을 못 알아듣던 것(9/29 12시 5분 뒤 기록)을 고침. 부름을 들으면 다른 앱 소리를 크게 낮춤.
// 2.12.3 (빌드 260929-3, 이사장님 승인 1) 걸러 듣기와 소리 낮추기를 뺌 — 아이폰이 스피커 소리를 통화처럼 줄이고
//   길눈 대답("네")까지 낮춰 들리지 않았음. 소리 크기는 2.12.1처럼 그대로. 걸러 듣기는 길눈 소리를 같은 통로로 내는 방법을 따로 연구함.
// 2.12.4 (빌드 260929-4, 이사장님 승인 1) 마이크 살피기 — 켜져 있는데 10초 넘게 소리가 안 들어오면 닫았다 다시 엶(기록 maik_meomchum)
// 2.12.5 (빌드 260929-5) 명령 받아쓰기에 가까운 역·즐겨찾기 이름을 미리 알림, 되물은 뒤 10초 기다림, "하이길"까지만 들려도 알아들음
// 나중에 다른 받아쓰기(애저 등)로 바꿀 때는 이 파일만 바꿔 끼우면 됩니다.
import Foundation
import Speech
import AVFoundation
import UIKit   // 2.12.6 앱이 뒤에 있는지 보려고

final class MalDeutgi: NSObject {
    static let shared = MalDeutgi()

    enum Bangsik { case myeongryeong, bureum }

    private let recog = SFSpeechRecognizer(locale: Locale(identifier: "ko-KR"))
    private let engine = AVAudioEngine()
    private var req: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var beon = 0
    private(set) var bangsik: Bangsik = .myeongryeong
    /// 마이크가 열려 있음(알아듣는 중이거나, 길눈이 말하는 동안 잠시 쉬는 중)
    var dolgoItda: Bool { engine.isRunning }
    /// 지금 알아듣는 중
    private(set) var aradeutneun = false
    /// 2.12.2 걸러 듣기(폰이 내는 소리를 빼고 들음)로 마이크가 열려 있음
    var georeunda: Bool { engine.isRunning && engine.inputNode.isVoiceProcessingEnabled }

    // 마이크 소리를 지금의 알아듣기에 넘기는 자리 — 소리 줄(오디오 스레드)과 함께 쓰므로 잠금
    private let jamgeum = NSLock()
    private var _tapReq: SFSpeechAudioBufferRecognitionRequest?
    private var tapReq: SFSpeechAudioBufferRecognitionRequest? {
        get { jamgeum.lock(); defer { jamgeum.unlock() }; return _tapReq }
        set { jamgeum.lock(); _tapReq = newValue; jamgeum.unlock() }
    }
    // 2.12.4 마이크에서 소리 조각이 들어온 수 — 멎었는지 살피려고
    private var _beopeoSu = 0
    private var jijeomSu = -1
    private var salpigiSigye: Timer?
    private func tapBatgi(_ buf: AVAudioPCMBuffer) {
        jamgeum.lock()
        _beopeoSu &+= 1
        let q = _tapReq
        jamgeum.unlock()
        q?.append(buf)
    }
    private func beopeoSuIlgi() -> Int { jamgeum.lock(); defer { jamgeum.unlock() }; return _beopeoSu }

    private var alts: [String] = []
    private var malHam = false
    private var jamjamSigye: Timer?
    private var handoSigye: Timer?
    private var myeongryeongKkeut: (([String]) -> Void)?
    private var bureumDeureum: (() -> Void)?
    private var bureumKkeunkim: (() -> Void)?
    private var moreuneunMal = Date.distantPast
    /// 2.12.5 명령을 들을 때 받아쓰기에 미리 알려 줄 말(가까운 역·즐겨찾기)
    var doumMal: [String] = []
    /// 마이크를 열어 두어야 함(아주 닫으면 false) — 소리 자리가 바뀌어 멎었을 때만 다시 돌리려고
    private var yeollyeoya = false

    override init() {
        super.init()
        // 다른 소리가 소리 자리를 바꿔 마이크가 멎으면 곧바로 다시 돌림
        NotificationCenter.default.addObserver(forName: .AVAudioEngineConfigurationChange, object: engine, queue: .main) { [weak self] _ in
            self?.dasiDollim()
        }
        // 2.12.4 10초마다 마이크가 살아 있는지 살핌
        DispatchQueue.main.async { [weak self] in
            self?.salpigiSigye = Timer.scheduledTimer(withTimeInterval: 10, repeats: true) { [weak self] _ in self?.maikSalpigi() }
        }
    }

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
    func myeongryeong(gidarim: Double = 6, _ kkeut: @escaping ([String]) -> Void) -> Bool {
        swigi()
        bangsik = .myeongryeong
        myeongryeongKkeut = kkeut
        alts = []
        malHam = false
        guard sijak(.myeongryeong) else {
            myeongryeongKkeut = nil
            return false
        }
        handoSigye = Timer.scheduledTimer(withTimeInterval: gidarim, repeats: false) { [weak self] _ in
            guard let s = self, !s.malHam else { return }
            s.myeongryeongMaechim()
        }
        return true
    }

    /// "하이 길눈" 기다리기
    func bureum(deureum: @escaping () -> Void, kkeunkim: @escaping () -> Void) -> Bool {
        swigi()
        bangsik = .bureum
        bureumDeureum = deureum
        bureumKkeunkim = kkeunkim
        guard sijak(.bureum) else { return false }
        // 한 번 듣기가 너무 길어지지 않게 50초마다 알아듣기만 새로 엶(마이크는 그대로)
        handoSigye = Timer.scheduledTimer(withTimeInterval: 50, repeats: false) { [weak self] _ in
            guard let s = self, s.bangsik == .bureum, s.aradeutneun else { return }
            s.swigi()
            s.bureumKkeunkim?()
        }
        return true
    }

    /// 알아듣기만 쉼 — 마이크는 열어 둠(길눈이 말하는 동안, 한 번 듣기를 마쳤을 때)
    func swigi() {
        beon += 1
        jamjamSigye?.invalidate()
        jamjamSigye = nil
        handoSigye?.invalidate()
        handoSigye = nil
        tapReq = nil
        task?.cancel()
        task = nil
        req?.endAudio()
        req = nil
        aradeutneun = false
    }

    /// 2.12.2 부름을 들은 동안 다른 소리(다른 앱·방송)를 크게 낮추고, 끝나면 되돌림 — 걸러 듣기일 때만
    func dareunSori(jurim: Bool) {
        guard georeunda else { return }
        if jurim { return }   // 2.12.3 크게 낮추지 않음 — 길눈 대답까지 낮아졌음
        if #available(iOS 17.0, *) {
            engine.inputNode.voiceProcessingOtherAudioDuckingConfiguration =
                AVAudioVoiceProcessingOtherAudioDuckingConfiguration(enableAdvancedDucking: false, duckingLevel: jurim ? .max : .min)
        }
    }

    /// 마이크까지 아주 닫음(넘길 것 없이)
    func meomchugi() {
        yeollyeoya = false
        swigi()
        if engine.isRunning { engine.stop() }
        engine.inputNode.removeTap(onBus: 0)
    }

    // MARK: 속

    private func sesyeon(_ b: Bangsik, seokgi: Bool = false) throws {
        let s = AVAudioSession.sharedInstance()
        var o: AVAudioSession.CategoryOptions = [.defaultToSpeaker, .allowBluetoothA2DP]
        // 2.12.0 부름을 기다릴 때는 이어폰을 통화 음질(HFP)로 바꾸지 않음 — 음악이 먹먹해지지 않게
        if b != .bureum { o.insert(.allowBluetooth) }
        // 부름을 기다릴 때는 음악을 그대로 두고, 명령을 들을 때만 잠시 낮춤
        // 2.6.0 안내 중 이어폰 단추를 받을 때는 섞지 않음(단추가 길눈으로 오게)
        if !RemoteDanchu.shared.kyeojim { o.insert(b == .bureum ? .mixWithOthers : .duckOthers) }
        // 2.12.7 폰이 잠겼거나 앱이 뒤에 있을 때 다른 소리를 끊고 들어가지 못하면(!int) 섞어서라도 엶
        if seokgi { o.remove(.duckOthers); o.insert(.mixWithOthers) }
        try s.setCategory(.playAndRecord, mode: .default, options: o)
        try s.setActive(true)
    }

    /// 마이크를 엶 — 이미 열려 있으면 그대로 씀
    private func maikYeolgi(_ b: Bangsik) -> Bool {
        if engine.isRunning && AVAudioSession.sharedInstance().category == .playAndRecord { return true }
        if engine.isRunning { engine.stop() }
        do {
            try sesyeon(b)
        } catch {
            let code = (error as NSError).code
            Girok.shared.namgi("maldeutgi_mot", ["kkadak": "sesyeon", "code": code])
            // 2.12.7 앱을 켠 직후 마이크가 아홉 번 열리지 못하던 것(9/29 17시 7분) — 다른 소리를 끊지 못한다는 까닭이면 섞어서 한 번 더
            do {
                try sesyeon(b, seokgi: true)
                Girok.shared.namgi("maik_seokgi", ["code": code])
            } catch {
                Girok.shared.namgi("maldeutgi_mot", ["kkadak": "sesyeon_seokgi", "code": (error as NSError).code])
                return false
            }
        }
        let inp = engine.inputNode
        // 2.12.2 걸러 듣기 — 하이 길눈과 "방송 중에도 하이 길눈 듣기"를 켜 두셨을 때
        let geureo = false   // 2.12.3 걸러 듣기를 쓰지 않음(켜져 있던 폰은 여기서 끔)
        if inp.isVoiceProcessingEnabled != geureo {
            do {
                try inp.setVoiceProcessingEnabled(geureo)
            } catch {
                Girok.shared.namgi("maldeutgi_mot", ["kkadak": "georeugi", "code": (error as NSError).code])
            }
        }
        if inp.isVoiceProcessingEnabled {
            if #available(iOS 17.0, *) {
                // 기다리는 동안에는 다른 소리를 되도록 낮추지 않음
                inp.voiceProcessingOtherAudioDuckingConfiguration =
                    AVAudioVoiceProcessingOtherAudioDuckingConfiguration(enableAdvancedDucking: false, duckingLevel: .min)
            }
            _ = engine.mainMixerNode   // 걸러 듣기는 소리 내보내는 길도 함께 있어야 돌아감
        }
        let fmt = inp.outputFormat(forBus: 0)
        guard fmt.sampleRate > 0, fmt.channelCount > 0 else {
            Girok.shared.namgi("maldeutgi_mot", ["kkadak": "mike_eopseum"])
            return false
        }
        inp.removeTap(onBus: 0)
        inp.installTap(onBus: 0, bufferSize: 1024, format: fmt) { [weak self] buf, _ in self?.tapBatgi(buf) }
        engine.prepare()
        do {
            try engine.start()
        } catch {
            inp.removeTap(onBus: 0)
            Girok.shared.namgi("maldeutgi_mot", ["kkadak": "engine", "code": (error as NSError).code])
            return false
        }
        yeollyeoya = true
        return true
    }

    /// 2.12.4 마이크가 켜진 것으로 되어 있는데 소리가 들어오지 않으면 닫았다 다시 엶
    private func maikSalpigi() {
        guard yeollyeoya, engine.isRunning else { jijeomSu = -1; return }
        let n = beopeoSuIlgi()
        if n == jijeomSu {
            Girok.shared.namgi("maik_meomchum", ["bangsik": bangsik == .bureum ? "bureum" : "myeongryeong", "aradeut": aradeutneun])
            // 2.12.6 폰이 잠겨 앱이 뒤에 있으면 마이크를 끄지 않음 — 아이폰은 잠긴 채로는 다시 열어 주지 않아 아예 못 듣게 되던 것
            if UIApplication.shared.applicationState != .active {
                jijeomSu = -1
                return
            }
            engine.stop()
            jijeomSu = -1
            dasiDollim()
            return
        }
        jijeomSu = n
    }

    /// 소리 자리가 바뀌어 마이크가 멎었을 때 — 듣던 대로 다시 돌림
    private func dasiDollim() {
        guard yeollyeoya, !engine.isRunning else { return }
        let b = bangsik
        let dd = aradeutneun
        swigi()
        engine.inputNode.removeTap(onBus: 0)
        let ok = maikYeolgi(b)
        Girok.shared.namgi("maik_dasi", ["ok": ok])
        guard ok else {
            Girok.shared.namgi("maldeutgi_mot", ["kkadak": "dasi_dollim"])
            if b == .bureum { bureumKkeunkim?() } else { myeongryeongMaechim() }
            return
        }
        if dd { if b == .bureum { bureumKkeunkim?() } else { myeongryeongMaechim() } }
    }

    private func sijak(_ b: Bangsik) -> Bool {
        guard MalDeutgi.heorakItda, let r = recog, r.isAvailable else {
            Girok.shared.namgi("maldeutgi_mot", ["kkadak": "heorak_ttoneun_bappeum"])
            return false
        }
        guard maikYeolgi(b) else { return false }
        let q = SFSpeechAudioBufferRecognitionRequest()
        q.shouldReportPartialResults = true
        if r.supportsOnDeviceRecognition { q.requiresOnDeviceRecognition = true }
        q.addsPunctuation = false
        // 2.12.1 "하이 길눈"을 바르게 알아듣도록 받아쓰기에 미리 알려 줌
        q.contextualStrings = b == .myeongryeong ? (["하이 길눈"] + doumMal) : ["하이 길눈", "길눈아", "길눈"]
        req = q
        tapReq = q
        beon += 1
        let b0 = beon
        task = r.recognitionTask(with: q) { [weak self] res, err in
            let ls: [String] = res.map { Array($0.transcriptions.prefix(3)).map { $0.formattedString } } ?? []
            let final = res?.isFinal ?? false
            let oryu = (err != nil)
            DispatchQueue.main.async { self?.gyeolgwa(b0, ls, final, oryu) }
        }
        aradeutneun = true
        return true
    }

    private func gyeolgwa(_ b0: Int, _ ls: [String], _ final: Bool, _ oryu: Bool) {
        guard b0 == beon else { return }
        if bangsik == .bureum {
            // 2.12.2 길눈이 제 입으로 "길눈"을 말하는 중이면 부름으로 치지 않음
            let jegaMalham = SoriEngine.shared.malhaneunJung && SoriEngine.shared.hanunMal.contains("길눈")   // 2.12.6 실제로 말하는 동안만
            if !jegaMalham, ls.contains(where: { MalDeutgi.bureumMal($0) }) {
                swigi()
                Girok.shared.namgi("hai_gilnun", [:])
                bureumDeureum?()
                return
            }
            // 2.12.1 "하이"·"헤이"로 시작했는데 못 알아들은 짧은 말만 남김(알아듣는 말을 키우려고)
            if let t = ls.first, Date().timeIntervalSince(moreuneunMal) > 5 {
                let z = MalSajeon.ttuk(t)
                if let i = z.range(of: "하이") ?? z.range(of: "헤이") ?? z.range(of: "아이") {
                    let kkori = String(z[i.lowerBound...].prefix(8))
                    if kkori.count >= 3 {
                        moreuneunMal = Date()
                        Girok.shared.namgi("hai_moreum", ["mal": kkori])
                    }
                }
            }
            if final || oryu {
                swigi()
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
        swigi()
        f(a)
    }

    /// "하이 길눈"(또는 "길눈아")을 들었는가 — 끝부분만 보고, 발음이 조금 달라도 알아들음
    /// 2.12.1 아이폰이 잘못 적는 말(하이론 등)도 받아들임
    static func bureumMal(_ t: String) -> Bool {
        let z = MalSajeon.ttuk(t)
        if z.isEmpty { return false }
        // 2.12.5 "하이길"까지만 들려도 곧바로(기록상 0.3~0.5초 빨라짐)
        if z.hasSuffix("하이길") || z.hasSuffix("헤이길") { return true }
        for m in ["하이길눈", "하이길는", "하이기룬", "하이길론", "헤이길눈", "길눈아",
                  "하이론", "하이기론", "하이기눈", "하이길룬", "하이길문", "하이길운", "아이길눈", "하이킬눈", "헤이기룬", "헤이론"] where z.contains(m) { return true }
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
