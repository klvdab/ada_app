# 테스트플라이트 판 상태 살피기 (1.0판, 빌드 260929-1, 이사장님 승인 2026-09-29)
# 로그인 없이 앱스토어 커넥트 열쇠로 애플에 물어, 올린 판들이 시험 가능한지 알려 줍니다.
# 읽기만 합니다. 아무것도 바꾸거나 지우지 않습니다.
import os, time, json, datetime, urllib.request, urllib.parse
import jwt

kid = os.environ["ASC_KEY_ID"]
iss = os.environ["ASC_ISSUER_ID"]
key = open(os.path.expanduser("~/private_keys/AuthKey_%s.p8" % kid)).read()
now = int(time.time())
tok = jwt.encode({"iss": iss, "iat": now, "exp": now + 600, "aud": "appstoreconnect-v1"},
                 key, algorithm="ES256", headers={"kid": kid, "typ": "JWT"})
API = "https://api.appstoreconnect.apple.com/v1/"
MAN = int(os.environ.get("MYEOT", "5") or "5")


def bureugi(gil, q):
    url = API + gil + "?" + urllib.parse.urlencode(q)
    r = urllib.request.Request(url, headers={"Authorization": "Bearer " + tok})
    with urllib.request.urlopen(r, timeout=30) as f:
        return json.loads(f.read())


CHEORI = {"PROCESSING": "애플 처리 중", "VALID": "처리 끝", "FAILED": "처리 실패", "INVALID": "무효"}
ANJJOK = {"PROCESSING": "처리 중", "PROCESSING_EXCEPTION": "처리 중 문제", "MISSING_EXPORT_COMPLIANCE": "수출 규정 답 필요",
          "READY_FOR_BETA_TESTING": "시험 준비됨", "IN_BETA_TESTING": "시험 가능", "EXPIRED": "기한 지남",
          "IN_EXPORT_COMPLIANCE_REVIEW": "수출 규정 검토 중"}
BAKKAT = {"PROCESSING": "처리 중", "PROCESSING_EXCEPTION": "처리 중 문제", "MISSING_EXPORT_COMPLIANCE": "수출 규정 답 필요",
          "READY_FOR_BETA_TESTING": "시험 준비됨", "IN_BETA_TESTING": "시험 가능", "EXPIRED": "기한 지남",
          "READY_FOR_BETA_SUBMISSION": "베타 심사 내기 전", "IN_EXPORT_COMPLIANCE_REVIEW": "수출 규정 검토 중",
          "WAITING_FOR_BETA_REVIEW": "베타 심사 기다림", "IN_BETA_REVIEW": "베타 심사 중",
          "BETA_REJECTED": "베타 심사 거절", "BETA_APPROVED": "베타 심사 통과"}


def kst(s):
    if not s:
        return "-"
    t = datetime.datetime.strptime(s[:19], "%Y-%m-%dT%H:%M:%S").replace(tzinfo=datetime.timezone.utc)
    return (t + datetime.timedelta(hours=9)).strftime("%m월 %d일 %H시 %M분")


def allim(jul):
    print(jul)
    print("::notice::" + jul)


apps = bureugi("apps", {"limit": "50"}).get("data", [])
for ap in apps:
    ireum = ap["attributes"].get("name", "")
    d = bureugi("builds", {"filter[app]": ap["id"], "sort": "-uploadedDate", "limit": str(MAN),
                           "include": "preReleaseVersion,buildBetaDetail"})
    gyeot = {(x["type"], x["id"]): x.get("attributes", {}) for x in d.get("included", [])}
    if not d.get("data"):
        allim("%s: 올린 판 없음" % ireum)
        continue
    for b in d["data"]:
        a = b["attributes"]
        rel = b.get("relationships", {})
        pv = (rel.get("preReleaseVersion", {}).get("data") or {})
        bd = (rel.get("buildBetaDetail", {}).get("data") or {})
        pan = gyeot.get(("preReleaseVersions", pv.get("id")), {}).get("version", "?")
        bet = gyeot.get(("buildBetaDetails", bd.get("id")), {})
        cheori = CHEORI.get(a.get("processingState"), a.get("processingState"))
        an = ANJJOK.get(bet.get("internalBuildState"), bet.get("internalBuildState") or "-")
        ba = BAKKAT.get(bet.get("externalBuildState"), bet.get("externalBuildState") or "-")
        gihan = " (기한 지남)" if a.get("expired") else ""
        allim("%s %s판 빌드 %s / 올린 때 %s / %s / 안쪽 시험: %s / 바깥 시험: %s%s"
              % (ireum, pan, a.get("version"), kst(a.get("uploadedDate")), cheori, an, ba, gihan))
