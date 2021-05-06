package no.nav.helse.prosessering.v1.asynkron

import no.nav.helse.felles.formaterStatuslogging
import no.nav.helse.kafka.KafkaConfig
import no.nav.helse.kafka.ManagedKafkaStreams
import no.nav.helse.kafka.ManagedStreamHealthy
import no.nav.helse.kafka.ManagedStreamReady
import no.nav.helse.prosessering.v1.PreprosesseringV1Service
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.slf4j.LoggerFactory

internal class PreprosesseringStream(
    preprosesseringV1Service: PreprosesseringV1Service,
    kafkaConfig: KafkaConfig
) {
    private val stream = ManagedKafkaStreams(
        name = NAME,
        properties = kafkaConfig.stream(NAME),
        topology = topology(preprosesseringV1Service),
        unreadyAfterStreamStoppedIn = kafkaConfig.unreadyAfterStreamStoppedIn
    )

    internal val ready = ManagedStreamReady(stream)
    internal val healthy = ManagedStreamHealthy(stream)

    private companion object {

        private const val NAME = "PreprosesseringV1"
        private val logger = LoggerFactory.getLogger("no.nav.$NAME.topology")

        private fun topology(preprosesseringV1Service: PreprosesseringV1Service): Topology {
            val builder = StreamsBuilder()
            val fromMottatt = Topics.MOTTATT
            val tilPreprosessert = Topics.PREPROSESSERT

            builder
                .stream(fromMottatt.name, fromMottatt.consumed)
                .filter { _, entry -> 1 == entry.metadata.version }
                .mapValues { soknadId, entry ->
                    process(NAME, soknadId, entry) {
                        logger.info(formaterStatuslogging(soknadId, "preprosesseres"))

                        val preprosessertMelding = preprosesseringV1Service.preprosesser(
                            melding = entry.deserialiserTilMelding(),
                            metadata = entry.metadata
                        )
                        logger.trace("Preprosessering ferdig.")

                        preprosessertMelding.serialiserTilData()
                    }
                }
                .to(tilPreprosessert.name, tilPreprosessert.produced)
            return builder.build()
        }
    }

    internal fun stop() = stream.stop(becauseOfError = false)
}