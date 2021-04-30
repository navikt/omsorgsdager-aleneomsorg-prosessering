package no.nav.helse.prosessering.v1.søknad

import java.time.ZonedDateTime

data class MeldingV1(
    val id: String,
    val søknadId: String,
    val mottatt: ZonedDateTime,
    val språk: String? = "nb",
    val søker: Søker,
    val barn: List<Barn>,
    val harForståttRettigheterOgPlikter: Boolean,
    val harBekreftetOpplysninger: Boolean
)