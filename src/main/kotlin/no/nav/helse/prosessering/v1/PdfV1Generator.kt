package no.nav.helse.prosessering.v1

import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Helper
import com.github.jknack.handlebars.context.MapValueResolver
import com.github.jknack.handlebars.io.ClassPathTemplateLoader
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.openhtmltopdf.util.XRLog
import no.nav.helse.dusseldorf.ktor.core.fromResources
import no.nav.helse.prosessering.v1.søknad.MeldingV1
import no.nav.helse.prosessering.v1.søknad.Søker
import no.nav.helse.prosessering.v1.søknad.capitalizeName
import no.nav.helse.prosessering.v1.søknad.somMapTilPdf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.logging.Level

internal class PdfV1Generator {
    private companion object {
        private const val ROOT = "handlebars"
        private const val SOKNAD = "soknad"

        private val REGULAR_FONT = "$ROOT/fonts/SourceSansPro-Regular.ttf".fromResources().readBytes()
        private val BOLD_FONT = "$ROOT/fonts/SourceSansPro-Bold.ttf".fromResources().readBytes()
        private val ITALIC_FONT = "$ROOT/fonts/SourceSansPro-Italic.ttf".fromResources().readBytes()

        private val sRGBColorSpace = "$ROOT/sRGB.icc".fromResources().readBytes()

        private val handlebars = Handlebars(ClassPathTemplateLoader("/$ROOT")).apply {
            registerHelper("eq", Helper<String> { context, options ->
                if (context == options.param(0)) options.fn() else options.inverse()
            })
            registerHelper("eqTall", Helper<Int> { context, options ->
                if (context == options.param(0)) options.fn() else options.inverse()
            })
            registerHelper("fritekst", Helper<String> { context, _ ->
                if (context == null) "" else {
                    val text = Handlebars.Utils.escapeExpression(context)
                        .toString()
                        .replace(Regex("\\r\\n|[\\n\\r]"), "<br/>")
                    Handlebars.SafeString(text)
                }
            })
            registerHelper("jaNeiSvar", Helper<Boolean> { context, _ ->
                if (context == true) "Ja" else "Nei"
            })

            infiniteLoops(true)
        }

        private val soknadTemplate = handlebars.compile(SOKNAD)

        private val ZONE_ID = ZoneId.of("Europe/Oslo")
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZONE_ID)
    }

    internal fun generateSoknadOppsummeringPdf(melding: MeldingV1): ByteArray {
        XRLog.listRegisteredLoggers().forEach { logger -> XRLog.setLevel(logger, Level.WARNING) }
        soknadTemplate.apply(
            Context
                .newBuilder(
                    mapOf(
                        "tittel" to "Omsorgsdager - Melding om aleneomsorg",
                        "søknadId" to melding.søknadId,
                        "søknadMottattDag" to melding.mottatt.withZoneSameInstant(ZONE_ID).norskDag(),
                        "søknadMottatt" to DATE_TIME_FORMATTER.format(melding.mottatt),
                        "søker" to mapOf(
                            "navn" to melding.søker.formatertNavn().capitalizeName(),
                            "fødselsnummer" to melding.søker.fødselsnummer
                        ),
                        "barn" to melding.barn.somMapTilPdf(),
                        "samtykke" to mapOf(
                            "harForståttRettigheterOgPlikter" to melding.harForståttRettigheterOgPlikter,
                            "harBekreftetOpplysninger" to melding.harBekreftetOpplysninger
                        ),
                        "hjelp" to mapOf(
                            "språk" to melding.språk?.språkTilTekst()
                        )
                    )
                )
                .resolver(MapValueResolver.INSTANCE)
                .build()
        ).let { html ->
            val outputStream = ByteArrayOutputStream()
            PdfRendererBuilder()
                .useFastMode()
                .usePdfUaAccessbility(true)
                .usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_1_B)
                .useColorProfile(sRGBColorSpace)
                .withHtmlContent(html, "")
                .medFonter()
                .toStream(outputStream)
                .buildPdfRenderer()
                .createPDF()
            return outputStream.use {
                it.toByteArray()
            }
        }
    }

    private fun PdfRendererBuilder.medFonter() =
        useFont(
            { ByteArrayInputStream(REGULAR_FONT) },
            "Source Sans Pro",
            400,
            BaseRendererBuilder.FontStyle.NORMAL,
            false
        )
            .useFont(
                { ByteArrayInputStream(BOLD_FONT) },
                "Source Sans Pro",
                700,
                BaseRendererBuilder.FontStyle.NORMAL,
                false
            )
            .useFont(
                { ByteArrayInputStream(ITALIC_FONT) },
                "Source Sans Pro",
                400,
                BaseRendererBuilder.FontStyle.ITALIC,
                false
            )
}

private fun Søker.formatertNavn() = if (mellomnavn != null) "$fornavn $mellomnavn $etternavn" else "$fornavn $etternavn"

private fun String.språkTilTekst() = when (this.lowercase(Locale.getDefault())) {
    "nb" -> "bokmål"
    "nn" -> "nynorsk"
    else -> this
}

private fun ZonedDateTime.norskDag() = when(dayOfWeek) {
    DayOfWeek.MONDAY -> "Mandag"
    DayOfWeek.TUESDAY -> "Tirsdag"
    DayOfWeek.WEDNESDAY -> "Onsdag"
    DayOfWeek.THURSDAY -> "Torsdag"
    DayOfWeek.FRIDAY -> "Fredag"
    DayOfWeek.SATURDAY -> "Lørdag"
    else -> "Søndag"
}
