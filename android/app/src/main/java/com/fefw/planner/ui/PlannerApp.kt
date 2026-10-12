package com.fefw.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fefw.planner.data.Route
import com.fefw.planner.ui.components.Badge
import com.fefw.planner.ui.components.BadgeKind
import com.fefw.planner.ui.components.RouteChip
import com.fefw.planner.ui.components.SectionHeader
import com.fefw.planner.ui.components.TagPill
import com.fefw.planner.ui.components.parseHexColor
import com.fefw.planner.ui.screens.CharsScreen
import com.fefw.planner.ui.screens.ClassScreen
import com.fefw.planner.ui.screens.OnboardingScreen
import com.fefw.planner.ui.screens.OverviewScreen
import com.fefw.planner.ui.screens.RecruitScreen
import com.fefw.planner.ui.screens.SavesScreen
import com.fefw.planner.ui.theme.AccentSoft
import com.fefw.planner.ui.theme.Bg
import com.fefw.planner.ui.theme.Bg2
import com.fefw.planner.ui.theme.Border
import com.fefw.planner.ui.theme.DangerSoft
import com.fefw.planner.ui.theme.DangerText
import com.fefw.planner.ui.theme.ErrorRed
import com.fefw.planner.ui.theme.Muted
import com.fefw.planner.ui.theme.Primary
import com.fefw.planner.ui.theme.Surface1
import com.fefw.planner.ui.theme.TextPrimary
import com.fefw.planner.ui.theme.TextSecondary
import com.fefw.planner.viewmodel.PlannerViewModel
import com.fefw.planner.viewmodel.activeDataOrNull
import com.fefw.planner.viewmodel.activeSlot

private val TAB_LABELS = listOf("挖角规划", "转职规划", "角色信息", "四线总览", "存档")
private val TAB_KEYS = listOf("recruit", "class", "chars", "overview", "saves")

@Composable
fun PlannerApp(vm: PlannerViewModel) {
    val store by vm.store.collectAsStateWithLifecycle()
    val data = store.activeDataOrNull

    if (data == null || data.onboarded != true) {
        OnboardingScreen(vm)
        return
    }

    val tab by vm.tab.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var rulesOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    containerColor = Bg2,
                    tonalElevation = 0.dp
                ) {
                    TAB_KEYS.forEachIndexed { i, key ->
                        val selected = tab == key
                        NavigationBarItem(
                            selected = selected,
                            onClick = { vm.setTab(key) },
                            icon = {
                                Text(
                                    TAB_LABELS[i],
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Primary else TextSecondary
                                )
                            },
                            label = null,
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedTextColor = Primary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = AccentSoft
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                AppHeader(vm, data.route)
                RouteBar(vm, data.route) { rulesOpen = true }
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        "class" -> ClassScreen(vm)
                        "chars" -> CharsScreen(vm)
                        "overview" -> OverviewScreen(vm)
                        "saves" -> SavesScreen(vm)
                        else -> RecruitScreen(vm)
                    }
                }
            }
        }

        if (rulesOpen) {
            RulesSheet(vm, data.route) { rulesOpen = false }
        }

        error?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 88.dp, start = 24.dp, end = 24.dp)
                    .zIndex(4f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DangerSoft)
                    .border(1.dp, ErrorRed.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .clickable { vm.dismissError() }
                    .padding(12.dp)
            ) {
                Text(msg, style = MaterialTheme.typography.bodyMedium, color = DangerText)
            }
        }
    }
}

@Composable
private fun AppHeader(vm: PlannerViewModel, route: String) {
    val store by vm.store.collectAsStateWithLifecycle()
    val name = store.activeSlot?.name ?: "我的规划"
    Row(
        modifier = Modifier.fillMaxWidth().background(Bg).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Surface1)
                .border(1.dp, Primary, RoundedCornerShape(999.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).rotate45().background(Primary))
        }
        Column {
            Text("火焰纹章·万缕千丝 规划", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text("「$name」 · 主角线：$route", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

private fun Modifier.rotate45(): Modifier = this.rotate(45f)

@Composable
private fun RouteBar(vm: PlannerViewModel, route: String, onHelp: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("主角线", style = MaterialTheme.typography.bodySmall, color = Muted)
        vm.fe.routes.forEach { r ->
            RouteChip(
                label = r.key,
                dotColor = parseHexColor(r.color, Primary),
                active = r.key == route,
                onClick = { vm.chooseRoute(r.key) }
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            "说明",
            style = MaterialTheme.typography.bodyMedium,
            color = Primary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onHelp() }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private val RULES = listOf(
    "挖角角色必须同时满足 名声LV / 支援LV / 交涉难度，条件缺一不可。",
    "名声LV 为 4 线共享；资金 / 道具 / 外传 / 支线进度按角色在各自线路内独立消耗。",
    "支援LV 与好感对话需在对应主角篇内完成，跨线不累计。",
    "每话可重复出击刷等级（凹点）；难挖角色务必提前规划名声与材料。"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RulesSheet(vm: PlannerViewModel, route: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("挖角规则说明", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            RULES.forEachIndexed { i, rule ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(rule, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
            SectionHeader("本篇主角解锁职业")
            val cur = vm.fe.routes.firstOrNull { it.key == route }
            if (cur != null && cur.unlocks.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    cur.unlocks.forEach { TagPill(it) }
                }
            }
            val guide = vm.fe.guides.scout
            if (guide.isNotBlank()) {
                Badge("挖角总规则", BadgeKind.Scout)
                Text(guide, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
    }
}