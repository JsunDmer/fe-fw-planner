package com.fefw.planner.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fefw.planner.data.CUSTOM_TIERS
import com.fefw.planner.data.Character
import com.fefw.planner.data.ClassInfo
import com.fefw.planner.data.Growth
import com.fefw.planner.data.KAI_ROUTE
import com.fefw.planner.data.KIND_STORY
import com.fefw.planner.data.MOUNT_UNIT_REGEX
import com.fefw.planner.data.Step
import com.fefw.planner.data.addGrowth
import com.fefw.planner.data.growthCells
import com.fefw.planner.data.mountGrowthOf
import com.fefw.planner.data.mountTypesForClass
import com.fefw.planner.data.recruitsFor
import com.fefw.planner.data.resolveSteps
import com.fefw.planner.data.stepMountKey
import com.fefw.planner.ui.components.AssetImage
import com.fefw.planner.ui.components.Badge
import com.fefw.planner.ui.components.BadgeKind
import com.fefw.planner.ui.components.CheckChip
import com.fefw.planner.ui.components.ChoiceChip
import com.fefw.planner.ui.components.EmptyHint
import com.fefw.planner.ui.components.GrowthGrid
import com.fefw.planner.ui.components.PanelCard
import com.fefw.planner.ui.components.TagPill
import com.fefw.planner.ui.theme.Border
import com.fefw.planner.ui.theme.Muted
import com.fefw.planner.ui.theme.OkText
import com.fefw.planner.ui.theme.Primary
import com.fefw.planner.ui.theme.ScoutText
import com.fefw.planner.ui.theme.Surface1
import com.fefw.planner.ui.theme.Surface2
import com.fefw.planner.ui.theme.TextPrimary
import com.fefw.planner.ui.theme.TextSecondary
import com.fefw.planner.viewmodel.PlannerViewModel
import com.fefw.planner.viewmodel.activeDataOrNull

@Composable
fun ClassScreen(vm: PlannerViewModel) {
    val store by vm.store.collectAsStateWithLifecycle()
    val data = store.activeDataOrNull ?: return
    val showAll by vm.showAllPaths.collectAsStateWithLifecycle()
    val expandClass by vm.expandClass.collectAsStateWithLifecycle()
    val customConfirmed by vm.customConfirmed.collectAsStateWithLifecycle()

    val all = remember(vm.fe.characters) { vm.fe.characters.filter { it.paths.isNotEmpty() } }
    val chars = remember(all, data, showAll) {
        if (showAll) all
        else all.filter { c ->
            // 默认也要露出 4 位主角与本路线剧情加入角色，否则首次进入该页几乎为空、无法规划他们。
            if (c.isHero) return@filter true
            if (c.recruitByRoute[data.route]?.kind == KIND_STORY) return@filter true
            val rec = data.recruitsFor(data.route)[c.id]
            if (rec?.planned == true || rec?.done == true) return@filter true
            val p = data.paths[c.id]
            if (p == null) return@filter false
            p.srcPathIndex != 0 || p.custom.isNotEmpty() || p.doneSteps.isNotEmpty() || !data.notes[c.id].isNullOrBlank()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CheckChip(showAll, "显示全部角色", vm::toggleShowAllPaths)
            Text("显示 ${chars.size} / ${all.size} 人", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        if (chars.isEmpty()) {
            EmptyHint(if (showAll) "没有可规划的转职角色。" else "还没有已计划/已规划的转职角色，勾选「显示全部角色」查看。", Modifier.padding(16.dp))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(chars, key = { it.id }) { c ->
                    CharPlanCard(vm, c, data, expandClass, customConfirmed)
                }
            }
        }
    }
}

@Composable
private fun CharPlanCard(
    vm: PlannerViewModel,
    c: Character,
    data: com.fefw.planner.data.SlotData,
    expandClass: Set<String>,
    customConfirmed: Set<String>
) {
    val pd = data.paths[c.id]
    val rec = data.recruitsFor(data.route)[c.id]
    val steps = resolveSteps(c, pd, vm.classesById)
    val customMode = pd?.srcPathIndex == -1
    val doneSteps = pd?.doneSteps.orEmpty()
    val doneCount = steps.count { doneSteps[it.tier] == true }

    val route = data.route
    val stepMounts = data.stepMounts
    val mountSum: Growth? = if (route == KAI_ROUTE) {
        steps.mapNotNull { stepMounts[stepMountKey(c.id, it.tier)] }
            .filter { it.isNotBlank() }
            .distinct()
            .mapNotNull { mountGrowthOf(vm.fe.mounts, it) }
            .fold<Growth, Growth?>(null) { acc, g -> acc?.let { addGrowth(it, g) } ?: g }
    } else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface1)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssetImage(c.portrait, c.name, Modifier.size(40.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    if (c.isHero) Badge("主角", BadgeKind.Hero)
                }
                val start = c.startClass.orEmpty()
                if (start.isNotBlank()) {
                    Text("初始职业：$start", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                Text(
                    if (mountSum != null) "基础成长（含坐骑）" else "基础成长",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
                GrowthGrid(growthCells(c.growth, null, vm.fe.stats, mountSum), compact = true, mini = true)
            }
        }

        if (c.skills.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("特性", style = MaterialTheme.typography.bodySmall, color = Muted)
                c.skills.forEach { sk ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TagPill(sk.kind)
                        Text(
                            sk.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (c.paths.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                c.paths.forEachIndexed { i, _ ->
                    ChoiceChip("方案 ${i + 1}", selected = pd?.srcPathIndex == i, onClick = { vm.selectPath(c.id, i) })
                }
                ChoiceChip("自定义", selected = customMode, onClick = { vm.selectPath(c.id, -1) })
            }
        }

        if (customMode) {
            if (c.id !in customConfirmed) {
                CUSTOM_TIERS.forEachIndexed { i, tier ->
                    CustomTierRow(vm, c.id, tier, pd?.custom?.getOrNull(i))
                }
                val hasAny = pd?.custom?.any { it.isNotBlank() } == true
                OutlinedButton(
                    onClick = { vm.confirmCustom(c.id) },
                    enabled = hasAny,
                    border = BorderStroke(1.dp, if (hasAny) Primary else Border),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        "确认路线",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hasAny) TextPrimary else Muted
                    )
                }
            } else {
                if (steps.isEmpty()) {
                    Text("尚未选择职业，点「重新编辑」。", style = MaterialTheme.typography.bodySmall, color = Muted)
                } else {
                    StepFlow(vm, c, steps, doneSteps, expandClass, route, stepMounts)
                }
                ChoiceChip("重新编辑", selected = false, onClick = { vm.editCustom(c.id) })
            }
        } else if (steps.isEmpty()) {
            Text("该方案暂无推荐转职详情，可切到「自定义」。", style = MaterialTheme.typography.bodySmall, color = Muted)
        } else {
            StepFlow(vm, c, steps, doneSteps, expandClass, route, stepMounts)
        }

        Text(
            "进度 $doneCount / ${steps.size}",
            style = MaterialTheme.typography.bodySmall,
            color = if (doneCount == steps.size && steps.isNotEmpty()) OkText else Muted
        )
    }
}

@Composable
private fun StepFlow(
    vm: PlannerViewModel,
    c: Character,
    steps: List<Step>,
    doneSteps: Map<String, Boolean>,
    expandClass: Set<String>,
    route: String,
    stepMounts: Map<String, String>
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, s ->
            if (i > 0) {
                item { Text("→", color = Muted, fontSize = 14.sp) }
            }
            item(key = s.classId.ifBlank { "$i" }) {
                StepNode(
                    vm = vm,
                    charId = c.id,
                    step = s,
                    cls = vm.classesById[s.classId],
                    baseGrowth = c.growth,
                    route = route,
                    mountName = stepMounts[stepMountKey(c.id, s.tier)],
                    done = doneSteps[s.tier] == true,
                    opened = s.classId in expandClass
                )
            }
        }
    }
}

@Composable
private fun CustomTierRow(vm: PlannerViewModel, charId: String, tier: String, currentId: String?) {
    val classes = remember(vm.classesById, tier) {
        vm.classesById.values.filter { it.tier == tier }.sortedBy { it.name }
    }
    val currentName = vm.classesById[currentId.orEmpty()]?.name
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(tier, style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.width(52.dp))
        TierPicker(tier = tier, currentName = currentName, classes = classes) { picked ->
            vm.setCustomStep(charId, CUSTOM_TIERS.indexOf(tier), picked ?: "")
        }
    }
}

@Composable
private fun TierPicker(tier: String, currentName: String?, classes: List<ClassInfo>, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            border = androidx.compose.foundation.BorderStroke(1.dp, Border),
            shape = RoundedCornerShape(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                currentName ?: "选${tier}职业…",
                style = MaterialTheme.typography.bodyMedium,
                color = if (currentName != null) TextPrimary else Muted
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Surface2,
            shape = RoundedCornerShape(10.dp)
        ) {
            DropdownMenuItem(text = { Text("（不选）", color = Muted) }, onClick = { onPick(null); open = false })
            classes.forEach { cls ->
                DropdownMenuItem(
                    text = { Text(cls.name, color = TextPrimary) },
                    onClick = { onPick(cls.id); open = false }
                )
            }
        }
    }
}

@Composable
private fun StepNode(
    vm: PlannerViewModel,
    charId: String,
    step: Step,
    cls: ClassInfo?,
    baseGrowth: Growth,
    route: String,
    mountName: String?,
    done: Boolean,
    opened: Boolean
) {
    val unitType = cls?.unitType.orEmpty()
    val needsMount = MOUNT_UNIT_REGEX.containsMatchIn(unitType)
    val mountTypes = mountTypesForClass(cls)
    val mountEligible = route == KAI_ROUTE && mountTypes.isNotEmpty() && cls != null
    val mountGrowth = if (mountEligible) mountGrowthOf(vm.fe.mounts, mountName) else null
    // 有效成长 = 角色基础 + 职业加成 + 坐骑加成；结构固定 9 格，保证所有收起态节点等高。
    val cells = growthCells(baseGrowth, cls?.growth, vm.fe.stats, mountGrowth)
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (done) com.fefw.planner.ui.theme.OkSoft else Surface2)
            .border(1.dp, if (done) com.fefw.planner.ui.theme.Secondary else Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
            .clickable { if (cls != null) vm.toggleClassInfo(cls.id) },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssetImage(cls?.icon, step.name, Modifier.size(30.dp))
            Column {
                Text(step.tier, fontSize = 10.sp, color = ScoutText, maxLines = 1)
                Text(
                    if (cls != null) cls.name else step.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        GrowthGrid(cells)
        if (cls != null && opened) {
            SkillList("职业特技", cls.innate, vm.fe.skillDesc)
            SkillList("精通特技", listOf(cls.mastery), vm.fe.skillDesc)
            if (mountEligible) {
                MountPicker(vm, charId, step.tier, mountName, mountTypes)
            } else if (needsMount) {
                Text(
                    if (route == KAI_ROUTE) {
                        "需要坐骑（当前无可选类型）"
                    } else {
                        "需要坐骑（仅凯伊线可选）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CheckChip(checked = done, label = "已完成", onToggle = { vm.toggleStepDone(charId, step.tier) }, mark = "✓")
            Text(if (opened) "▾" else "▸", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun SkillList(label: String, names: List<String>, desc: Map<String, String>) {
    val items = names.filter { it.isNotBlank() }
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
        items.forEach { name ->
            val t = desc[name]
            Text(
                if (t.isNullOrBlank()) name else "$name：$t",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun MountPicker(vm: PlannerViewModel, charId: String, tier: String, current: String?, types: List<String>) {
    var open by remember { mutableStateOf(false) }
    val mounts = remember(vm.fe.mounts, types) {
        vm.fe.mounts.filter { it.type in types }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("坐骑", style = MaterialTheme.typography.bodySmall, color = Muted)
            Box {
                OutlinedButton(
                    onClick = { open = true },
                    border = BorderStroke(1.dp, if (current.isNullOrBlank()) Border else Primary),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text(
                        current ?: "选择坐骑…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (current.isNullOrBlank()) TextSecondary else Primary
                    )
                }
                DropdownMenu(
                    expanded = open,
                    onDismissRequest = { open = false },
                    containerColor = Surface2,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("（不选坐骑）", color = Muted) },
                        onClick = { vm.setStepMount(charId, tier, ""); open = false }
                    )
                    mounts.forEach { m ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(m.name, color = TextPrimary)
                                    if (m.growth.isNotBlank()) {
                                        Text(
                                            "成长 ${m.growth}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Muted
                                        )
                                    }
                                }
                            },
                            onClick = { vm.setStepMount(charId, tier, m.name); open = false }
                        )
                    }
                }
            }
        }
    }
}