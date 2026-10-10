package com.fefw.planner.data

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Meta(
    val site: String = "",
    val game: String = "",
    val generated: String = "",
    val note: String = ""
)

@Serializable
data class Growth(
    val hp: Int = 0,
    val str: Int = 0,
    val mag: Int = 0,
    val spd: Int = 0,
    val dex: Int = 0,
    val def: Int = 0,
    val res: Int = 0,
    val lck: Int = 0,
    val cha: Int = 0
) {
    fun value(key: String): Int? = when (key) {
        "hp" -> hp
        "str" -> str
        "mag" -> mag
        "spd" -> spd
        "dex" -> dex
        "def" -> def
        "res" -> res
        "lck" -> lck
        "cha" -> cha
        else -> null
    }
}

@Serializable
data class Route(
    val key: String,
    val color: String = "#3d9ac0",
    val feature: String = "",
    val unlocks: List<String> = emptyList(),
    val fameSource: String = "",
    val note: String = ""
)

@Serializable
data class PersonalSkill(
    val name: String = "",
    val text: String = ""
)

@Serializable
data class Step(
    val tier: String = "",
    val name: String = "",
    val classId: String = ""
)

@Serializable
data class PathReco(
    val src: String = "",
    val steps: List<Step> = emptyList()
)

@Serializable
data class RecruitInfo(
    val route: String = "",
    val kind: String = "no",
    val rep: Int? = null,
    val sup: Int? = null,
    val diff: String? = null,
    val cond: List<String> = emptyList(),
    val ch: String? = null,
    val how: String? = null,
    val gift: Boolean = false,
    val hero: Boolean = false,
    val minCh: Int? = null,
    val minAt: String? = null,
    val hard: String? = null
)

@Serializable
data class Character(
    val id: String,
    val name: String,
    val sex: String? = null,
    val faction: String? = null,
    val startClass: String? = null,
    val portrait: String = "",
    val growth: Growth = Growth(),
    val growthTotal: Int = 0,
    val baseLevel: Int = 0,
    val isHero: Boolean = false,
    val late: Boolean = false,
    val recruit: String = "",
    val joinChapter: List<String> = emptyList(),
    val personalSkill: PersonalSkill? = null,
    val paths: List<PathReco> = emptyList(),
    val recruitByRoute: Map<String, RecruitInfo> = emptyMap()
)

@Serializable
data class ClassInfo(
    val id: String,
    val name: String,
    val tier: String = "",
    val tierKey: String = "",
    val family: String? = null,
    val unitType: String? = null,
    val requirement: String = "",
    val unlock: String? = null,
    val move: Int = 0,
    val growth: Growth = Growth(),
    val growthTotal: Int = 0,
    val innate: List<String> = emptyList(),
    val mastery: String = "",
    val usableSkills: List<String> = emptyList(),
    val icon: String = ""
)

@Serializable
data class Pick(
    val charId: String = "",
    val name: String = "",
    val trait: String = "",
    val note: String = ""
)

@Serializable
data class Recommend(
    val group: String = "",
    val classId: String = "",
    val className: String = "",
    val tag: String = "",
    val desc: String = "",
    val color: String = "",
    val picks: List<Pick> = emptyList(),
    val footer: String = ""
)

@Serializable
data class Mount(
    val name: String,
    val tentative: Boolean = false,
    val type: String = "",
    val bonus: String = "",
    val food: String = "",
    val growth: String = "",
    val where: String = "",
    val obtain: String = "",
    val skills: List<String> = emptyList(),
    val levels: List<List<String>> = emptyList()
)

@Serializable
data class Material(
    val id: String = "",
    val name: String = "",
    val kind: String = "",
    val cat: String = "",
    val price: String = "",
    val text: String = "",
    val obtain: List<String> = emptyList(),
    val obtainGW: List<String> = emptyList(),
    val note: String = ""
)

@Serializable
data class Guides(
    @SerialName("g-hero") val hero: String = "",
    @SerialName("g-fame") val fame: String = "",
    @SerialName("g-scout") val scout: String = ""
)

@Serializable
data class FeData(
    val meta: Meta = Meta(),
    val stats: List<List<String>> = emptyList(),
    val routes: List<Route> = emptyList(),
    val recommendedOrder: List<String> = emptyList(),
    val routeKeys: List<String> = emptyList(),
    val characters: List<Character> = emptyList(),
    val classes: List<ClassInfo> = emptyList(),
    val recommend: List<Recommend> = emptyList(),
    val mounts: List<Mount> = emptyList(),
    val materials: Map<String, Material> = emptyMap(),
    val guides: Guides = Guides()
)

object FeDataLoader {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Volatile
    private var cached: FeData? = null

    val decodeJson: Json get() = json

    fun load(context: Context): FeData {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val text = context.assets.open("fe_data.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val parsed = json.decodeFromString(FeData.serializer(), text)
            cached = parsed
            return parsed
        }
    }
}
