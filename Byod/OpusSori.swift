// LVD 송출 아이폰 — 오푸스 소리(시험) (1.10.0판, 빌드 261026-BI14, 새방클, 이사장님 승인 2026-10-26 「해 보자. 다른 기능에 문제를 만들지 않는 선에서」)
// 안드로이드 1.10.0(OpusSori.kt)과 같음. 지금 방식(뮤 8비트, 1초에 16000)은 그대로 두고, 켜 두었을 때만 같은 해설 소리를
// 오푸스(1초에 48000, 48kbps, 20밀리초 덩이)로도 만들어 이 아이폰의 /opus 길(아이피타임 방식)이나 플린트 2(BYOD2 꼴)로 함께 내보냄.
// 오푸스 한 덩이 앞에 길이 2바이트(큰 자리 먼저)를 붙여 보냄. 길이 0은 「살아 있음」 알림.
import Foundation
import AVFoundation
import AudioToolbox

enum OpusSori {
    /// 더 보기에서 켜고 끔(처음값 꺼짐) — 다음 방송부터 적용
    static var on: Bool {
        get { UserDefaults.standard.bool(forKey: "opus_sihum") }
        set { UserDefaults.standard.set(newValue, forKey: "opus_sihum") }
    }
    private static let jamgeum = NSLock()
    private static var _naganda = false
    /// 지금 방송에서 오푸스가 실제로 나가는가(줄이개가 있는가)
    static var naganda: Bool {
        get { jamgeum.lock(); defer { jamgeum.unlock() }; return _naganda }
        set { jamgeum.lock(); _naganda = newValue; jamgeum.unlock() }
    }
    static let SALAITTA = Data([0, 0])
    /// 길이 2바이트를 붙인 한 덩이
    static func teul(_ p: Data) -> Data {
        var o = Data([UInt8((p.count >> 8) & 0xFF), UInt8(p.count & 0xFF)])
        o.append(p)
        return o
    }
    /// 오푸스 꼴(1초에 48000, 한 줄기, 20밀리초 덩이)
    static func kkol() -> AVAudioFormat? {
        var d = AudioStreamBasicDescription(mSampleRate: 48000, mFormatID: kAudioFormatOpus, mFormatFlags: 0, mBytesPerPacket: 0,
                                            mFramesPerPacket: 960, mBytesPerFrame: 0, mChannelsPerFrame: 1, mBitsPerChannel: 0, mReserved: 0)
        return AVAudioFormat(streamDescription: &d)
    }
}

/// 오푸스 줄이개 — 48000 16비트 소리를 받아 20밀리초 덩이들로 돌려줌(한 줄기에서만 부름)
final class OpusSseugi {
    private let cv: AVAudioConverter
    private let inFmt: AVAudioFormat
    private let opFmt: AVAudioFormat

    init?() {
        guard let i = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 48000, channels: 1, interleaved: true),
              let o = OpusSori.kkol(), let c = AVAudioConverter(from: i, to: o) else { return nil }
        c.bitRate = 48000
        cv = c; inFmt = i; opFmt = o
    }

    func neoki(_ pcm: [Int16]) -> [Data] {
        guard !pcm.isEmpty, let b = AVAudioPCMBuffer(pcmFormat: inFmt, frameCapacity: AVAudioFrameCount(pcm.count)), let ch = b.int16ChannelData else { return [] }
        for i in 0..<pcm.count { ch[0][i] = pcm[i] }
        b.frameLength = AVAudioFrameCount(pcm.count)
        let out = AVAudioCompressedBuffer(format: opFmt, packetCapacity: 16, maximumPacketSize: max(cv.maximumOutputPacketSize, 1500))
        var juem = false
        var err: NSError?
        let st = cv.convert(to: out, error: &err) { _, s in
            if juem { s.pointee = .noDataNow; return nil }
            juem = true
            s.pointee = .haveData
            return b
        }
        guard st != .error, let pd = out.packetDescriptions else { return [] }
        var mok: [Data] = []
        for k in 0..<Int(out.packetCount) {
            let n = Int(pd[k].mDataByteSize)
            if n <= 0 { continue }
            mok.append(Data(bytes: out.data.advanced(by: Int(pd[k].mStartOffset)), count: n))
        }
        return mok
    }
}
