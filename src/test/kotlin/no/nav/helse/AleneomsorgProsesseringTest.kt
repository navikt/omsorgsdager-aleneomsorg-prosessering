package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import com.typesafe.config.ConfigFactory
import io.ktor.config.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.prometheus.client.CollectorRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
import no.nav.common.KafkaEnvironment
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.Ignore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


@KtorExperimentalAPI
class AleneomsorgProsesseringTest {

    @KtorExperimentalAPI
    private companion object {

        private val logger: Logger = LoggerFactory.getLogger(AleneomsorgProsesseringTest::class.java)

        private val wireMockServer: WireMockServer = WireMockBuilder()
            .withAzureSupport()
            .navnOppslagConfig()
            .build()
            .stubK9MellomlagringHealth()
            .stubOmsorgspengerJoarkHealth()
            .stubJournalfor()
            .stubLagreDokument()
            .stubSlettDokument()

        private val kafkaEnvironment = KafkaWrapper.bootstrap()
        private val kafkaTestProducer = kafkaEnvironment.meldingsProducer()

        private val k9RapidConsumer = kafkaEnvironment.k9RapidV2Consumer()

        private val dNummerA = "55125314561"

        private var engine = newEngine(kafkaEnvironment).apply {
            start(wait = true)
        }

        private fun getConfig(kafkaEnvironment: KafkaEnvironment?): ApplicationConfig {
            val fileConfig = ConfigFactory.load()
            val testConfig = ConfigFactory.parseMap(
                TestConfiguration.asMap(
                    wireMockServer = wireMockServer,
                    kafkaEnvironment = kafkaEnvironment
                )
            )
            val mergedConfig = testConfig.withFallback(fileConfig)
            return HoconApplicationConfig(mergedConfig)
        }

        private fun newEngine(kafkaEnvironment: KafkaEnvironment?) = TestApplicationEngine(createTestEnvironment {
            config = getConfig(kafkaEnvironment)
        })

        private fun stopEngine() = engine.stop(5, 60, TimeUnit.SECONDS)

        fun restartEngine() {
            stopEngine()
            CollectorRegistry.defaultRegistry.clear()
            engine = newEngine(kafkaEnvironment)
            engine.start(wait = true)
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            logger.info("Tearing down")
            wireMockServer.stop()
            k9RapidConsumer.close()
            kafkaTestProducer.close()
            stopEngine()
            kafkaEnvironment.tearDown()
            logger.info("Tear down complete")
        }
    }

    @Test
    fun `test isready, isalive, health og metrics`() {
        with(engine) {
            handleRequest(HttpMethod.Get, "/isready") {}.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                handleRequest(HttpMethod.Get, "/isalive") {}.apply {
                    assertEquals(HttpStatusCode.OK, response.status())
                    handleRequest(HttpMethod.Get, "/metrics") {}.apply {
                        assertEquals(HttpStatusCode.OK, response.status())
                        handleRequest(HttpMethod.Get, "/health") {}.apply {
                            assertEquals(HttpStatusCode.OK, response.status())
                        }
                    }
                }
            }
        }
    }

    @Test
    @Ignore //TODO 05.05.2021 - Ignorert fordi cleanup ikke sender videre til k9-rapid-v2 og vi ikke journalfører
    fun `Gylding søknad blir prosessert av journalføringskonsumer`() {
        val søknad = SøknadUtils.gyldigSøknad(id="01ARZ3NDEKTSV4RRFFQ69G5FAA")

        kafkaTestProducer.leggTilMottak(søknad)
        k9RapidConsumer
            .hentK9RapidMelding(søknad.id)
            .assertGyldigK9RapidFormat(søknad.id)
    }

    @Test
    @Ignore //TODO 05.05.2021 - Ignorert fordi cleanup ikke sender videre til k9-rapid-v2 og vi ikke journalfører
    fun `En feilprosessert søknad vil bli prosessert etter at tjenesten restartes`() {
        val søknad = SøknadUtils.gyldigSøknad().copy(id = "01ARZ3NDEKTSV4RRFFQ69G5FAA")

        wireMockServer.stubJournalfor(500) // Simulerer feil ved journalføring

        kafkaTestProducer.leggTilMottak(søknad)
        ventPaaAtRetryMekanismeIStreamProsessering()
        readyGir200HealthGir503()

        wireMockServer.stubJournalfor(201) // Simulerer journalføring fungerer igjen
        restartEngine()
        k9RapidConsumer
            .hentK9RapidMelding(søknad.id)
            .assertGyldigK9RapidFormat(søknad.id)
    }

    @Test
    @Ignore //TODO 05.05.2021 - Ignorert fordi cleanup ikke sender videre til k9-rapid-v2
    fun `Sende søknad hvor søker har D-nummer`() {
        val søknad = SøknadUtils.gyldigSøknad(søkerFødselsnummer = dNummerA)

        kafkaTestProducer.leggTilMottak(søknad)
        k9RapidConsumer
            .hentK9RapidMelding(søknad.id)
            .assertGyldigK9RapidFormat(søknad.id)
    }

    private fun ventPaaAtRetryMekanismeIStreamProsessering() = runBlocking { delay(Duration.ofSeconds(30)) }

    private fun readyGir200HealthGir503() {
        with(engine) {
            handleRequest(HttpMethod.Get, "/isready") {}.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                handleRequest(HttpMethod.Get, "/health") {}.apply {
                    assertEquals(HttpStatusCode.ServiceUnavailable, response.status())
                }
            }
        }
    }

}

internal fun String.assertGyldigK9RapidFormat(id: String) {
    val rawJson = JSONObject(this)

    assertEquals(rawJson.getJSONArray("@behovsrekkefølge").getString(0), "AleneOmOmsorgen")
    assertEquals(rawJson.getString("@type"),"Behovssekvens")
    assertEquals(rawJson.getString("@id"), id)

    assertNotNull(rawJson.getString("@correlationId"))
    assertNotNull(rawJson.getJSONObject("@behov"))
}