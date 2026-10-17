// LVD투어스튜디오 앱 — 1.2.0판 화면 ② 코스 작업 (빌드 261017-T4, 방클, 이사장님 지시 「웹에 있는 모든 기능을 겉과 속이 같게」)
// 내보내기·협회 최종 승인 요청, 대본 살펴보기·원칙 16개 점검표, 장소 평면도, 답사 지도, 걷는 시간 어림, 테마투어 느낌 칸,
// 방향 도우미(스무 낱말), 용어 뜻 찾기·우리 용어 사전, 장소 지식 들이기, 우리가 확인한 것 채워 넣기,
// 승인본 현장영상해설 화면(시간 재기·화면 안 꺼짐·읽어 주기·따라가기·벗어남), 점검, 기능 찾기
import SwiftUI
import UIKit
import MapKit
import CoreLocation
import AVFoundation
import Speech

// MARK: - 바깥 창구 부르기 (사전·장소 지식·우리 자료 — 로그인 없이 GET)
enum Bakkat {
    static let teo = "https://lvd.ada.or.kr/lvdts/"
    static func get(_ path: String, _ q: [String: String]) async -> [String: Any] {
        var c = URLComponents(string: teo + path)
        c?.queryItems = q.map { URLQueryItem(name: $0.key, value: $0.value) }
        guard let u = c?.url else { return ["ok": false, "err": "주소를 만들지 못했습니다."] }
        var r = URLRequest(url: u); r.timeoutInterval = 120; r.cachePolicy = .reloadIgnoringLocalCacheData
        r.setValue(TourAPI.userAgent, forHTTPHeaderField: "User-Agent")
        do {
            let (d, _) = try await URLSession.shared.data(for: r)
            return (try? JSONSerialization.jsonObject(with: d) as? [String: Any]) ?? ["ok": false, "err": "이상한 대답을 받았습니다."]
        } catch { return ["ok": false, "err": "닿지 못했습니다. 인터넷을 확인해 주십시오."] }
    }
    static func post(_ path: String, _ q: [String: String], _ form: [String: String]) async -> [String: Any] {
        var c = URLComponents(string: teo + path)
        c?.queryItems = q.map { URLQueryItem(name: $0.key, value: $0.value) }
        guard let u = c?.url else { return ["ok": false] }
        var r = URLRequest(url: u); r.httpMethod = "POST"; r.timeoutInterval = 60
        r.setValue("application/x-www-form-urlencoded; charset=utf-8", forHTTPHeaderField: "Content-Type")
        var cs = CharacterSet.alphanumerics; cs.insert(charactersIn: "-._~")
        r.httpBody = form.map { $0.key + "=" + ($0.value.addingPercentEncoding(withAllowedCharacters: cs) ?? "") }.joined(separator: "&").data(using: .utf8)
        do {
            let (d, _) = try await URLSession.shared.data(for: r)
            return (try? JSONSerialization.jsonObject(with: d) as? [String: Any]) ?? ["ok": false, "err": "이상한 대답을 받았습니다."]
        } catch { return ["ok": false, "err": "닿지 못했습니다."] }
    }
}

/// 서버 소리 듣기 — 주소 그대로(승인본 소리·안내문 녹음)
@MainActor
final class Teulgi: ObservableObject {
    static let shared = Teulgi()
    @Published var jigeum = ""
    private var p: AVPlayer?
    func teul(_ u: URL, _ key: String) {
        if jigeum == key { meomchum(); return }
        meomchum()
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
        try? AVAudioSession.sharedInstance().setActive(true)
        let it = AVPlayerItem(url: u)
        p = AVPlayer(playerItem: it); jigeum = key; p?.play()
        NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: it, queue: .main) { _ in Task { @MainActor in Teulgi.shared.jigeum = "" } }
    }
    func meomchum() { p?.pause(); p = nil; jigeum = "" }
}

// MARK: - 내보내기·협회 최종 승인 (웹 6단계)
struct NaebonaegiView: View {
    @EnvironmentObject var store: TourStore
    @ObservedObject private var rec = Nokeumgi.shared
    @ObservedObject private var mok = Moksori.shared
    @ObservedObject private var teul = Teulgi.shared
    @State private var msg = ""
    @State private var muleum = ""
    @State private var muleumOn = false
    @State private var jeonsong = ""
    @State private var sendUrl: URL? = nil
    @State private var mapMal = ""
    var body: some View {
        Form {
            AllimJul()
            if let k = store.kos {
                Section("협회 최종 승인") {
                    Text(Wonchik.finalStatus(k.d["final"] as? [String: Any]))
                    if !k.isGyobon {
                        TextField("협회에 전할 말 (없어도 됩니다)", text: $msg, axis: .vertical)
                        Button(geulja((k.d["final"] as? [String: Any])?["status"]) == "approved" ? "새 판으로 다시 승인 요청하기 — 협회 DB에 저장됩니다" : "협회에 최종 승인 요청하기 — 협회 DB에 저장됩니다") { junbi(k) }
                            .font(.headline)
                    }
                }
                Section("지금 상태") {
                    let s = k.spots
                    Text(NaebonaegiView.sangtae(k))
                    NavigationLink("대본 살펴보기·원칙 16개 점검표") { JeomgeomView().dwiro() }
                }
                Section("내보내기 — 만들어 이 폰에 받은 뒤 파일 앱·문자·카톡으로 보냅니다") {
                    ForEach(Gwan.NAEBO, id: \.kind) { m in
                        Button(m.why + " 만들기") { Task { sendUrl = await Gwan.shared.naebonaegi(m.kind) } }
                    }
                    Button("이 코스 통째로 내려받기 (코스기록·답사사진·소리·내보낸것)") { Task { sendUrl = await Gwan.shared.tongjaero() } }
                    if let u = sendUrl { ShareLink("받은 파일 보내기·저장하기 — " + u.lastPathComponent, item: u).font(.headline) }
                    Text("점자 대본과 대본집·안내문·운영표는 점자정보단말기에서 바로 읽히는 한글 글자판(CP949)으로 저장됩니다.").font(.footnote)
                }
                Section("참가자 안내문 소리 판") {
                    Button(mok.malham ? "듣기 멈춤" : "안내문 폰 음성으로 미리 듣기") { mok.malham ? mok.meomchum() : mok.ilkgi(Wonchik.noticeText(k)) }
                    if !k.isGyobon {
                        if rec.jung && rec.galle == "notice" {
                            Button("다 읽었습니다 (녹음 끝)") { kkeut(k) }.font(.headline)
                        } else {
                            Button("현장영상해설사 목소리로 안내문 읽어 녹음하기") {
                                mok.meomchum()
                                Task { if let e = await rec.start(galle: "notice", spotId: "notice", where_: "") { store.say(e) } else { store.say("녹음을 시작했습니다. 안내문을 읽으신 뒤 다 읽었습니다를 누르십시오.") } }
                            }.disabled(rec.jung)
                        }
                    }
                    let na = k.d["noticeAudio"] as? [String: Any]
                    if let na {
                        let f = geulja(na["f"])
                        if f.isEmpty { Text("녹음은 되었으나 아직 나스에 올리지 못했습니다(\(Int(sutja(na["sec"]) ?? 0))초).").font(.footnote) }
                        else {
                            Text("\(Int(sutja(na["sec"]) ?? 0))초, " + ttae(geulja(na["at"]))).font(.footnote)
                            Button(teul.jigeum == f ? "듣기 멈춤" : "녹음 들어보기") { Task { let tk = await TourAPI.needTicket(); if let u = TourAPI.fileURL("audio", course: k.id, f: f, tk: tk) { teul.teul(u, f) } } }
                            Button("소리 파일 내려받기") {
                                Task {
                                    let tk = await TourAPI.needTicket()
                                    if let u = TourAPI.fileURL("audio", course: k.id, f: f, tk: tk), let to = await Gwan.naeryeogi(u, ireum: k.name + "_참가자안내문." + (f.split(separator: ".").last.map(String.init) ?? "mp3")) {
                                        sendUrl = to; store.say("받았습니다. 위의 받은 파일 보내기·저장하기로 문자나 카톡에 붙여 보내십시오.")
                                    } else { store.say("받지 못했습니다.") }
                                }
                            }
                        }
                    } else { Text("아직 녹음한 안내문이 없습니다.").font(.footnote) }
                }
                if store.isMaster {
                    Section("지도 앱에 올리기 (협회 승인을 받은 코스만)") {
                        Text("올라가는 것은 코스 이름·장소·집합 자리·걸리는 시간·들르는 자리·이동 구간 안내·만져 볼 것·비 올 때입니다. 지점별 대본과 답사 사진은 올라가지 않습니다. 1단계 지역을 골라 두십시오.").font(.footnote)
                        Button("지도 앱에 올리기") { Task { await jido(k, true) } }
                        Button("지도 앱에서 내리기") { Task { await jido(k, false) } }
                        if !mapMal.isEmpty { Text(mapMal) }
                    }
                }
                Section("전체 시간") {
                    ForEach(Array(Wonchik.timeSum(k).enumerated()), id: \.offset) { Text($0.element) }
                }
            } else { KosEopseum() }
        }
        .navigationTitle("내보내기·협회 승인")
        .alert(muleum, isPresented: $muleumOn) {
            Button("보내기") { Task { await bonaegi() } }
            Button("그만두기", role: .cancel) {}
        }
        .alert(jeonsong, isPresented: Binding(get: { !jeonsong.isEmpty }, set: { if !$0 { jeonsong = "" } })) {
            Button("확인") { jeonsong = "" }
        }
    }
    static func sangtae(_ k: Kos) -> String {
        let s = k.spots
        let sseun = s.filter { !Wonchik.bin($0.s("text")) }.count
        let eopsa = s.filter { $0.shots.isEmpty }.count
        var t = "지점 \(s.count)곳 / 대본 쓴 곳 \(sseun)곳 / 사진 없는 곳 \(eopsa)곳"
        t += " / 오프닝 " + (Wonchik.bin(k.s("opening")) ? "없음" : "있음")
        t += " / 클로징 " + (Wonchik.bin(k.s("closing")) ? "없음" : "있음")
        t += " / 모두 " + fmtMin(k.courseMin())
        return t
    }
    private func junbi(_ k: Kos) {
        if k.spots.isEmpty { store.say("지점이 없는 코스는 요청할 수 없습니다."); return }
        let noT = k.spots.filter { Wonchik.bin($0.s("text")) }.count
        if noT == k.spots.count { store.say("대본이 한 곳도 없어 승인을 요청할 수 없습니다. 대본을 쓴 뒤 다시 요청해 주십시오."); return }
        let L = Wonchik.issueLines(Wonchik.scriptIssues(k, store.meName))
        let warn = (noT > 0 ? "대본이 없는 지점이 \(noT)곳 있습니다. " : "") + (L.isEmpty ? "" : "살펴볼 것이 \(L.count)가지 남아 있습니다. ") + ((noT > 0 || !L.isEmpty) ? "그래도 요청하시겠습니까? " : "")
        muleum = warn + "지금 이 코스의 시나리오 전체와 답사 사진·소리가 협회 DB에 원본으로 저장되고, 협회의 최종 승인을 기다리게 됩니다. 저장된 판은 고칠 수 없습니다. 보낼까요?"
        muleumOn = true
    }
    private func bonaegi() async {
        guard let k = store.kos else { return }
        store.say("협회 DB에 저장하는 중입니다.")
        await store.save(quiet: true)
        let r = await TourAPI.call("submit_final", ["c": k.id, "msg": msg], timeout: 300)
        guard TourAPI.ok(r), let f = r["final"] as? [String: Any] else { store.say(TourAPI.err(r)); return }
        k.d["final"] = f; msg = ""; store.gen += 1
        jeonsong = "협회 DB에 전송 완료. \(Int(sutja(f["ver"]) ?? 0))판, 사진 \(Int(sutja(r["photos"]) ?? 0))장, 소리 \(Int(sutja(r["audio"]) ?? 0))개가 함께 보관되었습니다. 협회의 최종 승인을 기다립니다."
        store.say(jeonsong)
    }
    private func kkeut(_ k: Kos) {
        guard let r = rec.stop() else { return }
        let u = r.0, sec = r.1
        Task {
            if sec < 1 { store.say("담긴 소리가 없습니다."); return }
            let (f, q, _) = await store.soriOllim(k, k.openSpot, kind: "notice", wav: u, sec: sec)
            k.d["noticeAudio"] = ["f": f, "qref": q, "sec": sec, "at": ISO8601DateFormatter().string(from: Date())]
            store.say("\(sec)초 녹음했습니다." + (q.isEmpty ? " 녹음 들어보기와 소리 파일 내려받기가 생겼습니다." : " 아직 올리지 못해 폰에 남겨 두었습니다."))
            store.bakkwim()
        }
    }
    private func jido(_ k: Kos, _ on: Bool) async {
        await store.save(quiet: true)
        let r = await TourAPI.call(on ? "publish_map" : "unpublish_map", ["c": k.id])
        guard TourAPI.ok(r) else { mapMal = TourAPI.err(r); store.say(mapMal); return }
        k.d["onMap"] = on; store.bakkwim()
        mapMal = on ? "지도에 올렸습니다. 지금 지도에 올라간 코스는 모두 \(Int(sutja(r["count"]) ?? 0))개입니다." : "지도에서 내렸습니다. 남은 코스는 \(Int(sutja(r["count"]) ?? 0))개입니다."
        store.say(mapMal)
    }
}

// MARK: - 대본 살펴보기·원칙 16개 점검표 (웹 renderReady·principleTable)
struct JeomgeomView: View {
    @EnvironmentObject var store: TourStore
    var body: some View {
        List {
            if let k = store.kos {
                let iss = Wonchik.scriptIssues(k, store.meName)
                let L = Wonchik.issueLines(iss)
                let pt = Wonchik.principleTable(k, iss)
                let sm = Wonchik.summary(pt)
                Section("다음 할 일") { Text(Wonchik.nextTodo(k, store.meName)).font(.headline) }
                Section(L.isEmpty ? "기계가 잡아낼 것은 없습니다" : "살펴볼 것 \(L.count)가지") {
                    Jang(items: L.enumerated().map { Jul(id: "\($0.offset)", t: $0.element) }) { x in Text(x.t) }
                }
                Section("원칙 16개(통합본) 차례로 — 됨 \(sm.ok), 손볼 것 \(sm.bad), 사람이 판단 \(sm.person)") {
                    ForEach(Array(pt.enumerated()), id: \.offset) { Text($0.element.0) }
                }
                Section("사람이 읽어야 아는 것") {
                    ForEach(Array(Wonchik.askLines.enumerated()), id: \.offset) { Text($0.element) }
                }
            } else { KosEopseum() }
        }
        .navigationTitle("대본 살펴보기·원칙 16개")
    }
}

// MARK: - 지도 (북쪽이 위, 나침반과 거리 자) — 글로 된 방향 안내가 늘 함께 있음
struct KosJido: View {
    let pts: [(n: Int, name: String, c: CLLocationCoordinate2D)]
    var gijun: CLLocationCoordinate2D? = nil
    var seon: [(CLLocationCoordinate2D, CLLocationCoordinate2D)] = []
    var body: some View {
        Map(initialPosition: .automatic) {
            ForEach(Array(pts.enumerated()), id: \.offset) { _, p in
                Annotation(p.name, coordinate: p.c) {
                    Text("\(p.n)").font(.caption.bold()).foregroundStyle(.white).frame(width: 26, height: 26)
                        .background(Circle().fill(Color(red: 0.18, green: 0.36, blue: 0.31)))
                }
            }
            if pts.count > 1 { MapPolyline(coordinates: pts.map { $0.c }).stroke(Color(red: 0.18, green: 0.36, blue: 0.31), style: StrokeStyle(lineWidth: 3, dash: [6, 6])) }
            ForEach(Array(seon.enumerated()), id: \.offset) { _, s in
                MapPolyline(coordinates: [s.0, s.1]).stroke(Color(red: 0.55, green: 0.23, blue: 0.18), lineWidth: 3)
            }
            if let g = gijun {
                Annotation("기준 자리", coordinate: g) {
                    Text("기").font(.caption.bold()).foregroundStyle(.white).frame(width: 24, height: 24).background(Circle().fill(Color(red: 0.55, green: 0.23, blue: 0.18)))
                }
            }
        }
        .mapControls { MapCompass(); MapScaleView() }
        .frame(height: 320)
        .accessibilityHidden(true)
    }
}
func ll(_ p: (Double, Double)) -> CLLocationCoordinate2D { CLLocationCoordinate2D(latitude: p.0, longitude: p.1) }

/// 장소 평면도 (웹 1단계 「장소 평면도 (방향 잡기)」)
struct PyeongmyeonSection: View {
    @EnvironmentObject var store: TourStore
    let k: Kos
    var body: some View {
        Section("장소 평면도 (방향 잡기)") {
            Button("장소 자리 찾기 (장소 이름으로)") { Task { await chatgi() } }
            Button("지금 자리로 잡기 (위치)") {
                store.say("지금 자리를 재고 있습니다.")
                store.jari { l in
                    guard let l else { store.say("위치를 받지 못했습니다. 위치 허락을 확인해 주십시오."); return }
                    set(l.coordinate.latitude, l.coordinate.longitude, "지금 자리 (오차 \(Int(l.horizontalAccuracy))미터)")
                }
            }
            Text(Wonchik.pmText(k)).font(.callout)
            let pts = Wonchik.spotPts(k).map { (n: $0.i + 1, name: $0.s.name, c: ll($0.p)) }
            if !pts.isEmpty || Wonchik.pos(k) != nil { KosJido(pts: pts, gijun: Wonchik.pos(k).map(ll)) }
        }
    }
    private func set(_ la: Double, _ lo: Double, _ nm: String) {
        k.d["pos"] = ["la": TourStore.r6(la), "lo": TourStore.r6(lo)]; k.d["posName"] = nm
        store.bakkwim(); store.say(Wonchik.pmText(k))
    }
    private func chatgi() async {
        var q = (k.s("jisikQ").isEmpty ? (k.s("place").isEmpty ? k.name : k.s("place")) : k.s("jisikQ"))
        q = q.replacingOccurrences(of: "\\([^)]*\\)", with: " ", options: .regularExpression).replacingOccurrences(of: "코스", with: " ").trimmingCharacters(in: .whitespaces)
        let sp = Wonchik.spotPts(k)
        if q.isEmpty && sp.isEmpty { store.say("장소 이름을 먼저 적어 주십시오."); return }
        store.say("자리를 찾고 있습니다.")
        var c = URLComponents(string: "https://nominatim.openstreetmap.org/search")
        c?.queryItems = [URLQueryItem(name: "format", value: "json"), URLQueryItem(name: "limit", value: "1"), URLQueryItem(name: "accept-language", value: "ko"), URLQueryItem(name: "q", value: q)]
        if let u = c?.url {
            var r = URLRequest(url: u); r.setValue("LVDTourStudio/1.2 (lvd.ada.or.kr)", forHTTPHeaderField: "User-Agent")
            if let rd = try? await URLSession.shared.data(for: r), let a = try? JSONSerialization.jsonObject(with: rd.0) as? [[String: Any]], let f = a.first,
               let la = sutja(f["lat"]), let lo = sutja(f["lon"]) {
                set(la, lo, geulja(f["display_name"]).split(separator: ",").first.map { String($0).trimmingCharacters(in: .whitespaces) } ?? q); return
            }
        }
        if let f = sp.first { set(f.p.0, f.p.1, f.s.name); return }
        store.say("\"" + q + "\"의 자리를 찾지 못했습니다. 장소를 더 알려진 이름으로 적거나, 현장에서 지금 자리로 잡기를 눌러 주십시오.")
    }
}

/// 답사 지도 (웹 3단계 drawSurveyMap) — 지점 번호, 지점 잇는 점선, 사진 찍은 방향 붉은 선
struct SurveyMapView: View {
    @EnvironmentObject var store: TourStore
    var body: some View {
        List {
            if let k = store.kos {
                let sp = Wonchik.spotPts(k)
                let seon = SurveyMapView.seonOf(k)
                Section("지점 \(k.spots.count)곳 가운데 위치 있는 \(sp.count)곳, 사진 방향 \(seon.count)개") {
                    if sp.isEmpty && Wonchik.pos(k) == nil { Text("위치가 적힌 지점이 없어 지도를 그리지 못합니다. 현장에서 지점에 사진을 담으면 위치가 함께 적힙니다.") }
                    else { KosJido(pts: sp.map { (n: $0.i + 1, name: $0.s.name, c: ll($0.p)) }, gijun: Wonchik.pos(k).map(ll), seon: seon) }
                }
                Section("지점마다") { ForEach(Array(Wonchik.surveyText(k).enumerated()), id: \.offset) { Text($0.element) } }
            } else { KosEopseum() }
        }
        .navigationTitle("답사 지도")
    }
    /// 사진 찍은 방향 붉은 선 — 나침반 값(head)이 있으면 그것, 없으면 시계 방향 글자로
    static func seonOf(_ k: Kos) -> [(CLLocationCoordinate2D, CLLocationCoordinate2D)] {
        var seon: [(CLLocationCoordinate2D, CLLocationCoordinate2D)] = []
        for p in Wonchik.spotPts(k) {
            for sh in p.s.shots {
                var hd = sutja(sh["head"])
                let dir = geulja(sh["dir"])
                if hd == nil, let r = dir.range(of: "^\\d+", options: .regularExpression) { hd = Double((Int(dir[r]) ?? 0) % 12 * 30) }
                guard let h = hd else { continue }
                let rr = 12.0 / 111320, rad = h * Double.pi / 180
                seon.append((ll(p.p), CLLocationCoordinate2D(latitude: p.p.0 + rr * cos(rad), longitude: p.p.1 + rr * sin(rad) / cos(p.p.0 * Double.pi / 180))))
            }
        }
        return seon
    }
}

/// 걷는 시간 어림 (웹 3단계)
struct WalkSection: View {
    @EnvironmentObject var store: TourStore
    let k: Kos
    var body: some View {
        Section("걷는 시간 어림") {
            Stepper(value: Binding(get: { Int(sutja(k.d["walk"]) ?? 40) }, set: { k.d["walk"] = max(20, min(100, $0)); store.bakkwim() }), in: 20...100, step: 5) {
                Text("1분에 걷는 거리 \(Int(sutja(k.d["walk"]) ?? 40))미터 (서고 모이는 1분을 늘 더함)")
            }
            Button("위치로 걷는 시간 다시 어림") { let m = Wonchik.calcWalk(k); store.bakkwim(); store.say(m) }
            NavigationLink("답사 지도 보기") { SurveyMapView().dwiro() }
            NavigationLink("용어 뜻 찾기·우리 용어 사전") { DictView().dwiro() }
        }
    }
}

/// 테마투어 느낌 칸 여섯과 기억 칸 (갈래가 테마투어일 때)
struct TtSection: View {
    @EnvironmentObject var store: TourStore
    let s: Spot
    var body: some View {
        Section("테마투어 — 느낌으로 짓는 현장영상해설 (비어 있는 칸은 두셔도 됩니다)") {
            ForEach(Wonchik.TTKAN, id: \.id) { kk in
                VStack(alignment: .leading) {
                    Text(kk.nm).font(.footnote).accessibilityHidden(true)
                    TextField(kk.ex, text: Binding(get: { geulja((s.d["tt"] as? [String: Any])?[kk.id]) }, set: { v in
                        var tt = (s.d["tt"] as? [String: Any]) ?? [:]; tt[kk.id] = v; s.d["tt"] = tt; store.bakkwim()
                    }), axis: .vertical).accessibilityLabel(kk.nm)
                }
            }
        }
    }
}

/// 방향 도우미 — 고른 칸 끝에 붙임(웹 LASTBOX 와 같은 뜻)
struct DirSection: View {
    @EnvironmentObject var store: TourStore
    let k: Kos
    let s: Spot
    @State private var kan = "text"
    static let KAN: [(String, String)] = [("text", "대본"), ("to", "어디까지"), ("access", "이동 구간 안내"), ("touch", "만져 볼 것"), ("safe", "조심할 것"), ("gpos", "현장영상해설사 위치")]
    var body: some View {
        Section("방향 도우미 (고른 칸 끝에 붙습니다)") {
            Picker("붙일 칸", selection: $kan) { ForEach(DirSection.KAN, id: \.0) { Text($0.1).tag($0.0) } }
            Jang(items: Wonchik.DIRWORDS.map { Jul(id: $0, t: $0) }, per: 4) { w in
                Button(w.t + " 붙이기") {
                    let t = kan == "text" ? k.spotText(s) : s.s(kan)
                    let y = t + (t.isEmpty || t.hasSuffix(" ") ? "" : " ") + w.t
                    if kan == "text" { k.setSpotText(s, y) } else { s.d[kan] = y }
                    store.bakkwim(); store.say(w.t + "을(를) " + (DirSection.KAN.first { $0.0 == kan }?.1 ?? "") + " 칸에 붙였습니다.")
                }
            }
        }
    }
}

// MARK: - 용어 뜻 찾기·우리 용어 사전 (웹 4단계 dictFind·termAdd·termList)
struct DictView: View {
    @EnvironmentObject var store: TourStore
    @State private var q = ""
    @State private var gyeol: [[String: Any]] = []
    @State private var mal = ""
    @State private var tMal = ""
    @State private var tDdeut = ""
    @State private var tChul = ""
    @State private var terms: [[String: Any]] = []
    var body: some View {
        Form {
            AllimJul()
            Section("용어 뜻 찾기 (국립국어원 한국어기초사전·표준국어대사전)") {
                TextField("찾을 낱말", text: $q).onSubmit { Task { await chatgi() } }
                Button("찾기") { Task { await chatgi() } }
                if !mal.isEmpty { Text(mal) }
                ForEach(Array(gyeol.enumerated()), id: \.offset) { i, x in
                    Text(DictView.ddeutJul(i, x))
                }
                if let f = gyeol.first {
                    Button("찾은 뜻 우리 사전에 담아 두기 (아래 칸에 올림)") { tMal = geulja(f["mal"]); tDdeut = geulja(f["ddeut"]); tChul = geulja(f["sajeon"]); store.say("아래 칸에 올렸습니다. 시각장애인이 소리로 듣고 바로 그려지도록 우리 말로 고쳐 담으십시오.") }
                }
            }
            Section("우리 용어 사전 — 모든 코스와 모든 현장영상해설사가 함께 씀") {
                TextField("낱말", text: $tMal)
                TextField("뜻 (우리 말로)", text: $tDdeut, axis: .vertical)
                TextField("출처 (없어도 됩니다)", text: $tChul)
                Button("우리 사전에 담기") {
                    Task {
                        if Wonchik.bin(tMal) || Wonchik.bin(tDdeut) { store.say("낱말과 뜻을 모두 넣어 주십시오."); return }
                        let r = await TourAPI.call("add_term", ["mal": tMal.trimmingCharacters(in: .whitespaces), "ddeut": tDdeut.trimmingCharacters(in: .whitespaces), "chulcheo": tChul])
                        if TourAPI.ok(r) { let gc = (r["gochim"] as? Bool) == true; let n = Int(sutja(r["count"]) ?? 0); store.say(tMal + (gc ? "의 뜻을 고쳐 담았습니다." : "을(를) 우리 사전에 담았습니다.") + " 모두 \(n)개입니다."); tMal = ""; tDdeut = ""; tChul = ""; await mokrok() }
                        else { store.say(TourAPI.err(r)) }
                    }
                }
                Button("담아 둔 말 보기") { Task { await mokrok(); store.say("담아 둔 말 \(terms.count)개입니다.") } }
                Jang(items: terms.map { Mok(id: geulja($0["mal"]), j: $0) }) { t in
                    Text(t.id + " — " + geulja(t.j["ddeut"]))
                        .swipeActions { Button("지우기", role: .destructive) { Task { _ = await TourAPI.call("drop_term", ["mal": t.id]); await mokrok(); store.say(t.id + "을(를) 지웠습니다.") } } }
                }
            }
        }
        .navigationTitle("용어 뜻 찾기·우리 사전")
        .task { await mokrok() }
    }
    static func ddeutJul(_ i: Int, _ x: [String: Any]) -> String {
        var t = "\(i + 1). " + geulja(x["mal"])
        let pum = geulja(x["pum"]); if !pum.isEmpty { t += " (" + pum + ")" }
        t += " — " + geulja(x["ddeut"]) + " [" + geulja(x["sajeon"]) + "]"
        return t
    }
    private func chatgi() async {
        let w = q.trimmingCharacters(in: .whitespaces)
        if w.isEmpty { store.say("찾으실 낱말을 넣어 주십시오."); return }
        mal = w + " 찾는 중입니다."
        let j = await Bakkat.get("sajeon.php", ["q": w])
        gyeol = (j["list"] as? [[String: Any]]) ?? []
        if (j["ok"] as? Bool) != true { mal = geulja(j["error"]).isEmpty ? geulja(j["err"]) : geulja(j["error"]) }
        else if gyeol.isEmpty { mal = geulja(j["malsseum"]).isEmpty ? "두 사전에 그 말이 없습니다." : geulja(j["malsseum"]) }
        else { mal = "\(gyeol.count)가지 뜻이 있습니다." }
        store.say(mal + " " + gyeol.map { geulja($0["mal"]) + ". " + geulja($0["ddeut"]) }.joined(separator: " "))
    }
    private func mokrok() async {
        let r = await TourAPI.call("list_terms")
        if TourAPI.ok(r) { terms = ((r["list"] as? [[String: Any]]) ?? []) }
    }
}

// MARK: - 장소 지식 들이기 (뼈대 자동으로 짜기, 웹 jisik.html)
struct JisikView: View {
    @EnvironmentObject var store: TourStore
    @EnvironmentObject var nav: Nav
    @State private var q = ""
    @State private var tema = false
    @State private var mok = "110"
    @State private var D: [String: Any]? = nil
    @State private var jy: [String] = []
    @State private var order: [Int] = []
    @State private var mal = "장소 이름을 넣고 코스 뼈대 짜기를 누르십시오."
    @State private var chaeul = false
    @State private var dolnaem = false
    var list: [[String: Any]] { (D?["list"] as? [[String: Any]]) ?? [] }
    var body: some View {
        Form {
            AllimJul()
            Section {
                Text("장소 이름을 넣으면 국가유산청 자료에서 그 안에 무엇이 있는지, 어디에 있는지, 무슨 이야기가 있는지를 받아 와 코스 뼈대를 짜 드립니다. 테마투어를 고르면 시장·골목·마을길도 한국관광공사·카카오·오픈스트리트맵을 겹쳐 짭니다.").font(.footnote)
                TextField("장소 이름 (예: 신륵사, 경복궁)", text: $q)
                Picker("갈래", selection: $tema) { Text("국가유산·관광지 — 나라 자료로 짜기").tag(false); Text("테마투어 — 시장·골목·마을길·주제 코스").tag(true) }
                Picker("목표 시간", selection: $mok) { Text("1시간 30분").tag("90"); Text("1시간 50분").tag("110"); Text("2시간").tag("120") }
                Button("코스 뼈대 짜기") { Task { await run(nil) } }.font(.headline)
                Text(mal)
            }
            if !jy.isEmpty {
                Section("같은 이름이 여러 곳에 있습니다. 어느 곳입니까") {
                    ForEach(jy, id: \.self) { x in Button(x) { Task { await run(x) } } }
                }
            }
            if let j = D {
                let hap = hapGye()
                let mk = Int(sutja(j["mok"]) ?? 110)
                Section((geulja(j["goreun"]).isEmpty ? "" : geulja(j["goreun"]) + " ") + geulja(j["q"]) + " 코스 뼈대") {
                    Text(JisikView.hapJul(order.count, hap, mk))
                    Button("모두 차례대로 넣기") { let ppo = (j["ppo"] as? [String]) ?? []; order = list.indices.filter { !ppo.contains(geulja(list[$0]["ireum"])) }; store.say("목록 차례대로 \(order.count)곳을 모두 넣었습니다.") }
                    Button("모두 빼기 (다시 고르기)") { order = []; store.say("모두 뺐습니다. 가실 차례대로 누르십시오.") }
                    Text(jidoGeul()).font(.footnote)
                    let pts = order.compactMap { i -> (n: Int, name: String, c: CLLocationCoordinate2D)? in
                        guard let la = sutja(list[i]["la"]), let lo = sutja(list[i]["lo"]), la != 0 else { return nil }
                        return (n: (order.firstIndex(of: i) ?? 0) + 1, name: geulja(list[i]["ireum"]), c: CLLocationCoordinate2D(latitude: la, longitude: lo))
                    }
                    if !pts.isEmpty { KosJido(pts: pts) }
                }
                if let g = j["gw"] as? [String: Any], (g["found"] as? Bool) == true {
                    Section("관람 안내와 무장애 정보") {
                        let ln = [("여는 시간", "sigan"), ("쉬는 날", "hyugwan"), ("물어보실 곳", "munui"), ("주차", "jucha")].compactMap { geulja(g[$0.1]).isEmpty ? nil : $0.0 + " : " + geulja(g[$0.1]) }
                        if !ln.isEmpty { Text(ln.joined(separator: " / ")) }
                        if let u = g["uri"] as? [String], !u.isEmpty { Text("협회가 확인한 것 — " + u.joined(separator: " / ")) }
                        if let m = g["mujangae"] as? [String], !m.isEmpty { Text("무장애 정보 \(m.count)가지 — " + m.joined(separator: " / ")) } else { Text("이곳은 무장애 정보가 아직 등록되어 있지 않습니다.").font(.footnote) }
                    }
                }
                Section("들를 만한 곳 \(list.count)곳 — 가실 차례대로 누르십시오. 누르는 차례가 곧 코스 번호입니다") {
                    ForEach(Array(list.enumerated()), id: \.offset) { i, r in
                        let od = order.firstIndex(of: i)
                        VStack(alignment: .leading, spacing: 4) {
                            Button(julGeul(i, r, od)) {
                                if let k = order.firstIndex(of: i) { order.remove(at: k); store.say(geulja(r["ireum"]) + " 을(를) 뺐습니다. 뒤 번호가 당겨졌습니다.") }
                                else { order.append(i); store.say(geulja(r["ireum"]) + " 을(를) \(order.count)번째로 넣었습니다.") }
                            }.buttonStyle(.borderless)
                            Text(julDeo(r)).font(.footnote)
                            DisclosureGroup("현장영상해설 밑글 보기") {
                                Text(geulja(r["geul"]).isEmpty ? "(밑글이 없습니다)" : geulja(r["geul"])).font(.footnote)
                                if !geulja(r["gunggeul"]).isEmpty { Text("[궁궐·종묘 자료] " + geulja(r["gunggeul"])).font(.footnote) }
                                if let u = URL(string: geulja(r["sori"])), !geulja(r["sori"]).isEmpty { Button("국가유산청 음성 해설 들어 보기 (국가유산청 제공, 참고용)") { Teulgi.shared.teul(u, geulja(r["sori"])) } }
                            }
                        }
                    }
                }
                if let deo = j["deo"] as? [[String: Any]], !deo.isEmpty {
                    Section("이 안에서 더 볼 것 \(deo.count)곳 — 위치가 안 나와 지점으로는 못 넣고 밑글로만 담깁니다") {
                        ForEach(Array(deo.enumerated()), id: \.offset) { _, x in DisclosureGroup(geulja(x["ireum"])) { Text(geulja(x["geul"])).font(.footnote) } }
                    }
                }
                Section {
                    Button("이 뼈대로 코스 만들기") {
                        if order.isEmpty { store.say("넣은 곳이 하나도 없습니다. 가실 차례대로 목록의 이름을 눌러 넣어 주십시오."); return }
                        if let k = store.kos, !k.isGyobon { if k.spots.isEmpty { Task { await mandeulgi(into: true) } } else { chaeul = true } }
                        else { Task { await mandeulgi(into: false) } }
                    }.font(.headline)
                    NavigationLink("우리가 확인한 것 채워 넣기") { UrijaryoView(jangso: geulja(j["q"])).dwiro() }
                }
            }
        }
        .navigationTitle("장소 지식 들이기")
        .onAppear { if q.isEmpty, let k = store.kos { q = k.s("jisikQ").isEmpty ? k.s("place") : k.s("jisikQ"); tema = Wonchik.isTheme(k) } }
        .confirmationDialog("지금 작업 중인 코스에 지점이 이미 있습니다. 어떻게 할까요?", isPresented: $chaeul, titleVisibility: .visible) {
            Button("지금 코스에 이 뼈대로 바꿔 넣기") { Task { await mandeulgi(into: true) } }
            Button("새 코스를 따로 만들기") { Task { await mandeulgi(into: false) } }
            Button("그만두기", role: .cancel) {}
        }
    }
    static func hapJul(_ n: Int, _ hap: Int, _ mk: Int) -> String {
        let dwi = hap > mk ? "\(hap - mk)분 넘칩니다." : "목표 안에 들어옵니다."
        return "들르는 곳 \(n)곳, 모두 " + fmtMin(Double(hap)) + "입니다. 목표는 \(mk)분입니다. " + dwi
    }
    private func hapGye() -> Int {
        var h = 10
        for i in order { h += Int(sutja(list[i]["meomu"]) ?? 0); h += Int(sutja(list[i]["geot"]) ?? 0) }
        return h
    }
    private func apGeori(_ r: [String: Any]) -> String {
        let g = geulja(r["geori"]); if g.isEmpty { return "" }
        var t = "앞 지점에서 "
        let b = geulja(r["bang"]); if !b.isEmpty { t += b + "쪽으로 " }
        t += g + "미터"
        let sg = geulja(r["sigye"]); if !sg.isEmpty { t += ", " + sg }
        return t
    }
    private func julGeul(_ i: Int, _ r: [String: Any], _ od: Int?) -> String {
        var t = ""
        if let od { t += "\(od + 1)번째 · " }
        t += geulja(r["ireum"]) + " · " + geulja(r["jong"])
        t += " · 머무는 시간 \(Int(sutja(r["meomu"]) ?? 0))분"
        let nj = (r["sajins"] as? [Any])?.count ?? 0
        if nj > 0 { t += " · 사진 \(nj)장" }
        if !geulja(r["sori"]).isEmpty { t += " · 음성 해설 있음" }
        t += od != nil ? " · 넣음, 누르면 뺍니다" : " · 누르면 \(order.count + 1)번째로 넣습니다"
        return t
    }
    private func julDeo(_ r: [String: Any]) -> String {
        var a: [String] = [geulja(r["sidae"]), geulja(r["juso"]), apGeori(r)]
        let ch = geulja(r["chulcheo"]); if !ch.isEmpty { a.append("출처 " + ch) }
        return a.filter { !$0.isEmpty }.joined(separator: " · ")
    }
    private func jjul(_ s0: String) -> String {
        let s = s0.replacingOccurrences(of: "\\([^)]*\\)", with: " ", options: .regularExpression).replacingOccurrences(of: "코스", with: " ").replacingOccurrences(of: "[/,·]", with: " ", options: .regularExpression)
        let w = s.split(separator: " ").map(String.init).filter { x in
            if x.isEmpty || x.range(of: "[0-9]", options: .regularExpression) != nil { return false }
            if x.count <= 4 && x.range(of: "(도|시|군|구|동|읍|면|리)$", options: .regularExpression) != nil { return false }
            return true
        }
        return w.last ?? s
    }
    private func run(_ jy0: String?) async {
        let q0 = q.trimmingCharacters(in: .whitespaces)
        if q0.isEmpty { store.say("장소 이름을 넣어 주십시오."); return }
        mal = tema ? "한국관광공사·카카오·오픈스트리트맵 자료를 겹쳐 받아 오고 있습니다. 처음에는 조금 걸립니다." : "국가유산청 자료를 받아 오고 있습니다. 처음에는 조금 걸립니다."
        store.say(mal); D = nil; jy = []
        var p = ["a": tema ? "tema" : "chatgi", "q": q0, "mok": mok]
        if let jy0 { p["jy"] = jy0 }
        let j = await Bakkat.get("jisik.php", p)
        guard (j["ok"] as? Bool) == true else {
            let q2 = jjul(q0)
            if jy0 == nil && !dolnaem && !q2.isEmpty && q2 != q0 { dolnaem = true; q = q2; store.say("\"" + q0 + "\"(으)로는 찾지 못해 \"" + q2 + "\"(으)로 다시 찾습니다."); await run(nil); return }
            mal = geulja(j["err"]).isEmpty ? "받아 오지 못했습니다." : geulja(j["err"]); store.say(mal); return
        }
        dolnaem = false
        let jys = (j["jiyeoks"] as? [String]) ?? []
        if jy0 == nil && jys.count > 1 { jy = jys; mal = "같은 이름이 여러 곳에 있습니다. 어느 곳인지 골라 주십시오."; store.say(mal); return }
        D = j; order = []
        mal = "들를 만한 곳 \(list.count)곳을 찾았습니다. 가실 차례대로 하나씩 누르십시오. 한꺼번에 넣으시려면 모두 차례대로 넣기를 누르십시오."
        store.say(mal)
    }
    private func jidoGeul() -> String {
        let pts = list.indices.compactMap { i -> (Int, (Double, Double))? in
            guard let la = sutja(list[i]["la"]), let lo = sutja(list[i]["lo"]), la != 0 else { return nil }; return (i, (la, lo)) }
        guard !pts.isEmpty else { return "위치가 있는 곳이 없어 지도를 그리지 못합니다." }
        let baseI = order.first(where: { i in pts.contains { $0.0 == i } }) ?? pts[0].0
        guard let base = pts.first(where: { $0.0 == baseI })?.1 else { return "" }
        let lines = pts.map { p -> String in
            let od = order.firstIndex(of: p.0)
            return (od.map { "\($0 + 1)번째 " } ?? "") + geulja(list[p.0]["ireum"]) + " — " + (p.0 == baseI ? "기준 자리" : Wonchik.clock(Wonchik.brg(base, p.1)) + " 방향 \(Wonchik.meter(base, p.1))미터")
        }
        var warn: [String] = []
        let op = order.compactMap { i in pts.first { $0.0 == i }.map { (i, $0.1) } }
        if op.count >= 3 { for k in 2..<op.count {
            let a = op[k - 2].1, b = op[k - 1].1, c = op[k].1
            let t = abs(((Wonchik.brg(b, c) - Wonchik.brg(a, b)) + 540).truncatingRemainder(dividingBy: 360) - 180)
            if t > 150 { warn.append("\(k)번째 " + geulja(list[op[k - 1].0]["ireum"]) + "에서 \(k + 1)번째 " + geulja(list[op[k].0]["ireum"]) + "(으)로 가는 길은 왔던 방향으로 되돌아갑니다.") }
        } }
        return (order.isEmpty ? "아직 고르지 않으셨습니다. " : "첫 번째 지점에서 본 자리입니다. ") + "북쪽을 12시로 봅니다. " + lines.joined(separator: " / ") + (warn.isEmpty ? "" : " ★ " + warn.joined(separator: " ")) + " (지도: 오픈스트리트맵 기여자, ODbL)"
    }
    private func mandeulgi(into: Bool) async {
        guard let j = D else { return }
        let golf = order.map { list[$0] }
        let irum = (geulja(j["goreun"]).isEmpty ? "" : geulja(j["goreun"]) + " ") + geulja(j["q"]) + " 코스"
        var c: [String: Any]
        if into, let k = store.kos { c = k.json() }
        else {
            let a = await TourAPI.call("new_course", ["name": irum])
            guard TourAPI.ok(a), let cc = a["course"] as? [String: Any] else { store.say(TourAPI.err(a)); return }
            c = cc
        }
        if Wonchik.bin(geulja(c["name"])) { c["name"] = irum }
        if Wonchik.bin(geulja(c["place"])) { c["place"] = geulja(golf[0]["juso"]).isEmpty ? geulja(j["goreun"]) + " " + geulja(j["q"]) : geulja(golf[0]["juso"]) }
        c["jisikQ"] = geulja(j["q"]); c["jisikJy"] = geulja(j["goreun"])
        if c["pos"] == nil, let la = sutja(golf[0]["la"]), let lo = sutja(golf[0]["lo"]), la != 0 { c["pos"] = ["la": la, "lo": lo]; c["posName"] = geulja(golf[0]["ireum"]) }
        let isTema = (sutja(j["tema"]) ?? 0) > 0
        c["note"] = isTema ? "나라 자료가 없는 곳이라 한국관광공사·카카오·오픈스트리트맵을 겹쳐 짠 테마투어 뼈대입니다. 느낌 칸의 짐작은 반드시 현장에서 확인해 고쳐 쓰고, 계단과 방향과 만져 볼 것도 현장에서 채워 주십시오." : "나라가 열어 둔 자료로 짠 뼈대입니다. 계단과 방향과 만져 볼 것은 현장에서 채워 주십시오."
        if isTema { c["field"] = "테마투어 분야" }
        var acc = ""
        if let g = j["gw"] as? [String: Any], (g["found"] as? Bool) == true {
            for (nm, key) in [("여는 시간", "sigan"), ("쉬는 날", "hyugwan"), ("물어보실 곳", "munui"), ("주차", "jucha")] where !geulja(g[key]).isEmpty { acc += nm + " : " + geulja(g[key]) + "\n" }
            if let u = g["uri"] as? [String], !u.isEmpty { acc += "\n[협회가 확인한 것]\n" + u.map { "· " + $0 + "\n" }.joined() }
            if let m = g["mujangae"] as? [String], !m.isEmpty { acc += "\n[무장애 정보 — 한국관광공사]\n" + m.map { "· " + $0 + "\n" }.joined() }
        }
        acc += "\n[실측 확인] 오시는 길, 대중교통, 매표소까지의 동선, 계단 칸 수는 현장에서 채워 주십시오.\n"
        c["access"] = acc
        var his = ""
        for (k, r) in golf.enumerated() {
            his += "\(k + 1). " + geulja(r["ireum"]) + " (" + geulja(r["jong"]) + (geulja(r["sidae"]).isEmpty ? "" : " · " + geulja(r["sidae"])) + ")\n"
            his += (geulja(r["geul"]).isEmpty ? "(밑글이 없습니다)" : geulja(r["geul"])) + "\n"
            if !geulja(r["gunggeul"]).isEmpty { his += "[궁궐·종묘 자료] " + geulja(r["gunggeul"]) + "\n" }
            if let sj = r["sajins"] as? [[String: Any]], !sj.isEmpty {
                let seol: [String] = sj.map { (x: [String: Any]) -> String in let v = geulja(x["seol"]); return v.isEmpty ? "설명 없음" : v }
                his += "[사진 \(sj.count)장] " + seol.joined(separator: " / ") + "\n"
            }
            if !geulja(r["sori"]).isEmpty { his += "[국가유산청 음성 해설] " + geulja(r["sori"]) + "\n" }
            his += "\n"
        }
        if let deo = j["deo"] as? [[String: Any]], !deo.isEmpty {
            his += "\n[이 안에서 더 볼 것 — 위치가 안 나와 지점으로 넣지 못한 곳]\n\n"
            for x in deo { his += "· " + geulja(x["ireum"]) + "\n" + geulja(x["geul"]) + "\n\n" }
        }
        c["history"] = (isTema ? "[테마투어 — 한국관광공사·카카오·오픈스트리트맵 자료로 짠 뼈대입니다. 자료가 없는 곳이니 현장의 소리·냄새·발밑·손·볕과 바람·기척과 현장영상해설사의 기억으로 짓고, 아래는 참고로만 쓰십시오.]\n\n" : "[국가유산청 자료 — 참고 밑글입니다. 그대로 읽지 마시고 우리 원칙으로 다시 쓰십시오.]\n\n") + his
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"
        c["refs"] = "국가유산청 국가유산포털(www.khs.go.kr) 국가유산 정보·해설문·궁궐 정보, 공공누리 제1유형(출처 표시) / 한국관광공사 관광정보(api.visitkorea.or.kr) 국문 관광정보·무장애 여행 정보, 공공누리 제1유형(출처 표시) / 위치: 오픈스트리트맵 기여자(ODbL) (받은 날 " + f.string(from: Date()) + ")"
        let t0 = String(Int(Date().timeIntervalSince1970), radix: 36)
        c["spots"] = golf.enumerated().map { (k, r) -> [String: Any] in
            var sp: [String: Any] = ["id": "js" + t0 + "\(k)", "name": geulja(r["ireum"]), "stay": Int(sutja(r["meomu"]) ?? 10), "move": Int(sutja(r["geot"]) ?? 0),
                "access": (apGeori(r).isEmpty ? "" : apGeori(r) + ". ") + "[실측 확인] 계단·경사·바닥 상태와 걸음 수를 현장에서 적으십시오.",
                "touch": "", "safe": "", "gpos": "", "text": "", "lat": sutja(r["la"]) ?? 0, "lon": sutja(r["lo"]) ?? 0, "photos": [Any](), "shots": [Any]()]
            if let tt = r["tt"] as? [String: Any], !tt.isEmpty { sp["tt"] = tt }
            return sp
        }
        c.removeValue(forKey: "rangeV")
        let b = await TourAPI.call("save_course", ["course": c, "base": geulja(c["updated"]), "force": 1])
        guard TourAPI.ok(b) else { store.say(TourAPI.err(b)); return }
        await store.loadCourses()
        if await store.open(geulja(c["id"])) {
            store.say((into ? "지금 코스에 뼈대를 채워 넣었습니다." : geulja(c["name"]) + " 이(가) 만들어졌습니다.") + " 들르는 곳 \(golf.count)곳, 고르신 차례대로 번호를 붙였습니다. 지점 탭에서 보십시오.")
            nav.tab = 2
        }
    }
}

// MARK: - 우리가 확인한 것 채워 넣기 (웹 urijaryo.html)
struct UrijaryoView: View {
    @EnvironmentObject var store: TourStore
    var jangso = ""
    @State private var tel = UserDefaults.standard.string(forKey: "bfbPhone") ?? ""
    @State private var q = ""
    @State private var yeollim = false
    @State private var v: [String: String] = [:]
    @State private var mal = ""
    static let KAN: [(String, String)] = [("sigan", "여는 시간"), ("hyugwan", "쉬는 날"), ("munui", "물어보실 곳"), ("jucha", "주차"), ("gil", "오시는 길"), ("gyedan", "계단과 턱"), ("jeomja", "점자블록"), ("eumseong", "음성 안내"), ("manjyeo", "만져 볼 것"), ("josim", "조심할 것"), ("etc", "그 밖에")]
    var body: some View {
        Form {
            AllimJul()
            Section {
                Text("나라 자료에 비어 있는 칸을 협회가 채워 쌓는 자리입니다. 여기 채운 것은 나라 자료보다 앞서 나오고, 투어스튜디오와 더뷰 두 곳에서 함께 쓰입니다. 협회 현장영상해설사 명단에 있는 전화번호라야 저장됩니다.").font(.footnote)
                TextField("현장영상해설사 전화번호", text: $tel).keyboardType(.phonePad)
                TextField("장소 이름 (투어스튜디오·더뷰에서 쓰는 이름과 똑같이)", text: $q)
                Button("열기") { Task { await yeolgi() } }
                if !mal.isEmpty { Text(mal) }
            }
            if yeollim {
                Section(q + " — 아는 것만 채우십시오. 빈칸은 나라 자료가 그대로 나옵니다") {
                    ForEach(UrijaryoView.KAN, id: \.0) { k in
                        VStack(alignment: .leading) {
                            Text(k.1).font(.footnote).accessibilityHidden(true)
                            TextField(k.1, text: Binding(get: { v[k.0] ?? "" }, set: { v[k.0] = $0 }), axis: .vertical).accessibilityLabel(k.1)
                        }
                    }
                    Button("이대로 저장하기") { Task { await jeojang() } }.font(.headline)
                    Button("다른 장소") { yeollim = false; q = ""; mal = "다른 장소 이름을 넣어 주십시오." }
                }
            }
        }
        .navigationTitle("우리가 확인한 것")
        .onAppear { if q.isEmpty { q = jangso.isEmpty ? (store.kos?.s("place") ?? "") : jangso } }
    }
    private func yeolgi() async {
        let t = tel.filter { $0.isNumber }
        if t.count < 9 { mal = "전화번호를 다시 넣어 주십시오."; store.say(mal); return }
        if Wonchik.bin(q) { mal = "장소 이름을 넣어 주십시오."; store.say(mal); return }
        UserDefaults.standard.set(t, forKey: "bfbPhone")
        let j = await Bakkat.get("urijaryo.php", ["a": "get", "jangso": q.trimmingCharacters(in: .whitespaces)])
        v = [:]
        if (j["itda"] as? Bool) == true, let jr = j["jaryo"] as? [String: Any] {
            for k in UrijaryoView.KAN { v[k.0] = geulja(jr[k.0]) }
            mal = "이미 채워 둔 것이 있어 불러왔습니다. 적은 이는 " + geulja(jr["jeokeun"]) + ", " + geulja(jr["ttae"]) + "입니다."
        } else { mal = "아직 채운 것이 없습니다. 아는 것부터 적어 주십시오." }
        yeollim = true; store.say(mal)
    }
    private func jeojang() async {
        var f = ["tel": tel.filter { $0.isNumber }, "jangso": q.trimmingCharacters(in: .whitespaces)]
        for k in UrijaryoView.KAN { f[k.0] = (v[k.0] ?? "").trimmingCharacters(in: .whitespacesAndNewlines) }
        let j = await Bakkat.post("urijaryo.php", ["a": "set"], f)
        mal = (j["ok"] as? Bool) == true ? q + " 자료를 저장했습니다. 이제 투어스튜디오와 더뷰에서 이 내용이 먼저 나옵니다." : (geulja(j["err"]).isEmpty ? "저장하지 못했습니다." : geulja(j["err"]))
        store.say(mal)
    }
}

// MARK: - 승인본 현장영상해설 화면 (웹 lvdts_hyeonjang.html)
struct HyeonjangView: View {
    @EnvironmentObject var store: TourStore
    let entry: [String: Any]
    @State private var c: [String: Any]? = nil
    @State private var idx = -1
    @State private var mal = "여는 중입니다."
    @State private var left = 0
    @State private var timer: Timer? = nil
    @State private var kkeojim = false
    @State private var ilkgi = false
    @State private var ttara = false
    @State private var ttaraJul = ""
    @State private var lastM: Int? = nil
    @State private var lastSaid = Date.distantPast
    @State private var tk = ""
    @StateObject private var jg = Jarigi()
    @State private var offSince: Date? = nil
    @State private var offOut = false
    @State private var offSaid = Date.distantPast
    @AccessibilityFocusState private var meori: Bool
    var spots: [[String: Any]] { (c?["spots"] as? [[String: Any]]) ?? [] }
    var owner: String { geulja(entry["owner"]) }
    var cid: String { geulja(entry["cid"]) }
    var ver: String { geulja(entry["ver"]) }
    var body: some View {
        List {
            AllimJul()
            if let c {
                Section {
                    Text(geulja(c["name"])).font(.title3.bold()).accessibilityAddTraits(.isHeader)
                    Text(meoriJul(c)).font(.footnote)
                }
                Section("현장에서 쓰는 단추") {
                    Toggle("화면 안 꺼지게 하기", isOn: $kkeojim).onChange(of: kkeojim) { _, v in UIApplication.shared.isIdleTimerDisabled = v; store.say(v ? "현장영상해설하는 동안 화면이 꺼지지 않습니다." : "화면 켜 두기를 껐습니다.") }
                    Toggle("대본 읽어 주기 (지점을 옮길 때마다)", isOn: $ilkgi).onChange(of: ilkgi) { _, v in if v { ilgeojugi() } else { Moksori.shared.meomchum() }; store.say(v ? "대본을 읽어 드립니다. 지점을 옮길 때마다 읽습니다." : "읽어 주기를 껐습니다.") }
                    Toggle("지금 자리로 따라가기 (다음 지점까지 거리·방향, 5미터 안에 들면 저절로 넘어감)", isOn: $ttara).onChange(of: ttara) { _, v in ttaraOn(v) }
                    if !ttaraJul.isEmpty { Text(ttaraJul) }
                    Toggle("코스 벗어남 알림 (따라가기를 켰을 때)", isOn: Binding(get: { Gwan.shared.offOn }, set: { Gwan.shared.offOn = $0 }))
                }
                omgim
                if idx < 0 {
                    Section("앞머리 — 오프닝") {
                        ForEach([("1번 첫 만남 인사말", "first"), ("오프닝", "opening"), ("3번 장소 역사", "history"), ("3번 위치와 찾아오는 길", "access"), ("3번 규모와 형태", "scale"), ("3번 전체 포인트", "points"), ("5번 촉각지도 안내", "tactile"), ("6번 출발 전 안전 고지", "safety")], id: \.1) { x in
                            if !Wonchik.bin(geulja(c[x.1])) { DisclosureGroup(x.0) { Text(geulja(c[x.1])).font(.title3) } }
                        }
                    }
                } else if idx < spots.count {
                    let s = spots[idx]
                    Section {
                        Text(jijeomJemok(s)).font(.title2.bold()).accessibilityAddTraits(.isHeader).accessibilityFocused($meori)
                        Text(hjSigan(idx, s)).font(.footnote)
                        Text(Wonchik.bin(geulja(s["text"])) ? "(이 지점의 대본이 비어 있습니다.)" : geulja(s["text"])).font(.title3).textSelection(.enabled)
                        Button(timer == nil ? "머무는 시간 재기 시작" : "시간 재기 멈춤 — 남은 시간 \(left / 60)분 \(left % 60)초") { timer == nil ? sijakTimer(s) : meomTimer(true) }
                    }
                    Section("펼쳐 보기 — 이동 구간 안내·만져 볼 것·조심할 것·사진·소리") {
                        ForEach([("이동 구간 안내", "access"), ("만져 볼 것", "touch"), ("조심할 것", "safe"), ("현장영상해설사 위치", "gpos")], id: \.1) { x in
                            if !Wonchik.bin(geulja(s[x.1])) { DisclosureGroup(x.0) { Text(geulja(s[x.1])) } }
                        }
                        if let tt = s["tt"] as? [String: Any], !tt.isEmpty {
                            DisclosureGroup("느낌·기억 (테마투어)") { ForEach(Wonchik.TTKAN, id: \.id) { kk in if !geulja(tt[kk.id]).isEmpty { Text(kk.nm + ": " + geulja(tt[kk.id])) } } }
                        }
                        let shots = ((s["shots"] as? [[String: Any]]) ?? []).filter { !geulja($0["f"]).isEmpty }
                        if !shots.isEmpty && !tk.isEmpty {
                            DisclosureGroup("답사 사진 \(shots.count)장") {
                                ForEach(Array(shots.enumerated()), id: \.offset) { i, p in
                                    if let u = Gwan.juso(["action": "final_photo", "o": owner, "c": cid, "v": ver, "f": geulja(p["f"]), "tk": tk]) {
                                        AsyncImage(url: u) { im in im.resizable().scaledToFit() } placeholder: { ProgressView() }
                                            .accessibilityLabel("\(i + 1)번째 사진, " + geulja(p["dir"]))
                                    }
                                }
                            }
                        }
                        let memos = ((s["memos"] as? [[String: Any]]) ?? []).filter { !geulja($0["f"]).isEmpty }
                        if !memos.isEmpty && !tk.isEmpty {
                            ForEach(Array(memos.enumerated()), id: \.offset) { i, m in
                                Button("\(i + 1)번 현장 소리 듣기 — \(Int(sutja(m["sec"]) ?? 0))초") {
                                    if let u = Gwan.juso(["action": "final_audio", "o": owner, "c": cid, "v": ver, "f": geulja(m["f"]), "tk": tk]) { Teulgi.shared.teul(u, geulja(m["f"])) }
                                }
                            }
                        }
                    }
                } else {
                    Section("클로징") { Text(Wonchik.bin(geulja(c["closing"])) ? "(클로징이 비어 있습니다.)" : geulja(c["closing"])).font(.title3) }
                }
                omgim
            } else { Section { Text(mal) } }
        }
        .navigationTitle("현장영상해설 화면")
        .task { await yeolgi() }
        .onDisappear { meomTimer(false); ttaraOn(false); UIApplication.shared.isIdleTimerDisabled = false; Moksori.shared.meomchum(); Teulgi.shared.meomchum() }
    }
    @ViewBuilder var omgim: some View {
        Section {
            if idx >= 0 { Button(idx == 0 ? "앞머리로" : "앞 지점") { boyeo(idx - 1) } }
            if idx < spots.count { Button(daeumMal()) { boyeo(idx + 1) }.font(.headline) }
        }
    }
    private func meoriJul(_ c: [String: Any]) -> String {
        var t = geulja(c["place"]) + " / 집합 " + geulja(c["meet"])
        t += " / 승인번호 " + geulja(entry["no"]) + " / 지점 \(spots.count)곳"
        return t
    }
    private func jijeomJemok(_ s: [String: Any]) -> String { "\(idx + 1)지점 " + geulja(s["name"]) + " (\(idx + 1)/\(spots.count))" }
    private func daeumMal() -> String {
        if idx + 1 < spots.count { return "다음 지점 — \(idx + 2)지점 " + geulja(spots[idx + 1]["name"]) }
        return idx + 1 == spots.count ? "클로징으로" : "1지점으로"
    }
    private func hjSigan(_ i: Int, _ s: [String: Any]) -> String {
        var t = hjRange(i, s) + " / 머무는 시간 \(Int(sutja(s["stay"]) ?? 0))분"
        let mv = Int(sutja(s["move"]) ?? 0); if mv > 0 { t += " / 다음 지점까지 걷기 \(mv)분" }
        let gp = geulja(s["gpos"]); if !gp.isEmpty { t += " / 현장영상해설사 위치: " + gp }
        return t
    }
    private func hjRange(_ i: Int, _ s: [String: Any]) -> String {
        let fr: String
        if i == 0 { let p = (c?["openSpot"] as? [String: Any]); let pt = geulja(p?["to"]).trimmingCharacters(in: .whitespaces); fr = "오프닝 지점의 현장영상해설을 마치고 출발하는 자리" + (pt.isEmpty ? "" : " — " + pt) }
        else { let p = spots[i - 1]; let pt = geulja(p["to"]).trimmingCharacters(in: .whitespaces); fr = "\(i)지점 " + geulja(p["name"]) + "의 현장영상해설을 마치고 출발하는 자리" + (pt.isEmpty ? "" : " — " + pt) }
        return "어디서부터: " + fr + " / 어디까지: " + (geulja(s["to"]).trimmingCharacters(in: .whitespaces).isEmpty ? "적지 않음" : geulja(s["to"]))
    }
    private var cacheURL: URL {
        let d = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("hyeonjang", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d.appendingPathComponent(owner + "_" + cid + "_" + ver + ".json")
    }
    private func yeolgi() async {
        tk = await TourAPI.needTicket()
        let r = await TourAPI.call("final_get", ["owner": owner, "c": cid, "v": Int(sutja(entry["ver"]) ?? 0)])
        if TourAPI.ok(r), let cc = r["course"] as? [String: Any] {
            c = cc
            if let d = try? JSONSerialization.data(withJSONObject: cc) { try? d.write(to: cacheURL, options: .atomic) }
            store.say(geulja(cc["name"]) + "을(를) 열었습니다. 지점 \(spots.count)곳입니다. 앞머리부터 보십시오.")
        } else if let d = try? Data(contentsOf: cacheURL), let cc = try? JSONSerialization.jsonObject(with: d) as? [String: Any] {
            c = cc; store.say("통신이 안 되어 이 폰에 담아 둔 것으로 엽니다.")
        } else { mal = TourAPI.err(r); store.say(mal) }
    }
    private func boyeo(_ i: Int) {
        guard i >= -1 && i <= spots.count else { return }
        idx = i; meomTimer(false)
        if i >= 0 && i < spots.count { store.say("\(i + 1)지점 " + geulja(spots[i]["name"]) + "입니다.") } else if i == spots.count { store.say("클로징입니다.") } else { store.say("앞머리입니다.") }
        if ilkgi { ilgeojugi() }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { meori = true }
    }
    private func ilgeojugi() {
        if idx >= 0 && idx < spots.count { Moksori.shared.ilkgi(geulja(spots[idx]["text"])) }
        else if idx == spots.count { Moksori.shared.ilkgi(geulja(c?["closing"])) }
        else { Moksori.shared.ilkgi(geulja(c?["opening"])) }
    }
    private func sijakTimer(_ s: [String: Any]) {
        left = max(1, Int(((sutja(s["stay"]) ?? 1) * 60).rounded()))
        store.say("시간을 재기 시작합니다. \(left / 60)분입니다.")
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { _ in
            Task { @MainActor in
                left -= 1
                if left == 60 { store.say("1분 남았습니다.") }
                if left <= 0 { meomTimer(false); Gwan.deureul(); Gwan.ppi(); store.say("머무는 시간이 다 되었습니다. 다음 지점으로 옮기십시오.") }
            }
        }
    }
    private func meomTimer(_ tell: Bool) { timer?.invalidate(); timer = nil; if tell { store.say("시간 재기를 멈췄습니다.") } }
    private func spotLL(_ s: [String: Any]) -> (Double, Double)? {
        guard let la = sutja(s["lat"]), let lo = sutja(s["lng"]) ?? sutja(s["lon"]) else { return nil }; return (la, lo)
    }
    private func ttaraOn(_ on: Bool) {
        if !on { jg.meomchum(); jg.bada = nil; ttaraJul = ""; return }
        if !spots.contains(where: { spotLL($0) != nil }) { store.say("위치가 적힌 지점이 없어 따라갈 수 없습니다."); ttara = false; return }
        jg.bada = { l in Task { @MainActor in jari(l) } }
        jg.sijak(); store.say("지금 자리로 따라갑니다. 다음 지점까지 거리와 방향을 말씀드립니다.")
    }
    private func dirWord(_ brg: Double, _ heading: Double?) -> String {
        if let h = heading { var hr = Int((((brg - h) + 360).truncatingRemainder(dividingBy: 360) / 30).rounded()) % 12; if hr == 0 { hr = 12 }; return "\(hr)시 방향" }
        return "북쪽을 12시로 " + Wonchik.clock(brg) + " 방향"
    }
    private func jari(_ l: CLLocation) {
        guard ttara else { return }
        let me = (l.coordinate.latitude, l.coordinate.longitude), acc = Int(l.horizontalAccuracy)
        let heading: Double? = (l.course >= 0 && l.speed > 0.4) ? l.course : nil
        var k = idx + 1; while k < spots.count && spotLL(spots[k]) == nil { k += 1 }
        // 코스 벗어남
        if Gwan.shared.offOn {
            let pts = spots.compactMap { spotLL($0) }
            if !pts.isEmpty {
                let r = Wonchik.offDist(me, pts)
                if Double(r.d) > Double(Gwan.shared.offM) + min(Double(max(acc, 0)), 25) {
                    if offSince == nil { offSince = Date() }
                    if let s0 = offSince, Date().timeIntervalSince(s0) >= Double(Gwan.shared.offS) && (!offOut || Date().timeIntervalSince(offSaid) > 60) {
                        offOut = true; offSaid = Date()
                        var t = "코스에서 \(r.d)미터 벗어났습니다."
                        if let n = r.near { t += " 가장 가까운 지점은 \(n + 1)번, \(Wonchik.meter(me, pts[n]))미터 " + dirWord(Wonchik.brg(me, pts[n]), heading) + "." }
                        store.say(t); Gwan.deureul(); Gwan.ppi()
                    }
                } else { offSince = nil; if offOut { offOut = false; store.say("코스로 돌아왔습니다.") } }
            }
        }
        guard k < spots.count, let tg = spotLL(spots[k]) else { ttaraJul = "더 갈 지점이 없습니다."; return }
        let m = Wonchik.meter(me, tg), brg = Wonchik.brg(me, tg)
        let line = "다음 \(k + 1)번 " + geulja(spots[k]["name"]) + "까지 \(m)미터, " + dirWord(brg, heading) + (acc > 20 ? " (위치 오차 \(acc)미터)" : "")
        ttaraJul = line
        if m <= 5 || (m <= 12 && acc <= 8 && lastM != nil && m >= lastM!) {
            Gwan.deureul(); Gwan.ppi(); boyeo(k)
            store.say("\(k + 1)번 " + geulja(spots[k]["name"]) + "에 도착했습니다. 대본을 띄웠습니다."); lastM = nil; lastSaid = Date(); return
        }
        let now = Date()
        if now.timeIntervalSince(lastSaid) > 20 || (lastM != nil && abs(lastM! - m) >= 15) || (m <= 20 && now.timeIntervalSince(lastSaid) > 6) { store.say(line); lastSaid = now }
        lastM = m
    }
}

// MARK: - 점검 (카메라·위치·마이크·받아쓰기·나침반·나스)
struct DiagView: View {
    @EnvironmentObject var store: TourStore
    @State private var rows: [String] = []
    var body: some View {
        List {
            Section("현장에 나가시기 전에 한 번 눌러 보십시오") {
                Button("다시 살피기") { Task { await salpigi() } }
                ForEach(Array(rows.enumerated()), id: \.offset) { Text($0.element) }
            }
        }
        .navigationTitle("점검")
        .task { await salpigi() }
    }
    private func salpigi() async {
        var R: [String] = []
        func st(_ ok: Bool?, _ yes: String, _ no: String, _ nd: String) -> String { ok == nil ? nd : (ok! ? yes : no) }
        let cam = AVCaptureDevice.authorizationStatus(for: .video)
        R.append("카메라: " + (cam == .authorized ? "됨" : (cam == .notDetermined ? "아직 묻지 않음 (처음 찍을 때 허락해 주십시오)" : "허락되지 않음 — 폰 설정의 투어스튜디오에서 카메라를 켜 주십시오")))
        let loc = CLLocationManager().authorizationStatus
        R.append("위치: " + ((loc == .authorizedWhenInUse || loc == .authorizedAlways) ? "됨" : (loc == .notDetermined ? "아직 묻지 않음" : "허락되지 않음 — 폰 설정에서 위치를 켜 주십시오")))
        R.append("나침반: " + (CLLocationManager.headingAvailable() ? "됨" : "이 기기는 방위를 읽지 못합니다"))
        let mic = AVAudioApplication.shared.recordPermission
        R.append("마이크: " + (mic == .granted ? "됨" : (mic == .undetermined ? "아직 묻지 않음" : "허락되지 않음 — 폰 설정에서 마이크를 켜 주십시오")))
        let sp = SFSpeechRecognizer.authorizationStatus()
        R.append("받아쓰기: " + (sp == .authorized ? "됨" : (sp == .notDetermined ? "아직 묻지 않음" : "허락되지 않음 (소리로만 담깁니다)")))
        R.append("못 올린 사진·소리: \(Daegi.su)개")
        let j = await TourAPI.call("selfcheck")
        if TourAPI.ok(j) {
            R.append("나스 연결: 됨 (코스 \(Int(sutja(j["courses"]) ?? 0))개, 남은 자리 " + geulja(j["freegb"]) + "GB)")
            R.append("나스 저장: " + ((j["mine_writable"] as? Bool) == true ? "됨" : "안 됨 — 협회에 알려 주십시오"))
            R.append("코스 통째로 묶기(zip): " + ((j["zip"] as? Bool) == true ? "됨" : "안 됨"))
        } else { R.append("나스 연결: 안 됨 — " + TourAPI.err(j)) }
        R.append(Pan.ireum)
        rows = R
        store.say("점검했습니다. " + R.prefix(6).joined(separator: ". "))
    }
}

// MARK: - 기능 찾기 (웹 「기능 찾기」·한 줄 명령창과 같은 뜻 — 낱말로 찾아 바로 감)
struct GineungView: View {
    @EnvironmentObject var store: TourStore
    @EnvironmentObject var nav: Nav
    @State private var q = ""
    struct G: Identifiable { let id: String; let k: String; let tab: Int? ; let master: Bool }
    static let L: [G] = [
        G(id: "코스 정보 (이름·장소·집합 장소·분야·지역·인원·앞머리·장소 평면도)", k: "1단계 코스 정하기 이름 장소 집합 분야 지역 인원 앞머리 평면도 방향 잡기", tab: nil, master: false),
        G(id: "대본 살펴보기·원칙 16개 점검표", k: "원칙 점검 검사 금지어 방향 대본 살펴보기 16개", tab: nil, master: false),
        G(id: "내보내기·협회 최종 승인 요청", k: "6단계 내보내기 대본집 점자 안내문 운영표 지도 승인 요청 협회 DB 통째로 백업 안내문 소리", tab: nil, master: false),
        G(id: "장소 지식 들이기 (뼈대 짜기)", k: "장소 지식 뼈대 자동 국가유산 관광 테마투어", tab: nil, master: false),
        G(id: "우리가 확인한 것 채워 넣기", k: "우리자료 협회자료 계단 점자 채우기", tab: nil, master: false),
        G(id: "용어 뜻 찾기·우리 용어 사전", k: "뜻 낱말 사전 용어 우리사전", tab: nil, master: false),
        G(id: "답사 지도", k: "지도 답사 사진 방향 위치", tab: nil, master: false),
        G(id: "현장영상해설 화면 (협회 승인본)", k: "현장영상해설 승인본 시간 재기 따라가기 화면 안 꺼짐 읽어 주기", tab: nil, master: false),
        G(id: "판 되돌리기", k: "되돌리기 판 복구 실수", tab: nil, master: false),
        G(id: "휴지통 — 지운 코스 되살리기", k: "휴지통 되살리기 복구 지운 코스", tab: nil, master: false),
        G(id: "비밀번호 바꾸기", k: "비밀번호 바꾸기 암호", tab: nil, master: false),
        G(id: "점검", k: "점검 카메라 나침반 위치 나스 마이크", tab: nil, master: false),
        G(id: "답사 탭 — 사진 찍기·저절로 찍기·폰 사진 넣기·현장 메모·코스 벗어남", k: "2단계 답사 사진 카메라 저절로 찍기 담는 모양 폰 사진 메모 벗어남 경계", tab: 1, master: false),
        G(id: "지점 탭 — 지점 목록·지점 한 화면·걷는 시간 어림", k: "3단계 4단계 지점 목록 한 화면 대본 걷는 시간 방향 도우미 테마투어 느낌", tab: 2, master: false),
        G(id: "현장영상해설 탭 — 대본 보기·읽으며 녹음·전체 시간", k: "5단계 연습 녹음 시간 읽기", tab: 3, master: false),
        G(id: "클에게 대본 맡기기", k: "클 초안 대본 맡기기 요청함", tab: nil, master: true),
        G(id: "가입 신청 함", k: "가입 신청 승인", tab: nil, master: true),
        G(id: "명부 관리", k: "명부 사람 넣기 비밀번호 다시 막기", tab: nil, master: true),
        G(id: "반 관리", k: "반 수업 모둠 학생", tab: nil, master: true),
        G(id: "수업 진행판", k: "진행판 수업 학생 상태", tab: nil, master: true),
        G(id: "수업 채점표", k: "채점 채점표 학생", tab: nil, master: true),
        G(id: "협회 승인 보관소", k: "보관소 승인 돌려보내기 공개 내려받기 비교", tab: nil, master: true),
        G(id: "봐 달라는 코스", k: "봐달라 알림 확인", tab: nil, master: true)
    ]
    var body: some View {
        List {
            Section {
                TextField("찾을 낱말 (예: 사진, 대본, 승인, 지도)", text: $q)
            }
            let hits = GineungView.L.filter { (!$0.master || store.isMaster) && (q.isEmpty || ($0.id + " " + $0.k).contains(q.trimmingCharacters(in: .whitespaces))) }
            Section(hits.isEmpty ? "찾지 못했습니다. 다른 낱말로 찾아 보십시오." : "\(hits.count)가지") {
                Jang(items: hits) { g in
                    if let t = g.tab { Button(g.id) { nav.tab = t } }
                    else { NavigationLink(g.id) { gaja(g.id).dwiro() } }
                }
            }
        }
        .navigationTitle("기능 찾기")
    }
    @ViewBuilder func gaja(_ id: String) -> some View {
        switch id {
        case GineungView.L[0].id: KosJeongbo()
        case GineungView.L[1].id: JeomgeomView()
        case GineungView.L[2].id: NaebonaegiView()
        case GineungView.L[3].id: JisikView()
        case GineungView.L[4].id: UrijaryoView()
        case GineungView.L[5].id: DictView()
        case GineungView.L[6].id: SurveyMapView()
        case GineungView.L[7].id: FinalsView(hyeonjang: true)
        case GineungView.L[8].id: PanDoerigiView()
        case GineungView.L[9].id: HyujitongView()
        case GineungView.L[10].id: PwView()
        case GineungView.L[11].id: DiagView()
        case GineungView.L[15].id: ClaudeView()
        case GineungView.L[16].id: SignupsView()
        case GineungView.L[17].id: UsersView()
        case GineungView.L[18].id: ClassesView()
        case GineungView.L[19].id: BoardView()
        case GineungView.L[20].id: GradeView()
        case GineungView.L[21].id: FinalsView()
        default: ReviewsView()
        }
    }
}
