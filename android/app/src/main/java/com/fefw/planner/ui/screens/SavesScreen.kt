package com.fefw.planner.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fefw.planner.data.ImportResult
import com.fefw.planner.data.Slot
import com.fefw.planner.data.recruitsFor
import com.fefw.planner.ui.components.Badge
import com.fefw.planner.ui.components.BadgeKind
import com.fefw.planner.ui.components.FefwTextField
import com.fefw.planner.ui.components.PanelCard
import com.fefw.planner.ui.theme.Border
import com.fefw.planner.ui.theme.ErrorRed
import com.fefw.planner.ui.theme.Muted
import com.fefw.planner.ui.theme.Primary
import com.fefw.planner.ui.theme.Surface1
import com.fefw.planner.ui.theme.TextPrimary
import com.fefw.planner.ui.theme.TextSecondary
import com.fefw.planner.viewmodel.PlannerViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavesScreen(vm: PlannerViewModel) {
    val store by vm.store.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var renameText by remember { mutableStateOf("") }
    var renameSlotId by remember { mutableStateOf<String?>(null) }
    var deleteSlotId by remember { mutableStateOf<String?>(null) }
    var clearOpen by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var importSingle by remember { mutableStateOf<ImportResult.Single?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val text = pendingExport
        pendingExport = null
        if (uri != null && text != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            }.isSuccess
            if (!ok) vm.reportError("导出失败")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull()
            if (text == null) {
                vm.reportError("读取文件失败")
            } else {
                when (val r = vm.parseImportText(text)) {
                    is ImportResult.All -> vm.applyImportAll(r.slots)
                    is ImportResult.Single -> importSingle = r
                    is ImportResult.Invalid -> vm.reportError(r.message)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelCard {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ActionBtn("新建槽") { vm.newSlot() }
                ActionBtn("导出当前槽") {
                    pendingExport = vm.exportCurrentJson()
                    exportLauncher.launch("fefw-plan-slot.json")
                }
                ActionBtn("导出全部") {
                    pendingExport = vm.exportAllJson()
                    exportLauncher.launch("fefw-plan-all.json")
                }
                ActionBtn("导入文件") { importLauncher.launch(arrayOf("*/*")) }
                ActionBtn("清空当前槽") { clearOpen = true }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(store.slots, key = { it.id }) { slot ->
                SlotRow(
                    vm = vm,
                    slot = slot,
                    active = slot.id == store.activeId,
                    onRename = { renameSlotId = slot.id; renameText = slot.name },
                    onDelete = { deleteSlotId = slot.id }
                )
            }
        }

        Text(
            "数据仅保存在本机 App 私有目录（files/planner.json），不会联网传输。",
            style = MaterialTheme.typography.bodySmall,
            color = Muted
        )
    }

    renameSlotId?.let { id ->
        AlertDialog(
            onDismissRequest = { renameSlotId = null },
            containerColor = Surface1,
            title = { Text("重命名存档", color = TextPrimary) },
            text = {
                FefwTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    placeholder = "存档名称",
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.renameSlot(id, renameText); renameSlotId = null }) {
                    Text("确定", color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameSlotId = null }) { Text("取消", color = Muted) }
            }
        )
    }

    deleteSlotId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteSlotId = null },
            containerColor = Surface1,
            title = { Text("删除存档", color = TextPrimary) },
            text = { Text("删除后该存档不可恢复，确认删除？", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { vm.deleteSlot(id); deleteSlotId = null }) {
                    Text("删除", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteSlotId = null }) { Text("取消", color = Muted) }
            }
        )
    }

    if (clearOpen) {
        AlertDialog(
            onDismissRequest = { clearOpen = false },
            containerColor = Surface1,
            title = { Text("清空当前槽", color = TextPrimary) },
            text = { Text("将重置当前存档中的所有规划（挖角/转职/坐骑），不可恢复。", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { vm.clearCurrent(); clearOpen = false }) {
                    Text("清空", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { clearOpen = false }) { Text("取消", color = Muted) }
            }
        )
    }

    importSingle?.let { imp ->
        AlertDialog(
            onDismissRequest = { importSingle = null },
            containerColor = Surface1,
            title = { Text("导入存档「${imp.name}」", color = TextPrimary) },
            text = { Text("选择要如何处理这份存档。", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    vm.applyImportSingle(imp.name, imp.data, replaceCurrent = true)
                    importSingle = null
                }) {
                    Text("替换当前", color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.applyImportSingle(imp.name, imp.data, replaceCurrent = false)
                    importSingle = null
                }) {
                    Text("新建槽", color = Primary)
                }
            }
        )
    }
}

@Composable
private fun ActionBtn(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
private fun SlotRow(
    vm: PlannerViewModel,
    slot: Slot,
    active: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val plannedCount = slot.data.recruitsFor(slot.data.route).values.count { it.planned }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(if (active) 2.dp else 1.dp, if (active) Primary else Border, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(slot.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            if (active) Badge("当前", BadgeKind.Scout)
        }
        Text(
            "主角线：${slot.data.route} · 已计划 $plannedCount 人",
            style = MaterialTheme.typography.bodySmall,
            color = Muted
        )
        Text("更新于 ${slot.updatedAt.take(16).replace("T", " ")}", style = MaterialTheme.typography.bodySmall, color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SlotOp("切换", enabled = !active) { vm.switchSlot(slot.id) }
            SlotOp("重命名", onClick = onRename)
            SlotOp("复制") { vm.duplicateSlot(slot.id) }
            SlotOp("删除", onClick = onDelete)
        }
    }
}

@Composable
private fun SlotOp(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (enabled) Primary else Muted,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(4.dp)
    )
}