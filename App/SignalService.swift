// 음향신호기 울리기 (1.0판, 빌드 260927-3, 이사장님 승인 2026-09-27)
// 경찰청 「시각장애인용 음향신호기 규격서」(2022.4) Ⅶ 부가장치의 공용 프로토콜을 따릅니다.
//   · 블루투스를 단 음향신호기는 이름이 "AHG001+" 로 시작합니다 (예: AHG001+BBA050E123D4+).
//   · UART 서비스 0003cdd0-0000-1000-8000-00805f9b0131 (특성 cdd1·cdd2)
//   · 명령 3바이트: 0x31 0x00 데이터 — 데이터 1 위치안내(리모컨 "유"), 2 신호안내(리모컨 "신"), 3 설치 위치 음성안내
//   · 응답 3바이트: 0x32 0x00 데이터 — 데이터의 아래 네 비트가 0 이면 받음(ACK), 1 이면 못 받음(NAK)
// 하는 일: 둘레를 2.5초 훑어 가장 가까운(신호가 센) 음향신호기에 붙어 명령을 보내고, 응답을 받거나 3초가 지나면 끊습니다.
// 따로 "찾기"를 켜면 가까이 있는 음향신호기의 신호 세기를 계속 알려 줍니다(횡단보도 쪽으로 이끌기용).
import Foundation
import CoreBluetooth

final class SignalService: NSObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    static let shared = SignalService()

    static let serviceUUID = CBUUID(string: "0003cdd0-0000-1000-8000-00805f9b0131")
    static let charA = CBUUID(string: "0003cdd1-0000-1000-8000-00805f9b0131")
    static let charB = CBUUID(string: "0003cdd2-0000-1000-8000-00805f9b0131")
    static let prefix = "AHG001"

    /// 결과: (ok, 말) — ok 는 음향신호기가 받았다고 답했는지
    typealias Done = (Bool, String) -> Void

    private var central: CBCentralManager?
    private var ready: (() -> Void)?
    private var pending: (cmd: UInt8, done: Done)?
    private var found: [UUID: (p: CBPeripheral, rssi: Int, seen: Date)] = [:]
    private var target: CBPeripheral?
    private var writeChar: CBCharacteristic?
    private var scanTimer: Timer?
    private var capTimer: Timer?
    private var watching = false
    /// 찾기(이끌기) 중 가장 가까운 음향신호기의 신호 세기를 알려 줍니다: (찾았는지, 세기 dBm)
    var onNear: ((Bool, Int) -> Void)?

    private func withCentral(_ go: @escaping () -> Void) {
        if central == nil { central = CBCentralManager(delegate: self, queue: .main) }
        if central?.state == .poweredOn { go() } else { ready = go }
    }

    // MARK: 명령 보내기
    func send(_ cmd: UInt8, done: @escaping Done) {
        DispatchQueue.main.async {
            if self.pending != nil { done(false, "앞의 요청을 처리하는 중입니다. 잠시 뒤 다시 눌러 주십시오."); return }
            self.pending = (cmd, done)
            self.withCentral { self.startScan(forSend: true) }
            // 블루투스가 꺼져 있거나 허락이 없으면 여기서 멈추지 않게
            self.capTimer?.invalidate()
            self.capTimer = Timer.scheduledTimer(withTimeInterval: 9, repeats: false) { [weak self] _ in
                self?.finish(false, "음향신호기의 답이 없습니다. 가까이 가서 다시 눌러 주십시오.")
            }
        }
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
        if !watching { central?.stopScan() }
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
        if !watching { central?.stopScan() }
        guard let p = pending else { return }
        pending = nil
        p.done(ok, mal)
    }

    // MARK: 찾기(이끌기)
    func watch(_ on: Bool) {
        DispatchQueue.main.async {
            self.watching = on
            if on { self.withCentral { self.startScan(forSend: false) } }
            else if self.pending == nil { self.central?.stopScan() }
        }
    }

    // MARK: CBCentralManagerDelegate
    func centralManagerDidUpdateState(_ c: CBCentralManager) {
        switch c.state {
        case .poweredOn:
            let go = ready; ready = nil; go?()
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
        guard name.hasPrefix(SignalService.prefix) else { return }
        let r = RSSI.intValue
        guard r < 0 && r > -100 else { return }
        found[p.identifier] = (p, r, Date())
        if watching {
            let near = found.values.filter { Date().timeIntervalSince($0.seen) < 3 }.max { $0.rssi < $1.rssi }
            onNear?(near != nil, near?.rssi ?? -100)
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
