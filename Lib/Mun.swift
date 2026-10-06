// AI점자도서관 앱 — 세 겹의 문(주제별, 장르별, 테마별)과 형태 거르기 (판 0.3.0, 빌드 261006-1)
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

// 도서관 첫 화면 맨 위 — 세 겹의 문
struct MunSection: View {
    @EnvironmentObject var nav: Nav
    @AppStorage("hyeongtae") private var h = "all"
    var body: some View {
        Section("세 겹의 문") {
            Button("주제별로 찾기, 십진분류") { nav.push(Route.mun("jujae", "주제별")) }
            Button("장르별로 찾기") { nav.push(Route.mun("jangreu", "장르별")) }
            Button("테마별로 찾기") { nav.push(Route.mun("tema", "테마별")) }
            Picker("보일 책", selection: $h) {
                Text("모든 책").tag("all")
                Text("소리로 듣는 책만").tag("sori")
                Text("점자책만").tag("jeom")
            }
        }
    }
}

// 문 안 — 서가 목록(책 수와 함께)
struct MunView: View {
    let mun: String
    let ttl: String
    @EnvironmentObject var nav: Nav
    @AppStorage("hyeongtae") private var h = "all"
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
    @AppStorage("hyeongtae") private var h = "all"
    var body: some View {
        PagedList(loader: { o in try await API.seoga(mun, k, h, o) })
            .navigationTitle(ttl)
    }
}
