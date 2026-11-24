// AI점자도서관 앱 — 폰과 컴퓨터 기록 함께 쓰기 (0.8.9, 빌드 261124-L32, 도클 — 대클 설계 261124, 이사장님 지시 2026-11-24 19시 19분)
// 같은 아이디(같은 전화번호)면 폰 앱과 컴퓨터 웹(ailib.ada.or.kr)이 이어 읽기·다 읽은 책·책갈피를 함께 씀.
// 서버 창구: nas/dongi.php — a=nae 로 이 폰 기록과 지운 표시를 보내면 서버가 합친 결과(최근 것이 이김)를 돌려줌.
// 시각(at)은 서버와 웹에 맞춰 1970년부터 밀리초로 주고받음(폰 안 저장은 그대로 Date).
// 로그인(회원 열쇠)이 없거나 설정에서 끄면 아무것도 보내지 않고 폰에만 둠. 인터넷이 안 되어도 폰 저장은 그대로.
import Foundation
import UIKit

@MainActor
final class Dongi {
    static let shared = Dongi()
    private var tmr: Timer?
    private var bonaeneunJung = false
    private var dasi = false
    private var sijakdoem = false

    /// 앱이 켜질 때 한 번 — 켤 때와 앞으로 올 때마다 받아 합침
    func sijak() {
        guard !sijakdoem else { return }
        sijakdoem = true
        NotificationCenter.default.addObserver(forName: UIApplication.willEnterForegroundNotification, object: nil, queue: .main) { _ in
            Task { @MainActor in Dongi.shared.matchugi() }
        }
        matchugi()
    }
    /// 저장할 때마다 — 3초 모아서 보냄
    func moeum() {
        guard Store.shared.dongi, !Hoewon.shared.yeolsoe.isEmpty else { return }
        if let t = tmr, t.isValid { return }   // 이미 모으는 중이면 그대로(읽는 동안 자꾸 미뤄지지 않게)
        tmr = Timer.scheduledTimer(withTimeInterval: 3, repeats: false) { _ in Task { @MainActor in Dongi.shared.tmr = nil; Dongi.shared.matchugi() } }
    }
    static func ms(_ d: Date) -> Double { (d.timeIntervalSince1970 * 1000).rounded() }
    static func nal(_ v: Double) -> Date { Date(timeIntervalSince1970: v / 1000) }

    /// 이 폰 기록과 지운 표시를 보내고, 서버가 합친 결과를 받아 이 폰에 반영
    func matchugi() {
        let st = Store.shared, tk = Hoewon.shared.yeolsoe
        guard st.dongi, !tk.isEmpty else { return }
        if bonaeneunJung { dasi = true; return }
        bonaeneunJung = true
        var rs: [[String: Any]] = st.reads.map { ["i": $0.i, "t": $0.t, "kind": $0.kind, "pos": $0.pos, "modu": $0.modu, "at": Dongi.ms($0.at), "done": $0.done] }
        for (i, at) in st.delR { rs.append(["i": i, "del": 1, "at": at]) }
        var mk: [[String: Any]] = st.marks.map { ["i": $0.i, "t": $0.t, "kind": $0.kind, "pos": $0.pos, "at": Dongi.ms($0.at)] }
        for d in st.delM.values { mk.append(["i": d.i, "pos": d.pos, "del": 1, "at": d.at]) }
        let bonaen = (st.delR, st.delM)
        guard let body = try? JSONSerialization.data(withJSONObject: ["reads": rs, "marks": mk]) else { bonaeneunJung = false; return }
        var r = API.request(API.url("dongi.php", ["a": "nae"]), timeout: 20)
        r.httpMethod = "POST"; r.setValue("application/json", forHTTPHeaderField: "Content-Type"); r.httpBody = body
        Task { @MainActor in
            defer {
                self.bonaeneunJung = false
                if self.dasi { self.dasi = false; self.moeum() }
            }
            guard let (d, res) = try? await URLSession.shared.data(for: r), (res as? HTTPURLResponse)?.statusCode == 200,
                  let j = try? JSONSerialization.jsonObject(with: d) as? [String: Any], (j["ok"] as? Bool) == true else { return }   // 403 이어도 로그인을 풀지 않음
            self.hapchigi(j, bonaen: bonaen)
        }
    }

    private func su(_ v: Any?) -> Double { (v as? NSNumber)?.doubleValue ?? 0 }
    private func hapchigi(_ j: [String: Any], bonaen: ([Int: Double], [String: DelMark])) {
        let st = Store.shared
        // 보낸 지운 표시는 서버에 남았으니 덜어 냄(그새 새로 지운 것은 남김)
        for (i, at) in bonaen.0 where st.delR[i] == at { st.delR[i] = nil }
        for (k, v) in bonaen.1 where st.delM[k]?.at == v.at { st.delM[k] = nil }
        var reads = st.reads
        for x in (j["reads"] as? [[String: Any]]) ?? [] {
            guard let i = (x["i"] as? NSNumber)?.intValue else { continue }
            let at = su(x["at"]), k = reads.firstIndex { $0.i == i }
            if let k = k, Dongi.ms(reads[k].at) >= at { continue }
            if let dl = st.delR[i], dl >= at { continue }
            if (x["del"] as? NSNumber)?.intValue == 1 { if let k = k { reads.remove(at: k) }; continue }
            let rec = ReadRec(i: i, t: x["t"] as? String ?? "", kind: x["kind"] as? String ?? "geul", pos: su(x["pos"]), modu: Int(su(x["modu"])), at: Dongi.nal(at), done: (x["done"] as? Bool) ?? ((x["done"] as? NSNumber)?.boolValue ?? false))
            if let k = k { reads[k] = rec } else { reads.append(rec) }
        }
        var marks = st.marks
        for x in (j["marks"] as? [[String: Any]]) ?? [] {
            guard let i = (x["i"] as? NSNumber)?.intValue else { continue }
            let at = su(x["at"]), pos = su(x["pos"]), key = Store.markKey(i, pos)
            let k = marks.firstIndex { Store.markKey($0.i, $0.pos) == key }
            if let k = k, Dongi.ms(marks[k].at) >= at { continue }
            if let dl = st.delM[key], dl.at >= at { continue }
            if (x["del"] as? NSNumber)?.intValue == 1 { if let k = k { marks.remove(at: k) }; continue }
            let m = Mark(i: i, t: x["t"] as? String ?? "", kind: x["kind"] as? String ?? "geul", pos: pos, at: Dongi.nal(at))
            if let k = k { marks[k] = m } else { marks.append(m) }
        }
        if reads.count > 300 { reads = Array(reads.sorted { $0.at > $1.at }.prefix(300)) }
        st.reads = reads; st.marks = marks
        st.save(dongiDo: false)
    }
}
