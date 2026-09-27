// 음향신호기 울리기 (1.1판, 빌드 260927-9, 이사장님 승인 2026-09-27)
// ★1.1 (260927-9) 이사장님 — 건널목 앞에서 폰을 꺼내 단추를 찾게 하지 말 것. 무조건 자동으로 잡고, 불편한 분만 끄게.
//   · 길눈 앱이 켜져 있는 동안 어느 화면에서든 둘레의 블루투스 음향신호기를 늘 살핍니다(자동 울리기, 처음부터 켜짐).
//   · 음향신호기가 가까이(신호 세기 -80 이상) 잡히면 스스로 "위치 안내"를 한 번 보내 소리로 자리를 알립니다.
//   · 그 앞에서 4초 넘게 머무르시면(세기 -65 이상이 이어짐) "신호 안내"를 한 번 보냅니다.
//   · 같은 신호기에는 3분에 한 번씩만 보냅니다. 떠났다가 다시 오시면 다시 보냅니다.
//   · 횡단보도 "보행신호 음성안내 장치"(이름 KPOL01+)가 잡히면 "음성안내 장치가 있는 횡단보도 앞입니다"를 5분에 한 번 알립니다.
//   · 설정에서 끄면 살피기를 멈춥니다. 손으로 누르는 단추(화면·워치·이어폰)는 그대로 씁니다.
// 1.0 (260927-3) 경찰청 「시각장애인용 음향신호기 규격서」(2022.4) Ⅶ 부가장치 공용 프로토콜:
//   · 이름 "AHG001+", UART 서비스 0003cdd0-0000-1000-8000-00805f9b0131 (특성 cdd1·cdd2)
//   · 명령 0x31 0x00 데이터(1 위치안내 "유", 2 신호안내 "신", 3 설치 위치 음성안내), 응답 0x32 0x00 데이터(아래 네 비트 0 이면 받음)
import Foundation
import CoreBluetooth

final class SignalService: NSObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    static let shared = SignalService()

    static let serviceUUID = CBUUID(string: "0003cdd0-0000-1000-8000-00805f9b0131")
    static let charA = CBUUID(string: "0003cdd1-0000-1000-8000-00805f9b0131")
    static let charB = CBUUID(string: "0003cdd2-0000-1000-8000-00805f9b0131")
    static let prefix = "AHG001"
    static let bojaPrefix = "KPOL01"
    static let jadongKey = "adaSinhogiJadong"

    /// 결과: (ok, 말, 명령, 자동이었는지)
    typealias Done = (Bool, String, UInt8, Bool) -> Void

    private var central: CBCentralManager?
    private var ready: (() -> Void)?
    private var pending: (cmd: UInt8, auto: Bool, done: Done)?
    private var found: [UUID: (p: CBPeripheral, rssi: Int, seen: Date)] = [:]
    private var target: CBPeripheral?
    private var writeChar: CBCharacteristic?
    private var scanTimer: Timer?
    private var capTimer: Timer?
    private var watching = false
    private var jadongOn = false
    // 자동 울리기 기록 — 기기마다 마지막으로 보낸 때, 가까이 머문 첫 때
    private var lastWichi: [UUID: Date] = [:]
    private var lastSinho: [UUID: Date] = [:]
    private var gakkaSince: [UUID: Date] = [:]
    private var lastBoja: [UUID: Date] = [:]

    /// 찾기(이끌기) 중 가장 가까운 음향신호기의 신호 세기: (찾았는지, 세기 dBm)
    var onNear: ((Bool, Int) -> Void)?
    /// 자동 울리기 결과 (받았는지, 말, 명령)
    var onAuto: ((Bool, String, UInt8) -> Void)?
    /// 알려 드릴 말 (보행신호 음성안내 장치 등)
    var onAllim: ((String) -> Void)?

    var jadongKyeojim: Bool {
        if UserDefaults.standard.object(forKey: SignalService.jadongKey) == nil { return true }   // 처음부터 켜짐
        return UserDefaults.standard.bool(forKey: SignalService.jadongKey)
    }

    private func withCentral(_ go: @escaping () -> Void) {
        if central == nil { central = CBCentralManager(delegate: self, queue: .main) }
        if central?.state == .poweredOn { go() } else { ready = go }
    }

    private var salpim: Bool { watching || jadongOn }

    // MARK: 자동 울리기 켜기/끄기
    func jadongSijak() {
        DispatchQueue.main.async {
            guard self.jadongKyeojim else { return }
            self.jadongOn = true
            self.withCentral { self.startScan(forSend: false) }
            DiagLog.shared.log("sinhogiJadong", ["on": true])
        }
    }

    func setJadong(_ on: Bool) {
        UserDefaults.standard.set(on, forKey: SignalService.jadongKey)
        DispatchQueue.main.async {
            self.jadongOn = on
            if on { self.withCentral { self.startScan(forSend: false) } }
            else if self.pending == nil && !self.watching { self.central?.stopScan() }
            DiagLog.shared.log("sinhogiJadong", ["on": on])
        }
    }

    /// 이어폰·워치 재생 단추를 음향신호기 앞에서 눌렀는지 — 5초 안에 세기 -75 이상으로 잡힌 것이 있으면 참
    func apeIssna() -> Bool {
        let n = Date()
        return found.values.contains { n.timeIntervalSince($0.seen) < 5 && $0.rssi >= -75 }
    }

    // MARK: 명령 보내기 (손으로)
    func send(_ cmd: UInt8, done: @escaping Done) {
        DispatchQueue.main.async {
            if self.pending != nil { done(false, "앞의 요청을 처리하는 중입니다. 잠시 뒤 다시 눌러 주십시오.", cmd, false); return }
            self.pending = (cmd, false, done)
            self.withCentral { self.startScan(forSend: true) }
            self.capTimer?.invalidate()
            self.capTimer = Timer.scheduledTimer(withTimeInterval: 9, repeats: false) { [weak self] _ in
                self?.finish(false, "음향신호기의 답이 없습니다. 가까이 가서 다시 눌러 주십시오.")
            }
        }
    }

    /// 자동으로 — 이미 잡힌 기기에 바로 붙습니다
    private func sendAuto(_ cmd: UInt8, to p: CBPeripheral) {
        guard pending == nil else { return }
        pending = (cmd, true, { [weak self] ok, mal, c, _ in self?.onAuto?(ok, mal, c) })
        target = p
        p.delegate = self
        central?.connect(p, options: nil)
        capTimer?.invalidate()
        capTimer = Timer.scheduledTimer(withTimeInterval: 8, repeats: false) { [weak self] _ in
            self?.finish(false, "음향신호기의 답이 없습니다.")
        }
        DiagLog.shared.log("sinhogiAuto", ["cmd": Int(cmd)])
    }

    private func startScan(forSend: Bool) {
        guard let c = central, c.state == .poweredOn else { return }
        if forSend { found = found.filter { Date().timeIntervalSince($0.value.seen) < 3 } }
        c.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: true])
        if forSend {
            scanTimer?.invalidate()
            scanTimer = Timer.scheduledTimer(withTimeInterval: 2.5, repeats: false) { [weak self] _ in self?.pickAndConnect() }
        }
    }

    private func pickAndConnect() {
        guard pending != nil else { return }
        if !salpim { central?.stopScan() }
        let best = found.values.filter { Date().timeIntervalSince($0.seen) < 4 }.max { $0.rssi < $1.rssi }
        guard let b = best else { finish(false, "가까이에 블루투스 음향신호기가 없습니다. 이 횡단보도는 리모컨으로만 울릴 수 있을지 모릅니다."); return }
        target = b.p
        b.p.delegate = self
        central?.connect(b.p, options: nil)
    }

    private func finish(_ ok: Bool, _ mal: String) {
        scanTimer?.invalidate(); scanTimer = nil
        capTimer?.invalidate(); capTimer = nil
        if let t = target { central?.cancelPeripheralConnection(t) }
        target = nil; writeChar = nil
        if !salpim { central?.stopScan() }
        guard let p = pending else { return }
        pending = nil
        p.done(ok, mal, p.cmd, p.auto)
    }

    // MARK: 찾기(이끌기)
    func watch(_ on: Bool) {
        DispatchQueue.main.async {
            self.watching = on
            if on { self.withCentral { self.startScan(forSend: false) } }
            else if self.pending == nil && !self.jadongOn { self.central?.stopScan() }
        }
    }

    // MARK: CBCentralManagerDelegate
    func centralManagerDidUpdateState(_ c: CBCentralManager) {
        switch c.state {
        case .poweredOn:
            let go = ready; ready = nil; go?()
            if salpim { startScan(forSend: false) }
        case .poweredOff:
            ready = nil; finish(false, "폰의 블루투스가 꺼져 있습니다. 제어 센터에서 블루투스를 켜 주십시오.")
        case .unauthorized:
            ready = nil; finish(false, "블루투스 사용을 허락하지 않으셨습니다. 설정의 길눈에서 블루투스를 켜 주십시오.")
        case .unsupported:
            ready = nil; finish(false, "이 기기는 블루투스를 쓸 수 없습니다.")
        default: break
        }
    }

    func centralManager(_ c: CBCentralManager, didDiscover p: CBPeripheral, advertisementData ad: [String: Any], rssi RSSI: NSNumber) {
        let name = (ad[CBAdvertisementDataLocalNameKey] as? String) ?? p.name ?? ""
        let r = RSSI.intValue
        guard r < 0 && r > -100 else { return }
        let now = Date()
        // 보행신호 음성안내 장치 — 자리만 알림
        if name.hasPrefix(SignalService.bojaPrefix) {
            if jadongOn, r >= -80, now.timeIntervalSince(lastBoja[p.identifier] ?? .distantPast) > 300 {
                lastBoja[p.identifier] = now
                onAllim?("음성안내 장치가 있는 횡단보도 앞입니다.")
            }
            return
        }
        guard name.hasPrefix(SignalService.prefix) else { return }
        found[p.identifier] = (p, r, now)
        if watching {
            let near = found.values.filter { now.timeIntervalSince($0.seen) < 3 }.max { $0.rssi < $1.rssi }
            onNear?(near != nil, near?.rssi ?? -100)
        }
        guard jadongOn, pending == nil else { return }
        // 가장 센 것 하나에만
        if let best = found.values.filter({ now.timeIntervalSince($0.seen) < 3 }).max(by: { $0.rssi < $1.rssi }), best.p.identifier != p.identifier { return }
        let id = p.identifier
        if r >= -65 {
            if gakkaSince[id] == nil { gakkaSince[id] = now }
        } else if r < -72 {
            gakkaSince[id] = nil
        }
        if r >= -80, now.timeIntervalSince(lastWichi[id] ?? .distantPast) > 180 {
            lastWichi[id] = now
            sendAuto(1, to: p)
            return
        }
        if let since = gakkaSince[id], now.timeIntervalSince(since) >= 4,
           now.timeIntervalSince(lastSinho[id] ?? .distantPast) > 180,
           now.timeIntervalSince(lastWichi[id] ?? .distantPast) > 6 {
            lastSinho[id] = now
            sendAuto(2, to: p)
        }
    }

    func centralManager(_ c: CBCentralManager, didConnect p: CBPeripheral) {
        p.discoverServices([SignalService.serviceUUID])
    }
    func centralManager(_ c: CBCentralManager, didFailToConnect p: CBPeripheral, error: Error?) {
        finish(false, "음향신호기에 붙지 못했습니다. 다시 눌러 주십시오.")
    }

    // MARK: CBPeripheralDelegate
    func peripheral(_ p: CBPeripheral, didDiscoverServices error: Error?) {
        guard let s = p.services?.first(where: { $0.uuid == SignalService.serviceUUID }) else {
            finish(false, "이 음향신호기는 공용 방식을 받지 않습니다."); return
        }
        p.discoverCharacteristics([SignalService.charA, SignalService.charB], for: s)
    }

    func peripheral(_ p: CBPeripheral, didDiscoverCharacteristicsFor s: CBService, error: Error?) {
        let cs = s.characteristics ?? []
        // 쓰기가 되는 특성으로 명령을 보내고, 알림이 되는 특성으로 답을 받습니다(규격서의 TX·RX 이름은 기기 쪽 기준이라 성질로 가립니다)
        let w = cs.first { $0.properties.contains(.write) || $0.properties.contains(.writeWithoutResponse) }
        for c in cs where c.properties.contains(.notify) || c.properties.contains(.indicate) { p.setNotifyValue(true, for: c) }
        guard let wc = w, let cmd = pending?.cmd else { finish(false, "음향신호기에 명령을 보낼 수 없습니다."); return }
        writeChar = wc
        let data = Data([0x31, 0x00, cmd & 0x0F])
        let type: CBCharacteristicWriteType = wc.properties.contains(.write) ? .withResponse : .withoutResponse
        p.writeValue(data, for: wc, type: type)
        // 답이 오지 않는 기기도 있어 3초 기다린 뒤에는 보냈다고만 알립니다
        capTimer?.invalidate()
        capTimer = Timer.scheduledTimer(withTimeInterval: 3, repeats: false) { [weak self] _ in
            self?.finish(true, SignalService.boneMal(cmd, hwakin: false))
        }
    }

    func peripheral(_ p: CBPeripheral, didWriteValueFor c: CBCharacteristic, error: Error?) {
        if error != nil { finish(false, "음향신호기가 명령을 받지 않았습니다. 다시 눌러 주십시오.") }
    }

    func peripheral(_ p: CBPeripheral, didUpdateValueFor c: CBCharacteristic, error: Error?) {
        guard let v = c.value, v.count >= 3, v[0] == 0x32, let cmd = pending?.cmd else { return }
        if (v[2] & 0x0F) == 0 { finish(true, SignalService.boneMal(cmd, hwakin: true)) }
        else { finish(false, "음향신호기가 요청을 거절했습니다. 잠시 뒤 다시 눌러 주십시오.") }
    }

    static func boneMal(_ cmd: UInt8, hwakin: Bool) -> String {
        let what: String
        switch cmd {
        case 1: what = "위치 안내"
        case 2: what = "신호 안내"
        default: what = "설치 위치 안내"
        }
        return hwakin ? "음향신호기가 \(what) 요청을 받았습니다." : "음향신호기에 \(what) 요청을 보냈습니다."
    }
}
