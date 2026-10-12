package com.fefw.planner.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fefw.planner.data.Character
import com.fefw.planner.data.KIND_NO
import com.fefw.planner.data.KIND_SCOUT
import com.fefw.planner.data.KIND_STORY
import com.fefw.planner.data.RecruitInfo
import com.fefw.planner.data.RecruitState
import com.fefw.planner.data.SlotData
import com.fefw.planner.data.bestScoutScore
import com.fefw.planner.data.cmpScore
import com.fefw.planner.data.hasScout
import com.fefw.planner.data.recruitsFor
import com.fefw.planner.ui.components.CheckChip
import com.fefw.planner.ui.components.EmptyHint
import com.fefw.planner.ui.components.FefwTextField
import com.fefw.planner.ui.components.parseHexColor
import com.fefw.planner.ui.theme.Muted
import com.fefw.planner.ui.theme.Muted2
import com.fefw.planner.ui.theme.OkText
import com.fefw.planner.ui.theme.Primary
import com.fefw.planner.ui.theme.Surface1
import com.fefw.planner.ui.theme.TextPrimary
import com.fefw.planner.ui.theme.TextSecondary
import com.fefw.planner.viewmodel.PlannerViewModel
import com.fefw.planner.viewmodel.activeDataOrNull

/**
 * 四线总览：把「全角色 × 4 条主角线」的挖角关系摊成一张矩阵，一屏看清
 * 每个角色会出现在哪几条线、以什么身份出现（剧情/可挖/不可）。
 *
 * 设计取舍：
 * - 列头点按即切换当前主角线（复用 vm.chooseRoute），省去回到顶部换线。
 * - 每格只放「身份 + 该线规划标记（✓已招募 / ☆已计划）」，窄列下用极简文本而非徽章，
 *   避免手机竖屏 4 列等宽时的换行/挤压（与旧挖角页的 CompareBlock 相比更省横向空间）。
 * - 筛选状态与「角色信息」页共享（charsQ / charsOnlyScout / charsShowLate），避免重复的输入状态。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverviewScreen(vm: PlannerViewModel) {
    val store by vm.store.collectAsStateWithLifecycle()
    val data = store.activeDataOrNull ?: return
    val q by vm.charsQ.collectAsStateWithLifecycle()
    val onlyScout by vm.charsOnlyScout.collectAsStateWithLifecycle()
    val showLate by vm.charsShowLate.collectAsStateWithLifecycle()

    val all = vm.fe.characters
    val routeKeys = vm.routeKeys
    val routeColors = remember(vm.fe.routes) {
        vm.fe.routes.associate { it.key to parseHexColor(it.color, Primary) }
    }
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
        FefwTextField(
            value = q,
            onValueChange = vm::setCharsQ,
            placeholder = "搜索角色名…",
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CheckChip(onlyScout, "只看可挖", vm::toggleCharsOnlyScout)
            CheckChip(showLate, "显示后期角色", vm::toggleCharsShowLate)
        }
        Text(
            "显示 ${list.size} / ${all.size} 人 · 点列头可切换主角线",
            style = MaterialTheme.typography.bodySmall,
            color = Muted
        )

        if (list.isEmpty()) {
            EmptyHint("没有符合筛选条件的角色。")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                stickyHeader {
                    OverviewHeader(routeKeys, routeColors, data.route, vm::chooseRoute)
                }
                itemsIndexed(list, key = { _, c -> c.id }) { idx, c ->
                    OverviewRow(
                        c = c,
                        data = data,
                        routeKeys = routeKeys,
                        best = bestScoutScore(c, routeKeys),
                        zebra = idx % 2 == 1
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewHeader(
    routeKeys: List<String>,
    routeColors: Map<String, Color>,
    curRoute: String,
    onRoute: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface1)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "角色",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1.2f)
        )
        routeKeys.forEach { k ->
            val active = k == curRoute
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onRoute(k) }
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(routeColors[k] ?: Primary)
                )
                Text(
                    k,
                    fontSize = 10.sp,
                    color = if (active) Primary else TextSecondary,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun OverviewRow(
    c: Character,
    data: SlotData,
    routeKeys: List<String>,
    best: Int?,
    zebra: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (zebra) Surface1 else Color.Transparent)
            .padding(vertical = 7.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            c.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (c.isHero) Primary else TextPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1.2f)
        )
        routeKeys.forEach { k ->
            val r = c.recruitByRoute[k]
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                OverviewCell(
                    r = r,
                    rec = data.recruitsFor(k)[c.id],
                    isBest = r != null && r.kind == KIND_SCOUT && best != null && cmpScore(r) == best
                )
            }
        }
    }
}

@Composable
private fun OverviewCell(r: RecruitInfo?, rec: RecruitState?, isBest: Boolean) {
    val mark = when {
        rec?.done == true -> "✓"
        rec?.planned == true -> "☆"
        else -> ""
    }
    val (text, color) = when {
        r == null || r.kind == KIND_NO -> "—" to Muted2
        r.kind == KIND_STORY -> "剧情" to OkText
        else -> ("名${r.rep ?: "?"}" + if (isBest) "★" else "") to (if (isBest) OkText else TextSecondary)
    }
    Text(
        text = if (mark.isEmpty()) text else "$mark$text",
        fontSize = 11.sp,
        color = color,
        maxLines = 1
    )
}
