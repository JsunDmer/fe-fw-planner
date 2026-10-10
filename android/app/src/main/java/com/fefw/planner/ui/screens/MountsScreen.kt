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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.fefw.planner.data.MOUNT_TYPES
import com.fefw.planner.data.Mount
import com.fefw.planner.data.isPlannedOrDone
import com.fefw.planner.data.mountFlagged
import com.fefw.planner.data.mountNeeded
import com.fefw.planner.ui.components.AssetImage
import com.fefw.planner.ui.components.Badge
import com.fefw.planner.ui.components.BadgeKind
import com.fefw.planner.ui.components.CheckChip
import com.fefw.planner.ui.components.KV
import com.fefw.planner.ui.components.PanelCard
import com.fefw.planner.ui.components.SectionHeader
import com.fefw.planner.ui.components.TagPill
import com.fefw.planner.ui.theme.Border
import com.fefw.planner.ui.theme.Muted
import com.fefw.planner.ui.theme.Muted2
import com.fefw.planner.ui.theme.Primary
import com.fefw.planner.ui.theme.Surface1
import com.fefw.planner.ui.theme.Surface2
import com.fefw.planner.ui.theme.TextPrimary
import com.fefw.planner.ui.theme.TextSecondary
import com.fefw.planner.ui.theme.Tertiary
import com.fefw.planner.ui.theme.WarnSoft
import com.fefw.planner.ui.theme.WarnText
import com.fefw.planner.viewmodel.PlannerViewModel
import com.fefw.planner.viewmodel.activeDataOrNull

@Composable
fun MountsScreen(vm: PlannerViewModel) {
    val store by vm.store.collectAsStateWithLifecycle()
    val data = store.activeDataOrNull ?: return
    val highlight by vm.mountHighlight.collectAsStateWithLifecycle()

    val needed = remember(data.recruits, data.paths, vm.fe.characters, vm.classesById) {
        mountNeeded(vm.fe.characters, data.recruits, data.paths, vm.classesById)
    }
    val byType = remember(vm.fe.mounts) { vm.fe.mounts.groupBy { it.type } }
    val orderedTypes = remember(byType) {
        MOUNT_TYPES.filter { byType.containsKey(it) } + byType.keys.filter { it !in MOUNT_TYPES }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PanelCard(modifier = Modifier.padding(top = 10.dp)) {
            Text(
                "坐骑与捕获规划：统计已计划/已招募角色的转职路线中需要坐骑的职业。捕获数量与分配可手动勾选，供后宫与坑位对照使用。",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        if (needed.isNotEmpty()) {
            SectionHeader("需要坐骑 · ${needed.size} 人")
            needed.forEach { n ->
                NeededCard(vm, n, highlighted = highlight == n.char.id && highlight != null)
            }
        }

        orderedTypes.forEach { type ->
            val mounts = byType[type].orEmpty()
            SectionHeader("$type · ${mounts.size} 种")
            mounts.forEach { m -> MountCard(vm, m, data) }
        }
    }
}

@Composable
private fun NeededCard(vm: PlannerViewModel, n: com.fefw.planner.data.NeededMount, highlighted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) WarnSoft else Surface1)
            .border(1.dp, if (highlighted) Tertiary else Border, RoundedCornerShape(10.dp))
            .clickable { vm.gotoMount(n.char.id) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssetImage(n.char.portrait, n.char.name, Modifier.size(36.dp))
        Column(Modifier.weight(1f)) {
            Text(n.char.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(
                n.classes.joinToString("、") + if (highlighted) " ← 已定位" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (highlighted) WarnText else Muted,
                maxLines = 2
            )
        }
        Text("去坐骑规划 ▸", style = MaterialTheme.typography.bodySmall, color = Primary)
    }
}

@Composable
private fun MountCard(vm: PlannerViewModel, m: Mount, data: com.fefw.planner.data.SlotData) {
    val ms = data.mounts[m.name]
    val planned = ms?.planned == true
    val captured = ms?.captured == true
    val assigned = ms?.assigned.orEmpty()
    val flagged = mountFlagged(m, vm.routeKeys)
    val eligible = remember(data.recruits, vm.fe.characters) {
        vm.fe.characters.filter { isPlannedOrDone(data.recruits, it.id) }
    }

    var pickOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2)
            .border(1.dp, if (planned || captured) com.fefw.planner.ui.theme.Secondary else Border, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(m.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            if (m.tentative) Badge("推测", BadgeKind.No)
            if (flagged) Badge("特定路线", BadgeKind.Mount)
        }
        if (m.bonus.isNotBlank()) KV("加成", m.bonus)
        if (m.obtain.isNotBlank()) KV("获取", m.obtain)
        if (m.where.isNotBlank()) KV("出现", m.where)
        if (m.food.isNotBlank()) KV("食物", m.food)
        if (m.growth.isNotBlank()) KV("成长", m.growth)
        if (m.skills.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                m.skills.forEach { TagPill(it) }
            }
        }
        if (m.levels.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                m.levels.forEachIndexed { i, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Lv${i + 1}", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.size(34.dp))
                        Text(row.joinToString(" / "), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CheckChip(checked = captured, label = "已捕获", onToggle = { vm.toggleMountCaptured(m.name) }, mark = "✓")
            CheckChip(checked = planned, label = "计划捕获", plan = true, onToggle = { vm.toggleMountPlanned(m.name) }, mark = "☆")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("分配给：", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.width(52.dp))
            Box {
                OutlinedButton(
                    onClick = { pickOpen = true },
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Text("选择角色…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                DropdownMenu(
                    expanded = pickOpen,
                    onDismissRequest = { pickOpen = false },
                    containerColor = Surface2,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (eligible.isEmpty()) {
                        DropdownMenuItem(text = { Text("没有已计划/已招募的角色", color = Muted) }, onClick = { pickOpen = false })
                    }
                    eligible.sortedBy { it.name }.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c.name, color = TextPrimary) },
                            onClick = {
                                vm.assignMount(m.name, c.id)
                                pickOpen = false
                            }
                        )
                    }
                }
            }
        }
        if (assigned.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("已分配：", style = MaterialTheme.typography.bodySmall, color = Muted)
                assigned.forEach { charId ->
                    val name = vm.charsById[charId]?.name ?: charId
                    Text(
                        "$name ✕",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(WarnSoft)
                            .border(1.dp, Tertiary.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .clickable { vm.unassignMount(m.name, charId) }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}