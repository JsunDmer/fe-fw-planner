package com.fefw.planner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fefw.planner.data.Character
import com.fefw.planner.data.growthCells
import com.fefw.planner.data.hasScout
import com.fefw.planner.ui.components.AssetImage
import com.fefw.planner.ui.components.Badge
import com.fefw.planner.ui.components.BadgeKind
import com.fefw.planner.ui.components.CheckChip
import com.fefw.planner.ui.components.EmptyHint
import com.fefw.planner.ui.components.GrowthGrid
import com.fefw.planner.ui.components.TagPill
import com.fefw.planner.ui.theme.Border
import com.fefw.planner.ui.theme.Muted
import com.fefw.planner.ui.theme.Primary
import com.fefw.planner.ui.theme.Surface1
import com.fefw.planner.ui.theme.TextPrimary
import com.fefw.planner.ui.theme.TextSecondary
import com.fefw.planner.viewmodel.PlannerViewModel

/**
 * 角色信息：每个角色只呈现「基础成长率 + 特性」两块内容。
 *
 * 原先的「全角色 × 4 线」挖角矩阵与招募徽章已移除，统一收敛到「四线总览」页，
 * 避免两页重复；本页只回答「这个角色本身强不强 / 有什么特性」。
 * 四线总览点角色名会设置 locateCharId 再切到本页，本页据此滚动定位到该角色。
 */
@Composable
fun CharsScreen(vm: PlannerViewModel) {
    val locate by vm.locateCharId.collectAsStateWithLifecycle()
    val onlyScout by vm.charsOnlyScout.collectAsStateWithLifecycle()
    val showLate by vm.charsShowLate.collectAsStateWithLifecycle()

    val all = vm.fe.characters
    val routeKeys = vm.routeKeys
    val stats = vm.fe.stats
    val list = remember(all, onlyScout, showLate, routeKeys) {
        all.filter { c ->
            if (c.late && !showLate) return@filter false
            if (onlyScout && !hasScout(c, routeKeys)) return@filter false
            true
        }
    }

    val listState = rememberLazyListState()
    // 无论是否命中都消费掉 locate：命中才滚动，未命中直接清空，避免 effect 反复重跑。
    LaunchedEffect(list, locate) {
        val id = locate ?: return@LaunchedEffect
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) listState.animateScrollToItem(index)
        vm.consumeLocate()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        }
        Text("显示 ${list.size} / ${all.size} 人", style = MaterialTheme.typography.bodySmall, color = Muted)

        if (list.isEmpty()) {
            EmptyHint("没有符合筛选条件的角色。")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list, key = { it.id }) { c ->
                    CharRow(c = c, stats = stats, highlight = locate == c.id)
                }
            }
        }
    }
}

@Composable
private fun CharRow(c: Character, stats: List<List<String>>, highlight: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface1)
            .border(if (highlight) 2.dp else 1.dp, if (highlight) Primary else Border, RoundedCornerShape(10.dp))
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
                Text(c.startClass.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        GrowthGrid(growthCells(c.growth, null, stats), mini = true)
        // 个人特技 与 skills[].kind=="个人特技" 是 1:1 等价的，直接用 skills 完整列表即可覆盖
        // 「个人特技 + 血印 + Lv特技」，因此无需再单独渲染 personalSkill 字段。
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
