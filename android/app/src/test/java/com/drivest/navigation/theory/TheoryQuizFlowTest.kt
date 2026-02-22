package com.drivest.navigation.theory

import com.drivest.navigation.theory.quiz.TheoryNextAction
import com.drivest.navigation.theory.quiz.TheoryQuizFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class TheoryQuizFlowTest {

    @Test
    fun blockedWhenNoSelectionAndNoSubmission() {
        val action = TheoryQuizFlow.decideNextAction(
            answerSubmitted = false,
            hasSelectedAnswer = false,
            isLastQuestion = false
        )
        assertEquals(TheoryNextAction.BLOCKED_NO_SELECTION, action)
    }

    @Test
    fun submitThenAdvanceWhenSelectionExistsButNotSubmitted() {
        val action = TheoryQuizFlow.decideNextAction(
            answerSubmitted = false,
            hasSelectedAnswer = true,
            isLastQuestion = false
        )
        assertEquals(TheoryNextAction.SUBMIT_THEN_ADVANCE, action)
    }

    @Test
    fun advanceWhenSubmittedAndNotLast() {
        val action = TheoryQuizFlow.decideNextAction(
            answerSubmitted = true,
            hasSelectedAnswer = true,
            isLastQuestion = false
        )
        assertEquals(TheoryNextAction.ADVANCE, action)
    }

    @Test
    fun finishWhenSubmittedAndLast() {
        val action = TheoryQuizFlow.decideNextAction(
            answerSubmitted = true,
            hasSelectedAnswer = true,
            isLastQuestion = true
        )
        assertEquals(TheoryNextAction.FINISH, action)
    }
}
