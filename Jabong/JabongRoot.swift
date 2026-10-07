// 자봉 앱 화면 — 처음 등록, 탭 넷(봉사, 나눔, 내 기록, 알림·설정)
// 길눈 화면 원칙 그대로: 탭 바는 모든 속 화면에 늘, 뒤로 단추는 위에 하나, 목록은 다섯 개씩, 결과가 나오면 커서를 첫 줄로
import SwiftUI

struct JabongRoot: View {
    @ObservedObject private var nae = JabongNae.shared
    var body: some View {
        if nae.deungrokham && !nae.hwanyeong && !nae.gyoyukDoem {
            NavigationStack { DeungrokView(dasi: true) }   // 2.10.0 새 교육(일곱 가지 약속)을 아직 안 들은 봉사자
        } else if nae.deungrokham && !nae.hwanyeong {
            JabongTab()
        } else if nae.deungrokham {
            NavigationStack { HwanyeongView() }   // 2.6.0 등록을 막 마친 분께 환영 화면부터
        } else {
            NavigationStack { DeungrokView() }
        }
    }
}

// MARK: 탭 넷

struct JabongTab: View {
    @ObservedObject private var t = JabongTonghwa.shared
    @ObservedObject private var tg = JbTabGil.shared   // 2.7.0 그려 주세요에서 봉사 탭으로 보낼 수 있게
    var body: some View {
        TabView(selection: $tg.tab) {
            NavigationStack { BongsaTab() }
                .tabItem { Label("봉사", systemImage: "figure.walk") }.tag(0)
            JbNanumTab()   // 2.7.0 나눔 탭은 제 길(NavigationStack)을 가짐
                .tabItem { Label("나눔", systemImage: "bubble.left.and.bubble.right") }.tag(1)
            NavigationStack { NaeGirokTab() }
                .tabItem { Label("내 기록", systemImage: "list.bullet.rectangle") }.tag(2)
            NavigationStack { AllimTab() }
                .tabItem { Label("알림·설정", systemImage: "gearshape") }.tag(3)
        }
        .tint(Saek.nam)
        .fullScreenCover(isPresented: $t.boim) { TonghwaView() }   // 2.1.0 긴급통화 통화 화면
        .task { await JabongNae.shared.gyoyukBonaegi(); await JbBowan.shared.bulleo(gangje: true) }   // 2.13.0 켤 때 보완 부탁도 살핌   // 2.10.0 못 보낸 교육 기록을 다시
    }
}

/// 탭 첫 화면의 공통 틀 — 첫 줄로 커서
struct TabCheot<Naeyong: View>: View {
    let jemok: String
    @ViewBuilder let naeyong: () -> Naeyong
    @AccessibilityFocusState private var cheot: Bool
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(jemok).font(.title2.bold()).accessibilityAddTraits(.isHeader).accessibilityFocused($cheot)
                naeyong()
            }
            .padding()
        }
        .navigationTitle(jemok)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { cheot = true } }
    }
}

struct BongsaTab: View {
    @ObservedObject private var g = JabongDaegi.shared
    @ObservedObject private var nae = JabongNae.shared
    @ObservedObject private var s = Seoljeong.shared
    @ObservedObject private var gr = JeomGeurigi.shared   // 2.2.0
    @ObservedObject private var bw = JbBowan.shared        // 2.13.0 보완 부탁
    var body: some View {
        TabCheot(jemok: "봉사") {
            HyeophoeMeori(ireum: "길눈 자봉 — 점지도 그리기")   // 2.11.0 협회 로고(이사장님 지시)
            OllimJul()   // 2.12.0 새 판이 있을 때만 — 두드리면 테스트플라이트에서 업데이트
            NavigationLink { JbHamkkeView() } label: {
                Text(g.geobu ? "긴급통화 받기 — 받지 않기로 하심" : (g.kyeojim ? "긴급통화 받기 — 받고 있음" : "긴급통화 받기 — 길눈님이 도움을 청하면 전화처럼 울립니다"))
            }.buttonStyle(KeunDanchu())
            // 2.2.0 점지도 그리기(속까지 앱)
            NavigationLink { GeurigiView() } label: {
                Text(gr.sangtae == .georeum ? "점지도 그리기 — 그리는 중" : gr.sangtae == .meomchum ? "점지도 그리기 — 잠깐 멈춤, 이어 그리기" : "점지도 그리기")
            }.buttonStyle(KeunDanchu())
            Button("지금 상태 듣기") { jigeumSangtae() }.buttonStyle(KeunDanchu())
            NavigationLink { BopokView() } label: {
                Text(s.bopok > 0.2 ? "내 보폭 다시 재기 — 지금 \(Int((s.bopok * 100).rounded()))센티미터" : "내 보폭 재기 — 점지도를 그리기 전에 한 번")
            }.buttonStyle(KeunDanchu())
            if !bw.namun.isEmpty {   // 2.13.0 보완 부탁이 있으면 한 줄 — 두드리면 알림·설정 탭으로
                Button("보완 부탁 \(bw.namun.count)건 — \(JbBowan.butak)") { JbTabGil.shared.tab = 3 }.buttonStyle(KeunDanchu())
            }
        }
    }

    private func jigeumSangtae() {
        var m = "자봉 번호 \(nae.beonho), \(nae.ireum)님."
        m += g.kyeojim ? " 긴급통화를 받고 있습니다." : " 긴급통화는 받지 않는 중입니다."
        if gr.sangtae != .swim { m += " " + gr.sangtaeMal() }   // 2.2.0
        m += s.bopok > 0.2 ? " 보폭은 \(Int((s.bopok * 100).rounded()))센티미터입니다." : " 아직 보폭을 재지 않으셨습니다. 점지도를 그리기 전에 한 번 재 주십시오."
        if let w = WichiEngine.shared.jigeum {
            m += w.ochae <= 15 ? " 위성이 잘 잡혀 있습니다." : " 위성이 아직 흐립니다. 하늘이 트인 곳에서 잠시 기다려 주십시오."
        } else {
            m += " 아직 위치를 받지 못했습니다."
        }
        SoriEngine.shared.mal(m)
    }
}

// MARK: 나눔 탭 (2.7.0, 261006-I2, 이사장님 승인) — 그려 주세요, 걸음 나눔, 나눔 마당. 길눈과 같은 나스 창고를 씀
struct JbNanumTab: View {
    @ObservedObject private var gil = NanumGil.shared
    var body: some View {
        NavigationStack(path: $gil.path) {
            JbNanumCheot()
                .navigationDestination(for: NanumHwamyeon.self) { h in
                    switch h {
                    case .mulnanum: MulnanumView()
                    case .mulMok(let j): MulMokView(jong: j)
                    case .mulSseugi: MulSseugiView()
                    case .georeum: GeoreumNanumView()
                    case .nanumGeul: JbGeoreumNanumView()   // 2.8.0 글마다 응원 박수
                    case .nanumSangse(let id): NanumSangseView(id: id)
                    case .hamkke: HamkkeView()
                    case .butak: JbGeuryeojuseyoView()
                    case .butakSseugi: ButakSseugiView()
                    case .butakMok: JbGeuryeojuseyoView()
                    case .butakSangse(let id): JbButakSangseView(id: id)
                    }
                }
        }
    }
}

struct JbNanumCheot: View {
    @AccessibilityFocusState private var chojeom: Bool
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                NavigationLink(value: NanumHwamyeon.butakMok) { Text("그려 주세요 — 길눈님이 부탁한 길") }
                    .buttonStyle(KeunDanchu())
                    .accessibilityFocused($chojeom)
                NavigationLink(value: NanumHwamyeon.nanumGeul) { Text("걸음 나눔 — 봉사 이야기와 응원 박수") }.buttonStyle(KeunDanchu())
                NavigationLink { JbGirokpanView() } label: { Text("함께한 기록판 — 이번 주 우리가 그린 길") }.buttonStyle(KeunDanchu())   // 2.8.0
                DisclosureGroup("더 보기 펼치기") {
                    VStack(alignment: .leading, spacing: 12) {
                        NavigationLink(value: NanumHwamyeon.mulnanum) { Text("나눔 마당 — 쓰지 않는 물건 주고받기") }.buttonStyle(KeunDanchu())
                        NavigationLink(value: NanumHwamyeon.hamkke) { Text("함께하기 — 자원봉사 요령과 제도") }.buttonStyle(KeunDanchu())
                    }
                }
                .font(.title3)
            }
            .padding()
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear { chojeom = false; DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { chojeom = true } }
    }
}

/// 그려 주세요 — 길눈님이 부탁한 길. 아직 안 그려진 부탁이 먼저, 다섯 개씩
struct JbGeuryeojuseyoView: View {
    @State private var mok: [Butak]?
    @State private var mot = false
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if let l = mok, !l.isEmpty {
                    Mokrok5(l) { b in
                        NavigationLink(value: NanumHwamyeon.butakSangse(b.id)) { Text((b.doen ? "그려짐 · " : "") + b.julMal) }
                            .buttonStyle(KeunDanchu())
                    }
                } else if mok != nil {
                    Text("지금은 부탁된 길이 없습니다. 길눈님이 부탁하시면 이곳에 바로 나타납니다.").font(.title2)
                } else if mot {
                    Button("불러오지 못했습니다 — 다시 불러오기") { bulreogi() }.buttonStyle(KeunDanchu())
                } else {
                    Text("부탁된 길을 불러오고 있습니다.").font(.title2)
                }
            }
            .padding()
        }
        .sokHwamyeon("그려 주세요")
        .onAppear { bulreogi() }
    }

    private func bulreogi() {
        mot = false
        Task {
            let r = await GilButak.mok()
            await MainActor.run {
                if let r = r {
                    for b in r { NanumGil.shared.butak[b.id] = b }
                    mok = r.filter { !$0.doen } + r.filter { $0.doen }
                } else {
                    mot = true
                    SoriEngine.shared.mal("불러오지 못했습니다.")
                }
            }
        }
    }
}

/// 그려 주세요 한 건 — 이 길 그리러 가기, 응원 한마디, 다 그렸습니다(박수)
struct JbButakSangseView: View {
    let id: String
    @ObservedObject private var nae = JabongNae.shared
    @Environment(\.dismiss) private var dismiss
    @State private var datMal = ""
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if let b = NanumGil.shared.butak[id] {
                    Text(b.jaseMal).font(.title2).fixedSize(horizontal: false, vertical: true).accessibilityFocused($chojeom)
                    if !b.doen {
                        Button("이 길 그리러 가기 — 봉사 탭으로") {
                            SoriEngine.shared.mal("고맙습니다. 봉사 탭의 점지도 그리기에서 \(b.sin)부터 \(b.min)까지 걸어 주십시오. 다 그리신 뒤 이 부탁으로 돌아와 다 그렸습니다를 눌러 주십시오.")
                            JbTabGil.shared.tab = 0
                        }
                        .buttonStyle(KeunDanchu())
                    }
                    ForEach(b.daetgeul, id: \.id) { d in
                        Text("한마디 : \(d.mal)" + (d.nugu.isEmpty ? "" : " — \(d.nugu)")).font(.title3).fixedSize(horizontal: false, vertical: true)
                    }
                    DisclosureGroup("응원 한마디 남기기 펼치기") {
                        VStack(alignment: .leading, spacing: 10) {
                            TextField("한마디", text: $datMal)
                                .font(.title2).padding(12)
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Saek.nam, lineWidth: 2))
                            Button("한마디 올리기") {
                                let m = datMal.trimmingCharacters(in: .whitespaces)
                                guard !m.isEmpty else { SoriEngine.shared.mal("한마디를 적어 주십시오."); return }
                                hagi("daet", [("mal", m), ("nugu", "자봉 \(nae.beonho)")], "한마디를 남겼습니다.", baksu: false)
                            }
                            .buttonStyle(KeunDanchu())
                        }
                    }
                    .font(.title3)
                    if !b.doen {
                        Button("다 그렸습니다 표시하기") { hagi("doen", [], "다 그렸습니다. 길눈님께 큰 힘이 됩니다. 고맙습니다!", baksu: true) }
                            .buttonStyle(KeunDanchu())
                    }
                } else {
                    Text("부탁을 찾지 못했습니다.").font(.title2)
                }
            }
            .padding()
        }
        .sokHwamyeon("그려 주세요")
        .onAppear { chojeom = false; DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { chojeom = true } }
    }

    private func hagi(_ a: String, _ deo: [(String, String)], _ mal: String, baksu: Bool) {
        Task {
            let ok = await GilButak.hagi(a, id, deo)
            await MainActor.run {
                if ok && baksu { Baksu.chigi(keuge: true) }
                SoriEngine.shared.mal(ok ? mal : "하지 못했습니다. 통신을 확인해 주십시오.")
                if ok { dismiss() }
            }
        }
    }
}

struct NaeGirokTab: View {
    @ObservedObject private var nae = JabongNae.shared
    var body: some View {
        TabCheot(jemok: "내 기록") {
            Text("자봉 번호 \(nae.beonho)").font(.title3)
            Text("이름 \(nae.ireum)").font(.body)
            if !nae.jiyeok.isEmpty { Text("활동 지역 \(nae.jiyeok)").font(.body) }
            Text(nae.id1365.isEmpty ? "1365 아이디는 아직 넣지 않으셨습니다." : "1365 아이디 \(nae.id1365)").font(.body)
            Text("그려 주신 길 \(nae.geurinSu)개").font(.body)
            Text("내 발자취와 교육 마당이 이 탭에 들어섭니다.").font(.body)
        }
    }
}

struct AllimTab: View {
    @ObservedObject private var s = Seoljeong.shared
    @ObservedObject private var nae = JabongNae.shared
    @State private var ijeugiMureum = false
    var body: some View {
        TabCheot(jemok: "알림·설정") {
            BowanKan()   // 2.13.0 보완 부탁 — 있을 때만 맨 위에
            Toggle(isOn: $s.malKyeojim) { Text("말소리").font(.title3.weight(.semibold)) }
                .padding(.horizontal, 4).frame(minHeight: 60)
            Button("빠르기 — 지금 \(["아주 느리게", "느리게", "보통", "빠르게", "아주 빠르게"][max(0, min(4, s.bbareugiDan))])") {
                s.bbareugiDan = (s.bbareugiDan + 1) % 5
                SoriEngine.shared.mal("이 빠르기로 말씀드립니다.")
            }.buttonStyle(KeunDanchu())
            Button("새로고침 — 등록 정보를 나스에서 다시 받기") {
                Task {
                    await nae.dasiBatgi()
                    await MainActor.run { SoriEngine.shared.mal("새로 받았습니다. 그려 주신 길은 \(nae.geurinSu)개입니다.") }
                }
            }.buttonStyle(KeunDanchu())
            OllimSeoljeongDanchu()   // 2.12.0 업데이트 — 새로고침 바로 아래 한 곳
            NavigationLink { YaksokBogiView() } label: { Text("점지도 일곱 가지 약속 다시 보기") }.buttonStyle(KeunDanchu())
            NavigationLink { JabongDoumalView() } label: { Text("도움말") }.buttonStyle(KeunDanchu())
            DisclosureGroup("더 보기 펼치기") {
                VStack(alignment: .leading, spacing: 12) {
                    NavigationLink { JabongPanView() } label: { Text("판 기록 — 자봉 앱 \(JabongPan.pan)") }.buttonStyle(KeunDanchu())
                    Button("이 폰에서 등록 지우기 — 나스 기록은 그대로") { ijeugiMureum = true }.buttonStyle(KeunDanchu())
                }
            }.font(.title3)
        }
        .confirmationDialog("이 폰에서 등록을 지울까요? 자봉 번호와 네 자리 숫자로 언제든 다시 이어 쓰실 수 있습니다.", isPresented: $ijeugiMureum, titleVisibility: .visible) {
            Button("지우기", role: .destructive) { nae.ijeugi() }
            Button("그만두기", role: .cancel) {}
        }
    }
}

struct JabongPanView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("자봉 앱 \(JabongPan.pan) (빌드 \(JabongPan.bild), 앱 짓기 번호 \(JabongPan.appBild))").font(.title3)
                ForEach(JabongPan.girok) { g in
                    VStack(alignment: .leading, spacing: 6) {
                        Text("\(g.pan)판, \(g.nal)").font(.title3.bold())
                        ForEach(g.naeyong, id: \.self) { Text($0).font(.body) }
                    }
                }
            }.padding()
        }
        .sokHwamyeon("판 기록")
    }
}

struct JabongDoumalView: View {
    static let hangmok: [(String, String)] = [
        ("처음 등록", "자봉 앱을 처음 여시면 한 번만 등록합니다. 이름, 연락처, 주로 활동하실 지역, 네 자리 숫자를 적고, 1365 아이디는 비워 두었다가 나중에 넣으셔도 됩니다. 다음을 누르시면 점지도 일곱 가지 약속을 길눈 목소리로 차례로 읽어 드립니다. 약속 한 장을 누르시면 그 약속만 다시 들으실 수 있습니다. 이어서 확인 문제 아홉 개를 문제와 고를 말 넷(가, 나, 다, 라)까지 읽어 드리며, 맞히면 박수 소리와 진동으로, 틀리면 풀이를 들려 드리고 다시 고르시게 합니다. 아홉 문제를 모두 맞혀야 통과합니다. 칸이 비었거나 맞지 않으면 무엇이 모자란지 말로 알려 드립니다. 통과하시면 환영 화면에서 자봉 번호를 알려 드리고, 봉사 시작하기를 누르시면 봉사 탭으로 갑니다. 프로그램 말소리를 꺼 두셨으면 소리 대신 보이스오버 커서로 알려 드립니다. 웹 자봉에서 이미 등록하셨으면 자봉 번호와 네 자리 숫자로 이어서 쓰십시오."),
        ("보완 부탁", "올린 길이 협회 점검에서 고칠 곳이 나오면 「조금만 더 보완해 주세요. 시각장애인이 기다립니다」로 보완을 부탁드립니다. 봉사 탭에 보완 부탁 몇 건이 한 줄로 뜨고, 알림·설정 탭 맨 위에 길마다 단계와 기한이 나옵니다. 길을 누르시면 고칠 곳을 몇 걸음째인지와 빛깔(빨강은 따라 걸을 수 없게 하는 것, 주황은 방향 빠짐, 노랑은 목소리 토막 손볼 것)로 보여 드리고, 빨강은 깜박입니다. 고칠 곳 말로 듣기를 누르시면 차례로 읽어 드립니다. 3일째에는 앱을 여실 때 한 번 다시 알려 드리고, 7일이 지나도 보완이 없으면 협회 보완팀이 맡습니다. 그린 분의 이름은 그대로 남습니다. 같은 출발지와 도착지로 다시 걸어 점검을 통과하면 저절로 보완 완료가 됩니다."),
        ("표시마다 짧게 말 남기기", "점지도 일곱 가지 약속의 일곱째입니다. 표시를 남기면 안내 말 뒤에 딩동 소리가 나고 폰이 짧게 귀를 엽니다. 그 자리 모습을 한두 마디로 말씀해 주십시오. 말이 멈추면 저절로 끊기고, 길어도 10초에서 끊기며, 4초 안에 말이 없으면 남기지 않습니다. 이 토막은 녹음한 시간이 아니라 그 표시의 걸음 자리에 묶여, 시각장애인이 그 자리에 닿기 몇 걸음 앞에서 들려 드리게 됩니다. 문은 두 번째로 찍었을 때만 귀를 엽니다. 다른 표시를 누르거나 잠깐 멈춤, 다 걸었습니다를 누르면 바로 닫힙니다. 다른 표시와 도구 펼치기 안에서 끄고 켤 수 있습니다. 말로 표시로 찍은 것은 폰이 이대로 남길까요 하고 되물어, 네라고 하셔야 남습니다."),
        ("점지도 일곱 가지 약속", "시작과 끝은 문 앞에서, 걸음을 끊지 않기, 폰은 가슴 앞에 걷는 쪽으로, 꺾이는 그 자리에서 바로 표시, 짝 표시는 시작과 끝을 함께, 보폭은 걷기 전에, 표시마다 짧게 말로 남기기입니다. 서버의 점지도 점검과 같은 잣대라, 약속대로 걸으시면 점검을 통과합니다. 이미 등록하신 분도 교육이 새로워지면 앱을 열 때 약속을 한 번 다시 듣고 확인 문제를 풀어야 점지도 그리기를 쓰실 수 있습니다. 교육을 마친 날짜는 협회 등록 창고에 남습니다. 알림·설정 탭의 점지도 일곱 가지 약속 다시 보기에서 언제든 다시 보고 들으실 수 있습니다."),

        ("나눔 탭 — 그려 주세요", "나눔 탭 맨 위에 있습니다. 길눈님이 그려 주었으면 하고 부탁한 길이 다섯 개씩 나오며, 아직 안 그려진 부탁이 먼저 나옵니다. 줄에 엔터를 치시면 출발지와 도착지, 남긴 말이 나오고, 이 길 그리러 가기를 누르시면 봉사 탭으로 옮겨 가 어디부터 어디까지 걸으면 되는지 말씀드립니다. 다 그리신 뒤 그 부탁으로 돌아와 다 그렸습니다 표시하기를 누르시면 큰 박수와 함께 길눈님께 알려집니다. 응원 한마디 남기기로 짧은 말을 남기실 수 있고, 이름 대신 자봉 번호로 적힙니다."),
        ("함께한 기록판", "나눔 탭 셋째 줄에 있습니다. 모두 그린 길 수, 이번 주 함께 그린 길과 거리, 이번 주 가장 많이 그려 주신 분(자봉 번호, 1등부터 3등), 모두 보낸 응원 박수를 크게 보여 드리고 소리로도 읽어 드립니다. 다 걸었습니다를 누르시면 그 길이 기록판에 저절로 셈해집니다. 이번 주 모든 자봉님께 응원 박수 보내기는 한 주에 한 번 보낼 수 있습니다. 기록판은 협회 리눅스 서버가 맡으며, 서버가 잠시 쉬면 쉬고 있다고 알려 드립니다."),
        ("나눔 탭 — 걸음 나눔과 나눔 마당", "걸음 나눔은 시각장애인과 자원봉사자가 함께 쓰는 이야기 마당입니다. 다섯 개씩 나오고, 글마다 응원 박수 단추가 있어 한 글에 한 번 박수를 보낼 수 있습니다. 한마디 적기를 펼쳐 봉사 이야기나 응원 한마디를 올리시면 박수로 고마움을 전하며, 이름 대신 자봉 번호로 적힙니다. 더 보기 펼치기 안에 나눔 마당(쓰지 않는 물건 주고받기)과 함께하기(자원봉사 요령과 제도)가 있습니다."),
        ("탭 넷", "화면 아래에 봉사, 나눔, 내 기록, 알림·설정 탭이 있고, 속 화면에서도 늘 보입니다. 속 화면의 뒤로 단추는 위에 하나 있고, 두 손가락으로 문질러도 뒤로 갑니다."),
        ("긴급통화 받기", "봉사 탭 맨 위에 있습니다. 자원봉사자나 현장영상해설사 가운데 받으실 역할을 고르고, 별명과 수료 번호(해설사는 협회에 등록한 전화번호)를 적은 뒤 함께하겠습니다를 한 번 누르시면 됩니다. 이때 카메라와 마이크 허락도 한 번에 받아 둡니다. 그 뒤로는 길손님이 도움을 청하면 폰이 잠겨 있어도 일반 전화처럼 울리고, 받으시면 곧바로 길손님 카메라 화면과 말소리가 이어집니다. 다른 길눈님이 먼저 받으시면 벨이 멈추고 다른 분께 연결되었다고 알려 드립니다. 실명과 전화번호는 화면에 나오지 않고 별명만 씁니다. 잠시 쉬기를 누르시면 울리지 않습니다. 긴급통화는 이 자봉 앱으로만 받습니다. 자원봉사자와 현장영상해설사는 누구를 고를 수 없게 되어 있고, 받을 수 있는 분 가운데 먼저 받는 분이 연결됩니다. 기회가 고르게 가도록 처음 15초는 최근에 덜 받으신 다섯 분께 먼저 울리고, 그래도 아무도 안 받으면 모든 분께 울립니다. 통화료는 들지 않고 데이터만 씁니다(와이파이에서는 따로 드는 돈이 없음). 곧바로 잇지 못할 때 거치는 영상 다리 주소는 나스에서 받아 쓰므로, 다리를 옮겨도 앱을 새로 받으실 필요가 없습니다."),
        ("가족·지인으로 받기 — 이음 번호", "길눈을 쓰시는 가족이나 지인이 나를 콕 집어 화상통화를 요청하실 수 있게 등록합니다. 먼저 길눈님이 길눈 설정 탭의 가족·지인 명단에서 이음 번호 받기를 누르면 여섯 자리 숫자가 나옵니다. 이 번호를 전화로 불러 받으십시오. 번호는 30분 동안만 쓰입니다. 자봉 앱 봉사 탭, 긴급통화 받기에서 가족·지인으로 받기를 고르고, 이음 번호와 길눈님이 부르실 내 이름(보기: 큰딸)을 넣고 가족·지인으로 등록하기를 누르시면 끝입니다. 그 뒤로 그 길눈님이 나를 고르시면 이 폰만 일반 전화처럼 울리고 화면에 그분 이름이 뜹니다. 자원봉사자로도 함께하시는 분은 두 가지가 다 됩니다."),
        ("긴급통화 받지 않기", "점지도만 그려 주시고 통화는 원치 않으시면, 긴급통화 받기 화면의 긴급통화 받지 않기(처음이면 긴급통화는 받지 않겠습니다)를 누르십시오. 어떤 요청도 울리지 않고, 폰 알림 주소도 나스에서 지웁니다. 점지도 그리기는 그대로 쓰십니다. 마음이 바뀌시면 같은 화면의 긴급통화 받기 시작을 누르시면 됩니다."),
        ("지금 상태 듣기", "봉사 탭에서 누르시면 자봉 번호, 긴급통화를 받는지, 보폭, 위성이 잘 잡혔는지를 말씀드립니다."),
        ("내 보폭 재기", "봉사 탭에서 엽니다. 정해진 거리를 걸으면 보폭을 셈해 폰이 기억합니다. 한 번 재면 다시 재지 않아도 되고, 원하실 때 다시 잴 수 있습니다."),
        ("새로고침", "알림·설정 탭에서 누르시면 등록 정보와 그려 주신 길 수를 나스에서 다시 받습니다."),
        ("업데이트 — 새 판 받기", "자봉 앱은 켤 때와 앱으로 돌아올 때 협회 서버에 새 판이 나왔는지 스스로 물어봅니다. 새 판이 있으면 봉사 탭 맨 위에 자봉 새 판이 나왔습니다, 두드리면 테스트플라이트에서 업데이트합니다라는 단추가 뜹니다. 두드리시면 테스트플라이트의 자봉 자리가 열리니 자봉 옆의 업데이트를 두 번 두드리십시오. 아이폰은 앱이 스스로 자기를 설치할 수 없어 이렇게 테스트플라이트를 거칩니다. 새 판이 나오면 테스트플라이트도 알림을 보내 드리고, 테스트플라이트의 자봉 화면에서 자동 업데이트를 켜 두시면 저절로 깔립니다. 알림·설정 탭의 새로고침 바로 아래 업데이트 단추로 언제든 새 판이 있는지 살피실 수 있습니다. 그리던 길과 그린 길, 등록 정보, 보폭은 그대로 남습니다. 점지도를 그리는 중이면 다 그리고 올리신 뒤에 업데이트하시기를 권합니다."),
        ("저절로 저장", "현장에서는 늘 의외의 일이 생기므로 1분마다 저절로 저장합니다. 점지도를 그리는 중에는 1분마다, 표시를 남길 때마다, 앱이 뒤로 갈 때 그리던 길을 저장하고, 앱이 꺼졌다 켜지면 그리던 길을 잠깐 멈춤으로 되살려 이어 그리실 수 있습니다."),
        ("몸 센서 — 걸음과 방향을 더 정확하게", "점지도를 그리시는 동안 폰의 가속도계, 자이로, 나침반을 1초에 50번 읽습니다. 발이 땅에 닿을 때마다 한 걸음을 바로 세고, 몸이 몇 도 돌았는지 자이로로 재어 쇠붙이나 건물 옆에서도 방향이 틀어지지 않게 합니다. 걸음마다 시각, 방향, 돈 각도, 위아래 충격, 높이, 멈춤과 걷기와 탈것 상태를 한 줄씩 남깁니다. 꺾이셨습니까 물음도 이 각도로 가려 다 도신 뒤에 여쭙니다. 다 걸었습니다를 누르시면 아이폰 만보기로 센 걸음과 몸 센서로 센 걸음을 견주어, 차이가 크면 알려 드립니다. 폰을 손에 드셔도 주머니에 넣으셔도 됩니다. 기록 모양은 안드로이드 자봉 앱과 똑같아 어느 폰으로 그린 점지도든 함께 쓰입니다."),
        ("자봉 앱과 하이 길눈", "자봉 앱은 길을 그리는 앱이라 하이 길눈이라고 불러 깨우는 듣기를 켜지 않습니다. 같은 폰에 길눈 앱이 있을 때 두 앱이 마이크를 다투지 않게 하려는 것입니다. 하이 길눈은 길눈 앱에서만 쓰십시오."),
        ("볼거리 표시", "점지도 그리기 화면에 볼거리 표시 펼치기가 있습니다. 팽나무, 동상, 안내판, 분수처럼 길눈님이 찾아가실 만한 것 바로 앞에 서서, 이름과 만져지는 것과 다가가는 법(예를 들어 오른손을 뻗으면 줄기가 닿습니다, 둘레에 낮은 나무 울타리가 있습니다)을 적고 볼거리 남기기를 누르시면 지금 자리와 함께 협회 서버에 남습니다. 걷기 전에도, 그리는 중에도 남길 수 있습니다. 길눈님이 그곳을 목적지로 걸어오시면 마지막 스무 걸음을 이 자리로 이끌고, 닿으면 남겨 주신 말을 들려 드립니다. 남기면 박수로 고마움을 전합니다."),
        ("점지도 그리기", "봉사 탭에서 엽니다. 보폭을 먼저 재 두셔야 시작할 수 있습니다. 걷기 시작을 누르시면 출발한 자리 주소를 저절로 적고, 걸음 수와 방향, 위성 자리, 높이를 1초마다 폰 안에 기록합니다. 화면이 잠기거나 다른 앱을 쓰셔도 이어 갑니다. 길을 접어드시면 무슨 길에 접어드셨는지 알려 드립니다. 다 걸었습니다를 누르시면 걸음과 거리, 표시 수를 말씀드리고 도착한 자리 주소를 적어 그린 길로 폰에 담아 둡니다. 안내 바로 아래 방금 그린 길 올리기를 누르시면 협회로 올라갑니다. 그린 길은 같은 화면의 그린 길 펼치기에서 다섯 개씩 보시고, 줄을 누르시면 그 길의 올리기, 이름 고치기, 지우기 화면이 열립니다."),
        ("방향은 시계 방향으로", "점지도의 모든 방향은 시계 방향으로 남깁니다. 걷는 쪽이 12시, 오른손 쪽이 3시, 뒤가 6시, 왼손 쪽이 9시입니다. 꺾임 표시는 3시 방향으로 꺾임, 9시 방향으로 꺾임으로 남고, 폰이 먼저 여쭐 때는 실제로 도신 만큼 2시 방향으로 꺾이셨습니까처럼 여쭙니다. 말로 표시에서는 3시, 3시 방향이라고 말씀하셔도 되고, 오른쪽이라고 하셔도 3시 방향으로 꺾임으로 남깁니다. 표시마다 짧게 말을 남기실 때도 3시 방향에 화단 턱이 있습니다처럼 시계 방향으로 말씀해 주십시오."),
        ("그린 길 올리기", "다 걸으신 뒤 안내 바로 아래의 방금 그린 길 올리기를 누르시거나, 그린 길 펼치기에서 길을 골라 이 길 올리기를 누르십시오. 협회 서버가 점지도 일곱 가지 약속의 잣대로 점검해, 통과한 길만 점지도 창고에 넣어 길눈님이 쓰시게 합니다. 고칠 곳이 있으면 40걸음째에서 길이 크게 꺾였는데 꺾임 표시가 없습니다처럼 걸음 자리로 알려 드리고 올리지 않습니다. 출발지나 도착지 이름이 빠진 것은 그 화면에서 바로 넣고 다시 올리시면 되고, 걸음이나 표시가 빠진 구간은 다시 걸어 새로 그려 주십시오. 함께한 기록판에는 올린 길만 셈합니다. 그린 길 줄에서 올림, 올리기 전, 고칠 곳 있음으로 들립니다."),
        ("그린 길 지우기", "잘못 그린 길은 그린 길 펼치기에서 길을 골라 이 길 지우기를 누르십시오. 이 길을 지울까요 하고 한 번 여쭙고, 네 — 이 길 지우기를 누르시면 폰 안의 길과 그 길의 목소리 토막이 지워집니다. 이미 협회에 올린 길은 협회 창고에 그대로 남습니다."),
        ("출발지와 도착지 이름", "출발지와 도착지는 위성이 잡은 주소가 저절로 들어갑니다. 직접 넣고 싶으시면 걷기 시작 아래의 출발지 이름 직접 넣기 펼치기에 적고 걷기 시작을 누르십시오. 그리는 중에는 다른 표시와 도구 펼치기 안에서, 다 그린 뒤에는 그린 길 화면의 출발지·도착지 이름 고치기에서 적으실 수 있습니다. 직접 넣으신 이름이 있으면 위성 주소로 덮어쓰지 않습니다. GS25 마로니에점 문 앞처럼 문 앞 자리까지 적어 주시면 길눈님이 찾아가시기 좋습니다."),
        ("안내 말소리 끄기", "그리는 중 다른 표시와 도구 펼치기 안의 안내 말소리를 끄시면 앱이 하는 안내 말소리가 멈추고, 같은 안내가 화면 글자와 진동으로만 나옵니다. 보이스오버를 쓰시면 커서가 그 안내 줄로 갑니다. 지금 상태 듣기도 화면 글자로 보여 드립니다. 설정의 말소리와 같은 스위치입니다."),
        ("표시 남기기", "그리는 중 화면 겉에 자주 쓰는 여덟 가지(9시 방향으로 꺾임, 3시 방향으로 꺾임, 올라가는 계단 시작, 내려가는 계단 시작, 계단 끝, 횡단보도 건너기 시작과 끝, 문)가 크게 있고, 다른 표시와 도구 펼치기 안에 나머지 열네 가지와 잠깐 멈춤, 계단 칸수 고치기가 있습니다. 계단과 횡단보도는 시작을 찍으면 끝도 꼭 찍으셔야 하며, 그 사이 칸수와 걸음을 셈해 알려 드립니다. 에스컬레이터, 지하철, 버스는 탈 때와 내릴 때를 찍으시면 그 사이는 걸음으로 재지 않습니다. 문은 딱 찍고 두 걸음 앞에서 한 번 더 찍으셔야 확실한 문이 됩니다."),
        ("말로 표시", "손이 바쁘실 때 말로 표시 단추를 누르고 계단 시작, 3시 방향, 횡단보도 끝, 문처럼 말씀하시면 단추를 누른 그 자리에 표시를 남깁니다. 좌회전, 우회전, 건널목, 승강기 같은 말도 알아듣습니다. 단추에 없는 것은 벤치, 공사 가림막처럼 말씀하신 그대로 표시로 남깁니다. 받아쓰기가 틀릴 수 있으니 벤치, 이대로 남길까요 하고 되물어, 네라고 하셔야 남습니다."),
        ("폰이 먼저 여쭘", "봉사자분들의 말씀에 따라 계단은 폰이 먼저 여쭙지 않습니다. 계단 시작과 계단 끝은 단추나 말로 표시로 남겨 주십시오. 꺾임 여쭙기는 처음에는 꺼져 있고, 그리는 중 다른 표시와 도구 펼치기 안의 꺾임 여쭙기를 켜시면 방향이 크게 바뀔 때 2시 방향으로 꺾이셨습니까처럼 실제로 도신 만큼 시계 방향으로 여쭙니다. 네라고 말씀하시거나 화면 맨 위에 나오는 네 단추를 누르셔야 표시가 되며, 바뀐 것을 알아챈 그 자리에 남깁니다. 아니오면 남기지 않습니다. 20초 동안 답이 없으면 물음을 거둡니다.")
    ]
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                ForEach(JabongDoumalView.hangmok, id: \.0) { h in
                    DisclosureGroup(h.0) { Text(h.1).font(.body) }.font(.title3)
                }
            }.padding()
        }
        .sokHwamyeon("도움말")
    }
}

// MARK: 처음 등록
// 2.6.0 (261006-I1, 이사장님 승인 2026-10-06) — 짧은 교육을 소리로 읽어 드림, 화면을 크고 선명하게, 봉사의 보람과 재미를 느끼게
// 프로그램 말소리가 켜져 있으면 길눈 목소리로 알리고(보이스오버 커서는 옮기지 않아 겹치지 않게), 꺼져 있으면 지금처럼 보이스오버 커서를 옮겨 글로 알림

struct DeungrokView: View {
    @ObservedObject private var nae = JabongNae.shared
    @ObservedObject private var seol = Seoljeong.shared
    @State private var dangye = 0          // 0 첫 화면, 1 적기, 2 요령, 3 문제, 4 이어 쓰기
    @State private var ireum = ""
    @State private var yeonrak = ""
    @State private var jiyeok = ""
    @State private var id1365 = ""
    @State private var jam = ""
    @State private var beonhoIeo = ""
    @State private var mi = 0
    @State private var majeun = 0
    @State private var allim = ""
    @State private var boneunJung = false
    @AccessibilityFocusState private var allimChojeom: Bool

    /// 2.10.0 이미 등록한 봉사자가 새 교육(일곱 가지 약속)을 다시 듣는 길 — 적기 없이 약속부터
    let dasi: Bool
    init(dasi: Bool = false) {
        self.dasi = dasi
        _dangye = State(initialValue: dasi ? 2 : 0)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if !allim.isEmpty {
                    Text(allim)
                        .font(.title2.weight(.semibold))
                        .foregroundColor(Saek.nam)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityFocused($allimChojeom)
                }
                if dasi && dangye >= 2 && dangye <= 3 {
                    Text("점지도 교육이 새로워졌습니다. 일곱 가지 약속을 한 번 듣고 확인 문제 아홉 개를 풀어 주시면 점지도 그리기를 이어서 쓰실 수 있습니다.")
                        .font(.title3).fixedSize(horizontal: false, vertical: true)
                } else if dangye >= 1 && dangye <= 3 { danggyePyo }
                switch dangye {
                case 0: cheotHwamyeon
                case 1: jeokgi
                case 2: yoryeongHwamyeon
                case 3: munjeHwamyeon
                default: ieoSseugiHwamyeon
                }
            }
            .padding()
        }
        .navigationTitle("자원봉사 등록")
        .navigationBarTitleDisplayMode(.inline)
    }

    /// 알리기 — 말소리가 켜져 있으면 길눈 목소리로, 꺼져 있으면 보이스오버 커서를 옮겨 글로
    private func allyeo(_ t: String, malHagi: Bool = true) {
        allim = t
        allimChojeom = false
        if malHagi && seol.malKyeojim {
            SoriEngine.shared.modu_geodugi()
            SoriEngine.shared.mal(t)
        } else {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { allimChojeom = true }
        }
    }

    /// 지금 몇 단계인지 — 눈으로는 세 칸 막대, 보이스오버로는 한 줄
    private var danggyePyo: some View {
        let ireumdeul = ["적기", "요령 듣기", "확인 문제"]
        return HStack(spacing: 8) {
            ForEach(0..<3, id: \.self) { k in
                VStack(spacing: 4) {
                    Capsule().fill(k < dangye ? Saek.nam : Color.gray.opacity(0.3)).frame(height: 10)
                    Text(ireumdeul[k]).font(.headline).foregroundColor(k < dangye ? Saek.nam : .secondary)
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("등록 세 단계 가운데 \(dangye)단계, \(ireumdeul[max(0, min(2, dangye - 1))])")
    }

    private var cheotHwamyeon: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 14) {
                Image(systemName: "figure.walk.circle.fill")
                    .font(.system(size: 64)).foregroundColor(Saek.nam).accessibilityHidden(true)
                Text("길눈 자봉에 오신 것을 환영합니다")
                    .font(.title.bold()).foregroundColor(Saek.nam).fixedSize(horizontal: false, vertical: true)
            }
            Text("여러분이 걸으며 그리는 점지도가 시각장애인이 혼자 걷는 길이 됩니다. 처음 한 번만 등록합니다. 5분쯤 걸리며, 이 폰이 기억하므로 다시 하지 않으셔도 됩니다.")
                .font(.title2).fixedSize(horizontal: false, vertical: true)
            Button("등록 시작하기") { dangye = 1; allyeo("이름부터 적어 주십시오.") }.buttonStyle(KeunDanchu())
            Button("웹 자봉에서 이미 등록했습니다 — 자봉 번호로 이어 쓰기") { dangye = 4; allyeo("자봉 번호와 네 자리 숫자를 적어 주십시오.") }.buttonStyle(KeunDanchu())
        }
    }

    /// 크고 선명한 적는 칸 — 눈에는 칸 위 이름표, 보이스오버에는 칸 이름 한 번만
    private func keunKan<V: View>(_ ireumpyo: String, _ kan: V) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(ireumpyo).font(.headline).foregroundColor(Saek.nam).accessibilityHidden(true)
            kan
                .font(.title2)
                .padding(14)
                .background(RoundedRectangle(cornerRadius: 12).fill(Color(.systemBackground)))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Saek.nam, lineWidth: 2))
                .accessibilityLabel(ireumpyo)
        }
    }

    private var jeokgi: some View {
        VStack(alignment: .leading, spacing: 16) {
            keunKan("이름", TextField("예: 홍길동", text: $ireum))
            keunKan("연락처 — 보완 요청과 회신을 드릴 때 씁니다", TextField("예: 010-1234-5678", text: $yeonrak).keyboardType(.phonePad))
            keunKan("주로 활동하실 지역", TextField("예: 서울 동대문구", text: $jiyeok))
            keunKan("1365 아이디 — 비워 두고 나중에 넣으셔도 됩니다", TextField("1365 아이디", text: $id1365).textInputAutocapitalization(.never).autocorrectionDisabled())
            keunKan("네 자리 숫자 — 나중에 등록 정보를 고치실 때 씁니다", SecureField("숫자 네 개", text: $jam).keyboardType(.numberPad))
            Button("다음 — 짧은 교육 듣기") {
                if ireum.trimmingCharacters(in: .whitespaces).isEmpty { allyeo("이름 칸이 비어 있습니다. 이름을 적어 주십시오."); return }
                if yeonrak.filter({ $0.isNumber }).count < 9 { allyeo("연락처가 짧습니다. 전화번호를 끝까지 적어 주십시오."); return }
                if jam.filter({ $0.isNumber }).count != 4 { allyeo("네 자리 숫자 칸에 숫자 네 개를 정해 적어 주십시오."); return }
                dangye = 2
                allim = "점지도 일곱 가지 약속입니다."
            }.buttonStyle(KeunDanchu())
            Button("뒤로 — 등록 첫 화면으로") { dangye = 0; allim = "" }.buttonStyle(KeunDanchu())
        }
    }

    /// 일곱 가지 약속을 차례로 읽음
    private func yoryeongIlgi() {
        if seol.malKyeojim {
            SoriEngine.shared.modu_geodugi()
            SoriEngine.shared.mal("점지도 일곱 가지 약속을 읽어 드리겠습니다. 약속 한 장을 누르시면 그 약속만 다시 들으실 수 있습니다.")
            for t in JbGyoyuk.sorijul { SoriEngine.shared.mal(t) }
            SoriEngine.shared.mal("다 들으셨으면 아래 다 들었습니다 단추를 눌러 확인 문제 아홉 개를 풀어 주십시오. 모두 맞혀야 통과합니다.")
        } else {
            allyeo("점지도 일곱 가지 약속입니다. 아래로 넘기시며 읽어 주십시오.", malHagi: false)
        }
    }

    private var yoryeongHwamyeon: some View {
        VStack(alignment: .leading, spacing: 14) {
            Button("약속 처음부터 다시 듣기") { yoryeongIlgi() }.buttonStyle(KeunDanchu())
            Text(JbGyoyuk.meorimal).font(.title3).fixedSize(horizontal: false, vertical: true)
            ForEach(Array(JbGyoyuk.yaksok.enumerated()), id: \.offset) { _, y in YaksokKadeu(y: y) }
            Text(JbGyoyuk.maejeummal).font(.title3).fixedSize(horizontal: false, vertical: true)
            Button("다 들었습니다 — 확인 문제 아홉 개 풀기") {
                mi = 0; majeun = 0; dangye = 3
                munjeIlgi("이제 확인 문제 아홉 개입니다. 모두 맞혀야 통과합니다. ")
            }.buttonStyle(KeunDanchu())
            if !dasi {
                Button("뒤로 — 적은 것 고치기") { SoriEngine.shared.modu_geodugi(); dangye = 1; allim = "" }.buttonStyle(KeunDanchu())
            }
        }
        .onAppear { yoryeongIlgi() }
    }

    /// 문제와 고를 말을 함께 읽음
    private func munjeIlgi(_ apmal: String = "") {
        let m = JbGyoyuk.munje[min(mi, JbGyoyuk.munje.count - 1)]
        var t = apmal + "\(mi + 1)번 문제. " + m.q
        for (k, d) in m.d.enumerated() { t += " \(JbGyoyuk.beonho[k]), \(d)." }
        allyeo(t)
    }

    private var munjeHwamyeon: some View {
        let m = JbGyoyuk.munje[min(mi, JbGyoyuk.munje.count - 1)]
        return VStack(alignment: .leading, spacing: 14) {
            Text("문제 \(mi + 1) / \(JbGyoyuk.munje.count)")
                .font(.headline).foregroundColor(.secondary).accessibilityHidden(true)
            Text(m.q)
                .font(.title.bold()).foregroundColor(Saek.nam).fixedSize(horizontal: false, vertical: true)
                .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 16).fill(Saek.norang.opacity(0.18)))
            ForEach(Array(m.d.enumerated()), id: \.offset) { i, d in
                Button("\(JbGyoyuk.beonho[i]). \(d)") { goreum(i) }.buttonStyle(KeunDanchu()).disabled(boneunJung)
            }
            Button("문제 다시 듣기") { munjeIlgi() }.buttonStyle(KeunDanchu())
            Button("뒤로 — 약속 다시 듣기") { dangye = 2 }.buttonStyle(KeunDanchu())
        }
    }

    private func goreum(_ i: Int) {
        let m = JbGyoyuk.munje[mi]
        guard i == m.a else {
            SoriEngine.shared.sori(.bikyeo)
            allyeo("아깝습니다. " + m.h + " 다시 골라 주십시오.")
            return
        }
        majeun += 1
        Baksu.chigi()   // 2.7.0 정답이면 박수
        Jindong.hagi("arrive")
        if mi + 1 < JbGyoyuk.munje.count {
            mi += 1
            let ap = ["맞습니다! 잘하셨습니다. ", "맞습니다! 점지도 박사님이십니다. "][mi % 2]
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { munjeIlgi(ap) }   // 박수가 끝난 뒤에 다음 문제
            return
        }
        boneunJung = true
        if dasi {
            // 2.10.0 이미 등록하신 분 — 교육 마친 기록만 남기고 탭으로
            allyeo("아홉 문제 모두 맞히셨습니다! 교육을 마쳤습니다. 이제 점지도 그리기를 이어서 쓰실 수 있습니다.")
            Baksu.chigi(keuge: true)
            Task {
                await nae.gyoyukMachim()
                await MainActor.run { boneunJung = false }
            }
            return
        }
        allyeo("아홉 문제 모두 맞히셨습니다! 등록하는 중입니다.")
        Task {
            let t = await nae.deungrok(ireum: ireum.trimmingCharacters(in: .whitespaces), yeonrak: yeonrak, jiyeok: jiyeok.trimmingCharacters(in: .whitespaces), id1365: id1365.trimmingCharacters(in: .whitespaces), jam: jam)
            await MainActor.run {
                boneunJung = false
                if let t = t { allyeo(t) }
            }
        }
    }

    private var ieoSseugiHwamyeon: some View {
        VStack(alignment: .leading, spacing: 16) {
            keunKan("자봉 번호", TextField("예: J0001", text: $beonhoIeo).textInputAutocapitalization(.characters).autocorrectionDisabled())
            keunKan("네 자리 숫자", SecureField("숫자 네 개", text: $jam).keyboardType(.numberPad))
            Button("이어 쓰기") {
                boneunJung = true
                allyeo("찾는 중입니다.")
                Task {
                    let t = await nae.ieoSseugi(beonho: beonhoIeo, jam: jam)
                    await MainActor.run {
                        boneunJung = false
                        if let t = t { allyeo(t) } else { SoriEngine.shared.mal("\(nae.ireum)님, 이어서 쓰십니다. 자봉 번호는 \(nae.beonho)입니다.") }
                    }
                }
            }.buttonStyle(KeunDanchu()).disabled(boneunJung)
            Button("뒤로 — 등록 첫 화면으로") { dangye = 0; allim = "" }.buttonStyle(KeunDanchu())
        }
    }
}

// MARK: 등록을 마친 환영 화면 (2.6.0) — 봉사의 보람을 느끼게
struct HwanyeongView: View {
    @ObservedObject private var nae = JabongNae.shared
    @AccessibilityFocusState private var chojeom: Bool
    var body: some View {
        ScrollView {
            VStack(spacing: 22) {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 96)).foregroundColor(Saek.norang)
                    .shadow(color: Saek.nam.opacity(0.5), radius: 2)
                    .accessibilityHidden(true)
                Text("환영합니다, \(nae.ireum)님!")
                    .font(.largeTitle.bold()).foregroundColor(Saek.nam)
                    .multilineTextAlignment(.center)
                    .accessibilityFocused($chojeom)
                Text("자봉 번호 \(nae.beonho)")
                    .font(.title.bold()).foregroundColor(.white)
                    .padding(.horizontal, 22).padding(.vertical, 12)
                    .background(Capsule().fill(Saek.nam))
                Text("오늘부터 걸으시는 한 걸음 한 걸음이 시각장애인이 혼자 걷는 길이 됩니다. 함께해 주셔서 고맙습니다.")
                    .font(.title2).multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
                Text("자봉 번호는 내 기록 탭에서 언제든 다시 보실 수 있습니다.")
                    .font(.title3).foregroundColor(.secondary).multilineTextAlignment(.center)
                Button("봉사 시작하기 — 봉사 탭에서 보폭부터 잽니다") { nae.hwanyeongKkeut() }.buttonStyle(KeunDanchu())
            }
            .padding()
        }
        .navigationTitle("등록을 마쳤습니다")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            Baksu.chigi(keuge: true)   // 2.7.0 등록을 마치면 큰 박수
            Jindong.hagi("arrive")
            if Seoljeong.shared.malKyeojim {
                SoriEngine.shared.mal("등록을 마쳤습니다. 환영합니다, \(nae.ireum)님. 자봉 번호는 \(nae.beonho)입니다. 오늘부터 걸으시는 한 걸음 한 걸음이 시각장애인이 혼자 걷는 길이 됩니다. 봉사 시작하기를 누르시면 봉사 탭으로 갑니다.")
            } else {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { chojeom = true }
            }
        }
    }
}
