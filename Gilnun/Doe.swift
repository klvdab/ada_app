// 되짚어 나가기 — 앱 2.13.0 (빌드 260929-8, 이사장님 승인 1). 웹 길눈 doe.html 과 같은 쓰임.
// 병원·관공서처럼 위성이 잡히지 않는 건물 안에서, 들어갈 때 켜 두면 걸음 수와 방향으로 길을 기억했다가
// 나올 때 왔던 길을 거꾸로 알려 드립니다. "몇 걸음 걸으신 뒤 몇 시 방향으로 꺾으십시오"로 말합니다.
// 기억한 길은 이 폰에만 남고(다른 사람에게 가지 않음) 지우실 때까지 남습니다. 앱이 꺼졌다 켜져도 이어 기억합니다.
import Foundation
import Combine

/// 한 토막 — 같은 쪽으로 곧게 걸은 걸음 수와 그 방향(북쪽 0도)
struct DoeTomak: Codable {
    var georeum: Int
    var bang: Double
}

final class DoeEngine: ObservableObject {
    static let shared = DoeEngine()

    enum Sangtae: String { case swim, gieok, annae }

    @Published private(set) var sangtae: Sangtae = .swim
    @Published private(set) var tomak: [DoeTomak] = []     // 기억한 길
    @Published private(set) var jul = ""                   // 화면에 보이는 지금 상태 한 줄

    private let kiGil = "gn.doeGil"
    private let kiGieok = "gn.doeGieokJung"
    private var sigye: Timer?
    private var majimakGeoreum = 0
    private var bitgan: [Double] = []        // 방향이 어긋난 채 걸은 걸음들의 방향
    private var sseulHyang: [Double] = []    // 지금 토막 걸음들의 방향(평균을 냄)

    // 안내 중
    private var gil: [DoeTomak] = []          // 거꾸로 뒤집은 길
    private var gi = 0                        // 지금 토막
    private var gugangSijak = 0               // 이 토막을 시작한 때의 걸음
    private var yegoHaet = false
    private var majimakBangMal = Date.distantPast
    private var majimakNamEum = 0

    private init() {
        if let d = UserDefaults.standard.data(forKey: kiGil), let t = try? JSONDecoder().decode([DoeTomak].self, from: d) { tomak = t }
    }

    var gieokItda: Bool { !tomak.isEmpty && tomak.reduce(0) { $0 + $1.georeum } > 0 }
    var chongGeoreum: Int { tomak.reduce(0) { $0 + $1.georeum } }
    var kkeokSu: Int { max(0, tomak.count - 1) }

    // MARK: 들어갈 때 — 기억

    func gieokSijak() {
        WichiEngine.shared.sijak()
        tomak = []
        bitgan = []
        sseulHyang = []
        majimakGeoreum = WichiEngine.shared.georeumSu
        sangtae = .gieok
        UserDefaults.standard.set(true, forKey: kiGieok)
        jeojang()
        jul = "길을 기억하는 중입니다."
        mal("지금부터 걸으신 길을 기억합니다. 화면을 켜 두시고 평소처럼 걸어 들어가십시오. 나오실 때 되짚어 나가기를 누르십시오.")
        Girok.shared.namgi("doe_gieok", [:])
        sigyeKyeogi()
    }

    /// 앱이 꺼졌다 켜져도 기억하던 중이면 이어 기억
    func ieoGagi() {
        guard UserDefaults.standard.bool(forKey: kiGieok) else { return }
        majimakGeoreum = WichiEngine.shared.georeumSu
        sangtae = .gieok
        jul = "길을 기억하는 중입니다. 지금까지 \(chongGeoreum)걸음."
        sigyeKyeogi()
    }

    func gieokGeuman() {
        guard sangtae == .gieok else { return }
        gieokMaechim()
        sangtae = .swim
        mal(gieokItda ? "길 기억을 멈췄습니다. 모두 \(chongGeoreum)걸음, \(kkeokSu)번 꺾으셨습니다. 나오실 때 되짚어 나가기를 누르십시오." : "길 기억을 멈췄습니다. 기억한 걸음이 없습니다.")
    }

    func jiugi() {
        sigye?.invalidate(); sigye = nil
        tomak = []
        sangtae = .swim
        UserDefaults.standard.removeObject(forKey: kiGil)
        UserDefaults.standard.removeObject(forKey: kiGieok)
        jul = ""
        mal("기억한 길을 지웠습니다.")
    }

    private func gieokMaechim() {
        sigye?.invalidate(); sigye = nil
        // 어긋난 채 남은 걸음은 지금 토막에 되돌려 넣음
        if !bitgan.isEmpty, !tomak.isEmpty { tomak[tomak.count - 1].georeum += bitgan.count }
        bitgan = []
        tomak = tomak.filter { $0.georeum > 0 }
        UserDefaults.standard.set(false, forKey: kiGieok)
        jeojang()
        jul = gieokItda ? "기억한 길 — \(chongGeoreum)걸음, \(kkeokSu)번 꺾음." : ""
    }

    private func sigyeKyeogi() {
        sigye?.invalidate()
        sigye = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in self?.dolgi() }
    }

    private func dolgi() {
        switch sangtae {
        case .gieok: gieokDolgi()
        case .annae: annaeDolgi()
        case .swim: break
        }
    }

    /// 새 걸음마다 그때의 방향을 보아 — 45도 넘게 어긋난 걸음이 셋 이어지면 꺾은 것으로 보고 새 토막을 엶
    private func gieokDolgi() {
        let n = WichiEngine.shared.georeumSu
        let sae = n - majimakGeoreum
        guard sae > 0 else { return }
        majimakGeoreum = n
        let h = WichiEngine.shared.nachimban
        guard h >= 0 else {
            if tomak.isEmpty { tomak.append(DoeTomak(georeum: 0, bang: 0)) }
            tomak[tomak.count - 1].georeum += sae
            return
        }
        for _ in 0..<min(sae, 20) {
            if tomak.isEmpty {
                tomak.append(DoeTomak(georeum: 1, bang: h))
                sseulHyang = [h]
                continue
            }
            let bon = tomak[tomak.count - 1].bang
            if abs(DoeEngine.chai(h, bon)) > 45 {
                bitgan.append(h)
                if bitgan.count >= 3 {
                    let saeBang = DoeEngine.pyeonggyun(bitgan)
                    tomak.append(DoeTomak(georeum: bitgan.count, bang: saeBang))
                    sseulHyang = bitgan
                    bitgan = []
                }
            } else {
                tomak[tomak.count - 1].georeum += 1 + bitgan.count
                bitgan = []
                sseulHyang.append(h)
                if sseulHyang.count > 40 { sseulHyang.removeFirst() }
                tomak[tomak.count - 1].bang = DoeEngine.pyeonggyun(sseulHyang)
            }
        }
        if sae > 20 { tomak[tomak.count - 1].georeum += sae - 20 }
        jul = "길을 기억하는 중입니다. 지금까지 \(chongGeoreum)걸음, \(kkeokSu)번 꺾음."
        if n % 20 == 0 { jeojang() }
    }

    // MARK: 나올 때 — 되짚어 안내

    func doejipgi() {
        if sangtae == .gieok { gieokMaechim() }
        guard gieokItda else {
            mal("기억해 둔 길이 없습니다. 들어가실 때 들어갑니다를 눌러 두십시오.")
            return
        }
        WichiEngine.shared.sijak()
        gil = tomak.reversed().map { DoeTomak(georeum: $0.georeum, bang: ($0.bang + 180).truncatingRemainder(dividingBy: 360)) }
        gi = 0
        gugangSijak = WichiEngine.shared.georeumSu
        yegoHaet = false
        majimakNamEum = gil[0].georeum
        sangtae = .annae
        Girok.shared.namgi("doe_annae", ["georeum": chongGeoreum, "kkeok": kkeokSu])
        let cheot = gil[0]
        var m = "왔던 길을 되짚어 나갑니다. 모두 \(chongGeoreum)걸음, \(kkeokSu)번 꺾습니다. "
        let h = WichiEngine.shared.nachimban
        if h >= 0 {
            let s = WichiEngine.sigyeBanghyang(jeongmyeon: h, mokpyo: cheot.bang)
            m += s == 12 ? "앞으로 " : "\(s)시 방향으로 돌아 서신 뒤 "
        } else {
            m += "들어오신 쪽으로 돌아 서신 뒤 "
        }
        m += "\(cheot.georeum)걸음 걸으십시오."
        jul = "되짚어 나가는 중입니다. 첫 토막 \(cheot.georeum)걸음."
        mal(m)
        sigyeKyeogi()
    }

    func annaeGeuman() {
        guard sangtae == .annae else { return }
        sigye?.invalidate(); sigye = nil
        sangtae = .swim
        jul = gieokItda ? "기억한 길 — \(chongGeoreum)걸음, \(kkeokSu)번 꺾음." : ""
        mal("되짚어 나가기를 멈췄습니다. 기억한 길은 그대로 있습니다.")
    }

    /// 지금 무엇을 할지 다시 듣기
    func jigeumMal() {
        switch sangtae {
        case .swim: mal(gieokItda ? "기억해 둔 길이 있습니다. \(chongGeoreum)걸음, \(kkeokSu)번 꺾습니다." : "기억해 둔 길이 없습니다.")
        case .gieok: mal("길을 기억하는 중입니다. 지금까지 \(chongGeoreum)걸음, \(kkeokSu)번 꺾으셨습니다.")
        case .annae:
            let nam = max(0, gil[gi].georeum - (WichiEngine.shared.georeumSu - gugangSijak))
            var m = "이 토막에서 \(nam)걸음 더 걸으십시오."
            if gi + 1 < gil.count { m += " 그다음 \(kkeokMal(gi))." } else { m += " 그러면 들어오신 곳입니다." }
            mal(m)
        }
    }

    private func kkeokMal(_ i: Int) -> String {
        let s = WichiEngine.sigyeBanghyang(jeongmyeon: gil[i].bang, mokpyo: gil[i + 1].bang)
        return s == 12 ? "그대로 앞으로" : "\(s)시 방향으로 꺾으십시오"
    }

    private func annaeDolgi() {
        guard gi < gil.count else { return }
        let geoleun = WichiEngine.shared.georeumSu - gugangSijak
        let nam = gil[gi].georeum - geoleun
        // 꺾는 곳 다섯 걸음 앞에서 미리
        if nam <= 5 && nam > 1 && !yegoHaet && gil[gi].georeum > 8 {
            yegoHaet = true
            if gi + 1 < gil.count {
                mal("\(nam)걸음 뒤 \(kkeokMal(gi)).")
            } else {
                mal("\(nam)걸음 더 가시면 들어오신 곳입니다.")
            }
        }
        if nam <= 0 {
            if gi + 1 >= gil.count {
                sigye?.invalidate(); sigye = nil
                sangtae = .swim
                jul = "기억한 길 — \(chongGeoreum)걸음, \(kkeokSu)번 꺾음."
                Girok.shared.namgi("doe_kkeut", [:])
                mal("들어오신 곳에 닿았습니다. 되짚어 나가기를 마칩니다. 기억한 길은 지우실 때까지 남습니다.")
                return
            }
            let m = "지금 \(kkeokMal(gi)). 그다음 \(gil[gi + 1].georeum)걸음."
            gi += 1
            gugangSijak = WichiEngine.shared.georeumSu
            yegoHaet = false
            majimakNamEum = gil[gi].georeum
            majimakBangMal = Date()
            jul = "되짚어 나가는 중입니다. \(gi + 1)번째 토막, \(gil[gi].georeum)걸음."
            mal(m)
            return
        }
        // 열 걸음마다 남은 걸음, 방향이 크게 어긋나면 6초에 한 번
        if majimakNamEum - nam >= 10 {
            majimakNamEum = nam
            jul = "되짚어 나가는 중입니다. 이 토막 \(nam)걸음 남음."
            mal("\(nam)걸음 남았습니다.", .jeongbo)
        }
        let h = WichiEngine.shared.nachimban
        if h >= 0, geoleun >= 2, abs(DoeEngine.chai(h, gil[gi].bang)) > 50, Date().timeIntervalSince(majimakBangMal) > 6 {
            majimakBangMal = Date()
            let s = WichiEngine.sigyeBanghyang(jeongmyeon: h, mokpyo: gil[gi].bang)
            mal("방향이 어긋났습니다. \(s)시 방향으로 돌아 서십시오.")
        }
    }

    // MARK: 셈

    private func jeojang() {
        if let d = try? JSONEncoder().encode(tomak) { UserDefaults.standard.set(d, forKey: kiGil) }
    }

    /// 두 방향의 차이(-180~180, 오른쪽이 +)
    static func chai(_ a: Double, _ b: Double) -> Double {
        var d = (a - b).truncatingRemainder(dividingBy: 360)
        if d > 180 { d -= 360 }
        if d < -180 { d += 360 }
        return d
    }

    /// 방향들의 평균(둥근 평균)
    static func pyeonggyun(_ l: [Double]) -> Double {
        guard !l.isEmpty else { return 0 }
        let r = Double.pi / 180
        let x = l.reduce(0.0) { $0 + cos($1 * r) }, y = l.reduce(0.0) { $0 + sin($1 * r) }
        var d = atan2(y, x) / r
        if d < 0 { d += 360 }
        return d
    }

    private func mal(_ t: String, _ g: MalGeup = .annae) {
        SoriEngine.shared.mal(t, g)
    }
}
