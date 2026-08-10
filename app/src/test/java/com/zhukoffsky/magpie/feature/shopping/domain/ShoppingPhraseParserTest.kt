package com.zhukoffsky.magpie.feature.shopping.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingPhraseParserTest {

    @Test
    fun `single item stays intact`() {
        assertEquals(listOf("молоко"), ShoppingPhraseParser.parse("молоко"))
    }

    @Test
    fun `commas separate items`() {
        assertEquals(
            listOf("молоко", "хлеб", "яйца"),
            ShoppingPhraseParser.parse("молоко, хлеб, яйца"),
        )
    }

    @Test
    fun `conjunction separates items`() {
        assertEquals(
            listOf("чай", "кофе", "сахар"),
            ShoppingPhraseParser.parse("чай, кофе и сахар"),
        )
    }

    @Test
    fun `leading verb is dropped`() {
        assertEquals(listOf("молоко", "хлеб"), ShoppingPhraseParser.parse("купи молоко и хлеб"))
        assertEquals(listOf("соль"), ShoppingPhraseParser.parse("надо купить соль"))
    }

    @Test
    fun `trailing punctuation is dropped`() {
        assertEquals(listOf("молоко"), ShoppingPhraseParser.parse("молоко."))
    }

    @Test
    fun `multi-word items survive`() {
        assertEquals(
            listOf("хлеб бородинский", "сыр с плесенью"),
            ShoppingPhraseParser.parse("хлеб бородинский, сыр с плесенью"),
        )
    }

    @Test
    fun `the letter i inside a word is not a separator`() {
        assertEquals(listOf("икра"), ShoppingPhraseParser.parse("икра"))
        assertEquals(listOf("иван-чай"), ShoppingPhraseParser.parse("иван-чай"))
    }

    @Test
    fun `blank input yields nothing`() {
        assertEquals(emptyList<String>(), ShoppingPhraseParser.parse("   "))
        assertEquals(emptyList<String>(), ShoppingPhraseParser.parse(", , ,"))
    }

    @Test
    fun `english phrasing works too`() {
        assertEquals(
            listOf("milk", "bread", "eggs"),
            ShoppingPhraseParser.parse("buy milk, bread and eggs"),
        )
    }
}
