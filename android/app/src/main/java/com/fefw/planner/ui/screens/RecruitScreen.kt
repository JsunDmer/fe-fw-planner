package com.fefw.planner.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.fefw.planner.data.KIND_NO
import com.fefw.planner.data.KIND_SCOUT
import com.fefw.planner.data.KIND_STORY
import com.fefw.planner.data.RecruitRow
import com.fefw.planner.data.aggregateConditions
import com.fefw.planner.data.formatMoney
import com.fefw.planner.data.groupScoutsByRep
import com.fefw.planner.data.matchFilter
import com.fefw.planner.data.recruitPool
import com.fefw.planner.data.recruitsFor
import com.fefw.planner.data.repThresholds
import com.fefw.planner.data.scoutComparator
import com.fefw.planner.data.storyOrderComparator
import com.fefw.planner.ui.components.AssetImage
import com.fefw.planner.ui.components.Badge
import com.fefw.planner.ui.components.BadgeKind
import com.fefw.planner.ui.components.CheckChip
import com.fefw.planner.ui.components.ChoiceChip
import com.fefw.planner.ui.components.CondChip
import com.fefw.planner.ui.components.EmptyHint
import com.fefw.planner.ui.components.FefwTextField
import com.fefw.planner.ui.components.KV
import com.fefw.planner.ui.components.PanelCard
import com.fefw.planner.ui.components.SectionHeader
import com.fefw.planner.ui.theme.AccentSoft
import com.fefw.planner.ui.theme.Bg
import com.fefw.planner.ui.theme.Border
import com.fefw.planner.ui.theme.DangerSoft
import com.fefw.planner.ui.theme.DangerText
import com.fefw.planner.ui.theme.Muted
import com.fefw.planner.ui.theme.Muted2
import com.fefw.planner.ui.theme.OkSoft
import com.fefw.planner.ui.theme.Primary
import com.fefw.planner.ui.theme.Surface1
import com.fefw.planner.ui.theme.TextPrimary
import com.fefw.planner.ui.theme.TextSecondary
import com.fefw.planner.ui.theme.WarnText
import com.fefw.planner.viewmodel.PlannerViewModel
import com.fefw.planner.viewmodel.activeDataOrNull

private val REP_OPTIONS = listOf(0, 2, 4, 6, 8, 10, 12)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecruitScreen(vm: PlannerViewModel) {
    val store by vm.store.collectAsStateWithLifecycle()
    val data = store.activeDataOrNull ?: return
    val q by vm.filterQ.collectAsStateWithLifecycle()
    val sort by vm.filterSort.collectAsStateWithLifecycle()
    val repMin by vm.filterRepMin.collectAsStateWithLifecycle()
    val onlyPlanned by vm.filterOnlyPlanned.collectAsStateWithLifecycle()

    val route = data.route
    val routeRecruits = data.recruitsFor(route)
    val pool = remember(vm.fe.characters, route) { recruitPool(vm.fe.characters, route) }
    val shown = pool.filter { matchFilter(it, q, onlyPlanned, repMin, routeRecruits) }
    val scoutAll = pool.count { it.r?.kind == KIND_SCOUT }
    val plannedCount = pool.count { routeRecruits[it.char.id]?.planned == true }

    var sheetOpen by remember { mutableStateOf(false) }
    if (sheetOpen) {
        HandleSheet(vm = vm, data = data, route = route, scoutAll = scoutAll, plannedCount = plannedCount) {
            sheetOpen = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        FilterBar(vm = vm, q = q, sort = sort, repMin = repMin, onlyPlanned = onlyPlanned)
        SummaryStrip(plannedCount = plannedCount, scoutAll = scoutAll) { sheetOpen = true }

        if (shown.isEmpty()) {
            EmptyHint("没有符合筛选条件的角色。", Modifier.padding(16.dp))
        } else {
            val story = shown.filter { it.r?.kind == KIND_STORY }
            val scouts = shown.filter { it.r?.kind == KIND_SCOUT }
            val no = shown.filter { it.r == null || it.r.kind == KIND_NO }
            var noOpen by remember { mutableStateOf(false) }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!onlyPlanned && story.isNotEmpty()) {
                    stickyHeader {
                        GroupHeader("本线固定出场 · ${story.size} 人", "剧情加入，无需交涉。")
                    }
                    items(story.sortedWith(storyOrderComparator), key = { it.char.id }) { row ->
                        FixedRow(row)
                    }
                }
                if (scouts.isNotEmpty()) {
                    val title = when (sort) {
                        "sup" -> "可挖角 · 按支援等级"
                        "name" -> "可挖角 · 按名字"
                        else -> "可挖角 · 按名声等级"
                    }
                    stickyHeader { GroupHeader(title, "已计划 $plannedCount 人 · 共可挖 $scoutAll 人") }
                    if (sort == "rep") {
                        groupScoutsByRep(scouts).forEach { (lv, list) ->
                            item(key = "rep-$lv") { RankHead("名声 LV$lv", "${list.size} 人") }
                            items(list, key = { it.char.id }) { row -> ScoutRow(vm, row, data) }
                        }
                    } else {
                        items(scouts.sortedWith(scoutComparator(sort)), key = { it.char.id }) { row ->
                            ScoutRow(vm, row, data)
                        }
                    }
                }
                if (!onlyPlanned && no.isNotEmpty()) {
                    item {
                        NoGroupHeader(count = no.size, open = noOpen) { noOpen = !noOpen }
                    }
                    if (noOpen) {
                        items(no.sortedWith(scoutComparator("name")), key = { it.char.id }) { row ->
                            NoRow(row)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterBar(
    vm: PlannerViewModel,
    q: String,
    sort: String,
    repMin: Int,
    onlyPlanned: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FefwTextField(
                value = q,
                onValueChange = vm::setFilterQ,
                placeholder = "搜索角色名…",
                modifier = Modifier.weight(1f)
            )
            CheckChip(onlyPlanned, "只看已计划", vm::toggleOnlyPlanned)
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            REP_OPTIONS.forEach { n ->
                ChoiceChip(
                    label = if (n == 0) "全部" else "名声≥$n",
                    selected = repMin == n,
                    onClick = { vm.setRepMin(n) }
                )
            }
            Box(Modifier.size(1.dp, 16.dp).clip(RoundedCornerShape(1.dp)).background(Border))
            ChoiceChip("按名声", selected = sort == "rep", onClick = { vm.setSort("rep") })
            ChoiceChip("按支援", selected = sort == "sup", onClick = { vm.setSort("sup") })
            ChoiceChip("按名字", selected = sort == "name", onClick = { vm.setSort("name") })
        }
    }
}

@Composable
private fun SummaryStrip(plannedCount: Int, scoutAll: Int, onMaterials: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface1)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "已计划 $plannedCount 人 · 可挖 $scoutAll 人",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            "材料清单 ▸",
            style = MaterialTheme.typography.bodyMedium,
            color = Primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onMaterials() }
                .padding(4.dp)
        )
    }
}

@Composable
private fun GroupHeader(title: String, hint: String) {
    Column(Modifier.fillMaxWidth().background(Bg).padding(horizontal = 12.dp).padding(top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun RankHead(text: String, count: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(Primary))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontWeight = FontWeight.Medium)
        Text(count, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun FixedRow(row: RecruitRow) {
    val c = row.char
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssetImage(c.portrait, c.name, Modifier.size(36.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Badge("剧情加入", BadgeKind.Story)
                    if (c.isHero) Badge("主角", BadgeKind.Hero)
                }
                val ch = row.r?.ch
                if (!ch.isNullOrBlank()) {
                    Text("第 $ch 章加入", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScoutRow(vm: PlannerViewModel, row: RecruitRow, data: com.fefw.planner.data.SlotData) {
    val c = row.char
    val r = row.r
    val rec = data.recruitsFor(data.route)[c.id]
    val planned = rec?.planned == true
    val done = rec?.done == true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface1)
            .border(1.dp, if (done) com.fefw.planner.ui.theme.Secondary else Border, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssetImage(c.portrait, c.name, Modifier.size(44.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                }
                val start = c.startClass.orEmpty()
                if (start.isNotBlank()) {
                    Text(start, style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
            CheckChip(checked = done, label = "已招募", onToggle = { vm.toggleDone(c.id) }, mark = "✓")
            CheckChip(checked = planned, label = "计划招募", plan = true, onToggle = { vm.togglePlanned(c.id) }, mark = "☆")
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "名声 ${r?.rep ?: "?"} · 支援 ${r?.sup ?: "?"} · 难度 ${r?.diff ?: "?"}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            val ch = r?.ch
            if (!ch.isNullOrBlank()) {
                Text("最早章节 $ch", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        if (!r?.how.isNullOrBlank()) {
            KV("入队", r?.how ?: "", Modifier.padding(end = 40.dp))
        }
        if (!r?.hard.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DangerSoft)
                    .border(1.dp, com.fefw.planner.ui.theme.ErrorRed.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("难挖：${r?.hard}", style = MaterialTheme.typography.bodySmall, color = DangerText)
            }
        }
        val conds = r?.cond.orEmpty()
        if (conds.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                conds.forEach { CondChip(it, isOther = false) }
            }
        }
    }
}

@Composable
private fun NoGroupHeader(count: Int, open: Boolean, onToggle: () -> Unit) {
    PanelCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggle() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Badge("本篇不可招募", BadgeKind.No)
            Text("$count 人", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.weight(1f))
            Text(if (open) "▾" else "▸", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun NoRow(row: RecruitRow) {
    val c = row.char
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(com.fefw.planner.ui.theme.Panel3)
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssetImage(c.portrait, c.name, Modifier.size(28.dp))
        Text(c.name, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
        if (c.isHero) Badge("主角", BadgeKind.Hero)
        Text("本篇不可招募", style = MaterialTheme.typography.bodySmall, color = Muted2)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HandleSheet(
    vm: PlannerViewModel,
    data: com.fefw.planner.data.SlotData,
    route: String,
    scoutAll: Int,
    plannedCount: Int,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("材料与条件清单", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text("已计划 $plannedCount 人 · 本线可挖 $scoutAll 人", style = MaterialTheme.typography.bodySmall, color = Muted)

            val scoutPlanned = vm.fe.characters.filter {
                data.recruitsFor(route)[it.id]?.planned == true && it.recruitByRoute[route]?.kind == KIND_SCOUT
            }
            if (scoutPlanned.isEmpty()) {
                EmptyHint(
                    if (plannedCount == 0) "还没有已计划的角色，先在挖角规划里勾选「计划招募」。"
                    else "已计划的角色中没有本线可挖角（可能都是固定出场）。"
                )
            } else {
                val agg = aggregateConditions(scoutPlanned, route)
                if (agg.moneyTotal > 0) MoneyPanel(agg)
                if (agg.items.isNotEmpty()) ItemsPanel(vm, agg)
                NamesPanel("前置外传", agg.paralogues)
                NamesPanel("前置支线", agg.quests)
                RepPanel(agg, scoutPlanned, route)
                OthersPanel(agg)
                Text(
                    "共 ${scoutPlanned.size} 名已计划角色参与统计。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MoneyPanel(agg: com.fefw.planner.data.AggResult) {
    SectionHeader("资金需求", "合计 ${formatMoney(agg.moneyTotal)} G")
    Column {
        agg.moneyRows.forEach { Text("${it.name}：${formatMoney(it.amount)} G", style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemsPanel(vm: PlannerViewModel, agg: com.fefw.planner.data.AggResult) {
    SectionHeader("道具需求")
    agg.items.forEach { (name, need) ->
        var open by remember(name) { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(com.fefw.planner.ui.theme.Panel3)
                .border(1.dp, Border, RoundedCornerShape(8.dp))
                .clickable { open = !open }
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
                Text("×${need.qty}", style = MaterialTheme.typography.bodyMedium, color = WarnText)
                Text("（${need.chars.joinToString("、")}）", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.weight(1f))
                Text(if (open) "▾" else "▸", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            if (open) {
                val mat = vm.fe.materials[name]
                val obtain = mat?.obtain?.takeIf { it.isNotEmpty() }?.joinToString("；")
                    ?: mat?.obtainGW?.takeIf { it.isNotEmpty() }?.joinToString("；")
                    ?: "未收录该道具的获取方式"
                Text(obtain, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun NamesPanel(title: String, map: LinkedHashMap<String, List<String>>) {
    if (map.isEmpty()) return
    SectionHeader(title)
    map.forEach { (k, v) ->
        Text("「$k」：${v.joinToString("、")}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
private fun RepPanel(agg: com.fefw.planner.data.AggResult, chars: List<com.fefw.planner.data.Character>, route: String) {
    SectionHeader("名声门槛汇总")
    repThresholds(chars, route).forEach { (lv, names) ->
        Text("名声 LV$lv：${names.joinToString("、")}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
private fun OthersPanel(agg: com.fefw.planner.data.AggResult) {
    if (agg.others.isEmpty()) return
    SectionHeader("对话·其它条件")
    agg.others.forEach {
        Text("${it.char}：${it.cond}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}