// 2.59.0 (261009-I13, 이사장님 지시·승인 2026-10-09) 전화가 오거나 걸거나 통화하는 동안 길눈의 모든 소리를 멈춤
// 이사장님 말씀: "tv를 보든 라디오를 듣든 어떤 일을 하다가도 전화 통화가 연결되면 그 하던 건 멈추는 게 맞아" —
//   JTV를 틀어 둔 채 전화를 걸어 통화하는데 방송 소리가 계속 겹쳐 큰 문제가 생길 뻔하심.
//   까닭: 전화가 방송을 잠시 끊어도, 방송 지킴이(4초마다)가 16초 넘게 멈춘 방송을 "끊김"으로 알고 다시 이어 틀었음.
// 안드로이드 JeonhwaGamsi.kt 와 같은 뜻:
//   ① 벨이 울릴 때, 전화를 걸 때(누르는 순간부터), 통화하는 동안, 다른 앱의 인터넷 통화(카카오톡 보이스톡 등, 콜킷을 쓰는 것) 동안 → 전화 중
//   ② 전화 중에는 방송·음악·기사 읽기 멈춤(지킴이도 다시 틀지 않음), 길눈 안내 말소리·알림 소리·목소리 토막 멈춤, 하이 길눈 듣기 멈춤
//      다만 걷는 중 위험 경고는 말 대신 길게 진동(긴급통화 때와 같은 길)
//   ③ 통화가 끝나면 방송은 저절로 다시 틀지 않고 길 찾기 첫 화면과 음악·방송 첫 화면 맨 위에 「방송 이어 듣기」 단추 하나
//      (음악·방송 첫 화면의 「통화 뒤 방송 저절로 이어 듣기」를 켜시면 저절로 이어짐)
// 콜킷 통화 살피기(CXCallObserver)는 따로 허락이 필요 없음
import AVFoundation
import CallKit
import Combine
import Foundation

final class JeonhwaGamsi: NSObject, ObservableObject, CXCallObserverDelegate {
    static let shared = JeonhwaGamsi()

    /// 지금 전화 중(벨·걸기·통화)
    @Published private(set) var jeonhwaJung = false

    private let gwanchal = CXCallObserver()
    private var dolgo = false

    func sijak() {
        guard !dolgo else { return }
        dolgo = true
        gwanchal.setDelegate(self, queue: DispatchQueue.main)
        salpigi()
    }

    func callObserver(_ callObserver: CXCallObserver, callChanged call: CXCall) { salpigi() }

    private func salpigi() {
        // 끝나지 않은 통화가 하나라도 있으면(벨이 울리는 중, 거는 중, 통화 중, 대기 중 모두) 전화 중
        let j = gwanchal.calls.contains { !$0.hasEnded }
        guard j != jeonhwaJung else { return }
        jeonhwaJung = j
        Girok.shared.namgi("jeonhwa", ["on": j])
        SoriEngine.shared.jeonhwaJung = j
        if j {
            SoriEngine.shared.jeonhwaMeomchum()   // 하던 말을 곧바로 끊음(경고 진동은 그대로)
            TomakDeutgi.shared.meomchugi()        // 목소리 토막
        }
        BangsongEngine.shared.jeonhwa(j)
    }
}
