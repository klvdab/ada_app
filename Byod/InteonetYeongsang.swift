// 1.17.0 (261115-BI26, 대클, 이사장님 지시 2026-11-15 「BYOD 방송을 겸할 때 인터넷으로는 영상과 음성이 동시에」 — 새방클 쪽지 17시 35분·18시 40분)
// BYOD 방송과 함께 나가는 인터넷 방송에 뒤 카메라 영상을 싣습니다.
//   1. 현장(플)으로는 지금처럼 해설 소리만 나갑니다. 이 파일은 현장 방송에 손대지 않습니다.
//   2. 인터넷 쪽: 뒤 카메라 영상(640x360, H.264)과 현장으로 나가는 것과 같은 해설 소리(뮤 → 16비트 → AAC)를
//      2초 조각 mp4(조각 mp4, fMP4)로 만들어 InteonetBonaegi 에 넘깁니다. 「인터넷 방송만 하기」와 같은 video/mp4 입니다.
//   3. 마이크는 한 곳(현장 방송)만 씁니다 — 카메라 세션이 소리 설정(AVAudioSession)을 건드리지 않게 막아 둠.
//   4. 카메라가 안 되거나, 화면이 꺼져 아이폰이 카메라를 막으면 실패를 알리고, InteonetBonaegi 가 소리만 보내는 길로 돌아갑니다.
import Foundation
import AVFoundation
import UIKit
import UniformTypeIdentifiers

final class InteonetYeongsang: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate, AVAssetWriterDelegate {
    static let shared = InteonetYeongsang()
    static let RATE = 16000

    /// 조각 mp4 가 나올 때마다(첫 조각은 머리 조각) — InteonetBonaegi 줄기로 넘김
    var jogakBatgi: ((Data) -> Void)?
    /// 영상을 이어 갈 수 없을 때 한 번 — 까닭 말
    var silpae: ((String) -> Void)?

    private let q = DispatchQueue(label: "byod.inteonet.yeongsang")
    private var ss: AVCaptureSession?
    private var writer: AVAssetWriter?
    private var vIn: AVAssetWriterInput?
    private var aIn: AVAssetWriterInput?
    private var aFmt: CMAudioFormatDescription?
    private var dolgo = false
    private var sijakTtae: CMTime = .invalid      // 조각 mp4 첫 때(첫 영상 틀)
    private var aDaeum: CMTime = .invalid         // 다음 소리 덩이의 때
    private var silpaeHam = false
    private var bomyeon: [NSObjectProtocol] = []

    private let jamgeum = NSLock()
    private var _bogi: AVCaptureVideoPreviewLayer?
    /// 첫 화면이 보는 영상 판(영상이 나가는 동안만 있음)
    var bogiPan: AVCaptureVideoPreviewLayer? { jamgeum.lock(); defer { jamgeum.unlock() }; return _bogi }

    static var heorak: Bool { AVCaptureDevice.authorizationStatus(for: .video) == .authorized }

    // MARK: 켜고 끄기 — InteonetBonaegi 가 부름

    func kyeogi() {
        q.async {
            guard !self.dolgo else { return }
            self.dolgo = true; self.silpaeHam = false
            self.sijakTtae = .invalid; self.aDaeum = .invalid
            self.writer = nil; self.vIn = nil; self.aIn = nil
            guard InteonetYeongsang.heorak else { self.kkeutnae("카메라 허락이 없어 영상은 보내지 못합니다."); return }
            guard self.sesyeon() else { return }
            self.ss?.startRunning()
            DispatchQueue.main.async {
                UIApplication.shared.isIdleTimerDisabled = true   // 화면이 꺼지면 아이폰이 카메라를 막음
                let nc = NotificationCenter.default
                self.bomyeon.append(nc.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: nil) { [weak self] _ in
                    self?.q.async { self?.kkeutnae("화면이 꺼져 카메라가 멈췄습니다.") }
                })
            }
            ByodGirok.shared.namgi("inteonet_yeongsang", ["mal": "켬"])
        }
    }

    func kkeugi() {
        q.async { self.dakgi() }
    }

    /// 0.1초 뮤 조각 — 현장으로 나가는 것과 같은 소리
    func soriNeoki(_ mu: Data) {
        guard !mu.isEmpty else { return }
        let jigeum = CMClockGetTime(CMClockGetHostTimeClock())
        q.async { self.soriButigi(mu, jigeum) }
    }

    // MARK: 줄기 안

    private func kkeutnae(_ mal: String) {
        guard dolgo else { return }
        let han = !silpaeHam
        silpaeHam = true
        dakgi()
        ByodGirok.shared.namgi("inteonet_yeongsang", ["mal": mal])
        if han { silpae?(mal) }
    }

    private func dakgi() {
        dolgo = false
        ss?.stopRunning()
        if let w = writer, w.status == .writing {
            w.delegate = nil   // 닫는 쓰개의 늦은 조각이 새 방에 섞이지 않게(마지막 2초는 버림)
            vIn?.markAsFinished(); aIn?.markAsFinished()
            w.finishWriting { }
        }
        writer = nil; vIn = nil; aIn = nil; ss = nil
        jamgeum.lock(); _bogi = nil; jamgeum.unlock()
        let b = bomyeon; bomyeon = []
        DispatchQueue.main.async {
            b.forEach { NotificationCenter.default.removeObserver($0) }
            if !YeongsangNokhwa.kyeojim { UIApplication.shared.isIdleTimerDisabled = false }
        }
    }

    /// 뒤 카메라, 영상만(소리 설정은 건드리지 않음)
    private func sesyeon() -> Bool {
        let s = AVCaptureSession()
        s.automaticallyConfiguresApplicationAudioSession = false
        s.beginConfiguration()
        if s.canSetSessionPreset(.hd1280x720) { s.sessionPreset = .hd1280x720 }
        guard let cam = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let inp = try? AVCaptureDeviceInput(device: cam), s.canAddInput(inp) else {
            s.commitConfiguration(); kkeutnae("뒤 카메라를 열지 못했습니다."); return false
        }
        s.addInput(inp)
        let out = AVCaptureVideoDataOutput()
        out.alwaysDiscardsLateVideoFrames = true
        out.setSampleBufferDelegate(self, queue: q)
        guard s.canAddOutput(out) else { s.commitConfiguration(); kkeutnae("영상 장치를 붙이지 못했습니다."); return false }
        s.addOutput(out)
        if let c = out.connection(with: .video), c.isVideoOrientationSupported { c.videoOrientation = .landscapeRight }
        s.commitConfiguration()
        ss = s
        let pan = AVCaptureVideoPreviewLayer(session: s)
        pan.videoGravity = .resizeAspect
        jamgeum.lock(); _bogi = pan; jamgeum.unlock()
        return true
    }

    /// 첫 영상 틀이 오면 조각 mp4 쓰개를 엶
    private func sseugaeYeolgi(_ t: CMTime) -> Bool {
        let w = AVAssetWriter(contentType: UTType(AVFileType.mp4.rawValue) ?? .mpeg4Movie)
        w.outputFileTypeProfile = .mpeg4AppleHLS
        w.preferredOutputSegmentInterval = CMTime(seconds: 2, preferredTimescale: 1)
        w.initialSegmentStartTime = t
        w.delegate = self
        let v = AVAssetWriterInput(mediaType: .video, outputSettings: [
            AVVideoCodecKey: AVVideoCodecType.h264,
            AVVideoWidthKey: 640, AVVideoHeightKey: 360,
            AVVideoScalingModeKey: AVVideoScalingModeResizeAspect,
            AVVideoCompressionPropertiesKey: [
                AVVideoAverageBitRateKey: 700_000,
                AVVideoMaxKeyFrameIntervalDurationKey: 2,
                AVVideoExpectedSourceFrameRateKey: 24,
                AVVideoProfileLevelKey: AVVideoProfileLevelH264MainAutoLevel
            ]
        ])
        v.expectsMediaDataInRealTime = true
        let a = AVAssetWriterInput(mediaType: .audio, outputSettings: [
            AVFormatIDKey: kAudioFormatMPEG4AAC,
            AVSampleRateKey: InteonetYeongsang.RATE,
            AVNumberOfChannelsKey: 1,
            AVEncoderBitRateKey: 32000
        ])
        a.expectsMediaDataInRealTime = true
        guard w.canAdd(v), w.canAdd(a) else { return false }
        w.add(v); w.add(a)
        guard w.startWriting() else { return false }
        w.startSession(atSourceTime: t)
        writer = w; vIn = v; aIn = a; sijakTtae = t
        return true
    }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard dolgo else { return }
        let t = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
        if writer == nil {
            guard sseugaeYeolgi(t) else { kkeutnae("영상 만들기를 시작하지 못했습니다."); return }
        }
        guard let w = writer, w.status == .writing else {
            if writer?.status == .failed { kkeutnae("영상 만들기가 멈췄습니다.") }
            return
        }
        if let v = vIn, v.isReadyForMoreMediaData { v.append(sampleBuffer) }
    }

    private func soriButigi(_ mu: Data, _ badeunTtae: CMTime) {
        guard dolgo, let w = writer, w.status == .writing, let a = aIn, sijakTtae.isValid else { return }
        let n = mu.count
        let gil = CMTime(value: CMTimeValue(n), timescale: CMTimeScale(InteonetYeongsang.RATE))
        let mat = CMTimeSubtract(badeunTtae, gil)   // 이 덩이가 시작된 때(받은 때 - 길이)
        // 처음이거나 0.5초 넘게 어긋나면 받은 때에 다시 맞춤, 아니면 이어 붙임(소리가 끊기지 않게)
        if !aDaeum.isValid || abs(CMTimeGetSeconds(CMTimeSubtract(mat, aDaeum))) > 0.5 { aDaeum = mat }
        if CMTimeCompare(aDaeum, sijakTtae) < 0 { return }
        guard a.isReadyForMoreMediaData, let sb = soriSampleBuffer(mu, aDaeum) else { return }
        a.append(sb)
        aDaeum = CMTimeAdd(aDaeum, gil)
    }

    /// 뮤 8비트 → 16비트 소리 덩이
    private func soriSampleBuffer(_ mu: Data, _ t: CMTime) -> CMSampleBuffer? {
        if aFmt == nil {
            var asbd = AudioStreamBasicDescription(mSampleRate: Float64(InteonetYeongsang.RATE), mFormatID: kAudioFormatLinearPCM,
                                                   mFormatFlags: kLinearPCMFormatFlagIsSignedInteger | kLinearPCMFormatFlagIsPacked,
                                                   mBytesPerPacket: 2, mFramesPerPacket: 1, mBytesPerFrame: 2, mChannelsPerFrame: 1,
                                                   mBitsPerChannel: 16, mReserved: 0)
            var f: CMAudioFormatDescription?
            CMAudioFormatDescriptionCreate(allocator: kCFAllocatorDefault, asbd: &asbd, layoutSize: 0, layout: nil,
                                           magicCookieSize: 0, magicCookie: nil, extensions: nil, formatDescriptionOut: &f)
            aFmt = f
        }
        guard let fmt = aFmt else { return nil }
        let n = mu.count
        var pcm = [Int16](repeating: 0, count: n)
        mu.withUnsafeBytes { (p: UnsafeRawBufferPointer) in for i in 0..<n { pcm[i] = InteonetYeongsang.muPulgi(p[i]) } }
        var bb: CMBlockBuffer?
        let kugi = n * 2
        guard CMBlockBufferCreateWithMemoryBlock(allocator: kCFAllocatorDefault, memoryBlock: nil, blockLength: kugi,
                                                 blockAllocator: kCFAllocatorDefault, customBlockSource: nil, offsetToData: 0,
                                                 dataLength: kugi, flags: 0, blockBufferOut: &bb) == kCMBlockBufferNoErr, let b = bb else { return nil }
        let ok = pcm.withUnsafeBytes { p in CMBlockBufferReplaceDataBytes(with: p.baseAddress!, blockBuffer: b, offsetIntoDestination: 0, dataLength: kugi) }
        guard ok == kCMBlockBufferNoErr else { return nil }
        var sb: CMSampleBuffer?
        guard CMAudioSampleBufferCreateReadyWithPacketDescriptions(allocator: kCFAllocatorDefault, dataBuffer: b, formatDescription: fmt,
                                                                   sampleCount: n, presentationTimeStamp: t, packetDescriptions: nil,
                                                                   sampleBufferOut: &sb) == noErr else { return nil }
        return sb
    }

    /// G.711 뮤 한 바이트 → 16비트
    static func muPulgi(_ u: UInt8) -> Int16 {
        let x = ~u
        let sign = x & 0x80
        let exponent = Int32((x >> 4) & 0x07)
        let mantissa = Int32(x & 0x0F)
        var s = ((mantissa << 3) + 0x84) << exponent
        s -= 0x84
        return Int16(clamping: sign != 0 ? -s : s)
    }

    // MARK: 조각 mp4 받기

    func assetWriter(_ writer: AVAssetWriter, didOutputSegmentData segmentData: Data, segmentType: AVAssetSegmentType, segmentReport: AVAssetSegmentReport?) {
        jogakBatgi?(segmentData)
    }
}
