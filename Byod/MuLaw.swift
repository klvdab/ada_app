// BYOD 방송 아이폰 — 뮤 소리 줄이기 (1.1.5판, 빌드 261010-BI1, 방송클)
// 안드로이드판·노트북판(현장 해설방송 3.3판)과 똑같이 16비트 소리를 8비트 뮤(G.711 μ-law)로 줄입니다.
// 듣는 화면(deut.html)의 DEC 표가 이것을 되돌려 틉니다.
import Foundation

enum MuLaw {
    private static let BIAS: Int32 = 0x84
    private static let CLIP: Int32 = 32635

    static func hana(_ sample: Int16) -> UInt8 {
        var s = Int32(sample)
        let sign: Int32 = s < 0 ? 0x80 : 0
        if s < 0 { s = -s }
        if s > CLIP { s = CLIP }
        s += BIAS
        var exponent: Int32 = 7
        var mask: Int32 = 0x4000
        while (s & mask) == 0 && exponent > 0 { exponent -= 1; mask >>= 1 }
        let mantissa = (s >> (exponent + 3)) & 0x0F
        return UInt8(truncatingIfNeeded: ~(sign | (exponent << 4) | mantissa) & 0xFF)
    }

    /// pcm 을 줄여 담아 돌려줌
    static func jurigi(_ pcm: [Int16]) -> Data {
        var out = Data(count: pcm.count)
        out.withUnsafeMutableBytes { (p: UnsafeMutableRawBufferPointer) in
            for i in 0..<pcm.count { p[i] = hana(pcm[i]) }
        }
        return out
    }

    /// 소리 없음(뮤 0xFF)
    static func goyo(_ n: Int) -> Data { Data(repeating: 0xFF, count: n) }
}
