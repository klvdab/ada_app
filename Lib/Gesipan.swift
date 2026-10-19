// 0.8.4 (261019-L23) 게시판 — 공지·묻고 답하기·후기·제작 요청·불편 신고(서버 gesipan.php, 웹 연구실 0.3.6 과 같은 약속)
// 도서관 첫 화면의 「게시판」 줄에서 들어감. 목록은 설정의 목록 줄 수(기본 15줄), 최근 글이 맨 위.
// 공지 쓰기·답하기·제작 요청 단계 바꾸기는 운영자·최고관리자만(서버가 막음). 지우기는 서버가 가려 둠(실제로 지우지 않음).
import SwiftUI
import UIKit

struct GsPan: Decodable, Identifiable, Hashable { let b: String; let t: String; let su: Int; let sae: Int; let sseugi: Bool; var id: String { b } }
struct GsPanResp: Decodable { let ok: Bool; let pan: [GsPan]?; let unyeong: Bool?; let msg: String? }
struct GsDap: Decodable, Hashable { let nal: String; let naeyong: String }
struct GsGeul: Decodable, Identifiable, Hashable {
    let id: String; let beon: Int; let jemok: String; let nal: String; let ireum: String
    let bimil: Bool?; let nae: Bool?; let dap_su: Int?; let dangye: String?; let dangye_t: String?; let gojeong: Bool?
    let naeyong: String?; let dap: [GsDap]?
    var julMal: String {
        var s = "\(beon)번. " + ((gojeong ?? false) ? "고정, " : "") + jemok + ", \(ireum), \(nal)"
        if bimil ?? false { s += ", 비밀글" }
        if let d = dangye_t { s += ", \(d)" }
        if let n = dap_su, n > 0 { s += ", 답 \(n)개" }
        return s
    }
}
struct GsMokResp: Decodable { let ok: Bool; let modu: Int?; let o: Int?; let mok: [GsGeul]?; let msg: String? }
struct GsBogiResp: Decodable { let ok: Bool; let geul: GsGeul?; let unyeong: Bool?; let msg: String? }
struct GsDapResp: Decodable { let ok: Bool; let msg: String?; let id: String? }

enum GsAPI {
    static func pan() async throws -> GsPanResp { try await API.get("gesipan.php", ["a": "pan"], as: GsPanResp.self) }
    static func mok(_ b: String, _ o: Int) async throws -> GsMokResp { try await API.get("gesipan.php", ["a": "mok", "b": b, "o": String(o), "n": String(API.perPage)], as: GsMokResp.self) }
    static func bogi(_ b: String, _ id: String) async throws -> GsBogiResp { try await API.get("gesipan.php", ["a": "bogi", "b": b, "id": id], as: GsBogiResp.self) }
    /// 쓰는 일은 POST(서버가 GET 으로는 받지 않음)
    static func bonae(_ q: [String: String]) async -> GsDapResp {
        var r = API.request(URL(string: API.base + "gesipan.php")!)
        r.httpMethod = "POST"
        r.setValue("application/x-www-form-urlencoded; charset=utf-8", forHTTPHeaderField: "Content-Type")
        var all = q; all["k"] = API.key
        var cs = CharacterSet.alphanumerics; cs.insert(charactersIn: "-._~")
        r.httpBody = all.map { "\($0.key)=\($0.value.addingPercentEncoding(withAllowedCharacters: cs) ?? "")" }.joined(separator: "&").data(using: .utf8)
        guard let (d, _) = try? await URLSession.shared.data(for: r), let j = try? JSONDecoder().decode(GsDapResp.self, from: d) else {
            return GsDapResp(ok: false, msg: "보내지 못했습니다. 인터넷을 확인해 주십시오.", id: nil)
        }
        return j
    }
}

// MARK: 게시판 다섯
struct GesipanView: View {
    @EnvironmentObject var nav: Nav
    @State private var pan: [GsPan] = []
    @State private var msg = "가져오는 중입니다."
    @AccessibilityFocusState private var cheot: String?
    var body: some View {
        List {
            if pan.isEmpty { Text(msg) }
            ForEach(pan) { p in
                Button { nav.lib.append(Route.gsMok(p.b, p.t, p.sseugi)) } label: {
                    Text(p.t + ", 글 \(p.su)개" + (p.sae > 0 ? ", 이번 주 새 글 \(p.sae)개" : "")).foregroundStyle(.primary)
                }
                .accessibilityFocused($cheot, equals: p.b)
            }
        }
        .navigationTitle("게시판")
        .task {
            do { let r = try await GsAPI.pan(); if r.ok, let p = r.pan { pan = p; DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { cheot = p.first?.b } } else { msg = r.msg ?? "게시판을 가져오지 못했습니다." } }
            catch { msg = "도서관에 닿지 못했습니다. 인터넷을 확인해 주십시오." }
        }
    }
}

// MARK: 한 게시판 목록
struct GsMokView: View {
    @EnvironmentObject var nav: Nav
    let b: String; let ttl: String; let sseugi: Bool
    @State private var mok: [GsGeul] = []
    @State private var modu = 0
    @State private var o = 0
    @State private var msg = "가져오는 중입니다."
    @State private var sseuChang = false
    @AccessibilityFocusState private var cheot: String?
    var body: some View {
        List {
            if sseugi {
                Button(b == "jejak" ? "제작 요청하기" : "글쓰기") { sseuChang = true }
            }
            Section(mok.isEmpty ? ttl : "\(ttl), 글 \(modu)개") {
                if mok.isEmpty { Text(msg) }
                ForEach(mok) { g in
                    Button { nav.lib.append(Route.gsBogi(b, ttl, g.id)) } label: { Text(g.julMal).foregroundStyle(.primary) }
                        .accessibilityFocused($cheot, equals: g.id)
                }
            }
            if o + API.perPage < modu { Button("다음 \(API.perPage)개") { o += API.perPage; Task { await load() } } }
            if o > 0 { Button("앞 \(API.perPage)개") { o = max(0, o - API.perPage); Task { await load() } } }
        }
        .navigationTitle(ttl)
        .sheet(isPresented: $sseuChang, onDismiss: { Task { await load() } }) { GsSseugiView(b: b, ttl: ttl) }
        .task { await load() }
    }
    func load() async {
        do {
            let r = try await GsAPI.mok(b, o)
            if r.ok { mok = r.mok ?? []; modu = r.modu ?? 0; msg = mok.isEmpty ? "아직 글이 없습니다." + (sseugi ? " 위의 \(b == "jejak" ? "제작 요청하기" : "글쓰기")로 첫 글을 올리실 수 있습니다." : "") : "" }
            else { msg = r.msg ?? "목록을 가져오지 못했습니다." }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { cheot = mok.first?.id }
        } catch { msg = "도서관에 닿지 못했습니다. 인터넷을 확인해 주십시오." }
    }
}

// MARK: 글 보기
struct GsBogiView: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) private var dismiss
    let b: String; let ttl: String; let gid: String
    @State private var g: GsGeul?
    @State private var unyeong = false
    @State private var msg = "가져오는 중입니다."
    @State private var dapGeul = ""
    @State private var dangye = "jeopsu"
    @State private var jiugiMureum = false
    @AccessibilityFocusState private var jemokCheot: Bool
    var body: some View {
        List {
            if let g {
                Section {
                    Text("\(g.beon)번. \(g.jemok)").font(.headline).accessibilityAddTraits(.isHeader).accessibilityFocused($jemokCheot)
                    Text(g.ireum + ", " + g.nal + ((g.bimil ?? false) ? ", 비밀글" : "") + (g.dangye_t.map { ", 지금 단계 \($0)" } ?? "")).foregroundStyle(.secondary)
                    Text(g.naeyong ?? "")
                }
                Section("운영자 답 \(g.dap?.count ?? 0)개") {
                    if (g.dap ?? []).isEmpty { Text("아직 답이 없습니다.") }
                    ForEach(g.dap ?? [], id: \.self) { d in VStack(alignment: .leading) { Text(d.nal).foregroundStyle(.secondary); Text(d.naeyong) }.accessibilityElement(children: .combine) }
                }
                if unyeong {
                    Section("답하기") {
                        TextField("답 내용", text: $dapGeul, axis: .vertical).lineLimit(3...10)
                        Button("답 올리기") { Task { let r = await GsAPI.bonae(["a": "dap", "b": b, "id": gid, "naeyong": dapGeul]); store.say(r.msg ?? ""); if r.ok { dapGeul = ""; await load() } } }
                    }
                    if b == "jejak" {
                        Picker("단계", selection: $dangye) {
                            Text("접수").tag("jeopsu"); Text("제작 중").tag("jejakjung"); Text("완료").tag("wallyo"); Text("제작할 수 없음").tag("bulga")
                        }
                        .onChange(of: dangye) { _, v in guard v != (g.dangye ?? "jeopsu") else { return }; Task { let r = await GsAPI.bonae(["a": "dangye", "b": b, "id": gid, "dangye": v]); store.say(r.msg ?? ""); await load() } }
                    }
                }
                if (g.nae ?? false) || unyeong {
                    Button("이 글 지우기", role: .destructive) { jiugiMureum = true }
                }
            } else { Text(msg) }
        }
        .navigationTitle(ttl)
        .confirmationDialog("이 글을 지울까요?", isPresented: $jiugiMureum, titleVisibility: .visible) {
            Button("지우기", role: .destructive) { Task { let r = await GsAPI.bonae(["a": "jiugi", "b": b, "id": gid]); store.say(r.msg ?? ""); if r.ok { dismiss() } } }
            Button("그만두기", role: .cancel) {}
        }
        .task { await load() }
    }
    func load() async {
        do {
            let r = try await GsAPI.bogi(b, gid)
            if r.ok, let x = r.geul { g = x; unyeong = r.unyeong ?? false; dangye = x.dangye ?? "jeopsu"; DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { jemokCheot = true } }
            else { msg = r.msg ?? "글을 찾을 수 없습니다." }
        } catch { msg = "도서관에 닿지 못했습니다. 인터넷을 확인해 주십시오." }
    }
}

// MARK: 글쓰기 — 쓰던 글은 폰에 저절로 남김
struct GsSseugiView: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) private var dismiss
    let b: String; let ttl: String
    @State private var jemok = ""
    @State private var naeyong = ""
    @State private var bimil = false
    @State private var gojeong = false
    @State private var bonaeneunJung = false
    @AccessibilityFocusState private var cheot: Bool
    var choKey: String { "gsCho_" + b }
    var body: some View {
        NavigationStack {
            Form {
                if b == "jejak" { Text("책 이름과 지은이, 펴낸 곳을 아시는 대로 적어 주십시오. 한 달에 다섯 권까지 청하실 수 있고, 접수, 제작 중, 완료 단계를 목록에서 들으실 수 있습니다.") }
                TextField("제목", text: $jemok).accessibilityFocused($cheot)
                TextField("내용", text: $naeyong, axis: .vertical).lineLimit(5...20)
                if b == "mutdap" { Toggle("비밀글, 나와 운영자만 봅니다", isOn: $bimil) }
                if b == "gongji" { Toggle("맨 위에 고정", isOn: $gojeong) }
                Button(bonaeneunJung ? "올리는 중입니다" : "올리기") {
                    bonaeneunJung = true
                    Task {
                        let r = await GsAPI.bonae(["a": "sseugi", "b": b, "jemok": jemok, "naeyong": naeyong, "bimil": bimil ? "1" : "0", "gojeong": gojeong ? "1" : "0"])
                        bonaeneunJung = false; store.say(r.msg ?? "")
                        if r.ok { UserDefaults.standard.removeObject(forKey: choKey); dismiss() }
                    }
                }
                .disabled(bonaeneunJung)
                Text("쓰던 글은 이 폰에 저절로 남아, 창을 닫아도 다시 이어 쓰실 수 있습니다.").font(.footnote).foregroundStyle(.secondary)
            }
            .navigationTitle(ttl + (b == "jejak" ? " — 만들어 주셨으면 하는 책" : " 글쓰기"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) { Button("닫기") { dismiss() } } }
            .onAppear {
                if let d = UserDefaults.standard.dictionary(forKey: choKey) as? [String: String] { jemok = d["j"] ?? ""; naeyong = d["n"] ?? "" }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { cheot = true }
            }
            .onChange(of: jemok) { _, _ in nam() }
            .onChange(of: naeyong) { _, _ in nam() }
        }
    }
    func nam() { UserDefaults.standard.set(["j": jemok, "n": naeyong], forKey: choKey) }
}
