package com.fefw.planner.data

import java.text.Collator
import java.util.Locale

val FALLBACK_ROUTE_KEYS = listOf("凯伊", "迪托利希", "赛奥朵拉", "蕾达")
val CUSTOM_TIERS = listOf("初级", "中级", "上级", "最上级", "神将")
val MOUNT_UNIT_REGEX = Regex("骑兵|飞行")
/** 凯伊线坐骑选择可选类型（按需求排除战象）。 */
val SELECTABLE_MOUNT_TYPES = listOf("马", "天马", "飞龙", "飞鸵")
const val KAI_ROUTE = "凯伊"

/**
 * 职业可用的坐骑类型：按职业名内置坐骑关键词（鸵/龙/天翼）判定，
 * 其余 unitType 含「骑兵」的职业默认为马；战象被排除在规划范围外，返回空列表（不提供选择）。
 */
fun mountTypesForClass(cls: ClassInfo?): List<String> {
    if (cls == null) return emptyList()
    val name = cls.name
    return when {
        name.contains("鸵") -> listOf("飞鸵")
        name.contains("龙") -> listOf("飞龙")
        name.contains("天翼") -> listOf("天马")
        name.contains("战象") -> emptyList()
        cls.unitType?.contains("骑兵") == true -> listOf("马")
        else -> emptyList()
    }
}
val DIFF_RANK = mapOf("低" to 0, "中" to 1, "高" to 2, "超高" to 3)

const val KIND_STORY = "story"
const val KIND_SCOUT = "scout"
const val KIND_NO = "no"

private val zhCollator: Collator = Collator.getInstance(Locale.CHINESE)

fun zhCompare(a: String, b: String): Int = synchronized(zhCollator) { zhCollator.compare(a, b) }

private val zhNameComparator = Comparator<Character> { a, b -> zhCompare(a.name, b.name) }

private val RE_COND_MONEY = Regex("交涉时付[ 　]*([0-9]+)G")
private val RE_COND_ITEM = Regex("交涉时交出[ 　]*(.+?)×([0-9]+)")
private val RE_COND_PARALOGUE = Regex("先通关(.+?)外传")
private val RE_COND_QUEST = Regex("先做完.*?支线「(.+?)」")
private val RE_COND_QUEST_LOOSE = Regex("做完.*?支线「(.+?)」")
private val RE_CHAPTER = Regex("(\\d+)\\D+(\\d+)")

sealed interface ParsedCond {
    data class Money(val amount: Int, val raw: String) : ParsedCond
    data class Item(val name: String, val qty: Int, val raw: String) : ParsedCond
    data class Paralogue(val name: String, val raw: String) : ParsedCond
    data class Quest(val name: String, val raw: String) : ParsedCond
    data class Other(val raw: String) : ParsedCond
}

/**
 * 条件是非结构化中文文本，用一组正则按优先级识别；未命中的原样归入「其它条件」，
 * 保证新增或异常写法不会静默丢失。
 */
fun parseCond(raw: String?): ParsedCond {
    val s = (raw ?: "").trim()
    RE_COND_MONEY.find(s)?.let { return ParsedCond.Money(it.groupValues[1].toIntOrNull() ?: 0, s) }
    RE_COND_ITEM.find(s)?.let {
        return ParsedCond.Item(it.groupValues[1].trim(), it.groupValues[2].toIntOrNull() ?: 0, s)
    }
    RE_COND_PARALOGUE.find(s)?.let { return ParsedCond.Paralogue(it.groupValues[1].trim(), s) }
    (RE_COND_QUEST.find(s) ?: RE_COND_QUEST_LOOSE.find(s))?.let {
        return ParsedCond.Quest(it.groupValues[1].trim(), s)
    }
    return ParsedCond.Other(s)
}

/** 判断「哪条线最好挖」：先比名声 LV，再比支援 LV，最后比难度；数值越低越省事。 */
fun cmpScore(r: RecruitInfo): Int {
    val rep = r.rep ?: 99
    val sup = r.sup ?: 99
    val diff = DIFF_RANK[r.diff] ?: 9
    return rep * 1000 + sup * 10 + diff
}

/** 章节串（如「1部3章2月」）取前两段数字，供固定出场排序；无法解析的沉底。 */
fun chapterKey(ch: String?): Pair<Int, Int> {
    val m = RE_CHAPTER.find(ch ?: "") ?: return 99 to 99
    return (m.groupValues[1].toIntOrNull() ?: 99) to (m.groupValues[2].toIntOrNull() ?: 99)
}

fun formatMoney(amount: Int): String = String.format(Locale.US, "%,d", amount)

data class RecruitRow(val char: Character, val r: RecruitInfo?)

fun recruitPool(characters: List<Character>, route: String): List<RecruitRow> =
    characters.filter { !it.late }.map { RecruitRow(it, it.recruitByRoute[route]) }

fun isPlannedOrDone(recruits: Map<String, RecruitState>, id: String): Boolean {
    val r = recruits[id]
    return r?.planned == true || r?.done == true
}

private fun supOf(r: RecruitInfo?): Int = r?.sup ?: 99
private fun repOf(r: RecruitInfo?): Int = r?.rep ?: 99

fun scoutComparator(sort: String): Comparator<RecruitRow> = Comparator { a, b ->
    val primary = when (sort) {
        "sup" -> supOf(a.r) - supOf(b.r)
        "name" -> 0
        else -> repOf(a.r) - repOf(b.r)
    }
    if (primary != 0) primary else zhCompare(a.char.name, b.char.name)
}

val storyOrderComparator = Comparator<RecruitRow> { a, b ->
    val ka = chapterKey(a.r?.ch)
    val kb = chapterKey(b.r?.ch)
    val byPart = ka.first.compareTo(kb.first)
    if (byPart != 0) byPart
    else {
        val byChap = ka.second.compareTo(kb.second)
        if (byChap != 0) byChap else zhCompare(a.char.name, b.char.name)
    }
}

fun groupScoutsByRep(rows: List<RecruitRow>): List<Pair<String, List<RecruitRow>>> {
    val groups = LinkedHashMap<String, MutableList<RecruitRow>>()
    rows.forEach { x ->
        val key = x.r?.rep?.toString() ?: "?"
        groups.getOrPut(key) { mutableListOf() }.add(x)
    }
    return groups.entries
        .sortedBy { it.key.toIntOrNull() ?: 99 }
        .map { e ->
            e.key to e.value.sortedWith(Comparator { a, b ->
                val primary = supOf(a.r) - supOf(b.r)
                if (primary != 0) primary else zhCompare(a.char.name, b.char.name)
            })
        }
}

fun bestScoutScore(c: Character, routeKeys: List<String>): Int? {
    var best: Int? = null
    routeKeys.forEach { k ->
        val r = c.recruitByRoute[k]
        if (r != null && r.kind == KIND_SCOUT) {
            val sc = cmpScore(r)
            if (best == null || sc < best!!) best = sc
        }
    }
    return best
}

fun hasScout(c: Character, routeKeys: List<String>): Boolean =
    routeKeys.any { c.recruitByRoute[it]?.kind == KIND_SCOUT }

class ItemNeed(val name: String) {
    var qty: Int = 0
    val chars: MutableList<String> = mutableListOf()
}

data class MoneyRow(val name: String, val amount: Int)
data class OtherRow(val cond: String, val char: String)

data class AggResult(
    val moneyTotal: Int,
    val moneyRows: List<MoneyRow>,
    val items: LinkedHashMap<String, ItemNeed>,
    val paralogues: LinkedHashMap<String, List<String>>,
    val quests: LinkedHashMap<String, List<String>>,
    val others: List<OtherRow>
)

/** 只统计「已计划招募」且该条线可挖的角色，聚合所有附加条件。 */
fun aggregateConditions(chars: List<Character>, route: String): AggResult {
    var money = 0
    val moneyRows = mutableListOf<MoneyRow>()
    val items = LinkedHashMap<String, ItemNeed>()
    val paralogues = LinkedHashMap<String, MutableList<String>>()
    val quests = LinkedHashMap<String, MutableList<String>>()
    val others = mutableListOf<OtherRow>()

    chars.forEach { c ->
        val r = c.recruitByRoute[route]
        if (r == null || r.kind != KIND_SCOUT) return@forEach
        r.cond.forEach { raw ->
            when (val p = parseCond(raw)) {
                is ParsedCond.Money -> {
                    money += p.amount
                    moneyRows.add(MoneyRow(c.name, p.amount))
                }
                is ParsedCond.Item -> items.getOrPut(p.name) { ItemNeed(p.name) }.also {
                    it.qty += p.qty
                    it.chars.add(c.name)
                }
                is ParsedCond.Paralogue -> paralogues.getOrPut(p.name) { mutableListOf() }.add(c.name)
                is ParsedCond.Quest -> quests.getOrPut(p.name) { mutableListOf() }.add(c.name)
                is ParsedCond.Other -> others.add(OtherRow(p.raw, c.name))
            }
        }
    }
    return AggResult(
        moneyTotal = money,
        moneyRows = moneyRows,
        items = items,
        paralogues = LinkedHashMap(paralogues.mapValues { it.value.toList() }),
        quests = LinkedHashMap(quests.mapValues { it.value.toList() }),
        others = others
    )
}

fun repThresholds(chars: List<Character>, route: String): List<Pair<String, List<String>>> {
    val groups = LinkedHashMap<String, MutableList<String>>()
    chars.forEach { c ->
        val r = c.recruitByRoute[route] ?: return@forEach
        val key = r.rep?.toString() ?: "?"
        groups.getOrPut(key) { mutableListOf() }.add(c.name)
    }
    return groups.entries
        .sortedBy { it.key.toIntOrNull() ?: 99 }
        .map { it.key to it.value.toList() }
}

fun resolveSteps(char: Character, pathState: PathState?, classesById: Map<String, ClassInfo>): List<Step> {
    val idx = pathState?.srcPathIndex ?: 0
    if (idx == -1) return customSteps(pathState?.custom ?: emptyList(), classesById)
    return char.paths.getOrNull(idx)?.steps ?: emptyList()
}

fun customSteps(ids: List<String>, classesById: Map<String, ClassInfo>): List<Step> =
    ids.mapIndexedNotNull { i, id ->
        val cls = classesById[id] ?: return@mapIndexedNotNull null
        Step(tier = CUSTOM_TIERS.getOrNull(i) ?: cls.tier, name = cls.name, classId = id)
    }

fun growthInline(g: Growth?, stats: List<List<String>>): String {
    if (g == null) return "—"
    val parts = stats.mapNotNull { s ->
        val key = s.getOrNull(0) ?: return@mapNotNull null
        val label = s.getOrNull(1) ?: key
        val v = g.value(key) ?: return@mapNotNull null
        if (v == 0) null else label + (if (v > 0) "+" else "") + v
    }
    return parts.joinToString(" ").ifBlank { "—" }
}

/** 单格成长数据：value 为有效成长率，bonus 单列供 UI 标注职业加成。 */
data class GrowthCell(val key: String, val label: String, val value: Int, val bonus: Int)

/**
 * 表格单格：有效成长 = 角色基础成长 + 职业加成 + 额外(坐骑)加成。
 * bonus 为职业+坐骑的总加成，单独保留供 UI 标注与高亮。
 */
fun growthCells(
    base: Growth?,
    cls: Growth?,
    stats: List<List<String>>,
    extra: Growth? = null
): List<GrowthCell> =
    stats.map { s ->
        val key = s.getOrNull(0).orEmpty()
        val label = s.getOrNull(1) ?: key
        val bonus = (cls?.value(key) ?: 0) + (extra?.value(key) ?: 0)
        GrowthCell(key, label, (base?.value(key) ?: 0) + bonus, bonus)
    }

/** 坐骑成长字符串 → Growth。格式如 "HP5 速5 技5 防5 魔防5"；魔防必须先于魔匹配。 */
private val RE_MOUNT_GROWTH = Regex("(魔防|HP|力|魔|速|技|防|幸|魅)(\\d+)")

fun parseMountGrowth(raw: String?): Growth {
    if (raw.isNullOrBlank()) return Growth()
    var hp = 0; var str = 0; var mag = 0; var spd = 0; var dex = 0
    var def = 0; var res = 0; var lck = 0; var cha = 0
    RE_MOUNT_GROWTH.findAll(raw).forEach { m ->
        val n = m.groupValues[2].toIntOrNull() ?: 0
        when (m.groupValues[1]) {
            "HP" -> hp = n
            "力" -> str = n
            "魔" -> mag = n
            "速" -> spd = n
            "技" -> dex = n
            "防" -> def = n
            "魔防" -> res = n
            "幸" -> lck = n
            "魅" -> cha = n
        }
    }
    return Growth(hp, str, mag, spd, dex, def, res, lck, cha)
}

/** 逐项相加。 */
fun addGrowth(a: Growth, b: Growth): Growth = Growth(
    a.hp + b.hp, a.str + b.str, a.mag + b.mag, a.spd + b.spd, a.dex + b.dex,
    a.def + b.def, a.res + b.res, a.lck + b.lck, a.cha + b.cha
)

/** 坐骑选择存储键：每角色每档位一个坐骑。 */
fun stepMountKey(charId: String, tier: String): String = "$charId::$tier"

/** 取某坐骑的成长；名字为空或找不到返回 null。 */
fun mountGrowthOf(mounts: List<Mount>, name: String?): Growth? =
    name?.takeIf { it.isNotBlank() }?.let { n ->
        mounts.firstOrNull { it.name == n }?.let { parseMountGrowth(it.growth) }
    }

fun statLabel(stats: List<List<String>>, key: String): String =
    stats.firstOrNull { it.firstOrNull() == key }?.getOrNull(1) ?: key

fun notLateSortedByName(characters: List<Character>): List<Character> =
    characters.filter { !it.late }.sortedWith(zhNameComparator)
