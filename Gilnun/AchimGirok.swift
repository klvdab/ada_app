// 아침 기록 — 관리자 공간 (앱 2.59.0, 빌드 261009-I13, 이사장님 승인 2026-10-09 "매일 아침에 길눈과 자봉이 사용된 기록을 볼 수 있도록")
// 나스 jeom/achim.php 가 지난 24시간 앱 기록을 모아 정리한 글을 받아 한 줄씩 보여 드림(안드로이드 AchimGirok.kt 와 같은 말).
// 살펴볼 일이 있으면 맨 첫 줄에. 같은 글이 매일 아침 이사장님 메일로도 감.
// 화면을 열면 커서가 첫 줄(요약)로. 줄에 번호 없음.
import SwiftUI

struct AchimGirokView: View {
    @State private var jul: [String] = []
    @State private var badneun = true
    @State private var mot = false
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                if badneun {
                    Text("아침 기록을 받는 중입니다.").font(.title3).accessibilityFocused($chojeom)
                } else if mot {
                    Text("아침 기록을 받지 못했습니다. 인터넷을 확인하신 뒤 설정의 새로고침을 누르시고 다시 열어 주십시오.")
                        .font(.title3).accessibilityFocused($chojeom)
                } else {
                    ForEach(Array(jul.enumerated()), id: \.offset) { i, s in
                        if s.isEmpty {
                            EmptyView()
                        } else if s.hasPrefix("[") {
                            Text(s.trimmingCharacters(in: CharacterSet(charactersIn: "[]")))
                                .font(.title3.bold()).accessibilityAddTraits(.isHeader)
                        } else if i == 1 {
                            Text(s).font(.title2.bold()).accessibilityFocused($chojeom)
                        } else {
                            Text(s).font(.title3).frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                }
            }
            .padding()
        }
        .sokHwamyeon("아침 기록")
        .task { await batgi() }
    }

    private func batgi() async {
        badneun = true
        let o = await Nas.get("achim.php", [("f", "json")])
        let l = (o?["jul"] as? [String]) ?? []
        await MainActor.run {
            jul = l
            mot = l.isEmpty
            badneun = false
            Girok.shared.namgi("achim_girok", ["ok": !l.isEmpty])
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { chojeom = true }
        }
    }
}
