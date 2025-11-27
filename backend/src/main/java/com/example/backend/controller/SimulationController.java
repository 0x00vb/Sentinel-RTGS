package com.example.backend.controller;

import com.example.backend.service.TrafficSimulatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for Traffic Simulation operations.
 * Provides endpoints to control realistic banking traffic simulation for testing and demonstration.
 *
 * Only available in development mode for security and compliance reasons.
 */
@RestController
@RequestMapping("/api/v1/simulation")
public class SimulationController {

    private static final Logger logger = LoggerFactory.getLogger(SimulationController.class);

    private final TrafficSimulatorService trafficSimulatorService;

    @Autowired
    public SimulationController(TrafficSimulatorService trafficSimulatorService) {
        this.trafficSimulatorService = trafficSimulatorService;
    }

    /**
     * Start continuous traffic simulation.
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startSimulation(
            @RequestParam(defaultValue = "5") int messagesPerSecond) {

        logger.info("Starting traffic simulation at {} messages per second", messagesPerSecond);

        try {
            String result = trafficSimulatorService.startSimulation(messagesPerSecond);

            return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", result,
                "messagesPerSecond", messagesPerSecond
            ));

        } catch (Exception e) {
            logger.error("Error starting simulation", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to start simulation: " + e.getMessage()
            ));
        }
    }

    /**
     * Stop traffic simulation.
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopSimulation() {

        logger.info("Stopping traffic simulation");

        try {
            String result = trafficSimulatorService.stopSimulation();

            return ResponseEntity.ok(Map.of(
                "status", "stopped",
                "message", result
            ));

        } catch (Exception e) {
            logger.error("Error stopping simulation", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to stop simulation: " + e.getMessage()
            ));
        }
    }

    /**
     * Get simulation status.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSimulationStatus() {

        try {
            Map<String, Object> status = trafficSimulatorService.getSimulationStatus();

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            logger.error("Error getting simulation status", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to get status: " + e.getMessage()
            ));
        }
    }

    /**
     * Send a single test message (for development and testing).
     */
    @PostMapping("/send-test-message")
    public ResponseEntity<Map<String, Object>> sendTestMessage() {

        logger.info("Sending single test message");

        try {
            trafficSimulatorService.sendSingleMessage();

            return ResponseEntity.ok(Map.of(
                "status", "sent",
                "message", "Test message sent successfully"
            ));

        } catch (Exception e) {
            logger.error("Error sending test message", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to send test message: " + e.getMessage()
            ));
        }
    }
}
