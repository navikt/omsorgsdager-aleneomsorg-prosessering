package no.nav.helse.dokument

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.prosessering.v1.asynkron.Journalfort
import no.nav.helse.prosessering.v1.søknad.MeldingV1
import no.nav.k9.søknad.Søknad

class Søknadsformat {
    companion object {
        private val objectMapper = jacksonObjectMapper()
            .dusseldorfConfigured()
            .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
            .configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)

        internal fun somJson(
            meldingV1: MeldingV1
        ): ByteArray {
            val node = objectMapper.valueToTree<ObjectNode>(meldingV1)
            return objectMapper.writeValueAsBytes(node)
        }

        internal fun somJson(
            søknad: Journalfort
        ) : ByteArray{
            val node = objectMapper.valueToTree<ObjectNode>(søknad)
            return objectMapper.writeValueAsBytes(node)
        }

        internal fun somJson(
            k9Format: Søknad
        ) : ByteArray{
            val node = objectMapper.valueToTree<ObjectNode>(k9Format)
            return objectMapper.writeValueAsBytes(node)
        }

    }
}