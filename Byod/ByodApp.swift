// BYOD 방송 아이폰 — 시작과 화면 (1.1.5판, 빌드 261010-BI1, 방송클, 이사장님 승인 2026-10-10 「아이폰용도 진행」)
// 안드로이드 BYOD 방송 1.1.5판(ByodActivity)과 같은 화면입니다.
// 첫 화면: 협회 로고 머리, 「BYOD 방송 시작」 큰 단추 하나, 그 바로 아래 「현장 점검」, 새 판이 있을 때만 업데이트 한 줄.
// 그 아래 상태 글은 바뀔 때만 한 번 알립니다(듣는 분 수는 10초에 한 번까지). 나머지는 「더 보기」 안에 둡니다.
// 도움말·접속 도구 화면은 맨 위와 맨 아래에 뒤로 단추를 두고, 보이스오버 두 손가락 문지르기로도 돌아옵니다.
// 1.2.0 (261014-BI3, 이사장님 승인 「1」, 방송클) 플린트 2 방식이 기본 — 더 보기에 방송 방식 바꾸기와 공유기 방송 서버 확인하기
// 1.3.0 (261014-BI4, 이사장님 승인 「1」, 방송클) 행사 이름·현장영상해설사 칸, 방송을 멈추면 녹음과 결과보고를 받아 배프에 올림, 더 보기에 지난 행사 기록
import SwiftUI
import UIKit

@main
struct ByodApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(.dark)
                .onAppear { ByodOllim.shared.sijak() }
        }
    }
}

// MARK: 꾸밈 — 법인 짙은 남색 바탕, 흰 단추에 노란 테, 큰 단추는 노랑(안드로이드판과 같은 색)

enum Saek {
    static let bada = Color(red: 18 / 255, green: 52 / 255, blue: 110 / 255)    // #12346E 화면 바탕
    static let nam = Color(red: 11 / 255, green: 33 / 255, blue: 76 / 255)      // 로고 머리 띠(바탕보다 짙게)
    static let norang = Color(red: 1, green: 204 / 255, blue: 0)                 // #FFCC00
    static let geom = Color(red: 17 / 255, green: 17 / 255, blue: 17 / 255)
    static let yeonhan = Color(red: 220 / 255, green: 230 / 255, blue: 245 / 255)
}

struct Danchu: View {
    let geul: String
    var keun = false
    var norang = false
    let f: () -> Void
    init(geul: String, keun: Bool = false, norang: Bool = false, f: @escaping () -> Void) {
        self.geul = geul; self.keun = keun; self.norang = norang; self.f = f
    }
    var body: some View {
        Button(action: f) {
            Text(geul)
                .font(keun ? .title.weight(.bold) : .title3.weight(.bold))
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, minHeight: keun ? 120 : 52)
                .padding(.vertical, keun ? 20 : 10)
                .padding(.horizontal, 14)
                .foregroundStyle(norang ? Saek.geom : Saek.bada)
                .background(RoundedRectangle(cornerRadius: 16).fill(norang ? Saek.norang : Color.white))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(norang ? Saek.geom : Saek.norang, lineWidth: 3))
        }
        .buttonStyle(.plain)
    }
}

struct Geul: View {
    let t: String
    var gulgeum = false
    var saek: Color = .white
    init(_ t: String, gulgeum: Bool = false, saek: Color = .white) { self.t = t; self.gulgeum = gulgeum; self.saek = saek }
    var body: some View {
        Text(t)
            .font(gulgeum ? .title3.weight(.bold) : .body)
            .foregroundStyle(saek)
            .lineSpacing(4)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct Kan: View {
    let ireum: String
    @Binding var gap: String
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(ireum).font(.body).foregroundStyle(Saek.yeonhan).accessibilityHidden(true)
            TextField(ireum, text: $gap)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .font(.title3)
                .padding(10)
                .foregroundStyle(Saek.geom)
                .background(RoundedRectangle(cornerRadius: 10).fill(Color.white))
                .accessibilityLabel(ireum)
        }
    }
}

// MARK: 화면 고르기

enum Hwamyeon { case cheot, doumal, jeopsok, haengsa, gyoyuk, live }   // 1.4.0 gyoyuk 교육 녹화

struct RootView: View {
    @State private var hwamyeon = Hwamyeon.cheot
    var body: some View {
        ZStack {
            Saek.bada.ignoresSafeArea()
            switch hwamyeon {
            case .cheot: CheotView(gagi: { hwamyeon = $0 })
            case .doumal: DoumalView(dwiro: { hwamyeon = .cheot })
            case .jeopsok: JeopsokView(dwiro: { hwamyeon = .cheot })
            case .haengsa: HaengsaView(dwiro: { hwamyeon = .cheot })   // 1.3.0
            case .gyoyuk: GyoyukView(dwiro: { hwamyeon = .cheot })   // 1.4.0
            case .live: GyoyukView(jemok: "라이브 현장영상해설 방송", juso: GyoyukPan.LIVE, dwiro: { hwamyeon = .cheot })   // 1.4.0
            }
        }
    }
}

// MARK: 첫 화면의 상태 글 — 바뀔 때만, 듣는 분 수만 바뀐 때는 10초에 한 번까지

final class SangtaeBogi: ObservableObject {
    @Published var geul = ""
    @Published var juso = ""
    @Published var yocheong = ""
    private var ttae = Date.distantPast
    private var kyeojim = false
    private var sojae = ""
    private var jalmot = ""
    private var yocheongSeq = -1
    private var cheoeum = true

    func gochigi(baro: Bool) {
        let b = Bang.shared
        let now = Date()
        let k = b.kyeojim, so = b.sojae, jm = b.jalmot
        let fl = Flint.flintInga
        let t = !k ? "방송이 꺼져 있습니다." + (fl ? " 방송 방식: 플린트 2 공유기." : " 방송 방식: 이 아이폰이 서버.") + (jm.isEmpty ? "" : " " + jm)
                   : "방송 중입니다. 듣는 분 \(b.deutnunSu)명. 소리 받는 곳: \(so)." + (fl && FlintBonaegi.shared.bonaenda ? " 공유기로 보내고 있습니다." : "") + (jm.isEmpty ? "" : " " + jm)
        let keunBakkwim = k != kyeojim || so != sojae || jm != jalmot
        if t != geul && (baro || keunBakkwim || now.timeIntervalSince(ttae) >= 10) {
            let allil = !cheoeum && !baro
            if keunBakkwim && !baro && !jm.isEmpty && jm != jalmot { Malsori.shared.mal(jm) }
            geul = t
            ttae = now
            kyeojim = k; sojae = so; jalmot = jm
            if allil { allyeo(t) }   // 낭독기에 바뀐 것만 한 번
        }
        cheoeum = false
        // 듣기 주소 — 방송 중에만
        if k {
            let j = fl ? Flint.JUSO : Juso.deutgiJuso()   // 와이파이에서 랜으로 바꾸거나 주소가 바뀌면 바로 따라감(1.2.0 플린트 2 방식은 공유기 주소)
            if j.isEmpty {
                if juso != "공유기에 붙지 않았습니다. 와이파이나 랜선을 확인하십시오." { juso = "공유기에 붙지 않았습니다. 와이파이나 랜선을 확인하십시오." }
            } else {
                if b.juso != j { b.juso = j }
                if juso != j { juso = j }
            }
        } else if !juso.isEmpty { juso = "" }
        // 도움 요청 — 새로 온 것만 한 번
        if b.yocheongSeq != yocheongSeq {
            let cheotBeon = yocheongSeq < 0
            yocheongSeq = b.yocheongSeq
            if !cheotBeon, k, let y = b.majimakYocheong() {
                let f = DateFormatter()
                f.locale = Locale(identifier: "ko_KR")
                f.dateFormat = "a h시 m분"
                let bh = y.beonho.isEmpty ? "번호 없음" : y.beonho + "번"
                let g = "새 알림: \(bh), \(Bang.jongryuMal(y.jongryu)) (\(f.string(from: y.ttae)))"
                yocheong = g
                if Malsori.shared.kyeojim { Malsori.shared.mal(g) } else { allyeo(g) }
            }
        }
    }
}

// MARK: 첫 화면

struct CheotView: View {
    let gagi: (Hwamyeon) -> Void
    @ObservedObject private var bang = Bang.shared
    @ObservedObject private var ollim = ByodOllim.shared
    @StateObject private var st = SangtaeBogi()
    @State private var meomchumDaegi = Date.distantPast
    @State private var meomchumMal = false
    @State private var deo = false
    @State private var malsoriOn = Malsori.shared.kyeojim
    @State private var flintOn = Flint.flintInga   // 1.2.0
    @AppStorage("haengsa_jemok") private var haengsaJemok = ""   // 1.3.0
    @AppStorage("haeseolsa") private var haeseolsa = ""
    @State private var tikSu = 0
    @State private var damgim = Jaryo.damgimMal
    @State private var jeomgeomGeul = ""
    @State private var jeomgeomBoim = false
    @State private var gigiGeul = ""
    @State private var gigiBoim = false
    @AccessibilityFocusState private var bangChojeom: Bool
    @AccessibilityFocusState private var deoChojeom: Bool
    private let tik = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    private var bangDanchuGeul: String {
        if meomchumMal { return "한 번 더 누르면 방송을 멈춥니다" }
        return bang.kyeojim ? "LVD 송출 멈추기" : "LVD 송출 시작"
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HyeophoeMeori(ireum: "LVD 송출")
                if flintOn {   // 1.3.0 행사 이름과 현장영상해설사(녹음 이름·다시듣기 제목·결과보고에 쓰임, 한 번 넣으면 기억)
                    Kan(ireum: "행사 이름 (비워 두면 날짜와 시각)", gap: $haengsaJemok)
                    Kan(ireum: "현장영상해설사 이름", gap: $haeseolsa)
                }
                Danchu(geul: bangDanchuGeul, keun: true, norang: true) { bangNureum() }
                    .accessibilityFocused($bangChojeom)
                Danchu(geul: "현장 점검") { hyeonjangJeomgeom() }
                Danchu(geul: "라이브 현장영상해설 방송 (인터넷)") { gagi(.live) }   // 1.4.0 한 폰 한 화면에서
                Danchu(geul: "현장영상해설교육 녹화") { gagi(.gyoyuk) }   // 1.4.0 (261016-BI6) 교육 녹화를 앱 안에서
                if let p = ollim.saePan {
                    Danchu(geul: "LVD 송출 새 판 \(p)이 나왔습니다. 두드리면 테스트플라이트에서 업데이트합니다") { ollim.yeolgi() }
                }
                Geul(st.geul, gulgeum: true)
                if !st.juso.isEmpty {
                    Geul(Flint.flintInga ? "듣기 화면(와이파이에 붙으면 저절로 뜸)" : "듣기 주소", saek: Saek.yeonhan).accessibilityHidden(true)
                    Text(st.juso).font(.title2.weight(.bold)).foregroundStyle(Saek.norang)
                        .textSelection(.enabled)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityLabel((Flint.flintInga ? "듣기 화면 " : "듣기 주소 ") + st.juso)
                }
                if !st.yocheong.isEmpty {
                    Geul(st.yocheong, gulgeum: true, saek: Saek.norang)
                }
                Danchu(geul: deo ? "더 보기 접기" : "더 보기") {
                    deo.toggle()
                    if deo { DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { deoChojeom = true } }
                }
                if deo {
                    Danchu(geul: "접속 도구: 엔에프시 태그와 큐알코드") { gagi(.jeopsok) }
                    Danchu(geul: "지난 행사 기록 (녹음과 결과보고)") { gagi(.haengsa) }   // 1.3.0
                        .accessibilityFocused($deoChojeom)
                    Danchu(geul: "공유기 방송 서버 확인하기") { flintHwagin() }   // 1.2.0
                    Danchu(geul: flintOn ? "방송 방식: 플린트 2 공유기. 누르면 이 아이폰이 서버인 예비 방식으로 바꿉니다" : "방송 방식: 이 아이폰이 서버(예비). 누르면 플린트 2 공유기 방식으로 바꿉니다") { bangsikBakkugi() }   // 1.2.0
                    Danchu(geul: "기기 점검: 소리 장치와 연결 보기") { gigiGeul = Sori.gigiJeomgeom(); gigiBoim = true }
                    Danchu(geul: "새로고침") { saerogochim() }
                    Danchu(geul: ollim.seoljeongGeul) { ollim.seoljeongNureum() }
                    Danchu(geul: malsoriOn ? "프로그램 말소리 끄기" : "프로그램 말소리 켜기") { malsoriBakkugi() }
                    Danchu(geul: "도움말") { gagi(.doumal) }
                    Geul("LVD 송출 아이폰 \(Bang.PAN)판, 빌드 \(Bang.BILD)", saek: Saek.yeonhan)
                    Geul(damgim, saek: Saek.yeonhan)
                }
            }
            .padding(20)
        }
        .accessibilityAction(.escape) {
            if deo { deo = false; DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { bangChojeom = true } }
            else { allyeo("첫 화면입니다. 홈으로 나가도 방송은 이어집니다.") }
        }
        .onAppear {
            HaengsaOlligi.shared.allim = { t in if Malsori.shared.kyeojim { Malsori.shared.mal(t) } else { allyeo(t) } }   // 1.3.0
            st.gochigi(baro: true)
            if Jaryo.pail("deut.html") == nil { Jaryo.batgi { _ in damgim = Jaryo.damgimMal } }   // 듣는 화면을 한 번도 받지 못했으면 조용히 받아 둠
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { bangChojeom = true }
        }
        .onReceive(tik) { _ in
            if meomchumMal && Date().timeIntervalSince(meomchumDaegi) > 5 { meomchumMal = false }
            st.gochigi(baro: false)
            // 1.3.0 배프에 아직 못 올린 행사가 있으면 1분마다 다시 올려 봄
            tikSu += 1
            if tikSu % 60 == 0 && !bang.kyeojim && HaengsaOlligi.nameum { HaengsaOlligi.shared.sijak(maegam: false) }
        }
        .alert("현장 점검", isPresented: $jeomgeomBoim) {
            Button("닫기", role: .cancel) { }
            Button("다시 점검") { DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { hyeonjangJeomgeom() } }
            Button("결과 복사") { UIPasteboard.general.string = jeomgeomGeul; malhagi("현장 점검 결과를 복사했습니다.") }
        } message: {
            Text(jeomgeomGeul)
        }
        .alert("기기 점검", isPresented: $gigiBoim) {
            Button("닫기", role: .cancel) { }
            Button("결과 복사") { UIPasteboard.general.string = gigiGeul; malhagi("기기 점검 결과를 복사했습니다.") }
        } message: {
            Text(gigiGeul)
        }
    }

    private func malhagi(_ t: String) { Malsori.shared.mal(t) }

    private func hyeonjangJeomgeom() {
        let t = Hyeonjang.geul()
        jeomgeomGeul = t
        jeomgeomBoim = true
        if let cheot = t.split(separator: "\n").first { malhagi(String(cheot)) }
    }

    private func bangNureum() {
        if bang.kyeojim {
            if !meomchumMal || Date().timeIntervalSince(meomchumDaegi) > 5 {
                meomchumDaegi = Date()
                meomchumMal = true
                allyeo("한 번 더 누르면 방송을 멈춥니다.")
                malhagi("한 번 더 누르면 방송을 멈춥니다.")
                return
            }
            meomchumMal = false
            BangIl.shared.kkeugi()
            malhagi("방송을 멈추었습니다.")
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { st.gochigi(baro: true) }
            return
        }
        BangIl.shared.kyeogi { r in
            malhagi(r)
            if !Bang.shared.kyeojim { allyeo(r) }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { st.gochigi(baro: true) }
        }
    }

    private func saerogochim() {
        allyeo("나스에서 듣는 화면과 대기 음악을 받고 있습니다.")
        ByodOllim.shared.salpigi()   // 새로고침 한 곳에서 새 판도 함께 살핌
        Jaryo.batgi { r in
            damgim = Jaryo.damgimMal
            if Malsori.shared.kyeojim { malhagi(r) } else { allyeo(r) }
        }
    }

    // 1.2.0 방송 방식 바꾸기 — 방송 중에는 바꾸지 않음
    private func bangsikBakkugi() {
        if bang.kyeojim { let t = "방송 중에는 방식을 바꾸지 않습니다. 방송을 멈춘 뒤 눌러 주십시오."; allyeo(t); malhagi(t); return }
        Flint.flintInga = !Flint.flintInga
        flintOn = Flint.flintInga
        let t = flintOn ? "플린트 2 공유기 방식으로 바꾸었습니다." : "이 아이폰이 서버인 예비 방식으로 바꾸었습니다."
        allyeo(t); malhagi(t)
        st.gochigi(baro: true)
    }

    // 1.2.0 공유기 방송 서버에 판·듣는 분 수를 물음(준비하기는 안드로이드 BYOD 앱에서)
    private func flintHwagin() {
        allyeo("공유기 방송 서버에 묻고 있습니다.")
        Flint.mutgi("/status") { j in
            DispatchQueue.main.async {
                let t: String
                if let j = j {
                    t = "공유기 방송 서버가 답합니다. \(j["pan"] as? String ?? "")판, 듣는 분 \(j["n"] as? Int ?? 0)명. " +
                        ((j["mp3"] as? Bool ?? false) ? "화면을 잠가도 이어지는 소리를 쓸 수 있습니다." : "늦음이 가장 적은 소리만 씁니다.")
                } else {
                    t = "공유기 방송 서버가 답하지 않습니다. 이 아이폰이 와이파이 \(Flint.SSID) 에 붙었는지 보십시오. 새 공유기는 안드로이드 LVD 송출 앱의 플린트 2 준비하기로 먼저 준비합니다."
                }
                if Malsori.shared.kyeojim { malhagi(t) } else { allyeo(t) }
                jeomgeomGeul = t; jeomgeomBoim = true
            }
        }
    }

    private func malsoriBakkugi() {
        let on = !malsoriOn
        Malsori.shared.kyeojim = on
        malsoriOn = on
        allyeo(on ? "프로그램 말소리를 켰습니다." : "프로그램 말소리를 껐습니다.")
    }
}

// MARK: 도움말 화면 — 다섯 개씩, 아래에 더 보기, 그 아래 이전 보기

struct DoumalView: View {
    let dwiro: () -> Void
    @State private var chatgiMal = ""
    @State private var jjok = 0
    @State private var yeollin: Set<String> = []
    @State private var mok: [Doumal.Hang] = Doumal.modu
    @AccessibilityFocusState private var wiChojeom: Bool
    @AccessibilityFocusState private var cheotChojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Danchu(geul: "뒤로") { dwiro() }.accessibilityFocused($wiChojeom)
                TextField("도움말 찾기 (낱말을 넣고 엔터)", text: $chatgiMal)
                    .submitLabel(.search)
                    .onSubmit {
                        mok = Doumal.chatgi(chatgiMal)
                        jjok = 0
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { cheotChojeom = true }
                    }
                    .font(.title3)
                    .padding(10)
                    .foregroundStyle(Saek.geom)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color.white))
                    .accessibilityLabel("도움말 찾기")
                if mok.isEmpty {
                    Geul("찾는 낱말이 든 도움말이 없습니다. 다른 낱말로 찾아보십시오.")
                        .accessibilityFocused($cheotChojeom)
                } else {
                    let sijak = jjok * 5
                    let ichjjok = Array(mok[sijak..<min(sijak + 5, mok.count)])
                    ForEach(Array(ichjjok.enumerated()), id: \.element.id) { i, h in
                        if i == 0 {
                            Danchu(geul: h.jemok) { yeoldat(h.jemok) }.accessibilityFocused($cheotChojeom)
                        } else {
                            Danchu(geul: h.jemok) { yeoldat(h.jemok) }
                        }
                        if yeollin.contains(h.jemok) { Geul(h.naeyong) }
                    }
                    if sijak + 5 < mok.count {
                        Danchu(geul: "더 보기") { jjok += 1; DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { cheotChojeom = true } }
                    }
                    if jjok > 0 {
                        Danchu(geul: "이전 보기") { jjok -= 1; DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { cheotChojeom = true } }
                    }
                }
                Danchu(geul: "뒤로") { dwiro() }
            }
            .padding(20)
        }
        .accessibilityAction(.escape) { dwiro() }
        .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { wiChojeom = true } }
    }

    private func yeoldat(_ j: String) {
        if yeollin.contains(j) { yeollin.remove(j) } else { yeollin.insert(j) }
    }
}

// MARK: 1.3.0 지난 행사 기록 — 다섯 개씩, 이름을 누르면 결과보고와 녹음 듣기

struct HaengsaView: View {
    let dwiro: () -> Void
    @State private var mok: [HaengsaOlligi.Jul] = HaengsaOlligi.mokrok()
    @State private var jjok = 0
    @State private var yeollin: Set<String> = []
    @State private var deutneun = ""
    @AccessibilityFocusState private var wiChojeom: Bool
    @AccessibilityFocusState private var cheotChojeom: Bool

    private func nalMal(_ d: Date) -> String {
        let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "M월 d일 a h시 m분"; return f.string(from: d)
    }
    private func gileeMal(_ c: Double) -> String { let n = Int(c); return n >= 60 ? "\(n / 60)분" : "\(n)초" }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Danchu(geul: "뒤로") { NokeumDeutgi.shared.meomchum(); dwiro() }.accessibilityFocused($wiChojeom)
                if mok.isEmpty {
                    Geul("아직 담아 둔 행사가 없습니다. 플린트 2 방식으로 방송을 멈추면 녹음과 결과보고가 여기에 담깁니다.")
                } else {
                    if mok.contains(where: { !$0.olim }) {
                        Danchu(geul: "배프에 아직 못 올린 것 지금 올리기") { HaengsaOlligi.shared.sijak(maegam: false); allyeo("배프에 올리고 있습니다.") }
                    }
                    let sijak = jjok * 5
                    let ichjjok = Array(mok[min(sijak, mok.count)..<min(sijak + 5, mok.count)])
                    ForEach(Array(ichjjok.enumerated()), id: \.element.id) { i, x in
                        let geul = "\(x.jemok), \(nalMal(x.sijak)), \(gileeMal(x.gilee)), " + (x.olim ? "배프에 올림" : "아직 못 올림")
                        if i == 0 {
                            Danchu(geul: geul) { yeoldat(x.sid) }.accessibilityFocused($cheotChojeom)
                        } else {
                            Danchu(geul: geul) { yeoldat(x.sid) }
                        }
                        if yeollin.contains(x.sid) {
                            Geul(HaengsaOlligi.bogo(x.sid))
                            Danchu(geul: deutneun == x.sid ? "녹음 멈추기" : "녹음 듣기") {
                                if deutneun == x.sid { NokeumDeutgi.shared.meomchum(); deutneun = ""; allyeo("녹음을 멈추었습니다.") }
                                else if NokeumDeutgi.shared.teulgi(x.sid) { deutneun = x.sid }
                                else { allyeo("녹음을 틀지 못했습니다.") }
                            }
                        }
                    }
                    if sijak + 5 < mok.count {
                        Danchu(geul: "더 보기") { jjok += 1; DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { cheotChojeom = true } }
                    }
                    if jjok > 0 {
                        Danchu(geul: "이전 보기") { jjok -= 1; DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { cheotChojeom = true } }
                    }
                }
                Danchu(geul: "뒤로") { NokeumDeutgi.shared.meomchum(); dwiro() }
            }
            .padding(20)
        }
        .accessibilityAction(.escape) { NokeumDeutgi.shared.meomchum(); dwiro() }
        .onAppear {
            mok = HaengsaOlligi.mokrok()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { if mok.isEmpty { wiChojeom = true } else { cheotChojeom = true } }
        }
    }

    private func yeoldat(_ sid: String) {
        if yeollin.contains(sid) { yeollin.remove(sid) } else { yeollin.insert(sid) }
    }
}
