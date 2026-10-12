// AI점자도서관 앱 — 곱씹어 읽기와 사전 (판 0.6.0, 빌드 261012-L12, 도서관 창 클, 이사장님 승인 2026-10-12 「1」)
// 이 문단 다시 듣기·천천히 다시, 풀어 읽기(글자·단어 단위로 짚기), 중요 표시와 메모, 중요 표시 모음, 책 속에서 찾기,
// 사전(국어 두 사전·영어·일본어·중국어·한자, 서버 사전 일꾼), 내 낱말장(찾아본 낱말을 그 문장과 함께 저절로 모음).
// 목록은 설정의 목록 줄 수(처음 15줄)로 끊고 더 보기·이전 보기. 결과가 나오면 커서를 첫 줄로.
import SwiftUI
import UIKit

struct Jungyo: Codable, Identifiable, Hashable {
    var i: Int
    var t: String
    var pos: Int
    var geul: String
    var memo: String
    var at: Date
    var id: String { "\(i)-\(pos)" }
}
struct Nanmal: Codable, Identifiable, Hashable {
    var w: String
    var ddeut: String
    var chulcheo: String
    var munjang: String
    var chaek: String
    var at: Date
    var id: String { w + "|" + chaek }
}
struct SajeonItem: Decodable, Hashable { let t: String; let ddeut: String; let chulcheo: String }
struct SajeonResp: Decodable { let ok: Bool; let items: [SajeonItem]?; let msg: String? }
struct SokHit: Decodable, Hashable { let o: Int; let t: String }
struct SokResp: Decodable { let ok: Bool; let modu: Int?; let hits: [SokHit]?; let msg: String? }

extension API {
    static func sajeon(_ q: String) async throws -> SajeonResp { try await get("sajeon.php", ["a": "chatgi", "q": q], as: SajeonResp.self) }
    static func sokChatgi(_ i: Int, _ q: String) async throws -> SokResp { try await get("dokseo.php", ["m": "sokchatgi", "i": String(i), "q": q, "gp": "4"], as: SokResp.self) }
}

@MainActor
final class Gomssip: ObservableObject {
    static let shared = Gomssip()
    @Published var jungyo: [Jungyo] = []
    @Published var nanmal: [Nanmal] = []
    private let ud = UserDefaults.standard
    private init() {
        if let d = ud.data(forKey: "jungyo"), let v = try? JSONDecoder().decode([Jungyo].self, from: d) { jungyo = v }
        if let d = ud.data(forKey: "nanmal"), let v = try? JSONDecoder().decode([Nanmal].self, from: d) { nanmal = v }
    }
    func save() {
        if let d = try? JSONEncoder().encode(jungyo) { ud.set(d, forKey: "jungyo") }
        if let d = try? JSONEncoder().encode(nanmal) { ud.set(d, forKey: "nanmal") }
    }
    var jungyoSun: [Jungyo] { jungyo.sorted { $0.at > $1.at } }
    var nanmalSun: [Nanmal] { nanmal.sorted { $0.at > $1.at } }
    func pyosi(i: Int, t: String, pos: Int, geul: String) -> Bool {
        if jungyo.contains(where: { $0.i == i && $0.pos == pos }) { return false }
        jungyo.append(Jungyo(i: i, t: t, pos: pos, geul: String(geul.prefix(300)), memo: "", at: Date())); save(); return true
    }
    func memo(i: Int, pos: Int, _ m: String) {
        if let k = jungyo.firstIndex(where: { $0.i == i && $0.pos == pos }) { jungyo[k].memo = m; save() }
    }
    func jiugi(_ j: Jungyo) { jungyo.removeAll { $0.id == j.id }; save() }
    func damgi(w: String, it: SajeonItem, munjang: String, chaek: String) {
        nanmal.removeAll { $0.w == w && $0.chaek == chaek }
        nanmal.append(Nanmal(w: w, ddeut: it.ddeut, chulcheo: it.chulcheo, munjang: String(munjang.prefix(200)), chaek: chaek, at: Date())); save()
    }
    func jiugi(_ n: Nanmal) { nanmal.removeAll { $0.id == n.id }; save() }
    /// 책 안의 그 자리로 가서 읽기
    static func garyeo(_ nav: Nav, i: Int, t: String, pos: Int) {
        let r = Reader.shared; let s = Store.shared
        if r.i == i { nav.push(Route.reader(i, t, "geul")); r.play(at: pos) }
        else { s.remember(i: i, t: t, kind: "geul", pos: Double(pos), modu: s.rec(i)?.modu ?? 0); nav.push(Route.reader(i, t, "geul")) }
    }
}

/// 문단 글을 낱말로 — 앞뒤 문장부호를 떼고, 같은 낱말은 한 번만
func nanmalDeul(_ t: String) -> [String] {
    var seen = Set<String>(); var out: [String] = []
    for raw in t.split(whereSeparator: { $0.isWhitespace }) {
        let w = String(raw).trimmingCharacters(in: CharacterSet.punctuationCharacters.union(.symbols).union(CharacterSet(charactersIn: "「」『』《》〈〉“”‘’…·")))
        if w.isEmpty || seen.contains(w) { continue }
        seen.insert(w); out.append(w)
    }
    return out
}

// 풀어 읽기 — 지금 문단을 한 덩이 글로 띄워 보이스오버 글자·단어 단위 읽기로 짚게 함
struct PureoView: View {
    let geul: String
    @AccessibilityFocusState private var focus: Bool
    var body: some View {
        ScrollView {
            Text(geul)
                .font(.title3)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
                .textSelection(.enabled)
                .accessibilityFocused($focus)
        }
        .navigationTitle("풀어 읽기")
        .task { try? await Task.sleep(nanoseconds: 400_000_000); focus = true }
    }
}

// 책 속에서 찾기
struct SokChatgiView: View {
    let i: Int
    let ttl: String
    @EnvironmentObject var nav: Nav
    @State private var q = ""
    @State private var hits: [SokHit] = []
    @State private var msg = ""
    @State private var o = 0
    @AccessibilityFocusState private var focus: Int?
    var body: some View {
        List {
            Section {
                TextField("찾을 낱말이나 이름", text: $q).submitLabel(.search).onSubmit { chatgi() }
                Button("찾기", action: chatgi)
            }
            if !msg.isEmpty { Text(msg) }
            if !hits.isEmpty {
                Section {
                    ForEach(Array(hits.dropFirst(o).prefix(API.perPage).enumerated()), id: \.element) { k, h in
                        Button("\(h.o + 1)번째 문단, \(h.t)") { Gomssip.garyeo(nav, i: i, t: ttl, pos: h.o) }
                            .accessibilityFocused($focus, equals: k)
                    }
                    if o + API.perPage < hits.count { Button("더 보기") { o += API.perPage; cheot() } }
                    if o > 0 { Button("이전 보기") { o = max(0, o - API.perPage); cheot() } }
                }
            }
        }
        .navigationTitle("책 속에서 찾기")
    }
    func cheot() { Task { try? await Task.sleep(nanoseconds: 300_000_000); focus = 0 } }
    func chatgi() {
        let s = q.trimmingCharacters(in: .whitespaces); if s.isEmpty { return }
        msg = "찾는 중입니다."; hits = []; o = 0
        Task {
            do {
                let r = try await API.sokChatgi(i, s)
                hits = r.hits ?? []
                msg = hits.isEmpty ? (r.msg ?? "찾는 말이 이 책에 없습니다.") : "\(r.modu ?? hits.count)곳을 찾았습니다."
                if !hits.isEmpty { cheot() }
            } catch { msg = "찾지 못했습니다. 인터넷을 확인해 주십시오." }
        }
    }
}

// 사전 — 지금 문단의 낱말을 골라 뜻 듣기, 또는 낱말을 적어 찾기
struct SajeonView: View {
    let chaek: String
    let munjang: String
    @EnvironmentObject var store: Store
    @State private var q = ""
    @State private var o = 0
    @State private var chajeun = ""
    @State private var items: [SajeonItem] = []
    @State private var msg = ""
    @AccessibilityFocusState private var focus: String?
    var mal: [String] { nanmalDeul(munjang) }
    var body: some View {
        List {
            if !chajeun.isEmpty {
                Section {
                    Button("다른 낱말 고르기") { chajeun = ""; items = []; msg = "" }
                    if !msg.isEmpty { Text(msg).accessibilityFocused($focus, equals: "gyeol") }
                    ForEach(Array(items.enumerated()), id: \.offset) { k, it in
                        Text("\(it.t). \(it.ddeut) 출처 \(it.chulcheo).")
                            .accessibilityFocused($focus, equals: k == 0 ? "gyeol" : "g\(k)")
                    }
                } header: { Text("「\(chajeun)」 뜻") }
            } else {
                Section {
                    TextField("찾을 낱말", text: $q).submitLabel(.search).onSubmit { chatgi(q) }
                    Button("사전에서 찾기") { chatgi(q) }
                }
                if !mal.isEmpty {
                    Section {
                        ForEach(Array(mal.dropFirst(o).prefix(API.perPage).enumerated()), id: \.element) { k, w in
                            Button(w) { chatgi(w) }.accessibilityFocused($focus, equals: k == 0 ? "mal0" : w)
                        }
                        if o + API.perPage < mal.count { Button("더 보기") { o += API.perPage; cheot("mal0") } }
                        if o > 0 { Button("이전 보기") { o = max(0, o - API.perPage); cheot("mal0") } }
                    } header: { Text("지금 문단의 낱말") }
                }
            }
        }
        .navigationTitle("사전")
    }
    func cheot(_ f: String) { Task { try? await Task.sleep(nanoseconds: 300_000_000); focus = f } }
    func chatgi(_ w0: String) {
        let w = w0.trimmingCharacters(in: .whitespaces); if w.isEmpty { return }
        chajeun = w; items = []; msg = "찾는 중입니다."; cheot("gyeol")
        Task {
            do {
                let r = try await API.sajeon(w)
                items = r.items ?? []
                if items.isEmpty { msg = r.msg ?? "사전에 없는 낱말입니다." }
                else {
                    msg = ""
                    Gomssip.shared.damgi(w: w, it: items[0], munjang: munjang, chaek: chaek)   // 내 낱말장에 저절로
                }
                cheot("gyeol")
            } catch { msg = "사전에 닿지 못했습니다. 인터넷을 확인해 주십시오."; cheot("gyeol") }
        }
    }
}

// 중요 표시 모음 — 모든 책, 최근 것이 위
struct JungyoListView: View {
    @EnvironmentObject var nav: Nav
    @ObservedObject var g = Gomssip.shared
    @State private var o = 0
    @AccessibilityFocusState private var focus: Int?
    var body: some View {
        let L = g.jungyoSun
        List {
            if L.isEmpty { Text("중요 표시가 없습니다. 책을 읽다가 더 보기의 「중요 표시하기」를 누르면 여기에 모입니다.") }
            else {
                Text("중요 표시 \(L.count)개 가운데 \(o + 1)번부터 \(min(o + API.perPage, L.count))번").font(.footnote)
                ForEach(Array(L.dropFirst(o).prefix(API.perPage).enumerated()), id: \.element) { k, j in
                    Button("\(j.t), \(j.pos + 1)번째 문단. \(j.memo.isEmpty ? "" : "메모 " + j.memo + ". ")\(j.geul)") { Gomssip.garyeo(nav, i: j.i, t: j.t, pos: j.pos) }
                        .accessibilityAction(named: "중요 표시 지우기") { g.jiugi(j); Store.shared.say("지웠습니다.") }
                        .accessibilityFocused($focus, equals: k)
                }
                if o + API.perPage < L.count { Button("더 보기") { o += API.perPage; cheot() } }
                if o > 0 { Button("이전 보기") { o = max(0, o - API.perPage); cheot() } }
            }
        }
        .navigationTitle("중요 표시 모음")
    }
    func cheot() { Task { try? await Task.sleep(nanoseconds: 300_000_000); focus = 0 } }
}

// 내 낱말장 — 찾아본 낱말이 그 문장과 함께 저절로 모임
struct NanmalJangView: View {
    @ObservedObject var g = Gomssip.shared
    @State private var o = 0
    @AccessibilityFocusState private var focus: Int?
    var body: some View {
        let L = g.nanmalSun
        List {
            if L.isEmpty { Text("찾아본 낱말이 없습니다. 책을 읽다가 사전에서 낱말을 찾으면 여기에 저절로 모입니다.") }
            else {
                Text("낱말 \(L.count)개 가운데 \(o + 1)번부터 \(min(o + API.perPage, L.count))번").font(.footnote)
                ForEach(Array(L.dropFirst(o).prefix(API.perPage).enumerated()), id: \.element) { k, n in
                    Text("\(n.w). \(n.ddeut) 출처 \(n.chulcheo). 책 \(n.chaek). 문장 \(n.munjang)")
                        .accessibilityAction(named: "낱말장에서 지우기") { g.jiugi(n); Store.shared.say("지웠습니다.") }
                        .accessibilityFocused($focus, equals: k)
                }
                if o + API.perPage < L.count { Button("더 보기") { o += API.perPage; cheot() } }
                if o > 0 { Button("이전 보기") { o = max(0, o - API.perPage); cheot() } }
            }
        }
        .navigationTitle("내 낱말장")
    }
    func cheot() { Task { try? await Task.sleep(nanoseconds: 300_000_000); focus = 0 } }
}
