// LVD 송출 아이폰 — 녹화·방송 지킴이 (1.6.0판, 빌드 261019-BI8, 방클, 이사장님 지시 2026-10-19 「녹화 끊김 막기」)
// 녹화(교육 녹화·인터넷 방송)나 BYOD 방송이 멈추면 3초에 한 번 조용한 두 음 알림으로 알립니다. 급하게 쉼 없이 울리지 않습니다.
// 화면을 잠갔거나 앱이 뒤로 가 있어도 울립니다(뒤에서 소리). 다시 이어지거나 마치면 멈춥니다. 통화 중에는 울리지 않습니다.
import Foundation
import AVFoundation
import AVFAudio
import UIKit

final class Jikim {
    private var timer: Timer?
    private var player: AVAudioPlayer?
    private var ullim = false
    private let meomchumInga: () -> Bool
    private let bakkwim: (Bool) -> Void

    init(meomchumInga: @escaping () -> Bool, bakkwim: @escaping (Bool) -> Void = { _ in }) {
        self.meomchumInga = meomchumInga; self.bakkwim = bakkwim
    }

    func sijak() {
        guard timer == nil else { return }
        let t = Timer(timeInterval: 1, repeats: true) { [weak self] _ in self?.salpim() }
        RunLoop.main.add(t, forMode: .common)
        timer = t
    }
    /// 지금 곧바로 한 번 살핌(앱이 뒤로 갈 때)
    func dangjang() { salpim() }
    func meomchugi() { timer?.invalidate(); timer = nil; ullimKkeugi(); ullim = false }

    private func salpim() {
        let m = meomchumInga()
        if m != ullim { ullim = m; bakkwim(m); if m { ullimSijak() } else { ullimKkeugi() } }
    }
    /// 3초 한 바퀴(조용한 두 음 + 쉼)를 되풀이 — 뒤에서도 울리게 AVAudioPlayer 로
    private func ullimSijak() {
        let ss = AVAudioSession.sharedInstance()
        if ss.category != .playAndRecord {   // 마이크를 쓰는 중이면 갈래를 건드리지 않음
            try? ss.setCategory(.playback, mode: .default, options: [.mixWithOthers])
        }
        try? ss.setActive(true)
        do {
            let p = try AVAudioPlayer(data: Jikim.sorimun())
            p.numberOfLoops = -1; p.volume = 0.6
            p.prepareToPlay(); p.play()
            player = p
        } catch { }
    }
    private func ullimKkeugi() { player?.stop(); player = nil }

    /// 3초짜리 wav — 660Hz 0.25초, 494Hz 0.25초, 나머지 쉼 (16000Hz, 16비트, 홑소리)
    static func sorimun() -> Data {
        let sr = 16000, n = sr * 3
        var pcm = [Int16](repeating: 0, count: n)
        func eum(_ f: Double, _ s0: Double) {
            let a = Int(s0 * Double(sr)), len = Int(0.25 * Double(sr))
            for i in 0..<len where a + i < n {
                let t = Double(i) / Double(sr)
                let env = min(1, t / 0.03) * max(0, 1 - t / 0.25)
                pcm[a + i] = Int16(sin(2 * .pi * f * t) * env * 9000)
            }
        }
        eum(660, 0); eum(494, 0.28)
        var d = Data()
        func u32(_ v: UInt32) { var x = v.littleEndian; d.append(Data(bytes: &x, count: 4)) }
        func u16(_ v: UInt16) { var x = v.littleEndian; d.append(Data(bytes: &x, count: 2)) }
        d.append("RIFF".data(using: .ascii)!); u32(UInt32(36 + n * 2)); d.append("WAVE".data(using: .ascii)!)
        d.append("fmt ".data(using: .ascii)!); u32(16); u16(1); u16(1); u32(UInt32(sr)); u32(UInt32(sr * 2)); u16(2); u16(16)
        d.append("data".data(using: .ascii)!); u32(UInt32(n * 2))
        pcm.withUnsafeBufferPointer { d.append(Data(buffer: $0)) }
        return d
    }
}
