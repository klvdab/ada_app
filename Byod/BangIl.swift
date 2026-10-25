// BYOD 방송 아이폰 — 방송 켜고 끄기와 프로그램 말소리 (1.1.5판, 빌드 261010-BI1, 방송클)
// 방송 단추가 부르는 곳입니다. 서버(Seobeo)를 열고, 해설 소리 받기(Sori)를 켜서 0.1초 조각마다 서버로 넘깁니다.
// 1.2.0 (261014-BI3, 이사장님 승인 「1」, 방송클) 플린트 2 방식이면 서버를 열지 않고 소리를 공유기로 보냄(FlintBonaegi)
import Foundation
import AVFoundation
import UIKit
import AudioToolbox

final class BangIl {
    static let shared = BangIl()
    private var sori: Sori?
    // 1.6.0 (261019-BI8) 방송 지킴이 — 해설 소리가 6초 넘게 안 들어오면 3초에 한 번 조용한 알림 소리
    private var soriTtae = Date().timeIntervalSince1970
    private lazy var jikim = Jikim(meomchumInga: { [weak self] in
        guard let self = self else { return false }
        return Bang.shared.kyeojim && Date().timeIntervalSince1970 - self.soriTtae > 6
    }, bakkwim: { m in
        DispatchQueue.main.async {
            let b = Bang.shared
            if m { b.jalmot = "해설 소리가 들어오지 않습니다. 마이크와 소리 받는 곳을 살펴 주십시오. 방송은 켜져 있습니다."; allyeo(b.jalmot) }
            else if b.jalmot.hasPrefix("해설 소리가 들어오지") { b.jalmot = "" }
        }
    })

    /// 마이크 허락을 묻고(처음 한 번), 되면 방송을 켬. 결과 한 줄을 화면 줄기에서 돌려줌
    func kyeogi(_ kkeut: @escaping (String) -> Void) {
        let s = AVAudioSession.sharedInstance()
        switch s.recordPermission {
        case .granted:
            kkeut(kyeogiBaro())
        case .denied:
            kkeut("마이크 허락이 없어 방송을 시작하지 못했습니다. 아이폰 설정, LVD 송출, 마이크를 켠 뒤 다시 누르십시오.")
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
        if Flint.flintInga { return flintKyeogi() }   // 1.2.0
        do {
            try Seobeo.shared.sijak()
        } catch {
            b.jalmot = "방송 문(8080)을 열지 못했습니다. 아이폰을 다시 켠 뒤 해 보십시오."
            return b.jalmot
        }
        // 1.7.0 (261021-BI9) 아이피타임 방식 — 이 아이폰이 행사 녹음을 함(플린트 2 방식은 공유기가 녹음)
        let ud = UserDefaults.standard
        GigiNokeum.shared.sijak(jemok: (ud.string(forKey: "haengsa_jemok") ?? "").trimmingCharacters(in: .whitespacesAndNewlines),
                                haeseolsa: (ud.string(forKey: "haeseolsa") ?? "").trimmingCharacters(in: .whitespacesAndNewlines))
        let so = Sori { [weak self] d in self?.soriTtae = Date().timeIntervalSince1970; Seobeo.shared.ppurida(d); GigiNokeum.shared.sseugi(d); InteonetBonaegi.shared.neoki(d) }   // 1.5.0 인터넷 방송에도 같은 소리, 1.7.0 녹음
        do {
            try so.sijak()
        } catch {
            Seobeo.shared.meomchugi()
            _ = GigiNokeum.shared.maegam()   // 1.7.0 시작하지 못했으면 연 행사를 닫음
            b.jalmot = "마이크: " + error.localizedDescription
            return "방송을 시작하지 못했습니다. " + b.jalmot
        }
        sori = so
        b.juso = Juso.deutgiJuso()
        b.keu = true
        b.kyeojim = true
        b.sigakTtae = Date()
        inteonetSijak()
        soriTtae = Date().timeIntervalSince1970; jikim.sijak()   // 1.6.0
        girokSijak("iptime")   // 1.8.0
        yeongsangSijak()   // 1.9.0 (261024-BI11) 무대 영상 녹화
        return "BYOD 방송을 시작했습니다."
    }

    /// 1.2.0 플린트 2 방식 — 소리 받기는 그대로, 받은 소리를 공유기로 보냄
    private func flintKyeogi() -> String {
        let b = Bang.shared
        FlintBonaegi.shared.sijak()
        let so = Sori { [weak self] d in self?.soriTtae = Date().timeIntervalSince1970; FlintBonaegi.shared.neoki(d); InteonetBonaegi.shared.neoki(d) }   // 1.5.0 인터넷 방송에도 같은 소리
        do {
            try so.sijak()
        } catch {
            FlintBonaegi.shared.meomchugi()
            b.jalmot = "마이크: " + error.localizedDescription
            return "방송을 시작하지 못했습니다. " + b.jalmot
        }
        sori = so
        b.juso = Flint.JUSO
        b.keu = true
        b.kyeojim = true
        b.sigakTtae = Date()
        inteonetSijak()
        soriTtae = Date().timeIntervalSince1970; jikim.sijak()   // 1.6.0
        girokSijak("flint")   // 1.8.0
        yeongsangSijak()   // 1.9.0 (261024-BI11) 무대 영상 녹화
        return "BYOD 방송을 시작했습니다. 해설 소리를 플린트 2 공유기로 보냅니다."
    }

    /// 1.8.0 (261023-BI10) 이용 기록과, 듣는 앱이 이 아이폰을 찾을 수 있는지 10초마다 스스로 확인(아이피타임 방식)
    private var girokTimer: Timer?
    private var girokCho = 0
    private func girokSijak(_ bangsik: String) {
        let b = Bang.shared
        ByodGirok.shared.namgi("bang_sijak", ["bangsik": bangsik, "juso": b.juso, "inteonet_hamkke": InteonetBonaegi.hamkke, "jeonhwa": InteonetBonaegi.jeonhwa.count >= 9])
        Bang.mundapTtae = .distantPast
        tteoreojim = false   // 1.9.1
        girokCho = 0
        girokTimer?.invalidate()
        let t = Timer(timeInterval: 10, repeats: true) { [weak self] _ in self?.girokDolgi(bangsik) }
        RunLoop.main.add(t, forMode: .common)
        girokTimer = t
        girokDolgi(bangsik)
    }
    private func girokDolgi(_ bangsik: String) {
        let b = Bang.shared
        let cho = girokCho; girokCho += 1
        if cho % 6 == 0 && cho > 0 { ByodGirok.shared.namgi("deutneun", ["n": b.deutnunSu]) }
        if bangsik == "iptime" { gongyugiSalpim() }   // 1.9.1 (261025-BI12) 공유기에서 떨어짐 알림
        guard bangsik == "iptime", let u = URL(string: b.juso.hasSuffix("/") ? b.juso + "status" : b.juso + "/status") else { return }
        var r = URLRequest(url: u, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 2)
        r.allowsCellularAccess = false
        let jeon = Date().timeIntervalSince(Bang.mundapTtae) < 25
        URLSession.shared.dataTask(with: r) { d, res, _ in
            let ok = (res as? HTTPURLResponse)?.statusCode == 200 && (d.flatMap { String(data: $0, encoding: .utf8) } ?? "").contains("\"hyeonjang\":true")
            DispatchQueue.main.async {
                if ok { Bang.mundapTtae = Date() }
                if cho == 0 || ok != jeon { ByodGirok.shared.namgi("mundap", ["ok": ok, "juso": b.juso]) }
            }
        }.resume()
    }

    /// 1.9.1 (261025-BI12, 새방클, 이사장님 승인 2026-10-25) 공유기에서 떨어짐 알림 — 안드로이드 1.8.1과 같은 뜻.
    /// 아이피타임 방식으로 방송하는 동안 10초마다 이 아이폰의 와이파이(또는 유선 랜·핫스팟) 주소를 살펴, 없어지면
    /// 진동 세 번과 함께 「공유기에서 떨어졌습니다」를 보이스오버로 알리고 기록을 남김. 다시 붙으면 알림을 지우고 듣기 주소를 고침
    private var tteoreojim = false
    private func gongyugiSalpim() {
        let b = Bang.shared
        guard b.kyeojim else { return }
        let l = Juso.moduJuso()
        let ip = l.first(where: { $0.0 == "유선 랜" })?.1 ?? l.first(where: { $0.0 == "와이파이" })?.1 ?? l.first(where: { $0.0 == "개인용 핫스팟" })?.1
        if let i = ip {
            let sae = "http://\(i):\(Bang.PORT)/"
            if tteoreojim {
                tteoreojim = false
                if b.jalmot.hasPrefix("공유기에서 떨어졌습니다") { b.jalmot = "" }
                allyeo("공유기에 다시 붙었습니다. 방송이 이어집니다.")
                ByodGirok.shared.namgi("gongyugi_dasi", ["juso": sae])
            }
            if sae != b.juso { b.juso = sae }
        } else if !tteoreojim {
            tteoreojim = true
            b.jalmot = "공유기에서 떨어졌습니다. 이 아이폰이 와이파이를 떠나 듣는 분이 방송을 찾지 못합니다. 설정의 와이파이에서 행사장 공유기에 다시 붙이십시오. 휴대폰 데이터를 꺼 두면 가장 확실합니다."
            allyeo(b.jalmot)
            for k in 0..<3 { DispatchQueue.main.asyncAfter(deadline: .now() + Double(k) * 0.8) { AudioServicesPlaySystemSound(kSystemSoundID_Vibrate) } }
            ByodGirok.shared.namgi("gongyugi_tteoreojim", ["juso": b.juso])
        }
    }

    /// 1.5.0 현장 방송과 함께 인터넷 방송 — 제목은 행사 이름(비면 날짜와 시각), 현장영상해설사 이름을 함께
    private func inteonetSijak() {
        let d = UserDefaults.standard
        var jm = (d.string(forKey: "haengsa_jemok") ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if jm.isEmpty { let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "M월 d일 a h시 m분"; jm = "현장영상해설 방송 " + f.string(from: Date()) }
        let who = (d.string(forKey: "haeseolsa") ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        InteonetBonaegi.shared.sijak(jemok: jm, who: who)
    }

    /// 1.9.0 (261024-BI11) 무대 영상 녹화 — 첫 화면에서 켜 두었으면 방송과 함께 뒤 카메라로 녹화(카메라가 안 되어도 방송은 그대로)
    /// 아이피타임 방식은 이 아이폰 행사 녹음과 같은 폴더, 플린트 2 방식은 이 아이폰이 이름을 지은 폴더에 담음
    func yeongsangSijak() {
        guard YeongsangNokhwa.kyeojim, Bang.shared.kyeojim else { return }
        let ud = UserDefaults.standard
        YeongsangNokhwa.shared.kyeogi(sid0: Flint.flintInga ? "" : GigiNokeum.shared.sid,
                                      bangsik: Flint.flintInga ? "flint" : "iptime",
                                      jemok: (ud.string(forKey: "haengsa_jemok") ?? "").trimmingCharacters(in: .whitespacesAndNewlines),
                                      haeseolsa: (ud.string(forKey: "haeseolsa") ?? "").trimmingCharacters(in: .whitespacesAndNewlines))
    }

    func kkeugi() {
        let b = Bang.shared
        YeongsangNokhwa.shared.kkeugi()   // 1.9.0 (261024-BI11) 무대 영상 녹화도 함께 멈춤
        b.keu = false
        jikim.meomchugi()   // 1.6.0
        girokTimer?.invalidate(); girokTimer = nil   // 1.8.0
        if b.kyeojim { ByodGirok.shared.namgi("bang_kkeut", ["deutneun": b.deutnunSu]); ByodGirok.shared.bonaegi() }
        InteonetBonaegi.shared.meomchugi()   // 1.5.0
        sori?.meomchugi()
        sori = nil
        Seobeo.shared.meomchugi()
        let flintDolatda = b.juso == Flint.JUSO && b.kyeojim
        FlintBonaegi.shared.meomchugi()   // 1.2.0
        if flintDolatda { HaengsaOlligi.shared.sijak(maegam: true) }   // 1.3.0 행사 마감·녹음과 결과보고 받기·배프에 올리기
        if !GigiNokeum.shared.sid.isEmpty {   // 1.7.0 아이피타임 방식 녹음 마감 — 긴 녹음을 잇는 일이라 화면 줄기를 붙잡지 않게 따로
            DispatchQueue.global(qos: .userInitiated).async { if GigiNokeum.shared.maegam() { HaengsaOlligi.shared.sijak(maegam: false) } }
        }
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
