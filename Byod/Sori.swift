// BYOD 방송 아이폰 — 해설 소리 받기 (1.1.5판, 빌드 261010-BI1, 방송클)
// 사운드카드(수신기 이어폰 선을 꽂은 USB 사운드카드)가 붙어 있으면 그쪽을 저절로 고르고, 없으면 아이폰 마이크로 받습니다.
// 아이폰 12처럼 라이트닝 단자인 폰은 애플 「라이트닝-USB 3 카메라 어댑터」(충전 구멍 달린 것)에 사운드카드를 꽂습니다.
// 방송 중에 꽂거나 빼면 저절로 다시 고릅니다. 소리를 다듬지 않는 측정 방식으로 받아(안드로이드판의 원음과 같은 뜻)
// 1초에 16000번, 0.1초(1600개)씩 모아 뮤로 줄여 넘깁니다 — 안드로이드판·노트북판과 같은 방식.
// 화면을 잠가도 소리 받기가 이어지도록 앱 설정에 「뒤에서 소리」(audio)를 켜 두었습니다.
import Foundation
import AVFoundation

final class Sori {
    static let RATE: Double = 16000
    static let JOGAK = 1600   // 0.1초

    private let engine = AVAudioEngine()
    private var converter: AVAudioConverter?
    private let outFmt = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 16000, channels: 1, interleaved: true)!
    private var moum: [Int16] = []
    private let batgi: (Data) -> Void
    private var dolgo = false
    private var bomyeon: [NSObjectProtocol] = []
    private var keugiJul = [Int](repeating: -99, count: 30)
    private var keugiJari = 0
    private var dasiDdae = Date.distantPast
    private let tapJamgeum = NSLock()   // 소리 일꾼 줄기와 화면 줄기가 converter·moum 을 함께 만지지 않게

    init(batgi: @escaping (Data) -> Void) { self.batgi = batgi }

    // MARK: 켜고 끄기

    func sijak() throws {
        if dolgo { return }
        dolgo = true
        JadongKeugi.cheoeum()   // 1.9.2
        do {
            try sesyeon()
            try engineYeolgi()
        } catch {
            dolgo = false
            throw error
        }
        let nc = NotificationCenter.default
        bomyeon.append(nc.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] n in
            self?.gilBakkwim(n)
        })
        bomyeon.append(nc.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] n in
            self?.kkeunkim(n)
        })
        bomyeon.append(nc.addObserver(forName: .AVAudioEngineConfigurationChange, object: engine, queue: .main) { [weak self] _ in
            self?.dasiYeolgi("소리 장치가 바뀜")
        })
        bomyeon.append(nc.addObserver(forName: AVAudioSession.mediaServicesWereResetNotification, object: nil, queue: .main) { [weak self] _ in
            self?.dasiYeolgi("소리 일꾼이 다시 켜짐")
        })
    }

    func meomchugi() {
        dolgo = false
        gidarimMeomchum()
        for o in bomyeon { NotificationCenter.default.removeObserver(o) }
        bomyeon.removeAll()
        if engine.isRunning { engine.stop() }
        engine.inputNode.removeTap(onBus: 0)
        tapJamgeum.lock(); moum.removeAll(); converter = nil; tapJamgeum.unlock()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    // MARK: 속

    private func sesyeon() throws {
        let s = AVAudioSession.sharedInstance()
        // 측정 방식 — 아이폰이 소리를 다듬지(크기 맞추기·잡음 줄이기) 않게. 블루투스 마이크는 쓰지 않음
        try s.setCategory(.playAndRecord, mode: .measurement, options: [.mixWithOthers, .defaultToSpeaker, .allowBluetoothA2DP])
        try? s.setPreferredSampleRate(48000)
        try? s.setPreferredIOBufferDuration(0.02)
        usbGoreugi()
        try s.setActive(true)
    }

    /// 사운드카드 → 이어폰 마이크 단자 → 라인 순서로 고름. 하나도 없으면 아이폰 마이크
    private func usbGoreugi() {
        let s = AVAudioSession.sharedInstance()
        let ins = s.availableInputs ?? []
        let goreum = ins.first(where: { $0.portType == .usbAudio }) ?? ins.first(where: { $0.portType == .headsetMic || $0.portType == .lineIn })
        try? s.setPreferredInput(goreum)
    }

    private func engineYeolgi() throws {
        let inp = engine.inputNode
        let fmt = inp.outputFormat(forBus: 0)
        guard fmt.sampleRate > 0, fmt.channelCount > 0 else {
            throw NSError(domain: "byod", code: 1, userInfo: [NSLocalizedDescriptionKey: "마이크를 열지 못했습니다."])
        }
        guard let cv = AVAudioConverter(from: fmt, to: outFmt) else {
            throw NSError(domain: "byod", code: 2, userInfo: [NSLocalizedDescriptionKey: "소리 바꾸개를 만들지 못했습니다."])
        }
        cv.downmix = true   // 사운드카드가 두 줄(스테레오)로 줘도 한 줄로 합침
        tapJamgeum.lock(); converter = cv; moum.removeAll(); tapJamgeum.unlock()
        inp.removeTap(onBus: 0)
        inp.installTap(onBus: 0, bufferSize: 4096, format: fmt) { [weak self] buf, _ in self?.tapBatgi(buf) }
        engine.prepare()
        do {
            try engine.start()
        } catch {
            inp.removeTap(onBus: 0)
            throw NSError(domain: "byod", code: 3, userInfo: [NSLocalizedDescriptionKey: "마이크가 다른 앱에 잡혀 있거나 열리지 않습니다."])
        }
        // 실제로 소리가 들어오는 장치를 알림
        let nm = Sori.jangchiIreum()
        let hal = Sori.haljilMal()
        Bang.shared.juge { b in
            b.sojae = nm
            if b.jalmot.hasPrefix("마이크") || b.jalmot.hasPrefix("소리 장치") || b.jalmot.isEmpty { b.jalmot = hal }
        }
    }

    /// 꽂고 빼고, 일꾼이 바뀌었을 때 — 다시 고르고 다시 엶(1초에 한 번까지)
    private func dasiYeolgi(_ kkadak: String) {
        guard dolgo else { return }
        if Date().timeIntervalSince(dasiDdae) < 1 {
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.1) { [weak self] in self?.dasiYeolgi(kkadak) }
            return
        }
        dasiDdae = Date()
        if engine.isRunning { engine.stop() }
        engine.inputNode.removeTap(onBus: 0)
        usbGoreugi()
        do {
            try? AVAudioSession.sharedInstance().setActive(true)
            try engineYeolgi()
        } catch {
            Bang.shared.juge { b in b.jalmot = "마이크: " + error.localizedDescription }
            DispatchQueue.main.asyncAfter(deadline: .now() + 2) { [weak self] in self?.dasiYeolgi("다시 해 봄") }
        }
    }

    private func gilBakkwim(_ n: Notification) {
        guard let v = n.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt,
              let r = AVAudioSession.RouteChangeReason(rawValue: v) else { return }
        switch r {
        case .newDeviceAvailable, .oldDeviceUnavailable: dasiYeolgi("꽂거나 뺌")
        default:
            let nm = Sori.jangchiIreum()
            Bang.shared.juge { b in if b.sojae != nm { b.sojae = nm } }
        }
    }

    /// 전화 따위로 소리가 끊겼다가 끝나면 다시 엶
    private func kkeunkim(_ n: Notification) {
        guard let v = n.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              let t = AVAudioSession.InterruptionType(rawValue: v) else { return }
        if t == .began {
            Bang.shared.juge { b in b.jalmot = "통화 중입니다. 듣는 분께 잠시 기다려 달라는 안내를 보내고 있습니다. 통화가 끝나면 저절로 이어 보냅니다." }
            gidarimSijak()   // 1.3.1 (261015-BI5) 듣는 분께 「잠시 기다려 주십시오」
        } else {
            gidarimMeomchum()
            Bang.shared.juge { b in b.jalmot = "" }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in self?.dasiYeolgi("끊김이 끝남") }
        }
    }

    // 1.3.1 (261015-BI5) 통화 중 기다림 안내 — 0.1초마다 한 조각씩, 안내 약 9.5초 뒤 15초 조용함을 되풀이
    private var gidarimTimer: DispatchSourceTimer?
    private func gidarimSijak() {
        gidarimMeomchum()
        Gidarim.cheoeum()
        let t = DispatchSource.makeTimerSource(queue: DispatchQueue(label: "byod.gidarim"))
        t.schedule(deadline: .now(), repeating: 0.1)
        t.setEventHandler { [weak self] in
            guard let s = self, s.dolgo, Bang.shared.keu else { return }
            s.batgi(Gidarim.jogak(Sori.JOGAK))
        }
        t.resume()
        gidarimTimer = t
    }
    private func gidarimMeomchum() { gidarimTimer?.cancel(); gidarimTimer = nil }

    private func tapBatgi(_ buf: AVAudioPCMBuffer) {
        var jogakdeul: [[Int16]] = []
        tapJamgeum.lock()
        defer {
            tapJamgeum.unlock()
            for jogak in jogakdeul { if Bang.shared.keu { batgi(MuLaw.jurigi(jogak)) } }
        }
        guard let cv = converter, buf.frameLength > 0 else { return }
        let bi = Sori.RATE / buf.format.sampleRate
        let cap = AVAudioFrameCount(Double(buf.frameLength) * bi + 64)
        guard let out = AVAudioPCMBuffer(pcmFormat: outFmt, frameCapacity: cap) else { return }
        var juem = false
        var err: NSError?
        let st = cv.convert(to: out, error: &err) { _, inStatus in
            if juem { inStatus.pointee = .noDataNow; return nil }
            juem = true
            inStatus.pointee = .haveData
            return buf
        }
        guard st != .error, let ch = out.int16ChannelData else { return }
        let n = Int(out.frameLength)
        if n > 0 { moum.append(contentsOf: UnsafeBufferPointer(start: ch[0], count: n)) }
        while moum.count >= Sori.JOGAK {
            let jogak = Array(moum[0..<Sori.JOGAK])
            moum.removeFirst(Sori.JOGAK)
            keugiJaegi(jogak)   // 들어오는 그대로 잼(현장 점검)
            jogakdeul.append(JadongKeugi.on ? JadongKeugi.matchugi(jogak) : jogak)   // 1.9.2 소리 자동 맞춤
        }
    }

    /// 들어오는 소리 크기 — 0.1초 조각의 가장 큰 값을 dB로, 3초 동안 가장 큰 것을 남김(안드로이드 1.1.5와 같음)
    private func keugiJaegi(_ b: [Int16]) {
        var m: Int32 = 0
        for v in b { let a = v < 0 ? -Int32(v) : Int32(v); if a > m { m = a } }
        let db = m <= 0 ? -99 : Int(20.0 * log10(Double(m) / 32768.0))
        keugiJul[keugiJari] = db
        keugiJari = (keugiJari + 1) % keugiJul.count
        Bang.shared.sorikeugiNeogi(keugiJul.max() ?? -99)
    }

    // MARK: 장치 이름과 할 일

    /// 지금 실제로 소리를 받는 장치 — 종류와 제품 이름
    static func jangchiIreum() -> String {
        guard let p = AVAudioSession.sharedInstance().currentRoute.inputs.first else { return "아이폰 마이크" }
        switch p.portType {
        case .builtInMic: return "아이폰 마이크"
        case .usbAudio: return p.portName.isEmpty ? "USB 사운드카드" : "USB 사운드카드(\(p.portName))"
        case .headsetMic: return "이어폰 마이크 단자"
        case .lineIn: return "라인 단자"
        case .bluetoothHFP: return "블루투스"
        default: return p.portName.isEmpty ? "기타 장치" : "기타 장치(\(p.portName))"
        }
    }

    static func usbInneunga() -> Bool {
        (AVAudioSession.sharedInstance().availableInputs ?? []).contains(where: { $0.portType == .usbAudio })
    }

    /// 사운드카드가 안 잡혔을 때 할 일 한 줄 — 없으면 빈 글
    static func haljilMal() -> String {
        let jigeum = AVAudioSession.sharedInstance().currentRoute.inputs.first?.portType
        if let j = jigeum, j != .builtInMic { return "" }
        if usbInneunga() {
            return "소리 장치: 사운드카드는 보이는데 아이폰 마이크로 받고 있습니다. 방송을 멈추고 어댑터를 아이폰에서 뺐다가 다시 꽂은 뒤 시작하십시오."
        }
        return "소리 장치: 사운드카드가 보이지 않아 아이폰 마이크로 받습니다. 카메라 어댑터를 아이폰에 끝까지 꽂고, 사운드카드를 어댑터에 꽂으십시오. 어댑터의 충전 구멍에 충전기를 꽂아야 사운드카드가 켜지는 일이 많습니다."
    }

    /// 기기 점검 — 아이폰이 무엇을 보는지 한 줄씩
    static func gigiJeomgeom() -> String {
        let s = AVAudioSession.sharedInstance()
        func jongryu(_ t: AVAudioSession.Port) -> String {
            switch t {
            case .builtInMic: return "아이폰 마이크"
            case .builtInSpeaker: return "아이폰 스피커"
            case .builtInReceiver: return "아이폰 수화기"
            case .usbAudio: return "USB 소리 장치"
            case .headsetMic: return "이어폰 마이크"
            case .headphones: return "이어폰"
            case .lineIn: return "라인 입력"
            case .lineOut: return "라인 출력"
            case .bluetoothHFP, .bluetoothA2DP, .bluetoothLE: return "블루투스"
            default: return "기타"
            }
        }
        var t = "LVD 송출 아이폰 \(Bang.PAN)판, 빌드 \(Bang.BILD)\n"
        let b = Bang.shared
        t += "방송: " + (b.kyeojim ? "켜짐, 지금 받는 곳 \(b.sojae)" : "꺼짐") + "\n"
        t += "판단: " + (usbInneunga() ? "사운드카드가 소리 장치로 잡혔습니다. 정상입니다." :
            "사운드카드가 보이지 않습니다. 카메라 어댑터를 끝까지 꽂았는지, 사운드카드가 어댑터에 꽂혔는지, 어댑터 충전 구멍에 충전기가 꽂혔는지 확인하십시오.") + "\n"
        t += "소리 받을 수 있는 장치:\n"
        for p in s.availableInputs ?? [] { t += "  \(jongryu(p.portType)), \(p.portName)\n" }
        t += "지금 받는 장치:\n"
        for p in s.currentRoute.inputs { t += "  \(jongryu(p.portType)), \(p.portName)\n" }
        t += "지금 내는 장치:\n"
        for p in s.currentRoute.outputs { t += "  \(jongryu(p.portType)), \(p.portName)\n" }
        t += "연결:\n"
        for (nm, ip) in Juso.moduJuso() { t += "  \(nm) \(ip)\n" }
        return t
    }
}

/// 1.3.1 (261015-BI5) 통화 중 듣는 분께 보낼 소리(뮤 8비트, 1초에 16000)
enum Gidarim {
    /// gidarim.wav(16비트, 1초에 16000, 한 줄기)를 뮤 8비트로
    private static let sori: Data = {
        guard let u = Bundle.main.url(forResource: "gidarim", withExtension: "wav"), let d = try? Data(contentsOf: u) else { return Data() }
        let b = [UInt8](d)
        var i = 12, sijak = 44, gil = max(0, b.count - 44)
        while i + 8 <= b.count {
            let ireum = String(bytes: b[i..<i + 4], encoding: .ascii) ?? ""
            let n = Int(b[i + 4]) | Int(b[i + 5]) << 8 | Int(b[i + 6]) << 16 | Int(b[i + 7]) << 24
            if ireum == "data" { sijak = i + 8; gil = min(n, b.count - sijak); break }
            i += 8 + n
        }
        var pcm = [Int16](repeating: 0, count: gil / 2)
        for k in 0..<pcm.count { pcm[k] = Int16(bitPattern: UInt16(b[sijak + 2 * k]) | UInt16(b[sijak + 2 * k + 1]) << 8) }
        return MuLaw.jurigi(pcm)
    }()
    private static var jari = 0
    private static let SWIM = 16000 * 15
    private static let jamgeum = NSLock()
    static func cheoeum() { jamgeum.lock(); jari = 0; jamgeum.unlock() }
    static func jogak(_ n: Int) -> Data {
        jamgeum.lock(); defer { jamgeum.unlock() }
        let s = [UInt8](sori)
        let gil = s.count + SWIM
        var out = [UInt8](repeating: 0xFF, count: n)
        for i in 0..<n {
            let k = jari % gil
            if k < s.count { out[i] = s[k] }
            jari += 1
        }
        if jari >= gil * 1000 { jari = 0 }
        return Data(out)
    }
}

/// 1.9.2 (261026-BI13, 새방클, 이사장님 지시 2026-10-26 「소리가 너무 작아 현장에서 사용이 불가」) 소리 자동 맞춤 — 안드로이드 1.9.2와 같음.
/// 들어오는 현장영상해설 소리가 작으면 가장 큰 소리가 -6dB쯤 되게 최대 8배까지 키움(커질 때 천천히, 줄일 때 곧바로, 잡음은 더 키우지 않음).
/// 넘치는 큰 소리는 부드럽게 눌러 찢어지지 않게. 더 보기에서 끌 수 있음(처음값 켜짐)
enum JadongKeugi {
    static var on: Bool {
        get { UserDefaults.standard.object(forKey: "jadong_keugi") as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: "jadong_keugi") }
    }
    private static var baesu = 1.0
    static func cheoeum() { baesu = 1.0 }
    static func matchugi(_ b: [Int16]) -> [Int16] {
        var m: Int32 = 0
        for v in b { let a = v < 0 ? -Int32(v) : Int32(v); if a > m { m = a } }
        if m >= 330 {
            let baram = min(8.0, max(1.0, 16384.0 / Double(m)))
            baesu = baram < baesu ? baram : baesu + (baram - baesu) * 0.05
        }
        let g = baesu
        if g <= 1.001 { return b }
        return b.map { v -> Int16 in
            var x = Double(v) * g
            let ax = abs(x)
            if ax > 28000 { x = (x < 0 ? -1 : 1) * min(32767, 28000 + (ax - 28000) * 0.2) }
            return Int16(max(-32768, min(32767, x)))
        }
    }
}
