// AI점자도서관 앱 — 내 서재와 설정 보관 (판 0.2.0, 빌드 261002-1: 지우기·되돌리기)
// 0.8.9 (261124-L32, 도클) 폰과 컴퓨터 기록 함께 쓰기 — 저장할 때 Dongi 로 모아 보냄, 지운 책·책갈피는 지운 표시(delR·delM)를 남겨 다른 기기에서도 지워지게 함
// 0.1.0 (260930-1) 첫 판
import Foundation
import SwiftUI
import UIKit

struct ReadRec: Codable, Identifiable, Hashable {
    var i: Int
    var t: String
    var kind: String      // geul, sori, yeongsang
    var pos: Double       // 글자책은 문단 번호, 소리책·동영상은 초
    var modu: Int
    var at: Date
    var done: Bool
    var id: Int { i }
    var wichiMal: String {
        if kind == "geul" { return "\(Int(pos) + 1)번째 문단" }
        let s = Int(pos); return "\(s / 60)분 \(s % 60)초"
    }
}

struct Mark: Codable, Identifiable, Hashable {
    var i: Int
    var t: String
    var kind: String
    var pos: Double
    var at: Date
    var id: String { "\(i)-\(pos)" }
    var wichiMal: String {
        if kind == "geul" { return "\(Int(pos) + 1)번째 문단" }
        let s = Int(pos); return "\(s / 60)분 \(s % 60)초"
    }
}

/// 0.8.9 지운 책갈피 표시 — 서버에 닿을 때까지 폰에 둠(at 은 1970년부터 밀리초)
struct DelMark: Codable, Hashable { var i: Int; var pos: Double; var at: Double }

@MainActor
final class Store: ObservableObject {
    static let shared = Store()
    @Published var reads: [ReadRec] = []
    @Published var marks: [Mark] = []
    @Published var rateIndex: Int = 1
    @Published var voice: Int = 0
    @Published var speechOn: Bool = true
    @Published var downloaded: Set<Int> = []
    @Published var ieo: String = "ap"      // 0.8.0 (261018-L19) 엔터테인먼트 듣는 방식
    @Published var gogmal: Bool = true     // 0.8.0 넘어갈 때 이름 알리기
    static let ieoGal: [(String, String)] = [("ap", "앨범·작품 끝까지"), ("gal", "갈래 전체 이어서"), ("seokgi", "섞어 듣기"), ("ban", "한 곡 반복"), ("han", "한 곡만")]
    static func ieoIreum(_ v: String) -> String { ieoGal.first(where: { $0.0 == v })?.1 ?? "앨범·작품 끝까지" }
    @Published var dongi: Bool = true   // 0.8.9 폰과 컴퓨터 기록 함께 쓰기(처음 켜짐)
    var delR: [Int: Double] = [:]          // 0.8.9 지운 책 표시
    var delM: [String: DelMark] = [:]      // 0.8.9 지운 책갈피 표시
    static func markKey(_ i: Int, _ pos: Double) -> String { "\(i)|\(Int(pos.rounded()))" }
    @Published var hanjaeum: Int = 1   // 0.6.1 (261012) 한자음 읽기: 1 음만 듣기, 2 음과 원문 함께 보기, 0 꺼짐
    static func hanjaeumIreum(_ v: Int) -> String { v == 0 ? "꺼짐" : (v == 2 ? "음과 원문 함께 보기" : "음만 듣기") }

    static let rates: [Float] = [0.8, 1.0, 1.2, 1.4, 1.7]
    static let rateNames = ["아주 느리게", "보통", "조금 빠르게", "빠르게", "아주 빠르게"]
    var rate: Float { Store.rates[min(max(rateIndex, 0), Store.rates.count - 1)] }

    private let ud = UserDefaults.standard
    private init() { load() }

    func load() {
        if let d = ud.data(forKey: "reads"), let v = try? JSONDecoder().decode([ReadRec].self, from: d) { reads = v }
        if let d = ud.data(forKey: "marks"), let v = try? JSONDecoder().decode([Mark].self, from: d) { marks = v }
        rateIndex = ud.object(forKey: "rateIndex") as? Int ?? 1
        voice = ud.object(forKey: "voice") as? Int ?? 0
        speechOn = ud.object(forKey: "speechOn") as? Bool ?? true
        downloaded = Set((ud.array(forKey: "downloaded") as? [Int]) ?? [])
        hanjaeum = ud.object(forKey: "hanjaeum") as? Int ?? 1
        ieo = ud.string(forKey: "ieo") ?? "ap"; gogmal = ud.object(forKey: "gogmal") as? Bool ?? true
        dongi = ud.object(forKey: "dongi") as? Bool ?? true
        if let d = ud.data(forKey: "delR"), let v = try? JSONDecoder().decode([Int: Double].self, from: d) { delR = v }
        if let d = ud.data(forKey: "delM"), let v = try? JSONDecoder().decode([String: DelMark].self, from: d) { delM = v }
    }
    func save(dongiDo: Bool = true) {
        if let d = try? JSONEncoder().encode(reads) { ud.set(d, forKey: "reads") }
        if let d = try? JSONEncoder().encode(marks) { ud.set(d, forKey: "marks") }
        ud.set(rateIndex, forKey: "rateIndex"); ud.set(voice, forKey: "voice"); ud.set(speechOn, forKey: "speechOn")
        ud.set(Array(downloaded), forKey: "downloaded")
        ud.set(hanjaeum, forKey: "hanjaeum")
        ud.set(ieo, forKey: "ieo"); ud.set(gogmal, forKey: "gogmal")
        ud.set(dongi, forKey: "dongi")
        if let d = try? JSONEncoder().encode(delR) { ud.set(d, forKey: "delR") }
        if let d = try? JSONEncoder().encode(delM) { ud.set(d, forKey: "delM") }
        if dongiDo { Dongi.shared.moeum() }   // 0.8.9
    }

    var last: ReadRec? { reads.filter { !$0.done }.sorted { $0.at > $1.at }.first }
    var reading: [ReadRec] { reads.filter { !$0.done }.sorted { $0.at > $1.at } }
    var finished: [ReadRec] { reads.filter { $0.done }.sorted { $0.at > $1.at } }
    func rec(_ i: Int) -> ReadRec? { reads.first { $0.i == i } }

    func remember(i: Int, t: String, kind: String, pos: Double, modu: Int, done: Bool = false) {
        if let k = reads.firstIndex(where: { $0.i == i }) {
            reads[k].pos = pos; reads[k].modu = modu; reads[k].at = Date(); reads[k].t = t
            if done { reads[k].done = true } else if reads[k].done && pos < Double(max(modu - 1, 0)) { reads[k].done = false }
        } else {
            reads.append(ReadRec(i: i, t: t, kind: kind, pos: pos, modu: modu, at: Date(), done: done))
        }
        save()
    }
    // 0.5.2 (261012) 글 판 4(강제 줄바꿈 바로잡기) — 옛 판 문단 번호로 적힌 읽던 자리·책갈피를 책을 처음 열 때 한 번만 새 판 번호로 옮김. 내려받은 책은 옛 글 그대로라 옮기지 않음
    private var pan4Sido: Set<Int> = []
    func gulPan4Omgigi(_ i: Int) async {
        var ok = Set((ud.array(forKey: "gulPan4Ok") as? [Int]) ?? [])
        if ok.contains(i) || pan4Sido.contains(i) { return }
        pan4Sido.insert(i)
        let ol = Array(Set(reads.filter { $0.i == i && $0.kind == "geul" }.map { Int($0.pos) } + marks.filter { $0.i == i && $0.kind == "geul" }.map { Int($0.pos) })).sorted()
        if !downloaded.contains(i) && !ol.isEmpty {
            guard let r = try? await API.jari(i, ol) else { return }   // 인터넷이 안 되면 다음에 다시
            ok = Set((ud.array(forKey: "gulPan4Ok") as? [Int]) ?? [])
            if ok.contains(i) { return }
            if r.ok, let j = r.jari, j.count == ol.count {
                var mp: [Int: Int] = [:]; for (a, b) in zip(ol, j) { mp[a] = b }
                for k in reads.indices where reads[k].i == i && reads[k].kind == "geul" { if let n = mp[Int(reads[k].pos)] { reads[k].pos = Double(n) } }
                for k in marks.indices where marks[k].i == i && marks[k].kind == "geul" { if let n = mp[Int(marks[k].pos)] { marks[k].pos = Double(n) } }
                save()
            }
        }
        ok.insert(i); ud.set(Array(ok), forKey: "gulPan4Ok")
    }
    func addMark(i: Int, t: String, kind: String, pos: Double) {
        if !marks.contains(where: { $0.i == i && abs($0.pos - pos) < 0.5 }) {
            marks.append(Mark(i: i, t: t, kind: kind, pos: pos, at: Date())); save()
        }
    }
    func removeMark(_ m: Mark) { delM[Store.markKey(m.i, m.pos)] = DelMark(i: m.i, pos: m.pos, at: Dongi.ms(Date())); marks.removeAll { $0.id == m.id }; save() }
    /// 내 서재에서 지우기 — 그 책의 읽던 자리와 책갈피를 함께 빼고, 되돌리기를 위해 돌려줌 (0.2.0)
    func jiugi(_ i: Int) -> (ReadRec, [Mark])? {
        guard let r = reads.first(where: { $0.i == i }) else { return nil }
        let mk = marks.filter { $0.i == i }
        reads.removeAll { $0.i == i }; marks.removeAll { $0.i == i }
        let now = Dongi.ms(Date()); delR[i] = now   // 0.8.9 다른 기기에서도 지워지게
        for m in mk { delM[Store.markKey(m.i, m.pos)] = DelMark(i: m.i, pos: m.pos, at: now) }
        save(); return (r, mk)
    }
    func doedollrigi(_ r: ReadRec, _ mk: [Mark]) {
        var r = r; delR[r.i] = nil; r.at = Date()   // 0.8.9 되돌린 것이 지운 표시보다 나중이 되게
        if !reads.contains(where: { $0.i == r.i }) { reads.append(r) }
        for var m in mk where !marks.contains(where: { $0.id == m.id }) { delM[Store.markKey(m.i, m.pos)] = nil; m.at = Date(); marks.append(m) }
        save()
    }
    func marks(of i: Int) -> [Mark] { marks.filter { $0.i == i }.sorted { $0.pos < $1.pos } }

    /// 앱이 스스로 내는 안내 말(보이스오버 알림). 설정에서 끌 수 있다.
    func say(_ s: String) {
        guard speechOn else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            UIAccessibility.post(notification: .announcement, argument: s)
        }
    }
}

enum Pan {
    static let jeomMal = "이 기능은 웹버전에서 가능합니다. 컴퓨터에서 ailib.ada.or.kr 로 들어가 주십시오."   // 0.8.7 (261122) 점역 코너는 웹판에서만
    static var ver: String { Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.1.0" }
    static var build: String { Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1" }
    static var mal: String { "AI점자도서관 \(ver)판, 빌드 \(build)" }
}
