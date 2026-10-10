package com.fefw.planner.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fefw.planner.data.Route
import com.fefw.planner.ui.components.TagPill
import com.fefw.planner.ui.components.parseHexColor
import com.fefw.planner.ui.theme.Border
import com.fefw.planner.ui.theme.Muted
import com.fefw.planner.ui.theme.Primary
import com.fefw.planner.ui.theme.Surface1
import com.fefw.planner.ui.theme.TextPrimary
import com.fefw.planner.ui.theme.TextSecondary
import com.fefw.planner.viewmodel.PlannerViewModel
import com.fefw.planner.viewmodel.activeDataOrNull

@Composable
fun OnboardingScreen(vm: PlannerViewModel) {
    val store by vm.store.collectAsStateWithLifecycle()
    val data = store.activeDataOrNull ?: return
    val order = vm.fe.recommendedOrder
    val orderLine = order.joinToString(" → ") { key ->
        if (key == data.route) "$key（本线）" else key
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.fefw.planner.ui.theme.Bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("选择本篇主角", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Text(
                "先选定要操作的主角线，规划器会据此显示本线可招募角色、名声与条件。",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
            Text("推荐通关顺序：$orderLine", style = MaterialTheme.typography.bodySmall, color = Muted)
        }

        vm.fe.routes.forEach { route ->
            OnboardingRouteCard(route = route, active = route.key == data.route) {
                vm.chooseRoute(route.key, goRecruit = true)
            }
        }

        Text(
            "进入后可用顶部常驻的主角线切换条随时换线。",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
        )
    }
}

@Composable
private fun OnboardingRouteCard(route: Route, active: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface1)
            .border(if (active) 2.dp else 1.dp, if (active) Primary else Border, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(999.dp)).background(parseHexColor(route.color, Primary)))
            Text(route.key, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        Text(route.feature, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        if (route.unlocks.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                route.unlocks.forEach { TagPill(it) }
            }
        }
        Text("名声来源：${route.fameSource}", style = MaterialTheme.typography.bodySmall, color = Muted)
        Text(route.note, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}