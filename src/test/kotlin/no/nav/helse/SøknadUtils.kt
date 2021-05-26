package no.nav.helse

import no.nav.helse.prosessering.v1.søknad.Barn
import no.nav.helse.prosessering.v1.søknad.MeldingV1
import no.nav.helse.prosessering.v1.søknad.Søker
import no.nav.helse.prosessering.v1.søknad.TidspunktForAleneomsorg
import no.nav.k9.søknad.Søknad
import no.nav.k9.søknad.felles.Versjon
import no.nav.k9.søknad.felles.type.NorskIdentitetsnummer
import no.nav.k9.søknad.felles.type.Periode
import no.nav.k9.søknad.felles.type.SøknadId
import no.nav.k9.søknad.ytelse.omsorgspenger.utvidetrett.v1.OmsorgspengerAleneOmsorg
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.*


object SøknadUtils {

    fun gyldigSøknad(
        søkerFødselsnummer: String = "02119970078",
        søknadId: String = UUID.randomUUID().toString(),
        mottatt: ZonedDateTime = ZonedDateTime.now(),
    ) = MeldingV1(
        språk = "nb",
        søknadId = søknadId,
        mottatt = mottatt,
        søker = Søker(
            aktørId = "123456",
            fødselsnummer = søkerFødselsnummer,
            fødselsdato = LocalDate.now().minusDays(1000),
            etternavn = "Nordmann",
            mellomnavn = "Mellomnavn",
            fornavn = "Ola"
        ),
        barn = listOf(
            Barn(
                navn = "Ole Dole",
                identitetsnummer = "29076523302",
                aktørId = null,
                tidspunktForAleneomsorg = TidspunktForAleneomsorg.SISTE_2_ÅRENE,
                dato = LocalDate.now().minusMonths(4)
            )
        ),
        k9Søknad = Søknad(
            SøknadId(søknadId),
            Versjon.of("1.0.0"),
            mottatt,
            no.nav.k9.søknad.felles.personopplysninger.Søker(NorskIdentitetsnummer.of(søkerFødselsnummer)),
            OmsorgspengerAleneOmsorg(
                no.nav.k9.søknad.felles.personopplysninger.Barn(NorskIdentitetsnummer.of("29076523302")),
                Periode(mottatt.toLocalDate(), null),
                ""
            )

        ),
        harBekreftetOpplysninger = true,
        harForståttRettigheterOgPlikter = true
    )
}
