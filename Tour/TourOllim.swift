// LVD투어스튜디오 아이폰 — 새 판 알림(판 1.0.0, 빌드 261012-T1) — LVD투어스튜디오의 TourOllim 과 같은 일
// 판 번호 한 장: https://lvd.ada.or.kr/sihum/pan.json 의 tour.ios
// 애플은 앱이 스스로 자기를 설치하는 것을 허락하지 않음 — 새 판이 있으면 코스 탭 맨 위와 설정에 테스트플라이트로 가는 단추를 띄움.
import SwiftUI
import UIKit

final class TourOllim: ObservableObject {
    static let shared = TourOllim()
    static let juli = "https://lvd.ada.or.kr/sihum/pan.json"

    @Published private(set) var saePan: String?
    @Published private(set) var testflight = ""
    @Published private(set) var allim = ""
    private var sijakham = false
    private var majimak = Date.distantPast

    var jigeumPan: String { (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "" }

    static func deoSae(_ a: String, _ b: String) -> Bool {
        let x = a.split(separator: ".").map { Int($0.filter(\.isNumber)) ?? 0 }
        let y = b.split(separator: ".").map { Int($0.filter(\.isNumber)) ?? 0 }
        for i in 0..<max(x.count, y.count) {
            let p = i < x.count ? x[i] : 0, q = i < y.count ? y[i] : 0
            if p != q { return p > q }
        }
        return false
    }

    /// 켤 때 한 번 — 곧바로 살피고, 앱으로 돌아올 때마다(1시간에 한 번까지)
    func sijak() {
        guard !sijakham else { return }
        sijakham = true
        salpigi()
        NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { _ in
            let e = TourOllim.shared
            if Date().timeIntervalSince(e.majimak) > 3600 { e.salpigi() }
        }
    }

    func salpigi(kkeut: ((Bool?) -> Void)? = nil) {
        majimak = Date()
        let jigeum = jigeumPan
        guard let u = URL(string: TourOllim.juli + "?t=\(Int(Date().timeIntervalSince1970))") else { kkeut?(nil); return }
        let r = URLRequest(url: u, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        URLSession.shared.dataTask(with: r) { d, res, _ in
            var chat: (String, String)? = nil
            var bateum = false
            if let d = d, (res as? HTTPURLResponse)?.statusCode == 200,
               let j = try? JSONSerialization.jsonObject(with: d) as? [String: Any] {
                bateum = true
                if let a = (j["tour"] as? [String: Any])?["ios"] as? [String: Any],
                   let pan = a["pan"] as? String, let tf = a["testflight"] as? String,
                   tf.hasPrefix("https://"), TourOllim.deoSae(pan, jigeum) {
                    chat = (pan, tf)
                }
            }
            DispatchQueue.main.async {
                let e = TourOllim.shared
                if bateum { e.saePan = chat?.0; e.testflight = chat?.1 ?? "" }
                kkeut?(bateum ? (chat != nil) : nil)
            }
        }.resume()
    }

    func yeolgi() {
        guard let u = URL(string: testflight.isEmpty ? "itms-beta://" : testflight) else { return }
        mal("테스트플라이트를 엽니다. LVD투어스튜디오 옆의 업데이트를 두 번 두드리십시오.")
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { UIApplication.shared.open(u) }
    }

    func seoljeongNureum() {
        if saePan != nil { yeolgi(); return }
        mal("새 판이 있는지 살핍니다.")
        salpigi { r in
            let e = TourOllim.shared
            switch r {
            case .some(true): e.mal("LVD투어스튜디오 새 판 \(e.saePan ?? "")이 있습니다. 테스트플라이트를 엽니다."); e.yeolgi()
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
        UIAccessibility.post(notification: .announcement, argument: t)   // 업데이트 안내는 앱 안내 말소리를 꺼 두셔도 한 번 알림
    }
}

/// 코스 탭 맨 위 한 줄 — 새 판이 있을 때만(안내와 단추를 한 자리에)
struct TourOllimJul: View {
    @ObservedObject private var e = TourOllim.shared
    var body: some View {
        if let p = e.saePan {
            Button("LVD투어스튜디오 새 판 \(p)이 나왔습니다. 두드리면 테스트플라이트에서 업데이트합니다") { e.yeolgi() }
                .font(.headline)
                .foregroundStyle(.white)
                .listRowBackground(Saek.namsaek)
        }
    }
}

/// 설정의 업데이트 단추 — 새로고침 바로 아래
struct TourOllimDanchu: View {
    @ObservedObject private var e = TourOllim.shared
    @AccessibilityFocusState private var chojeom: Bool
    var body: some View {
        Button(e.seoljeongGeul) { e.seoljeongNureum() }
        if !e.allim.isEmpty {
            Text(e.allim).accessibilityFocused($chojeom)
                .onChange(of: e.allim) { _ in DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { chojeom = true } }
        }
    }
}
