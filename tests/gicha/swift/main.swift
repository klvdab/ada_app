// 여정 다시 돌려 보기 시험 — 아이폰 길눈 기차 안내(GichaAnnae.swift)를 시나리오 자리 기록에 그대로 돌려 봄
// 쓰는 법: siheom 시나리오.json … — 「있어야 할 말」이 없거나 「없어야 할 말」이 나오면 실패(나가는 값 1)
import Foundation

var silpae = 0
for pail in CommandLine.arguments.dropFirst() {
    guard let d = FileManager.default.contents(atPath: pail),
          let s = try? JSONSerialization.jsonObject(with: d) as? [String: Any] else { print("못 읽음: \(pail)"); silpae += 1; continue }
    let ireum = (s["ireum"] as? String) ?? pail
    Tongsin.shared.jaryo = s
    SoriEngine.shared.malMok = []
    let mk = s["mokjeok"] as? [String: Any] ?? [:]
    let mok = Mokjeok(ireum: mk["ireum"] as? String ?? "", lat: Chatgi.su(mk["lat"]) ?? 0, lon: Chatgi.su(mk["lon"]) ?? 0)
    let y = Yeojeong(mokjeok: mok)
    YeojeongEngine.shared.jigeum = y
    let nal = (s["nal"] as? String) ?? "20261017"
    var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(identifier: "Asia/Seoul")!
    let f = DateFormatter(); f.timeZone = c.timeZone; f.dateFormat = "yyyyMMdd"
    let bam = f.date(from: nal) ?? Date()
    var jigeum = bam
    GichaAnnae.sigye = { jigeum }
    GichaAnnae.shared.kkeut()
    for x in (s["jari"] as? [[String: Any]]) ?? [] {
        jigeum = bam.addingTimeInterval(Chatgi.su(x["t"]) ?? 0)
        let w = Wichi(lat: Chatgi.su(x["lat"]) ?? 0, lon: Chatgi.su(x["lon"]) ?? 0, ochae: Chatgi.su(x["acc"]) ?? 10,
                      banghyang: Chatgi.su(x["bang"]) ?? -1, sokdo: Chatgi.su(x["sokdo"]) ?? -1, ttae: jigeum, georeumChu: false)
        GichaAnnae.shared.wichi(w, y)
        RunLoop.main.run(until: Date().addingTimeInterval(0.003))
    }
    RunLoop.main.run(until: Date().addingTimeInterval(0.3))
    GichaAnnae.shared.kkeut()
    let tf = DateFormatter(); tf.timeZone = c.timeZone; tf.dateFormat = "HH:mm:ss"
    let mal = SoriEngine.shared.malMok
    print("── \(ireum) — 말 \(mal.count)마디")
    for (t, m) in mal { print("  \(tf.string(from: t))  \(m)") }
    let modu = mal.map { $0.1 }
    for p in (s["isseoya"] as? [String]) ?? [] where !modu.contains(where: { $0.range(of: p, options: .regularExpression) != nil }) {
        print("  ✗ 있어야 할 말이 없음: \(p)"); silpae += 1
    }
    for p in (s["eopseoya"] as? [String]) ?? [] {
        for m in modu where m.range(of: p, options: .regularExpression) != nil { print("  ✗ 없어야 할 말이 나옴(\(p)): \(m)"); silpae += 1 }
    }
}
print(silpae == 0 ? "통과" : "실패 \(silpae)건")
exit(silpae == 0 ? 0 : 1)
