# 앱스토어 내기 (1.1판, 빌드 261028-1 — 공개 방식 MANUAL(10-20 결정: 통과해도 공개 날은 이사장님이), 거절된 판·해결 안 된 심사 묶음을 이어 씀. 1.0판 빌드 261025-1, 대클 — 이사장님 지시 2026-10-25 「아이폰 버전 앱스토어 올리는거 확실히 챙겨야한다」)
# 앱스토어 커넥트 열쇠로 애플에 직접 말해, 사람 손(로그인) 없이 아래 일을 합니다.
#   gonggae : 심사를 통과하고 「우리가 공개하기를 기다림」에 있는 정식 판을 앱스토어에 공개
#   saepan  : 새 정식 판(판 번호·빌드 번호)을 만들어 빌드를 붙이고, 새 기능 글을 넣어 심사에 냄
#   boda    : 아무것도 바꾸지 않고 지금 상태와 할 일만 알림(기본)
# 한 앱씩 합니다. 앱은 번들 아이디로 고릅니다(kr.or.ada.app = 길눈, kr.or.ada.jabong = 자봉).
import os, sys, time, json, urllib.request, urllib.parse, urllib.error
import jwt

kid = os.environ["ASC_KEY_ID"]
iss = os.environ["ASC_ISSUER_ID"]
key = open(os.path.expanduser("~/private_keys/AuthKey_%s.p8" % kid)).read()
API = "https://api.appstoreconnect.apple.com/v1/"
HAL = os.environ.get("HAL", "boda").strip()
BUNDLE = os.environ.get("BUNDLE", "").strip()
PAN = os.environ.get("PAN", "").strip()          # 새 정식 판 번호 예: 2.109.0
BILD = os.environ.get("BILD", "").strip()        # 붙일 빌드 번호 예: 2610251225
SAE = os.environ.get("SAE", "").strip()          # 새 기능 글(한국어)


def tok():
    now = int(time.time())
    return jwt.encode({"iss": iss, "iat": now, "exp": now + 600, "aud": "appstoreconnect-v1"},
                      key, algorithm="ES256", headers={"kid": kid, "typ": "JWT"})


def allim(jul):
    print(jul)
    print("::notice::" + jul)


def mal(bang, gil, q=None, mom=None):
    url = API + gil + (("?" + urllib.parse.urlencode(q)) if q else "")
    data = json.dumps(mom).encode() if mom is not None else None
    r = urllib.request.Request(url, data=data, method=bang,
                               headers={"Authorization": "Bearer " + tok(), "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(r, timeout=60) as f:
            b = f.read()
            return json.loads(b) if b else {}
    except urllib.error.HTTPError as e:
        allim("애플이 거절함 %s %s: %s" % (bang, gil, e.read().decode()[:600]))
        sys.exit(1)


ap = mal("GET", "apps", {"filter[bundleId]": BUNDLE}).get("data", [])
if not ap:
    allim("번들 아이디 %s 앱을 찾지 못함" % BUNDLE)
    sys.exit(1)
ap = ap[0]
ireum = ap["attributes"]["name"]
vs = mal("GET", "apps/%s/appStoreVersions" % ap["id"], {"limit": "5"}).get("data", [])
for v in vs:
    a = v["attributes"]
    allim("%s 정식 판 %s / 상태 %s / 공개 방식 %s" % (ireum, a["versionString"], a.get("appVersionState") or a.get("appStoreState"), a.get("releaseType")))


def sangtae(v):
    a = v["attributes"]
    return a.get("appVersionState") or a.get("appStoreState")


if HAL == "boda":
    sys.exit(0)

if HAL == "gonggae":
    gi = [v for v in vs if sangtae(v) == "PENDING_DEVELOPER_RELEASE"]
    if not gi:
        allim("%s: 공개를 기다리는 판이 없어 공개하지 않음" % ireum)
        sys.exit(0)
    v = gi[0]
    mal("POST", "appStoreVersionReleaseRequests",
        mom={"data": {"type": "appStoreVersionReleaseRequests",
                      "relationships": {"appStoreVersion": {"data": {"type": "appStoreVersions", "id": v["id"]}}}}})
    allim("%s %s판 앱스토어 공개를 요청함" % (ireum, v["attributes"]["versionString"]))
    sys.exit(0)

if HAL == "saepan":
    if not (PAN and BILD and SAE):
        allim("새 판에는 판 번호·빌드 번호·새 기능 글이 다 있어야 함")
        sys.exit(1)
    mak = [v for v in vs if sangtae(v) in ("WAITING_FOR_REVIEW", "IN_REVIEW", "PENDING_DEVELOPER_RELEASE", "PROCESSING_FOR_DISTRIBUTION")]
    if mak:
        allim("%s: %s판이 아직 %s 이라 새 판을 낼 수 없음 — 먼저 끝나야 함" % (ireum, mak[0]["attributes"]["versionString"], sangtae(mak[0])))
        sys.exit(1)
    b = mal("GET", "builds", {"filter[app]": ap["id"], "filter[version]": BILD, "limit": "5"}).get("data", [])
    b = [x for x in b if x["attributes"].get("processingState") == "VALID"]
    if not b:
        allim("%s: 빌드 %s 가 없거나 처리가 덜 끝남" % (ireum, BILD))
        sys.exit(1)
    b = b[0]
    if b["attributes"].get("usesNonExemptEncryption") is None:
        mal("PATCH", "builds/%s" % b["id"], mom={"data": {"type": "builds", "id": b["id"], "attributes": {"usesNonExemptEncryption": False}}})
    # 이미 같은 번호로 만든 준비 중 판이 있으면 그것을 씀
    jun = [v for v in vs if v["attributes"]["versionString"] == PAN and sangtae(v) in ("PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED")]
    if jun:
        vid = jun[0]["id"]
        mal("PATCH", "appStoreVersions/%s" % vid, mom={"data": {"type": "appStoreVersions", "id": vid, "attributes": {"releaseType": "MANUAL"}}})
    else:
        jun2 = [v for v in vs if sangtae(v) in ("PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED")]   # 1.1 거절된 판도 번호를 바꿔 다시 씀
        if jun2:   # 다른 번호의 준비 중 판은 번호만 바꿔 씀(앱마다 준비 중 판은 하나뿐)
            vid = jun2[0]["id"]
            mal("PATCH", "appStoreVersions/%s" % vid, mom={"data": {"type": "appStoreVersions", "id": vid,
                                                                     "attributes": {"versionString": PAN, "releaseType": "MANUAL"}}})
        else:
            vid = mal("POST", "appStoreVersions", mom={"data": {"type": "appStoreVersions",
                      "attributes": {"platform": "IOS", "versionString": PAN, "releaseType": "MANUAL"},
                      "relationships": {"app": {"data": {"type": "apps", "id": ap["id"]}}}}})["data"]["id"]
    mal("PATCH", "appStoreVersions/%s/relationships/build" % vid, mom={"data": {"type": "builds", "id": b["id"]}})
    for lo in mal("GET", "appStoreVersions/%s/appStoreVersionLocalizations" % vid).get("data", []):
        mal("PATCH", "appStoreVersionLocalizations/%s" % lo["id"],
            mom={"data": {"type": "appStoreVersionLocalizations", "id": lo["id"], "attributes": {"whatsNew": SAE}}})
    # 심사 내기 — 열려 있는(아직 안 낸) 묶음이 있으면 그것을 씀
    rs = mal("GET", "reviewSubmissions", {"filter[app]": ap["id"], "filter[state]": "READY_FOR_REVIEW,UNRESOLVED_ISSUES", "limit": "5"}).get("data", [])
    rid = rs[0]["id"] if rs else mal("POST", "reviewSubmissions", mom={"data": {"type": "reviewSubmissions",
        "attributes": {"platform": "IOS"}, "relationships": {"app": {"data": {"type": "apps", "id": ap["id"]}}}}})["data"]["id"]
    try:
        mal("POST", "reviewSubmissionItems", mom={"data": {"type": "reviewSubmissionItems", "relationships": {
            "reviewSubmission": {"data": {"type": "reviewSubmissions", "id": rid}},
            "appStoreVersion": {"data": {"type": "appStoreVersions", "id": vid}}}}})
    except SystemExit:
        allim("묶음에 이미 들어 있을 수 있어 그대로 냄을 시도함")
    mal("PATCH", "reviewSubmissions/%s" % rid, mom={"data": {"type": "reviewSubmissions", "id": rid, "attributes": {"submitted": True}}})
    allim("%s %s판(빌드 %s)을 심사에 냄 — 통과해도 공개하지 않고 기다림(공개 날은 이사장님이 정하심)" % (ireum, PAN, BILD))
    sys.exit(0)

allim("할 일(HAL)은 boda, gonggae, saepan 가운데 하나")
sys.exit(1)
