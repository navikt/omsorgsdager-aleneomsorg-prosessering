package no.nav.helse

import no.nav.helse.felles.AktørId
import no.nav.helse.prosessering.v1.søknad.PreprossesertMeldingV1
import org.json.JSONObject
import org.junit.Test
import org.skyscreamer.jsonassert.JSONAssert

class BehovssekvensTest {

    @Test
    fun `Sjekke at behovssekvens blir bygget opp som forventet`() {
        val melding = PreprossesertMeldingV1(
            melding = SøknadUtils.gyldigSøknad(),
            dokumentUrls = listOf(),
            søkerAktørId = AktørId("123456")
        )
        val (id, løsning) = melding.tilBehovssekvens("0123456789").keyValue

        val forventetBehovssekvens = JSONObject(
            """
                {
                  "@behovsrekkefølge": [
                    "AleneOmOmsorgen"
                  ],
                  "@correlationId": "0123456789",
                  "@type": "Behovssekvens",
                  "@behov": {
                    "AleneOmOmsorgen": {
                      "identitetsnummer": "02119970078",
                      "barn": [
                        {
                          "identitetsnummer": "29076523302"
                        },
                        {
                          "identitetsnummer": "26106923468"
                        }
                      ],
                      "mottaksdato": "2021-04-30",
                      "versjon": "1.1.0"
                    }
                  },
                  "@versjon": "1",
                  "@id": "01ARZ3NDEKTSV4RRFFQ69G5FAV",
                }
        """.trimIndent()
        )
        val faktiskBehovssekvens = JSONObject(løsning)
        faktiskBehovssekvens.remove("@opprettet")
        faktiskBehovssekvens.remove("@sistEndret")

        JSONAssert.assertEquals(forventetBehovssekvens, faktiskBehovssekvens, true)
    }
}