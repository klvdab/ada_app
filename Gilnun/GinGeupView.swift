// 긴급통화서비스 화면 — 세 갈래, 한마디 먼저 남기기, 가족·지인 고르기.
// 2.4.1 (260928-6) 가족·지인 명단과 내 이름은 설계도대로 설정 탭에 둡니다(이사장님 지적). 명단이 비었을 때만 여기서 명단 화면을 곧장 엽니다.
// 요청 중·통화 중에는 맨 위 한 줄에 지금 형편과 "그만두기" 단추를 한 자리에.
import SwiftUI
import UIKit

struct GinGeupView: View {
    @ObservedObject private var g = GinGeup.shared
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                switch g.sangtae {
                case .eopseum:
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
                    if !g.geul.isEmpty { Text(g.geul).font(.title3) }
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
        .onAppear { chojeom = false; DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { chojeom = true } }   // 2.12.1 매번 첫 줄로
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

/// 가족·지인 명단 — 만들기, 초대 주소 보내기, 빼기
struct JiinMyeongdanView: View {
    @ObservedObject private var jiin = Jiin.shared
    @State private var ireum = ""
    @State private var tel = ""
    @State private var allim = ""
    @State private var saero: JiinSaram?
    @State private var saeroTel = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let s = saero {
                    Text("\(s.name) 님을 만들었습니다. 아래 초대 주소를 그 분께 보내십시오. 그 분이 한 번 열어 받겠습니다를 누르시면 등록이 끝납니다.")
                        .font(.title3)
                    if !saeroTel.isEmpty {
                        Button("문자로 초대 주소 보내기 — \(s.name) 님께") { munja(s, saeroTel) }
                            .buttonStyle(KeunDanchu())
                    }
                    ShareLink(item: Jiin.chodaeJuso(s.k),
                              message: Text("\(s.name)님, 제가 앞이 보이지 않을 때 도움을 청하면 이 주소로 알려 드립니다. 한 번만 열어서 받겠습니다를 눌러 주십시오.")) {
                        Text("다른 앱으로 초대 주소 보내기 — 카카오톡 등")
                    }
                    .buttonStyle(KeunDanchu())
                }
                TextField("이름", text: $ireum)
                    .textFieldStyle(.roundedBorder)
                    .font(.title3)
                TextField("전화번호 — 폰 안에만 담깁니다", text: $tel)
                    .textFieldStyle(.roundedBorder)
                    .font(.title3)
                    .keyboardType(.phonePad)
                Button("이 사람 만들기") { mandeulgi() }
                    .buttonStyle(KeunDanchu())
                if !allim.isEmpty { Text(allim).font(.title3) }
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
                            .accessibilityAction(named: "초대 주소 다시 보내기") {
                                let t = jiin.tel(s.id)
                                if !t.isEmpty { munja(s, t) } else { saero = s; saeroTel = "" }
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
