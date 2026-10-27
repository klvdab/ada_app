// 설정 탭 — 새로고침은 이 한 곳에만 둡니다.
import SwiftUI
import AVFoundation

struct SeoljeongView: View {
    @ObservedObject private var g = GinGeup.shared
    @ObservedObject private var gj = GongjiEngine.shared
    @ObservedObject private var heorak = HeorakAllim.shared   // 2.110.0
    @ObservedObject private var wichi = WichiEngine.shared    // 2.110.0 허락이 바뀌면 단추 이름도
    @State private var saerogochimMal = ""
    @AccessibilityFocusState private var malChojeom: Bool
    @AccessibilityFocusState private var cheotJul: Bool   // 2.12.1 설정 탭을 고르면 첫 줄로

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                NavigationLink { GongjiView() } label: { Text("알림" + (gj.gingeupSae != nil ? " — 읽지 않은 긴급 공지가 있습니다" : "")) }
                    .buttonStyle(KeunDanchu())
                    .accessibilityFocused($cheotJul)
                // 2.110.0 (261025-I64, 이사장님 승인 2026-10-25) 위치·마이크 허락 — 지금 상태를 이름에 담고, 누르면 아이폰 설정의 길눈 화면이 바로 열림
                // 2.111.0 위치 서비스가 꺼졌을 때는 켜는 차례를 알려 드린다고 이름에 밝힘
                Button(heorak.wichiSeobiseu
                       ? "위치 허락 — \(heorak.wichiMal). 누르면 아이폰 설정의 길눈 화면을 엽니다. 위치에서 항상을 골라 주십시오"
                       : "위치 허락 — \(heorak.wichiMal). 누르면 아이폰 위치 서비스를 켜는 차례를 알려 드립니다") {
                    heorak.wichiSeoljeongYeolgi()
                }
                .buttonStyle(KeunDanchu())
                Button("마이크 허락 — \(heorak.maikMal). 누르면 아이폰 설정의 길눈 화면을 엽니다") {
                    heorak.maikSeoljeongYeolgi()
                }
                .buttonStyle(KeunDanchu())
                // 2.116.0 (261027-I70, 이사장님 승인 2026-10-27 「위치를 음악방송이 아니라 설정탭에」, 안드로이드 2.81.0과 같음) 일정 — 폰 캘린더를 말로 듣고 넣고 고치기
                NavigationLink { IljeongView() } label: { Text("일정 — 일정 듣기와 넣기, 아침 브리핑, 떠날 시각 알림") }
                    .buttonStyle(KeunDanchu())
                // 2.70.0 (이사장님 승인 2026-10-14 「1」) 설정 순서를 한 잣대로 — 자주 손대는 것부터, 시험용은 맨 뒤 펼치기 안에. 안드로이드와 같은 순서·같은 이름
                NavigationLink { MalSeoljeongView() } label: { Text("말하기 설정 — 켜기와 끄기, 빠르기, 목소리, 얼마나 자세히, 무엇을 말할지") }
                    .buttonStyle(KeunDanchu())
                NavigationLink { MalHagiSeoljeongView() } label: { Text("말로 하기 설정 — 하이 길눈, 호칭") }
                    .buttonStyle(KeunDanchu())
                NavigationLink { JeomSeoljeongView() } label: { Text("점지도와 걸음 — 보폭 재기, 나만의 점지도, 걸음 오차 재기, 내 문") }
                    .buttonStyle(KeunDanchu())
                NavigationLink { GigiSeoljeongView() } label: { Text("기기 설정 — 음향신호기, 이어폰 단추, 워치, 리모컨") }
                    .buttonStyle(KeunDanchu())
                NavigationLink { JiinMyeongdanView() } label: { Text("가족·지인 명단 — 이음 번호로 등록, 받는 분 화면에 뜰 내 이름") }
                    .buttonStyle(KeunDanchu())
                NavigationLink { SeoryuhamView() } label: { Text("내 서류 보관함 — 복지카드와 신분증 담아 두기, 복지콜 등록") }
                    .buttonStyle(KeunDanchu())
                DisclosureGroup("폰 펼치기 — 흔들면 자리 번호, 현 위치정보 말할 내용") {
                    VStack(alignment: .leading, spacing: 10) {
                        NavigationLink { HeundeulView() } label: { Text("흔들면 자리 번호 — 긴급통화 열기 또는 국가지점번호") }
                            .buttonStyle(KeunDanchu())
                        NavigationLink { JariSeoljeongView() } label: { Text("현 위치정보 말할 내용") }
                            .buttonStyle(KeunDanchu())
                    }
                }
                .font(.title3)
                NavigationLink { DoumalView() } label: { Text("도움말") }
                    .buttonStyle(KeunDanchu())
                NavigationLink { YeogiJeomgeomView() } label: { Text("여기서 점검 — 지금 이 자리에서 무엇이 막혔는지 알아보기") }
                    .buttonStyle(KeunDanchu())
                Button("새로고침 — 받아 둔 자료를 다시 받습니다. 내 설정과 여정은 그대로입니다") {
                    Tongsin.shared.gamchumBiugi()
                    saerogochimMal = "새로고침을 마쳤습니다. 다음에 쓰실 때 나스에서 새 자료를 받습니다."
                    SoriEngine.shared.mal(saerogochimMal)
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { malChojeom = true }
                }
                .buttonStyle(KeunDanchu())
                if !saerogochimMal.isEmpty {
                    Text(saerogochimMal).accessibilityFocused($malChojeom)
                }
                OllimSeoljeongDanchu()   // 2.54.0 업데이트 — 새로고침 바로 아래 한 곳
                NavigationLink { GilnunJeongboView() } label: { Text("길눈 정보 — 판과 빌드, 고친 기록") }
                    .buttonStyle(KeunDanchu())
                DisclosureGroup("더 보기 펼치기 — 기초 시험" + (TeokAllim.boim ? ", 아침 기록, 관리자 시험" : "")) {
                    VStack(alignment: .leading, spacing: 10) {
                        NavigationLink { GichoSiheomView() } label: { Text("기초 시험 — 위치, 방향, 걸음, 말소리가 도는지 듣기") }
                            .buttonStyle(KeunDanchu())
                        if TeokAllim.boim {   // 2.24.0 관리자 시험 중 — 나스 음악 열쇠가 있는 폰에서만
                            // 2.59.0 아침 기록 — 지난 24시간 길눈·자봉 쓰임과 오류(이사장님 승인 2026-10-09)
                            NavigationLink { AchimGirokView() } label: { Text("아침 기록 — 지난 24시간 길눈과 자봉 쓰임, 오류") }
                                .buttonStyle(KeunDanchu())
                            NavigationLink { TeokAllimView() } label: { Text("관리자 시험 — 발 앞 계단·턱 알림, 프로 모델 아이폰") }
                                .buttonStyle(KeunDanchu())
                            NavigationLink { MalbeotGyeonjugiView() } label: { Text("관리자 시험 — 말벗 견주기, 서버와 폰 안 인공지능") }   // 2.51.0
                                .buttonStyle(KeunDanchu())
                        }
                    }
                }
                .font(.title3)
            }
            .padding()
        }
        .navigationTitle("설정")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { cheotJul = false; DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { cheotJul = true } }
        .tabChojeom { cheotJul = false; DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { cheotJul = true } }   // 2.108.0 탭을 고르면 첫 줄로
    }
}

/// 말하기 설정 — 화면 글자와 보이스오버가 읽는 것은 건드리지 않고 길눈의 말소리만 다룹니다.
struct MalSeoljeongView: View {
    @AppStorage("gn.tomakDeutgi") private var tomakDeutgi = true   // 2.55.0 자봉 목소리 토막 듣기(TomakDeutgi 와 같은 칸)
    @ObservedObject private var s = Seoljeong.shared

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Toggle(isOn: $s.malKyeojim) { Text("길눈 말소리").font(.title3.weight(.semibold)) }
                    .padding(.horizontal, 4)
                    .frame(minHeight: 60)
                // 2.55.0 목소리 따라 걷기 — 점지도 표시에 닿기 몇 걸음 앞에서 그린 자봉의 짧은 말을 들려 드림
                Toggle(isOn: $tomakDeutgi) { Text("자봉 목소리 토막 듣기").font(.title3.weight(.semibold)) }
                    .padding(.horizontal, 4)
                    .frame(minHeight: 60)
                // 2.15.0 카메라 눈 말소리 — 끄면 문 찾기 등이 소리로만
                Toggle(isOn: $s.kameraMal) { Text("카메라 눈 말소리").font(.title3.weight(.semibold)) }
                    .padding(.horizontal, 4)
                    .frame(minHeight: 60)
                if SoriEngine.msYeollim {   // 2.28.0 길눈 목소리(선희) — 애저 정식 열쇠 전에는 나스 음악 열쇠가 있는 폰에서만
                    Toggle(isOn: $s.msMoksori) { Text("길눈 목소리 — 마이크로소프트 선희").font(.title3.weight(.semibold)) }
                        .padding(.horizontal, 4)
                        .frame(minHeight: 60)
                }
                Button("빠르기 — \(Seoljeong.bbareugiIreum[s.bbareugiDan]). 누르면 바꿉니다") {
                    s.bbareugiDan = (s.bbareugiDan + 1) % Seoljeong.bbareugiIreum.count
                    SoriEngine.shared.mal("\(Seoljeong.bbareugiIreum[s.bbareugiDan]) 말씀드립니다.")
                }
                .buttonStyle(KeunDanchu())
                Button("목소리 — \(SoriEngine.moksoriIreum(SoriEngine.shared.moksori())). 누르면 바꿉니다") {
                    let l = SoriEngine.hangukMoksori()
                    guard !l.isEmpty else { return }
                    let jigeum = SoriEngine.shared.moksori()?.identifier ?? ""
                    let i = l.firstIndex { $0.identifier == jigeum } ?? -1
                    s.moksoriId = l[(i + 1) % l.count].identifier
                    SoriEngine.shared.mal("이 목소리로 말씀드립니다.")
                }
                .buttonStyle(KeunDanchu())
                MalSeolDeoView()   // 2.9.0 얼마나 자세히, 무엇을 말할지
                Text("경고는 안전을 위해 말소리를 꺼도 늘 말씀드립니다. 더 좋은 목소리는 아이폰 설정의 손쉬운 사용, 읽기 및 말하기, 음성에서 한국어 목소리를 내려받으시면 여기에 나타납니다.")
                    .font(.body)
            }
            .padding()
        }
        .sokHwamyeon("말하기 설정")
    }
}

/// 길눈 정보 — 판과 빌드, 고친 기록
struct GilnunJeongboView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("길눈 \(Pan.pan)판, 빌드 \(Pan.bild), 앱 짓기 번호 \(Pan.appBild). 만든 곳 사단법인 한국시각장애인현장영상해설협회.")
                    .font(.title3)
                Mokrok5(Pan.girok) { g in
                    NavigationLink {
                        ScrollView {
                            VStack(alignment: .leading, spacing: 10) {
                                ForEach(g.naeyong, id: \.self) { Text($0) }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding()
                        }
                        .sokHwamyeon("\(g.pan)판 고친 내용")
                    } label: {
                        Text("\(g.nal) \(g.pan)판, 빌드 \(g.bild)")
                    }
                    .buttonStyle(KeunDanchu())
                }
            }
            .padding()
        }
        .sokHwamyeon("길눈 정보")
    }
}
