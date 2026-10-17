// LVD투어스튜디오 앱 — 들어오기와 코스 탭 (판 1.2.0, 빌드 261017-T4 — 가입 신청, 다음 할 일, 승인 표시, 씨앗 코스, 내보내기·협회 승인, 관리 묶음, 장소 평면도. 1.1.0 261017-T3 — 봐 달라고 알리기·판 되돌리기·코스 지우기·휴지통·더 적을 것. 1.0.0 261012-T1)
import SwiftUI

struct LoginView: View {
    @EnvironmentObject var store: TourStore
    @State private var id = ""
    @State private var pw = ""
    @State private var mal = ""
    @State private var bappeum = false
    @AccessibilityFocusState private var malF: Bool
    var body: some View {
        NavigationStack {
            Form {
                Section { Meori().listRowInsets(EdgeInsets()).listRowBackground(Color.clear) }
                Section {
                    Text("웹 투어스튜디오와 같은 아이디와 비밀번호로 들어오십시오. 아이디가 없으시면 아래 가입 신청하기를 눌러 주십시오.")
                    TextField("아이디", text: $id)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textContentType(.username)
                    SecureField("비밀번호", text: $pw)
                        .textContentType(.password)
                    Button(bappeum ? "들어가는 중입니다" : "들어가기") {
                        Task {
                            bappeum = true
                            let e = await store.login(id: id.trimmingCharacters(in: .whitespaces), pw: pw)
                            bappeum = false
                            pw = ""
                            if let e { mal = e; DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { malF = true } }
                        }
                    }
                    .disabled(bappeum || id.isEmpty || pw.isEmpty)
                    if !mal.isEmpty { Text(mal).accessibilityFocused($malF) }
                }
                Section {
                    NavigationLink("가입 신청하기") { SignupView().dwiro() }
                    Text(Pan.ireum).font(.footnote)
                }
            }
            .navigationTitle("LVD투어스튜디오")
            .navigationBarTitleDisplayMode(.inline)
            .background(Saek.bada)
        }
    }
}

struct KosJul: Identifiable {
    let id: String
    let j: [String: Any]
    var name: String { geulja(j["name"]) }
    var gyobon: Bool { (j["gyobon"] as? Bool) == true || id == "GYOBON" }
    var label: String {
        var t = (gyobon ? "교본 — " : "") + name
        let pl = geulja(j["place"]); if !pl.isEmpty { t += ", " + pl }
        if let n = sutja(j["spots"]) { t += ", 지점 \(Int(n))곳" }
        if let m = sutja(j["total"]), m > 0 { t += ", " + fmtMin(m) }
        if let f = j["final"] as? [String: Any] { let b = Wonchik.finalBadge(f); if !b.isEmpty { t += ", " + b } }
        return t
    }
}

struct KosTab: View {
    @EnvironmentObject var store: TourStore
    @EnvironmentObject var nav: Nav
    @State private var saeIreum = ""
    @State private var saeJangso = ""
    @State private var ssiat = -1
    var body: some View {
        List {
            Section { Meori().listRowInsets(EdgeInsets()).listRowBackground(Color.clear) }
            TourOllimJul()
            AllimJul()
            if let k = store.kos {
                Section("다음 할 일") {
                    Text(Wonchik.nextTodo(k, store.meName)).font(.headline)
                }
                Section("지금 연 코스") {
                    Text(k.name + " — 지점 \(k.spots.count)곳과 오프닝·클로징 지점, 모두 " + fmtMin(k.courseMin()))
                        .font(.headline)
                    if !store.saveMark.isEmpty { Text(store.saveMark).font(.footnote) }
                    if k.isGyobon {
                        Text("교본은 읽기만 됩니다.")
                        Button("교본을 내 코스로 복사") { Task { _ = await store.copyGyobon() } }
                    }
                    NavigationLink("코스 정보 (이름·장소·집합 장소·비 올 때)") { KosJeongbo().dwiro() }
                    NavigationLink("대본 살펴보기·원칙 16개 점검표") { JeomgeomView().dwiro() }
                    NavigationLink("내보내기·협회 최종 승인 요청 — " + Wonchik.finalStatus(k.d["final"] as? [String: Any])) { NaebonaegiView().dwiro() }
                    if store.isMaster && !k.isGyobon { NavigationLink("클에게 대본 맡기기") { ClaudeView().dwiro() } }
                    NavigationLink("기능 찾기") { GineungView().dwiro() }
                    Button("지점 탭으로 가기") { nav.tab = 2 }
                }
                KosGwanri()
            }
            GwanriSection()
            Section("코스 목록 (\(store.courses.count)개)") {
                if store.courses.isEmpty { Text(store.bappeum ? "불러오는 중입니다." : "코스가 없습니다. 아래에서 새 코스를 만드십시오.") }
                Jang(items: store.courses.map { KosJul(id: geulja($0["id"]), j: $0) }) { c in
                    Button(c.label + (store.kos?.id == c.id ? " (열려 있음)" : "")) {
                        Task { if await store.open(c.id) { store.say(c.name + " 코스를 열었습니다. 지점 \(store.kos?.spots.count ?? 0)곳입니다.") } }
                    }
                }
            }
            Section("새 코스 만들기") {
                TextField("코스 이름", text: $saeIreum)
                TextField("장소 (예: 부산 용두산공원)", text: $saeJangso)
                Button("새 코스 만들기") {
                    Task {
                        if await store.newCourse(name: saeIreum.trimmingCharacters(in: .whitespaces), place: saeJangso.trimmingCharacters(in: .whitespaces)) {
                            store.say(saeIreum + " 코스를 만들고 열었습니다. 오프닝 지점과 클로징 지점이 들어 있습니다.")
                            saeIreum = ""; saeJangso = ""
                        }
                    }
                }
                .disabled(saeIreum.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            Section("이 코스로 시작하기 (씨앗 코스 — 지점이 미리 들어 있음)") {
                Picker("씨앗 코스", selection: $ssiat) {
                    Text("고르기").tag(-1)
                    ForEach(Array(Wonchik.SEEDS.enumerated()), id: \.offset) { i, x in Text(x.name + " — " + x.place).tag(i) }
                }
                Button("고른 씨앗 코스로 시작하기") { Task { await ssiatSijak() } }
                NavigationLink("장소 지식 들이기 (장소 이름으로 코스 뼈대 짜기)") { JisikView().dwiro() }
            }
            Section("휴지통") {
                NavigationLink("휴지통 — 지운 코스 되살리기") { HyujitongView().dwiro() }
            }
        }
        .navigationTitle("코스")
        .scrollContentBackground(.hidden)
        .background(Saek.bada)
        .task { if store.isMaster { await Gwan.shared.loadAlerts(tell: true) } }
    }
    /// 씨앗 코스 — 웹 newCourse(seed) 와 같음(분야·집합 장소·메모·지점 이름을 미리 넣고 저장)
    private func ssiatSijak() async {
        guard ssiat >= 0 && ssiat < Wonchik.SEEDS.count else { store.say("먼저 씨앗 코스를 골라 주십시오."); return }
        let x = Wonchik.SEEDS[ssiat]
        guard await store.newCourse(name: x.name, place: x.place), let k = store.kos else { return }
        k.d["field"] = x.field; k.d["meet"] = x.meet; k.d["note"] = x.note
        var cur: Spot? = k.openSpot
        for n in x.spots { let s = k.addSpot(after: cur, name: n); s.d["move"] = 5; cur = s }
        store.bakkwim()
        _ = await store.save(quiet: true)
        ssiat = -1
        store.say(x.name + " 을(를) 바탕으로 시작했습니다. 지점 \(k.spots.count)곳이 미리 들어갔습니다.")
    }
}

/// 코스 정보 — 웹 1단계의 기본 칸 (1.1.0: 웹 「더 적을 것」의 분야·지역·참가 인원·현장영상해설사 수도)
struct KosJeongbo: View {
    @EnvironmentObject var store: TourStore
    static let BUNYA = ["", "문화유산 분야", "박물관·전시 분야", "자연·경관 분야", "공연·행사 분야", "스포츠 관람 분야", "교육·체험 분야", "실시간 퍼포먼스 분야", "테마투어 분야"]
    static let JIYEOK = ["", "서울", "경기", "인천", "부산", "대구", "광주", "대전", "강원", "충청", "전라", "경상", "제주"]
    var body: some View {
        Form {
            if let k = store.kos {
                Section("코스") {
                    kan(k, "name", "코스 이름")
                    kan(k, "place", "장소")
                    kan(k, "meet", "집합 장소 (첫 만남 자리 — 오프닝 지점이 여기서 시작)")
                    kan(k, "rain", "비 올 때")
                    kan(k, "note", "메모")
                }
                PyeongmyeonSection(k: k)
                Section("더 적을 것 (분야·지역·인원)") {
                    Picker("분야", selection: Binding(get: { k.s("field") }, set: { k.d["field"] = $0; store.bakkwim() })) {
                        ForEach(KosJeongbo.BUNYA, id: \.self) { Text($0.isEmpty ? "고르지 않음" : $0).tag($0) }
                    }
                    Picker("지역", selection: Binding(get: { k.s("region") }, set: { k.d["region"] = $0; store.bakkwim() })) {
                        ForEach(KosJeongbo.JIYEOK, id: \.self) { Text($0.isEmpty ? "고르지 않음" : $0).tag($0) }
                    }
                    Stepper(value: Binding(get: { Int(sutja(k.d["people"]) ?? 0) }, set: { k.d["people"] = max(0, $0); store.bakkwim() }), in: 0...500) {
                        Text("참가 인원 \(Int(sutja(k.d["people"]) ?? 0))명")
                    }
                    Stepper(value: Binding(get: { max(1, Int(sutja(k.d["guides"]) ?? 1)) }, set: { k.d["guides"] = max(1, $0); store.bakkwim() }), in: 1...50) {
                        Text("현장영상해설사 수 \(max(1, Int(sutja(k.d["guides"]) ?? 1)))명")
                    }
                }
                Section("앞머리 (작성원칙 통합본 1·3·5·6·16번)") {
                    kan(k, "first", "1번 · 첫 만남 인사말")
                    kan(k, "history", "3번 · 장소 소개 — 역사")
                    kan(k, "access", "3번 · 장소 소개 — 위치와 찾아오는 길")
                    kan(k, "scale", "3번 · 장소 소개 — 규모와 형태")
                    kan(k, "points", "3번 · 장소 소개 — 전체 포인트")
                    kan(k, "tactile", "5번 · 촉각지도·손바닥 지도 안내")
                    kan(k, "safety", "6번 · 출발 전 안전 고지")
                    kan(k, "refs", "16번 · 참고문헌")
                }
            } else { KosEopseum() }
        }
        .navigationTitle("코스 정보")
        .disabled(store.kos?.isGyobon == true)
    }
    @ViewBuilder func kan(_ k: Kos, _ key: String, _ nm: String) -> some View {
        VStack(alignment: .leading) {
            Text(nm).font(.footnote).accessibilityHidden(true)
            TextField(nm, text: Binding(get: { k.s(key) }, set: { k.d[key] = $0; store.bakkwim() }), axis: .vertical)
                .accessibilityLabel(nm)
        }
    }
}

struct Jul: Identifiable { let id: String; let t: String }
