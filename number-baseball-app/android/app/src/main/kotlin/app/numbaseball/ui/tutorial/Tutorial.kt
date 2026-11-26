package app.numbaseball.ui.tutorial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.numbaseball.R
import app.numbaseball.core.ai.ComputerLevel
import app.numbaseball.core.protocol.RoundEntry
import app.numbaseball.ui.design.MemoMark
import app.numbaseball.ui.match.Memo
import app.numbaseball.ui.match.MatchActions
import app.numbaseball.ui.match.MatchMode
import app.numbaseball.ui.match.MatchPhase
import app.numbaseball.ui.match.MatchScreen
import app.numbaseball.ui.match.MatchUiState
import app.numbaseball.ui.match.MemoState
import app.numbaseball.ui.match.Opponent
import app.numbaseball.ui.match.OpponentStatus
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbType

/** Steps of the interactive tutorial (01 §2). The spec's step numbers 1–4 are [number]. */
enum class TutorialStep(val number: Int) { INTRO(1), FIRST_GUESS(2), EXPLAIN(3), OUT(3), SOLVE(4), DONE(4) }

/** Tutorial state; the hidden number is fixed (0 4 7) so the coach text is always right. Never counts in the record. */
data class TutorialState(
    val step: TutorialStep = TutorialStep.INTRO,
    val input: String = "",
    val rounds: List<RoundEntry> = emptyList(),
    val memo: MemoState = MemoState(Tutorial.DIGITS),
)

/** The tutorial's rules as pure functions (unit-tested). */
object Tutorial {
    const val DIGITS = 3
    const val SECRET = "047"
    const val FIRST_GUESS = "075"
    const val OUT_GUESS = "123"
    const val STEPS = 4

    fun judge(guess: String, round: Int): RoundEntry {
        val s = guess.indices.count { guess[it] == SECRET[it] }
        val b = guess.indices.count { guess[it] != SECRET[it] && guess[it] in SECRET }
        return RoundEntry(round, guess, s, b)
    }

    /** Digits can be typed in step 2 and 4 only; distinct, at most 3. */
    fun typing(state: TutorialState) = state.step == TutorialStep.FIRST_GUESS || state.step == TutorialStep.SOLVE

    fun digit(state: TutorialState, d: Char): TutorialState =
        if (!typing(state) || state.input.length >= DIGITS || d in state.input) state else state.copy(input = state.input + d)

    fun delete(state: TutorialState) = if (typing(state)) state.copy(input = state.input.dropLast(1)) else state

    fun clear(state: TutorialState) = if (typing(state)) state.copy(input = "") else state

    fun tapSlot(state: TutorialState, index: Int) = if (typing(state)) state.copy(input = state.input.take(index)) else state

    /** The key the coach asks for next in step 2 (2 dp chalk inset), or null. */
    fun expectedKey(state: TutorialState): Char? =
        if (state.step == TutorialStep.FIRST_GUESS) FIRST_GUESS.getOrNull(state.input.length)?.takeIf { FIRST_GUESS.startsWith(state.input) } else null

    /** Step 2 pitches only the coached guess (0 7 5); step 4 any full guess. */
    fun canPitch(state: TutorialState) = when (state.step) {
        TutorialStep.FIRST_GUESS -> state.input == FIRST_GUESS
        TutorialStep.SOLVE -> state.input.length == DIGITS
        else -> false
    }

    fun pitch(state: TutorialState): TutorialState {
        if (!canPitch(state)) return state
        val entry = judge(state.input, state.rounds.size + 1)
        val rounds = state.rounds + entry
        return when {
            state.step == TutorialStep.FIRST_GUESS -> state.copy(step = TutorialStep.EXPLAIN, input = "", rounds = rounds)
            entry.strikes == DIGITS -> state.copy(step = TutorialStep.DONE, input = "", rounds = rounds)
            else -> state.copy(input = "", rounds = rounds)
        }
    }

    /** "다음": intro → step 2; explanation → automatic second guess 1 2 3 (OUT); OUT → step 4 with a pre-marked memo. */
    fun next(state: TutorialState): TutorialState = when (state.step) {
        TutorialStep.INTRO -> state.copy(step = TutorialStep.FIRST_GUESS)
        TutorialStep.EXPLAIN -> state.copy(step = TutorialStep.OUT, rounds = state.rounds + judge(OUT_GUESS, state.rounds.size + 1))
        // One cell marked in (0 is a strike in position 1) shows how marking works; 1, 2, 3 and 5 are out from the hints.
        TutorialStep.OUT -> state.copy(
            step = TutorialStep.SOLVE,
            memo = Memo.cycleCell(Memo.cycleCell(MemoState(DIGITS), 0, 0), 0, 0).let { memo ->
                memo.copy(digitMarks = memo.digitMarks.mapIndexed { d, m -> if (d in listOf(1, 2, 3, 5)) MemoMark.OUT else m })
            },
        )
        else -> state
    }
}

/** What the match screen shows for the tutorial: top bar step + skip, the coach band, the action override. */
class TutorialUi(
    val step: Int,
    val total: Int,
    val coach: AnnotatedString,
    /** Replaces Pitch ("다음", "시작하기"); null = the normal Pitch key. */
    val actionLabel: String?,
    val onAction: (() -> Unit)?,
    val canPitch: Boolean,
    val expectedKey: Char?,
    val showMemo: Boolean,
    val slotPlaceholder: Char?,
    val legend: String?,
    val onSkip: () -> Unit,
)

/** `**bold**` markup of the coach strings → key words in `bodyStrong` `ink`. */
fun coachText(raw: String): AnnotatedString = buildAnnotatedString {
    raw.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = NbType.bodyStrong.fontWeight, color = NbColors.ink)) { append(part) } else append(part)
    }
}

@Composable
fun tutorialUi(state: TutorialState, onNext: () -> Unit, onDone: () -> Unit, onSkip: () -> Unit): TutorialUi {
    val coach = when (state.step) {
        TutorialStep.INTRO -> R.string.tut_1
        TutorialStep.FIRST_GUESS -> R.string.tut_2
        TutorialStep.EXPLAIN -> R.string.tut_3
        TutorialStep.OUT -> R.string.tut_out
        TutorialStep.SOLVE -> R.string.tut_4
        TutorialStep.DONE -> R.string.tut_done
    }
    val action = when (state.step) {
        TutorialStep.INTRO, TutorialStep.EXPLAIN, TutorialStep.OUT -> stringResource(R.string.tut_next) to onNext
        TutorialStep.DONE -> stringResource(R.string.tut_start) to onDone
        else -> null
    }
    return TutorialUi(
        step = state.step.number,
        total = Tutorial.STEPS,
        coach = coachText(stringResource(coach)),
        actionLabel = action?.first,
        onAction = action?.second,
        canPitch = Tutorial.canPitch(state),
        expectedKey = Tutorial.expectedKey(state),
        showMemo = state.step == TutorialStep.SOLVE || state.step == TutorialStep.DONE,
        slotPlaceholder = if (state.step == TutorialStep.INTRO) '?' else null,
        legend = if (state.step == TutorialStep.EXPLAIN) stringResource(R.string.tut_legend) else null,
        onSkip = onSkip,
    )
}

fun TutorialState.toMatchUiState() = MatchUiState(
    digits = Tutorial.DIGITS,
    opponent = Opponent.Computer(ComputerLevel.EASY),
    mode = MatchMode.COMPUTER,
    phase = MatchPhase.GUESSING,
    input = input,
    mySecret = null,
    round = rounds.size + 1,
    myRounds = rounds,
    opponentStatus = OpponentStatus.Thinking,
    memo = memo,
    // Keys are locked outside the two typing steps; the action key carries "다음" there.
    busy = !Tutorial.typing(this),
)

/**
 * The interactive tutorial (01 §2) on the real match screen: coach band instead of the duel strip, step "n/4" and
 * "건너뛰기" in the top bar, no pitch clock, the memo only in step 4. [onFinish] runs on skip and on "시작하기".
 */
@Composable
fun TutorialScreen(onFinish: () -> Unit, initial: TutorialState = TutorialState()) {
    var state by rememberSaveable(stateSaver = TutorialSaver) { mutableStateOf(initial) }
    val ui = tutorialUi(state, onNext = { state = Tutorial.next(state) }, onDone = onFinish, onSkip = onFinish)
    MatchScreen(
        state = state.toMatchUiState(),
        actions = MatchActions(
            onDigit = { state = Tutorial.digit(state, it) },
            onDelete = { state = Tutorial.delete(state) },
            onClear = { state = Tutorial.clear(state) },
            onTapSlot = { state = Tutorial.tapSlot(state, it) },
            onSubmit = { state = Tutorial.pitch(state) },
            onRandomSecret = {},
            onForfeit = onFinish,
            onLeave = onFinish,
            onMemoDigit = { d -> state = state.copy(memo = Memo.cycleDigit(state.memo, d)) },
            onMemoCell = { p, d -> state = state.copy(memo = Memo.cycleCell(state.memo, p, d)) },
            onMemoClear = { state = state.copy(memo = Memo.clear(state.memo)) },
            onMemoCollapse = { c -> state = state.copy(memo = Memo.setCollapsed(state.memo, c)) },
        ),
        tutorial = ui,
    )
}

/** Saves step + input + rounds (the memo restarts from the step's preset). */
private val TutorialSaver = androidx.compose.runtime.saveable.listSaver<TutorialState, String>(
    save = { s -> listOf(s.step.name, s.input) + s.rounds.mapNotNull { it.guess } },
    restore = { list ->
        val step = TutorialStep.valueOf(list[0])
        val rounds = list.drop(2).mapIndexed { i, g -> Tutorial.judge(g, i + 1) }
        val base = TutorialState(step = step, input = list[1], rounds = rounds)
        if (step == TutorialStep.SOLVE || step == TutorialStep.DONE) base.copy(memo = Tutorial.next(base.copy(step = TutorialStep.OUT)).memo) else base
    },
)
