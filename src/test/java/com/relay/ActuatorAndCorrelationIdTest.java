package com.relay;

import com.relay.consumer.JobConsumer;
import com.relay.domain.enums.JobType;
import com.relay.dto.KafkaJobMessage;
import com.relay.service.JobProcessingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=jdbc:h2:mem:actuatortest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.listener.auto-startup=false",
        "relay.security.api-key=test-secret-key",
        "relay.retry.poller-interval-ms=600000",
        "relay.outbox.poller-interval-ms=600000",
        "relay.cron.enabled=false",
        "management.endpoints.web.exposure.include=*",
        "management.endpoint.prometheus.enabled=true",
        "management.endpoint.metrics.enabled=true",
        "management.prometheus.metrics.export.enabled=true"
})
@AutoConfigureMockMvc


class ActuatorAndCorrelationIdTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobProcessingService jobProcessingService;

    @MockBean
    private org.springframework.kafka.core.KafkaAdmin kafkaAdmin;

    @MockBean
    private org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private JobConsumer jobConsumer;

    @Test
    @DisplayName("Should permit public access to /actuator/health without X-API-Key")
    void testActuatorHealthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should permit public access to /actuator/prometheus without X-API-Key")
    void testActuatorPrometheusIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should permit public access to /actuator/metrics without X-API-Key")
    void testActuatorMetricsIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("JobConsumer should generate correlation ID and set MDC during execution")
    void testJobConsumerGeneratesCorrelationIdInMdc() {
        UUID jobId = UUID.randomUUID();
        KafkaJobMessage message = KafkaJobMessage.builder()
                .jobId(jobId)
                .jobType(JobType.EMAIL_NOTIFICATION)
                .attemptNumber(1)
                .payload(Map.of("test", "data"))
                .build();

        doAnswer(invocation -> {
            // Verify MDC inside the consumer execution cycle
            assertThat(MDC.get("correlationId")).isNotNull().isNotBlank();
            assertThat(MDC.get("jobId")).isEqualTo(jobId.toString());
            assertThat(MDC.get("attemptNumber")).isEqualTo("1");
            assertThat(MDC.get("jobType")).isEqualTo("EMAIL_NOTIFICATION");
            return null;
        }).when(jobProcessingService).processJob(eq(jobId), eq(1));

        jobConsumer.consumeJob(message);

        verify(jobProcessingService).processJob(eq(jobId), eq(1));

        // Verify MDC is cleared after execution
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("jobId")).isNull();
    }
}
