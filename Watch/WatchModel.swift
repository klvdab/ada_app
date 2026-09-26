// 워치 쪽 두뇌 — 폰에서 받은 마지막 안내·다음 갈림길을 간직하고, 폰이 없으면 서버(watch.php)에 직접 묻습니다.
import Foundation
import WatchConnectivity
import WatchKit
import AVFoundation
import CoreLocation

final class WatchModel: NSObject, ObservableObject, WCSessionDelegate, CLLocationManagerDelegate {
    static let shared = WatchModel()
    @Published var mal = "아직 받은 안내가 없습니다."
    @Published var daeum = ""
    @Published var ttae: Double = 0
    @Published var jari = ""
    private let synth = AVSpeechSynthesizer()
    private let loc = CLLocationManager()
    private var jariDone: ((String) -> Void)?
    private let WURL = "https://lvd.ada.or.kr/jeom/watch.php"

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
        if let m = r["mal"] as? String, !m.isEmpty { mal = m }
        if let d = r["daeum"] as? String { daeum = d }
        if let t = r["ttae"] as? Double { ttae = t }
        if let k = r["watchBeonho"] as? String { UserDefaults.standard.set(k, forKey: "watchBeonho") }
        if let mu = r["jindong"] as? String { jindongHagi(mu) }
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
