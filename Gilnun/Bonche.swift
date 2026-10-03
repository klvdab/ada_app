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
        TalgeotGamji.shared.sijak()  // 2.40.0 탈것 저절로 알아채기(움직임 감지기·기압계, 땅속에서도)
        _ = AnnaeEngine.shared       // 지난 여정이 있으면 안내를 곧장 이어 감
        _ = JeomEngine.shared        // 2.10.0 점지도 따라 걷기
        JeomEngine.shared.ieoGagi()  // 2.11.1 앱이 꺼졌다 켜져도 하던 점지도 따라 걷기를 이어 감
        _ = NaeGil.shared            // 2.10.0 나만의 점지도
        _ = Jeulgyeo.shared
        WatchLink.shared.sijak()     // 워치와 잇기
        SinhogiEngine.shared.sijak() // 음향신호기 자동 울리기(설정에서 끔)
        RemoteDanchu.shared.sijak()  // 안내 중 이어폰 단추
        MalHagi.shared.sijak()       // 말로 하기 — 사전 받기, 하이 길눈을 켜 두셨으면 부름 기다리기
        GojangEngine.shared.sijak()  // 2.7.0 탈것으로 지나는 고장 이야기(설정에서 끔)
        BangsongEngine.shared.sijak()   // 2.8.0 음악·방송 — 멈춤 지킴이, 긴급통화 중 멈춤
        GongjiEngine.shared.sijak()     // 2.9.0 알림 — 15분마다, 긴급 공지는 첫 화면 맨 위
        Heundeul.shared.sijak()         // 2.9.0 흔들면(설정에서 켬)
        JihacheolEngine.shared.ieoGagi()   // 지하철 타고 가던 중이면 역 알림을 이어 감
        DoeEngine.shared.ieoGagi()         // 2.13.0 되짚어 나가기 — 기억하던 중이면 이어 기억
        Nalssi.shared.cheotMal()           // 2.13.0 앱을 켠 뒤 날씨 한 번(웹 길눈과 같이)
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
