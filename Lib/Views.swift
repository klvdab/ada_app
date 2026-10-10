// AI점자도서관 앱 — 화면들 (판 0.5.0, 빌드 261010-L9: 첫 화면에 고른 문의 서가 목록, 형태별 갈래·보일 책 뺌, 목록은 세 손가락 위아래 쓸기로 넘김·넘기면 커서 첫 줄 — 도서관 창 클, 이사장님 승인 「1」)
// 0.2.0 (빌드 261002-1: 디자인 바탕(남색·금빛·로고·책 표지), 첫 화면 머리와 이어 듣기 카드, 내 서재 15개씩·지우기·되돌리기·다 읽은 책)
// 0.1.0 (260930-3) 도움말 갈래에 「대본」, 독서기에 한글·데이지
// 규칙: 한 줄에 이름 하나 단추 하나, 목록은 한 쪽에 15줄(아래에 더 보기, 그 아래 이전 보기),
// 결과가 나오면 커서를 첫 줄에, 겉에는 급한 것만 두고 나머지는 더 보기에 접는다.
import SwiftUI
import AVKit
import AVFoundation
import UIKit   // 0.5.0 UIAccessibility(쪽 넘김 알림)

// MARK: 첫 화면
struct HomeView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Nav
    @State private var gal: [Gal] = []
    @State private var msg = ""
    @State private var s = ""
    @State private var more = false
    @AccessibilityFocusState private var focusFirst: Bool

    var body: some View {
        List {
            LibOllimJul()   // 0.4.3 새 판이 있을 때만 맨 위 한 줄(대장클, 이사장님 지시)
            MunSection()   // 0.3.0 — 세 겹의 문
            Section {
                Meori()
                    .listRowInsets(EdgeInsets(top: 8, leading: 12, bottom: 8, trailing: 12))
                    .listRowBackground(Color.clear)
                if let l = store.last {
                    Button {
                        nav.lib.append(Route.reader(l.i, l.t, l.kind))
                    } label: {
                        HStack(spacing: 14) {
                            BookCover(title: l.t, keugi: 48)
                            VStack(alignment: .leading, spacing: 3) {
                                Text("이어 듣기").font(.caption.weight(.semibold)).foregroundStyle(Saek.ganjo)
                                Text(l.t).font(.headline).foregroundStyle(.primary).lineLimit(2)
                                Text("\(l.wichiMal)부터").font(.subheadline).foregroundStyle(.secondary)
                            }
                            Spacer(minLength: 0)
                        }
                    }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("이어 듣기, \(l.t), \(l.wichiMal)부터")
                    .accessibilityAddTraits(.isButton)
                    .accessibilityFocused($focusFirst)
                    .listRowBackground(Saek.kadeu)
                }
            }
            Section {
                HStack {
                    TextField("찾을 책 이름", text: $s)
                        .submitLabel(.search)
                        .onSubmit(chatgi)
                    Button("찾기", action: chatgi)
                }
            }
            Section {
                DisclosureGroup("더 보기", isExpanded: $more) {
                    Text("AI점자도서관은 두 가지를 합니다. 하나, 책을 사람 목소리로 읽어 주고 AI로 쉽고 빠르게 정보를 얻게 합니다. 둘, 책이 되지 못한 세상(영화, 드라마, 궁궐, 전시, 관광지)을 현장영상해설로 책으로 만듭니다.")
                    Text(Pan.mal)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Saek.bada)
        .tint(Saek.ganjo)
        .navigationTitle("AI점자도서관")
        .navigationBarTitleDisplayMode(.inline)
    }
    func chatgi() {
        let q = s.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return }
        nav.lib.append(Route.find(q))
    }
}

// MARK: 한 쪽 15줄 목록(공통) — 0.5.0 보이스오버 세 손가락 위로 쓸기는 다음 목록, 아래로 쓸기는 앞 목록, 넘기면 커서는 첫 줄
struct PagedList: View {
    let loader: (Int) async throws -> ListResp
    var onTitle: ((String) -> Void)? = nil
    @EnvironmentObject var nav: Nav
    @State private var o = 0
    @State private var items: [Item] = []
    @State private var modu = 0
    @State private var msg = "가져오는 중입니다."
    @AccessibilityFocusState private var first: String?

    var body: some View {
        List {
            if items.isEmpty {
                Text(msg)
            } else {
                Text("모두 \(modu.formatted())개 가운데 \(o + 1)번부터 \(o + items.count)번")
                    .font(.footnote)
                ForEach(items) { it in
                    Button(it.t) { go(it) }
                        .accessibilityLabel(it.id == items.first?.id && o > 0 ? "\(o + 1)번부터, \(it.t)" : it.t)
                        .accessibilityFocused($first, equals: it.id)
                        .accessibilityScrollAction { edge in neomgigi(edge) }
                }
                if o + API.perPage < modu {
                    Button("다음 목록") { Task { await load(o + API.perPage) } }   // 0.5.0 손짓이 안 먹을 때를 위해 하나만 남김
                        .accessibilityScrollAction { edge in neomgigi(edge) }
                }
            }
        }
        .task { if items.isEmpty { await load(0) } }
    }
    // 0.5.0 세 손가락 위아래 쓸기 — 위로 쓸면(아래쪽 내용) 다음 목록, 아래로 쓸면 앞 목록
    func neomgigi(_ edge: Edge) {
        switch edge {
        case .bottom, .trailing:
            if o + API.perPage < modu { Task { await load(o + API.perPage, malhagi: true) } }
            else { UIAccessibility.post(notification: .pageScrolled, argument: "마지막 목록입니다") }
        case .top, .leading:
            if o > 0 { Task { await load(max(0, o - API.perPage), malhagi: true) } }
            else { UIAccessibility.post(notification: .pageScrolled, argument: "첫 목록입니다") }
        }
    }
    func go(_ it: Item) {
        if let i = it.i { nav.push(Route.book(i)) }
        else if let j = it.j { nav.push(Route.jakbon(j, it.t)) }
    }
    func load(_ off: Int, malhagi: Bool = false) async {
        do {
            let r = try await loader(off)
            o = r.o ?? off; items = r.items ?? []; modu = r.modu ?? items.count
            if malhagi { UIAccessibility.post(notification: .pageScrolled, argument: "\(o + 1)번부터 \(o + items.count)번, 모두 \(modu.formatted())개") }
            if let t = r.ttl { onTitle?(t) }
            msg = items.isEmpty ? "찾은 것이 없습니다." : ""
            if let f = items.first?.id {
                try? await Task.sleep(nanoseconds: 400_000_000)
                first = f
            }
        } catch { msg = "목록을 가져오지 못했습니다. 인터넷을 확인해 주십시오." }
    }
}

extension Nav {
    func push(_ r: Route) { if tab == 1 { seojae.append(r) } else { lib.append(r) } }
}

struct GalListView: View {
    let g: String
    var body: some View {
        PagedList(loader: { o in
            g == "인터넷소설" ? try await API.list(g, o) : try await API.list(g, o)
        })
        .navigationTitle(g)
    }
}

struct JakbonView: View {
    let j: String
    let ttl: String
    var body: some View {
        PagedList(loader: { o in try await API.jakbon(j, o) })
            .navigationTitle(ttl)
    }
}

struct FindView: View {
    let s: String
    var body: some View {
        PagedList(loader: { o in try await API.find(s, o) })
            .navigationTitle("\(s) 찾은 결과")
    }
}

// MARK: 책 정보
struct BookView: View {
    let i: Int
    @EnvironmentObject var nav: Nav
    @EnvironmentObject var store: Store
    @EnvironmentObject var offline: Offline
    @State private var b: BookResp?
    @State private var msg = "가져오는 중입니다."
    @AccessibilityFocusState private var focus: Bool

    var body: some View {
        List {
            if let b, b.ok, let t = b.t {
                let kind = b.kind ?? "etc"
                if kind != "etc" {
                    Button(kind == "geul" ? "독서기로 듣기" : "틀기") {
                        nav.push(Route.reader(i, t, kind))
                    }
                    .accessibilityFocused($focus)
                } else {
                    Text("이 형식은 아직 독서기로 들을 수 없습니다.")
                }
                if let r = store.rec(i) { Text(r.done ? "다 읽은 책입니다." : "읽던 자리: \(r.wichiMal)") }
                Text("갈래 \(b.g ?? "")")
                if let s = b.sogae, !s.isEmpty {   // 0.4.2 — 책 소개(카카오 책 정보), 지은이, 출판사
                    if let j = b.jieun, !j.isEmpty { Text("지은이 " + j) }
                    if let c = b.chulpan, !c.isEmpty { Text("출판사 " + c) }
                    Text("책 소개. " + s)
                    Button("책 소개 듣기") { Miri.shared.deutgiGeul("책 소개. " + s) }
                }
                Text("크기 \(String(format: "%.1f", b.meg ?? 0))메가")
                Text("들어온 날 \(b.nal ?? "")")
                if kind != "etc" {   // 0.3.0 — 소리책도 내려받기
                    if store.downloaded.contains(i) {
                        Button("폰에서 지우기(내려받은 것)") { offline.remove(i) }
                    } else if offline.busy == i {
                        Text("내려받는 중, \(offline.total)문단 가운데 \(offline.done)")
                        Button("내려받기 멈추기") { offline.cancel() }
                    } else {
                        Button("폰에 내려받기(인터넷 없이 듣기)") { offline.download(i: i, title: t, kind: kind) }
                    }
                }
            } else {
                Text(msg)
            }
        }
        .navigationTitle(b?.t ?? "책 정보")
        .task {
            do {
                let r = try await API.book(i); b = r
                msg = r.ok ? "" : (r.msg ?? "책을 찾지 못했습니다.")
                try? await Task.sleep(nanoseconds: 400_000_000); focus = true
            } catch { msg = "책 정보를 가져오지 못했습니다." }
        }
    }
}

// MARK: 독서기
struct ReaderView: View {
    let i: Int
    let title: String
    let kind: String
    @EnvironmentObject var r: Reader
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Nav
    @State private var more = false
    @AccessibilityFocusState private var focus: Bool

    var body: some View {
        List {
            Section {
                Button(r.playing ? "멈춤" : (r.waiting ? "소리 만드는 중" : "읽기")) { r.toggle() }
                    .accessibilityFocused($focus)
                Button("앞으로 30초") { r.gaCho(30) }   // 0.4.0 — 헷갈리지 않는 이름(이사장님)
                Button("뒤로 30초") { r.gaCho(-30) }
                JaesaengWichi()   // 0.4.0 — 재생 위치 막대
            }
            Section {
                Text(r.wichiMal).font(.footnote)
                if !r.status.isEmpty { Text(r.status) }
                if kind == "geul" {
                    Text(r.text.isEmpty ? "글자를 가져오는 중입니다." : r.text)
                } else if let p = r.player, kind == "yeongsang" {
                    VideoPlayer(player: p).frame(height: 220)
                }
            }
            Section {
                DisclosureGroup("더 보기", isExpanded: $more) {
                    Button("더 느리게, 지금 \(Store.rateNames[store.rateIndex])") {
                        store.rateIndex = max(0, store.rateIndex - 1); store.save(); r.applyRate(); store.say(Store.rateNames[store.rateIndex])
                    }
                    Button("더 빠르게, 지금 \(Store.rateNames[store.rateIndex])") {
                        store.rateIndex = min(Store.rates.count - 1, store.rateIndex + 1); store.save(); r.applyRate(); store.say(Store.rateNames[store.rateIndex])
                    }
                    if kind == "geul" {
                        Picker("목소리, 지금 " + Moksori.name(store.voice), selection: Binding(get: { store.voice }, set: { store.voice = $0; store.save(); store.say(Moksori.name($0)) })) {   // 0.4.0 — 목소리 열 가지
                            ForEach(0..<10, id: \.self) { Text(Moksori.name($0)).tag($0) }
                        }
                    }
                    Button("책갈피 넣기") {
                        store.addMark(i: i, t: title, kind: kind, pos: kind == "geul" ? Double(r.pos) : r.mediaPos)
                        store.say("책갈피를 넣었습니다.")
                    }
                    Button("책갈피 보기") { nav.push(Route.marks(i)) }
                    Button("처음부터") { r.fromStart() }
                }
            }
        }
        .navigationTitle(title)
        .task {
            await r.open(i: i, title: title, kind: kind)
            try? await Task.sleep(nanoseconds: 400_000_000); focus = true
        }
    }
}

struct MarksView: View {
    let i: Int
    @EnvironmentObject var store: Store
    @EnvironmentObject var r: Reader
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        List {
            let ms = store.marks(of: i)
            if ms.isEmpty { Text("넣은 책갈피가 없습니다.") }
            ForEach(ms) { m in
                Button("\(m.wichiMal)부터 읽기") {
                    if m.kind == "geul" { r.play(at: Int(m.pos)) } else { r.player?.seek(to: .init(seconds: m.pos, preferredTimescale: 600)); r.player?.play(); r.playing = true }
                    dismiss()
                }
                .accessibilityAction(named: "책갈피 지우기") { store.removeMark(m) }
            }
        }
        .navigationTitle("책갈피")
    }
}

// MARK: 내 서재 (0.2.0, 261002-1 이사장님 승인)
// 읽기 시작한 책은 저절로 담김(가장 최근이 맨 위), 한 쪽 15개, 지우기는 줄마다 단추를 달지 않고
// 보이스오버 위아래 쓸기(동작) 「내 서재에서 지우기」로. 지운 뒤 10초 동안 「되돌리기」.
// 지우면 폰에 내려받아 둔 책도 함께 지워 공간을 돌려드림. 다 읽은 책은 따로 묶음.
struct SeojaeView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Nav
    @State private var more = false
    @State private var o = 0
    @State private var od = 0
    @State private var jiun: (ReadRec, [Mark])? = nil
    @State private var jiunTask: Task<Void, Never>? = nil
    @AccessibilityFocusState private var focus: String?
    static var jjok: Int { API.perPage }   // 0.3.0 — 설정의 목록 줄 수

    var body: some View {
        List {
            NaeryeoSection()   // 0.3.0 — 내려받은 책
            if let rc = jiun?.0 {
                Section {
                    Button("되돌리기, 방금 지운 \(rc.t)") { doedollrigi() }
                        .font(.headline)
                        .foregroundStyle(Saek.ganjo)
                        .accessibilityFocused($focus, equals: "doedol")
                        .listRowBackground(Saek.kadeu)
                }
            }
            Section {
                let ilk = store.reading
                if ilk.isEmpty {
                    Text("읽던 책이 없습니다. 도서관에서 책을 찾아 읽기 시작하면 여기에 저절로 담깁니다.")
                } else {
                    Text("읽던 책 \(ilk.count)권 가운데 \(o + 1)번부터 \(min(o + Self.jjok, ilk.count))번")
                        .font(.footnote).foregroundStyle(.secondary)
                    ForEach(Array(ilk.dropFirst(o).prefix(Self.jjok))) { rc in
                        jul(rc, mal: "\(rc.t), \(rc.wichiMal)부터")
                    }
                    if o + Self.jjok < ilk.count { Button("더 보기") { o += Self.jjok; focusCheot(ilk) } }
                    if o > 0 { Button("이전 보기") { o = max(0, o - Self.jjok); focusCheot(ilk) } }
                }
            } header: { Text("읽던 책").foregroundStyle(Saek.ganjo) }
            Section {
                DisclosureGroup("더 보기", isExpanded: $more) {
                    let da = store.finished
                    Text("다 읽은 책 \(da.count)권")
                    ForEach(Array(da.dropFirst(od).prefix(Self.jjok))) { rc in
                        jul(rc, mal: "\(rc.t), 다 읽음", daIlgeum: true)
                    }
                    if od + Self.jjok < da.count { Button("다 읽은 책 더 보기") { od += Self.jjok } }
                    if od > 0 { Button("다 읽은 책 이전 보기") { od = max(0, od - Self.jjok) } }
                    Text("책갈피 \(store.marks.count)개")
                    ForEach(store.marks.sorted { $0.at > $1.at }) { m in
                        Button("\(m.t), \(m.wichiMal)") { nav.seojae.append(Route.reader(m.i, m.t, m.kind)) }
                    }
                    Text("폰에 내려받은 책 \(store.downloaded.count)권")
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Saek.bada)
        .tint(Saek.ganjo)
        .navigationTitle("내 서재")
    }

    @ViewBuilder func jul(_ rc: ReadRec, mal: String, daIlgeum: Bool = false) -> some View {
        Button {
            if daIlgeum { nav.seojae.append(Route.book(rc.i)) } else { nav.seojae.append(Route.reader(rc.i, rc.t, rc.kind)) }
        } label: {
            HStack(spacing: 12) {
                BookCover(title: rc.t, keugi: 40)
                VStack(alignment: .leading, spacing: 2) {
                    Text(rc.t).font(.body.weight(.semibold)).foregroundStyle(.primary).lineLimit(2)
                    Text(daIlgeum ? "다 읽음" : "\(rc.wichiMal)부터").font(.footnote).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(mal)
        .accessibilityAddTraits(.isButton)
        .accessibilityAction(named: "내 서재에서 지우기") { jiugi(rc) }
        .accessibilityFocused($focus, equals: "r\(rc.i)")
        .swipeActions(edge: .trailing) {
            Button("지우기", role: .destructive) { jiugi(rc) }
        }
        .listRowBackground(Saek.kadeu)
    }

    func jiugi(_ rc: ReadRec) {
        guard let d = store.jiugi(rc.i) else { return }
        jiun = d
        store.say("\(rc.t)을 내 서재에서 지웠습니다. 10초 안에 되돌리기를 누르시면 되살아납니다.")
        Task { try? await Task.sleep(nanoseconds: 500_000_000); focus = "doedol" }
        jiunTask?.cancel()
        jiunTask = Task {
            try? await Task.sleep(nanoseconds: 10_000_000_000)
            if !Task.isCancelled, let r = jiun?.0 {
                Offline.shared.remove(r.i)   // 되돌리지 않으면 내려받은 책도 지워 공간을 돌려드림
                jiun = nil
            }
        }
        if o >= store.reading.count, o > 0 { o = max(0, o - Self.jjok) }
    }
    func doedollrigi() {
        guard let j = jiun else { return }
        let rc = j.0, mk = j.1
        jiunTask?.cancel()
        store.doedollrigi(rc, mk)
        jiun = nil
        store.say("\(rc.t)을 되살렸습니다.")
        Task { try? await Task.sleep(nanoseconds: 400_000_000); focus = "r\(rc.i)" }
    }
    func focusCheot(_ ilk: [ReadRec]) {
        if let f = ilk.dropFirst(o).first { Task { try? await Task.sleep(nanoseconds: 400_000_000); focus = "r\(f.i)" } }
    }
}

// MARK: 설정·도움말
struct SettingsView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var r: Reader
    @State private var q = ""
    @AppStorage("cheotMun") private var cheotMun = "jangreu"
    var body: some View {
        List {
            Section {   // 0.5.0 첫 화면에 바로 나올 목록(이사장님 승인 「1」)
                Picker("첫 화면 목록", selection: $cheotMun) {
                    ForEach(MunIreum.modu, id: \.0) { Text($0.1).tag($0.0) }
                }
            }
            NaeryeoSeoljeong()   // 0.3.0 — 목록 줄 수, 와이파이에서만 내려받기, 저장 공간
            MoksoriSection()   // 0.4.0 — 목소리 열 가지 고르기와 미리 듣기
            JaesaengSeoljeong()   // 0.4.0 — 재생 위치 막대 한 번에 움직이는 양
            Section {
                Button("새로고침") {
                    URLCache.shared.removeAllCachedResponses()
                    r.stop(); r.i = -1
                    store.say("새로고침했습니다. 도서관 탭으로 가시면 목록을 새로 가져옵니다.")
                }
                LibOllimDanchu()   // 0.4.3 업데이트 — 새로고침 바로 아래
                Toggle("앱 안내 말소리", isOn: Binding(get: { store.speechOn }, set: { store.speechOn = $0; store.save() }))
            }
            Section("도움말 찾기") {
                TextField("찾을 낱말", text: $q)
                ForEach(Doum.items.filter { q.isEmpty || $0.0.contains(q) || $0.1.contains(q) }, id: \.0) { it in
                    VStack(alignment: .leading) { Text(it.0).bold(); Text(it.1) }
                        .accessibilityElement(children: .combine)
                }
            }
            Section { Text("회원: \(Hoewon.shared.ireum)"); Text(Pan.mal) }
        }
        .navigationTitle("설정·도움말")
    }
}

enum Doum {
    static let items: [(String, String)] = [
        ("이어 읽기", "읽던 책이 있으면 도서관 첫 화면 맨 위에 이어 읽기가 나옵니다. 누르면 독서기가 열리고 커서가 읽기 단추에 놓입니다. 읽기를 누르면 읽던 자리부터 읽습니다."),
        ("책 찾기", "도서관 첫 화면의 찾을 책 이름 칸에 낱말을 쓰고 찾기를 누릅니다. 찾은 결과만 나오고 커서가 첫 줄에 놓입니다. 다시 찾을 때는 뒤로를 누릅니다."),
        ("갈래", "글자책, 소리책, 대본, 인터넷소설, 점자책으로 나뉩니다. 대본에는 드라마, 영화, 연극·뮤지컬, 라디오 드라마 대본이 들어 있습니다. 인터넷소설은 한 줄에 작품 하나로 나오고, 누르면 권이 차례로 나옵니다."),
        ("첫 화면 목록", "도서관 첫 화면에는 설정의 첫 화면 목록에서 고른 목록(주제별, 장르별, 테마별 가운데 하나)의 서가가 바로 나옵니다. 처음 값은 장르별입니다. 다른 두 목록은 그 아래 한 줄씩 있습니다."),
        ("목록 넘기기", "목록은 처음에 한 쪽 15줄입니다. 설정의 목록 줄 수에서 5, 10, 15, 20, 30줄 가운데 고를 수 있습니다. 목록에서 세 손가락으로 위로 쓸면 다음 목록, 아래로 쓸면 앞 목록이 나오고 커서는 그 목록 첫 줄에 놓입니다. 손짓이 안 될 때는 맨 아래 다음 목록을 누릅니다."),
        ("세 겹의 문", "도서관 첫 화면 맨 위에 주제별, 장르별, 테마별 세 문이 있습니다. 문을 누르면 서가와 책 수가 나오고, 서가를 누르면 책 목록이 나옵니다. 주제별은 도서관 십진분류, 장르별은 판타지·무협 같은 갈래, 테마별은 이달의 새 책 같은 모음입니다."),
        ("내려받기", "책 정보 화면의 폰에 내려받기를 누르면 책을 폰에 받아 둡니다. 글자책은 글 전체를, 소리책은 소리 파일을 받습니다. 받은 책은 이 앱 안에만 있고 다른 앱이나 파일 앱에서는 보이지 않습니다. 내 서재 맨 위 내려받은 책에 모입니다."),
        ("인터넷 없이 듣기", "인터넷이 끊기거나 데이터가 모자라도 내려받은 책은 들을 수 있습니다. 소리책은 받은 파일 그대로, 글자책은 폰 목소리로 읽습니다. 인터넷이 다시 되면 도서관 목소리로 읽습니다."),
        ("와이파이에서만 내려받기", "설정에서 켜 두면 휴대폰 데이터로는 내려받지 않습니다. 처음에는 켜져 있습니다. 데이터로도 받으려면 끄십시오."),
        ("내려받은 책 지우기", "책 정보 화면의 폰에서 지우기로 한 권씩, 설정의 내려받은 책 모두 지우기로 한꺼번에 지웁니다. 설정에 내려받은 책 권수와 차지한 크기가 나옵니다."),
        ("탭 다시 누르기", "지금 보고 있는 탭을 한 번 더 누르면 그 탭의 첫 화면 맨 위로 돌아갑니다."),
        ("목소리 고르기", "설정의 목소리 고르기에서 여자 1부터 5, 남자 1부터 5까지 열 가지 가운데 고릅니다. 누르면 그 목소리로 바뀌고 바로 미리 들려 드립니다. 처음 값은 여자 1입니다. 책 읽는 화면의 목소리에서도 바꿀 수 있습니다."),
        ("재생 위치 막대", "책 읽는 화면의 재생 위치에 커서를 두면 전체 시간과 지금 시간, 퍼센트를 읽어 줍니다. 한 손가락으로 위로 쓸면 앞으로, 아래로 쓸면 뒤로 갑니다. 한 번에 움직이는 양은 설정에서 5퍼센트나 1퍼센트로 고릅니다. 글자책의 시간은 읽는 빠르기로 셈한 대략의 시간입니다."),
        ("앞으로 30초와 뒤로 30초", "앞으로 30초는 지금 읽는 곳에서 30초 뒤의 내용으로 건너뛰고, 뒤로 30초는 30초 전의 내용으로 되돌아갑니다. 글자책은 읽는 빠르기로 30초 분량의 글만큼 움직입니다."),
        ("책 묶어 보기", "목록에는 파일 이름이 아니라 책 제목과 권수가 한 줄로 나옵니다. 보기: 야인시대, 전 117회. 그 줄을 누르면 야인시대 1회, 야인시대 2회처럼 제목과 번호가 차례대로 나옵니다. 찾기를 해도 같은 책은 묶음 한 줄로 나옵니다."),
        ("입체낭독", "드라마 대본을 인물마다 다른 목소리로 연기하듯 읽은 소리 드라마입니다. 이야기꾼과 주인공이 서로 다른 목소리로 나옵니다. 지금은 야인시대가 날마다 몇 회씩 늘어납니다. 소리책처럼 독서기에서 틀고, 듣던 자리를 기억합니다."),
        ("독서기", "글자책은 사람 목소리로 문단마다 읽어 줍니다. 한글 파일(hwp, hwpx)과 데이지 책도 읽습니다. 옛 한글 3.0 파일도 읽지만, 배포용이나 암호가 걸린 한글 파일은 읽지 못합니다. 앞 문단을 읽는 동안 뒤 문단을 미리 만들어 둡니다. 겉에는 읽기, 다음 문단, 앞 문단이 있고 빠르기, 목소리, 책갈피, 처음부터는 더 보기 안에 있습니다."),
        ("화면을 꺼도 읽기", "읽는 중에 화면을 끄거나 폰을 주머니에 넣어도 계속 읽습니다. 이어폰 단추와 잠금 화면으로 멈춤, 다음 문단, 앞 문단을 쓸 수 있습니다."),
        ("소리책과 동영상", "소리책과 동영상도 같은 독서기에서 틉니다. 다음과 앞은 30초씩 건너뜁니다. 읽던 자리는 5초마다 기억합니다."),
        ("폰에 내려받기", "책 정보에서 폰에 내려받기를 누르면 글자와 소리를 폰에 담아 인터넷이 없는 곳에서도 듣습니다. 책이 길면 오래 걸립니다."),
        ("책갈피", "독서기 더 보기에서 책갈피 넣기를 누릅니다. 책갈피 보기나 내 서재에서 그 자리부터 읽습니다. 책갈피를 지우려면 책갈피 줄에서 동작(위아래 쓸기)으로 책갈피 지우기를 고릅니다."),
        ("내 서재", "읽던 책이 겉에 나오고, 책갈피와 다 읽은 책, 내려받은 책 수는 더 보기 안에 있습니다."),
        ("내 서재에서 지우기", "읽기 시작한 책은 저절로 내 서재에 담기고, 가장 최근에 본 책이 맨 위에 15권씩 나옵니다. 지울 책 줄에서 보이스오버 동작(위아래 쓸기)으로 내 서재에서 지우기를 고르거나 왼쪽으로 밀면 지워집니다. 10초 안에 되돌리기를 누르면 되살아납니다. 다 읽은 책은 더 보기 안에 15권씩 있습니다."),
        ("새로고침", "앱이 이상하거나 새 책이 안 보일 때 설정의 새로고침을 누릅니다."),
        ("업데이트 — 새 판 받기", "도서관 앱은 켤 때와 앱으로 돌아올 때 협회 서버에 새 판이 나왔는지 스스로 물어봅니다. 새 판이 있으면 도서관 첫 화면 맨 위에 AI점자도서관 새 판이 나왔습니다, 두드리면 테스트플라이트에서 업데이트합니다라는 단추가 뜹니다. 두드리시면 테스트플라이트가 열리니 AI점자도서관 옆의 업데이트를 두 번 두드리십시오. 아이폰은 앱이 스스로 자기를 설치할 수 없어 테스트플라이트를 거칩니다. 테스트플라이트에서 자동 업데이트를 켜 두시면 저절로 깔립니다. 설정의 새로고침 바로 아래 업데이트 단추로 언제든 살피실 수 있습니다. 내 서재와 책갈피, 내려받은 책은 그대로 남습니다."),
        ("앱 안내 말소리", "앱이 스스로 알리는 말(책갈피를 넣었습니다 등)을 켜고 끕니다. 책 읽는 목소리는 그대로입니다."),
        ("회원 등록", "처음 켤 때 이름과 휴대전화 번호로 한 번만 등록합니다. 등록하면 이 폰만의 회원 열쇠가 담겨 도서관과 독서기가 열립니다. 폰을 바꾸면 새 폰에서 같은 번호로 다시 등록합니다. 열쇠가 막히면 등록 화면이 다시 나옵니다."),
        ("뒤로", "속 화면 맨 위에 뒤로가 있습니다. 보이스오버에서는 두 손가락으로 문질러도 뒤로 갑니다."),
    ]
}
