// 즐겨찾기 — 자주 가는 곳. 폰 안에 담고, 요즘 쓴 곳이 맨 위로 옵니다.
// 담기는 것은 반드시 주소와 함께(2026-09-11 이사장님 지시).
import Foundation

struct JeulgyeoHang: Codable, Identifiable, Hashable {
    var ireum: String
    var juso: String
    var lat: Double
    var lon: Double
    var ttae: Date
    var id: String { "\(ireum)|\(lat)|\(lon)" }
    var jangso: Jangso { Jangso(ireum: ireum, juso: juso, lat: lat, lon: lon) }
}

final class Jeulgyeo: ObservableObject {
    static let shared = Jeulgyeo()
    @Published private(set) var mokrok: [JeulgyeoHang] = []
    private let pail: URL

    init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("gilnun", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        pail = dir.appendingPathComponent("jeulgyeo.json")
        if let d = try? Data(contentsOf: pail), let l = try? JSONDecoder().decode([JeulgyeoHang].self, from: d) {
            mokrok = l
        }
    }

    func itna(_ j: Jangso) -> Bool {
        mokrok.contains { $0.ireum == j.ireum || (abs($0.lat - j.lat) < 0.00005 && abs($0.lon - j.lon) < 0.00005) }
    }

    @discardableResult
    func damgi(_ j: Jangso) -> Bool {
        guard !itna(j) else { return false }
        mokrok.insert(JeulgyeoHang(ireum: j.ireum, juso: j.juso, lat: j.lat, lon: j.lon, ttae: Date()), at: 0)
        jeojang()
        Girok.shared.namgi("jeulgyeo_damgi", [:])
        return true
    }

    func jiugi(_ h: JeulgyeoHang) {
        mokrok.removeAll { $0.id == h.id }
        jeojang()
    }

    /// 쓴 곳을 맨 위로
    func sseum(_ j: Jangso) {
        guard let i = mokrok.firstIndex(where: { $0.ireum == j.ireum }) else { return }
        var h = mokrok.remove(at: i)
        h.ttae = Date()
        mokrok.insert(h, at: 0)
        jeojang()
    }

    private func jeojang() {
        if let d = try? JSONEncoder().encode(mokrok) { try? d.write(to: pail, options: .atomic) }
    }
}
