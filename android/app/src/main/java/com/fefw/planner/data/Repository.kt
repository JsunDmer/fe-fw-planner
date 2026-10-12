package com.fefw.planner.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Instant
import kotlin.random.Random

@Serializable
data class RecruitState(val planned: Boolean = false, val done: Boolean = false)

@Serializable
data class PathState(
    val srcPathIndex: Int = 0,
    val custom: List<String> = emptyList(),
    val doneSteps: Map<String, Boolean> = emptyMap()
)

@Serializable
data class UiState(
    val tab: String = "recruit"
)

@Serializable
data class SlotData(
    val route: String = "",
    /**
     * 按主角线分区保存挖角勾选：routeKey -> charId -> 状态。
     * 每条线的勾选互相独立，切换线路时不会把 A 线的勾选带到 B 线（修复过「切线路串线」的 bug）。
     */
    val recruitsByRoute: Map<String, Map<String, RecruitState>> = emptyMap(),
    /**
     * 旧存档兼容字段：早期版本按 charId 扁平存储勾选（无线路维度）。
     * 仅用于读取旧数据，由 Repository.normalize() 迁移进 recruitsByRoute[route]；迁移后恒为空，不再写入。
     */
    val recruits: Map<String, RecruitState> = emptyMap(),
    val paths: Map<String, PathState> = emptyMap(),
    /**
     * 凯伊线坐骑选择：key = "charId::tier" → 坐骑名。
     * 仅凯伊线生效，用于把坐骑成长叠加进角色/职业成长显示。
     */
    val stepMounts: Map<String, String> = emptyMap(),
    val notes: Map<String, String> = emptyMap(),
    val onboarded: Boolean? = null,
    val ui: UiState = UiState()
)

@Serializable
data class Slot(
    val id: String = "",
    val name: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val data: SlotData = SlotData()
)

@Serializable
data class Store(val activeId: String? = null, val slots: List<Slot> = emptyList())

fun SlotData.recruitsFor(route: String): Map<String, RecruitState> = recruitsByRoute[route].orEmpty()

sealed interface ImportResult {
    data class All(val slots: List<Slot>) : ImportResult
    data class Single(val name: String, val data: SlotData) : ImportResult
    data class Invalid(val message: String) : ImportResult
}

@Serializable
private data class ExportSlot(
    val app: String = "fefw-planner",
    val version: Int = 2,
    val kind: String = "slot",
    val name: String = "",
    val data: SlotData = SlotData()
)

@Serializable
private data class ExportAll(
    val app: String = "fefw-planner",
    val version: Int = 2,
    val kind: String = "all",
    val slots: List<Slot> = emptyList()
)

@Serializable
private data class ImportSingle(val name: String? = null, val data: SlotData? = null)

class Repository(context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val prettyJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val file = File(context.filesDir, "planner.json")

    fun load(defaultRoute: String): Store {
        val raw = runCatching {
            if (file.exists()) json.decodeFromString(Store.serializer(), file.readText()) else null
        }.getOrNull()

        if (raw == null || raw.slots.isEmpty()) {
            val slot = newSlot(defaultRoute, "我的规划", 1)
            return Store(activeId = slot.id, slots = listOf(slot))
        }
        val migrated = raw.slots.map { it.copy(data = normalize(it.data, defaultRoute)) }
        val active = if (migrated.any { it.id == raw.activeId }) raw.activeId else migrated.first().id
        return Store(activeId = active, slots = migrated)
    }

    fun save(store: Store) {
        runCatching { file.writeText(json.encodeToString(Store.serializer(), store)) }
    }

    fun newSlot(defaultRoute: String, name: String?, index: Int): Slot {
        val now = nowIso()
        return Slot(
            id = uid(),
            name = name?.takeIf { it.isNotBlank() } ?: "存档 $index",
            createdAt = now,
            updatedAt = now,
            data = SlotData(route = defaultRoute, onboarded = false, ui = UiState())
        )
    }

    fun exportSlot(slot: Slot): String =
        prettyJson.encodeToString(
            ExportSlot.serializer(),
            ExportSlot(name = slot.name, data = slot.data)
        )

    fun exportAll(store: Store): String =
        prettyJson.encodeToString(ExportAll.serializer(), ExportAll(slots = store.slots))

    fun parseImport(text: String): ImportResult {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: return ImportResult.Invalid("文件格式无法识别")
        val kind = root["kind"]?.jsonPrimitive?.contentOrNull
        if (kind == "all") {
            val all = runCatching { json.decodeFromJsonElement(ExportAll.serializer(), root) }.getOrNull()
            val slots = all?.slots.orEmpty()
            if (slots.isEmpty()) return ImportResult.Invalid("文件缺少可达存档")
            return ImportResult.All(slots)
        }
        val wrapper = runCatching { json.decodeFromJsonElement(ImportSingle.serializer(), root) }.getOrNull()
        val data = wrapper?.data
            ?: runCatching { json.decodeFromJsonElement(SlotData.serializer(), root) }.getOrNull()
            ?: return ImportResult.Invalid("文件格式无法识别")
        return ImportResult.Single(wrapper?.name ?: "未命名", data)
    }

    fun uid(): String =
        "s" + System.currentTimeMillis().toString(36) + Random.nextInt(0x1000, 0xFFFF).toString(36)

    fun nowIso(): String = Instant.now().toString()

    companion object {
        fun normalize(d: SlotData, defaultRoute: String): SlotData {
            val route = d.route.ifBlank { defaultRoute }
            val tab = if (d.ui.tab == "settings" || d.ui.tab.isBlank()) "recruit" else d.ui.tab
            // 旧存档的扁平 recruits 没有线路维度，归入其当前主角线，升级后不丢勾选；之后该字段不再写入。
            val byRoute = if (d.recruitsByRoute.isEmpty() && d.recruits.isNotEmpty()) {
                mapOf(route to d.recruits)
            } else {
                d.recruitsByRoute
            }
            return d.copy(
                route = route,
                recruitsByRoute = byRoute,
                recruits = emptyMap(),
                onboarded = d.onboarded ?: true,
                ui = d.ui.copy(tab = tab)
            )
        }
    }
}
