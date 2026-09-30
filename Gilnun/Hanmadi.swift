// 인터넷 없이 한마디 설명(카메라 눈 묶음 8) — 앱 2.23.0 (빌드 260930-10, 대표님 승인 "8번 이어 짓기")
// 카메라에 잡힌 것을 폰 안에서 대강 한마디로 알려 드립니다(보기: "컵, 책상인 듯합니다. 글자가 보입니다").
// 애플이 폰에 열어 둔 사진 갈래 알아보기(Vision)와 글자·사람 알아보기만 쓰며, 인터넷과 요금이 들지 않습니다.
// 자세한 설명은 둘러보기 탭의 사진 읽어 주기가 맡습니다. 사진은 담지도 보내지도 않습니다.
import SwiftUI
import AVFoundation
import Vision
import UIKit

final class Hanmadi: NSObject, ObservableObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    static let shared = Hanmadi()

    @Published private(set) var kyeojim = false
    @Published private(set) var sangtae = ""
    @Published private(set) var majimak = ""

    private let sesyeon = AVCaptureSession()
    private let jul = DispatchQueue(label: "gilnun.hanmadi")
    private var junbiDoem = false
    // 아래는 jul 에서만
    private var boT = Date.distantPast
    private var ingneun = false
    private var apMal = ""                      // 앞 장의 한마디(두 장 잇달아 같아야 알림)
    // 아래는 메인에서만
    private var malHan = ""
    private var malT = Date.distantPast
    private var saeT = Date()
    private var sigye: Timer?

    // MARK: 켜기·끄기

    func kyeogi() {
        guard !kyeojim else { return }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: sijak()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { ok in
                DispatchQueue.main.async {
                    if ok { self.sijak() } else { SoriEngine.shared.mal("카메라를 쓸 수 없어 한마디 설명을 켜지 못했습니다.") }
                }
            }
        default:
            SoriEngine.shared.mal("카메라 허락이 꺼져 있습니다. 아이폰 설정의 길눈에서 카메라를 켜 주십시오.")
        }
    }

    private func sijak() {
        // 카메라는 한 곳만
        AnmyeonEngine.shared.kkeugi(malHagi: false)
        QrEngine.shared.kkeugi(malHagi: false)
        MunChatgi.shared.kkeugi(malHagi: false)
        GeulIlgi.shared.kkeugi(malHagi: false)
        GarikiIlgi.shared.kkeugi(malHagi: false)
        SaramGamji.shared.kkeugi(malHagi: false)
        SangpumIlgi.shared.kkeugi(malHagi: false)
        JipyeSaek.shared.kkeugi(malHagi: false)
        BitAlgi.shared.kkeugi(malHagi: false)
        TeokAllim.shared.kkeugi(malHagi: false)  // 2.24.0
        jul.async {
            if !self.junbiDoem {
                guard self.junbi() else {
                    DispatchQueue.main.async { SoriEngine.shared.mal("카메라를 열지 못했습니다.") }
                    return
                }
                self.junbiDoem = true
            }
            self.apMal = ""
            if !self.sesyeon.isRunning { self.sesyeon.startRunning() }
            DispatchQueue.main.async {
                self.kyeojim = true
                self.malHan = ""
                self.saeT = Date()
                self.sangtae = "살피는 중입니다."
                UIApplication.shared.isIdleTimerDisabled = true
                self.mal("한마디 설명을 시작합니다. 폰 뒤쪽 카메라를 궁금한 쪽으로 향해 주십시오.", sseuGi: true)
                Girok.shared.namgi("hanmadi", ["kyeogi": true])
                self.sigye?.invalidate()
                self.sigye = Timer.scheduledTimer(withTimeInterval: 10, repeats: true) { [weak self] _ in self?.salpigi() }
            }
        }
    }

    func kkeugi(malHagi: Bool = true) {
        jul.async { if self.sesyeon.isRunning { self.sesyeon.stopRunning() } }
        sigye?.invalidate(); sigye = nil
        guard kyeojim else { return }
        kyeojim = false
        sangtae = ""
        UIApplication.shared.isIdleTimerDisabled = false
        if malHagi { SoriEngine.shared.mal("한마디 설명을 멈췄습니다.", .jeongbo) }
    }

    func dasiDeutgi() {
        if !majimak.isEmpty { SoriEngine.shared.mal(majimak) }
        else { SoriEngine.shared.mal(kyeojim ? "살피는 중입니다." : "한마디 설명이 꺼져 있습니다.") }
    }

    /// 5분 동안 새로 알린 것이 없으면 카메라를 끔(배터리)
    private func salpigi() {
        guard kyeojim, Date().timeIntervalSince(saeT) > 300 else { return }
        kkeugi(malHagi: false)
        mal("5분 동안 새로 알려 드릴 것이 없어 카메라를 껐습니다.", sseuGi: true)
    }

    private func junbi() -> Bool {
        sesyeon.beginConfiguration()
        defer { sesyeon.commitConfiguration() }
        sesyeon.sessionPreset = .high
        guard let k = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let ip = try? AVCaptureDeviceInput(device: k), sesyeon.canAddInput(ip) else { return false }
        sesyeon.addInput(ip)
        let op = AVCaptureVideoDataOutput()
        op.alwaysDiscardsLateVideoFrames = true
        guard sesyeon.canAddOutput(op) else { return false }
        sesyeon.addOutput(op)
        op.setSampleBufferDelegate(self, queue: jul)
        return true
    }

    // MARK: 한 장마다(jul) — 1초에 한 번

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        let now = Date()
        guard now.timeIntervalSince(boT) >= 1, !ingneun, let px = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        boT = now
        ingneun = true
        defer { ingneun = false }
        let m = Hanmadi.salpyeoBogi(px)
        // 두 장 잇달아 같은 한마디일 때만(흔들려 헛보인 것 줄이기)
        let hwakjeong = (m == apMal) ? m : ""
        apMal = m
        guard !hwakjeong.isEmpty else { return }
        DispatchQueue.main.async { self.allida(hwakjeong) }
    }

    /// 사진 갈래 셋까지 + 사람 수 + 글자 여부를 한마디로
    static func salpyeoBogi(_ px: CVPixelBuffer) -> String {
        let gal = VNClassifyImageRequest()
        let saram = VNDetectHumanRectanglesRequest()
        let geul = VNDetectTextRectanglesRequest()
        let h = VNImageRequestHandler(cvPixelBuffer: px, orientation: .right, options: [:])
        try? h.perform([gal, saram, geul])
        var ireum: [String] = []
        for o in (gal.results ?? []).sorted(by: { $0.confidence > $1.confidence }) where o.confidence >= 0.35 {
            guard let k = Hanmadi.urimal[o.identifier], !ireum.contains(k) else { continue }
            ireum.append(k)
            if ireum.count >= 3 { break }
        }
        var jogak: [String] = []
        let su = (saram.results ?? []).filter { $0.confidence > 0.5 }.count
        if su == 1 { jogak.append("사람 한 명이 보입니다") }
        else if su > 1 { jogak.append("사람 \(su)명이 보입니다") }
        if !ireum.isEmpty {
            // 사람을 따로 셌으면 갈래의 사람은 뺌
            let nam = su > 0 ? ireum.filter { $0 != "사람" && $0 != "어린이" } : ireum
            if !nam.isEmpty { jogak.append(nam.joined(separator: ", ") + "인 듯합니다") }
        }
        if (geul.results ?? []).count >= 2 { jogak.append("글자가 보입니다") }
        return jogak.joined(separator: ". ")
    }

    // MARK: 알리기(메인)

    private func allida(_ m: String) {
        guard kyeojim else { return }
        let now = Date()
        // 바뀌었을 때만, 4초에 한 번까지
        guard m != malHan, now.timeIntervalSince(malT) >= 4 else { return }
        malT = now; saeT = now
        malHan = m
        majimak = m + "."
        sangtae = majimak
        SoriEngine.shared.mal(majimak, .annae)   // 기다리는 대답이므로 말소리를 꺼도 알림
    }

    private func mal(_ t: String, _ g: MalGeup = .annae, sseuGi: Bool = false) {
        if !Seoljeong.shared.kameraMal && !sseuGi { return }
        SoriEngine.shared.mal(t, g)
    }

    /// 애플 사진 갈래 이름을 우리말로 — 여기에 없는 갈래는 말하지 않음(뜻이 넓거나 헷갈리는 것은 일부러 뺌)
    static let urimal: [String: String] = [
        // 사람·동물
        "people": "사람", "adult": "사람", "child": "어린이", "baby": "아기", "crowd": "사람들",
        "dog": "개", "cat": "고양이", "bird": "새", "fish": "물고기", "horse": "말", "cow": "소",
        "chicken_animal": "닭", "insect": "벌레", "butterfly": "나비", "pigeon": "비둘기", "duck": "오리",
        // 먹을 것
        "food": "먹을 것", "fruit": "과일", "apple": "사과", "banana": "바나나", "orange": "귤", "grape": "포도",
        "strawberry": "딸기", "watermelon": "수박", "vegetable": "채소", "bread": "빵", "cake": "케이크",
        "cookie": "과자", "dessert": "후식", "rice": "밥", "noodles": "국수", "soup": "국", "pizza": "피자",
        "hamburger": "햄버거", "sandwich": "샌드위치", "sushi": "초밥", "egg": "달걀", "meat": "고기",
        "salad": "샐러드", "coffee": "커피", "tea_drink": "차", "juice": "주스", "beer": "맥주", "wine": "포도주",
        "drink": "마실 것", "ice_cream": "아이스크림", "chocolate": "초콜릿", "candy": "사탕", "kimchi": "김치",
        // 그릇·부엌
        "cup": "컵", "mug": "머그잔", "bottle": "병", "plate": "접시", "bowl": "그릇", "utensil": "수저",
        "spoon": "숟가락", "fork": "포크", "knife": "칼", "chopsticks": "젓가락", "kettle": "주전자",
        "refrigerator": "냉장고", "microwave": "전자레인지", "oven": "오븐", "stove": "가스레인지", "sink": "개수대",
        "kitchen_room": "부엌", "kitchen": "부엌",
        // 집·가구
        "chair": "의자", "table": "탁자", "desk": "책상", "sofa": "소파", "couch": "소파", "bed": "침대",
        "pillow": "베개", "blanket": "이불", "shelf": "선반", "bookshelf": "책장", "cabinet": "장",
        "door": "문", "window": "창문", "stairs": "계단", "curtain": "커튼", "lamp": "전등", "chandelier": "샹들리에",
        "mirror": "거울", "clock": "시계", "toilet": "변기", "bathroom_room": "화장실", "bathroom": "화장실",
        "bathtub": "욕조", "shower": "샤워기", "towel": "수건", "carpet": "깔개", "fan": "선풍기",
        "bedroom": "침실", "living_room": "거실", "office": "사무실", "classroom": "교실", "restaurant": "식당",
        "store": "가게", "supermarket": "마트",
        // 물건
        "book": "책", "document": "서류", "paper": "종이", "newspaper": "신문", "magazine": "잡지",
        "pen": "펜", "pencil": "연필", "scissors": "가위", "bag": "가방", "handbag": "손가방", "backpack": "배낭",
        "wallet": "지갑", "money": "돈", "coin": "동전", "key": "열쇠", "umbrella": "우산", "glasses": "안경",
        "sunglasses": "선글라스", "hat": "모자", "shoes": "신발", "sneaker": "운동화", "clothing": "옷",
        "shirt": "셔츠", "jacket": "외투", "coat": "외투", "pants": "바지", "jeans": "청바지", "dress": "원피스",
        "sock": "양말", "glove": "장갑", "watch": "손목시계", "jewelry": "장신구", "necklace": "목걸이",
        "toy": "장난감", "ball": "공", "doll": "인형", "teddy_bear": "곰 인형", "box": "상자", "basket": "바구니",
        "flower_pot": "화분", "vase": "꽃병", "candle": "양초", "medicine": "약", "pill": "알약",
        "cane": "지팡이", "wheelchair": "휠체어", "trash_can": "쓰레기통",
        // 전자 기기
        "computer": "컴퓨터", "laptop": "노트북", "computer_keyboard": "자판", "keyboard": "자판",
        "computer_mouse": "마우스", "monitor": "모니터", "television": "텔레비전", "phone": "전화기",
        "cellphone": "휴대전화", "smartphone": "휴대전화", "tablet": "태블릿", "headphones": "헤드폰",
        "speaker": "스피커", "camera": "카메라", "remote_control": "리모컨", "printer": "프린터",
        // 바깥
        "sky": "하늘", "cloud": "구름", "sun": "해", "moon": "달", "tree": "나무", "grass": "풀밭", "flower": "꽃",
        "plant": "식물", "leaf": "나뭇잎", "forest": "숲", "mountain": "산", "hill": "언덕", "rock": "바위",
        "river": "강", "lake": "호수", "sea": "바다", "ocean": "바다", "beach": "바닷가", "sand": "모래",
        "snow": "눈", "rain": "비", "water": "물", "fountain": "분수", "park": "공원", "garden": "정원",
        "road": "길", "street": "거리", "sidewalk": "보도", "crosswalk": "횡단보도", "bridge": "다리",
        "building": "건물", "house": "집", "apartment": "아파트", "skyscraper": "높은 건물", "tower": "탑",
        "temple": "절", "church": "교회", "palace": "궁궐", "castle": "성", "fence": "울타리", "wall": "벽",
        "signboard": "간판", "sign": "표지판", "traffic_light": "신호등", "streetlight": "가로등",
        "bench": "벤치", "parking_lot": "주차장", "station": "역", "airport": "공항", "stadium": "경기장",
        // 탈것
        "car": "자동차", "bus": "버스", "truck": "트럭", "taxi": "택시", "motorcycle": "오토바이",
        "bicycle": "자전거", "train": "기차", "subway": "지하철", "airplane": "비행기", "boat": "배", "ship": "배",
        "scooter": "킥보드",
        // 그 밖
        "painting": "그림", "drawing": "그림", "sculpture": "조각", "statue": "동상", "poster": "포스터",
        "musical_instrument": "악기", "piano": "피아노", "guitar": "기타", "drum": "북",
        "fire": "불", "smoke": "연기"
    ]
}

// MARK: 화면 — 둘러보기 탭

struct HanmadiView: View {
    @ObservedObject private var g = Hanmadi.shared
    @ObservedObject private var s = Seoljeong.shared
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Button(g.kyeojim ? "멈추기 — 앞을 살피는 중" : "이어 살피기 — 카메라를 켜고 한마디 설명") {
                    if g.kyeojim { g.kkeugi() } else { g.kyeogi() }
                }
                .buttonStyle(KeunDanchu())
                .accessibilityFocused($chojeom)
                Button("방금 한마디 다시 듣기") { g.dasiDeutgi() }
                    .buttonStyle(KeunDanchu())
                Toggle(isOn: $s.kameraMal) { Text("카메라 눈 말소리").font(.title3.weight(.semibold)) }
                    .padding(.horizontal, 4)
                    .frame(minHeight: 60)
                DisclosureGroup("알아 두실 것 펼치기") {
                    Text("화면을 여시면 바로 시작합니다. 폰 뒤쪽 카메라를 궁금한 쪽으로 향하시면, 보이는 것을 컵, 책상인 듯합니다처럼 대강 한마디로 알려 드립니다. 사람이 있으면 몇 명인지, 글자가 있으면 글자가 보입니다라고 덧붙입니다. 보이는 것이 바뀔 때만, 4초에 한 번까지 말씀드립니다. 인터넷 없이 폰 안에서만 짐작하는 것이라 틀릴 수 있으니 참고로만 들으십시오. 자세한 설명이 필요하시면 둘러보기 탭의 사진 읽어 주기를, 글자를 읽으시려면 즉석 글자 읽기를 쓰십시오. 방금 한마디 다시 듣기를 누르시면 마지막 한마디를 다시 들려 드립니다. 5분 동안 새로 알릴 것이 없거나 이 화면을 떠나시면 카메라를 끕니다. 말로 하기에서 이게 뭐야, 뭐가 보여라고 하셔도 열립니다. 사진은 담지도 보내지도 않습니다.")
                        .font(.body)
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("한마디 설명")
        .onAppear {
            g.kyeogi()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { chojeom = true }
        }
        .onDisappear { g.kkeugi(malHagi: false) }
    }
}
