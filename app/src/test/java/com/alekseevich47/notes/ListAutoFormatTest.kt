package com.alekseevich47.notes

import com.alekseevich47.notes.ui.editor.ListAutoFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ListAutoFormatTest {
    @Test
    fun enterCreatesNextItem() {
        val (text, cursor) = ListAutoFormat.onTextChanged("1. Привет", "1. Привет\n", 10)!!
        assertThat(text).isEqualTo("1. Привет\n2. ")
        assertThat(cursor).isEqualTo(text.length)
    }

    @Test
    fun emptyEnterExitsList() {
        val (text, _) = ListAutoFormat.onTextChanged("1. Привет\n2. ", "1. Привет\n2. \n", 14)!!
        assertThat(text).isEqualTo("1. Привет\n")
    }

    @Test
    fun renumberAfterInsert() {
        val result = ListAutoFormat.renumber("1. A\n1. B\n1. C")
        assertThat(result).isEqualTo("1. A\n2. B\n3. C")
    }

    @Test
    fun autoSpaceAfterOneDot() {
        val (text, cursor) = ListAutoFormat.onTextChanged("", "1.", 2)!!
        assertThat(text).isEqualTo("1. ")
        assertThat(cursor).isEqualTo(3)
    }
}
