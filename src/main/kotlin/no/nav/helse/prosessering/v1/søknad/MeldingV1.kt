package no.nav.helse.prosessering.v1.søknad

import no.nav.k9.søknad.Søknad
import java.time.ZonedDateTime

data class MeldingV1(
    val søknadId: String,
    val mottatt: ZonedDateTime,
    val språk: String? = "nb",
    val søker: Søker,
    val barn: Barn,
    val k9Søknad: Søknad,
    val harForståttRettigheterOgPlikter: Boolean,
    val harBekreftetOpplysninger: Boolean
)