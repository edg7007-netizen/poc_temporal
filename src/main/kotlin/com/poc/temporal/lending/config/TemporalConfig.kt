package com.poc.temporal.lending.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.temporal.common.converter.DefaultDataConverter
import io.temporal.common.converter.JacksonJsonPayloadConverter
import io.temporal.common.converter.NullPayloadConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TemporalConfig {

    /**
     * Configures a Temporal DataConverter that uses Jackson with the Kotlin module.
     * This is required so that Kotlin data classes (especially [LoanWorkflowRequest])
     * can be properly serialized / deserialized as workflow parameters.
     */
    @Bean
    fun temporalDataConverter(): DefaultDataConverter {
        val mapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        return DefaultDataConverter(
            NullPayloadConverter(),
            JacksonJsonPayloadConverter(mapper)
        )
    }
}
