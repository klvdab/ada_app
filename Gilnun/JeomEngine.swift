// 점지도 따라 걷기 엔진 — 앱 2.10.0 (빌드 260928-12)
// 웹 길눈 ttara.html 과 그 부품(hwaksin.js 1.7, kkeokim.js, georeum_iego.js, gyeonggo.js, geollim.js, lvdmal.js)을 옮겼습니다.
// ★이사장님 말씀 — 점지도는 직방이어야 한다. 한 걸음만 떨어져도 바로 알려야 점지도다. 따라 걸을 때 입을 다물면 안 된다.
//   ① 점지도 위를 제대로 디디면 걸음마다 맑은 확신음
//   ② 반 걸음 비켜나면 가운데 소리와 "왼쪽으로 조금 비켜나십니다" 한 번
//   ③ 한 걸음 벗어나면 곧바로 경고음과 "왼쪽으로 한 걸음 벗어났습니다. 1시 방향으로 한 걸음 옮기십시오" — 경고음 2.5초, 말 5초마다
//   ④ 돌아오시면 돌아옴 소리와 "점지도 위로 돌아오셨습니다"
//   ⑤ 정한 거리(5·10·20미터)마다 "제대로 가고 있습니다"
//   ⑥ 제자리·위성 기다림은 5초에 한 번 알림
//   꺾이는 곳은 서른 걸음 앞·열한 걸음 앞·코앞에서, 표시는 서른 걸음 앞·일곱 미터 앞·세 미터 앞에서
//   위성이 6초 넘게 끊기거나 흐리면 걸음 수 × 보폭만큼 점지도 위를 나아간 것으로 셈(점지도의 바탕은 걸음)
//   안전 경고는 끌 수 없음 — 걷기 시작 한마디, 확인 중인 길, 안내 끊김(10초)
// 한 걸음 어긋남 재기 — 위성은 몇 미터씩 흔들리므로, 폰의 움직임 감지로 발 디딤을 잡고
// 나침반으로 디딘 방향을 읽어 점지도 구간 방향과 견주어 옆으로 비켜난 거리를 걸음마다 쌓아 셉니다.
import Foundation
import CoreMotion
import Combine
import UIKit

/// 걸을 길 한 구간(2.11.0 — 여러 점지도를 이어 걸을 때 구간이 여럿)
struct JeomGugan: Hashable, Codable {
    let id: String
    let dwit: Bool
}

struct JeomMuleum {
    let mok: Jangso
    let julMal: String
    let gugan: [JeomGugan]
}

/// 문까지 이어 안내할 문(2.11.0) — jarye 0 내 문, 1 여러 번 확인된 문, 2 한 번 찍힌 문
struct JeomMun {
    let lat: Double
    let lon: Double
    let ireum: String
    let saengMal: String
    let bang: Double?
    let jarye: Int
    let d: Double
    var geul: [String] = []   // 2.15.0 내 문에 담아 둔 문 둘레 글자
}

final class JeomEngine: ObservableObject {
    static let shared = JeomEngine()

    @Published private(set) var gil: JeomGil?
    @Published private(set) var dwit = false
    @Published private(set) var dochakHam = false
    @Published private(set) var sangMal = ""
    @Published private(set) var muleum: JeomMuleum?
    @Published private(set) var bulleoneun = false
    // 2.11.0 여러 점지도 이어 걷기(이사장님 나1 — 한 구간을 마치면 저절로 다음 구간)
    @Published private(set) var ieum: [JeomGugan] = []
    @Published private(set) var ieumIdx = 0
    private var ieumMok: Jangso?
    // 2.11.0 문까지 이어 안내(이사장님 가1 — 내 문 먼저)
    @Published private(set) var munOn = false
    @Published private(set) var munSu = 0
    private var munKamera = false   // 2.15.0 문 10미터 안에서 카메라 문 찾기를 켰는지
    private var mun: JeomMun?
    private var munList: [JeomMun] = []
    private var munIdx = 0
    private var munDasi = false
    private var munT = Date.distantPast
    private var munGakkaum: Double?
    private var munBeon = 0
    // 2.11.1 지나는 곳 안내(웹 juwi.js — 걸을 때 25미터 둘레, 뒤쪽은 말하지 않음)
    private var juwiRows: [(ireum: String, jong: String, lat: Double, lon: Double, wi: Bool)] = []
    private var juwiHan = Set<String>()
    private var juwiEonje = Date.distantPast
    private var juwiJari: (Double, Double)?
    private var juwiCenter: (Double, Double)?
    private var juwiBan: Double = 0
    private var juwiBadneun = false
    private var juwiBatT = Date.distantPast
    // 2.11.0 함께 시험하기
    @Published private(set) var hamkkeBunho: String?
    private var hamkkeT = Date.distantPast

    /// 이어진 길의 가운데 구간을 걷는 중(끝이 목적지가 아님)
    var jungganGugan: Bool { !ieum.isEmpty && ieumIdx < ieum.count - 1 }

    /// 점지도를 따라 걷는 중(도착 전)
    var georeoJung: Bool { gil != nil && !dochakHam }

    // 길
    private var pts: [JeomJeom] = []
    private var nu: [Double] = []
    private struct Pyo { var p: JeomPyo; var i: Int; var said30 = false; var said = false; var near = false }
    private var pyo: [Pyo] = []
    private struct Kkeok { let i: Int; let d: Double; let lat: Double; let lon: Double }
    private var kkeoks: [Kkeok] = []
    private var kkeokHan = Set<String>()
    private var gilRaw = ""
    private var sijakT = Date()

    // 자리
    private var idx = 0
    private var firstFix = true
    private var me: (Double, Double)?
    private var acc: Double = 0
    private var spd: Double = 0
    private var lastP: (Double, Double, Date)?
    private var jariTtae = Date.distantPast        // 마지막으로 자리를 받은 때(위성이든 걸음이든)
    private var wiseongTtae = Date.distantPast     // 마지막으로 또렷한 위성을 받은 때
    private var geoMode = false                    // 걸음으로 이어 가는 중
    private var geoS: Double = 0
    private var geoSu = 0
    private var jeop30 = false, jeop20 = false
    private var jeopT = Date.distantPast
    private var cheotBang = false
    private var dolgiMok: (k: Int?, bang: Double?, t: Date)?
    private var geonneolOn = false
    private var geonneol: (i: Int, len: Double)?
    private var offSu = 0
    private var beoseoSu = 0
    private var makhimT = Date.distantPast
    private var geollimJari: (Double, Double)?
    private var geollimHan = Set<String>()
    private var geollimT = Date.distantPast
    private var malT = Date.distantPast
    private var ssak = Set<AnyCancellable>()
    private var sigye: Timer?
    private var tikT = Date()
    private var kkeunMalT = Date.distantPast
    private var muleumJakeop: DispatchWorkItem?
    private var sedae = 0                          // 2.12.0 그만두면 늦게 돌아온 일(불러오기 등)을 버리려는 세대 번호
    private var munSijakT = Date()
    private var geoAcc0: Double = 5
    private var geoS0: Double = 0
    private var dwiNeolge = false

    // 확신음(hwaksin.js 의 S)
    private struct HS {
        var gidarim = false, gidarimSu = 0
        var dolgiKkaji = Date.distantPast
        var gyeol = ""                 // "", baro, heundeul, beoseo
        var yeop: Double = 0           // 오른쪽이 +
        var segBang: Double?
        var segKijun: Double?
        var jin: Double?
        var jinSijak: Double?
        var rest: Double?
        var stepT = Date.distantPast
        var stepSu = 0
        var offSu = 0
        var offT = Date.distantPast, offMalT = Date.distantPast
        var sinho = Date()
        var wdMal = Date.distantPast
        var hwakMalT = Date.distantPast
        var gamyeon = false
        var kijun: Double?
        var malKijun: Double?
        var jumeoni = false, jumeoniMal = false
        var dallaSu = 0, majaSu = 0, heundeulSu = 0
        var dolrim: Double = 0
        var gyeolgwa: [(nal: Double, jin: Double?)] = []
        var gpsNeomSu = 0
        var pokgiMal = false
        var sijakT = Date()
    }
    private var S = HS()

    // 2.32.0 걸음 감지는 몸 센서(자봉이 점지도를 그릴 때와 같은 센서·같은 셈법). 몸 센서를 쓸 수 없는 폰만 옛 잣대로
    // 옛 잣대(웹과 같음 — 중력을 뺀 흔들림이 1.3을 넘으면 한 걸음, 0.3초 안에는 다시 세지 않음)
    private var momNaega = false        // 몸 센서를 따라 걷기가 켰는가(그만둘 때 끄려고)
    private var momSseum = false        // 걸음과 방향을 몸 센서로 받는 중
    private var dolgiDg0: Double?       // 도시라고 말씀드린 때 몸이 돈 각도의 누계
    private var dolgiWant: Double?      // 돌아야 할 쪽과 크기(오른쪽 +)
    private var bandaeMal = false
    private let umjik = CMMotionManager()
    private var moG: Double = 9.8
    private var moWi = false
    private var moT = Date.distantPast
    // 2.35.0 워치 걸음 — 폰이 4초 넘게 걸음을 못 셀 때만 워치가 센 걸음으로 이어 감(두 번 세지 않게)
    private var ponGeoreumT = Date.distantPast
    private var watchN0: Int?
    // 2.36.0 손목 가리키기 — 워치에 마지막으로 보낸 가야 할 쪽
    private var garikiBonaen: Double?
    private var garikiT = Date.distantPast

    private let RAD = Double.pi / 180
    private var bocok: Double { let b = Seoljeong.shared.bopok; return (b > 0.3 && b < 1.2) ? b : 0.7 }

    init() {
        WichiEngine.shared.saeWichi
            .receive(on: DispatchQueue.main)
            .sink { [weak self] w in self?.wichiBatda(w) }
            .store(in: &ssak)
    }

    // MARK: 걸어가기 — 점지도가 있으면 한 번 여쭘(이사장님 결정 나2, 점지도를 먼저 권함)

    /// 걸어가실 곳에 맞는 점지도가 있으면 여쭙고(mutgi 에 여쭐 말), 없으면 곧장 위성으로 걷는 안내(mutgi 에 nil)
    func georeoGagiBoda(_ j: Jangso, _ mutgi: @escaping (String?) -> Void) {
        // 2.12.6 새 목적지면 걷던 점지도와 지하철 안내를 먼저 끔 — 옛 길이 계속 말하고 새 목적지를 덮어쓰던 것(이사장님 승인 1)
        yeojeongKkeut()
        JihacheolEngine.shared.meomchugi()
        let sd = sedae
        guard let w = WichiEngine.shared.jigeum else {
            mutgi(nil)
            AnnaeEngine.shared.georeoGagi(j)
            return
        }
        Task {
            let m = await Jeomjido.matneunGil(w, j)
            await MainActor.run {
                guard sd == self.sedae else { return }
                guard let mm = m else {
                    mutgi(nil)
                    AnnaeEngine.shared.georeoGagi(j)
                    return
                }
                YeojeongEngine.shared.jeonghagi(Mokjeok(ireum: j.ireum, lat: j.lat, lon: j.lon, juso: j.juso))
                self.muleum = JeomMuleum(mok: j, julMal: mm.0, gugan: mm.1)
                let nm = mm.0
                mutgi("이 길에는 \(nm)가 있습니다. 점지도로 걸을까요, 위성으로 걸을까요? 점지도가 더 정확합니다. 네 하시면 점지도로 걷습니다. 20초 안에 고르지 않으시면 점지도로 걷습니다.")
                let jk = DispatchWorkItem { [weak self] in
                    guard let self = self, self.muleum != nil else { return }
                    SoriEngine.shared.mal("고르지 않으셔서 점지도로 걷습니다.")
                    self.muleumDap(jeom: true)
                }
                self.muleumJakeop = jk
                DispatchQueue.main.asyncAfter(deadline: .now() + 20, execute: jk)
            }
        }
    }

    /// 여쭌 말의 대답 — 참이면 점지도, 거짓이면 위성
    func muleumDap(jeom: Bool) {
        guard let m = muleum else { return }
        muleumJiugi()
        if jeom {
            ieumGeotgi(m.gugan, mok: m.mok)
        } else {
            AnnaeEngine.shared.georeoGagi(m.mok)
        }
    }

    func muleumJiugi() {
        sedae += 1
        muleumJakeop?.cancel()
        muleumJakeop = nil
        muleum = nil
    }

    // MARK: 시작과 끝

    /// 2.11.0 여러 점지도를 차례로 이어 걷기 — 구간이 하나면 그냥 따라 걷기
    func ieumGeotgi(_ g: [JeomGugan], mok: Jangso?) {
        guard let cheot = g.first else { return }
        ieum = g.count > 1 ? g : []
        ieumIdx = 0
        ieumMok = mok
        bulleoGeotgi(cheot.id, dwit: cheot.dwit, mok: mok, ieumYuji: true)
    }

    /// 점지도를 불러 따라 걷기 시작
    func bulleoGeotgi(_ id: String, dwit dw: Bool, mok: Jangso? = nil, ieumYuji: Bool = false) {
        muleumJiugi()
        let sd = sedae
        bulleoneun = true
        mal(dw ? "되돌아가는 길을 불러오는 중입니다." : "길을 불러오는 중입니다.", .jeongbo)
        Task {
            let g = await Jeomjido.bulleoogi(id)
            await MainActor.run {
                guard sd == self.sedae else { return }   // 그사이 그만두셨으면 버림
                self.bulleoneun = false
                guard let g = g else {
                    // 2.12.0 이어 걷던 길을 못 불러오면 멈추지 않고 위성 안내로 이어 감
                    if ieumYuji && YeojeongEngine.shared.jigeum != nil {
                        self.ieum = []; self.ieumIdx = 0; self.ieumMok = nil
                        self.ieogaJiugi()
                        self.mal("길을 불러오지 못했습니다. 위성 안내로 이어 갑니다.")
                        AnnaeEngine.shared.georeoGagi()
                    } else {
                        self.mal("길을 불러오지 못했습니다. 통신을 확인해 주십시오.")
                    }
                    return
                }
                self.sijak(g, dwit: dw, mok: mok, ieumYuji: ieumYuji)
            }
        }
    }

    func sijak(_ g0: JeomGil, dwit dw: Bool, mok: Jangso? = nil, ieumYuji: Bool = false) {
        muleumJiugi()
        let sd = sedae
        bulleoneun = false
        geumanSok(malHam: false)
        if !ieumYuji { ieum = []; ieumIdx = 0; ieumMok = nil }
        let g = dw ? g0.dwit() : g0
        let p = g.pts.filter { $0.lat != 0 && $0.lon != 0 }
        guard p.count >= 3 else { ieogaJiugi(); mal("이 길에는 점이 너무 적습니다."); return }
        pts = p
        nu = [0]
        for i in 1..<p.count { nu.append(nu[i - 1] + WichiEngine.geori(p[i - 1].lat, p[i - 1].lon, p[i].lat, p[i].lon)) }
        pyo = g.marks.compactMap { m in
            guard let la = m.lat, let lo = m.lon, la != 0 else { return nil }
            return Pyo(p: m, i: gakkaunJeom(la, lo))
        }
        kkeoks = JeomEngine.kkeokChatgi(p)
        kkeokHan = []
        gilRaw = g0.id + (dw ? "|r" : "")
        dwit = dw
        dochakHam = false
        idx = 0; firstFix = true; me = nil; spd = 0; lastP = nil; offSu = 0; beoseoSu = 0
        jeop30 = false; jeop20 = false; cheotBang = false; dolgiMok = nil; geonneolOn = false; geonneol = nil
        geoMode = false; dwiNeolge = false; geollimJari = nil; geollimHan = []
        munOn = false; mun = nil; munList = []; munSu = 0; munIdx = 0; munDasi = false; munGakkaum = nil; munKamera = false
        juwiRows = []; juwiHan = []; juwiEonje = .distantPast; juwiJari = nil; juwiCenter = nil; juwiBatT = .distantPast
        sijakT = Date()
        jariTtae = Date(); wiseongTtae = Date()
        S = HS()
        gil = g
        // 여정 — 이 길의 끝을 목적지로(차를 타셔도 목적지가 이어지게, 웹 260910-9와 같음)
        let e = p[p.count - 1]
        let doIreum = (mok?.ireum ?? "").isEmpty ? (g.to.isEmpty ? g.title : g.to) : mok!.ireum
        let yj = YeojeongEngine.shared
        yj.jeonghagi(Mokjeok(ireum: doIreum, lat: mok?.lat ?? e.lat, lon: mok?.lon ?? e.lon, juso: mok?.juso ?? ""))
        yj.talgeotJeonghagi(.georeum, barojabeum: false)
        yj.danggyeBakkugi(.namEunGil)
        if !g0.id.hasPrefix("nae_") {
            Task { _ = await Nas.get("ttara.php", [("a", "put"), ("id", g0.id)]) }
        }
        Girok.shared.namgi("jeom_sijak", ["id": g0.id, "dwit": dw, "m": Int(nu.last ?? 0)])
        // 안전 경고 — 끌 수 없음
        Task {
            let hwakin = await JeomEngine.hwakinDoen(g0.id)
            await MainActor.run {
                guard sd == self.sedae, self.gil != nil else { return }
                var t = self.ieumIdx > 0 ? "" : "길눈은 보조 안내입니다. 단독보행을 하실 때는 반드시 흰지팡이를 짚고, 주변 소리를 먼저 확인하십시오."   // 2.50.0 흰지팡이 강조
                if !hwakin { t += " 이 점지도는 아직 확인 중인 길입니다. 조심해서 걸으십시오." }
                if !t.isEmpty { self.mal(t, .gyeonggo) }
                let s = Seoljeong.shared
                var m = self.ieum.isEmpty ? "" : "이어진 길 \(self.ieum.count)구간 가운데 \(self.ieumIdx + 1)번째 구간입니다. "
                m += (dw ? "되돌아가기를 시작합니다. " : "따라 걷기를 시작합니다. ") + "모두 \(Int((self.nu.last ?? 0).rounded()))미터입니다."
                m += s.bopokJaem ? " 걸음 수는 \(Seoljeong.bopokModeIreum(s.bopokMode)) 보폭으로 알려 드립니다." : " 보폭을 아직 재지 않으셔서 걸음 수 대신 미터로 알려 드립니다."   // 2.50.0
                if !self.kkeoks.isEmpty { m += " 이 길에 꺾이는 자리가 \(self.kkeoks.count)곳 있습니다. 미리 알려 드리겠습니다." }
                self.mal(m)
            }
        }
        ieogaJeojang(g0.id, dw, mok)
        munJunbi()
        umjikSijak()
        sigye?.invalidate()
        tikT = Date()
        sigye = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in self?.jikim() }
        WichiEngine.shared.sijak()
        TabGil.shared.tab = 0
        GilGil.shared.cheotHwamyeon()
        if let w = WichiEngine.shared.jigeum, Date().timeIntervalSince(w.ttae) < 5, !w.georeumChu { wichiBatda(w) }
    }

    /// 그만 걷기
    func geuman() {
        let bulleo = bulleoneun
        sedae += 1
        bulleoneun = false
        guard gil != nil else {
            if bulleo { ieum = []; ieumIdx = 0; ieumMok = nil; ieogaJiugi(); mal("따라 걷기를 그만두었습니다.") }
            return
        }
        ieum = []; ieumIdx = 0; ieumMok = nil
        ieogaJiugi()
        if let b = hamkkeBunho { hamkkeKkeut(b); hamkkeBunho = nil }
        let dochak = dochakHam
        geumanSok(malHam: !dochak)
        YeojeongEngine.shared.kkeut()
        if dochak { mal("여정을 끝냈습니다.") }
    }

    /// 여정을 끝낼 때 — 안내 엔진이 부름
    func yeojeongKkeut() {
        muleumJiugi()
        bulleoneun = false
        ieum = []; ieumIdx = 0; ieumMok = nil
        ieogaJiugi()
        if let b = hamkkeBunho { hamkkeKkeut(b); hamkkeBunho = nil }
        if gil != nil { geumanSok(malHam: false) }
    }

    private func geumanSok(malHam: Bool) {
        let itdeon = gil != nil
        if itdeon && !dochakHam { sseumNamgigi(kkeut: false) }
        sigye?.invalidate()
        sigye = nil
        umjik.stopAccelerometerUpdates()
        MomSensor.shared.gilnunGeoreum = nil
        WatchLink.shared.geotgiAllim(false)
        watchN0 = nil
        if garikiBonaen != nil { WatchLink.shared.garikiBonae(nil); garikiBonaen = nil }
        if momNaega { MomSensor.shared.kkeugi(); momNaega = false }
        momSseum = false
        WichiEngine.shared.momBbareum = false
        munOn = false; mun = nil; munList = []; munSu = 0; munKamera = false
        gil = nil
        dochakHam = false
        sangMal = ""
        if itdeon && malHam { mal("따라 걷기를 그만두었습니다.") }
    }

    /// 도착한 뒤 — 되돌아가기(같은 길을 거꾸로)
    func doedoragagi() {
        guard let g = gil else { return }
        let id = gilRaw.replacingOccurrences(of: "|r", with: "")
        let dw = !dwit
        // 2.12.0 이어진 길을 다 걸은 뒤에는 이어진 길 전체를 거꾸로
        if ieum.count > 1 {
            let r = ieum.reversed().map { JeomGugan(id: $0.id, dwit: !$0.dwit) }
            ieumGeotgi(Array(r), mok: nil)
            return
        }
        ieum = []; ieumIdx = 0; ieumMok = nil
        if id.hasPrefix("nae_"), let n = NaeGil.shared.chatgi(id) { sijak(n, dwit: dw); return }
        _ = g
        bulleoGeotgi(id, dwit: dw)
    }

    // MARK: 단추·이어폰·워치·말로 하기

    /// 지금 어디입니까
    func jigeumEodi() {
        guard gil != nil else { return }
        guard me != nil else { mal("지금 자리를 잡는 중입니다. 잠시만 기다려 주십시오."); return }
        mal("남은 거리는 \(georiMal(namEun))입니다. 위치 오차는 약 \(max(1, Int(acc.rounded())))미터입니다." + (geoMode ? " 지금은 걸음으로 이어 셈하고 있습니다." : ""))
    }

    /// 다음에 무엇이 있습니까
    func daeumMuot() {
        mal(daeumMuotMal())
    }

    func daeumMuotMal() -> String {
        guard gil != nil else { return "" }
        guard me != nil else { return "지금 자리를 잡는 중입니다. 잠시만 기다려 주십시오." }
        var near: Pyo?
        var nd = 1e9
        for p in pyo where p.i >= idx {
            let d = ap(idx, p.i)
            if d < nd { nd = d; near = p }
        }
        var kk: (Double, Kkeok)?
        for k in kkeoks where k.i > idx {
            let d = ap(idx, k.i)
            if kk == nil || d < kk!.0 { kk = (d, k) }
        }
        if let k = kk, k.0 < nd {
            return "다음은 꺾이는 곳이고, \(georeum(k.0)) 앞에서 \(sigyeGak(k.1.d))으로 꺾습니다."
        }
        guard let n = near else {
            if pyo.isEmpty && kkoksEopseum { return "이 길에는 표시가 없습니다. 목적지까지 남은 거리 \(georiMal(namEun))입니다." }
            return "앞에는 더 표시가 없습니다. 목적지까지 곧장 가시면 됩니다. 남은 거리 \(georiMal(namEun))입니다."
        }
        let nm = n.p.ireum
        let extra = (n.p.cnt ?? 0) > 0 && nm.contains("계단") ? " \(n.p.cnt!)칸입니다." : ""
        return "다음은 \(nm)이고, " + (nd < 3 ? "바로 앞입니다." : "\(georeum(nd)) 앞입니다.") + extra
    }

    private var kkoksEopseum: Bool { kkeoks.isEmpty }

    var namEun: Double { (nu.last ?? 0) - (nu.isEmpty ? 0 : nu[min(idx, nu.count - 1)]) }

    // MARK: 자리 받기

    private func wichiBatda(_ w: Wichi) {
        guard gil != nil, !dochakHam else { return }
        if w.georeumChu { return }   // 위치 엔진이 곧게 이어 셈한 자리는 쓰지 않고, 점지도 위로 걸음을 셈(아래 jikim)
        if geoMode && w.ochae > 25 { return }   // 걸음으로 가는 동안 흐린 위성은 받지 않음
        if w.ochae <= 25 { wiseongTtae = Date() }
        if geoMode { dwiNeolge = true }   // 걸음으로 가다 위성이 돌아온 첫 자리 — 뒤로도 넓게 찾음
        geoMode = false
        let hd: Double? = (w.banghyang >= 0 && w.sokdo > 0.3) ? w.banghyang : nil
        onMove(w.lat, w.lon, w.ochae, hd)
        // 2.11.1 사거리·갈림길 알림 — 문까지 가는 동안은 쉼
        if gil != nil && !dochakHam && !munOn { AnnaeEngine.shared.neagoriBakkeseo(w) }
    }

    private func onMove(_ la: Double, _ lo: Double, _ ac: Double, _ course: Double?) {
        guard gil != nil else { return }
        let now = Date()
        me = (la, lo)
        acc = ac
        jariTtae = now
        if let l = lastP {
            let dt = now.timeIntervalSince(l.2)
            if dt > 0.7 {
                let v = WichiEngine.geori(l.0, l.1, la, lo) / dt
                if v >= 0.2 && v <= 2.5 { spd = spd > 0 ? spd * 0.7 + v * 0.3 : v }
                lastP = (la, lo, now)
            }
        } else {
            lastP = (la, lo, now)
        }
        // 길 위에서 가장 가까운 점(처음에는 길 전체에서, 그 뒤로는 앞쪽만)
        var best = idx, bestD = 1e9
        let jeon = idx
        let k0 = firstFix ? 0 : max(0, idx - (dwiNeolge ? 40 : 3))
        let k1 = firstFix ? pts.count : min(pts.count, idx + 40)
        for k in k0..<k1 {
            let d = WichiEngine.geori(la, lo, pts[k].lat, pts[k].lon)
            if d < bestD { bestD = d; best = k }
        }
        dwiNeolge = false
        if firstFix {
            // 되돌아오는 길(시작과 끝이 붙은 길)에서 처음 자리를 끝으로 잡지 않게 — 비슷하게 가까우면 앞쪽 점
            for k in 0..<pts.count where WichiEngine.geori(la, lo, pts[k].lat, pts[k].lon) <= bestD + 5 { best = k; break }
        } else if ac > 15 && best > jeon + 15 {
            best = jeon + 15   // 흐린 위성이 크게 뛰면 한 번에 멀리 건너뛰지 않음(건널목·꺾임 알림을 놓치지 않게)
        }
        idx = best
        if firstFix {
            firstFix = false
            for i in pyo.indices where pyo[i].i < idx { pyo[i].said = true; pyo[i].near = true; pyo[i].said30 = true }
        }
        hamkkeBonaegi()
        let rest = namEun
        sangMal = "남은 거리 \(Int(rest.rounded()))미터, 위치 오차 약 \(Int(ac.rounded()))미터."
        hwaksinGil(geori: bestD, ac: ac, rest: rest)
        bangHwagin()
        // 도착 접근 안내 — 30미터쯤 한 번, 20미터 안에서 한 번, 그 뒤 2.5초마다 남은 거리와 시 방향
        let e = pts[pts.count - 1]
        let kkeutMal = jungganGugan ? "이 구간 끝" : "목적지"
        if rest >= 5 && rest < 32 && mun == nil {
            if rest > 20 {
                if !jeop30 { jeop30 = true; mal("\(kkeutMal)까지 \(Int(rest.rounded()))미터입니다.") }
            } else if !jeop20 {
                jeop20 = true; jeopT = now
                mal("\(kkeutMal) \(Int(rest.rounded()))미터 앞\(siMal(e.lat, e.lon))입니다.")
            } else if now.timeIntervalSince(jeopT) > 2.5 {
                jeopT = now
                mal("남은 거리 \(georiMal(rest))\(siMal(e.lat, e.lon))입니다.")
            }
        }
        // 2.11.0 문까지 — 길 끝 50미터 안에서 문을 다시 찾고, 30미터 안에서 문으로 이끌고, 문 5미터 안에서 도착
        if rest < 50 && !munDasi && !munOn { munDasi = true; munJunbi() }
        if let MU = mun {
            let dm = WichiEngine.geori(la, lo, MU.lat, MU.lon)
            if !munOn && rest < 30 {
                munOn = true; munT = now; munSijakT = now; munGakkaum = dm
                S.gyeol = ""; S.yeop = 0
                mal("문까지 이어 안내합니다. 여기서부터 위성 안내입니다. " + munMal(MU) + (munList.count > 1 ? " 문이 \(munList.count)곳 있습니다." : ""))
                return
            }
            if munOn {
                // 2.15.0 문 10미터 안 — 카메라 문 찾기를 저절로(2.16.0 모든 폰). 위성 안내는 그대로 이어 감
                if dm < 10 && !munKamera {
                    munKamera = true
                    if MunChatgi.gigiGaneung {
                        if !MU.geul.isEmpty { MunChatgi.shared.gidae = (MU.ireum, MU.geul) }   // 찍어 두신 문의 글자로 맞는 문인지 가림
                        MunChatgi.shared.kyeogi("munkkaji")
                    }
                }
                if let g = munGakkaum {
                    if g - dm >= 1.5 { munGakkaum = dm; eum(.hwaksin) } else if dm > g + 3 { munGakkaum = dm }
                }
                if dm < max(5, min(acc, 10)) {
                    var t = "문 앞입니다. " + munMal(MU)
                    if !MU.saengMal.isEmpty { t += " 문은 \(MU.saengMal)입니다." }
                    dochak(munAp: t)
                    return
                }
                if now.timeIntervalSince(munT) > 2.5 && !MunChatgi.shared.munBoim { munT = now; mal(munMal(MU)) }   // 2.15.0 카메라가 문을 보고 있으면 카메라 말에 맡김
                return
            }
        }
        // 2.11.1 지나는 곳 안내 — 목적지 20미터 안에서는 문 찾기에 집중하도록 쉼
        if rest >= 20 { juwiMalhagi(la, lo) }
        geollimSalpigi(la, lo)
        kkeokBoda(la, lo)
        // 길에서 크게 벗어남 — 말은 확신음 쪽이 맡고, 여기서는 걸린 자리로 남김
        if bestD > max(12, ac * 1.5) {
            offSu += 1
            if offSu >= 2 { makhimNamgigi(la, lo, bestD) }
            return
        }
        offSu = 0
        if rest < 5 { dochak(); return }
        pyoBoda()
        // 건널목 끝 표시가 없는 점지도 — 건널목 길이만큼 지나면 다 건넜다고
        if let gn = geonneol, ap(gn.i, idx) >= gn.len + 2 {
            geonneol = nil; geonneolOn = false; S.gidarim = false
            mal("다 건넜습니다. " + daeumMalGil())
        }
    }

    // MARK: 1초마다 지킴

    private func jikim() {
        guard gil != nil, !dochakHam else { return }
        let now = Date()
        // 폰이 멈췄다 깨어나 시계가 10초 넘게 건너뛰면 — 안내가 끊겼던 것(끌 수 없음)
        if now.timeIntervalSince(tikT) > 10 && now.timeIntervalSince(kkeunMalT) >= 10 {
            kkeunMalT = now
            mal("안내가 끊겼습니다. 멈추고 주변을 확인하십시오.", .gyeonggo)
            Girok.shared.namgi("jeom_kkeunkim", [:])
        }
        tikT = now
        garikiAllim(now)
        // 위성이 6초 넘게 끊기면 걸음으로 점지도 위를 나아감(georeum_iego.js)
        if now.timeIntervalSince(wiseongTtae) > 6 && S.stepSu > 0 {
            if !geoMode {
                geoMode = true
                geoSu = S.stepSu
                geoS = S.jin ?? (nu.isEmpty ? 0 : nu[min(idx, nu.count - 1)])
                geoS0 = geoS
                geoAcc0 = max(3, acc)
            } else if S.stepSu > geoSu {
                geoS += Double(S.stepSu - geoSu) * bocok
                geoSu = S.stepSu
                let q = jeomAt(geoS)
                onMove(q.0, q.1, geoAcc0 + (geoS - geoS0) * 0.1, nil)   // 걸을수록 오차가 커짐
            }
        }
        // 2.12.0 문을 90초 넘게 찾으면 — 끝없이 말하지 않고 마침
        if munOn, let MU = mun, now.timeIntervalSince(munSijakT) > 90 {
            dochak(munAp: "문을 찾는 시간이 길어져 안내를 마칩니다. " + munMal(MU))
            return
        }
        // 점지도를 확신할 수 없는 동안(폰 방향이 들쭉날쭉) 5초마다
        if S.jumeoni && !munOn && !S.pokgiMal && now.timeIntervalSince(S.hwakMalT) >= 5 {
            S.hwakMalT = now
            mal("점지도 확인이 어렵습니다. 멈추고 주변을 확인하십시오.", .gyeonggo)
        }
        if now.timeIntervalSince(jariTtae) > 5 && !georeumSalanna {
            if now.timeIntervalSince(S.wdMal) >= 5 {
                S.wdMal = now; S.sinho = now
                mal("점지도 확인이 어렵습니다. 멈추고 주변을 확인하십시오.", .gyeonggo)
            }
            return
        }
        if S.gyeol == "beoseo" {
            if now.timeIntervalSince(S.offT) >= 2.5 { S.offT = now; eum(.beoseo); S.sinho = now }
            if now.timeIntervalSince(S.offMalT) >= 5 { S.offMalT = now; beoseoMal(cheot: false) }
            return
        }
        if S.gidarim { return }
        if now < S.dolgiKkaji { return }
        if now.timeIntervalSince(malT) < 5 { S.sinho = max(S.sinho, malT); return }
        if now.timeIntervalSince(S.sinho) >= 5 && now.timeIntervalSince(S.wdMal) >= 5 {
            S.wdMal = now; S.sinho = now
            let t = S.rest.map { "남은 거리 \(georiMal($0))." } ?? sangMal
            mal((S.gamyeon ? "안내 중입니다. " : "제자리에 계십니다. ") + t, .jeongbo)
            S.gamyeon = false
        }
    }

    // MARK: 걸음

    private func umjikSijak() {
        WatchLink.shared.geotgiAllim(true)   // 2.35.0 워치도 깨어 걸음을 셈
        // 2.32.0 몸 센서 — 1초에 50번, 발이 땅에 닿을 때마다 한 걸음, 자이로로 돈 각도
        if CMMotionManager().isDeviceMotionAvailable {
            let ms = MomSensor.shared
            ms.nachimbanNeogi(WichiEngine.shared.nachimban)
            if !ms.dollyeo { ms.kyeogi(); momNaega = true }
            momSseum = true
            WichiEngine.shared.momBbareum = true
            ms.gilnunGeoreum = { [weak self] in
                guard let self = self else { return }
                WichiEngine.shared.momGeoreumNal()
                self.ponGeoreumT = Date()
                if self.S.gidarim { self.S.gidarimSu += 1; if self.S.gidarimSu >= 3 { self.S.gidarim = false } }
                self.georeum()
            }
            Girok.shared.namgi("jeom_momsensor", ["on": true])
            return
        }
        guard umjik.isAccelerometerAvailable else { return }
        umjik.accelerometerUpdateInterval = 1.0 / 50
        moG = 9.8; moWi = false
        umjik.startAccelerometerUpdates(to: .main) { [weak self] d, _ in
            guard let self = self, let a = d?.acceleration else { return }
            let m = sqrt(a.x * a.x + a.y * a.y + a.z * a.z) * 9.81
            self.moG = self.moG * 0.95 + m * 0.05
            let df = m - self.moG
            let now = Date()
            if !self.moWi && df > 1.3 && now.timeIntervalSince(self.moT) > 0.3 {
                self.moWi = true
                self.moT = now
                self.ponGeoreumT = now
                if self.S.gidarim { self.S.gidarimSu += 1; if self.S.gidarimSu >= 3 { self.S.gidarim = false } }
                self.georeum()
            } else if self.moWi && df < 0.3 {
                self.moWi = false
            }
        }
    }

    /// 2.36.0 (빌드 261002-4, 대표님 승인) 손목 가리키기 — 가야 할 쪽(돌아야 할 때는 돌 쪽, 아니면 앞 6미터)을 워치에
    ///   5도 넘게 바뀌거나 10초가 지나면 다시 보냄
    private func garikiAllim(_ now: Date) {
        guard let b = dolgiMok?.bang ?? gilBang(idx, 6) else { return }
        if let o = garikiBonaen, abs(chai(b, o)) < 5, now.timeIntervalSince(garikiT) < 10 { return }
        garikiBonaen = b; garikiT = now
        WatchLink.shared.garikiBonae(b)
    }

    /// 2.36.0 가리키기 방향 맞추기 — 지금 몸이 향한 방향(따라 걷는 중일 때만)
    var momBang: Double? { gil != nil ? jigeumHead : nil }

    /// 2.35.0 (빌드 261002-3, 대표님 승인) 워치가 팔 흔들림으로 센 걸음 누계
    func watchGeoreum(_ n: Int) {
        guard gil != nil else { watchN0 = nil; return }
        defer { watchN0 = n }
        guard let n0 = watchN0, n > n0 else { return }
        guard Date().timeIntervalSince(ponGeoreumT) > 4 else { return }
        for _ in 0..<min(n - n0, 10) {
            if S.gidarim { S.gidarimSu += 1; if S.gidarimSu >= 3 { S.gidarim = false } }
            georeum()
        }
        Girok.shared.namgi("jeom_watch_georeum", ["n": n - n0])
    }

    private var nachimban: Double? {
        // 2.32.0 몸 센서를 쓰는 동안은 자이로 합성 방향 — 쇠붙이·건물 옆에서 나침반이 흔들려도 틀어지지 않음
        if momSseum, MomSensor.shared.dollyeo, let h = MomSensor.shared.hapseong { return h }
        let n = WichiEngine.shared.nachimban
        return n >= 0 ? n : nil
    }

    private var georeumSalanna: Bool {
        Date().timeIntervalSince(S.stepT) < 120 && nachimban != nil && !S.jumeoni
    }

    /// 발을 한 번 디딜 때마다
    private func georeum() {
        guard gil != nil, !dochakHam, !munOn else { return }
        let now = Date()
        if now.timeIntervalSince(S.stepT) > 10 {
            S.yeop = 0
            if S.gyeol == "beoseo" || S.gyeol == "heundeul" { S.gyeol = "" }
            S.offSu = 0; S.gpsNeomSu = 0
        }
        S.stepT = now
        S.stepSu += 1
        guard let seg = S.segBang else { return }
        if now < S.dolgiKkaji { S.gyeolgwa = []; S.dallaSu = 0; S.majaSu = 0; return }
        guard let h = nachimban else { return }
        let nal = chai(h, seg)
        S.gyeolgwa.append((nal, S.jin))
        if S.gyeolgwa.count > 8 { S.gyeolgwa.removeFirst() }
        let df = chai(h - S.dolrim, seg)
        if abs(df) > 70 {
            S.dallaSu += 1; S.majaSu = 0
            if S.dallaSu >= 6 {
                // 폰을 거꾸로(옆으로) 넣으셨는지 — 늘 같은 쪽으로 어긋나며 앞으로 나아가면 그만큼 돌려 읽음
                let g = S.gyeolgwa.suffix(6)
                var sx = 0.0, sy = 0.0
                for x in g { sx += sin(x.nal * RAD); sy += cos(x.nal * RAD) }
                let goreum = sqrt(sx * sx + sy * sy) / Double(g.count)
                let pyeong = atan2(sx, sy) / RAD
                var nagam = 0.0
                if let a = g.last?.jin, let b = g.first?.jin { nagam = a - b }
                if goreum > 0.8 && nagam >= 2 {
                    let dol = ((pyeong / 90).rounded() * 90 + 360).truncatingRemainder(dividingBy: 360)
                    if dol != 0 {
                        S.dolrim = dol; S.dallaSu = 0; S.majaSu = 0; S.jumeoni = false; S.yeop = 0; S.gyeolgwa = []
                        if S.gyeol == "beoseo" || S.gyeol == "heundeul" { S.gyeol = "" }
                        mal(dol == 180 ? "폰이 거꾸로 들어 있어 맞춰 읽겠습니다." : "폰이 옆으로 들어 있어 맞춰 읽겠습니다.")
                        return
                    }
                }
                if !S.jumeoni {
                    S.jumeoni = true
                    if !S.jumeoniMal {
                        S.jumeoniMal = true; S.hwakMalT = Date()
                        mal("점지도 확인이 어렵습니다. 멈추고 주변을 확인하십시오. 폰이 향한 쪽이 자꾸 바뀝니다. 가슴 주머니에 세워 넣거나 가슴 앞에 들어 주십시오.", .gyeonggo)
                    }
                }
            }
            return
        }
        if abs(df) < 30 { S.majaSu += 1; if S.majaSu >= 3 { S.dallaSu = 0; if S.jumeoni { S.jumeoni = false; S.yeop = 0 } } }
        if S.jumeoni { return }
        // 폰이 흔들려 방향이 들쭉날쭉한 걸음은 셈에 넣지 않음(헛경고를 막음)
        let gg = S.gyeolgwa, n3 = gg.count
        if n3 >= 3 {
            let p1 = gg[n3 - 1].nal, p2 = gg[n3 - 2].nal, p3 = gg[n3 - 3].nal
            let d12 = chai(p1, p2), d23 = chai(p2, p3)
            if (abs(d12) > 35 && abs(d23) > 35 && d12 * d23 < 0 && abs(chai(p1, p3)) > 20) || abs(chai(p1, p3)) > 100 || abs(d12) > 100 {
                S.heundeulSu += 1
                if S.heundeulSu >= 6 && !S.jumeoniMal {
                    S.jumeoniMal = true; S.hwakMalT = Date()
                    mal("점지도 확인이 어렵습니다. 멈추고 주변을 확인하십시오. 폰이 향한 쪽이 자꾸 바뀝니다. 가슴 주머니에 세워 넣거나 가슴 앞에 들어 주십시오.", .gyeonggo)
                }
                return
            }
        }
        S.heundeulSu = 0
        S.yeop += bocok * sin(df * RAD)
        pandan()
    }

    private func pandan() {
        let b = bocok, a = abs(S.yeop), now = Date()
        if a >= b {
            if S.gyeol != "beoseo" {
                S.gyeol = "beoseo"; S.offT = now; S.offMalT = now; beoseoSu += 1
                eum(.beoseo); beoseoMal(cheot: true)
                if let m = me { makhimNamgigi(m.0, m.1, a) }
            }
            S.sinho = now
            return
        }
        if S.gyeol == "beoseo" {
            if a > b * 0.45 { S.sinho = now; return }
            S.gyeol = "baro"; eum(.doraom); mal("점지도 위로 돌아오셨습니다."); S.sinho = now
            return
        }
        if a >= b * 0.5 || (S.gyeol == "heundeul" && a > b * 0.3) {
            eum(.bikyeo)
            if S.gyeol != "heundeul" { S.gyeol = "heundeul"; mal((S.yeop > 0 ? "3시" : "9시") + " 방향으로 조금 비켜나십니다.") }
            S.sinho = now
            return
        }
        S.gyeol = "baro"; eum(.jeomOk); S.sinho = now; S.gamyeon = true
    }

    private func beoseoMal(cheot: Bool) {
        let ap = cheot ? "점지도에서 벗어났습니다. 멈추고 방향을 다시 잡으십시오. " : ""
        let oreun = S.yeop > 0
        // 2.12.0 돌아갈 방향은 몸이 향한 쪽 기준 — 점지도 방향과 벗어난 거리로 셈(모르면 11시·1시)
        var bangM = oreun ? "11시 방향" : "1시 방향"
        if let seg = S.segBang, let h = jigeumHead {
            let mok = seg + atan2(-S.yeop, 3) / RAD
            bangM = sigyeGak(chai(mok, h))
        }
        if !georeumSalanna {
            mal(ap + (oreun ? "3시 방향으로 " : "9시 방향으로 ") + "약 \(max(1, Int(abs(S.yeop).rounded())))미터 벗어났습니다. " + bangM + "으로 돌아가십시오.", .gyeonggo)
            return
        }
        let n = max(1, Int((abs(S.yeop) / bocok).rounded()))
        let gm = JeomEngine.georeumSu(n)
        mal(ap + (oreun ? "3시 방향으로 " : "9시 방향으로 ") + gm + " 벗어났습니다. " + bangM + "으로 " + gm + " 옮기십시오.", .gyeonggo)
    }

    /// 자리가 올 때마다(위성이든 걸음이든) — 점지도 선 위에 앉혀 옆 거리와 나아간 거리를 봄
    private func hwaksinGil(geori g0: Double, ac: Double, rest: Double) {
        guard let m = me, !munOn else { return }
        let now = Date()
        var g = g0, jin = -rest
        var gy: Double?
        S.rest = rest
        let an = anchigi(m.0, m.1)
        if let s = an.s {
            jin = s; g = min(g, an.d); gy = an.y; S.jin = s
            if S.jinSijak == nil { S.jinSijak = s }
            // 2.12.0 구간 방향은 앞쪽 8미터로(꺾이는 곳에서 끊음), 꺾이는 곳이 2미터 안이면 뒤쪽 8미터로
            var s0 = s, s1 = s + 8
            if let nk = kkeoks.first(where: { $0.i < nu.count && nu[$0.i] > s }) { s1 = min(s1, nu[nk.i]) }
            if s1 - s < 2 {
                s0 = s - 8; s1 = s
                if let pk = kkeoks.last(where: { $0.i < nu.count && nu[$0.i] <= s }) { s0 = max(s0, nu[pk.i]) }
                if s1 - s0 < 1 { s0 = s - 2 }
            }
            let a = jeomAt(s0), b = jeomAt(s1)
            let bg = WichiEngine.bangwi(a.0, a.1, b.0, b.1)
            S.segBang = bg
            if let k = S.segKijun {
                if abs(chai(bg, k)) > 30 { S.segKijun = bg; if S.gyeol != "beoseo" { S.yeop = 0 } }
            } else {
                S.segKijun = bg
            }
        }
        if georeumSalanna {
            if let y = gy, ac > 0, ac <= 6, now.timeIntervalSince(S.stepT) < 2 { S.yeop = S.yeop * 0.97 + y * 0.03 }
            if !S.gidarim, let y = gy, abs(y) > max(5, ac * 1.2) {
                S.gpsNeomSu += 1
                if S.gpsNeomSu >= 3 { S.yeop = y; pandan() }
            } else {
                S.gpsNeomSu = 0
            }
        } else {
            // 걸음 감지·나침반을 못 쓸 때 — 되는 척하지 않고 분명히 알린 뒤, 위성 오차에 맞춘 잣대로
            if !S.pokgiMal && now.timeIntervalSince(S.sijakT) > 15, let j = S.jin, let j0 = S.jinSijak, abs(j - j0) >= 8 {
                S.pokgiMal = true
                let kkadak = S.jumeoni ? "폰이 향한 쪽이 자꾸 바뀌어" : (S.stepSu == 0 ? "걸음 감지를 쓸 수 없어" : (nachimban == nil ? "나침반을 쓸 수 없어" : "걸음 감지가 끊겨"))
                mal(kkadak + " 한 걸음 안내가 되지 않습니다. 지금부터 위성 안내입니다. 몇 미터 오차가 있을 수 있습니다.", .gyeonggo)
                Girok.shared.namgi("jeom_wiseongsem", ["k": kkadak])
            }
            let beoseo = g > max(8, ac * 1.2)
            let heundeul = !beoseo && g > max(5, ac * 0.8)
            if let y = gy { S.yeop = (y >= 0 ? 1 : -1) * max(g, 0) }
            if beoseo && !S.gidarim {
                S.offSu += 1
                if S.offSu >= 2 {
                    if S.gyeol != "beoseo" {
                        S.gyeol = "beoseo"; S.offT = now; S.offMalT = now; beoseoSu += 1
                        eum(.beoseo); beoseoMal(cheot: true)
                        if let mm = me { makhimNamgigi(mm.0, mm.1, g) }
                    }
                    S.sinho = now
                }
            } else {
                S.offSu = 0
                if S.gyeol == "beoseo" { S.gyeol = "baro"; eum(.doraom); mal("점지도 위로 돌아오셨습니다."); S.sinho = now; S.kijun = jin }
                if S.kijun == nil { S.kijun = jin }
                if let k = S.kijun {
                    if jin - k >= 0.8 {
                        S.kijun = jin; S.gamyeon = true; S.sinho = now
                        if heundeul {
                            eum(.bikyeo)
                            if S.gyeol != "heundeul" { S.gyeol = "heundeul"; mal((S.yeop > 0 ? "3시" : "9시") + " 방향으로 조금 비켜나십니다.") }
                        } else {
                            eum(.jeomOk); S.gyeol = "baro"
                        }
                    } else if jin < k - 3 {
                        S.kijun = jin
                    }
                }
            }
        }
        // 정한 거리마다 "제대로 가고 있습니다"
        if S.malKijun == nil { S.malKijun = jin }
        if let mk = S.malKijun {
            if jin - mk >= Double(Seoljeong.shared.hwaksinGan) {
                S.malKijun = jin
                if S.gyeol != "beoseo" && rest >= 32 && now.timeIntervalSince(malT) >= 4, let jm = daeumMal() {
                    mal("제대로 가고 있습니다. " + (jm.isEmpty ? "남은 거리 \(georiMal(rest))." : jm))
                    S.sinho = now
                }
            } else if jin < mk - 5 {
                S.malKijun = jin
            }
        }
    }

    /// 점지도 선 위에 지금 자리를 앉힘 — s: 처음부터 몇 미터, d: 떨어진 거리, y: 오른쪽이 + 인 옆 거리
    private func anchigi(_ la: Double, _ lo: Double) -> (s: Double?, d: Double, y: Double) {
        let R = 6371000.0, cs = cos(la * RAD)
        let k0 = max(0, idx - 8), k1 = min(pts.count - 2, idx + 15)
        var best = 1e9, s: Double?, y = 0.0
        guard k0 <= k1 else { return (nil, best, 0) }
        for k in k0...k1 {
            let ax = (pts[k].lon - lo) * RAD * cs * R, ay = (pts[k].lat - la) * RAD * R
            let bx = (pts[k + 1].lon - lo) * RAD * cs * R, by = (pts[k + 1].lat - la) * RAD * R
            let dx = bx - ax, dy = by - ay, L2 = dx * dx + dy * dy
            var t = L2 > 0 ? -(ax * dx + ay * dy) / L2 : 0
            t = max(0, min(1, t))
            let px = ax + t * dx, py = ay + t * dy, d = sqrt(px * px + py * py)
            if d < best {
                best = d
                s = nu[k] + t * (nu[k + 1] - nu[k])
                let L = sqrt(L2) > 0 ? sqrt(L2) : 1
                y = (dx * py - dy * px) / L
            }
        }
        return (s, best, y)
    }

    /// 처음부터 s미터 되는 점지도 위의 자리
    private func jeomAt(_ s: Double) -> (Double, Double) {
        guard let last = nu.last, !pts.isEmpty else { return (0, 0) }
        if s <= 0 { return (pts[0].lat, pts[0].lon) }
        if s >= last { return (pts[pts.count - 1].lat, pts[pts.count - 1].lon) }
        var lo = 0, hi = nu.count - 1
        while hi - lo > 1 { let mid = (lo + hi) / 2; if nu[mid] <= s { lo = mid } else { hi = mid } }
        let f = (nu[hi] - nu[lo]) > 0 ? (s - nu[lo]) / (nu[hi] - nu[lo]) : 0
        return (pts[lo].lat + (pts[hi].lat - pts[lo].lat) * f, pts[lo].lon + (pts[hi].lon - pts[lo].lon) * f)
    }

    private func dolgi(_ s: Double) { S.dolgiKkaji = Date().addingTimeInterval(s); S.yeop = 0; if S.gyeol == "heundeul" { S.gyeol = "" } }
    private func dolgiKkeut() { S.dolgiKkaji = .distantPast; S.yeop = 0; S.gyeolgwa = [] }

    // MARK: 꺾이는 곳(kkeokim.js)

    private static func kkeokChatgi(_ p: [JeomJeom]) -> [Kkeok] {
        var out: [Kkeok] = []
        guard p.count >= 5 else { return out }
        func g(_ a: Int, _ b: Int) -> Double { WichiEngine.geori(p[a].lat, p[a].lon, p[b].lat, p[b].lon) }
        func meol(_ i: Int, _ dir: Int) -> Int {
            var j = i, m = 0.0
            while true {
                let k = j + dir
                if k < 0 || k >= p.count { break }
                m += g(j, k)
                j = k
                if m >= 10 { break }
            }
            return j
        }
        for i in 2..<(p.count - 2) {
            let a = meol(i, -1), b = meol(i, 1)
            if a == i || b == i { continue }
            if g(a, i) < 6 || g(i, b) < 6 { continue }
            let b1 = WichiEngine.bangwi(p[a].lat, p[a].lon, p[i].lat, p[i].lon)
            let b2 = WichiEngine.bangwi(p[i].lat, p[i].lon, p[b].lat, p[b].lon)
            let d = (b2 - b1 + 540).truncatingRemainder(dividingBy: 360) - 180
            if !d.isFinite || abs(d) < 35 { continue }
            if let jeon = out.last, g(jeon.i, i) < 12, jeon.d * d > 0 {
                if abs(d) > abs(jeon.d) { out[out.count - 1] = Kkeok(i: i, d: d, lat: p[i].lat, lon: p[i].lon) }
                continue
            }
            out.append(Kkeok(i: i, d: d, lat: p[i].lat, lon: p[i].lon))
        }
        // 위성이 흔들린 자국을 모퉁이로 잘못 읽지 않게 — 서른 미터 안에 붙은 꺾임은 한 덩이로, 틀어진 만큼(합)만
        guard out.count >= 2 else { return out }
        var res: [Kkeok] = []
        var mung: [Kkeok] = [out[0]]
        func mudda(_ gg: [Kkeok]) {
            if gg.count == 1 { res.append(gg[0]); return }
            let hap = gg.reduce(0) { $0 + $1.d }
            if abs(hap) < 35 { return }
            let j = gg[gg.count / 2]
            res.append(Kkeok(i: j.i, d: hap, lat: j.lat, lon: j.lon))
        }
        for n in 1..<out.count {
            let jeon = mung[mung.count - 1], now = out[n]
            // 2.12.0 같은 쪽으로 꺾인 것끼리만 한 덩이로(왼쪽 뒤 오른쪽처럼 반대로 꺾인 두 모퉁이가 지워지지 않게)
            if WichiEngine.geori(jeon.lat, jeon.lon, now.lat, now.lon) < 30 && jeon.d * now.d > 0 { mung.append(now) } else { mudda(mung); mung = [now] }
        }
        mudda(mung)
        // 15미터 안에서 반대로 살짝 꺾였다 돌아오는 것은 위성이 흔들린 자국으로 보아 뺌
        var n2 = 0
        var gyeol: [Kkeok] = []
        while n2 < res.count {
            if n2 + 1 < res.count {
                let a = res[n2], b = res[n2 + 1]
                if a.d * b.d < 0 && abs(a.d) < 60 && abs(b.d) < 60 && WichiEngine.geori(a.lat, a.lon, b.lat, b.lon) < 15 { n2 += 2; continue }
            }
            gyeol.append(res[n2]); n2 += 1
        }
        return gyeol
    }

    private func kkeokBoda(_ la: Double, _ lo: Double) {
        // 2.12.0 위성이 뛰어 모퉁이를 지나쳐도 가까이(오차만큼) 있으면 놓치지 않고 알림
        let yeoyu = max(8, acc)
        guard let dk = kkeoks.first(where: { k in
            !kkeokHan.contains("k\(k.i)b") && (k.i >= idx - 1 || ap(k.i, idx) <= yeoyu)
        }) else { return }
        let mi = WichiEngine.geori(la, lo, dk.lat, dk.lon)
        let key = "k\(dk.i)"
        if dk.i < idx - 1 {
            kkeokHan.insert(key + "a"); kkeokHan.insert(key + "c"); kkeokHan.insert(key + "b")
            mal("지금 \(sigyeGak(dk.d))으로 도십시오.")
            Jindong.banghyang(sigyeSu(dk.d))   // 2.38.0 꺾는 곳에서 폰과 워치에 방향 진동(안드로이드 길눈 2.5.0과 맞춤)
            dolgi(8)
            dolgiMok = (dk.i, nil, Date()); dolgiGijun()
            return
        }
        let su = bocokSu(mi)
        if su <= 30 && su > 13 && !kkeokHan.contains(key + "a") {
            kkeokHan.insert(key + "a")
            if Date().timeIntervalSince(malT) >= 3 { mal("직진 \(georeum(mi)) 뒤 \(sigyeGak(dk.d))으로 꺾습니다.") }
        } else if su <= 11 && mi > 5 && !kkeokHan.contains(key + "c") {
            kkeokHan.insert(key + "c"); kkeokHan.insert(key + "a")
            mal("\(georeum(mi)) 앞에서 \(sigyeGak(dk.d))으로 꺾습니다.")
        } else if mi <= 5 && !kkeokHan.contains(key + "b") {
            kkeokHan.insert(key + "b")
            mal("지금 \(sigyeGak(dk.d))으로 도십시오.")
            Jindong.banghyang(sigyeSu(dk.d))   // 2.38.0 꺾는 곳에서 폰과 워치에 방향 진동(안드로이드 길눈 2.5.0과 맞춤)
            dolgi(8)
            dolgiMok = (dk.i, nil, Date()); dolgiGijun()
        }
    }

    private func kkeokGakkai(_ mi: Int) -> Bool {
        kkeoks.contains { abs(ap(min($0.i, mi), max($0.i, mi))) <= 15 }
    }

    // MARK: 출발 방향·돈 뒤 방향 확인(현장영상해설 말법)

    private func gilBang(_ from: Int, _ m: Double) -> Double? {
        var s = 0.0, k = from
        while k < pts.count - 1 && s < m {
            s += WichiEngine.geori(pts[k].lat, pts[k].lon, pts[k + 1].lat, pts[k + 1].lon)
            k += 1
        }
        return k > from ? WichiEngine.bangwi(pts[from].lat, pts[from].lon, pts[k].lat, pts[k].lon) : nil
    }

    private var jigeumHead: Double? {
        if let n = nachimban { return (n - S.dolrim + 720).truncatingRemainder(dividingBy: 360) }   // 폰을 돌려 넣으신 만큼 바로잡음
        if let w = WichiEngine.shared.jigeum, w.banghyang >= 0, w.sokdo > 0.3 { return w.banghyang }
        return nil
    }

    /// 2.32.0 도시라고 말씀드린 순간의 몸 돈 각도를 기준으로 잡음
    private func dolgiGijun() {
        dolgiDg0 = (momSseum && MomSensor.shared.dollyeo) ? MomSensor.shared.nujeokDol : nil
        dolgiWant = nil
        bandaeMal = false
    }

    private func bangHwagin() {
        let hh = jigeumHead, now = Date()
        if !cheotBang {
            guard now.timeIntervalSince(sijakT) >= 5 else { return }   // 첫 안전 경고가 끝난 뒤에
            cheotBang = true
            guard let gb = gilBang(idx, 6) else { return }
            guard let h = hh else { mal(daeumMalGil()); return }
            let df = chai(gb, h)
            if abs(df) > 25 {
                mal("지금 몸을 \(sigyeGak(df))으로 돌리십시오.")
                dolgi(8)
                dolgiMok = (nil, gb, now); dolgiGijun()
            } else {
                mal("12시 방향 맞습니다. " + daeumMalGil())
            }
            return
        }
        guard var dm = dolgiMok else { return }
        if now.timeIntervalSince(dm.t) > 20 { dolgiMok = nil; return }
        if dm.bang == nil, let k = dm.k {
            guard let b = gilBang(k, 6) else { dolgiMok = nil; return }
            dm.bang = b
            dolgiMok = dm
        }
        guard let mok = dm.bang else { return }
        // 2.32.0 몸 센서 — 도셔야 할 쪽과 반대로 크게 도시면 곧바로 알려 드림(자이로로 잰 돈 각도)
        if let h = hh, momSseum, MomSensor.shared.dollyeo, let d0 = dolgiDg0, !bandaeMal {
            if dolgiWant == nil { dolgiWant = chai(mok, h) }
            if let w = dolgiWant, abs(w) >= 45 {
                let dd = MomSensor.shared.nujeokDol - d0
                if dd * w < 0 && abs(dd) >= 45 {
                    bandaeMal = true
                    mal("반대쪽으로 도셨습니다. 몸을 \(sigyeGak(chai(mok, h)))으로 돌리십시오.")
                    Girok.shared.namgi("jeom_bandae", ["dd": Int(dd), "w": Int(w)])
                }
            }
        }
        if let h = hh {
            if now.timeIntervalSince(dm.t) > 1.5 && abs(chai(mok, h)) <= 25 {
                dolgiMok = nil
                dolgiKkeut()
                mal("12시 방향 맞습니다. " + daeumMalGil())
            }
        } else if let k = dm.k, idx > k, ap(k, idx) >= 4 {
            dolgiMok = nil
            mal("직진입니다. " + daeumMalGil())
        }
    }

    // MARK: 표시

    private func ilMal(_ nm: String) -> String {
        if nm.contains("꺾임") || nm.contains("끝") { return "" }
        if nm.contains("신호등 없는 횡단보도") { return "신호등 없는 건널목입니다" }
        if nm.contains("횡단보도") || nm.contains("건널목") { return "건널목입니다" }
        if nm.contains("계단") { return "계단입니다" }
        if nm.contains("오름턱") { return "올라서는 턱입니다" }
        if nm.contains("내림턱") { return "내려서는 턱입니다" }
        if nm.contains("점자블록 끊김") { return "점자블록이 끊깁니다" }
        if nm.contains("조심할 곳") { return "조심할 곳입니다" }
        return nm + "입니다"
    }

    /// 2.53.0 꺾임 표시 이름 → 시계 방향(새 이름 「3시 방향으로 꺾임」과 옛 이름 「오른쪽으로 꺾임」 모두 읽음)
    private func jjok(_ nm: String) -> String {
        if let r = nm.range(of: "(1[0-2]|[1-9])시", options: .regularExpression) { return String(nm[r]) }
        return (nm.contains("오른") || nm.contains("우회전")) ? "3시" : "9시"
    }

    private func yego(_ p: Pyo, _ ahead: Double, seoreun: Bool) -> String {
        let nm = p.p.ireum
        let apm = seoreun ? "직진 \(georeum(ahead)) 뒤 " : (ahead < 3 ? "바로 앞 " : "\(georeum(ahead)) 앞 ")
        if nm.contains("꺾임") {
            if kkeokGakkai(p.i) { return "" }
            return seoreun ? "직진 \(georeum(ahead)) 뒤 \(jjok(nm)) 방향으로 꺾습니다." : "\(georeum(ahead)) 앞에서 \(jjok(nm)) 방향으로 꺾습니다."
        }
        if nm.contains("끝") { return "" }
        let m = ilMal(nm)
        if m.isEmpty { return "" }
        let cnt = (p.p.cnt ?? 0) > 0 && nm.contains("계단") ? " \(p.p.cnt!)칸입니다." : ""
        var t = apm + m + "." + cnt
        if let h = p.p.mal, !h.isEmpty, !seoreun { t += " " + h }
        return t
    }

    private func jigeumPyoMal(_ p: Pyo) -> String {
        let nm = p.p.ireum
        if nm.contains("꺾임") {
            if kkeokGakkai(p.i) { return "" }
            dolgi(8)
            return "지금 \(jjok(nm)) 방향으로 도십시오."
        }
        if nm.contains("건너기 끝") || nm.contains("횡단보도 끝") || nm.contains("건널목 끝") {
            if !geonneolOn { return "" }
            geonneolOn = false; geonneol = nil; S.gidarim = false
            return "다 건넜습니다. " + daeumMalGil()
        }
        if nm.contains("계단 끝") { return "계단 끝입니다. " + daeumMalGil() }
        if nm.contains("횡단보도") || nm.contains("건널목") {
            var kkeut: Pyo?
            for q in pyo {
                let n2 = q.p.ireum
                guard n2.contains("건너기 끝") || n2.contains("횡단보도 끝") || n2.contains("건널목 끝") else { continue }
                if q.i > p.i && ap(p.i, q.i) <= 60 && (kkeut == nil || q.i < kkeut!.i) { kkeut = q }
            }
            let gil2 = kkeut.map { ap(p.i, $0.i) } ?? (p.p.dist ?? 0)
            var bangM = ""
            if let k = kkeut, let h = jigeumHead, let la = p.p.lat, let lo = p.p.lon, let kla = k.p.lat, let klo = k.p.lon {
                bangM = sigyeGak(chai(WichiEngine.bangwi(la, lo, kla, klo), h)) + "으로 "
            }
            S.gidarim = true; S.gidarimSu = 0
            geonneolOn = true
            if kkeut == nil { geonneol = (p.i, gil2 > 0 ? gil2 : 12) }
            let jeol = nm.contains("신호등 없는") ? "신호등 없는 건널목입니다. 멈추십시오. 차 소리를 잘 들으십시오. " : "건널목입니다. 멈추십시오. 신호등 상황을 확인하십시오. 길눈은 신호 색을 알지 못합니다. "
            let dwi = gil2 > 0 ? "건널목은 \(bangM)\(georeum(gil2))입니다." : "다 건너시면 알려 드립니다."
            mal(jeol + dwi, .gyeonggo)   // 건널목은 끌 수 없는 경고로
            return ""
        }
        if nm.contains("계단") { return "계단 시작입니다." + ((p.p.cnt ?? 0) > 0 ? " \(p.p.cnt!)칸입니다." : "") }
        if nm.contains("오름턱") { return "지금 올라서는 턱입니다." }
        if nm.contains("내림턱") { return "지금 내려서는 턱입니다." }
        if nm.contains("점자블록 끊김") { return "여기서 점자블록이 끊깁니다." }
        if nm.contains("조심할 곳") { return "지금 조심할 곳입니다. 천천히 가십시오." }
        if nm.contains("꺾임") { return "지금 \(jjok(nm)) 방향으로 꺾으십시오." }
        return "지금 \(nm)입니다." + ((p.p.mal ?? "").isEmpty ? "" : " " + (p.p.mal ?? ""))
    }

    private func pyoBoda() {
        for n in pyo.indices {
            var p = pyo[n]
            // 지나온 것은 말하지 않음 — 위성이 앞질러 표시를 지나쳐도 8미터 안이면 지금 말함
            if p.i < idx - 1 {
                if !p.near && ap(p.i, idx) <= max(8, acc) { p.said = true } else { p.said = true; p.near = true; pyo[n] = p; continue }
            }
            let ahead = ap(idx, p.i)
            if !p.said30 && !p.said && ahead > 0.7 * 13 && bocokSu(ahead) <= 30 {
                p.said30 = true
                let y = yego(p, ahead, seoreun: true)
                if !y.isEmpty && Date().timeIntervalSince(malT) >= 3 { mal(y) }
            }
            if !p.said && ahead <= 7.7 {
                p.said = true
                let y = yego(p, ahead, seoreun: false)
                if !y.isEmpty { mal(y) }
            }
            if !p.near && ahead <= 3 {
                p.near = true
                pyo[n] = p
                let j = jigeumPyoMal(p)
                if !j.isEmpty { mal(j) }
                continue
            }
            pyo[n] = p
        }
    }

    // MARK: 다음 일

    private func daeumIl() -> (a: Double, mal: String)? {
        var best: (a: Double, mal: String)?
        for k in kkeoks where k.i > idx && k.i != dolgiMok?.k {
            let a = ap(idx, k.i)
            if a < 2 { continue }
            if best == nil || a < best!.a { best = (a, "\(sigyeGak(k.d))으로 꺾습니다") }
        }
        for p in pyo where p.i > idx {
            let a = ap(idx, p.i)
            if a < 2 { continue }
            let nm = p.p.ireum
            var m = ilMal(nm)
            if m.isEmpty && nm.contains("꺾임") && !kkeokGakkai(p.i) { m = "\(jjok(nm)) 방향으로 꺾습니다" }
            if m.isEmpty { continue }
            if best == nil || a < best!.a { best = (a, m) }
        }
        return best
    }

    /// "제대로 가고 있습니다" 뒤에 붙는 말 — 서른 걸음 안에 할 일이 있으면 nil(그때는 예고가 걸음 수로 말함)
    private func daeumMal() -> String? {
        guard let b = daeumIl() else { return bocokSu(namEun) > 30 ? "계속 직진하세요." : nil }
        if bocokSu(b.a) <= 30 { return nil }
        return "계속 직진하세요."
    }

    private func daeumMalGil() -> String {
        guard let b = daeumIl() else {
            return bocokSu(namEun) > 30 ? "계속 직진하세요." : "직진 \(georeum(namEun)) 가시면 목적지입니다."
        }
        if b.a < 4 { return "이어서 \(b.mal)." }
        if bocokSu(b.a) > 30 { return "계속 직진하세요." }
        return "직진 \(georeum(b.a)) 뒤 \(b.mal)."
    }

    // MARK: 도착

    private func dochak(munAp: String? = nil) {
        guard !dochakHam else { return }
        // 2.11.0 이어진 길의 가운데 구간 끝 — 알리고 곧장 다음 구간으로(손을 쓰지 않게)
        if jungganGugan {
            sseumNamgigi(kkeut: true)
            gilRaw = ""   // 다음 구간을 부르며 이 구간의 쓰임을 두 번 남기지 않게
            let nam = ieum.count - ieumIdx - 1
            ieumIdx += 1
            let da = ieum[ieumIdx]
            geumanSok(malHam: false)   // 2.12.0 이 구간을 닫아 다음 구간을 부르는 동안 헛도착·건너뜀이 없게
            SoriEngine.shared.sori(.doraom)
            mal("한 구간을 마쳤습니다. 남은 구간 \(nam)개, 이어서 안내합니다.")
            Girok.shared.namgi("jeom_ieum", ["id": da.id, "idx": ieumIdx])
            bulleoGeotgi(da.id, dwit: da.dwit, mok: ieumMok, ieumYuji: true)
            return
        }
        dochakHam = true
        if garikiBonaen != nil { WatchLink.shared.garikiBonae(nil); garikiBonaen = nil }   // 2.36.0 도착하면 손목 가리키기 쉼
        munOn = false
        ieogaJiugi()
        sseumNamgigi(kkeut: true)
        sigye?.invalidate(); sigye = nil
        umjik.stopAccelerometerUpdates()
        SoriEngine.shared.sori(.dochak)
        Jindong.dochak()
        YeojeongEngine.shared.danggyeBakkugi(.dochak)
        sangMal = munAp == nil ? "목적지에 닿았습니다." : "문 앞에 닿았습니다."
        let ap = !ieum.isEmpty ? "이어진 길을 모두 걸었습니다. " : ""
        if let t = munAp {
            mal(ap + t + " 따라 걷기를 마칩니다. 되돌아가시려면 되돌아가기 단추를 누르십시오.")
        } else {
            mal(ap + "목적지에 닿았습니다. 따라 걷기를 마칩니다. 되돌아가시려면 되돌아가기 단추를 누르십시오.")
        }
        Girok.shared.namgi("jeom_dochak", ["id": gilRaw, "mun": munAp != nil])
    }

    // MARK: 2.11.0 문까지(웹 munJunbi — 내 문 → 여러 번 확인된 문 → 한 번 찍힌 문)

    private func munJunbi() {
        munList = []; munSu = 0
        if !munOn { mun = nil; munIdx = 0 }
        guard pts.count >= 2, !jungganGugan else { return }
        let kk = pts[pts.count - 1]
        let hubo: [JeomMun] = NaeMun.shared.mokrok.compactMap { m in
            let d = WichiEngine.geori(kk.lat, kk.lon, m.lat, m.lon)
            guard d <= 60 else { return nil }
            return JeomMun(lat: m.lat, lon: m.lon, ireum: m.ireum.isEmpty ? "내 문" : m.ireum, saengMal: "", bang: m.bang, jarye: 0, d: d, geul: m.geul ?? [])
        }
        munSeugi(hubo)
        munBeon += 1
        let beon = munBeon
        Task {
            guard let o = await Nas.get("mun.php", [("a", "near")] + Jeomjido.jari(kk.lat, kk.lon) + [("r", "60")]) else { return }
            let rows = (o["list"] as? [[String: Any]]) ?? (o["rows"] as? [[String: Any]]) ?? []
            await MainActor.run {
                guard beon == self.munBeon, self.gil != nil else { return }
                var h = hubo
                for x in rows {
                    guard let la = Chatgi.su(x["lat"]), let lo = Chatgi.su(x["lon"]), la != 0 else { continue }
                    if h.contains(where: { WichiEngine.geori($0.lat, $0.lon, la, lo) < 3 }) { continue }
                    let nm = Nas.gul(x["ireum"]).trimmingCharacters(in: .whitespaces)
                    h.append(JeomMun(lat: la, lon: lo, ireum: nm.isEmpty ? "문" : nm, saengMal: Nas.gul(x["saengMal"]),
                                     bang: Chatgi.su(x["bang"]), jarye: (Chatgi.su(x["doo"]) ?? 0) > 0 ? 1 : 2,
                                     d: WichiEngine.geori(kk.lat, kk.lon, la, lo)))
                }
                self.munSeugi(h)
            }
        }
    }

    private func munSeugi(_ h: [JeomMun]) {
        let s = h.sorted { Double($0.jarye) * 1000 + $0.d < Double($1.jarye) * 1000 + $1.d }
        munList = s
        munSu = s.count
        if !munOn { munIdx = 0; mun = s.first }
    }

    /// 문은 몇 시 방향, 몇 걸음 — 방향을 모르면 걸어 보시라고
    private func munMal(_ MU: JeomMun) -> String {
        guard let m = me else { return "" }
        let nm = MU.ireum
        let d = WichiEngine.geori(m.0, m.1, MU.lat, MU.lon)
        guard let h = jigeumHead else {
            return "\(nm)까지 \(georeum(d))입니다. 폰을 앞으로 든 채 한두 걸음 걸으시면 방향을 알려 드립니다."
        }
        var t = "\(nm)\(MalHagi.eun(nm)) \(sigyeGak(chai(WichiEngine.bangwi(m.0, m.1, MU.lat, MU.lon), h))), \(georeum(d))입니다."
        if let b = MU.bang { t += " 들어가는 쪽은 \(sigyeGak(chai(b, h)))입니다." }
        return t
    }

    /// 다른 문으로
    func dareunMun() {
        guard munOn, munList.count > 1 else { mal("다른 문이 없습니다."); return }
        munIdx = (munIdx + 1) % munList.count
        let MU = munList[munIdx]
        mun = MU
        munGakkaum = nil
        mal("다른 문으로 바꿉니다. " + munMal(MU))
    }

    // MARK: 2.11.1 이 길목은 어떻게 생겼습니까, 가까운 버스 정류장, 지나는 곳

    func gilmok() {
        guard let jj = jigeumJari() else { mal("지금 자리를 잡는 중입니다. 잠시만 기다려 주십시오."); return }
        let la = jj.0, lo = jj.1
        mal("길목을 살펴보는 중입니다.", .jeongbo)
        let h = Int((jigeumHead ?? 0).rounded())
        Task {
            let o = await Nas.get("ppyeodae.php", [("a", "gakkaun")] + Jeomjido.jari(la, lo) + [("head", String(h))])
            await MainActor.run {
                guard let o = o, (o["ok"] as? Bool) ?? false else { self.mal("길목을 살펴보지 못했습니다."); return }
                self.mal(Nas.gul(o["mal"]) + " " + Nas.gul(o["aljjik"]))
            }
        }
    }

    func beoseuJeongryujang() {
        guard let jj = jigeumJari() else { mal("지금 자리를 잡는 중입니다. 잠시만 기다려 주십시오."); return }
        let la = jj.0, lo = jj.1
        mal("가까운 버스 정류장을 찾는 중입니다.", .jeongbo)
        let h = Int((jigeumHead ?? 0).rounded())
        Task {
            let o = await Nas.get("beoseu.php", [("a", "gakkaun")] + Jeomjido.jari(la, lo) + [("head", String(h)), ("myeot", "3")])
            await MainActor.run {
                guard let o = o, (o["ok"] as? Bool) ?? false else { self.mal("정류장을 찾지 못했습니다."); return }
                self.mal(Nas.gul(o["mal"]) + " " + Nas.gul(o["aljjik"]))
            }
        }
    }

    private func jigeumJari() -> (Double, Double)? {
        if let m = me { return m }
        if let w = WichiEngine.shared.jigeum { return (w.lat, w.lon) }
        return nil
    }

    private func juwiMalhagi(_ la: Double, _ lo: Double) {
        guard Seoljeong.shared.gilOn else { return }
        let now = Date()
        let pilyo = juwiCenter.map { WichiEngine.geori($0.0, $0.1, la, lo) > juwiBan * 0.6 } ?? true
        if (pilyo || juwiRows.isEmpty) && !juwiBadneun && now.timeIntervalSince(juwiBatT) > 15 {
            juwiBadneun = true
            juwiBatT = now
            var c = (la, lo), r = 300.0
            if let e = pts.last {
                c = ((la + e.lat) / 2, (lo + e.lon) / 2)
                r = min(900, max(150, (WichiEngine.geori(la, lo, e.lat, e.lon) / 2).rounded() + 150))
            }
            juwiCenter = c
            juwiBan = r
            Task {
                let o = await Nas.get("juwi2.php", Jeomjido.jari(c.0, c.1) + [("ban", String(Int(r)))])
                await MainActor.run {
                    self.juwiBadneun = false
                    guard let rows = o?["rows"] as? [[String: Any]] else { return }
                    self.juwiRows = rows.compactMap { x -> (ireum: String, jong: String, lat: Double, lon: Double, wi: Bool)? in
                        guard let a = Chatgi.su(x["lat"]), let b = Chatgi.su(x["lon"]) else { return nil }
                        return (Nas.gul(x["ireum"]), Nas.gul(x["jong"]), a, b, (x["wi"] as? Bool) ?? ((Chatgi.su(x["wi"]) ?? 0) > 0))
                    }
                }
            }
        }
        guard !juwiRows.isEmpty, now.timeIntervalSince(juwiEonje) >= 8, now.timeIntervalSince(malT) >= 3 else { return }
        if let j = juwiJari, WichiEngine.geori(j.0, j.1, la, lo) < 15 { return }
        let head = jigeumHead
        var wi: [(String, String)] = [], ap: [(String, String)] = [], oreun: [(String, String)] = [], oen: [(String, String)] = [], gakkai: [(String, String)] = []
        for r in juwiRows {
            let key = "\(r.ireum)|\(r.lat)|\(r.lon)"
            if juwiHan.contains(key) { continue }
            if WichiEngine.geori(la, lo, r.lat, r.lon) > 25 { continue }
            var rel: Double?
            if let h = head {
                let x = chai(WichiEngine.bangwi(la, lo, r.lat, r.lon), h)
                if abs(x) > 150 { continue }   // 뒤에 있는 것은 말하지 않음
                rel = x
            }
            juwiHan.insert(key)
            let nm = JeomEngine.juwiIreum(r.ireum)
            if r.wi { wi.append((nm, r.jong)) }
            else if let x = rel { if abs(x) <= 30 { ap.append((nm, r.jong)) } else if x > 0 { oreun.append((nm, r.jong)) } else { oen.append((nm, r.jong)) } }
            else { gakkai.append((nm, r.jong)) }
        }
        var jul: [String] = []
        if !wi.isEmpty { jul.append("조심하십시오. " + JeomEngine.juwiMukkgi(wi) + "입니다.") }
        if !oreun.isEmpty { jul.append("3시 방향에 " + JeomEngine.juwiMukkgi(oreun) + "입니다.") }
        if !oen.isEmpty { jul.append("9시 방향에 " + JeomEngine.juwiMukkgi(oen) + "입니다.") }
        if !ap.isEmpty { jul.append("앞에 " + JeomEngine.juwiMukkgi(ap) + "입니다.") }
        if !gakkai.isEmpty { jul.append("가까이 " + JeomEngine.juwiMukkgi(gakkai) + "입니다.") }
        guard !jul.isEmpty else { return }
        juwiEonje = now
        juwiJari = (la, lo)
        mal(jul.joined(separator: " "), .jeongbo)
    }

    /// 한글 이름 뒤에 붙은 영문 이름은 뗌
    static func juwiIreum(_ nm: String) -> String {
        let t = nm.trimmingCharacters(in: .whitespaces)
        guard t.range(of: "[가-힣]", options: .regularExpression) != nil else { return t }
        let x = t.replacingOccurrences(of: "\\s+[A-Za-z][A-Za-z0-9 .,'&\\-]*$", with: "", options: .regularExpression)
        return x.isEmpty ? t : x
    }

    /// 같은 갈래끼리 묶어 — "식당 3곳입니다. 가, 나, 다"
    static func juwiMukkgi(_ l: [(String, String)]) -> String {
        var cha: [String] = []
        var moum: [String: [String]] = [:]
        for (nm, jong) in l {
            if moum[jong] == nil { cha.append(jong); moum[jong] = [] }
            moum[jong]!.append(nm)
        }
        return cha.map { k -> String in
            let nn = moum[k] ?? []
            if nn.count == 1 { return k.isEmpty ? nn[0] : k + " " + nn[0] }
            return (k.isEmpty ? "" : k + " ") + "\(nn.count)곳입니다. " + nn.joined(separator: ", ")
        }.joined(separator: ". ")
    }

    // MARK: 2.11.1 앱이 꺼졌다 켜져도 이어 걷기 — 걷던 길을 폰에 적어 둠(3시간 안이면 이어 감)

    private struct IeogaGirok: Codable {
        let id: String
        let dwit: Bool
        let mok: Jangso?
        let ieum: [JeomGugan]
        let ieumIdx: Int
        let ttae: Date
    }
    private let ieogaKi = "gn.jeomIeoga"

    private func ieogaJeojang(_ id: String, _ dw: Bool, _ mok: Jangso?) {
        let g = IeogaGirok(id: id, dwit: dw, mok: mok ?? ieumMok, ieum: ieum, ieumIdx: ieumIdx, ttae: Date())
        if let d = try? JSONEncoder().encode(g) { UserDefaults.standard.set(d, forKey: ieogaKi) }
    }

    private func ieogaJiugi() { UserDefaults.standard.removeObject(forKey: ieogaKi) }

    /// 앱이 켜질 때 — 여정이 걷는 중이고 3시간 안에 걷던 점지도가 있으면 그 길로 이어 감
    func ieoGagi() {
        guard let d = UserDefaults.standard.data(forKey: ieogaKi),
              let g = try? JSONDecoder().decode(IeogaGirok.self, from: d) else { return }
        guard Date().timeIntervalSince(g.ttae) < 3 * 3600,
              let y = YeojeongEngine.shared.jigeum, y.danggye == .namEunGil else { ieogaJiugi(); return }
        // 2.12.6 걷던 점지도의 목적지가 지금 여정의 목적지와 다르면 잇지 않음(옛 길이 새 목적지를 덮어쓰던 것)
        if let m = g.mok, WichiEngine.geori(m.lat, m.lon, y.mokjeok.lat, y.mokjeok.lon) > 50 {
            ieogaJiugi()
            Girok.shared.namgi("jeom_ieoga_an", ["id": g.id])
            return
        }
        ieum = g.ieum
        ieumIdx = min(g.ieumIdx, max(0, g.ieum.count - 1))
        ieumMok = g.mok
        Girok.shared.namgi("jeom_ieoga", ["id": g.id])
        SoriEngine.shared.mal("하던 점지도 따라 걷기를 이어 갑니다.")
        bulleoGeotgi(g.id, dwit: g.dwit, mok: g.mok, ieumYuji: true)
    }

    // MARK: 2.11.0 함께 시험하기(곁의 자봉이 번호로 따라 봄)

    func hamkkeNureum() {
        if let b = hamkkeBunho {
            hamkkeKkeut(b)
            hamkkeBunho = nil
            mal("함께 시험을 마쳤습니다.")
            return
        }
        guard gil != nil else { mal("먼저 걸으실 길을 골라 따라 걷기를 시작해 주십시오. 그 뒤에 번호를 받으실 수 있습니다."); return }
        let id = gilRaw.replacingOccurrences(of: "|r", with: "")
        let mok = YeojeongEngine.shared.jigeum?.mokjeok.ireum ?? (gil?.to ?? "")
        let body: [String: Any] = ["gil": id, "dwit": dwit ? 1 : 0, "mok": mok]
        let sd = sedae
        Task {
            let o = await Nas.postJson("hamkke.php", [("a", "yeol")], body)
            await MainActor.run {
                if sd != self.sedae || self.gil == nil {   // 그사이 그만두셨으면 받은 번호를 닫음
                    if let o = o, (o["ok"] as? Bool) ?? false { self.hamkkeKkeut(Nas.gul(o["bunho"])) }
                    return
                }
                guard let o = o, (o["ok"] as? Bool) ?? false else { self.mal("번호를 받지 못했습니다. 잠시 뒤에 다시 눌러 주십시오."); return }
                let b = Nas.gul(o["bunho"])
                self.hamkkeBunho = b
                self.hamkkeT = .distantPast
                self.mal("함께 시험 번호는 \(b.map { String($0) }.joined(separator: " "))입니다. 곁의 자봉께 알려 주십시오.")
            }
        }
    }

    private func hamkkeKkeut(_ b: String) {
        Task { _ = await Nas.postJson("hamkke.php", [("a", "kkeut")], ["bunho": b]) }
    }

    private func hamkkeBonaegi() {
        guard let b = hamkkeBunho, Date().timeIntervalSince(hamkkeT) >= 2, !pts.isEmpty else { return }
        hamkkeT = Date()
        var mi = -1, mn = ""
        for p in pyo where p.i >= idx && (mi < 0 || p.i < mi) { mi = p.i; mn = p.p.ireum }
        if mi < 0 { mi = pts.count - 1; mn = "도착" }
        let body: [String: Any] = ["bunho": b, "tc": Int(Date().timeIntervalSince1970 * 1000), "idx": idx, "mi": mi, "mn": mn,
                                   "ws": ap(idx, mi), "su": S.stepSu, "bo": bocok, "acc": acc,
                                   "mal": String(SoriEngine.shared.majimak.prefix(100))]
        Task { _ = await Nas.postJson("hamkke.php", [("a", "sang")], body) }
    }

    // MARK: 걸린 자리(geollim.js)

    private func geollimSalpigi(_ la: Double, _ lo: Double) {
        if let j = geollimJari, WichiEngine.geori(j.0, j.1, la, lo) < 30 { return }
        geollimJari = (la, lo)
        Task {
            guard let o = await Nas.get("makhim.php", Jeomjido.jari(la, lo) + [("ban", "120")]),
                  let x = (o["rows"] as? [[String: Any]])?.first else { return }
            let m = Int(Chatgi.su(x["meter"]) ?? 999)
            guard m <= 60 else { return }
            let key = String(format: "%.4f_%.4f", Chatgi.su(x["lat"]) ?? 0, Chatgi.su(x["lon"]) ?? 0)
            await MainActor.run {
                guard self.gil != nil, !self.geollimHan.contains(key), Date().timeIntervalSince(self.geollimT) >= 15 else { return }
                self.geollimHan.insert(key)
                self.geollimT = Date()
                self.mal("\(m)미터 앞. " + Nas.gul(x["mal"]))
            }
        }
    }

    /// 여기 걸렸어요 — 다음 분께 알려 주기
    func geollimNamgigi() {
        guard let w = WichiEngine.shared.jigeum else { mal("지금 자리를 잡는 중입니다. 잠시 뒤에 다시 눌러 주십시오."); return }
        Task {
            let o = await Nas.get("jeom_db.php", [("a", "makhim"), ("kind", "beoseonam")] + Jeomjido.jari(w.lat, w.lon))
            await MainActor.run {
                if (o?["ok"] as? Bool) ?? false {
                    self.mal("여기가 걸리는 자리라고 남겼습니다. 다음에 오시는 분께 미리 알려 드리겠습니다. 고맙습니다.")
                } else {
                    self.mal("남기지 못했습니다. 잠시 뒤에 다시 해 주십시오.")
                }
            }
        }
    }

    private func makhimNamgigi(_ la: Double, _ lo: Double, _ d: Double) {
        let now = Date()
        guard now.timeIntervalSince(makhimT) >= 30 else { return }
        makhimT = now
        Task { _ = await Nas.get("jeom_db.php", [("a", "makhim"), ("kind", "beoseonam")] + Jeomjido.jari(la, lo) + [("dist", String(Int(d.rounded())))]) }
    }

    private func sseumNamgigi(kkeut: Bool) {
        guard !gilRaw.isEmpty, !gilRaw.hasPrefix("nae_") else { return }
        let secs = Int(Date().timeIntervalSince(sijakT))
        var q: [(String, String)] = [("a", "sseum"), ("gil", gilRaw), ("kind", "sigak"), ("secs", String(secs)),
                                     ("off", String(beoseoSu)), ("kkeut", kkeut ? "1" : "0")]
        if let m = me { q += Jeomjido.jari(m.0, m.1) }
        Task { _ = await Nas.get("jeom_db.php", q) }
    }

    /// 함께 시험을 마쳐 확인된 점지도인가(hwakin_gil.json)
    static func hwakinDoen(_ id: String) async -> Bool {
        guard let o = await Nas.get("hwakin_gil.json", []) else { return false }
        return ((o["gil"] as? [Any]) ?? []).contains { Nas.gul($0) == id }
    }

    // MARK: 셈과 말

    private func mal(_ t: String, _ g: MalGeup = .annae) {
        SoriEngine.shared.mal(t, g)
        malT = Date()
    }

    /// 확신음 — 말하는 동안에는 쉬어 말과 겹치지 않게(벗어남 경고음은 늘)
    private func eum(_ j: SoriJong) {
        guard Seoljeong.shared.hwaksinEum else { return }
        if j != .beoseo && SoriEngine.shared.bappeum { return }
        SoriEngine.shared.sori(j)
    }

    private func chai(_ a: Double, _ b: Double) -> Double {
        (a - b + 540).truncatingRemainder(dividingBy: 360) - 180
    }

    private func gakkaunJeom(_ la: Double, _ lo: Double) -> Int {
        var b = 0, bd = 1e9
        for (k, p) in pts.enumerated() {
            let d = WichiEngine.geori(la, lo, p.lat, p.lon)
            if d < bd { bd = d; b = k }
        }
        return b
    }

    /// 점지도를 따라 앞으로 몇 미터(from 점에서 to 점까지)
    private func ap(_ from: Int, _ to: Int) -> Double {
        guard to > from, from >= 0, to < nu.count else { return 0 }
        return nu[to] - nu[from]
    }

    private func bocokSu(_ m: Double) -> Int { max(1, Int((m / bocok).rounded())) }

    /// 미터 → "스무 걸음", 아흔아홉 걸음이 넘으면 "약 80미터"
    private func georeum(_ m: Double) -> String {
        // 2.50.0 보폭을 재기 전에는 걸음 수 대신 미터로(틀린 걸음 수보다 안전, 이사장님 승인)
        if !Seoljeong.shared.bopokJaem { return "약 \(max(1, Int(m.rounded())))미터" }
        let n = bocokSu(m)
        if let g = JeomEngine.goyu(n) { return g + " 걸음" }
        return "약 \(Int(m.rounded()))미터"
    }

    static func goyu(_ n0: Int) -> String? {
        let n = max(1, n0)
        guard n <= 99 else { return nil }
        let il = ["", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉"]
        let sip = ["", "열", "스물", "서른", "마흔", "쉰", "예순", "일흔", "여든", "아흔"]
        let s = n / 10, i = n % 10
        if s == 2 && i == 0 { return "스무" }
        return sip[s] + il[i]
    }

    static func georeumSu(_ n: Int) -> String {
        n <= 10 ? (goyu(n) ?? "\(n)") + " 걸음" : "\(n)걸음"
    }

    private func georiMal(_ m: Double) -> String {
        if m < 3 { return "바로 앞" }
        return "\(Int(m.rounded()))미터, 약 \(bocokSu(m))걸음"
    }

    /// 꺾는 각도(오른쪽이 +) → "3시 방향"
    /// 2.38.0 돌 쪽 각도를 시계 숫자로(진동 무늬 고르기)
    private func sigyeSu(_ d: Double) -> Int {
        var h = Int((d / 30).rounded())
        if h <= 0 { h += 12 }
        if h > 12 { h -= 12 }
        if abs(d) > 150 { h = 6 }
        return h
    }

    private func sigyeGak(_ d: Double) -> String {
        var h = Int((d / 30).rounded())
        if h <= 0 { h += 12 }
        if h > 12 { h -= 12 }
        if abs(d) > 150 { h = 6 }
        return "\(h)시 방향"
    }

    private func siMal(_ la: Double, _ lo: Double) -> String {
        guard let h = jigeumHead, let m = me else { return "" }
        return ", \(WichiEngine.sigyeBanghyang(jeongmyeon: h, mokpyo: WichiEngine.bangwi(m.0, m.1, la, lo)))시 방향"
    }
}
