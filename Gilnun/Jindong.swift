// 진동 무늬 — 왼쪽은 짧게 두 번, 오른쪽은 길게 한 번, 도착은 세 번, 뒤쪽은 길게, 그 밖은 짧게 한 번.
// 앱 2.6.0 (빌드 260928-8) — 옛 앱(260927-2, 이사장님 승인)의 방향 진동을 새 앱으로. 폰과 워치가 같은 무늬로 울림.
import UIKit
import CoreHaptics

enum Jindong {
    private static var engine: CHHapticEngine? = {
        guard CHHapticEngine.capabilitiesForHardware().supportsHaptics else { return nil }
        let e = try? CHHapticEngine()
        e?.resetHandler = { try? e?.start() }
        try? e?.start()
        return e
    }()

    /// 시계 방향으로 — 1~5시는 오른쪽, 7~11시는 왼쪽, 6시는 뒤, 12시(와 11·1시)는 곧게라 울리지 않음
    static func banghyang(_ s: Int) {
        let mu: String
        switch s {
        case 2...5: mu = "right"
        case 7...10: mu = "left"
        case 6: mu = "long"
        default: return
        }
        hagi(mu)
        WatchLink.shared.jindongBonae(mu)
    }

    /// 도착 — 폰과 워치 함께
    static func dochak() {
        hagi("arrive")
        WatchLink.shared.jindongBonae("arrive")
    }

    static func hagi(_ mu: String) {
        DispatchQueue.main.async {
            let muni: [(Double, Double)]   // (시작, 길이)
            switch mu {
            case "left": muni = [(0, 0.12), (0.25, 0.12)]
            case "right": muni = [(0, 0.6)]
            case "arrive": muni = [(0, 0.15), (0.3, 0.15), (0.6, 0.15)]
            case "long": muni = [(0, 0.8)]
            default: muni = [(0, 0.12)]
            }
            guard let e = engine else { daesin(mu); return }
            var ev: [CHHapticEvent] = []
            for (t, d) in muni {
                ev.append(CHHapticEvent(eventType: .hapticContinuous,
                                        parameters: [CHHapticEventParameter(parameterID: .hapticIntensity, value: 1.0),
                                                     CHHapticEventParameter(parameterID: .hapticSharpness, value: 0.6)],
                                        relativeTime: t, duration: d))
            }
            do {
                let p = try CHHapticPattern(events: ev, parameters: [])
                try e.start()
                try e.makePlayer(with: p).start(atTime: 0)
            } catch {
                daesin(mu)
            }
        }
    }

    private static func daesin(_ mu: String) {
        switch mu {
        case "arrive": UINotificationFeedbackGenerator().notificationOccurred(.success)
        case "right", "long": UINotificationFeedbackGenerator().notificationOccurred(.warning)
        default: UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        }
    }
}
