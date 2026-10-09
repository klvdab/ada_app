// 2.58.0 (261009-I12, 이사장님 승인 2026-10-09 「클이 제안한 방법을 모두 승인한다」) 차 부르기 — 교통약자 묶음
// ① 지역 이용 안내: 나스 jeom/kol_annae.json(171곳 조사, 2026-10-08)으로 지금 시·군의 시각장애인 이용 조건과 등록하는 곳을 알려 드림
//    공식 누리집으로 확인한 곳만 조건을 말하고, 보도로만 확인한 곳은 "전화로 먼저 물어보십시오"라고 말함
// ② 복지카드 보내기: 등록이 필요한 곳은 내 서류 보관함의 서류를 그 지역 메일로 바로(메일 앱 창에서 이용자가 보냄)
// ③ 서울은 복지콜을 맨 앞에(서울시설공단 안내: 시각장애인은 원칙상 복지콜)
// ④ 부르기와 타기 돕기: 걸기 전에 상담원께 말할 것을 들려 드리고, 통화 중에도 알림으로 볼 수 있게 남김
// ⑤ 이용 기록: 부를 때 저절로 남기고, 끊은 뒤 "배차되었습니까" 한마디만 여쭙고, 차에 타면 저절로 탄 시각을 남김 → 나의 이용 성적표
//    기록은 폰 안에만. 협회로 보내기는 동의하신 분만(받는 곳이 준비되면 보냄)
// ⑥ 정기 호출: 평일 정한 시각에 알림으로 깨워 드리고 한 번 두드리면 전화. 공휴일은 저절로 건너뜀, 그날만 쉬기·바꾸기는 말로
import SwiftUI
import UIKit
import Combine
import CallKit
import MessageUI
import UserNotifications

// MARK: - 지역 이용 안내

struct KolAnnae {
    let sido: String
    let sigungu: String
    let ireum: String
    let iyong: String      // 가능, 조건부
    let jogeon: String
    let hwagin: String     // 공식, 보도
    let pilyo: String
    let bangbeop: String
    let seoryu: String
    let paekseu: String
    let meil: String
    let unhaeng: String
    let beomwi: String
    let baucheo: Bool

    var gongsik: Bool { hwagin == "공식" }
    var deungrokPilyo: Bool { pilyo.hasPrefix("필요") }
}

final class KolAnnaeJaryo {
    static let shared = KolAnnaeJaryo()
    private(set) var jul: [KolAnnae] = []

    func bureogi() {
        Task {
            guard let d = try? await Tongsin.shared.get("kol_annae.json"),
                  let o = try? JSONSerialization.jsonObject(with: d.data) as? [String: Any],
                  let w = o["w"] as? [String], let r = o["r"] as? [[Any]] else { return }
            func mal(_ x: Any) -> String {
                if let i = (x as? NSNumber)?.intValue, i >= 0, i < w.count { return w[i] }
                return (x as? String) ?? ""
            }
            func geul(_ x: Any) -> String { (x as? String) ?? "" }
            var l: [KolAnnae] = []
            for x in r where x.count >= 14 {
                l.append(KolAnnae(sido: geul(x[0]), sigungu: geul(x[1]), ireum: geul(x[2]), iyong: geul(x[3]),
                                  jogeon: mal(x[4]), hwagin: geul(x[5]), pilyo: mal(x[6]), bangbeop: mal(x[7]),
                                  seoryu: mal(x[8]), paekseu: geul(x[9]), meil: geul(x[10]), unhaeng: mal(x[11]),
                                  beomwi: mal(x[12]), baucheo: ((x[13] as? NSNumber)?.intValue ?? 0) == 1))
            }
            let l2 = l
            await MainActor.run { self.jul = l2 }
        }
    }

    /// 지금 지역의 안내 — 시·군 → (서울은 복지콜) → 시·도 광역, 바우처택시는 뺌
    func jigeum() -> KolAnnae? {
        guard let j = MalSajeon.shared.kolJiyeok() else { return nil }
        let l = jul.filter { $0.sido == j.sido && !$0.baucheo }
        if let s = l.first(where: { a in
            !a.sigungu.isEmpty && j.sigungu.contains(where: { $0 == a.sigungu || $0.hasPrefix(a.sigungu) || a.sigungu.hasPrefix($0) })
        }) { return s }
        if j.sido == "서울특별시", let b = l.first(where: { $0.ireum.contains("복지콜") }) { return b }
        return l.first { $0.sigungu.isEmpty }
    }

    /// 들려 드릴 이용 조건과 등록하는 곳
    func mal(_ a: KolAnnae?) -> String {
        guard let a = a else {
            return "이 지역 이용 조건은 아직 확인하지 못했습니다. 전화로 시각장애인도 탈 수 있는지 먼저 물어보십시오."
        }
        guard a.gongsik else {
            return "\(a.ireum)의 이용 조건은 아직 공식 안내로 확인하지 못했습니다. 전화로 시각장애인도 탈 수 있는지 먼저 물어보십시오."
        }
        var t = a.iyong == "가능" ? "시각장애인도 탈 수 있습니다." : "장애 정도가 심한 시각장애인은 미리 등록하고 심사를 받아야 탈 수 있습니다."
        if a.deungrokPilyo || a.iyong != "가능" {
            var gil: [String] = []
            if !a.paekseu.isEmpty { gil.append("팩스 \(a.paekseu)") }
            if !a.meil.isEmpty { gil.append("메일") }
            if !gil.isEmpty { t += " 서류는 " + gil.joined(separator: "이나 ") + "로 보냅니다." }
            else if !a.bangbeop.isEmpty && !a.bangbeop.hasPrefix("확인 중") { t += " 등록은 이렇게 합니다. " + a.bangbeop }
        }
        return t
    }
}

// MARK: - 이용 기록

struct KolGirokHang: Codable, Identifiable, Equatable {
    var id = UUID().uuidString
    var ttae: Date
    var ireum: String
    var jeonhwa: String
    var sido: String
    var sigungu: String
    var jeonggi: Bool
    var yeongyeol: Bool?        // 통화가 이어졌는지(폰이 알려 줄 때만)
    var kkeunnam: Date?         // 통화가 끝난 때(폰이 알려 줄 때만)
    var gyeolgwa: String?       // baecha 배차됨, gidarim 기다리라 함, andoem 안 된다 함
    var tan: Date?              // 차에 탄 때(저절로 알아챔)
    var naerim: Date?           // 2.60.0 탄 뒤 걸어서 내린 때(저절로 알아챔)
    var gyeolgwaTtae: Date?     // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 배차·기다리라 함을 남긴 때(차 못 박기 3시간을 여기서 셈)
    var pulrim: Date?           // 2.61.0 차 못 박기를 푼 때(내렸어·여정 끝·하던 일 멈춤·땅속·콜 취소)
    var yeojjum = false         // 끊은 뒤 여쭈었는지
}

// MARK: - 차 부르기 엔진

final class ChaBureugi: NSObject, ObservableObject, CXCallObserverDelegate {
    static let shared = ChaBureugi()

    @Published private(set) var girok: [KolGirokHang] = []
    /// 끊은 뒤 "배차되었습니까"를 여쭌 기록
    @Published private(set) var mureumId: String?
    private var mureumTtae = Date.distantPast
    /// 안 된다고 했을 때 권하는 다음 수단
    @Published private(set) var daeum: KolBeonho?
    private var daeumTtae = Date.distantPast
    /// "복지카드를 지금 메일로 보낼까요?"를 여쭌 때
    @Published var meilYeolgi = false
    private var meilMureumTtae = Date.distantPast
    /// 보조견과 함께 타시는지(상담원께 말할 것)
    @Published var bojogyeon: Bool { didSet { UserDefaults.standard.set(bojogyeon, forKey: "gn.kolBojogyeon") } }
    /// 협회로 이름 없이 보내기 동의 — 0 아직 안 여쭘, 1 동의, 2 동의 안 함
    @Published var dongui: Int { didSet { UserDefaults.standard.set(dongui, forKey: "gn.kolDongui") } }

    private let gwanchal = CXCallObserver()
    private var mukkeum = Set<AnyCancellable>()
    private var dolgo = false
    private let ki = "gn.kolGirok"

    override init() {
        _bojogyeon = Published(initialValue: UserDefaults.standard.bool(forKey: "gn.kolBojogyeon"))
        _dongui = Published(initialValue: UserDefaults.standard.integer(forKey: "gn.kolDongui"))
        super.init()
        if let d = UserDefaults.standard.data(forKey: ki), let l = try? JSONDecoder().decode([KolGirokHang].self, from: d) { girok = l }
    }

    func sijak() {
        guard !dolgo else { return }
        dolgo = true
        KolAnnaeJaryo.shared.bureogi()
        gwanchal.setDelegate(self, queue: .main)
        NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { self?.salpigi() }
        }
        TalgeotGamji.shared.$chujeong
            .receive(on: DispatchQueue.main)
            .sink { [weak self] t in
                // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 지하철 안내 중(땅 위 구간)에는 "차에 타셨습니다"를 하지 않음
                // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 여정의 탈것이 지하철·버스·기차·고속버스이면(지하철로 바로잡으셨는데 지하철 길을 못 찾은 때 포함) 하지 않음
                let yt = YeojeongEngine.shared.jigeum?.talgeot
                let dareunTalgeot = yt == .jihacheol || yt == .beoseu || yt == .gicha || yt == .gosokbeoseu
                if t == .cha && YeojeongEngine.shared.jigeum?.jiha == nil && !JihacheolEngine.shared.dolgo && !dareunTalgeot { self?.chaTatda() }
                else if t == .georeum && TalgeotGamji.shared.jigeumUmjigim == "걸음" { self?.chaNaerim() }
            }
            .store(in: &mukkeum)
        JeonggiHochul.shared.sijak()
    }

    private func jeojang() {
        if girok.count > 500 { girok.removeFirst(girok.count - 500) }
        if let d = try? JSONEncoder().encode(girok) { UserDefaults.standard.set(d, forKey: ki) }
    }

    private func gochigi(_ id: String, _ f: (inout KolGirokHang) -> Void) {
        guard let i = girok.firstIndex(where: { $0.id == id }) else { return }
        var h = girok[i]
        f(&h)
        girok[i] = h
        jeojang()
    }

    // MARK: 지금 지역의 수단

    /// 지금 지역의 콜 — 서울은 복지콜을 맨 앞에
    func sudanDeul() -> [KolBeonho] {
        var l = MalSajeon.shared.kolDeul()
        if MalSajeon.shared.kolJiyeok()?.sido == "서울특별시", let i = l.firstIndex(where: { $0.ireum.contains("복지콜") }), i > 0 {
            let b = l.remove(at: i)
            l.insert(b, at: 0)
        }
        return l
    }

    func cheotSudan() -> KolBeonho? { sudanDeul().first }

    /// 정기 호출의 수단 이름 — bokji, jangaein, nabi
    func sudan(_ jong: String) -> KolBeonho? {
        if jong == "bokji", let b = sudanDeul().first(where: { $0.ireum.contains("복지") }) { return b }
        return MalSajeon.shared.kolChatgi(jong) ?? cheotSudan()
    }

    // MARK: 걸기

    /// 상담원께 말할 것을 들려 드린 뒤 전화를 겁니다
    func geolgi(_ k: KolBeonho, jeonggi: Bool = false, dap: ((String, Bool) -> Void)? = nil) {
        let j = MalSajeon.shared.kolJiyeok()
        let h = KolGirokHang(ttae: Date(), ireum: k.ireum, jeonhwa: k.jeonhwa, sido: j?.sido ?? "", sigungu: j?.sigungu.first ?? "", jeonggi: jeonggi)
        girok.append(h)
        mureumId = nil
        daeum = nil
        jeojang()
        Girok.shared.namgi("kol_georeum", ["ireum": k.ireum, "jeonggi": jeonggi])
        Task {
            let hal = await self.halMal(jeonggi: jeonggi)
            await MainActor.run {
                let meori = "\(k.ireum)에 겁니다. "
                let kkori = " 통화 확인이 뜨면 통화를 두 번 두드려 주십시오. 통화 중에도 알림 센터에서 이 말을 다시 보실 수 있습니다."
                let t = meori + (hal.isEmpty ? "" : "연결되면 이렇게 말씀하시면 됩니다. " + hal) + kkori
                self.halMalAllim(hal)
                guard let u = URL(string: "tel:" + k.jeonhwa) else { return }
                if let dap = dap { dap(t, false) } else { SoriEngine.shared.mal(t) }
                SoriEngine.shared.kkeutnamyeon { UIApplication.shared.open(u) }
            }
        }
    }

    /// 상담원께 말할 것 — 출발 주소와 입구, 도착지, 도착 희망 시각, 보조견
    private func halMal(jeonggi: Bool) async -> String {
        let jg = JeonggiHochul.shared
        var chulbal = ""
        var dochak = ""
        var sigak = ""
        var bj = bojogyeon
        if jeonggi {
            let s = jg.oneul()
            chulbal = s.chulbalJuso.isEmpty ? s.chulbalIreum : s.chulbalJuso + (s.chulbalIreum.isEmpty ? "" : ", \(s.chulbalIreum)")
            dochak = s.dochakIreum + (s.dochakJuso.isEmpty || s.dochakJuso == s.dochakIreum ? "" : ", \(s.dochakJuso)")
            sigak = JeonggiHochul.sigakMal(s.dochakSi, s.dochakBun)
            bj = s.bojogyeon
        }
        if chulbal.isEmpty, let w = WichiEngine.shared.jigeum {
            chulbal = await Chatgi.juso(w.lat, w.lon) ?? ""
        }
        if dochak.isEmpty, let y = YeojeongEngine.shared.jigeum { dochak = y.mokjeok.ireum }
        var t: [String] = []
        if !chulbal.isEmpty { t.append("출발은 \(chulbal)입니다.") }
        if !dochak.isEmpty { t.append("도착은 \(dochak)" + (sigak.isEmpty ? "입니다." : ", \(sigak)까지 가야 합니다.")) }
        t.append(bj ? "보조견과 함께 탑니다." : "보조견은 없습니다.")
        return t.joined(separator: " ")
    }

    /// 통화 중에도 볼 수 있게 할 말을 알림으로 남김
    private func halMalAllim(_ hal: String) {
        guard !hal.isEmpty else { return }
        let c = UNMutableNotificationContent()
        c.title = "상담원께 말씀하실 것"
        c.body = hal
        c.threadIdentifier = "kol-halmal"
        let r = UNNotificationRequest(identifier: "kol-halmal", content: c, trigger: UNTimeIntervalNotificationTrigger(timeInterval: 3, repeats: false))
        UNUserNotificationCenter.current().add(r, withCompletionHandler: nil)
    }

    // MARK: 통화가 이어지고 끝난 것(폰이 알려 줄 때)

    func callObserver(_ callObserver: CXCallObserver, callChanged call: CXCall) {
        guard call.isOutgoing, let h = girok.last, h.gyeolgwa == nil, Date().timeIntervalSince(h.ttae) < 600 else { return }
        if call.hasConnected { gochigi(h.id) { $0.yeongyeol = true } }
        if call.hasEnded {
            gochigi(h.id) { $0.kkeunnam = Date() }
            if UIApplication.shared.applicationState == .active { DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { self.salpigi() } }
        }
    }

    // MARK: 끊은 뒤 한마디 여쭙기

    /// 앱으로 돌아오시면 — 걸고 2시간 안, 아직 결과를 모르는 기록이 있으면 한 번만 여쭘
    func salpigi() {
        guard UIApplication.shared.applicationState == .active, mureumId == nil else { return }
        guard gwanchal.calls.isEmpty else { return }   // 아직 통화 중
        guard let h = girok.last, h.gyeolgwa == nil, !h.yeojjum, h.tan == nil else { return }
        let jinan = Date().timeIntervalSince(h.ttae)
        guard jinan < 7200 else { return }
        // 걸지 않고 바로 돌아오신 경우는 여쭙지 않음(이어졌거나, 끝난 것을 알았거나, 1분이 지났을 때만)
        guard h.yeongyeol == true || h.kkeunnam != nil || jinan > 60 else { return }
        gochigi(h.id) { $0.yeojjum = true }
        mureumId = h.id
        mureumTtae = Date()
        hwamyeonYeolgi()
        SoriEngine.shared.mal("\(h.ireum), 배차되었습니까? 되었다, 기다리라고 했다, 안 된다고 했다 가운데 말씀해 주십시오. 화면의 단추를 누르셔도 됩니다.")
        SoriEngine.shared.kkeutnamyeon { if self.mureumId != nil { MalHagi.shared.dudeurim() } }
    }

    /// 단추나 말로 받은 대답
    func dapBatgi(_ g: String) {
        guard let id = mureumId ?? girok.last?.id else { return }
        mureumId = nil
        gochigi(id) { $0.gyeolgwa = g; $0.gyeolgwaTtae = Date() }
        Girok.shared.namgi("kol_gyeolgwa", ["g": g])
        switch g {
        case "baecha":
            SoriEngine.shared.mal("배차되었다고 남겼습니다. 차에 타시면 저절로 알아채 기다리신 시간을 남겨 드립니다.")
        case "gidarim":
            SoriEngine.shared.mal("기다리라고 했다고 남겼습니다. 차에 타시면 저절로 알아채 남겨 드립니다.")
        default:
            let jigeum = girok.first(where: { $0.id == id })
            let l = sudanDeul().filter { $0.jeonhwa != jigeum?.jeonhwa }
            let n = l.first(where: { $0.ireum.contains("나비") || $0.ireum.contains("바우처") }) ?? l.first
            if let n = n {
                daeum = n
                daeumTtae = Date()
                SoriEngine.shared.mal("\(jigeum?.ireum ?? "그 콜")\(MalHagi.i(jigeum?.ireum ?? "콜")) 안 된다고 했습니다. \(n.ireum)에 걸까요? 걸어 줘, 또는 아니라고 말씀해 주십시오.")
                SoriEngine.shared.kkeutnamyeon { if self.daeum != nil { MalHagi.shared.dudeurim() } }
            } else {
                SoriEngine.shared.mal("안 된다고 했다고 남겼습니다. 이 지역에는 다른 수단 번호가 없습니다. 길 찾기에서 대중교통이나 걷기 안내를 받으실 수 있습니다.")
            }
        }
    }

    func daeumGeolgi() {
        guard let n = daeum else { return }
        daeum = nil
        geolgi(n)
    }

    func daeumGeuman() {
        daeum = nil
        SoriEngine.shared.mal("알겠습니다.")
    }

    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 말로 하기가 새로 여쭙거나 묻던 말을 거둘 때 — 「다음 수단에 걸까요」「복지카드 메일」을 말 대답으로는 받지 않음
    ///   (화면의 다음 수단 단추는 그대로 둠)
    func mureumBiugi() {
        daeumTtae = .distantPast
        meilMureumTtae = .distantPast
    }

    // MARK: 차에 탄 것을 저절로

    private func chaTatda() {
        guard let h = girok.last, h.tan == nil, h.gyeolgwa != "andoem", h.pulrim == nil else { return }
        let jinan = Date().timeIntervalSince(h.ttae)
        guard jinan > 60, jinan < 3 * 3600 else { return }
        gochigi(h.id) { $0.tan = Date() }
        if mureumId == h.id { mureumId = nil; gochigi(h.id) { if $0.gyeolgwa == nil { $0.gyeolgwa = "baecha"; $0.gyeolgwaTtae = Date() } } }
        let bun = max(1, Int((jinan / 60).rounded()))
        SoriEngine.shared.mal("차에 타셨습니다. \(JeonggiHochul.sigakMal(Calendar.current.component(.hour, from: h.ttae), Calendar.current.component(.minute, from: h.ttae)))에 부르셔서 \(bun)분 기다리셨습니다.", .jeongbo)
        Girok.shared.namgi("kol_tan", ["bun": bun])
    }

    /// 2.60.0 탄 뒤 15초 넘게 걸으심 — 내리신 것으로 남김(차 못 박기를 풂)
    private func chaNaerim() {
        guard let h = girok.last, h.tan != nil, h.naerim == nil else { return }
        gochigi(h.id) { $0.naerim = Date() }
        Girok.shared.namgi("kol_naerim", [:])
    }

    /// 2.60.0 (261009, 이사장님 승인 — 남산 가실 때 복지콜을 지하철로 안 일) 콜을 불러 배차된 뒤 3시간 안이고
    ///   아직 걸어서 내리지 않으셨으면 — 탈것을 차로 못 박음(땅속으로 내려간 때만 빼고)
    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 통화가 이어진 것만으로는 못 박지 않음(안드로이드와 같음),
    ///   3시간은 부른 때가 아니라 탄 때(없으면 배차를 남긴 때)부터 셈, 푼 기록(kolPulgi)이 있으면 못 박지 않음
    var chaGojeong: Bool {
        guard let h = girok.last, h.gyeolgwa != "andoem", h.naerim == nil, h.pulrim == nil else { return false }
        guard Date().timeIntervalSince(h.tan ?? h.gyeolgwaTtae ?? h.ttae) < 3 * 3600 else { return false }
        return h.gyeolgwa == "baecha" || h.gyeolgwa == "gidarim" || h.tan != nil
    }

    /// 2.60.0 가장 최근 콜(3시간 안, 안 된다고 하지 않은 것) — "복지콜 기다리는 중"처럼 말씀하시면 다시 걸지 않고 형편을 알려 드림
    var choegeun: KolGirokHang? {
        guard let h = girok.last, h.gyeolgwa != "andoem", h.naerim == nil, h.pulrim == nil, Date().timeIntervalSince(h.ttae) < 3 * 3600 else { return nil }
        return h
    }

    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 콜 차 못 박기를 풂 — 내렸어, 여정 끝, 하던 일 멈춤, 땅속에서 탈것이 움직임, 「콜 취소」, 차 아닌 탈것으로 바로잡음
    ///   iyu: naerim, naerim_jadong, kkeut, meomchum, jiha_talgeot, malro, barojapgi
    func kolPulgi(_ iyu: String) {
        guard let h = girok.last, h.naerim == nil, h.pulrim == nil,
              Date().timeIntervalSince(h.tan ?? h.gyeolgwaTtae ?? h.ttae) < 3 * 3600 else { return }
        gochigi(h.id) { $0.pulrim = Date() }
        Girok.shared.namgi("kol_pulgi", ["iyu": iyu])
    }

    // MARK: 나의 이용 성적표

    func seongjeokpyo(_ nal: Date = Date()) -> String {
        let cal = Calendar.current
        let l = girok.filter { cal.isDate($0.ttae, equalTo: nal, toGranularity: .month) }
        let dal = cal.component(.month, from: nal)
        guard !l.isEmpty else { return "\(dal)월에는 아직 부르신 기록이 없습니다." }
        let tan = l.filter { $0.tan != nil || $0.gyeolgwa == "baecha" }
        var t = "\(dal)월에는 콜을 \(l.count)번 부르셨고 \(tan.count)번 타셨습니다."
        let gidarim = l.compactMap { h -> Double? in h.tan.map { $0.timeIntervalSince(h.ttae) / 60 } }
        if !gidarim.isEmpty { t += " 평균 \(Int((gidarim.reduce(0, +) / Double(gidarim.count)).rounded()))분 기다리셨습니다." }
        let andoem = l.filter { $0.gyeolgwa == "andoem" }
        if !andoem.isEmpty {
            var si: [Int: Int] = [:]
            for h in andoem { si[cal.component(.hour, from: h.ttae), default: 0] += 1 }
            if let m = si.max(by: { $0.value < $1.value }) {
                t += " " + (m.key < 12 ? "아침 " : (m.key < 18 ? "낮 " : "저녁 ")) + "\(m.key > 12 ? m.key - 12 : m.key)시대가 가장 안 잡혔습니다."
            }
        }
        return t
    }

    // MARK: 화면

    func hwamyeonYeolgi() {
        TabGil.shared.tab = 0
        GilGil.shared.cheotHwamyeon()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { GilGil.shared.path.append(GilHwamyeon.chaBureugi) }
    }

    /// 복지카드를 그 지역 메일로 보낼지 여쭘
    func meilMutgi(_ a: KolAnnae) {
        guard !a.meil.isEmpty, Seoryuham.jongryu.contains(where: { Seoryuham.itna($0.0) }) else { return }
        meilMureumTtae = Date()
        SoriEngine.shared.mal("내 서류 보관함의 복지카드를 지금 메일로 보낼까요? 보내 줘, 또는 아니라고 말씀해 주십시오.")
        SoriEngine.shared.kkeutnamyeon { MalHagi.shared.dudeurim() }
    }

    // MARK: 말로 하기

    /// 말로 하기에서 차 부르기·정기 호출·성적표에 해당하면 처리하고 true
    func malCheori(_ alts: [String], _ z: String, _ dap: @escaping (String, Bool) -> Void) -> Bool {
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 네·아니오는 말로 하기와 같은 잣대로 말 전체만(「걸어가자」「응급실」을 네로 듣지 않게).
        //   차 부르기에서만 쓰는 대답 「걸어」「보내 줘」는 말 전체가 그것일 때만
        let hd = MalHagi.hwaginDap1(z)
        let ye = hd == .ye || ["걸어", "걸어요", "보내", "보내요", "보내줘", "보내줘요", "보내주세요"].contains(z)
        let ani = hd == .ani || z.contains("됐어그만")
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 말로 하기가 그 뒤에 다른 것을 여쭈었으면 받지 않음(마지막에 여쭌 물음에만 대답)
        let majimakMal = MalHagi.shared.majimakMureumTtae
        // 1. 배차되었습니까 — 여쭌 지 10분 안의 말만(그 뒤 다른 말을 대답으로 잘못 듣지 않게. 화면 단추는 그대로)
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 다른 차 부르기 물음과 같게 마지막에 여쭌 물음일 때만, 12자 이하의 짧은 말만
        //   (10분 동안 「못」이 든 아무 말이나 안 됨으로 받던 일)
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 「배차됐어」「배차 됐어」「배차되었어」는 「됐어」(대화 끝)보다 먼저 배차 대답으로 —
        //   여쭌 물음이 있거나, 2시간 안에 부르고 아직 결과를 모르는 콜이 있으면
        if MalHagi.baechaDapMal(alts) {
            let yeojjumJung = mureumId != nil && Date().timeIntervalSince(mureumTtae) < 600
            if yeojjumJung || (girok.last.map { $0.gyeolgwa == nil && $0.tan == nil && Date().timeIntervalSince($0.ttae) < 7200 } ?? false) {
                dap("", false); dapBatgi("baecha"); return true
            }
        }
        if mureumId != nil, Date().timeIntervalSince(mureumTtae) < 600, mureumTtae > majimakMal, z.count <= 12 {
            if z.contains("안돼") || z.contains("안된") || z.contains("안됐") || z.contains("안되") || z.contains("없대") || z.contains("없다") || z.contains("못") {
                dap("", false); dapBatgi("andoem"); return true
            }
            if z.contains("기다리") || z.contains("대기") {
                dap("", false); dapBatgi("gidarim"); return true
            }
            if z.contains("됐") || z.contains("되었") || z.contains("배차") || z.contains("온대") || z.contains("온다") {
                dap("", false); dapBatgi("baecha"); return true
            }
        }
        // 2. 다음 수단에 걸까요
        if daeum != nil, Date().timeIntervalSince(daeumTtae) < 600, daeumTtae > majimakMal, z.count <= 12 {
            if ani { dap("", false); daeumGeuman(); return true }
            if ye { dap("", false); daeumGeolgi(); return true }
        }
        // 3. 복지카드 메일로 보낼까요
        if Date().timeIntervalSince(meilMureumTtae) < 300, meilMureumTtae > majimakMal, z.count <= 12 {
            if ani { meilMureumTtae = .distantPast; dap("알겠습니다.", false); return true }
            if ye {
                meilMureumTtae = .distantPast
                dap("메일 쓰는 창을 엽니다. 받는 곳과 서류가 채워져 있습니다. 확인하신 뒤 보내기를 눌러 주십시오.", false)
                hwamyeonYeolgi()
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { self.meilYeolgi = true }
                return true
            }
        }
        // 4. 성적표
        if z.contains("성적표") || (z.contains("이용") && z.contains("기록") && (z.contains("콜") || z.contains("차"))) {
            dap(seongjeokpyo(), false); return true
        }
        // 5. 이 지역 이용 조건
        if (z.contains("콜") || z.contains("교통약자")) && (z.contains("조건") || z.contains("등록") || z.contains("가입")) {
            let a = KolAnnaeJaryo.shared.jigeum()
            dap(MalSajeon.shared.kolJiyeokMal() + " " + KolAnnaeJaryo.shared.mal(a), false)
            if let a = a, a.gongsik, !a.meil.isEmpty { DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { self.meilMutgi(a) } }
            return true
        }
        // 6. 정기 호출
        return JeonggiHochul.shared.malCheori(alts, z, dap)
    }
}

// MARK: - 정기 호출

struct JeonggiSeoljeong: Codable, Equatable {
    var kyeojim = false
    var yoil: [Int] = [2, 3, 4, 5, 6]     // 달력 요일 — 1 일요일, 2 월요일 … 7 토요일
    var si = 7
    var bun = 30
    var chulbalIreum = ""                 // 비우면 부를 때 지금 자리 주소
    var chulbalJuso = ""
    var dochakIreum = ""
    var dochakJuso = ""
    var dochakSi = 9
    var dochakBun = 0
    var sudan = "bokji"                   // bokji 복지콜, jangaein 장애인콜·교통약자 콜, nabi 나비콜·바우처택시
    var bojogyeon = false
}

final class JeonggiHochul: NSObject, ObservableObject, UNUserNotificationCenterDelegate {
    static let shared = JeonggiHochul()

    @Published var s: JeonggiSeoljeong { didSet { jeojang() } }
    /// 그날만 바꾸기 — "yyyyMMdd": "swim" 쉬기, "HHmm" 시각, "dochak:이름" 도착
    @Published private(set) var bakkum: [String: String] { didSet { jeojang() } }
    @Published private(set) var daeumMal = ""
    private var hyuil: Set<String> = JeonggiHochul.gibonHyuil
    private var dolgo = false

    /// 한국천문연구원 월력요항(2026-10-09 확인) — 나스 jeom/hyuil.json 이 있으면 그것을 씀
    static let gibonHyuil: Set<String> = ["20261009", "20261225",
        "20270101", "20270206", "20270207", "20270208", "20270209", "20270301", "20270501", "20270503", "20270505",
        "20270513", "20270606", "20270717", "20270719", "20270815", "20270816", "20270914", "20270915", "20270916",
        "20271003", "20271004", "20271009", "20271011", "20271225", "20271227"]

    override init() {
        let d = UserDefaults.standard
        var v0 = JeonggiSeoljeong()
        if let x = d.data(forKey: "gn.jeonggi"), let v = try? JSONDecoder().decode(JeonggiSeoljeong.self, from: x) { v0 = v }
        _s = Published(initialValue: v0)
        _bakkum = Published(initialValue: (d.dictionary(forKey: "gn.jeonggiBakkum") as? [String: String]) ?? [:])
        super.init()
    }

    func sijak() {
        guard !dolgo else { return }
        dolgo = true
        let c = UNUserNotificationCenter.current()
        if c.delegate == nil { c.delegate = self }
        Task {
            if let d = try? await Tongsin.shared.get("hyuil.json"),
               let o = try? JSONSerialization.jsonObject(with: d.data) as? [String: Any],
               let l = o["nal"] as? [String], !l.isEmpty {
                await MainActor.run { self.hyuil = Set(l).union(JeonggiHochul.gibonHyuil); self.jaeYeyak() }
            }
        }
        NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
            self?.jaeYeyak()
        }
        jaeYeyak()
    }

    private func jeojang() {
        let d = UserDefaults.standard
        if let x = try? JSONEncoder().encode(s) { d.set(x, forKey: "gn.jeonggi") }
        // 지난 날 바꾸기는 지움
        let oneul = JeonggiHochul.ymd(Date())
        let b = bakkum.filter { $0.key >= oneul }
        d.set(b, forKey: "gn.jeonggiBakkum")
    }

    static func ymd(_ t: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ko_KR")
        f.dateFormat = "yyyyMMdd"
        return f.string(from: t)
    }

    static func sigakMal(_ si: Int, _ bun: Int) -> String {
        let ap = si < 12 ? "아침 " : (si < 18 ? "낮 " : "저녁 ")
        let h = si > 12 ? si - 12 : (si == 0 ? 12 : si)
        return ap + "\(h)시" + (bun == 0 ? "" : (bun == 30 ? " 반" : " \(bun)분"))
    }

    static func nalMal(_ t: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ko_KR")
        f.dateFormat = "M월 d일 EEEE"
        return f.string(from: t)
    }

    static let yoilIreum = ["", "일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일"]

    var sudanIreum: String {
        switch s.sudan {
        case "jangaein": return "교통약자 콜"
        case "nabi": return "나비콜"
        default: return "복지콜"
        }
    }

    /// 오늘 쓸 설정(그날만 바꾼 도착을 넣어서)
    func oneul() -> JeonggiSeoljeong {
        var x = s
        if let b = bakkum[JeonggiHochul.ymd(Date())], b.hasPrefix("dochak:") {
            let nm = String(b.dropFirst(7))
            x.dochakIreum = nm
            x.dochakJuso = Jeulgyeo.shared.mokrok.first(where: { $0.ireum == nm })?.juso ?? nm
        }
        return x
    }

    /// 앞으로 14일의 알림을 다시 걸기(공휴일·쉬는 날 건너뜀)
    func jaeYeyak() {
        let c = UNUserNotificationCenter.current()
        c.getPendingNotificationRequests { reqs in
            let ids = reqs.map { $0.identifier }.filter { $0.hasPrefix("jeonggi-") }
            c.removePendingNotificationRequests(withIdentifiers: ids)
            DispatchQueue.main.async { self.yeyakGeolgi() }
        }
    }

    private func yeyakGeolgi() {
        guard s.kyeojim else { daeumMal = "정기 호출이 꺼져 있습니다."; return }
        let cal = Calendar.current
        let now = Date()
        var cheot: Date?
        for i in 0..<14 {
            guard let nal = cal.date(byAdding: .day, value: i, to: cal.startOfDay(for: now)) else { continue }
            let k = JeonggiHochul.ymd(nal)
            guard s.yoil.contains(cal.component(.weekday, from: nal)), !hyuil.contains(k), bakkum[k] != "swim" else { continue }
            var si = s.si, bun = s.bun
            if let b = bakkum[k], b.count == 4, let v = Int(b) { si = v / 100; bun = v % 100 }
            guard let t = cal.date(bySettingHour: si, minute: bun, second: 0, of: nal), t > now else { continue }
            if cheot == nil { cheot = t }
            let ct = UNMutableNotificationContent()
            ct.title = "\(sudanIreum) 부를 시간입니다"
            var dochak = s.dochakIreum
            if let b = bakkum[k], b.hasPrefix("dochak:") { dochak = String(b.dropFirst(7)) }
            ct.body = "두드리시면 바로 전화가 걸립니다." + (dochak.isEmpty ? "" : " \(dochak)까지 \(JeonggiHochul.sigakMal(s.dochakSi, s.dochakBun)).")
            ct.sound = .default
            ct.interruptionLevel = .timeSensitive
            ct.userInfo = ["jeonggi": k]
            let dc = cal.dateComponents([.year, .month, .day, .hour, .minute], from: t)
            UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "jeonggi-" + k, content: ct,
                                                                         trigger: UNCalendarNotificationTrigger(dateMatching: dc, repeats: false)), withCompletionHandler: nil)
        }
        if let t = cheot {
            let c = Calendar.current.dateComponents([.hour, .minute], from: t)
            daeumMal = "다음 알림은 \(JeonggiHochul.nalMal(t)) \(JeonggiHochul.sigakMal(c.hour ?? 0, c.minute ?? 0))입니다."
        } else {
            daeumMal = "앞으로 14일 안에는 알림이 없습니다."
        }
    }

    /// 정한 내용을 들려 드리는 말
    func hwaginMal() -> String {
        let yo: String
        if s.yoil.sorted() == [2, 3, 4, 5, 6] { yo = "월요일부터 금요일까지" }
        else if s.yoil.count == 7 { yo = "날마다" }
        else { yo = s.yoil.sorted().map { JeonggiHochul.yoilIreum[$0] }.joined(separator: ", ") + "마다" }
        let chul = s.chulbalIreum.isEmpty ? "부를 때 계신 곳" : s.chulbalIreum
        let do_ = s.dochakIreum.isEmpty ? "아직 정하지 않음" : s.dochakIreum
        return "\(yo) \(JeonggiHochul.sigakMal(s.si, s.bun))에 알려 드립니다. 출발은 \(chul), 도착은 \(do_), \(JeonggiHochul.sigakMal(s.dochakSi, s.dochakBun))까지입니다. 수단은 \(sudanIreum)입니다. 공휴일은 건너뜁니다."
    }

    func jeonghagi(_ x: JeonggiSeoljeong) {
        s = x
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in
            DispatchQueue.main.async { self.jaeYeyak() }
        }
        Girok.shared.namgi("jeonggi_jeonghagi", ["kyeojim": x.kyeojim, "si": x.si, "bun": x.bun])
    }

    func geunalBakkugi(_ nal: Date, _ v: String?) {
        let k = JeonggiHochul.ymd(nal)
        if let v = v { bakkum[k] = v } else { bakkum.removeValue(forKey: k) }
        jaeYeyak()
    }

    // MARK: 알림을 두드렸을 때

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        // 정기 호출만 앱이 켜져 있어도 띄움 — 다른 알림은 지금까지처럼(띄우지 않음)
        let id = notification.request.identifier
        if id.hasPrefix("jeonggi-") {
            completionHandler([.banner, .list, .sound])
            SoriEngine.shared.mal("\(sudanIreum) 부를 시간입니다. 알림을 두드리시거나 차 부르기에서 정기 호출로 거시면 됩니다.", .annae)
        } else {
            completionHandler([])
        }
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        if response.notification.request.identifier.hasPrefix("jeonggi-") {
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { self.geolgi() }
        }
        completionHandler()
    }

    /// 정기 호출로 걸기
    func geolgi() {
        let c = ChaBureugi.shared
        guard let k = c.sudan(s.sudan) else {
            SoriEngine.shared.mal(MalSajeon.shared.kolJiyeokMal())
            return
        }
        c.hwamyeonYeolgi()
        c.geolgi(k, jeonggi: true)
    }

    // MARK: 말로 정하기·바꾸기

    /// "7시 30분", "8시 반", "오후 2시" 처럼 말에서 시각을 모두 찾음
    static func sigakDeul(_ z: String) -> [(Int, Int)] {
        var l: [(Int, Int)] = []
        let ch = Array(z)
        var i = 0
        while i < ch.count {
            if ch[i].isNumber {
                var j = i
                var su = ""
                while j < ch.count, ch[j].isNumber { su.append(ch[j]); j += 1 }
                if j < ch.count, ch[j] == "시", let h0 = Int(su), h0 <= 24 {
                    var h = h0
                    var m = 0
                    var k = j + 1
                    if k < ch.count, ch[k] == "반" { m = 30; k += 1 }
                    else {
                        var su2 = ""
                        var q = k
                        while q < ch.count, ch[q].isNumber { su2.append(ch[q]); q += 1 }
                        if q < ch.count, ch[q] == "분", let mm = Int(su2), mm < 60 { m = mm; k = q + 1 }
                    }
                    let ap = String(ch[max(0, i - 3)..<i])
                    if (ap.contains("오후") || ap.contains("저녁") || ap.contains("밤")) && h < 12 { h += 12 }
                    l.append((h, m))
                    i = k
                    continue
                }
                i = j
                continue
            }
            i += 1
        }
        return l
    }

    func malCheori(_ alts: [String], _ z: String, _ dap: @escaping (String, Bool) -> Void) -> Bool {
        let cal = Calendar.current
        let jeonggiMal = z.contains("정기호출") || z.contains("정기콜")
        let nalMal = z.hasPrefix("내일") || z.hasPrefix("오늘") || z.hasPrefix("모레") || JeonggiHochul.yoilIreum.dropFirst().contains(where: { z.hasPrefix($0) || z.hasPrefix(String($0.prefix(1)) + "요일") })
        // 그날 고르기
        func geunal() -> Date? {
            let oneul = cal.startOfDay(for: Date())
            if z.hasPrefix("오늘") { return oneul }
            if z.hasPrefix("내일") { return cal.date(byAdding: .day, value: 1, to: oneul) }
            if z.hasPrefix("모레") { return cal.date(byAdding: .day, value: 2, to: oneul) }
            for (i, y) in JeonggiHochul.yoilIreum.enumerated() where i > 0 && z.hasPrefix(y) {
                for d in 0..<8 {
                    if let n = cal.date(byAdding: .day, value: d, to: oneul), cal.component(.weekday, from: n) == i { return n }
                }
            }
            return nil
        }
        // 1. 그날만 쉬기·바꾸기 — 정기 호출을 켜 두셨을 때만
        if s.kyeojim && nalMal, let nal = geunal() {
            let nm = JeonggiHochul.nalMal(nal)
            if z.contains("쉬어") || z.contains("쉴게") || z.contains("쉬자") || z.contains("안불러") || z.contains("부르지마") {
                geunalBakkugi(nal, "swim")
                dap("\(nm)은 정기 호출을 쉽니다.", false)
                return true
            }
            if z.contains("다시불러") || z.contains("원래대로") || z.contains("되돌려") {
                geunalBakkugi(nal, nil)
                dap("\(nm)은 원래대로 \(JeonggiHochul.sigakMal(s.si, s.bun))에 알려 드립니다.", false)
                return true
            }
            if z.contains("바꿔") || z.contains("로해") || z.contains("시로") || jeonggiMal {
                if let t = JeonggiHochul.sigakDeul(z).first, z.contains("시") {
                    geunalBakkugi(nal, String(format: "%02d%02d", t.0, t.1))
                    dap("\(nm)은 \(JeonggiHochul.sigakMal(t.0, t.1))에 알려 드립니다.", false)
                    return true
                }
                if let j = Jeulgyeo.shared.mokrok.first(where: { z.contains(MalSajeon.ttuk($0.ireum)) }) {
                    geunalBakkugi(nal, "dochak:" + j.ireum)
                    dap("\(nm)은 도착을 \(j.ireum)\(MalHagi.ro(j.ireum)) 바꿉니다.", false)
                    return true
                }
                if z.contains("바꿔") && !z.contains("시") {
                    dap("그곳을 즐겨찾기에서 찾지 못했습니다. 먼저 즐겨찾기에 담아 주시면 말로 바꾸실 수 있습니다.", false)
                    return true
                }
            }
        }
        // 2. 켜기·끄기
        if jeonggiMal && (z.contains("꺼") || z.contains("끄") || z.contains("멈춰")) {
            var x = s; x.kyeojim = false; jeonghagi(x)
            dap("정기 호출을 껐습니다. 다시 켜시려면 정기 호출 켜 줘라고 말씀해 주십시오.", false)
            return true
        }
        if jeonggiMal && z.contains("켜") {
            var x = s; x.kyeojim = true; jeonghagi(x)
            dap("정기 호출을 켰습니다. " + hwaginMal(), false)
            return true
        }
        if jeonggiMal && (z.contains("알려") || z.contains("뭐야") || z.contains("언제") || z.count <= 6) {
            dap(s.kyeojim ? hwaginMal() + " " + daeumMal : "정기 호출이 꺼져 있습니다. 차 부르기 화면의 정기 호출에서 정하시거나, 평일마다 아침 7시 30분에 복지콜 불러 줘처럼 말씀해 주십시오.", false)
            return true
        }
        // 3. 말로 정하기 — "평일마다 아침 7시 30분에 복지콜 불러 줘, 사무실까지 9시"
        let maeil = z.contains("평일마다") || z.contains("매일") || z.contains("날마다") || z.contains("주중")
        if (maeil || jeonggiMal) && (z.contains("콜") || (jeonggiMal && z.contains("불러"))) {
            let t = JeonggiHochul.sigakDeul(z)
            guard let si = t.first else {
                dap("몇 시에 알려 드릴까요? 평일마다 아침 7시 30분에 복지콜 불러 줘처럼 시각을 함께 말씀해 주십시오.", false)
                return true
            }
            var x = s
            x.kyeojim = true
            x.yoil = (z.contains("매일") || z.contains("날마다")) ? [1, 2, 3, 4, 5, 6, 7] : [2, 3, 4, 5, 6]
            x.si = si.0; x.bun = si.1
            if t.count > 1 { x.dochakSi = t[1].0; x.dochakBun = t[1].1 }
            if z.contains("나비") || z.contains("바우처") { x.sudan = "nabi" }
            else if z.contains("장애인콜") || z.contains("교통약자") || z.contains("이동지원") { x.sudan = "jangaein" }
            else { x.sudan = "bokji" }
            if let j = Jeulgyeo.shared.mokrok.first(where: { z.contains(MalSajeon.ttuk($0.ireum)) && !$0.ireum.contains("집") }) {
                x.dochakIreum = j.ireum; x.dochakJuso = j.juso
            }
            if x.chulbalIreum.isEmpty, let jip = Jeulgyeo.shared.mokrok.first(where: { $0.ireum.contains("집") }) {
                x.chulbalIreum = jip.ireum; x.chulbalJuso = jip.juso
            }
            jeonghagi(x)
            var m = hwaginMal()
            if x.dochakIreum.isEmpty { m += " 도착할 곳은 차 부르기 화면의 정기 호출에서 정해 주십시오." }
            dap(m, false)
            return true
        }
        return false
    }
}

// MARK: - 차 부르기 화면

struct ChaBureugiView: View {
    @ObservedObject private var c = ChaBureugi.shared
    @ObservedObject private var j = JeonggiHochul.shared
    @State private var gaeng = 0
    @State private var meil = false
    @AccessibilityFocusState private var meoriChojeom: Bool

    private var sudan: [KolBeonho] { c.sudanDeul() }
    private var annae: KolAnnae? { KolAnnaeJaryo.shared.jigeum() }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if c.mureumId != nil {
                    Text("배차되었습니까?").font(.title3.weight(.bold)).accessibilityAddTraits(.isHeader)
                        .accessibilityFocused($meoriChojeom)
                    Button("되었다 — 배차됨") { c.dapBatgi("baecha") }.buttonStyle(KeunDanchu())
                    Button("기다리라고 했다") { c.dapBatgi("gidarim") }.buttonStyle(KeunDanchu())
                    Button("안 된다고 했다") { c.dapBatgi("andoem") }.buttonStyle(KeunDanchu())
                } else if let d = c.daeum {
                    Button("\(d.ireum)에 걸기 — 앞의 콜이 안 된다고 했을 때") { c.daeumGeolgi() }
                        .buttonStyle(KeunDanchu())
                        .accessibilityFocused($meoriChojeom)
                    Button("다른 수단에 걸지 않기") { c.daeumGeuman() }.buttonStyle(KeunDanchu())
                } else {
                    Text(MalSajeon.shared.kolJiyeokMal()).font(.body).accessibilityFocused($meoriChojeom)
                }
                if let k = sudan.first {
                    Button("\(k.ireum) 부르기") { c.geolgi(k) }.buttonStyle(KeunDanchu())
                }
                if sudan.count > 1 {
                    DisclosureGroup("다른 수단 — " + sudan.dropFirst().prefix(3).map { $0.ireum }.joined(separator: ", ")) {
                        VStack(alignment: .leading, spacing: 10) {
                            ForEach(Array(sudan.dropFirst().enumerated()), id: \.offset) { _, k in
                                Button("\(k.ireum) 부르기" + (k.bigo.isEmpty ? "" : " — \(k.bigo)")) { c.geolgi(k) }
                                    .buttonStyle(KeunDanchu())
                            }
                        }
                    }
                    .font(.title3)
                }
                NavigationLink { JeonggiHochulView() } label: {
                    Text(j.s.kyeojim ? "정기 호출 — \(JeonggiHochul.sigakMal(j.s.si, j.s.bun))" + (j.s.dochakIreum.isEmpty ? "" : ", \(j.s.dochakIreum)") : "정기 호출 — 출퇴근처럼 정한 시각에 부르기")
                }
                .buttonStyle(KeunDanchu())
                DisclosureGroup("그 밖에 펼치기 — 이용 조건, 등록, 성적표") {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("이 지역 이용 조건").font(.title3.weight(.bold)).accessibilityAddTraits(.isHeader)
                        Text(KolAnnaeJaryo.shared.mal(annae)).font(.body)
                        if let a = annae, a.gongsik {
                            if !a.seoryu.isEmpty && !a.seoryu.hasPrefix("확인 중") { Text("등록 서류: " + a.seoryu).font(.body) }
                            if !a.paekseu.isEmpty { Text("서류 보내는 팩스: " + a.paekseu).font(.body) }
                            if !a.meil.isEmpty {
                                Text("서류 보내는 메일: " + a.meil).font(.body)
                                Button("복지카드를 이 메일로 보내기 — 내 서류 보관함") { meil = true }.buttonStyle(KeunDanchu())
                            }
                            if !a.unhaeng.isEmpty && !a.unhaeng.hasPrefix("확인 중") { Text("운행: " + a.unhaeng).font(.body) }
                        }
                        Text("나의 이용 성적표").font(.title3.weight(.bold)).accessibilityAddTraits(.isHeader)
                        Text(c.seongjeokpyo()).font(.body)
                        Toggle(isOn: Binding(get: { c.dongui == 1 }, set: { c.dongui = $0 ? 1 : 2 })) {
                            Text("이름 없이 협회로 보내기 — 지역마다 얼마나 잡히는지 알리는 근거가 됩니다").font(.body)
                        }
                        .frame(minHeight: 60)
                        Toggle(isOn: $c.bojogyeon) { Text("보조견과 함께 탑니다 — 상담원께 말할 것에 넣음").font(.body) }
                            .frame(minHeight: 60)
                        NavigationLink { SeoryuhamView() } label: { Text("내 서류 보관함 — 복지카드 담아 두기") }
                            .buttonStyle(KeunDanchu())
                    }
                }
                .font(.title3)
            }
            .padding()
            .id(gaeng)
        }
        .sokHwamyeon("차 부르기")
        .onAppear {
            gaeng += 1
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { meoriChojeom = true }
        }
        .onChange(of: c.meilYeolgi) { v in if v { c.meilYeolgi = false; meil = true } }
        .sheet(isPresented: $meil) {
            SeoryuMeil(baneun: annae?.meil ?? "", jiyeok: annae?.ireum ?? "") { meil = false }
        }
    }
}

/// 복지카드와 적어 둔 것을 그 지역 메일로 — 이용자가 메일 창에서 보내기를 누름
struct SeoryuMeil: View {
    let baneun: String
    let jiyeok: String
    let kkeut: () -> Void

    private var pail: [URL] { Seoryuham.jongryu.filter { Seoryuham.itna($0.0) }.map { Seoryuham.pail($0.0) } }

    var body: some View {
        if MFMailComposeViewController.canSendMail() {
            MeilChang(baneun: baneun, jemok: "\(jiyeok) 이용 등록 서류", bonmun: bonmun, pail: pail, kkeut: kkeut)
                .ignoresSafeArea()
        } else {
            VStack(alignment: .leading, spacing: 12) {
                Text("이 폰에 메일 앱이 설정되어 있지 않아 보내기 창으로 보냅니다. 받는 곳을 고르실 때 \(baneun)으로 보내 주십시오.").font(.body)
                if pail.isEmpty {
                    Text("내 서류 보관함에 담아 둔 서류가 없습니다. 먼저 복지카드를 담아 주십시오.").font(.body)
                } else {
                    ShareLink(items: pail) { Text("서류 보내기 창 열기") }.buttonStyle(KeunDanchu())
                }
                Button("닫기") { kkeut() }.buttonStyle(KeunDanchu())
            }
            .padding()
        }
    }

    private var bonmun: String {
        var t = "\(jiyeok) 이용 등록을 신청합니다. 시각장애인입니다. 복지카드를 붙입니다."
        let j = Seoryuham.jeokeunGeul
        if !j.isEmpty { t += "\n\n" + j }
        t += "\n\n길눈 앱에서 보냅니다."
        return t
    }
}

struct MeilChang: UIViewControllerRepresentable {
    let baneun: String
    let jemok: String
    let bonmun: String
    let pail: [URL]
    let kkeut: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(kkeut) }

    func makeUIViewController(context: Context) -> MFMailComposeViewController {
        let m = MFMailComposeViewController()
        m.mailComposeDelegate = context.coordinator
        if !baneun.isEmpty { m.setToRecipients([baneun]) }
        m.setSubject(jemok)
        m.setMessageBody(bonmun, isHTML: false)
        for u in pail {
            if let d = try? Data(contentsOf: u) { m.addAttachmentData(d, mimeType: "image/jpeg", fileName: u.lastPathComponent) }
        }
        return m
    }

    func updateUIViewController(_ uiViewController: MFMailComposeViewController, context: Context) {}

    final class Coordinator: NSObject, MFMailComposeViewControllerDelegate {
        let kkeut: () -> Void
        init(_ k: @escaping () -> Void) { kkeut = k }
        func mailComposeController(_ controller: MFMailComposeViewController, didFinishWith result: MFMailComposeResult, error: Error?) {
            SoriEngine.shared.mal(result == .sent ? "메일을 보냈습니다. 센터에서 등록 결과를 알려 줄 것입니다." : "메일을 보내지 않았습니다.")
            Girok.shared.namgi("kol_seoryu_meil", ["r": result.rawValue])
            kkeut()
        }
    }
}

// MARK: - 정기 호출 화면

struct JeonggiHochulView: View {
    @ObservedObject private var j = JeonggiHochul.shared
    @ObservedObject private var jg = Jeulgyeo.shared
    @State private var x = JeonggiSeoljeong()
    @State private var buril = Date()
    @State private var dochak = Date()
    @State private var jjk = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(j.s.kyeojim ? j.daeumMal : "정기 호출이 꺼져 있습니다. 아래에서 정하고 정하기를 누르십시오.").font(.body)
                Toggle(isOn: $x.kyeojim) { Text("정기 호출 켜기").font(.title3.weight(.semibold)) }
                    .frame(minHeight: 60)
                DatePicker("부를 시각", selection: $buril, displayedComponents: .hourAndMinute)
                    .font(.title3).frame(minHeight: 60)
                Picker("도착", selection: $x.dochakIreum) {
                    Text("정하지 않음").tag("")
                    ForEach(jg.mokrok) { h in Text(h.ireum).tag(h.ireum) }
                }
                .pickerStyle(.menu).font(.title3).frame(minHeight: 60)
                if jg.mokrok.isEmpty {
                    Text("도착할 곳은 길 찾기의 즐겨찾기에 먼저 담아 두시면 여기서 고르실 수 있습니다.").font(.body)
                }
                DatePicker("도착 희망 시각", selection: $dochak, displayedComponents: .hourAndMinute)
                    .font(.title3).frame(minHeight: 60)
                Button("정하기 — 저장하고 알림 걸기") { jeonghagi() }.buttonStyle(KeunDanchu())
                DisclosureGroup("자세히 펼치기 — 요일, 출발, 수단, 보조견, 내일만 바꾸기") {
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach([2, 3, 4, 5, 6, 7, 1], id: \.self) { y in
                            Toggle(isOn: Binding(get: { x.yoil.contains(y) }, set: { v in
                                if v { if !x.yoil.contains(y) { x.yoil.append(y) } } else { x.yoil.removeAll { $0 == y } }
                            })) { Text(JeonggiHochul.yoilIreum[y]).font(.body) }
                            .frame(minHeight: 50)
                        }
                        Picker("출발", selection: $x.chulbalIreum) {
                            Text("부를 때 계신 곳").tag("")
                            ForEach(jg.mokrok) { h in Text(h.ireum).tag(h.ireum) }
                        }
                        .pickerStyle(.menu).font(.title3).frame(minHeight: 60)
                        Picker("수단", selection: $x.sudan) {
                            Text("복지콜").tag("bokji")
                            Text("교통약자 콜").tag("jangaein")
                            Text("나비콜·바우처택시").tag("nabi")
                        }
                        .pickerStyle(.menu).font(.title3).frame(minHeight: 60)
                        Toggle(isOn: $x.bojogyeon) { Text("보조견과 함께 탑니다").font(.body) }.frame(minHeight: 60)
                        Button("내일 하루 쉬기") {
                            if let n = Calendar.current.date(byAdding: .day, value: 1, to: Date()) {
                                j.geunalBakkugi(n, "swim")
                                SoriEngine.shared.mal("\(JeonggiHochul.nalMal(n))은 정기 호출을 쉽니다.")
                            }
                        }
                        .buttonStyle(KeunDanchu())
                        Button("내일 원래대로") {
                            if let n = Calendar.current.date(byAdding: .day, value: 1, to: Date()) {
                                j.geunalBakkugi(n, nil)
                                SoriEngine.shared.mal("\(JeonggiHochul.nalMal(n))은 원래대로 알려 드립니다.")
                            }
                        }
                        .buttonStyle(KeunDanchu())
                        Button("지금 정기 호출로 걸어 보기") { j.geolgi() }.buttonStyle(KeunDanchu())
                        Text("말로도 됩니다. 평일마다 아침 7시 30분에 복지콜 불러 줘, 사무실까지 9시. 내일은 쉬어. 내일은 8시로 바꿔. 금요일은 병원으로.").font(.body)
                    }
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("정기 호출")
        .onAppear {
            x = j.s
            let cal = Calendar.current
            buril = cal.date(bySettingHour: x.si, minute: x.bun, second: 0, of: Date()) ?? Date()
            dochak = cal.date(bySettingHour: x.dochakSi, minute: x.dochakBun, second: 0, of: Date()) ?? Date()
        }
    }

    private func jeonghagi() {
        let cal = Calendar.current
        var y = x
        y.si = cal.component(.hour, from: buril); y.bun = cal.component(.minute, from: buril)
        y.dochakSi = cal.component(.hour, from: dochak); y.dochakBun = cal.component(.minute, from: dochak)
        y.dochakJuso = jg.mokrok.first(where: { $0.ireum == y.dochakIreum })?.juso ?? ""
        y.chulbalJuso = jg.mokrok.first(where: { $0.ireum == y.chulbalIreum })?.juso ?? ""
        if y.yoil.isEmpty { y.yoil = [2, 3, 4, 5, 6] }
        j.jeonghagi(y)
        x = y
        SoriEngine.shared.mal(y.kyeojim ? j.hwaginMal() : "정기 호출을 껐습니다.")
    }
}
