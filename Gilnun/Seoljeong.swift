// 설정 — 폰 안에 담아 두는 이용자의 뜻
import Foundation

final class Seoljeong: ObservableObject {
    static let shared = Seoljeong()
    private let d = UserDefaults.standard

    /// 길눈이 스스로 내는 말소리(보이스오버와 화면 글자는 그대로). 경고는 꺼도 말합니다.
    @Published var malKyeojim: Bool { didSet { d.set(malKyeojim, forKey: "gn.malKyeojim") } }
    /// 말 빠르기 다섯 단
    @Published var bbareugiDan: Int { didSet { d.set(bbareugiDan, forKey: "gn.bbareugi") } }
    /// 고른 목소리(비어 있으면 가장 좋은 한국어 목소리)
    @Published var moksoriId: String { didSet { d.set(moksoriId, forKey: "gn.moksori") } }
    /// 보폭(미터) — 위성이 흐릴 때 걸음으로 이어 셈하는 데 씀
    @Published var bopok: Double { didSet { d.set(bopok, forKey: "gn.bopok") } }
    /// 말로 하기 — "하이 길눈"으로 부르기(마이크를 계속 열어 둠, 처음에는 꺼 둠)
    @Published var haiGilnun: Bool { didSet { if Seoljeong.jabongApp && haiGilnun { haiGilnun = false; return }; d.set(haiGilnun, forKey: "gn.haiGilnun") } }
    /// 2.52.1 지금 도는 앱이 자봉 앱인가(자봉 앱은 길눈 부품을 함께 실음)
    static let jabongApp = Bundle.main.bundleIdentifier == "kr.or.ada.jabong"
    /// 2.27.0 새 알아듣기 부품(애플 SpeechAnalyzer, iOS 26) 쓰기 — 처음부터 켜짐
    @Published var saeDeutgi: Bool { didSet { d.set(saeDeutgi, forKey: "gn.saeDeutgi") } }
    /// 2.29.0 못 알아들은 말을 폰 안 인공지능(애플 인텔리전스)으로 풀기 — 처음부터 켜짐
    @Published var malAI: Bool { didSet { d.set(malAI, forKey: "gn.malAI") } }
    /// 2.12.2 방송 중에도 하이 길눈 듣기 — 폰이 내는 소리를 빼고 들음(처음부터 켜짐)
    @Published var haiBangsongDeutgi: Bool { didSet { d.set(haiBangsongDeutgi, forKey: "gn.haiBangsong") } }
    /// 말로 하기가 이용자를 부르는 호칭
    @Published var hoching: String { didSet { d.set(hoching, forKey: "gn.hoching") } }
    /// 음향신호기 자동 울리기(처음부터 켜짐)
    @Published var sinhogiJadong: Bool { didSet { d.set(sinhogiJadong, forKey: "gn.sinhogiJadong") } }
    /// 안내 중 이어폰 단추 받기(처음부터 켜짐)
    @Published var ieoponDanchu: Bool { didSet { d.set(ieoponDanchu, forKey: "gn.ieoponDanchu") } }
    /// 2.34.0 받은 동영상을 애플워치가 있으면 워치에서 틀기(처음엔 켬)
    @Published var dongyeongWatch: Bool { didSet { d.set(dongyeongWatch, forKey: "gn.dongyeongWatch") } }
    /// 차 안에서 고장이 바뀌면 고장 이야기 한 번(처음부터 켜짐)
    @Published var gojangJadong: Bool { didSet { d.set(gojangJadong, forKey: "gn.gojangJadong") } }
    /// 2.12.7 차에 타면 지나는 고장 노래 저절로 틀기(2.57.0부터 처음엔 꺼짐, 켜신 분만)
    @Published var gojangNorae: Bool { didSet { d.set(gojangNorae, forKey: "gn.gojangNorae") } }
    /// 2.59.0 통화가 끝난 뒤 방송을 저절로 이어 들음(처음엔 꺼짐 — 맨 위 「방송 이어 듣기」 단추로, 이사장님 승인)
    @Published var tonghwaDwiIeum: Bool { didSet { d.set(tonghwaDwiIeum, forKey: "gn.tonghwaDwiIeum") } }
    /// 2.15.0 카메라 눈 말소리 — 끄면 문 찾기 등 카메라 기능이 말 없이 소리로만(처음부터 켜짐)
    @Published var kameraMal: Bool { didSet { d.set(kameraMal, forKey: "gn.kameraMal") } }
    /// 2.28.0 길눈 목소리(마이크로소프트 선희) 쓰기 — 처음부터 켜짐(애저 정식 열쇠 전에는 나스 음악 열쇠가 있는 폰에서만)
    @Published var msMoksori: Bool { didSet { d.set(msMoksori, forKey: "gn.msMoksori") } }
    /// 2.8.0 기사 읽는 목소리와 빠르기(길 안내 목소리와 따로)
    @Published var gisaMoksoriId: String { didSet { d.set(gisaMoksoriId, forKey: "gn.gisaMoksori") } }
    @Published var gisaBbareugiDan: Int { didSet { d.set(gisaBbareugiDan, forKey: "gn.gisaBbareugi") } }
    // 2.9.0 말하기 설정 — 얼마나, 무엇을 말할지(웹 길눈 malseol 과 같은 기본값)
    /// 얼마나 자세히 — 0 짧게, 1 보통, 2 자세히
    @Published var malSang: Int { didSet { d.set(malSang, forKey: "gn.malSang") } }
    @Published var kkeokOn: Bool { didSet { d.set(kkeokOn, forKey: "gn.kkeokOn") } }
    @Published var kkeokCho: Int { didSet { d.set(kkeokCho, forKey: "gn.kkeokCho") } }
    @Published var doepul: Int { didSet { d.set(doepul, forKey: "gn.doepul") } }
    @Published var gilOn: Bool { didSet { d.set(gilOn, forKey: "gn.gilOn") } }
    @Published var gilGap: Int { didSet { d.set(gilGap, forKey: "gn.gilGap") } }
    /// 2.56.0 차 안 안내 정도 1 간단 · 2 보통 · 3 자세히(웹 길눈 0.84.0과 같은 처음 값 보통)
    @Published var chaJeongdo: Int { didSet { d.set(chaJeongdo, forKey: "gn.chaJeongdo") } }
    /// 2.25.0 차 안 간판 알림 — 달릴 때 지나는 가게·건물(처음부터 켜짐), 서 있을 때 카메라로 창밖 간판 읽기(처음엔 꺼짐)
    @Published var ganpanOn: Bool { didSet { d.set(ganpanOn, forKey: "gn.ganpanOn") } }
    @Published var ganpanKamera: Bool { didSet { d.set(ganpanKamera, forKey: "gn.ganpanKamera") } }
    @Published var malJaru: Bool { didSet { d.set(malJaru, forKey: "gn.malJaru") } }
    @Published var hwaksinEum: Bool { didSet { d.set(hwaksinEum, forKey: "gn.hwaksinEum") } }
    // 2.9.0 현 위치정보 말할 내용
    @Published var jariJuso: Bool { didSet { d.set(jariJuso, forKey: "gn.jariJuso") } }
    @Published var jariGot: Bool { didSet { d.set(jariGot, forKey: "gn.jariGot") } }
    @Published var jariJibeon: Bool { didSet { d.set(jariJibeon, forKey: "gn.jariJibeon") } }
    @Published var jariJijeom: Bool { didSet { d.set(jariJijeom, forKey: "gn.jariJijeom") } }
    @Published var jariOcha: Bool { didSet { d.set(jariOcha, forKey: "gn.jariOcha") } }
    // 2.9.0 흔들면 — 처음에는 꺼 둠. 하는 일은 gingeup(긴급통화 열기) 또는 jari(자리 번호 말하기)
    @Published var heundeulKyeojim: Bool { didSet { d.set(heundeulKyeojim, forKey: "gn.heundeul") } }
    @Published var heundeulIl: String { didSet { d.set(heundeulIl, forKey: "gn.heundeulIl") } }
    // 2.10.0 점지도 따라 걷기 — "제대로 가고 있습니다"를 몇 미터마다(5·10·20, 처음 10)
    @Published var hwaksinGan: Int { didSet { d.set(hwaksinGan, forKey: "gn.hwaksinGan") } }
    // 2.10.0 보폭 — 혼자 걸을 때와 동반자와 걸을 때를 따로 재 둠(웹과 같음). 지금 쓰는 쪽이 bopok 에 들어감
    @Published var bopokMode: String { didSet { d.set(bopokMode, forKey: "gn.bopokMode") } }
    @Published var bopokHonja: Double { didSet { d.set(bopokHonja, forKey: "gn.bopokHonja") } }
    @Published var bopokDongban: Double { didSet { d.set(bopokDongban, forKey: "gn.bopokDongban") } }
    // 2.10.0 리모컨 배우기 — 자리("1" 지금 어디, "2" 다음에 무엇, "3" 다시 말해 주기)마다 익힌 단추
    @Published var rimo: [String: String] { didSet { d.set(rimo, forKey: "gn.rimo") } }

    static let bbareugiIreum = ["아주 느리게", "느리게", "보통", "빠르게", "아주 빠르게"]
    static let bbareugiGap: [Float] = [0.40, 0.46, 0.52, 0.58, 0.64]
    static let sangIreum = ["짧게", "보통", "자세히"]

    init() {
        let ud = UserDefaults.standard
        malKyeojim = (ud.object(forKey: "gn.malKyeojim") as? Bool) ?? true
        bbareugiDan = (ud.object(forKey: "gn.bbareugi") as? Int) ?? 2
        moksoriId = ud.string(forKey: "gn.moksori") ?? ""
        let b = ud.double(forKey: "gn.bopok")
        bopok = b > 0.2 ? b : 0.65
        // 2.52.1 자봉 앱(길눈 부품을 함께 실음)에서는 하이 길눈 듣기를 아예 켜지 않음 — 길눈 앱과 마이크를 다투지 않게(이사장님 승인 2026-10-06)
        haiGilnun = Seoljeong.jabongApp ? false : ((ud.object(forKey: "gn.haiGilnun") as? Bool) ?? false)
        saeDeutgi = (ud.object(forKey: "gn.saeDeutgi") as? Bool) ?? true
        malAI = (ud.object(forKey: "gn.malAI") as? Bool) ?? true
        haiBangsongDeutgi = (ud.object(forKey: "gn.haiBangsong") as? Bool) ?? true
        let h = (ud.string(forKey: "gn.hoching") ?? "").trimmingCharacters(in: .whitespaces)
        hoching = h.isEmpty ? "길손님" : h
        sinhogiJadong = (ud.object(forKey: "gn.sinhogiJadong") as? Bool) ?? true
        ieoponDanchu = (ud.object(forKey: "gn.ieoponDanchu") as? Bool) ?? true
        dongyeongWatch = (ud.object(forKey: "gn.dongyeongWatch") as? Bool) ?? true
        gojangJadong = (ud.object(forKey: "gn.gojangJadong") as? Bool) ?? true
        tonghwaDwiIeum = (ud.object(forKey: "gn.tonghwaDwiIeum") as? Bool) ?? false   // 2.59.0
        gojangNorae = (ud.object(forKey: "gn.gojangNorae") as? Bool) ?? false   // 2.57.0 처음값 끔(이사장님 지시 — 처음 받은 분이 다른 기능을 먼저 익히게)
        kameraMal = (ud.object(forKey: "gn.kameraMal") as? Bool) ?? true
        msMoksori = (ud.object(forKey: "gn.msMoksori") as? Bool) ?? true
        gisaMoksoriId = ud.string(forKey: "gn.gisaMoksori") ?? ""
        gisaBbareugiDan = (ud.object(forKey: "gn.gisaBbareugi") as? Int) ?? 2
        malSang = (ud.object(forKey: "gn.malSang") as? Int) ?? 2
        kkeokOn = (ud.object(forKey: "gn.kkeokOn") as? Bool) ?? true
        kkeokCho = (ud.object(forKey: "gn.kkeokCho") as? Int) ?? 8
        doepul = (ud.object(forKey: "gn.doepul") as? Int) ?? 6
        gilOn = (ud.object(forKey: "gn.gilOn") as? Bool) ?? true
        gilGap = (ud.object(forKey: "gn.gilGap") as? Int) ?? 60
        chaJeongdo = (ud.object(forKey: "gn.chaJeongdo") as? Int) ?? 2
        ganpanOn = (ud.object(forKey: "gn.ganpanOn") as? Bool) ?? true
        ganpanKamera = (ud.object(forKey: "gn.ganpanKamera") as? Bool) ?? false
        malJaru = (ud.object(forKey: "gn.malJaru") as? Bool) ?? false
        hwaksinEum = (ud.object(forKey: "gn.hwaksinEum") as? Bool) ?? true
        jariJuso = (ud.object(forKey: "gn.jariJuso") as? Bool) ?? true
        jariGot = (ud.object(forKey: "gn.jariGot") as? Bool) ?? true
        jariJibeon = (ud.object(forKey: "gn.jariJibeon") as? Bool) ?? false
        jariJijeom = (ud.object(forKey: "gn.jariJijeom") as? Bool) ?? true
        jariOcha = (ud.object(forKey: "gn.jariOcha") as? Bool) ?? true
        heundeulKyeojim = (ud.object(forKey: "gn.heundeul") as? Bool) ?? false
        heundeulIl = ud.string(forKey: "gn.heundeulIl") ?? "gingeup"
        let hg = (ud.object(forKey: "gn.hwaksinGan") as? Int) ?? 10
        hwaksinGan = [5, 10, 20].contains(hg) ? hg : 10
        bopokMode = ud.string(forKey: "gn.bopokMode") == "dongban" ? "dongban" : "honja"
        bopokHonja = ud.double(forKey: "gn.bopokHonja")
        bopokDongban = ud.double(forKey: "gn.bopokDongban")
        rimo = (ud.dictionary(forKey: "gn.rimo") as? [String: String]) ?? [:]
    }

    /// 보폭을 재 두셨는가(지금 쓰는 쪽)
    var bopokJaem: Bool { (bopokMode == "dongban" ? bopokDongban : bopokHonja) > 0.2 }
    static func bopokModeIreum(_ m: String) -> String { m == "dongban" ? "동반자와 걸을 때" : "혼자 걸을 때" }

    /// 보폭 쪽 바꾸기 — 재 둔 값이 있으면 그것을 씀
    func bopokModeBakkugi(_ m: String) {
        bopokMode = m
        let v = m == "dongban" ? bopokDongban : bopokHonja
        if v > 0.2 { bopok = v }
    }

    var malBbareugi: Float {
        Seoljeong.bbareugiGap[max(0, min(Seoljeong.bbareugiGap.count - 1, bbareugiDan))]
    }
}

extension Seoljeong {
    /// 호칭이 비어 있으면 길손님
    var ho: String {
        let h = hoching.trimmingCharacters(in: .whitespaces)
        return h.isEmpty ? "길손님" : h
    }
}
