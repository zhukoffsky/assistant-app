package com.zhukoffsky.magpie.feature.shopping.domain

/**
 * Режет сплошную строку на товары — когда резать её больше некому.
 *
 * Нужен на полностью офлайновом пути: распознаватель на телефоне на
 * ускоренной подаче отдаёт «хлеб бородинский молоко ещё пельмени» без
 * единой запятой, а модели, которая поняла бы границы, нет. Правила режут
 * только по знакам и «и»; здесь границы угадываются по словарю отделов.
 *
 * Где режем:
 * - на смене отдела: «хлеб бородинский | молоко | пельмени»;
 * - на повторе товара: «хлеб белый | хлеб чёрный»;
 * - между двумя известными существительными подряд, даже одного отдела:
 *   «хлеб | булочка», «вода | coca-cola»;
 * - перед вторым прилагательным подряд после существительного — оно уже
 *   начинает новый товар: «вырезка свиная | куриные бёдрышки»;
 * - перед прилагательным, которое согласовано со следующим словом, а не с
 *   предыдущим: «вода | подсолнечное масло», но «хлеб бородинский | молоко».
 *
 * Где не режем — склеить два товара лучше, чем разорвать один:
 * - после предлога: «макароны с сыром», «средство для посуды»;
 * - после прилагательного, с которого товар начался: «апельсиновый сок»,
 *   «куриные бёдрышки»;
 * - после слова-меры: «пачка масла», «десяток яиц»;
 * - после общего слова, за которым идёт его разновидность: «сыр моцарелла»,
 *   «чай каркаде».
 *
 * Согласование проверяется по окончаниям, без словаря форм, и только для
 * именительного падежа — в нём товары и диктуют. Это цена правил, и платит
 * её только путь без модели.
 */
object ShoppingAisleSplitter {

    fun split(item: String): List<String> {
        val words = item.trim().split(WHITESPACE)
            .filter { it.isNotEmpty() && it.lowercase() !in FILLERS }
        if (words.isEmpty()) return emptyList()

        val groups = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        var currentAisle: ShoppingCategory? = null
        val seenHeads = mutableSetOf<String>()

        words.forEachIndexed { index, word ->
            val aisle = ShoppingCategoryDictionary.categorize(word)
            val previous = words.getOrNull(index - 1)
            val head = word.lowercase().replace('ё', 'е').take(HEAD_LENGTH)
            val groupHasNoun = current.any { !it.isAdjective() && ShoppingCategoryDictionary.categorize(it) != null }
            // Прилагательное, с которого товар начался, держит следующее слово.
            val previousOpensItem = previous != null && previous.isAdjective() && !groupHasNoun

            val aisleChanged = aisle != null && currentAisle != null && aisle != currentAisle
            val repeated = aisle != null && head in seenHeads
            // Второе прилагательное начинает новый товар, только если к
            // прежнему не подходит: «вырезка свиная | куриные бёдрышки», но
            // «вино красное сухое».
            val groupNoun = current.lastOrNull { it.isKnownNoun() }
            val secondAdjective = word.isAdjective() && previous?.isAdjective() == true && groupNoun != null &&
                !agrees(word, groupNoun)
            // Товар уже закончен прилагательным после существительного:
            // «яблоки зелёные | бананы».
            val nounAfterComplete = word.isKnownNoun() && previous?.isAdjective() == true && groupNoun != null
            // Мера открывает новый товар: «подсолнечное масло | пачка сливочного масла».
            val measure = word.lowercase() in QUANTITIES && groupNoun != null
            val secondNoun = word.isKnownNoun() && previous != null && previous.isKnownNoun() &&
                previous.lowercase() !in QUANTITIES && !previous.isGenericOf(aisle)
            val next = words.getOrNull(index + 1)
            val afterNoun = word.isAdjective() && groupHasNoun && previous?.isAdjective() == false
            val agreesWithNext = afterNoun && next != null && next.isKnownNoun() && agrees(word, next)
            val agreesWithPrevious = afterNoun && agrees(word, previous!!)
            // Согласовано с обоими — решает отдел («вареники | куриные
            // бёдрышки»: куриное к мясу, а не к заморозке), а без него манера,
            // в которой начат товар: «белый хлеб | чёрный хлеб».
            val sameAisleAsNext = aisle != null && aisle != currentAisle &&
                aisle == next?.let(ShoppingCategoryDictionary::categorize)
            val opensNext = agreesWithNext &&
                (!agreesWithPrevious || sameAisleAsNext || current.first().isAdjective())
            // «яйца куриные»: у прилагательного свой отдел, но товар прежний.
            val postposed = agreesWithPrevious && !opensNext
            val phrase = previous != null && ShoppingCategoryDictionary.containsPhrase("$previous $word")

            val boundary = current.isNotEmpty() &&
                previous?.lowercase() !in PREPOSITIONS &&
                !previousOpensItem && !postposed && !phrase &&
                (aisleChanged || repeated || secondAdjective || secondNoun || opensNext || nounAfterComplete || measure)

            if (boundary) {
                groups += current
                current = mutableListOf()
                currentAisle = null
                seenHeads.clear()
            }
            current += word
            if (aisle != null) {
                if (currentAisle == null) currentAisle = aisle
                seenHeads += head
            }
        }
        groups += current

        return groups.map { ShoppingPhraseParser.clean(it.joinToString(" ")) }.filter { it.isNotEmpty() }
    }

    /**
     * Уверены ли правила в позиции: до предлога не больше трёх слов, и каждое
     * существительное там знакомо словарю. Незнакомое слово — возможно,
     * расслышанное неверно или приклеенное к соседу («молоко подарок маме»:
     * у позиции есть отдел, но подарок к молоку не относится). Слова-меры и
     * числа незнакомыми не считаются: «пачка», «2,5%».
     */
    fun isSure(title: String): Boolean {
        val head = title.trim().split(WHITESPACE)
            .filter { it.isNotEmpty() }
            .takeWhile { it.lowercase() !in PREPOSITIONS }
        if (head.isEmpty() || head.size > MAX_HEAD_WORDS) return false
        // Словосочетание из словаря знакомо целиком, даже если слова по
        // отдельности нет: «мешки для мусора», «крабовые палочки».
        if (ShoppingCategoryDictionary.containsPhrase(title)) return true
        return head.all { word ->
            word.isAdjective() || word.lowercase() in QUANTITIES || word.any(Char::isDigit) ||
                ShoppingCategoryDictionary.categorize(word) != null
        }
    }

    private fun String.isAdjective(): Boolean {
        val word = lowercase()
        return word.length > 3 && ADJECTIVE_ENDINGS.any { word.endsWith(it) }
    }

    private fun String.isKnownNoun(): Boolean = !isAdjective() && ShoppingCategoryDictionary.categorize(this) != null

    /** «Сыр», за которым идёт сыр же: дальше не новый товар, а какой именно. */
    private fun String.isGenericOf(aisle: ShoppingCategory?): Boolean {
        val word = lowercase().replace('ё', 'е')
        return GENERIC.any { word.startsWith(it) } && ShoppingCategoryDictionary.categorize(this) == aisle
    }

    /**
     * Согласовано ли прилагательное с существительным в именительном падеже:
     * род и число видны по окончаниям. Мягкий знак бывает и в мужском, и в
     * женском роде («картофель», «морковь»), поэтому подходит обоим.
     * Множественное на «-а» среди продуктов по сути одно — «яйца»; общим
     * правилом его не взять, «вырезка» кончается так же.
     */
    private fun agrees(adjective: String, noun: String): Boolean {
        val a = adjective.lowercase()
        val word = noun.lowercase().replace('ё', 'е')
        val last = word.last()
        return when {
            a.endsWith("ое") || a.endsWith("ее") -> last in "ое"
            a.endsWith("ая") || a.endsWith("яя") -> last in "аяь"
            a.endsWith("ые") || a.endsWith("ие") -> last in "ыия" || word == "яйца"
            a.endsWith("ую") || a.endsWith("юю") -> last in "ую"
            a.endsWith("ый") || a.endsWith("ий") || a.endsWith("ой") -> last !in "аяоеыиую"
            else -> false
        }
    }

    private val WHITESPACE = Regex("""\s+""")

    /** «вино красное сухое» — три; четвёртое слово до предлога — уже склейка. */
    private const val MAX_HEAD_WORDS = 3

    /** Сколько первых букв считать «тем же словом»: «хлеб» и «хлеба». */
    private const val HEAD_LENGTH = 4

    private val PREPOSITIONS = setOf("с", "со", "для", "из", "в", "во", "без", "на", "под", "от", "к", "with", "for", "of")

    /** После них идёт то, что меряют, а не новый товар: «пачка масла». */
    private val QUANTITIES = setOf(
        "пачка", "пачку", "пакет", "пакета", "банка", "банку", "бутылка", "бутылку", "бутылки", "коробка",
        "коробку", "упаковка", "упаковку", "десяток", "десятка", "кило", "килограмм", "литр", "литра",
        "pack", "bag", "can", "jar", "bottle", "box", "dozen",
    )

    /** Общие слова, за которыми называют разновидность: «сыр моцарелла». */
    private val GENERIC = listOf("сыр", "чай", "кофе", "колбас", "рыб", "мяс", "сок", "вин", "пив", "каш", "круп", "гриб")

    /** Слова-заминки посреди перечисления: «молоко ещё пельмени». */
    private val FILLERS = setOf(
        "ещё", "еще", "также", "потом", "наверное", "наверно", "надо", "нужно", "давай", "давайте",
        "купить", "купи", "возьми", "взять", "ну", "вот", "так", "also", "then", "buy",
    )

    private val ADJECTIVE_ENDINGS = listOf(
        "ый", "ий", "ой", "ая", "яя", "ое", "ее", "ые", "ие", "ую", "юю", "ого", "его", "ых", "их",
    )
}
