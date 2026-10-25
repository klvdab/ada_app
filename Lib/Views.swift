// AI점자도서관 앱 — 화면들 (판 0.8.5 빌드 261025-L24: 찾기 칸과 찾기 단추를 다른 줄로 — 아이폰 27판에서 칸에 글자가 안 들어가던 것, 도클, 대표님 승인 2026-10-25 「그래. 해보자」)
// 0.5.1 ( 빌드 261010-L10: 첫 화면 맨 위 이름 줄·바로 아래 찾기 칸, 목록 넘긴 뒤 커서를 첫 줄로 옮김(목록을 맨 위로 다시 그림), 목록에서 책을 한 번 두드리면 독서기가 열려 바로 읽음·책 정보는 독서기 더 보기 안 — 도서관 창 클, 이사장님 승인 「1」)
// 0.5.0 (빌드 261010-L9: 첫 화면에 고른 문의 서가 목록, 형태별 갈래·보일 책 뺌, 목록은 세 손가락 위아래 쓸기로 넘김·넘기면 커서 첫 줄 — 도서관 창 클, 이사장님 승인 「1」)
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
    @AccessibilityFocusState private var chatgiFocus: Bool   // 0.7.1 이어 들을 책이 없으면 찾기 칸이 첫 줄

    var body: some View {
        List {
            LibOllimJul()   // 0.4.3 새 판이 있을 때만 맨 위 한 줄(대장클, 이사장님 지시)
            Section {   // 0.5.1 맨 위 이름 줄, 바로 아래 찾기 칸(이사장님 지시)
                Meori()
                    .listRowInsets(EdgeInsets(top: 8, leading: 12, bottom: 8, trailing: 12))
                    .listRowBackground(Color.clear)
                // 0.8.5 (261025-L24) 찾기 칸과 찾기 단추를 다른 줄로 뗌 — 한 줄에 붙어 있으면 아이폰 27판에서 줄 전체가 단추로 묶여 칸에 글자가 들어가지 않음(대표님 2026-10-25)
                TextField("찾을 책 이름", text: $s)
                    .submitLabel(.search)
                    .onSubmit(chatgi)
                    .accessibilityFocused($chatgiFocus)
                Button("찾기", action: chatgi)
                    .buttonStyle(.borderless)
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
            MunSection()   // 0.3.0 — 세 겹의 문(0.5.0 고른 문의 서가 목록)
            Section {   // 0.8.4 게시판
                Button { nav.lib.append(Route.gesipan) } label: { Text("게시판, 공지와 묻고 답하기, 후기, 제작 요청, 불편 신고").foregroundStyle(.primary) }
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
        .onAppear {   // 0.7.1 탭을 고르면 커서를 첫 줄(이어 듣기, 없으면 찾기 칸)에
            guard nav.cheotJulBatgi(0) else { return }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { if store.last != nil { focusFirst = true } else { chatgiFocus = true } }
        }
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
    var ent = false   // 0.7.0 엔터테인먼트 목록(폴더는 엔터테인먼트 안에서 열고, 작품은 소리로 바로 틂)
    @EnvironmentObject var nav: Nav
    @State private var o = 0
    @State private var items: [Item] = []
    @State private var modu = 0
    @State private var msg = "가져오는 중입니다."
    @AccessibilityFocusState private var first: String?
    @State private var gen = 0   // 0.5.1 넘길 때마다 목록을 새로 그려 맨 위에서 시작(커서를 첫 줄에 놓으려고)

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
        .id(gen)
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
        if ent {
            if let i = it.i { nav.push(Route.reader(i, it.t, it.k ?? "sori")) } else if let j = it.j { nav.push(Route.ent(j, it.t)) }
            return
        }
        if let i = it.i { nav.push(Route.reader(i, it.t, "")) }   // 0.5.1 한 번 두드리면 독서기가 열려 바로 읽음(형식은 독서기가 알아냄)
        else if let j = it.j { nav.push(Route.jakbon(j, it.t)) }
    }
    func load(_ off: Int, malhagi: Bool = false) async {
        do {
            let r = try await loader(off)
            first = nil
            o = r.o ?? off; items = r.items ?? []; modu = r.modu ?? items.count
            if malhagi { gen += 1 }   // 0.5.1 목록을 맨 위부터 다시 그림 — 첫 줄 라벨이 「N번부터, 제목」이라 몇 번부터인지 커서가 놓이며 들림
            if let t = r.ttl { onTitle?(t) }
            msg = items.isEmpty ? "찾은 것이 없습니다." : ""
            if let f = items.first?.id {
                try? await Task.sleep(nanoseconds: 450_000_000)
                first = f
                if malhagi {   // 0.5.1 보이스오버가 쪽 넘김 뒤 커서를 잃지 않게 한 번 더
                    try? await Task.sleep(nanoseconds: 350_000_000)
                    first = nil
                    try? await Task.sleep(nanoseconds: 100_000_000)
                    first = f
                }
            }
        } catch { msg = "목록을 가져오지 못했습니다. 인터넷을 확인해 주십시오." }
    }
}

extension Nav {
    func push(_ r: Route) { if tab == 1 { seojae.append(r) } else if tab == 3 { ent.append(r) } else { lib.append(r) } }
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
                    Button(kind == "geul" ? "독서기로 듣기" : "틀기") {   // 0.5.1 누르면 바로 읽음
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
    @State private var kind: String
    @EnvironmentObject var r: Reader
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Nav
    @State private var more = false
    @State private var bulga = false     // 0.5.1 독서기로 들을 수 없는 형식
    @State private var yeolligo = false  // 0.5.1 처음 한 번만 저절로 읽기
    @State private var memoChang = false   // 0.6.0 메모 창
    @State private var memoGeul = ""
    @State private var jeungChang = false   // 0.8.3
    @AccessibilityFocusState private var focus: Bool
    init(i: Int, title: String, kind: String) { self.i = i; self.title = title; _kind = State(initialValue: kind) }

    var body: some View {
        if bulga {
            List {
                Text("이 형식은 아직 독서기로 들을 수 없습니다.").accessibilityFocused($focus)
                Button("책 정보") { nav.push(Route.book(i)) }
            }
            .navigationTitle(title)
        } else if kind.isEmpty {
            List { Text("책을 여는 중입니다.") }
                .navigationTitle(title)
                .task { await hyeongsik() }
        } else {
            boneun
                .sheet(isPresented: $jeungChang) { JeungView() }   // 0.8.3
        }
    }
    // 0.5.1 목록에서 바로 왔을 때 — 책 형식을 알아내 연다
    func hyeongsik() async {
        let b = try? await API.book(i)
        let k = b?.kind ?? "etc"
        if k == "etc" || k.isEmpty { bulga = true; try? await Task.sleep(nanoseconds: 400_000_000); focus = true }
        else { kind = k }
    }
    // 0.8.0 이어 듣기 중인가(이 화면에서 연 작품에서 이어져 지금 다른 곡·회를 틀고 있음)
    var ieoJung: Bool { API.isEnt(i) && r.ieoSijak == i && API.isEnt(r.i) && r.i != i }
    var boKind: String { ieoJung ? r.kind : kind }
    // 0.6.0 곱씹어 읽기
    func dasiDeutgi() { r.play(at: r.pos) }
    func jungyoPyosi() {
        if kind != "geul" || r.text.isEmpty { store.say("글자책에서만 중요 표시를 할 수 있습니다."); return }
        let ok = Gomssip.shared.pyosi(i: i, t: title, pos: r.pos, geul: r.text)
        store.say(ok ? "\(r.pos + 1)번째 문단에 중요 표시를 했습니다." : "이미 중요 표시가 된 문단입니다.")
    }
    var boneun: some View {
        List {
            if let p = r.player, boKind == "yeongsang" {   // 0.7.2 영상 화면은 읽기 단추 위에(안드로이드와 같게)
                Section { VideoPlayer(player: p).frame(height: 220) }
            }
            Section {
                Button(r.playing ? "멈춤" : (r.waiting ? "소리 만드는 중" : "읽기")) { r.toggle() }
                    .accessibilityFocused($focus)
                    .accessibilityAction(named: "이 문단 다시 듣기") { dasiDeutgi() }   // 0.6.0 — 위아래 쓸기로 바로
                    .accessibilityAction(named: "사전") { if kind == "geul" { nav.push(Route.sajeon(title, r.text)) } }
                    .accessibilityAction(named: "중요 표시하기") { jungyoPyosi() }
                Button("앞으로 30초") { r.gaCho(30) }   // 0.4.0 — 헷갈리지 않는 이름(이사장님)
                Button("뒤로 30초") { r.gaCho(-30) }
                JaesaengWichi()   // 0.4.0 — 재생 위치 막대
                if API.isEnt(i) {   // 0.8.0 (261018-L19) 이어 듣기·이어 보기
                    Button(boKind == "yeongsang" ? "다음 회" : "다음 곡") { r.ieoGa(1) }
                    Button(boKind == "yeongsang" ? "앞 회" : "앞 곡") { r.ieoGa(-1) }
                    Picker("듣는 방식, 지금 " + Store.ieoIreum(store.ieo), selection: Binding(get: { store.ieo }, set: { store.ieo = $0; store.save(); store.say(Store.ieoIreum($0) + "로 듣습니다."); r.ieoMiri() })) {
                        ForEach(Store.ieoGal, id: \.0) { Text($0.1).tag($0.0) }
                    }
                }
            }
            Section {
                Text(r.wichiMal).font(.footnote)
                if !r.status.isEmpty { Text(r.status) }
                if r.jamgeum { Button("장애 확인하기") { jeungChang = true } }   // 0.8.3 잠긴 책 — 서류 사진 올리는 화면
                if kind == "geul" {
                    Text(r.text.isEmpty ? "글자를 가져오는 중입니다." : r.text)
                    if !r.won.isEmpty { Text("원문: " + r.won).font(.footnote) }   // 0.6.1 한자음 읽기 — 음과 원문 함께 보기
                }
            }
            Section {
                DisclosureGroup("더 보기", isExpanded: $more) {
                    if kind == "geul" {   // 0.6.0 곱씹어 읽기와 사전
                        Button("이 문단 다시 듣기") { dasiDeutgi() }
                        Button("천천히 다시 듣기") { store.rateIndex = 0; store.save(); r.applyRate(); store.say("아주 느리게 다시 읽습니다. 빠르기는 더 빠르게로 되돌릴 수 있습니다."); dasiDeutgi() }
                        Button("풀어 읽기, 이 문단을 글자 단위로 짚기") { nav.push(Route.pureo(r.text)) }
                        Button("사전, 이 문단 낱말 뜻 찾기") { nav.push(Route.sajeon(title, r.text)) }
                        Button("중요 표시하기") { jungyoPyosi() }
                if kind == "geul" && r.hjgal { Button("한자음 읽기 바꾸기, 지금은 " + Store.hanjaeumIreum(store.hanjaeum)) { r.hanjaeumBakkugi() } }   // 0.6.1 (261012) 한자음 읽기
                        Button("중요 표시에 메모 붙이기") { memoGeul = Gomssip.shared.jungyo.first(where: { $0.i == i && $0.pos == r.pos })?.memo ?? ""; memoChang = true }
                        Button("책 속에서 찾기") { nav.push(Route.sokchatgi(i, title)) }
                    }
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
                    if !API.isEnt(i) { Button("책 정보, 책 소개·지은이·내려받기") { nav.push(Route.book(i)) } }   // 0.5.1 책 정보는 여기 안에(0.7.0 엔터테인먼트 작품은 책 정보가 없음)
                }
            }
        }
        .navigationTitle(ieoJung ? r.title : title)
        .alert("중요 표시 메모", isPresented: $memoChang) {   // 0.6.0 — 자판의 마이크로 말로 적을 수 있음
            TextField("메모", text: $memoGeul)
            Button("붙이기") { if !Gomssip.shared.jungyo.contains(where: { $0.i == i && $0.pos == r.pos }) { jungyoPyosi() }; Gomssip.shared.memo(i: i, pos: r.pos, memoGeul); store.say("메모를 붙였습니다.") }
            Button("그만", role: .cancel) {}
        }
        .task {
            if !ieoJung { await r.open(i: i, title: title, kind: kind) }   // 0.8.0 이어 들은 뒤 돌아와도 처음 작품으로 되돌아가지 않음
            if !yeolligo { yeolligo = true; if !r.playing { r.toggle() } }   // 0.5.1 열리면 바로 읽음(이사장님 지시 — 한 번 두드리면)
            try? await Task.sleep(nanoseconds: 500_000_000); focus = true   // 커서는 멈춤 단추에
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

// MARK: 엔터테인먼트 (0.7.0, 261014-L16 — 이사장님 승인 2026-10-14 18시 10분 「2」, 이름은 18시 08분 「1」)
// 나스 「AI문화관」의 공개 갈래만 연다(지금은 7 화면해설 작품). 첫 화면: 이름 줄, 찾기 칸, 이어 듣기, 갈래 목록, 더 보기.
// 작품은 소리책처럼 독서기에서 틀고, 읽던 자리·책갈피·내 서재가 도서관 책과 똑같이 쓰인다.
struct EntHomeView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Nav
    @State private var gal: [Gal] = []
    @State private var msg = "가져오는 중입니다."
    @State private var s = ""
    @State private var more = false
    @AccessibilityFocusState private var ieoFocus: Bool   // 0.7.1 탭을 고르면 커서를 첫 줄(이어 듣기, 없으면 찾기 칸)에
    @AccessibilityFocusState private var chatgiFocus: Bool
    private var ieo: ReadRec? { store.reads.filter({ API.isEnt($0.i) && !$0.done }).sorted(by: { $0.at > $1.at }).first }
    var body: some View {
        List {
            Section {
                Meori()
                    .listRowInsets(EdgeInsets(top: 8, leading: 12, bottom: 8, trailing: 12))
                    .listRowBackground(Color.clear)
                // 0.8.5 찾기 칸과 찾기 단추를 다른 줄로 뗌(도서관 첫 화면과 같음)
                TextField("찾을 작품 이름", text: $s)
                    .submitLabel(.search)
                    .onSubmit(chatgi)
                    .accessibilityFocused($chatgiFocus)
                Button("찾기", action: chatgi)
                    .buttonStyle(.borderless)
                if let l = ieo {
                    Button {
                        nav.ent.append(Route.reader(l.i, l.t, l.kind.isEmpty ? "sori" : l.kind))   // 0.7.2 영상이었으면 영상으로
                    } label: {
                        VStack(alignment: .leading, spacing: 3) {
                            Text("이어 듣기").font(.caption.weight(.semibold)).foregroundStyle(Saek.ganjo)
                            Text(l.t).font(.headline).foregroundStyle(.primary).lineLimit(2)
                            Text("\(l.wichiMal)부터").font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("이어 듣기, \(l.t), \(l.wichiMal)부터")
                    .accessibilityAddTraits(.isButton)
                    .accessibilityFocused($ieoFocus)
                    .listRowBackground(Saek.kadeu)
                }
            }
            Section {
                if gal.isEmpty { Text(msg) }
                ForEach(gal) { g in
                    Button("\(g.dan), \(g.n)편") { nav.ent.append(Route.ent(g.g, g.dan)) }
                }
            } header: { Text("갈래").foregroundStyle(Saek.ganjo) }   // 0.7.2 여덟 갈래가 다 보임
            Section {
                DisclosureGroup("더 보기", isExpanded: $more) {
                    Text("엔터테인먼트는 협회가 모아 온 영화, 드라마, 다큐 같은 자료를 모은 곳입니다. 영화, 드라마, 다큐·시사·교양, 애니메이션, 라디오·오디오 드라마, 음악, 화면해설 작품, 교육 여덟 갈래가 있습니다. 작품을 한 번 두드리면 바로 틀고, 영상은 화면도 함께 나오며, 듣던 자리를 기억합니다.")
                    Text(Pan.mal)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Saek.bada)
        .tint(Saek.ganjo)
        .navigationTitle("엔터테인먼트")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            guard nav.cheotJulBatgi(3) else { return }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { if ieo != nil { ieoFocus = true } else { chatgiFocus = true } }
        }
        .task {
            do {
                let r = try await API.entGal()
                gal = r.gal ?? []
                msg = gal.isEmpty ? "아직 열린 갈래가 없습니다." : ""
            } catch { msg = "엔터테인먼트에 닿지 못했습니다. 인터넷을 확인한 뒤 설정의 새로고침을 눌러 주십시오." }
        }
    }
    func chatgi() {
        let q = s.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return }
        nav.ent.append(Route.entFind(q))
    }
}

struct EntListView: View {
    let j: String
    let ttl: String
    var body: some View {
        PagedList(loader: { o in try await API.entList(j, o) }, ent: true)
            .navigationTitle(ttl)
    }
}

struct EntFindView: View {
    let s: String
    var body: some View {
        PagedList(loader: { o in try await API.entFind(s, o) }, ent: true)
            .navigationTitle("\(s) 찾은 결과")
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
                        .accessibilityFocused($focus, equals: "bin")   // 0.7.1 읽던 책이 없으면 이 줄이 첫 줄
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
                    Button("중요 표시 모음, \(Gomssip.shared.jungyo.count)개") { nav.push(Route.jungyo) }   // 0.6.0
                    Button("내 낱말장, \(Gomssip.shared.nanmal.count)개") { nav.push(Route.nanmal) }
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
        .onAppear {   // 0.7.1 탭을 고르면 커서를 읽던 책 첫 줄에(길눈처럼, 이사장님 지시)
            guard nav.cheotJulBatgi(1) else { return }
            let ilk = store.reading
            if ilk.isEmpty { Task { try? await Task.sleep(nanoseconds: 500_000_000); focus = "bin" } } else { focusCheot(ilk) }
        }
    }

    @ViewBuilder func jul(_ rc: ReadRec, mal: String, daIlgeum: Bool = false) -> some View {
        Button {
            if daIlgeum && !API.isEnt(rc.i) { nav.seojae.append(Route.book(rc.i)) } else { nav.seojae.append(Route.reader(rc.i, rc.t, rc.kind)) }
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
    @EnvironmentObject var nav: Nav
    @State private var q = ""
    @AppStorage("cheotMun") private var cheotMun = "jangreu"
    @AccessibilityFocusState private var cheot: Bool   // 0.7.1 탭을 고르면 커서를 첫 줄에
    @ObservedObject var hw = Hoewon.shared   // 0.8.3
    @State private var jeungChang = false
    var body: some View {
        List {
            Section {   // 0.5.0 첫 화면에 바로 나올 목록(이사장님 승인 「1」)
                Picker("첫 화면 목록", selection: $cheotMun) {
                    ForEach(MunIreum.modu, id: \.0) { Text($0.1).tag($0.0) }
                }
                .accessibilityFocused($cheot)
            }
            NaeryeoSeoljeong()   // 0.3.0 — 목록 줄 수, 와이파이에서만 내려받기, 저장 공간
            MoksoriSection()   // 0.4.0 — 목소리 열 가지 고르기와 미리 듣기
            JaesaengSeoljeong()   // 0.4.0 — 재생 위치 막대 한 번에 움직이는 양
            Section {   // 0.8.1 (261018-L20) 곡이 넘어갈 때 제목 읽기 — 듣는 화면에서 설정으로 옮김(이사장님 승인 「3」)
                Toggle("곡이 넘어갈 때 제목 읽기", isOn: Binding(get: { store.gogmal }, set: { store.gogmal = $0; store.save(); store.say($0 ? "곡이 넘어갈 때 제목을 읽습니다." : "곡이 넘어갈 때 제목을 읽지 않습니다.") }))
            }
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
            Section("내 회원 등급") {   // 0.8.3 (261019-L22) 회원 등급
                Text(hw.deungT.isEmpty ? "회원 등급을 확인하는 중입니다" : hw.deungT)
                if hw.ilban {
                    Text("지금은 외국 도서(저작권이 끝난 책)만 들으실 수 있습니다. 장애 확인을 받으시면 모든 책과 엔터테인먼트 작품을 들으십니다. " + Hoewon.jeungIreum(hw.jeung) + ".")
                    Button("장애 확인하기") { jeungChang = true }
                }
            }
            Section { Text("회원: \(Hoewon.shared.ireum)"); Text(Pan.mal) }
        }
        .navigationTitle("설정·도움말")
        .sheet(isPresented: $jeungChang) { JeungView() }   // 0.8.3
        .task { await hw.deungHwakin() }
        .onAppear {
            guard nav.cheotJulBatgi(2) else { return }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { cheot = true }
        }
    }
}

enum Doum {
    static let items: [(String, String)] = [
        ("탭 고르기", "아래 탭(도서관, 엔터테인먼트, 내 서재, 설정·도움말)을 고르면 커서가 그 화면 목록의 첫 줄에 놓입니다. 도서관과 엔터테인먼트는 이어 듣기, 이어 들을 것이 없으면 찾기 칸입니다. 내 서재는 읽던 책 첫 권입니다. 지금 탭을 다시 누르면 그 탭 첫 화면으로 돌아가 첫 줄에 놓입니다."),   // 0.7.1
        ("이어 듣기", "읽던 책이 있으면 도서관 첫 화면 찾기 칸 아래에 이어 듣기가 나옵니다. 누르면 독서기가 열리면서 읽던 자리부터 바로 읽고, 커서는 멈춤 단추에 놓입니다."),
        ("책 고르면 바로 읽기", "목록에서 책 이름을 한 번 두드리면 독서기가 열리면서 바로 읽습니다. 읽던 책이면 읽던 자리부터 읽습니다. 소리책과 동영상도 바로 틉니다. 커서는 멈춤 단추에 놓이니 잘못 고르셨으면 바로 멈추십시오. 책 소개, 지은이, 출판사, 내려받기는 독서기 더 보기 안의 책 정보에 있습니다."),
        ("책 찾기", "도서관 첫 화면 맨 위 이름 줄 바로 아래의 찾을 책 이름 칸을 두 번 두드리면 글자판이 열립니다. 낱말을 쓰고 글자판의 검색을 누르거나, 칸 바로 아래 줄의 찾기 단추를 누릅니다. 찾은 결과만 나오고 커서가 첫 줄에 놓입니다. 다시 찾을 때는 뒤로를 누릅니다. 엔터테인먼트 탭의 찾을 작품 이름 칸도 똑같이 씁니다."),   // 0.8.5
        ("갈래", "글자책, 소리책, 대본, 인터넷소설, 점자책으로 나뉩니다. 대본에는 드라마, 영화, 연극·뮤지컬, 라디오 드라마 대본이 들어 있습니다. 인터넷소설은 한 줄에 작품 하나로 나오고, 누르면 권이 차례로 나옵니다."),
        ("첫 화면 목록", "도서관 첫 화면에는 찾기 칸과 이어 듣기 아래에 설정의 첫 화면 목록에서 고른 목록(주제별, 장르별, 테마별 가운데 하나)의 서가가 바로 나옵니다. 처음 값은 장르별입니다. 다른 두 목록은 그 아래 한 줄씩 있습니다."),
        ("목록 넘기기", "목록은 처음에 한 쪽 15줄입니다. 설정의 목록 줄 수에서 5, 10, 15, 20, 30줄 가운데 고를 수 있습니다. 목록에서 세 손가락으로 위로 쓸면 다음 목록, 아래로 쓸면 앞 목록이 나오고 커서는 그 목록 첫 줄에 놓입니다. 손짓이 안 될 때는 맨 아래 다음 목록을 누릅니다."),
        ("세 겹의 문", "도서관 첫 화면에 주제별, 장르별, 테마별 세 문이 있습니다. 문을 누르면 서가와 책 수가 나오고, 서가를 누르면 책 목록이 나옵니다. 주제별은 도서관 십진분류, 장르별은 판타지·무협 같은 갈래, 테마별은 이달의 새 책 같은 모음입니다."),
        ("이 문단 다시 듣기", "책 읽는 화면 더 보기 안에 있습니다. 지금 읽던 문단을 처음부터 다시 읽습니다. 읽기 단추에서 위아래로 쓸어 이 문단 다시 듣기를 골라도 됩니다. 천천히 다시 듣기는 빠르기를 아주 느리게로 바꾸어 다시 읽고, 더 빠르게로 되돌릴 수 있습니다."),
        ("풀어 읽기", "책 읽는 화면 더 보기 안에 있습니다. 지금 문단을 한 덩이 글로 띄웁니다. 보이스오버 로터를 글자나 단어로 놓고 위아래로 쓸면 한 글자씩, 한 낱말씩 짚어 읽을 수 있어 외국어 철자나 한자를 확인할 때 좋습니다."),
        ("중요 표시와 메모", "책 읽는 화면 더 보기 안의 중요 표시하기를 누르면 지금 문단에 중요 표시가 붙습니다. 중요 표시에 메모 붙이기로 짧은 메모를 적을 수 있고, 자판의 마이크를 누르면 말로 적을 수 있습니다. 읽기 단추에서 위아래로 쓸어 중요 표시하기를 골라도 됩니다."),
        ("중요 표시 모음", "내 서재의 더 보기 안에 있습니다. 모든 책의 중요 표시가 최근 것부터 모이고, 누르면 그 문단으로 가서 바로 읽습니다. 지우려면 그 줄에서 위아래로 쓸어 중요 표시 지우기를 고릅니다."),
        ("책 속에서 찾기", "책 읽는 화면 더 보기 안에 있습니다. 낱말이나 이름을 적고 찾기를 누르면 그 말이 나오는 문단이 목록으로 나오고, 누르면 그 문단부터 읽습니다."),
        ("사전", "책 읽는 화면 더 보기 안의 사전을 누르면 지금 문단의 낱말이 목록으로 나오고, 고르면 바로 뜻을 들려줍니다. 낱말을 적어 찾을 수도 있습니다. 우리말은 한국어기초사전과 표준국어대사전에서 찾고, 두 사전에 적게 나오는 새말, 방언, 옛말, 전문어는 우리말샘에서 보충합니다. 영어는 워드넷, 일본어는 JMdict, 중국어는 CC-CEDICT에서 찾고, 한자는 한 글자마다 음과 뜻을 알려 줍니다. 한문 낱말이나 구절은 한국고전종합DB에서 경서 번역과 고전 번역 문장을 함께 들려줍니다. 뜻 끝에는 출처를 붙입니다."),
        ("내 낱말장", "사전에서 찾아본 낱말이 그 문장, 책 이름과 함께 저절로 모입니다. 내 서재의 더 보기 안에 있습니다. 지우려면 그 줄에서 위아래로 쓸어 낱말장에서 지우기를 고릅니다."),
        ("외국 도서", "외국어로 된 원서는 우리 책과 섞지 않고 외국 도서 문에 따로 모았습니다. 외국 도서를 누르면 영어, 중국어, 일본어, 프랑스어, 독일어 같은 언어와 책 수가 나오고, 언어를 누르면 소설, 시, 역사 같은 장르와 주제가 나옵니다. 영어 소설은 고전 명작, 모험, 추리와 스릴러처럼 한 겹 더 나뉩니다. 첫 화면의 외국 도서로 찾기를 누르거나, 설정의 첫 화면 목록에서 외국 도서를 고르면 첫 화면에 바로 언어 목록이 나옵니다."),
        ("띄어 읽기 바로잡기", "옛 글자책은 한 줄 길이를 정해 강제로 줄을 바꾼 것이 많아, 그렇습니다를 그 렇습니다처럼 낱말 가운데서 끊어 읽는 일이 있었습니다. 이제 도서관이 책마다 끊긴 줄을 알아보고 낱말을 바르게 이어, 문장이 끝나는 곳에서만 쉬며 읽어 드립니다. 읽던 자리와 책갈피는 책을 처음 열 때 새 자리로 맞춰 옮겨 드립니다. 내려받은 책은 받을 때의 글 그대로 읽습니다."),
        ("한자음 읽기", "중국 고전처럼 한자로 된 책은 우리 한자음으로 바꿔 읽어 드립니다. 학이시습지처럼 우리가 한문을 읽는 소리로 듣게 되고, 연보, 노인처럼 두음법칙도 지킵니다. 한문 구절은 한국고전종합DB의 경서 읽는 소리를 따라 불역열호, 불지위불지처럼 읽습니다. 중국 본토 글자(간체자)로 된 책도 먼저 옛 글자로 바꾼 뒤 읽습니다. 독서기 더 보기의 한자음 읽기 바꾸기를 누를 때마다 음만 듣기, 음과 원문 함께 보기, 꺼짐 차례로 바뀝니다. 음과 원문 함께 보기에서는 문단 아래에 원문 한자가 나와 점자나 화면으로 확인하실 수 있습니다. 일본 책은 일본어 그대로 읽습니다. 이 단추는 한자로 된 책을 열었을 때만 나옵니다."),
        ("내려받기", "독서기 더 보기 안의 책 정보 화면에서 폰에 내려받기를 누르면 책을 폰에 받아 둡니다. 글자책은 글 전체를, 소리책은 소리 파일을 받습니다. 받은 책은 이 앱 안에만 있고 다른 앱이나 파일 앱에서는 보이지 않습니다. 내 서재 맨 위 내려받은 책에 모입니다."),
        ("인터넷 없이 듣기", "인터넷이 끊기거나 데이터가 모자라도 내려받은 책은 들을 수 있습니다. 소리책은 받은 파일 그대로, 글자책은 폰 목소리로 읽습니다. 인터넷이 다시 되면 도서관 목소리로 읽습니다."),
        ("와이파이에서만 내려받기", "설정에서 켜 두면 휴대폰 데이터로는 내려받지 않습니다. 처음에는 켜져 있습니다. 데이터로도 받으려면 끄십시오."),
        ("내려받은 책 지우기", "책 정보 화면의 폰에서 지우기로 한 권씩, 설정의 내려받은 책 모두 지우기로 한꺼번에 지웁니다. 설정에 내려받은 책 권수와 차지한 크기가 나옵니다."),
        ("탭 다시 누르기", "지금 보고 있는 탭을 한 번 더 누르면 그 탭의 첫 화면 맨 위로 돌아갑니다."),
        ("목소리 고르기", "설정의 목소리 고르기에서 여자 1부터 5, 남자 1부터 5까지 열 가지 가운데 고릅니다. 누르면 그 목소리로 바뀌고 바로 미리 들려 드립니다. 처음 값은 여자 1입니다. 책 읽는 화면의 목소리에서도 바꿀 수 있습니다."),
        ("재생 위치 막대", "책 읽는 화면의 재생 위치에 커서를 두면 전체 시간과 지금 시간, 퍼센트를 읽어 줍니다. 한 손가락으로 위로 쓸면 앞으로, 아래로 쓸면 뒤로 갑니다. 한 번에 움직이는 양은 설정에서 5퍼센트나 1퍼센트로 고릅니다. 글자책의 시간은 읽는 빠르기로 셈한 대략의 시간입니다."),
        ("앞으로 30초와 뒤로 30초", "앞으로 30초는 지금 읽는 곳에서 30초 뒤의 내용으로 건너뛰고, 뒤로 30초는 30초 전의 내용으로 되돌아갑니다. 글자책은 읽는 빠르기로 30초 분량의 글만큼 움직입니다."),
        ("책 묶어 보기", "목록에는 파일 이름이 아니라 책 제목과 권수가 한 줄로 나옵니다. 보기: 야인시대, 전 117회. 그 줄을 누르면 야인시대 1회, 야인시대 2회처럼 제목과 번호가 차례대로 나옵니다. 찾기를 해도 같은 책은 묶음 한 줄로 나옵니다."),
        ("입체낭독", "드라마 대본을 인물마다 다른 목소리로 연기하듯 읽은 소리 드라마입니다. 이야기꾼과 주인공이 서로 다른 목소리로 나옵니다. 지금은 야인시대가 날마다 몇 회씩 늘어납니다. 소리책처럼 독서기에서 틀고, 듣던 자리를 기억합니다."),
        ("독서기", "글자책은 사람 목소리로 문단마다 읽어 줍니다. 한글 파일(hwp, hwpx)과 데이지 책도 읽습니다. 옛 한글 3.0 파일도 읽지만, 배포용이나 암호가 걸린 한글 파일은 읽지 못합니다. 앞 문단을 읽는 동안 뒤 문단을 미리 만들어 둡니다. 겉에는 멈춤과 읽기, 앞으로 30초, 뒤로 30초가 있고 빠르기, 목소리, 책갈피, 처음부터, 책 정보는 더 보기 안에 있습니다."),
        ("화면을 꺼도 읽기", "읽는 중에 화면을 끄거나 폰을 주머니에 넣어도 계속 읽습니다. 이어폰 단추와 잠금 화면으로 멈춤, 다음 문단, 앞 문단을 쓸 수 있습니다."),
        ("소리책과 동영상", "소리책과 동영상도 같은 독서기에서 틉니다. 다음과 앞은 30초씩 건너뜁니다. 읽던 자리는 5초마다 기억합니다."),
        ("폰에 내려받기", "책 정보에서 폰에 내려받기를 누르면 글자와 소리를 폰에 담아 인터넷이 없는 곳에서도 듣습니다. 책이 길면 오래 걸립니다."),
        ("책갈피", "독서기 더 보기에서 책갈피 넣기를 누릅니다. 책갈피 보기나 내 서재에서 그 자리부터 읽습니다. 책갈피를 지우려면 책갈피 줄에서 동작(위아래 쓸기)으로 책갈피 지우기를 고릅니다."),
        ("내 서재", "읽던 책이 겉에 나오고, 책갈피와 다 읽은 책, 내려받은 책 수는 더 보기 안에 있습니다."),
        ("내 서재에서 지우기", "읽기 시작한 책은 저절로 내 서재에 담기고, 가장 최근에 본 책이 맨 위에 15권씩 나옵니다. 지울 책 줄에서 보이스오버 동작(위아래 쓸기)으로 내 서재에서 지우기를 고르거나 왼쪽으로 밀면 지워집니다. 10초 안에 되돌리기를 누르면 되살아납니다. 다 읽은 책은 더 보기 안에 15권씩 있습니다."),
        ("새로고침", "앱이 이상하거나 새 책이 안 보일 때 설정의 새로고침을 누릅니다."),
        ("업데이트 — 새 판 받기", "도서관 앱은 켤 때와 앱으로 돌아올 때 협회 서버에 새 판이 나왔는지 스스로 물어봅니다. 새 판이 있으면 도서관 첫 화면 맨 위에 AI점자도서관 새 판이 나왔습니다, 두드리면 테스트플라이트에서 업데이트합니다라는 단추가 뜹니다. 두드리시면 테스트플라이트가 열리니 AI점자도서관 옆의 업데이트를 두 번 두드리십시오. 아이폰은 앱이 스스로 자기를 설치할 수 없어 테스트플라이트를 거칩니다. 테스트플라이트에서 자동 업데이트를 켜 두시면 저절로 깔립니다. 설정의 새로고침 바로 아래 업데이트 단추로 언제든 살피실 수 있습니다. 내 서재와 책갈피, 내려받은 책은 그대로 남습니다."),
        ("앱 안내 말소리", "앱이 스스로 알리는 말(책갈피를 넣었습니다 등)을 켜고 끕니다. 책 읽는 목소리는 그대로입니다."),
        ("소리를 기다릴 때", "처음 듣는 문단은 도서관 서버가 그 자리에서 목소리를 만듭니다. 여러 분이 함께 들으시면 조금 기다리실 수 있습니다. 10초가 넘으면 소리를 만들고 있다고, 45초가 넘으면 조금 더 걸린다고 한 번씩 알려 드리고, 4분까지 기다립니다. 한 번 만든 소리는 다음 분이 바로 들으십니다. 도서관은 한가할 때 새 책과 읽던 책 앞부분을 미리 만들어 둡니다."),
        ("이용 기록", "앱이 잘 듣는지 살피려고, 앱 판과 아이폰 판, 책을 연 때, 소리를 오래 기다리거나 만들지 못한 일을 협회 서버에 남깁니다. 이름, 전화번호, 듣는 책 이름과 내용은 남기지 않습니다. 협회는 이 기록으로 막히는 곳을 먼저 찾아 고칩니다. 책을 열면 그 책 번호를, 듣는 동안에는 1분마다 들은 시간을, 끝까지 들으면 다 들었다는 것을 함께 남깁니다(이용 장부). 이 장부는 이름과 전화번호 대신 알아볼 수 없는 가명 번호로만 적고, 도서관 실적(이용자 수, 이용 시간, 많이 읽힌 책)을 낼 때 숫자로만 셉니다. 누가 무슨 책을 읽었는지는 운영하는 사람에게도 보이지 않습니다."),   // 0.8.2
        ("엔터테인먼트", "아래 탭의 둘째 칸입니다. 협회가 모아 온 영화, 드라마, 다큐 같은 자료를 모은 곳입니다. 영화, 드라마, 다큐·시사·교양, 애니메이션, 라디오·오디오 드라마, 음악, 화면해설 작품, 교육 여덟 갈래가 있습니다. 갈래를 누르면 작품이 나오고, 드라마는 작품을 누르면 회차가 차례로 나옵니다. 회차를 한 번 두드리면 바로 틉니다. 독서기와 같아서 앞으로 30초, 뒤로 30초, 재생 위치, 빠르기, 책갈피를 그대로 쓰고, 듣던 자리를 기억해 엔터테인먼트 첫 화면의 이어 듣기와 내 서재에 나옵니다. 작품 이름을 적어 찾을 수도 있습니다."),
        ("음악·드라마 이어 듣기", "엔터테인먼트에서 음악을 틀면 곡이 끝날 때 다음 곡이 저절로 나오고, 드라마는 다음 회로 이어집니다. 독서기 화면의 듣는 방식에서 앨범·작품 끝까지(여러 장짜리 앨범은 다음 장까지), 갈래 전체 이어서, 섞어 듣기, 한 곡 반복, 한 곡만 가운데 고릅니다. 고른 것은 기억해 둡니다. 다음 곡·앞 곡 단추(드라마는 다음 회·앞 회)로 넘기고, 이어폰 단추 두 번 누르기와 잠금 화면의 다음 곡 단추로도 넘깁니다. 넘어갈 때 곡 이름을 한 번 알려 드리며, 설정의 곡이 넘어갈 때 제목 읽기를 끄면 조용히 넘어갑니다."),   // 0.8.0
        ("곡이 넘어갈 때 제목 읽기", "설정·도움말 탭에 있습니다. 켜 두면 음악이 다음 곡으로 넘어갈 때(드라마는 다음 회로 넘어갈 때) 제목을 한 번 읽어 드립니다. 새 앨범으로 넘어가면 앨범 이름도 함께 읽습니다. 끄면 제목을 읽지 않고 다음 곡이 바로 이어집니다. 처음에는 켜져 있습니다. 듣는 화면을 간결하게 두려고 이 설정은 설정·도움말 탭에만 둡니다."),   // 0.8.1
        ("영상 보기", "엔터테인먼트의 영상 작품은 소리와 함께 화면이 나옵니다. 화면은 읽기 단추 위에 있고, 화면을 두드리면 폰이 주는 재생 단추와 크게 보기가 나옵니다. 앞으로 30초, 뒤로 30초, 재생 위치, 책갈피는 소리 작품과 똑같이 씁니다. 영상은 데이터를 많이 쓰니 와이파이에서 보시기를 권합니다."),   // 0.7.2
        ("회원 등급", "회원은 일반 회원, 인증 회원, 봉사 회원, 운영자, 최고관리자로 나뉩니다. 처음 등록하면 일반 회원이고, 일반 회원은 외국 도서(저작권이 끝난 책)만 들으실 수 있습니다. 설정·도움말 탭의 내 회원 등급에서 장애 확인하기를 누르고, 장애인 복지카드나 장애인증명서를 사진으로 골라 올리시면 협회가 확인해 인증 회원으로 바꿔 드립니다. 그러면 모든 책과 엔터테인먼트 작품을 들으실 수 있습니다. 잠긴 책을 열면 인증 회원만 들을 수 있다고 알려 드리고, 그 자리에 장애 확인하기 단추가 나옵니다. 확인됐는지 다시 보기를 누르면 지금 등급을 다시 알려 드립니다."),   // 0.8.3
        ("회원 등록", "처음 켤 때 이름과 휴대전화 번호로 한 번만 등록합니다. 등록하면 이 폰만의 회원 열쇠가 담겨 도서관과 독서기가 열립니다. 폰을 바꾸면 새 폰에서 같은 번호로 다시 등록합니다. 열쇠가 막히면 등록 화면이 다시 나옵니다."),
        ("회원 정보 여쭙기", "처음 등록할 때, 이미 등록하신 분은 앱을 켤 때 한 번, 장애 유형, 사시는 지역, 연령대 세 가지를 여쭙고 이용 약관에 동의를 받습니다. 도서관 실적을 숫자로만 내는 데 씁니다. 셋 가운데 밝히고 싶지 않은 것은 「밝히지 않음」을 고르시면 됩니다. 「다음에 하기」를 누르면 그날은 다시 묻지 않습니다."),   // 0.8.2
        ("소리가 멈추면", "음악이나 소리책이 듣는 중에 6초 넘게 멈추면 앱이 스스로 그 자리부터 다시 이어 틉니다. 그래도 안 되면 그 곡을 새로 열어 멈춘 자리부터 틉니다."),   // 0.8.2
        ("게시판", "도서관 첫 화면 아래쪽의 게시판 줄을 누르면 공지, 묻고 답하기, 후기, 제작 요청, 불편 신고 다섯 가지가 나옵니다. 목록은 설정의 목록 줄 수만큼(처음 15줄) 나오고 최근 글이 맨 위입니다. 다음 목록과 앞 목록 단추로 넘깁니다."),   // 0.8.4
        ("게시판 글쓰기", "게시판에 들어가 글쓰기를 누르고 제목과 내용을 적은 뒤 올리기를 누릅니다. 공지는 운영자만 씁니다. 쓰던 글은 폰에 저절로 남아 창을 닫아도 다시 이어 쓸 수 있습니다. 내가 쓴 글은 열어서 이 글 지우기를 누르고 지우기를 한 번 더 고르면 지워집니다."),   // 0.8.4
        ("비밀글", "묻고 답하기에서 글을 쓸 때 비밀글을 켜면 나와 운영자만 봅니다."),   // 0.8.4
        ("제작 요청", "읽고 싶은 책을 도서관에 만들어 달라고 청하는 곳입니다. 인증 회원부터 한 달에 다섯 권까지 청하실 수 있고, 목록에서 접수, 제작 중, 완료, 제작할 수 없음 단계를 들으실 수 있습니다."),   // 0.8.4
        ("뒤로", "속 화면 맨 위에 뒤로가 있습니다. 보이스오버에서는 두 손가락으로 문질러도 뒤로 갑니다."),
    ]
}
