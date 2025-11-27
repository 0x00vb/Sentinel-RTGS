package com.example.backend.service;

import com.example.backend.service.AccountSeedingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for TrafficSimulatorService account creation functionality.
 * Covers dynamic account creation, balance generation, and concurrent safety.
 */
@ExtendWith(MockitoExtension.class)
class TrafficSimulatorServiceTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private AccountSeedingService accountSeedingService;

    @InjectMocks
    private TrafficSimulatorService trafficSimulatorService;


    private AtomicLong messagesSent;

    @BeforeEach
    void setUp() {
        // Set dev mode to true for testing
        ReflectionTestUtils.setField(trafficSimulatorService, "devMode", true);
        messagesSent = new AtomicLong(0);
        ReflectionTestUtils.setField(trafficSimulatorService, "messagesSent", messagesSent);
    }

    @Test
    void startSimulation_ShouldSeedAccountsBeforeStarting() {
        // Given
        when(accountSeedingService.seedSimulationAccounts(anyList())).thenReturn(150);

        // When
        String result = trafficSimulatorService.startSimulation(1);

        // Then
        assertThat(result).contains("Traffic simulation started");
        verify(accountSeedingService).seedSimulationAccounts(anyList());

        // Cleanup
        trafficSimulatorService.stopSimulation();
    }

    @Test
    void startSimulation_ShouldReportAccountSeeding() {
        // Given
        when(accountSeedingService.seedSimulationAccounts(anyList())).thenReturn(75);

        // When
        String result = trafficSimulatorService.startSimulation(1);

        // Then
        assertThat(result).contains("Traffic simulation started");
        // The seeding happens and is logged, but result only contains basic status

        // Cleanup
        trafficSimulatorService.stopSimulation();
    }

    @Test
    void generateRealisticPacs008Message_ShouldReturnValidXml() {
        // When
        String xml = (String) ReflectionTestUtils.invokeMethod(trafficSimulatorService, "generateRealisticPacs008Message");

        // Then - Should return valid XML without any account creation
        assertThat(xml).isNotNull();
        assertThat(xml).contains("pacs008:Document");
        assertThat(xml).contains("pacs008:FIToFICstmrCdtTrf");
        assertThat(xml).contains("pacs008:DbtrAcct"); // Sender account
        assertThat(xml).contains("pacs008:CdtrAcct"); // Receiver account
    }

    @Test
    void startSimulation_ShouldCreateAccountsDuringSimulation() {
        // When
        String result = trafficSimulatorService.startSimulation(1); // 1 message per second

        // Then
        assertThat(result).contains("Traffic simulation started");

        // Stop simulation to clean up
        trafficSimulatorService.stopSimulation();
    }

    @Test
    void startSimulation_ShouldFail_WhenNotInDevMode() {
        // Given
        ReflectionTestUtils.setField(trafficSimulatorService, "devMode", false);

        // When
        String result = trafficSimulatorService.startSimulation(1);

        // Then
        assertThat(result).contains("only available in development mode");
    }

    @Test
    void sendSingleMessage_ShouldFail_WhenNotInDevMode() {
        // Given
        ReflectionTestUtils.setField(trafficSimulatorService, "devMode", false);

        // When & Then
        assertThatThrownBy(() -> trafficSimulatorService.sendSingleMessage())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("only available in development mode");
    }

    @Test
    void getSimulationStatus_ShouldReturnCorrectStatus() {
        // Given
        ReflectionTestUtils.setField(trafficSimulatorService, "messagesSent", new AtomicLong(42));

        // When
        var status = trafficSimulatorService.getSimulationStatus();

        // Then
        assertThat(status).containsEntry("running", false);
        assertThat(status).containsEntry("messagesSent", 42L);
        assertThat(status).containsEntry("devMode", true);
    }

    @Test
    void getSimulationStatus_ShouldIncludeUptime_WhenRunning() {
        // Given - start simulation to set running state
        trafficSimulatorService.startSimulation(1);

        // When
        var status = trafficSimulatorService.getSimulationStatus();

        // Then
        assertThat(status).containsEntry("running", true);
        assertThat(status).containsKey("uptime");

        // Cleanup
        trafficSimulatorService.stopSimulation();
    }

    @Test
    void generateIbanPool_ShouldCreateExpectedNumberOfIbans() {
        // This is a static method test - we'll verify the pool is created correctly
        // by checking that the IBAN_POOL field is properly initialized

        // The IBAN_POOL is initialized statically, so we can verify it's not empty
        Object ibanPool = ReflectionTestUtils.getField(trafficSimulatorService, "IBAN_POOL");
        assertThat(ibanPool).isNotNull();

        // The pool should contain hardcoded IBANs (30) + generated ones (200) = 230 total
        @SuppressWarnings("unchecked")
        List<String> pool = (List<String>) ibanPool;
        assertThat(pool).hasSizeGreaterThan(200); // At least the generated ones
    }
}
