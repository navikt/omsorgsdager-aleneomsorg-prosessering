package no.nav.helse

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.prometheus.client.hotspot.DefaultExports
import no.nav.helse.auth.AccessTokenClientResolver
import no.nav.helse.dusseldorf.ktor.auth.clients
import no.nav.helse.dusseldorf.ktor.client.HttpRequestHealthCheck
import no.nav.helse.dusseldorf.ktor.client.HttpRequestHealthConfig
import no.nav.helse.dusseldorf.ktor.client.buildURL
import no.nav.helse.dusseldorf.ktor.core.Paths
import no.nav.helse.dusseldorf.ktor.core.logProxyProperties
import no.nav.helse.dusseldorf.ktor.health.HealthRoute
import no.nav.helse.dusseldorf.ktor.health.HealthService
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.dusseldorf.ktor.metrics.MetricsRoute
import no.nav.helse.joark.JoarkGateway
import no.nav.helse.k9mellomlagring.K9MellomlagringGateway
import no.nav.helse.k9mellomlagring.K9MellomlagringService
import no.nav.helse.prosessering.v1.PdfV1Generator
import no.nav.helse.prosessering.v1.PreprosesseringV1Service
import no.nav.helse.prosessering.v1.asynkron.AsynkronProsesseringV1Service
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI

private val logger: Logger = LoggerFactory.getLogger("nav.AleneomsorgProsessering")

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.aleneomsorgProsessering() {
    logProxyProperties()
    DefaultExports.initialize()

    install(ContentNegotiation) {
        jackson {
            dusseldorfConfigured()
                .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)

        }
    }

    val configuration = Configuration(environment.config)
    val accessTokenClientResolver = AccessTokenClientResolver(environment.config.clients())

    val k9MellomlagringGateway = K9MellomlagringGateway(
        baseUrl = configuration.getK9MellomlagringServiceDiscovery(),
        accessTokenClient = accessTokenClientResolver.dokumentAccessTokenClient(),
        lagreDokumentScopes = configuration.getLagreDokumentScopes(),
        sletteDokumentScopes = configuration.getSletteDokumentScopes()
    )

    val k9MellomlagringService = K9MellomlagringService(k9MellomlagringGateway)

    val preprosesseringV1Service = PreprosesseringV1Service(
        pdfV1Generator = PdfV1Generator(),
        k9MellomlagringService = k9MellomlagringService
    )
    val joarkGateway = JoarkGateway(
        baseUrl = configuration.getk9JoarkBaseUrl(),
        accessTokenClient = accessTokenClientResolver.joarkAccessTokenClient(),
        journalforeScopes = configuration.getJournalforeScopes()
    )

    val asynkronProsesseringV1Service = AsynkronProsesseringV1Service(
        kafkaConfig = configuration.getKafkaConfig(),
        preprosesseringV1Service = preprosesseringV1Service,
        joarkGateway = joarkGateway,
        k9MellomlagringService = k9MellomlagringService
    )

    environment.monitor.subscribe(ApplicationStopping) {
        logger.info("Stopper AsynkronProsesseringV1Service.")
        asynkronProsesseringV1Service.stop()
        logger.info("AsynkronProsesseringV1Service Stoppet.")
    }

    install(Routing) {
        MetricsRoute()
        HealthRoute(
            path = Paths.DEFAULT_ALIVE_PATH,
            healthService = HealthService(
                healthChecks = asynkronProsesseringV1Service.isReadyChecks()
            )
        )
        get(Paths.DEFAULT_READY_PATH) {
            call.respondText("READY")
        }
        HealthRoute(
            healthService = HealthService(
                healthChecks = mutableSetOf(
                    k9MellomlagringGateway,
                    joarkGateway,
                    HttpRequestHealthCheck(
                        mapOf(
                            Url.healthURL(configuration.getK9MellomlagringServiceDiscovery()) to HttpRequestHealthConfig(
                                expectedStatus = HttpStatusCode.OK
                            ),
                            Url.healthURL(configuration.getk9JoarkBaseUrl()) to HttpRequestHealthConfig(
                                expectedStatus = HttpStatusCode.OK
                            )
                        )
                    )
                ).plus(asynkronProsesseringV1Service.healthChecks()).toSet()
            )
        )
    }
}

private fun Url.Companion.healthURL(baseUrl: URI) = Url.buildURL(baseUrl = baseUrl, pathParts = listOf("health"))
