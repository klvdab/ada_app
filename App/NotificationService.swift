// 알림 — 잠긴 폰과 애플워치로 안내를 띄웁니다. 웹의 조건(홈 화면 앱·16.4)이 없습니다.
import UserNotifications
import UIKit

final class NotificationService: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationService()
    var onTap: ((String) -> Void)?

    func setup() {
        UNUserNotificationCenter.current().delegate = self
    }

    func requestPermission(_ done: @escaping (Bool) -> Void) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { ok, _ in
            DispatchQueue.main.async { done(ok) }
        }
    }

    func show(title: String, body: String, url: String, tag: String) {
        let c = UNMutableNotificationContent()
        c.title = title
        c.body = body
        c.sound = .default
        c.userInfo = ["url": url]
        c.threadIdentifier = tag
        c.interruptionLevel = .timeSensitive
        let req = UNNotificationRequest(identifier: tag, content: c, trigger: nil)   // 같은 tag 는 갈아끼움
        UNUserNotificationCenter.current().add(req, withCompletionHandler: nil)
    }

    // 앱이 켜져 있어도 알림을 띄울지 — 화면이 보이면 웹이 이미 읽으므로 띄우지 않음
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler(UIApplication.shared.applicationState == .active ? [] : [.banner, .sound])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        onTap?((response.notification.request.content.userInfo["url"] as? String) ?? "")
        completionHandler()
    }
}
