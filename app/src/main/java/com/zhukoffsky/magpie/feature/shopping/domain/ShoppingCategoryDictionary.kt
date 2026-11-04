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

/**
 * Отдел по названию — без сети и без модели.
 *
 * Раньше отдел знала только модель в облаке, и всё, что её миновало, шло в
 * «Прочее»: набранное с клавиатуры, диктовка при отказавшем провайдере.
 * 4 ноября 2026 провайдер отказал на полдня, и диктовка легла сплошным
 * «Прочим». Словарь закрывает частые продукты — модель остаётся для редкого
 * и для разрезания сплошной фразы.
 *
 * **Как устроено.** Таблица «начало слова → отдел»: «пельмен» ловит
 * «пельмени» и «пельменей», «курин» — «куриные бёдрышки». Начала, а не
 * слова, потому что русский склоняется: в диктовке «гречку», а не «гречка».
 *
 * **Порядок записей — это приоритет**, побеждает первая совпавшая. Отсюда
 * последовательность:
 * 1. Адресат, а не продукт: «корм для кошек», «консервы для кошек» — товары
 *    для животных, «детское пюре» — детские товары, «таблетки для
 *    посудомойки» — бытовая химия, а не аптека.
 * 2. Слова, чьё начало совпадает с чужим отделом («виноград» — не «вино»,
 *    «сельдерей» — не «сельдь», «сырок» — не «сыр»), и готовое из продукта
 *    («куриный бульон» — бакалея, «картошка фри» — заморозка, «сосиски
 *    молочные» — колбасы).
 * 3. Непродовольственное и напитки — раньше продуктов, которые в них
 *    упоминаются («средство для мытья посуды», «яблочный сок», «рыбий
 *    жир» — здоровье, «зелёнка» — не зелень).
 * 4. Рыба раньше колбас, колбасы раньше мяса: «печень трески» — рыба,
 *    «ветчина куриная» — колбасы.
 */
object ShoppingCategoryDictionary {

    fun categorize(title: String): ShoppingCategory? {
        val words = WORD.findAll(title.lowercase().replace('ё', 'е')).map { it.value }.toList()
        if (words.isEmpty()) return null
        val joined = " " + words.joinToString(" ")

        // Главное — до предлога: «рис для плова» — про рис, «приправа для
        // курицы» — про приправу, «булка с маком» — про булку.
        val head = words.takeWhile { it !in PREPOSITIONS }.ifEmpty { words }
        val (adjectives, nouns) = head.partition(::looksLikeAdjective)

        // Первый проход: словосочетания по всей фразе, слова — по
        // существительным главной части. «уксус яблочный» — уксус, а не
        // яблоко; «семечки тыквенные» — семечки, а не тыква.
        ENTRIES.firstOrNull { (stem, _) ->
            when {
                ' ' in stem -> joined.contains(" $stem")
                stem.startsWith('~') -> head.matches(stem.substring(1))
                else -> nouns.matches(stem)
            }
        }?.let { return it.second }

        // Второй проход — прилагательные, когда больше зацепиться не за что:
        // «детское питание», «индюшиные ноги», «крабовые палочки».
        return ENTRIES.firstOrNull { (stem, _) ->
            ' ' !in stem && adjectives.matches(stem.removePrefix("~"))
        }?.second
    }

    /** Есть ли во фразе словосочетание из словаря: «картошка фри» — один товар, а не два. */
    fun containsPhrase(text: String): Boolean {
        val joined = " " + WORD.findAll(text.lowercase().replace('ё', 'е')).joinToString(" ") { it.value }
        return ENTRIES.any { (stem, _) -> ' ' in stem && joined.contains(" $stem") }
    }

    private fun List<String>.matches(stem: String): Boolean =
        if (stem.startsWith('=')) stem.substring(1) in this else any { it.startsWith(stem) }

    private fun looksLikeAdjective(word: String): Boolean =
        word.length > 3 && ADJECTIVE_ENDINGS.any { word.endsWith(it) }

    private val PREPOSITIONS = setOf(
        "для", "с", "со", "в", "во", "без", "от", "из", "на", "под", "по", "for", "with", "in", "without", "from",
    )

    private val ADJECTIVE_ENDINGS = listOf(
        "ый", "ий", "ой", "ая", "яя", "ое", "ее", "ые", "ие", "ую", "юю", "ого", "его", "ых", "их", "ым", "им",
    )

    private val WORD = Regex("""\p{L}+""")

    private fun MutableList<Pair<String, ShoppingCategory>>.add(category: ShoppingCategory, vararg stems: String) {
        stems.forEach { add(it to category) }
    }

    /*
     * Запись — начало слова («пельмен»), точное слово («=ром», чтобы не
     * цеплять «ромашку») или начало словосочетания («картошка фри»). Всё в
     * нижнем регистре и через «е» вместо «ё».
     *
     * «~» — прилагательное, которое само задаёт отдел и потому сравнивается
     * сразу, наравне с существительными: «замороженные овощи» — заморозка,
     * «сгущённое молоко» — консервы, «детский шампунь» — детское.
     */
    private val ENTRIES: List<Pair<String, ShoppingCategory>> = buildList {
        // ── 1. Адресат важнее продукта ─────────────────────────────────────
        add(
            PETS,
            "для кошек", "для котят", "для собак", "для щенков", "для животных", "для попугая",
            "~кошач", "~собач", "=корм", "корма", "корм для", "наполнитель", "когтеточк",
            "cat food", "dog food", "cat litter", "litter", "pet",
        )
        add(
            KIDS,
            "~детск", "для детей", "для малыш", "подгузник", "памперс", "смесь для", "соска", "пустышк",
            "baby", "diaper", "infant",
        )
        add(HOUSEHOLD, "таблетки для", "капсулы для", "~хозяйственн", "dish soap", "dishwasher", "paper plates")

        // ── 2. Ловушки: чужое начало слова и готовое из продукта ───────────
        add(PRODUCE, "виноград", "сельдер", "watermelon", "eggplant")
        add(
            DAIRY,
            "сырок", "сырки", "сырков", "сырочк", "сливки для", "молоко для", "кефирный напиток",
            "йогуртов", "молочный напиток", "кисломолочн", "cream cheese", "sour cream", "buttermilk",
        )
        add(SWEETS, "печенье", "печенья", "печеньк", "печенюш", "батончик", "молочный шоколад")
        add(SNACKS, "чипс", "сухарик", "крекер", "попкорн", "tortilla chips", "pretzel", "jerky", "trail mix")
        add(DELI, "pepperoni", "сосиск", "сосисоч", "сардельк", "колбас")
        add(MEAT, "суповой набор", "на кости")
        add(BAKERY, "булочки для", "булочка для")
        add(GROCERY, "кукурузная мука")
        add(BREAKFAST, "геркулес", "cornflake")
        add(
            HYGIENE,
            "зубная паст", "зубную паст", "зубной паст", "зубная щетк", "зубную щетк", "зубная нить",
        )
        add(
            CANNED,
            "кабачковая икра", "икра кабачков", "икру кабачков", "баклажанная икра", "~сгущ", "nutella",
            "шоколадная паст", "шоколадную паст", "в томатном соусе", "в собственном соку",
        )
        add(CANNED, "арахисовая паста", "ореховая паста", "peanut butter", "~консерв", "тушенк", "шпрот", "сайр", "горошек", "кабачков", "маслин", "бульон", "кубик", "canned", "broth")
        add(
            CONDIMENTS,
            "соус", "кетчуп", "майонез", "аджик", "лечо", "перец черн", "перец молот", "черный перец",
            "молотый перец", "кислот", "кукурузный крахмал", "паприк",
            "масло подсолнечн", "подсолнечное масло", "масло оливков", "оливковое масло",
            "масло растительн", "растительное масло", "масло кукурузн", "кукурузное масло",
            "масло льнян", "льняное масло", "масло кунжутн", "кунжутное масло", "томатная паст", "томатную паст", "томатной паст",
            "sauce", "ketchup", "mayo", "pesto", "gravy", "olive oil", "sunflower oil",
        )
        add(
            BREAKFAST,
            "хлопь", "мюсли", "гранол", "сухой завтрак", "готовый завтрак", "готовые завтраки",
            "cereal", "granola", "oatmeal", "pancake mix", "pop tart",
        )
        add(
            READY_MEALS,
            "салат оливье", "оливье", "салат цезарь", "цезарь", "крабовый салат", "салат с", "винегрет",
            "салат греческ", "греческий салат", "мимоза", "под шубой", "борщ", "=щи", "солянк", "окрошк",
            "харчо", "рассольник",
            "суши", "ролл", "сэндвич", "шаурм", "шаверм", "бургер", "готов", "=суп", "супы", "супа",
            "плов", "голубц", "чебурек", "беляш",
            "ready meal", "sandwich", "sushi",
        )
        add(
            FROZEN,
            "картошка фри", "картофель фри", "=фри", "рыбные палочки", "сырник", "блинчик", "наггетс",
            "french fries", "fish sticks", "frozen",
        )
        add(
            HYGIENE,
            "для умыван", "для лица", "для волос", "для тела", "для рук", "для душа", "для ванн",
            "влажные салфет", "влажных салфет",
        )
        add(ALCOHOL, "рижский бальзам")

        // ── 3. Непродовольственное и напитки ───────────────────────────────
        // Заморозка первой: «мороженое крем-брюле» — не крем для рук.
        add(
            FROZEN,
            "~морожен", "пельмен", "вареник", "~заморож", "~замороз", "хинкал", "манты", "креветк", "=лед",
            "пицц", "лазань", "=тесто", "теста", "пломбир", "эскимо",
            "ice cream", "dumpling", "nugget", "popsicle", "pizza", "gelato",
        )
        // Здоровье раньше продуктов: «рыбий жир» — не рыба, «зелёнка» — не зелень.
        add(
            HEALTH,
            "витамин", "магний", "магне", "гематоген", "=бад", "=бады", "пластыр", "бинт", "аптечк", "лекарств", "таблетк", "=капли",
            "спрей для носа", "градусник", "термометр", "аспирин", "парацетамол", "ибупрофен", "нурофен",
            "омега", "рыбий жир", "презерватив", "тест на", "медицинск", "антисептик", "=йод",
            "зеленк", "перекис", "активированн", "мазь", "сироп от", "от кашля", "от горла", "от головы",
            "от давления", "от простуды",
            "vitamin", "medicine", "band-aid", "bandage", "painkiller", "ibuprofen", "aspirin",
            "supplement", "condom",
        )
        add(
            HYGIENE,
            "мыло", "мыла", "шампун", "бальзам для", "гель для", "=крем", "лосьон", "зубн", "зубочист",
            "щетк", "дезодорант", "антиперспирант", "бритв", "станок для", "лезви", "пена для",
            "прокладк", "тампон", "ватн", "косметик", "помад", "тушь", "маска для", "скраб", "духи",
            "soap", "shampoo", "conditioner", "toothpaste", "toothbrush", "deodorant", "razor",
            "lotion", "tampon", "pads", "perfume", "mouthwash",
        )
        add(
            HOUSEHOLD,
            "средств", "моющ", "чистящ", "стиральн", "порошок", "отбелив", "освежит", "пятновывод",
            "кондиционер для", "ополаскиват", "антинакип", "посудомо",
            "туалетн", "бумаг", "салфет", "полотенц", "губк", "мусорн", "пакеты", "мешки для",
            "тряпк", "швабр", "веник", "ершик", "перчатк", "фольг", "пленк", "пергамент",
            "батарейк", "лампочк", "спичк", "зажигалк", "свеч", "одноразов",
            "дворник", "омывайк", "омывател", "незамерзайк", "антифриз", "автошампун",
            "detergent", "toilet", "paper", "napkin", "sponge", "trash", "garbage", "bleach", "foil",
            "batter", "tissue", "wipes", "dish", "cleaner",
        )
        add(
            ALCOHOL,
            "пиво", "пивк", "пива", "вино", "винц", "вина", "коньяк", "виски", "водк", "шампанск",
            "игрист", "сидр", "=ром", "джин", "текил", "ликер", "вермут", "мартини", "рислинг",
            "настойк", "портвейн", "медовух", "бренди", "саке",
            "beer", "wine", "cider", "=gin", "=rum", "vodka", "whisk", "tequila", "champagne",
            "brandy", "cognac", "sake",
        )
        add(
            TEA_COFFEE,
            "чай", "чаек", "чая", "кофе", "какао", "цикори", "капучино", "латте",
            "coffee", "=tea", "teabag", "cocoa",
        )
        add(
            DRINKS,
            "сок", "вод", "минерал", "газиров", "лимонад", "кола", "пепси", "спрайт", "фанта", "швепс",
            "тоник", "квас", "компот", "морс", "кисел", "энергетик", "энергетич", "нектар", "смузи",
            "=напиток", "напитк", "мохито", "безалкогольн",
            "juice", "water", "soda", "cola", "coke", "lemonade", "smoothie", "drink",
        )

        // ── 4. Продукты ────────────────────────────────────────────────────
        add(
            BAKERY,
            "хлеб", "батон", "булк", "булочк", "багет", "лаваш", "лепешк", "=пита", "пирог", "пирож",
            "торт", "кекс", "круассан", "сушк", "баранк", "бублик", "ватрушк", "плюшк", "маффин",
            "кулич", "чиабатт", "бородинск", "рулет", "эклер", "слойк", "тост", "=блины", "блинов",
            "bread", "bun", "baguette", "cake", "croissant", "pie", "muffin", "bagel", "toast",
            "donut", "doughnut", "tortilla", "brioche", "focaccia", "sourdough",
        )
        // Рыба раньше молочного: «сельдь в масле» — рыба, а не масло.
        // «Филе» отдела не задаёт: решает, чьё оно — «филе минтая», «куриное филе».
        add(
            FISH,
            "~рыбн", "рыб", "лосос", "семг", "форел", "горбуш", "=кета", "кижуч", "нерк", "сельд", "селедк",
            "скумбри", "треск", "минта", "хек", "тунец", "тунц", "дорадо", "сибас", "карп", "судак",
            "щук", "окун", "камбал", "палтус", "килечк", "кильк", "мойв", "кальмар", "мидии",
            "краб", "икр", "осьминог", "морепродукт", "устриц", "гребешк",
            "fish", "salmon", "tuna", "=cod", "shrimp", "prawn", "seafood", "crab", "lobster",
            "sardine", "octopus",
        )
        add(
            CHEESE,
            "сыр", "моцарел", "пармезан", "брынз", "фета", "рикотт", "маскарпон", "сулугун",
            "адыгейск", "гауда", "чеддер", "камамбер", "=бри", "дорблю", "эмментал", "халлуми",
            "cheese", "mozzarella", "parmesan", "feta", "ricotta", "cheddar",
        )
        add(
            DAIRY,
            "молок", "молочн", "молочк", "кефир", "ряженк", "йогурт", "биойогурт", "творог", "творож",
            "сметан", "сливк", "сливочн", "масл", "яйц", "яиц", "яичк", "простокваш", "айран",
            "варенец", "снежок", "бифидок", "мацони", "=тан", "маргарин", "спред",
            "milk", "kefir", "yogurt", "yoghurt", "butter", "cream", "egg", "cottage", "custard",
        )
        add(
            DELI,
            "ветчин", "бекон", "салями", "сервелат", "буженин", "карбонад", "грудинк", "шпикачк",
            "балык", "хамон", "прошутто", "~копчен", "вяленое мясо", "паштет", "пастрам", "мортадел",
            "ham", "bacon", "salami", "sausage", "prosciutto", "pastrami", "mortadella", "chorizo",
        )
        add(
            MEAT,
            "мяс", "фарш", "говяд", "телят", "свинин", "свин", "баран", "ягнят", "курин", "куриц",
            "куры", "курочк", "цыпл", "бройлер", "окорок", "окорочк", "грудк", "бедр", "крыл",
            "голен", "ножк", "индейк", "индюш", "утк", "утин", "гус", "кролик", "печенк", "печень",
            "сердечк", "желудк", "шашлык", "котлет", "стейк", "вырезк", "антрекот", "ребр",
            "ребрышк", "шея", "шейк", "лопатк", "тефтел", "фрикадел",
            "meat", "beef", "pork", "chicken", "turkey", "lamb", "mince", "steak", "ribs",
        )
        add(
            PRODUCE,
            "овощ", "фрукт", "яблок", "яблоч", "груш", "банан", "апельсин", "мандарин", "клементин",
            "лимон", "лайм", "грейпфрут", "помело", "киви", "персик", "нектарин", "абрикос", "слив",
            "вишн", "черешн", "клубник", "малин", "черник", "голубик", "ежевик", "смородин",
            "крыжовник", "клюкв", "брусник", "ягод", "арбуз", "дын", "ананас", "манго", "авокадо",
            "гранат", "хурм", "инжир", "фейхоа", "картош", "картофел", "морков", "=лук", "лучок",
            "лучк", "лука", "чеснок", "чесноч", "капуст", "огур", "помидор", "томат", "черри", "перец",
            "перц", "кабач", "цукини", "баклажан", "тыкв", "свекл", "редис", "редьк", "=репа",
            "петрушк", "укроп", "зелен", "салат", "шпинат", "руккол", "кинз", "базилик", "щавел",
            "брокколи", "спарж", "гриб", "шампиньон", "вешенк", "имбир", "батат", "кукуруз",
            "apple", "banana", "orange", "lemon", "lime", "grape", "potato", "carrot", "onion", "garlic",
            "cabbage", "cucumber", "tomato", "pepper", "lettuce", "salad", "spinach", "berr", "strawberr",
            "blueberr", "raspberr", "avocado", "mushroom", "fruit", "vegetable", "pear", "peach", "plum",
            "cherr", "melon", "zucchini", "broccoli", "celery", "ginger", "herb", "dill", "parsley",
        )
        add(
            SWEETS,
            "шоколад", "конфет", "сладост", "мармелад", "зефир", "пастил", "халв", "вафл", "пряник",
            "кондитерск", "ирис", "леденц", "жвачк", "жевательн", "драже", "козинак", "щербет",
            "chocolate", "candy", "cookie", "biscuit", "marshmallow", "lollipop", "=gum", "mints",
        )
        add(
            SNACKS,
            "орех", "арахис", "миндал", "фисташк", "кешью", "фундук", "семечк", "снек", "сухофрукт",
            "изюм", "кураг", "чернослив", "финик", "кокос",
            "chips", "cracker", "=nut", "nuts", "almond", "peanut", "snack", "popcorn",
        )
        add(
            CANNED,
            "мед", "варень", "джем", "повидл", "конфитюр", "сироп", "сгущ", "оливк",
            "honey", "jam", "syrup",
        )
        add(
            CONDIMENTS,
            "сахар", "соль", "=сода", "уксус", "горчиц", "хрен", "специ", "приправ", "кориц", "ванил",
            "лавров", "паприк", "куркум", "дрожж", "разрыхлит", "желатин", "крахмал", "панировочн",
            "sugar", "salt", "oil", "mustard", "spice", "vinegar", "yeast", "baking",
        )
        add(
            GROCERY,
            "греч", "рис", "пшен", "перлов", "булгур", "кускус", "кус кус", "киноа", "манк", "круп", "каш",
            "овсян", "макарон", "спагетт", "паст", "лапш", "вермишел", "мук", "фасол", "чечевиц",
            "=нут", "горох",
            "rice", "pasta", "noodle", "flour", "bean", "lentil", "chickpea", "spaghetti", "penne",
            "fettuccine", "linguine", "macaroni", "gnocchi", "quinoa",
        )
    }
}
