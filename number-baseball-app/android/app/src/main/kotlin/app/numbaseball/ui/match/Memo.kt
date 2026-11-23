package app.numbaseball.ui.match

import app.numbaseball.ui.design.MemoMark

/**
 * The memo board (DESIGN_SYSTEM 12.8): a status per digit 0–9 and a grid of positions × digits. Local only, cleared when
 * the duel ends, never sent to the server. [collapsed] is the user's choice (null = automatic).
 */
data class MemoState(
    val digits: Int,
    val digitMarks: List<MemoMark> = List(10) { MemoMark.NONE },
    val cells: List<List<MemoMark>> = List(digits) { List(10) { MemoMark.NONE } },
    val collapsed: Boolean? = null,
) {
    val isEmpty: Boolean get() = digitMarks.all { it == MemoMark.NONE } && cells.all { row -> row.all { it == MemoMark.NONE } }

    /** Marks shown on the keypad keys (they mirror the digit row; keys never change memo state). */
    val keyMarks: Map<Char, MemoMark> get() = digitMarks.withIndex().associate { ('0' + it.index) to it.value }
}

object Memo {
    private fun MemoMark.next() = when (this) {
        MemoMark.NONE -> MemoMark.OUT
        MemoMark.OUT -> MemoMark.IN
        MemoMark.IN -> MemoMark.NONE
    }

    /** Digit status cycle: unknown → out → in → unknown. */
    fun cycleDigit(state: MemoState, digit: Int): MemoState =
        state.copy(digitMarks = state.digitMarks.mapIndexed { i, m -> if (i == digit) m.next() else m })

    /**
     * Position cell cycle: unknown → out → in → unknown. Setting a cell to in marks every other unknown cell in that row
     * and column as out and sets the digit's status to in (same as the web prototype).
     */
    fun cycleCell(state: MemoState, position: Int, digit: Int): MemoState {
        val next = state.cells[position][digit].next()
        var cells = state.cells.mapIndexed { p, row -> row.mapIndexed { d, m -> if (p == position && d == digit) next else m } }
        var marks = state.digitMarks
        if (next == MemoMark.IN) {
            cells = cells.mapIndexed { p, row ->
                row.mapIndexed { d, m ->
                    val sameLine = (p == position) != (d == digit) // same row or same column, not the cell itself
                    if (sameLine && m == MemoMark.NONE) MemoMark.OUT else m
                }
            }
            marks = marks.mapIndexed { d, m -> if (d == digit) MemoMark.IN else m }
        }
        return state.copy(cells = cells, digitMarks = marks)
    }

    fun clear(state: MemoState): MemoState = MemoState(state.digits, collapsed = state.collapsed)

    fun setCollapsed(state: MemoState, collapsed: Boolean): MemoState = state.copy(collapsed = collapsed)
}
