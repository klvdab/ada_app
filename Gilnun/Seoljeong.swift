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

    static let bbareugiIreum = ["아주 느리게", "느리게", "보통", "빠르게", "아주 빠르게"]
    static let bbareugiGap: [Float] = [0.40, 0.46, 0.52, 0.58, 0.64]

    init() {
        let ud = UserDefaults.standard
        malKyeojim = (ud.object(forKey: "gn.malKyeojim") as? Bool) ?? true
        bbareugiDan = (ud.object(forKey: "gn.bbareugi") as? Int) ?? 2
        moksoriId = ud.string(forKey: "gn.moksori") ?? ""
        let b = ud.double(forKey: "gn.bopok")
        bopok = b > 0.2 ? b : 0.65
    }

    var malBbareugi: Float {
        Seoljeong.bbareugiGap[max(0, min(Seoljeong.bbareugiGap.count - 1, bbareugiDan))]
    }
}
