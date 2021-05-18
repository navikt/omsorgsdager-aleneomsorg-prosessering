package no.nav.helse.prosessering.v1.søknad

import java.util.*

data class Barn (
    val navn: String,
    val aktørId: String?,
    var identitetsnummer: String?,
    val aleneomsorg: Boolean
) {
    override fun toString(): String {
        return "Barn(navn='$navn', aktørId=*****, identitetsnummer=*****)"
    }
}

internal fun List<Barn>.somMapTilPdf(): List<Map<String, Any?>> {
    return map {
        mapOf<String, Any?>(
            "navn" to it.navn.capitalizeName(),
            "identitetsnummer" to it.identitetsnummer,
            "aleneomsorg" to it.aleneomsorg
        )
    }
}

fun String.capitalizeName(): String = split(" ").joinToString(" ") { it.lowercase(Locale.getDefault()).capitalize() }