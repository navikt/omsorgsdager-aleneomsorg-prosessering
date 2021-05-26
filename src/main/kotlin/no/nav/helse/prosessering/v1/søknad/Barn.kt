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

enum class TidspunktForAleneomsorg {
    SISTE_2_ÅRENE,
    TIDLIGERE
}

internal fun List<Barn>.somMapTilPdf(): List<Map<String, Any?>> {
    return map {
        mapOf<String, Any?>(
            "navn" to it.navn.capitalizeName(),
            "identitetsnummer" to it.identitetsnummer,
            "tidspunktForAleneomsorg" to it.tidspunktForAleneomsorg,
            "dato" to it.dato
        )
    }
}

fun String.capitalizeName(): String = split(" ").joinToString(" ") { it.lowercase(Locale.getDefault()).capitalize() }