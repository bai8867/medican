"""一次性：生成与前端 svgPlaceholderCover 同风格的药膳名示意图 SVG。"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "tcm-diet-frontend" / "public" / "recipe-covers"

NAMES = [
    "枸杞菊花茶",
    "黄芪炖鸡",
    "姜枣茶",
    "酸枣仁茶",
    "绿豆汤",
    "当归生姜羊肉汤",
    "红糖姜枣茶",
    "菊花决明子茶",
    "山药红枣粥",
    "银耳雪梨汤",
]


def esc(s: str) -> str:
    return (s or "膳").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def font_for(n: str) -> int:
    L = len(n)
    if L <= 4:
        return 28
    if L <= 6:
        return 26
    if L <= 8:
        return 24
    if L <= 10:
        return 21
    if L <= 12:
        return 19
    return 17


def svg(name: str) -> str:
    t = esc(name)
    fs = font_for(name)
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="480" height="360" viewBox="0 0 480 360">
<defs><linearGradient id="g" x1="0" x2="1" y1="0" y2="1">
<stop offset="0%" stop-color="#ecfdf5"/><stop offset="100%" stop-color="#d8f3dc"/>
</linearGradient></defs>
<rect fill="url(#g)" width="100%" height="100%"/>
<text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle"
 font-family="system-ui, -apple-system, Segoe UI, sans-serif" font-size="{fs}" fill="#2d6a4f">{t}</text>
</svg>
"""


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "default-cover.svg").write_text(svg("药膳"), encoding="utf-8")
    for i, n in enumerate(NAMES, 1):
        (OUT / f"recipe-{i}.svg").write_text(svg(n), encoding="utf-8")
    print("OK", OUT)


if __name__ == "__main__":
    main()
