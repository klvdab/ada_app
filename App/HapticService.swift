// 진동 무늬 — 왼쪽은 짧게 두 번, 오른쪽은 길게 한 번, 도착은 세 번, 그 밖은 한 번.
import UIKit
import CoreHaptics

enum HapticService {
    private static var engine: CHHapticEngine? = {
        guard CHHapticEngine.capabilitiesForHardware().supportsHaptics else { return nil }
        let e = try? CHHapticEngine()
        try? e?.start()
        return e
    }()

    static func play(_ mu: String) {
        let pattern: [(Double, Double)]   // (시작 시각, 길이)
        switch mu {
        case "left":   pattern = [(0, 0.12), (0.25, 0.12)]
        case "right":  pattern = [(0, 0.6)]
        case "arrive": pattern = [(0, 0.15), (0.3, 0.15), (0.6, 0.15)]
        case "long":   pattern = [(0, 0.8)]
        default:       pattern = [(0, 0.12)]
        }
        guard let engine = engine else { fallback(mu); return }
        var events: [CHHapticEvent] = []
        for (t, d) in pattern {
            events.append(CHHapticEvent(eventType: .hapticContinuous,
                                        parameters: [CHHapticEventParameter(parameterID: .hapticIntensity, value: 1.0),
                                                     CHHapticEventParameter(parameterID: .hapticSharpness, value: 0.6)],
                                        relativeTime: t, duration: d))
        }
        do {
            let p = try CHHapticPattern(events: events, parameters: [])
            let player = try engine.makePlayer(with: p)
            try engine.start()
            try player.start(atTime: 0)
        } catch { fallback(mu) }
    }

    private static func fallback(_ mu: String) {
        let g = UINotificationFeedbackGenerator()
        switch mu {
        case "arrive": g.notificationOccurred(.success)
        case "right", "long": g.notificationOccurred(.warning)
        default: UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        }
    }
}
