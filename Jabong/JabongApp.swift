// 자봉 앱(점지도그리기) — 속까지 앱으로 다시 짓는 판 (2.0.0, 빌드 261001-7)
// 2026-10-01 대표님 승인: 웹을 띄우던 껍데기 대신 모든 화면을 앱으로. 설계도: 나스 /test/ada_app/자봉앱_설계도_261001.txt
// 길눈 부품 폴더(Gilnun)를 통째로 함께 싣고, 시작 파일만 자봉 것(이 파일)으로 바꿔 짓습니다.
// 2.0.0 기초판: 앱의 틀, 탭 넷, 처음 등록, 위치·말소리·기록, 1분 자동 저장. 2.1.0 긴급통화 받기(전화처럼 울림, 받으면 곧바로 화상통화).
import SwiftUI
import UIKit

@main
struct JabongApp: App {
    @UIApplicationDelegateAdaptor(JabongAppDelegate.self) var delegate
    var body: some Scene {
        WindowGroup {
            JabongRoot()
        }
    }
}

final class JabongAppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        JabongBonche.shared.sijak()
        return true
    }
}

/// 자봉 앱의 몸통 — 길눈 부품 가운데 자봉에 필요한 것만 세우고, 1분마다 저절로 저장
final class JabongBonche {
    static let shared = JabongBonche()
    private var sigye: Timer?
    private var sijakham = false

    func sijak() {
        guard !sijakham else { return }
        sijakham = true
        _ = Seoljeong.shared
        _ = SoriEngine.shared
        Girok.shared.sijak()
        Tongsin.shared.sijak()
        WichiEngine.shared.sijak()
        JabongDaegi.shared.sijak()   // 2.1.0 긴급통화 받기 — 애플 알림(VoIP) 받을 준비
        Task { @MainActor in OllimEngine.shared.sijak() }   // 2.12.0 새 판 알림(이사장님 승인)
        Girok.shared.namgi("jabong_app_sijak", ["pan": JabongPan.pan, "bild": JabongPan.bild, "beonho": JabongNae.shared.beonho])
        // 현장에서는 늘 의외의 일이 생깁니다 — 1분마다 저절로 저장
        sigye = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { [weak self] _ in self?.jeojang() }
        let nc = NotificationCenter.default
        nc.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in self?.jeojang() }
        nc.addObserver(forName: UIApplication.willTerminateNotification, object: nil, queue: .main) { [weak self] _ in self?.jeojang() }
    }

    func jeojang() {
        Girok.shared.jeojang()
        NotificationCenter.default.post(name: JabongBonche.jeojangHal, object: nil)   // 그리기 등이 제 것을 저장
    }
    static let jeojangHal = Notification.Name("jabong.jeojang")
}
