// 박수 소리 — 자봉 앱 2.7.0 (261006-I2, 이사장님 승인 2026-10-06 "정답을 맞추면 박수 소리도 나오게")
// 자봉 앱 안에서만 만들어 씁니다. 길눈의 소리 부품(SoriEngine)은 건드리지 않아 길눈이 흔들리지 않게 합니다.
import Foundation
import AVFoundation

enum Baksu {
    private static var player: AVAudioPlayer?

    /// 박수 치기 — 크게면 여러 사람이 더 오래 치는 박수(세 문제를 다 맞혔을 때, 다 그렸을 때)
    static func chigi(keuge: Bool = false) {
        let data = mandeulgi(su: keuge ? 26 : 10, gil: keuge ? 2.4 : 1.1)
        DispatchQueue.main.async {
            player = try? AVAudioPlayer(data: data)
            player?.volume = 0.9
            player?.play()
        }
    }

    /// 짧게 터지는 잡음을 여러 번 겹쳐 박수 소리를 만듦(16비트 한 줄 wav)
    private static func mandeulgi(su: Int, gil: Double) -> Data {
        let rate = 22050
        let n = Int(Double(rate) * gil)
        var s = [Float](repeating: 0, count: n)
        let ttae = rate / 25                      // 한 번 치는 소리 길이 40천분의 1초
        for k in 0..<su {
            let pyeon = (Double(k) + Double.random(in: 0..<1)) / Double(su)
            let start = Int(Double(max(1, n - ttae)) * pyeon)
            let keugi = Float.random(in: 0.35...0.75)
            for i in 0..<ttae where start + i < n {
                let jureum = expf(-Float(i) / Float(ttae) * 6)
                s[start + i] += Float.random(in: -1...1) * keugi * jureum
            }
        }
        // 끝을 부드럽게 줄임
        let kkeut = min(n, rate / 4)
        for i in 0..<kkeut { s[n - 1 - i] *= Float(i) / Float(kkeut) }
        let goj = max(s.map { abs($0) }.max() ?? 1, 0.0001)
        var d = Data()
        func u32(_ v: UInt32) { var x = v.littleEndian; d.append(Data(bytes: &x, count: 4)) }
        func u16(_ v: UInt16) { var x = v.littleEndian; d.append(Data(bytes: &x, count: 2)) }
        d.append(contentsOf: Array("RIFF".utf8)); u32(UInt32(36 + n * 2)); d.append(contentsOf: Array("WAVE".utf8))
        d.append(contentsOf: Array("fmt ".utf8)); u32(16); u16(1); u16(1); u32(UInt32(rate)); u32(UInt32(rate * 2)); u16(2); u16(16)
        d.append(contentsOf: Array("data".utf8)); u32(UInt32(n * 2))
        for v in s {
            let p = max(-1, min(1, v / goj * 0.9))
            u16(UInt16(bitPattern: Int16(p * 32000)))
        }
        return d
    }
}

// MARK: 탭 옮기기 — 나눔의 그려 주세요에서 봉사 탭으로 보낼 때 씀
final class JbTabGil: ObservableObject {
    static let shared = JbTabGil()
    @Published var tab = 0
}
