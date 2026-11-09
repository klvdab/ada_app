// 소리 기록 — 2.126.0 (261109-I80, 대클, 이사장님 지시 2026-11-09 「사용 기록이 없는 것부터 고쳐야」·「모두 승인」)
// 이사장님이 11-09 새벽 1시부터 음악을 켜 두고 주무시다 화면을 만지면 소리가 커지고 볼륨 단추가 제 기능을 못 함을 겪으심.
// 그런데 길눈 기록에는 1시 18분부터 6시 36분까지 아무것도 없었음 — 음악이 이어지는 것·볼륨 단추·소리 방식 바뀜·앱이 꺼진 것을
// 길눈이 처음부터 적지 않게 만들어져 있었기 때문. 이 부품이 그 빈자리를 채웁니다.
//   1. 볼륨 단추(폰 소리 크기)가 바뀔 때마다 — 얼마에서 얼마로, 그때 소리 방식·나가는 곳·방송 형편
//   2. 소리 방식(카테고리·모드·섞기)이 바뀔 때마다 — 무엇에서 무엇으로, 마지막으로 바꾼 부품(누가)
//   3. 소리 나가는 곳이 바뀔 때(이어폰 꽂음·뺌 등)와 소리 끊김(전화·알람 등)
//   4. 음악·방송이 나오는 동안 10분마다 「아직 나오는 중」 — 무엇을, 폰 소리 크기, 방송 자체 크기(0.2 = 길눈이 말하느라 줄인 것)
//   5. 앱이 뒤로 가고 앞으로 나올 때
//   6. 앱이 다시 켜질 때 지난번이 정상으로 끝났는지, 마지막으로 살아 있던 시각과 그때 방송 형편
//   7. 아이폰이 알려 주는 길눈 고장 보고(멈춤·강제 종료 까닭, MetricKit) — 다음에 켜질 때 받아 나스로
// 기록은 1분마다 폰에 저장되고 20초마다 나스로 갑니다(Girok). 위치 좌표는 이 부품에서 남기지 않습니다.
import Foundation
import UIKit
import AVFoundation
import MetricKit

final class SoriGirok: NSObject, MXMetricManagerSubscriber {
    static let shared = SoriGirok()

    /// 소리 방식을 바꾸기 바로 앞에 그 부품 이름을 적어 둠 — 「누가 바꿨는지」를 기록에 남기려고
    static var nuga = ""

    private var bollyumGwanchal: NSKeyValueObservation?
    private var sigye: Timer?
    private var jinanBangsik = ""
    private var jinanBollyum: Float = -1
    private var saraitdaTtae = Date.distantPast
    private var sijakham = false

    private let ud = UserDefaults.standard
    private let kSarait = "gn.sg.saraitda"        // 마지막으로 살아 있던 시각(초)
    private let kKkeut = "gn.sg.jeongsangKkeut"   // 정상으로 끝났는가
    private let kHyeongpyeon = "gn.sg.hyeongpyeon" // 그때 방송·앞뒤 형편 한 줄
    private let kApDwi = "gn.sg.apdwi"            // 마지막 앞/뒤

    func sijak() {
        guard !sijakham else { return }
        sijakham = true
        jinanKkeutSalpigi()
        let s = AVAudioSession.sharedInstance()
        jinanBollyum = s.outputVolume
        jinanBangsik = bangsikJul(s)
        Girok.shared.namgi("sori_cheo", sangtae(s))
        // 1. 볼륨 단추 — 폰 소리 크기
        bollyumGwanchal = s.observe(\.outputVolume, options: [.new]) { [weak self] ss, _ in
            DispatchQueue.main.async { self?.bollyumBakkwim(ss) }
        }
        let nc = NotificationCenter.default
        // 3. 나가는 곳·끊김
        nc.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] n in
            guard let self = self else { return }
            let r = (n.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt) ?? 0
            var d = self.sangtae(AVAudioSession.sharedInstance())
            d["kkadak"] = Int(r)
            Girok.shared.namgi("sori_gil", d)
        }
        nc.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] n in
            guard let self = self else { return }
            let t = (n.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt) ?? 0
            var d = self.sangtae(AVAudioSession.sharedInstance())
            d["kkeunkim"] = t == AVAudioSession.InterruptionType.began.rawValue ? "sijak" : "kkeut"
            Girok.shared.namgi("sori_kkeunkim", d)
        }
        // 5. 앞·뒤
        nc.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in
            guard let self = self else { return }
            self.ud.set("dwi", forKey: self.kApDwi)
            Girok.shared.namgi("app_dwi", self.sangtae(AVAudioSession.sharedInstance()))
            self.saraitda()
        }
        nc.addObserver(forName: UIApplication.willEnterForegroundNotification, object: nil, queue: .main) { [weak self] _ in
            guard let self = self else { return }
            self.ud.set("ap", forKey: self.kApDwi)
            Girok.shared.namgi("app_ap", self.sangtae(AVAudioSession.sharedInstance()))
        }
        nc.addObserver(forName: UIApplication.willTerminateNotification, object: nil, queue: .main) { [weak self] _ in
            guard let self = self else { return }
            self.ud.set(true, forKey: self.kKkeut)
            Girok.shared.namgi("app_kkeut", ["jeongsang": true])
            Girok.shared.jeojang()
        }
        nc.addObserver(forName: UIApplication.didReceiveMemoryWarningNotification, object: nil, queue: .main) { _ in
            Girok.shared.namgi("memori_gyeonggo", [:])
            Girok.shared.jeojang()
        }
        // 이번 판은 아직 정상으로 끝나지 않음 — 정상으로 끝나면 willTerminate 에서 true 로
        ud.set(false, forKey: kKkeut)
        saraitda()
        // 2·4. 1초마다 소리 방식 살피기, 10분마다 아직 나오는 중
        sigye = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in self?.salpigi() }
        // 7. 아이폰 고장 보고
        MXMetricManager.shared.add(self)
    }

    // MARK: 살피기

    private func bollyumBakkwim(_ s: AVAudioSession) {
        let v = s.outputVolume
        guard abs(v - jinanBollyum) >= 0.005 else { return }
        var d = sangtae(s)
        d["jeon"] = jarisu(jinanBollyum)
        d["hu"] = jarisu(v)
        jinanBollyum = v
        Girok.shared.namgi("sori_keugi", d)
    }

    private func salpigi() {
        let s = AVAudioSession.sharedInstance()
        let b = bangsikJul(s)
        if b != jinanBangsik {
            var d = sangtae(s)
            d["jeon"] = jinanBangsik
            d["nuga"] = SoriGirok.nuga
            jinanBangsik = b
            Girok.shared.namgi("sori_bangsik", d)
        }
        // 방송 크기가 길눈 말 때문에 줄어든 채 오래 남으면 되돌림(2.126.0 소리 하나로 쥐기)
        BangsongEngine.shared.jageunChaeNamumPulgi()
        // 2.127.0 방송이 나오는 동안 길눈이 말하지 않는데 소리 방식이 방송 방식(재생·기본·섞지 않음)이 아니면 바로잡음
        if BangsongEngine.shared.naneunJung, !SoriEngine.shared.bappeum, !MalDeutgi.shared.dolgoItda, !RemoteDanchu.shared.kyeojim,
           b != "Playback/Default/0" {
            BangsongEngine.shared.sesyeonJapgi()
            Girok.shared.namgi("bangsong_bangsik_barojapgi", ["jeon": b])
        }
        // 1분마다 살아 있음 표시, 방송이 나오는 동안 10분마다 기록
        let jigeum = Date()
        if jigeum.timeIntervalSince(saraitdaTtae) >= 60 { saraitda() }
        if BangsongEngine.shared.naneunJung, jigeum.timeIntervalSince(naoneunGirokTtae) >= 600 {
            naoneunGirokTtae = jigeum
            Girok.shared.namgi("bangsong_naoneunjung", sangtae(s))
        }
    }
    private var naoneunGirokTtae = Date()

    private func saraitda() {
        saraitdaTtae = Date()
        ud.set(Date().timeIntervalSince1970, forKey: kSarait)
        let b = BangsongEngine.shared
        ud.set("bangsong=\(b.jong.rawValue) naonda=\(b.naneunJung) ap=\(UIApplication.shared.applicationState == .active)", forKey: kHyeongpyeon)
    }

    /// 6. 지난번 끝 — 정상으로 끝났는지
    private func jinanKkeutSalpigi() {
        let t = ud.double(forKey: kSarait)
        guard t > 0 else { return }   // 처음 깐 폰
        let jeongsang = ud.bool(forKey: kKkeut)
        let f = DateFormatter()
        f.locale = Locale(identifier: "ko_KR")
        f.timeZone = TimeZone(identifier: "Asia/Seoul")
        f.dateFormat = "MM-dd HH:mm:ss"
        Girok.shared.namgi("jinan_kkeut", ["jeongsang": jeongsang,
                                          "majimak": f.string(from: Date(timeIntervalSince1970: t)),
                                          "hyeongpyeon": ud.string(forKey: kHyeongpyeon) ?? "",
                                          "apdwi": ud.string(forKey: kApDwi) ?? ""])
    }

    // MARK: 7. 아이폰 고장 보고(MetricKit)

    func didReceive(_ payloads: [MXDiagnosticPayload]) {
        for p in payloads {
            for c in p.crashDiagnostics ?? [] {
                Girok.shared.namgi("gojang_bogo", ["jong": "meomchum",
                                                   "pan": c.metaData.applicationBuildVersion,
                                                   "kkadak": c.terminationReason ?? "",
                                                   "yeoe": c.exceptionType?.intValue ?? -1,
                                                   "sinho": c.signal?.intValue ?? -1,
                                                   "ttae": "\(p.timeStampBegin)"])
            }
            for h in p.hangDiagnostics ?? [] {
                Girok.shared.namgi("gojang_bogo", ["jong": "eoreum", "gil": "\(h.hangDuration)", "ttae": "\(p.timeStampBegin)"])
            }
            for c in p.cpuExceptionDiagnostics ?? [] {
                Girok.shared.namgi("gojang_bogo", ["jong": "cpu", "gil": "\(c.totalCPUTime)", "ttae": "\(p.timeStampBegin)"])
            }
        }
        Girok.shared.jeojang()
        Girok.shared.bonaegi()
    }

    // MARK: 줄 만들기

    private func bangsikJul(_ s: AVAudioSession) -> String {
        let k = s.category.rawValue.replacingOccurrences(of: "AVAudioSessionCategory", with: "")
        let m = s.mode.rawValue.replacingOccurrences(of: "AVAudioSessionMode", with: "")
        return "\(k)/\(m)/\(s.categoryOptions.rawValue)"
    }

    private func sangtae(_ s: AVAudioSession) -> [String: Any] {
        let b = BangsongEngine.shared
        return ["bangsik": bangsikJul(s),
                "bollyum": jarisu(s.outputVolume),
                "chulryeok": s.currentRoute.outputs.first?.portType.rawValue ?? "",
                "bangsong": b.jong.rawValue,
                "naonda": b.naneunJung,
                "bangsongKeugi": jarisu(b.player.volume),
                "malhaneunjung": SoriEngine.shared.bappeum,
                "vo": UIAccessibility.isVoiceOverRunning,
                "ap": UIApplication.shared.applicationState == .active]
    }

    private func jarisu(_ v: Float) -> Double { (Double(v) * 100).rounded() / 100 }
}
