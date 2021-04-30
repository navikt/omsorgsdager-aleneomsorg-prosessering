package no.nav.helse.prosessering.v1.søknad

import com.fasterxml.jackson.annotation.JsonFormat
import no.nav.helse.felles.AktørId
import no.nav.k9.rapid.behov.AleneOmOmsorgenBehov
import no.nav.k9.rapid.behov.Behovssekvens
import java.net.URI
import java.time.LocalDate
import java.time.ZonedDateTime

data class PreprossesertMeldingV1(
    val søknadId: String,
    val mottatt: ZonedDateTime,
    val språk: String?,
    val dokumentUrls: List<List<URI>>,
    val søker: PreprossesertSøker,
    val id: String,
    val barn: List<Barn>,
    val harForståttRettigheterOgPlikter: Boolean,
    val harBekreftetOpplysninger: Boolean
) {
    internal constructor(
        melding: MeldingV1,
        dokumentUrls: List<List<URI>>,
        søkerAktørId: AktørId
    ) : this(
        språk = melding.språk,
        søknadId = melding.søknadId,
        mottatt = melding.mottatt,
        dokumentUrls = dokumentUrls,
        søker = PreprossesertSøker(melding.søker, søkerAktørId),
        id = melding.id,
        barn = melding.barn,
        harForståttRettigheterOgPlikter = melding.harForståttRettigheterOgPlikter,
        harBekreftetOpplysninger = melding.harBekreftetOpplysninger
    )

    fun tilBehovssekvens(correlationId: String) : Behovssekvens {
        val behov = AleneOmOmsorgenBehov(
            identitetsnummer = søker.fødselsnummer,
            mottaksdato = mottatt.toLocalDate(),
            barn = barn.mapNotNull { if(it.aleneomsorg) AleneOmOmsorgenBehov.Barn(it.identitetsnummer!!) else null }
        )

        return Behovssekvens(
            id = id,
            correlationId = correlationId,
            behov = arrayOf(behov)
        )
    }
}

data class PreprossesertSøker(
    val fødselsnummer: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val aktørId: String,
    @JsonFormat(pattern = "yyyy-MM-dd") val fødselsdato: LocalDate?
    ) {
    internal constructor(søker: Søker, aktørId: AktørId) : this(
        fødselsnummer = søker.fødselsnummer,
        fornavn = søker.fornavn,
        mellomnavn = søker.mellomnavn,
        etternavn = søker.etternavn,
        aktørId = aktørId.id,
        fødselsdato = søker.fødselsdato
    )
}