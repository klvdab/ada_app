// 워치 쪽 두뇌 — 폰에서 받은 마지막 안내·다음 갈림길을 간직하고, 폰이 없으면 서버(watch.php)에 직접 묻습니다.
import Foundation
import WatchConnectivity
import WatchKit
import AVFoundation
import CoreLocation
import CoreMotion

final class WatchModel: NSObject, ObservableObject, WCSessionDelegate, CLLocationManagerDelegate, WKExtendedRuntimeSessionDelegate {
    static let shared = WatchModel()
    @Published var mal = "아직 받은 안내가 없습니다."
    @Published var daeum = ""
    @Published var ttae: Double = 0
    @Published var jari = ""
    @Published var dapMal = ""
    /// 2.34.0 폰 길눈이 보낸 동영상 — 오면 워치에서 곧바로 틂
    @Published var dongyeong: WatchDongyeong?
    private let synth = AVSpeechSynthesizer()
    private let loc = CLLocationManager()
    private var jariDone: ((String) -> Void)?
    private let WURL = "https://lvd.ada.or.kr/jeom/watch.php"
    // 2.33.0 두 번 집기 횟수 세기
    private var jipgiSu = 0
    private var jipgiSigye: Timer?
    // 2.35.0 (빌드 261002-3, 대표님 승인) 걷는 동안 깨어 있기 + 팔 흔들림 걸음 세기
    //   폰 길눈이 점지도 따라 걷기를 시작하면 워치도 깨어 있는 운동 시간(물리 치료 갈래, 길게 한 시간)을 열어
    //   손목을 내려도 꺼지지 않고, 워치의 걸음 세기(팔 흔들림)를 폰에 보냄. 폰이 가방 속이라 걸음을 못 셀 때 폰이 이것으로 이어 감.
    @Published var kkaeeoItda = false
    private let manbo = CMPedometer()
    private var gilSession: WKExtendedRuntimeSession?
    private var majimakBonaen = -1
    private var ponGeotneun = false   // 폰이 알린 걷는 중 — 바뀔 때만 따름
    private var tteollimKyeom = false  // 2.37.0 지팡이 떨림 기록이 깨어 있기를 켰는가

    override init() {
        super.init()
        if WCSession.isSupported() { WCSession.default.delegate = self; WCSession.default.activate() }
        loc.delegate = self
        loc.desiredAccuracy = kCLLocationAccuracyBest
    }

    // 말하기 + 진동
    func speak(_ t: String, jindong: WKHapticType = .click) {
        WKInterfaceDevice.current().play(jindong)
        synth.stopSpeaking(at: .immediate)
        let u = AVSpeechUtterance(string: t)
        u.voice = AVSpeechSynthesisVoice(language: "ko-KR")
        u.rate = 0.55
        synth.speak(u)
    }

    // 단추 셋
    func malDeutgi() {
        askPhone("mal")
        speak(mal.isEmpty ? "아직 받은 안내가 없습니다." : mal)
    }

    func daeumDeutgi() {
        askPhone("daeum")
        if !daeum.isEmpty { speak(daeum, jindong: .directionUp); return }
        speak("다음 갈림길을 폰에서 받아 오는 중입니다.")
    }

    // 2.33.0 (빌드 261002-1, 대표님 승인) 손가락 두 번 집기 — 1.5초 안에 잇달아 한 횟수로 나눔
    //   집을 때마다 한 번 짧게 떨어 몇 번 셌는지 손목으로 알게 함. 세 번이면 더 기다리지 않고 곧바로.
    func jipgi() {
        jipgiSu += 1
        WKInterfaceDevice.current().play(.click)
        jipgiSigye?.invalidate()
        if jipgiSu >= 3 { jipgiKkeut(); return }
        jipgiSigye = Timer.scheduledTimer(withTimeInterval: 1.5, repeats: false) { [weak self] _ in self?.jipgiKkeut() }
    }

    private func jipgiKkeut() {
        let n = jipgiSu
        jipgiSu = 0
        jipgiSigye?.invalidate(); jipgiSigye = nil
        switch n {
        case 1: daeumDeutgi()
        case 2: jariDeutgi()
        default: malSijak()
        }
    }

    /// 2.33.0 집기 세 번 — 폰 길눈이 말로 하기 듣기를 엶(폰의 마이크나 이어폰으로 말씀하시면 됨)
    func malSijak() {
        guard WCSession.isSupported(), WCSession.default.isReachable else {
            speak("폰의 길눈과 이어져 있지 않습니다. 폰에서 길눈을 열어 주십시오.", jindong: .failure); return
        }
        WCSession.default.sendMessage(["what": "malhagiSijak"], replyHandler: { [weak self] r in
            DispatchQueue.main.async {
                WKInterfaceDevice.current().play(.start)
                if let d = r["dapMal"] as? String { self?.dapMal = d }
            }
        }, errorHandler: { [weak self] _ in
            DispatchQueue.main.async { self?.speak("폰에 요청을 보내지 못했습니다. 다시 해 주십시오.", jindong: .failure) }
        })
    }

    // 260927-3 음향신호기 — 폰이 블루투스로 가까운 음향신호기를 울림 (1 위치안내 / 2 신호안내)
    func sinhogi(_ cmd: Int) {
        guard WCSession.isSupported(), WCSession.default.isReachable else {
            speak("폰의 길눈과 이어져 있지 않습니다. 폰에서 길눈을 열어 주십시오.", jindong: .failure); return
        }
        WKInterfaceDevice.current().play(.start)
        WCSession.default.sendMessage(["what": "sinhogi", "cmd": cmd], replyHandler: { [weak self] r in
            DispatchQueue.main.async { self?.apply(r) }
        }, errorHandler: { [weak self] _ in
            DispatchQueue.main.async { self?.speak("폰에 요청을 보내지 못했습니다. 다시 눌러 주십시오.", jindong: .failure) }
        })
    }

    // 2.6.0 (빌드 260928-8) 말로 하기 — 손목에 말씀하신 글을 폰 길눈에 넘기고, 폰이 대답을 말함(워치는 진동과 글로)
    func malhagi(_ t: String) {
        let s = t.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !s.isEmpty else { return }
        guard WCSession.isSupported(), WCSession.default.isReachable else {
            speak("폰의 길눈과 이어져 있지 않습니다. 폰에서 길눈을 열어 주십시오.", jindong: .failure); return
        }
        WKInterfaceDevice.current().play(.start)
        dapMal = "폰 길눈이 알아보는 중입니다."
        WCSession.default.sendMessage(["what": "malhagi", "t": s], replyHandler: { [weak self] r in
            DispatchQueue.main.async {
                self?.dapMal = (r["dapMal"] as? String) ?? ""
                WKInterfaceDevice.current().play(.success)
            }
        }, errorHandler: { [weak self] _ in
            DispatchQueue.main.async { self?.speak("폰에 말씀을 보내지 못했습니다. 다시 해 주십시오.", jindong: .failure) }
        })
    }

    func jariDeutgi() {
        speak("내 자리를 찾는 중입니다.", jindong: .start)
        jariDone = { [weak self] s in self?.jari = s; self?.speak(s, jindong: .success) }
        if loc.authorizationStatus == .notDetermined { loc.requestWhenInUseAuthorization() }
        loc.requestLocation()
    }

    // 폰에 부탁 — 폰이 곁에 있으면 폰의 길눈이 답하고, 없으면 서버에서 마지막 것을 받아 옵니다
    private func askPhone(_ what: String) {
        guard WCSession.isSupported(), WCSession.default.isReachable else { fetchServer(what); return }
        WCSession.default.sendMessage(["what": what], replyHandler: { [weak self] r in
            DispatchQueue.main.async { self?.apply(r) }
        }, errorHandler: { [weak self] _ in self?.fetchServer(what) })
    }

    private func fetchServer(_ what: String) {
        guard let k = UserDefaults.standard.string(forKey: "watchBeonho"), !k.isEmpty,
              let u = URL(string: "\(WURL)?a=deut&k=\(k)&what=\(what)") else { return }
        URLSession.shared.dataTask(with: u) { [weak self] d, _, _ in
            guard let d = d, let s = String(data: d, encoding: .utf8) else { return }
            DispatchQueue.main.async {
                if what == "mal" { self?.mal = s } else { self?.daeum = s }
                self?.speak(s)
            }
        }.resume()
    }

    private func apply(_ r: [String: Any]) {
        if let g = r["geotneun"] as? Bool, g != ponGeotneun {
            ponGeotneun = g
            g ? geotgiKyeogi() : geotgiKkeugi()
            if !g { SonmokGariki.shared.mokBatda(nil) }
        }
        // 2.36.0 손목 가리키기 — 폰이 보낸 가야 할 쪽(음수면 없음)
        if let b = r["gariki"] as? Double { SonmokGariki.shared.mokBatda(b >= 0 ? b : nil) }
        if let m = r["mal"] as? String, !m.isEmpty { mal = m }
        if let d = r["daeum"] as? String { daeum = d }
        if let t = r["ttae"] as? Double { ttae = t }
        if let k = r["watchBeonho"] as? String { UserDefaults.standard.set(k, forKey: "watchBeonho") }
        if let mu = r["jindong"] as? String { jindongHagi(mu) }
        if (r["what"] as? String) == "dongyeongJuso", let s = r["u"] as? String, let u = URL(string: s) { dongyeongTeulgi(u) }
        if let s = r["sinhogiMal"] as? String, !s.isEmpty {
            let ok = (r["sinhogiOk"] as? Bool) ?? true
            speak(s, jindong: r["sinhogiOk"] == nil ? .click : (ok ? .success : .failure))
        }
    }

    // 진동 무늬: 왼쪽 짧게 두 번, 오른쬭 길게 한 번, 도착 세 번
    func jindongHagi(_ mu: String) {
        let dev = WKInterfaceDevice.current()
        switch mu {
        case "left":   dev.play(.directionDown); DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { dev.play(.directionDown) }
        case "right":  dev.play(.directionUp)
        case "arrive": dev.play(.success); DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { dev.play(.success) }
        default:       dev.play(.click)
        }
    }

    // 폰 → 워치
    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        DispatchQueue.main.async { self.apply(session.receivedApplicationContext) }
    }
    func session(_ session: WCSession, didReceiveApplicationContext ctx: [String: Any]) {
        DispatchQueue.main.async {
            let before = self.mal
            self.apply(ctx)
            if self.mal != before, let m = ctx["mal"] as? String, !m.isEmpty { WKInterfaceDevice.current().play(.notification) }
        }
    }
    func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        DispatchQueue.main.async { self.apply(message) }
    }

    // 2.34.0 (빌드 261002-2, 대표님 승인) 동영상 — 폰이 보낸 파일·주소를 워치에서 틂
    func session(_ session: WCSession, didReceive file: WCSessionFile) {
        guard (file.metadata?["what"] as? String) == "dongyeong" else { return }
        // 받은 파일은 이 함수가 끝나면 지워지므로 곧바로 옮김. 앞서 받은 동영상은 지움(워치 저장 공간이 작음)
        let d = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("dongyeong", isDirectory: true)
        try? FileManager.default.removeItem(at: d)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        let ext = file.fileURL.pathExtension.isEmpty ? "mp4" : file.fileURL.pathExtension
        let mok = d.appendingPathComponent("dongyeong_\(Int(Date().timeIntervalSince1970)).\(ext)")
        do { try FileManager.default.moveItem(at: file.fileURL, to: mok) } catch { return }
        DispatchQueue.main.async { self.dongyeongTeulgi(mok) }
    }
    // 2.37.0 지팡이 떨림 기록이 폰에 다 넘어가면 워치에서 지움
    func session(_ session: WCSession, didFinish fileTransfer: WCSessionFileTransfer, error: Error?) {
        guard error == nil, (fileTransfer.file.metadata?["what"] as? String) == "tteollim" else { return }
        JipangiTteollim.shared.neomeoganGeotJiugi(fileTransfer.file.fileURL)
    }
    func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any] = [:]) {
        DispatchQueue.main.async { self.apply(userInfo) }
    }

    func dongyeongTeulgi(_ u: URL) {
        WKInterfaceDevice.current().play(.notification)
        dongyeong = WatchDongyeong(url: u)
    }

    // MARK: 2.35.0 걷는 동안 깨어 있기

    /// 폰이 걷기를 시작했다고 알리거나, 손목에서 "걷는 동안 깨어 있기"를 누르면
    func geotgiKyeogi() {
        if gilSession == nil {
            let s = WKExtendedRuntimeSession()
            s.delegate = self
            s.start()   // 워치 길눈이 화면에 떠 있을 때만 열림 — 못 열리면 화면에 다시 뜰 때 한 번 더
            gilSession = s
        }
        if CMPedometer.isStepCountingAvailable() {
            majimakBonaen = -1
            manbo.stopUpdates()
            manbo.startUpdates(from: Date()) { [weak self] d, _ in
                guard let d = d else { return }
                DispatchQueue.main.async { self?.georeumBonae(d.numberOfSteps.intValue) }
            }
        }
    }

    func geotgiKkeugi() {
        gilSession?.invalidate()
        gilSession = nil
        manbo.stopUpdates()
        kkaeeoItda = false
    }

    /// 2.37.0 지팡이 떨림 기록 중에는 손목을 내려도 멈추지 않게 — 걸음 세기는 건드리지 않음
    func kkaeeoBojang(_ on: Bool) {
        if on {
            guard gilSession == nil else { return }
            let s = WKExtendedRuntimeSession()
            s.delegate = self
            s.start()
            gilSession = s
            tteollimKyeom = true
        } else if tteollimKyeom {
            tteollimKyeom = false
            if !ponGeotneun { gilSession?.invalidate(); gilSession = nil; kkaeeoItda = false }
        }
    }

    /// 손목의 단추 — 켜져 있으면 끄고, 꺼져 있으면 켬
    func kkaeeoDanchu() {
        if kkaeeoItda || gilSession != nil {
            geotgiKkeugi(); speak("걷는 동안 깨어 있기를 껐습니다.")
        } else {
            geotgiKyeogi(); speak("걷는 동안 워치가 깨어 있고, 팔 흔들림으로 걸음을 세어 폰 길눈에 보냅니다.", jindong: .start)
        }
    }

    /// 워치 길눈이 화면에 다시 떴을 때 — 폰이 걷는 중인데 깨어 있지 못하면 다시 엶
    func hwamyeonDolawa() {
        if ponGeotneun && gilSession == nil { geotgiKyeogi() }
    }

    /// 2.36.0 가리키기 방향 맞추기 — 폰 길눈에게 지금 몸이 향한 방향을 물음
    func momBangMureum(_ dap: @escaping (Double?) -> Void) {
        guard WCSession.isSupported(), WCSession.default.isReachable else { dap(nil); return }
        WCSession.default.sendMessage(["what": "momBang"], replyHandler: { r in
            let b = r["momBang"] as? Double ?? -1
            dap(b >= 0 ? b : nil)
        }, errorHandler: { _ in dap(nil) })
    }

    private func georeumBonae(_ n: Int) {
        guard n != majimakBonaen, WCSession.isSupported(), WCSession.default.isReachable else { return }
        majimakBonaen = n
        WCSession.default.sendMessage(["what": "watchGeoreum", "n": n], replyHandler: nil, errorHandler: nil)
    }

    func extendedRuntimeSessionDidStart(_ s: WKExtendedRuntimeSession) {
        DispatchQueue.main.async { self.kkaeeoItda = true }
    }
    func extendedRuntimeSessionWillExpire(_ s: WKExtendedRuntimeSession) {
        // 한 시간이 다 되어 감 — 손목으로 알려 드림(화면에 길눈을 띄우시면 다시 열림)
        DispatchQueue.main.async { self.speak("워치 깨어 있기 시간이 곧 끝납니다. 길눈 워치 화면을 한 번 여시면 다시 이어집니다.", jindong: .retry) }
    }
    func extendedRuntimeSession(_ s: WKExtendedRuntimeSession, didInvalidateWith reason: WKExtendedRuntimeSessionInvalidationReason, error: Error?) {
        DispatchQueue.main.async {
            if self.gilSession === s { self.gilSession = nil }
            self.kkaeeoItda = false
        }
    }

    // 내 자리 — 워치 GPS 로 서버에 물음
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let l = locations.last,
              let u = URL(string: "\(WURL)?a=jari&lat=\(l.coordinate.latitude)&lon=\(l.coordinate.longitude)") else { return }
        URLSession.shared.dataTask(with: u) { [weak self] d, _, _ in
            let s = (d.flatMap { String(data: $0, encoding: .utf8) }) ?? "자리를 알아내지 못했습니다."
            DispatchQueue.main.async { self?.jariDone?(s) }
        }.resume()
    }
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        DispatchQueue.main.async { self.jariDone?("위치를 잡지 못했습니다. 하늘이 보이는 곳에서 다시 눌러 주십시오.") }
    }
}
