// LVD 방송 아이폰(듣는 앱) — 시작과 화면 (2.1.0판, 빌드 261016-LI2, 방클, 이사장님 승인 「1」)
// 배프 웹 껍데기(BfbApp)를 속까지 앱으로 새로 지음. 고유 번호 kr.or.ada.bfb 를 그대로 이어 업데이트로 바뀝니다.
// 이사장님 뜻: 듣는 분께는 들을 것만. 방송 방식·공유기 같은 말은 어디에도 보이지 않게.
// 첫 화면(2.2.0): 협회 로고 머리 → 「지금 현장영상해설 듣기」(큰 단추, 커서, 행사장이면 현장 방송·아니면 인터넷 방송) → 상태 한 줄(바뀔 때만 알림)
//   → (인터넷 방송이 여럿이면 고르기) → TV해설방송 듣기 → 다시 듣기 → 「현장영상해설사 도움 요청」(행사장에서) → (처음 한 번) 저절로 들리게 하기 → 새 판 한 줄
//   → 더 보기(방송 알림 받기, 인터넷 방송 목록, 그 밖에 들을 거리 TV방송·라디오방송, 저절로 듣기, 와이파이, 말소리, 도움말, 새로고침, 업데이트, 판 정보)
// 행사장(행사장 와이파이)이면 앱을 열기만 해도 곧바로 들려 드립니다.
// 2.5.0(빌드 261024-LI7): 듣는 인터넷 방송에 영상이 실려 있으면 상태 한 줄 아래에 「영상으로 보기」(Bogi.swift). 고르기 목록에도 영상이 있는 방송은 영상으로 보기 단추를 함께.
import SwiftUI
import UIKit
import AVFoundation

enum Pan {
    static let PAN = "2.7.0"   // 2.7.0(261026-LI10) 오푸스(시험) — 송출이 오푸스도 내보내면 저절로 오푸스로 듣고, 없으면 지금 방식(새방클, 이사장님 승인 2026-10-26). 2.6.1(261026-LI9) 현장영상해설 소리 더 크게(보통·두 배·네 배, 새방클, 이사장님 지시 2026-10-26). 2.6.0(261025-LI8) 한 번에 듣기 — 송출 앱이 만든 큐알·엔에프시 태그로 앱이 열리고 행사장 와이파이에 붙어 곧바로 들림(새방클, 이사장님 승인 2026-10-25). 2.5.0(261024-LI7) 대조표 미진한 부분 고치기(새방송클, 이사장님 지시 2026-10-24) — 인터넷 방송 영상으로 보기(늦게 들어와도). 2.4.1(261023-LI6) 이용 기록 — 듣기 상태가 바뀔 때마다 협회 서버에 남김(새방클, 이사장님 지시 2026-10-22). 2.4.0(261023-LI5) 아이피타임 방식 BYOD 방송도 저절로 찾아 들음(와이파이 안에서 8080번 문을 연 LVD 송출 폰 찾기, 찾은 자리 기억), 도움말 두 항목(새방클, 이사장님 지시 2026-10-23). 2.3.0(261019-LI4) 다시 듣기 내려받기를 앱 안에서(소리 mp3 두 가지·받아쓰기 글, 와이파이가 아니면 데이터 크기를 먼저 여쭘), 다시 듣기도 화면 잠가도 이어지고 전화 오면 멈춤, BYOD 방송 이름, 도움말 네 항목 더함(방클, 이사장님 결정 2026-10-19). 2.2.0(261018-LI3) LVD 방송 정리 — 지금 현장영상해설 듣기 하나로(행사장이면 현장 방송, 아니면 인터넷 방송을 앱 안에서), TV해설방송 듣기·다시 듣기를 첫 화면에, 방송 알림 받기, 그 밖에 들을 거리(방클, 이사장님 승인 「1」). 2.1.0(261016-LI2) 더 보기에 TV해설방송 듣기·TV방송·라디오방송, 인터넷 생방송 듣기로 이름 줄임, 앱 안 누리집의 겹친 뒤로 감춤(방클, 이사장님 승인 「1」). 2.0.0(261015-LI1) LVD 방송 아이폰 첫 판 — 행사장 와이파이 저절로 붙기, 앱 열면 곧바로 듣기, 도움 요청, 화면 잠가도 듣기, 통화 때 멈춤, 벗어나면 알림, 인터넷 생방송·다시 듣기, 도움말, 새 판 알림(방송클, 이사장님 승인 「1」)
    static let BILD = "261026-LI10"
    static let BAEP = "https://lvd.ada.or.kr/bfblive/"
}

@main
struct LvdBangsongApp: App {
    var body: some Scene {
        WindowGroup {
            CheotHwamyeon()
                .preferredColorScheme(.dark)
                .onAppear { BfbOllim.shared.sijak(); BfbGirok.shared.sijak() }   // 2.4.1 이용 기록
                .onOpenURL { u in Hyeonjang.hanbeon(u) }   // 2.6.0 (261025-LI8) 한 번에 듣기 — 큐알·태그(lvdbangsong://deut?w=…)로 열림
        }
    }
}

// MARK: 꾸밈 — 법인 짙은 남색 바탕, 흰 단추에 노란 테, 큰 단추는 노랑(LVD 송출과 같은 색)

enum Saek {
    static let bada = Color(red: 18 / 255, green: 52 / 255, blue: 110 / 255)
    static let nam = Color(red: 11 / 255, green: 33 / 255, blue: 76 / 255)
    static let norang = Color(red: 1, green: 204 / 255, blue: 0)
    static let geom = Color(red: 17 / 255, green: 17 / 255, blue: 17 / 255)
    static let yeonhan = Color(red: 220 / 255, green: 230 / 255, blue: 245 / 255)
}

struct Danchu: View {
    let geul: String
    var keun = false
    var norang = false
    let f: () -> Void
    init(_ geul: String, keun: Bool = false, norang: Bool = false, f: @escaping () -> Void) {
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

/// 프로그램 말소리 — 켜고 끌 수 있음(화면 글과 보이스오버는 그대로)
final class Malsori {
    static let shared = Malsori()
    private let synth = AVSpeechSynthesizer()
    var kyeojim: Bool {
        get { UserDefaults.standard.object(forKey: "malsori") as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: "malsori") }
    }
    func mal(_ t: String) {
        guard kyeojim else { return }
        if synth.isSpeaking { synth.stopSpeaking(at: .immediate) }
        let u = AVSpeechUtterance(string: t)
        u.voice = AVSpeechSynthesisVoice(language: "ko-KR")
        synth.speak(u)
    }
}

/// 한 번 알릴 말 — 말소리가 켜져 있으면 말로, 꺼져 있으면 보이스오버에(두 번 겹쳐 듣지 않게)
func allim(_ t: String) {
    if Malsori.shared.kyeojim { Malsori.shared.mal(t) } else { UIAccessibility.post(notification: .announcement, argument: t) }
}

// MARK: 첫 화면

enum Hwamyeon: Equatable { case cheot, doumal, web(String, String), bogi(String, String) }   // 2.5.0 bogi(방 번호, 제목) — 영상으로 보기

struct CheotHwamyeon: View {
    @ObservedObject private var deut = Deut.shared
    @ObservedObject private var ollim = BfbOllim.shared
    @State private var hwamyeon: Hwamyeon = .cheot
    @State private var yocheongYeollim = false
    @State private var deoYeollim = false
    @State private var deungrok = Hyeonjang.deungrokDoem
    @State private var jeoljeolo = UserDefaults.standard.object(forKey: "jeoljeolo_deutgi") as? Bool ?? true
    @State private var keugi = Deut.keugi   // 2.6.1 소리 더 크게
    @State private var malsori = Malsori.shared.kyeojim
    @State private var salpimJung = false
    @State private var gitaYeollim = false   // 2.2.0 그 밖에 들을 거리
    @AccessibilityFocusState private var deutCursor: Bool
    @Environment(\.scenePhase) private var scenePhase
    private let tik = Timer.publish(every: 3, on: .main, in: .common).autoconnect()

    var body: some View {
        ZStack {
            Saek.bada.ignoresSafeArea()
            switch hwamyeon {
            case .cheot: cheot
            case .doumal: DoumalHwamyeon { hwamyeon = .cheot }
            case .web(let jemok, let juso): WebHwamyeon(jemok: jemok, juso: juso) { hwamyeon = .cheot }
            case .bogi(let sid, let jemok): BogiHwamyeon(sid: sid, jemok: jemok) { hwamyeon = .cheot }
            }
        }
        .onAppear {
            Deut.shared.mal = { m in allim(m) }
            hyeonjangSalpigi(baro: false)
        }
        .onReceive(tik) { _ in if hwamyeon == .cheot && !deut.kyeojim && jeoljeolo && !salpimJung { hyeonjangSalpigi(baro: false) } }
        .onChange(of: scenePhase) { p in if p == .active { hyeonjangSalpigi(baro: false) } }
    }

    private var cheot: some View {
        ScrollView {
            VStack(spacing: 14) {
                HyeophoeMeori(ireum: "LVD 방송")
                Danchu(deut.kyeojim ? "듣기 멈추기" : "지금 현장영상해설 듣기", keun: true, norang: true) { deutNureum() }
                    .accessibilityFocused($deutCursor)
                Geul(deut.sangtae, gulgeum: true)
                // 2.5.0 듣는 인터넷 방송에 영상이 있으면 영상으로 보기
                if let bb = deut.bogiBang {
                    Danchu("영상으로 보기" + (bb.jemok.isEmpty ? "" : " — " + bb.jemok)) { hwamyeon = .bogi(bb.id, bb.jemok) }
                }
                // 2.2.0 인터넷 방송이 여럿이면 고르기
                ForEach(deut.gorgi) { b in
                    Danchu(b.ireum) { deut.inteonetGoreum(b) }
                    if b.yeongsang {   // 2.5.0
                        Danchu("영상으로 보기 — " + (b.jemok.isEmpty ? "제목 없는 방송" : b.jemok)) { hwamyeon = .bogi(b.id, b.jemok) }
                    }
                }
                Danchu("TV해설방송 듣기") { hwamyeon = .web("TV해설방송 듣기", "https://lvd.ada.or.kr/lvds/haeseol_deutgi.html") }
                Danchu("다시 듣기") { hwamyeon = .web("다시 듣기", Pan.BAEP + "dasi.html") }
                Danchu("현장영상해설사 도움 요청 (행사장에서)") { yocheongYeollim.toggle() }
                if yocheongYeollim {
                    Danchu("도와주세요 (현장영상해설사가 찾아갑니다)") { yocheong("dowum") }
                    Danchu("소리가 끊겨요") { yocheong("kkeunkim") }
                    Danchu("궁금한 것이 있어요") { yocheong("gungeum") }
                }
                if !deungrok {
                    Danchu("행사장에 들어서면 저절로 들리게 하기 (처음 한 번 허락)") { deungrokHagi() }
                }
                if let p = ollim.saePan {
                    Danchu("LVD 방송 새 판 \(p)이 있습니다. 누르면 업데이트합니다") { ollim.yeolgi() }
                }
                Danchu(deoYeollim ? "더 보기 접기" : "더 보기") { deoYeollim.toggle() }
                if deoYeollim {
                    Danchu("방송 알림 받기") { hwamyeon = .web("방송 알림 받기", Pan.BAEP + "deutgi2.html#allim") }   // 2.2.0
                    Danchu("인터넷 방송 목록 (누리집 화면)") { hwamyeon = .web("인터넷 방송 목록", Pan.BAEP + "deutgi2.html") }
                    Danchu(gitaYeollim ? "그 밖에 들을 거리 접기" : "그 밖에 들을 거리 — TV방송과 라디오방송") { gitaYeollim.toggle() }   // 2.2.0
                    if gitaYeollim {
                        Danchu("TV방송") { hwamyeon = .web("TV방송", Pan.BAEP + "chae.html?kind=tv") }
                        Danchu("라디오방송") { hwamyeon = .web("라디오방송", Pan.BAEP + "chae.html?kind=radio") }
                    }
                    Danchu(jeoljeolo ? "행사장에 들어서면 저절로 듣기: 켜짐. 누르면 끕니다" : "행사장에 들어서면 저절로 듣기: 꺼짐. 누르면 켭니다") {
                        jeoljeolo.toggle()
                        UserDefaults.standard.set(jeoljeolo, forKey: "jeoljeolo_deutgi")
                        allim(jeoljeolo ? "행사장에서 앱을 열면 저절로 들려 드립니다." : "저절로 듣기를 껐습니다. 행사장에서는 듣기 단추를 눌러 주십시오.")
                    }
                    Danchu("현장영상해설 소리 더 크게: 지금 " + (keugi == 2 ? "두 배" : keugi == 4 ? "네 배" : "보통") + ". 누르면 바꿉니다") {   // 2.6.1 (261026-LI9)
                        let sae = keugi == 1 ? 2 : (keugi == 2 ? 4 : 1)
                        Deut.keugi = sae
                        keugi = sae
                        let ir = sae == 2 ? "두 배" : sae == 4 ? "네 배" : "보통"
                        allim("현장영상해설 소리를 " + ir + (sae == 1 ? "로 들려 드립니다." : "로 키워 들려 드립니다. 너무 크면 다시 누르십시오."))
                    }
                    Danchu("행사장 와이파이 저절로 붙기 다시 하기") { deungrokHagi() }
                    Danchu(malsori ? "프로그램 말소리 끄기" : "프로그램 말소리 켜기") {
                        malsori.toggle()
                        Malsori.shared.kyeojim = malsori
                        allim(malsori ? "프로그램 말소리를 켰습니다." : "프로그램 말소리를 껐습니다.")
                    }
                    Danchu("도움말") { hwamyeon = .doumal }
                    Danchu("새로고침") {
                        allim("새로고침합니다. 행사장인지 다시 살피고, 새 판이 있는지 살핍니다.")
                        BfbOllim.shared.salpigi()
                        hyeonjangSalpigi(baro: true)
                    }
                    Danchu(ollim.seoljeongGeul) { ollim.seoljeongNureum() }
                    Geul("LVD 방송 아이폰 \(Pan.PAN)판, 빌드 \(Pan.BILD). 사단법인 한국시각장애인현장영상해설협회", saek: Saek.yeonhan)
                }
            }
            .padding(20)
        }
        .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { deutCursor = true } }
    }

    private func deutNureum() {
        if deut.kyeojim { deut.kkeugi(son: true); allim("듣기를 멈추었습니다. 다시 들으시려면 같은 단추를 누르십시오."); return }
        deut.kyeogi()
        allim("현장영상해설을 찾고 있습니다. 행사장이면 BYOD 방송을, 아니면 인터넷 방송을 들려 드립니다.")
    }

    private func hyeonjangSalpigi(baro: Bool) {
        salpimJung = true
        Hyeonjang.salpigi { jm in
            salpimJung = false
            if jm == nil { Deut.shared.sonMeomchum = false; return }
            if baro { Deut.shared.sonMeomchum = false }
            if !Deut.shared.kyeojim && !Deut.shared.sonMeomchum && (jeoljeolo || baro) { Deut.shared.kyeogi() }
        }
    }

    private func yocheong(_ k: String) {
        Hyeonjang.yocheong(k) { ok in
            allim(ok ? "알렸습니다. 현장영상해설사가 곧 찾아갑니다." : "현장영상해설사에게 알리지 못했습니다. 도움 요청은 행사장 안에서만 됩니다. 잠시 뒤 다시 눌러 주십시오.")
        }
    }

    private func deungrokHagi() {
        allim("아이폰이 연결할지 물으면 연결을 눌러 주십시오.")
        Hyeonjang.deungrok { m in
            deungrok = Hyeonjang.deungrokDoem
            allim(m)
        }
    }
}

// MARK: 도움말 — 찾기 칸, 다섯 개씩, 위아래 뒤로, 두 손가락 문지르기로 돌아옴

struct DoumalHwamyeon: View {
    let dwiro: () -> Void
    @State private var chatgi = ""
    @State private var jjok = 0
    @State private var yeollin: String? = nil
    @AccessibilityFocusState private var cheotCursor: Bool

    var body: some View {
        let l = Doumal.chatgi(chatgi)
        let sijak = jjok * 5
        let boil = Array(l[min(sijak, l.count)..<min(sijak + 5, l.count)])
        ScrollView {
            VStack(spacing: 12) {
                Danchu("뒤로") { dwiro() }.accessibilityFocused($cheotCursor)
                TextField("도움말 찾기 (낱말을 넣고 엔터)", text: $chatgi)
                    .font(.title3).padding(10).foregroundStyle(Saek.geom)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color.white))
                    .onSubmit { jjok = 0 }
                    .accessibilityLabel("도움말 찾기")
                if l.isEmpty { Geul("찾는 낱말이 든 도움말이 없습니다. 다른 낱말로 찾아보십시오.") }
                ForEach(boil) { h in
                    Danchu(h.jemok) { yeollin = (yeollin == h.jemok) ? nil : h.jemok }
                    if yeollin == h.jemok { Geul(h.naeyong) }
                }
                if sijak + 5 < l.count { Danchu("더 보기") { jjok += 1 } }
                if jjok > 0 { Danchu("이전 보기") { jjok -= 1 } }
                Danchu("뒤로") { dwiro() }
            }
            .padding(20)
        }
        .accessibilityAction(.escape) { dwiro() }
        .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { cheotCursor = true } }
    }
}
