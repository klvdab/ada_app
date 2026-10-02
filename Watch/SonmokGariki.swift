// 손목 가리키기 — 길눈 2.36.0 (빌드 261002-4, 대표님 승인 2026-10-02)
// 지팡이를 쥐지 않은 손에 워치를 차셨을 때, 점지도 따라 걷는 중 그 팔을 손등이 위로 오게 앞으로 뻗어 가리키시면
//   팔이 가야 할 쪽을 가리키는 순간 "맞음" 진동(톡 하고 굵게)을 1초마다,
//   어긋나면 팔을 옮길 쪽을 진동으로 — 오른쪽은 길게 한 번, 왼쪽은 짧게 두 번(길눈 방향 진동과 같은 무늬)
//   팔을 뻗는 순간 한 번만 "맞습니다", "오른쪽으로", "왼쪽으로"라고 말함. 팔을 내리면 조용해짐.
// 가야 할 쪽은 폰 길눈이 보냄(돌아야 할 때는 돌 쪽, 아니면 앞 6미터의 점지도 방향). 팔 방향은 워치 나침반으로 잼.
// 팔 방향과 나침반의 어긋남은 손목에 따라 기본값(왼손목 +90도, 오른손목 -90도)을 쓰고, "가리키기 방향 맞추기"로 한 번 바로잡으면 그 값을 씀.
import Foundation
import CoreLocation
import CoreMotion
import WatchKit

final class SonmokGariki: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let shared = SonmokGariki()
    /// 폰이 보낸 가야 할 쪽(진북 기준 도). 없으면 쉼
    @Published private(set) var mok: Double?
    @Published var kyeojim: Bool = UserDefaults.standard.object(forKey: "garikiOn") as? Bool ?? true {
        didSet { UserDefaults.standard.set(kyeojim, forKey: "garikiOn"); dasiJeonghagi() }
    }
    private let loc = CLLocationManager()
    private let umjik = CMMotionManager()
    private var heading: Double?
    private var dolgo = false
    private var ppeotT: Date?           // 팔을 뻗기 시작한 때
    private var ppeotMal = false        // 이번에 뻗은 뒤 말했는가
    private var jindongT = Date.distantPast

    override init() {
        super.init()
        loc.delegate = self
        loc.headingFilter = 2
    }

    /// 지팡이를 쥐지 않은 손에 차셨는가(묻기에 답하신 것으로)
    var sseulSuItda: Bool {
        let j = UserDefaults.standard.string(forKey: "jipangiSon") ?? ""
        let w = UserDefaults.standard.string(forKey: "watchSonmok") ?? ""
        return !j.isEmpty && !w.isEmpty && j != w
    }

    /// 팔 방향 = 나침반 + 어긋남
    private var eogeutnam: Double {
        if let v = UserDefaults.standard.object(forKey: "garikiPyeon") as? Double { return v }
        return WKInterfaceDevice.current().wristLocation == .right ? -90 : 90
    }

    /// 폰 길눈이 보낸 가야 할 쪽 — nil 이면 걷기가 끝난 것
    func mokBatda(_ b: Double?) {
        mok = b
        dasiJeonghagi()
    }

    private func dasiJeonghagi() {
        let halil = kyeojim && sseulSuItda && mok != nil && CLLocationManager.headingAvailable()
        if halil && !dolgo { kyeogi() } else if !halil && dolgo { kkeugi() }
    }

    private func kyeogi() {
        dolgo = true
        loc.startUpdatingHeading()
        guard umjik.isDeviceMotionAvailable else { return }
        umjik.deviceMotionUpdateInterval = 0.1
        umjik.startDeviceMotionUpdates(to: .main) { [weak self] d, _ in
            guard let self = self, let g = d?.gravity else { return }
            self.salpigi(g.z)
        }
    }

    private func kkeugi() {
        dolgo = false
        loc.stopUpdatingHeading()
        umjik.stopDeviceMotionUpdates()
        ppeotT = nil; ppeotMal = false
    }

    func locationManager(_ manager: CLLocationManager, didUpdateHeading h: CLHeading) {
        guard h.headingAccuracy >= 0 else { return }
        heading = h.trueHeading >= 0 ? h.trueHeading : h.magneticHeading
    }

    static func chai(_ a: Double, _ b: Double) -> Double {
        var d = (a - b).truncatingRemainder(dividingBy: 360)
        if d > 180 { d -= 360 }
        if d < -180 { d += 360 }
        return d
    }

    /// 0.1초마다 — 손등이 위로(화면이 하늘로) 0.4초 넘게 있으면 가리키는 중
    private func salpigi(_ gz: Double) {
        let now = Date()
        guard gz < -0.8 else { ppeotT = nil; ppeotMal = false; return }
        if ppeotT == nil { ppeotT = now }
        guard let t0 = ppeotT, now.timeIntervalSince(t0) >= 0.4, let m = mok, let h = heading else { return }
        let pal = (h + eogeutnam + 720).truncatingRemainder(dividingBy: 360)
        let d = SonmokGariki.chai(m, pal)   // + 이면 가야 할 쪽이 팔보다 오른쪽
        let dev = WKInterfaceDevice.current()
        let maja = abs(d) <= 12
        if !ppeotMal {
            ppeotMal = true
            WatchModel.shared.speak(maja ? "맞습니다" : (d > 0 ? "오른쪽으로" : "왼쪽으로"), jindong: .click)
            jindongT = now
            return
        }
        let gan: Double = maja ? 1.0 : 0.7
        guard now.timeIntervalSince(jindongT) >= gan else { return }
        jindongT = now
        if maja {
            dev.play(.success)
        } else if d > 0 {
            dev.play(.directionUp)
        } else {
            dev.play(.directionDown)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { dev.play(.directionDown) }
        }
    }

    /// 가리키기 방향 맞추기 — 3초 뒤 팔을 몸 정면으로 곧게 뻗은 채로 폰이 잰 몸 방향과 워치 나침반을 맞춤
    func majchugi(_ phonBang: @escaping (@escaping (Double?) -> Void) -> Void) {
        WatchModel.shared.speak("이 말이 끝나고 곧 잽니다. 워치 찬 팔을 손등이 위로 오게 몸 정면으로 곧게 뻗고 기다리십시오.", jindong: .start)
        loc.startUpdatingHeading()
        if !umjik.isDeviceMotionActive && umjik.isDeviceMotionAvailable {
            umjik.deviceMotionUpdateInterval = 0.1
            umjik.startDeviceMotionUpdates()
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 6) { [weak self] in
            guard let self = self else { return }
            let gz = self.umjik.deviceMotion?.gravity.z ?? 0
            if !self.dolgo { self.loc.stopUpdatingHeading(); self.umjik.stopDeviceMotionUpdates() }
            guard gz < -0.8, let h = self.heading else {
                WatchModel.shared.speak("팔이 곧게 뻗어 있지 않아 재지 못했습니다. 손등이 위로 오게 하고 다시 해 주십시오.", jindong: .failure); return
            }
            phonBang { b in
                DispatchQueue.main.async {
                    guard let b = b else {
                        WatchModel.shared.speak("폰 길눈에서 몸 방향을 받지 못했습니다. 폰 길눈으로 점지도 따라 걷기를 켜고 다시 해 주십시오.", jindong: .failure); return
                    }
                    let p = SonmokGariki.chai(b, h)
                    UserDefaults.standard.set(p, forKey: "garikiPyeon")
                    WatchModel.shared.speak("가리키기 방향을 맞췄습니다.", jindong: .success)
                }
            }
        }
    }
}
