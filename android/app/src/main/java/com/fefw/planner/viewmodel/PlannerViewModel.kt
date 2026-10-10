package com.fefw.planner.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.fefw.planner.data.Character
import com.fefw.planner.data.ClassInfo
import com.fefw.planner.data.FALLBACK_ROUTE_KEYS
import com.fefw.planner.data.FeData
import com.fefw.planner.data.FeDataLoader
import com.fefw.planner.data.ImportResult
import com.fefw.planner.data.MountState
import com.fefw.planner.data.PathState
import com.fefw.planner.data.RecruitState
import com.fefw.planner.data.Recommend
import com.fefw.planner.data.Repository
import com.fefw.planner.data.Slot
import com.fefw.planner.data.SlotData
import com.fefw.planner.data.Store
import com.fefw.planner.data.UiState
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

    private val _tab = MutableStateFlow("recruit")
    val tab: StateFlow<String> = _tab.asStateFlow()

    private val _rulesOpen = MutableStateFlow(false)
    val rulesOpen: StateFlow<Boolean> = _rulesOpen.asStateFlow()

    private val _filterQ = MutableStateFlow("")
    val filterQ: StateFlow<String> = _filterQ.asStateFlow()

    private val _filterSort = MutableStateFlow("rep")
    val filterSort: StateFlow<String> = _filterSort.asStateFlow()

    private val _filterRepMin = MutableStateFlow(0)
    val filterRepMin: StateFlow<Int> = _filterRepMin.asStateFlow()

    private val _filterOnlyPlanned = MutableStateFlow(false)
    val filterOnlyPlanned: StateFlow<Boolean> = _filterOnlyPlanned.asStateFlow()

    private val _showAllPaths = MutableStateFlow(false)
    val showAllPaths: StateFlow<Boolean> = _showAllPaths.asStateFlow()

    private val _mountHighlight = MutableStateFlow<String?>(null)
    val mountHighlight: StateFlow<String?> = _mountHighlight.asStateFlow()

    private val _charsQ = MutableStateFlow("")
    val charsQ: StateFlow<String> = _charsQ.asStateFlow()

    private val _charsOnlyScout = MutableStateFlow(false)
    val charsOnlyScout: StateFlow<Boolean> = _charsOnlyScout.asStateFlow()

    private val _charsShowLate = MutableStateFlow(false)
    val charsShowLate: StateFlow<Boolean> = _charsShowLate.asStateFlow()

    private val _expandCmp = MutableStateFlow<Set<String>>(emptySet())
    val expandCmp: StateFlow<Set<String>> = _expandCmp.asStateFlow()

    private val _expandItem = MutableStateFlow<Set<String>>(emptySet())
    val expandItem: StateFlow<Set<String>> = _expandItem.asStateFlow()

    private val _expandClass = MutableStateFlow<Set<String>>(emptySet())
    val expandClass: StateFlow<Set<String>> = _expandClass.asStateFlow()

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
        _tab.value = d.ui.tab
        _filterQ.value = d.ui.q
        _filterSort.value = d.ui.sort
        _filterRepMin.value = d.ui.repMin
        _filterOnlyPlanned.value = d.ui.onlyPlanned
        _rulesOpen.value = false
        _mountHighlight.value = null
    }

    fun setTab(tab: String) {
        _tab.value = tab
        mutate { it.copy(ui = it.ui.copy(tab = tab)) }
    }

    fun toggleRules() {
        _rulesOpen.value = !_rulesOpen.value
    }

    fun chooseRoute(key: String, goRecruit: Boolean = false) {
        mutate { d ->
            d.copy(
                route = key,
                onboarded = true,
                ui = if (goRecruit) d.ui.copy(tab = "recruit") else d.ui
            )
        }
        if (goRecruit) _tab.value = "recruit"
    }

    fun togglePlanned(id: String) {
        mutate { d ->
            val rec = d.recruits[id] ?: RecruitState()
            d.copy(recruits = d.recruits + (id to rec.copy(planned = !rec.planned)))
        }
    }

    fun toggleDone(id: String) {
        mutate { d ->
            val rec = d.recruits[id] ?: RecruitState()
            val done = !rec.done
            d.copy(recruits = d.recruits + (id to rec.copy(done = done, planned = if (done) true else rec.planned)))
        }
    }

    fun toggleCmp(id: String) = _expandCmp.update { if (id in it) it - id else it + id }

    fun toggleItem(name: String) = _expandItem.update { if (name in it) it - name else it + name }

    fun setFilterQ(q: String) {
        _filterQ.value = q
        mutate { it.copy(ui = it.ui.copy(q = q)) }
    }

    fun setSort(sort: String) {
        _filterSort.value = sort
        mutate { it.copy(ui = it.ui.copy(sort = sort)) }
    }

    fun setRepMin(min: Int) {
        _filterRepMin.value = min
        mutate { it.copy(ui = it.ui.copy(repMin = min)) }
    }

    fun toggleOnlyPlanned() {
        val next = !_filterOnlyPlanned.value
        _filterOnlyPlanned.value = next
        mutate { it.copy(ui = it.ui.copy(onlyPlanned = next)) }
    }

    fun toggleShowAllPaths() = _showAllPaths.update { !it }

    fun selectPath(id: String, index: Int) {
        mutate { d ->
            val pd = d.paths[id] ?: PathState()
            d.copy(paths = d.paths + (id to pd.copy(srcPathIndex = index)))
        }
    }

    fun setCustomStep(id: String, tierIndex: Int, classId: String) {
        mutate { d ->
            val pd = d.paths[id] ?: PathState()
            val custom = pd.custom.toMutableList()
            while (custom.size <= tierIndex) custom.add("")
            custom[tierIndex] = classId
            d.copy(paths = d.paths + (id to pd.copy(custom = custom, srcPathIndex = -1)))
        }
    }

    fun toggleStepDone(id: String, tier: String) {
        mutate { d ->
            val pd = d.paths[id] ?: PathState()
            val done = pd.doneSteps.toMutableMap()
            done[tier] = !(done[tier] ?: false)
            d.copy(paths = d.paths + (id to pd.copy(doneSteps = done)))
        }
    }

    fun toggleClassInfo(key: String) = _expandClass.update { if (key in it) it - key else it + key }

    fun setNote(id: String, text: String) {
        mutate { it.copy(notes = it.notes + (id to text)) }
    }

    fun toggleMountPlanned(name: String) = toggleMount(name) { it.copy(planned = !it.planned) }

    fun toggleMountCaptured(name: String) = toggleMount(name) { it.copy(captured = !it.captured) }

    private fun toggleMount(name: String, block: (MountState) -> MountState) {
        mutate { d ->
            val m = d.mounts[name] ?: MountState()
            d.copy(mounts = d.mounts + (name to block(m)))
        }
    }

    fun assignMount(name: String, charId: String) {
        if (charId.isBlank()) return
        mutate { d ->
            val m = d.mounts[name] ?: MountState()
            if (charId in m.assigned) d
            else d.copy(mounts = d.mounts + (name to m.copy(assigned = m.assigned + charId)))
        }
    }

    fun unassignMount(name: String, charId: String) {
        mutate { d ->
            val m = d.mounts[name] ?: return@mutate d
            d.copy(mounts = d.mounts + (name to m.copy(assigned = m.assigned.filter { it != charId })))
        }
    }

    fun gotoMount(charId: String) {
        setTab("mounts")
        _mountHighlight.value = charId
    }

    fun setCharsQ(q: String) {
        _charsQ.value = q
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
