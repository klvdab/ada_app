# 옛 개발용 인증서 정리 (1.0판, 빌드 260927-1, 이사장님 승인 2026-09-27)
# 깃허브가 짓기마다 애플에 개발용 인증서("Created via API")를 하나씩 새로 만들어, 열 개가 차면 짓기가 막힙니다.
# 짓기 전에 두 시간 넘은 "Created via API" 개발용 인증서만 폐기합니다.
#   - 배포용 인증서(앱스토어·테스트플라이트 서명)는 건드리지 않습니다.
#   - 두 시간 안에 만든 것은 다른 짓기가 쓰는 중일 수 있어 남겨 둡니다.
#   - 이 단계가 실패해도 짓기는 그대로 이어 갑니다(워크플로에서 continue-on-error).
import os, time, json, datetime, urllib.request
import jwt

kid = os.environ["ASC_KEY_ID"]
iss = os.environ["ASC_ISSUER_ID"]
key = open(os.path.expanduser("~/private_keys/AuthKey_%s.p8" % kid)).read()
now = int(time.time())
tok = jwt.encode({"iss": iss, "iat": now, "exp": now + 600, "aud": "appstoreconnect-v1"},
                 key, algorithm="ES256", headers={"kid": kid, "typ": "JWT"})
API = "https://api.appstoreconnect.apple.com/v1/certificates"


def bureugi(method, url):
    r = urllib.request.Request(url, method=method, headers={"Authorization": "Bearer " + tok})
    with urllib.request.urlopen(r, timeout=30) as f:
        return f.read()


d = json.loads(bureugi("GET", API + "?limit=200"))
gijun = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=365) - datetime.timedelta(hours=2)
pye = 0
nam = 0
for c in d.get("data", []):
    a = c.get("attributes", {})
    jong = a.get("certificateType", "")
    ireum = (a.get("displayName") or "") + " " + (a.get("name") or "")
    if jong not in ("DEVELOPMENT", "IOS_DEVELOPMENT"):
        continue
    if "Created via API" not in ireum:
        continue
    # 애플은 UTC 로 줍니다 — 앞 19자(YYYY-MM-DDTHH:MM:SS)만 읽어 파이썬 판에 따라 어긋나지 않게
    kkeut = datetime.datetime.strptime(a["expirationDate"][:19], "%Y-%m-%dT%H:%M:%S").replace(tzinfo=datetime.timezone.utc)
    if kkeut > gijun:
        nam += 1
        continue
    bureugi("DELETE", API + "/" + c["id"])
    pye += 1
print("옛 개발용 인증서 폐기: %d개, 새것이라 남김: %d개" % (pye, nam))
