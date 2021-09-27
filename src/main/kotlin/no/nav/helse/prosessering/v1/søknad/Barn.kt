package no.nav.helse.prosessering.v1.søknad

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

data class Barn (
    val navn: String,
    val aktørId: String,
    var identitetsnummer: String,
    val tidspunktForAleneomsorg: TidspunktForAleneomsorg,
    val dato: LocalDate? = null
) {
    override fun toString(): String {
        return "Barn(navn='$navn', aktørId=*****, identitetsnummer=*****)"
    }
}

enum class TidspunktForAleneomsorg(val pdfUtskrift: String) {
    SISTE_2_ÅRENE(""),
    TIDLIGERE("Du ble alene om omsorgen for over 2 år siden.")
}

internal fun Barn.somMapTilPdf(): Map<String, Any?> {
    return mapOf<String, Any?>(
        "navn" to navn.capitalizeName(),
        "identitetsnummer" to identitetsnummer,
        "tidspunktForAleneomsorgUtskrift" to tidspunktForAleneomsorg.pdfUtskrift,
        "dato" to if(dato!= null) DATE_TIME_FORMATTER.format(dato) else null
    )
}

private val ZONE_ID = ZoneId.of("Europe/Oslo")
private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZONE_ID)
fun String.capitalizeName(): String = split(" ").joinToString(" ") { it.lowercase(Locale.getDefault()).capitalize() }