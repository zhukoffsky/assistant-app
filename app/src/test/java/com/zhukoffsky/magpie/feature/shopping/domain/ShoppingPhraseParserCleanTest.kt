package com.zhukoffsky.magpie.feature.shopping.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Чистка позиции применяется и к результату модели: разрезав фразу верно,
 * она оставляет внутри позиций вводные слова.
 */
class ShoppingPhraseParserCleanTest {

    @Test
    fun `leading verb is dropped`() {
        assertEquals("хлеб", ShoppingPhraseParser.clean("купить хлеб"))
        assertEquals("хлеб", ShoppingPhraseParser.clean("надо купить хлеб"))
        assertEquals("хлеб", ShoppingPhraseParser.clean("добавь хлеб"))
    }

    @Test
    fun `leading hedges and conjunctions are dropped`() {
        assertEquals("фарш", ShoppingPhraseParser.clean("и наверное ещё фарш"))
        assertEquals("фарш", ShoppingPhraseParser.clean("а ещё фарш"))
        assertEquals("milk", ShoppingPhraseParser.clean("and also milk"))
    }

    /** Слово-товар не должно пострадать от чистки. */
    @Test
    fun `an ordinary item is left alone`() {
        assertEquals("хлеб бородинский", ShoppingPhraseParser.clean("хлеб бородинский"))
        assertEquals("немножко хлеба", ShoppingPhraseParser.clean("немножко хлеба"))
        assertEquals("два литра молока", ShoppingPhraseParser.clean("два литра молока"))
    }

    @Test
    fun `trailing punctuation goes`() {
        assertEquals("фарш", ShoppingPhraseParser.clean("фарш."))
    }
}
