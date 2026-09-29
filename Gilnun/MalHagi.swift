// 말로 하기 — 길눈을 말로 시킵니다(이사장님 발안 2026-09-17, 앱 2.5.0 빌드 260928-7).
// 2.12.1 (빌드 260929-1) 하이 길눈 — 길눈이 말할 때 마이크를 닫지 않고 알아듣기만 쉼(잠긴 폰에서도 이어지게)
// 2.12.2 (빌드 260929-2) 걸러 듣기로 방송·안내 말 중에도 부름을 듣고, 부름을 들으면 길눈 소리를 멈추고 다른 소리를 낮춤
// 웹 길눈에서 이사장님이 정하신 것을 앱의 알맹이로 옮겼습니다.
//   부르기: 말로 하기 단추, 보이스오버 두 손가락 두 번 두드리기, "하이 길눈"(설정에서 켬), 화면이 잠긴 채 "시리야, 길눈"
//   알아듣기: 아이폰 자체 받아쓰기(폰 안, 무료) + 웹과 같은 나스 알아듣기 사전
//   명령 먼저: 묻는 중이라도 네·아니오가 아니면 새 명령을 따름
//   목적지를 알아들으면 되묻지 않고 곧바로 안내(가자는 말 없이 이름만 들리면 한 번 여쭘)
//   "그만"은 어디서나, 급한 순서는 긴급통화 > 경고 > 길 안내
//   대답을 마치면 땡 소리, 묻는 말에는 마이크를 한 번만 저절로 엶
//   모르는 말은 사과하고 기록해 두었다가 사전을 키움
import Foundation
import UIKit
import AVFoundation
import Combine
import SwiftUI   // 2.7.0 둘러보기 화면 길(NavigationPath)

final class MalHagi: ObservableObject {
    static let shared = MalHagi()

    enum Sangtae { case swim, deutneun, araboneun }
    @Published private(set) var sangtae: Sangtae = .swim
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
        MalDeutgi.shared.swigi()   // 2.12.1 마이크는 열어 둔 채 알아듣기만 바꿈
        bureumDolgo = false
        SoriEngine.shared.modu_geodugi()   // 명령 먼저 — 하던 말을 멈춤(경고는 남김)
        sangtae = .deutneun
        SoriEngine.shared.myeongryeongDeutneunJung = true   // 2.12.0 마이크를 여는 틈에 다른 말이 끼어 닫지 않게
        let yeolgi = { [weak self] in
            guard let self = self, self.sangtae == .deutneun else { return }
            SoriEngine.shared.myeongryeongDeutneunJung = true
            let ok = MalDeutgi.shared.myeongryeong { [weak self] alts in self?.deureum(alts) }
            if !ok {
                SoriEngine.shared.myeongryeongDeutneunJung = false
                self.sangtae = .swim
                self.malHam("지금은 마이크를 열지 못했습니다. 잠시 뒤 다시 해 주십시오.") { [weak self] in self?.bureumDasi(1.0) }
            }
        }
        if sori {
            SoriEngine.shared.sori(.deutgi)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.35, execute: yeolgi)
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
        guard sangtae == .deutneun else { return }
        let alts = alts0.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        if alts.isEmpty {
            sangtae = .swim
            if jadongYeolim > 0 {
                // 저절로 연 마이크에 말씀이 없으면 조용히 닫음
                jadongYeolim = 0
                SoriEngine.shared.sori(.ttaeng)
                bureumDasi(0.8)
                return
            }
            dapHagi("말씀이 들리지 않았습니다.", false)
            return
        }
        deureunMal = alts[0]
        sangtae = .araboneun
        cheori(alts) { [weak self] t, mutneun in self?.dapHagi(t, mutneun) }
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
                SoriEngine.shared.sori(.ttaeng)
                self.bureumDasi(0.8)
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
        ijeonMal = SoriEngine.shared.majimak
        // 2.12.2 부름을 들으면 곧바로 길눈 방송을 멈추고 다른 앱 소리를 크게 낮춤(명령을 마치면 되돌림)
        bureumSoriJurim = true
        BangsongEngine.shared.bureumMeomchum(true)
        MalDeutgi.shared.dareunSori(jurim: true)
        SoriEngine.shared.modu_geodugi()
        sangtae = .deutneun
        let ne = { [weak self] in
            guard let self = self else { return }
            self.sangtae = .swim
            self.myeongryeongYeolgi(sori: !Seoljeong.shared.malKyeojim)
        }
        if Seoljeong.shared.malKyeojim {
            SoriEngine.shared.mal("네", .annae)
            SoriEngine.shared.kkeutnamyeon(ne)
        } else {
            ne()
        }
    }

    private func moduMeomchum() {
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
        let alts = alts0.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        guard let t = alts.first else { dap("말씀이 들리지 않았습니다.", false); return }
        Girok.shared.namgi("malhagi", ["mal": String(t.prefix(60))])
        let z = MalSajeon.ttuk(t)
        let y = YeojeongEngine.shared.jigeum
        if Date().timeIntervalSince(mureumTtae) > 180 { mureum = .eopseum }

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
            let (q, r) = await MalHagi.jangsoChatgi(qA, qB0)
            DispatchQueue.main.async {
                guard let r = r else {
                    dap("찾는 중에 연결이 끊겼습니다. 통신을 확인하시고 다시 말씀해 주십시오.", false)
                    return
                }
                if r.isEmpty {
                    self.motAradeureum(t)
                    self.mureum = .mokjeok
                    self.mureumTtae = Date()
                    if hwagin {
                        dap(self.sagwa() + "다시 말씀해 주십시오.", true)
                    } else {
                        dap("죄송합니다. \(q)\(MalHagi.eul(q)) 찾지 못했습니다. 다른 이름으로 말씀해 주십시오.", true)
                    }
                    return
                }
                self.hubo = Array(r.prefix(3))
                self.huboI = 0
                self.huboTalgeot = taltgeot
                self.huboAnnae(dap, hwagin: hwagin)
            }
        }
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
        if s.itda(alts, "doumal") {
            dap(MalHagi.doumalMal, false)
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
        if s.itda(alts, "gojang") {
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
        let radio = z.contains("라디오") || zl.contains("fm") || z.contains("에프엠")
        let tv = zl.contains("tv") || z.contains("티비") || z.contains("티브이") || z.contains("텔레비전") || z.contains("듣는방송")
        let nyuseu = s.itda(alts, "nyuseu") || s.itda(alts, "jangae") || z.contains("뉴스") || z.contains("세상이야기") || z.contains("속보") || z.contains("기사읽") || z.contains("기사들려")
        let eumak = s.itda(alts, "eumak") || z.contains("노래") || z.contains("음악") || z.contains("틀어")
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
                let m = await b.chaeneolMalro(zl, kind: tv ? "tv" : "radio")
                DispatchQueue.main.async { dap(m, false) }
            }
            return true
        }
        if eumak {
            var q = t
            for w in ["틀어 주세요", "틀어 줘", "틀어줘", "틀어 봐", "틀어봐", "틀어", "들려 줘", "들려줘", "듣고 싶어", "듣자", "들을래",
                      "음악", "노래", "좀", "곡", "줘"] {
                q = q.replacingOccurrences(of: w, with: " ")
            }
            q = q.trimmingCharacters(in: .whitespacesAndNewlines.union(.punctuationCharacters))
            for p in ["을", "를"] where q.hasSuffix(p) && q.count > 2 { q = String(q.dropLast()).trimmingCharacters(in: .whitespaces) }
            if (Yeolsoe.ilgi("eumakTk") ?? "").isEmpty {
                Task { await b.nugunaTeulgi("", "나스 음악 열쇠를 아직 넣지 않으셔서 누구나 음악을 틉니다.") }
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
        var m = h.ireum + "."
        if let w = WichiEngine.shared.jigeum {
            let d = WichiEngine.geori(w.lat, w.lon, h.lat, h.lon)
            m += d < 30 ? " 지금 계신 곳 바로 가까이입니다." : " 여기서 약 \(Annae.geoMal(d))."
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

    static let doumalMal = "이렇게 말씀하시면 됩니다. 집으로 가자. 걸어서 가자. 지하철로 가자. 버스로 가자. 차에 탔어. 내렸어. 얼마나 남았어. 지금 어디야. 지금 가는 길 알려 줘. 즐겨찾기 목록. 즐겨찾기에 담아 줘. 복지콜에 전화해 줘. 콜 번호 알려 줘. 신호기 울려 줘. 신호기 찾아 줘. 근처 약국. 음악 틀어 줘. 트롯 틀어 줘. 다음 곡. 라디오 틀어 줘. 뉴스 들려줘. 음악 꺼. 도와줘, 또는 가족 이름과 화상통화. 몇 시야. 말 빠르게, 말 느리게. 다시 말해. 그만. 여정 끝. 점지도를 따라 걸을 때는 다음에 무엇, 그만 걷기, 여기 문제 있어, 여기 걸렸어. 가까운 점지도 찾아 줘."

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
    static func eun(_ w: String) -> String { batchim(w).0 ? "은" : "는" }
    static func i(_ w: String) -> String { batchim(w).0 ? "이" : "가" }
    static func ro(_ w: String) -> String { let b = batchim(w); return b.0 && !b.1 ? "으로" : "로" }
    static func irago(_ w: String) -> String { batchim(w).0 ? "이라고" : "라고" }
}
