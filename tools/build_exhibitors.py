#!/usr/bin/env python3
"""tools/raw/ のテキストから app/src/main/assets/exhibitors.json を作る。

raw データは公式サイトのオンラインカタログ（出展社名50音検索・カテゴリー検索・英語版）から
会社名・小間番号・出展製品分類だけを書き写したもの。説明文や画像は含めない。
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
RAW = ROOT / "raw"
OUT = ROOT.parent / "app/src/main/assets/exhibitors.json"

GROUP_LABEL = {
    "1": "ア", "2": "カ", "3": "サ", "4": "タ", "5": "ナ",
    "6": "ハ", "7": "マ", "8": "ヤ", "9": "ラ", "10": "ワ", "oversea": "海外",
}
CODE_RE = re.compile(r"^[gms]\d+(,[gms]\d+)*$")
MARK_RE = re.compile(r"\s*\((\*{1,2})\)\s*$")


def parse_categories():
    g, m, s = {}, {}, {}
    for line in (RAW / "categories.txt").read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        kind, rest = line.split(" ", 1)
        if kind == "G":
            v, name = rest.split(" ", 1)
            g[v] = re.sub(r"^\d+(-[ab])?\.\s*", "", name).strip()
        elif kind == "M":
            v, parent, name = rest.split(" ", 2)
            name = re.sub(r"^（\d+）\s*", "", name)
            name = re.sub(r"^\d+(-[ab])?\.\s*", "", name)
            m[v] = {"g": parent, "t": name.strip()}
        elif kind == "S":
            _idx, parent, num, name = rest.split(" ", 3)
            s[num] = {"m": parent, "t": name.strip()}
    return g, m, s


def parse_en():
    en = {}
    for line in (RAW / "en_names.txt").read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        booth, name = line.split(" ", 1)
        en[booth] = name.strip()
    return en


def parse_exhibitors(en_by_booth):
    out = []
    for line in (RAW / "exhibitors.txt").read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        tokens = line.split(" ")
        group, booths = tokens[0], tokens[1].split(",")
        rest = tokens[2:]
        codes = []
        if rest and CODE_RE.match(rest[-1]):
            codes = rest.pop().split(",")
        name = " ".join(rest).strip()
        mark = 0
        mm = MARK_RE.search(name)
        if mm:
            mark = len(mm.group(1))
            name = name[: mm.start()].strip()
        entry = {"n": name, "b": booths, "k": GROUP_LABEL[group]}
        if mark:
            entry["j"] = mark
        en = None
        if len(booths) == 1 and booths[0] in en_by_booth:
            en = en_by_booth[booths[0]]
        elif len(booths) > 1:
            names = {en_by_booth.get(b) for b in booths} - {None}
            if len(names) == 1:
                en = names.pop()
        if en and en.lower() != name.lower():
            entry["e"] = en
        if codes:
            entry["c"] = codes
        out.append(entry)
    return out


# よく使われる言い方 → 分類コード。分類名そのものに含まれる語（旋盤・研削盤・チャック など）は
# 分類名の検索で当たるので入れない。ここには分類名に出てこない言い方だけを置く
SYNONYMS = {
    # 旋盤
    "立旋盤": ["s6"], "たてせんばん": ["s6"], "VTL": ["s6"], "立形旋盤": ["s6"], "縦旋盤": ["s6"],
    "横旋盤": ["s5"], "横形旋盤": ["s5"],
    "複合加工機": ["s7", "s29"],
    # マシニングセンタ・特殊加工
    "MC": ["m5"],
    "EDM": ["s53", "s54", "s55"], "ワイヤカット": ["s54"],
    "ウォータージェット": ["s59"],
    "AM": ["s67"], "アディティブ": ["s67"],
    # 測定
    "CMM": ["s233"], "三次元測定機": ["s233"], "3次元測定": ["s233"],
    "粗さ": ["s237"], "硬度": ["s248"], "計測": ["g8"], "測定器": ["g8"],
    # 周辺機器・自動化
    "自動化": ["s166", "s173"], "省人化": ["s166", "s173"], "ATC": ["s166"],
    "クーラント": ["s266", "s171"], "切削液": ["s266"],
    "切粉": ["s168"], "切りくず": ["s168"], "チップコンベア": ["s168"], "ミスト": ["s170"],
    "主軸": ["s163"], "軸受": ["s162"], "LMガイド": ["s161"],
    "空圧": ["s176"],
    # 工具・ツーリング
    "インサート": ["s198"], "チップ": ["s198", "s184"], "ツーリング": ["m21"], "ハイス": ["m28"],
    "バンドソー": ["s64"],
    # 制御・ソフト
    "CNC": ["s257"], "ソフト": ["g9"], "ソフトウェア": ["g9"], "IoT": ["s261"], "DX": ["s261", "s262"],
    # 鍛圧・その他
    "板金": ["g2", "s88"], "ベンダー": ["s78"], "ギヤ": ["g5"], "ギア": ["g5"],
    "洗浄機": ["s136"], "新聞": ["s263"], "雑誌": ["s263"],
}


def main():
    g, m, s = parse_categories()
    en = parse_en()
    ex = parse_exhibitors(en)

    valid = {f"g{k}" for k in g} | {f"m{k}" for k in m} | {f"s{k}" for k in s}
    bad = sorted({c for e in ex for c in e.get("c", []) if c not in valid})
    if bad:
        sys.exit(f"unknown codes: {bad}")
    bad_syn = sorted({c for v in SYNONYMS.values() for c in v if c not in valid})
    if bad_syn:
        sys.exit(f"unknown synonym codes: {bad_syn}")

    data = {
        "retrievedOn": "2026年10月26日",
        "source": "JIMTOF2026公式サイト オンラインカタログ（出展社名50音検索・カテゴリー検索）",
        "marks": {"1": "共同出展者", "2": "内部出展者"},
        "cats": {"g": g, "m": m, "s": s},
        "synonyms": SYNONYMS,
        "exhibitors": ex,
    }
    OUT.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    with_cats = sum(1 for e in ex if e.get("c"))
    with_en = sum(1 for e in ex if e.get("e"))
    print(f"exhibitors={len(ex)} withCats={with_cats} withEn={with_en} g={len(g)} m={len(m)} s={len(s)} bytes={OUT.stat().st_size}")


if __name__ == "__main__":
    main()
