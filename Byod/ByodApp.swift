// BYOD 방송 아이폰 — 시작과 화면 (1.1.5판, 빌드 261010-BI1, 방송클, 이사장님 승인 2026-10-10 「아이폰용도 진행」)
// 안드로이드 BYOD 방송 1.1.5판(ByodActivity)과 같은 화면입니다.
// 첫 화면: 협회 로고 머리, 「BYOD 방송 시작」 큰 단추 하나, 그 바로 아래 「현장 점검」, 새 판이 있을 때만 업데이트 한 줄.
// 그 아래 상태 글은 바뀔 때만 한 번 알립니다(듣는 분 수는 10초에 한 번까지). 나머지는 「더 보기」 안에 둡니다.
// 도움말·접속 도구 화면은 맨 위와 맨 아래에 뒤로 단추를 두고, 보이스오버 두 손가락 문지르기로도 돌아옵니다.
// 1.2.0 (261014-BI3, 이사장님 승인 「1」, 방송클) 플린트 2 방식이 기본 — 더 보기에 방송 방식 바꾸기와 공유기 방송 서버 확인하기
// 1.3.0 (261014-BI4, 이사장님 승인 「1」, 방송클) 행사 이름·현장영상해설사 칸, 방송을 멈추면 녹음과 결과보고를 받아 배프에 올림, 더 보기에 지난 행사 기록
// 1.5.0 (261018-BI7, 이사장님 승인 「1」, 방클) LVD 방송 정리 — 첫 화면 차례: 행사 이름·현장영상해설사 이름·전화번호 칸, 현장 방송 시작(인터넷 방송도 함께), 상태 두 줄,
//   현장 점검, 인터넷 방송만 하기, TV해설방송 하기, 현장영상해설교육 녹화, 새 판, 더 보기(함께 나가는 인터넷 방송 켜고 끄기 더함)
// 1.9.0 (261024-BI11, 새방송클, 대조표 송출 21·22·23번) 노트북판 현장 기능 — 현장 점검 아래 「피디 화면 열기」(사파리로 이 아이폰의 피디 화면)와
//   「관계자 붙이기 큐알 보기」(크게), 더 보기에 피디 번호 한 줄, 피디 화면의 긴급 안내도 첫 화면 한 줄로 바뀔 때만 한 번 알림
import SwiftUI
import UIKit

@main
struct ByodApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(.dark)
                .onAppear { ByodOllim.shared.sijak(); ByodGirok.shared.sijak() }   // 1.8.0 이용 기록
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
    var pan: UIKeyboardType = .default   // 1.5.0 전화번호 칸은 숫자 자판
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(ireum).font(.body).foregroundStyle(Saek.yeonhan).accessibilityHidden(true)
            TextField(ireum, text: $gap)
                .keyboardType(pan)
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

enum Hwamyeon { case cheot, doumal, jeopsok, haengsa, gyoyuk, live, tv, gwanQr }   // 1.4.0 gyoyuk 교육 녹화, 1.5.0 tv TV해설방송 하기, 1.9.0 gwanQr 관계자 붙이기 큐알

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
            case .live: GyoyukView(jemok: "인터넷 방송", juso: GyoyukPan.LIVE, dwiro: { hwamyeon = .cheot })   // 1.4.0, 1.5.0 이름
            case .tv: GyoyukView(jemok: "TV해설방송", juso: GyoyukPan.TV, dwiro: { hwamyeon = .cheot })   // 1.5.0
            case .gwanQr: GwanQrView(dwiro: { hwamyeon = .cheot })   // 1.9.0
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
    private var gingeupSeq = -1   // 1.9.0
    private var cheoeum = true
    private var inteonetJul = ""   // 1.5.0

    func gochigi(baro: Bool) {
        let b = Bang.shared
        let now = Date()
        let k = b.kyeojim, so = b.sojae, jm = b.jalmot
        let fl = Flint.flintInga
        let t = !k ? "BYOD 방송이 꺼져 있습니다." + " 공유기: " + Flint.gongyugiIreum + "." + (jm.isEmpty ? "" : " " + jm)
                   : "BYOD 방송 중입니다. 듣는 분 \(b.deutnunSu)명. 소리 받는 곳: \(so)." + (fl && FlintBonaegi.shared.bonaenda ? " 공유기로 보내고 있습니다." : "") + (jm.isEmpty ? "" : " " + jm)
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
        // 1.5.0 인터넷 방송 한 줄 — 바뀔 때만 한 번(시작·끝의 빈 글은 알리지 않음)
        if b.inteonet != inteonetJul {
            inteonetJul = b.inteonet
            if !inteonetJul.isEmpty && !baro { if Malsori.shared.kyeojim { Malsori.shared.mal(inteonetJul) } else { allyeo(inteonetJul) } }
        }
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
        // 1.9.0 (261024-BI11) 피디 화면의 긴급 안내 — 내거나 끝낼 때만 한 번
        if b.gingeupSeq != gingeupSeq {
            let cheotBeon = gingeupSeq < 0
            gingeupSeq = b.gingeupSeq
            if !cheotBeon, k {
                let g = b.gingeup.isEmpty ? "긴급 안내를 끝냈습니다." : "긴급 안내를 냈습니다: " + b.gingeup
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
    @AppStorage("haeseolsa_phone") private var haeseolsaPhone = ""   // 1.5.0 인터넷 방송에 씀(협회 명단 번호)
    @State private var hamkkeOn = InteonetBonaegi.hamkke   // 1.5.0
    @State private var keugiOn = JadongKeugi.on   // 1.9.2 소리 자동 맞춤
    @State private var opusOn = OpusSori.on   // 1.10.0 소리 방식 오푸스(시험)
    @State private var tikSu = 0
    @State private var damgim = Jaryo.damgimMal
    @State private var jeomgeomGeul = ""
    @State private var jeomgeomBoim = false
    @State private var gigiGeul = ""
    @State private var gigiBoim = false
    @State private var gongyugiGeul = ""      // 1.9.0 공유기 확인 창
    @State private var gongyugiBoim = false
    @State private var salpineunJung = false
    @AppStorage("yeongsang_nokhwa") private var yeongsangOn = false   // 1.9.0 (261024-BI11) 무대 영상 녹화
    @State private var yeongsangJul = ""
    @AccessibilityFocusState private var bangChojeom: Bool
    @AccessibilityFocusState private var deoChojeom: Bool
    private let tik = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    private var bangDanchuGeul: String {
        if meomchumMal { return "한 번 더 누르면 방송을 멈춥니다" }
        return bang.kyeojim ? "BYOD 방송 멈추기" : "BYOD 방송 시작"
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HyeophoeMeori(ireum: "LVD 송출")
                // 1.3.0 행사 이름과 현장영상해설사(녹음 이름·다시듣기 제목·결과보고에 쓰임, 한 번 넣으면 기억), 1.5.0 두 방식 모두·전화번호(인터넷 방송)
                Kan(ireum: "행사 이름 (비워 두면 날짜와 시각)", gap: $haengsaJemok)
                Kan(ireum: "현장영상해설사 이름", gap: $haeseolsa)
                Kan(ireum: "현장영상해설사 전화번호 (인터넷 방송에 꼭 필요합니다)", gap: $haeseolsaPhone, pan: .phonePad)
                // 1.9.0 (261024-BI11) 공유기는 방송을 시작할 때 앱이 스스로 알아냄 — 고르기 단추는 더 보기로(직접 고를 때만)
                Danchu(geul: bangDanchuGeul, keun: true, norang: true) { bangNureum() }
                    .accessibilityFocused($bangChojeom)
                // 1.9.0 (261024-BI11) 무대 영상 녹화 — 켜 두면 BYOD 방송을 시작할 때 뒤 카메라로 무대를 함께 녹화
                Danchu(geul: yeongsangOn ? "무대 영상 녹화: 켜짐. 누르면 끕니다" : "무대 영상 녹화: 꺼짐. 누르면 켭니다") { yeongsangBakkugi() }
                Geul(st.geul, gulgeum: true)
                if !yeongsangJul.isEmpty { Geul(yeongsangJul) }   // 1.9.0 (261024-BI11) 무대 영상 녹화 한 줄
                if !bang.inteonet.isEmpty { Geul(bang.inteonet) }   // 1.5.0 함께 나가는 인터넷 방송
                else if !bang.kyeojim && hamkkeOn && haeseolsaPhone.filter({ $0.isNumber }).count < 9 {   // 1.9.0 방송 전에도 분명히(대조표 송출 3번)
                    Geul("인터넷 방송: 현장영상해설사 전화번호가 비어 있어, 이대로면 BYOD 방송만 나가고 인터넷 방송은 나가지 않습니다. 위 전화번호 칸에 협회 명단의 번호를 넣으십시오.", saek: Saek.norang)
                }
                Danchu(geul: "현장 점검") { hyeonjangJeomgeom() }
                // 1.9.0 (261024-BI11) 노트북판 현장 기능 — 피디 화면과 관계자 붙이기(아이피타임 방식, 방송 중에)
                Danchu(geul: "피디 화면 열기") { pdHwamyeonYeolgi() }
                Danchu(geul: "관계자 붙이기 큐알 보기") { gwanQrBogi() }
                Danchu(geul: "인터넷 방송만 하기 (공유기 없이)") { gagi(.live) }   // 1.4.0 한 폰 한 화면에서, 1.5.0 이름
                Danchu(geul: "TV해설방송 하기") { gagi(.tv) }   // 1.5.0
                Danchu(geul: "현장영상해설교육 녹화") { gagi(.gyoyuk) }   // 1.4.0 (261016-BI6) 교육 녹화를 앱 안에서
                if let p = ollim.saePan {
                    Danchu(geul: "LVD 송출 새 판 \(p)이 나왔습니다. 두드리면 테스트플라이트에서 업데이트합니다") { ollim.yeolgi() }
                }
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
                    Danchu(geul: (flintOn ? "공유기 직접 고르기: 지금 플린트 2. 누르면 아이피타임으로 바꿉니다" : "공유기 직접 고르기: 지금 아이피타임. 누르면 플린트 2로 바꿉니다") + "(보통은 방송을 시작할 때 앱이 스스로 알아냅니다)") { bangsikBakkugi() }   // 1.9.0
                    Danchu(geul: "접속 도구: 엔에프시 태그와 큐알코드") { gagi(.jeopsok) }
                    Danchu(geul: "지난 행사 기록 (녹음과 결과보고)") { gagi(.haengsa) }   // 1.3.0
                        .accessibilityFocused($deoChojeom)
                    Danchu(geul: "BYOD 방송 서버 확인하기") { flintHwagin() }   // 1.2.0
                    Danchu(geul: hamkkeOn ? "BYOD 방송과 함께 인터넷 방송: 켜짐. 누르면 끕니다" : "BYOD 방송과 함께 인터넷 방송: 꺼짐. 누르면 켭니다") { hamkkeBakkugi() }   // 1.5.0
                    Danchu(geul: keugiOn ? "소리 자동 맞춤(작은 해설 소리 키우기): 켜짐. 누르면 끕니다" : "소리 자동 맞춤(작은 해설 소리 키우기): 꺼짐. 누르면 켭니다") { keugiBakkugi() }   // 1.9.2
                    Danchu(geul: opusOn ? "소리 방식 오푸스(시험): 켜짐. 누르면 끕니다" : "소리 방식 오푸스(시험): 꺼짐. 누르면 켭니다") { opusBakkugi() }   // 1.10.0
                    Danchu(geul: "기기 점검: 소리 장치와 연결 보기") { gigiGeul = Sori.gigiJeomgeom(); gigiBoim = true }
                    Danchu(geul: "새로고침") { saerogochim() }
                    Danchu(geul: ollim.seoljeongGeul) { ollim.seoljeongNureum() }
                    Danchu(geul: malsoriOn ? "프로그램 말소리 끄기" : "프로그램 말소리 켜기") { malsoriBakkugi() }
                    Danchu(geul: "도움말") { gagi(.doumal) }
                    Geul("피디 번호 \(HyeonjangGil.shared.pdKey)", saek: Saek.yeonhan)   // 1.9.0 (261024-BI11) 다른 폰에서 피디 화면을 열 때 씀
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
            if Jaryo.pail("deut.html") == nil || Jaryo.pail("pd.html") == nil { Jaryo.batgi { _ in damgim = Jaryo.damgimMal } }   // 듣는 화면(1.9.0 피디 화면)을 한 번도 받지 못했으면 조용히 받아 둠
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { bangChojeom = true }
        }
        .onReceive(tik) { _ in
            if meomchumMal && Date().timeIntervalSince(meomchumDaegi) > 5 { meomchumMal = false }
            st.gochigi(baro: false)
            // 1.3.0 배프에 아직 못 올린 행사가 있으면 1분마다 다시 올려 봄
            tikSu += 1
            if tikSu % 60 == 0 && !bang.kyeojim && (HaengsaOlligi.nameum || !GamchumDaegi.mokrok.isEmpty) { HaengsaOlligi.shared.sijak(maegam: false) }   // 1.5.0 감출 인터넷 녹음도
            // 1.9.0 (261024-BI11) 무대 영상 녹화 — 한 줄은 바뀔 때만 고침(읽어 주지는 않음, 문제는 녹화 쪽이 한 번 알림), 못 올린 영상은 1분마다 다시
            let yj = YeongsangNokhwa.shared.sangtaeMal
            if yj != yeongsangJul { yeongsangJul = yj }
            if tikSu % 60 == 30 && !bang.kyeojim && YeongsangOlligi.nameum { YeongsangOlligi.shared.sijak() }
        }
        .alert("현장 점검", isPresented: $jeomgeomBoim) {
            Button("닫기", role: .cancel) { }
            Button("다시 점검") { DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { hyeonjangJeomgeom() } }
            Button("결과 복사") { UIPasteboard.general.string = jeomgeomGeul; malhagi("현장 점검 결과를 복사했습니다.") }
        } message: {
            Text(jeomgeomGeul)
        }
        .alert("공유기 확인", isPresented: $gongyugiBoim) {   // 1.9.0
            Button("닫기", role: .cancel) { }
            Button("그래도 시작") { sijakhagi() }
            Button("설정 열기") { if let u = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(u) } }
        } message: {
            Text(gongyugiGeul)
        }
        .alert("기기 점검", isPresented: $gigiBoim) {
            Button("닫기", role: .cancel) { }
            Button("결과 복사") { UIPasteboard.general.string = gigiGeul; malhagi("기기 점검 결과를 복사했습니다.") }
        } message: {
            Text(gigiGeul)
        }
    }

    private func malhagi(_ t: String) { Malsori.shared.mal(t) }

    // 1.9.0 (261024-BI11) 무대 영상 녹화 켜고 끄기 — 켤 때 카메라 허락을 미리 물음. 방송 중이면 바로 시작·멈춤
    private func yeongsangBakkugi() {
        yeongsangOn.toggle()
        if yeongsangOn {
            YeongsangNokhwa.heorakMutgi { ok in
                if !ok {
                    malhagi("카메라 허락이 없어 무대 영상을 녹화할 수 없습니다. 아이폰 설정, LVD 송출, 카메라를 켜 주십시오.")
                    return
                }
                if bang.kyeojim { BangIl.shared.yeongsangSijak(); malhagi("무대 영상 녹화를 시작합니다. 녹화 동안 화면을 켜 두십시오.") }
                else { malhagi("무대 영상 녹화를 켰습니다. BYOD 방송을 시작하면 뒤 카메라로 함께 녹화합니다. 녹화 동안 화면을 켜 두십시오.") }
            }
        } else {
            if bang.kyeojim { YeongsangNokhwa.shared.kkeugi() }
            malhagi(bang.kyeojim ? "무대 영상 녹화를 멈추었습니다. BYOD 방송은 그대로 이어집니다." : "무대 영상 녹화를 껐습니다.")
        }
    }

    // 1.9.0 (261024-BI11) 피디 화면 — 아이피타임 방식에서 이 아이폰이 방송 서버일 때, 사파리로 http://127.0.0.1:8080/pd?k=피디 번호
    /// 피디 화면을 열 수 있는가 — 안 되면 까닭 한 줄
    private func hyeonjangMotham() -> String? {
        if Flint.flintInga { return "피디 화면과 관계자 붙이기는 아이피타임 방식에서 이 아이폰이 방송 서버일 때 씁니다. 플린트 2 방식에서는 안드로이드 LVD 송출 앱에서 여십시오." }
        if !bang.kyeojim || !Seobeo.shared.dolgo { return "피디 화면과 관계자 붙이기는 BYOD 방송을 시작한 뒤에 쓸 수 있습니다. 먼저 BYOD 방송 시작을 누르십시오." }
        return nil
    }

    private func pdHwamyeonYeolgi() {
        if let m = hyeonjangMotham() { allyeo(m); malhagi(m); return }
        guard let u = URL(string: "http://127.0.0.1:\(Bang.PORT)/pd?k=\(HyeonjangGil.shared.pdKey)") else { return }
        let ap = Jaryo.pail("pd.html") == nil ? "이 아이폰에 피디 화면 파일이 아직 없어 받는 법만 보입니다. 인터넷이 되는 곳에서 더 보기의 새로고침을 누르십시오. " : ""
        let t = ap + "사파리에서 피디 화면을 엽니다. 피디 번호 \(HyeonjangGil.shared.pdKey). 방송은 그대로 이어집니다. 무대 카메라와 관계자 무전은 아이폰 방송 서버에서는 열리지 않습니다."
        malhagi(t)
        UIApplication.shared.open(u)
    }

    private func gwanQrBogi() {
        if let m = hyeonjangMotham() { allyeo(m); malhagi(m); return }
        gagi(.gwanQr)
    }

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
        gongyugiSalpigo()
    }

    // 1.9.0 (261024-BI11, 이사장님 지시 2026-10-23 「직원이 헷갈려 한다, 더 간단하게」)
    // ① 붙은 공유기를 앱이 스스로 알아냄(플린트 2 방송 서버가 답하면 플린트 2, 아니면 아이피타임)
    // ② 방송 공유기가 아닌 와이파이(사무실 와이파이 등)에 붙어 있으면 시작 전에 분명히 알리고 묻습니다
    private func gongyugiSalpigo() {
        if salpineunJung { return }
        salpineunJung = true
        allyeo("붙은 공유기를 살피고 있습니다.")
        Flint.mutgi("/status") { j in
            DispatchQueue.main.async {
                salpineunJung = false
                let flintDap = j != nil
                Flint.flintInga = flintDap
                flintOn = flintDap
                let juso = Juso.deutgiJuso()
                let host = juso.replacingOccurrences(of: "http://", with: "").components(separatedBy: ":").first ?? ""
                var munje: String? = nil
                if !flintDap {
                    if juso.isEmpty { munje = "이 아이폰이 어느 공유기에도 붙어 있지 않습니다. 방송 공유기의 와이파이에 붙이거나 랜 어댑터와 랜선으로 공유기에 이으십시오." }
                    else if host.hasPrefix("192.168.8.") { munje = "플린트 2 공유기로 보이는데 방송 서버가 답하지 않습니다. 처음 쓰는 플린트 2면 안드로이드 LVD 송출 앱의 플린트 2 준비하기를 먼저 하십시오." }
                    else if !host.hasPrefix("192.168.") { munje = "이 아이폰이 방송 공유기가 아닌 와이파이에 붙어 있는 것 같습니다. 지금 주소는 \(host) 입니다. 아이피타임 방송 공유기에 붙으면 주소가 192.168 로 시작합니다. 이대로 시작하면 듣는 분이 방송을 찾지 못합니다." }
                }
                if let m = munje { gongyugiGeul = m; gongyugiBoim = true; malhagi(m); return }
                sijakhagi()
            }
        }
    }

    private func sijakhagi() {
        BangIl.shared.kyeogi { r in
            let itJul = (InteonetBonaegi.hamkke && InteonetBonaegi.jeonhwa.count < 9) ? " 다만 현장영상해설사 전화번호가 비어 있어 인터넷 방송은 나가지 않습니다." : ""
            malhagi(r + (Bang.shared.kyeojim ? " 공유기: " + Flint.gongyugiIreum + "." + itJul : ""))
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

    // 1.2.0 방송 방식 바꾸기 — 방송 중에는 바꾸지 않음. 1.7.0 「공유기: 아이피타임 / 플린트 2」
    private func bangsikBakkugi() {
        if bang.kyeojim { let t = "방송 중에는 공유기를 바꾸지 않습니다. 방송을 멈춘 뒤 눌러 주십시오."; allyeo(t); malhagi(t); return }
        Flint.flintInga = !Flint.flintInga
        flintOn = Flint.flintInga
        let t = flintOn ? "플린트 2로 바꾸었습니다." : "아이피타임으로 바꾸었습니다. 이 아이폰을 랜 어댑터와 랜선으로 아이피타임 공유기에 잇거나 아이피타임 와이파이에 붙이십시오. 듣는 분은 와이파이에 붙은 뒤 듣기 주소 엔에프시 태그나 큐알로 듣습니다."
        allyeo(t); malhagi(t)
        st.gochigi(baro: true)
    }

    // 1.2.0 공유기 방송 서버에 판·듣는 분 수를 물음(준비하기는 안드로이드 BYOD 앱에서)
    private func flintHwagin() {
        allyeo("BYOD 방송 서버에 묻고 있습니다.")
        Flint.mutgi("/status") { j in
            DispatchQueue.main.async {
                let t: String
                if let j = j {
                    t = "BYOD 방송 서버가 답합니다. \(j["pan"] as? String ?? "")판, 듣는 분 \(j["n"] as? Int ?? 0)명. " +
                        ((j["mp3"] as? Bool ?? false) ? "화면을 잠가도 이어지는 소리를 쓸 수 있습니다." : "늦음이 가장 적은 소리만 씁니다.")
                } else {
                    t = "BYOD 방송 서버가 답하지 않습니다. 이 아이폰이 와이파이 \(Flint.SSID) 에 붙었는지 보십시오. 새 공유기는 안드로이드 LVD 송출 앱의 플린트 2 준비하기로 먼저 준비합니다."
                }
                if Malsori.shared.kyeojim { malhagi(t) } else { allyeo(t) }
                jeomgeomGeul = t; jeomgeomBoim = true
            }
        }
    }

    // 1.5.0 현장 방송과 함께 인터넷 방송 켜고 끄기 — 다음 방송부터
    private func hamkkeBakkugi() {
        let on = !hamkkeOn
        InteonetBonaegi.hamkke = on
        hamkkeOn = on
        let ap: String = on ? "BYOD 방송을 켜면 인터넷 방송도 함께 나갑니다." : "BYOD 방송만 나갑니다. 인터넷 방송은 함께 나가지 않습니다."
        let dwi: String = bang.kyeojim ? " 다음 방송부터 적용됩니다." : ""
        let t = ap + dwi
        allyeo(t); malhagi(t)
    }

    // 1.9.2 (261026-BI13) 소리 자동 맞춤 켜고 끄기 — 방송 중에도 곧바로 적용
    private func keugiBakkugi() {
        let on = !keugiOn
        JadongKeugi.on = on
        JadongKeugi.cheoeum()
        keugiOn = on
        let t = on ? "소리 자동 맞춤을 켰습니다. 들어오는 해설 소리가 작으면 앱이 최대 8배까지 키워 내보냅니다." : "소리 자동 맞춤을 껐습니다. 들어오는 소리를 그대로 내보냅니다."
        allyeo(t); malhagi(t)
    }

    // 1.10.0 (261026-BI14) 소리 방식 오푸스(시험) 켜고 끄기 — 처음값 꺼짐, 다음 방송부터. 지금 방식(뮤)은 늘 함께 나감
    private func opusBakkugi() {
        let on = !opusOn
        OpusSori.on = on
        opusOn = on
        let t = on ? "오푸스(시험)를 켰습니다. 다음 방송부터 지금 방식과 함께 더 맑은 오푸스 소리도 내보냅니다. 엘브이디 방송 2.7.0 이상은 저절로 오푸스로 듣습니다." + (bang.kyeojim ? " 지금 방송은 그대로이니, 방송을 끝내고 다시 시작하십시오." : "")
                   : "오푸스(시험)를 껐습니다. 다음 방송부터 지금 방식으로만 내보냅니다."
        allyeo(t); malhagi(t)
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
                    Geul("아직 담아 둔 행사가 없습니다. 방송을 멈추면 녹음과 결과보고가 여기에 담깁니다.")
                } else {
                    if mok.contains(where: { !$0.olim }) {
                        Danchu(geul: "다시 듣기에 아직 못 올린 것 지금 올리기") { HaengsaOlligi.shared.sijak(maegam: false); allyeo("다시 듣기에 올리고 있습니다.") }
                    }
                    let sijak = jjok * 5
                    let ichjjok = Array(mok[min(sijak, mok.count)..<min(sijak + 5, mok.count)])
                    ForEach(Array(ichjjok.enumerated()), id: \.element.id) { i, x in
                        let geul = "\(x.jemok), \(nalMal(x.sijak)), \(gileeMal(x.gilee)), " + (x.olim ? "다시 듣기에 올림" : "아직 못 올림")
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

// MARK: 1.9.0 (261024-BI11) 관계자 붙이기 큐알 — 진행요원·현장영상해설사·무대 카메라가 폰 카메라로 비추면 붙기 화면(/gwan)이 열림

struct GwanQrView: View {
    let dwiro: () -> Void
    @State private var juso = ""
    @State private var qr: UIImage?
    @AccessibilityFocusState private var wiChojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Danchu(geul: "뒤로") { dwiro() }.accessibilityFocused($wiChojeom)
                Geul("관계자 붙이기 큐알", gulgeum: true)
                Geul("진행요원, 현장영상해설사, 피디 보조가 이 큐알을 폰 카메라로 비추면 붙기 화면이 열립니다. 역할을 고르고 이름을 적으면 피디 화면의 관계자 목록에 나오고, 피디 화면과 현장영상해설사 화면을 함께 씁니다. " +
                     "듣는 분 폰과 같은 공유기 와이파이에 붙어 있어야 합니다. 아이폰 방송 서버에는 자물쇠(https) 주소가 없어, 무대 카메라와 관계자 무전은 안드로이드 LVD 송출이나 플린트 2에서만 됩니다.")
                if let q = qr {
                    Image(uiImage: q).resizable().interpolation(.none).scaledToFit()
                        .frame(maxWidth: .infinity)
                        .accessibilityLabel("관계자 붙이기 큐알코드")
                } else {
                    Geul("큐알을 만들지 못했습니다. 이 아이폰이 공유기에 붙었는지 확인하십시오.", saek: Saek.norang)
                }
                Geul("붙기 주소", saek: Saek.yeonhan).accessibilityHidden(true)
                Text(juso).font(.title3.weight(.bold)).foregroundStyle(Saek.norang)
                    .textSelection(.enabled)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityLabel("붙기 주소 " + juso)
                Danchu(geul: "뒤로") { dwiro() }
            }
            .padding(20)
        }
        .accessibilityAction(.escape) { dwiro() }
        .onAppear {
            juso = HyeonjangGil.shared.gwanJuso(host: "")
            qr = Jeopsok.qrGeurim(juso)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { wiChojeom = true }
        }
    }
}
