package no.nav.helse

import no.nav.helse.prosessering.v1.søknad.*
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.*


object SøknadUtils {

    fun gyldigSøknad(
        søkerFødselsnummer: String = "02119970078",
        søknadId: String = UUID.randomUUID().toString(),
        mottatt: ZonedDateTime = ZonedDateTime.now(),
        id: String = "01ARZ3NDEKTSV4RRFFQ69G5FAV"
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
        id = id,
        barn = listOf(
            Barn(
                navn = "Ole Dole",
                identitetsnummer = "29076523302",
                aktørId = null,
                aleneomsorg = true
            ),
            Barn(
                navn = "Emil",
                identitetsnummer = "26106923468",
                aktørId = null,
                aleneomsorg = true
            ),
            Barn(
                navn = "Oliver",
                identitetsnummer = "07097427806",
                aktørId = null,
                aleneomsorg = false
            )
        ),
        harBekreftetOpplysninger = true,
        harForståttRettigheterOgPlikter = true
    )
}
