// AI점자도서관 앱 — 내 서재와 설정 보관 (판 0.2.0, 빌드 261002-1: 지우기·되돌리기)
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

@MainActor
final class Store: ObservableObject {
    static let shared = Store()
    @Published var reads: [ReadRec] = []
    @Published var marks: [Mark] = []
    @Published var rateIndex: Int = 1
    @Published var voice: Int = 0
    @Published var speechOn: Bool = true
    @Published var downloaded: Set<Int> = []

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
    }
    func save() {
        if let d = try? JSONEncoder().encode(reads) { ud.set(d, forKey: "reads") }
        if let d = try? JSONEncoder().encode(marks) { ud.set(d, forKey: "marks") }
        ud.set(rateIndex, forKey: "rateIndex"); ud.set(voice, forKey: "voice"); ud.set(speechOn, forKey: "speechOn")
        ud.set(Array(downloaded), forKey: "downloaded")
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
    func addMark(i: Int, t: String, kind: String, pos: Double) {
        if !marks.contains(where: { $0.i == i && abs($0.pos - pos) < 0.5 }) {
            marks.append(Mark(i: i, t: t, kind: kind, pos: pos, at: Date())); save()
        }
    }
    func removeMark(_ m: Mark) { marks.removeAll { $0.id == m.id }; save() }
    /// 내 서재에서 지우기 — 그 책의 읽던 자리와 책갈피를 함께 빼고, 되돌리기를 위해 돌려줌 (0.2.0)
    func jiugi(_ i: Int) -> (ReadRec, [Mark])? {
        guard let r = reads.first(where: { $0.i == i }) else { return nil }
        let mk = marks.filter { $0.i == i }
        reads.removeAll { $0.i == i }; marks.removeAll { $0.i == i }
        save(); return (r, mk)
    }
    func doedollrigi(_ r: ReadRec, _ mk: [Mark]) {
        if !reads.contains(where: { $0.i == r.i }) { reads.append(r) }
        for m in mk where !marks.contains(where: { $0.id == m.id }) { marks.append(m) }
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
    static var ver: String { Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.1.0" }
    static var build: String { Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1" }
    static var mal: String { "AI점자도서관 \(ver)판, 빌드 \(build)" }
}
