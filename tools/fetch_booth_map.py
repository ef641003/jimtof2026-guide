#!/usr/bin/env python3
"""公式の小間割図（jimtof.org の地図ページが読み込む *-BoothInfo.js）から、各小間の位置と形を取り出して
tools/raw/booth_map/*.js に保存し、app/src/main/assets/booth_map.json を作る。

保存するのは小間番号と、その小間の四角（左上と幅・高さ）、印の位置だけ。地図の画像は使わない。
座標は公式図の座標系（Leaflet CRS.Simple、y は下向きが負）を 1/8 にしたもの。
"""
import json
import re
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "tools/raw/booth_map"
OUT = ROOT / "app/src/main/assets/booth_map.json"
BASE = "https://www.jimtof.org/map/jp/{m}_booth/{M}-BoothInfo.js"
MAPS = ["e1-3", "e7-8", "w1-2", "w3-4", "s1-2", "s3-4"]
PAGE = "https://www.jimtof.org/map/jp/{m}_booth/{m}.html?no={code}"

POLY = re.compile(
    r"L\.polygon\((\[\[.*?\]\])\)?,\s*\{[^}]*\}\)[\s\S]*?get\('no'\)\s*==+\s*'([A-Z0-9]+)'\)\{L\.marker\(\[(-?\d+),(-?\d+)\]\)"
)


def load(m: str, offline: bool) -> str:
    path = RAW / f"{m}.js"
    if offline:
        return path.read_text(encoding="utf-8")
    req = urllib.request.Request(BASE.format(m=m, M=m.upper()), headers={"User-Agent": "jimtof-guide-build"})
    with urllib.request.urlopen(req, timeout=60) as r:
        text = r.read().decode("utf-8")
    RAW.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    return text


def main() -> int:
    offline = "--offline" in sys.argv
    maps = []
    total = 0
    for m in MAPS:
        text = load(m, offline)
        booths = []
        for poly, code, my, mx in POLY.findall(text):
            pts = json.loads(poly)
            ys = [p[0] for p in pts]
            xs = [p[1] for p in pts]
            x1, y1, x2, y2 = min(xs), min(ys), max(xs), max(ys)
            # y を下向き正にそろえ、1/8 にする
            booths.append([
                code,
                round(x1 / 8), round(-y2 / 8), round((x2 - x1) / 8), round((y2 - y1) / 8),
                round(int(mx) / 8), round(-int(my) / 8),
            ])
        if not booths:
            print(f"{m}: 小間が取れません", file=sys.stderr)
            return 1
        total += len(booths)
        maps.append({"id": m, "page": PAGE.format(m=m, code="{code}"), "booths": booths})
        print(f"{m}: {len(booths)}")
    OUT.write_text(json.dumps({"note": "公式小間割図の小間の位置（左上x, 左上y, 幅, 高さ, 印x, 印y）。1/8 縮尺", "maps": maps},
                              ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"total {total} -> {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
