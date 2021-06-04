package no.nav.helse.prosessering.v1.søknad

import java.time.LocalDate
import java.util.*

data class Barn (
    val navn: String,
    val aktørId: String?,
    var identitetsnummer: String?,
    val tidspunktForAleneomsorg: TidspunktForAleneomsorg,
    val dato: LocalDate? = null
) {
    override fun toString(): String {
        return "Barn(navn='$navn', aktørId=*****, identitetsnummer=*****)"
    }
}

enum class TidspunktForAleneomsorg(val pdfUtskrift: String) {
    SISTE_2_ÅRENE("De siste to årene."),
    TIDLIGERE("Tidligere enn de to siste årene.")
}

internal fun Barn.somMapTilPdf(): Map<String, Any?> {
    return mapOf<String, Any?>(
        "navn" to navn.capitalizeName(),
        "identitetsnummer" to identitetsnummer,
        "tidspunktForAleneomsorg" to tidspunktForAleneomsorg.pdfUtskrift,
        "dato" to dato
    )
}

fun String.capitalizeName(): String = split(" ").joinToString(" ") { it.lowercase(Locale.getDefault()).capitalize() }