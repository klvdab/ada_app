# 시험 모임 만들기 (1.0.2판, 빌드 260930-3 — 사람 넣기를 두 길로 다시 시도, 1.0.1 애플 답 한 줄, 이사장님 승인 2026-09-30
#  "AI점자도서관 앱에 협회 안쪽 시험 모임을 만들고 길눈과 같은 사람들을 넣는 것을 허락한다.")
# 앱스토어 커넥트 열쇠로, 대상 앱에 길눈 앱과 같은 이름의 내부 시험 모임을 만들고(이미 있으면 그대로 씀)
# 길눈 모임에 있는 사람들을 그대로 넣습니다. 모임은 앞으로 올라오는 판을 모두 받게 해 둡니다.
# 아무것도 지우지 않습니다.
import os, time, json, urllib.request, urllib.parse, urllib.error
import jwt

kid = os.environ["ASC_KEY_ID"]
iss = os.environ["ASC_ISSUER_ID"]
key = open(os.path.expanduser("~/private_keys/AuthKey_%s.p8" % kid)).read()
now = int(time.time())
tok = jwt.encode({"iss": iss, "iat": now, "exp": now + 900, "aud": "appstoreconnect-v1"},
                 key, algorithm="ES256", headers={"kid": kid, "typ": "JWT"})
API = "https://api.appstoreconnect.apple.com/v1/"
WON = os.environ.get("WON_BUNDLE", "kr.or.ada.app")
DAE = os.environ.get("DAE_BUNDLE", "kr.or.ada.lib")
MOIM = os.environ.get("MOIM", "협회 안쪽 시험")


def allim(jul):
    print(jul)
    print("::notice::" + jul)


def bureugi(gil, q=None, bon=None, bang="GET"):
    url = API + gil + ("?" + urllib.parse.urlencode(q) if q else "")
    data = json.dumps(bon).encode() if bon is not None else None
    r = urllib.request.Request(url, data=data, method=bang,
                               headers={"Authorization": "Bearer " + tok, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(r, timeout=30) as f:
            t = f.read()
            return json.loads(t) if t else {}
    except urllib.error.HTTPError as e:
        allim("애플 답 %s: %s" % (e.code, " ".join(e.read().decode("utf-8", "replace").split())[:600]))
        raise


def app_id(bundle):
    d = bureugi("apps", {"filter[bundleId]": bundle, "limit": "5"}).get("data", [])
    for a in d:
        if a["attributes"].get("bundleId") == bundle:
            return a["id"], a["attributes"].get("name", "")
    raise SystemExit("앱을 찾지 못함: " + bundle)


def moimdeul(aid):
    return bureugi("apps/%s/betaGroups" % aid, {"limit": "50"}).get("data", [])


won_id, won_nm = app_id(WON)
dae_id, dae_nm = app_id(DAE)
allim("원래 앱: %s / 대상 앱: %s" % (won_nm, dae_nm))

won_moim = [g for g in moimdeul(won_id) if g["attributes"].get("isInternalGroup")]
if not won_moim:
    raise SystemExit("원래 앱에 내부 시험 모임이 없음")
wm = next((g for g in won_moim if g["attributes"].get("name") == MOIM), won_moim[0])
saram = bureugi("betaGroups/%s/betaTesters" % wm["id"], {"limit": "200"}).get("data", [])
allim("원래 모임 「%s」 사람 %d명" % (wm["attributes"].get("name"), len(saram)))

dm = next((g for g in moimdeul(dae_id) if g["attributes"].get("name") == MOIM), None)
if dm is None:
    dm = bureugi("betaGroups", bon={"data": {"type": "betaGroups",
                                            "attributes": {"name": MOIM, "isInternalGroup": True, "hasAccessToAllBuilds": True},
                                            "relationships": {"app": {"data": {"type": "apps", "id": dae_id}}}}},
                 bang="POST")["data"]
    allim("대상 앱에 「%s」 모임을 새로 만듦" % MOIM)
else:
    allim("대상 앱에 「%s」 모임이 이미 있어 그대로 씀" % MOIM)

itdeon = {t["id"] for t in bureugi("betaGroups/%s/betaTesters" % dm["id"], {"limit": "200"}).get("data", [])}
neol = [t for t in saram if t["id"] not in itdeon]
for t in neol:
    at = t["attributes"]
    try:
        bureugi("betaTesters/%s/relationships/betaGroups" % t["id"],
                bon={"data": [{"type": "betaGroups", "id": dm["id"]}]}, bang="POST")
        allim("넣음(사람 쪽에서 모임 잇기): %s" % (at.get("firstName") or ""))
        continue
    except urllib.error.HTTPError:
        pass
    try:
        bureugi("betaTesters", bon={"data": {"type": "betaTesters",
                                             "attributes": {"email": at.get("email"), "firstName": at.get("firstName") or "",
                                                            "lastName": at.get("lastName") or ""},
                                             "relationships": {"betaGroups": {"data": [{"type": "betaGroups", "id": dm["id"]}]}}}},
                bang="POST")
        allim("넣음(메일로 새로 청함): %s" % (at.get("firstName") or ""))
    except urllib.error.HTTPError:
        allim("이 사람은 넣지 못함: %s" % (at.get("firstName") or ""))
allim("넣은 사람 %d명, 이미 있던 사람 %d명" % (len(neol), len(itdeon)))
for t in saram:
    a = t["attributes"]
    allim(" - %s %s" % (a.get("lastName") or "", a.get("firstName") or ""))

hwak = bureugi("betaGroups/%s/betaTesters" % dm["id"], {"limit": "200"}).get("data", [])
allim("확인: 대상 모임 사람 %d명" % len(hwak))
b = bureugi("builds", {"filter[app]": dae_id, "sort": "-uploadedDate", "limit": "3", "include": "buildBetaDetail"})
gyeot = {x["id"]: x.get("attributes", {}) for x in b.get("included", [])}
for x in b.get("data", []):
    rel = x.get("relationships", {}).get("buildBetaDetail", {}).get("data") or {}
    allim("판 %s: 처리 %s, 안쪽 시험 %s" % (x["attributes"].get("version"), x["attributes"].get("processingState"),
                                     gyeot.get(rel.get("id"), {}).get("internalBuildState", "-")))
