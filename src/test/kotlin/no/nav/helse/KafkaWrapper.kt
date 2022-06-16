package no.nav.helse

import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import no.nav.helse.felles.Metadata
import no.nav.helse.prosessering.v1.asynkron.Data
import no.nav.helse.prosessering.v1.asynkron.TopicEntry
import no.nav.helse.prosessering.v1.asynkron.Topics.CLEANUP
import no.nav.helse.prosessering.v1.asynkron.Topics.K9_DITTNAV_VARSEL
import no.nav.helse.prosessering.v1.asynkron.Topics.MOTTATT
import no.nav.helse.prosessering.v1.asynkron.Topics.PREPROSESSERT
import no.nav.helse.prosessering.v1.asynkron.aleneomsorgKonfigurertMapper
import no.nav.helse.prosessering.v1.søknad.MeldingV1
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.Assert.assertEquals
import java.time.Duration
import java.util.*

private const val username = "srvkafkaclient"
private const val password = "kafkaclient"

object KafkaWrapper {
    fun bootstrap(): KafkaEnvironment {
        val kafkaEnvironment = KafkaEnvironment(
            users = listOf(JAASCredential(username, password)),
            autoStart = true,
            withSchemaRegistry = false,
            withSecurity = true,
            topicNames = listOf(
                MOTTATT.name,
                PREPROSESSERT.name,
                CLEANUP.name,
                K9_DITTNAV_VARSEL.name
            )
        )
        return kafkaEnvironment
    }
}

private fun KafkaEnvironment.testConsumerProperties(groupId: String): MutableMap<String, Any>? {
    return HashMap<String, Any>().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, brokersURL)
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
        put(SaslConfigs.SASL_MECHANISM, "PLAIN")
        put(
            SaslConfigs.SASL_JAAS_CONFIG,
            "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$username\" password=\"$password\";"
        )
        put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
    }
}

private fun KafkaEnvironment.testProducerProperties(clientId: String): MutableMap<String, Any>? {
    return HashMap<String, Any>().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, brokersURL)
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
        put(SaslConfigs.SASL_MECHANISM, "PLAIN")
        put(
            SaslConfigs.SASL_JAAS_CONFIG,
            "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$username\" password=\"$password\";"
        )
        put(ProducerConfig.CLIENT_ID_CONFIG, clientId)
    }
}


fun KafkaEnvironment.cleanupKonsumer(): KafkaConsumer<String, String> {
    val consumer = KafkaConsumer(
        testConsumerProperties("CleanupConsumer"),
        StringDeserializer(),
        StringDeserializer()
    )
    consumer.subscribe(listOf(CLEANUP.name))
    return consumer
}

fun KafkaEnvironment.meldingsProducer() = KafkaProducer(
    testProducerProperties("OmsorgsdagerAleneomsorgTestProducer"),
    MOTTATT.keySerializer,
    MOTTATT.serDes
)

fun KafkaConsumer<String, String>.hentCleanupMelding(
    id: String,
    maxWaitInSeconds: Long = 40
): String {
    val end = System.currentTimeMillis() + Duration.ofSeconds(maxWaitInSeconds).toMillis()
    while (System.currentTimeMillis() < end) {
        seekToBeginning(assignment())
        val entries = poll(Duration.ofSeconds(1))
            .records(CLEANUP.name)
            .filter { it.key() == id }

        if (entries.isNotEmpty()) {
            assertEquals(1, entries.size)
            return entries.first().value()
        }
    }
    throw IllegalStateException("Fant ikke melding på ${CLEANUP.name} med ID:$id etter $maxWaitInSeconds sekunder.")
}

fun KafkaProducer<String, TopicEntry>.leggTilMottak(soknad: MeldingV1) {
    send(
        ProducerRecord(
            MOTTATT.name,
            soknad.søknadId,
            TopicEntry(
                metadata = Metadata(
                    version = 1,
                    correlationId = UUID.randomUUID().toString()
                ),
                data = Data(aleneomsorgKonfigurertMapper().writeValueAsString(soknad))
            )
        )
    ).get()
}

fun KafkaEnvironment.username() = username
fun KafkaEnvironment.password() = password
