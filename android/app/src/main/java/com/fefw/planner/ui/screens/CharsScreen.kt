package com.fefw.planner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fefw.planner.data.Character
import com.fefw.planner.data.artsBySkill
import com.fefw.planner.data.growthCells
import com.fefw.planner.data.hasScout
import com.fefw.planner.ui.components.AssetImage
import com.fefw.planner.ui.components.Badge
import com.fefw.planner.ui.components.BadgeKind
import com.fefw.planner.ui.components.CheckChip
import com.fefw.planner.ui.components.EmptyHint
import com.fefw.planner.ui.components.GrowthGrid
import com.fefw.planner.ui.components.SkillDetailDialog
import com.fefw.planner.ui.components.TagPill
import com.fefw.planner.ui.theme.Border
import com.fefw.planner.ui.theme.Muted
import com.fefw.planner.ui.theme.Muted2
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
    val artWeapon = vm.fe.artWeapon
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
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text("显示 ${list.size} / ${all.size} 人", style = MaterialTheme.typography.labelSmall, color = Muted)

        if (list.isEmpty()) {
            EmptyHint("没有符合筛选条件的角色。")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(list, key = { it.id }) { c ->
                    CharRow(c = c, stats = stats, highlight = locate == c.id, artWeapon = artWeapon, skillDesc = vm.fe.skillDesc)
                }
            }
        }
    }
}

@Composable
private fun CharRow(c: Character, stats: List<List<String>>, highlight: Boolean, artWeapon: Map<String, String>, skillDesc: Map<String, String>) {
    // 弹框内容只依赖被点的那条技能；用 (name,kind) 作 key、渲染时再查 desc，避免在列表项里缓存整张 desc 映射。
    var skillDetail by remember { mutableStateOf<Pair<String, String?>?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Surface1)
            .border(if (highlight) 2.dp else 1.dp, if (highlight) Primary else Border, RoundedCornerShape(8.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssetImage(c.portrait, c.name, Modifier.size(22.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(c.name, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    if (c.isHero) Badge("主角", BadgeKind.Hero)
                }
                Text(c.startClass.orEmpty(), style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
        GrowthGrid(growthCells(c.growth, null, stats), mini = true)
        // 个人特技 与 skills[].kind=="个人特技" 是 1:1 等价的，直接用 skills 完整列表即可覆盖
        // 「个人特技 + 血印 + Lv特技」，因此无需再单独渲染 personalSkill 字段。
        if (c.skills.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("特性", style = MaterialTheme.typography.labelSmall, color = Muted)
                c.skills.forEach { sk ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // 特性行内已直接展示 effect 文本，无独立技能名可查，故胶囊保持纯展示。
                        TagPill(sk.kind)
                        Text(
                            sk.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        // 战技/魔法/擅长来自 learn 与 skillsGood，与网页版 ch-detail 对齐；
        // 移动端空间有限，只保留高信号三类（可习得技能/升级技能在职业页已有，避免重复占屏）。
        LearnSkillGroups(remember(c, artWeapon) { artsBySkill(c.learn, artWeapon) }, onSkill = { n, k -> skillDetail = n to k })
        LearnChips("魔法", c.learn.magic, onSkill = { n -> skillDetail = n to "魔法" })
        LearnChips("擅长", c.skillsGood, onSkill = { n -> skillDetail = n to "擅长" })
    }
    skillDetail?.let { (name, kind) ->
        SkillDetailDialog(name = name, kind = kind, desc = skillDesc[name], onDismiss = { skillDetail = null })
    }
}

/**
 * learn 字段展示块：标签 + 名称胶囊（单行横滑），风格与「特性」块一致。
 * 空列表显示「—」，与网页版 detailListHTML「空缺不留白」的处理保持一致。
 */
@Composable
private fun LearnChips(label: String, items: List<String>, onSkill: (String) -> Unit) {
    val list = items.filter { it.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            if (list.isEmpty()) label else "$label · ${list.size}",
            style = MaterialTheme.typography.labelSmall,
            color = Muted
        )
        if (list.isEmpty()) {
            Text("—", style = MaterialTheme.typography.labelSmall, color = Muted2)
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                list.forEach { name -> TagPill(name, onClick = { onSkill(name) }) }
            }
        }
    }
}

/**
 * 战技按武器/技能类别分组：一类一行（左定宽标签 + 右横滑胶囊），节奏与上方「特性」块一致。
 * 移动端一行藏多类虽省纵向空间，但「有哪些武器类别」需滑动才可见、等于没分组；
 * 实测均值 ~4 类/人、最多 8 类，纵向由 LazyColumn 承担，故取一类一行。
 */
@Composable
private fun LearnSkillGroups(groups: List<Pair<String, List<String>>>, onSkill: (String, String) -> Unit) {
    val total = groups.sumOf { it.second.size }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            if (groups.isEmpty()) "战技" else "战技 · $total",
            style = MaterialTheme.typography.labelSmall,
            color = Muted
        )
        if (groups.isEmpty()) {
            Text("—", style = MaterialTheme.typography.labelSmall, color = Muted2)
        } else {
            groups.forEach { (cat, names) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        cat,
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted2,
                        maxLines = 1,
                        modifier = Modifier.width(40.dp)
                    )
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        names.forEach { name -> TagPill(name, onClick = { onSkill(name, cat) }) }
                    }
                }
            }
        }
    }
}
