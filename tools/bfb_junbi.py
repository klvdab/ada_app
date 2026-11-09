# LVD 방송 앱스토어 제출 준비 1.0 (빌드 261109-BJ1, 대클 — 이사장님 승인 2026-11-09 「1」)
# 앱스토어 커넥트 열쇠로 애플에 직접 말해, 사람 손(로그인) 없이 「제출 준비 중」 판의 빈 칸을 채웁니다.
#   boda    : 아무것도 바꾸지 않고 채워진 것·빠진 것만 알림(기본)
#   chaeugi : 이사장님이 승인하신 글과 설정을 넣음(개인정보 주소·부제·설명·키워드·지원 주소·저작권·
#             콘텐츠 권리·무료·나이 등급·출시 수동·심사 연락과 메모). 붙일 빌드(BILD)를 주면 그 빌드도 붙임
#   sajin   : SAJIN_GLOB 사진들을 아이폰 6.9인치 묶음에 올림(옛 사진은 바꿈)
# 심사에 내는 일은 하지 않습니다. 앱 개인정보 표시는 애플이 열쇠로 넣는 길을 열어 두지 않아 웹에서 넣습니다.
import os, sys, time, json, glob, hashlib, urllib.request, urllib.parse, urllib.error
import jwt

kid = os.environ["ASC_KEY_ID"]
iss = os.environ["ASC_ISSUER_ID"]
key = open(os.path.expanduser("~/private_keys/AuthKey_%s.p8" % kid)).read()
API = "https://api.appstoreconnect.apple.com/v1/"
HAL = os.environ.get("HAL", "boda").strip() or "boda"
BUNDLE = os.environ.get("BUNDLE", "kr.or.ada.bfb").strip() or "kr.or.ada.bfb"
BILD = os.environ.get("BILD", "").strip()
SAJIN_GLOB = os.environ.get("SAJIN_GLOB", "").strip()

# ---- 이사장님이 승인하신 글(2026-11-09 「1」) ----
GAEIN = "https://lvd.ada.or.kr/bfblive/privacy.html"
JIWON = "https://lvd.ada.or.kr/saehome/"
BUJE = "시각장애인을 위한 현장영상해설 듣기"
KIWEODEU = "시각장애인,현장영상해설,LVD,화면해설,음성해설,접근성,행사,공연,축제,경기,해설방송,배리어프리,보이스오버"
JEOJAKGWON = "2026 사단법인 한국시각장애인현장영상해설협회"
HONGBO = "공연, 축제, 경기장에서 현장영상해설사의 해설을 내 폰으로 바로 듣습니다."
SEOLMYEONG = """LVD 방송은 공연, 축제, 경기, 행사장에서 현장영상해설사가 들려주는 현장영상해설을 내 폰으로 바로 듣는 앱입니다. 행사장에 들어서면 저절로 들리고, 인터넷 방송과 다시 듣기도 됩니다. 화면낭독기(보이스오버)로 모든 단추를 쓸 수 있습니다.

현장영상해설은 현장영상해설사가 지금 그 자리에서 보는 무대, 경기, 풍경, 사람의 움직임을 시각장애인에게 말로 전해 드리는 일입니다. 사단법인 한국시각장애인현장영상해설협회가 만들고 운영합니다.

주요 기능
- 지금 현장영상해설 듣기: 단추 하나로 듣습니다. 행사장 와이파이에 붙어 있으면 행사장 방송(BYOD 방송)을 데이터 없이 바로 듣고, 행사장이 아니면 지금 나가는 인터넷 방송을 앱 안에서 듣습니다.
- 행사장에 들어서면 저절로 듣기: 처음 한 번만 허락하시면 행사장에서 앱을 열기만 해도 들립니다.
- 화면을 잠가도 이어 듣기: 다른 앱을 쓰거나 화면을 잠가도 해설이 이어집니다. 전화가 오면 멈추고 통화가 끝나면 다시 들립니다.
- 해설 고르기와 소리 크기: 해설 갈래가 여럿이면 골라 듣고, 해설 소리와 대기 음악 크기를 따로 맞춥니다.
- 현장영상해설사 도움 요청: 행사장에서 「도와주세요」, 「소리가 끊겨요」, 「궁금한 것이 있어요」를 누르면 현장영상해설사가 찾아갑니다.
- 다시 듣기: 지난 방송을 차례별로 골라 듣고, 소리와 받아쓰기 글을 내려받습니다.
- TV해설방송 듣기: 집의 TV를 켜 두고 현장영상해설사의 해설을 함께 듣습니다.
- 입장 번호와 행운권 추첨 알림: 행사장에서 듣는 동안 내 번호가 뽑히면 진동과 말로 알려 드립니다. 추첨은 행사를 여는 곳과 협회가 진행하며, 애플(Apple)은 추첨의 후원자가 아니고 관계가 없습니다.

이름이나 전화번호 없이 쓰실 수 있습니다. 문의: 02-363-4455, ada015@naver.com"""
SAE_GEUL = "앱스토어 첫 판입니다."
SIMSA_MEMO = """LVD 방송 (LVD Broadcast) is a free listening app for blind and low-vision people, made by the Korea Live Visual Description Association for the Blind, a non-profit. Live describers narrate what is happening at concerts, festivals, sports games and events, and listeners hear it on their own phones.

How to review:
1. Open the app and tap 「지금 현장영상해설 듣기」 (Listen now). Outside an event venue the app looks for a live internet broadcast from the association. If none is on air, it says so and waits; this is normal.
2. 「다시 듣기」 (Replay) always has past broadcasts you can play, including downloads of audio and transcripts.
3. 「도움말」 (Help, under 「더 보기」) explains every feature.
4. At a venue the app joins the local Wi-Fi of the association's portable router and plays the on-site broadcast without mobile data (Local Network permission). This needs our router on site, so it cannot be tested remotely.
5. 「점검 듣기(직원용)」 is a staff toggle to hear the describer's test audio before the broadcast starts. It is not a hidden feature.

No account or login is needed. The app does not collect names, phone numbers or location. Usage logs use a random 8-character device code only.
Sweepstakes: venue lucky draws are run by the event host and the association, free to enter; Apple is not a sponsor and is not involved (shown in the app).
TV broadcaster channels were removed from the iOS version. Only the association's own audio and official YouTube embeds are played.

앱 설명(한국어): 시각장애인을 위한 현장영상해설 듣기 앱입니다. 로그인이 없으며, 행사장 방송은 협회 공유기가 있어야 하므로 다시 듣기로 들어 보실 수 있습니다."""
YEONRAK = {"contactFirstName": "광재", "contactLastName": "박", "contactPhone": "+82 10-4447-8275", "contactEmail": "pkj1135@gmail.com"}


def tok():
    now = int(time.time())
    return jwt.encode({"iss": iss, "iat": now, "exp": now + 600, "aud": "appstoreconnect-v1"},
                      key, algorithm="ES256", headers={"kid": kid, "typ": "JWT"})


def allim(jul):
    print(jul)
    print("::notice::" + jul.replace("\n", " "))


def mal(bang, gil, q=None, mom=None, gwaen=False):
    u = API + gil + (("?" + urllib.parse.urlencode(q)) if q else "")
    r = urllib.request.Request(u, data=json.dumps(mom).encode() if mom is not None else None, method=bang,
                               headers={"Authorization": "Bearer " + tok(), "Content-Type": "application/json"})
    try:
        b = urllib.request.urlopen(r, timeout=120).read()
        return json.loads(b) if b else {}
    except urllib.error.HTTPError as e:
        t = e.read().decode()[:1200]
        if gwaen:
            print("애플 대답", e.code, bang, gil, t[:300])
            return {"_oryu": e.code, "_mal": t}
        allim("애플이 거절함 %s %s %s %s" % (e.code, bang, gil, t))
        raise SystemExit(1)


ap = mal("GET", "apps", {"filter[bundleId]": BUNDLE}).get("data", [])
if not ap:
    allim("번들 아이디 %s 앱을 찾지 못함" % BUNDLE)
    sys.exit(1)
ap = ap[0]
APID = ap["id"]
vs = mal("GET", "apps/%s/appStoreVersions" % APID, {"filter[appStoreState]": "PREPARE_FOR_SUBMISSION,DEVELOPER_REJECTED,REJECTED,METADATA_REJECTED"}).get("data", [])
if not vs:
    allim("제출 준비 중인 판이 없음")
    sys.exit(1)
v = vs[0]
VID = v["id"]
allim("앱 %s / 판 %s / 상태 %s / 출시 방식 %s" % (ap["attributes"]["name"], v["attributes"]["versionString"], v["attributes"].get("appStoreState"), v["attributes"].get("releaseType")))

infos = mal("GET", "apps/%s/appInfos" % APID).get("data", [])
info = next((i for i in infos if i["attributes"].get("appStoreState") in ("PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "WAITING_FOR_REVIEW") or i["attributes"].get("state") in ("PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED")), infos[0] if infos else None)


def sajin_olligi():
    fs = sorted(glob.glob(SAJIN_GLOB))
    if not fs:
        allim("올릴 사진이 없음: %s" % SAJIN_GLOB)
        sys.exit(1)
    for lo in mal("GET", "appStoreVersions/%s/appStoreVersionLocalizations" % VID)["data"]:
        sets = mal("GET", "appStoreVersionLocalizations/%s/appScreenshotSets" % lo["id"])["data"]
        st = next((s for s in sets if s["attributes"]["screenshotDisplayType"] == "APP_IPHONE_67"), None)
        if not st:
            st = mal("POST", "appScreenshotSets", mom={"data": {"type": "appScreenshotSets", "attributes": {"screenshotDisplayType": "APP_IPHONE_67"},
                     "relationships": {"appStoreVersionLocalization": {"data": {"type": "appStoreVersionLocalizations", "id": lo["id"]}}}}})["data"]
        for y in mal("GET", "appScreenshotSets/%s/appScreenshots" % st["id"])["data"]:
            mal("DELETE", "appScreenshots/%s" % y["id"])
        for f in fs:
            b = open(f, "rb").read()
            r = mal("POST", "appScreenshots", mom={"data": {"type": "appScreenshots", "attributes": {"fileName": os.path.basename(f), "fileSize": len(b)},
                    "relationships": {"appScreenshotSet": {"data": {"type": "appScreenshotSets", "id": st["id"]}}}}})["data"]
            for op in r["attributes"]["uploadOperations"]:
                hd = {x["name"]: x["value"] for x in (op.get("requestHeaders") or [])}
                urllib.request.urlopen(urllib.request.Request(op["url"], data=b[op["offset"]:op["offset"] + op["length"]], method=op["method"], headers=hd), timeout=120).read()
            mal("PATCH", "appScreenshots/%s" % r["id"], mom={"data": {"type": "appScreenshots", "id": r["id"], "attributes": {"uploaded": True, "sourceFileChecksum": hashlib.md5(b).hexdigest()}}})
        allim("%s 아이폰 6.9인치 묶음에 사진 %d장 올림" % (lo["attributes"]["locale"], len(fs)))
    for _ in range(30):
        mot = 0
        for lo in mal("GET", "appStoreVersions/%s/appStoreVersionLocalizations" % VID)["data"]:
            for s in mal("GET", "appStoreVersionLocalizations/%s/appScreenshotSets" % lo["id"])["data"]:
                for y in mal("GET", "appScreenshotSets/%s/appScreenshots" % s["id"])["data"]:
                    if ((y["attributes"].get("assetDeliveryState") or {}).get("state")) not in ("COMPLETE", None):
                        mot += 1
        if mot == 0:
            allim("애플 쪽 사진 처리 끝")
            return
        time.sleep(10)
    allim("사진 처리가 5분 안에 끝나지 않음")


def chaeugi():
    # 앱 정보(개인정보 주소·부제)
    if info:
        for lo in mal("GET", "appInfos/%s/appInfoLocalizations" % info["id"])["data"]:
            if lo["attributes"].get("locale", "").startswith("ko"):
                mal("PATCH", "appInfoLocalizations/%s" % lo["id"], mom={"data": {"type": "appInfoLocalizations", "id": lo["id"],
                    "attributes": {"privacyPolicyUrl": GAEIN, "subtitle": BUJE}}})
                allim("앱 정보 %s: 개인정보 주소·부제 넣음" % lo["attributes"]["locale"])
        if not (mal("GET", "appInfos/%s/primaryCategory" % info["id"], gwaen=True).get("data")):
            r = mal("PATCH", "appInfos/%s" % info["id"], mom={"data": {"type": "appInfos", "id": info["id"],
                    "relationships": {"primaryCategory": {"data": {"type": "appCategories", "id": "ENTERTAINMENT"}}}}}, gwaen=True)
            allim("주 카테고리: " + ("엔터테인먼트로 넣음" if "_oryu" not in r else "넣지 못함"))
        # 나이 등급 — LVD 방송에 맞게(행운권 추첨이 있어 contests 만 「가끔·가벼움」, 나머지는 없음)
        ar = mal("GET", "appInfos/%s/ageRatingDeclaration" % info["id"], gwaen=True).get("data")
        if ar:
            NAI = {"advertising": False, "gambling": False, "lootBox": False, "messagingAndChat": False,
                   "parentalControls": False, "ageAssurance": False, "healthOrWellnessTopics": False,
                   "userGeneratedContent": False, "unrestrictedWebAccess": False,
                   "contests": "INFREQUENT_OR_MILD", "gamblingSimulated": "NONE",
                   "alcoholTobaccoOrDrugUseOrReferences": "NONE", "gunsOrOtherWeapons": "NONE",
                   "medicalOrTreatmentInformation": "NONE", "profanityOrCrudeHumor": "NONE",
                   "sexualContentGraphicAndNudity": "NONE", "sexualContentOrNudity": "NONE",
                   "horrorOrFearThemes": "NONE", "matureOrSuggestiveThemes": "NONE",
                   "violenceCartoonOrFantasy": "NONE", "violenceRealisticProlongedGraphicOrSadistic": "NONE",
                   "violenceRealistic": "NONE", "socialMedia": False}
            import re as _re
            sae = dict(NAI)
            for _ in range(12):
                r1 = mal("PATCH", "ageRatingDeclarations/%s" % ar["id"], mom={"data": {"type": "ageRatingDeclarations", "id": ar["id"], "attributes": sae}}, gwaen=True)
                if "_oryu" not in r1:
                    allim("나이 등급: 한 번에 넣음")
                    break
                m = r1.get("_mal", "")
                k = (_re.search(r"attribute '([A-Za-z]+)'", m) or [None, None])[1]
                if not k:
                    allim("나이 등급 넣기 실패: " + m[:300])
                    break
                if "Expected a BOOLEAN" in m:
                    sae[k] = False
                elif "Expected" in m and k in sae:
                    sae[k] = "NONE"
                elif "required" in m.lower() or "REQUIRED" in m:
                    sae[k] = False if k.startswith(("is", "has")) else "NONE"
                elif k in sae:
                    print("나이 등급", k, "을 빼고 다시")
                    sae.pop(k)
                else:
                    allim("나이 등급 넣기 실패: " + m[:300])
                    break
            nam = [k for k, x in (mal("GET", "appInfos/%s/ageRatingDeclaration" % info["id"])["data"]["attributes"]).items() if x is None]
            allim("나이 등급: 넣은 뒤 빈 물음 %d개 %s" % (len(nam), ", ".join(nam)))
    # 콘텐츠 권리(협회 자체 해설과 유튜브 공식 화면을 씀 — 길눈과 같이 「권리 있음」)
    if ap["attributes"].get("contentRightsDeclaration") != "USES_THIRD_PARTY_CONTENT":
        mal("PATCH", "apps/%s" % APID, mom={"data": {"type": "apps", "id": APID, "attributes": {"contentRightsDeclaration": "USES_THIRD_PARTY_CONTENT"}}})
        allim("콘텐츠 권리 신고: 권리 있음으로 넣음")
    # 무료
    pp = mal("GET", "apps/%s/appPricePoints" % APID, {"filter[territory]": "USA", "limit": "200"})["data"]
    free = [x for x in pp if str(x["attributes"].get("customerPrice")) in ("0", "0.0", "0.00")]
    if free:
        mal("POST", "appPriceSchedules", mom={"data": {"type": "appPriceSchedules",
            "relationships": {"app": {"data": {"type": "apps", "id": APID}},
                              "baseTerritory": {"data": {"type": "territories", "id": "USA"}},
                              "manualPrices": {"data": [{"type": "appPrices", "id": "${p0}"}]}}},
            "included": [{"type": "appPrices", "id": "${p0}", "attributes": {"startDate": None},
                          "relationships": {"appPricePoint": {"data": {"type": "appPricePoints", "id": free[0]["id"]}}}}]})
        allim("가격: 무료로 넣음")
    # 판: 출시 수동, 저작권
    mal("PATCH", "appStoreVersions/%s" % VID, mom={"data": {"type": "appStoreVersions", "id": VID,
        "attributes": {"releaseType": "MANUAL", "copyright": JEOJAKGWON}}})
    allim("출시 방식: 수동(통과해도 이사장님이 정하신 날 공개), 저작권 넣음")
    # 판 설명(한국어)
    for lo in mal("GET", "appStoreVersions/%s/appStoreVersionLocalizations" % VID)["data"]:
        if lo["attributes"].get("locale", "").startswith("ko"):
            at = {"description": SEOLMYEONG, "keywords": KIWEODEU, "supportUrl": JIWON, "promotionalText": HONGBO}
            mal("PATCH", "appStoreVersionLocalizations/%s" % lo["id"], mom={"data": {"type": "appStoreVersionLocalizations", "id": lo["id"], "attributes": at}})
            allim("판 설명 %s: 설명·키워드·지원 주소·홍보 글 넣음" % lo["attributes"]["locale"])
    # 심사 연락과 메모
    rd = mal("GET", "appStoreVersions/%s/appStoreReviewDetail" % VID, gwaen=True).get("data")
    at = dict(YEONRAK, notes=SIMSA_MEMO, demoAccountRequired=False)
    if rd:
        mal("PATCH", "appStoreReviewDetails/%s" % rd["id"], mom={"data": {"type": "appStoreReviewDetails", "id": rd["id"], "attributes": at}})
    else:
        mal("POST", "appStoreReviewDetails", mom={"data": {"type": "appStoreReviewDetails", "attributes": at,
            "relationships": {"appStoreVersion": {"data": {"type": "appStoreVersions", "id": VID}}}}})
    allim("심사 연락처와 심사 메모 넣음(로그인 없음)")
    # 빌드 붙이기
    if BILD:
        bs = mal("GET", "builds", {"filter[app]": APID, "filter[version]": BILD, "limit": "5"}).get("data", [])
        if not bs:
            allim("빌드 %s 를 찾지 못함" % BILD)
        else:
            b = bs[0]
            if b["attributes"].get("usesNonExemptEncryption") is None:
                mal("PATCH", "builds/%s" % b["id"], mom={"data": {"type": "builds", "id": b["id"], "attributes": {"usesNonExemptEncryption": False}}}, gwaen=True)
            mal("PATCH", "appStoreVersions/%s/relationships/build" % VID, mom={"data": {"type": "builds", "id": b["id"]}})
            allim("빌드 %s 붙임(처리 상태 %s)" % (BILD, b["attributes"].get("processingState")))


def boda():
    ppa = mal("GET", "apps/%s" % APID)["data"]["attributes"]
    allim("콘텐츠 권리: %s" % ppa.get("contentRightsDeclaration"))
    if info:
        for lo in mal("GET", "appInfos/%s/appInfoLocalizations" % info["id"])["data"]:
            a = lo["attributes"]
            allim("앱 정보 %s / 이름 %s / 부제 %s / 개인정보 주소 %s" % (a.get("locale"), a.get("name"), a.get("subtitle"), a.get("privacyPolicyUrl")))
        c = mal("GET", "appInfos/%s/primaryCategory" % info["id"], gwaen=True).get("data")
        allim("주 카테고리: %s" % ((c or {}).get("id")))
        ar = mal("GET", "appInfos/%s/ageRatingDeclaration" % info["id"], gwaen=True).get("data")
        if ar:
            bin_ = [k for k, x in (ar.get("attributes") or {}).items() if x is None]
            allim("나이 등급 빈 물음 %d개: %s" % (len(bin_), ", ".join(bin_)))
    va = mal("GET", "appStoreVersions/%s" % VID)["data"]["attributes"]
    allim("저작권: %s" % va.get("copyright"))
    for lo in mal("GET", "appStoreVersions/%s/appStoreVersionLocalizations" % VID)["data"]:
        a = lo["attributes"]
        allim("판 설명 %s / 설명 %d자 / 키워드 %s / 지원 주소 %s" % (a.get("locale"), len(a.get("description") or ""), a.get("keywords"), a.get("supportUrl")))
        n = 0
        for s in mal("GET", "appStoreVersionLocalizations/%s/appScreenshotSets" % lo["id"])["data"]:
            k = len(mal("GET", "appScreenshotSets/%s/appScreenshots" % s["id"])["data"])
            n += k
            allim("  사진 묶음 %s: %d장" % (s["attributes"]["screenshotDisplayType"], k))
        allim("  사진 모두 %d장" % n)
    b = mal("GET", "appStoreVersions/%s/build" % VID, gwaen=True).get("data")
    allim("붙은 빌드: %s" % ((b or {}).get("attributes", {}).get("version", "없음")))
    rd = mal("GET", "appStoreVersions/%s/appStoreReviewDetail" % VID, gwaen=True).get("data")
    if rd:
        a = rd["attributes"]
        allim("심사 연락: %s %s / 전화 %s / 메모 %d자" % (a.get("contactLastName"), a.get("contactFirstName"), "있음" if a.get("contactPhone") else "없음", len(a.get("notes") or "")))
    else:
        allim("심사 연락: 없음")
    bs = mal("GET", "builds", {"filter[app]": APID, "sort": "-uploadedDate", "limit": "3"}).get("data", [])
    for x in bs:
        allim("최근 빌드 %s / %s / 처리 %s" % (x["attributes"].get("version"), x["attributes"].get("uploadedDate"), x["attributes"].get("processingState")))


if HAL == "chaeugi":
    chaeugi()
    boda()
elif HAL == "sajin":
    sajin_olligi()
else:
    boda()
