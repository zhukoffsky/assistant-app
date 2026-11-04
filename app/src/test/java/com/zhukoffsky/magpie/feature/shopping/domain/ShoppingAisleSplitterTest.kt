package com.zhukoffsky.magpie.feature.shopping.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import com.zhukoffsky.magpie.core.speech.dropRecognizerCommas

class ShoppingAisleSplitterTest {

    private fun split(text: String) = ShoppingAisleSplitter.split(text)

    /**
     * Ровно то, что распознаватель на телефоне выдал 4 ноября на ускоренной
     * подаче — сплошной строкой, без единой запятой. Модели нет, режут правила.
     */
    @Test
    fun `offline dictation of 4 November`() = runTest {
        val items = RuleBasedShoppingParser().parse(
            "Хлеб Бородинский молоко ещё пельмени и вареники куриные бёдрышки гречку и средства для мытья посуды.",
        ).map { it.title }

        assertEquals(
            listOf(
                "Хлеб Бородинский", "молоко", "пельмени", "вареники",
                "куриные бёдрышки", "гречку", "средства для мытья посуды",
            ),
            items,
        )
    }

    @Test
    fun `sentences from the on-device recognizer are split on periods`() = runTest {
        val items = RuleBasedShoppingParser().parse("Хлеб Бородинский молоко. Ещё пельмени и вареники.").map { it.title }

        assertEquals(listOf("Хлеб Бородинский", "молоко", "пельмени", "вареники"), items)
    }

    @Test
    fun `decimal point is not a boundary`() = runTest {
        assertEquals(listOf("молоко 2.5%"), RuleBasedShoppingParser().parse("молоко 2.5%").map { it.title })
    }

    @Test
    fun `multi word names stay whole`() {
        assertEquals(listOf("хлеб бородинский"), split("хлеб бородинский"))
        assertEquals(listOf("куриные бёдрышки"), split("куриные бёдрышки"))
        assertEquals(listOf("апельсиновый сок"), split("апельсиновый сок"))
    }

    @Test
    fun `preposition holds the next word`() {
        assertEquals(listOf("макароны с сыром"), split("макароны с сыром"))
        assertEquals(listOf("булки с маком"), split("булки с маком"))
    }

    @Test
    fun `aisle change is a boundary`() {
        assertEquals(listOf("молоко", "хлеб", "яйца"), split("молоко хлеб яйца"))
        assertEquals(listOf("молоко", "апельсиновый сок"), split("молоко апельсиновый сок"))
    }

    @Test
    fun `repeated item is a boundary even within one aisle`() {
        assertEquals(listOf("хлеб белый", "хлеб черный"), split("хлеб белый хлеб черный"))
    }

    @Test
    fun `second adjective after a noun starts a new item`() {
        assertEquals(listOf("вырезка свиная", "куриные бедрышки"), split("вырезка свиная куриные бедрышки"))
    }

    /**
     * Живая диктовка 4 ноября, расшифрованная на лету телефоном, — без
     * модели. Первая версия правил отдала из неё четыре позиции, две из них
     * склейками: «Хлеб булочка с маком» и «вода coca-cola дворники для
     * машины подсолнечное». Хвост дан так, как его говорили: телефон
     * расслышал «фарш куриные из свиной окорок».
     */
    @Test
    fun `live dictation of 4 November`() = runTest {
        val items = RuleBasedShoppingParser().parse(
            "Хлеб булочка с маком вода coca-cola дворники для машины подсолнечное масло фарш куриный и свиной окорок.",
        ).map { it.title }

        assertEquals(
            listOf(
                "Хлеб", "булочка с маком", "вода", "coca-cola", "дворники для машины",
                "подсолнечное масло", "фарш куриный", "свиной окорок",
            ),
            items,
        )
    }

    /**
     * Вторая живая диктовка 4 ноября, дословно как её выдал телефон — с его
     * запятыми, три из которых не на месте. Запятые снимаются до разбора.
     */
    @Test
    fun `live dictation with the recognizer's commas`() = runTest {
        val heard = "Хлеб Бородинский, булочка с маком, молоко, яйца, куриные. " +
            "Подсолнечное масло, пачка сливочного масла, сыр моцарелла, картошка фри, яблоки, " +
            "зелёные бананы, вино, красное сухое средство для мытья посуды."

        val items = RuleBasedShoppingParser().parse(dropRecognizerCommas(heard)).map { it.title }

        assertEquals(
            listOf(
                "Хлеб Бородинский", "булочка с маком", "молоко", "яйца куриные",
                "Подсолнечное масло", "пачка сливочного масла", "сыр моцарелла", "картошка фри",
                "яблоки зелёные", "бананы", "вино красное сухое", "средство для мытья посуды",
            ),
            items,
        )
    }

    @Test
    fun `decimal comma survives`() {
        assertEquals("молоко 2,5% хлеб", dropRecognizerCommas("молоко 2,5%, хлеб"))
    }

    @Test
    fun `two known nouns in a row are two items`() {
        assertEquals(listOf("фарш", "свинина"), split("фарш свинина"))
        assertEquals(listOf("хлеб", "булочка"), split("хлеб булочка"))
    }

    /** «Сыр моцарелла» — это один сыр, а не сыр и ещё моцарелла. */
    @Test
    fun `generic word followed by its kind stays whole`() {
        assertEquals(listOf("сыр моцарелла"), split("сыр моцарелла"))
        assertEquals(listOf("чай каркаде"), split("чай каркаде"))
        assertEquals(listOf("грибы шампиньоны"), split("грибы шампиньоны"))
    }

    @Test
    fun `quantity word holds the next noun`() {
        assertEquals(listOf("пачка масла"), split("пачка масла"))
        assertEquals(listOf("десяток яиц"), split("десяток яиц"))
        assertEquals(listOf("молоко", "пачка масла"), split("молоко пачка масла"))
    }

    /**
     * Прилагательное между двумя товарами принадлежит тому, с кем согласовано:
     * «подсолнечное» — среднего рода, как «масло», а не как «машины».
     */
    @Test
    fun `adjective goes to the noun it agrees with`() {
        assertEquals(listOf("вода", "подсолнечное масло"), split("вода подсолнечное масло"))
        assertEquals(listOf("молоко", "белый хлеб"), split("молоко белый хлеб"))
        assertEquals(listOf("хлеб бородинский", "молоко"), split("хлеб бородинский молоко"))
        assertEquals(listOf("белый хлеб", "чёрный хлеб"), split("белый хлеб чёрный хлеб"))
    }

    /** У прилагательного свой отдел («куриные» — мясо), но товар это не меняет. */
    @Test
    fun `adjective after its noun stays even from another aisle`() {
        assertEquals(listOf("яйца куриные"), split("яйца куриные"))
        assertEquals(listOf("сок апельсиновый"), split("сок апельсиновый"))
        assertEquals(listOf("молоко овсяное"), split("молоко овсяное"))
        assertEquals(listOf("чай зелёный"), split("чай зелёный"))
    }

    @Test
    fun `item closed by its adjective ends before the next noun`() {
        assertEquals(listOf("яблоки зелёные", "бананы"), split("яблоки зелёные бананы"))
        assertEquals(
            listOf("вода газированная", "сок яблочный"),
            split("вода газированная сок яблочный"),
        )
    }

    @Test
    fun `two adjectives of one noun stay together`() {
        assertEquals(listOf("вино красное сухое"), split("вино красное сухое"))
        assertEquals(listOf("хлопья кукурузные"), split("хлопья кукурузные"))
    }

    @Test
    fun `dictionary phrase stays whole`() {
        assertEquals(listOf("картошка фри"), split("картошка фри"))
    }

    @Test
    fun `unknown words stay with their neighbours`() {
        assertEquals(listOf("подарок маме"), split("подарок маме"))
    }
}
