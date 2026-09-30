// AI점자도서관 앱 — 화면들 (판 0.1.0, 빌드 260930-3: 도움말 갈래에 「대본」, 독서기에 한글·데이지)
// 규칙: 한 줄에 이름 하나 단추 하나, 목록은 한 쪽에 15줄(아래에 더 보기, 그 아래 이전 보기),
// 결과가 나오면 커서를 첫 줄에, 겉에는 급한 것만 두고 나머지는 더 보기에 접는다.
import SwiftUI
import AVKit
import AVFoundation

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
            if let l = store.last {
                Button("이어 읽기, \(l.t) \(l.wichiMal)부터") {
                    nav.lib.append(Route.reader(l.i, l.t, l.kind))
                }
                .accessibilityFocused($focusFirst)
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
                ForEach(gal) { g in
                    NavigationLink(value: Route.list(g.g)) {
                        Text("\(g.g) \(g.n.formatted())\(g.dan)")
                    }
                }
                if !msg.isEmpty { Text(msg) }
            }
            Section {
                DisclosureGroup("더 보기", isExpanded: $more) {
                    Text("AI점자도서관은 두 가지를 합니다. 하나, 책을 사람 목소리로 읽어 주고 AI로 쉽고 빠르게 정보를 얻게 합니다. 둘, 책이 되지 못한 세상(영화, 드라마, 궁궐, 전시, 관광지)을 현장영상해설로 책으로 만듭니다.")
                    Text(Pan.mal)
                }
            }
        }
        .navigationTitle("AI점자도서관")
        .task { await load() }
        .refreshable { await load() }
    }
    func chatgi() {
        let q = s.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return }
        nav.lib.append(Route.find(q))
    }
    func load() async {
        do {
            let r = try await API.gal()
            gal = r.gal ?? []
            msg = gal.isEmpty ? "갈래를 가져오지 못했습니다." : ""
        } catch { msg = "도서관에 닿지 못했습니다. 인터넷을 확인한 뒤 설정의 새로고침을 눌러 주십시오." }
    }
}

// MARK: 한 쪽 15줄 목록(공통)
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
                        .accessibilityFocused($first, equals: it.id)
                }
                if o + API.perPage < modu {
                    Button("더 보기") { Task { await load(o + API.perPage) } }
                }
                if o > 0 {
                    Button("이전 보기") { Task { await load(max(0, o - API.perPage)) } }
                }
            }
        }
        .task { if items.isEmpty { await load(0) } }
    }
    func go(_ it: Item) {
        if let i = it.i { nav.push(Route.book(i)) }
        else if let j = it.j { nav.push(Route.jakbon(j, it.t)) }
    }
    func load(_ off: Int) async {
        do {
            let r = try await loader(off)
            o = r.o ?? off; items = r.items ?? []; modu = r.modu ?? items.count
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
                Text("크기 \(String(format: "%.1f", b.meg ?? 0))메가")
                Text("들어온 날 \(b.nal ?? "")")
                if kind == "geul" {
                    if store.downloaded.contains(i) {
                        Button("폰에서 지우기(내려받은 것)") { offline.remove(i) }
                    } else if offline.busy == i {
                        Text("내려받는 중, \(offline.total)문단 가운데 \(offline.done)")
                        Button("내려받기 멈추기") { offline.cancel() }
                    } else {
                        Button("폰에 내려받기(인터넷 없이 듣기)") { offline.download(i: i, title: t) }
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
                Button(kind == "geul" ? "다음 문단" : "30초 뒤로 건너뛰기") { r.next() }
                Button(kind == "geul" ? "앞 문단" : "30초 앞으로 되돌리기") { r.prev() }
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
                        Button("목소리 바꾸기, 지금 목소리 \(store.voice + 1)") {
                            store.voice = store.voice == 0 ? 1 : 0; store.save()
                            if r.playing { r.play(at: r.pos) }
                            store.say("목소리 \(store.voice + 1)")
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

// MARK: 내 서재
struct SeojaeView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Nav
    @State private var more = false
    var body: some View {
        List {
            Section("읽던 책") {
                if store.reading.isEmpty { Text("읽던 책이 없습니다.") }
                ForEach(store.reading) { rc in
                    Button("\(rc.t), \(rc.wichiMal)부터") { nav.seojae.append(Route.reader(rc.i, rc.t, rc.kind)) }
                }
            }
            Section {
                DisclosureGroup("더 보기", isExpanded: $more) {
                    Text("책갈피 \(store.marks.count)개")
                    ForEach(store.marks.sorted { $0.at > $1.at }) { m in
                        Button("\(m.t), \(m.wichiMal)") { nav.seojae.append(Route.reader(m.i, m.t, m.kind)) }
                    }
                    Text("다 읽은 책 \(store.finished.count)권")
                    ForEach(store.finished) { rc in
                        Button(rc.t) { nav.seojae.append(Route.book(rc.i)) }
                    }
                    Text("폰에 내려받은 책 \(store.downloaded.count)권")
                }
            }
        }
        .navigationTitle("내 서재")
    }
}

// MARK: 설정·도움말
struct SettingsView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var r: Reader
    @State private var q = ""
    var body: some View {
        List {
            Section {
                Button("새로고침") {
                    URLCache.shared.removeAllCachedResponses()
                    r.stop(); r.i = -1
                    store.say("새로고침했습니다. 도서관 탭으로 가시면 목록을 새로 가져옵니다.")
                }
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
        ("목록 넘기기", "목록은 한 쪽에 15줄입니다. 아래의 더 보기로 다음 15줄, 그 아래 이전 보기로 앞의 15줄을 봅니다."),
        ("독서기", "글자책은 사람 목소리로 문단마다 읽어 줍니다. 한글 파일(hwp, hwpx)과 데이지 책도 읽습니다. 옛 한글 3.0 파일도 읽지만, 배포용이나 암호가 걸린 한글 파일은 읽지 못합니다. 앞 문단을 읽는 동안 뒤 문단을 미리 만들어 둡니다. 겉에는 읽기, 다음 문단, 앞 문단이 있고 빠르기, 목소리, 책갈피, 처음부터는 더 보기 안에 있습니다."),
        ("화면을 꺼도 읽기", "읽는 중에 화면을 끄거나 폰을 주머니에 넣어도 계속 읽습니다. 이어폰 단추와 잠금 화면으로 멈춤, 다음 문단, 앞 문단을 쓸 수 있습니다."),
        ("소리책과 동영상", "소리책과 동영상도 같은 독서기에서 틉니다. 다음과 앞은 30초씩 건너뜁니다. 읽던 자리는 5초마다 기억합니다."),
        ("폰에 내려받기", "책 정보에서 폰에 내려받기를 누르면 글자와 소리를 폰에 담아 인터넷이 없는 곳에서도 듣습니다. 책이 길면 오래 걸립니다."),
        ("책갈피", "독서기 더 보기에서 책갈피 넣기를 누릅니다. 책갈피 보기나 내 서재에서 그 자리부터 읽습니다. 책갈피를 지우려면 책갈피 줄에서 동작(위아래 쓸기)으로 책갈피 지우기를 고릅니다."),
        ("내 서재", "읽던 책이 겉에 나오고, 책갈피와 다 읽은 책, 내려받은 책 수는 더 보기 안에 있습니다."),
        ("새로고침", "앱이 이상하거나 새 책이 안 보일 때 설정의 새로고침을 누릅니다."),
        ("앱 안내 말소리", "앱이 스스로 알리는 말(책갈피를 넣었습니다 등)을 켜고 끕니다. 책 읽는 목소리는 그대로입니다."),
        ("회원 등록", "처음 켤 때 이름과 휴대전화 번호로 한 번만 등록합니다. 등록하면 이 폰만의 회원 열쇠가 담겨 도서관과 독서기가 열립니다. 폰을 바꾸면 새 폰에서 같은 번호로 다시 등록합니다. 열쇠가 막히면 등록 화면이 다시 나옵니다."),
        ("뒤로", "속 화면 맨 위에 뒤로가 있습니다. 보이스오버에서는 두 손가락으로 문질러도 뒤로 갑니다."),
    ]
}
