// 협회 아이폰 앱 공통 — 새 판 알림(1.0.0판, 빌드 261007-U1, 이사장님 승인 2026-10-07 「1」)
// 길눈·자봉이 함께 씀(자봉도 Gilnun 폴더를 싣음). AI점자도서관은 이 파일을 그대로 옮겨 쓰면 됨(앱 이름은 앱 번호로 가림).
// 애플은 앱이 스스로 자기를 설치하는 것을 허락하지 않음 — 그래서 새 판이 있으면 첫 화면 맨 위와 설정에
// 「새 판이 나왔습니다. 두드리면 테스트플라이트에서 업데이트합니다」 단추를 띄우고, 누르면 테스트플라이트의 이 앱 자리를 엶.
// 새 판 알림 자체는 테스트플라이트가 보내 주고, 테스트플라이트 설정에서 자동 업데이트를 켜 두시면 저절로 깔림.
// 판 번호 한 장: https://lvd.ada.or.kr/sihum/pan.json (안드로이드 Ollim.kt 와 같은 것). 켤 때와 앱으로 돌아올 때(1시간에 한 번까지) 살핌.
import SwiftUI
import UIKit

final class OllimEngine: ObservableObject {
    static let shared = OllimEngine()
    static let juli = "https://lvd.ada.or.kr/sihum/pan.json"

    /// 새 판 번호(없으면 nil)
    @Published private(set) var saePan: String?
    /// 테스트플라이트의 이 앱 자리
    @Published private(set) var testflight = ""
    /// 마지막 알림 말(설정 단추 아래 한 줄)
    @Published private(set) var allim = ""

    private var sijakham = false
    private var majimak = Date.distantPast

    var aeBeonho: String {
        switch Bundle.main.bundleIdentifier ?? "" {
        case "kr.or.ada.app": return "gilnun"
        case "kr.or.ada.jabong": return "jabong"
        case "kr.or.ada.lib": return "lib"
        default: return ""
        }
    }
    var aeIreum: String {
        switch aeBeonho {
        case "gilnun": return "길눈"
        case "jabong": return "자봉"
        case "lib": return "AI점자도서관"
        default: return "앱"
        }
    }
    var jigeumPan: String { (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "" }

    /// 2.53.0 과 2.54.0 처럼 점으로 나뉜 판 번호 — a 가 b 보다 새것이면 참
    static func deoSae(_ a: String, _ b: String) -> Bool {
        let x = a.split(separator: ".").map { Int($0.filter(\.isNumber)) ?? 0 }
        let y = b.split(separator: ".").map { Int($0.filter(\.isNumber)) ?? 0 }
        for i in 0..<max(x.count, y.count) {
            let p = i < x.count ? x[i] : 0, q = i < y.count ? y[i] : 0
            if p != q { return p > q }
        }
        return false
    }

    /// 앱을 켤 때 한 번 — 곧바로 살피고, 앱으로 돌아올 때마다(1시간에 한 번까지)
    func sijak() {
        guard !sijakham else { return }
        sijakham = true
        salpigi()
        NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { _ in
            let e = OllimEngine.shared
            if Date().timeIntervalSince(e.majimak) > 3600 { e.salpigi() }
        }
    }

    /// 판 번호 한 장을 받아 견줌. kkeut(새 판이 있는가, 받지 못했으면 nil)
    func salpigi(kkeut: ((Bool?) -> Void)? = nil) {
        majimak = Date()
        let beon = aeBeonho, jigeum = jigeumPan
        guard let u = URL(string: OllimEngine.juli + "?t=\(Int(Date().timeIntervalSince1970))") else { kkeut?(nil); return }
        let r = URLRequest(url: u, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        URLSession.shared.dataTask(with: r) { d, res, _ in
            var chat: (String, String)? = nil
            var bateum = false
            if let d = d, (res as? HTTPURLResponse)?.statusCode == 200,
               let j = try? JSONSerialization.jsonObject(with: d) as? [String: Any] {
                bateum = true
                if let a = (j[beon] as? [String: Any])?["ios"] as? [String: Any],
                   let pan = a["pan"] as? String, let tf = a["testflight"] as? String,
                   tf.hasPrefix("https://"), OllimEngine.deoSae(pan, jigeum) {
                    chat = (pan, tf)
                }
            }
            DispatchQueue.main.async {
                let e = OllimEngine.shared
                if bateum {
                    e.saePan = chat?.0
                    e.testflight = chat?.1 ?? ""
                    if chat != nil { Girok.shared.namgi("ollim_sae", ["pan": chat?.0 ?? ""]) }
                }
                kkeut?(bateum ? (chat != nil) : nil)
            }
        }.resume()
    }

    /// 테스트플라이트의 이 앱 자리를 엶 — 거기서 업데이트를 누르시면 됨
    func yeolgi() {
        guard let u = URL(string: testflight.isEmpty ? "itms-beta://" : testflight) else { return }
        mal("테스트플라이트를 엽니다. \(aeIreum) 옆의 업데이트를 두 번 두드리십시오.")
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { UIApplication.shared.open(u) }
        Girok.shared.namgi("ollim_yeolgi", ["pan": saePan ?? ""])
    }

    /// 설정 단추 — 새 판이 있으면 테스트플라이트로, 없으면 살펴서 알려 드림
    func seoljeongNureum() {
        if saePan != nil { yeolgi(); return }
        mal("새 판이 있는지 살핍니다.")
        salpigi { r in
            let e = OllimEngine.shared
            switch r {
            case .some(true): e.mal("\(e.aeIreum) 새 판 \(e.saePan ?? "")이 있습니다. 테스트플라이트를 엽니다."); e.yeolgi()
            case .some(false): e.mal("지금 \(e.jigeumPan)판이 가장 새 판입니다.")
            case .none: e.mal("새 판을 살피지 못했습니다. 인터넷을 확인하시고 다시 눌러 주십시오.")
            }
        }
    }

    var seoljeongGeul: String {
        if let p = saePan { return "업데이트 — 새 판 \(p)이 있습니다. 누르면 테스트플라이트에서 업데이트합니다(지금 \(jigeumPan)판)" }
        return "업데이트 — 지금 \(jigeumPan)판. 누르면 새 판이 있는지 살핍니다"
    }

    private func mal(_ t: String) {
        allim = t
        if Seoljeong.shared.malKyeojim { SoriEngine.shared.mal(t) } else { UIAccessibility.post(notification: .announcement, argument: t) }
    }
}

/// 첫 화면 맨 위 한 줄 — 새 판이 있을 때만(안내와 단추를 한 자리에)
struct OllimJul: View {
    @ObservedObject private var e = OllimEngine.shared
    var body: some View {
        if let p = e.saePan {
            Button("\(e.aeIreum) 새 판 \(p)이 나왔습니다. 두드리면 테스트플라이트에서 업데이트합니다") { e.yeolgi() }
                .buttonStyle(KeunDanchu())
        }
    }
}

/// 설정 안 업데이트 단추 — 새로고침 바로 아래 한 곳
struct OllimSeoljeongDanchu: View {
    @ObservedObject private var e = OllimEngine.shared
    @AccessibilityFocusState private var chojeom: Bool
    var body: some View {
        Button(e.seoljeongGeul) { e.seoljeongNureum() }
            .buttonStyle(KeunDanchu())
        if !e.allim.isEmpty {
            Text(e.allim).font(.body).accessibilityFocused($chojeom)
                .onChange(of: e.allim) { _ in DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { chojeom = true } }
        }
    }
}
