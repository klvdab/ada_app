// BYOD 방송 아이폰 — 방송 상태 한곳 (1.1.5판, 빌드 261010-BI1, 방송클, 이사장님 승인 2026-10-10 「아이폰용도 진행」)
// 안드로이드 BYOD 방송 1.1.5판(261009-B7)과 같은 일을 아이폰에서 합니다. 소리 방식·듣는 화면·주소 길이 모두 같습니다.
// 화면(ByodView)과 방송 일꾼(Seobeo·Sori)이 함께 보는 상태입니다. 화면 고침은 바뀔 때만(낭독기가 쉬지 않고 떠들지 않게).
import Foundation
import Combine

final class Bang: ObservableObject {
    static let shared = Bang()

    static let PAN = "1.9.2"   // 1.9.2(261026-BI13) 소리 자동 맞춤 — 작은 해설 소리를 최대 8배까지 키움(새방클, 이사장님 지시 2026-10-26). 1.9.1(261025-BI12) 접속 도구에 한 번에 듣기 엔에프시 태그·큐알, 공유기에서 떨어짐 알림 — 아이피타임 방식에서 와이파이를 떠나면 진동 세 번과 보이스오버로 알리고 기록(새방클, 이사장님 승인 2026-10-25). 1.9.0(261024-BI11) 대조표 미진한 부분 고치기(새방송클, 이사장님 지시 2026-10-24) — 공유기 스스로 알아내기·다른 와이파이 경고·전화번호 안내·무대 영상 녹화·노트북판 현장 기능·여러 갈래 해설·만족도·구역별 인원. 1.8.0(261023-BI10) 이용 기록(방송 켜고 끔·인터넷 방송 상태·1분마다 듣는 분 수)을 협회 서버에 남김, 현장 점검에 「듣는 앱이 이 폰을 찾을 수 있는지」(새방클, 이사장님 지시 2026-10-23). 1.7.0(261021-BI9) 공유기 고르기(아이피타임·플린트 2)를 첫 화면 방송 시작 바로 위에, 처음값 아이피타임, 「예비 방식」을 「아이피타임 방식」으로, 아이피타임 방식에서 이 아이폰이 행사 녹음·결과보고·다시 듣기에 올림, 큐알을 작게(인쇄 약 5센티미터, 화면 약 4센티미터)·A4 자르는 선(방클, 이사장님 승인 2026-10-21). 1.6.0(261019-BI8) 녹화·방송 지킴이 — 교육 녹화·인터넷 방송이 멈추거나 화면이 잠겨 녹화가 끊기면, BYOD 방송의 해설 소리가 6초 넘게 안 들어오면 3초에 한 번 조용한 알림 소리(통화 중에는 안 울림), 화면 쪽 녹화 다시 잇기, 「BYOD 방송」 이름(방클, 이사장님 지시 2026-10-19). 1.5.0(261018-BI7) LVD 방송 정리 — 현장 방송 시작(켜면 인터넷 방송도 함께, 셀룰러로), 현장영상해설사 전화번호 칸, 인터넷 방송만 하기·TV해설방송 하기, 같은 행사 녹음 하나만 다시 듣기에(방클, 이사장님 승인 「1」). 1.4.0(261016-BI6) 첫 화면에 라이브 현장영상해설 방송(인터넷)·현장영상해설교육 녹화 — 배프 화면을 앱 안에서, 카메라·마이크, 화면 꺼짐 막기(방클, 이사장님 지시). 1.3.1(261015-BI5) 이름을 LVD 송출로 바꿈(듣는 앱 LVD 방송과 나눔), 통화 중 듣는 분께 「잠시 기다려 주십시오」 안내(방송클, 이사장님 승인 「1」). 1.3.0(261014-BI4) 행사 이름·현장영상해설사 칸, 공유기가 녹음과 행사 기록을 남기고 방송을 멈추면 받아 배프 다시듣기에 올림, 지난 행사 기록(방송클, 이사장님 승인 「1」). 1.2.0(261014-BI3) 플린트 2 방식 — 방송 서버를 공유기 안에, 이 아이폰은 해설 소리만 보냄·와이파이 태그·큐알 한 장으로 듣기까지(방송클, 이사장님 승인 「1」). 1.1.6(261012-BI2) 스티커를 엔에프시 태그로 부름·듣기 주소 바로잡기와 확인·엔에프시 태그 쓴 뒤 다시 읽어 확인·A4 인쇄용 한 장·듣기 화면 단추에 커서(방송클, 이사장님 승인). 1.1.5(261010-BI1) 아이폰 첫 판 — 안드로이드 1.1.5와 같은 기능(방송·현장 점검·소리 크기·멈춘 폰 빼기·접속 도구·도움말·새 판 알림), 방송클, 이사장님 승인
    static let BILD = "261026-BI13"
    static var mundapTtae = Date.distantPast   // 1.8.0 듣는 앱이 이 아이폰(아이피타임 방식 방송 서버)을 찾을 수 있다고 마지막으로 확인한 때
    static let PORT: UInt16 = 8080

    // 화면이 보는 값 — 바뀔 때만 고침
    @Published var kyeojim = false            // 방송이 돌고 있는가
    @Published var sojae = "아직 정하지 않음"   // 실제로 소리를 받는 곳 이름
    @Published var juso = ""                   // 듣기 주소(http://…:8080/)
    @Published var jalmot = ""                 // 잘못된 일(없으면 빈 글)
    @Published var deutnunSu = 0               // 지금 듣는 분 수
    @Published var yocheongSeq = 0             // 새 도움 요청이 오면 하나씩 늘어남
    @Published var inteonet = ""               // 1.5.0 함께 나가는 인터넷 방송 한 줄(없으면 빈 글)
    @Published var gingeup = ""                // 1.9.0 (261024-BI11) 피디 화면에서 낸 긴급 안내(끝나면 빈 글)
    @Published var gingeupSeq = 0              // 1.9.0 긴급 안내를 내거나 끝낼 때마다 하나씩 늘어남

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

    func suGochigi(_ n: Int) { GigiNokeum.shared.su = n; juge { b in if b.deutnunSu != n { b.deutnunSu = n } } }   // 1.7.0 녹음 기록에도

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
        if ok { juge { b in b.yocheongSeq += 1 }; GigiNokeum.shared.yocheong(jongryu: jongryu, beonho: beonho) }   // 1.7.0 아이피타임 방식 행사 기록에도
        return ok
    }

    /// 1.9.0 (261024-BI11) 피디 화면의 긴급 안내(HyeonjangGil 이 부름) — 첫 화면 한 줄로 한 번 알림
    func gingeupBatgi(_ t: String) { juge { b in b.gingeup = t; b.gingeupSeq += 1 } }

    func majimakYocheong() -> Yocheong? {
        jamgeum.lock(); defer { jamgeum.unlock() }; return yocheong.last
    }

    static func jongryuMal(_ k: String) -> String {
        switch k {
        case "dowum": return "도움 요청"
        case "gungeum", "gunggeum": return "궁금함"   // 1.9.0 노트북판 듣는 화면은 gunggeum
        case "kkeunkim": return "소리가 끊김"
        case "jakda": return "소리가 작음"            // 1.9.0 노트북판 듣는 화면의 짧은 요청
        case "cheoncheon": return "조금 천천히"
        default: return k
        }
    }
}
