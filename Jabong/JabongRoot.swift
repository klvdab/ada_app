// 자봉 앱 화면 — 처음 등록, 탭 넷(봉사, 나눔, 내 기록, 알림·설정)
// 길눈 화면 원칙 그대로: 탭 바는 모든 속 화면에 늘, 뒤로 단추는 위에 하나, 목록은 다섯 개씩, 결과가 나오면 커서를 첫 줄로
import SwiftUI

struct JabongRoot: View {
    @ObservedObject private var nae = JabongNae.shared
    var body: some View {
        if nae.deungrokham {
            JabongTab()
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
    var body: some View {
        TabCheot(jemok: "봉사") {
            NavigationLink { HamkkeView() } label: {
                Text(g.kyeojim ? "긴급통화 받기 — 받고 있음" : "긴급통화 받기 — 길손님이 도움을 청하면 전화처럼 울립니다")
            }.buttonStyle(KeunDanchu())
            Button("지금 상태 듣기") { jigeumSangtae() }.buttonStyle(KeunDanchu())
            NavigationLink { BopokView() } label: {
                Text(s.bopok > 0.2 ? "내 보폭 다시 재기 — 지금 \(Int((s.bopok * 100).rounded()))센티미터" : "내 보폭 재기 — 점지도를 그리기 전에 한 번")
            }.buttonStyle(KeunDanchu())
            Text("점지도 그리기, 오늘 걸을 길, 함께 걷기는 이 탭에 차례로 들어섭니다.").font(.body)
        }
    }

    private func jigeumSangtae() {
        var m = "자봉 번호 \(nae.beonho), \(nae.ireum)님."
        m += g.kyeojim ? " 긴급통화를 받고 있습니다." : " 긴급통화는 받지 않는 중입니다."
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
        ("처음 등록", "자봉 앱을 처음 여시면 한 번만 등록합니다. 이름, 연락처, 주로 활동하실 지역, 네 자리 숫자를 적고, 1365 아이디는 비워 두었다가 나중에 넣으셔도 됩니다. 점지도 그리기 요령 다섯 가지를 듣고 확인 문제 세 개를 풀면 자봉 번호가 나옵니다. 웹 자봉에서 이미 등록하셨으면 자봉 번호와 네 자리 숫자로 이어서 쓰십시오."),
        ("탭 넷", "화면 아래에 봉사, 나눔, 내 기록, 알림·설정 탭이 있고, 속 화면에서도 늘 보입니다. 속 화면의 뒤로 단추는 위에 하나 있고, 두 손가락으로 문질러도 뒤로 갑니다."),
        ("긴급통화 받기", "봉사 탭 맨 위에 있습니다. 자원봉사자나 현장영상해설사 가운데 받으실 역할을 고르고, 별명과 수료 번호(해설사는 협회에 등록한 전화번호)를 적은 뒤 함께하겠습니다를 한 번 누르시면 됩니다. 이때 카메라와 마이크 허락도 한 번에 받아 둡니다. 그 뒤로는 길손님이 도움을 청하면 폰이 잠겨 있어도 일반 전화처럼 울리고, 받으시면 곧바로 길손님 카메라 화면과 말소리가 이어집니다. 다른 길눈님이 먼저 받으시면 벨이 멈추고 다른 분께 연결되었다고 알려 드립니다. 실명과 전화번호는 화면에 나오지 않고 별명만 씁니다. 잠시 쉬기를 누르시면 울리지 않습니다."),
        ("지금 상태 듣기", "봉사 탭에서 누르시면 자봉 번호, 긴급통화를 받는지, 보폭, 위성이 잘 잡혔는지를 말씀드립니다."),
        ("내 보폭 재기", "봉사 탭에서 엽니다. 정해진 거리를 걸으면 보폭을 셈해 폰이 기억합니다. 한 번 재면 다시 재지 않아도 되고, 원하실 때 다시 잴 수 있습니다."),
        ("새로고침", "알림·설정 탭에서 누르시면 등록 정보와 그려 주신 길 수를 나스에서 다시 받습니다."),
        ("저절로 저장", "현장에서는 늘 의외의 일이 생기므로 1분마다 저절로 저장합니다.")
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

struct DeungrokView: View {
    @ObservedObject private var nae = JabongNae.shared
    @State private var dangye = 0          // 0 첫 화면, 1 적기, 2 요령, 3 문제, 4 이어 쓰기
    @State private var ireum = ""
    @State private var yeonrak = ""
    @State private var jiyeok = ""
    @State private var id1365 = ""
    @State private var jam = ""
    @State private var beonhoIeo = ""
    @State private var mi = 0
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
    static let munje: [(q: String, d: [String], a: Int, h: String)] = [
        ("첫째 문제. 꺾이는 곳 표시는 언제 찍습니까?", ["꺾이는 곳에 닿는 순간 찍습니다", "다 걸은 뒤 한꺼번에 찍습니다"], 0, "꺾이는 곳에 닿는 바로 그 순간 찍어야 시각장애인이 정확한 자리에서 꺾을 수 있습니다."),
        ("둘째 문제. 건널목 건너기 시작을 찍었으면 어떻게 합니까?", ["시작만 찍어도 됩니다", "다 건너서 끝도 꼭 찍습니다"], 1, "시작과 끝이 짝을 이루어야 건널목의 길이를 알려 드릴 수 있습니다."),
        ("셋째 문제. 그리는 동안 폰은 어디에 둡니까?", ["손에 들거나 주머니에 넣습니다", "가방 깊숙이 넣습니다"], 0, "가방 깊숙이 넣으면 걸음이 잡히지 않아 걸음 수가 틀립니다.")
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if !allim.isEmpty {
                    Text(allim).font(.title3).accessibilityFocused($allimChojeom)
                }
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

    private func allyeo(_ t: String) {
        allim = t
        allimChojeom = false
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { allimChojeom = true }
    }

    private var cheotHwamyeon: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("점지도 자원봉사를 시작하기 전에 처음 한 번만 등록합니다. 5분쯤 걸리며, 이 폰이 기억하므로 다시 하지 않으셔도 됩니다.").font(.title3)
            Button("등록 시작하기") { dangye = 1; allyeo("이름부터 적어 주십시오.") }.buttonStyle(KeunDanchu())
            Button("웹 자봉에서 이미 등록했습니다 — 자봉 번호로 이어 쓰기") { dangye = 4; allyeo("자봉 번호와 네 자리 숫자를 적어 주십시오.") }.buttonStyle(KeunDanchu())
        }
    }

    private var jeokgi: some View {
        VStack(alignment: .leading, spacing: 14) {
            TextField("이름", text: $ireum).textFieldStyle(.roundedBorder).font(.title3)
            TextField("연락처 — 보완 요청과 회신을 드릴 때 씁니다", text: $yeonrak).keyboardType(.phonePad).textFieldStyle(.roundedBorder).font(.title3)
            TextField("주로 활동하실 지역 — 예를 들어 서울 동대문구", text: $jiyeok).textFieldStyle(.roundedBorder).font(.title3)
            TextField("1365 아이디 — 비워 두고 나중에 넣으셔도 됩니다", text: $id1365).textInputAutocapitalization(.never).autocorrectionDisabled().textFieldStyle(.roundedBorder).font(.title3)
            SecureField("네 자리 숫자 — 나중에 등록 정보를 고치실 때 씁니다", text: $jam).keyboardType(.numberPad).textFieldStyle(.roundedBorder).font(.title3)
            Button("다음 — 짧은 교육 듣기") {
                if ireum.trimmingCharacters(in: .whitespaces).isEmpty { allyeo("이름을 적어 주십시오."); return }
                if yeonrak.filter({ $0.isNumber }).count < 9 { allyeo("연락처를 적어 주십시오."); return }
                if jam.filter({ $0.isNumber }).count != 4 { allyeo("네 자리 숫자를 정해 주십시오."); return }
                dangye = 2
                allyeo("점지도 그리기 요령 다섯 가지입니다.")
            }.buttonStyle(KeunDanchu())
            Button("뒤로 — 등록 첫 화면으로") { dangye = 0; allim = "" }.buttonStyle(KeunDanchu())
        }
    }

    private var yoryeongHwamyeon: some View {
        VStack(alignment: .leading, spacing: 14) {
            ForEach(DeungrokView.yoryeong, id: \.self) { Text($0).font(.title3) }
            Button("다 들었습니다 — 확인 문제 풀기") { mi = 0; dangye = 3; allyeo(DeungrokView.munje[0].q) }.buttonStyle(KeunDanchu())
            Button("뒤로 — 적은 것 고치기") { dangye = 1; allim = "" }.buttonStyle(KeunDanchu())
        }
    }

    private var munjeHwamyeon: some View {
        let m = DeungrokView.munje[min(mi, DeungrokView.munje.count - 1)]
        return VStack(alignment: .leading, spacing: 14) {
            ForEach(Array(m.d.enumerated()), id: \.offset) { i, d in
                Button(d) { goreum(i) }.buttonStyle(KeunDanchu()).disabled(boneunJung)
            }
            Button("뒤로 — 요령 다시 듣기") { dangye = 2; allyeo("점지도 그리기 요령 다섯 가지입니다.") }.buttonStyle(KeunDanchu())
        }
    }

    private func goreum(_ i: Int) {
        let m = DeungrokView.munje[mi]
        guard i == m.a else { allyeo("다시 생각해 보십시오. " + m.h + " " + m.q); return }
        if mi + 1 < DeungrokView.munje.count {
            mi += 1
            allyeo("맞습니다. " + DeungrokView.munje[mi].q)
            return
        }
        boneunJung = true
        allyeo("맞습니다. 등록하는 중입니다.")
        Task {
            let t = await nae.deungrok(ireum: ireum.trimmingCharacters(in: .whitespaces), yeonrak: yeonrak, jiyeok: jiyeok.trimmingCharacters(in: .whitespaces), id1365: id1365.trimmingCharacters(in: .whitespaces), jam: jam)
            await MainActor.run {
                boneunJung = false
                if let t = t { allyeo(t) } else { SoriEngine.shared.mal("등록을 마쳤습니다. 자봉 번호는 \(nae.beonho)입니다. 봉사 탭에서 보폭을 먼저 재 주십시오.") }
            }
        }
    }

    private var ieoSseugiHwamyeon: some View {
        VStack(alignment: .leading, spacing: 14) {
            TextField("자봉 번호 — 예를 들어 J0001", text: $beonhoIeo).textInputAutocapitalization(.characters).autocorrectionDisabled().textFieldStyle(.roundedBorder).font(.title3)
            SecureField("네 자리 숫자", text: $jam).keyboardType(.numberPad).textFieldStyle(.roundedBorder).font(.title3)
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
