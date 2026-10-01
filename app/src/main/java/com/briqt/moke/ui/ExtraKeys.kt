/* Modified for Resume Terminal (personal Moke fork), 2026-10-01.
 * Original copyright and licenses retained; see COPYRIGHT.md. */
package com.briqt.moke.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.input.key.onPreviewKeyEvent
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.briqt.moke.R
import com.briqt.moke.terminal.KeyId
import com.briqt.moke.terminal.QuickShortcut
import com.briqt.moke.terminal.KeySeq
import com.briqt.moke.terminal.ModKind
import com.briqt.moke.terminal.ModState
import com.briqt.moke.terminal.Modifiers
import com.briqt.moke.ui.theme.MokeMono
import com.briqt.moke.ui.theme.MokeShapes

/** 附加键：普通按键（字节由 `KeySeq` 按当前修饰统一编码）/ 修饰键 / 动作键。 */
sealed interface ExtraKey {
    val label: String

    /** 普通按键：只描述"按了哪个键"，序列交给编码器，故能与 Ctrl/Alt/Shift 组合。 */
    data class Key(override val label: String, val key: KeyId) : ExtraKey
    /** 修饰键（三态：一次性 / 锁定 / 关）。 */
    data class Mod(override val label: String, val kind: ModKind) : ExtraKey
    /**
     * 动作键（[id] 交给上层处理）。
     *
     * 文本和面板入口按 id 本地化；命令按钮可提供紧凑标签。
     */
    data class Action(val id: String, override val label: String = "") : ExtraKey
}

/** 动作键 id：文本段入口、展开全键盘面板。 */
const val ACTION_COMPOSER = "composer"
const val ACTION_PANEL = "panel"
const val ACTION_MODEL = "model"
const val ACTION_RESUME = "resume"
const val ACTION_PASTE = "paste"
const val ACTION_COPY = "copy"

/*
 * 收录标准（rc.3 重定）：**只放软键盘给不了的键**。
 *
 * 字母、数字、标点（含 `| ~ \ {} <>`）输入法都打得出来，摆在这里等于用最贵的屏幕位置重复一遍
 * 已有的功能。只有实体全键盘才有的是：修饰键、Esc/Tab、方向与翻页、行首行尾、F1–F12、
 * Ctrl 组合、Shift+Tab。按这条线砍掉了 rc.2 的整页符号，以及散在导航行里的 `/` `-`。
 *
 * 分工：**常驻两排 = 给不了 ∩ 高频**；**面板 = 给不了的其余全部**（外加 Enter/⌫ 两个
 * "软键盘被隐藏时"的兜底键）。同一个键不在两处重复出现——面板就浮在常驻两排上方，
 * 重复只会让人分不清该按哪个，也是 rc.2 显得乱的主要来源。
 */

/**
 * 常驻双排附加键：均匀铺满宽度、不横向滚动，中间三列保持倒 T 方向键。
 *
 * 粘贴常驻；Shift+Tab 用修饰键组合。有输出选区时，中断键改为复制。
 */
val DEFAULT_EXTRA_KEYS: List<List<ExtraKey>> = extraKeyRows(QuickShortcut.SHIFT_LEFT)

fun extraKeyRows(shortcut: QuickShortcut, selectingText: Boolean = false): List<List<ExtraKey>> = listOf(
    listOf(
        ExtraKey.Key("ESC", KeyId.Esc),
        ExtraKey.Mod("CTRL", ModKind.Ctrl),
        ExtraKey.Mod("ALT", ModKind.Alt),
        ExtraKey.Key("↑", KeyId.Up),
        ExtraKey.Mod("SHIFT", ModKind.Shift),
        when (shortcut) {
            QuickShortcut.MODEL -> ExtraKey.Action(ACTION_MODEL, shortcut.label)
            QuickShortcut.RESUME -> ExtraKey.Action(ACTION_RESUME, shortcut.label)
            else -> ExtraKey.Key(shortcut.label, shortcut.key)
        },
        ExtraKey.Action(ACTION_PANEL),
    ),
    listOf(
        ExtraKey.Key("TAB", KeyId.Tab),
        ExtraKey.Action(ACTION_PASTE, "^V"),
        ExtraKey.Key("←", KeyId.Left),
        ExtraKey.Key("↓", KeyId.Down),
        ExtraKey.Key("→", KeyId.Right),
        if (selectingText) ExtraKey.Action(ACTION_COPY)
        else ExtraKey.Key("^C", KeyId.Macro(ctrlOf('c'))),
        ExtraKey.Action(ACTION_COMPOSER),
    ),
)

/** 全键盘面板的一个分组（面板是单页竖排，分组只作视觉分区，不再有分段切换）。 */
data class KeySection(
    val titleRes: Int,
    val rows: List<List<ExtraKey>>,
    val secondaryRows: List<List<ExtraKey>> = emptyList(),
)

private fun macro(label: String, bytes: String) = ExtraKey.Key(label, KeyId.Macro(bytes))

/** Ctrl+字母的字节（宏用；标签沿用终端惯例的 `^X` 写法）。 */
private fun ctrlOf(c: Char) = ((c.uppercaseChar().code - 64)).toChar().toString()

/** 常用编辑键默认可见；低频控制键和功能键在“全部按键”中展开。 */
val KEY_SECTIONS: List<KeySection> = listOf(
    KeySection(
        R.string.keys_section_edit,
        listOf(
            listOf(
                ExtraKey.Key("END", KeyId.End),
                ExtraKey.Key("HOME", KeyId.Home),
                ExtraKey.Key("PgUp", KeyId.PageUp),
                ExtraKey.Key("PgDn", KeyId.PageDown),
            ),
            listOf(
                ExtraKey.Key("INS", KeyId.Insert),
                ExtraKey.Key("DEL", KeyId.Delete),
                // Enter/⌫ 输入法上有，这里留一份是给"隐藏软键盘只看输出"的场景兜底。
                ExtraKey.Key("⌫", KeyId.Backspace),
                ExtraKey.Key("Enter", KeyId.Enter),
            ),
        ),
    ),
    KeySection(
        R.string.keys_section_ctrl,
        listOf(
            // 行编辑：行首 / 行尾 / 删到行首 / 删到行尾 / 删词 / 粘回 / 清屏。
            listOf(
                macro("^A", ctrlOf('a')),
                macro("^E", ctrlOf('e')),
                macro("^U", ctrlOf('u')),
                macro("^K", ctrlOf('k')),
                macro("^W", ctrlOf('w')),
                macro("^R", ctrlOf('r')),
                macro("^L", ctrlOf('l')),
            ),
        ),
        secondaryRows = listOf(
            // Less frequent controls only appear after expanding all keys.
            listOf(
                macro("^D", ctrlOf('d')),
                macro("^Z", ctrlOf('z')),
                macro("^Y", ctrlOf('y')),
                macro("^P", ctrlOf('p')),
                macro("^N", ctrlOf('n')),
                macro("^B", ctrlOf('b')),
                // 标签不用 ⌥：等宽字体里没有该字形，真机上会渲染成豆腐块。
                macro("ALT↵", "\u001b\r"),
            ),
        ),
    ),
    KeySection(
        R.string.keys_section_fn,
        rows = emptyList(),
        secondaryRows = listOf(
            (1..6).map { ExtraKey.Key("F$it", KeyId.Fn(it)) },
            (7..12).map { ExtraKey.Key("F$it", KeyId.Fn(it)) },
        ),
    ),
)

@Composable
fun ExtraKeys(
    rows: List<List<ExtraKey>>,
    mods: Modifiers,
    panelOpen: Boolean = false,
    composerOpen: Boolean = false,
    onKey: (KeyId) -> Unit,
    onToggleMod: (ModKind) -> Unit,
    onAction: (String) -> Unit,
    onLockMod: (ModKind) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            rows.forEach { row ->
                KeyRow(row, mods, panelOpen, onKey, onToggleMod, onAction, composerOpen, onLockMod)
            }
        }
    }
}

@Composable
private fun KeyRow(
    row: List<ExtraKey>,
    mods: Modifiers,
    panelOpen: Boolean,
    onKey: (KeyId) -> Unit,
    onToggleMod: (ModKind) -> Unit,
    onAction: (String) -> Unit,
    composerOpen: Boolean = false,
    onLockMod: (ModKind) -> Unit = {},
) {
    val pasteDescription = stringResource(R.string.key_paste)
    val interruptDescription = stringResource(R.string.key_interrupt)
    val modOff = stringResource(R.string.modifier_off)
    val modOnce = stringResource(R.string.modifier_once)
    val modLocked = stringResource(R.string.modifier_locked)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        row.forEach { key ->
            val state = if (key is ExtraKey.Mod) mods.state(key.kind) else ModState.Off
            // 「文本段」与「更多」用图标（与符号键风格一致、无需 i18n）；本地化文案作无障碍描述。
            val isComposer = key is ExtraKey.Action && key.id == ACTION_COMPOSER
            val isPanel = key is ExtraKey.Action && key.id == ACTION_PANEL
            val isPaste = key is ExtraKey.Action && key.id == ACTION_PASTE
            val isCopy = key is ExtraKey.Action && key.id == ACTION_COPY
            val isInterrupt = key is ExtraKey.Key && key.key == KeyId.Macro("\u0003")
            val label = when {
                isComposer -> stringResource(R.string.key_text)
                isPanel -> stringResource(R.string.key_more)
                isCopy -> stringResource(R.string.copy_text)
                isPaste -> stringResource(R.string.paste_text)
                isInterrupt -> stringResource(R.string.key_interrupt_label)
                else -> key.label
            }
            KeyCap(
                label = label,
                icon = when {
                    isComposer -> Icons.Filled.EditNote
                    isPanel -> if (panelOpen) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp
                    else -> null
                },
                // 一次性修饰用主色实心，锁定态用更强的色块区分——否则分不清"这次有效"和"一直有效"。
                active = state.active || (isPanel && panelOpen) || (isComposer && composerOpen),
                locked = state == ModState.Locked,
                modifier = Modifier.weight(1f).semantics {
                    if (isPaste) contentDescription = pasteDescription
                    if (isInterrupt) contentDescription = interruptDescription
                    if (key is ExtraKey.Mod) stateDescription = when (state) {
                        ModState.Off -> modOff
                        ModState.Once -> modOnce
                        ModState.Locked -> modLocked
                    }
                },
                repeatable = key is ExtraKey.Key && key.key in listOf(KeyId.Up, KeyId.Down, KeyId.Left, KeyId.Right, KeyId.Backspace, KeyId.Delete, KeyId.PageUp, KeyId.PageDown),
                onClick = {
                    when (key) {
                        is ExtraKey.Key -> onKey(key.key)
                        is ExtraKey.Mod -> onToggleMod(key.kind)
                        is ExtraKey.Action -> onAction(key.id)
                    }
                },
                onLongClick = if (key is ExtraKey.Mod) ({ onLockMod(key.kind) }) else null,
            )
        }
    }
}

/**
 * 展开的全键盘面板：**浮在终端之上**（调用方把它放进终端 Box），不挤压终端——
 * 挤压会改行数触发远端 SIGWINCH，全屏 TUI 会跟着重绘抖动。
 *
 * 单页竖排、分组标题分区，**没有分段切换、没有 pager**：内容与外框同一次布局产出，
 * 不存在"边框先动内容后动"。内容比可用高度长（横屏）时整块滚动，面板本身不改高。
 * 顶部那根横条是收起把手（点一下收起），与常驻行上的「更多」键（展开时高亮成 ⌄）互为两条出路。
 */
@Composable
fun KeyboardPanel(
    sections: List<KeySection>,
    mods: Modifiers,
    onKey: (KeyId) -> Unit,
    onToggleMod: (ModKind) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    quickShortcut: QuickShortcut = QuickShortcut.SHIFT_LEFT,
    onConfigureShortcut: () -> Unit = {},
    onCommand: (String) -> Unit = {},
) {
    val config = LocalConfiguration.current
    var allKeys by remember { mutableStateOf(false) }
    // 最高只吃屏幕的 6 成：再高就把终端整块盖没了。父 Box 更矮时由父约束接管，内容滚动。
    val maxHeight = (config.screenHeightDp * 0.6f).dp
    // 横屏矮而宽：竖屏的 5 排在这里放不下（只能滚），但一排塞得下十几个键——
    // 把每组压成尽量少的排，三组一次全见，不用滚。
    val perRow = if (config.screenWidthDp >= 600) 14 else 0
    val collapseLabel = stringResource(R.string.keys_panel_collapse)
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(max = maxHeight),
        color = MaterialTheme.colorScheme.surface,
        shape = MokeShapes.keyPanel,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 5.dp)
                .padding(bottom = 6.dp),
        ) {
            // 收起把手：整条可点，命中区域比一个图标按钮大，也不占一整行高度。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clickable(onClick = onDismiss)
                    .semantics { contentDescription = collapseLabel },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.width(34.dp).height(4.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                ) {}
            }
            Row(Modifier.fillMaxWidth()) {
                if (quickShortcut != QuickShortcut.MODEL) TextButton(onClick = { onCommand("/model") }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.codex_model_command))
                }
                if (quickShortcut != QuickShortcut.RESUME) TextButton(onClick = { onCommand("/resume") }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.codex_resume_command))
                }
            }
            TextButton(onClick = { allKeys = !allKeys }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (allKeys) R.string.keys_show_less else R.string.keys_show_all))
            }
            sections.forEachIndexed { index, section ->
                val visibleRows = section.rows + if (allKeys) section.secondaryRows else emptyList()
                if (visibleRows.isEmpty()) return@forEachIndexed
                Text(
                    stringResource(section.titleRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 3.dp, top = if (index == 0) 0.dp else 9.dp, bottom = 4.dp),
                )
                val rows = if (perRow > 0) visibleRows.flatten().chunked(perRow) else visibleRows
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    rows.forEach { row ->
                        KeyRow(row, mods, panelOpen = false, onKey = onKey, onToggleMod = onToggleMod, onAction = {})
                    }
                }
            }
            if (allKeys) TextButton(onClick = onConfigureShortcut, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.quick_shortcut_configure, quickShortcut.title))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun KeyCap(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    icon: ImageVector? = null,
    repeatable: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val currentClick by rememberUpdatedState(onClick)
    var repeated by remember { mutableStateOf(false) }
    LaunchedEffect(pressed, repeatable) {
        if (pressed && repeatable) {
            repeated = false
            delay(400)
            while (true) {
                repeated = true
                currentClick()
                delay(75)
            }
        }
    }
    // Give a thumb more vertical room while preserving two visible rows above the IME.
    Surface(
        modifier = modifier.height(42.dp).combinedClickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            onClick = { if (!repeated) currentClick(); repeated = false },
            onLongClick = onLongClick,
        ),
        shape = MokeShapes.keycap,
        color = when {
            locked -> MaterialTheme.colorScheme.tertiary
            active -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = when {
            locked -> MaterialTheme.colorScheme.onTertiary
            active -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurface
        },
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (icon != null) {
                // 图标键（文本段 / 更多）：label 作无障碍描述，视觉用图标。
                Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
            } else {
                // 方向键单字符符号（↑ ↓ ← →）本身偏小、看不清，放大到 17sp；其余文字标签（含 Enter）保持 13sp。
                val glyph = label.length == 1 && label[0] in "↑↓←→"
                Text(label, fontFamily = MokeMono, fontSize = if (glyph) 17.sp else 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            if (locked) Icon(Icons.Filled.Lock, contentDescription = null,
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(10.dp))
        }
    }
}

/** Compact command draft with an optional multiline prompt editor. Shortcut rows stay visible. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TextBlockComposer(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onDismiss: () -> Unit,
    onSend: (appendEnter: Boolean) -> Unit,
    onKey: (KeyId, ctrl: Boolean, alt: Boolean, shift: Boolean) -> Unit,
    focusRequester: FocusRequester,
    isDraftEmpty: () -> Boolean,
    onPaste: () -> Unit,
    onCopy: () -> Unit,
    outputSelected: Boolean,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var multiline by remember { mutableStateOf(false) }
    var clipboardCopyKeyDown by remember { mutableStateOf(false) }
    val expanded = multiline || value.text.contains('\n')
    val currentEmpty by rememberUpdatedState(isDraftEmpty)
    val currentKey by rememberUpdatedState(onKey)
    // Keep the interceptor identity stable: recreating it tears down the active IME session.
    val inputInterceptor = remember {
        PlatformTextInputInterceptor { request, nextHandler ->
            nextHandler.startInputMethod(object : PlatformTextInputMethodRequest {
                override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
                    val target = request.createInputConnection(outAttributes)
                    outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                        EditorInfo.IME_FLAG_NO_FULLSCREEN
                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                        outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                    }
                    return DraftInputConnection(target, { currentEmpty() },
                        { key -> currentKey(key, false, false, false) })
                }
            })
        }
    }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    InterceptPlatformTextInput(inputInterceptor) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = onValueChange,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 144.dp).focusRequester(focusRequester)
                                .onPreviewKeyEvent { event ->
                                    val native = event.nativeKeyEvent
                                    if (native.keyCode == KeyEvent.KEYCODE_C && native.action == KeyEvent.ACTION_UP)
                                        clipboardCopyKeyDown = false
                                    if (native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                                    if (native.keyCode == KeyEvent.KEYCODE_C) {
                                        if (clipboardCopyKeyDown && native.repeatCount > 0) return@onPreviewKeyEvent true
                                        if (native.repeatCount == 0) clipboardCopyKeyDown = false
                                    }
                                    // Clipboard shortcuts belong to the local draft, never the remote process.
                                    if (native.isCtrlPressed && !native.isAltPressed) {
                                        if (value.text.isNotEmpty() && native.keyCode in listOf(
                                                KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_Y) ||
                                            !value.selection.collapsed && native.keyCode == KeyEvent.KEYCODE_X) {
                                            return@onPreviewKeyEvent false // Local select-all, undo/redo and cut.
                                        }
                                        if (native.keyCode == KeyEvent.KEYCODE_V) {
                                            if (native.repeatCount == 0) onPaste()
                                            return@onPreviewKeyEvent true
                                        }
                                        if (native.keyCode == KeyEvent.KEYCODE_C &&
                                            (!value.selection.collapsed || outputSelected || native.isShiftPressed)) {
                                            clipboardCopyKeyDown = true
                                            if (native.repeatCount == 0) onCopy()
                                            return@onPreviewKeyEvent true
                                        }
                                    }
                                    val key = when (native.keyCode) {
                                        KeyEvent.KEYCODE_TAB -> KeyId.Tab
                                        KeyEvent.KEYCODE_ESCAPE -> KeyId.Esc
                                        KeyEvent.KEYCODE_DEL -> if (isDraftEmpty()) KeyId.Backspace else null
                                        KeyEvent.KEYCODE_FORWARD_DEL -> if (isDraftEmpty()) KeyId.Delete else null
                                        KeyEvent.KEYCODE_ENTER -> if (!expanded || native.isAltPressed || native.isCtrlPressed || native.isShiftPressed) KeyId.Enter else null
                                        KeyEvent.KEYCODE_DPAD_UP -> if (native.isAltPressed || native.isCtrlPressed) KeyId.Up else null
                                        KeyEvent.KEYCODE_DPAD_DOWN -> if (native.isAltPressed || native.isCtrlPressed) KeyId.Down else null
                                        KeyEvent.KEYCODE_DPAD_LEFT -> if (native.isAltPressed || native.isCtrlPressed || native.isShiftPressed) KeyId.Left else null
                                        KeyEvent.KEYCODE_DPAD_RIGHT -> if (native.isAltPressed || native.isCtrlPressed || native.isShiftPressed) KeyId.Right else null
                                        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> if (native.isCtrlPressed || native.isAltPressed)
                                            KeyId.Chars(('a'.code + native.keyCode - KeyEvent.KEYCODE_A).toChar().toString()) else null
                                        else -> null
                                    }
                                    if (key == null) false else {
                                        onKey(key, native.isCtrlPressed, native.isAltPressed, native.isShiftPressed)
                                        true
                                    }
                                },
                            singleLine = !expanded,
                            minLines = if (expanded) 2 else 1,
                            placeholder = { Text(stringResource(if (expanded) R.string.composer_prompt else R.string.composer_field), maxLines = 1) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MokeMono),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                imeAction = if (expanded) ImeAction.Default else ImeAction.Send,
                            ),
                            keyboardActions = KeyboardActions(onSend = { onSend(true) }),
                            leadingIcon = {
                                IconButton(onClick = { multiline = !multiline }) {
                                    Icon(Icons.Filled.EditNote, contentDescription = stringResource(
                                        if (expanded) R.string.composer_command_mode else R.string.composer_multiline_mode),
                                        tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            trailingIcon = {
                                IconButton(onClick = { onSend(true) }) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.composer_send_enter))
                                }
                            },
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
            if (expanded) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.composer_multiline_hint), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onValueChange(TextFieldValue()) }, enabled = value.text.isNotEmpty()) {
                        Text(stringResource(R.string.composer_clear))
                    }
                    TextButton(onClick = { onSend(false) }, enabled = value.text.isNotEmpty()) {
                        Text(stringResource(R.string.composer_send))
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
}

/**
 * 底部快捷键被隐藏后留下的把手：点一下恢复。
 *
 * 全键盘面板与文本段输入的入口都在快捷键行上，隐藏之后若什么都不留，这两个能力就只剩
 * 「回 ⋮ 菜单重新显示」一条隐蔽的出路。样式与全键盘面板顶部的收起把手一致。
 */
@Composable
fun ExtraKeysRestoreHandle(onRestore: () -> Unit) {
    val label = stringResource(R.string.show_extra_keys)
    Surface(color = MaterialTheme.colorScheme.surface) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clickable(onClick = onRestore)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.width(34.dp).height(4.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            ) {}
        }
    }
}


@Composable
fun QuickShortcutDialog(selected: QuickShortcut, onDismiss: () -> Unit, onSelect: (QuickShortcut) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_shortcut_title)) },
        text = {
            Column {
                Text(stringResource(R.string.quick_shortcut_hint), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.quick_shortcut_scroll_hint), style = MaterialTheme.typography.bodySmall)
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    QuickShortcut.entries.forEach { shortcut ->
                        TextButton(onClick = { onSelect(shortcut) }, modifier = Modifier.fillMaxWidth()) {
                            Text((if (shortcut == selected) "✓ " else "") + shortcut.title)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.quick_shortcut_close)) } },
    )
}
