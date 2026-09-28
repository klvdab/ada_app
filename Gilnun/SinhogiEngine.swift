// 음향신호기 — 경찰청 「시각장애인용 음향신호기 규격서」(2022.4) Ⅶ 부가장치 공용 프로토콜로 폰이 블루투스로 울립니다.
// 앱 2.6.0 (빌드 260928-8) — 옛 껍데기 앱(1.x, 260927-3·260927-9)에서 이사장님이 승인하신 방식을 새 앱의 알맹이로 옮김.
//   · 건널목 앞에서 폰을 꺼내 단추를 찾게 하지 않음 — 길눈이 켜져 있으면 둘레의 음향신호기를 늘 살피고 스스로 울림(처음부터 켜짐, 설정에서 끔)
//   · 가까이(세기 -80 이상) 잡히면 "위치 안내" 한 번, 그 앞에 4초 넘게 머무르면(-65 이상) "신호 안내" 한 번, 같은 신호기에는 3분에 한 번
//   · 자동으로 울릴 때 길눈은 말하지 않고 짧게 진동만(신호기가 소리를 냄)
//   · 보행신호 음성안내 장치(이름 KPOL01)가 잡히면 "음성안내 장치가 있는 횡단보도 앞입니다"를 5분에 한 번
//   · 손으로 울리기: 워치 단추, 이어폰 재생 단추(신호기 앞일 때), 말로 하기, 길 찾기 첫 화면의 그 밖에 펼치기
//   · 음향신호기 찾기: 가까워질수록 확신음이 빨라짐, 바로 앞이면 알리고 위치 안내를 울림
// 규격: 이름 "AHG001", 서비스 0003cdd0-0000-1000-8000-00805f9b0131, 명령 0x31 0x00 (1 위치안내 / 2 신호안내 / 3 설치 위치 음성안내),
//       답 0x32 0x00 (아래 네 비트가 0 이면 받음)
// 폰이 잠겨 주머니에 있을 때는 아이폰 규칙상 공용 서비스 번호를 광고에 싣는 신호기만 잡힙니다.
import Foundation
import CoreBluetooth
import UIKit
import Combine

final class SinhogiEngine: NSObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    static let shared = SinhogiEngine()

    static let serviceUUID = CBUUID(string: "0003cdd0-0000-1000-8000-00805f9b0131")
    static let charA = CBUUID(string: "0003cdd1-0000-1000-8000-00805f9b0131")
    static let charB = CBUUID(string: "0003cdd2-0000-1000-8000-00805f9b0131")
    static let apMal = "AHG001"
    static let bojaApMal = "KPOL01"

    typealias Done = (Bool, String) -> Void

    private var central: CBCentralManager?
    private var junbiDoemyeon: (() -> Void)?
    private var pending: (cmd: UInt8, jadong: Bool, done: Done)?
    private var chajeun: [UUID: (p: CBPeripheral, rssi: Int, ttae: Date)] = [:]
    private var target: CBPeripheral?
    private var chatgiSigye: Timer?
    private var hanSigye: Timer?
    private var jadongOn = false
    private var dwi = false
    private var lastWichi: [UUID: Date] = [:]
    private var lastSinho: [UUID: Date] = [:]
    private var gakkaSince: [UUID: Date] = [:]
    private var lastBoja: [UUID: Date] = [:]
    private var ssak = Set<AnyCancellable>()

    // 찾기(이끌기)
    private(set) var chatneunJung = false
    private var chatgiSijak = Date()
    private var chatgiSori = Date.distantPast
    private var eopdaMal = false

    private var salpim: Bool { jadongOn || chatneunJung || pending != nil }

    // MARK: 세우기

    func sijak() {
        let nc = NotificationCenter.default
        nc.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in
            self?.dwi = true
            self?.dasiSalpim()
        }
        nc.addObserver(forName: UIApplication.willEnterForegroundNotification, object: nil, queue: .main) { [weak self] _ in
            self?.dwi = false
            self?.dasiSalpim()
        }
        if Seoljeong.shared.sinhogiJadong { jadongKyeogi(true) }
        Seoljeong.shared.$sinhogiJadong
            .dropFirst()
            .removeDuplicates()
            .sink { [weak self] on in self?.jadongKyeogi(on) }
            .store(in: &ssak)
    }

    /// 자동 울리기 켜기/끄기(설정)
    func jadongKyeogi(_ on: Bool) {
        DispatchQueue.main.async {
            self.jadongOn = on
            if on { self.withCentral { self.dasiSalpim() } } else { self.dasiSalpim() }
            Girok.shared.namgi("sinhogi_jadong", ["on": on])
        }
    }

    /// 이어폰·워치 재생 단추를 음향신호기 앞에서 눌렀는가 — 5초 안에 세기 -75 이상으로 잡힌 것이 있으면 참
    func apeIssna() -> Bool {
        let n = Date()
        return chajeun.values.contains { n.timeIntervalSince($0.ttae) < 5 && $0.rssi >= -75 }
    }

    /// 손으로 울리기 — 결과를 말하고, 진동하고, 워치에도 알림
    func ulligi(_ cmd: UInt8) {
        Girok.shared.namgi("sinhogi_son", ["cmd": Int(cmd)])
        bonaegi(cmd) { ok, mal in
            SoriEngine.shared.mal(mal, .annae)
            Jindong.hagi(ok ? "arrive" : "long")
            WatchLink.shared.sinhogiDap(ok, mal)
        }
    }

    private func withCentral(_ go: @escaping () -> Void) {
        if central == nil { central = CBCentralManager(delegate: self, queue: .main) }
        if central?.state == .poweredOn { go() } else { junbiDoemyeon = go }
    }

    /// 살피기를 지금 형편에 맞춰 다시 — 앞에서는 모든 기기를, 뒤(잠김)에서는 공용 서비스 번호만
    private func dasiSalpim() {
        guard let c = central, c.state == .poweredOn else { return }
        c.stopScan()
        guard salpim else { return }
        if dwi {
            c.scanForPeripherals(withServices: [SinhogiEngine.serviceUUID], options: nil)
        } else {
            c.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: true])
        }
    }

    // MARK: 보내기

    private func bonaegi(_ cmd: UInt8, done: @escaping Done) {
        DispatchQueue.main.async {
            if self.pending != nil { done(false, "앞의 요청을 처리하는 중입니다. 잠시 뒤 다시 해 주십시오."); return }
            self.pending = (cmd, false, done)
            self.withCentral {
                self.chajeun = self.chajeun.filter { Date().timeIntervalSince($0.value.ttae) < 3 }
                self.dasiSalpim()
                DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) { self.goruGoBuchigi() }
            }
            self.hanSigye?.invalidate()
            self.hanSigye = Timer.scheduledTimer(withTimeInterval: 9, repeats: false) { [weak self] _ in
                self?.maechim(false, "음향신호기의 답이 없습니다. 가까이 가서 다시 해 주십시오.")
            }
        }
    }

    private func jadongBonaegi(_ cmd: UInt8, _ p: CBPeripheral) {
        guard pending == nil else { return }
        pending = (cmd, true, { ok, _ in
            if ok { Jindong.hagi("short") }
        })
        target = p
        p.delegate = self
        central?.connect(p, options: nil)
        hanSigye?.invalidate()
        hanSigye = Timer.scheduledTimer(withTimeInterval: 8, repeats: false) { [weak self] _ in
            self?.maechim(false, "음향신호기의 답이 없습니다.")
        }
        Girok.shared.namgi("sinhogi_jadong_bonaem", ["cmd": Int(cmd)])
    }

    private func goruGoBuchigi() {
        guard pending != nil, target == nil else { return }
        let best = chajeun.values.filter { Date().timeIntervalSince($0.ttae) < 4 }.max { $0.rssi < $1.rssi }
        guard let b = best else {
            maechim(false, "가까이에 블루투스 음향신호기가 잡히지 않습니다. 이 횡단보도는 리모컨으로만 울릴 수 있을지 모릅니다.")
            return
        }
        target = b.p
        b.p.delegate = self
        central?.connect(b.p, options: nil)
    }

    private func maechim(_ ok: Bool, _ mal: String) {
        hanSigye?.invalidate()
        hanSigye = nil
        if let t = target { central?.cancelPeripheralConnection(t) }
        target = nil
        guard let p = pending else { return }
        pending = nil
        if !salpim { central?.stopScan() }
        if !p.jadong { Girok.shared.namgi("sinhogi_gyeolgwa", ["ok": ok, "cmd": Int(p.cmd)]) }
        p.done(ok, mal)
    }

    static func boneMal(_ cmd: UInt8, hwagin: Bool) -> String {
        let what: String
        switch cmd {
        case 1: what = "위치 안내"
        case 2: what = "신호 안내"
        default: what = "설치 위치 안내"
        }
        return hwagin ? "음향신호기가 \(what) 요청을 받았습니다." : "음향신호기에 \(what) 요청을 보냈습니다."
    }

    // MARK: 찾기(이끌기)

    func chatgiKyeogi() {
        DispatchQueue.main.async {
            guard !self.chatneunJung else { return }
            self.chatneunJung = true
            self.chatgiSijak = Date()
            self.eopdaMal = false
            self.withCentral { self.dasiSalpim() }
            self.chatgiSigye?.invalidate()
            self.chatgiSigye = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { [weak self] _ in self?.chatgiBoda() }
            Girok.shared.namgi("sinhogi_chatgi", [:])
        }
    }

    func chatgiKkeugi(_ mal: String? = nil) {
        DispatchQueue.main.async {
            guard self.chatneunJung else { return }
            self.chatneunJung = false
            self.chatgiSigye?.invalidate()
            self.chatgiSigye = nil
            self.dasiSalpim()
            if let m = mal { SoriEngine.shared.mal(m, .annae) }
        }
    }

    private func chatgiBoda() {
        let n = Date()
        if n.timeIntervalSince(chatgiSijak) > 60 {
            chatgiKkeugi("음향신호기 찾기를 마칩니다.")
            return
        }
        let best = chajeun.values.filter { n.timeIntervalSince($0.ttae) < 3 }.max { $0.rssi < $1.rssi }
        guard let b = best else {
            if !eopdaMal && n.timeIntervalSince(chatgiSijak) > 6 {
                eopdaMal = true
                SoriEngine.shared.mal("가까이에 음향신호기가 아직 잡히지 않습니다. 천천히 둘러보십시오.", .annae)
            }
            return
        }
        if b.rssi >= -55 {
            chatgiKkeugi("음향신호기 바로 앞입니다.")
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) { self.ulligi(1) }
            return
        }
        let gan: Double = b.rssi >= -62 ? 0.35 : (b.rssi >= -70 ? 0.7 : (b.rssi >= -80 ? 1.2 : 2.0))
        if n.timeIntervalSince(chatgiSori) >= gan && !SoriEngine.shared.bappeum {
            chatgiSori = n
            SoriEngine.shared.sori(.hwaksin)
        }
    }

    // MARK: CBCentralManagerDelegate

    func centralManagerDidUpdateState(_ c: CBCentralManager) {
        switch c.state {
        case .poweredOn:
            let go = junbiDoemyeon
            junbiDoemyeon = nil
            go?()
            dasiSalpim()
        case .poweredOff:
            junbiDoemyeon = nil
            maechim(false, "폰의 블루투스가 꺼져 있습니다. 제어 센터에서 블루투스를 켜 주십시오.")
        case .unauthorized:
            junbiDoemyeon = nil
            maechim(false, "블루투스 사용을 허락하지 않으셨습니다. 아이폰 설정의 길눈에서 블루투스를 켜 주십시오.")
        case .unsupported:
            junbiDoemyeon = nil
            maechim(false, "이 기기는 블루투스를 쓸 수 없습니다.")
        default:
            break
        }
    }

    func centralManager(_ c: CBCentralManager, didDiscover p: CBPeripheral, advertisementData ad: [String: Any], rssi RSSI: NSNumber) {
        let name = (ad[CBAdvertisementDataLocalNameKey] as? String) ?? p.name ?? ""
        let r = RSSI.intValue
        guard r < 0 && r > -100 else { return }
        let now = Date()
        if name.hasPrefix(SinhogiEngine.bojaApMal) {
            if jadongOn, r >= -80, now.timeIntervalSince(lastBoja[p.identifier] ?? .distantPast) > 300 {
                lastBoja[p.identifier] = now
                SoriEngine.shared.mal("음성안내 장치가 있는 횡단보도 앞입니다.", .annae)
                Girok.shared.namgi("sinhogi_boja", [:])
            }
            return
        }
        let gongyong = ((ad[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID]) ?? []).contains(SinhogiEngine.serviceUUID)
        guard name.hasPrefix(SinhogiEngine.apMal) || gongyong else { return }
        chajeun[p.identifier] = (p, r, now)
        guard jadongOn, pending == nil, !chatneunJung else { return }
        // 가장 센 것 하나에만
        if let best = chajeun.values.filter({ now.timeIntervalSince($0.ttae) < 3 }).max(by: { $0.rssi < $1.rssi }),
           best.p.identifier != p.identifier { return }
        let id = p.identifier
        if r >= -65 {
            if gakkaSince[id] == nil { gakkaSince[id] = now }
        } else if r < -72 {
            gakkaSince[id] = nil
        }
        if r >= -80, now.timeIntervalSince(lastWichi[id] ?? .distantPast) > 180 {
            lastWichi[id] = now
            jadongBonaegi(1, p)
            return
        }
        if let since = gakkaSince[id], now.timeIntervalSince(since) >= 4,
           now.timeIntervalSince(lastSinho[id] ?? .distantPast) > 180,
           now.timeIntervalSince(lastWichi[id] ?? .distantPast) > 6 {
            lastSinho[id] = now
            jadongBonaegi(2, p)
        }
    }

    func centralManager(_ c: CBCentralManager, didConnect p: CBPeripheral) {
        p.discoverServices([SinhogiEngine.serviceUUID])
    }

    func centralManager(_ c: CBCentralManager, didFailToConnect p: CBPeripheral, error: Error?) {
        maechim(false, "음향신호기에 붙지 못했습니다. 다시 해 주십시오.")
    }

    // MARK: CBPeripheralDelegate

    func peripheral(_ p: CBPeripheral, didDiscoverServices error: Error?) {
        guard let s = p.services?.first(where: { $0.uuid == SinhogiEngine.serviceUUID }) else {
            maechim(false, "이 음향신호기는 공용 방식을 받지 않습니다.")
            return
        }
        p.discoverCharacteristics([SinhogiEngine.charA, SinhogiEngine.charB], for: s)
    }

    func peripheral(_ p: CBPeripheral, didDiscoverCharacteristicsFor s: CBService, error: Error?) {
        let cs = s.characteristics ?? []
        // 쓰기가 되는 특성으로 명령을 보내고, 알림이 되는 특성으로 답을 받음(규격서의 TX·RX 이름은 기기 쪽 기준이라 성질로 가림)
        let w = cs.first { $0.properties.contains(.write) || $0.properties.contains(.writeWithoutResponse) }
        for c in cs where c.properties.contains(.notify) || c.properties.contains(.indicate) { p.setNotifyValue(true, for: c) }
        guard let wc = w, let cmd = pending?.cmd else {
            maechim(false, "음향신호기에 명령을 보낼 수 없습니다.")
            return
        }
        let data = Data([0x31, 0x00, cmd & 0x0F])
        let ty: CBCharacteristicWriteType = wc.properties.contains(.write) ? .withResponse : .withoutResponse
        p.writeValue(data, for: wc, type: ty)
        // 답이 오지 않는 기기도 있어 3초 기다린 뒤에는 보냈다고만 알림
        hanSigye?.invalidate()
        hanSigye = Timer.scheduledTimer(withTimeInterval: 3, repeats: false) { [weak self] _ in
            self?.maechim(true, SinhogiEngine.boneMal(cmd, hwagin: false))
        }
    }

    func peripheral(_ p: CBPeripheral, didWriteValueFor c: CBCharacteristic, error: Error?) {
        if error != nil { maechim(false, "음향신호기가 명령을 받지 않았습니다. 다시 해 주십시오.") }
    }

    func peripheral(_ p: CBPeripheral, didUpdateValueFor c: CBCharacteristic, error: Error?) {
        guard let v = c.value, v.count >= 3, v[0] == 0x32, let cmd = pending?.cmd else { return }
        if (v[2] & 0x0F) == 0 {
            maechim(true, SinhogiEngine.boneMal(cmd, hwagin: true))
        } else {
            maechim(false, "음향신호기가 요청을 거절했습니다. 잠시 뒤 다시 해 주십시오.")
        }
    }
}
