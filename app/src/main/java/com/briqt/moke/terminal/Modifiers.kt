/* Modified for Resume Terminal (personal Moke fork), 2026-10-01.
 * Original copyright and licenses retained; see COPYRIGHT.md. */
package com.briqt.moke.terminal

/** 附加键上的修饰键。Shift 只作用于附加键与宏（Shift+Tab / Shift+方向），字母大小写仍归输入法。 */
enum class ModKind { Ctrl, Alt, Shift }

/**
 * 点按切换关闭/下一键生效；长按显式锁定。点按任何已启用状态都能取消。
 */
enum class ModState {
    Off, Once, Locked;

    val active: Boolean get() = this != Off

    fun next(): ModState = when (this) {
        Off -> Once
        Once -> Off
        Locked -> Off
    }
}

/** 三个修饰键的当前状态。纯数据，供 UI 与编码器共用。 */
data class Modifiers(
    val ctrl: ModState = ModState.Off,
    val alt: ModState = ModState.Off,
    val shift: ModState = ModState.Off,
) {
    val ctrlOn: Boolean get() = ctrl.active
    val altOn: Boolean get() = alt.active
    val shiftOn: Boolean get() = shift.active

    fun state(kind: ModKind): ModState = when (kind) {
        ModKind.Ctrl -> ctrl
        ModKind.Alt -> alt
        ModKind.Shift -> shift
    }

    fun toggle(kind: ModKind): Modifiers = when (kind) {
        ModKind.Ctrl -> copy(ctrl = ctrl.next())
        ModKind.Alt -> copy(alt = alt.next())
        ModKind.Shift -> copy(shift = shift.next())
    }

    fun lock(kind: ModKind): Modifiers = when (kind) {
        ModKind.Ctrl -> copy(ctrl = ModState.Locked)
        ModKind.Alt -> copy(alt = ModState.Locked)
        ModKind.Shift -> copy(shift = ModState.Locked)
    }

    /** 一次性修饰被一个按键消费后复位；锁定态保持不变。 */
    fun consumeOnce(): Modifiers = Modifiers(
        ctrl = if (ctrl == ModState.Once) ModState.Off else ctrl,
        alt = if (alt == ModState.Once) ModState.Off else alt,
        shift = if (shift == ModState.Once) ModState.Off else shift,
    )

    /** 按当前修饰把一个按键编码成字节序列。 */
    fun encode(key: KeyId): String = KeySeq.encode(key, ctrl = ctrlOn, alt = altOn, shift = shiftOn)
}
