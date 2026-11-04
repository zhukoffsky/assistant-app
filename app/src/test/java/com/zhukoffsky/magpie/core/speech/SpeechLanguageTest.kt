package com.zhukoffsky.magpie.core.speech

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SpeechLanguageTest {

    /** Ровно тот тег, что Pixel 8 отдал 4 ноября: с региональными настройками. */
    @Test
    fun `regional preferences are stripped`() {
        val locale = Locale.forLanguageTag("ru-RU-u-fw-mon-ms-metric-mu-celsius")
        assertEquals("ru-RU", speechLanguageTag(locale))
    }

    @Test
    fun `plain locale stays as is`() {
        assertEquals("en-US", speechLanguageTag(Locale.US))
    }
}
