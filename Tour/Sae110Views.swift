// LVD투어스튜디오 앱 — 1.1.0판에 넣은 화면 (1.2.0 261017-T4 사진 크게 보기 보강. 빌드 261017-T3, 방클, 이사장님 승인 「첫단계부터 시작해」)
// 웹 투어스튜디오에 있던 것을 앱에도: 사진 크게 보기·지우기·다시 찍기, 현장 메모(말·글), 메모 듣기,
// 읽으며 녹음과 되돌아보기, 판 되돌리기, 휴지통, 관리자에게 봐 달라고 알리기, 코스 지우기.
// 맞대기 표: 저장소 matdae/tour_web_app.txt
import SwiftUI
import UIKit

// MARK: - 사진 한 장 크게 보기 (지우기·같은 방향 다시 찍기, 1.2.0: 앞·다음 사진, 밝기·또렷함, 방향 고치기, 폰에 저장하기, 다른 지점으로 옮기기 — 웹 shotView 와 같음)
struct SajinView: View {
    @EnvironmentObject var store: TourStore
    @Environment(\.dismiss) private var dismiss
    let spotId: String
    @State private var idx: Int
    @State private var url: URL? = nil
    @State private var jiulkka = false
    @State private var kamera = false
    @State private var dasiBang = ""
    @State private var omgilGot = ""
    @State private var omgilkka = false
    @AccessibilityFocusState private var meori: Bool
    init(spotId: String, idx: Int) { self.spotId = spotId; _idx = State(initialValue: idx) }

    var body: some View {
        List {
            if let k = store.kos, let s = k.spot(spotId), idx < s.shots.count {
                let p = s.shots[idx]
                let dir = geulja(p["dir"]), f = geulja(p["f"])
                let q = p["q"] as? [String: Any]
                Section {
                    Text(k.spotFull(s) + ", \(s.shots.count)장 가운데 \(idx + 1)번째, " + (dir.isEmpty ? "방위 모름" : dir) + (geulja(q?["warn"]).isEmpty ? ", 밝기와 또렷함 좋음" : ", " + geulja(q?["warn"])) + (f.isEmpty ? ", 아직 나스에 올리지 못함" : ""))
                        .font(.headline).accessibilityAddTraits(.isHeader).accessibilityFocused($meori)
                    if let url {
                        AsyncImage(url: url) { im in im.resizable().scaledToFit() } placeholder: { ProgressView() }
                            .frame(maxWidth: .infinity)
                            .accessibilityLabel(k.spotLab(s) + " " + dir + " 사진, 함께 가는 분이 보실 수 있습니다")
                    } else if f.isEmpty {
                        Text("이 사진은 폰에 남아 있고 아직 나스에 올라가지 않았습니다. 설정의 「못 올린 것 지금 올리기」로 올리십시오.")
                    }
                    if let q, sutja(q["bright"]) ?? -1 >= 0 { Text("밝기 \(Int(sutja(q["bright"]) ?? 0)), 또렷함 " + geulja(q["sharp"]) + (geulja(p["head"]).isEmpty ? "" : ", 찍을 때 나침반 \(Int(sutja(p["head"]) ?? 0))도")).font(.footnote) }
                    if idx > 0 { Button("앞 사진") { bogi(idx - 1) } }
                    if idx + 1 < s.shots.count { Button("다음 사진") { bogi(idx + 1) } }
                }
                if !f.isEmpty {
                    Section { Button("이 사진 폰에 저장하기 (사진 앱)") { Task { await jeojang(k, s, p) } } }
                }
                if !k.isGyobon {
                    Section {
                        Button("이 사진 지우기", role: .destructive) { jiulkka = true }
                        if !dir.isEmpty {
                            Button("이 방향 다시 찍기 (" + dir + ")") { dasiBang = dir; jiulkka = false; dasiJjikgi(s) }
                        }
                    }
                    Section("방향 고치기") {
                        ForEach(["12시 방향", "3시 방향", "6시 방향", "9시 방향"], id: \.self) { d in
                            Button(d + "으로 적기" + (d == dir ? " (지금)" : "")) {
                                var a = s.shots; a[idx]["dir"] = d; s.shots = a
                                store.bakkwim(); store.say("이 사진 방향을 " + d + "으로 적었습니다.")
                            }
                        }
                    }
                    Section("다른 지점으로 옮기기") {
                        Picker("옮길 지점", selection: $omgilGot) {
                            Text("고르기").tag("")
                            ForEach(k.allSpots.filter { $0.id != s.id }) { o in Text(k.spotFull(o)).tag(o.id) }
                        }
                        Button("고른 지점으로 옮기기") { if omgilGot.isEmpty { store.say("옮길 지점을 먼저 골라 주십시오.") } else { omgilkka = true } }
                    }
                }
            } else {
                Text("이 사진은 지워졌거나 옮겨졌습니다. 뒤로 가십시오.")
            }
        }
        .navigationTitle("사진 크게 보기")
        .task { await juso() }
        .alert("사진을 지웁니다. 되돌릴 수 없습니다. 지울까요?", isPresented: $jiulkka) {
            Button("지우기", role: .destructive) {
                if let s = store.kos?.spot(spotId) { store.deletePhoto(s, idx) }
                dismiss()
            }
            Button("그만두기", role: .cancel) {}
        }
        .alert("이 사진을 " + (store.kos?.spot(omgilGot)?.name ?? "") + " 지점으로 옮깁니다. 옮길까요?", isPresented: $omgilkka) {
            Button("옮기기") { omgigi() }
            Button("그만두기", role: .cancel) {}
        }
        .fullScreenCover(isPresented: $kamera) {
            Kamera { im in
                kamera = false
                if let im { let b = dasiBang; Task { await store.photo(im, dir: b) } }
                dismiss()
            }
            .ignoresSafeArea()
        }
    }
    private func bogi(_ i: Int) { idx = i; url = nil; Task { await juso() } }
    private func juso() async {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { meori = true }
        if let s = store.kos?.spot(spotId), idx < s.shots.count, let k = store.kos {
            let f = geulja(s.shots[idx]["f"])
            if !f.isEmpty { let tk = await TourAPI.needTicket(); url = TourAPI.fileURL("photo", course: k.id, f: f, tk: tk) }
        }
    }
    private func jeojang(_ k: Kos, _ s: Spot, _ p: [String: Any]) async {
        let tk = await TourAPI.needTicket()
        guard let u = TourAPI.fileURL("photo", course: k.id, f: geulja(p["f"]), tk: tk) else { return }
        store.say("사진을 폰에 저장합니다.")
        guard let rd = try? await URLSession.shared.data(from: u), let im = UIImage(data: rd.0) else { store.say("사진을 저장하지 못했습니다. 잠시 뒤 다시 눌러 주십시오."); return }
        UIImageWriteToSavedPhotosAlbum(im, nil, nil, nil)
        store.say("사진 앱에 저장했습니다. " + s.name + " " + (geulja(p["dir"]).isEmpty ? "방위 모름" : geulja(p["dir"])) + " 사진입니다.")
    }
    private func omgigi() {
        guard let k = store.kos, let s = k.spot(spotId), let t = k.spot(omgilGot), idx < s.shots.count else { return }
        var a = s.shots; let p = a.remove(at: idx); s.shots = a
        var b = t.shots; b.append(p); t.shots = b
        if let l = store.lastShot, l.0 == s.id, l.1 == idx { store.lastShot = (t.id, b.count - 1) }
        store.bakkwim()
        store.say("사진을 " + t.name + " 지점으로 옮겼습니다. " + s.name + " 지점에 \(a.count)장, " + t.name + " 지점에 \(b.count)장입니다.")
        dismiss()
    }

    /// 같은 방향 다시 찍기 — 이 사진을 지우고 같은 방향으로 카메라를 엶(웹 redoShot 과 같음)
    private func dasiJjikgi(_ s: Spot) {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else { store.say("이 기기에서는 카메라를 쓸 수 없습니다."); return }
        store.deletePhoto(s, idx)
        store.curId = s.id
        store.say("같은 방향으로 다시 찍습니다. " + dasiBang + "을 바라보고 찍으십시오.")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { kamera = true }
    }
}

/// 사진 목록 한 줄 — 두드리면 크게 보기
struct SajinMokrok: View {
    @EnvironmentObject var store: TourStore
    let s: Spot
    var body: some View {
        if s.shots.isEmpty { Text("아직 사진이 없습니다. 답사 탭에서 찍으십시오.") }
        Jang(items: s.shots.enumerated().map { Jul(id: "\($0.offset)|" + geulja($0.element["f"]), t: geulja($0.element["dir"])) }) { x in
            let i = Int(x.id.split(separator: "|").first ?? "0") ?? 0
            NavigationLink { SajinView(spotId: s.id, idx: i).dwiro() } label: {
                SajinJul(kosId: store.kos?.id ?? "", jul: x)
            }
        }
    }
}

// MARK: - 방금 찍은 사진 (답사 탭)
struct BangeumSajin: View {
    @EnvironmentObject var store: TourStore
    @State private var jiulkka = false
    var body: some View {
        if let l = store.lastShot, let k = store.kos, let s = k.spot(l.0), l.1 < s.shots.count {
            let dir = geulja(s.shots[l.1]["dir"])
            Section("방금 찍은 사진 — " + k.spotLab(s) + " " + dir) {
                NavigationLink("방금 찍은 사진 크게 보기") { SajinView(spotId: s.id, idx: l.1).dwiro() }
                Button("이 사진 지우기", role: .destructive) { jiulkka = true }
                NavigationLink("이 방향 다시 찍기 (" + dir + ")") { SajinView(spotId: s.id, idx: l.1).dwiro() }
                    .accessibilityHint("사진 크게 보기 화면에서 이 방향 다시 찍기를 누르십시오")
            }
            .alert("사진을 지웁니다. 되돌릴 수 없습니다. 지울까요?", isPresented: $jiulkka) {
                Button("지우기", role: .destructive) { store.deletePhoto(s, l.1) }
                Button("그만두기", role: .cancel) {}
            }
        }
    }
}

// MARK: - 현장 메모 (말로 적기·글로 적기)
struct MemoSection: View {
    @EnvironmentObject var store: TourStore
    @ObservedObject private var rec = Nokeumgi.shared
    @State private var kan = "access"
    @State private var geul = ""
    var body: some View {
        if let k = store.kos, let s = store.cur, !k.isGyobon {
            Section("현장 메모 — " + k.spotLab(s) + " (말로 적기 · 글로 적기)") {
                Picker("어디에 담을지", selection: $kan) {
                    ForEach(MemoKan.mok.map { $0.0 }, id: \.self) { key in Text(MemoKan.ireum(key)).tag(key) }
                }
                if rec.jung && rec.galle == "memo" {
                    Button("다 말했습니다 (녹음 끝)") { kkeutnaegi(s) }.font(.headline)
                } else {
                    Button("말로 적기 시작") {
                        Task {
                            if let e = await rec.start(galle: "memo", spotId: s.id, where_: kan) { store.say(e) }
                            else { store.say("녹음을 시작했습니다. 말씀하신 뒤 다 말했습니다를 누르십시오.") }
                        }
                    }
                    .disabled(rec.jung)
                }
                TextField("글로 적기 (" + MemoKan.ireum(kan) + " 칸에 붙습니다)", text: $geul, axis: .vertical)
                Button("글로 담기") { store.memoGeul(s, where_: kan, text: geul); geul = "" }
                NavigationLink("담긴 메모 \(((s.d["memos"] as? [[String: Any]]) ?? []).count)개 보기와 듣기") { MemoMokrokView(spotId: s.id).dwiro() }
            }
        }
    }
    private func kkeutnaegi(_ s: Spot) {
        guard let r = rec.stop() else { return }
        let u = r.0, sec = r.1
        let w = rec.where_
        let sid = rec.spotId
        Task {
            if let sp = store.kos?.spot(sid) { await store.memoMal(sp, where_: w, wav: u, sec: sec) }
            else { await store.memoMal(s, where_: w, wav: u, sec: sec) }
        }
    }
}

struct MemoMokrokView: View {
    @EnvironmentObject var store: TourStore
    @ObservedObject private var deut = Deutgi.shared
    let spotId: String
    var body: some View {
        List {
            AllimJul()
            if let k = store.kos, let s = k.spot(spotId) {
                let m = (s.d["memos"] as? [[String: Any]]) ?? []
                Section(k.spotFull(s) + " — 담긴 메모 \(m.count)개") {
                    if m.isEmpty { Text("아직 담긴 메모가 없습니다.") }
                    Jang(items: m.enumerated().reversed().map { Jul(id: "\($0.offset)", t: "") }) { x in
                        let i = Int(x.id) ?? 0
                        let one = m[i]
                        let f = geulja(one["f"]), sec = Int(sutja(one["sec"]) ?? 0)
                        let tx = geulja(one["text"])
                        let at = geulja(one["at"]).replacingOccurrences(of: "T", with: " ").dropFirst(5).prefix(11)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(MemoKan.ireum(geulja(one["where"])) + ", " + at + (sec > 0 ? ", \(sec)초 녹음" : ", 글로 담음"))
                                .font(.footnote)
                            Text(tx.isEmpty ? "(받아쓰지 못함 — 소리로만 남음)" : tx)
                            if !f.isEmpty {
                                Button(deut.jigeum == f ? "듣기 멈춤" : "이 메모 듣기") { Task { await deut.deutgi(kosId: k.id, f: f) } }
                            } else if sec > 0 {
                                Text("소리는 아직 나스에 올리지 못해 폰에 남아 있습니다.").font(.footnote)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("담긴 메모")
        .onDisappear { deut.meomchum() }
    }
}

// MARK: - 읽으며 녹음과 되돌아보기 (현장영상해설 탭)
struct YeonseupSection: View {
    @EnvironmentObject var store: TourStore
    @ObservedObject private var rec = Nokeumgi.shared
    @ObservedObject private var deut = Deutgi.shared
    var body: some View {
        if let k = store.kos, let s = store.cur {
            Section("연습 — 읽으며 녹음") {
                Text("어림 \(k.readSec(k.spotText(s)))초" + ((s.n("real") ?? 0) > 0 ? " / 실제 \(Int(s.n("real") ?? 0))초" : " / 아직 재지 않았습니다"))
                let m = Yeonseup.mal(k, s)
                if !m.isEmpty { Text("되돌아보기: " + m).fixedSize(horizontal: false, vertical: true) }
                if let p = s.d["prac"] as? [String: Any], !geulja(p["heard"]).isEmpty {
                    DisclosureGroup("받아쓴 말 보기 (참고)") { Text(geulja(p["heard"])) }
                }
                if !k.isGyobon {
                    if rec.jung && rec.galle == "prac" {
                        Button("다 읽었습니다 (녹음 끝)") { kkeutnaegi() }.font(.headline)
                    } else {
                        Button("읽으며 녹음 시작") {
                            Moksori.shared.meomchum()
                            Task {
                                if let e = await rec.start(galle: "prac", spotId: s.id, where_: "") { store.say(e) }
                                else { store.say("녹음을 시작했습니다. 대본을 실제처럼 읽으신 뒤 다 읽었습니다를 누르십시오.") }
                            }
                        }
                        .disabled(rec.jung)
                    }
                }
                let pf = s.s("pracFile")
                if !pf.isEmpty {
                    Button(deut.jigeum == pf ? "듣기 멈춤" : "연습 녹음 듣기") { Task { await deut.deutgi(kosId: k.id, f: pf) } }
                } else if !s.s("pracQref").isEmpty {
                    Text("연습 녹음은 아직 나스에 올리지 못해 폰에 남아 있습니다.").font(.footnote)
                }
            }
        }
    }
    private func kkeutnaegi() {
        guard let r = rec.stop() else { return }
        let u = r.0, sec = r.1
        let sid = rec.spotId
        Task { if let sp = store.kos?.spot(sid) { await store.yeonseup(sp, wav: u, sec: sec) } }
    }
}

// MARK: - 코스 관리 (봐 달라고 알리기·판 되돌리기·코스 지우기)
struct KosGwanri: View {
    @EnvironmentObject var store: TourStore
    @State private var mal = ""
    @State private var jiulkka = false
    var body: some View {
        if let k = store.kos, !k.isGyobon {
            Section("관리자에게 봐 달라고 알리기") {
                TextField("함께 전할 말 (없어도 됩니다)", text: $mal, axis: .vertical)
                Button("관리자에게 봐 달라고 알리기") { Task { if await store.askReview(mal.trimmingCharacters(in: .whitespacesAndNewlines)) { mal = "" } } }
            }
            Section("이 코스") {
                NavigationLink("판 되돌리기 (예전에 저장된 판으로)") { PanDoerigiView().dwiro() }
                Button("이 코스 지우기 (휴지통으로)", role: .destructive) { jiulkka = true }
            }
            .alert(k.name + " 코스를 지울까요? 휴지통으로 가므로 필요하면 되살릴 수 있습니다.", isPresented: $jiulkka) {
                Button("지우기", role: .destructive) { let id = k.id, nm = k.name; Task { await store.deleteCourse(id, nm) } }
                Button("그만두기", role: .cancel) {}
            }
        }
    }
}

struct PanDoerigiView: View {
    @EnvironmentObject var store: TourStore
    @Environment(\.dismiss) private var dismiss
    @State private var L: [[String: Any]]? = nil
    @State private var goreun: [String: Any]? = nil
    var body: some View {
        List {
            AllimJul()
            Section {
                Text("저장할 때마다 앞의 것이 판으로 남습니다. 되돌려도 지금 것이 다시 판으로 남으므로 잃지 않습니다.").font(.footnote)
                Button("다시 훑기") { Task { L = await store.listVersions() } }
            }
            if let L {
                Section(L.isEmpty ? "아직 남은 판이 없습니다." : "남은 판 \(L.count)개 — 새것부터") {
                    Jang(items: L.map { Jul(id: geulja($0["v"]), t: geulja($0["at"])) }) { x in
                        let v = L.first { geulja($0["v"]) == x.id } ?? [:]
                        Button(x.t.replacingOccurrences(of: "T", with: " ").dropFirst(5).prefix(11) + ", 지점 \(Int(sutja(v["spots"]) ?? 0))곳, 대본 \(Int(sutja(v["texts"]) ?? 0))곳, \(Int(sutja(v["chars"]) ?? 0))자 — 이 판으로 되돌리기") { goreun = v }
                    }
                }
            } else { Text("훑는 중입니다.") }
        }
        .navigationTitle("판 되돌리기")
        .task { L = await store.listVersions() }
        .alert("이 판으로 되돌립니다. 지금 것도 판으로 남습니다. 되돌릴까요?", isPresented: Binding(get: { goreun != nil }, set: { if !$0 { goreun = nil } })) {
            Button("되돌리기") { let v = geulja(goreun?["v"]); goreun = nil; Task { if await store.restoreVersion(v) { dismiss() } } }
            Button("그만두기", role: .cancel) { goreun = nil }
        }
    }
}

struct HyujitongView: View {
    @EnvironmentObject var store: TourStore
    @State private var L: [[String: Any]]? = nil
    @State private var goreun: [String: Any]? = nil
    var body: some View {
        List {
            AllimJul()
            if let L {
                Section(L.isEmpty ? "휴지통이 비어 있습니다." : "휴지통에 지운 코스 \(L.count)개") {
                    Jang(items: L.map { Jul(id: geulja($0["f"]), t: geulja($0["name"])) }) { x in
                        let v = L.first { geulja($0["f"]) == x.id } ?? [:]
                        let pl = geulja(v["place"])
                        Button("되살리기 — " + x.t + (pl.isEmpty ? "" : ", " + pl) + ", 지점 \(Int(sutja(v["spots"]) ?? 0))곳, 지운 때 " + geulja(v["at"])) { goreun = v }
                    }
                }
            } else { Text("불러오는 중입니다.") }
        }
        .navigationTitle("휴지통")
        .task { L = await store.listTrash() }
        .alert(geulja(goreun?["name"]) + " 코스를 되살릴까요? 코스 목록에 다시 들어갑니다.", isPresented: Binding(get: { goreun != nil }, set: { if !$0 { goreun = nil } })) {
            Button("되살리기") {
                let f = geulja(goreun?["f"]), nm = geulja(goreun?["name"]); goreun = nil
                Task { if await store.restoreTrash(f, nm) { L = await store.listTrash() } }
            }
            Button("그만두기", role: .cancel) { goreun = nil }
        }
    }
}

/// 설정의 못 올린 것 줄
struct DaegiJul: View {
    @EnvironmentObject var store: TourStore
    var body: some View {
        Button(store.daegiSu > 0 ? "못 올린 것 \(store.daegiSu)개 — 지금 올리기" : "못 올린 것이 없습니다 — 살펴보기") {
            Task { await store.daegiOllim(quiet: false) }
        }
        .onAppear { store.daegiSu = Daegi.su }
    }
}
