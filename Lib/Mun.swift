// AI점자도서관 앱 — 세 겹의 문(주제별, 장르별, 테마별) (판 0.5.0, 빌드 261010-L9)
// 0.5.0 (261010-L9, 도서관 창 클, 이사장님 승인 「1」) 첫 화면에 고른 문의 서가 목록을 바로 보임(설정의 첫 화면 목록, 처음 값 장르별),
//       「보일 책」 거르기를 뺌(늘 모든 책). 0.3.0 (261006-1) 처음 판
// 나스 도서관 창구 doseo.php 의 j_mun(문과 서가 수), j_seoga(서가 안 책 목록)를 쓴다. 파일은 옮기지 않고 목록 카드로 분류한 것.
import SwiftUI

struct MunResp: Decodable { let ok: Bool; let mun: [Mun]? }
struct Mun: Decodable, Identifiable { let k: String; let t: String; let seoga: [Seoga]; var id: String { k } }
struct Seoga: Decodable, Identifiable { let k: String; let t: String; let n: Int; var id: String { k } }

extension API {
    static func munGet(_ h: String) async throws -> MunResp {
        let (d, _) = try await URLSession.shared.data(for: request(url("doseo.php", ["m": "j_mun", "h": h])))
        return try JSONDecoder().decode(MunResp.self, from: d)
    }
    static func seoga(_ mun: String, _ k: String, _ h: String, _ o: Int) async throws -> ListResp {
        let (d, _) = try await URLSession.shared.data(for: request(url("doseo.php", ["m": "j_seoga", "mun": mun, "sk": k, "h": h, "o": String(o), "n": String(perPage)])))
        return try JSONDecoder().decode(ListResp.self, from: d)
    }
}

// 0.5.0 — 세 문의 이름(설정의 첫 화면 목록과 함께 씀)
enum MunIreum {
    static let modu: [(String, String)] = [("jujae", "주제별"), ("jangreu", "장르별"), ("tema", "테마별")]
    static func t(_ k: String) -> String { modu.first(where: { $0.0 == k })?.1 ?? "장르별" }
}

// 도서관 첫 화면 — 고른 문의 서가 목록을 바로(0.5.0, 이사장님 승인 「1」), 다른 두 문은 그 아래 한 줄씩
struct MunSection: View {
    @EnvironmentObject var nav: Nav
    @AppStorage("cheotMun") private var cheot = "jangreu"
    @State private var sg: [Seoga] = []
    @State private var msg = "가져오는 중입니다."
    var body: some View {
        Section {
            if sg.isEmpty { Text(msg) }
            ForEach(sg.filter { $0.n > 0 }) { s in
                Button("\(s.t), \(s.n)권") { nav.push(Route.seoga(cheot, s.k, s.t)) }
            }
            ForEach(MunIreum.modu.filter { $0.0 != cheot }, id: \.0) { m in
                Button(m.0 == "jujae" ? "주제별로 찾기, 십진분류" : "\(m.1)로 찾기") { nav.push(Route.mun(m.0, m.1)) }
            }
        } header: { Text("\(MunIreum.t(cheot))") }
        .task(id: cheot) {
            do {
                let r = try await API.munGet("all")
                sg = r.mun?.first(where: { $0.k == cheot })?.seoga ?? []
                msg = sg.isEmpty ? "이 문에는 아직 책이 없습니다." : ""
            } catch { msg = "도서관에 닿지 못했습니다. 인터넷을 확인한 뒤 설정의 새로고침을 눌러 주십시오." }
        }
    }
}

// 문 안 — 서가 목록(책 수와 함께)
struct MunView: View {
    let mun: String
    let ttl: String
    @EnvironmentObject var nav: Nav
    private let h = "all"   // 0.5.0 보일 책 거르기를 뺌
    @State private var sg: [Seoga] = []
    @State private var msg = "가져오는 중입니다."
    @AccessibilityFocusState private var focus: String?
    var body: some View {
        List {
            if sg.isEmpty { Text(msg) }
            ForEach(sg.filter { $0.n > 0 }) { s in
                Button("\(s.t), \(s.n)권") { nav.push(Route.seoga(mun, s.k, s.t)) }
                    .accessibilityFocused($focus, equals: s.k)
            }
        }
        .navigationTitle(ttl)
        .task(id: h) {
            do {
                let r = try await API.munGet(h)
                sg = r.mun?.first(where: { $0.k == mun })?.seoga ?? []
                msg = sg.isEmpty ? "이 문에는 아직 책이 없습니다." : ""
                if let f = sg.first(where: { $0.n > 0 }) { try? await Task.sleep(nanoseconds: 300_000_000); focus = f.k }
            } catch { msg = "가져오지 못했습니다. 설정의 새로고침을 눌러 주십시오." }
        }
    }
}

// 서가 안 — 책 목록(묶음 책은 제목과 권수로)
struct SeogaView: View {
    let mun: String
    let k: String
    let ttl: String
    private let h = "all"   // 0.5.0
    var body: some View {
        PagedList(loader: { o in try await API.seoga(mun, k, h, o) })
            .navigationTitle(ttl)
    }
}
