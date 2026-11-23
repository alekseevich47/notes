package com.alekseevich47.notes

import androidx.compose.ui.text.TextRange
import com.alekseevich47.notes.ui.editor.WordBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordBoundsTest {
    @Test
    fun middleStartAndEndOfAWord() {
        val text = "alpha beta"
        assertEquals(TextRange(0, 5), WordBounds.at(text, 0))
        assertEquals(TextRange(0, 5), WordBounds.at(text, 2))
        assertEquals(TextRange(0, 5), WordBounds.at(text, 4))
        assertEquals(TextRange(6, 10), WordBounds.at(text, 6))
        assertEquals(TextRange(6, 10), WordBounds.at(text, 9))
    }

    @Test
    fun offsetJustAfterAWord() {
        assertEquals(TextRange(0, 5), WordBounds.at("alpha", 5))
        assertEquals(TextRange(0, 5), WordBounds.at("alpha beta", 5))
    }

    @Test
    fun spaceBetweenWordsSelectsThePreviousWordWhenItTouchesThatWord() {
        // A separator immediately after a word selects that word. A space that
        // does not touch a word (the second space, or a leading space) selects nothing.
        assertEquals(TextRange(0, 5), WordBounds.at("alpha beta", 5))
        assertNull(WordBounds.at("alpha  beta", 6))
        assertNull(WordBounds.at(" alpha", 0))
    }

    @Test
    fun hyphenAndApostropheJoinWordCharacters() {
        assertEquals(TextRange(0, 9), WordBounds.at("по-русски", 0))
        assertEquals(TextRange(0, 9), WordBounds.at("по-русски", 2))
        assertEquals(TextRange(0, 9), WordBounds.at("по-русски", 3))
        assertEquals(TextRange(0, 5), WordBounds.at("don't", 2))
        assertEquals(TextRange(0, 5), WordBounds.at("don't", 3))
        val curly = "don\u2019t"
        assertEquals(TextRange(0, curly.length), WordBounds.at(curly, 3))
    }

    @Test
    fun digitsAndUnderscore() {
        assertEquals(TextRange(0, 4), WordBounds.at("3.14", 0))
        assertEquals(TextRange(0, 4), WordBounds.at("3.14", 1))
        assertEquals(TextRange(0, 4), WordBounds.at("3.14", 3))
        assertEquals(TextRange(0, 3), WordBounds.at("a_b", 1))
    }

    @Test
    fun cyrillicWord() {
        assertEquals(TextRange(0, 6), WordBounds.at("привет", 0))
        assertEquals(TextRange(0, 6), WordBounds.at("привет", 5))
    }

    @Test
    fun emojiIsNotAWordAndSurrogatePairsStayWhole() {
        val emoji = "\uD83D\uDE00"
        assertEquals(2, emoji.length)
        assertNull(WordBounds.at(emoji, 0))
        assertNull(WordBounds.at(emoji, 1))
        assertNull(WordBounds.at(emoji, 2))
        assertEquals(TextRange(0, 2), WordBounds.at("ok$emoji", 2))
        assertEquals(TextRange(0, 2), WordBounds.at("ok$emoji", 3))
    }

    @Test
    fun emptyString() {
        assertNull(WordBounds.at("", 0))
        assertNull(WordBounds.at("a", -1))
        assertNull(WordBounds.at("a", 2))
    }

    @Test
    fun trailingPunctuationSelectsTheWordItFollows() {
        assertEquals(TextRange(0, 5), WordBounds.at("hello!", 5))
        assertNull(WordBounds.at("hello!", 6))
        assertEquals(TextRange(0, 5), WordBounds.at("hello.", 5))
        assertNull(WordBounds.at("hello.", 6))
    }
}
