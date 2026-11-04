package com.zhukoffsky.magpie.feature.shopping.domain

import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.ALCOHOL
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.BAKERY
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.BREAKFAST
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.CANNED
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.CHEESE
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.CONDIMENTS
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.DAIRY
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.DELI
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.DRINKS
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.FISH
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.FROZEN
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.GROCERY
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.HEALTH
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.HOUSEHOLD
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.HYGIENE
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.KIDS
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.MEAT
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.PETS
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.PRODUCE
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.READY_MEALS
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.SNACKS
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.SWEETS
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory.TEA_COFFEE
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Эталонный набор словаря отделов: то, что люди на самом деле пишут в список.
 *
 * Собран из живых диктовок владельца (они записаны в CLAUDE.md), частых
 * продуктов и тех форм, в которых их диктуют: винительный падеж («гречку»),
 * уменьшительные («молочко», «курочка»), прилагательное впереди
 * («куриный фарш») и позади («фарш куриный»).
 *
 * Тест не останавливается на первой ошибке: он печатает все промахи разом,
 * чтобы дополнять словарь по списку, а не по одному.
 */
class ShoppingCategoryCorpusTest {

    private val corpus: List<Pair<String, ShoppingCategory>> = listOf(
        // Живые диктовки владельца, август–ноябрь 2026.
        "хлеб" to BAKERY, "лаваш" to BAKERY, "тортик" to BAKERY, "булка с маком" to BAKERY,
        "булки с маком" to BAKERY, "ватрушку с изюмом" to BAKERY, "хлеб бородинский" to BAKERY,
        "хлеб белый" to BAKERY, "хлеб черный" to BAKERY,
        "фарш куриный" to MEAT, "куриный фарш" to MEAT, "свинина" to MEAT, "рыбка" to FISH,
        "мясо" to MEAT, "колбаса" to DELI, "колбасу" to DELI, "индюшиные ноги" to MEAT,
        "вырезка свиная" to MEAT, "свиная вырезка" to MEAT, "куриные бедра" to MEAT,
        "куриные бёдрышки" to MEAT, "куриные ножки" to MEAT, "курица" to MEAT, "лосось" to FISH,
        "каша гречневая" to GROCERY, "гречку" to GROCERY, "гречка" to GROCERY, "овсянка" to GROCERY,
        "макароны" to GROCERY,
        "молоко" to DAIRY, "сыр" to CHEESE, "масло" to DAIRY,
        "морковку" to PRODUCE, "картошку" to PRODUCE,
        "воды" to DRINKS, "вода" to DRINKS, "водичка" to DRINKS, "кофе" to TEA_COFFEE, "чай" to TEA_COFFEE,
        "кока-кола" to DRINKS,
        "пельмени" to FROZEN, "вареники" to FROZEN,
        "салфетки" to HOUSEHOLD, "средство для мытья посуды" to HOUSEHOLD,

        // Уменьшительные — так говорят вслух.
        "молочко" to DAIRY, "сырок" to DAIRY, "творожок" to DAIRY, "сметанка" to DAIRY,
        "яички" to DAIRY, "кефирчик" to DAIRY, "маслице" to DAIRY,
        "курочка" to MEAT, "курочку" to MEAT, "колбаска" to DELI, "мяско" to MEAT, "сосисочки" to DELI,
        "лучок" to PRODUCE, "чесночок" to PRODUCE, "огурчики" to PRODUCE, "помидорки" to PRODUCE,
        "яблочки" to PRODUCE, "картошечка" to PRODUCE, "укропчик" to PRODUCE, "зелень" to PRODUCE,
        "хлебушек" to BAKERY, "печеньки" to SWEETS, "булочки" to BAKERY,
        "чаёк" to TEA_COFFEE, "пивко" to ALCOHOL, "винцо" to ALCOHOL, "сок" to DRINKS, "соку" to DRINKS,
        "сахарок" to CONDIMENTS,

        // Молочное.
        "кефир" to DAIRY, "ряженка" to DAIRY, "йогурт" to DAIRY, "творог" to DAIRY, "сметана" to DAIRY,
        "сливки" to DAIRY, "масло сливочное" to DAIRY, "яйца" to DAIRY, "десяток яиц" to DAIRY,
        "сыр плавленый" to CHEESE, "моцарелла" to CHEESE, "сулугуни" to CHEESE, "брынза" to CHEESE,
        "сыр адыгейский" to CHEESE, "простокваша" to DAIRY, "варенец" to DAIRY, "снежок" to DAIRY,
        "молоко 2.5%" to DAIRY, "пакет молока" to DAIRY,

        // Мясо и рыба.
        "говядина" to MEAT, "фарш" to MEAT, "телятина" to MEAT, "баранина" to MEAT,
        "куриное филе" to MEAT, "куриная грудка" to MEAT, "крылышки" to MEAT, "окорочка" to MEAT,
        "цыплёнок" to MEAT, "индейка" to MEAT, "утка" to MEAT, "печень куриная" to MEAT,
        "сосиски" to DELI, "сардельки" to DELI, "ветчина" to DELI, "бекон" to DELI, "сервелат" to DELI,
        "буженина" to DELI, "шашлык" to MEAT, "котлеты" to MEAT, "стейк" to MEAT, "рёбрышки" to MEAT,
        "сёмга" to FISH, "форель" to FISH, "селёдка" to FISH, "скумбрия" to FISH, "треска" to FISH,
        "минтай" to FISH, "горбуша" to FISH, "кальмары" to FISH, "мидии" to FISH, "икра красная" to FISH,
        "морепродукты" to FISH, "колбаса докторская" to DELI,

        // Выпечка.
        "батон" to BAKERY, "багет" to BAKERY, "лепёшка" to BAKERY, "пирог" to BAKERY,
        "пирожки" to BAKERY, "торт" to BAKERY, "пирожное" to BAKERY, "кекс" to BAKERY,
        "круассаны" to BAKERY, "сушки" to BAKERY, "баранки" to BAKERY, "пряники" to SWEETS,
        "вафли" to SWEETS, "бублики" to BAKERY, "рулет" to BAKERY, "печенье" to SWEETS,
        "хлебцы" to BAKERY, "тостовый хлеб" to BAKERY, "блины" to BAKERY,

        // Овощи, фрукты, зелень.
        "яблоки" to PRODUCE, "груши" to PRODUCE, "бананы" to PRODUCE, "апельсины" to PRODUCE,
        "мандарины" to PRODUCE, "лимон" to PRODUCE, "киви" to PRODUCE, "виноград" to PRODUCE,
        "персики" to PRODUCE, "сливы" to PRODUCE, "вишня" to PRODUCE, "клубника" to PRODUCE,
        "малина" to PRODUCE, "черника" to PRODUCE, "смородина" to PRODUCE, "клюква" to PRODUCE,
        "арбуз" to PRODUCE, "дыня" to PRODUCE, "ананас" to PRODUCE, "авокадо" to PRODUCE,
        "картофель" to PRODUCE, "морковь" to PRODUCE, "лук" to PRODUCE, "лук репчатый" to PRODUCE,
        "чеснок" to PRODUCE, "капуста" to PRODUCE, "огурцы" to PRODUCE, "помидоры" to PRODUCE,
        "помидоры черри" to PRODUCE, "перец болгарский" to PRODUCE, "кабачки" to PRODUCE,
        "баклажаны" to PRODUCE, "тыква" to PRODUCE, "свёкла" to PRODUCE, "редиска" to PRODUCE,
        "петрушка" to PRODUCE, "укроп" to PRODUCE, "салат" to PRODUCE, "шпинат" to PRODUCE,
        "руккола" to PRODUCE, "кинза" to PRODUCE, "брокколи" to PRODUCE, "грибы" to PRODUCE,
        "шампиньоны" to PRODUCE, "имбирь" to PRODUCE, "кукуруза" to PRODUCE,

        // Бакалея.
        "рис" to GROCERY, "пшено" to GROCERY, "булгур" to GROCERY, "манка" to GROCERY,
        "спагетти" to GROCERY, "лапша" to GROCERY, "вермишель" to GROCERY, "хлопья" to BREAKFAST,
        "мюсли" to BREAKFAST, "мука" to GROCERY, "сахар" to CONDIMENTS, "соль" to CONDIMENTS, "сода" to CONDIMENTS,
        "масло подсолнечное" to CONDIMENTS, "масло оливковое" to CONDIMENTS, "уксус" to CONDIMENTS,
        "соевый соус" to CONDIMENTS, "кетчуп" to CONDIMENTS, "майонез" to CONDIMENTS, "горчица" to CONDIMENTS,
        "перец чёрный молотый" to CONDIMENTS, "корица" to CONDIMENTS, "лавровый лист" to CONDIMENTS,
        "тушёнка" to CANNED, "горошек" to CANNED, "зелёный горошек" to CANNED,
        "кукуруза консервированная" to CANNED, "фасоль" to GROCERY, "чечевица" to GROCERY,
        "нут" to GROCERY, "мёд" to CANNED, "варенье" to CANNED, "джем" to CANNED,
        "шоколад" to SWEETS, "конфеты" to SWEETS, "зефир" to SWEETS, "мармелад" to SWEETS,
        "чипсы" to SNACKS, "чипсы картофельные" to SNACKS, "сухарики" to SNACKS, "орехи" to SNACKS,
        "арахис" to SNACKS, "семечки" to SNACKS, "изюм" to SNACKS, "курага" to SNACKS,
        "дрожжи" to CONDIMENTS, "сгущёнка" to CANNED, "оливки" to CANNED, "маслины" to CANNED,
        "томатная паста" to CONDIMENTS, "кабачковая икра" to CANNED, "куриный бульон" to CANNED,
        "лимонная кислота" to CONDIMENTS, "батончик" to SWEETS, "детское питание" to KIDS,
        "кукурузные хлопья" to BREAKFAST, "сырный соус" to CONDIMENTS, "сливочный соус" to CONDIMENTS,
        "куриные яйца" to DAIRY, "миндальное молоко" to DAIRY, "арахисовая паста" to CANNED,

        // Заморозка.
        "мороженое" to FROZEN, "пельмени с говядиной" to FROZEN, "хинкали" to FROZEN,
        "наггетсы" to FROZEN, "креветки" to FROZEN, "сырники" to FROZEN, "блинчики с мясом" to FROZEN,
        "картошка фри" to FROZEN, "замороженные овощи" to FROZEN, "рыбные палочки" to FROZEN,
        "лёд" to FROZEN, "пицца" to FROZEN, "слоёное тесто" to FROZEN, "лазанья" to FROZEN,

        // Напитки.
        "минералка" to DRINKS, "газировка" to DRINKS, "лимонад" to DRINKS, "квас" to DRINKS,
        "пиво" to ALCOHOL, "вино" to ALCOHOL, "вино красное" to ALCOHOL, "шампанское" to ALCOHOL,
        "коньяк" to ALCOHOL, "водка" to ALCOHOL, "сидр" to ALCOHOL, "ром" to ALCOHOL, "джин" to ALCOHOL,
        "чай зелёный" to TEA_COFFEE, "зелёный чай" to TEA_COFFEE, "какао" to TEA_COFFEE, "компот" to DRINKS,
        "морс" to DRINKS, "яблочный сок" to DRINKS, "томатный сок" to DRINKS, "энергетик" to DRINKS,
        "кофе в зёрнах" to TEA_COFFEE,

        // Бытовое и гигиена.
        "туалетная бумага" to HOUSEHOLD, "бумажные полотенца" to HOUSEHOLD, "влажные салфетки" to HYGIENE,
        "губки для посуды" to HOUSEHOLD, "пакеты для мусора" to HOUSEHOLD, "мешки для мусора" to HOUSEHOLD,
        "стиральный порошок" to HOUSEHOLD, "кондиционер для белья" to HOUSEHOLD, "отбеливатель" to HOUSEHOLD,
        "мыло" to HYGIENE, "жидкое мыло" to HYGIENE, "шампунь" to HYGIENE, "гель для душа" to HYGIENE,
        "зубная паста" to HYGIENE, "зубная щётка" to HYGIENE, "дезодорант" to HYGIENE,
        "станок для бритья" to HYGIENE, "ватные диски" to HYGIENE, "прокладки" to HYGIENE,
        "подгузники" to KIDS, "фольга" to HOUSEHOLD, "пищевая плёнка" to HOUSEHOLD,
        "батарейки" to HOUSEHOLD, "лампочка" to HOUSEHOLD, "таблетки для посудомойки" to HOUSEHOLD,
        "освежитель воздуха" to HOUSEHOLD, "корм для кошек" to PETS, "наполнитель для лотка" to PETS,
        "спички" to HOUSEHOLD, "крем для рук" to HYGIENE,

        // Промахи первой проверки на незнакомых словах (4 ноября): 53 из 60.
        "кабачок" to PRODUCE, "клементины" to PRODUCE, "сельдерей стебель" to PRODUCE,
        "биойогурт" to DAIRY, "сосиски молочные" to DELI, "кофе молотый" to TEA_COFFEE,
        "зубочистки" to HYGIENE,


        // Новые отделы и продукты из исследований чеков: яйца, хлеб, молоко,
        // сладкая газировка, картофель, томаты, черешня, бананы, яблоки,
        // вода, пиво, конфеты, соки, колбасы — то, что чаще всего в чеках.
        "сладкая газировка" to DRINKS, "вода негазированная" to DRINKS, "черешня" to PRODUCE,
        "томаты" to PRODUCE, "кондитерские изделия" to SWEETS, "соки и нектары" to DRINKS,
        "сыр гауда" to CHEESE, "сырок глазированный" to DAIRY, "творожный сырок" to DAIRY,
        "молочный шоколад" to SWEETS, "печень трески" to FISH, "филе минтая" to FISH,
        "копчёная скумбрия" to FISH, "ветчина куриная" to DELI, "колбаса копчёная" to DELI,
        "суповой набор" to MEAT, "фарш рыбный" to FISH, "крабовые палочки" to FISH,
        "салат оливье" to READY_MEALS, "крабовый салат" to READY_MEALS, "роллы" to READY_MEALS,
        "суши" to READY_MEALS, "суп" to READY_MEALS, "готовая еда" to READY_MEALS, "шаурма" to READY_MEALS,
        "сэндвич" to READY_MEALS, "плов" to READY_MEALS,
        "консервы для кошек" to PETS, "корм для собак" to PETS, "сухой корм" to PETS,
        "детское пюре" to KIDS, "детская смесь" to KIDS, "детский шампунь" to KIDS,
        "хозяйственное мыло" to HOUSEHOLD, "средство для умывания" to HYGIENE, "бальзам для волос" to HYGIENE,
        "торт с кремом" to BAKERY, "мороженое крем-брюле" to FROZEN, "рижский бальзам" to ALCOHOL,
        "кефирный напиток" to DAIRY, "пиво безалкогольное" to ALCOHOL, "кофе растворимый" to TEA_COFFEE,
        "чай пакетированный" to TEA_COFFEE, "цикорий" to TEA_COFFEE, "маргарин" to DAIRY,
        "пломбир" to FROZEN, "орехи грецкие" to SNACKS, "семечки подсолнечные" to SNACKS,


        // Промахи второй проверки на незнакомых словах (4 ноября, 18 отделов): 56 из 65.
        "сливки для кофе" to DAIRY, "сельдь в масле" to FISH, "кукурузная мука" to GROCERY,
        "салат греческий" to READY_MEALS, "борщ" to READY_MEALS, "энергетический напиток" to DRINKS,
        "сухой завтрак" to BREAKFAST, "мохито безалкогольный" to DRINKS, "кокосовая стружка" to SNACKS,

        // Новые отделы после сверки с Беларусью, Испанией и США (4 ноября).
        "лекарство от давления" to HEALTH, "витамины" to HEALTH, "пластырь" to HEALTH, "рыбий жир" to HEALTH,
        "зелёнка" to HEALTH, "леденцы от горла" to HEALTH, "таблетки от головы" to HEALTH, "термометр" to HEALTH,
        "мюсли с орехами" to BREAKFAST, "каша овсяная быстрого приготовления" to GROCERY,
        "консервы рыбные" to CANNED, "фасоль консервированная" to CANNED,
        "pastrami" to DELI, "gnocchi" to GROCERY, "marshmallows" to SWEETS, "pretzels" to SNACKS,
        "pancake mix" to BREAKFAST, "cream cheese" to DAIRY, "lobster" to FISH, "brandy" to ALCOHOL,
        "mouthwash" to HYGIENE, "pesto" to CONDIMENTS, "honey" to CANNED, "vitamins" to HEALTH,

        // Третья проверка на незнакомых словах (4 ноября, 23 отдела): 36 из 50 — отдел решало
        // уточняющее слово, а не главное. Отсюда разбор «главное до предлога, существительное
        // раньше прилагательного».
        "рис для плова" to GROCERY, "геркулес" to BREAKFAST, "вермишель паутинка" to GROCERY,
        "мука ржаная" to GROCERY, "горох колотый" to GROCERY, "соль крупная" to CONDIMENTS,
        "сахар-песок" to CONDIMENTS, "ванильный сахар" to CONDIMENTS, "приправа для курицы" to CONDIMENTS,
        "соус терияки" to CONDIMENTS, "масло кукурузное" to CONDIMENTS, "уксус яблочный" to CONDIMENTS,
        "майонез провансаль" to CONDIMENTS, "горчица дижонская" to CONDIMENTS, "шпроты в масле" to CANNED,
        "тунец консервированный" to CANNED, "ананасы консервированные" to CANNED, "мёд гречишный" to CANNED,
        "варенье клубничное" to CANNED, "сгущённое молоко" to CANNED, "хлопья кукурузные" to BREAKFAST,
        "мюсли запечённые" to BREAKFAST, "овсяная каша с ягодами" to GROCERY, "гранола с орехами" to BREAKFAST,
        "шоколад горький" to SWEETS, "конфеты ассорти" to SWEETS, "печенье овсяное" to SWEETS,
        "пряники тульские" to SWEETS, "зефир в шоколаде" to SWEETS, "орехи кешью" to SNACKS,
        "фисташки солёные" to SNACKS, "чипсы со сметаной" to SNACKS, "сухарики с чесноком" to SNACKS,
        "семечки тыквенные" to SNACKS, "витамин С" to HEALTH, "магний B6" to HEALTH,
        "пластырь бактерицидный" to HEALTH, "капли для глаз" to HEALTH, "мазь от ушибов" to HEALTH,
        "бинт" to HEALTH, "активированный уголь" to HEALTH, "сироп от кашля" to HEALTH, "гематоген" to HEALTH,
        "презервативы" to HEALTH, "nutella" to CANNED, "cornflakes" to BREAKFAST, "maple syrup" to CANNED,
        "beef jerky" to SNACKS, "trail mix" to SNACKS,

        // Четвёртая проверка, уже после переделки разбора: 36 из 40 (90%).
        "кус-кус" to GROCERY, "паприка копчёная" to CONDIMENTS, "шоколадная паста" to CANNED,
        "фасоль в томатном соусе" to CANNED, "перловка" to GROCERY, "масло льняное" to CONDIMENTS,
        "сгущёнка варёная" to CANNED, "батончик мюсли" to SWEETS, "финики сушёные" to SNACKS,
        "куриная печень" to MEAT, "скумбрия холодного копчения" to FISH, "икра щуки" to FISH,

        // Английский.
        "milk" to DAIRY, "eggs" to DAIRY, "cheese" to CHEESE, "butter" to DAIRY, "yogurt" to DAIRY,
        "bread" to BAKERY, "bagels" to BAKERY, "cookies" to SWEETS, "hamburger buns" to BAKERY,
        "chicken breast" to MEAT, "ground beef" to MEAT, "bacon" to DELI, "salmon" to FISH, "pepperoni" to DELI,
        "bananas" to PRODUCE, "apples" to PRODUCE, "tomatoes" to PRODUCE, "onions" to PRODUCE,
        "bell pepper" to PRODUCE, "strawberries" to PRODUCE, "watermelon" to PRODUCE, "eggplant" to PRODUCE,
        "rice" to GROCERY, "pasta" to GROCERY, "olive oil" to CONDIMENTS, "peanut butter" to CANNED,
        "chips" to SNACKS, "cereal" to BREAKFAST,
        "ice cream" to FROZEN, "frozen pizza" to FROZEN,
        "orange juice" to DRINKS, "coffee" to TEA_COFFEE, "beer" to ALCOHOL, "sparkling water" to DRINKS,
        "toilet paper" to HOUSEHOLD, "dish soap" to HOUSEHOLD, "paper towels" to HOUSEHOLD,
        "laundry detergent" to HOUSEHOLD, "cat food" to PETS,
    )

    /** Чего словарь знать не должен: лучше «Прочее», чем чужой отдел. */
    private val unknown = listOf("подарок маме", "открытка", "цветы", "билеты в кино", "gift card")

    @Test
    fun `corpus is categorized`() {
        val misses = corpus.mapNotNull { (title, expected) ->
            val actual = ShoppingCategoryDictionary.categorize(title)
            if (actual == expected) null else "«$title»: ждали $expected, получили $actual"
        }
        assertTrue(
            "Промахов ${misses.size} из ${corpus.size}:\n" + misses.joinToString("\n"),
            misses.isEmpty(),
        )
    }

    @Test
    fun `unknown things stay unknown`() {
        val wrong = unknown.mapNotNull { title ->
            ShoppingCategoryDictionary.categorize(title)?.let { "«$title» → $it" }
        }
        assertTrue("Ложные отделы:\n" + wrong.joinToString("\n"), wrong.isEmpty())
    }
}
