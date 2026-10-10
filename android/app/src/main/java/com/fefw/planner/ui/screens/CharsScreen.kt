package com.fefw.planner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fefw.planner.data.Character
import com.fefw.planner.data.RecruitInfo
import com.fefw.planner.data.RecruitState
import com.fefw.planner.data.SlotData
import com.fefw.planner.data.bestScoutScore
import com.fefw.planner.data.cmpScore
import com.fefw.planner.data.hasScout
import com.fefw.planner.data.recruitsFor
import com.fefw.planner.ui.components.AssetImage
import com.fefw.planner.ui.components.Badge
import com.fefw.planner.ui.components.BadgeKind
import com.fefw.planner.ui.components.CheckChip
import com.fefw.planner.ui.components.EmptyHint
import com.fefw.planner.ui.components.FefwTextField
import com.fefw.planner.ui.components.TagPill
import com.fefw.planner.ui.theme.AccentSoft
import com.fefw.planner.ui.theme.Border
import com.fefw.planner.ui.theme.Muted
import com.fefw.planner.ui.theme.Muted2
import com.fefw.planner.ui.theme.OkSoft
import com.fefw.planner.ui.theme.OkText
import com.fefw.planner.ui.theme.Panel3
import com.fefw.planner.ui.theme.Primary
import com.fefw.planner.ui.theme.Surface1
import com.fefw.planner.ui.theme.TextPrimary
import com.fefw.planner.ui.theme.TextSecondary
import com.fefw.planner.ui.theme.WarnSoft
import com.fefw.planner.ui.theme.WarnText
import com.fefw.planner.viewmodel.PlannerViewModel
import com.fefw.planner.viewmodel.activeDataOrNull

@Composable
fun CharsScreen(vm: PlannerViewModel) {
    val store by vm.store.collectAsStateWithLifecycle()
    val data = store.activeDataOrNull ?: return
    val q by vm.charsQ.collectAsStateWithLifecycle()
    val onlyScout by vm.charsOnlyScout.collectAsStateWithLifecycle()
    val showLate by vm.charsShowLate.collectAsStateWithLifecycle()

    val all = vm.fe.characters
    val routeKeys = vm.routeKeys
    val list = remember(all, q, onlyScout, showLate, routeKeys) {
        all.filter { c ->
            if (c.late && !showLate) return@filter false
            if (onlyScout && !hasScout(c, routeKeys)) return@filter false
            if (q.isNotEmpty() && !c.name.contains(q) && !c.id.contains(q)) return@filter false
            true
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FefwTextField(value = q, onValueChange = vm::setCharsQ, placeholder = "搜索角色名…", modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CheckChip(onlyScout, "只看可挖", vm::toggleCharsOnlyScout)
            CheckChip(showLate, "显示后期角色", vm::toggleCharsShowLate)
        }
        Text("显示 ${list.size} / ${all.size} 人", style = MaterialTheme.typography.bodySmall, color = Muted)

        if (list.isEmpty()) {
            EmptyHint("没有符合筛选条件的角色。")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list, key = { it.id }) { c ->
                    CharRow(c = c, route = data.route, data = data, routeKeys = routeKeys)
                }
            }
        }
    }
}

@Composable
private fun CharRow(c: Character, route: String, data: SlotData, routeKeys: List<String>) {
    val rec = data.recruitsFor(route)[c.id]
    val best = bestScoutScore(c, routeKeys)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface1)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssetImage(c.portrait, c.name, Modifier.size(36.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    if (c.isHero) Badge("主角", BadgeKind.Hero)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(c.startClass.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Muted)
                    if (rec?.done == true) {
                        Badge("本线已招募", BadgeKind.Story)
                    } else if (rec?.planned == true) {
                        Badge("本线已计划", BadgeKind.Hero)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            routeKeys.forEach { k ->
                CharRouteCell(
                    c = c,
                    routeKey = k,
                    curRoute = route,
                    best = best,
                    rec = data.recruitsFor(k)[c.id],
                    modifier = Modifier.weight(1f)
                )
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
    }
}

@Composable
private fun CharRouteCell(
    c: Character,
    routeKey: String,
    curRoute: String,
    best: Int?,
    rec: RecruitState?,
    modifier: Modifier = Modifier
) {
    val r = c.recruitByRoute[routeKey]
    val isCur = routeKey == curRoute
    val isBest = r != null && r.kind == com.fefw.planner.data.KIND_SCOUT && best != null && cmpScore(r) == best
    val bg = if (isCur) AccentSoft else if (isBest) OkSoft else Panel3
    val bd = if (isCur) Primary else if (isBest) com.fefw.planner.ui.theme.Secondary else Border
    val mark = when {
        rec?.done == true -> "✓"
        rec?.planned == true -> "☆"
        else -> ""
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(if (isCur) 1.dp else 1.dp, bd, RoundedCornerShape(8.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(routeKey, style = MaterialTheme.typography.bodySmall, color = if (isCur) TextPrimary else Muted)
            if (mark.isNotEmpty()) {
                Text(mark, style = MaterialTheme.typography.bodySmall, color = if (rec?.done == true) OkText else Primary)
            }
        }
        when {
            r == null || r.kind == com.fefw.planner.data.KIND_NO -> Text("—", style = MaterialTheme.typography.bodySmall, color = Muted2)
            r.kind == com.fefw.planner.data.KIND_STORY -> Badge("剧情加入", BadgeKind.Story)
            else -> ScoutCellText(r, isBest)
        }
    }
}

@Composable
private fun ScoutCellText(r: RecruitInfo, isBest: Boolean) {
    val text = "名声${r.rep ?: "?"} · 支援${r.sup ?: "?"} · ${r.diff ?: "?"}" + if (isBest) " ★" else ""
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isBest) OkText else TextSecondary
    )
}