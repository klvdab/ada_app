// LVD 방송 아이폰(듣는 앱) — 행사장 와이파이 저절로 붙기와 현장 찾기 (2.0.0판, 빌드 261015-LI1, 방송클, 이사장님 승인 「1」)
// 1. 처음 한 번 허락을 받아 행사장 와이파이(현장영상해설방송, 비밀번호 없음)를 아이폰에 등록합니다(joinOnce = false).
//    그 뒤로는 행사장 범위 안에 들기만 하면 아이폰이 저절로 붙습니다.
// 2. 붙어 있으면 현장 방송(192.168.8.2:7880)이 답합니다. 앱을 열면 곧바로 들려 드립니다.
//    아이폰은 앱이 닫혀 있을 때 「와이파이에 붙었다」를 앱에 알려 주지 않으므로, 앱을 여는 것까지는 이용자가 합니다.
import Foundation
import NetworkExtension
import Network

enum Hyeonjang {
    static let SSID = "현장영상해설방송"
    static let IP = "192.168.8.2"
    static let WEB = 7880

    static var deungrokDoem: Bool {
        get { UserDefaults.standard.bool(forKey: "wifi_deungrok") }
        set { UserDefaults.standard.set(newValue, forKey: "wifi_deungrok") }
    }

    /// 행사장 와이파이 등록 — 아이폰이 「연결하시겠습니까」를 한 번 묻습니다. 결과 말을 돌려줌
    static func deungrok(_ kkeut: @escaping (String) -> Void) {
        let c = NEHotspotConfiguration(ssid: SSID)
        c.joinOnce = false
        NEHotspotConfigurationManager.shared.apply(c) { e in
            DispatchQueue.main.async {
                if let e = e as NSError?, e.domain == NEHotspotConfigurationErrorDomain,
                   e.code != NEHotspotConfigurationError.alreadyAssociated.rawValue {
                    if e.code == NEHotspotConfigurationError.userDenied.rawValue {
                        kkeut("등록하지 않았습니다. 더 보기의 행사장 와이파이 저절로 붙기 다시 하기로 언제든 다시 하실 수 있습니다.")
                    } else {
                        // 행사장 밖이라 지금 붙지 못한 것은 괜찮음 — 아이폰이 기억해 두었다가 범위 안에서 붙음
                        deungrokDoem = true
                        kkeut("행사장 와이파이를 등록했습니다. 행사장에 들어서면 아이폰이 저절로 붙습니다. 그때 LVD 방송을 열기만 하시면 들립니다.")
                    }
                } else {
                    deungrokDoem = true
                    kkeut("행사장 와이파이를 등록했습니다. 행사장에 들어서면 아이폰이 저절로 붙습니다. 그때 LVD 방송을 열기만 하시면 들립니다.")
                }
            }
        }
    }

    /// 현장 방송에 묻기(짧게)
    static func mutgi(_ gil: String, ttae: TimeInterval = 3, _ kkeut: @escaping (Data?) -> Void) {
        guard let u = URL(string: "http://\(IP):\(WEB)\(gil)") else { kkeut(nil); return }
        var r = URLRequest(url: u, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: ttae)
        r.allowsCellularAccess = false   // 행사장 와이파이로만
        URLSession.shared.dataTask(with: r) { d, res, _ in
            kkeut((res as? HTTPURLResponse)?.statusCode == 200 ? d : nil)
        }.resume()
    }

    /// 행사장인가 — 현장 방송이 답하면 행사 이름(없으면 빈 글), 아니면 nil
    static func salpigi(_ kkeut: @escaping (String?) -> Void) {
        mutgi("/status") { d in
            guard let d = d, let j = (try? JSONSerialization.jsonObject(with: d)) as? [String: Any],
                  j["hyeonjang"] as? Bool == true else { DispatchQueue.main.async { kkeut(nil) }; return }
            let jm = j["jemok"] as? String ?? ""
            DispatchQueue.main.async { kkeut(jm) }
        }
    }

    /// 도움 요청 — 현장영상해설사 방송 앱에 바로 뜸
    static func yocheong(_ jongryu: String, _ kkeut: @escaping (Bool) -> Void) {
        mutgi("/yocheong?k=\(jongryu)&n=") { d in DispatchQueue.main.async { kkeut(d != nil) } }
    }
}
