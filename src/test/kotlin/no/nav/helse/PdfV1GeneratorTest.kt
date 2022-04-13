package no.nav.helse

import no.nav.helse.prosessering.v1.PdfV1Generator
import no.nav.helse.prosessering.v1.søknad.Barn
import no.nav.helse.prosessering.v1.søknad.TidspunktForAleneomsorg
import no.nav.helse.prosessering.v1.søknad.TypeBarn
import java.io.File
import java.time.LocalDate
import kotlin.test.Test

class PdfV1GeneratorTest {

    private companion object {
        private val generator = PdfV1Generator()
        private val fødselsdato = LocalDate.now()
    }

    private fun genererOppsummeringsPdfer(writeBytes: Boolean) {

        var id = "1-full-søknad"
        var pdf = generator.generateSoknadOppsummeringPdf(
            melding = SøknadUtils.gyldigSøknad(søknadId = id)
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)

        id = "2-full-søknad-fosterbarn"
        pdf = generator.generateSoknadOppsummeringPdf(
            melding = SøknadUtils.gyldigSøknad(søknadId = id).copy(
                barn = Barn(
                    navn = "Ole Dole",
                    identitetsnummer = "29076523302",
                    type = TypeBarn.FOSTERBARN,
                    fødselsdato = LocalDate.now().minusMonths(5),
                    tidspunktForAleneomsorg = TidspunktForAleneomsorg.SISTE_2_ÅRENE,
                    dato = LocalDate.now().minusMonths(4)
                )
            )
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)
    }

    private fun pdfPath(soknadId: String) = "${System.getProperty("user.dir")}/generated-pdf-$soknadId.pdf"

    @Test
    fun `generering av oppsummerings-PDF fungerer`() {
        genererOppsummeringsPdfer(false)
    }

    @Test
    fun `opprett lesbar oppsummerings-PDF`() {
        genererOppsummeringsPdfer(true)
    }
}