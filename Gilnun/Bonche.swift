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
        _ = AnnaeEngine.shared       // 지난 여정이 있으면 안내를 곧장 이어 감
        _ = Jeulgyeo.shared
        WatchLink.shared.sijak()     // 워치와 잇기
        SinhogiEngine.shared.sijak() // 음향신호기 자동 울리기(설정에서 끔)
        RemoteDanchu.shared.sijak()  // 안내 중 이어폰 단추
        MalHagi.shared.sijak()       // 말로 하기 — 사전 받기, 하이 길눈을 켜 두셨으면 부름 기다리기
        GojangEngine.shared.sijak()  // 2.7.0 탈것으로 지나는 고장 이야기(설정에서 끔)
        JihacheolEngine.shared.ieoGagi()   // 지하철 타고 가던 중이면 역 알림을 이어 감
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
