#!/usr/bin/env python3
"""抓取 annasnotebook 的技能总表，补齐 data/skills.json 中缺失的技能条目。

数据来源（站点把「特技/战技/血印/根源」都收在一页）：
  - https://www.annasnotebook.net/db/skills/     技能总表（16 个分类，每行含名称/效果/习得）
  - https://www.annasnotebook.net/assets/linktips.json  按 slug 的 [名称, 类别+数值, 描述]

为什么需要它：本站 data/skills.json（镜像自 cn.fire-emblem-fw.site）缺少约 76 条，
主要是「等级特技的基础版」（本站只有 +版）、圣焰战技、以及整类「根源」条目。
靠本脚本可复现地补齐，避免手工誊抄出错。

用法：
  python3 tools/scrape_annas_skills.py            # 只比对，打印缺失报告，不改 data/
  python3 tools/scrape_annas_skills.py --apply     # 合并缺失条目进 data/skills.json
  python3 tools/scrape_annas_skills.py --offline   # 用本地缓存（.cache_annas/）不联网

合并原则（幂等）：
  - 只新增「本站不存在的中文名」，绝不改动已有条目任何字段；
  - 站点分类 → 本站桶的映射见 SITE_CAT_TO_BUCKET；「根源」是本站没有的新分类，
    归入 skills 桶并打 kind="根源"（这样弹框能统一按 name 查到描述）；
  - learn/chars 尽量用站点「习得」列还原（形如「角色：Lv.35」），否则留空，
    不臆造数据。
"""
from __future__ import annotations

import argparse
import html
import json
import os
import re
import sys
import urllib.request
from collections import Counter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "data", "skills.json")
CACHE = os.path.join(ROOT, ".cache_annas")

SITE = "https://www.annasnotebook.net"
DB_URL = SITE + "/db/skills/"
TIPS_URL = SITE + "/assets/linktips.json"

# 站点 data-g → 本站 data/skills.json 桶 + 该桶内的归类字段值
# skills 桶的 kind 用现有数据的短名（技能/等级/兵种/精通/个人/骑乘/装备）；站点的
# 「可学特技」在本站叫「技能」，「兵种特技」叫「兵种」，必须对齐否则分组/筛选会分裂。
# arts 桶用 weapon；blood 桶无归类字段；「根源」是本站原先没有的新 kind。
SITE_CAT_TO_BUCKET = {
    "personal": ("skills", "kind", "个人"),
    "level":    ("skills", "kind", "等级"),
    "class":    ("skills", "kind", "兵种"),
    "master":   ("skills", "kind", "精通"),
    "skill":    ("skills", "kind", "技能"),
    "mount":    ("skills", "kind", "骑乘"),
    "equip":    ("skills", "kind", "装备"),
    "a0":       ("arts", "weapon", "剑"),
    "a1":       ("arts", "weapon", "枪"),
    "a2":       ("arts", "weapon", "斧"),
    "a3":       ("arts", "weapon", "弓"),
    "a4":       ("arts", "weapon", "手甲"),
    "a5":       ("arts", "weapon", "任意"),
    "a6":       ("arts", "weapon", "圣焰"),
    "blood":    ("blood", None, None),
    "origin":   ("skills", "kind", "根源"),
}
CAT_LABEL = {
    "personal": "个人特技", "level": "等级特技", "class": "兵种特技", "master": "精通特技",
    "skill": "可学特技", "mount": "骑乘特技", "equip": "装备特技", "a0": "剑战技",
    "a1": "枪战技", "a2": "斧战技", "a3": "弓战技", "a4": "手甲战技", "a5": "不限武器战技",
    "a6": "圣焰战技", "blood": "血印", "origin": "根源",
}


def fetch(url: str, dest: str, force: bool) -> str:
    os.makedirs(CACHE, exist_ok=True)
    if os.path.exists(dest) and not force:
        return open(dest, encoding="utf-8").read()
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    raw = urllib.request.urlopen(req, timeout=90).read()
    # 站点可能用 gzip/br；urllib 不发 Accept-Encoding，拿到的通常是明文
    text = raw.decode("utf-8", "replace")
    open(dest, "w", encoding="utf-8").write(text)
    return text


def strip_tags(s: str) -> str:
    return re.sub(r"<[^>]+>", "", s).strip()


def parse_db(html_text: str) -> list[dict]:
    """把 /db/skills/ 的每一行解析成 {name,en,jp,effect,acq,cat,g,slug}。"""
    rows: list[dict] = []
    parts = re.split(r'<section class="dbpane" data-g="', html_text)
    for p in parts[1:]:
        g = p.split('"', 1)[0]
        if g not in SITE_CAT_TO_BUCKET:
            continue
        for r in re.findall(r"<tr id=\"it-[^\"]+\".*?</tr>", p, re.S):
            def attr(a):
                m = re.search(rf'{a}="([^"]*)"', r)
                return html.unescape(m.group(1)) if m else ""
            tds = re.findall(r"<td[^>]*>(.*?)</td>", r, re.S)
            rows.append({
                "slug": attr("id").replace("it-", ""),
                "name": attr("data-sz"),
                "en": attr("data-se"),
                "jp": attr("data-sj"),
                "effect": attr("data-tb"),
                "acq": strip_tags(tds[2]) if len(tds) > 2 else "",
                "g": g,
                "cat": CAT_LABEL[g],
            })
    # 去重（同名同行只留一条，保留第一次出现）
    seen, out = set(), []
    for r in rows:
        if r["name"] and r["name"] not in seen:
            seen.add(r["name"])
            out.append(r)
    return out


def existing_names(data: dict) -> set[str]:
    names = set()
    for bucket in ("blood", "skills", "arts", "magic"):
        for e in data.get(bucket, []):
            if e.get("name"):
                names.add(e["name"])
    return names


def bucket_prefix(bucket: str) -> str:
    return {"arts": "art-", "skills": "sk-", "blood": "bm-", "magic": "mg-"}[bucket]


def next_id(data: dict, bucket: str) -> int:
    """返回该桶可用的下一个序号（沿用现有前缀 art-/sk-/bm-/mg-）。"""
    prefix = bucket_prefix(bucket)
    mx = 0
    for e in data.get(bucket, []):
        m = re.match(re.escape(prefix) + r"(\d+)$", e.get("id", ""))
        if m:
            mx = max(mx, int(m.group(1)))
    return mx + 1


def parse_acq(acq: str) -> tuple[list[str], list[str]]:
    """站点「习得」列 → (learn[], chars[])。形如「角色：Lv.35」或「重装术 C：角色、角色」。"""
    acq = acq.strip()
    if not acq:
        return [], []
    learn, chars = [], []
    # 可能有多段（分号/换行分隔）
    for seg in re.split(r"[；;\n]", acq):
        seg = seg.strip()
        if not seg:
            continue
        if "：" in seg:
            cond, who = seg.split("：", 1)
            for name in re.split(r"[、,，]", who):
                name = name.strip()
                if name and name not in ("", "—"):
                    chars.append(name)
                    learn.append(f"{name}：{cond.strip()}")
        else:
            learn.append(seg)
    # 去重保序
    seen = set()
    chars = [c for c in chars if not (c in seen or seen.add(c))]
    return learn, chars


def build_entry(data: dict, row: dict) -> dict:
    bucket, key, val = SITE_CAT_TO_BUCKET[row["g"]]
    eid = f"{bucket_prefix(bucket)}{next_id(data, bucket)}"
    e = {
        "id": eid,
        "name": row["name"],
        "text": row["effect"],
    }
    if key:
        e[key] = val
    learn, chars = parse_acq(row["acq"])
    e["learn"] = learn
    e["chars"] = chars
    if bucket == "arts":
        # 保持与现有 arts 条目字段一致，未知数值留 "--"
        for f in ("cost", "might", "hit", "crit", "avo", "ddg", "eff", "sup", "range"):
            e[f] = "--"
        e["note"] = ""
    return e


def merge(data: dict, rows: list[dict]) -> list[dict]:
    """把缺失条目追加进 data，返回新增的条目列表（幂等）。"""
    have = existing_names(data)
    added = []
    for row in rows:
        if row["name"] in have:
            continue
        entry = build_entry(data, row)
        bucket = SITE_CAT_TO_BUCKET[row["g"]][0]
        data.setdefault(bucket, []).append(entry)
        have.add(row["name"])
        added.append((bucket, entry))
    return added


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="合并缺失条目进 data/skills.json")
    ap.add_argument("--offline", action="store_true", help="用本地缓存，不联网")
    ap.add_argument("--force", action="store_true", help="强制重新下载")
    args = ap.parse_args()

    db_cache = os.path.join(CACHE, "db_skills.html")
    if args.offline and not os.path.exists(db_cache):
        print(f"offline 模式但缺少缓存 {db_cache}；先联网运行一次。", file=sys.stderr)
        return 2
    db_html = open(db_cache, encoding="utf-8").read() if args.offline \
        else fetch(DB_URL, db_cache, args.force)

    rows = parse_db(db_html)
    data = json.load(open(DATA, encoding="utf-8"))
    have = existing_names(data)
    missing = [r for r in rows if r["name"] not in have]

    print(f"站点条目 {len(rows)} · 本站已有名字 {len(have)} · 缺失 {len(missing)}")
    print("缺失分布：", dict(Counter(r["cat"] for r in missing)))

    if not args.apply:
        print("\n（dry-run。加 --apply 合并。缺失清单：）")
        for r in missing:
            print(f"  [{r['cat']}] {r['name']} | {r['effect'][:50]}")
        return 0

    added = merge(data, rows)
    # indent=1 与 data/skills.json 现有格式一致（json.dump 默认会缩进整个文件，
    # 若改成 2/4 会让 diff 出现全文件重排），末尾保留与原文件相同的单个换行。
    with open(DATA, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"\n已合并 {len(added)} 条：")
    for bucket, e in added:
        print(f"  + [{bucket}] {e['name']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
