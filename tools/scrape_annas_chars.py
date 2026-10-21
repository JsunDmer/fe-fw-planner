#!/usr/bin/env python3
"""抓取 annasnotebook 角色详情页的「可学特技」表，补全 characters.json 的 learn 字段。

数据来源：
  - https://www.annasnotebook.net/characters/<slug>/  角色详情页
    每个页面有一张 table.ab-t.skl-t（thead「熟练度」），按武器/魔术类别（剑术/枪术/…
    黑魔术/白魔术/指挥术…）逐行列出该角色在该类别各熟练度等级（D/D+/C/…/S+）可学的技能。
    每个技能节点形如：
      <span class="skl-r"><b class="skl-rk">D</b>
        <a class="sk sk-n" href="../../skills/art-16/"
           data-tt="燕返" data-ts="战技　剑术 D 学会" data-tb="对飞行有效">…燕返</a></span>
    data-ts 同时编码了「类别」(战技/特技) 与「类别名 等级」；data-tb 是效果说明。

为什么需要它：本站 characters.json 的 learn 只是一份残缺的精选（如迪托利希仅 ~34 条），
站点每角色约 90 条。learn.ranks 是「技能名 → {r: 熟练度, sk: 类别}」，learn.skills/arts/magic
是按类别分桶的名称列表。三者可完全由 skl-t 表重建。

用法：
  python3 tools/scrape_annas_chars.py             # dry-run：打印每个角色的缺失统计，不改文件
  python3 tools/scrape_annas_chars.py --apply      # 用站点数据覆盖/补全 learn
  python3 tools/scrape_annas_chars.py --offline    # 用本地缓存（.cache_annas/chars/）不联网
  python3 tools/scrape_annas_chars.py --only dietrich,theodora   # 只处理指定 slug（调试用）

合并原则（幂等）：
  - 以站点 skl-t 表为准重建 learn：skills/arts/magic 三个列表按类别重排、去重保序，
    ranks 覆盖为站点解析结果；
  - 站点是权威且可复现（--offline 重跑 byte-identical），因此是「补齐」而非「只增不减」；
  - 与站点 name 匹配不上的角色跳过并报警，绝不臆造。
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
CHARS = os.path.join(ROOT, "data", "characters.json")
SKILLS = os.path.join(ROOT, "data", "skills.json")
CACHE = os.path.join(ROOT, ".cache_annas", "chars")

SITE = "https://www.annasnotebook.net"

# 角色页 slug（取自 /characters/stats/ 的 64 个链接，去掉 stats/compare 等非角色项）。
# 站点按 slug 建页，没有稳定的名字→slug 索引可用，故内联；新增角色时需同步补这里。
SLUGS = [
    "dietrich", "talimun", "eshmel", "ultand", "anatolia", "theodora", "sirocco",
    "ninae", "cai", "leda", "alexandra", "orchel", "mu", "bertrand", "gaitz",
    "seteth", "ursula", "catania", "bonaventure", "hong-hua", "simon", "sha-lan",
    "centurio", "olympia", "troy", "jasmine", "creek", "jester", "esmeralda",
    "mikaela", "lilian", "halvin", "guzran", "tialla", "fabio", "diego", "loretta",
    "yang-jie", "nathan", "tobias", "nezha", "io", "peppe", "noctula", "nydine",
    "inyoni", "peter", "dante", "lysander", "fianna", "tahonia", "buccar", "sofia",
    "benditz", "kiroc", "dadao", "nuzzuo", "ludia", "goliath", "zarcone", "klapka",
    "aswan", "majide",
]

# 站点的魔术类别（用于把技能分到 learn.magic，而非 learn.skills）。
MAGIC_CATS = {"黑魔术", "白魔术"}


def fetch(url: str, dest: str, force: bool) -> str:
    os.makedirs(CACHE, exist_ok=True)
    if os.path.exists(dest) and not force:
        return open(dest, encoding="utf-8").read()
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    raw = urllib.request.urlopen(req, timeout=90).read()
    text = raw.decode("utf-8", "replace")
    open(dest, "w", encoding="utf-8").write(text)
    return text


def strip_tags(s: str) -> str:
    return re.sub(r"<[^>]+>", "", s).strip()


def _attr(tag: str, name: str) -> str:
    m = re.search(rf'{name}="([^"]*)"', tag)
    return html.unescape(m.group(1)) if m else ""


def parse_char_page(page: str) -> dict:
    """解析角色详情页 → {name, skills:[{category,badge,rank,name,kind,cond,effect}]}。

    只取 table.ab-t.skl-t（「熟练度」表）；其他表（能力数据/专属/配装）不在此函数处理。
    """
    name_m = re.search(r"<h1[^>]*>([^<]+)</h1>", page)
    name = html.unescape(name_m.group(1)).strip() if name_m else ""

    tbl_m = re.search(r'<table class="ab-t skl-t">(.*?)</table>', page, re.S)
    if not tbl_m:
        return {"name": name, "skills": []}
    body = tbl_m.group(1)

    out = []
    # 每一行 = 一个类别：<th class="pf-h">…类别…</th><td class="skl-seq">…技能…</td>
    for row in re.findall(r"<tr>(.*?)</tr>", body, re.S):
        th_m = re.search(r'<th class="pf-h">(.*?)</th>', row, re.S)
        if not th_m:
            continue
        th = th_m.group(1)
        badge_m = re.search(r'class="pf pf-[sw]">([^<]+)<', th)
        badge = badge_m.group(1).strip() if badge_m else ""
        # 类别名 = th 去标签后去掉尾部的擅长/不擅长徽标文字
        cat = strip_tags(th)
        if badge:
            cat = cat[: -len(badge)].strip()
        td_m = re.search(r'<td class="skl-seq">(.*?)</td>', row, re.S)
        if not td_m:
            continue
        for node in re.findall(r'<span class="skl-r">(.*?)</span>\s*(?=<span|$)', td_m.group(1), re.S):
            rk_m = re.search(r'<b class="skl-rk">([^<]+)</b>', node)
            a_m = re.search(r"<a\b[^>]*>", node)
            if not rk_m or not a_m:
                continue
            a = a_m.group(0)
            ts = _attr(a, "data-ts")  # 形如「战技　剑术 D 学会」「特技　白魔术 C+ 学会」
            parts = ts.split("　")
            kind = parts[0].strip() if parts else ""
            out.append({
                "category": cat,
                "badge": badge,
                "rank": rk_m.group(1).strip(),
                "name": _attr(a, "data-tt"),
                "kind": kind,
                "cond": ts,
                "effect": _attr(a, "data-tb"),
            })
    return {"name": name, "skills": out}


def build_learn(skills: list[dict]) -> dict:
    """站点技能节点列表 → learn{skills,arts,magic,ranks}。

    分桶规则：
      - 类别属黑/白魔术 → magic；
      - kind==「战技」→ arts；
      - 其余（特技）→ skills。
    同名技能只归第一个出现的位置（站点一页内同一技能可能在不同类别重复，如
    「剑术的奥妙」）。ranks 记录 {name:{r,sk}}，sk 用类别名。
    """
    buckets = {"skills": [], "arts": [], "magic": []}
    ranks: dict[str, dict] = {}
    for s in skills:
        nm = s["name"]
        if not nm:
            continue
        if nm not in ranks:
            ranks[nm] = {"r": s["rank"], "sk": s["category"]}
        if s["category"] in MAGIC_CATS:
            b = "magic"
        elif s["kind"] == "战技":
            b = "arts"
        else:
            b = "skills"
        if nm not in buckets[b]:
            buckets[b].append(nm)
    return {"skills": buckets["skills"], "arts": buckets["arts"],
            "magic": buckets["magic"], "ranks": ranks}


def learn_union(old: dict, new: dict) -> dict:
    """把站点解析结果与现有 learn 取并集（保序：先现有后新增），幂等。

    站点表偶尔缺「专属/配装」类技能（如圣焰技、血印），这些只在现有的 learn 里有；
    直接覆盖会丢数据，故三个列表取并集、ranks 以站点为准再补现有缺项。
    """
    out = {}
    for key in ("skills", "arts", "magic"):
        merged = list(old.get(key, []))
        for nm in new.get(key, []):
            if nm not in merged:
                merged.append(nm)
        out[key] = merged
    ranks = dict(new.get("ranks", {}))
    for nm, v in old.get("ranks", {}).items():
        ranks.setdefault(nm, v)
    out["ranks"] = ranks
    return out


def collect_effects(site: dict) -> tuple[dict, dict]:
    """汇总所有角色页的 {技能名: 效果说明} 与 {技能名: 类别}（首见优先）。"""
    desc: dict[str, str] = {}
    kind: dict[str, str] = {}
    for parsed in site.values():
        for s in parsed["skills"]:
            if not s["name"]:
                continue
            if s["name"] not in kind and s["kind"]:
                kind[s["name"]] = s["kind"]
            if s["effect"] and s["name"] not in desc:
                desc[s["name"]] = s["effect"]
    return desc, kind


def merge_skill_descriptions(skills: dict, chars: list[dict], desc: dict, kind: dict) -> list[str]:
    """把 learn 里引用但 skills.json 没有的技能名，用角色页效果补进对应桶（幂等）。

    这些名字（如「剑术Lv.1」「暗杀拳」）是站点 skl-t 表独有、未收录进 /db/skills/ 总表的
    等级/可学特技；弹框按 name 查 skillDesc，缺了就只显示「暂无说明」，故一并补上。
    已存在的名字一律不动；按 kind 分流（战技→arts，其余→skills），与 build_learn 一致。
    """
    have = set()
    for b in ("blood", "skills", "arts", "magic"):
        for e in skills.get(b, []):
            if e.get("name"):
                have.add(e["name"])
    learn_names = set()
    for c in chars:
        l = c.get("learn", {})
        learn_names |= set(l.get("skills", [])) | set(l.get("arts", [])) | set(l.get("magic", []))

    maxid = {}
    for b, pre in (("skills", "sk"), ("arts", "art")):
        mx = 0
        for e in skills.get(b, []):
            m = re.match(rf"{pre}-(\d+)$", e.get("id", ""))
            if m:
                mx = max(mx, int(m.group(1)))
        maxid[b] = mx

    added = []
    for nm in learn_names:
        if nm in have or nm not in desc:
            continue
        b = "arts" if kind.get(nm) == "战技" else "skills"
        pre = "art" if b == "arts" else "sk"
        maxid[b] += 1
        skills.setdefault(b, []).append({
            "id": f"{pre}-{maxid[b]}", "name": nm, "text": desc[nm],
            "learn": [], "note": "", "kind": kind.get(nm, "技能"), "chars": [], "classes": [],
        })
        have.add(nm)
        added.append(nm)
    return added


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="把补全后的 learn 写回 characters.json")
    ap.add_argument("--offline", action="store_true", help="用本地缓存，不联网")
    ap.add_argument("--force", action="store_true", help="强制重新下载")
    ap.add_argument("--only", default="", help="只处理逗号分隔的 slug（调试用）")
    args = ap.parse_args()

    slugs = [s for s in args.only.split(",") if s] or SLUGS
    chars = json.load(open(CHARS, encoding="utf-8"))
    by_name = {c.get("name"): c for c in chars}

    site = {}
    for slug in slugs:
        cache = os.path.join(CACHE, f"{slug}.html")
        if args.offline and not os.path.exists(cache):
            print(f"offline 缺缓存 {cache}", file=sys.stderr)
            continue
        page = open(cache, encoding="utf-8").read() if args.offline \
            else fetch(f"{SITE}/characters/{slug}/", cache, args.force)
        parsed = parse_char_page(page)
        if parsed["name"]:
            site[parsed["name"]] = parsed

    matched = 0
    unmatched_site = [n for n in site if n not in by_name]
    dist = Counter()
    for nm, parsed in site.items():
        c = by_name.get(nm)
        if not c:
            continue
        matched += 1
        new_learn = build_learn(parsed["skills"])
        dist["站点技能总数"] += sum(len(v) for k, v in new_learn.items() if k != "ranks")
        if args.apply:
            c["learn"] = learn_union(c.get("learn", {}), new_learn)

    print(f"处理 slug {len(slugs)} · 解析出角色 {len(site)} · 匹配本地 {matched}")
    if unmatched_site:
        print(f"站点有、本地无（跳过）：{unmatched_site}")
    print(f"站点技能合计（去重前）：{dist['站点技能总数']}")

    if not args.apply:
        print("\n（dry-run。加 --apply 写回。抽样对比：）")
        for nm in list(site)[:8]:
            c = by_name.get(nm)
            if not c:
                continue
            nl = build_learn(site[nm]["skills"])
            old = c.get("learn", {})
            old_n = len(old.get("skills", [])) + len(old.get("arts", [])) + len(old.get("magic", []))
            new_n = len(nl["skills"]) + len(nl["arts"]) + len(nl["magic"])
            print(f"  {nm:<8} 现 {old_n:>3} → 站点 {new_n:>3}")
        return 0

    with open(CHARS, "w", encoding="utf-8") as f:
        json.dump(chars, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"\n已写回 {CHARS}（{matched} 个角色的 learn 已补全）")

    skills = json.load(open(SKILLS, encoding="utf-8"))
    desc, kind = collect_effects(site)
    added = merge_skill_descriptions(skills, chars, desc, kind)
    if added:
        with open(SKILLS, "w", encoding="utf-8") as f:
            json.dump(skills, f, ensure_ascii=False, indent=1)
            f.write("\n")
    print(f"已补 {len(added)} 条技能说明进 {SKILLS}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
