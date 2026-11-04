package com.zhukoffsky.magpie.feature.shopping.domain

import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.ALCOHOL
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.BAKERY
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.CANNED
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.CHEESE
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.CONDIMENTS
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.DAIRY
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.DELI
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.DRINKS
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.FISH
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.FROZEN
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.GROCERY
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.HOUSEHOLD
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.HYGIENE
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.MEAT
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.PETS
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.PRODUCE
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.SWEETS
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.TEA_COFFEE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Правила словаря, каждое отдельным тестом. Широкий охват — в [ShoppingCategoryCorpusTest]. */
class ShoppingCategoryDictionaryTest {

    private fun assertAisle(expected: ShoppingCategory, vararg titles: String) = titles.forEach {
        assertEquals(it, expected, ShoppingCategoryDictionary.categorize(it))
    }

    /** Живые диктовки 4 ноября — ровно то, что легло в «Прочее», пока молчала модель. */
    @Test
    fun `real dictation of 4 November`() {
        assertAisle(BAKERY, "Хлеб", "булки с маком", "булка с маком", "хлеб бородинский")
        assertAisle(FROZEN, "пельмени", "вареники")
        assertAisle(MEAT, "вырезка свиная", "вырезка свина", "куриные бёдрышки", "куриные ножки")
        assertAisle(FISH, "лосось")
        assertAisle(TEA_COFFEE, "кофе", "чай")
        assertAisle(DRINKS, "водичка", "кока-кола")
        assertAisle(GROCERY, "гречку", "гречка", "овсянка")
    }

    @Test
    fun `inflected forms from dictation`() {
        assertAisle(DAIRY, "молока", "кефира", "сметану", "яйца", "десяток яиц")
        assertAisle(MEAT, "фарша", "курицу")
        assertAisle(DELI, "колбасу")
        assertAisle(PRODUCE, "картошку", "морковки", "яблоки", "лук")
    }

    @Test
    fun `household is not food it mentions`() {
        assertAisle(HOUSEHOLD, "средство для мытья посуды", "туалетную бумагу", "пакеты для мусора")
        assertAisle(HYGIENE, "зубную пасту")
    }

    /** Отдел определяет, для кого товар, а не из чего он: консервы для кошек — не бакалея. */
    @Test
    fun `addressee wins over the product`() {
        assertAisle(PETS, "консервы для кошек", "корм для собак")
    }

    @Test
    fun `dish made of meat is still frozen`() {
        assertAisle(FROZEN, "пельмени с говядиной", "мороженое пломбир")
    }

    @Test
    fun `drink made of fruit is a drink`() {
        assertAisle(DRINKS, "яблочный сок", "orange juice")
    }

    /** Начало одного слова совпадает с началом другого — классические промахи по префиксу. */
    @Test
    fun `prefix collisions resolve to the right aisle`() {
        assertAisle(PRODUCE, "виноград", "watermelon", "eggplant", "сливы", "сельдерей")
        assertAisle(ALCOHOL, "вино красное")
        assertAisle(CANNED, "маслины")
        assertAisle(CONDIMENTS, "масло подсолнечное")
        assertAisle(DAIRY, "масло сливочное", "сливки", "сырок глазированный")
        assertAisle(SWEETS, "печенье", "печеньки")
        assertAisle(MEAT, "печень говяжья")
        assertAisle(FISH, "печень трески")
        assertAisle(DAIRY, "пакет молока")
    }

    @Test
    fun `english items`() {
        assertAisle(DAIRY, "milk", "eggs")
        assertAisle(CHEESE, "cheddar cheese")
        assertAisle(BAKERY, "bread", "hamburger buns")
        assertAisle(PRODUCE, "bananas", "tomatoes")
    }

    @Test
    fun `unknown item has no aisle, not a wrong one`() {
        assertNull(ShoppingCategoryDictionary.categorize("подарок маме"))
        assertNull(ShoppingCategoryDictionary.categorize("   "))
    }
}
