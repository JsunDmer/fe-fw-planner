package com.fefw.planner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fefw.planner.data.CUSTOM_TIERS
import com.fefw.planner.data.Character
import com.fefw.planner.data.ClassInfo
import com.fefw.planner.data.MOUNT_UNIT_REGEX
import com.fefw.planner.data.Step
import com.fefw.planner.data.growthInline
import com.fefw.planner.data.resolveSteps
import com.fefw.planner.ui.components.AssetImage
import com.fefw.planner.ui.components.Badge
import com.fefw.planner.ui.components.BadgeKind
import com.fefw.planner.ui.components.CheckChip
import com.fefw.planner.ui.components.ChoiceChip
import com.fefw.planner.ui.components.EmptyHint
import com.fefw.planner.ui.components.FefwTextField
import com.fefw.planner.ui.components.KV
import com.fefw.planner.ui.components.PanelCard
import com.fefw.planner.ui.components.TagPill
import com.fefw.planner.ui.theme.Border
import com.fefw.planner.ui.theme.Muted
import com.fefw.planner.ui.theme.OkText
import com.fefw.planner.ui.theme.ScoutText
import com.fefw.planner.ui.theme.Surface1
import com.fefw.planner.ui.theme.Surface2
import com.fefw.planner.ui.theme.Tertiary
import com.fefw.planner.ui.theme.TextPrimary
import com.fefw.planner.ui.theme.TextSecondary
import com.fefw.planner.ui.theme.WarnSoft
import com.fefw.planner.ui.theme.WarnText
import com.fefw.planner.viewmodel.PlannerViewModel
import com.fefw.planner.viewmodel.activeDataOrNull

@Composable
fun ClassScreen(vm: PlannerViewModel) {
    val store by vm.store.collectAsStateWithLifecycle()
    val data = store.activeDataOrNull ?: return
    val showAll by vm.showAllPaths.collectAsStateWithLifecycle()
    val expandClass by vm.expandClass.collectAsStateWithLifecycle()

    val all = remember(vm.fe.characters) { vm.fe.characters.filter { it.paths.isNotEmpty() } }
    val chars = remember(all, data, showAll) {
        if (showAll) all
        else all.filter { c ->
            val rec = data.recruits[c.id]
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
                    CharPlanCard(vm, c, data, expandClass)
                }
            }
        }
    }
}

@Composable
private fun CharPlanCard(vm: PlannerViewModel, c: Character, data: com.fefw.planner.data.SlotData, expandClass: Set<String>) {
    val pd = data.paths[c.id]
    val rec = data.recruits[c.id]
    val steps = resolveSteps(c, pd, vm.classesById)
    val customMode = pd?.srcPathIndex == -1
    val doneSteps = pd?.doneSteps.orEmpty()
    val doneCount = steps.count { doneSteps[it.tier] == true }

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
                    "成长：${growthInline(c.growth, vm.fe.stats)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
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
            CUSTOM_TIERS.forEachIndexed { i, tier ->
                CustomTierRow(vm, c.id, tier, pd?.custom?.getOrNull(i))
            }
        } else if (steps.isEmpty()) {
            Text("该方案暂无推荐转职详情，可切到「自定义」。", style = MaterialTheme.typography.bodySmall, color = Muted)
        } else {
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
                            done = doneSteps[s.tier] == true,
                            opened = s.classId in expandClass
                        )
                    }
                }
            }
        }

        Text(
            "进度 $doneCount / ${steps.size}",
            style = MaterialTheme.typography.bodySmall,
            color = if (doneCount == steps.size && steps.isNotEmpty()) OkText else Muted
        )

        FefwTextField(
            value = data.notes[c.id].orEmpty(),
            onValueChange = { vm.setNote(c.id, it) },
            placeholder = "备注（如该角色需要的凹点、支援对象）…",
            modifier = Modifier.fillMaxWidth()
        )
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
    done: Boolean,
    opened: Boolean
) {
    val unitType = cls?.unitType.orEmpty()
    val needsMount = MOUNT_UNIT_REGEX.containsMatchIn(unitType)
    Column(
        modifier = Modifier
            .width(132.dp)
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
                Text(step.tier, style = MaterialTheme.typography.bodySmall, color = ScoutText)
                Text(if (cls != null) cls.name else step.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
        }
        if (cls != null) {
            val req = cls.requirement.ifBlank { "转职证" }
            Text("需求：$req", style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2)
            if (!cls.unlock.isNullOrBlank()) {
                Text("解锁：${cls.unlock}", style = MaterialTheme.typography.bodySmall, color = ScoutText, maxLines = 2)
            }
            Text("成长：${growthInline(cls.growth, vm.fe.stats)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 2)
            if (cls.mastery.isNotBlank()) {
                KV("精通", cls.mastery)
            }
            if (cls.innate.isNotEmpty()) {
                KV("固有", cls.innate.joinToString("、"))
            }
            if (needsMount) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(WarnSoft)
                        .border(1.dp, Tertiary.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .clickable { vm.gotoMount(charId) }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("🐴", fontSize = 11.sp)
                    Text("需要坐骑 →", style = MaterialTheme.typography.bodySmall, color = WarnText)
                }
            }
            if (opened) {
                ClassDetail(cls, vm)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CheckChip(checked = done, label = "已完成", onToggle = { vm.toggleStepDone(charId, step.tier) }, mark = "✓")
                if (opened) {
                    Text("▾", style = MaterialTheme.typography.bodySmall, color = Muted)
                } else {
                    Text("▸", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun ClassDetail(cls: ClassInfo, vm: PlannerViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (cls.usableSkills.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                cls.usableSkills.forEach { TagPill(it) }
            }
        }
        vm.recommendByClass[cls.id]?.forEach { r ->
            Text(r.group, style = MaterialTheme.typography.bodySmall, color = Tertiary, fontWeight = FontWeight.Medium)
            if (r.desc.isNotBlank()) {
                Text(r.desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            r.picks.forEach { p ->
                Text(
                    "${p.name}${if (p.trait.isNotBlank()) "（${p.trait}）" else ""}${if (p.note.isNotBlank()) " ${p.note}" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            if (r.footer.isNotBlank()) {
                Text(r.footer, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
}