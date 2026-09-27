// 진단 기록 (1.0판, 빌드 260927-6, 이사장님 승인 2026-09-27)
// 이사장님 폰에서 실제로 무슨 일이 있었는지 클이 볼 수 있게, 마이크가 열리고 닫힌 때와 소리 설정이 바뀐 때를 서버에 남깁니다.
// 말씀하신 내용(받아쓴 글)은 남기지 않고 글자 수만 남깁니다. 5초마다 또는 20건이 모이면 한 번에 보냅니다.
// 받는 곳: https://lvd.ada.or.kr/jeom/applog.php  (나스 /volume1/lvt_data/applog/날짜.txt)
import Foundation
import UIKit

final class DiagLog {
    static let shared = DiagLog()
    private let url = URL(string: "https://lvd.ada.or.kr/jeom/applog.php")!
    private var jul: [[String: Any]] = []
    private var timer: Timer?
    private let q = DispatchQueue(label: "kr.or.ada.diaglog")
    private let ios = UIDevice.current.systemVersion
    private let dev: String = {
        let k = "adaDiagDev"
        if let v = UserDefaults.standard.string(forKey: k) { return v }
        let v = String(UUID().uuidString.prefix(8))
        UserDefaults.standard.set(v, forKey: k)
        return v
    }()

    func log(_ e: String, _ d: [String: Any] = [:]) {
        var row: [String: Any] = ["t": Date().timeIntervalSince1970, "e": e]
        for (k, v) in d where JSONSerialization.isValidJSONObject([k: v]) { row[k] = v }
        q.async {
            self.jul.append(row)
            if self.jul.count > 200 { self.jul.removeFirst(self.jul.count - 200) }
            if self.jul.count >= 20 { self.bonaegi() }
        }
        DispatchQueue.main.async {
            if self.timer == nil {
                self.timer = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { [weak self] _ in self?.q.async { self?.bonaegi() } }
            }
        }
    }

    private func bonaegi() {
        guard !jul.isEmpty else { return }
        let rows = jul; jul = []
        let body: [String: Any] = ["dev": dev, "app": AppInfo.pan + "(" + AppInfo.build + ")",
                                   "ios": ios, "ev": rows]
        guard let data = try? JSONSerialization.data(withJSONObject: body) else { return }
        var r = URLRequest(url: url)
        r.httpMethod = "POST"
        r.setValue("application/json", forHTTPHeaderField: "Content-Type")
        r.httpBody = data
        r.timeoutInterval = 10
        URLSession.shared.dataTask(with: r).resume()
    }
}
