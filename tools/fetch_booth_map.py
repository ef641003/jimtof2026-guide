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

# 1つの小間：polygon のあとに「== '小間番号'」の印。間に別の polygon をはさまない
POLY = re.compile(
    r"L\.polygon\((\[\[.*?\]\])\)?,\s*\{[^}]*\}\)(?:(?!L\.polygon)[\s\S])*?get\('no'\)\s*==+\s*'([A-Z0-9-]+)'\)\{L\.marker\(\[(-?\d+),(-?\d+)\]\)"
)
# パビリオン（複数社が1つの区画）：polygon のあとに ["E1009","E1009-1",...].includes(...)
PAVILION = re.compile(
    r"L\.polygon\((\[\[.*?\]\])\)?,\s*\{[^}]*\}\)(?:(?!L\.polygon)[\s\S])*?if\(\[([^\]]*)\]\.includes\(searchParams\.get\('no'\)\)\)\{L\.marker\(\[(-?\d+),(-?\d+)\]\)"
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

        def add(poly: str, code: str, my: str, mx: str, members=None):
            pts = json.loads(poly)
            # x はそのまま、y は下向き正に。どちらも 1/8
            ps = [[round(p[1] / 8), round(-p[0] / 8)] for p in pts]
            # 閉じるための最後の点（最初とほぼ同じ）は落とす
            if len(ps) > 3 and abs(ps[0][0] - ps[-1][0]) <= 4 and abs(ps[0][1] - ps[-1][1]) <= 4:
                ps = ps[:-1]
            row = {"c": code, "p": [v for q in ps for v in q], "m": [round(int(mx) / 8), round(-int(my) / 8)]}
            if members:
                row["g"] = members
            booths.append(row)

        for poly, code, my, mx in POLY.findall(text):
            add(poly, code, my, mx)
        for poly, codes, my, mx in PAVILION.findall(text):
            members = [c.strip().strip('"\'') for c in codes.split(",") if c.strip()]
            add(poly, members[0], my, mx, members)
        polys = text.count("L.polygon")
        if polys != len(booths):
            print(f"{m}: polygon {polys} に対して {len(booths)} しか取れません", file=sys.stderr)
            return 1
        codes_seen = [b["c"] for b in booths]
        if len(codes_seen) != len(set(codes_seen)):
            print(f"{m}: 小間番号が重複", file=sys.stderr)
            return 1
        if not booths:
            print(f"{m}: 小間が取れません", file=sys.stderr)
            return 1
        total += len(booths)
        maps.append({"id": m, "page": PAGE.format(m=m, code="{code}"), "booths": booths})
        print(f"{m}: {len(booths)}")
    OUT.write_text(json.dumps({"note": "公式小間割図の小間の形（p: x,y の並び）と印の位置（m）。1/8 縮尺、y は下向き。g はパビリオンの小間番号", "maps": maps},
                              ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"total {total} -> {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
