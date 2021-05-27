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
    TIDLIGERE("Tidligere enn de siste to årene.")
}

internal fun List<Barn>.somMapTilPdf(): List<Map<String, Any?>> {
    return map {
        mapOf<String, Any?>(
            "navn" to it.navn.capitalizeName(),
            "identitetsnummer" to it.identitetsnummer,
            "tidspunktForAleneomsorg" to it.tidspunktForAleneomsorg.pdfUtskrift,
            "dato" to it.dato
        )
    }
}

fun String.capitalizeName(): String = split(" ").joinToString(" ") { it.lowercase(Locale.getDefault()).capitalize() }