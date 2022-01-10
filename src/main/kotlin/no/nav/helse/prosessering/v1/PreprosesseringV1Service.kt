package no.nav.helse.prosessering.v1

import no.nav.helse.felles.AktørId
import no.nav.helse.felles.CorrelationId
import no.nav.helse.felles.Metadata
import no.nav.helse.k9mellomlagring.K9MellomlagringGateway.Dokument
import no.nav.helse.k9mellomlagring.K9MellomlagringGateway.DokumentEier
import no.nav.helse.k9mellomlagring.K9MellomlagringService
import no.nav.helse.k9mellomlagring.Søknadsformat
import no.nav.helse.prosessering.v1.søknad.MeldingV1
import no.nav.helse.prosessering.v1.søknad.PreprosessertMeldingV1
import org.slf4j.LoggerFactory
import java.net.URI

internal class PreprosesseringV1Service(
    private val pdfV1Generator: PdfV1Generator,
    private val k9MellomlagringService: K9MellomlagringService
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(PreprosesseringV1Service::class.java)
    }

    internal suspend fun preprosesser(
        melding: MeldingV1,
        metadata: Metadata
    ): PreprosessertMeldingV1 {
        val correlationId = CorrelationId(metadata.correlationId)
        val dokumentEier = DokumentEier(melding.søker.fødselsnummer)

        logger.info("Genererer Oppsummerings-PDF av søknaden.")
        val søknadOppsummeringPdf = pdfV1Generator.generateSoknadOppsummeringPdf(melding)

        logger.info("Mellomlagrer Oppsummerings-PDF.")
        val oppsummeringPdfVedleggId = k9MellomlagringService.lagreDokument(
            dokument = Dokument(
                eier = dokumentEier,
                content = søknadOppsummeringPdf,
                contentType = "application/pdf",
                title = "Omsorgsdager - Melding om aleneomsorg"
            ),
            correlationId = correlationId
        ).vedleggId()

        logger.info("Mellomlagrer Oppsummerings-JSON")
        val søknadJsonVedleggId = k9MellomlagringService.lagreDokument(
            dokument = Dokument(
                eier = dokumentEier,
                content = Søknadsformat.somJson(melding.k9Søknad),
                contentType = "application/json",
                title = "Omsorgsdager - Melding om aleneomsorg som JSON"
            ),
            correlationId = correlationId
        ).vedleggId()

        val komplettDokumentUrls = mutableListOf(
            listOf(
                oppsummeringPdfVedleggId,
                søknadJsonVedleggId
            )
        )

        logger.info("Totalt ${komplettDokumentUrls.size} dokumentbolker.")

        val preprosessertMeldingV1 = PreprosessertMeldingV1(
            melding = melding,
            dokumentId = komplettDokumentUrls.toList(),
            søkerAktørId = AktørId(melding.søker.aktørId)
        )

        preprosessertMeldingV1.reportMetrics()
        return preprosessertMeldingV1
    }

}

fun URI.vedleggId(): String = this.toString().substringAfterLast("/")
