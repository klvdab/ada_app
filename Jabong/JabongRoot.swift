// 자봉 앱 화면 — 처음 등록, 탭 넷(봉사, 나눔, 내 기록, 알림·설정)
// 길눈 화면 원칙 그대로: 탭 바는 모든 속 화면에 늘, 뒤로 단추는 위에 하나, 목록은 다섯 개씩, 결과가 나오면 커서를 첫 줄로
import SwiftUI

struct JabongRoot: View {
    @ObservedObject private var nae = JabongNae.shared
    var body: some View {
        if nae.deungrokham && !nae.hwanyeong {
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
    @State private var tab = 0
    var body: some View {
        TabView(selection: $tab) {
            NavigationStack { BongsaTab() }
                .tabItem { Label("봉사", systemImage: "figure.walk") }.tag(0)
            NavigationStack { JbNanumTab() }
                .tabItem { Label("나눔", systemImage: "bubble.left.and.bubble.right") }.tag(1)
            NavigationStack { NaeGirokTab() }
                .tabItem { Label("내 기록", systemImage: "list.bullet.rectangle") }.tag(2)
            NavigationStack { AllimTab() }
                .tabItem { Label("알림·설정", systemImage: "gearshape") }.tag(3)
        }
        .tint(Saek.nam)
        .fullScreenCover(isPresented: $t.boim) { TonghwaView() }   // 2.1.0 긴급통화 통화 화면
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
    var body: some View {
        TabCheot(jemok: "봉사") {
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
            Text("오늘 걸을 길과 함께 걷기는 이 탭에 차례로 들어섭니다.").font(.body)
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

struct JbNanumTab: View {
    var body: some View {
        TabCheot(jemok: "나눔") {
            Text("그려주세요 게시판, 걸음 나눔 게시판, 물품 나눔 마당이 이 탭에 들어섭니다. 그동안은 웹 자봉에서 쓰시던 글이 그대로 남아 있고, 앱이 채워지면 같은 글을 앱에서 보시게 됩니다.").font(.body)
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
        ("처음 등록", "자봉 앱을 처음 여시면 한 번만 등록합니다. 이름, 연락처, 주로 활동하실 지역, 네 자리 숫자를 적고, 1365 아이디는 비워 두었다가 나중에 넣으셔도 됩니다. 다음을 누르시면 점지도 그리기 요령 다섯 가지를 길눈 목소리로 차례로 읽어 드리고, 요령 다시 듣기로 언제든 다시 들으실 수 있습니다. 이어서 확인 문제 세 개를 문제와 고를 말까지 읽어 드리며, 맞히면 딩동 소리와 진동으로 알려 드립니다. 칸이 비었거나 맞지 않으면 무엇이 모자란지 말로 알려 드립니다. 세 문제를 다 맞히면 환영 화면에서 자봉 번호를 알려 드리고, 봉사 시작하기를 누르시면 봉사 탭으로 갑니다. 프로그램 말소리를 꺼 두셨으면 소리 대신 보이스오버 커서로 알려 드립니다. 웹 자봉에서 이미 등록하셨으면 자봉 번호와 네 자리 숫자로 이어서 쓰십시오."),
        ("탭 넷", "화면 아래에 봉사, 나눔, 내 기록, 알림·설정 탭이 있고, 속 화면에서도 늘 보입니다. 속 화면의 뒤로 단추는 위에 하나 있고, 두 손가락으로 문질러도 뒤로 갑니다."),
        ("긴급통화 받기", "봉사 탭 맨 위에 있습니다. 자원봉사자나 현장영상해설사 가운데 받으실 역할을 고르고, 별명과 수료 번호(해설사는 협회에 등록한 전화번호)를 적은 뒤 함께하겠습니다를 한 번 누르시면 됩니다. 이때 카메라와 마이크 허락도 한 번에 받아 둡니다. 그 뒤로는 길손님이 도움을 청하면 폰이 잠겨 있어도 일반 전화처럼 울리고, 받으시면 곧바로 길손님 카메라 화면과 말소리가 이어집니다. 다른 길눈님이 먼저 받으시면 벨이 멈추고 다른 분께 연결되었다고 알려 드립니다. 실명과 전화번호는 화면에 나오지 않고 별명만 씁니다. 잠시 쉬기를 누르시면 울리지 않습니다. 긴급통화는 이 자봉 앱으로만 받습니다. 자원봉사자와 현장영상해설사는 누구를 고를 수 없게 되어 있고, 받을 수 있는 분 가운데 먼저 받는 분이 연결됩니다. 기회가 고르게 가도록 처음 15초는 최근에 덜 받으신 다섯 분께 먼저 울리고, 그래도 아무도 안 받으면 모든 분께 울립니다. 통화료는 들지 않고 데이터만 씁니다(와이파이에서는 따로 드는 돈이 없음). 곧바로 잇지 못할 때 거치는 영상 다리 주소는 나스에서 받아 쓰므로, 다리를 옮겨도 앱을 새로 받으실 필요가 없습니다."),
        ("가족·지인으로 받기 — 이음 번호", "길눈을 쓰시는 가족이나 지인이 나를 콕 집어 화상통화를 요청하실 수 있게 등록합니다. 먼저 길눈님이 길눈 설정 탭의 가족·지인 명단에서 이음 번호 받기를 누르면 여섯 자리 숫자가 나옵니다. 이 번호를 전화로 불러 받으십시오. 번호는 30분 동안만 쓰입니다. 자봉 앱 봉사 탭, 긴급통화 받기에서 가족·지인으로 받기를 고르고, 이음 번호와 길눈님이 부르실 내 이름(보기: 큰딸)을 넣고 가족·지인으로 등록하기를 누르시면 끝입니다. 그 뒤로 그 길눈님이 나를 고르시면 이 폰만 일반 전화처럼 울리고 화면에 그분 이름이 뜹니다. 자원봉사자로도 함께하시는 분은 두 가지가 다 됩니다."),
        ("긴급통화 받지 않기", "점지도만 그려 주시고 통화는 원치 않으시면, 긴급통화 받기 화면의 긴급통화 받지 않기(처음이면 긴급통화는 받지 않겠습니다)를 누르십시오. 어떤 요청도 울리지 않고, 폰 알림 주소도 나스에서 지웁니다. 점지도 그리기는 그대로 쓰십니다. 마음이 바뀌시면 같은 화면의 긴급통화 받기 시작을 누르시면 됩니다."),
        ("지금 상태 듣기", "봉사 탭에서 누르시면 자봉 번호, 긴급통화를 받는지, 보폭, 위성이 잘 잡혔는지를 말씀드립니다."),
        ("내 보폭 재기", "봉사 탭에서 엽니다. 정해진 거리를 걸으면 보폭을 셈해 폰이 기억합니다. 한 번 재면 다시 재지 않아도 되고, 원하실 때 다시 잴 수 있습니다."),
        ("새로고침", "알림·설정 탭에서 누르시면 등록 정보와 그려 주신 길 수를 나스에서 다시 받습니다."),
        ("저절로 저장", "현장에서는 늘 의외의 일이 생기므로 1분마다 저절로 저장합니다. 점지도를 그리는 중에는 1분마다, 표시를 남길 때마다, 앱이 뒤로 갈 때 그리던 길을 저장하고, 앱이 꺼졌다 켜지면 그리던 길을 잠깐 멈춤으로 되살려 이어 그리실 수 있습니다."),
        ("몸 센서 — 걸음과 방향을 더 정확하게", "점지도를 그리시는 동안 폰의 가속도계, 자이로, 나침반을 1초에 50번 읽습니다. 발이 땅에 닿을 때마다 한 걸음을 바로 세고, 몸이 몇 도 돌았는지 자이로로 재어 쇠붙이나 건물 옆에서도 방향이 틀어지지 않게 합니다. 걸음마다 시각, 방향, 돈 각도, 위아래 충격, 높이, 멈춤과 걷기와 탈것 상태를 한 줄씩 남깁니다. 꺾이셨습니까 물음도 이 각도로 가려 다 도신 뒤에 여쭙니다. 다 걸었습니다를 누르시면 아이폰 만보기로 센 걸음과 몸 센서로 센 걸음을 견주어, 차이가 크면 알려 드립니다. 폰을 손에 드셔도 주머니에 넣으셔도 됩니다. 기록 모양은 안드로이드 자봉 앱과 똑같아 어느 폰으로 그린 점지도든 함께 쓰입니다."),
        ("점지도 그리기", "봉사 탭에서 엽니다. 보폭을 먼저 재 두셔야 시작할 수 있습니다. 걷기 시작을 누르시면 출발한 자리 주소를 저절로 적고, 걸음 수와 방향, 위성 자리, 높이를 1초마다 폰 안에 기록합니다. 화면이 잠기거나 다른 앱을 쓰셔도 이어 갑니다. 길을 접어드시면 무슨 길에 접어드셨는지 알려 드립니다. 다 걸었습니다를 누르시면 걸음과 거리, 표시 수를 말씀드리고 도착한 자리 주소를 적어 그린 길로 폰에 담아 둡니다. 그린 길은 같은 화면의 그린 길 펼치기에서 다섯 개씩 보십니다. 올리기 전 점검과 올리기는 다음 판에 들어섭니다."),
        ("표시 남기기", "그리는 중 화면 겉에 자주 쓰는 여덟 가지(왼쪽으로 꺾임, 오른쪽으로 꺾임, 올라가는 계단 시작, 내려가는 계단 시작, 계단 끝, 횡단보도 건너기 시작과 끝, 문)가 크게 있고, 다른 표시와 도구 펼치기 안에 나머지 열네 가지와 잠깐 멈춤, 계단 칸수 고치기가 있습니다. 계단과 횡단보도는 시작을 찍으면 끝도 꼭 찍으셔야 하며, 그 사이 칸수와 걸음을 셈해 알려 드립니다. 에스컬레이터, 지하철, 버스는 탈 때와 내릴 때를 찍으시면 그 사이는 걸음으로 재지 않습니다. 문은 딱 찍고 두 걸음 앞에서 한 번 더 찍으셔야 확실한 문이 됩니다."),
        ("말로 표시", "손이 바쁘실 때 말로 표시 단추를 누르고 계단 시작, 왼쪽, 횡단보도 끝, 문처럼 말씀하시면 단추를 누른 그 자리에 표시를 남깁니다. 좌회전, 우회전, 건널목, 승강기 같은 말도 알아듣습니다."),
        ("폰이 먼저 여쭘", "그리는 중에 방향이 크게 바뀌면 왼쪽으로 꺾이셨습니까, 높이가 바뀌면 올라가는 계단입니까처럼 먼저 여쭙고, 계단 중에 높이가 그대로이면 계단이 끝났습니까 하고 여쭙니다. 네라고 말씀하시거나 화면 맨 위에 나오는 네 단추를 누르셔야 표시가 되며, 바뀐 것을 알아챈 그 자리에 남깁니다. 아니오면 남기지 않습니다. 20초 동안 답이 없으면 물음을 거둡니다.")
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

    static let yoryeong = [
        "하나. 처음 그리시기 전에 보폭을 한 번 잽니다. 보폭이 있어야 걸음 수가 정확해집니다.",
        "둘. 출발 전에 하늘이 트인 곳에서 잠시 기다려 위성이 잡히게 합니다. 출발지와 도착지 주소는 길눈이 저절로 적습니다.",
        "셋. 폰은 손에 들거나 주머니에 넣고, 평소 걸음으로 걷습니다. 가방 깊숙이 넣으면 걸음이 잡히지 않습니다.",
        "넷. 꺾이는 곳, 건널목, 턱, 계단, 점자블록이 끊기는 곳은 그 자리에 닿는 순간 표시를 찍습니다. 시작을 찍었으면 끝도 꼭 찍습니다.",
        "다섯. 올리기 전 점검에서 흠이 나오면 그 구간을 다시 걷습니다. 시각장애인의 안전이 이 한 줄에 달려 있습니다."
    ]
    static let yoryeongGeurim = ["ruler", "location.fill", "iphone", "hand.tap.fill", "checkmark.shield.fill"]
    static let munje: [(q: String, d: [String], a: Int, h: String)] = [
        ("첫째 문제. 꺾이는 곳 표시는 언제 찍습니까?", ["꺾이는 곳에 닿는 순간 찍습니다", "다 걸은 뒤 한꺼번에 찍습니다"], 0, "꺾이는 곳에 닿는 바로 그 순간 찍어야 시각장애인이 정확한 자리에서 꺾을 수 있습니다."),
        ("둘째 문제. 건널목 건너기 시작을 찍었으면 어떻게 합니까?", ["시작만 찍어도 됩니다", "다 건너서 끝도 꼭 찍습니다"], 1, "시작과 끝이 짝을 이루어야 건널목의 길이를 알려 드릴 수 있습니다."),
        ("셋째 문제. 그리는 동안 폰은 어디에 둡니까?", ["손에 들거나 주머니에 넣습니다", "가방 깊숙이 넣습니다"], 0, "가방 깊숙이 넣으면 걸음이 잡히지 않아 걸음 수가 틀립니다.")
    ]

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
                if dangye >= 1 && dangye <= 3 { danggyePyo }
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
                allim = "점지도 그리기 요령 다섯 가지입니다."
            }.buttonStyle(KeunDanchu())
            Button("뒤로 — 등록 첫 화면으로") { dangye = 0; allim = "" }.buttonStyle(KeunDanchu())
        }
    }

    /// 요령 다섯 가지를 차례로 읽음
    private func yoryeongIlgi() {
        if seol.malKyeojim {
            SoriEngine.shared.modu_geodugi()
            SoriEngine.shared.mal("점지도 그리기 요령 다섯 가지를 읽어 드리겠습니다.")
            for t in DeungrokView.yoryeong { SoriEngine.shared.mal(t) }
            SoriEngine.shared.mal("다 들으셨으면 바로 아래 다 들었습니다 단추를 눌러 확인 문제를 풀어 주십시오.")
        } else {
            allyeo("점지도 그리기 요령 다섯 가지입니다. 아래로 넘기시며 읽어 주십시오.", malHagi: false)
        }
    }

    private var yoryeongHwamyeon: some View {
        VStack(alignment: .leading, spacing: 14) {
            Button("요령 다시 듣기") { yoryeongIlgi() }.buttonStyle(KeunDanchu())
            ForEach(Array(DeungrokView.yoryeong.enumerated()), id: \.offset) { i, t in
                HStack(alignment: .top, spacing: 14) {
                    Image(systemName: DeungrokView.yoryeongGeurim[i])
                        .font(.system(size: 32, weight: .bold)).foregroundColor(Saek.nam)
                        .frame(width: 46).accessibilityHidden(true)
                    Text(t).font(.title2).foregroundColor(.primary).fixedSize(horizontal: false, vertical: true)
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 16).fill(Saek.norang.opacity(0.18)))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Saek.nam, lineWidth: 2))
            }
            Button("다 들었습니다 — 확인 문제 풀기") {
                mi = 0; majeun = 0; dangye = 3
                munjeIlgi("이제 확인 문제 세 개입니다. ")
            }.buttonStyle(KeunDanchu())
            Button("뒤로 — 적은 것 고치기") { SoriEngine.shared.modu_geodugi(); dangye = 1; allim = "" }.buttonStyle(KeunDanchu())
        }
        .onAppear { yoryeongIlgi() }
    }

    /// 문제와 고를 말을 함께 읽음
    private func munjeIlgi(_ apmal: String = "") {
        let m = DeungrokView.munje[min(mi, DeungrokView.munje.count - 1)]
        var t = apmal + m.q
        for (k, d) in m.d.enumerated() { t += " \(k == 0 ? "하나" : "둘"), \(d)." }
        allyeo(t)
    }

    private var munjeHwamyeon: some View {
        let m = DeungrokView.munje[min(mi, DeungrokView.munje.count - 1)]
        return VStack(alignment: .leading, spacing: 14) {
            Text("문제 \(mi + 1) / \(DeungrokView.munje.count)")
                .font(.headline).foregroundColor(.secondary).accessibilityHidden(true)
            Text(m.q)
                .font(.title.bold()).foregroundColor(Saek.nam).fixedSize(horizontal: false, vertical: true)
                .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 16).fill(Saek.norang.opacity(0.18)))
            ForEach(Array(m.d.enumerated()), id: \.offset) { i, d in
                Button(d) { goreum(i) }.buttonStyle(KeunDanchu()).disabled(boneunJung)
            }
            Button("문제 다시 듣기") { munjeIlgi() }.buttonStyle(KeunDanchu())
            Button("뒤로 — 요령 다시 듣기") { dangye = 2 }.buttonStyle(KeunDanchu())
        }
    }

    private func goreum(_ i: Int) {
        let m = DeungrokView.munje[mi]
        guard i == m.a else {
            SoriEngine.shared.sori(.bikyeo)
            allyeo("아깝습니다. " + m.h + " 다시 골라 주십시오.")
            return
        }
        majeun += 1
        SoriEngine.shared.sori(.dingdong)
        Jindong.hagi("arrive")
        if mi + 1 < DeungrokView.munje.count {
            mi += 1
            munjeIlgi(["맞습니다! 잘하셨습니다. ", "맞습니다! 점지도 박사님이십니다. "][min(mi - 1, 1)])
            return
        }
        boneunJung = true
        allyeo("세 문제 모두 맞히셨습니다! 등록하는 중입니다.")
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
            SoriEngine.shared.sori(.dochak)
            Jindong.hagi("arrive")
            if Seoljeong.shared.malKyeojim {
                SoriEngine.shared.mal("등록을 마쳤습니다. 환영합니다, \(nae.ireum)님. 자봉 번호는 \(nae.beonho)입니다. 오늘부터 걸으시는 한 걸음 한 걸음이 시각장애인이 혼자 걷는 길이 됩니다. 봉사 시작하기를 누르시면 봉사 탭으로 갑니다.")
            } else {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { chojeom = true }
            }
        }
    }
}
