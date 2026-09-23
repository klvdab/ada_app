# 협회 앱·자봉 앱·배프 앱 아이콘 만들기 (1.0.2판, 빌드 260923-5)
# 깃허브의 빌린 맥에서 짓기 직전에 돌려 아이폰·워치 아이콘(1024, 투명 없음)을 만듭니다.
# 협회 앱: 짙은 파랑 바탕에 흰 글자 LVD, 아래 노란 줄
# 자봉 앱: 짙은 초록 바탕에 흰 글자 자봉, 아래 노란 줄
# 배프 앱: 짙은 자주 바탕에 흰 글자 배프, 아래 노란 줄
# 저시력 이용자도 알아보기 쉽게 대비를 크게 했습니다.
import json
import os

from PIL import Image, ImageDraw, ImageFont

S = 1024
FG = (255, 255, 255)
BAR = (255, 204, 0)
BG_ADA = (18, 52, 110)
BG_JABONG = (12, 96, 60)
BG_BFB = (122, 20, 70)

FONTS = [
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    "/System/Library/Fonts/Supplemental/Arial Black.ttf",
    "/Library/Fonts/Arial Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]
FONTS_KO = [
    "/System/Library/Fonts/AppleSDGothicNeo.ttc",
    "/System/Library/Fonts/Supplemental/AppleGothic.ttf",
    "/usr/share/fonts/truetype/nanum/NanumGothicBold.ttf",
]


def font(size, korean=False):
    paths = (FONTS_KO + FONTS) if korean else FONTS
    for p in paths:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                continue
    raise SystemExit("글꼴을 찾지 못했습니다: " + ", ".join(paths))


def can_draw(f, t):
    try:
        x0, y0, x1, y1 = ImageDraw.Draw(Image.new("RGB", (10, 10))).textbbox((0, 0), t, font=f)
        return (x1 - x0) > 0 and (y1 - y0) > 0
    except Exception:
        return False


def make(bg, text, korean=False, size=360):
    img = Image.new("RGB", (S, S), bg)
    d = ImageDraw.Draw(img)
    f = font(size, korean=korean)
    t = text
    if not can_draw(f, t):
        t = "JB"
        f = font(size)
    x0, y0, x1, y1 = d.textbbox((0, 0), t, font=f)
    w, h = x1 - x0, y1 - y0
    d.text(((S - w) / 2 - x0, (S - h) / 2 - y0 - 60), t, font=f, fill=FG)
    d.rounded_rectangle((252, 700, 772, 748), radius=24, fill=BAR)
    return img


def save(img, folder, platform):
    os.makedirs(folder, exist_ok=True)
    img.save(os.path.join(folder, "icon-1024.png"))
    with open(os.path.join(folder, "Contents.json"), "w") as fp:
        json.dump({"images": [{"filename": "icon-1024.png", "idiom": "universal",
                               "platform": platform, "size": "1024x1024"}],
                   "info": {"author": "xcode", "version": 1}}, fp, indent=2)
    with open(os.path.join(os.path.dirname(folder), "Contents.json"), "w") as fp:
        json.dump({"info": {"author": "xcode", "version": 1}}, fp, indent=2)


if __name__ == "__main__":
    ada = make(BG_ADA, "LVD")
    save(ada, "App/Assets.xcassets/AppIcon.appiconset", "ios")
    save(ada, "Watch/Assets.xcassets/AppIcon.appiconset", "watchos")
    jb = make(BG_JABONG, "자봉", korean=True, size=400)
    save(jb, "Jabong/Assets.xcassets/AppIcon.appiconset", "ios")
    bf = make(BG_BFB, "배프", korean=True, size=400)
    save(bf, "Bfb/Assets.xcassets/AppIcon.appiconset", "ios")
    print("아이콘을 만들었습니다:", ada.size, jb.size, bf.size)
