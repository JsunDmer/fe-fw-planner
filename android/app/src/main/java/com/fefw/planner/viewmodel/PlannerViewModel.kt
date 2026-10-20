package com.fefw.planner.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.fefw.planner.data.Character
import com.fefw.planner.data.ClassInfo
import com.fefw.planner.data.FALLBACK_ROUTE_KEYS
import com.fefw.planner.data.FeData
import com.fefw.planner.data.FeDataLoader
import com.fefw.planner.data.ImportResult
import com.fefw.planner.data.PathState
import com.fefw.planner.data.RecruitState
import com.fefw.planner.data.Recommend
import com.fefw.planner.data.Repository
import com.fefw.planner.data.Slot
import com.fefw.planner.data.SlotData
import com.fefw.planner.data.Store
import com.fefw.planner.data.UiState
import com.fefw.planner.data.recruitsFor
import com.fefw.planner.data.stepMountKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

val Store.activeSlot: Slot? get() = slots.firstOrNull { it.id == activeId }
val Store.activeDataOrNull: SlotData? get() = activeSlot?.data

class PlannerViewModel(app: Application) : AndroidViewModel(app) {

    val fe: FeData = FeDataLoader.load(app)
    private val repo = Repository(app)

    val defaultRoute: String =
        fe.recommendedOrder.firstOrNull() ?: fe.routeKeys.firstOrNull() ?: FALLBACK_ROUTE_KEYS.first()

    val classesById: Map<String, ClassInfo> = fe.classes.associateBy { it.id }
    val charsById: Map<String, Character> = fe.characters.associateBy { it.id }
    val recommendByClass: Map<String, List<Recommend>> = fe.recommend.groupBy { it.classId }
    val routeKeys: List<String> = fe.routeKeys.ifEmpty { FALLBACK_ROUTE_KEYS }

    private val _store = MutableStateFlow(repo.load(defaultRoute))
    val store: StateFlow<Store> = _store.asStateFlow()

    private val _tab = MutableStateFlow("class")
    val tab: StateFlow<String> = _tab.asStateFlow()

    private val _rulesOpen = MutableStateFlow(false)
    val rulesOpen: StateFlow<Boolean> = _rulesOpen.asStateFlow()

    private val _showAllPaths = MutableStateFlow(false)
    val showAllPaths: StateFlow<Boolean> = _showAllPaths.asStateFlow()

    private val _locateCharId = MutableStateFlow<String?>(null)
    val locateCharId: StateFlow<String?> = _locateCharId.asStateFlow()

    private val _charsOnlyScout = MutableStateFlow(false)
    val charsOnlyScout: StateFlow<Boolean> = _charsOnlyScout.asStateFlow()

    private val _charsShowLate = MutableStateFlow(false)
    val charsShowLate: StateFlow<Boolean> = _charsShowLate.asStateFlow()

    private val _expandItem = MutableStateFlow<Set<String>>(emptySet())
    val expandItem: StateFlow<Set<String>> = _expandItem.asStateFlow()

    private val _expandClass = MutableStateFlow<Set<String>>(emptySet())
    val expandClass: StateFlow<Set<String>> = _expandClass.asStateFlow()

    private val _customConfirmed = MutableStateFlow<Set<String>>(emptySet())
    val customConfirmed: StateFlow<Set<String>> = _customConfirmed.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        restoreUi()
    }

    fun currentData(): SlotData =
        _store.value.activeDataOrNull ?: SlotData(route = defaultRoute, onboarded = false)

    fun isOnboarded(): Boolean = currentData().onboarded == true

    private fun save(store: Store) {
        _store.value = store
        repo.save(store)
    }

    private fun mutate(block: (SlotData) -> SlotData) {
        val cur = _store.value
        val idx = cur.slots.indexOfFirst { it.id == cur.activeId }
        if (idx < 0) return
        val slot = cur.slots[idx]
        val newSlot = slot.copy(data = block(slot.data), updatedAt = repo.nowIso())
        val slots = cur.slots.toMutableList().also { it[idx] = newSlot }
        save(cur.copy(slots = slots))
    }

    private fun restoreUi() {
        val d = currentData()
        _tab.value = if (d.ui.tab == "recruit") "class" else d.ui.tab
        _rulesOpen.value = false
    }

    fun setTab(tab: String) {
        _tab.value = tab
        mutate { it.copy(ui = it.ui.copy(tab = tab)) }
    }

    fun toggleRules() {
        _rulesOpen.value = !_rulesOpen.value
    }

    fun chooseRoute(key: String, goPlan: Boolean = false) {
        mutate { d ->
            d.copy(
                route = key,
                onboarded = true,
                ui = if (goPlan) d.ui.copy(tab = "class") else d.ui
            )
        }
        if (goPlan) _tab.value = "class"
    }

    /** 四线总览点角色名 → 切到「角色信息」页并滚动定位到该角色（搜索框已移除，改用 locateCharId 通知列表滚动）。 */
    fun openChar(c: Character) {
        // 搜索框已移除，列表只剩 late / onlyScout 两个过滤条件；先重置它们，确保目标角色此刻一定在列表里，
        // 否则滚动定位会因找不到该角色而落空。
        if (c.late) _charsShowLate.value = true
        _charsOnlyScout.value = false
        _locateCharId.value = c.id
        setTab("chars")
    }

    fun togglePlanned(route: String, id: String) {
        mutate { d ->
            val map = d.recruitsFor(route)
            val rec = map[id] ?: RecruitState()
            val next = map + (id to rec.copy(planned = !rec.planned))
            d.copy(recruitsByRoute = d.recruitsByRoute + (route to next))
        }
    }

    fun toggleDone(route: String, id: String) {
        mutate { d ->
            val map = d.recruitsFor(route)
            val rec = map[id] ?: RecruitState()
            val done = !rec.done
            val next = map + (id to rec.copy(done = done, planned = if (done) true else rec.planned))
            d.copy(recruitsByRoute = d.recruitsByRoute + (route to next))
        }
    }

    fun toggleItem(name: String) = _expandItem.update { if (name in it) it - name else it + name }

    fun toggleShowAllPaths() = _showAllPaths.update { !it }

    /**
     * paths 唯一写入口：所有转职痕迹（方案选择 / 自定义 / 进度勾选）都会把当时主角线
     * 记进 PathState.routes，ClassScreen 据此只在留下痕迹的线上露出角色。
     * 绕过本函数直接写 d.paths 会漏记线路，重新引入跨线残留。
     */
    private fun mutatePath(id: String, block: (PathState) -> PathState) {
        mutate { d ->
            val pd = d.paths[id] ?: PathState()
            val next = block(pd)
            val routed = if (d.route.isBlank() || d.route in next.routes) next
            else next.copy(routes = next.routes + d.route)
            d.copy(paths = d.paths + (id to routed))
        }
    }

    fun selectPath(id: String, index: Int) =
        mutatePath(id) { it.copy(srcPathIndex = index) }

    fun setCustomStep(id: String, tierIndex: Int, classId: String) = mutatePath(id) { pd ->
        val custom = pd.custom.toMutableList()
        while (custom.size <= tierIndex) custom.add("")
        custom[tierIndex] = classId
        pd.copy(custom = custom, srcPathIndex = -1)
    }

    fun toggleStepDone(id: String, tier: String) = mutatePath(id) { pd ->
        val done = pd.doneSteps.toMutableMap()
        done[tier] = !(done[tier] ?: false)
        pd.copy(doneSteps = done)
    }

    fun toggleClassInfo(key: String) = _expandClass.update { if (key in it) it - key else it + key }

    fun confirmCustom(id: String) = _customConfirmed.update { it + id }

    fun editCustom(id: String) = _customConfirmed.update { it - id }

    fun setStepMount(charId: String, tier: String, mountName: String) {
        mutate { d ->
            val key = stepMountKey(charId, tier)
            val next = if (mountName.isBlank()) d.stepMounts - key else d.stepMounts + (key to mountName)
            d.copy(stepMounts = next)
        }
    }

    /** 列表完成一次滚动定位后清空，避免重组时反复触发 Scroll。 */
    fun consumeLocate() {
        _locateCharId.value = null
    }

    fun toggleCharsOnlyScout() = _charsOnlyScout.update { !it }

    fun toggleCharsShowLate() = _charsShowLate.update { !it }

    fun switchSlot(id: String) {
        save(_store.value.copy(activeId = id))
        restoreUi()
    }

    fun newSlot() {
        val s = repo.newSlot(defaultRoute, null, _store.value.slots.size + 1)
        save(_store.value.copy(activeId = s.id, slots = _store.value.slots + s))
        restoreUi()
    }

    fun renameSlot(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val slots = _store.value.slots.map { if (it.id == id) it.copy(name = trimmed) else it }
        save(_store.value.copy(slots = slots))
    }

    fun duplicateSlot(id: String) {
        val s = _store.value.slots.firstOrNull { it.id == id } ?: return
        val now = repo.nowIso()
        val copy = s.copy(id = repo.uid(), name = s.name + " 副本", createdAt = now, updatedAt = now)
        save(_store.value.copy(slots = _store.value.slots + copy))
    }

    fun deleteSlot(id: String) {
        if (_store.value.slots.size <= 1) {
            reportError("至少保留一个存档槽")
            return
        }
        val slots = _store.value.slots.filter { it.id != id }
        val activeId = if (_store.value.activeId == id) slots.first().id else _store.value.activeId
        save(_store.value.copy(activeId = activeId, slots = slots))
        restoreUi()
    }

    fun clearCurrent() {
        mutate { SlotData(route = defaultRoute, onboarded = false, ui = UiState()) }
        restoreUi()
    }

    fun exportCurrentJson(): String {
        val slot = _store.value.activeSlot ?: return "{}"
        return repo.exportSlot(slot)
    }

    fun exportAllJson(): String = repo.exportAll(_store.value)

    fun parseImportText(text: String): ImportResult = repo.parseImport(text)

    fun applyImportAll(slots: List<Slot>) {
        val incoming = slots.map { it.copy(id = repo.uid(), data = Repository.normalize(it.data, defaultRoute)) }
        save(_store.value.copy(slots = _store.value.slots + incoming))
    }

    fun applyImportSingle(name: String, data: SlotData, replaceCurrent: Boolean) {
        if (replaceCurrent) {
            mutate { Repository.normalize(data, defaultRoute) }
            restoreUi()
        } else {
            val s = repo.newSlot(defaultRoute, name, _store.value.slots.size + 1)
                .copy(data = Repository.normalize(data, defaultRoute))
            save(_store.value.copy(activeId = s.id, slots = _store.value.slots + s))
            restoreUi()
        }
    }

    fun reportError(message: String) {
        _error.value = message
    }

    fun dismissError() {
        _error.value = null
    }
}
