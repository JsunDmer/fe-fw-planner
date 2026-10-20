package com.fefw.planner.data

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

/** 判断「哪条线最好挖」：先比名声 LV，再比支援 LV，最后比难度；数值越低越省事。 */
fun cmpScore(r: RecruitInfo): Int {
    val rep = r.rep ?: 99
    val sup = r.sup ?: 99
    val diff = DIFF_RANK[r.diff] ?: 9
    return rep * 1000 + sup * 10 + diff
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

/**
 * 「属于某条线」的判定：本线剧情固定出场或可挖角。
 * 用白名单（story/scout）而非「非 no」——先按本线可用性收口，
 * 切换路线后别的线角色（含其它三线主角）就不会残留；与网页版 availableOnRoute 一致。
 */
fun availableOnRoute(c: Character, route: String): Boolean {
    val r = c.recruitByRoute[route] ?: return false
    return r.kind == KIND_STORY || r.kind == KIND_SCOUT
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
