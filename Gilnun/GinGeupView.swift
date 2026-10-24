// 긴급통화서비스 화면 — 세 갈래, 한마디 먼저 남기기, 가족·지인 고르기.
// 2.4.1 (260928-6) 가족·지인 명단과 내 이름은 설계도대로 설정 탭에 둡니다(이사장님 지적). 명단이 비었을 때만 여기서 명단 화면을 곧장 엽니다.
// 요청 중·통화 중에는 맨 위 한 줄에 지금 형편과 "그만두기" 단추를 한 자리에.
import SwiftUI
import UIKit

struct GinGeupView: View {
    @ObservedObject private var g = GinGeup.shared
    @AccessibilityFocusState private var chojeom: Bool
    @AccessibilityFocusState private var neomChojeom: Bool

    /// 2.94.0 (261017-I48, 이사장님 승인 2026-10-17) 내 자리 보내기 — 그만 보내기·다시 보내기
    private var jariDanchu: some View {
        Button(g.jariBonaem ? "내 자리 보내는 중 — 그만 보내기" : "내 자리 보내기 — 다시 보내기") { g.jariBonaegiBakkugi() }
            .buttonStyle(KeunDanchu())
    }

    /// 2.104.0 (261024-I58, 이사장님 지시 2026-10-24) 카메라 형편 한 줄과, 허락이 꺼져 있으면 설정으로 가는 단추
    @ViewBuilder private var kameraJul: some View {
        if !g.kameraSangtae.isEmpty { Text(g.kameraSangtae).font(.title3) }
        if g.kameraMakhim { kameraSeoljeong }
    }

    private var kameraSeoljeong: some View {
        Button("아이폰 설정에서 길눈 카메라 켜기") {
            if let u = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(u) }
        }
        .buttonStyle(KeunDanchu())
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                switch g.sangtae {
                case .eopseum:
                    if g.neomgilkka {
                        // 2.42.0 안내를 읽은 바로 그 자리에 단추
                        Button("\(g.geul) — 예, 요청합니다") { g.yocheong(.dowum) }
                            .buttonStyle(KeunDanchu())
                            .accessibilityFocused($neomChojeom)
                        Button("아니요, 그만둡니다") { g.neomgilkka = false }
                            .buttonStyle(KeunDanchu())
                    }
                    NavigationLink(value: GilHwamyeon.jiinGoreugi) { Text("가족·지인에게 화상통화 요청 — 한 분을 고르십시오") }
                        .buttonStyle(KeunDanchu())
                        .accessibilityFocused($chojeom)
                    Button("자원봉사자에게 화상통화 요청") { g.yocheong(.haebong) }
                        .buttonStyle(KeunDanchu())
                    Button("전문 현장영상해설사에게 화상통화 요청") { g.yocheong(.haeseolsa) }
                        .buttonStyle(KeunDanchu())
                    TextField("한마디 먼저 남기기 — 받는 분 화면에 뜹니다", text: $g.malHan)
                        .textFieldStyle(.roundedBorder)
                        .font(.title3)
                    if !g.geul.isEmpty && !g.neomgilkka { Text(g.geul).font(.title3) }
                    if g.kameraMakhim { kameraSeoljeong }   // 2.104.0
                case .tonghwa:
                    Button("\(g.geul) — 끊기") { g.geumanhagi() }
                        .buttonStyle(KeunDanchu())
                        .accessibilityFocused($chojeom)
                    kameraJul   // 2.104.0
                    jariDanchu   // 2.94.0
                default:
                    Button("\(g.geul) — 요청 그만두기") { g.geumanhagi() }
                        .buttonStyle(KeunDanchu())
                        .accessibilityFocused($chojeom)
                    kameraJul   // 2.104.0
                    jariDanchu   // 2.94.0
                    if g.galrae == .jiin, let s = g.saram, !Jiin.shared.tel(s.id).isEmpty {
                        Button("\(s.name) 님께 전화 걸기") { g.jeonhwa() }
                            .buttonStyle(KeunDanchu())
                    }
                }
            }
            .padding()
        }
        .sokHwamyeon("긴급통화서비스")
        .onAppear {   // 2.12.1 매번 첫 줄로 (2.42.0 넘길지 여쭐 때는 그 단추로)
            g.neomgilkkaSalpigi()   // 2.86.0 / 2.56.0 (261016-I40 / 261016-A44, 이사장님 승인 2026-10-16) 2분 지난 물음은 거둠
            chojeom = false; neomChojeom = false
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { if g.neomgilkka { neomChojeom = true } else { chojeom = true } }
        }
        .onChange(of: g.neomgilkka) { v in if v { neomChojeom = false; DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { neomChojeom = true } } }
    }
}

/// 가족·지인 고르기 — 이름에 두 번 두드리면 그 한 분께만 요청이 갑니다
struct JiinGoreugiView: View {
    @ObservedObject private var jiin = Jiin.shared
    @Environment(\.dismiss) private var dismiss
    @State private var mot = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if !jiin.bulreoom && !mot {
                    Text("명단을 받는 중입니다.").font(.title3)
                } else if jiin.mokrok.isEmpty {
                    NavigationLink(value: GilHwamyeon.jiinMyeongdan) {
                        Text(mot ? "명단을 받지 못했습니다. 통신이 끊겼을 수 있습니다 — 명단 화면 열기" : "명단이 비어 있습니다 — 명단 화면을 열어 등록하기. 설정 탭의 가족·지인 명단과 같은 화면입니다")
                    }
                    .buttonStyle(KeunDanchu())
                } else {
                    Mokrok5(jiin.mokrok) { s in
                        // 2.86.0 / 2.56.0 (261016-I40 / 261016-A44, 이사장님 승인 2026-10-16) 별명도 함께
                        Button(s.badeum ? jiin.julIreum(s) : "\(jiin.julIreum(s)) (아직 받겠다고 안 하심)") {
                            GinGeup.shared.yocheong(.jiin, s)
                            dismiss()
                        }
                        .buttonStyle(KeunDanchu())
                    }
                }
            }
            .padding()
        }
        .sokHwamyeon("가족·지인 고르기")   // 2.86.0 / 2.56.0 (261016-I40 / 261016-A44, 이사장님 승인 2026-10-16) 제목(안드로이드와 같게)
        .task { if !(await jiin.bureogi()) { mot = true } }
    }
}

/// 가족·지인 명단 — 2.42.0 이음 번호로 등록(자봉 앱), 빼기. 예전 초대 주소 방식은 감춤(yetBangsik)
struct JiinMyeongdanView: View {
    @ObservedObject private var jiin = Jiin.shared
    @ObservedObject private var gg = GinGeup.shared   // 2.70.0 내 이름 칸
    @State private var ireum = ""
    @State private var tel = ""
    @State private var allim = ""
    @State private var saero: JiinSaram?
    @State private var saeroTel = ""
    // 2.86.0 / 2.56.0 (261016-I40 / 261016-A44, 이사장님 승인 2026-10-16) 이음 번호와 등록 살피기는 Jiin 이 쥠(화면을 떠나도 30분)
    @AccessibilityFocusState private var beonhoChojeom: Bool
    /// 예전 방식(초대 주소를 문자·카톡으로 보내기) — 지우지 않고 감춤(이사장님 2026-10-04 "주소를 보내 연결하는 건 어려운 일")
    private static let yetBangsik = false

    private var beonhoMal: String {
        let ttuim = jiin.beonho.map { String($0) }.joined(separator: " ")
        return "이음 번호 \(ttuim). 30분 안에 가족이나 지인에게 불러 주십시오. 그분이 자봉 앱의 그리기 탭, 긴급통화 받기, 가족·지인으로 받기에서 이 번호와 부르실 이름을 넣으시면 등록됩니다"
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if jiin.beonhoSalda {
                    Button(beonhoMal + " — 다시 듣기") { SoriEngine.shared.mal(beonhoMal + ".") }
                        .buttonStyle(KeunDanchu())
                        .accessibilityFocused($beonhoChojeom)
                    Button("새 이음 번호 받기") { beonhoBatgi() }
                        .buttonStyle(KeunDanchu())
                } else {
                    Button("이음 번호 받기 — 가족·지인이 자봉 앱에 넣을 세 자리") { beonhoBatgi() }
                        .buttonStyle(KeunDanchu())
                }
                if !allim.isEmpty { Text(allim).font(.title3) }
                if !jiin.deungrokAllim.isEmpty { Text(jiin.deungrokAllim).font(.title3) }
                // 2.70.0 받는 분 화면에 뜰 내 이름 — 설정 첫 화면에서 이리로 옮김(이사장님 승인 2026-10-14)
                TextField("받는 분 화면에 뜰 내 이름 — 긴급통화 때 보입니다", text: $gg.naIrum)
                    .textFieldStyle(.roundedBorder)
                    .font(.title3)
                    .submitLabel(.done)
                if JiinMyeongdanView.yetBangsik {
                    yetHwamyeon
                }
                // 2.86.0 / 2.56.0 (261016-I40 / 261016-A44, 이사장님 승인 2026-10-16) 몇 분인지와 열 분 한도
                if jiin.bulreoom {
                    Text("등록된 분 \(jiin.mokrok.count)분, 열 분까지").font(.title3)
                }
                if !jiin.mokrok.isEmpty {
                    Text("이름을 두드리시면 부르는 이름(별명)을 적거나 명단에서 빼실 수 있습니다. 위아래로 쓸어 명단에서 빼기를 고르셔도 됩니다.").font(.body)
                    Mokrok5(jiin.mokrok) { s in
                        // 2.86.0 / 2.56.0 (261016-I40 / 261016-A44, 이사장님 승인 2026-10-16) 이름을 두드리면 별명·빼기 화면
                        NavigationLink { JiinSaramView(s: s) } label: {
                            Text("\(jiin.julIreum(s)) — \(s.badeum ? "받음" : "아직 받겠다고 안 하심")")
                                .font(.title3)
                                .frame(maxWidth: .infinity, minHeight: 50, alignment: .leading)
                        }
                            .buttonStyle(KeunDanchu())
                            .accessibilityAction(named: "명단에서 빼기") {
                                Task { await jiin.jiugi(s) }
                                SoriEngine.shared.mal("\(s.name) 님을 명단에서 뺐습니다.")
                            }
                            .contextMenu {
                                Button("명단에서 빼기", role: .destructive) { Task { await jiin.jiugi(s) } }
                            }
                    }
                }
            }
            .padding()
        }
        .sokHwamyeon("가족·지인 명단")
        .task { await jiin.bureogi() }
    }

    /// 예전 초대 주소 방식 화면 — 감춰 둠
    @ViewBuilder private var yetHwamyeon: some View {
        if let s = saero {
            Text("\(s.name) 님을 만들었습니다. 아래 초대 주소를 그 분께 보내십시오.").font(.title3)
            ShareLink(item: Jiin.chodaeJuso(s.k)) { Text("다른 앱으로 초대 주소 보내기") }.buttonStyle(KeunDanchu())
        }
        TextField("이름", text: $ireum).textFieldStyle(.roundedBorder).font(.title3)
        TextField("전화번호 — 폰 안에만 담깁니다", text: $tel).textFieldStyle(.roundedBorder).font(.title3).keyboardType(.phonePad)
        Button("이 사람 만들기") { mandeulgi() }.buttonStyle(KeunDanchu())
    }

    /// 이음 번호 받기 — 받은 뒤 30분 동안 Jiin 이 5초마다 명단을 살펴 새로 등록하신 분을 알려 드림
    /// 2.86.0 / 2.56.0 (261016-I40 / 261016-A44, 이사장님 승인 2026-10-16) 열 분이 차 있으면 번호를 청하지 않고 알려 드림
    private func beonhoBatgi() {
        if jiin.bulreoom && jiin.mokrok.count >= Jiin.HANDO_SU {
            allim = Jiin.gadeukMal
            SoriEngine.shared.mal(Jiin.gadeukMal)
            return
        }
        allim = "이음 번호를 받고 있습니다."
        Task {
            let (b, e) = await jiin.ieumBeonho()
            await MainActor.run {
                guard let b = b else { allim = e; SoriEngine.shared.mal(e); return }
                jiin.beonhoNoki(b)
                allim = ""
                SoriEngine.shared.mal(beonhoMal + ".")
                beonhoChojeom = false
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { beonhoChojeom = true }
            }
        }
    }

    private func mandeulgi() {
        let nm = ireum.trimmingCharacters(in: .whitespaces)
        guard !nm.isEmpty else { allim = "이름을 적어 주십시오."; SoriEngine.shared.mal(allim); return }
        allim = "만들고 있습니다."
        let t = tel
        Task {
            let (s, k) = await jiin.mandeulgi(nm, t)
            await MainActor.run {
                if let s = s {
                    saero = s
                    saeroTel = t.filter { "0123456789+".contains($0) }
                    ireum = ""
                    tel = ""
                    allim = ""
                    SoriEngine.shared.mal("\(s.name) 님을 만들었습니다. 초대 주소를 보내십시오.")
                } else {
                    allim = k
                    SoriEngine.shared.mal(k)
                }
            }
        }
    }

    /// 문자 앱 열기 — 아이폰은 물음표가 아니라 앰퍼샌드(2026-08-27 여주에서 겪음)
    private func munja(_ s: JiinSaram, _ t: String) {
        let geul = "\(s.name)님, 제가 앞이 보이지 않을 때 도움을 청하면 이 주소로 알려 드립니다. 한 번만 열어서 받겠습니다를 눌러 주십시오. " + Jiin.chodaeJuso(s.k).absoluteString
        let body = geul.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        if let u = URL(string: "sms:\(t)&body=\(body)") { UIApplication.shared.open(u) }
    }
}

/// 2.86.0 / 2.56.0 (261016-I40 / 261016-A44, 이사장님 승인 2026-10-16) 등록된 한 분 — 부르는 이름(별명) 적기, 명단에서 빼기
/// 별명은 폰 안에만 담고 나스에는 보내지 않습니다. 말로 하기에서 이름 대신 별명으로 부르셔도 됩니다
struct JiinSaramView: View {
    let s: JiinSaram
    @ObservedObject private var jiin = Jiin.shared
    @Environment(\.dismiss) private var dismiss
    @State private var byeolGeul = ""
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(s.badeum ? "받겠다고 하셨습니다." : "아직 받겠다고 하지 않으셨습니다.")
                    .font(.title3)
                    .accessibilityFocused($chojeom)
                TextField("부르는 이름 — 별명, 쉼표로 나누어 여럿", text: $byeolGeul)
                    .textFieldStyle(.roundedBorder)
                    .font(.title3)
                    .submitLabel(.done)
                    .onSubmit { jeojang() }
                Button("별명 저장") { jeojang() }
                    .buttonStyle(KeunDanchu())
                Button("명단에서 빼기 — \(s.name) 님") {
                    Task { await jiin.jiugi(s) }
                    SoriEngine.shared.mal("\(s.name) 님을 명단에서 뺐습니다.")
                    dismiss()
                }
                .buttonStyle(KeunDanchu())
            }
            .padding()
        }
        .sokHwamyeon(s.name)
        .onAppear {
            byeolGeul = jiin.byeol(s.id).joined(separator: ", ")
            chojeom = false
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { chojeom = true }
        }
    }

    private func jeojang() {
        let l = Jiin.byeolPulgi(byeolGeul)
        jiin.byeolNoki(s.id, l)
        Girok.shared.namgi("jiin_byeol", ["n": l.count])
        if l.isEmpty {
            SoriEngine.shared.mal("별명을 지웠습니다. \(s.name)\(MalHagi.ro(s.name)) 부르시면 됩니다.")
        } else {
            let mal = l.joined(separator: ", ")
            SoriEngine.shared.mal("별명을 저장했습니다. \(mal)\(MalHagi.ro(l.last ?? mal)) 부르셔도 됩니다.")
        }
    }
}
