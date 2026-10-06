# 공개 시험 링크 만들기 (1.0판, 빌드 261006-1, 이사장님 승인 2026-10-06 "공개시험으로 가자")
# 대상 앱에 바깥 시험 모임(공개 링크 켬)을 만들고, 가장 새 판을 넣어 애플 베타 심사에 냅니다.
# 심사 연락처·피드백 메일 같은 앱 공통 칸은 길눈 앱에 이미 적힌 것을 그대로 옮겨 씁니다(비어 있는 칸만).
# 아무것도 지우지 않습니다. 다시 돌리면 이미 된 단계는 건너뛰고 상태와 링크만 보여 줍니다.
import os, time, json, urllib.request, urllib.parse, urllib.error
import jwt

kid = os.environ["ASC_KEY_ID"]
iss = os.environ["ASC_ISSUER_ID"]
key = open(os.path.expanduser("~/private_keys/AuthKey_%s.p8" % kid)).read()
now = int(time.time())
tok = jwt.encode({"iss": iss, "iat": now, "exp": now + 1200, "aud": "appstoreconnect-v1"},
                 key, algorithm="ES256", headers={"kid": kid, "typ": "JWT"})
API = "https://api.appstoreconnect.apple.com/v1/"
WON = os.environ.get("WON_BUNDLE", "kr.or.ada.app")
DAE = os.environ.get("DAE_BUNDLE", "kr.or.ada.jabong")
MOIM = os.environ.get("MOIM", "자봉 공개 시험")
SOGAE = os.environ.get("SOGAE", "길눈 자봉은 자원봉사자가 길을 걸으며 시각장애인 보행 안내용 점지도를 그려 올리는 앱입니다.")
MUEOT = os.environ.get("MUEOT", "점지도 그리기 시험판입니다. 길을 걸으며 점지도를 그려 올려 보시고, 불편한 점이나 고칠 점을 알려 주십시오.")


def allim(jul):
    print(jul)
    print("::notice::" + jul)


def bureugi(gil, q=None, bon=None, bang="GET", jo=False):
    url = API + gil + ("?" + urllib.parse.urlencode(q) if q else "")
    data = json.dumps(bon).encode() if bon is not None else None
    r = urllib.request.Request(url, data=data, method=bang,
                               headers={"Authorization": "Bearer " + tok, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(r, timeout=30) as f:
            t = f.read()
            return json.loads(t) if t else {}
    except urllib.error.HTTPError as e:
        allim("애플 답 %s (%s %s): %s" % (e.code, bang, gil.split("?")[0], " ".join(e.read().decode("utf-8", "replace").split())[:500]))
        if jo:
            return None
        raise


def app_id(bundle):
    for a in bureugi("apps", {"filter[bundleId]": bundle, "limit": "5"}).get("data", []):
        if a["attributes"].get("bundleId") == bundle:
            return a["id"], a["attributes"].get("name", "")
    raise SystemExit("앱을 찾지 못함: " + bundle)


won_id, won_nm = app_id(WON)
dae_id, dae_nm = app_id(DAE)
allim("본보기 앱: %s / 대상 앱: %s" % (won_nm, dae_nm))

# 1. 베타 심사 연락처 — 비어 있는 칸만 길눈에서 옮김
won_rd = bureugi("apps/%s/betaAppReviewDetail" % won_id).get("data", {}).get("attributes", {})
dae_rd = bureugi("apps/%s/betaAppReviewDetail" % dae_id).get("data", {})
chae = {}
for k in ["contactFirstName", "contactLastName", "contactPhone", "contactEmail"]:
    if not (dae_rd.get("attributes", {}).get(k)) and won_rd.get(k):
        chae[k] = won_rd[k]
if dae_rd.get("attributes", {}).get("demoAccountRequired") is None:
    chae["demoAccountRequired"] = False
if chae:
    bureugi("betaAppReviewDetails/%s" % dae_rd["id"], bon={"data": {"type": "betaAppReviewDetails", "id": dae_rd["id"], "attributes": chae}}, bang="PATCH")
    allim("심사 연락처 칸 채움: %s" % ", ".join(chae.keys()))
else:
    allim("심사 연락처는 이미 채워져 있음")

# 2. 시험 안내 설명(한국어) — 없으면 만듦, 피드백 메일 등은 길눈 것을 씀
won_lc = bureugi("apps/%s/betaAppLocalizations" % won_id).get("data", [])
wl = next((x["attributes"] for x in won_lc if x["attributes"].get("locale", "").startswith("ko")), (won_lc[0]["attributes"] if won_lc else {}))
dae_lc = bureugi("apps/%s/betaAppLocalizations" % dae_id).get("data", [])
dl = next((x for x in dae_lc if x["attributes"].get("locale", "").startswith("ko")), None)
if dl is None:
    at = {"locale": wl.get("locale") or "ko", "description": SOGAE}
    for k in ["feedbackEmail", "marketingUrl", "privacyPolicyUrl"]:
        if wl.get(k):
            at[k] = wl[k]
    bureugi("betaAppLocalizations", bon={"data": {"type": "betaAppLocalizations", "attributes": at,
                                                   "relationships": {"app": {"data": {"type": "apps", "id": dae_id}}}}}, bang="POST")
    allim("시험 안내 설명(한국어)을 만듦")
else:
    bc = {}
    if not dl["attributes"].get("description"):
        bc["description"] = SOGAE
    for k in ["feedbackEmail", "marketingUrl", "privacyPolicyUrl"]:
        if not dl["attributes"].get(k) and wl.get(k):
            bc[k] = wl[k]
    if bc:
        bureugi("betaAppLocalizations/%s" % dl["id"], bon={"data": {"type": "betaAppLocalizations", "id": dl["id"], "attributes": bc}}, bang="PATCH")
        allim("시험 안내 설명 빈칸 채움: %s" % ", ".join(bc.keys()))
    else:
        allim("시험 안내 설명은 이미 있음")

# 3. 가장 새 판
b = bureugi("builds", {"filter[app]": dae_id, "filter[processingState]": "VALID", "sort": "-uploadedDate", "limit": "1",
                       "include": "buildBetaDetail,preReleaseVersion"})
if not b.get("data"):
    raise SystemExit("처리 끝난 판이 없음")
bd = b["data"][0]
ver = next((x["attributes"].get("version") for x in b.get("included", []) if x["type"] == "preReleaseVersions"), "")
allim("넣을 판: %s판 빌드 %s" % (ver, bd["attributes"].get("version")))
if bd["attributes"].get("usesNonExemptEncryption") is None:
    bureugi("builds/%s" % bd["id"], bon={"data": {"type": "builds", "id": bd["id"], "attributes": {"usesNonExemptEncryption": False}}}, bang="PATCH", jo=True)
    allim("암호 사용 신고: 쓰지 않음으로 적음")
bl = bureugi("builds/%s/betaBuildLocalizations" % bd["id"]).get("data", [])
if not any(x["attributes"].get("whatsNew") for x in bl):
    if bl:
        bureugi("betaBuildLocalizations/%s" % bl[0]["id"], bon={"data": {"type": "betaBuildLocalizations", "id": bl[0]["id"], "attributes": {"whatsNew": MUEOT}}}, bang="PATCH", jo=True)
    else:
        bureugi("betaBuildLocalizations", bon={"data": {"type": "betaBuildLocalizations", "attributes": {"locale": wl.get("locale") or "ko", "whatsNew": MUEOT},
                                                         "relationships": {"build": {"data": {"type": "builds", "id": bd["id"]}}}}}, bang="POST", jo=True)
    allim("이 판에서 시험할 것 적음")

# 4. 바깥 시험 모임(공개 링크)
moim = bureugi("apps/%s/betaGroups" % dae_id, {"limit": "50"}).get("data", [])
g = next((x for x in moim if x["attributes"].get("name") == MOIM), None)
if g is None:
    g = bureugi("betaGroups", bon={"data": {"type": "betaGroups",
                                            "attributes": {"name": MOIM, "publicLinkEnabled": True, "publicLinkLimitEnabled": False, "feedbackEnabled": True},
                                            "relationships": {"app": {"data": {"type": "apps", "id": dae_id}}}}}, bang="POST")["data"]
    allim("바깥 시험 모임 「%s」을 새로 만들고 공개 링크를 켬" % MOIM)
elif not g["attributes"].get("publicLinkEnabled"):
    bureugi("betaGroups/%s" % g["id"], bon={"data": {"type": "betaGroups", "id": g["id"], "attributes": {"publicLinkEnabled": True, "publicLinkLimitEnabled": False}}}, bang="PATCH")
    allim("「%s」 모임의 공개 링크를 켬" % MOIM)
else:
    allim("「%s」 모임이 이미 있고 공개 링크도 켜져 있음" % MOIM)
bureugi("betaGroups/%s/relationships/builds" % g["id"], bon={"data": [{"type": "builds", "id": bd["id"]}]}, bang="POST", jo=True)
allim("모임에 판을 넣음")

# 5. 베타 심사 내기
time.sleep(5)
det = bureugi("builds/%s/buildBetaDetail" % bd["id"]).get("data", {}).get("attributes", {})
st = det.get("externalBuildState")
allim("바깥 시험 상태(심사 전): %s" % st)
if st == "READY_FOR_BETA_SUBMISSION":
    r = bureugi("betaAppReviewSubmissions", bon={"data": {"type": "betaAppReviewSubmissions",
                                                         "relationships": {"build": {"data": {"type": "builds", "id": bd["id"]}}}}}, bang="POST", jo=True)
    allim("베타 심사를 냄" if r else "베타 심사 내기가 거절됨(위 애플 답 참고)")
time.sleep(5)
det = bureugi("builds/%s/buildBetaDetail" % bd["id"]).get("data", {}).get("attributes", {})
allim("바깥 시험 상태(지금): %s" % det.get("externalBuildState"))
g2 = bureugi("betaGroups/%s" % g["id"]).get("data", {}).get("attributes", {})
allim("공개 링크: %s" % (g2.get("publicLink") or "아직 없음"))
