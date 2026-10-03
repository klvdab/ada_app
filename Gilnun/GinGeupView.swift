// 긴급통화서비스 화면 — 세 갈래, 한마디 먼저 남기기, 가족·지인 고르기.
// 2.4.1 (260928-6) 가족·지인 명단과 내 이름은 설계도대로 설정 탭에 둡니다(이사장님 지적). 명단이 비었을 때만 여기서 명단 화면을 곧장 엽니다.
// 요청 중·통화 중에는 맨 위 한 줄에 지금 형편과 "그만두기" 단추를 한 자리에.
import SwiftUI
import UIKit

struct GinGeupView: View {
    @ObservedObject private var g = GinGeup.shared
    @AccessibilityFocusState private var chojeom: Bool
    @AccessibilityFocusState private var neomChojeom: Bool

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
                case .tonghwa:
                    Button("\(g.geul) — 끊기") { g.geumanhagi() }
                        .buttonStyle(KeunDanchu())
                        .accessibilityFocused($chojeom)
                default:
                    Button("\(g.geul) — 요청 그만두기") { g.geumanhagi() }
                        .buttonStyle(KeunDanchu())
                        .accessibilityFocused($chojeom)
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
                        Button(s.badeum ? s.name : "\(s.name) (아직 받겠다고 안 하심)") {
                            GinGeup.shared.yocheong(.jiin, s)
                            dismiss()
                        }
                        .buttonStyle(KeunDanchu())
                    }
                }
            }
            .padding()
        }
        .sokHwamyeon("")
        .task { if !(await jiin.bureogi()) { mot = true } }
    }
}

/// 가족·지인 명단 — 2.42.0 이음 번호로 등록(자봉 앱), 빼기. 예전 초대 주소 방식은 감춤(yetBangsik)
struct JiinMyeongdanView: View {
    @ObservedObject private var jiin = Jiin.shared
    @State private var ireum = ""
    @State private var tel = ""
    @State private var allim = ""
    @State private var saero: JiinSaram?
    @State private var saeroTel = ""
    @State private var beonho = ""
    @State private var beonhoTtae = Date.distantPast
    @State private var bonIds: Set<String> = []
    @State private var salpim: Timer?
    @AccessibilityFocusState private var beonhoChojeom: Bool
    /// 예전 방식(초대 주소를 문자·카톡으로 보내기) — 지우지 않고 감춤(이사장님 2026-10-04 "주소를 보내 연결하는 건 어려운 일")
    private static let yetBangsik = false

    private var beonhoMal: String {
        let ttuim = beonho.map { String($0) }.joined(separator: " ")
        return "이음 번호 \(ttuim). 30분 안에 가족이나 지인에게 불러 주십시오. 그분이 자봉 앱의 봉사 탭, 긴급통화 받기, 가족·지인으로 받기에서 이 번호와 부르실 이름을 넣으시면 등록됩니다"
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if !beonho.isEmpty && Date().timeIntervalSince(beonhoTtae) < 1800 {
                    Button(beonhoMal + " — 다시 듣기") { SoriEngine.shared.mal(beonhoMal + ".") }
                        .buttonStyle(KeunDanchu())
                        .accessibilityFocused($beonhoChojeom)
                    Button("새 이음 번호 받기") { beonhoBatgi() }
                        .buttonStyle(KeunDanchu())
                } else {
                    Button("이음 번호 받기 — 가족·지인이 자봉 앱에 넣을 여섯 자리") { beonhoBatgi() }
                        .buttonStyle(KeunDanchu())
                }
                if !allim.isEmpty { Text(allim).font(.title3) }
                if JiinMyeongdanView.yetBangsik {
                    yetHwamyeon
                }
                if !jiin.mokrok.isEmpty {
                    Text("등록된 분 — 빼실 때는 이름에서 위아래로 쓸어 명단에서 빼기를 고르십시오.").font(.body)
                    Mokrok5(jiin.mokrok) { s in
                        Text("\(s.name) — \(s.badeum ? "받음" : "아직 받겠다고 안 하심")")
                            .font(.title3)
                            .frame(maxWidth: .infinity, minHeight: 50, alignment: .leading)
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
        .task { await jiin.bureogi(); bonIds = Set(jiin.mokrok.map { $0.id }) }
        .onDisappear { salpim?.invalidate(); salpim = nil }
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

    /// 이음 번호 받기 — 받은 뒤 30분 동안 5초마다 명단을 살펴 새로 등록하신 분을 알려 드림
    private func beonhoBatgi() {
        allim = "이음 번호를 받고 있습니다."
        Task {
            let (b, e) = await jiin.ieumBeonho()
            await MainActor.run {
                guard let b = b else { allim = e; SoriEngine.shared.mal(e); return }
                beonho = b; beonhoTtae = Date(); allim = ""
                bonIds = Set(jiin.mokrok.map { $0.id })
                SoriEngine.shared.mal(beonhoMal + ".")
                beonhoChojeom = false
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { beonhoChojeom = true }
                salpim?.invalidate()
                salpim = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { t in
                    if Date().timeIntervalSince(beonhoTtae) > 1800 { t.invalidate(); return }
                    Task {
                        guard await jiin.bureogi() else { return }
                        await MainActor.run {
                            let sae = jiin.mokrok.filter { !bonIds.contains($0.id) }
                            for p in sae {
                                SoriEngine.shared.mal("\(p.name) 님이 가족·지인으로 등록하셨습니다.")
                                Girok.shared.namgi("jiin_ieum_deungrok", [:])
                            }
                            if !sae.isEmpty { beonho = ""; t.invalidate(); allim = sae.map { "\($0.name) 님이 등록하셨습니다." }.joined(separator: " ") }
                            bonIds = Set(jiin.mokrok.map { $0.id })
                        }
                    }
                }
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
