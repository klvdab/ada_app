// 현장영상해설 받기 — 앱 2.14.0 (빌드 260929-9, 이사장님 승인). 웹 길눈 haeseol.js 1.0판과 같은 세 갈래.
// 길눈이 데려다주는 데서 끝내지 않고, 그 자리에서 무엇을 보고 있는지까지 잇습니다.
// ① 지금 바로 현장영상해설사에게 화상통화(앱 자체 긴급통화서비스) ② 협회가 만들어 둔 현장영상해설 코스 듣기 ③ 현장영상해설사 파견 신청
// 2.14.0 이사장님 지시 — 우리가 다루는 해설은 모두 "현장영상해설", 해설사는 "현장영상해설사"로(긴급통화서비스의 "해설사"만 예외)
// ②③은 협회 홈페이지의 화면을 앱 안에서 엽니다(닫기를 누르시면 길눈으로 돌아옴).
import SwiftUI
import SafariServices

/// 앱 안에서 웹 화면 열기 — 보이스오버로 읽히고, 닫으면 길눈으로 돌아옴
struct SafariBogi: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> SFSafariViewController {
        let c = SFSafariViewController(url: url)
        c.dismissButtonStyle = .close
        return c
    }
    func updateUIViewController(_ vc: SFSafariViewController, context: Context) {}
}

struct HaeseolJuso: Identifiable {
    let id = UUID()
    let url: URL
}

struct HaeseolView: View {
    @State private var yeolgi: HaeseolJuso?
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Button("지금 바로 현장영상해설사에게 화상통화 요청 — 현장영상해설사가 눈이 되어 드립니다") {
                    Girok.shared.namgi("haeseol", ["gil": "hwasang"])
                    GinGeup.shared.yocheong(.haeseolsa)
                    TabGil.shared.tab = 0
                    GilGil.shared.cheotHwamyeon()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { GilGil.shared.path.append(GilHwamyeon.gingeup) }
                }
                .buttonStyle(KeunDanchu())
                .accessibilityFocused($chojeom)
                Button("준비된 현장영상해설 코스 듣기 — 협회가 만들어 둔 현장영상해설") {
                    Girok.shared.namgi("haeseol", ["gil": "koseu"])
                    if let u = URL(string: "https://lvd.ada.or.kr/lvdts/lvdts_hyeonjang.html") { yeolgi = HaeseolJuso(url: u) }
                }
                .buttonStyle(KeunDanchu())
                Button("현장영상해설사 파견 신청하기 — 날을 잡아 함께 다니실 현장영상해설사") {
                    Girok.shared.namgi("haeseol", ["gil": "pagyeon"])
                    if let u = URL(string: "https://ada.or.kr/dispatch.html") { yeolgi = HaeseolJuso(url: u) }
                }
                .buttonStyle(KeunDanchu())
                DisclosureGroup("알아 두실 것 펼치기") {
                    Text("가신 곳에서 무엇을 보고 계신지 현장영상해설로 이어 드립니다. 지금 바로 현장영상해설사에게 화상통화 요청을 누르시면 협회 현장영상해설사에게 화상통화를 청하고, 현장영상해설사가 폰 카메라로 보며 현장영상해설해 드립니다. 준비된 현장영상해설 코스 듣기는 협회가 만들어 둔 코스의 현장영상해설을 듣는 화면을, 현장영상해설사 파견 신청하기는 날을 잡아 현장영상해설사와 함께 다니시도록 신청하는 화면을 앱 안에서 엽니다. 다 보신 뒤 닫기를 누르시면 길눈으로 돌아옵니다. 말로 하기에서 현장영상해설 받고 싶어라고 하셔도 이 화면이 열립니다.")
                        .font(.body)
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("현장영상해설 받기")
        .sheet(item: $yeolgi) { j in SafariBogi(url: j.url).ignoresSafeArea() }
        .onAppear {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { chojeom = true }
        }
    }
}
