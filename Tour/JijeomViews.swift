// LVD투어스튜디오 앱 — 지점 탭: 지점 목록과 지점 한 화면 (판 1.0.0, 빌드 261012-T1)
// 지점 하나는 「어디서부터 어디까지」로 정합니다. 오프닝 지점과 클로징 지점도 지점입니다.
import SwiftUI

struct JijeomTab: View {
    @EnvironmentObject var store: TourStore
    @State private var saeIreum = ""
    var body: some View {
        List {
            Section { GyeonggyeJul() }
            AllimJul()
            if let k = store.kos {
                Section("지점 목록 — " + k.name + ", 모두 " + fmtMin(k.courseMin())) {
                    Jang(items: k.allSpots) { s in
                        NavigationLink(value: s.id) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(k.spotFull(s) + (s.id == store.curId ? " (지금 지점)" : "")).font(.headline)
                                Text(k.spotMinTxt(s) + ", 사진 \(s.shots.count)장" + (k.spotText(s).isEmpty ? ", 대본 없음" : "")).font(.footnote)
                            }
                            .accessibilityElement(children: .combine)
                        }
                    }
                }
                if !k.isGyobon {
                    Section("지점 넣기") {
                        Text("지금 지점(" + k.spotLab(store.cur) + ") 바로 뒤에 넣습니다. 현장에서 경계 자리까지 적으시려면 답사 탭의 「여기서부터 새 지점 시작」을 쓰십시오.")
                            .font(.footnote)
                        TextField("새 지점 이름", text: $saeIreum)
                        Button("지점 넣기 (지금 지점 바로 뒤에)") {
                            let s = k.addSpot(after: store.cur, name: saeIreum.trimmingCharacters(in: .whitespaces))
                            k.syncMove()
                            store.go(s)
                            store.say(k.spotFull(s) + "을 넣었습니다. 지점은 모두 \(k.spots.count)곳입니다.")
                            saeIreum = ""
                            store.bakkwim()
                        }
                    }
                }
            } else { KosEopseum() }
        }
        .navigationTitle("지점")
        .navigationDestination(for: String.self) { id in SpotView(id: id).dwiro() }
    }
}

/// 지점 한 화면 — 어디서부터·어디까지, 시간, 이동 구간 안내, 대본까지 한 곳에서
struct SpotView: View {
    @EnvironmentObject var store: TourStore
    @State var id: String
    @AccessibilityFocusState private var meori: Bool
    static let bangHyang = ["12시 방향", "1시 방향", "2시 방향", "3시 방향", "4시 방향", "5시 방향", "6시 방향", "7시 방향", "8시 방향", "9시 방향", "10시 방향", "11시 방향"]

    var body: some View {
        Form {
            if let k = store.kos, let s = k.spot(id) {
                Section {
                    Text(k.spotFull(s)).font(.title3.weight(.bold)).accessibilityAddTraits(.isHeader).accessibilityFocused($meori)
                    Text("어디서부터: " + k.spotFrom(s)).fixedSize(horizontal: false, vertical: true)
                    Text("어디까지: " + (s.s("to").trimmingCharacters(in: .whitespaces).isEmpty ? "아직 적지 않음 (아래 어디까지 칸에 적으십시오)" : s.s("to")))
                    Text("시간: " + k.spotMinTxt(s))
                }
                Section("경계와 시간") {
                    if s.kind.isEmpty { kan(s, "name", "지점 이름", false) }
                    kan(s, "to", k.spotToName(s), true)
                    Stepper(value: Binding(get: { Int(s.n("walkIn") ?? 0) }, set: { s.d["walkIn"] = max(0, $0); k.syncMove(); store.bakkwim() }), in: 0...240) {
                        Text("이동 시간 \(Int(s.n("walkIn") ?? 0))분 — " + (s.kind == "open" ? "첫 만남 자리에서 오프닝 자리까지" : "앞 지점을 떠나 여기까지"))
                    }
                    Stepper(value: Binding(get: { Int(s.n("stay") ?? 0) }, set: { s.d["stay"] = max(0, $0); store.bakkwim() }), in: 0...240) {
                        Text("정지 현장영상해설 시간 \(Int(s.n("stay") ?? 0))분" + (s.kind.isEmpty ? "" : " — 0이면 대본 읽는 시간으로 셉니다"))
                    }
                }
                Section("현장 자료") {
                    kan(s, "access", "이동 구간 안내 (계단·경사·점자블록·엘리베이터)", true)
                    kan(s, "touch", "만져 볼 수 있는 것", true)
                    kan(s, "safe", "조심할 것 · 화장실 · 쉼터", true)
                    kan(s, "gpos", "현장영상해설사 위치·현장영상해설 순서 (8번)", true)
                }
                Section(s.kind == "open" ? "오프닝 지점 대본" : (s.kind == "close" ? "클로징 지점 대본" : "이 지점 현장영상해설 글 (대본)")) {
                    TextField("대본", text: Binding(get: { k.spotText(s) }, set: { k.setSpotText(s, $0); store.bakkwim() }), axis: .vertical)
                        .lineLimit(6...30)
                        .accessibilityLabel("대본")
                    let t = k.spotText(s)
                    Text("\(t.filter { !$0.isWhitespace }.count)자, 읽는 데 \(k.readSec(t))초쯤").font(.footnote)
                }
                Section("방향 도우미 (대본 끝에 붙습니다)") {
                    Jang(items: SpotView.bangHyang.map { Jul(id: $0, t: $0) }, per: 4) { w in
                        Button(w.t + " 붙이기") {
                            let t = k.spotText(s)
                            k.setSpotText(s, t + (t.isEmpty || t.hasSuffix(" ") ? "" : " ") + w.t)
                            store.bakkwim(); store.say(w.t + "을 붙였습니다.")
                        }
                    }
                }
                Section("사진 \(s.shots.count)장") {
                    if s.shots.isEmpty { Text("아직 사진이 없습니다. 답사 탭에서 찍으십시오.") }
                    Jang(items: s.shots.enumerated().map { Jul(id: "\($0.offset)|" + geulja($0.element["f"]), t: geulja($0.element["dir"])) }) { x in
                        SajinJul(kosId: k.id, jul: x)
                    }
                }
                Section {
                    if let p = k.spotPrev(s) { Button("앞 지점 — " + k.spotFull(p)) { olgim(p.id) } }
                    if let n = k.spotNext(s) { Button("다음 지점 — " + k.spotFull(n)) { olgim(n.id) } }
                    if s.kind.isEmpty && !k.isGyobon {
                        Button("이 지점을 한 칸 앞으로") { if k.moveSpot(s, by: -1) { store.bakkwim(); store.say(k.spotFull(s) + "이 되었습니다.") } }
                        Button("이 지점을 한 칸 뒤로") { if k.moveSpot(s, by: 1) { store.bakkwim(); store.say(k.spotFull(s) + "이 되었습니다.") } }
                    }
                    Button("지금 저장") { Task { await store.save(quiet: false) } }
                }
            } else { KosEopseum() }
        }
        .navigationTitle("지점 한 화면")
        .disabled(store.kos?.isGyobon == true)
        .onAppear { if let s = store.kos?.spot(id) { store.curId = s.id } }
        .onDisappear { Task { await store.save(quiet: true) } }
    }

    private func olgim(_ nid: String) {
        id = nid; store.curId = nid
        if let k = store.kos, let s = k.spot(nid) { store.say(k.spotFull(s) + "입니다.") }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { meori = true }
    }

    @ViewBuilder func kan(_ s: Spot, _ key: String, _ nm: String, _ yeoreo: Bool) -> some View {
        VStack(alignment: .leading) {
            Text(nm).font(.footnote).accessibilityHidden(true)
            if yeoreo {
                TextField(nm, text: Binding(get: { s.s(key) }, set: { s.d[key] = $0; store.bakkwim() }), axis: .vertical).accessibilityLabel(nm)
            } else {
                TextField(nm, text: Binding(get: { s.s(key) }, set: { s.d[key] = $0; store.bakkwim() })).accessibilityLabel(nm)
            }
        }
    }
}

/// 사진 한 줄 — 방향을 글로, 그림은 함께 가는 분이 볼 수 있게
struct SajinJul: View {
    let kosId: String
    let jul: Jul
    @State private var url: URL? = nil
    var body: some View {
        let parts = jul.id.split(separator: "|", maxSplits: 1).map(String.init)
        let beon = (Int(parts.first ?? "0") ?? 0) + 1
        let f = parts.count > 1 ? parts[1] : ""
        HStack {
            if let url {
                AsyncImage(url: url) { im in im.resizable().scaledToFit() } placeholder: { ProgressView() }
                    .frame(width: 88, height: 66)
                    .accessibilityHidden(true)
            }
            Text("\(beon)번째 사진" + (jul.t.isEmpty ? "" : ", " + jul.t) + (f.isEmpty ? " (아직 못 올림)" : ""))
        }
        .task {
            if !f.isEmpty { let tk = await TourAPI.needTicket(); url = TourAPI.fileURL("photo", course: kosId, f: f, tk: tk) }
        }
    }
}
