// BYOD 방송 아이폰 — 방송 켜고 끄기와 프로그램 말소리 (1.1.5판, 빌드 261010-BI1, 방송클)
// 방송 단추가 부르는 곳입니다. 서버(Seobeo)를 열고, 해설 소리 받기(Sori)를 켜서 0.1초 조각마다 서버로 넘깁니다.
import Foundation
import AVFoundation
import UIKit

final class BangIl {
    static let shared = BangIl()
    private var sori: Sori?

    /// 마이크 허락을 묻고(처음 한 번), 되면 방송을 켬. 결과 한 줄을 화면 줄기에서 돌려줌
    func kyeogi(_ kkeut: @escaping (String) -> Void) {
        let s = AVAudioSession.sharedInstance()
        switch s.recordPermission {
        case .granted:
            kkeut(kyeogiBaro())
        case .denied:
            kkeut("마이크 허락이 없어 방송을 시작하지 못했습니다. 아이폰 설정, BYOD 방송, 마이크를 켠 뒤 다시 누르십시오.")
        default:
            s.requestRecordPermission { ok in
                DispatchQueue.main.async {
                    kkeut(ok ? self.kyeogiBaro() : "마이크 허락이 없어 방송을 시작하지 못했습니다. 다시 누르고 허용을 고르십시오.")
                }
            }
        }
    }

    private func kyeogiBaro() -> String {
        let b = Bang.shared
        if b.kyeojim { return "이미 방송 중입니다." }
        b.jalmot = ""
        do {
            try Seobeo.shared.sijak()
        } catch {
            b.jalmot = "방송 문(8080)을 열지 못했습니다. 아이폰을 다시 켠 뒤 해 보십시오."
            return b.jalmot
        }
        let so = Sori { d in Seobeo.shared.ppurida(d) }
        do {
            try so.sijak()
        } catch {
            Seobeo.shared.meomchugi()
            b.jalmot = "마이크: " + error.localizedDescription
            return "방송을 시작하지 못했습니다. " + b.jalmot
        }
        sori = so
        b.juso = Juso.deutgiJuso()
        b.keu = true
        b.kyeojim = true
        b.sigakTtae = Date()
        return "방송을 시작했습니다."
    }

    func kkeugi() {
        let b = Bang.shared
        b.keu = false
        sori?.meomchugi()
        sori = nil
        Seobeo.shared.meomchugi()
        b.kyeojim = false
        b.deutnunSu = 0
    }
}

/// 프로그램 말소리 — 앱이 스스로 하는 말. 켜고 끌 수 있음(화면 글과 보이스오버가 읽는 것은 그대로)
final class Malsori {
    static let shared = Malsori()
    private let synth = AVSpeechSynthesizer()
    var kyeojim: Bool {
        get { UserDefaults.standard.object(forKey: "malsori") as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: "malsori") }
    }
    func mal(_ t: String) {
        guard kyeojim else { return }
        if synth.isSpeaking { synth.stopSpeaking(at: .immediate) }
        let u = AVSpeechUtterance(string: t)
        u.voice = AVSpeechSynthesisVoice(language: "ko-KR")
        synth.speak(u)
    }
}

/// 보이스오버에 한 번 알림
func allyeo(_ t: String) {
    UIAccessibility.post(notification: .announcement, argument: t)
}
