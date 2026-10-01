// 말로 하기 — 길눈을 말로 시킵니다(이사장님 발안 2026-09-17, 앱 2.5.0 빌드 260928-7).
// 2.12.1 (빌드 260929-1) 하이 길눈 — 길눈이 말할 때 마이크를 닫지 않고 알아듣기만 쉼(잠긴 폰에서도 이어지게)
// 2.12.2 (빌드 260929-2) 걸러 듣기로 방송·안내 말 중에도 부름을 듣고, 부름을 들으면 길눈 소리를 멈추고 다른 소리를 낮춤
// 2.12.3 (빌드 260929-3) 걸러 듣기·소리 낮추기를 뺌. 명령을 기다리는 중 "하이 길눈"만 들리면 "네" 하고 다시 기다림
// 2.12.4 (빌드 260929-4) 멈춤 풀기(25초 넘게 한 자리에 멈추면 처음으로, 30초 넘게 부름을 못 기다리면 다시 엶)와 걸음마다 기록
// 2.12.5 (빌드 260929-5, 이사장님 승인 1) 역 이름 찾기 — 한글 번호(오 번→5번), 줄인 역 이름(제기역→제기동역), 가까운 역·즐겨찾기를 받아쓰기에 미리 알림,
//   되묻고 기다리는 시간 10초, 길거리 음악
// 웹 길눈에서 이사장님이 정하신 것을 앱의 알맹이로 옮겼습니다.
//   부르기: 말로 하기 단추, 보이스오버 두 손가락 두 번 두드리기, "하이 길눈"(설정에서 켬), 화면이 잠긴 채 "시리야, 길눈"
//   알아듣기: 아이폰 자체 받아쓰기(폰 안, 무료) + 웹과 같은 나스 알아듣기 사전
//   명령 먼저: 묻는 중이라도 네·아니오가 아니면 새 명령을 따름
//   목적지를 알아들으면 되묻지 않고 곧바로 안내(가자는 말 없이 이름만 들리면 한 번 여쭘)
//   "그만"은 어디서나, 급한 순서는 긴급통화 > 경고 > 길 안내
//   대답을 마치면 땡 소리, 묻는 말에는 마이크를 한 번만 저절로 엶
//   모르는 말은 사과하고 기록해 두었다가 사전을 키움
// 2.30.0 (빌드 261001-10, 대표님 승인 1) 딩동 대신 또렷한 목소리 「네」, 땡 대신 「잠깐만 기다려 주세요」(말소리를 끈 분께는 딩동·땡 그대로).
//   하이 길눈을 알아들은 귀가 그대로 명령을 들음(귀를 하나로). 못 들으면 「다시 한번 말씀해 주세요」 한 번만, 그래도 못 들으면 조용히 물러남.
//   명령 글자가 0이면 마이크 막힘(maik_makhim)을 따로 남김.
import Foundation
import UIKit
import AVFoundation
import Combine
import SwiftUI   // 2.7.0 둘러보기 화면 길(NavigationPath)

final class MalHagi: ObservableObject {
    static let shared = MalHagi()

    enum Sangtae { case swim, deutneun, araboneun }
    @Published private(set) var sangtae: Sangtae = .swim { didSet { sangtaeTtae = Date() } }
    // 2.12.4 멈춤 풀기
    private var sangtaeTtae = Date()
    private var bureumEopseumTtae: Date?
    private var gamsiSigye: Timer?
    @Published private(set) var deureunMal = ""
    @Published private(set) var dapMal = ""

    private enum Mureum { case eopseum, mokjeok, bangsik, chaYocheong, kol, galrae, hoching, hubo, jeom }
    private var mureum: Mureum = .eopseum
    private var mureumTtae = Date.distantPast
    private var mok: Jangso?
    private var talgeotDaegi: Talgeot?
    private var hubo: [Jangso] = []
    private var huboI = 0
    private var huboTalgeot: Talgeot?
    private var huboHwagin = false
    private var motBeon = 0
    private var motTtae = Date.distantPast
    private var jadongYeolim = 0
    private var bureumDolgo = false
    private var bureumYeyak = false
    private var bureumSilpae = 0   // 2.12.0 뒤에서 부름 기다리기를 거듭 못 열면 앱으로 돌아올 때까지 쉼
    private var ijeonMal = ""
    /// 2.30.0 「다시 한번 말씀해 주세요」를 이미 했음(한 번만)
    private var dasiHanbeon = false
    private var ssak = Set<AnyCancellable>()
    private var sijakham = false

    private var sajeon: MalSajeon { MalSajeon.shared }
    private var ho: String { Seoljeong.shared.ho }

    // MARK: 세우기

    func sijak() {
        guard !sijakham else { return }
        sijakham = true
        sajeon.bureogi()
        SoriEngine.shared.malSijakHook = { [weak self] in self?.malSijakham() }
        gamsiSigye = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { [weak self] _ in self?.gamsi() }
        Seoljeong.shared.$haiGilnun
            .dropFirst()
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] on in self?.haiGilnunBakkwim(on) }
            .store(in: &ssak)
        // 2.12.2 "방송 중에도 하이 길눈 듣기"를 바꾸시면 마이크를 새로 엶
        Seoljeong.shared.$haiBangsongDeutgi
            .dropFirst()
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self = self, self.sangtae == .swim, MalDeutgi.shared.dolgoItda else { return }
                MalDeutgi.shared.meomchugi()
                self.bureumDolgo = false
                self.bureumDasi(0.5)
            }
            .store(in: &ssak)
        // 긴급통화 중에는 마이크를 통화에 내어 줌
        GinGeup.shared.$sangtae
            .receive(on: DispatchQueue.main)
            .sink { [weak self] s in
                guard let self = self else { return }
                if s != .eopseum { self.moduMeomchum() } else { self.bureumDasi(1.5) }
            }
            .store(in: &ssak)
        let nc = NotificationCenter.default
        nc.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] n in
            guard let self = self,
                  let v = n.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                  let ty = AVAudioSession.InterruptionType(rawValue: v) else { return }
            if ty == .began { self.moduMeomchum() } else { self.bureumDasi(1.0) }
        }
        nc.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] n in
            guard let self = self, self.bureumDolgo,
                  let v = n.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt,
                  let r = AVAudioSession.RouteChangeReason(rawValue: v),
                  r == .newDeviceAvailable || r == .oldDeviceUnavailable else { return }
            // 이어폰을 꽂거나 빼면 그 마이크로 다시 엶
            MalDeutgi.shared.meomchugi()
            self.bureumDolgo = false
            self.bureumDasi(0.8)
        }
        nc.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
            self?.bureumSilpae = 0
            self?.bureumDasi(1.0)
        }
        // 2.12.6 폰 잠금을 풀면 듣기를 포기했던 것을 다시 셈하고 엶
        nc.addObserver(forName: UIApplication.protectedDataDidBecomeAvailableNotification, object: nil, queue: .main) { [weak self] _ in
            self?.bureumSilpae = 0
            self?.bureumDasi(1.0)
        }
    }

    // MARK: 부르기

    /// 말로 하기 단추·보이스오버 두 손가락 두 번 두드리기·시리로 앱을 열 때
    func dudeurim() {
        DispatchQueue.main.async {
            self.sijak()
            if self.sangtae == .deutneun {
                self.myeongryeongChwiso()
                SoriEngine.shared.sori(.ttaeng)
                self.bureumDasi(0.8)
                return
            }
            self.jadongYeolim = 0
            self.dasiHanbeon = false
            self.ijeonMal = SoriEngine.shared.majimak
            self.myeongryeongYeolgi(sori: true)
        }
    }

    private func myeongryeongYeolgi(sori: Bool) {
        guard GinGeup.shared.sangtae == .eopseum else { return }
        if !MalDeutgi.heorakItda {
            MalDeutgi.heorak { [weak self] ok in
                guard let self = self else { return }
                if ok {
                    self.myeongryeongYeolgi(sori: sori)
                } else {
                    self.malHam("말로 하기에는 마이크와 음성 인식 허락이 필요합니다. 아이폰 설정의 길눈에서 마이크와 음성 인식을 켜 주십시오.") {}
                }
            }
            return
        }
        // 2.30.0 하이 길눈을 알아들은 귀가 이어 들을 때는 끄지 않음(귀를 하나로)
        if !MalDeutgi.shared.saeIeum { MalDeutgi.shared.swigi() }   // 2.12.1 마이크는 열어 둔 채 알아듣기만 바꿈
        bureumDolgo = false
        SoriEngine.shared.modu_geodugi()   // 명령 먼저 — 하던 말을 멈춤(경고는 남김)
        sangtae = .deutneun
        SoriEngine.shared.myeongryeongDeutneunJung = true   // 2.12.0 마이크를 여는 틈에 다른 말이 끼어 닫지 않게
        let yeolgi = { [weak self] in
            guard let self = self, self.sangtae == .deutneun else { return }
            SoriEngine.shared.myeongryeongDeutneunJung = true
            MalDeutgi.shared.doumMal = self.doumMal()   // 2.12.5 가까운 역·즐겨찾기
            let gidarim: Double = self.jadongYeolim > 0 ? 10 : 6   // 2.12.5 되물은 뒤에는 10초 기다림
            let ok = MalDeutgi.shared.myeongryeong(gidarim: gidarim) { [weak self] alts in self?.deureum(alts) }
            Girok.shared.namgi("myeong_yeolgi", ["ok": ok])
            if !ok {
                SoriEngine.shared.myeongryeongDeutneunJung = false
                self.sangtae = .swim
                self.malHam("지금은 마이크를 열지 못했습니다. 잠시 뒤 다시 해 주십시오.") { [weak self] in self?.bureumDasi(1.0) }
            }
        }
        if sori && Seoljeong.shared.malKyeojim && jadongYeolim == 0 {
            // 2.30.0 딩동 대신 또렷한 「네」 — 말하는 동안 귀를 닫고, 다 말한 뒤 곧바로 엶
            MalDeutgi.shared.gwiDatgi(true)
            Girok.shared.namgi("ne", ["gwi": MalDeutgi.shared.saeIeum ? "hana" : "sae"])
            SoriEngine.shared.daehwaMal("네") {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.12, execute: yeolgi)
            }
        } else if sori {
            SoriEngine.shared.sori(.dingdong)   // 2.26.0 딩동 — 이제 말씀하십시오(말소리를 끈 분, 되물은 뒤)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.55, execute: yeolgi)
        } else {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15, execute: yeolgi)
        }
    }

    private func myeongryeongChwiso() {
        MalDeutgi.shared.swigi()
        SoriEngine.shared.myeongryeongDeutneunJung = false
        sangtae = .swim
    }

    /// 명령을 다 들었음
    private func deureum(_ alts0: [String]) {
        SoriEngine.shared.myeongryeongDeutneunJung = false
        let beopeo = MalDeutgi.shared.majimakBeopeo
        Girok.shared.namgi("myeong_deureum", ["su": alts0.count, "sangtae": sangtae == .deutneun, "beopeo": beopeo,
                                              "sae": MalDeutgi.shared.saeSseumJung])
        guard sangtae == .deutneun else { return }
        let alts = alts0.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        if alts.isEmpty {
            // 2.30.0 명령 글자가 0 — 마이크 막힘을 따로 남김(beopeo 0이면 마이크에 소리가 안 들어옴, 아니면 알아듣기가 못 적음)
            Girok.shared.namgi("maik_makhim", ["dan": "myeongryeong", "beopeo": beopeo, "dasi": dasiHanbeon,
                                               "sae": MalDeutgi.shared.saeSseumJung])
            sangtae = .swim
            if jadongYeolim > 0 {
                // 저절로 연 마이크에 말씀이 없으면 조용히 닫음
                jadongYeolim = 0
                MalDeutgi.shared.swigi()
                if !Seoljeong.shared.malKyeojim { SoriEngine.shared.sori(.ttaeng) }
                bureumDasi(0.8)
                return
            }
            if !dasiHanbeon && GinGeup.shared.sangtae == .eopseum {
                // 2.30.0 한 번만 「다시 한번 말씀해 주세요」 하고 같은 귀로 한 번 더 들음
                dasiHanbeon = true
                if Seoljeong.shared.malKyeojim {
                    sangtae = .deutneun
                    SoriEngine.shared.myeongryeongDeutneunJung = true
                    MalDeutgi.shared.gwiDatgi(true)
                    SoriEngine.shared.daehwaMal("다시 한번 말씀해 주세요") { [weak self] in
                        guard let self = self, self.sangtae == .deutneun else { return }
                        self.myeongryeongYeolgi(sori: false)
                    }
                } else {
                    myeongryeongYeolgi(sori: true)   // 말소리를 끄셨으면 딩동으로 다시 말씀하실 차례를 알림
                }
                return
            }
            // 그래도 못 들으면 조용히 물러남
            dasiHanbeon = false
            MalDeutgi.shared.swigi()
            if !Seoljeong.shared.malKyeojim { SoriEngine.shared.sori(.ttaeng) }
            Girok.shared.namgi("dap_kkeut", ["mureoNam": true])
            bureumDasi(0.8)
            return
        }
        dasiHanbeon = false
        // 2.12.3 "하이 길눈"만 들렸으면(대답을 못 들어 한 번 더 부르심) 명령으로 치지 않고 "네" 하고 다시 기다림
        if alts.contains(where: { MalDeutgi.bureumMal($0) && MalSajeon.ttuk($0).count <= 6 }) {
            Girok.shared.namgi("hai_dasi", [:])
            sangtae = .swim
            bureumDeureum()
            return
        }
        deureunMal = alts[0]
        sangtae = .araboneun
        if Seoljeong.shared.malKyeojim {
            // 2.30.0 (대표님 지시) 땡 대신 「잠깐만 기다려 주세요」 — 귀는 이미 닫음. 일을 하는 동안 말하고, 다 말한 뒤 결과를 말씀드림
            var malKkeut = false
            var dap: (String, Bool)?
            SoriEngine.shared.daehwaMal("잠깐만 기다려 주세요") { [weak self] in
                malKkeut = true
                if let d = dap { self?.dapHagi(d.0, d.1) }
            }
            cheori(alts) { [weak self] t, mutneun in
                DispatchQueue.main.async {
                    guard dap == nil else { return }
                    dap = (t, mutneun)
                    if malKkeut { self?.dapHagi(t, mutneun) }
                }
            }
            return
        }
        // 2.26.0 (대표님 지시) 말씀을 다 들으면 땡 — 그때서야 길눈 목소리로 결과를 말함(말소리를 끄신 분)
        SoriEngine.shared.sori(.ttaeng)
        let ttaengT = Date()
        cheori(alts) { [weak self] t, mutneun in
            let nameun = max(0, 0.35 - Date().timeIntervalSince(ttaengT))
            DispatchQueue.main.asyncAfter(deadline: .now() + nameun) { self?.dapHagi(t, mutneun) }
        }
    }

    /// 대답하고, 묻는 말이면 마이크를 한 번만 저절로 엶. 아니면 땡 소리로 마침
    private func dapHagi(_ t: String, _ mutneun: Bool) {
        sangtae = .swim
        if !t.isEmpty { dapMal = t }
        malHam(t) { [weak self] in
            guard let self = self else { return }
            if mutneun && self.jadongYeolim < 1 && GinGeup.shared.sangtae == .eopseum {
                self.jadongYeolim += 1
                self.myeongryeongYeolgi(sori: true)
            } else {
                self.jadongYeolim = 0
                Girok.shared.namgi("dap_kkeut", [:])
                self.bureumDasi(0.8)   // 2.26.0 땡은 대답 앞에서 이미 울림
            }
        }
    }

    /// 길눈이 말하기 — 말소리를 꺼 두셨으면 보이스오버로
    private func malHam(_ t: String, _ ihu: @escaping () -> Void) {
        if t.isEmpty {
            SoriEngine.shared.kkeutnamyeon(ihu)
            return
        }
        if Seoljeong.shared.malKyeojim {
            SoriEngine.shared.mal(t, .annae)
            SoriEngine.shared.kkeutnamyeon(ihu)
        } else {
            UIAccessibility.post(notification: .announcement, argument: t)
            DispatchQueue.main.asyncAfter(deadline: .now() + min(8, 0.8 + Double(t.count) * 0.09), execute: ihu)
        }
    }

    // MARK: 하이 길눈

    private func haiGilnunBakkwim(_ on: Bool) {
        if on {
            MalDeutgi.heorak { [weak self] ok in
                guard let self = self else { return }
                if ok {
                    self.malHam("이제 하이 길눈이라고 부르시면 대답합니다.") { [weak self] in self?.bureumDasi(0.5) }
                } else {
                    Seoljeong.shared.haiGilnun = false
                    self.malHam("하이 길눈에는 마이크와 음성 인식 허락이 필요합니다. 아이폰 설정의 길눈에서 켜 주십시오.") {}
                }
            }
        } else {
            if sangtae != .deutneun { MalDeutgi.shared.meomchugi() }   // 2.12.1 마이크를 아주 닫음
            bureumDolgo = false
            SoriEngine.shared.deutgiKyeojim = false
        }
    }

    /// 부름 기다리기를 다시 엶 — 길눈이 말하는 중이면 다 말한 뒤에
    private func bureumDasi(_ dwi: Double) {
        if sangtae == .swim { bureumSoriDollim() }   // 2.12.2 명령을 마쳤으면 멈췄던 소리를 되돌림
        guard !bureumYeyak else { return }
        bureumYeyak = true
        DispatchQueue.main.asyncAfter(deadline: .now() + dwi) { [weak self] in
            guard let self = self else { return }
            self.bureumYeyak = false
            guard Seoljeong.shared.haiGilnun, self.sangtae == .swim, !self.bureumDolgo,
                  GinGeup.shared.sangtae == .eopseum, MalDeutgi.heorakItda else {
                if !Seoljeong.shared.haiGilnun {
                    SoriEngine.shared.deutgiKyeojim = false
                    // 2.12.1 하이 길눈을 꺼 두셨으면 말로 하기를 마친 뒤 마이크를 닫음
                    if self.sangtae == .swim && MalDeutgi.shared.dolgoItda { MalDeutgi.shared.meomchugi() }
                }
                return
            }
            if SoriEngine.shared.bappeum {
                SoriEngine.shared.kkeutnamyeon { [weak self] in self?.bureumDasi(0.6) }
                return
            }
            SoriEngine.shared.deutgiKyeojim = true
            let ok = MalDeutgi.shared.bureum(deureum: { [weak self] in
                self?.bureumDeureum()
            }, kkeunkim: { [weak self] in
                self?.bureumDolgo = false
                self?.bureumDasi(0.3)
            })
            self.bureumDolgo = ok
            Girok.shared.namgi("bureum_yeolgi", ["ok": ok])
            if ok { self.bureumSilpae = 0; return }
            self.bureumSilpae += 1
            if UIApplication.shared.applicationState == .background && self.bureumSilpae >= 3 {
                Girok.shared.namgi("bureum_swim", [:])   // 앱으로 돌아오시면 다시 엶
                return
            }
            self.bureumDasi(10)
        }
    }

    /// 2.12.2 "하이 길눈"을 들은 동안 멈추고 낮췄던 소리를 되돌림
    private var bureumSoriJurim = false
    private func bureumSoriDollim() {
        MalDeutgi.shared.moduSoriMeomchum(false)   // 2.26.0 멈췄던 다른 앱 소리도 되돌림
        guard bureumSoriJurim else { return }
        bureumSoriJurim = false
        BangsongEngine.shared.bureumMeomchum(false)
        MalDeutgi.shared.dareunSori(jurim: false)
    }

    /// 길눈이 막 말하려 함 — 부름 기다리기를 잠시 닫고 다 말한 뒤 다시 엶. 명령 듣는 중 경고가 오면 경고부터
    private func malSijakham() {
        if sangtae == .deutneun {
            myeongryeongChwiso()
            return
        }
        // 2.12.2 걸러 듣기 중이면 길눈이 말하는 동안에도 부름을 들음(말에 "길눈"이 들면 그때만 쉼)
        if bureumDolgo && MalDeutgi.shared.georeunda && !SoriEngine.shared.hanunMal.contains("길눈") { return }
        if bureumDolgo {
            MalDeutgi.shared.swigi()   // 2.12.1 마이크는 열어 둔 채 쉼 — 잠긴 폰에서도 다시 듣게
            bureumDolgo = false
            bureumDasi(0.6)
        }
    }

    /// "하이 길눈"을 들음 — "네" 하고 명령을 들음
    private func bureumDeureum() {
        bureumDolgo = false
        guard sangtae == .swim, GinGeup.shared.sangtae == .eopseum else { return }
        jadongYeolim = 0
        dasiHanbeon = false
        ijeonMal = SoriEngine.shared.majimak
        gakkaunYeokGaengsin()   // 2.12.5
        // 2.12.2 부름을 들으면 곧바로 길눈 방송을 멈추고 다른 앱 소리를 크게 낮춤(명령을 마치면 되돌림)
        bureumSoriJurim = true
        BangsongEngine.shared.bureumMeomchum(true)
        MalDeutgi.shared.dareunSori(jurim: true)
        SoriEngine.shared.modu_geodugi()
        // 2.26.0 (대표님 지시) "하이 길눈"하고 끊으시면 곧바로 — 폰의 모든 소리를 멈추고 다음 말씀을 기다림
        // 2.30.0 말소리를 켜 두셨으면 딩동 대신 또렷한 「네」(myeongryeongYeolgi 에서)
        MalDeutgi.shared.moduSoriMeomchum(true)
        if !Seoljeong.shared.malKyeojim { Girok.shared.namgi("dingdong", ["bappeum": SoriEngine.shared.bappeum]) }
        myeongryeongYeolgi(sori: true)
    }

    // MARK: 2.12.4 멈춤 풀기 — 어디서 막히든 하이 길눈이 영영 먹통이 되지 않게

    private func gamsi() {
        guard Seoljeong.shared.haiGilnun, GinGeup.shared.sangtae == .eopseum else { bureumEopseumTtae = nil; return }
        let now = Date()
        if sangtae != .swim {
            bureumEopseumTtae = nil
            guard now.timeIntervalSince(sangtaeTtae) > 25 else { return }
            Girok.shared.namgi("malhagi_pulgi", ["sangtae": sangtae == .deutneun ? "deutneun" : "araboneun",
                                                 "bappeum": SoriEngine.shared.bappeum])
            MalDeutgi.shared.swigi()
            SoriEngine.shared.myeongryeongDeutneunJung = false
            jadongYeolim = 0
            sangtae = .swim
            bureumDolgo = false
            bureumDasiGangje()
            return
        }
        if bureumDolgo || bureumYeyak { bureumEopseumTtae = nil; return }
        if UIApplication.shared.applicationState == .background && bureumSilpae >= 3 { return }
        guard let t0 = bureumEopseumTtae else { bureumEopseumTtae = now; return }
        guard now.timeIntervalSince(t0) > 30 else { return }
        Girok.shared.namgi("bureum_gangje", ["bappeum": SoriEngine.shared.bappeum])
        bureumEopseumTtae = nil
        bureumDasiGangje()
    }

    /// 길눈이 말을 마치기를 기다리지 않고 곧바로 부름 기다리기를 엶
    private func bureumDasiGangje() {
        bureumYeyak = false
        bureumSoriDollim()
        guard sangtae == .swim, !bureumDolgo, MalDeutgi.heorakItda else { return }
        SoriEngine.shared.deutgiKyeojim = true
        let ok = MalDeutgi.shared.bureum(deureum: { [weak self] in
            self?.bureumDeureum()
        }, kkeunkim: { [weak self] in
            self?.bureumDolgo = false
            self?.bureumDasi(0.3)
        })
        bureumDolgo = ok
        Girok.shared.namgi("bureum_yeolgi", ["ok": ok, "gangje": true])
    }

    private func moduMeomchum() {
        SoriEngine.shared.daehwaGeuman()   // 2.30.0
        bureumSoriDollim()
        MalDeutgi.shared.meomchugi()
        bureumDolgo = false
        SoriEngine.shared.myeongryeongDeutneunJung = false
        SoriEngine.shared.deutgiKyeojim = false
        if sangtae == .deutneun { sangtae = .swim }
    }

    // MARK: 시리

    /// 시리로 받은 말 — 길눈의 대답을 시리가 읽어 줌
    func siriCheori(_ t: String) async -> String {
        await withCheckedContinuation { (c: CheckedContinuation<String, Never>) in
            DispatchQueue.main.async {
                self.sijak()
                var kkeut = false
                self.deureunMal = t
                self.cheori([t]) { d, _ in
                    guard !kkeut else { return }
                    kkeut = true
                    if !d.isEmpty { self.dapMal = d }
                    c.resume(returning: d.isEmpty ? "알겠습니다." : d)
                }
            }
        }
    }

    // MARK: 워치에서 온 말 (2.6.0)

    /// 손목(워치)에 말씀하신 글 — 폰 길눈이 알아듣고 폰에서 대답을 말하며, 대답 글을 워치에 돌려줌
    func bakkatCheori(_ t: String, _ kkeut: @escaping (String) -> Void) {
        DispatchQueue.main.async {
            self.sijak()
            let s = t.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !s.isEmpty else { kkeut("말씀이 들리지 않았습니다."); return }
            if self.sangtae == .deutneun { self.myeongryeongChwiso() }
            MalDeutgi.shared.swigi()
            self.bureumDolgo = false
            self.deureunMal = s
            self.jadongYeolim = 1   // 워치에서 온 말에는 폰 마이크를 저절로 열지 않음
            self.sangtae = .araboneun
            var han = false
            self.cheori([s]) { d, m in
                guard !han else { return }
                han = true
                self.dapHagi(d, m)
                kkeut(d.isEmpty ? "알겠습니다." : d)
            }
        }
    }

    // MARK: 알아듣고 하기

    /// 알아들은 말들로 할 일을 하고, 대답(dap)을 꼭 한 번 부름 — (할 말, 묻는 말인가)
    func cheori(_ alts0: [String], _ dap: @escaping (String, Bool) -> Void) {
        let alts = alts0.map { MalHagi.beonhoSutja($0.trimmingCharacters(in: .whitespacesAndNewlines)) }.filter { !$0.isEmpty }
        guard let t = alts.first else { dap("말씀이 들리지 않았습니다.", false); return }
        Girok.shared.namgi("malhagi", ["mal": String(t.prefix(60))])
        let z = MalSajeon.ttuk(t)
        let y = YeojeongEngine.shared.jigeum
        if Date().timeIntervalSince(mureumTtae) > 180 { mureum = .eopseum }

        // 0. 2.12.6 하던 일 멈추기 — 안내·따라 걷기·묻던 말을 모두 멈춤(음악·방송은 그대로)
        if sajeon.itda(alts, "hadeon_meomchum") && z.count <= 10 {
            dap("", false)
            AnnaeEngine.shared.haneunIlMeomchum()
            return
        }
        // 1. 여정 끝내기
        if sajeon.itda(alts, "yeojeong_kkeut") {
            mureum = .eopseum
            if y != nil {
                dap("", false)
                AnnaeEngine.shared.kkeut()
            } else {
                dap("지금 가시는 여정이 없습니다.", false)
            }
            return
        }
        // 1-2. 2.12.7 도착 — "도착", "도착했어", "다 왔어"를 목적지 이름으로 찾지 않고 여정 끝내기로(이사장님 승인 1)
        let dochakMal = ["도착", "도착했어", "도착했다", "도착했어요", "도착했습니다", "도착이야", "도착했네", "다왔어", "다왔다", "다왔어요", "다왔습니다", "다왔네", "도착완료", "여기도착"]
        if dochakMal.contains(z) || (z.hasPrefix("도착") && z.count <= 6 && !z.contains("까지") && !z.contains("시간")) {
            mureum = .eopseum
            if y != nil {
                dap("도착하셨습니다. 여정을 마칩니다.", false)
                AnnaeEngine.shared.kkeut()
            } else {
                dap("지금 가시는 여정이 없습니다. 가실 곳을 말씀하시려면 어디로 가자라고 해 주십시오.", false)
            }
            return
        }
        // 2. 긴급통화 중 그만·끊어
        if GinGeup.shared.sangtae != .eopseum && (sajeon.itda(alts, "geuman") || z.contains("끊어")) {
            dap("", false)
            GinGeup.shared.geumanhagi()
            return
        }
        // 2.10.0 점지도 — 점지도로 걸을까요의 대답, 따라 걷는 중의 명령("그만 걷기"는 아래 "그만"보다 먼저)
        let jm = JeomEngine.shared
        if jm.muleum != nil {
            let ye0 = sajeon.tteut(alts, "ye") != nil, ani0 = sajeon.tteut(alts, "ani") != nil
            if z.contains("위성") || (mureum == .jeom && ani0 && !ye0 && z.count <= 10) {
                mureum = .eopseum
                dap("위성으로 걷습니다.", false)
                jm.muleumDap(jeom: false)
                return
            }
            if z.contains("점지도") || (mureum == .jeom && ye0 && !ani0 && z.count <= 10) {
                mureum = .eopseum
                dap("점지도로 걷습니다.", false)
                jm.muleumDap(jeom: true)
                return
            }
        }
        if jm.gil != nil {
            if z.contains("그만걷") || z.contains("따라걷기그만") || z.contains("따라걷기끝") || z.contains("걷기그만") {
                dap("", false)
                jm.geuman()
                return
            }
            if z.contains("다음에무엇") || z.contains("다음에뭐") || z.contains("다음은뭐") || z.contains("다음표시") || z.contains("앞에뭐") {
                dap(jm.daeumMuotMal(), false)
                return
            }
            if jm.dochakHam && z.contains("되돌아") {
                dap("", false)
                jm.doedoragagi()
                return
            }
            if z.contains("문제있") || z.contains("여기문제") {
                dap("무슨 문제인지 고르시는 화면을 엽니다.", false)
                TabGil.shared.tab = 0
                GilGil.shared.cheotHwamyeon()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { GilGil.shared.path.append(GilHwamyeon.munje) }
                return
            }
            if z.contains("길목") {
                dap("", false)
                jm.gilmok()
                return
            }
            if z.contains("정류장") && !z.contains("까지") {
                dap("", false)
                jm.beoseuJeongryujang()
                return
            }
            if z.contains("다른문") {
                dap("", false)
                jm.dareunMun()
                return
            }
            if z.contains("여기걸렸") {
                dap("", false)
                jm.geollimNamgigi()
                return
            }
        }
        if z.contains("점지도") && (z.contains("가까운") || z.contains("근처") || z.contains("목록") || z.contains("찾아")) {
            dap("가까운 점지도를 엽니다. 골라서 누르시면 따라 걷습니다.", false)
            TabGil.shared.tab = 0
            GilGil.shared.cheotHwamyeon()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { GilGil.shared.path.append(GilHwamyeon.jeomMok) }
            return
        }
        // 3. 그만 — 어디서나
        if sajeon.itda(alts, "geuman") && z.count <= 8 {
            mureum = .eopseum
            SinhogiEngine.shared.chatgiKkeugi()
            SoriEngine.shared.modu_geodugi()
            if BangsongEngine.shared.naneunJung { BangsongEngine.shared.meomchumTogeul() }   // 2.8.0 방송도 멈춤(이어서 틀어로 다시)
            MalgilEngine.shared.geuman()   // 2.14.0 말로 그린 길 안내도 그만
            MunChatgi.shared.kkeugi()      // 2.15.0 문 찾기도 그만
            GeulIlgi.shared.kkeugi()       // 2.16.0 즉석 글자 읽기도 그만
            GarikiIlgi.shared.kkeugi()     // 2.17.0 가리키고 말하기도 그만
            SaramGamji.shared.kkeugi()     // 2.18.0 사람 감지도 그만
            SangpumIlgi.shared.kkeugi()    // 2.20.0 상품 바코드 읽기도 그만
            JipyeSaek.shared.kkeugi()      // 2.21.0 지폐와 색깔 알아보기도 그만
            BitAlgi.shared.kkeugi()        // 2.22.0 빛 알아보기도 그만
            Hanmadi.shared.kkeugi()        // 2.23.0 한마디 설명도 그만
            TeokAllim.shared.kkeugi()      // 2.24.0 발 앞 계단·턱 알림도 그만
            dap("", false)
            return
        }

        // 4. 묻던 말의 대답
        let ye = sajeon.tteut(alts, "ye") != nil
        let ani = sajeon.tteut(alts, "ani") != nil
        let jjalbeun = z.count <= 10
        switch mureum {
        case .hoching:
            mureum = .eopseum
            var h = t
            for k in ["이라고 불러 줘", "라고 불러 줘", "이라고 불러줘", "라고 불러줘", "이라고 불러", "라고 불러",
                      "이라고 해 줘", "라고 해 줘", "이라고", "라고", "으로 불러 줘", "로 불러 줘", "으로 불러", "로 불러"] where h.hasSuffix(k) {
                h = String(h.dropLast(k.count))
                break
            }
            h = h.trimmingCharacters(in: .whitespaces)
            if h.isEmpty || (ani && jjalbeun) {
                dap("알겠습니다. 호칭은 그대로 \(ho)입니다.", false)
                return
            }
            Seoljeong.shared.hoching = h
            dap("알겠습니다. 이제 \(h)\(MalHagi.irago(h)) 부르겠습니다.", false)
            return
        case .galrae:
            if gingeupJikjeop(z, dap) { return }
            if z.contains("가족") || z.contains("지인") {
                gingeup(t, dap)
                return
            }
        case .kol:
            if let jong = kolJong(alts) {
                mureum = .eopseum
                kolGeolgi(jong, dap)
                return
            }
            if ani && jjalbeun {
                mureum = .eopseum
                dap("알겠습니다. 차에 타시면 차에 탔어라고 말씀해 주십시오.", false)
                return
            }
        case .chaYocheong:
            if let jong = kolJong(alts) {
                mureum = .eopseum
                kolGeolgi(jong, dap)
                return
            }
            if ye && !ani && jjalbeun {
                mureum = .kol
                mureumTtae = Date()
                dap(kolMutgi(), true)
                return
            }
            if ani && jjalbeun {
                mureum = .eopseum
                dap("알겠습니다. 차에 타시면 차에 탔어라고 말씀해 주십시오.", false)
                return
            }
        case .bangsik:
            if let m = mok, let tg = talgeotChatgi(alts), z.count <= 10 {
                mureum = .eopseum
                gagi(m, tg, dap)
                return
            }
            if ani && jjalbeun {
                mureum = .eopseum
                dap("알겠습니다.", false)
                return
            }
        case .hubo:
            if huboHwagin && ye && !ani && jjalbeun && huboI < hubo.count {
                huboHwagin = false
                mureum = .eopseum
                let h = hubo[huboI]
                gagi(h, huboTalgeot, dap, apMal: "\(h.ireum)\(MalHagi.ro(h.ireum)) 안내합니다. ")
                return
            }
            if (ani && jjalbeun) || z.contains("다른곳") || z.contains("다른데") {
                daeumHubo(dap)
                return
            }
        case .mokjeok, .eopseum, .jeom:
            break
        }

        // 5. 긴급통화 — 가장 급한 일
        if sajeon.itda(alts, "doum") || z.contains("화상통화") || z.contains("영상통화") || z.contains("긴급통화") {
            if gingeupJikjeop(z, dap) { return }
            gingeup(t, dap)
            return
        }

        // 6. 목적지 말을 떼어 보고, 가자는 말이 없으면 명령부터 살핌
        let (q0, gagiMal, roTtem) = sajeon.mokjeokMal(t)
        let tg = talgeotChatgi(alts)
        var q = MalHagi.talgeotTteokgi(q0)
        // "서울역까지 버스로 가자"처럼 한 번 더 뗌(두 자 넘는 것만)
        let (q2, g2, _) = sajeon.mokjeokMal(q)
        if g2 && MalSajeon.ttuk(q2).count >= 2 { q = q2 }
        // "지금 가는 길 알려 줘"처럼 떼고 나서 남는 것이 없으면 명령으로 봄
        if (!gagiMal || MalSajeon.ttuk(q).count <= 2) && myeongryeong(alts, z, tg, dap) { return }

        // 7. 목적지 — 이름 끝의 로(종로·을지로)는 되살려 한 번 더 찾아봄
        var qB: String? = roTtem ? q + "로" : nil
        for p in ["으로", "까지", "에게", "한테", "로", "에"] where q.hasSuffix(p) && q.count > p.count + 1 {
            if p == "로" { qB = q }
            q = String(q.dropLast(p.count)).trimmingCharacters(in: .whitespaces)
            break
        }
        let mureunJung = (mureum == .mokjeok)
        if q.isEmpty {
            mureum = .mokjeok
            mureumTtae = Date()
            if let tg = tg { talgeotDaegi = tg }
            dap("\(ho), 어디로 가실까요?", true)
            return
        }
        let hwagin = !(gagiMal || mureunJung || tg != nil)
        let taltgeot = tg ?? talgeotDaegi
        // 즐겨찾기 먼저
        let favs = Jeulgyeo.shared.mokrok.sorted { $0.ireum.count > $1.ireum.count }
        for a in alts {
            let az = MalSajeon.ttuk(a)
            if let f = favs.first(where: { let fz = MalSajeon.ttuk($0.ireum); return !fz.isEmpty && az.contains(fz) }) {
                hubo = [f.jangso]
                huboI = 0
                huboTalgeot = taltgeot
                huboAnnae(dap, hwagin: hwagin)
                return
            }
        }
        let qA = q
        let qB0 = qB
        Task {
            // 2.12.5 "제기역 2번 출구", "제기 전철역" → 지하철역 목록의 바른 이름(제기동역)으로
            let yk = await MalHagi.yeokBarojapgi(qA)
            let res: (String, [Jangso]?)
            if let y = yk { res = await MalHagi.jangsoChatgi(y, nil) } else { res = await MalHagi.jangsoChatgi(qA, qB0) }
            let (q, r) = res
            // 2.26.0 (대표님 지시) "약수역 5번 출구"를 못 찾으면 그 역의 가까운 다른 출구를 권함
            var daean: Jangso? = nil
            var rMatjum = r
            if let y = yk, let bn = MalHagi.chulguBeon(y) {
                if let i = (r ?? []).prefix(5).firstIndex(where: { MalHagi.chulguBeon($0.ireum) == bn }), var rr0 = r {
                    let a = rr0.remove(at: i); rr0.insert(a, at: 0); rMatjum = rr0   // 맞는 출구를 맨 앞으로
                } else {
                    let yeok = y.components(separatedBy: " ").first ?? y
                    let w = await MainActor.run { WichiEngine.shared.jigeum }
                    if let rs = await Chatgi.jangso(yeok + " 출구") {
                        let chulgu = rs.filter { MalHagi.chulguBeon($0.ireum) != nil && MalHagi.chulguBeon($0.ireum) != bn }
                        if let w = w {
                            daean = chulgu.min { WichiEngine.geori(w.lat, w.lon, $0.lat, $0.lon) < WichiEngine.geori(w.lat, w.lon, $1.lat, $1.lon) }
                        } else {
                            daean = chulgu.first
                        }
                    }
                }
            }
            let rFinal = rMatjum
            let daeanFinal = daean
            DispatchQueue.main.async {
                if let dn = daeanFinal, let y = yk, let bn = MalHagi.chulguBeon(dn.ireum) {
                    self.hubo = [dn]
                    self.huboI = 0
                    self.huboTalgeot = taltgeot
                    self.huboHwagin = true
                    self.mureum = .hubo
                    self.mureumTtae = Date()
                    Girok.shared.namgi("chulgu_daean", ["mal": String(y.prefix(30)), "daean": bn])
                    dap("네, \(y)\(MalHagi.eul(y)) 찾지 못했습니다. 가까운 \(bn)번 출구로 안내해 드릴까요?", true)
                    return
                }
                guard let r = rFinal else {
                    dap("찾는 중에 연결이 끊겼습니다. 통신을 확인하시고 다시 말씀해 주십시오.", false)
                    return
                }
                if r.isEmpty {
                    // 2.29.0 사전으로 못 알아들은 말은 폰 안 인공지능에게 한 번 풀어 달라고 함(8초 안에 거듭 묻지 않음)
                    if MalAI.sseulSuItda && Date().timeIntervalSince(self.aiTtae) > 8 {
                        self.aiTtae = Date()
                        Task {
                            let s = await MalAI.puri(t)
                            await MainActor.run {
                                if let s = s, MalSajeon.ttuk(s) != MalSajeon.ttuk(t) {
                                    Girok.shared.namgi("mal_ai", ["jeon": String(t.prefix(40)), "hu": String(s.prefix(40))])
                                    self.cheori([s], dap)
                                } else {
                                    Girok.shared.namgi("mal_ai", ["jeon": String(t.prefix(40)), "hu": ""])
                                    self.motChatgiDap(t, q, hwagin, dap)
                                }
                            }
                        }
                        return
                    }
                    self.motChatgiDap(t, q, hwagin, dap)
                    return
                }
                // 2.12.7 주소로 말씀하시면(동호로 7길 14) 그 건물 안 가게 이름(주전) 대신 주소를 이름으로(이사장님 승인 1)
                var rr = Array(r.prefix(3))
                if MalHagi.jusoMalinga(qA), let a = rr.first {
                    let jj = a.juso.isEmpty ? qA : a.juso
                    rr = [Jangso(ireum: MalHagi.jusoIreum(jj), juso: jj, lat: a.lat, lon: a.lon)]
                    Girok.shared.namgi("juso_mokjeok", ["mal": String(qA.prefix(30)), "ireum": String(a.ireum.prefix(20))])
                }
                self.hubo = rr
                self.huboI = 0
                self.huboTalgeot = taltgeot
                self.huboAnnae(dap, hwagin: hwagin)
            }
        }
    }

    /// 2.12.7 도로명 주소나 지번 주소로 말씀하셨는가 — 동호로 7길 14, 동호로7길 14번지, 신당동 432-1
    static func jusoMalinga(_ q: String) -> Bool {
        let t = q.trimmingCharacters(in: .whitespaces)
        return t.range(of: "[가-힣0-9]+(로|길)\\s*[0-9]+(\\s*(번?길|가길))?\\s*[0-9-]*\\s*(번지|호)?\\s*$", options: .regularExpression) != nil
            && t.range(of: "[0-9]", options: .regularExpression) != nil
            || t.range(of: "[가-힣]+(동|리|가)\\s*[0-9]+(-[0-9]+)?\\s*(번지)?\\s*$", options: .regularExpression) != nil
    }

    /// 주소를 부를 이름으로 — "서울 중구 동호로7길 14" → "동호로7길 14"
    static func jusoIreum(_ juso: String) -> String {
        let t = juso.trimmingCharacters(in: .whitespaces)
        if let r = t.range(of: "[가-힣0-9]+(로|길)[0-9가-힣]*\\s*[0-9-]+.*$", options: .regularExpression) { return String(t[r]) }
        return t
    }

    /// 명령 — 하면 참
    private func myeongryeong(_ alts: [String], _ z: String, _ tg: Talgeot?, _ dap: @escaping (String, Bool) -> Void) -> Bool {
        let s = sajeon
        let y = YeojeongEngine.shared.jigeum
        // 2.6.0 음향신호기 — "신호기 울려 줘", "신호 알려 줘", "신호기 어디", "신호기 찾아 줘"
        if z.contains("신호기") || z.contains("신호알려") || z.contains("신호안내") || z.contains("신호등") {
            if z.contains("찾") {
                dap("음향신호기 찾기를 켭니다. 가까워질수록 소리가 빨라집니다. 그만이라고 하시면 멈춥니다.", false)
                SinhogiEngine.shared.chatgiKyeogi()
            } else {
                let cmd: UInt8 = (z.contains("어디") || z.contains("위치")) ? 1 : 2
                dap("", false)
                SinhogiEngine.shared.ulligi(cmd)
            }
            return true
        }
        // 2.13.0 기분과 날씨에 맞춰 음악 — "기분이 꿀꿀해", "잔잔한 음악 틀어 줘", "날씨에 맞게 틀어 줘"(이사장님 지시)
        if let g = BangsongEngine.gibunChatgi(z),
           g == .nalssi || ["음악", "노래", "틀어", "틀자", "들려", "추천", "곡", "기분"].contains(where: { z.contains($0) }),
           !["그만", "꺼", "끄기", "멈춰", "중지"].contains(where: { z.contains($0) }) {
            Task {
                let m = await BangsongEngine.shared.gibunTeulgi(g)
                DispatchQueue.main.async { dap(m, false) }
            }
            return true
        }
        // 2.13.0 날씨 — "날씨 어때", "오늘 날씨"(웹 길눈과 같은 자료)
        if z.contains("날씨") || z.contains("미세먼지") {
            Task {
                let n = await Nalssi.shared.mal()
                DispatchQueue.main.async { dap(n.isEmpty ? "날씨를 받아 오지 못했습니다. 통신과 위치를 확인해 주십시오." : "날씨는 " + n + ".", false) }
            }
            return true
        }
        // 2.13.0 되짚어 나가기 — "길 기억해 줘"(들어갈 때), "되짚어 나가자"(나올 때)
        if z.contains("되짚") || z.contains("왔던길로나가") || z.contains("들어온길로나가") {
            let d = DoeEngine.shared
            if d.sangtae == .annae { dap("", false); d.jigeumMal() }
            else if d.gieokItda || d.sangtae == .gieok { dap("", false); d.doejipgi() }
            else { dap("기억해 둔 길이 없습니다. 들어가실 때 길 기억해 줘라고 말씀해 주십시오.", false) }
            return true
        }
        if z.contains("길기억") || z.contains("길을기억") || z.contains("길좀기억") {
            if z.contains("그만") || z.contains("멈춰") || z.contains("꺼") { dap("", false); DoeEngine.shared.gieokGeuman(); return true }
            dap("", false)
            DoeEngine.shared.gieokSijak()
            return true
        }
        // 2.22.0 빛 알아보기(카메라 눈) — "불 켜져 있어", "빛 알려 줘", "밝은 쪽 찾아 줘"
        if z.contains("불켜") || z.contains("불꺼") || z.contains("빛알") || z.contains("빛찾") || z.contains("밝은쪽") || z.contains("창문어느") {
            dap("빛 알아보기를 엽니다.", false)
            TabGil.shared.tab = 1
            DulreoGil.shared.path = NavigationPath()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { DulreoGil.shared.path.append(DulreoHwamyeon.bit) }
            return true
        }
        // 2.21.0 지폐와 색깔 알아보기(카메라 눈) — "무슨 색이야", "색깔 알려 줘", "지폐 알려 줘", "얼마짜리야"
        if z.contains("무슨색") || z.contains("색깔") || z.contains("색알려") || z.contains("색이뭐") || z.contains("지폐") || z.contains("얼마짜리") {
            dap("지폐와 색깔 알아보기를 엽니다.", false)
            TabGil.shared.tab = 1
            DulreoGil.shared.path = NavigationPath()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { DulreoGil.shared.path.append(DulreoHwamyeon.jipye) }
            return true
        }
        // 2.20.0 상품 바코드 읽기(카메라 눈) — "바코드 읽어 줘", "이 상품 뭐야", "무슨 상품이야"
        if z.contains("바코드") || z.contains("상품뭐") || z.contains("무슨상품") || z.contains("상품이름") || z.contains("상품읽") {
            dap("상품 바코드 읽기를 엽니다.", false)
            TabGil.shared.tab = 1
            DulreoGil.shared.path = NavigationPath()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { DulreoGil.shared.path.append(DulreoHwamyeon.sangpum) }
            return true
        }
        // 2.23.0 인터넷 없이 한마디 설명(카메라 눈) — "이게 뭐야", "뭐가 보여", "한마디 설명"
        if z.contains("이게뭐") || z.contains("이거뭐") || z.contains("뭐가보여") || z.contains("무엇이보여") || z.contains("한마디설명") {
            dap("한마디 설명을 엽니다.", false)
            TabGil.shared.tab = 1
            DulreoGil.shared.path = NavigationPath()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { DulreoGil.shared.path.append(DulreoHwamyeon.hanmadi) }
            return true
        }
        // 2.18.0 사람 감지(카메라 눈) — "사람 있어", "사람 감지", "앞에 사람"
        if z.contains("사람있") || z.contains("사람감지") || z.contains("앞에사람") || z.contains("사람찾") {
            dap("사람 감지를 엽니다.", false)
            TabGil.shared.tab = 1
            DulreoGil.shared.path = NavigationPath()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { DulreoGil.shared.path.append(DulreoHwamyeon.saram) }
            return true
        }
        // 2.17.0 가리키고 말하기(카메라 눈) — "가리키는 거 읽어 줘", "가리킨 글자"
        if z.contains("가리키") || z.contains("가리킨") {
            dap("가리키고 말하기를 엽니다.", false)
            TabGil.shared.tab = 1
            DulreoGil.shared.path = NavigationPath()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { DulreoGil.shared.path.append(DulreoHwamyeon.gariki) }
            return true
        }
        // 2.16.0 즉석 글자 읽기(카메라 눈)
        if z.contains("글자읽") || z.contains("글읽어") || z.contains("글씨읽") {
            dap("즉석 글자 읽기를 엽니다.", false)
            TabGil.shared.tab = 1
            DulreoGil.shared.path = NavigationPath()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { DulreoGil.shared.path.append(DulreoHwamyeon.geulIlgi) }
            return true
        }
        // 2.15.0 문 찾기(카메라 눈)
        if z.contains("문찾") || z.contains("문어디") {
            dap("카메라로 문을 찾습니다.", false)
            TabGil.shared.tab = 0
            GilGil.shared.cheotHwamyeon()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { GilGil.shared.path.append(GilHwamyeon.munChatgi) }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { MunChatgi.shared.kyeogi("malhagi") }
            return true
        }
        // 2.14.0 말로 그린 길, 음성유도기와 승강기, 현장영상해설 받기
        if z.contains("말로그린") {
            dap("말로 그린 길을 엽니다.", false)
            TabGil.shared.tab = 0
            GilGil.shared.cheotHwamyeon()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { GilGil.shared.path.append(GilHwamyeon.malgil) }
            return true
        }
        if z.contains("음성유도기") || z.contains("유도기") || z.contains("승강기") || (z.contains("엘리베이터") && (z.contains("역") || z.contains("어디"))) {
            dap("가까운 역의 음성유도기와 승강기를 찾습니다.", false)
            TabGil.shared.tab = 0
            GilGil.shared.cheotHwamyeon()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { GilGil.shared.path.append(GilHwamyeon.yudo) }
            return true
        }
        if z.contains("현장영상해설") || z.contains("현장해설") || z.contains("해설받") || z.contains("해설코스") || z.contains("파견신청") {
            dap("현장영상해설 받기를 엽니다. 현장영상해설사 화상통화, 현장영상해설 코스, 파견 신청 가운데 고르십시오.", false)
            TabGil.shared.tab = 1
            DulreoGil.shared.path = NavigationPath()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { DulreoGil.shared.path.append(DulreoHwamyeon.haeseol) }
            return true
        }
        // 2.13.0 QR 찾기 — "QR 찾아 줘", "큐알"
        if z.lowercased().contains("qr") || z.contains("큐알") || z.contains("큐아르") {
            dap("QR 찾기를 엽니다.", false)
            TabGil.shared.tab = 0
            GilGil.shared.cheotHwamyeon()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { GilGil.shared.path.append(GilHwamyeon.qr) }
            return true
        }
        // 2.13.0 배리어프리방송국은 앞으로 따로 앱으로 키우기로 함(이사장님 결정) — 길눈에서는 모르는 척하지 않고 그렇다고 말씀드림
        if z.contains("배프") || z.contains("배리어프리") {
            dap("죄송합니다. 배리어프리방송국은 앞으로 따로 앱으로 만들 예정이라 길눈에는 들어 있지 않습니다.", false)
            return true
        }
        if s.itda(alts, "doumal") {
            dap(MalHagi.doumalMal, false)
            return true
        }
        if s.itda(alts, "dasi") && z.count <= 10 && MalgilEngine.shared.geotneun {   // 2.14.0 말로 그린 길을 걷는 중이면 그 안내를
            dap("", false)
            MalgilEngine.shared.dasiDeutgi()
            return true
        }
        if s.itda(alts, "dasi") && z.count <= 10 {
            dap(ijeonMal.isEmpty ? "다시 들려 드릴 말이 없습니다." : ijeonMal, false)
            return true
        }
        if s.itda(alts, "cheoeum") && z.count <= 8 {
            mok = nil
            talgeotDaegi = nil
            mureum = .mokjeok
            mureumTtae = Date()
            dap("처음부터 하겠습니다. \(ho), 어디로 가실까요?", true)
            return true
        }
        if s.itda(alts, "kol_beonho") {
            let l = s.kolDeul().map { "\($0.ireum) \(MalSajeon.beonhoMal($0.jeonhwa))" }
            dap("지금 계신 곳의 콜 번호입니다. " + l.joined(separator: ". ") + ".", false)
            return true
        }
        if let jong = kolJong(alts) {
            kolGeolgi(jong, dap)
            return true
        }
        if s.itda(alts, "naerim") {
            if let yy = y, yy.danggye == .taneunJung || yy.danggye == .taneunGotKkaji {
                dap("", false)
                AnnaeEngine.shared.naeryeotda()
                GilGil.shared.cheotHwamyeon()
            } else {
                dap("지금 타고 가시는 여정이 없습니다. 가실 곳을 말씀해 주시면 안내하겠습니다.", false)
            }
            return true
        }
        if s.itda(alts, "tatda") {
            if let yy = y {
                dap("", false)
                if yy.jiha != nil {
                    JihacheolEngine.shared.tatda(jadong: false)
                } else if yy.beoseu != nil {
                    AnnaeEngine.shared.beoseuTatda()
                } else {
                    AnnaeEngine.shared.chaTatda()
                }
                GilGil.shared.cheotHwamyeon()
            } else if let m = mok {
                dap("", false)
                AnnaeEngine.shared.chaTagi(m)
                GilGil.shared.cheotHwamyeon()
            } else {
                talgeotDaegi = .cha
                mureum = .mokjeok
                mureumTtae = Date()
                dap("차 안 안내를 하겠습니다. \(ho), 어디로 가십니까?", true)
            }
            return true
        }
        if s.itda(alts, "sigan") {
            if y == nil {
                mureum = .mokjeok
                mureumTtae = Date()
                dap("아직 가시는 곳이 없습니다. \(ho), 어디로 가실까요?", true)
            } else {
                dap(geollineunMal(), false)
            }
            return true
        }
        if s.itda(alts, "jigeum_gil") || s.itda(alts, "charye") {
            if y != nil {
                dap("", false)
                AnnaeEngine.shared.hyeonhwang()
            } else {
                mureum = .mokjeok
                mureumTtae = Date()
                dap("지금 가시는 길이 없습니다. \(ho), 어디로 가실까요?", true)
            }
            return true
        }
        if s.itda(alts, "eodi") {
            dap("", false)
            AnnaeEngine.shared.jigeumJari()
            return true
        }
        if s.itda(alts, "sigan_now") {
            dap(MalHagi.jigeumSigak(), false)
            return true
        }
        if s.itda(alts, "bareuge") {
            let d = min(Seoljeong.bbareugiIreum.count - 1, Seoljeong.shared.bbareugiDan + 1)
            Seoljeong.shared.bbareugiDan = d
            dap("\(Seoljeong.bbareugiIreum[d]) 말씀드립니다.", false)
            return true
        }
        if s.itda(alts, "neurige") {
            let d = max(0, Seoljeong.shared.bbareugiDan - 1)
            Seoljeong.shared.bbareugiDan = d
            dap("\(Seoljeong.bbareugiIreum[d]) 말씀드립니다.", false)
            return true
        }
        if s.itda(alts, "annae_kkeum") {
            Seoljeong.shared.malKyeojim = false
            dap("길눈 말소리를 껐습니다. 경고는 그대로 말씀드리고, 말로 하기의 대답은 보이스오버로 드립니다.", false)
            return true
        }
        if s.itda(alts, "annae_kyeom") {
            Seoljeong.shared.malKyeojim = true
            dap("길눈 말소리를 켰습니다.", false)
            return true
        }
        if s.itda(alts, "mok_bakkum") {
            let l = SoriEngine.hangukMoksori()
            if l.count < 2 {
                dap("이 폰에는 한국어 목소리가 하나뿐입니다. 아이폰 설정의 손쉬운 사용, 읽기 및 말하기, 음성에서 한국어 목소리를 더 받으시면 바꿀 수 있습니다.", false)
            } else {
                let jigeum = SoriEngine.shared.moksori()?.identifier ?? ""
                let i = l.firstIndex { $0.identifier == jigeum } ?? -1
                Seoljeong.shared.moksoriId = l[(i + 1) % l.count].identifier
                dap("이 목소리로 말씀드립니다.", false)
            }
            return true
        }
        if s.itda(alts, "hoching") {
            mureum = .hoching
            mureumTtae = Date()
            dap("지금은 \(ho)\(MalHagi.irago(ho)) 부릅니다. 뭐라고 불러 드릴까요?", true)
            return true
        }
        if s.itda(alts, "saerogochim") {
            Tongsin.shared.gamchumBiugi()
            s.bureogi()
            dap("새로고침을 마쳤습니다. 나스에서 새 자료를 받습니다.", false)
            return true
        }
        if s.itda(alts, "jeulgyeo_mok") {
            let l = Jeulgyeo.shared.mokrok.prefix(5).map { $0.ireum }
            if l.isEmpty {
                dap("즐겨찾기가 비어 있습니다. 가시는 곳에서 즐겨찾기에 담아 줘라고 말씀하시면 담아 둡니다.", false)
            } else {
                mureum = .mokjeok
                mureumTtae = Date()
                dap("즐겨찾기에 담긴 곳은 \(l.joined(separator: ", "))입니다. \(ho), 어디로 가실까요?", true)
            }
            return true
        }
        if s.itda(alts, "jeulgyeo_dam") {
            var j: Jangso?
            if let yy = y {
                j = Jangso(ireum: yy.mokjeok.ireum, juso: yy.mokjeok.juso, lat: yy.mokjeok.lat, lon: yy.mokjeok.lon)
            } else {
                j = mok
            }
            if let j = j {
                if Jeulgyeo.shared.damgi(j) {
                    dap("\(j.ireum)\(MalHagi.eul(j.ireum)) 즐겨찾기에 담았습니다.", false)
                } else {
                    dap("\(j.ireum)\(MalHagi.eun(j.ireum)) 이미 즐겨찾기에 있습니다.", false)
                }
            } else {
                dap("담을 곳이 없습니다. 먼저 가실 곳을 말씀해 주십시오.", false)
            }
            return true
        }
        // 2.7.0 둘러보기 — 고장 이야기, 마실, 사진 읽어 주기, 안면인식, 축제, 둘레 찾기
        if s.itda(alts, "gojang") && !(z.contains("노래") || z.contains("음악")) {   // 2.12.7 고장 노래는 음악으로
            Task {
                let r = await Dulreo.gojang()
                DispatchQueue.main.async {
                    guard let r = r, !r.0.isEmpty else { dap("이 고장 이야기를 받지 못했습니다. 통신과 위치를 확인해 주십시오.", false); return }
                    dap(r.0, false)
                }
            }
            return true
        }
        if s.itda(alts, "masil") { dulreoYeolgi(.masil, "마실을 엽니다. 떠나실 고장을 고르십시오.", dap); return true }
        if s.itda(alts, "sajin") { dulreoYeolgi(.sajin, "사진 읽어 주기를 엽니다. 사진 찍어 읽어 주기 단추를 두드리십시오.", dap); return true }
        if z.contains("안면") || z.contains("얼굴인식") || z.contains("누가있") || z.contains("사람있") {
            dulreoYeolgi(.anmyeon, "안면인식을 엽니다.", dap)
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { AnmyeonEngine.shared.kyeogi() }
            return true
        }
        if z.contains("축제") {
            Task {
                let r = await Dulreo.gabol("chukje")
                DispatchQueue.main.async {
                    guard let r = r else { dap("축제 소식을 받지 못했습니다. 통신과 위치를 확인해 주십시오.", false); return }
                    if r.isEmpty { dap("10킬로미터 안에 지금 알려진 축제가 없습니다.", false); return }
                    dap("가까운 축제 \(min(3, r.count))곳입니다. " + r.prefix(3).map { $0.julMal }.joined(separator: " ") + " 더 들으시려면 둘러보기 탭의 가는 김에에서 축제를 여십시오.", false)
                }
            }
            return true
        }
        if let jong = DulleJong.malEseo(z),
           ["근처", "가까운", "가까이", "주변", "제일가까", "찾아", "어디"].contains(where: { z.contains($0) }) || z.count <= 6 {
            Task {
                var r = await Dulreo.dulle(jong)
                if jong.id == "sikdang" && (r ?? []).isEmpty {
                    r = await Dulreo.dulle(DulleJong(id: "", ireum: "식당", natmal: "음식점", geot: true))
                }
                DispatchQueue.main.async {
                    let nm = jong.ireum.components(separatedBy: " — ").first ?? jong.ireum
                    guard let r = r else { dap("\(nm)\(MalHagi.eul(nm)) 찾지 못했습니다. 통신과 위치를 확인해 주십시오.", false); return }
                    if r.isEmpty { dap("1킬로미터 안에 \(nm)\(MalHagi.i(nm)) 없습니다.", false); return }
                    self.hubo = r.prefix(3).map { $0.jangso }
                    self.huboI = 0
                    self.huboTalgeot = tg
                    self.huboAnnae(dap, hwagin: true)
                }
            }
            return true
        }
        // 2.8.0 음악·방송 — 다음 곡, 무슨 곡이야, 음악 틀어 줘, 트롯 틀어 줘, 라디오 틀어 줘, 뉴스 들려줘, 음악 꺼
        if bangsongMyeongryeong(alts, alts.first ?? "", z, dap) { return true }
        // 아직 앱에 넣지 못한 기능 — 모르는 척하지 않고, 기록해 두었다가 그 기능을 넣을 때 말로도 되게
        let aJik: [(String, String)] = [("mun_namgigi", "문 남기기"),
                                       ("hwaksin_kkeum", "확신음 켜고 끄기"), ("hwaksin_kyeom", "확신음 켜고 끄기"),
                                       ("nopge", "목소리 높낮이"), ("natge", "목소리 높낮이")]
        for (k, nm) in aJik where s.itda(alts, k) {
            Girok.shared.namgi("malhagi_eopneun", ["k": k, "mal": String((alts.first ?? "").prefix(60))])
            dap("죄송합니다. \(nm)\(MalHagi.eun(nm)) 아직 앱에 넣지 못했습니다. 그 기능을 넣을 때 말로도 되게 하겠습니다. 이 말씀은 기록해 두었습니다.", false)
            return true
        }
        // 탈것만 말씀하심 — "걸어가자", "지하철로 가자"
        if let tg = tg, z.count <= 8 {
            if let yy = y {
                let j = Jangso(ireum: yy.mokjeok.ireum, juso: yy.mokjeok.juso, lat: yy.mokjeok.lat, lon: yy.mokjeok.lon)
                if tg == .georeum {
                    dap("", false)
                    if yy.danggye == .taneunJung { AnnaeEngine.shared.naeryeotda() } else { AnnaeEngine.shared.georeoGagi() }
                    GilGil.shared.cheotHwamyeon()
                } else {
                    gagi(j, tg, dap)
                }
            } else if let m = mok {
                gagi(m, tg, dap)
            } else {
                talgeotDaegi = tg
                mureum = .mokjeok
                mureumTtae = Date()
                dap("\(ho), 어디로 가실지 먼저 말씀해 주십시오.", true)
            }
            return true
        }
        // 네·아니오만
        if s.tteut(alts, "ye") == .gatda && z.count <= 4 {
            mureum = .mokjeok
            mureumTtae = Date()
            dap("네, \(ho). 어디로 가실까요?", true)
            return true
        }
        if s.tteut(alts, "ani") == .gatda && z.count <= 4 {
            dap("알겠습니다.", false)
            return true
        }
        return false
    }

    /// 2.8.0 음악·방송 명령 — 하면 참
    private func bangsongMyeongryeong(_ alts: [String], _ t: String, _ z: String, _ dap: @escaping (String, Bool) -> Void) -> Bool {
        let s = sajeon
        let b = BangsongEngine.shared
        let zl = z.lowercased()
        if s.itda(alts, "gok_daeum") {
            if b.itda { dap("", false); b.daeum() } else { dap("지금 틀고 있는 것이 없습니다.", false) }
            return true
        }
        if s.itda(alts, "gok_ijeon") {
            if b.itda { dap("", false); b.ijeon() } else { dap("지금 틀고 있는 것이 없습니다.", false) }
            return true
        }
        if s.itda(alts, "musun_gok") {
            dap(b.jigeumMal, false)
            return true
        }
        if s.itda(alts, "dasiteul") && b.itda && b.meomchum {
            dap("", false)
            b.meomchumTogeul()
            return true
        }
        let kkeugi = ["그만", "꺼", "끄기", "끄자", "멈춰", "중지"].contains { z.contains($0) }
        // 2.12.7 방송사 이름만 말씀하셔도(MBC 틀어 줘, 엠비시) 라디오로 — 음악 찾기로 새지 않게
        let pj = BangsongEngine.bangsongPyojun(z)
        let bangsongsaMal = ["kbs", "mbc", "ebs", "obs", "tbs", "bbs", "arirang", "표준fm", "fm4u", "4u", "해피fm", "쿨fm", "클래식fm", "사랑의소리", "한민족"].contains { pj.contains($0) }
        let radio = z.contains("라디오") || zl.contains("fm") || z.contains("에프엠") || bangsongsaMal
        let tv = zl.contains("tv") || z.contains("티비") || z.contains("티브이") || z.contains("텔레비전") || z.contains("듣는방송")
        let nyuseu = s.itda(alts, "nyuseu") || s.itda(alts, "jangae") || z.contains("뉴스") || z.contains("세상이야기") || z.contains("속보") || z.contains("기사읽") || z.contains("기사들려")
        let eumak = s.itda(alts, "eumak") || z.contains("노래") || z.contains("음악") || z.contains("틀어")
        // 2.12.7 지나는 고장 노래 — "고장 노래 틀어 줘", "지나는 고장 노래 꺼"(이사장님 승인 1)
        if z.contains("고장노래") || z.contains("고장음악") || (z.contains("지나는고장") && (z.contains("노래") || z.contains("음악"))) {
            if kkeugi {
                if b.jadoKyeojim { dap("", false); b.jadoKkeugi() } else { dap("지나는 고장 노래는 켜져 있지 않습니다.", false) }
                return true
            }
            if b.jadoKyeojim { dap("지나는 고장 노래가 이미 켜져 있습니다.", false); return true }
            dap("", false)   // 2.19.0 고장 노래는 열쇠 없이 모든 분께
            b.jadoKyeogi()
            return true
        }
        if kkeugi {
            guard b.itda, radio || tv || nyuseu || eumak || z.contains("방송") else { return false }
            dap("", false)
            b.geuman()
            return true
        }
        if nyuseu {
            var g = "all"
            if z.contains("속보") || z.contains("특보") { g = "sokbo" }
            else if z.contains("장애") || z.contains("복지") { g = "jangae" }
            else if z.contains("정치") { g = "jeongchi" }
            else if z.contains("경제") { g = "gyeongje" }
            else if z.contains("국제") || z.contains("세계") { g = "gukje" }
            else if z.contains("사회") { g = "sahoe" }
            else if z.contains("스포츠") || z.contains("연예") { g = "spo" }
            let nm = BangsongEngine.garae.first { $0.0 == g }?.1 ?? "두루 소식"
            Task {
                let ls = await b.gisaBatgi(g)
                DispatchQueue.main.async {
                    guard let ls = ls else { dap("소식을 받아 오지 못했습니다. 통신을 확인해 주십시오.", false); return }
                    if ls.isEmpty { dap("지금 \(nm)에는 새 기사가 없습니다.", false); return }
                    b.gisaYeolgi(0)
                    TabGil.shared.tab = 2
                    BangsongGil.shared.path = NavigationPath()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                        BangsongGil.shared.path.append(BangsongHwamyeon.sesang)
                        BangsongGil.shared.path.append(BangsongHwamyeon.gisa)
                    }
                    dap("\(nm), 최신 기사부터 읽어 드립니다. 다음 기사라고 하시면 넘어갑니다.", false)
                }
            }
            return true
        }
        if tv || radio {
            Task {
                let (m, mutneun) = await b.chaeneolMalro(z, kind: tv ? "tv" : "radio")
                DispatchQueue.main.async { dap(m, mutneun) }
            }
            return true
        }
        if eumak {
            var q = t
            for w in ["길 위의", "길위의", "길 위", "길거리", "틀어 주세요", "틀어 줘", "틀어줘", "틀어 봐", "틀어봐", "틀어", "들려 줘", "들려줘", "듣고 싶어", "듣자", "들을래",
                      "음악", "노래", "좀", "곡", "줘"] {
                q = q.replacingOccurrences(of: w, with: " ")
            }
            q = q.trimmingCharacters(in: .whitespacesAndNewlines.union(.punctuationCharacters))
            for p in ["을", "를"] where q.hasSuffix(p) && q.count > 2 { q = String(q.dropLast()).trimmingCharacters(in: .whitespaces) }
            if (Yeolsoe.ilgi("eumakTk") ?? "").isEmpty {   // 2.19.0 음악 전체는 다시 열쇠(이사장님 지시 2)
                Task { await b.nugunaTeulgi("", "나스 음악 열쇠를 아직 넣지 않으셔서 누구나 음악을 틉니다. 지나는 고장 노래는 열쇠 없이 고장 노래 틀어 줘라고 하시면 됩니다.") }
                dap("", false)
                return true
            }
            if q.isEmpty {
                if b.itda && b.meomchum { dap("", false); b.meomchumTogeul(); return true }
                Task { await b.galraeTeulgi("가요", "") }
                dap("", false)
                return true
            }
            Task {
                let m = await b.malChatgi(q)
                DispatchQueue.main.async { dap(m, false) }
            }
            return true
        }
        return false
    }

    /// 2.7.0 둘러보기 탭의 화면을 열어 드림
    private func dulreoYeolgi(_ h: DulreoHwamyeon, _ mal: String, _ dap: @escaping (String, Bool) -> Void) {
        TabGil.shared.tab = 1
        DulreoGil.shared.path = NavigationPath()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { DulreoGil.shared.path.append(h) }
        dap(mal, false)
    }

    // MARK: 가기

    /// 찾은 곳을 알려 드리고 곧바로 안내 — 가자는 말 없이 이름만 들렸으면 한 번 여쭘
    private func huboAnnae(_ dap: @escaping (String, Bool) -> Void, hwagin: Bool) {
        guard huboI < hubo.count else { return }
        let h = hubo[huboI]
        // 2.26.0 (대표님 지시) "네, 약수역 5번 출구는 1시 방향, 120미터에 있습니다."
        var m = "네, " + h.ireum
        if let w = WichiEngine.shared.jigeum {
            let d = WichiEngine.geori(w.lat, w.lon, h.lat, h.lon)
            if d < 30 {
                m += MalHagi.eun(h.ireum) + " 지금 계신 곳 바로 가까이에 있습니다."
            } else {
                let bang = w.banghyang >= 0 ? "\(MunChatgi.sigye(GanpanAllim.bangwi(w.lat, w.lon, h.lat, h.lon) - w.banghyang))시 방향, " : ""
                m += MalHagi.eun(h.ireum) + " \(bang)\(Annae.geoMal(d))에 있습니다."
            }
        } else {
            m += "."
        }
        mureum = .hubo
        mureumTtae = Date()
        huboHwagin = hwagin
        if hwagin {
            dap(m + " 여기로 안내할까요?", true)
            return
        }
        let dareun = hubo.count > huboI + 1 ? " 다른 곳이면 불러서 다른 곳이라고 하십시오." : ""
        gagi(h, huboTalgeot, dap, apMal: m + " 여기로 안내합니다." + dareun + " ")
    }

    private func daeumHubo(_ dap: @escaping (String, Bool) -> Void) {
        huboI += 1
        if huboI < hubo.count {
            huboAnnae(dap, hwagin: huboHwagin)
        } else {
            mureum = .mokjeok
            mureumTtae = Date()
            dap("다른 곳을 말씀해 주십시오. \(ho), 어디로 가실까요?", true)
        }
    }

    /// 탈것에 맞춰 안내 시작 — 대답을 먼저 하고 안내 엔진을 움직임(말 차례가 맞게)
    private func gagi(_ j: Jangso, _ tg0: Talgeot?, _ dap: @escaping (String, Bool) -> Void, apMal: String = "") {
        mok = j
        Jeulgyeo.shared.sseum(j)
        let d: Double? = WichiEngine.shared.jigeum.map { WichiEngine.geori($0.lat, $0.lon, j.lat, j.lon) }
        let yj = YeojeongEngine.shared
        let taneunJung = yj.sokdoChujeong != .georeum || (yj.jigeum?.danggye == .taneunJung && yj.jigeum?.jiha == nil)
        var tg = tg0 ?? talgeotDaegi
        talgeotDaegi = nil
        if tg == nil {
            if taneunJung {
                tg = .cha
            } else if d == nil || (d ?? 0) <= 2000 {
                tg = .georeum
            }
        }
        let mk = Mokjeok(ireum: j.ireum, lat: j.lat, lon: j.lon, juso: j.juso)
        guard let g = tg else {
            yj.jeonghagi(mk)
            GilGil.shared.cheotHwamyeon()
            mureum = .bangsik
            mureumTtae = Date()
            dap(apMal + "걸어가시기에는 먼 곳입니다. \(ho), 차로, 지하철로, 버스로 가운데 어떻게 가실까요?", true)
            return
        }
        switch g {
        case .georeum:
            // 2.10.0 맞는 점지도가 있으면 "점지도로 걸을까요, 위성으로 걸을까요" 한 번 여쭘(네 하시면 점지도)
            GilGil.shared.cheotHwamyeon()
            JeomEngine.shared.georeoGagiBoda(j) { [weak self] q in
                if let q = q {
                    self?.mureum = .jeom
                    self?.mureumTtae = Date()
                    dap(apMal + q, true)
                } else {
                    dap(apMal, false)
                }
            }
        case .cha, .gicha, .gosokbeoseu:
            if taneunJung {
                dap(apMal + "타고 가시는 중이니 차 안 안내로 잇습니다.", false)
                AnnaeEngine.shared.chaTagi(j)
            } else {
                yj.jeonghagi(mk)
                mureum = .chaYocheong
                mureumTtae = Date()
                dap(apMal + "차를 요청할까요? 이미 차가 있으시면 타신 뒤 차에 탔어라고 말씀해 주십시오.", true)
            }
            GilGil.shared.cheotHwamyeon()
        case .jihacheol:
            SoriEngine.shared.mal(apMal + "지하철 길을 찾는 중입니다.", .jeongbo)
            Task {
                let (gg, k) = await JihacheolEngine.gilChatgi(j)
                DispatchQueue.main.async {
                    if let gg = gg {
                        dap("\(gg.mal) 들어갈 곳은 \(gg.ipgu.ireum)입니다.", false)
                        AnnaeEngine.shared.jihacheolGagi(j, gg)
                        GilGil.shared.cheotHwamyeon()
                    } else {
                        yj.jeonghagi(mk)
                        self.mureum = .bangsik
                        self.mureumTtae = Date()
                        dap(k + " 걸어서, 차로, 버스로 가운데 어떻게 가실까요?", true)
                    }
                }
            }
        case .beoseu:
            guard let w = WichiEngine.shared.jigeum else {
                dap(apMal + "아직 위치를 잡는 중이라 가까운 정류장을 찾지 못했습니다. 잠시 뒤 다시 말씀해 주십시오.", false)
                return
            }
            SoriEngine.shared.mal(apMal + "가까운 정류장을 찾는 중입니다.", .jeongbo)
            Task {
                let r = await Beoseu.gakkaun(w.lat, w.lon)
                DispatchQueue.main.async {
                    if let jr = r?.first {
                        dap("가장 가까운 정류장은 \(Beoseu.julMal(jr))입니다.", false)
                        AnnaeEngine.shared.beoseuGagi(j, jr)
                        GilGil.shared.cheotHwamyeon()
                    } else {
                        yj.jeonghagi(mk)
                        self.mureum = .bangsik
                        self.mureumTtae = Date()
                        dap("가까운 정류장을 찾지 못했습니다. 걸어서, 차로, 지하철로 가운데 어떻게 가실까요?", true)
                    }
                }
            }
        }
    }

    // MARK: 2.12.5 역 이름과 번호

    /// 한글로 적힌 번호를 숫자로 — "약수역 오 번출구" → "약수역 5번 출구"(번 뒤에 출구·출입구가 올 때만)
    static func beonhoSutja(_ t: String) -> String {
        let su: [String: String] = ["일": "1", "이": "2", "삼": "3", "사": "4", "오": "5", "육": "6", "륙": "6", "칠": "7", "팔": "8", "구": "9",
                                    "십": "10", "십일": "11", "십이": "12", "십삼": "13", "십사": "14", "십오": "15", "십육": "16",
                                    "십칠": "17", "십팔": "18", "십구": "19", "이십": "20"]
        guard let re = try? NSRegularExpression(pattern: "(?<![가-힣0-9])(이십|십[일이삼사오육륙칠팔구]?|[일이삼사오육륙칠팔구])\\s*번\\s*(?=출)") else { return t }
        var s = t
        let ns = s as NSString
        for m in re.matches(in: s, range: NSRange(location: 0, length: ns.length)).reversed() {
            let w = ns.substring(with: m.range(at: 1))
            guard let d = su[w], let r = Range(m.range, in: s) else { continue }
            s.replaceSubrange(r, with: d + "번 ")
        }
        return s
    }

    /// 역 이름을 바로잡음 — 역을 찾는 말이 아니면 nil. "제기역 2번 출구" → "제기동역 2번 출구", "제기 전철역" → "제기동역"
    static func yeokBarojapgi(_ q: String) async -> String? {
        let t = q.trimmingCharacters(in: .whitespaces)
        guard let re = try? NSRegularExpression(pattern: "^(.+?)\\s*(지하철역|전철역|지하철|전철|역)\\s*(?:(\\d{1,2})\\s*번\\s*(?:출구|출입구)?)?$") else { return nil }
        let ns = t as NSString
        guard let m = re.firstMatch(in: t, range: NSRange(location: 0, length: ns.length)) else { return nil }
        let bon = ns.substring(with: m.range(at: 1)).trimmingCharacters(in: .whitespaces)
        let beon = m.range(at: 3).location != NSNotFound ? ns.substring(with: m.range(at: 3)) : ""
        guard !bon.isEmpty, bon.count <= 10 else { return nil }
        var ireum = bon.replacingOccurrences(of: " ", with: "")
        if let o = await Chatgi.json("yeok.php", ["a": "chatgi", "q": ireum]),
           let rows = o["rows"] as? [[String: Any]] {
            let nms = rows.compactMap { $0["nm"] as? String }
            if let n = nms.first(where: { $0 == ireum }) ?? nms.first(where: { $0.hasPrefix(ireum) }) { ireum = n }
        }
        let bakkum = ireum + "역" + (beon.isEmpty ? "" : " \(beon)번 출구")
        Girok.shared.namgi("yeok_barojapgi", ["jeon": String(t.prefix(30)), "hu": bakkum])
        return bakkum
    }

    /// 가까운 역 이름(받아쓰기에 미리 알려 줄 것) — 자리가 300미터 넘게 바뀌면 새로 받음
    private var gakkaunYeok: [String] = []
    private var gakkaunYeokJari: (Double, Double)?
    private func gakkaunYeokGaengsin() {
        guard let w = WichiEngine.shared.jigeum else { return }
        if let j = gakkaunYeokJari, WichiEngine.geori(j.0, j.1, w.lat, w.lon) < 300 { return }
        gakkaunYeokJari = (w.lat, w.lon)
        Task {
            let q = ["a": "gakkaun", "lat": String(format: "%.6f", w.lat), "lon": String(format: "%.6f", w.lon)]
            guard let o = await Chatgi.json("yeok.php", q), let rows = o["rows"] as? [[String: Any]] else { return }
            var l: [String] = []
            for r in rows { if let y = r["yeok"] as? String, !l.contains(y + "역") { l.append(y + "역") } }
            DispatchQueue.main.async { self.gakkaunYeok = Array(l.prefix(12)) }
        }
    }

    /// 명령을 들을 때 받아쓰기에 미리 알려 줄 말
    private func doumMal() -> [String] {
        var l = gakkaunYeok
        l += Jeulgyeo.shared.mokrok.prefix(30).map { $0.ireum }
        l += ["출구", "번 출구", "여기가 어디야", "길 위의 음악", "라디오 틀어 줘", "뉴스 들려줘", "집으로 가자"]
        return Array(l.prefix(80))
    }

    /// 되살린 이름(qB)으로 찾아 첫 곳 이름에 그 말이 들어 있으면 그것을, 아니면 뗀 이름(qA)으로
    private static func jangsoChatgi(_ qA: String, _ qB: String?) async -> (String, [Jangso]?) {
        if let b = qB, let rb = await Chatgi.jangso(b), !rb.isEmpty {
            // 뗀 이름이 한 자뿐이면(종로 → 종) 되살린 이름으로, 아니면 찾은 곳 이름에 그 말이 들어 있을 때만
            let bz = MalSajeon.ttuk(b)
            if MalSajeon.ttuk(qA).count <= 1 || rb.prefix(3).contains(where: { MalSajeon.ttuk($0.ireum).contains(bz) }) {
                return (b, rb)
            }
        }
        return (qA, await Chatgi.jangso(qA))
    }

    // MARK: 긴급통화

    /// 해설사·봉사자·명단의 이름이 바로 들리면 곧장 요청 — 하면 참
    private func gingeupJikjeop(_ z: String, _ dap: @escaping (String, Bool) -> Void) -> Bool {
        if z.contains("해설") {
            mureum = .eopseum
            dap("", false)
            GinGeup.shared.yocheong(.haeseolsa)
            return true
        }
        if z.contains("봉사") {
            mureum = .eopseum
            dap("", false)
            GinGeup.shared.yocheong(.haebong)
            return true
        }
        let l = Jiin.shared.mokrok.sorted { $0.name.count > $1.name.count }
        if let s = l.first(where: { let nz = MalSajeon.ttuk($0.name); return !nz.isEmpty && z.contains(nz) }) {
            mureum = .eopseum
            dap("", false)
            GinGeup.shared.yocheong(.jiin, s)
            return true
        }
        return false
    }

    /// 누구에게 요청할지 여쭘(명단을 먼저 받아 봄)
    private func gingeup(_ t: String, _ dap: @escaping (String, Bool) -> Void) {
        let z = MalSajeon.ttuk(t)
        Task {
            if Jiin.shared.mokrok.isEmpty { await Jiin.shared.bureogi() }
            DispatchQueue.main.async {
                if self.gingeupJikjeop(z, dap) { return }
                let l = Jiin.shared.mokrok
                self.mureum = .galrae
                self.mureumTtae = Date()
                if z.contains("가족") || z.contains("지인") {
                    if l.isEmpty {
                        dap("가족·지인 명단이 비어 있습니다. 설정 탭의 가족·지인 명단에서 먼저 등록해 주십시오. 자원봉사자나 현장영상해설사에게 요청하시려면 말씀해 주십시오.", true)
                    } else {
                        dap("가족·지인 가운데 누구에게 요청할까요? " + l.prefix(5).map { $0.name }.joined(separator: ", ") + ".", true)
                    }
                } else {
                    dap("\(self.ho), 누구에게 요청할까요? 가족·지인이면 이름을, 아니면 자원봉사자나 현장영상해설사라고 말씀해 주십시오.", true)
                }
            }
        }
    }

    // MARK: 콜

    private func kolJong(_ alts: [String]) -> String? {
        if sajeon.itda(alts, "kol_jangaein") { return "jangaein" }
        if sajeon.itda(alts, "kol_nabi") { return "nabi" }
        if sajeon.itda(alts, "kol_bokji") { return "bokji" }
        return nil
    }

    private func kolMutgi() -> String {
        "어디에 전화할까요? " + sajeon.kolDeul().map { $0.ireum }.joined(separator: ", ") + " 가운데 말씀해 주십시오."
    }

    private func kolGeolgi(_ jong: String, _ dap: @escaping (String, Bool) -> Void) {
        guard let k = sajeon.kolChatgi(jong), let u = URL(string: "tel:" + k.jeonhwa) else {
            dap("그 콜 번호가 이 지역 목록에 없습니다. 콜 번호 알려 줘라고 말씀하시면 이 지역 번호를 읽어 드립니다.", false)
            return
        }
        Girok.shared.namgi("malhagi_kol", ["k": jong])
        dap("\(k.ireum)에 전화를 겁니다. 통화 확인이 뜨면 통화를 두 번 두드려 주십시오. 차에 타시면 차에 탔어라고 말씀해 주십시오.", false)
        SoriEngine.shared.kkeutnamyeon { UIApplication.shared.open(u) }
    }

    // MARK: 돕는 말

    /// 남은 거리와 대략 걸리는 시간(길 막힘은 넣지 못한 셈)
    private func geollineunMal() -> String {
        guard let y = YeojeongEngine.shared.jigeum else { return "아직 가시는 곳이 없습니다." }
        guard let w = WichiEngine.shared.jigeum else { return "아직 위치를 잡는 중입니다. 잠시 뒤 다시 물어 주십시오." }
        let d = WichiEngine.geori(w.lat, w.lon, y.mokjeok.lat, y.mokjeok.lon)
        let t = YeojeongEngine.shared.talgeot
        let sokdo: Double
        let doragam: Double
        switch t {
        case .georeum: sokdo = 1.1; doragam = 1.3
        case .jihacheol: sokdo = 8.5; doragam = 1.3
        case .beoseu: sokdo = 5.0; doragam = 1.4
        case .gicha: sokdo = 25; doragam = 1.2
        case .gosokbeoseu: sokdo = 20; doragam = 1.2
        case .cha: sokdo = 7.0; doragam = 1.35
        }
        let bun = max(1, Int((d * doragam / sokdo / 60).rounded()))
        let bunMal = bun < 60 ? "\(bun)분" : (bun % 60 == 0 ? "\(bun / 60)시간" : "\(bun / 60)시간 \(bun % 60)분")
        let tal = t == .georeum ? "걸어서" : "\(t.ireum)\(MalHagi.ro(t.ireum))"
        return "\(y.mokjeok.ireum)까지 남은 거리는 \(Annae.geoMal(d)), \(tal) 대략 \(bunMal) 걸립니다. 길 막힘은 넣지 못한 셈입니다."
    }

    private static func jigeumSigak() -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ko_KR")
        f.dateFormat = "M월 d일 EEEE a h시 m분"
        return "지금은 \(f.string(from: Date()))입니다."
    }

    /// 2.12.6 하던 일 멈추기 때 — 묻던 말과 기다리던 목적지·탈것·후보를 모두 비움
    func mureumChoGihwa() {
        mureum = .eopseum
        mok = nil
        talgeotDaegi = nil
        hubo = []
        huboI = 0
        huboTalgeot = nil
        huboHwagin = false
    }

    static let doumalMal = "이렇게 말씀하시면 됩니다. 집으로 가자. 걸어서 가자. 지하철로 가자. 버스로 가자. 차에 탔어. 내렸어. 얼마나 남았어. 지금 어디야. 지금 가는 길 알려 줘. 즐겨찾기 목록. 즐겨찾기에 담아 줘. 복지콜에 전화해 줘. 콜 번호 알려 줘. 신호기 울려 줘. 신호기 찾아 줘. 근처 약국. 음악 틀어 줘. 트롯 틀어 줘. 다음 곡. 라디오 틀어 줘. MBC 라디오. 뉴스 들려줘. 음악 꺼. 고장 노래 틀어 줘. 기분이 꿀꿀해. 날씨에 맞게 틀어 줘. 날씨 어때. 길 기억해 줘. 되짚어 나가자. QR 찾아 줘. 말로 그린 길. 음성유도기 어디 있어. 현장영상해설 받고 싶어. 문 찾아 줘. 글자 읽어 줘. 가리키는 거 읽어 줘. 사람 있어. 바코드 읽어 줘, 이 상품 뭐야. 무슨 색이야, 얼마짜리야. 불 켜져 있어, 밝은 쪽 찾아 줘. 이게 뭐야, 뭐가 보여. 도착. 도와줘, 또는 가족 이름과 화상통화. 몇 시야. 말 빠르게, 말 느리게. 다시 말해. 그만. 여정 끝. 하던 일 멈춰. 점지도를 따라 걸을 때는 다음에 무엇, 그만 걷기, 여기 문제 있어, 여기 걸렸어. 가까운 점지도 찾아 줘."

    /// 2.29.0 인공지능이 풀어 본 때(거듭 묻지 않으려고)
    private var aiTtae = Date.distantPast

    /// 찾는 곳을 못 찾았을 때의 대답
    private func motChatgiDap(_ t: String, _ q: String, _ hwagin: Bool, _ dap: @escaping (String, Bool) -> Void) {
        motAradeureum(t)
        mureum = .mokjeok
        mureumTtae = Date()
        if hwagin {
            dap(sagwa() + "다시 말씀해 주십시오.", true)
        } else {
            dap("죄송합니다. \(q)\(MalHagi.eul(q)) 찾지 못했습니다. 다른 이름으로 말씀해 주십시오.", true)
        }
    }

    private func motAradeureum(_ t: String) {
        Girok.shared.namgi("mal_motaradeureum", ["mal": String(t.prefix(60))])
    }

    /// 모르면 먼저 사과하고, 거듭 모르면 기록해 두었다고 알림
    private func sagwa() -> String {
        let now = Date()
        motBeon = now.timeIntervalSince(motTtae) < 120 ? motBeon + 1 : 1
        motTtae = now
        return motBeon < 2 ? "죄송합니다. 제가 잘 알아듣지 못했습니다. "
                           : "죄송합니다. 이 말씀은 아직 배우지 못했습니다. 기록해 두었으니 다음 업그레이드에 넣겠습니다. "
    }

    // MARK: 탈것 말

    private static let talgeotMal: Set<String> = ["걸어서", "걸어", "도보로", "도보", "차로", "차", "택시로", "택시", "콜택시로",
                                                  "지하철로", "지하철", "전철로", "전철", "버스로", "버스", "타고", "차타고", "대중교통으로"]

    static func talgeotTteokgi(_ q: String) -> String {
        q.split(separator: " ").map(String.init)
            .filter { !talgeotMal.contains(MalSajeon.ttuk($0)) }
            .joined(separator: " ")
            .trimmingCharacters(in: .whitespaces)
    }

    private func talgeotChatgi(_ alts: [String]) -> Talgeot? {
        let z = MalSajeon.ttuk(alts.first ?? "")
        if z.contains("지하철") || z.contains("전철") { return .jihacheol }
        if z.contains("버스") && !z.contains("버스터미널") && !z.contains("고속버스") { return .beoseu }
        if z.contains("걸어") || z.contains("도보") || z.contains("걷자") { return .georeum }
        if z.contains("택시") || z.contains("차타고") || z.hasPrefix("차로") || z.contains("차로가") || z.contains("타고가") { return .cha }
        return nil
    }

    // MARK: 토씨

    /// 받침이 있는가(ㄹ 받침인가)
    private static func batchim(_ w: String) -> (Bool, Bool) {
        guard let u = w.unicodeScalars.last else { return (false, false) }
        let c = Int(u.value) - 0xAC00
        guard c >= 0 && c < 11172 else { return (false, false) }
        let j = c % 28
        return (j != 0, j == 8)
    }
    static func eul(_ w: String) -> String { batchim(w).0 ? "을" : "를" }
    /// 2.26.0 "약수역 5번 출구", "약수역 5번출구" → 5
    static func chulguBeon(_ t: String) -> Int? {
        guard let r = t.range(of: "([0-9]{1,2})\\s*번\\s*(출구|출입구)", options: .regularExpression) else { return nil }
        return Int(t[r].prefix { $0.isNumber })
    }
    static func eun(_ w: String) -> String { batchim(w).0 ? "은" : "는" }
    static func i(_ w: String) -> String { batchim(w).0 ? "이" : "가" }
    static func ro(_ w: String) -> String { let b = batchim(w); return b.0 && !b.1 ? "으로" : "로" }
    static func irago(_ w: String) -> String { batchim(w).0 ? "이라고" : "라고" }
}
