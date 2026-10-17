// LVD투어스튜디오 앱 — 앱 살림 (판 1.2.0, 빌드 261017-T4 — 사진에 담는 모양·밝기와 또렷함·방위, 저장 실패 셈. 1.0.0 261012-T1)
// 들어온 사람, 코스 목록, 열린 코스, 저절로 저장, 폰에 남겨 두기(인터넷이 끊겨도 잃지 않음), 앱 안내 말소리
import SwiftUI
import UIKit
import CoreLocation

@MainActor
final class TourStore: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let shared = TourStore()

    @Published var me: [String: Any]? = nil
    @Published var courses: [[String: Any]] = []
    @Published var kos: Kos? = nil
    @Published var curId: String = "sOpen"          // 지금 지점(답사·지점 탭이 함께 씀)
    @Published var gen = 0                          // 코스 자료가 바뀌면 화면을 다시 그림
    @Published var saveMark = ""                    // 「저장함 10:42」 같은 표
    @Published var allim = ""                       // 화면에 남는 마지막 안내
    @Published var bappeum = false
    var base = ""                                   // 서버에 마지막으로 저장된 때(겹침 막기)
    private var lastSaved = ""
    private var saving = false
    private var saveAgain = false
    private var saveTask: Task<Void, Never>? = nil

    var loggedIn: Bool { !TourAPI.token.isEmpty && me != nil }
    var meName: String { geulja(me?["name"]) }
    var isMaster: Bool { geulja(me?["role"]) == "master" }
    var malsori: Bool { UserDefaults.standard.object(forKey: "malsori") as? Bool ?? true }

    // ---- 말소리 ----
    func say(_ t: String) {
        allim = t
        if malsori { UIAccessibility.post(notification: .announcement, argument: t) }
    }

    // ---- 들어오기 ----
    func sijak() async {
        guard !TourAPI.token.isEmpty else { return }
        let j = await TourAPI.call("me")
        if TourAPI.ok(j), let m = j["me"] as? [String: Any] { me = m; await loadCourses() }
        else if (j["offline"] as? Bool) == true { me = ["name": UserDefaults.standard.string(forKey: "meName") ?? "", "role": ""] }   // 인터넷이 없어도 폰에 남긴 코스는 열 수 있게
        else { TourAPI.token = "" }
    }
    func login(id: String, pw: String) async -> String? {
        let j = await TourAPI.call("login", ["id": id, "pw": pw])
        guard TourAPI.ok(j), let tk = j["token"] as? String else { return TourAPI.err(j) }
        TourAPI.token = tk
        me = (j["me"] as? [String: Any]) ?? ["id": id, "name": geulja(j["name"]), "role": geulja(j["role"])]
        UserDefaults.standard.set(meName, forKey: "meName")
        await loadCourses()
        return nil
    }
    func naga(mal: String = "") {
        let tk = TourAPI.token
        if !tk.isEmpty { Task { _ = await TourAPI.call("logout", tk: tk) } }
        TourAPI.token = ""; me = nil; kos = nil; courses = []
        if !mal.isEmpty { say(mal) }
    }

    // ---- 코스 ----
    func loadCourses() async {
        bappeum = true; defer { bappeum = false }
        let j = await TourAPI.call("list_courses")
        if TourAPI.ok(j) { courses = (j["courses"] as? [[String: Any]]) ?? [] }
        else { say(TourAPI.err(j)) }
    }
    func open(_ id: String) async -> Bool {
        bappeum = true; defer { bappeum = false }
        let j = await TourAPI.call("get_course", ["id": id])
        guard TourAPI.ok(j), let c = j["course"] as? [String: Any] else {
            if let dr = draft(id) { setKos(Kos(dr)); say("인터넷이 없어 폰에 남겨 둔 코스를 열었습니다. 인터넷이 되면 저장을 눌러 주십시오."); return true }
            say(TourAPI.err(j)); return false
        }
        base = geulja(c["updated"])
        let k = Kos(c)
        setKos(k)
        lastSaved = snap()
        if var dr = draft(id) {
            dr.removeValue(forKey: "savedAt")
            if TourStore.jsonGeul(Kos(dr).json()) != lastSaved {
                say("폰에 아직 올리지 못한 고친 것이 있습니다. 설정의 「폰에 남은 것 올리기」로 올리실 수 있습니다.")
            } else { dropDraft(id) }
        }
        UserDefaults.standard.set(id, forKey: "lastCourse")
        if Daegi.mokrok().contains(where: { $0.c == id }) { Task { await self.daegiOllim(quiet: true) } }
        return true
    }
    private func setKos(_ k: Kos) { kos = k; curId = "sOpen"; gen += 1 }

    func newCourse(name: String, place: String) async -> Bool {
        let j = await TourAPI.call("new_course", ["name": name, "place": place])
        guard TourAPI.ok(j) else { say(TourAPI.err(j)); return false }
        let id = geulja((j["course"] as? [String: Any])?["id"] ?? j["id"])
        await loadCourses()
        return await open(id)
    }
    func copyGyobon() async -> Bool {
        let j = await TourAPI.call("copy_gyobon")
        guard TourAPI.ok(j) else { say(TourAPI.err(j)); return false }
        await loadCourses()
        say("교본을 내 코스로 복사했습니다.")
        return await open(geulja(j["id"]))
    }

    // ---- 저장 ----
    static func jsonGeul(_ o: [String: Any]) -> String {
        guard let d = try? JSONSerialization.data(withJSONObject: o, options: [.sortedKeys]) else { return "" }
        return String(data: d, encoding: .utf8) ?? ""
    }
    private func snap() -> String {
        guard let k = kos else { return "" }
        return TourStore.jsonGeul(k.json())
    }
    /// 고칠 때마다 부름 — 2초 동안 더 고치지 않으면 저장
    func bakkwim() {
        gen += 1
        saveTask?.cancel()
        saveTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            if Task.isCancelled { return }
            _ = await self?.save(quiet: true)
        }
    }
    @discardableResult
    func save(quiet: Bool) async -> Bool {
        guard let k = kos else { return false }
        if k.isGyobon { if !quiet { say("교본은 읽기만 됩니다. 코스 탭에서 내 코스로 복사한 뒤 쓰십시오.") }; return false }
        let s = snap()
        keepDraft(k)
        if quiet && s == lastSaved { return true }
        if saving { saveAgain = true; return true }
        saving = true
        var j = await TourAPI.call("save_course", ["course": k.json(), "base": base])
        if (j["conflict"] as? Bool) == true {
            // 다른 기기에서 먼저 저장된 경우 — 웹과 같이 지금 고친 것으로 맞춤(앞 것은 판으로 남아 판 되돌리기로 꺼낼 수 있음)
            base = geulja(j["updated"]).isEmpty ? base : geulja(j["updated"])
            j = await TourAPI.call("save_course", ["course": k.json(), "base": base, "force": true])
        }
        saving = false
        if TourAPI.ok(j) {
            base = geulja(j["updated"])
            lastSaved = s
            dropDraft(k.id)
            saveMark = "저장함 " + String(base.replacingOccurrences(of: "T", with: " ").dropFirst(11).prefix(5))
            if !quiet { say("저장했습니다.") }
        } else {
            saveMark = "저장 못 함 — 폰에 남겨 두었습니다"
            Gwan.shared.fails += 1; Gwan.shared.err = String(TourAPI.err(j).prefix(80))
            say("저장하지 못했습니다. 고친 것은 폰에 남겨 두었습니다. " + TourAPI.err(j))
        }
        if saveAgain { saveAgain = false; await save(quiet: true) }
        return TourAPI.ok(j)
    }

    // ---- 폰에 남겨 두기 ----
    private func draftURL(_ id: String) -> URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("draft", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent(id.replacingOccurrences(of: "/", with: "_") + ".json")
    }
    private func keepDraft(_ k: Kos) {
        var o = k.json()
        let f = ISO8601DateFormatter()
        o["savedAt"] = f.string(from: Date())
        if let d = try? JSONSerialization.data(withJSONObject: o) { try? d.write(to: draftURL(k.id), options: .atomic) }
    }
    private func dropDraft(_ id: String) { try? FileManager.default.removeItem(at: draftURL(id)) }
    func draft(_ id: String) -> [String: Any]? {
        guard let d = try? Data(contentsOf: draftURL(id)) else { return nil }
        return try? JSONSerialization.jsonObject(with: d) as? [String: Any]
    }
    func draftOllim() async {
        guard let k = kos, let dr = draft(k.id) else { say("폰에 남은 것이 없습니다."); return }
        var o = dr; o.removeValue(forKey: "savedAt")
        setKos(Kos(o))
        lastSaved = ""
        if await save(quiet: false) { say("폰에 남은 것을 올렸습니다.") }
    }

    // ---- 지금 지점 ----
    var cur: Spot? { kos?.spot(curId) }
    func go(_ s: Spot) { curId = s.id; gen += 1 }

    // ---- 자리(위치) ----
    private let lm = CLLocationManager()
    private var jariKkeut: [(CLLocation?) -> Void] = []
    func jari(_ kkeut: @escaping (CLLocation?) -> Void) {
        lm.delegate = self
        lm.desiredAccuracy = kCLLocationAccuracyBest
        jariKkeut.append(kkeut)
        if lm.authorizationStatus == .notDetermined { lm.requestWhenInUseAuthorization() }
        lm.requestLocation()
    }
    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let l = locations.last
        Task { @MainActor in let k = self.jariKkeut; self.jariKkeut = []; k.forEach { $0(l) } }
    }
    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in let k = self.jariKkeut; self.jariKkeut = []; k.forEach { $0(nil) } }
    }
    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor in if !self.jariKkeut.isEmpty { manager.requestLocation() } }
    }

    static func r6(_ x: Double) -> Double { (x * 1e6).rounded() / 1e6 }

    /// 여기서부터 다음 지점 — 앞 지점의 끝과 다음 지점의 시작을 이 자리·이 때로 적음(웹 boundGo 와 같음)
    func boundGo() {
        guard let k = kos, let c = cur else { return }
        if k.isGyobon { say("교본은 읽기만 됩니다. 코스 탭에서 내 코스로 복사한 뒤 쓰십시오."); return }
        guard let nx = k.spotNext(c) else { say("클로징 지점이 마지막입니다. 클로징 지점의 어디까지 칸에 해산하는 자리를 적어 주십시오."); return }
        markBound(c, nx)
        go(nx)
        var m = "여기서부터 " + k.spotFull(nx) + "입니다. "
        m += k.spotFull(c) + "은 여기까지입니다."
        if c.s("to").trimmingCharacters(in: .whitespaces).isEmpty { m += " " + k.spotLab(c) + "의 어디까지 칸에 이 자리를 적어 두십시오." }
        m += nx.kind == "close" ? " 해산하는 자리에서 클로징을 마치십시오." : " 정지 현장영상해설 자리에 서시면 사진 찍기를 누르십시오."
        say(m)
        bakkwim()
    }
    /// 여기서부터 새 지점 — 지금 지점 바로 뒤에 넣고 이 자리를 경계로 적음(웹 addSpot 과 같음)
    func addHere(name: String) {
        guard let k = kos else { return }
        if k.isGyobon { say("교본은 읽기만 됩니다. 코스 탭에서 내 코스로 복사한 뒤 지점을 넣으십시오."); return }
        let s = k.addSpot(after: cur, name: name)
        let pv = k.spotPrev(s)
        markBound(pv, s)
        k.syncMove()
        go(s)
        var m = "여기서부터 " + k.spotFull(s) + "입니다. "
        if pv != nil { m += k.spotLab(pv) + "은 여기까지입니다. " }
        m += "지점은 모두 \(k.spots.count)곳입니다."
        if name.isEmpty { m += " 이름은 지점 탭에서 적으십시오." }
        say(m)
        bakkwim()
    }
    private func markBound(_ p: Spot?, _ q: Spot?) {
        let at = ISO8601DateFormatter().string(from: Date())
        p?.d["endAt"] = at; q?.d["startAt"] = at
        jari { [weak self] l in
            guard let l else { return }
            let pos: [String: Any] = ["lat": TourStore.r6(l.coordinate.latitude), "lng": TourStore.r6(l.coordinate.longitude), "at": at]
            p?.d["endPos"] = pos; q?.d["startPos"] = pos
            self?.bakkwim()
        }
    }
    /// 정지 현장영상해설 자리 — 처음 사진을 찍을 때 지점 자리를 적고, 앞 지점에서 걸어온 거리로 이동 시간을 잡음
    func spotPosHere(_ s: Spot, _ l: CLLocation?) {
        guard let k = kos, let l, s.lat == nil else { return }
        s.d["lat"] = TourStore.r6(l.coordinate.latitude); s.d["lng"] = TourStore.r6(l.coordinate.longitude)
        let p = k.spotPrev(s)
        if let m = metersBetween(k.posOf(p), k.posOf(s)) {
            s.d["distIn"] = m; s.d["walkIn"] = k.walkMinutes(m); k.syncMove()
            let w = k.walkMinutes(m)
            say("\(k.spotLab(p))에서 \(Int(m))미터 걸어오셨습니다. \(k.spotLab(s))의 이동 시간을 \(w)분으로 잡았습니다.")
        }
        bakkwim()
    }

    /// 사진 올리기 — 웹과 같은 자리(shots)에 같은 모양으로 적음
    func photo(_ img: UIImage, dir: String, extra: [String: Any] = [:]) async {
        guard let k = kos, let s = cur else { return }
        if k.isGyobon { say("교본은 읽기만 됩니다."); return }
        let im = Wonchik.jareugi(img).keugi(1600)      // 1.2.0 담는 모양(원본·가로·세로·정사각) — 웹과 같음
        guard let jpg = im.jpegData(compressionQuality: 0.82) else { return }
        let q = Wonchik.photoQuality(im)
        let moyangMal = Wonchik.moyangMal(im)
        let qWarn = geulja(q["warn"])
        say(moyangMal + " 사진을 올립니다.")
        let l: CLLocation? = await withCheckedContinuation { c in jari { c.resume(returning: $0) } }
        spotPosHere(s, l)
        var shot: [String: Any] = ["f": "", "dir": dir, "garo": Int(im.size.width * im.scale), "sero": Int(im.size.height * im.scale),
                                   "at": ISO8601DateFormatter().string(from: Date()), "zoom": 1, "app": "ios", "q": q]
        for (kk, v) in extra { shot[kk] = v }
        if let la = s.lat, let lo = s.lng { shot["lat"] = la; shot["lng"] = lo } else { shot["lat"] = NSNull(); shot["lng"] = NSNull() }
        let j = await TourAPI.call("save_photo", ["c": k.id, "s": s.id, "ext": "jpg", "data": jpg.base64EncodedString()], timeout: 90)
        if TourAPI.ok(j) {
            shot["f"] = geulja(j["file"])
            var a = s.shots; a.append(shot); s.shots = a
            lastShot = (s.id, a.count - 1)
            say(moyangMal + " " + k.spotLab(s) + " 사진 \(a.count)번째, " + dir + " 올렸습니다." + (qWarn.isEmpty ? "" : " " + qWarn + " 다시 담으시려면 같은 방향으로 한 장 더 찍으십시오."))
            bakkwim()
        } else {
            // 못 올린 사진은 폰에 남겨 두고, 코스에는 「아직 못 올림」으로 적어 둠(웹 qref 와 같음)
            let ref = Daegi.saeRef("photo")
            Daegi.neoki(DaegiGeot(ref: ref, jong: "photo", c: k.id, s: s.id, ext: "jpg", kind: "", at: ISO8601DateFormatter().string(from: Date())), jpg)
            shot["qref"] = ref
            var a = s.shots; a.append(shot); s.shots = a
            lastShot = (s.id, a.count - 1)
            daegiSu = Daegi.su
            say("사진을 올리지 못해 폰에 남겨 두었습니다. 인터넷이 되면 설정의 「못 올린 것 지금 올리기」로 올리십시오. 이 코스를 다시 열 때도 저절로 올립니다. " + TourAPI.err(j))
            bakkwim()
        }
    }

    // ================= 1.1.0판 (261017-T3) — 웹에 있던 것을 앱에도 =================
    @Published var lastShot: (String, Int)? = nil       // 방금 찍은 사진(지점 번호, 몇째)
    @Published var daegiSu = Daegi.su                   // 폰에 남은 못 올린 것

    /// 사진 지우기 — 웹 deleteShot 과 같음(되돌릴 수 없음)
    func deletePhoto(_ s: Spot, _ i: Int) {
        guard let k = kos, !k.isGyobon else { say("교본의 사진은 지울 수 없습니다."); return }
        var a = s.shots
        guard i >= 0 && i < a.count else { return }
        let shot = a.remove(at: i)
        s.shots = a
        let f = geulja(shot["f"]), q = geulja(shot["qref"]), dir = geulja(shot["dir"])
        if !f.isEmpty { Task { _ = await TourAPI.call("delete_photo", ["c": k.id, "f": f]) } }
        if !q.isEmpty { Daegi.ppaegi(q); daegiSu = Daegi.su }
        if let l = lastShot, l.0 == s.id, l.1 == i { lastShot = nil }
        say((dir.isEmpty ? "" : dir + " ") + "사진을 지웠습니다. " + s.name + " 지점에 \(a.count)장 남았습니다.")
        bakkwim()
    }

    /// 이 지점 지우기 — 웹 spotDel 과 같음(오프닝·클로징 지점은 지우지 않음)
    func deleteSpot(_ s: Spot) {
        guard let k = kos, !k.isGyobon, s.kind.isEmpty else { return }
        let pv = k.spotPrev(s)
        let nm = s.name
        k.spots.removeAll { $0 === s }
        k.syncMove()
        if curId == s.id, let pv { curId = pv.id }
        if let l = lastShot, l.0 == s.id { lastShot = nil }
        gen += 1
        say(nm + "을(를) 지웠습니다. 지점은 모두 \(k.spots.count)곳입니다.")
        bakkwim()
    }

    /// 코스 지우기 — 휴지통으로(되살릴 수 있음)
    func deleteCourse(_ id: String, _ nm: String) async {
        let j = await TourAPI.call("delete_course", ["id": id])
        guard TourAPI.ok(j) else { say(TourAPI.err(j)); return }
        if kos?.id == id { kos = nil }
        await loadCourses()
        say(nm + " 코스를 지웠습니다. 휴지통으로 갔습니다.")
    }
    func listTrash() async -> [[String: Any]]? {
        let j = await TourAPI.call("list_trash")
        guard TourAPI.ok(j) else { say(TourAPI.err(j)); return nil }
        return (j["list"] as? [[String: Any]]) ?? []
    }
    func restoreTrash(_ f: String, _ nm: String) async -> Bool {
        let j = await TourAPI.call("restore_trash", ["f": f])
        guard TourAPI.ok(j) else { say(TourAPI.err(j)); return false }
        await loadCourses()
        say(nm + " 코스를 되살렸습니다." + ((sutja(j["renum"]) ?? 0) > 0 ? " 같은 번호의 코스가 그새 생겨 새 번호로 되살렸습니다." : "") + " 코스 목록에 들어갔습니다.")
        return true
    }

    /// 관리자에게 봐 달라고 알리기 — 웹 askReview 와 같음
    func askReview(_ msg: String) async -> Bool {
        guard let k = kos else { return false }
        await save(quiet: true)
        let j = await TourAPI.call("ask_review", ["c": k.id, "msg": msg])
        if TourAPI.ok(j) { say("관리자에게 봐 달라고 알렸습니다."); return true }
        say(TourAPI.err(j)); return false
    }

    /// 판 되돌리기 — 웹 loadVersions·restore_version 과 같음
    func listVersions() async -> [[String: Any]]? {
        guard let k = kos else { return nil }
        let j = await TourAPI.call("list_versions", ["c": k.id])
        guard TourAPI.ok(j) else { say(TourAPI.err(j)); return nil }
        return (j["list"] as? [[String: Any]]) ?? []
    }
    func restoreVersion(_ v: String) async -> Bool {
        guard let k = kos else { return false }
        let j = await TourAPI.call("restore_version", ["c": k.id, "v": v])
        guard TourAPI.ok(j), let c = j["course"] as? [String: Any] else { say(TourAPI.err(j)); return false }
        base = geulja(j["updated"])
        setKos(Kos(c))
        lastSaved = snap()
        say("되돌렸습니다. 지점 \(kos?.spots.count ?? 0)곳입니다.")
        return true
    }

    // ---- 소리 올리기(현장 메모·연습 녹음) ----
    /// 담은 소리를 mp3 로 바꾸고 받아써서 올림. 못 올리면 폰에 남겨 둠. (파일 이름, 못 올린 표, 받아쓴 말, 초)
    func soriOllim(_ k: Kos, _ s: Spot, kind: String, wav: URL, sec: Int) async -> (String, String, String) {
        say("담은 소리를 mp3 로 바꾸고 있습니다.")
        let heard = await Badasseugi.geullo(wav)
        var data: Data, ext: String
        if let mp3 = await Mp3Gi.shared.bakkugi(wav) { data = mp3; ext = "mp3" }
        else { data = (try? Data(contentsOf: wav)) ?? Data(); ext = "wav" }
        try? FileManager.default.removeItem(at: wav)
        let j = await TourAPI.call("save_audio", ["c": k.id, "s": s.id, "kind": kind, "ext": ext, "data": data.base64EncodedString()], timeout: 120)
        if TourAPI.ok(j) { return (geulja(j["file"]), "", heard) }
        let ref = Daegi.saeRef("audio")
        Daegi.neoki(DaegiGeot(ref: ref, jong: "audio", c: k.id, s: s.id, ext: ext, kind: kind, at: ISO8601DateFormatter().string(from: Date())), data)
        daegiSu = Daegi.su
        return ("", ref, heard)
    }

    /// 현장 메모 말로 적기 — 끝내면 담고, 받아쓴 말은 고른 칸에 붙임(웹 afterAudio memo 와 같음)
    func memoMal(_ s: Spot, where_: String, wav: URL, sec: Int) async {
        guard let k = kos, !k.isGyobon else { return }
        if sec < 1 { say("담긴 소리가 없습니다."); return }
        let (f, q, heard) = await soriOllim(k, s, kind: "memo", wav: wav, sec: sec)
        var m = (s.d["memos"] as? [[String: Any]]) ?? []
        m.append(["f": f, "qref": q, "sec": sec, "text": heard, "where": where_, "at": ISO8601DateFormatter().string(from: Date())])
        s.d["memos"] = m
        if !heard.isEmpty { kanBuchigi(k, s, where_, heard) }
        var mal = heard.isEmpty ? "\(sec)초 담았습니다. 이 폰은 받아쓰기가 안 되어 소리로만 남겼습니다. 나중에 들어 보실 수 있습니다."
                                : "\(sec)초 말씀하셨습니다. 글로 옮겨 " + MemoKan.ireum(where_) + " 칸에 넣었습니다. " + heard
        if !q.isEmpty { mal += " 소리는 아직 올리지 못해 폰에 남겨 두었습니다." }
        say(mal)
        bakkwim()
    }
    /// 현장 메모 글로 적기 — 웹 memoTextPut 과 같음
    func memoGeul(_ s: Spot, where_: String, text: String) {
        guard let k = kos, !k.isGyobon else { return }
        let tx = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if tx.isEmpty { say("적으신 글이 없습니다. 글 칸에 적고 다시 누르십시오."); return }
        var m = (s.d["memos"] as? [[String: Any]]) ?? []
        m.append(["f": "", "qref": "", "sec": 0, "text": tx, "where": where_, "at": ISO8601DateFormatter().string(from: Date()), "typed": 1])
        s.d["memos"] = m
        kanBuchigi(k, s, where_, tx)
        say("글로 담았습니다. " + MemoKan.ireum(where_) + " 칸에 넣었습니다.")
        bakkwim()
    }
    private func kanBuchigi(_ k: Kos, _ s: Spot, _ where_: String, _ t: String) {
        if where_ == "text" {
            let o = k.spotText(s); k.setSpotText(s, o.isEmpty ? t : o + " " + t)
        } else {
            let o = s.s(where_); s.d[where_] = o.isEmpty ? t : o + " " + t
        }
    }

    /// 지점별 읽으며 녹음 — 걸린 시간·빠르기·금지어 되돌아보기(웹 pracReview 와 같음)
    func yeonseup(_ s: Spot, wav: URL, sec: Int) async {
        guard let k = kos, !k.isGyobon else { return }
        if sec < 1 { say("담긴 소리가 없습니다."); return }
        let (f, q, heard) = await soriOllim(k, s, kind: "prac", wav: wav, sec: sec)
        s.d["real"] = sec
        s.d["pracFile"] = f
        if !q.isEmpty { s.d["pracQref"] = q } else { s.d.removeValue(forKey: "pracQref") }
        k.d["practiced"] = true
        s.d["prac"] = Yeonseup.review(k, s, sec: sec, heard: heard)
        say(Yeonseup.mal(k, s) + (q.isEmpty ? "" : " 소리는 아직 올리지 못해 폰에 남겨 두었습니다."))
        bakkwim()
    }

    /// 못 올린 것 올리기 — 지금 연 코스의 것부터. 다른 코스의 것은 그 코스를 열 때 올림
    func daegiOllim(quiet: Bool) async {
        let L = Daegi.mokrok()
        if L.isEmpty { daegiSu = 0; if !quiet { say("못 올린 것이 없습니다.") }; return }
        guard let k = kos else { if !quiet { say("못 올린 것 \(L.count)개가 있습니다. 그 코스를 열면 올립니다.") }; return }
        var ollin = 0, mot = 0
        for g in L where g.c == k.id {
            guard let data = Daegi.data(g) else { Daegi.ppaegi(g.ref); continue }
            let j: [String: Any]
            if g.jong == "photo" {
                j = await TourAPI.call("save_photo", ["c": g.c, "s": g.s, "ext": g.ext, "data": data.base64EncodedString()], timeout: 90)
            } else {
                j = await TourAPI.call("save_audio", ["c": g.c, "s": g.s, "kind": g.kind, "ext": g.ext, "data": data.base64EncodedString()], timeout: 120)
            }
            guard TourAPI.ok(j) else { mot += 1; continue }
            let f = geulja(j["file"])
            for s in k.allSpots {
                var a = s.shots
                for i in a.indices where geulja(a[i]["qref"]) == g.ref { a[i]["f"] = f; a[i].removeValue(forKey: "qref") }
                s.shots = a
                if var m = s.d["memos"] as? [[String: Any]] {
                    for i in m.indices where geulja(m[i]["qref"]) == g.ref { m[i]["f"] = f; m[i]["qref"] = "" }
                    s.d["memos"] = m
                }
                if s.s("pracQref") == g.ref { s.d["pracFile"] = f; s.d.removeValue(forKey: "pracQref") }
            }
            Daegi.ppaegi(g.ref)
            ollin += 1
        }
        daegiSu = Daegi.su
        if ollin > 0 { bakkwim() }
        let namun = Daegi.su
        if !quiet || ollin > 0 {
            var m = ollin > 0 ? "못 올린 것 \(ollin)개를 올렸습니다." : ""
            if mot > 0 { m += " \(mot)개는 아직 못 올렸습니다. 인터넷을 확인해 주십시오." }
            if namun - mot > 0 { m += " 다른 코스의 것 \(namun - mot)개는 그 코스를 열면 올립니다." }
            if m.isEmpty { m = "이 코스에는 못 올린 것이 없습니다." }
            say(m.trimmingCharacters(in: .whitespaces))
        }
    }
}

extension UIImage {
    /// 긴 쪽을 n 점으로 줄임(올리는 시간과 나스 자리를 아낌)
    func keugi(_ n: CGFloat) -> UIImage {
        let w = size.width, h = size.height, m = max(w, h)
        guard m > n else { return self }
        let r = n / m, sz = CGSize(width: (w * r).rounded(), height: (h * r).rounded())
        let f = UIGraphicsImageRendererFormat(); f.scale = 1
        return UIGraphicsImageRenderer(size: sz, format: f).image { _ in draw(in: CGRect(origin: .zero, size: sz)) }
    }
}
