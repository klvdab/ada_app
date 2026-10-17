// LVD투어스튜디오 앱 — 들어오기와 코스 탭 (판 1.1.0, 빌드 261017-T3 — 봐 달라고 알리기·판 되돌리기·코스 지우기·휴지통·더 적을 것. 1.0.0 261012-T1)
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
                    Text("웹 투어스튜디오와 같은 아이디와 비밀번호로 들어오십시오. 아이디가 없으시면 웹 투어스튜디오에서 가입을 신청해 주십시오.")
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
                    Link("웹 투어스튜디오 열기 (가입 신청)", destination: URL(string: "https://lvd.ada.or.kr/lvdts/lvdts.html")!)
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
        let f = geulja(j["final"]); if !f.isEmpty { t += ", 승인 " + f }
        return t
    }
}

struct KosTab: View {
    @EnvironmentObject var store: TourStore
    @EnvironmentObject var nav: Nav
    @State private var saeIreum = ""
    @State private var saeJangso = ""
    @State private var seungin = ""
    var body: some View {
        List {
            Section { Meori().listRowInsets(EdgeInsets()).listRowBackground(Color.clear) }
            TourOllimJul()
            AllimJul()
            if let k = store.kos {
                Section("지금 연 코스") {
                    Text(k.name + " — 지점 \(k.spots.count)곳과 오프닝·클로징 지점, 모두 " + fmtMin(k.courseMin()))
                        .font(.headline)
                    if !store.saveMark.isEmpty { Text(store.saveMark).font(.footnote) }
                    if k.isGyobon {
                        Text("교본은 읽기만 됩니다.")
                        Button("교본을 내 코스로 복사") { Task { _ = await store.copyGyobon() } }
                    }
                    NavigationLink("코스 정보 (이름·장소·집합 장소·비 올 때)") { KosJeongbo().dwiro() }
                    NavigationLink("원칙 16개 점검 (작성원칙 통합본)") { JeomgeomView().dwiro() }
                    Button("지점 탭으로 가기") { nav.tab = 2 }
                }
                if !k.isGyobon {
                    Section("협회 승인 요청") {
                        TextField("승인 요청에 붙일 말 (없어도 됩니다)", text: $seungin, axis: .vertical)
                        Button("협회 승인 요청 보내기") {
                            Task {
                                await store.save(quiet: true)
                                let j = await TourAPI.call("submit_final", ["c": k.id, "msg": seungin])
                                if TourAPI.ok(j) { seungin = ""; store.say("협회 승인 요청을 보냈습니다. 승인되면 코스 목록에 표시됩니다.") }
                                else { store.say(TourAPI.err(j)) }
                            }
                        }
                    }
                }
                KosGwanri()
            }
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
            Section("휴지통") {
                NavigationLink("휴지통 — 지운 코스 되살리기") { HyujitongView().dwiro() }
            }
        }
        .navigationTitle("코스")
        .scrollContentBackground(.hidden)
        .background(Saek.bada)
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

struct JeomgeomView: View {
    @EnvironmentObject var store: TourStore
    var body: some View {
        List {
            if let k = store.kos {
                let L = Jeomgeom.lines(k)
                Section {
                    Text(L.isEmpty ? "앱이 살필 수 있는 것은 모두 갖추었습니다. 나머지 원칙은 웹 투어스튜디오의 원칙 16개 점검표에서 확인하십시오." : "고칠 곳 \(L.count)가지입니다.")
                        .font(.headline)
                }
                Section("고칠 곳") {
                    Jang(items: L.enumerated().map { Jul(id: "\($0.offset)", t: $0.element) }) { x in Text(x.t) }
                }
            } else { KosEopseum() }
        }
        .navigationTitle("원칙 16개 점검")
    }
}

struct Jul: Identifiable { let id: String; let t: String }
