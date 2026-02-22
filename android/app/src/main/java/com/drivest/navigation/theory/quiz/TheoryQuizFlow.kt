package com.drivest.navigation.theory.quiz

enum class TheoryNextAction {
    BLOCKED_NO_SELECTION,
    SUBMIT_THEN_ADVANCE,
    ADVANCE,
    FINISH
}

object TheoryQuizFlow {
    fun decideNextAction(
        answerSubmitted: Boolean,
        hasSelectedAnswer: Boolean,
        isLastQuestion: Boolean
    ): TheoryNextAction {
        if (!answerSubmitted && !hasSelectedAnswer) {
            return TheoryNextAction.BLOCKED_NO_SELECTION
        }
        if (!answerSubmitted && hasSelectedAnswer) {
            return TheoryNextAction.SUBMIT_THEN_ADVANCE
        }
        return if (isLastQuestion) {
            TheoryNextAction.FINISH
        } else {
            TheoryNextAction.ADVANCE
        }
    }
}
