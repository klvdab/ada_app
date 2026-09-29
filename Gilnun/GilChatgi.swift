// 길 찾기 탭 — 2단계 첫 묶음(목적지 찾기, 즐겨찾기, 걷는 안내, 차 안 안내, 도착)
// 화면 원칙: 여정이 있으면 맨 위 한 줄에 "지금 무슨 차례인지"와 그 차례의 단추를 한 자리에.
// 안내는 화면이 아니라 안내 엔진이 맡으므로, 화면을 떠나도, 음악을 틀어도, 폰이 잠겨도 이어집니다.
import SwiftUI

enum GilHwamyeon: Hashable {
    case gyeolgwa(String)
    case mokjeok(Jangso)
    case jeulgyeo
    case gicho
    case jiha(Jangso)
    case beoseu(Jangso)
    case jeongryujang(Jangso, Jeongryujang)
    case talgeot
    case gingeup
    case jiinGoreugi
    case jiinMyeongdan
    case jeomMok      // 2.10.0 가까운 점지도
    case munje        // 2.10.0 여기 문제 있어요
    case doe          // 2.13.0 되짚어 나가기
    case qr           // 2.13.0 QR 찾기
    case malgil       // 2.14.0 말로 그린 길(실내)
    case yudo         // 2.14.0 음성유도기와 승강기
    case munChatgi    // 2.15.0 문 찾기(카메라 눈)
}

/// 길 찾기 탭의 길(화면 쌓임) — 목적지를 정하면 첫 화면으로 곧장 돌아가게
final class GilGil: ObservableObject {
    static let shared = GilGil()
    @Published var path = NavigationPath()
    func cheotHwamyeon() { path = NavigationPath() }
}

struct GilChatgiTab: View {
    @ObservedObject private var gil = GilGil.shared
    var body: some View {
        NavigationStack(path: $gil.path) {
            GilChatgiView()
                .navigationDestination(for: GilHwamyeon.self) { h in
                    switch h {
                    case .gyeolgwa(let q): ChatgiGyeolgwaView(mal: q)
                    case .mokjeok(let j): MokjeokView(j: j)
                    case .jeulgyeo: JeulgyeoView()
                    case .gicho: GichoSiheomView()
                    case .jiha(let j): JihacheolGilView(mok: j)
                    case .beoseu(let j): BeoseuChatgiView(mok: j)
                    case .jeongryujang(let j, let jr): JeongryujangView(mok: j, j: jr)
                    case .talgeot: TalgeotView()
                    case .gingeup: GinGeupView()
                    case .jiinGoreugi: JiinGoreugiView()
                    case .jiinMyeongdan: JiinMyeongdanView()
                    case .jeomMok: GakkaunJeomView()
                    case .munje: MunjeView()
                    case .doe: DoeView()
                    case .qr: QrView()
                    case .malgil: MalgilView()
                    case .yudo: YudoView()
                    case .munChatgi: MunChatgiView()
                    }
                }
        }
    }
}

struct GilChatgiView: View {
    @ObservedObject private var y = YeojeongEngine.shared
    @ObservedObject private var doe = DoeEngine.shared
    @ObservedObject private var malgil = MalgilEngine.shared
    @ObservedObject private var jeom = JeomEngine.shared
    @State private var mal = ""
    @AccessibilityFocusState private var meoriChojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                GingeupGongjiJul()   // 2.9.0 읽지 않은 긴급 공지 — 맨 위
                // 2.12.6 하던 일 멈추기 — 무엇이든 진행 중일 때만, 접지 않고 맨 위에(이사장님 승인 1)
                if jeom.muleum != nil || jeom.gil != nil || y.jigeum != nil {
                    Button("하던 일 멈추기 — 안내를 모두 멈추고 새 목적지 찾기") {
                        AnnaeEngine.shared.haneunIlMeomchum()
                        meoriChojeom = false   // 멈춘 뒤 커서를 목적지 적는 칸으로
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { meoriChojeom = true }
                    }
                    .buttonStyle(KeunDanchu())
                }
                if malgil.geotneun {
                    // 2.14.0 말로 그린 길을 걷는 중이면 맨 위에
                    NavigationLink(value: GilHwamyeon.malgil) { Text("말로 그린 길 — 따라 걷는 중, \(malgil.jemok)") }
                        .buttonStyle(KeunDanchu())
                }
                if doe.sangtae != .swim {
                    // 2.13.0 되짚어 나가기 기억·안내 중이면 맨 위에(속 화면을 떠나도 찾기 쉽게)
                    NavigationLink(value: GilHwamyeon.doe) {
                        Text(doe.sangtae == .gieok ? "되짚어 나가기 — 길을 기억하는 중, 나가실 때 누르십시오" : "되짚어 나가기 — 나가는 길 안내 중")
                    }
                    .buttonStyle(KeunDanchu())
                }
                if let m = jeom.muleum {
                    JeomMuleumPan(m: m, chojeom: $meoriChojeom)   // 2.10.0 점지도로 걸을까요
                } else if let g = jeom.gil {
                    TtaraPan(g: g, chojeom: $meoriChojeom)        // 2.10.0 점지도 따라 걷는 중
                } else if let yj = y.jigeum {
                    YeojeongPan(yj: yj, chojeom: $meoriChojeom)
                } else {
                    TextField("어디로 가실까요 — 이름이나 주소를 넣고 엔터", text: $mal)
                        .textFieldStyle(.roundedBorder)
                        .font(.title3)
                        .submitLabel(.search)
                        .accessibilityFocused($meoriChojeom)
                        .onSubmit {
                            let q = mal.trimmingCharacters(in: .whitespaces)
                            if !q.isEmpty { GilGil.shared.path.append(GilHwamyeon.gyeolgwa(q)) }
                        }
                    NavigationLink(value: GilHwamyeon.jeulgyeo) { Text("즐겨찾기 — 자주 가는 곳") }
                        .buttonStyle(KeunDanchu())
                    Button("지금 내 자리 듣기") { AnnaeEngine.shared.jigeumJari() }
                        .buttonStyle(KeunDanchu())
                }
                MalHagiDanchu()
                NavigationLink(value: GilHwamyeon.gingeup) {
                    Text("긴급통화서비스 — 가족·지인, 자원봉사자, 해설사에게 화상통화")
                }
                .buttonStyle(KeunDanchu())
                DisclosureGroup("그 밖에 펼치기") {
                    VStack(alignment: .leading, spacing: 10) {
                        Button("음향신호기 울리기 — 신호 안내") { SinhogiEngine.shared.ulligi(2) }
                            .buttonStyle(KeunDanchu())
                        Button("음향신호기 찾기 — 가까워질수록 소리가 빨라집니다") { SinhogiEngine.shared.chatgiKyeogi() }
                            .buttonStyle(KeunDanchu())
                        NavigationLink(value: GilHwamyeon.jeomMok) { Text("가까운 점지도 — 골라서 따라 걷기") }
                            .buttonStyle(KeunDanchu())
                        NavigationLink(value: GilHwamyeon.doe) { Text("되짚어 나가기 — 들어온 길로 혼자 나오기") }
                            .buttonStyle(KeunDanchu())
                        NavigationLink(value: GilHwamyeon.qr) { Text("QR 찾기 — 카메라로 둘레의 QR 저절로 찾기") }
                            .buttonStyle(KeunDanchu())
                        NavigationLink(value: GilHwamyeon.malgil) { Text("말로 그린 길 — 몸으로 익힌 실내 길을 손대지 않고 따라 걷기") }
                            .buttonStyle(KeunDanchu())
                        NavigationLink(value: GilHwamyeon.yudo) { Text("음성유도기와 승강기 — 가까운 역의 음성유도기, 엘리베이터") }
                            .buttonStyle(KeunDanchu())
                        NavigationLink(value: GilHwamyeon.munChatgi) { Text("문 찾기 — 카메라로 앞의 문을 찾아 방향과 걸음 수로") }
                            .buttonStyle(KeunDanchu())
                        NavigationLink(value: GilHwamyeon.gicho) { Text("기초 시험") }
                            .buttonStyle(KeunDanchu())
                    }
                }
                .font(.title3)
            }
            .padding()
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            meoriChojeom = false   // 2.12.1 탭을 고를 때마다 첫 줄로
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { meoriChojeom = true }
        }
    }
}

/// 여정 머리 — 지금 차례 한 줄과 그 차례의 큰 단추를 한 자리에
struct YeojeongPan: View {
    let yj: Yeojeong
    var chojeom: AccessibilityFocusState<Bool>.Binding
    @ObservedObject private var a = AnnaeEngine.shared

    private var mok: String { yj.mokjeok.ireum }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let b = yj.beoseu, yj.danggye == .taneunGotKkaji {
                beoseuDanchu(b)
            } else if let g = yj.jiha, yj.danggye == .taneunGotKkaji || yj.danggye == .taneunJung {
                jihaDanchu(g)
            } else {
                gibonDanchu
            }
            if yj.danggye != .dochak {
                DisclosureGroup("여정 다른 할 일 펼치기") {
                    VStack(alignment: .leading, spacing: 10) {
                        if yj.danggye == .namEunGil {
                            Button("차에 탔습니다 — 차 안 안내로") { a.chaTatda() }.buttonStyle(KeunDanchu())
                        }
                        if yj.jiha != nil && yj.danggye == .taneunJung {
                            Button("밖으로 나왔습니다 — 남은 길 걸어서 안내") { a.naeryeotda() }.buttonStyle(KeunDanchu())
                        }
                        if yj.jiha == nil && yj.danggye == .taneunJung {
                            NavigationLink(value: GilHwamyeon.talgeot) {
                                Text("탈것 바로잡기 — 지금 \(YeojeongEngine.shared.talgeot.ireum)로 알고 있습니다")
                            }
                            .buttonStyle(KeunDanchu())
                        }
                        Button("지금 내 자리 듣기") { a.jigeumJari() }.buttonStyle(KeunDanchu())
                        Button("여정 끝내기 — 목적지를 바꾸실 때도") { a.kkeut() }.buttonStyle(KeunDanchu())
                    }
                }
                .font(.title3)
            }
        }
    }

    @ViewBuilder
    private func beoseuDanchu(_ b: BeoseuGil) -> some View {
        if b.dochak {
            Button("\(b.jeongryujang.ireum) 정류장에 닿았습니다 — 버스에 탔습니다, 버스 안 안내") { a.beoseuTatda() }
                .buttonStyle(KeunDanchu())
                .accessibilityFocused(chojeom)
            Button("오는 버스 듣기") {
                Task {
                    let m = await Beoseu.douchak(b.jeongryujang)
                    await MainActor.run { SoriEngine.shared.mal(m) }
                }
            }
            .buttonStyle(KeunDanchu())
        } else {
            Button("\(b.jeongryujang.ireum) 정류장까지 걸어가는 중 — 지금 어떻게 가고 있습니까") { a.hyeonhwang() }
                .buttonStyle(KeunDanchu())
                .accessibilityFocused(chojeom)
            Button("버스에 탔습니다 — 버스 안 안내") { a.beoseuTatda() }
                .buttonStyle(KeunDanchu())
        }
    }

    @ViewBuilder
    private func jihaDanchu(_ g: JihaGil) -> some View {
        if yj.danggye == .taneunGotKkaji && !g.ipguDochak {
            Button("\(g.ipgu.ireum)까지 걸어가는 중 — 지금 어떻게 가고 있습니까") { a.hyeonhwang() }
                .buttonStyle(KeunDanchu())
                .accessibilityFocused(chojeom)
            Button("열차에 탔습니다 — 지나는 역 알려 주기") { JihacheolEngine.shared.tatda(jadong: false) }
                .buttonStyle(KeunDanchu())
        } else if yj.danggye == .taneunGotKkaji {
            Button("\(g.ipgu.ireum)에 닿았습니다 — 열차에 탔습니다, 지나는 역 알려 주기") { JihacheolEngine.shared.tatda(jadong: false) }
                .buttonStyle(KeunDanchu())
                .accessibilityFocused(chojeom)
        } else if g.kkeutnam {
            Button("\(g.to)역에 닿았습니다 — 밖으로 나왔습니다, 남은 길 걸어서 안내") { a.naeryeotda() }
                .buttonStyle(KeunDanchu())
                .accessibilityFocused(chojeom)
        } else {
            Button("\(g.to)역까지 지하철로 가는 중 — 몇 정거장 남았습니까") { a.hyeonhwang() }
                .buttonStyle(KeunDanchu())
                .accessibilityFocused(chojeom)
        }
    }

    @ViewBuilder
    private var gibonDanchu: some View {
            switch yj.danggye {
            case .namEunGil:
                Button("\(mok)까지 걸어가는 중 — 지금 어떻게 가고 있습니까") { a.hyeonhwang() }
                    .buttonStyle(KeunDanchu())
                    .accessibilityFocused(chojeom)
            case .taneunJung:
                Button("\(mok)까지 \(YeojeongEngine.shared.talgeot.ireum)로 가는 중 — 내렸습니다, 남은 길 걸어서 안내") {
                    a.naeryeotda()
                }
                .buttonStyle(KeunDanchu())
                .accessibilityFocused(chojeom)
                Button("지금 어떻게 가고 있습니까") { a.hyeonhwang() }
                    .buttonStyle(KeunDanchu())
            case .dochak:
                Button("\(mok)에 도착했습니다 — 여정 끝내기") { a.kkeut() }
                    .buttonStyle(KeunDanchu())
                    .accessibilityFocused(chojeom)
            default:
                Button("\(mok)까지 — 걸어가기, 걷는 안내 시작") {
                    // 2.10.0 맞는 점지도가 있으면 한 번 여쭘
                    let m = yj.mokjeok
                    JeomEngine.shared.georeoGagiBoda(Jangso(ireum: m.ireum, juso: m.juso, lat: m.lat, lon: m.lon)) { q in
                        if let q = q { SoriEngine.shared.mal(q) }
                    }
                }
                    .buttonStyle(KeunDanchu())
                    .accessibilityFocused(chojeom)
                Button("차에 탔습니다 — 차 안 안내 시작") { a.chaTatda() }
                    .buttonStyle(KeunDanchu())
            }
    }
}

/// 찾은 곳 목록 — 결과만 남기고 초점을 첫 줄로
struct ChatgiGyeolgwaView: View {
    let mal: String
    @State private var gyeolgwa: [Jangso]?
    @State private var mothbadeum = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let g = gyeolgwa {
                    if g.isEmpty {
                        Text("\(mal) — 찾지 못했습니다. 뒤로 가셔서 달리 적어 보십시오.").font(.title3)
                    } else {
                        Mokrok5(g) { j in
                            NavigationLink(value: GilHwamyeon.mokjeok(j)) {
                                Text("\(j.ireum) — \(j.juso)\(geoMal(j))")
                            }
                            .buttonStyle(KeunDanchu())
                        }
                    }
                } else if mothbadeum {
                    Button("찾기를 받지 못했습니다. 통신이 끊겼을 수 있습니다 — 다시 찾기") { chatgi() }
                        .buttonStyle(KeunDanchu())
                } else {
                    Text("\(mal) 찾는 중입니다.").font(.title3)
                }
            }
            .padding()
        }
        .sokHwamyeon("")
        .task { if gyeolgwa == nil { chatgi() } }
    }

    private func geoMal(_ j: Jangso) -> String {
        guard let w = WichiEngine.shared.jigeum else { return "" }
        return ", " + Annae.geoMal(WichiEngine.geori(w.lat, w.lon, j.lat, j.lon))
    }

    private func chatgi() {
        mothbadeum = false
        Task {
            let r = await Chatgi.jangso(mal)
            await MainActor.run {
                if let r = r { gyeolgwa = r } else { mothbadeum = true }
            }
        }
    }
}

/// 정한 곳 — 걸어가기, 차로 가기, 즐겨찾기에 담기
struct MokjeokView: View {
    let j: Jangso
    @ObservedObject private var jeul = Jeulgyeo.shared
    @State private var damam = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Button("\(j.ireum)\(geoMal) — 걸어가기, 걷는 안내 시작") {
                    jeul.sseum(j)
                    // 2.10.0 맞는 점지도가 있으면 "점지도로 걸을까요, 위성으로 걸을까요" 한 번 여쭘(점지도를 먼저 권함)
                    JeomEngine.shared.georeoGagiBoda(j) { q in if let q = q { SoriEngine.shared.mal(q) } }
                    GilGil.shared.cheotHwamyeon()
                }
                .buttonStyle(KeunDanchu())
                Button("차로 가기 — 차에 탔거나 곧 탑니다, 차 안 안내 시작") {
                    jeul.sseum(j)
                    AnnaeEngine.shared.chaTagi(j)
                    GilGil.shared.cheotHwamyeon()
                }
                .buttonStyle(KeunDanchu())
                NavigationLink(value: GilHwamyeon.jiha(j)) {
                    Text("지하철로 가기 — 가까운 역, 갈아타기, 나갈 출구를 찾아 드립니다")
                }
                .buttonStyle(KeunDanchu())
                NavigationLink(value: GilHwamyeon.beoseu(j)) {
                    Text("버스로 가기 — 가까운 정류장과 오는 버스")
                }
                .buttonStyle(KeunDanchu())
                if damam || jeul.itna(j) {
                    Text("즐겨찾기에 담겨 있습니다.").font(.title3)
                } else {
                    Button("즐겨찾기에 담기") {
                        jeul.damgi(j)
                        damam = true
                        SoriEngine.shared.mal("즐겨찾기에 담았습니다.")
                    }
                    .buttonStyle(KeunDanchu())
                }
                Text("주소 — \(j.juso)").font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("")
    }

    private var geoMal: String {
        guard let w = WichiEngine.shared.jigeum else { return "" }
        return ", 여기서 " + Annae.geoMal(WichiEngine.geori(w.lat, w.lon, j.lat, j.lon))
    }
}

/// 즐겨찾기 — 줄에는 이름 하나, 지우기는 보이스오버 동작(위아래로 쓸기)으로
struct JeulgyeoView: View {
    @ObservedObject private var jeul = Jeulgyeo.shared
    @State private var ireum = ""
    @State private var allim = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if jeul.mokrok.isEmpty {
                    Text("아직 담은 곳이 없습니다. 목적지를 찾아 고르신 뒤 즐겨찾기에 담기를 누르시면 여기에 모입니다. 아래 펼치기에서 지금 자리를 담으실 수도 있습니다.")
                        .font(.title3)
                } else {
                    Mokrok5(jeul.mokrok) { h in
                        NavigationLink(value: GilHwamyeon.mokjeok(h.jangso)) {
                            Text("\(h.ireum) — \(h.juso)")
                        }
                        .buttonStyle(KeunDanchu())
                        .accessibilityAction(named: "즐겨찾기에서 지우기") {
                            jeul.jiugi(h)
                            SoriEngine.shared.mal("\(h.ireum), 즐겨찾기에서 지웠습니다.")
                        }
                        .contextMenu {
                            Button("즐겨찾기에서 지우기", role: .destructive) { jeul.jiugi(h) }
                        }
                    }
                }
                DisclosureGroup("지금 자리를 즐겨찾기에 담기 펼치기") {
                    VStack(alignment: .leading, spacing: 10) {
                        TextField("이름 — 예: 집, 사무실", text: $ireum)
                            .textFieldStyle(.roundedBorder)
                            .font(.title3)
                        Button("지금 자리를 이 이름으로 담기") { jigeumDamgi() }
                            .buttonStyle(KeunDanchu())
                        if !allim.isEmpty { Text(allim) }
                    }
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("즐겨찾기")
    }

    private func jigeumDamgi() {
        let nm = ireum.trimmingCharacters(in: .whitespaces)
        guard !nm.isEmpty else { allim = "이름을 먼저 적어 주십시오."; SoriEngine.shared.mal(allim); return }
        guard let w = WichiEngine.shared.jigeum else { allim = "아직 위치를 잡는 중입니다."; SoriEngine.shared.mal(allim); return }
        if w.ochae > 30 {
            allim = "지금은 위성이 흐려 자리가 \(Int(w.ochae))미터쯤 어긋날 수 있습니다. 밖으로 나가 다시 담아 주십시오."
            SoriEngine.shared.mal(allim)
            return
        }
        allim = "주소를 알아보는 중입니다."
        Task {
            let juso = await Chatgi.juso(w.lat, w.lon)
            await MainActor.run {
                guard let juso = juso else {
                    allim = "주소를 받지 못했습니다. 통신을 확인하신 뒤 다시 눌러 주십시오."
                    SoriEngine.shared.mal(allim)
                    return
                }
                if jeul.damgi(Jangso(ireum: nm, juso: juso, lat: w.lat, lon: w.lon)) {
                    allim = "\(nm) — \(juso), 즐겨찾기에 담았습니다."
                    ireum = ""
                } else {
                    allim = "같은 이름이나 같은 자리가 이미 담겨 있습니다."
                }
                SoriEngine.shared.mal(allim)
            }
        }
    }
}
