// 몸통 — 모든 엔진을 한 곳에서 세우고, 1분마다 저절로 저장합니다.
import Foundation
import UIKit

final class Bonche {
    static let shared = Bonche()
    private var sigye: Timer?
    private var sijakham = false

    func sijak() {
        guard !sijakham else { return }
        sijakham = true
        _ = Seoljeong.shared
        _ = SoriEngine.shared
        Girok.shared.sijak()
        Tongsin.shared.sijak()
        YeojeongEngine.shared.bureogi()
        WichiEngine.shared.sijak()
        Girok.shared.namgi("app_sijak", ["pan": Pan.pan, "bild": Pan.bild, "appBild": Pan.appBild])
        // 현장에서는 늘 의외의 일이 생깁니다 — 1분마다 저절로 저장
        sigye = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { [weak self] _ in self?.jeojang() }
        let nc = NotificationCenter.default
        nc.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in
            self?.jeojang()
        }
        nc.addObserver(forName: UIApplication.willTerminateNotification, object: nil, queue: .main) { [weak self] _ in
            self?.jeojang()
        }
    }

    func jeojang() {
        YeojeongEngine.shared.jeojang()
        Girok.shared.jeojang()
    }
}
