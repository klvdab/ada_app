// 말벗 견주기 — 관리자 시험 (앱 2.51.0, 빌드 261006-I11, 이사장님 승인 2026-10-06)
// 같은 질문 다섯 가지를 협회 서버 말벗(엑사원)과 폰 안 인공지능(애플 인텔리전스)에 차례로 묻고,
// 걸린 시간과 대답을 나란히 적어 귀로 견주어 듣게 함. 폰 안 인공지능이 안 되는 폰(아이폰 15 프로 앞선 폰)은 서버만 잼.
import SwiftUI

struct MalbeotGyeonjugiView: View {
    static let MUREUM = ["현장영상해설이 뭐야", "길눈은 누가 만들었어", "비 오는 날 걸을 때 조심할 게 뭐야", "오늘 기분이 좀 우울해", "흰지팡이는 왜 짚어야 해"]
    @State private var jul: [String] = []
    @State private var doneun = false
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Button(doneun ? "견주는 중 — 질문 다섯 가지를 차례로 묻습니다" : "견주기 시작 — 같은 질문 다섯 가지를 서버와 폰 안 인공지능에 묻기") { sijak() }
                    .buttonStyle(KeunDanchu())
                    .disabled(doneun)
                if !jul.isEmpty {
                    Text("결과").font(.title3.bold()).accessibilityAddTraits(.isHeader).accessibilityFocused($chojeom)
                    ForEach(Array(jul.enumerated()), id: \.offset) { _, s in
                        Text(s).font(.body).frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
            .padding()
        }
        .sokHwamyeon("관리자 시험 — 말벗 견주기")
    }

    private func sijak() {
        doneun = true
        jul = []
        SoriEngine.shared.mal("견주기를 시작합니다. 질문 다섯 가지를 차례로 묻습니다. 한 질문에 반 분쯤 걸릴 수 있습니다.")
        Task {
            var go: [String] = []
            let pon = MalAI.daehwaGaneung
            if !pon { go.append("이 폰에서는 폰 안 인공지능을 쓸 수 없습니다. 아이폰 15 프로 이후 폰에서 애플 인텔리전스를 켜야 됩니다. 서버 대답만 잽니다.") }
            for (i, q) in MalbeotGyeonjugiView.MUREUM.enumerated() {
                let (sd, st) = await MalbeotGyeonjugiView.seobeo(q)
                var line = "\(i + 1). \(q)\n서버 \(st)초: \(sd)"
                if pon {
                    let t0 = Date()
                    let p = await MalAI.daehwa(q) ?? "대답 없음"
                    line += "\n폰 " + String(format: "%.1f", Date().timeIntervalSince(t0)) + "초: " + p
                }
                go.append(line)
                let g = go
                await MainActor.run { jul = g }
            }
            await MainActor.run {
                doneun = false
                SoriEngine.shared.mal("견주기를 마쳤습니다. 결과를 아래에 적었습니다.")
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { chojeom = true }
                Girok.shared.namgi("malbeot_gyeonju", ["pon": pon])
            }
        }
    }

    static func seobeo(_ q: String) async -> (String, String) {
        var c = URLComponents(string: "https://lvd.ada.or.kr/jeom/malbeot.php")
        c?.queryItems = [URLQueryItem(name: "mal", value: q)]
        guard let u = c?.url else { return ("주소를 만들지 못했습니다", "0") }
        var r = URLRequest(url: u)
        r.timeoutInterval = 60
        r.cachePolicy = .reloadIgnoringLocalCacheData
        let t0 = Date()
        let o = (try? await URLSession.shared.data(for: r)).flatMap { (try? JSONSerialization.jsonObject(with: $0.0)) as? [String: Any] }
        let s = String(format: "%.1f", Date().timeIntervalSince(t0))
        return ((o?["dap"] as? String) ?? (o?["msg"] as? String) ?? "대답 없음", s)
    }
}
