// 여정 엔진 — 목적지, 지금 차례, 탈것을 한 곳에서 다룹니다.
// ① 화면이 바뀌어도, 음악을 틀어도, 앱을 껐다 켜도 여정은 그대로(폰 안 파일에 담음)
// ② 빠르기로 탈것을 알아채되, 이용자가 바로잡은 탈것이 가장 앞섬
// ③ 열두 시간 넘게 손대지 않은 여정은 저절로 끝냄
import Foundation
import Combine

enum Talgeot: String, Codable, CaseIterable {
    case georeum, cha, jihacheol, beoseu, gicha, gosokbeoseu
    var ireum: String {
        switch self {
        case .georeum: return "걸어서"
        case .cha: return "차"
        case .jihacheol: return "지하철"
        case .beoseu: return "버스"
        case .gicha: return "기차"
        case .gosokbeoseu: return "고속버스"
        }
    }
}

enum Danggye: String, Codable {
    case eotteoke, taneunGotKkaji, taneunJung, namEunGil, dochak
    var ireum: String {
        switch self {
        case .eotteoke: return "어떻게 갈지 정할 차례"
        case .taneunGotKkaji: return "타는 곳까지 가는 차례"
        case .taneunJung: return "타고 가는 중"
        case .namEunGil: return "남은 길을 걷는 차례"
        case .dochak: return "도착"
        }
    }
}

struct Mokjeok: Codable, Equatable {
    var ireum: String
    var lat: Double
    var lon: Double
    var juso: String
}

struct Yeojeong: Codable {
    var mokjeok: Mokjeok
    var danggye: Danggye
    var talgeot: Talgeot
    var barojabeum: Bool
    var sijak: Date
    var gaengsin: Date
    var jiha: JihaGil? = nil      // 지하철로 가는 여정이면 그 길
}

final class YeojeongEngine: ObservableObject {
    static let shared = YeojeongEngine()

    @Published private(set) var jigeum: Yeojeong?
    /// 빠르기로 알아챈 탈것(이용자가 바로잡지 않았을 때 씀)
    @Published private(set) var sokdoChujeong: Talgeot = .georeum

    private var ssak = Set<AnyCancellable>()
    private var ppareunTtae: Date?
    private var neurinTtae: Date?
    private let pail: URL

    init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("gilnun", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        pail = dir.appendingPathComponent("yeojeong.json")
        WichiEngine.shared.saeWichi
            .sink { [weak self] w in self?.sokdoBoda(w) }
            .store(in: &ssak)
    }

    /// 앱이 켜질 때 지난 여정을 되살림
    func bureogi() {
        guard let d = try? Data(contentsOf: pail),
              let y = try? JSONDecoder().decode(Yeojeong.self, from: d) else { return }
        if Date().timeIntervalSince(y.gaengsin) > 12 * 3600 {
            kkeut()
            return
        }
        jigeum = y
    }

    func jeonghagi(_ m: Mokjeok) {
        jigeum = Yeojeong(mokjeok: m, danggye: .eotteoke, talgeot: .georeum, barojabeum: false,
                          sijak: Date(), gaengsin: Date())
        Girok.shared.namgi("yeojeong_sijak", ["mok": m.ireum])
        jeojang()
    }

    func danggyeBakkugi(_ d: Danggye) {
        guard var y = jigeum else { return }
        y.danggye = d
        y.gaengsin = Date()
        jigeum = y
        Girok.shared.namgi("yeojeong_danggye", ["d": d.rawValue])
        jeojang()
    }

    /// 지하철 길 담기(없애려면 nil)
    func jihaNoki(_ g: JihaGil?) {
        guard var y = jigeum else { return }
        y.jiha = g
        y.gaengsin = Date()
        jigeum = y
        jeojang()
    }

    /// 탈것 정하기 — barojabeum 이 참이면 이용자가 바로잡은 것(가장 앞섬)
    func talgeotJeonghagi(_ t: Talgeot, barojabeum: Bool) {
        guard var y = jigeum else { return }
        y.talgeot = t
        y.barojabeum = barojabeum
        y.gaengsin = Date()
        jigeum = y
        jeojang()
    }

    /// 이용자가 탈것을 바로잡음 — 가장 앞섬
    func talgeotBarojapgi(_ t: Talgeot) {
        guard var y = jigeum else { return }
        y.talgeot = t
        y.barojabeum = true
        y.gaengsin = Date()
        jigeum = y
        Girok.shared.namgi("yeojeong_barojapgi", ["t": t.rawValue])
        jeojang()
    }

    func kkeut() {
        if jigeum != nil { Girok.shared.namgi("yeojeong_kkeut", [:]) }
        jigeum = nil
        try? FileManager.default.removeItem(at: pail)
    }

    /// 지금 탈것 — 바로잡은 것이 가장 앞서고, 그다음이 빠르기로 알아챈 것
    var talgeot: Talgeot {
        if let y = jigeum, y.barojabeum { return y.talgeot }
        if sokdoChujeong != .georeum { return sokdoChujeong }
        return jigeum?.talgeot ?? .georeum
    }

    func jeojang() {
        guard let y = jigeum, let d = try? JSONEncoder().encode(y) else { return }
        try? d.write(to: pail, options: .atomic)
    }

    /// 시속 15킬로미터를 15초 넘게 넘으면 차(시속 150을 넘으면 기차), 시속 8 아래로 3분이면 걸음
    private func sokdoBoda(_ w: Wichi) {
        guard !w.georeumChu, w.ochae <= 30 else { return }
        let kmh = w.sokdo * 3.6
        let now = Date()
        if kmh > 15 {
            neurinTtae = nil
            if ppareunTtae == nil { ppareunTtae = now }
            if let t = ppareunTtae, now.timeIntervalSince(t) >= 15 {
                let sae: Talgeot = kmh > 150 ? .gicha : (sokdoChujeong == .gicha ? .gicha : .cha)
                if sae != sokdoChujeong {
                    sokdoChujeong = sae
                    Girok.shared.namgi("talgeot_chujeong", ["t": sae.rawValue, "kmh": Int(kmh)])
                }
            }
        } else if kmh < 8 {
            ppareunTtae = nil
            if neurinTtae == nil { neurinTtae = now }
            if let t = neurinTtae, now.timeIntervalSince(t) >= 180, sokdoChujeong != .georeum {
                sokdoChujeong = .georeum
                Girok.shared.namgi("talgeot_chujeong", ["t": "georeum", "kmh": Int(kmh)])
            }
        }
    }
}
