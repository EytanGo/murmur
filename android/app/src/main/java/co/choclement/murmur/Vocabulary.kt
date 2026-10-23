package co.choclement.murmur

/**
 * Fixes Hebrew and Yiddish words said in the middle of English ("I'm going to the mikvah",
 * "this is Aryeh"). The phone's Whisper engine can't be given a word list, so instead the
 * transcript is checked afterwards: known mis-hearings are swapped for the right spelling, and
 * words that *sound* like a term (same consonants, any vowels, like "mick vah") are too, unless
 * that sound is also an ordinary English word.
 */
object Vocabulary {

    /**
     * Each line is the spelling to use, then the ways Whisper tends to write it, comma-separated. A leading "="
     * means "only these exact spellings": the term sounds too much like English to match loosely.
     */
    private const val TERMS = """
Aryeh | arya, arye, arie, arieh, aryay, ariyeh, ariya, aryah, ari yeh, ar yeh, ari ya
mikvah | mikva, mikveh, mickva, mickvah, mikvo, mikvahs, mick vah, mic vah, mick fa
mikvaot | mikvot, mikvaos, mikvos
Shabbat | shabat, shabath, shabbath
Shabbos | shabos, shabbas, shabbes, shabus, shabis
Hashem | hashim, hasham, ha-shem
Baruch Hashem | baruch hashem, baruk hashem
=Torah | torah, tora, toyrah
Talmud | talmood
Gemara | gemora, gemarah, gimara
Mishnah | mishna, mishneh
halacha | halacha, halakha, halakhah, halachah
mitzvah | mitzva, mitsvah, mitsva, mitzvahs
mitzvot | mitzvos, mitsvot, mitzvoth
tzedakah | tzedaka, tsedakah, tsedaka, zedakah, sedaka
tefillin | tefilin, tfillin, tephillin
tallit | talit, tallis, tallith
tzitzit | tzitzis, tsitsit, tsitsis, zizit
kippah | kippa, kipa, kipah
yarmulke | yarmulka, yamaka, yarmulkah
sukkah | sukka, succah, suka
Sukkot | sukkos, succot, succos, sukot
Pesach | pesah, peysach, paysach
Shavuot | shavuos, shavuous
Rosh Hashanah | rosh hashana, rosh hashonah, rosh ha shana
Yom Kippur | yom kipper, yom kipur
Chanukah | hanukkah, hanukah, chanuka, hanuka, chanukkah
Purim | poorim
Simchat Torah | simchas torah, simhat torah
Tisha B'Av | tisha bav, tisha b'av, tishabav
challah | chala, chalah, halla, hallah
kiddush | kidush
havdalah | havdala
=daven | daven, davin
=davening | davening, davining
=shul | shul
yeshiva | yeshivah, yeshivas
kollel | kolel
=Rebbe | rebbe
rebbetzin | rebbitzen, rebbetzen, rebbitzin
=bris | bris
brit milah | bris milah, brit mila
bar mitzvah | bar mitzva, barmitzvah, barmitzva
bat mitzvah | bat mitzva, batmitzvah, batmitzva
chuppah | chupah, huppah, chupa, huppa
ketubah | ketuba, kesubah, kesuba
sheitel | shaytel, sheitl
shidduch | shiduch, shiduk, shidduk
shadchan | shadkhan, shatchen
chesed | chessed, hesed
emunah | emuna
bitachon | bitochon, bitakhon
teshuvah | teshuva, tshuva
B'ezrat Hashem | bezrat hashem
Im Yirtzeh Hashem | im yirtze hashem, im yirtza hashem
mazel tov | mazal tov, mazeltov, mazaltov
l'chaim | lchaim, lechaim, l'chayim, le chaim
=shalom | shalom
Shabbat Shalom | shabat shalom
Gut Shabbos | good shabbos, gut shabbes
Chag Sameach | chag sameakh, hag sameach, chag same'ach
=shiva | shiva
kaddish | kadish
Shema | shma, sh'ma
Amidah | amida
siddur | sidur
machzor | machsor
mezuzah | mezuza
menorah | menora
chametz | chometz, hametz
matzah | matza, matzo, matzoh
=seder | seder
Haggadah | hagaddah, hagada, haggada
megillah | megila, megilla
mishloach manot | mishloach manos
eruv | eruv
shtiebel | shteibel, shtibel
Chassidus | chasidus, hasidus
Chabad | habad
Lubavitch | lubavich
tznius | tzniyus, tznuis
=frum | frum
chutzpah | chutzpa, hutzpah, hutspa
kvetch | kvech
schlep | shlep
nebach | nebech
oy vey | oy vay, oyvey
mensch | mench, mentsh
naches | nachas, nachus
tachlis | tachlit
gevalt | gevald
kugel | kugal
cholent | cholnt, tsholent
rugelach | rugalach, rugelah
latkes | latkas, latke's
sufganiyot | sufganiyos
Yerushalayim | yerushalaim
Eretz Yisrael | eretz yisroel
aliyah | aliya
Kotel | kosel
Beis Hamikdash | bais hamikdash, beit hamikdash
Moshiach | mashiach, moshiac, mashiah
parsha | parasha, parshah
dvar Torah | d'var torah, devar torah
chavrusa | chavrusah, chavruta, havruta
shiur | shiyur
posek | poseck
Tosafos | tosafot
=Rashi | rashi
Rambam | rambam
gemach | g'mach, gmach
simcha | simchah
tehillim | tehilim
bentch | bentsh
bentching | bentshing
minyan | minyon
Mincha | minha
Maariv | ma'ariv
Shacharis | shacharit, shaharit
Musaf | mussaf
Selichos | selichot, slichot
shofar | shofer
lulav | lulov
etrog | esrog
Moshe | moishe, moshey
Yaakov | yakov, ya'akov, yaacov
Yitzchak | yitzhak, itzhak, yitzchok
Avraham | avrohom, avrum
Shlomo | shlomoh
Yosef | yossef
=Chaim | chaim, haim, hayim, chayim
Mordechai | mordechi, mordecai
Menachem | menahem
Yehuda | yehudah
Shmuel | shmual
Eliyahu | eliyahoo
Dovid | dovid
Tzvi | tsvi, zvi
=Yisroel | yisroel
Akiva | akiba
Ephraim | efraim, efrayim
Naftali | naphtali
Pinchas | pinhas
Gedalia | gedalya, gedaliah
Elchanan | elhanan
Rivka | rivkah, rifka
Rochel | rochel
Chana | chanah
Devorah | dvora, devora
Tzipora | tzipporah, tsipora, zipporah
Shoshana | shoshanah
Hadassah | hadasa, hadassa
Batsheva | bat sheva, batsheba
Chaya | chayah
Malka | malkah
Shira | shirah
Yehudis | yehudit
"""

    /**
     * English words and names (from the macOS dictionary) that sound like a term. These are never
     * changed, so "mushy" stays "mushy" even though "moishe" becomes "Moshe".
     */
    private val ENGLISH = """
aevia akawai amadi amadou amani amania amedeo amene amenia amide amido amiidae amine amini amino
amioidei ammine ammonea ammonia ammono arow arrow arya avouch away awee battakhin caddish calool
cape cappie cappy cattabu cattail caupo cautel cedar ceder cequi challie chane chapah chape chapeau
chappie chappy chauna cheepy chela chello chena cheney chenica cheyney chile chili chilla chilli
chillo chilly china chine chinee chinny chino chinoa chippy choana chola choli cholla cholo chopa
choppy choya chubbed chunnia chyle cider coattail coital copa cope copei coppy copy coupe coupee
coutel coutil coypu cupay cuppy cutheal cuttail cyke david devoid dowed dowery eave emydea ethrog
evea evoe game gamma gammy gamori gamy gaudily gaumy gemma gemmae gemmy geomoroi giddily gimirrai
goma gomari gooma goumi guama guaymie gumma gummy haggada haggaday hailey haily hainai haine hala
hale haley hallah hallie halloo halo haloa hanna hannah hano hanoi hanukkah haole hapi happy hapu
haya hayey hayseed heapy heinie hele heliaea helio hello helluo helly heloe henna henny hilly hilo
hinau hinney hinny hinoki hipe hippa hippia hippo hippy hole holey holia holla hollo holly holy hone
honey hooly hoopoe hope hopi hoppy hosed hoya hoyle hugsome hula hulloo hulu hupa hyla hyle hyne
hypo iiwi imide imine imino immane immune iowa kapa kapai kappa kappe kathal kepi ketal ketol ketuba
ketyl khaya khila khula kiddish kiddush kipe kippy kittel kittul kopi koppa kotal maize manche
manchu manure marrow masha mashie mashy massif matsu matzo maza maze mazy menura menurae merrow
meshech meshy metze mezzo miaotse miaotze milky minery minhah mirdaha mischio mizzy molka monera
moonery morrow moschi mosha mouzah muggily musha mushaa mushy muzo muzzy mygale nabby nabu nebby
nibby niobe nobby nuba nubby nubia omani omina outrig ovey pace paisa param pasi passe passee passo
pause peace peasy peise pesa pesah peso pice picea pici piece pisay pise piso poaceae poesie poesy
poise pose posey posse posy pouce poucey puce purshia pussy quiapo quipo quippy quipu rachel rachial
raggily ragule raguly rahul rakhal rechal regale regalia regally regula reguli rehaul reheal reheel
sacae saco sacque saka sakai sake saki sakieh sauqui schema scheme schemy schloop secque seder
seeder sequa sequoia shabbath shabuoth shade shady shama shame shammy share shari sharia sharra
sharry shauri shear shebat sheer shemu sher shera sherri sherry shide shier shimei shimmy shire
shirr shoddy shode shoer shoeshine shoor shophar shor shore shorea shure sicca sidder sider sika
sike siket soaky socket socky soco soekoe soka soke sooke sooky sudder sukey suku tailet tallet
tallith teachless teleut tephillin thuluth tillot toilet touchless uvea zapara zaparo zoopery
""".split(Regex("\\s+")).filter { it.isNotEmpty() }.toHashSet()

    /** The most common English words, which a split-up term may not be built from. */
    private val COMMON = """
a about above after again all also am an and any are as at be because been before being below between both
but by can could did do does doing down during each few for from further had has have having he her here hers
him his how i if in into is it its just me more most my no nor not now of off on once only or other our out over
own same she should so some such than that the their them then there these they this those through to too under
until up very was we were what when where which while who whom why will with would you your yours
get got go going gone good make made take put pass give say said see seen come came know think want need like
one two three new old way well back even still let lot bit kind sort mean yeah okay oh hey thing things file mode
""".split(Regex("\\s+")).filter { it.isNotEmpty() }.toHashSet()

    private class Term(val spelling: String, val loose: Boolean)

    private val phrases = HashMap<String, Term>()   // listed spelling, words kept apart -> term
    private val exact = HashMap<String, Term>()     // squashed spelling -> term
    private val sounds = HashMap<String, Term>()    // sound key -> term
    private const val MAX_WORDS = 3

    init {
        for (line in TERMS.lines()) {
            if ('|' !in line) continue
            val (head, rest) = line.split('|', limit = 2)
            val loose = !head.trim().startsWith("=")
            val term = Term(head.trim().removePrefix("="), loose)
            for (v in rest.split(',') + term.spelling) {
                exact[squash(v)] = term
                phrases[words(v)] = term
            }
            if (loose) sounds[key(term.spelling)] = term
        }
    }

    /** Fixes a transcript. Text without Latin letters (e.g. Hebrew script) is left alone. */
    fun fix(text: String): String {
        if (text.none { it in 'a'..'z' || it in 'A'..'Z' }) return text
        val words = text.split(' ').filter { it.isNotEmpty() }
        val out = ArrayList<String>()
        var i = 0
        while (i < words.size) {
            var matched = false
            for (n in minOf(MAX_WORDS, words.size - i) downTo 1) {
                val window = words.subList(i, i + n)
                // Only join words that aren't split by punctuation ("shabbat. shalom" stays two).
                if (window.dropLast(1).any { it.last().isPunctuationMark() }) continue
                val (stem, possessive) = possessive(window)
                val term = match(stem) ?: continue
                out += respell(stem, term) + possessive
                i += n
                matched = true
                break
            }
            if (!matched) out += words[i++]
        }
        return out.joinToString(" ")
    }

    private fun match(window: List<String>): Term? {
        phrases[words(window.joinToString(" "))]?.let { return it }
        val joined = squash(window.joinToString(""))
        if (joined.length < 3) return null
        if (window.size > 1) {
            // A term split in two ("Mick Vah"), but never built from everyday words ("a rye" isn't Aryeh).
            return if (window.any { squash(it) in COMMON }) null else exact[joined]
        }
        exact[joined]?.let { return it }
        // By sound: single words only, and not real English words or acronyms like HELO.
        if (joined.length < 4 || joined in ENGLISH || window[0].count { it.isUpperCase() } > 1) return null
        val k = key(joined)
        return if (k.length >= 4) sounds[k] else null
    }

    /** "Arya's," is matched as "Arya", then gets its "'s," back. */
    private fun possessive(window: List<String>): Pair<List<String>, String> {
        val last = window.last()
        val m = Regex("['’]s[^\\p{L}\\p{N}]*$").find(last) ?: return window to ""
        return (window.dropLast(1) + last.dropLast(m.value.length)) to m.value
    }

    /** Puts the right spelling in, keeping the punctuation around it and a capital at the start. */
    private fun respell(window: List<String>, term: Term): String {
        val first = window.first(); val last = window.last()
        val lead = first.takeWhile { !it.isLetterOrDigit() }
        val trail = last.takeLastWhile { !it.isLetterOrDigit() }
        val core = first.drop(lead.length)
        var s = term.spelling
        if (core.firstOrNull()?.isUpperCase() == true) s = s.replaceFirstChar { it.uppercaseChar() }
        return lead + s + trail
    }

    private fun Char.isPunctuationMark() = this in ".,!?;:"

    /** Lowercase words of letters: "Good  Shabbos!" -> "good shabbos". */
    private fun words(s: String) = s.split(' ').map(::squash).filter { it.isNotEmpty() }.joinToString(" ")

    /** Lowercase letters only: "Mick-Vah," -> "mickvah". */
    private fun squash(s: String) = s.lowercase().filter { it in 'a'..'z' }

    /**
     * How a word sounds, roughly: its consonants with every vowel group as "a", spelled the way
     * Hebrew is usually transliterated (ch = kh = h, tz = ts = z, sh = sch, ck = q = k, w = v).
     */
    fun key(word: String): String {
        val w = squash(word)
        val sounds = StringBuilder()
        var i = 0
        while (i < w.length) {
            val two = w.substring(i, minOf(i + 2, w.length))
            val (sound, used) = when {
                w.startsWith("sch", i) -> "#" to 3
                two == "sh" -> "#" to 2
                two == "ch" || two == "kh" -> "h" to 2
                two == "ph" -> "f" to 2
                two == "th" -> "t" to 2
                two == "tz" || two == "ts" -> "z" to 2
                two == "ck" -> "k" to 2
                w[i] == 'c' -> (if (w.getOrNull(i + 1)?.let { it in "eiy" } == true) "s" else "k") to 1
                w[i] == 'q' -> "k" to 1
                w[i] == 'w' -> "v" to 1
                w[i] == 'x' -> "ks" to 1
                else -> w[i].toString() to 1
            }
            sounds.append(sound)
            i += used
        }
        var s = sounds.toString()
        if (s.length > 2 && s.last() == 'h' && s[s.length - 2] in "aeiouy") s = s.dropLast(1)
        val out = StringBuilder()
        for ((n, c) in s.withIndex()) {
            val vowel = c in "aeiou" || (c == 'y' && s.getOrNull(n + 1)?.let { it in "aeiou" } != true)
            val ch = if (vowel) 'a' else c
            if (out.lastOrNull() != ch) out.append(ch)
        }
        return out.toString()
    }
}
