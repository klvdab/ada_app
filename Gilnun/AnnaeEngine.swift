// 안내 엔진 — 여정에 맞춰 길눈이 스스로 말합니다. 화면이 무엇이든, 폰이 잠겨도, 음악이 나와도 이 엔진이 돕니다.
// 2026-09-28 아침 길에서 드러난 일(화면이 잠기며 안내가 멈춤, 음악을 틀자 도착을 알아챌 주인이 없어짐)을 뿌리부터 막으려고,
// 안내는 화면이 아니라 이 엔진이 맡습니다.
//   걷기: 남은 거리와 시계 방향, 가까워질수록 자주, 방향이 틀어지면 바로, 제대로 가면 확신음, 사거리 알림, 곧 도착, 도착
//   차 안: 남은 거리 눈금(5·3·2·1킬로미터, 500·300·150미터), 지나는 길과 동네, 3분 넘게 말이 없으면 남은 거리
//   저절로 바꾸기: 빠르게 움직이면 곧장 차 안 안내로(묻지 않음), 목적지 가까이서 멈추고 걷기 시작하면 걷는 안내로
// 손을 쓰지 않아도 되게 하는 것이 원칙입니다(한 손에 지팡이, 한 손에 짐).
import Foundation
import Combine
import UIKit

enum Annae {
    static func geoMal(_ d: Double) -> String {
        if d >= 995 {
            let k = (d / 100).rounded() / 10
            return k == k.rounded() ? "\(Int(k))킬로미터" : String(format: "%.1f킬로미터", k)
        }
        if d >= 100 { return "\(Int((d / 10).rounded()) * 10)미터" }
        return "\(max(1, Int((d / 5).rounded()) * 5))미터"
    }

    static func sigyeMal(_ s: Int) -> String { s == 0 ? "" : ", \(s)시 방향" }

    static func sigyeCha(_ a: Int, _ b: Int) -> Int {
        let c = abs(a - b) % 12
        return min(c, 12 - c)
    }
}

final class AnnaeEngine: ObservableObject {
    static let shared = AnnaeEngine()

    @Published private(set) var namEunGeori: Double?

    private var ssak = Set<AnyCancellable>()
    private var majimakMal = Date.distantPast
    private var majimakGeoriMal: Double?
    private var majimakSigye = 0
    private var gotMal = false
    private var chaGeori = Set<Int>()
    private(set) var majimakGil = ""
    private var majimakDong = ""
    private var gilMuleun = Date.distantPast
    private var gilMutneunJung = false
    private var neagori: [Neagori] = []
    private var neagoriJari: (Double, Double)?
    private var neagoriBatneunJung = false
    private var malHanNeagori: [String: Date] = [:]
    private var neurinSijak: Date?
    private var neurinGeoreum = 0
    private var hwaksinTtae = Date.distantPast
    private var naonGijun: Int?
    private var gilMalTtae = Date.distantPast

    private var yj: YeojeongEngine { YeojeongEngine.shared }

    init() {
        WichiEngine.shared.saeWichi
            .receive(on: DispatchQueue.main)
            .sink { [weak self] w in self?.wichiBatda(w) }
            .store(in: &ssak)
        YeojeongEngine.shared.$sokdoChujeong
            .removeDuplicates()
            .dropFirst()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] t in self?.talgeotBakkwim(t) }
            .store(in: &ssak)
    }

    // MARK: 이용자가 누르는 일

    /// 2.12.6 새 목적지를 정하기 전에 하던 안내를 조용히 모두 끔 — 걷던 점지도, 지하철 안내, 이어 걷기 기록(이사장님 승인 1)
    func saeMokjeokJunbi() {
        JeomEngine.shared.yeojeongKkeut()
        JihacheolEngine.shared.meomchugi()
        namEunGeori = nil
    }

    /// 2.12.6 하던 일 멈추기 — 안내, 따라 걷기, 묻던 말, 신호기 찾기, 길눈의 말을 모두 멈추고 첫 화면으로(음악·방송은 그대로)
    func haneunIlMeomchum() {
        if DoeEngine.shared.sangtae == .annae { DoeEngine.shared.annaeGeuman() }   // 2.13.0 되짚어 나가기 안내도 멈춤(기억한 길은 그대로)
        JeomEngine.shared.yeojeongKkeut()
        JihacheolEngine.shared.meomchugi()
        yj.kkeut()
        namEunGeori = nil
        dasiSijak()
        MalHagi.shared.mureumChoGihwa()
        SinhogiEngine.shared.chatgiKkeugi()
        MunChatgi.shared.kkeugi(malHagi: false)   // 2.15.0 문 찾기도 멈춤
        GeulIlgi.shared.kkeugi(malHagi: false)    // 2.16.0 즉석 글자 읽기도 멈춤
        GarikiIlgi.shared.kkeugi(malHagi: false)  // 2.17.0 가리키고 말하기도 멈춤
        SaramGamji.shared.kkeugi(malHagi: false)  // 2.18.0 사람 감지도 멈춤
        SangpumIlgi.shared.kkeugi(malHagi: false) // 2.20.0 상품 바코드 읽기도 멈춤
        JipyeSaek.shared.kkeugi(malHagi: false)  // 2.21.0
        BitAlgi.shared.kkeugi(malHagi: false)  // 2.22.0
        Hanmadi.shared.kkeugi(malHagi: false)  // 2.23.0
        TeokAllim.shared.kkeugi(malHagi: false)  // 2.24.0
        GanpanAllim.shared.kkeut()  // 2.25.0
        SoriEngine.shared.modu_geodugi()
        TabGil.shared.tab = 0
        GilGil.shared.cheotHwamyeon()
        Girok.shared.namgi("haneunil_meomchum", [:])
        SoriEngine.shared.mal("하던 일을 멈췄습니다. 어디로 가실까요?")
    }

    /// 목적지를 정하고 걸어가기
    func georeoGagi(_ j: Jangso) {
        saeMokjeokJunbi()
        yj.jeonghagi(Mokjeok(ireum: j.ireum, lat: j.lat, lon: j.lon, juso: j.juso))
        georeoGagi()
    }

    /// 목적지를 정하고 차에 탐
    func chaTagi(_ j: Jangso) {
        saeMokjeokJunbi()
        yj.jeonghagi(Mokjeok(ireum: j.ireum, lat: j.lat, lon: j.lon, juso: j.juso))
        chaTatda()
    }

    /// 목적지를 정하고 지하철로 — 먼저 타는 역 출구까지 걷는 안내
    func jihacheolGagi(_ j: Jangso, _ g: JihaGil, malEopsi: Bool = false) {
        JeomEngine.shared.yeojeongKkeut()
        JihacheolEngine.shared.meomchugi()
        yj.jeonghagi(Mokjeok(ireum: j.ireum, lat: j.lat, lon: j.lon, juso: j.juso))
        yj.jihaNoki(g)
        yj.talgeotJeonghagi(.jihacheol, barojabeum: true)
        yj.danggyeBakkugi(.taneunGotKkaji)
        dasiSijak()
        if malEopsi { return }
        malHagi("\(g.ipgu.ireum)까지 걷는 안내를 시작합니다.")
        jigeumBoda()
    }

    /// 목적지를 정하고 버스로 — 먼저 정류장까지 걷는 안내
    func beoseuGagi(_ j: Jangso, _ jr: Jeongryujang) {
        JeomEngine.shared.yeojeongKkeut()
        JihacheolEngine.shared.meomchugi()
        yj.jeonghagi(Mokjeok(ireum: j.ireum, lat: j.lat, lon: j.lon, juso: j.juso))
        yj.beoseuNoki(BeoseuGil(jeongryujang: jr, dochak: false))
        yj.talgeotJeonghagi(.beoseu, barojabeum: true)
        yj.danggyeBakkugi(.taneunGotKkaji)
        dasiSijak()
        malHagi("\(jr.ireum) 정류장까지 걷는 안내를 시작합니다.")
        jigeumBoda()
    }

    /// 버스에 탔습니다
    func beoseuTatda() {
        guard yj.jigeum != nil else { return }
        yj.talgeotJeonghagi(.beoseu, barojabeum: true)
        yj.danggyeBakkugi(.taneunJung)
        dasiSijak()
        malHagi("버스 안 안내를 시작합니다.")
        jigeumBoda()
    }

    /// 탈것 바로잡기 — 바로잡은 것이 가장 앞섬
    func talgeotBarojapgi(_ t: Talgeot) {
        guard yj.jigeum != nil else { return }
        // 2.44.0 (261004-I2, 2026-10-04 KTX 부산행, 이사장님 승인) — 전에는 이름표만 바꾸고 돌던 지하철 안내를 그대로 두어
        // 화면은 기차인데 말은 지하철이었음. 차·기차·고속버스로 바로잡으시면 지하철·버스 안내를 멈추고 곧바로 탄 안내로 넘어감
        guard t == .cha || t == .gicha || t == .gosokbeoseu else {
            yj.talgeotBarojapgi(t)
            malHagi("\(t.ireum)로 알겠습니다.")
            return
        }
        JeomEngine.shared.yeojeongKkeut()
        JihacheolEngine.shared.meomchugi()
        yj.jihaNoki(nil)
        yj.beoseuNoki(nil)
        yj.talgeotBarojapgi(t)
        yj.danggyeBakkugi(.taneunJung)
        dasiSijak()
        malHagi("\(t.ireum)로 알겠습니다. \(t.ireum) 안 안내를 시작합니다.")
        Girok.shared.namgi("barojapgi_tal", ["t": t.rawValue])
        jigeumBoda()
    }

    func georeoGagi() {
        guard yj.jigeum != nil else { return }
        if JeomEngine.shared.dochakHam { JeomEngine.shared.yeojeongKkeut() }   // 점지도로 닿은 뒤 다시 걸으시면 위성 안내로
        yj.talgeotJeonghagi(.georeum, barojabeum: false)
        yj.danggyeBakkugi(.namEunGil)
        dasiSijak()
        malHagi("걷는 안내를 시작합니다.")
        jigeumBoda()
    }

    func chaTatda() {
        guard yj.jigeum != nil else { return }
        JeomEngine.shared.yeojeongKkeut()
        JihacheolEngine.shared.meomchugi()
        yj.jihaNoki(nil)
        yj.beoseuNoki(nil)
        yj.talgeotJeonghagi(.cha, barojabeum: true)
        yj.danggyeBakkugi(.taneunJung)
        dasiSijak()
        malHagi("차 안 안내를 시작합니다.")
        jigeumBoda()
    }

    func naeryeotda(jadong: Bool = false, mal: String? = nil) {
        guard yj.jigeum != nil else { return }
        JihacheolEngine.shared.meomchugi()
        GanpanAllim.shared.kkeut()   // 2.25.0 창밖 간판 읽기 카메라 끔
        yj.talgeotJeonghagi(.georeum, barojabeum: false)
        yj.danggyeBakkugi(.namEunGil)
        dasiSijak()
        malHagi(mal ?? (jadong ? "차에서 내리신 것 같습니다. 남은 길을 걸어서 안내합니다." : "남은 길을 걸어서 안내합니다."))
        Girok.shared.namgi("naerim", ["jadong": jadong])
        jigeumBoda()
    }

    func kkeut() {
        JeomEngine.shared.yeojeongKkeut()   // 2.10.0 점지도 따라 걷기도 함께 마침
        JihacheolEngine.shared.meomchugi()
        yj.kkeut()
        namEunGeori = nil
        dasiSijak()
        malHagi("여정을 끝냈습니다.")
    }

    /// 지금 어떻게 가고 있습니까
    func hyeonhwang() {
        // 2.10.0 점지도를 따라 걷는 중이면 점지도의 남은 거리
        if JeomEngine.shared.georeoJung { JeomEngine.shared.jigeumEodi(); return }
        guard let y = yj.jigeum else { jigeumJari(); return }
        let mok = y.mokjeok.ireum
        if y.danggye == .dochak {
            malHagi("\(mok)에 도착했습니다. 여정을 끝내시려면 여정 끝내기를 누르십시오.")
            return
        }
        if let b = y.beoseu, y.danggye == .taneunGotKkaji {
            malHagi(b.dochak ? "\(b.jeongryujang.ireum) 정류장에 닿았습니다. 버스에 타시면 저절로 버스 안 안내로 바뀝니다."
                             : "\(b.jeongryujang.ireum) 정류장까지 걸어가는 중입니다.")
            return
        }
        if y.jiha != nil && (y.danggye == .taneunGotKkaji || y.danggye == .taneunJung) {
            malHagi(JihacheolEngine.shared.hyeonhwang())
            return
        }
        let geotna = (y.danggye != .taneunJung)
        var m = "\(mok)까지 " + (geotna ? "걸어서" : yj.talgeot.ireum + "로") + " 가는 중입니다."
        if let w = WichiEngine.shared.jigeum {
            let d = WichiEngine.geori(w.lat, w.lon, y.mokjeok.lat, y.mokjeok.lon)
            m += " 남은 거리 \(Annae.geoMal(d))"
            if geotna {
                m += Annae.sigyeMal(sigye(w, y)) + "."
            } else {
                m += "."
                if !majimakGil.isEmpty { m += " 지금 달리는 길은 \(majimakGil)입니다." }
            }
        } else {
            m += " 아직 위치를 잡는 중입니다."
        }
        malHagi(m)
    }

    func jigeumJari() {
        guard let w = WichiEngine.shared.jigeum else {
            malHagi("아직 위치를 잡는 중입니다. 잡히면 다시 눌러 주십시오.")
            return
        }
        malHagi("지금 자리를 알아보는 중입니다.", .jeongbo)
        Task {
            let o = await Chatgi.json("jarimal.php", ["lat": String(format: "%.6f", w.lat), "lon": String(format: "%.6f", w.lon)])
            let n = await Nalssi.shared.mal()   // 2.13.0 날씨 한 마디를 끝에(웹 길눈과 같이)
            await MainActor.run {
                self.malHagi(AnnaeEngine.jariMalMandeulgi(o, w) + (n.isEmpty ? "" : " 날씨는 " + n + "."))
            }
        }
    }

    /// 2.9.0 현 위치정보 말할 내용 — 설정에서 고른 것만(주소, 가까운 곳, 지번, 국가지점번호, 위성 오차)
    static func jariMalMandeulgi(_ o: [String: Any]?, _ w: Wichi) -> String {
        let s = Seoljeong.shared
        var t: [String] = []
        if let o = o {
            let juso = (o["juso"] as? String) ?? ""
            if s.jariJuso && !juso.isEmpty { t.append(juso) }
            if s.jariJibeon, let jb = o["jibeon"] as? String, !jb.isEmpty { t.append("지번 " + jb) }
            if s.jariGot {
                if let c = o["chulgu"] as? [String: Any], let nm = c["ireum"] as? String, !nm.isEmpty {
                    t.append("\(nm)에서 \(Int(Chatgi.su(c["meter"]) ?? 0))미터")
                } else if let c = o["gakkaun"] as? [String: Any], let nm = c["ireum"] as? String, !nm.isEmpty {
                    t.append("\(nm)에서 \(Int(Chatgi.su(c["meter"]) ?? 0))미터")
                }
            }
            if t.isEmpty && !s.jariJijeom && !s.jariOcha, let m = o["mal"] as? String, !m.isEmpty { t.append(m) }
        } else if !s.jariJijeom {
            t.append("지금 자리 이름을 받지 못했습니다. 통신이 끊겼을 수 있습니다")
        }
        if s.jariJijeom, let j = Jijeom.mal(w.lat, w.lon) { t.append(j) }
        if s.jariOcha { t.append("위성 오차 약 \(max(1, Int(w.ochae)))미터") }
        if t.isEmpty { t.append("말할 내용이 모두 꺼져 있습니다. 설정 탭의 현 위치정보 말할 내용에서 켜 주십시오") }
        return t.joined(separator: ". ") + "."
    }

    // MARK: 워치·이어폰 (2.6.0)

    /// 걸어서 가는 중인가 — 목적지까지 걷거나, 타는 곳까지 걷는 차례
    var geonneunJung: Bool {
        guard let y = yj.jigeum else { return false }
        if y.danggye == .namEunGil { return true }
        if y.danggye == .taneunGotKkaji { return true }
        return false
    }

    /// 다음 갈림길 — 가는 쪽(앞쪽 10시~2시) 200미터 안에서 가장 가까운 사거리·갈림길
    /// 2.41.0 (261003-W1, 이사장님 지시 "폰 앱이 헤매면 워치라도 정신을 차려야지") 워치 두 번 집기 한 번 — 지금 형편에 맞는 한마디
    /// 걸을 때는 다음 갈림길, 지하철에서는 지난 역과 남은 정거장, 차·버스에서는 남은 거리와 지금 지나는 고장, 여정이 없으면 지금 자리
    func watchJigeumMal() -> String {
        let tg = TalgeotGamji.shared
        if JeomEngine.shared.georeoJung { return JeomEngine.shared.daeumMuotMal() }
        guard let y = yj.jigeum else {
            let jy = JiyeokEngine.shared.majimak.map { "지금 계신 곳은 \($0.balmal) 쪽입니다." } ?? ""
            let ta = tg.chujeong == .georeum ? "" : " " + (tg.chujeong == .jihacheol ? "지하철을" : "\(tg.chujeong.ireum)를") + " 타고 계신 것으로 보입니다."
            return ("가시는 곳이 아직 없습니다. " + jy + ta).trimmingCharacters(in: .whitespaces)
        }
        if let g = y.jiha {
            switch y.danggye {
            case .taneunJung:
                if JihacheolEngine.shared.dolgo || g.kkeutnam { return JihacheolEngine.shared.hyeonhwang() }
            case .taneunGotKkaji:
                if let w = WichiEngine.shared.jigeum, !g.ipguDochak {
                    let d = WichiEngine.geori(w.lat, w.lon, g.ipgu.lat, g.ipgu.lon)
                    return "\(g.ipgu.ireum)까지 \(Annae.geoMal(d))\(Annae.sigyeMal(sigye(w, g.ipgu.lat, g.ipgu.lon)))."
                }
                return JihacheolEngine.shared.hyeonhwang()
            default: break
            }
        }
        if y.danggye == .taneunJung {
            guard let w = WichiEngine.shared.jigeum else { return "\(y.mokjeok.ireum)으로 가는 중입니다. 위치를 다시 잡는 중입니다." }
            let d = WichiEngine.geori(w.lat, w.lon, y.mokjeok.lat, y.mokjeok.lon)
            let gojang = JiyeokEngine.shared.majimak.map { " 지금 \($0.balmal) 쪽을 지나고 있습니다." } ?? ""
            return "\(y.mokjeok.ireum)까지 \(Annae.geoMal(d)) 남았습니다.\(gojang)"
        }
        return daeumGalrimMal()
    }

    func daeumGalrimMal() -> String {
        // 2.10.0 점지도를 따라 걷는 중이면 점지도의 다음 표시·꺾이는 곳
        if JeomEngine.shared.georeoJung { return JeomEngine.shared.daeumMuotMal() }
        guard let w = WichiEngine.shared.jigeum else { return "아직 위치를 잡는 중입니다." }
        neagoriBoda(w, malHam: false)   // 2.12.0 자료만 받고 따로 말하지 않음(대답과 겹치지 않게)
        var gakka: (Double, Neagori)?
        for n in neagori where !n.mal.isEmpty {
            let d = WichiEngine.geori(w.lat, w.lon, n.lat, n.lon)
            guard d > 8 && d < 200 else { continue }
            if w.banghyang >= 0 {
                let s = WichiEngine.sigyeBanghyang(jeongmyeon: w.banghyang, mokpyo: WichiEngine.bangwi(w.lat, w.lon, n.lat, n.lon))
                guard s == 12 || s == 11 || s == 1 || s == 10 || s == 2 else { continue }
            }
            if gakka == nil || d < gakka!.0 { gakka = (d, n) }
        }
        if let g = gakka { return "다음 갈림길. \(Annae.geoMal(g.0)) 앞, \(g.1.mal)입니다." }
        if let y = yj.jigeum {
            let d = WichiEngine.geori(w.lat, w.lon, y.mokjeok.lat, y.mokjeok.lon)
            return "앞쪽 가까이에는 갈림길 자료가 없습니다. \(y.mokjeok.ireum)까지 \(Annae.geoMal(d))\(Annae.sigyeMal(sigye(w, y)))."
        }
        return "앞쪽 가까이에는 갈림길 자료가 없습니다."
    }

    // MARK: 속

    private func malHagi(_ t: String, _ g: MalGeup = .annae) {
        SoriEngine.shared.mal(t, g)
        majimakMal = Date()
    }

    private func dasiSijak() {
        ttJiugi()   // 2.47.0
        bgJiugi()   // 2.48.0
        majimakGeoriMal = nil
        majimakSigye = 0
        gotMal = false
        chaGeori = []
        neurinSijak = nil
        naonGijun = nil
        majimakMal = .distantPast
        hwaksinTtae = Date()
    }

    private func jigeumBoda() {
        WichiEngine.shared.sijak()
        if let w = WichiEngine.shared.jigeum {
            wichiBatda(w)
        } else {
            malHagi("위치를 잡는 중입니다. 잡히면 바로 안내합니다.", .jeongbo)
        }
    }

    private func sigye(_ w: Wichi, _ y: Yeojeong) -> Int {
        sigye(w, y.mokjeok.lat, y.mokjeok.lon)
    }

    private func sigye(_ w: Wichi, _ lat: Double, _ lon: Double) -> Int {
        let bang = WichiEngine.bangwi(w.lat, w.lon, lat, lon)
        let apjjok = (w.banghyang >= 0 && w.sokdo > 0.8) ? w.banghyang : WichiEngine.shared.nachimban
        return apjjok >= 0 ? WichiEngine.sigyeBanghyang(jeongmyeon: apjjok, mokpyo: bang) : 0
    }

    private func talgeotBakkwim(_ t: Talgeot) {
        guard let y = yj.jigeum else { return }
        // 2.40.0 움직임 감지기로 알아챈 지하철·버스·걸음도 이어 받음
        if t == .jihacheol {
            // 2.44.0 (261004-I2, 이사장님 승인) — 기차·고속버스를 타고 가는 동안 터널에 들어가 위성·통신이 끊기면 땅속으로 보고
            // "지하철을 타신 것 같습니다"라며 기차 안내를 덮던 일(KTX 부산행). 타고 가는 중이면 지하철로 보지 않음. 차로 바로잡으신 때도 그대로
            if y.danggye == .taneunJung && (y.talgeot == .gicha || y.talgeot == .gosokbeoseu || yj.sokdoChujeong == .gicha) { return }
            if y.barojabeum && y.talgeot == .cha && y.danggye == .taneunJung { return }
            if y.barojabeum && y.talgeot == .beoseu && !TalgeotGamji.shared.jiha { return }   // 이용자가 버스로 바로잡으셨고 땅속이 아니면 그대로
            if let g = y.jiha {
                // 타는 역으로 가던 중(입구에 닿았든 못 닿았든) 열차가 움직임 — 탄 것으로 보고 역 알림
                if y.danggye == .taneunGotKkaji && !JihacheolEngine.shared.dolgo {
                    if !g.ipguDochak { JihacheolEngine.shared.ipguDochak() }
                    JihacheolEngine.shared.tatda(jadong: true)
                }
                return
            }
            guard y.danggye == .namEunGil || y.danggye == .eotteoke || (y.danggye == .taneunJung && y.talgeot != .beoseu) else { return }
            let mok = Jangso(ireum: y.mokjeok.ireum, juso: y.mokjeok.juso, lat: y.mokjeok.lat, lon: y.mokjeok.lon)
            malHagi("지하철을 타신 것 같습니다. 지하철 길을 찾습니다.")
            JihacheolEngine.shared.jungganSijak(mok) { [weak self] ok, mal in
                if ok { Girok.shared.namgi("jadong_jihacheol", [:]) }
                else { self?.malHagi(mal + " 차 안 안내로 잇습니다.") }
            }
            return
        }
        if t == .beoseu {
            guard y.beoseu == nil, y.jiha == nil, !y.barojabeum || y.talgeot == .cha else { return }
            guard y.danggye == .namEunGil || y.danggye == .eotteoke || y.danggye == .taneunJung else { return }
            yj.talgeotJeonghagi(.beoseu, barojabeum: false)
            yj.danggyeBakkugi(.taneunJung)
            dasiSijak()
            malHagi("정류장마다 서는 것을 보니 버스를 타신 것 같습니다. 버스 안 안내로 잇습니다.")
            Girok.shared.namgi("jadong_beoseu", ["gil": "umjigim"])
            jigeumBoda()
            return
        }
        guard t == .cha || t == .gicha else { return }
        // 버스 정류장에서 기다리다 빠르게 움직이면 버스에 타신 것
        if y.beoseu != nil && y.danggye == .taneunGotKkaji {
            yj.talgeotJeonghagi(.beoseu, barojabeum: true)
            yj.danggyeBakkugi(.taneunJung)
            dasiSijak()
            malHagi("버스가 움직이는 것 같습니다. 버스 안 안내를 시작합니다.")
            Girok.shared.namgi("jadong_beoseu", [:])
            jigeumBoda()
            return
        }
        guard y.danggye == .namEunGil || y.danggye == .eotteoke else { return }
        JeomEngine.shared.yeojeongKkeut()
        JihacheolEngine.shared.meomchugi()
        yj.jihaNoki(nil)
        yj.beoseuNoki(nil)
        yj.talgeotJeonghagi(t, barojabeum: false)
        yj.danggyeBakkugi(.taneunJung)
        dasiSijak()
        malHagi("빠르게 움직이고 계십니다. \(t == .gicha ? "기차" : "차") 안 안내로 바꿉니다.")
        Girok.shared.namgi("jadong_cha", ["t": t.rawValue])
        jigeumBoda()
    }

    private func wichiBatda(_ w: Wichi) {
        guard let y = yj.jigeum else { namEunGeori = nil; return }
        let d = WichiEngine.geori(w.lat, w.lon, y.mokjeok.lat, y.mokjeok.lon)
        namEunGeori = d
        // 2.10.0 점지도를 따라 걷는 동안(도착 뒤 포함)과 점지도로 걸을지 여쭙는 동안에는 점지도 엔진이 안내를 맡음
        // 2.12.0 점지도로 닿은 뒤에는 이 엔진이 다시 맡음(차를 타시거나 다시 걸으실 때 조용하지 않게)
        if JeomEngine.shared.georeoJung || JeomEngine.shared.muleum != nil || JeomEngine.shared.bulleoneun { return }
        if let b = y.beoseu, y.danggye == .taneunGotKkaji {
            let jr = b.jeongryujang
            if !b.dochak { georeumAnnae(w, ireum: jr.ireum + " 정류장", lat: jr.lat, lon: jr.lon, jungan: true, y) }
            return
        }
        if let g = y.jiha {
            switch y.danggye {
            case .taneunGotKkaji:
                if !g.ipguDochak { georeumAnnae(w, ireum: g.ipgu.ireum, lat: g.ipgu.lat, lon: g.ipgu.lon, jungan: true, y) }
            case .taneunJung:
                if g.kkeutnam { naonGeotBoda(w) }
            case .namEunGil:
                let t = bgJari(w, y.mokjeok.lat, y.mokjeok.lon, y.mokjeok.ireum)   // 2.48.0 볼거리 자리
                georeumAnnae(w, ireum: y.mokjeok.ireum, lat: t.0, lon: t.1, jungan: false, y)
            default:
                break
            }
            return
        }
        switch y.danggye {
        case .taneunJung: chaAnnae(w, d, y)
        case .namEunGil:
            let t = bgJari(w, y.mokjeok.lat, y.mokjeok.lon, y.mokjeok.ireum)   // 2.48.0 볼거리 자리
            georeumAnnae(w, ireum: y.mokjeok.ireum, lat: t.0, lon: t.1, jungan: false, y)
        default: break
        }
    }

    // MARK: 2.48.0 마지막 스무 걸음과 볼거리(이사장님 승인 2026-10-06)
    //   목적지 80미터 앞에서 자봉이 남긴 볼거리(팽나무·동상·안내판 등, 서버 lvd-jabong)를 받아, 있으면 그 정확한 자리로 이끎.
    //   스무 미터 안에서는 「다섯 걸음, 1시 방향」처럼 걸음 수로 좁혀 말하고, 닿으면 볼거리 이름과 만져지는 것을 알려 드림.
    private struct Bolgeori { let ireum: String; let mal: String; let lat: Double; let lon: Double }
    private var bgMok: (Double, Double)?
    private var bg: Bolgeori?
    private var bgBatneun = false
    private var magakMalTtae = Date.distantPast
    private var magakSu = -1
    private var magakSigye = 0

    private func bgJiugi() { bgMok = nil; bg = nil; magakSu = -1; magakSigye = 0 }

    /// 최종 목적지의 자리 — 가까이에 볼거리 기록이 있으면 그 자리
    private func bgJari(_ w: Wichi, _ lat: Double, _ lon: Double, _ ireum: String) -> (Double, Double) {
        if let m = bgMok, abs(m.0 - lat) > 0.000001 || abs(m.1 - lon) > 0.000001 { bgJiugi() }
        if bgMok == nil && !bgBatneun && WichiEngine.geori(w.lat, w.lon, lat, lon) <= 80 {
            bgBatneun = true
            bgMok = (lat, lon)
            let q = ["a": "bolgeori", "lat": String(format: "%.6f", lat), "lon": String(format: "%.6f", lon), "r": "40"]
            Task {
                let o = await Chatgi.json("/jabong/hamkke.php", q)
                await MainActor.run {
                    self.bgBatneun = false
                    let ls = ((o?["rows"] as? [[String: Any]]) ?? []).compactMap { r -> Bolgeori? in
                        guard let a = Chatgi.su(r["lat"]), let b = Chatgi.su(r["lon"]) else { return nil }
                        return Bolgeori(ireum: (r["ireum"] as? String) ?? "", mal: (r["mal"] as? String) ?? "", lat: a, lon: b)
                    }
                    let nm = ireum.replacingOccurrences(of: " ", with: "")
                    self.bg = ls.first { !$0.ireum.isEmpty && nm.contains($0.ireum.replacingOccurrences(of: " ", with: "")) }
                        ?? ls.first { WichiEngine.geori(lat, lon, $0.lat, $0.lon) <= 25 }
                    if let b = self.bg { Girok.shared.namgi("bolgeori_chajeum", ["ireum": b.ireum]) }
                }
            }
        }
        if let b = bg { return (b.lat, b.lon) }
        return (lat, lon)
    }

    /// 마지막 스무 미터 — 걸음 수와 시 방향(4초에 한 번, 바뀔 때만). 맡았으면 참
    private func magakAnnae(_ w: Wichi, _ d: Double, _ lat: Double, _ lon: Double, _ mok: String) -> Bool {
        guard d <= 20 else { return false }
        let bp = Seoljeong.shared.bopok > 0.3 ? Seoljeong.shared.bopok : 0.65
        let su = max(1, Int((d / bp).rounded()))
        let s = sigye(w, lat, lon)
        let now = Date()
        guard now.timeIntervalSince(magakMalTtae) >= 4 else { return true }
        if magakSu < 0 {
            magakMalTtae = now; magakSu = su; magakSigye = s
            malHagi("곧 도착합니다. \(bg?.ireum ?? mok)까지 \(su)걸음" + (s == 0 ? "." : ", \(s)시 방향."))
            if s != 0 { Jindong.banghyang(s) }
        } else if abs(su - magakSu) >= 2 || (s != 0 && s != magakSigye) {
            magakMalTtae = now; magakSu = su; magakSigye = s
            malHagi("\(su)걸음" + (s == 0 ? "." : ", \(s)시 방향."))
            if s != 0 { Jindong.banghyang(s) }
        }
        return true
    }

    // MARK: 2.47.0 걸을 수 있는 길로 이끌기(이사장님 승인 2026-10-06, 부산 에이펙 공원에서 「2시 방향」만으로는 찾아갈 수 없었던 일)
    //   리눅스 서버의 걷기 길찾기(lvd-gil, 나스 /jeom/gilchatgi.php 가 건네줌)로 걸을 수 있는 길을 받아,
    //   목적지가 아니라 「다음 꺾는 곳」을 겨누고 걸음마다 방향을 다시 셈함. 열 걸음쯤 앞에서 미리, 닿으면 지금 꺾으라고.
    //   길에서 크게 벗어나면 그 자리에서 다시 길을 찾고, 길을 못 받으면 예전처럼 곧은 방향으로 안내.
    private struct TtAn { let sign: Int; let lat: Double; let lon: Double }
    private var ttPts: [(Double, Double)] = []
    private var ttAn: [TtAn] = []
    private var ttI = 1
    private var ttMok: (Double, Double)?
    private var ttBatneun = false
    private var ttMotTtae = Date.distantPast
    private var ttBeoseo = 0
    private var ttYego = -1
    private var ttMalTtae = Date.distantPast

    private func ttJiugi() { ttPts = []; ttAn = []; ttI = 1; ttMok = nil; ttBeoseo = 0; ttYego = -1 }

    private func dolgiMal(_ sign: Int, jigeum: Bool) -> String {
        let k = jigeum ? "꺾으십시오" : "꺾습니다"
        let g = jigeum ? "가십시오" : "갑니다"
        switch sign {
        case -3, -2: return "왼쪽, 9시 방향으로 " + k
        case 2, 3: return "오른쪽, 3시 방향으로 " + k
        case -1: return "11시 방향으로 비스듬히 " + g
        case 1: return "1시 방향으로 비스듬히 " + g
        case -7: return "갈림길에서 왼쪽 길로 " + g
        case 7: return "갈림길에서 오른쪽 길로 " + g
        case -98, 98: return "뒤로 돌아 " + g
        case 6, -6: return "둥근 길을 따라 " + g
        case 4: return jigeum ? "목적지 가까이입니다" : "목적지에 닿습니다"
        default: return "곧장 " + g
        }
    }

    private func dolgiSigye(_ sign: Int) -> Int {
        switch sign { case -3, -2, -7: return 9; case 2, 3, 7: return 3; case -1: return 11; case 1: return 1; case -98, 98: return 6; default: return 12 }
    }

    /// 지금 자리에서 걷는 길까지 몇 미터 떨어졌는지
    private func ttGeori(_ la: Double, _ lo: Double) -> Double {
        guard ttPts.count >= 2 else { return 0 }
        let kx = 111320 * cos(la * .pi / 180), ky = 110540.0
        var m = Double.greatestFiniteMagnitude
        for i in 0..<(ttPts.count - 1) {
            let ax = (ttPts[i].1 - lo) * kx, ay = (ttPts[i].0 - la) * ky
            let bx = (ttPts[i + 1].1 - lo) * kx, by = (ttPts[i + 1].0 - la) * ky
            let dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy
            var t = l2 > 0 ? -(ax * dx + ay * dy) / l2 : 0
            t = max(0, min(1, t))
            let px = ax + t * dx, py = ay + t * dy
            m = min(m, (px * px + py * py).squareRoot())
        }
        return m
    }

    private func ttDaeumMal(_ w: Wichi) -> String {
        guard ttI < ttAn.count else { return "" }
        let a = ttAn[ttI]
        let d = WichiEngine.geori(w.lat, w.lon, a.lat, a.lon)
        let s = sigye(w, a.lat, a.lon)
        return "\(Annae.geoMal(d)) 앞에서 \(dolgiMal(a.sign, jigeum: false))." + (s == 0 ? "" : " 그쪽은 \(s)시 방향입니다.")
    }

    private func ttBatgi(_ w: Wichi, _ lat: Double, _ lon: Double, apMal: String) {
        guard !ttBatneun, Date().timeIntervalSince(ttMotTtae) > 20 else { return }
        ttBatneun = true
        let q = ["slat": String(format: "%.6f", w.lat), "slon": String(format: "%.6f", w.lon),
                 "mlat": String(format: "%.6f", lat), "mlon": String(format: "%.6f", lon)]
        Task {
            let o = await Chatgi.json("gilchatgi.php", q)
            await MainActor.run {
                self.ttBatneun = false
                guard let o = o, (o["ok"] as? Bool) == true,
                      let pts = o["pts"] as? [[Double]], let an = o["an"] as? [[String: Any]], pts.count >= 2 else {
                    self.ttMotTtae = Date()
                    return
                }
                self.ttPts = pts.compactMap { $0.count >= 2 ? ($0[0], $0[1]) : nil }
                self.ttAn = an.compactMap { a in
                    guard let j = a["jeom"] as? [Double], j.count >= 2 else { return nil }
                    return TtAn(sign: Int(Chatgi.su(a["sign"]) ?? 0), lat: j[0], lon: j[1])
                }
                guard self.ttAn.count >= 2 else { self.ttJiugi(); self.ttMotTtae = Date(); return }
                self.ttI = 1
                self.ttMok = (lat, lon)
                self.ttYego = -1
                self.ttBeoseo = 0
                self.ttMalTtae = Date()
                let jeon = Chatgi.su(o["geori"]) ?? 0
                guard let w2 = WichiEngine.shared.jigeum else { return }
                self.malHagi(apMal + "걸을 수 있는 길로 안내합니다. 길 따라 \(Annae.geoMal(jeon)). " + self.ttDaeumMal(w2))
                Girok.shared.namgi("gil_ttara", ["m": Int(jeon), "an": self.ttAn.count])
            }
        }
    }

    /// 걷는 길을 따라 이끌기 — 맡았으면 참(받기 전·못 받았으면 거짓, 예전 곧은 방향 안내로)
    private func ttaraAnnae(_ w: Wichi, _ lat: Double, _ lon: Double) -> Bool {
        if let m = ttMok, abs(m.0 - lat) > 0.000001 || abs(m.1 - lon) > 0.000001 { ttJiugi() }
        if ttAn.isEmpty { ttBatgi(w, lat, lon, apMal: ""); return false }
        let now = Date()
        // 길에서 크게 벗어나면 그 자리에서 다시 찾기(세 번 잇달아 벗어났을 때만 — 위성 흔들림에 흔들리지 않게)
        if ttGeori(w.lat, w.lon) > max(25, w.ochae * 1.5) { ttBeoseo += 1 } else { ttBeoseo = 0 }
        if ttBeoseo >= 3 {
            ttJiugi()
            ttMotTtae = .distantPast
            ttBatgi(w, lat, lon, apMal: "길에서 벗어나신 것 같아 지금 자리에서 다시 길을 찾았습니다. ")
            return true
        }
        guard ttI < ttAn.count else { return false }
        let a = ttAn[ttI]
        let d = WichiEngine.geori(w.lat, w.lon, a.lat, a.lon)
        // 꺾는 곳에 닿음 — 지금 꺾으라고, 그다음 꺾는 곳으로 넘어감
        if d <= max(8, min(w.ochae, 15)) && ttI < ttAn.count - 1 {
            ttI += 1
            ttYego = -1
            ttMalTtae = now
            Jindong.banghyang(dolgiSigye(a.sign))
            malHagi("지금 \(dolgiMal(a.sign, jigeum: true)). 그다음은 " + ttDaeumMal(w))
            return true
        }
        // 열 걸음쯤 앞에서 미리
        if d <= 15 && ttYego != ttI && a.sign != 0 && a.sign != 4 {
            ttYego = ttI
            ttMalTtae = now
            malHagi("열 걸음쯤 앞에서 \(dolgiMal(a.sign, jigeum: false)).")
            return true
        }
        // 걷는 중 — 다음 꺾는 곳 쪽이 맞으면 확신음, 틀어졌으면 방향(20초에 한 번)
        if now.timeIntervalSince(ttMalTtae) >= 20 && now.timeIntervalSince(majimakMal) >= 6 {
            ttMalTtae = now
            let s = sigye(w, a.lat, a.lon)
            if s == 12 || s == 11 || s == 1 {
                if Seoljeong.shared.hwaksinEum { SoriEngine.shared.sori(.hwaksin) }
            } else if s != 0 {
                malHagi("길은 \(s)시 방향입니다. 다음 꺾는 곳까지 \(Annae.geoMal(d)).")
                Jindong.banghyang(s)
            }
        }
        return true
    }

    // MARK: 걷기

    private func georeumAnnae(_ w: Wichi, ireum mok: String, lat: Double, lon: Double, jungan: Bool, _ y: Yeojeong) {
        let d = WichiEngine.geori(w.lat, w.lon, lat, lon)
        let beom = jungan ? max(15, min(w.ochae, 30)) : (bg != nil ? max(5, min(w.ochae, 10)) : max(12, min(w.ochae, 25)))   // 2.48.0 볼거리면 더 가까이
        if d <= beom {
            if jungan {
                SoriEngine.shared.sori(.dochak)
                dasiSijak()
                if y.beoseu != nil { jeongryujangDochak() } else { JihacheolEngine.shared.ipguDochak() }
            } else {
                dochak(w, d, y)
            }
            return
        }
        // 2.48.0 마지막 스무 미터는 걸음 수와 시 방향으로 좁혀 말함
        if !jungan && magakAnnae(w, d, lat, lon, mok) { return }
        // 2.47.0 걸을 수 있는 길을 받았으면 그 길로 이끎(다음 꺾는 곳을 겨눔)
        if ttaraAnnae(w, lat, lon) { neagoriBoda(w); return }
        let s = sigye(w, lat, lon)
        let now = Date()
        let jinan = now.timeIntervalSince(majimakMal)
        neagoriBoda(w)
        if majimakGeoriMal == nil {
            malHagi("\(mok)까지 \(Annae.geoMal(d))\(Annae.sigyeMal(s)).")
            Jindong.banghyang(s)
            majimakGeoriMal = d
            majimakSigye = s
            return
        }
        if !gotMal && d <= 40 {
            gotMal = true
            malHagi("곧 도착합니다. \(mok)까지 \(Annae.geoMal(d))\(Annae.sigyeMal(s)).")
            majimakGeoriMal = d
            majimakSigye = s
            return
        }
        let st = Seoljeong.shared
        // 2.9.0 얼마나 자세히 — 자세히 그대로, 보통 1.5배, 짧게 2배 간격으로 / 되풀이 사이 시간(기본 6초 → 지금과 같은 10초)
        let bae: Double = st.malSang >= 2 ? 1 : (st.malSang == 1 ? 1.5 : 2)
        let gan: Double = (d > 300 ? 100 : (d > 100 ? 50 : 20)) * bae
        let doepul = Double(st.doepul)
        if let m = majimakGeoriMal {
            if m - d >= gan && jinan >= doepul + 4 {
                malHagi("\(mok)까지 \(Annae.geoMal(d))\(Annae.sigyeMal(s)).")
                majimakGeoriMal = d
                majimakSigye = s
                return
            }
            if d - m >= 30 && jinan >= doepul + 4 {
                malHagi("목적지에서 멀어지고 있습니다. \(mok) 쪽은\(s == 0 ? "" : " \(s)시 방향"), \(Annae.geoMal(d)).", .annae)
                Jindong.banghyang(s)
                majimakGeoriMal = d
                majimakSigye = s
                return
            }
        }
        if s != 0 && majimakSigye != 0 && Annae.sigyeCha(s, majimakSigye) >= 2 && jinan >= doepul + 2 {
            malHagi("\(mok) 쪽은 \(s)시 방향입니다.")
            Jindong.banghyang(s)
            majimakSigye = s
            return
        }
        // 걷는 중 입 다물지 않기 — 제대로 가면 25초마다 확신음, 틀어졌으면 방향
        if jinan >= 25 && now.timeIntervalSince(hwaksinTtae) >= 25 {
            hwaksinTtae = now
            if s == 12 || s == 11 || s == 1 {
                if st.hwaksinEum { SoriEngine.shared.sori(.hwaksin) }   // 2.9.0 설정에서 끔
            } else if s != 0 {
                malHagi("\(mok) 쪽은 \(s)시 방향입니다.")
                Jindong.banghyang(s)
                majimakSigye = s
            }
        }
    }

    /// 2.11.1 점지도를 따라 걷는 동안에도 사거리·갈림길을 알림(웹 ttara 의 Neagori.salpigi)
    func neagoriBakkeseo(_ w: Wichi) { neagoriBoda(w) }

    private func neagoriBoda(_ w: Wichi, malHam: Bool = true) {
        let badeulTtae: Bool
        if let j = neagoriJari {
            badeulTtae = WichiEngine.geori(j.0, j.1, w.lat, w.lon) > 400
        } else {
            badeulTtae = true
        }
        if badeulTtae && !neagoriBatneunJung {
            neagoriBatneunJung = true
            neagoriJari = (w.lat, w.lon)
            Task {
                let r = await Chatgi.neagori(w.lat, w.lon)
                await MainActor.run {
                    self.neagoriBatneunJung = false
                    if let r = r { self.neagori = r }
                }
            }
        }
        guard malHam, w.ochae <= 20, !w.georeumChu || w.ochae <= 15 else { return }
        // 2.9.0 꺾이는 곳 알리기(설정에서 끔), 몇 초 앞에서(걸음 초속 1.3미터로 셈, 기본 8초 → 10미터쯤)
        guard Seoljeong.shared.kkeokOn else { return }
        let ap = max(6, Double(Seoljeong.shared.kkeokCho) * 1.3)
        let now = Date()
        for n in neagori {
            guard !n.mal.isEmpty, WichiEngine.geori(w.lat, w.lon, n.lat, n.lon) <= ap else { continue }
            let k = String(format: "%.5f,%.5f", n.lat, n.lon)
            if malHanNeagori[k].map({ now.timeIntervalSince($0) > 300 }) ?? true {
                malHanNeagori[k] = now
                malHagi("\(n.mal)입니다.", .jeongbo)
                break
            }
        }
    }

    /// 버스 정류장에 닿음 — 오는 버스를 알려 드리고, 버스가 움직이면 저절로 버스 안 안내로
    private func jeongryujangDochak() {
        guard var b = yj.jigeum?.beoseu else { return }
        b.dochak = true
        yj.beoseuNoki(b)
        let jr = b.jeongryujang
        malHagi("\(jr.ireum) 정류장입니다. 버스에 타시면 저절로 버스 안 안내로 바뀝니다.")
        Girok.shared.namgi("beoseu_jeongryujang", [:])
        Task {
            let m = await Beoseu.douchak(jr)
            await MainActor.run { self.malHagi(m, .jeongbo) }
        }
    }

    /// 지하철에서 내린 뒤 — 위성이 다시 잡히고 스무 걸음 넘게 걸으셨으면 밖으로 나오신 것
    private func naonGeotBoda(_ w: Wichi) {
        let georeum = WichiEngine.shared.oneulGeoreum
        guard let gijun = naonGijun else { naonGijun = georeum; return }
        if !w.georeumChu && w.ochae <= 30 && georeum - gijun >= 20 {
            naeryeotda(mal: "밖으로 나오신 것 같습니다. 남은 길을 걸어서 안내합니다.")
        }
    }

    private func dochak(_ w: Wichi, _ d: Double, _ y: Yeojeong) {
        yj.danggyeBakkugi(.dochak)
        SoriEngine.shared.sori(.dochak)
        Jindong.dochak()
        let s = sigye(w, y)
        if let b = bg {   // 2.48.0 볼거리 — 이름과 만져지는 것
            malHagi("도착했습니다. \(b.ireum) 앞입니다." + (b.mal.isEmpty ? "" : " \(b.mal)"))
        } else {
            malHagi("도착했습니다. \(y.mokjeok.ireum)입니다\(s == 0 ? "" : ". \(s)시 방향 가까이에 있습니다").")
        }
        // 2.15.0 걸어서 닿으면 카메라로 문 찾기(2.16.0 모든 폰)
        if MunChatgi.gigiGaneung {
            DispatchQueue.main.asyncAfter(deadline: .now() + 4) { MunChatgi.shared.kyeogi("dochak") }
        }
        Girok.shared.namgi("dochak", ["m": Int(d), "ochae": Int(w.ochae)])
    }

    // MARK: 차 안

    private func chaAnnae(_ w: Wichi, _ d: Double, _ y: Yeojeong) {
        GanpanAllim.shared.chaAn(w)   // 2.25.0 차 안 간판 알림
        let mok = y.mokjeok.ireum
        let now = Date()
        let dan = [5000, 3000, 2000, 1000, 500, 300, 150]
        if majimakGeoriMal == nil {
            for g in dan where Double(g) >= d { chaGeori.insert(g) }
            malHagi("\(mok)까지 \(Annae.geoMal(d)) 남았습니다.")
            majimakGeoriMal = d
        } else {
            let saero = dan.filter { Double($0) >= d && !chaGeori.contains($0) }
            if let g = saero.min() {
                for x in saero { chaGeori.insert(x) }
                let beoseu = (yj.talgeot == .beoseu)
                if beoseu && g == 500 {
                    malHagi("\(mok)까지 \(Annae.geoMal(d)) 남았습니다. 버스 안내 방송을 잘 들으시고 내리실 준비를 하십시오.")
                } else if beoseu && g == 300 {
                    malHagi("곧 \(mok) 부근입니다. 다음 정류장에서 내리시면 됩니다. 남은 거리 \(Annae.geoMal(d)).")
                } else if g == 300 {
                    malHagi("곧 \(mok) 부근입니다. 내리실 준비를 하십시오. 남은 거리 \(Annae.geoMal(d)).")
                } else if g == 150 {
                    malHagi("\(mok) 부근입니다. 차에서 내려 걸으시면 저절로 걷는 안내로 이어 드립니다.")
                } else {
                    malHagi("\(mok)까지 \(Annae.geoMal(d)) 남았습니다.")
                }
            }
        }
        // 지나는 길과 동네
        if w.sokdo > 3 && !w.georeumChu && now.timeIntervalSince(gilMuleun) >= 20 && !gilMutneunJung {
            gilMuleun = now
            gilMutneunJung = true
            Task {
                let r = await Chatgi.gil(w.lat, w.lon)
                await MainActor.run {
                    self.gilMutneunJung = false
                    if let r = r { self.gilBoda(r.gil, r.dong) }
                }
            }
        }
        // 3분 넘게 말이 없으면 남은 거리 한 번
        if now.timeIntervalSince(majimakMal) >= 180 {
            malHagi("\(mok)까지 \(Annae.geoMal(d)) 남았습니다.", .jeongbo)
        }
        // 내림 알아채기 — 목적지 800미터 안에서 40초 넘게 멈추고, 그사이 열다섯 걸음 넘게 걸으셨으면
        if !w.georeumChu && w.sokdo < 2 && d < 800 {
            if neurinSijak == nil {
                neurinSijak = now
                neurinGeoreum = WichiEngine.shared.oneulGeoreum
            }
            if let t = neurinSijak, now.timeIntervalSince(t) >= 40,
               WichiEngine.shared.oneulGeoreum - neurinGeoreum >= 15 {
                naeryeotda(jadong: true)
            }
        } else if w.sokdo >= 2 {
            neurinSijak = nil   // 2.12.0 다시 달리면(초속 2미터 넘게) 내림 셈을 처음부터
        }
    }

    private func gilBoda(_ gil: String, _ dong: String) {
        // 2.9.0 지나는 곳 안내(설정에서 끔), 말하는 간격(기본 60초), 동네는 자세히에서만
        let st = Seoljeong.shared
        guard st.gilOn, Date().timeIntervalSince(gilMalTtae) >= Double(st.gilGap) else { return }
        let jeon = (majimakGil, majimakDong)
        defer { if (majimakGil, majimakDong) != jeon { gilMalTtae = Date() } }
        if !gil.isEmpty && gil != majimakGil {
            if majimakGil.isEmpty {
                malHagi("지금 달리는 길은 \(gil)입니다.", .jeongbo)
            } else {
                malHagi("이제 \(gil)에 들어섰습니다.", .jeongbo)
            }
            majimakGil = gil
        }
        if !dong.isEmpty && dong != majimakDong {
            if !majimakDong.isEmpty && st.malSang >= 2 { malHagi("\(dong)에 들어왔습니다.", .jeongbo) }
            majimakDong = dong
        }
    }
}
