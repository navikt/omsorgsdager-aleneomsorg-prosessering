package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import com.typesafe.config.ConfigFactory
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*
import io.prometheus.client.CollectorRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder
import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.Søknad
import org.json.JSONObject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.KafkaContainer
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AleneomsorgProsesseringTest {

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
        private val cleanupKonsumer = kafkaEnvironment.cleanupKonsumer()

        private val dNummerA = "55125314561"

        private var engine = newEngine(kafkaEnvironment).apply {
            start(wait = true)
        }

        private fun getConfig(kafkaContainer: KafkaContainer): ApplicationConfig {
            val fileConfig = ConfigFactory.load()
            val testConfig = ConfigFactory.parseMap(
                TestConfiguration.asMap(
                    wireMockServer = wireMockServer,
                    kafkaEnvironment = kafkaContainer
                )
            )
            val mergedConfig = testConfig.withFallback(fileConfig)
            return HoconApplicationConfig(mergedConfig)
        }

        private fun newEngine(kafkaContainer: KafkaContainer) = TestApplicationEngine(createTestEnvironment {
            config = getConfig(kafkaContainer)
        })

        private fun stopEngine() = engine.stop(5, 60, TimeUnit.SECONDS)

        fun restartEngine() {
            stopEngine()
            CollectorRegistry.defaultRegistry.clear()
            engine = newEngine(kafkaEnvironment)
            engine.start(wait = true)
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            logger.info("Tearing down")
            wireMockServer.stop()
            cleanupKonsumer.close()
            kafkaTestProducer.close()
            stopEngine()
            kafkaEnvironment.stop()
            logger.info("Tear down complete")
        }
    }

    @Test
    fun `test isready, isalive, health og metrics`() {
        with(engine) {
            val healthEndpoints = listOf("/isready", "/isalive", "/metrics", "/health")

            val responses = healthEndpoints.map { endpoint ->
                handleRequest(HttpMethod.Get, endpoint).response.status()
            }

            for(statusCode in responses) {
                Assertions.assertEquals(HttpStatusCode.OK, statusCode)
            }
        }
    }

    @Test
    fun `Gylding søknad blir prosessert av journalføringskonsumer`() {
        val søknad = SøknadUtils.gyldigSøknad()

        kafkaTestProducer.leggTilMottak(søknad)
        cleanupKonsumer
            .hentCleanupMelding(søknad.søknadId)
            .assertGyldigMelding(søknad.søknadId)
    }

    @Test
    fun `Sende søknad hvor søker har D-nummer`() {
        val søknad = SøknadUtils.gyldigSøknad(søkerFødselsnummer = dNummerA)

        kafkaTestProducer.leggTilMottak(søknad)
        cleanupKonsumer
            .hentCleanupMelding(søknad.søknadId)
            .assertGyldigMelding(søknad.søknadId)
    }

    @Test
    fun `En feilprosessert søknad vil bli prosessert etter at tjenesten restartes`() {
        val søknad = SøknadUtils.gyldigSøknad()

        wireMockServer.stubJournalfor(500) // Simulerer feil ved journalføring

        kafkaTestProducer.leggTilMottak(søknad)
        ventPaaAtRetryMekanismeIStreamProsessering()
        readyGir200HealthGir503()

        wireMockServer.stubJournalfor(201) // Simulerer journalføring fungerer igjen
        restartEngine()
        cleanupKonsumer
            .hentCleanupMelding(søknad.søknadId)
            .assertGyldigMelding(søknad.søknadId)
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

internal fun String.assertGyldigMelding(søknadId: String) {
    val rawJson = JSONObject(this)
    val metadata = assertNotNull(rawJson.getJSONObject("metadata"))
    assertNotNull(metadata.getString("correlationId"))

    val data = assertNotNull(rawJson.getJSONObject("data"))
    assertNotNull(data.getJSONObject("journalførtMelding").getString("journalpostId"))

    val søknad = assertNotNull(data.getJSONObject("melding")).getJSONObject("k9Søknad")
    assertEquals(søknadId, søknad.getString("søknadId"))

    val type = søknad.getJSONObject("ytelse").getString("type")
    assertEquals(type, "OMP_UTV_AO")

    val rekonstruertSøknad = JsonUtils.fromString(søknad.toString(), Søknad::class.java)
    val rekonstruertSøknadSomString = JsonUtils.toString(rekonstruertSøknad)
    JSONAssert.assertEquals(søknad.toString(), rekonstruertSøknadSomString, true)
}
