// BYOD 방송 아이폰 — 방송 상태 한곳 (1.1.5판, 빌드 261010-BI1, 방송클, 이사장님 승인 2026-10-10 「아이폰용도 진행」)
// 안드로이드 BYOD 방송 1.1.5판(261009-B7)과 같은 일을 아이폰에서 합니다. 소리 방식·듣는 화면·주소 길이 모두 같습니다.
// 화면(ByodView)과 방송 일꾼(Seobeo·Sori)이 함께 보는 상태입니다. 화면 고침은 바뀔 때만(낭독기가 쉬지 않고 떠들지 않게).
import Foundation
import Combine

final class Bang: ObservableObject {
    static let shared = Bang()

    static let PAN = "1.1.5"   // 1.1.5(261010-BI1) 아이폰 첫 판 — 안드로이드 1.1.5와 같은 기능(방송·현장 점검·소리 크기·멈춘 폰 빼기·접속 도구·도움말·새 판 알림), 방송클, 이사장님 승인
    static let BILD = "261010-BI1"
    static let PORT: UInt16 = 8080

    // 화면이 보는 값 — 바뀔 때만 고침
    @Published var kyeojim = false            // 방송이 돌고 있는가
    @Published var sojae = "아직 정하지 않음"   // 실제로 소리를 받는 곳 이름
    @Published var juso = ""                   // 듣기 주소(http://…:8080/)
    @Published var jalmot = ""                 // 잘못된 일(없으면 빈 글)
    @Published var deutnunSu = 0               // 지금 듣는 분 수
    @Published var yocheongSeq = 0             // 새 도움 요청이 오면 하나씩 늘어남

    // 화면에 바로 보이지 않는 값 — 여러 일꾼이 씀(잠금으로 지킴)
    private let jamgeum = NSLock()
    private var _keu = false
    private var _sorikeugi = -99
    private var _sorikeugiTtae = Date.distantPast
    var sigakTtae = Date.distantPast

    struct Yocheong { let ttae: Date; let beonho: String; let jongryu: String }
    private(set) var yocheong: [Yocheong] = []

    /// 소리를 내보내는 중인가(노트북판의 큐)
    var keu: Bool {
        get { jamgeum.lock(); defer { jamgeum.unlock() }; return _keu }
        set { jamgeum.lock(); _keu = newValue; jamgeum.unlock() }
    }

    /// 들어오는 소리 크기(최근 3초 가장 큰 값, dB, 0이 가장 큼)와 잰 때
    func sorikeugiNeogi(_ db: Int) {
        jamgeum.lock(); _sorikeugi = db; _sorikeugiTtae = Date(); jamgeum.unlock()
    }
    func sorikeugiBogi() -> (Int, Date) {
        jamgeum.lock(); defer { jamgeum.unlock() }; return (_sorikeugi, _sorikeugiTtae)
    }

    // 일꾼(다른 줄기)에서 부르면 화면 줄기로 넘겨 고침
    func juge(_ f: @escaping (Bang) -> Void) {
        if Thread.isMainThread { f(self) } else { DispatchQueue.main.async { f(self) } }
    }

    func suGochigi(_ n: Int) { juge { b in if b.deutnunSu != n { b.deutnunSu = n } } }

    /// 듣는 분의 도움 요청을 받음 — 같은 번호·같은 종류는 1분에 한 번
    func yocheongBatgi(beonho: String, jongryu: String) -> Bool {
        jamgeum.lock()
        let majimak = yocheong.last(where: { $0.beonho == beonho && $0.jongryu == jongryu })
        let ok = beonho.isEmpty || majimak == nil || Date().timeIntervalSince(majimak!.ttae) > 60
        if ok {
            yocheong.append(Yocheong(ttae: Date(), beonho: beonho, jongryu: jongryu))
            if yocheong.count > 200 { yocheong.removeFirst(yocheong.count - 200) }
        }
        jamgeum.unlock()
        if ok { juge { b in b.yocheongSeq += 1 } }
        return ok
    }

    func majimakYocheong() -> Yocheong? {
        jamgeum.lock(); defer { jamgeum.unlock() }; return yocheong.last
    }

    static func jongryuMal(_ k: String) -> String {
        switch k {
        case "dowum": return "도움 요청"
        case "gungeum": return "궁금함"
        case "kkeunkim": return "소리가 끊김"
        default: return k
        }
    }
}
