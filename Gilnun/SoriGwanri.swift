// 2.103.0 (261021-I57, 이사장님 승인 2026-10-21) 소리 관리자 — 길눈 안의 소리와 마이크가 한 자리에서 차례를 받음.
//
// 길눈 안에는 소리를 내거나 듣는 것이 여럿입니다(위험 경고, 전화, 보이스오버, 길눈 안내, 방송·음악, 말로 하기 마이크).
// 지금까지는 부딪칠 때마다 그 자리에서 따로 막아 와서, 한 곳을 막으면 다른 곳에서 새었습니다
// (10-12~10-21 기록: 말 끊김 161번, 마이크·받아쓰기 막힘 72번 — 말로 하기 세 번에 한 번꼴).
// 이제 마이크를 저절로 열기 전에는 반드시 이 관리자에게 물어보고, 막혔을 때 까닭을 한 번만 말씀드립니다.
//
// 차례(높은 것부터): 위험 경고 > 전화(거는 중·통화 중) > 보이스오버가 읽는 중 > 길눈 안내 > 방송·음악.
// 마이크는 아무도 말하지 않을 때만 저절로 엽니다. 말로 하기 단추를 직접 누르시면 언제든 엽니다(전화 중만 빼고).
import Foundation
import AVFoundation
import UIKit

final class SoriGwanri {
    static let shared = SoriGwanri()

    /// 마이크를 저절로 열지 못하는 까닭
    enum Makim: String {
        case eopseum = ""                 // 열어도 됨
        case gingeup = "gingeup"          // 긴급 통화 중
        case jeonhwa = "jeonhwa"          // 통화 중
        case geoneun = "geoneun"          // 전화를 거는 중(통화 확인 창)
        case sorijari = "sorijari"        // 다른 앱이 소리 자리를 가져감
        case voIlgeum = "vo_ilgeum"       // 보이스오버가 줄을 읽는 중
    }

    /// 지금 마이크를 저절로 열어도 되는가
    func maikMakim() -> Makim {
        if GinGeup.shared.sangtae != .eopseum { return .gingeup }
        if JeonhwaGamsi.shared.jeonhwaJung { return .jeonhwa }
        if ChaBureugi.shared.geoneunJung { return .geoneun }
        if SoriEngine.shared.meomchumJung { return .sorijari }
        if UIAccessibility.isVoiceOverRunning && Date() < SoriEngine.shared.voKkaji { return .voIlgeum }
        return .eopseum
    }

    /// 보이스오버가 읽기를 마칠 때까지 남은 초(없으면 0)
    var voNameunCho: Double { max(0, SoriEngine.shared.voKkaji.timeIntervalSinceNow) }

    // MARK: 마이크 막힘 — 두 번 잇달아 소리가 하나도 들어오지 않으면 까닭을 한 번 말씀드리고 저절로 듣기를 쉼

    private(set) var makhimYeonsok = 0
    private var makhimMalTtae = Date.distantPast
    /// 저절로 듣기를 쉬는 중(말로 하기 단추를 누르시면 풀림)
    private(set) var jadongSwim = false

    /// 명령을 들은 결과를 알려 줌. 소리가 하나도 안 들어왔으면(soriEopseum) 막힘으로 셈.
    /// 돌려주는 값이 참이면 지금 까닭을 말씀드려야 함
    func myeongDeureum(soriEopseum: Bool, geulja: Bool) -> Bool {
        if geulja { makhimYeonsok = 0; jadongSwim = false; return false }
        guard soriEopseum else { return false }
        makhimYeonsok += 1
        guard makhimYeonsok >= 2 else { return false }
        jadongSwim = true
        Girok.shared.namgi("maik_makhim_yeonsok", ["su": makhimYeonsok, "ipryeok": SoriGwanri.ipryeokJari()])
        if Date().timeIntervalSince(makhimMalTtae) < 600 { return false }   // 10분에 한 번만 말씀드림
        makhimMalTtae = Date()
        return true
    }

    /// 말로 하기 단추를 직접 누르심 — 쉬던 저절로 듣기를 풂
    func danchuNureum() {
        makhimYeonsok = 0
        jadongSwim = false
    }

    static let makhimMal = "마이크에 소리가 들어오지 않습니다. 이어폰이나 블루투스 연결을 살펴 주십시오. 그때까지 저절로 듣지 않습니다. 다시 하시려면 말로 하기 단추를 누르십시오."

    /// 지금 마이크 소리가 들어오는 자리(기록에 남김) — naejang 폰 마이크, ieopon 유선 이어폰, bluetooth, carplay, gita
    static func ipryeokJari() -> String {
        guard let p = AVAudioSession.sharedInstance().currentRoute.inputs.first?.portType else { return "eopseum" }
        switch p {
        case .builtInMic: return "naejang"
        case .headsetMic: return "ieopon"
        case .bluetoothHFP, .bluetoothLE, .bluetoothA2DP: return "bluetooth"
        case .carAudio: return "carplay"
        default: return p.rawValue
        }
    }

    // MARK: 소리 멈춤 한 번을 한 줄로 — 시작 때 까닭을 적고, 다시 말할 때 걸린 시간을 적음

    private var meomchumSijak: Date?
    private var meomchumIyu = ""

    func meomchumSijakham(_ iyu: String) {
        meomchumSijak = Date()
        meomchumIyu = iyu
        Girok.shared.namgi("sori_kkeunkim", ["dan": "sijak", "iyu": iyu])
    }

    /// dan: kkeut(돌려받았다는 알림) · dasi(앱으로 돌아옴) · seuseuro(8초 뒤 스스로 다시 잡음)
    func meomchumKkeut(_ dan: String) {
        guard let s = meomchumSijak else { return }
        meomchumSijak = nil
        Girok.shared.namgi("sori_dasi", ["dan": dan, "iyu": meomchumIyu, "cho": Int(Date().timeIntervalSince(s))])
    }

    /// 소리 멈춤의 까닭 — 전화, 받아쓰기(시리), 다른 앱
    static func meomchumIyu(_ n: Notification) -> String {
        if JeonhwaGamsi.shared.jeonhwaJung || ChaBureugi.shared.geoneunJung { return "jeonhwa" }
        if let r = n.userInfo?[AVAudioSessionInterruptionReasonKey] as? UInt,
           AVAudioSession.InterruptionReason(rawValue: r) == .appWasSuspended { return "app_swim" }
        if UIApplication.shared.applicationState != .active { return "bakkat" }
        return "dareun_app"
    }
}
