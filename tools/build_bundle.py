#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Build src/bundle.js  ->  window.FE_DATA = {...}
Source: data/*.json (mirrored from cn.fire-emblem-fw.site) + assets/ images.
Ports the site's own scoutByRoute / parseScout logic so the planner matches the wiki exactly.
Run:  python3 tools/build_bundle.py
"""
import json, os, re, base64, datetime

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "data")
ASSETS = os.path.join(ROOT, "assets")
OUT = os.path.join(ROOT, "src", "bundle.js")

def load(n):
    with open(os.path.join(DATA, n + ".json"), encoding="utf-8") as f:
        return json.load(f)


# 说明「为什么」按此顺序合并：同名技能可能同时存在于「特技/战技/魔法/血印」，
# 而职业的 mastery/innate 指向的是「特技」表，故把 skills 放最后，命中同名时以特技说明为准。
def skill_desc_map():
    sk = load("skills")
    out = {}
    for cat in ("arts", "magic", "blood", "skills"):
        for x in sk.get(cat, []):
            n = (x.get("name") or "").strip()
            t = (x.get("text") or "").strip()
            if n and t:
                out[n] = t
    return out

def data_uri(relpath):
    if not relpath:
        return ""
    parts = relpath.split("/")
    sub = parts[1] if len(parts) > 2 else ""
    fn = parts[-1]
    local = os.path.join(ASSETS, sub, fn)
    if not os.path.exists(local):
        return ""
    ext = fn.rsplit(".", 1)[-1].lower()
    mime = {"webp": "image/webp", "png": "image/png", "jpg": "image/jpeg", "jpeg": "image/jpeg"}.get(ext, "image/png")
    with open(local, "rb") as f:
        b = base64.b64encode(f.read()).decode()
    return f"data:{mime};base64,{b}"

# recruit logic ported from app.js so the planner matches the wiki exactly
ROUTES = ["凯伊", "迪托利希", "赛奥朵拉", "蕾达"]

def fix_txt(s):
    s = str(s or "")
    return re.sub(r"\s+", " ", s.replace("完成完成", "完成").replace("塔利穆恩", "谭利穆恩")
                  .replace("艾丝梅拉达", "艾丝梅拉尔达").replace("*", "×")).strip()

def chap(s):
    m = re.search(r"第(\d)部.*?第(\S+?)章", str(s or ""))
    return f"{m.group(1)}部{m.group(2)}章" if m and "？" not in m.group(2) else ""

def parse_scout(line):
    rep = int((re.search(r"名声LV(\d+)", line) or [0, 0])[1]) if re.search(r"名声LV(\d+)", line) else 0
    sup = int((re.search(r"支援LV(\d)", line) or [0, 0])[1]) if re.search(r"支援LV(\d)", line) else 0
    md = re.search(r"难度\s*(超高|高|中|低)", line)
    diff = md.group(1) if md else ""
    rest = re.sub(r"支援LV\d", "", line)
    rest = re.sub(r"名声LV\d+", "", rest)
    rest = rest.replace("需交涉", "")
    rest = re.sub(r"[（(]\s*难度\s*(超高|高|中|低)\s*[）)]", "", rest)
    extras = [fix_txt(x) for x in re.split(r"[、|｜]", rest)]
    extras = [x for x in extras if x and not re.fullmatch(r"[：:]", x)]
    return {"rep": rep, "sup": sup, "diff": diff, "cond": extras}

def late_join(c):
    if c.get("part1NoScout"):
        return True
    sj = c.get("storyJoin") or {}
    if any(not k.endswith("篇") for k in sj.keys()):
        return True
    jc = (c.get("joinChapter") or [""])[0]
    return bool(re.match(r"^(序幕|第[23]部)", jc))

def recruit_by_route(c):
    num = int(c["id"].split("-")[1])
    is_hero = num in (4, 5, 6, 7)
    scout = c.get("scoutCond") or []
    notes = [fix_txt(x) for x in scout if isinstance(x, str) and not re.match(r"^(凯伊|迪托利希|赛奥朵拉|蕾达)：", x)]
    jc = c.get("joinChapter") or []
    jh = c.get("joinHow") or []
    four = len(jc) == 4
    out = {}
    for i, r in enumerate(ROUTES):
        e = {"route": r}
        if is_hero:
            if c["name"] == r:
                e.update(kind="story", ch="1部1章", hero=True)
            else:
                e.update(kind="no", hero=True)
            out[r] = e
            continue
        obj = next((x for x in scout if isinstance(x, dict) and x.get("route") == r), None)
        line = next((x for x in scout if isinstance(x, str) and x.startswith(r + "：")), None)
        ch = chap(jc[i]) if four else ""
        how = jh[i] if (four and len(jh) == 4) else ""
        if re.fullmatch(r"？?", how or "") or re.search(r"自动加入", how or ""):
            how = ""
        how = fix_txt(re.sub(r"需满足挖角条件", "", how).replace("满足挖角条件后加入", "").replace("完成剧情送礼并挖角后加入", ""))
        gift = bool(four and re.search(r"剧情送礼", jh[i] if i < len(jh) else ""))
        w = ((obj or {}).get("when") or "")
        w = re.sub(r"^(\d)部(\d+)章(\d+月)$", r"\1部\2章 · \3出现", w)
        if obj:
            e.update(kind="scout", rep=obj.get("rep", 0), sup=obj.get("sup", 0), diff=obj.get("diff", ""),
                     cond=[*(obj.get("cond") or []), *notes],
                     ch=(("1部%s章%s起可挖" % (obj["minCh"], ("（%s）" % re.sub(r"^(\d+)/(\d+)$", r"\1月\2日", obj["minAt"])) if obj.get("minAt") else "")) if obj.get("minCh") else (w or ch)),
                     how="", gift=False, minCh=obj.get("minCh", 0), minAt=obj.get("minAt", ""), hard=obj.get("hard", ""))
        elif line:
            p = parse_scout(line[len(r) + 1:])
            e.update(kind="scout", rep=p["rep"], sup=p["sup"], diff=p["diff"], cond=[*p["cond"], *notes],
                     ch=(w or ch), how=how, gift=gift)
        elif (c.get("storyJoin") or {}).get(r + "篇"):
            e.update(kind="story", ch=c["storyJoin"][r + "篇"])
        else:
            e.update(kind="no")
        out[r] = e
    return out

def build(image_ref=data_uri):
    """Assemble the FE_DATA bundle dict. `image_ref(relpath)` maps a source
    image path (e.g. "img/chr/1.webp") to whatever the caller needs — a data
    URI for the web bundle, or an Android asset path. Kept as a parameter so
    the web and android builds share one source of truth for derived fields."""
    chars = load("characters")
    classes = load("classes")
    recommend = load("recommend")
    misc = load("misc")
    items = load("items")
    guides = {g["id"]: g for g in load("guides")}

    materials = {}
    for x in (items.get("items") or []):
        materials[x["name"]] = {"id": x.get("id", ""), "name": x["name"], "kind": "item",
                                "cat": x.get("cat", ""), "price": x.get("price", ""), "text": x.get("text", ""),
                                "obtain": x.get("obtain") or [], "obtainGW": x.get("obtainGW") or [], "note": x.get("note", "")}
    for x in (items.get("weapons") or []):
        materials[x["name"]] = {"id": x.get("id", ""), "name": x["name"], "kind": "weapon",
                                "cat": x.get("type", "武器"), "price": x.get("price", ""), "text": x.get("text", ""),
                                "obtain": x.get("obtain") or [], "obtainGW": x.get("obtainGW") or [], "note": x.get("note", "")}

    out_chars = []
    for c in chars:
        rec = {
            "id": c["id"], "name": c["name"], "sex": c.get("sex", ""), "faction": c.get("faction", ""),
            "startClass": c.get("startClass", ""), "tentative": c.get("tentative", False),
            "portrait": image_ref(c.get("portrait", "")),
            "growth": c.get("growth", {}), "growthTotal": c.get("growthTotal", 0),
            "baseLevel": c.get("baseLevel"), "baseStats": c.get("baseStats"),
            "skillsGood": c.get("skillsGood", []), "personalSkill": c.get("personalSkill"),
            "bloodMarks": c.get("bloodMarks", []), "skills": c.get("skills", []),
            "recruit": c.get("recruit", ""), "joinChapter": c.get("joinChapter", []),
            "joinHow": c.get("joinHow", []), "scoutCondRaw": c.get("scoutCond", []),
            "storyJoin": c.get("storyJoin", {}),
            "late": late_join(c), "isHero": int(c["id"].split("-")[1]) in (4, 5, 6, 7),
            "paths": c.get("paths", []), "learn": c.get("learn", {}),
            "lvSkills": c.get("lvSkills", []), "recruitByRoute": recruit_by_route(c),
        }
        out_chars.append(rec)

    out_classes = []
    for c in classes:
        out_classes.append({
            "id": c["id"], "name": c["name"], "tier": c.get("tier"), "tierKey": c.get("tierKey"),
            "family": c.get("family"), "unitType": c.get("unitType"), "requirement": c.get("requirement"),
            "unlock": c.get("unlock"), "move": c.get("move"), "growth": c.get("growth", {}),
            "growthTotal": c.get("growthTotal", 0), "innate": c.get("innate", []), "mastery": c.get("mastery"),
            "usableSkills": c.get("usableSkills", []), "baseStats": c.get("baseStats"),
            "icon": image_ref(c.get("icon", "")),
        })

    routes = [
        {"key": "凯伊", "color": "#3d9ac0", "feature": "助人任务、骑乘动物的捕获与饲养、指导",
         "unlocks": ["神鸵兵", "驭龙兵", "游唱诗人"], "fameSource": "助人任务（容易赚名声）",
         "note": "路线特色是坐骑：可捕获并饲养骑乘动物，凯伊篇专属解锁神鸵兵/驭龙兵/游唱诗人。"},
        {"key": "迪托利希", "color": "#8a5cc0", "feature": "悬赏屋的挑战书（秘传战技）、刃鸣（魔响石）",
         "unlocks": ["卫士", "游侠", "锻造师"], "fameSource": "悬赏屋委托随名声升级开放",
         "note": "本人战斗力极高，第 3 部仍是主力。"},
        {"key": "赛奥朵拉", "color": "#c05c5c", "feature": "军团兵与计策、战备增强、派兵募兵、车站移动",
         "unlocks": ["铁甲骑兵", "卫士", "驭龙兵"], "fameSource": "战备增强按委托缴纳素材",
         "note": "推荐第一个通关：军团兵提升探索效率。"},
        {"key": "蕾达", "color": "#3fa05c", "feature": "酒馆与地方表演、新商品委托、车站移动",
         "unlocks": ["舞者", "游侠", "游唱诗人"], "fameSource": "地方表演（去指定村镇）",
         "note": "舞者是蕾达线限定职业。"},
    ]
    recommended_order = ["赛奥朵拉", "迪托利希", "凯伊", "蕾达"]

    bundle = {
        "meta": {"site": "https://cn.fire-emblem-fw.site", "game": "火焰纹章·万缕千丝 (Fortunes Weave)",
                 "generated": datetime.date.today().isoformat(), "note": "数据镜像自攻略站，仅供个人游玩规划使用；请勿外链原站资源。"},
        "stats": [["hp", "HP"], ["str", "力量"], ["mag", "魔力"], ["spd", "速度"], ["dex", "技巧"],
                  ["def", "防守"], ["res", "魔防"], ["lck", "幸运"], ["cha", "魅力"]],
        "routes": routes, "recommendedOrder": recommended_order, "routeKeys": ROUTES,
        "characters": out_chars, "classes": out_classes, "recommend": recommend,
        "mounts": misc.get("mounts", []),
        "materials": materials,
        "skillDesc": skill_desc_map(),
        "guides": {k: re.sub(r"<[^>]+>", "", g.get("body", "")) for k, g in guides.items() if k in ("g-scout", "g-hero", "g-fame")},
    }

    return bundle


def main():
    bundle = build()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("/* AUTO-GENERATED by tools/build_bundle.py — do not edit by hand */\n")
        f.write("window.FE_DATA = ")
        json.dump(bundle, f, ensure_ascii=False, separators=(",", ":"))
        f.write(";\n")
    print("wrote", OUT, f"{os.path.getsize(OUT)/1024:.0f} KB")
    from collections import Counter
    k = Counter()
    for c in bundle["characters"]:
        for r in ROUTES:
            k[c["recruitByRoute"][r]["kind"]] += 1
    print("recruit kinds (x4 routes):", dict(k))
    print("chars:", len(bundle["characters"]), "classes:", len(bundle["classes"]),
          "materials:", len(bundle["materials"]), "mounts:", len(bundle["mounts"]))

if __name__ == "__main__":
    main()
