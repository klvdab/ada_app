// 지하철로 가기 — 가까운 역, 갈아타기, 들어갈 출구와 나갈 출구를 찾아 한 번에 보여 드리고, 누르면 역까지 걷는 안내를 시작합니다.
import SwiftUI

struct JihacheolGilView: View {
    let mok: Jangso
    @State private var gil: JihaGil?
    @State private var kkadak = ""
    @State private var chatneunJung = true

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if chatneunJung {
                    Text("\(mok.ireum)까지 지하철 길을 찾는 중입니다.").font(.title3)
                } else if let g = gil {
                    Button("\(g.mal) 들어갈 곳은 \(g.ipgu.ireum)입니다 — 이 길로 가기, 역까지 걷는 안내 시작") {
                        Jeulgyeo.shared.sseum(mok)
                        AnnaeEngine.shared.jihacheolGagi(mok, g)
                        GilGil.shared.cheotHwamyeon()
                    }
                    .buttonStyle(KeunDanchu())
                    Button("이미 열차에 탔습니다 — 곧장 역 알림 시작") {
                        Jeulgyeo.shared.sseum(mok)
                        AnnaeEngine.shared.jihacheolGagi(mok, g)
                        JihacheolEngine.shared.tatda(jadong: false)
                        GilGil.shared.cheotHwamyeon()
                    }
                    .buttonStyle(KeunDanchu())
                } else {
                    Button("\(kkadak) — 걸어가기로 안내 시작") {
                        AnnaeEngine.shared.georeoGagi(mok)
                        GilGil.shared.cheotHwamyeon()
                    }
                    .buttonStyle(KeunDanchu())
                    Button("다시 찾기") { chatgi() }
                        .buttonStyle(KeunDanchu())
                }
            }
            .padding()
        }
        .sokHwamyeon("")
        .task { if gil == nil && chatneunJung { chatgi() } }
    }

    private func chatgi() {
        chatneunJung = true
        Task {
            let (g, k) = await JihacheolEngine.gilChatgi(mok)
            await MainActor.run {
                gil = g
                kkadak = k
                chatneunJung = false
            }
        }
    }
}
