package com.example.backend.controller;

import com.example.backend.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for Dashboard metrics and real-time data.
 * Provides aggregated data for dashboard visualization and monitoring.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    @Autowired
    private ComplianceService complianceService;

    @Autowired
    private AuditReportService auditReportService;

    @Autowired
    private TrafficSimulatorService trafficSimulatorService;

    /**
     * Get comprehensive dashboard metrics for all cards.
     * Combines data from multiple services for a unified dashboard view.
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getDashboardMetrics() {
        try {
            Map<String, Object> metrics = new HashMap<>();

            // Compliance stats for pending reviews
            ComplianceService.ComplianceStats complianceStats = complianceService.getComplianceStats();
            metrics.put("pendingComplianceReviews", complianceStats.getBlockedTransactions());

            // Today's compliance report for transfer count
            AuditReportService.ComplianceReport todayReport = auditReportService.getComplianceReport(LocalDate.now());
            metrics.put("totalTransfersToday", todayReport.getDailyAuditEntries());

            // Current activity report for risk metrics
            // TODO: Implement real risk score calculation from audit data
            metrics.put("averageRiskScore", 2.1); // Placeholder value

            // Queue depth (simplified - in production would query RabbitMQ management API)
            int queueDepth = getEstimatedQueueDepth();
            metrics.put("queueDepth", queueDepth);

            // Simulation status (if available)
            Map<String, Object> simStatus = trafficSimulatorService.getSimulationStatus();
            metrics.put("simulationStatus", simStatus);

            // System health indicators
            AuditReportService.SystemHealthReport healthReport = auditReportService.getSystemHealthReport();
            metrics.put("systemHealth", Map.of(
                "status", healthReport.getHealthStatus(),
                "integrityIntact", !healthReport.getIntegrityStatus().hasIntegrityBreaches()
            ));

            logger.info("Dashboard metrics retrieved successfully");
            return ResponseEntity.ok(metrics);

        } catch (Exception e) {
            logger.error("Error retrieving dashboard metrics", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to retrieve dashboard metrics",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Get real-time metrics for live dashboard updates.
     * Optimized endpoint for frequent polling.
     */
    @GetMapping("/metrics/live")
    public ResponseEntity<Map<String, Object>> getLiveMetrics() {
        try {
            Map<String, Object> liveMetrics = new HashMap<>();

            // Fast queries for real-time dashboard
            ComplianceService.ComplianceStats complianceStats = complianceService.getComplianceStats();
            liveMetrics.put("pendingComplianceReviews", complianceStats.getBlockedTransactions());

            // Simulation status
            Map<String, Object> simStatus = trafficSimulatorService.getSimulationStatus();
            liveMetrics.put("simulationStatus", simStatus);

            return ResponseEntity.ok(liveMetrics);

        } catch (Exception e) {
            logger.error("Error retrieving live metrics", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to retrieve live metrics",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Get recent audit events for the event stream.
     * Provides formatted events for dashboard display.
     */
    @GetMapping("/events/recent")
    public ResponseEntity<Map<String, Object>> getRecentEvents(
            @RequestParam(defaultValue = "20") int limit) {
        try {
            // For now, return mock events until we implement real audit log streaming
            var events = java.util.Arrays.asList(
                Map.of(
                    "id", "system-1",
                    "type", "transfer_completed",
                    "message", "System initialized and ready",
                    "timestamp", java.time.LocalDateTime.now().toString(),
                    "severity", "success"
                ),
                Map.of(
                    "id", "system-2",
                    "type", "compliance_review_required",
                    "message", "Compliance engine activated",
                    "timestamp", java.time.LocalDateTime.now().minusMinutes(5).toString(),
                    "severity", "warning"
                ),
                Map.of(
                    "id", "system-3",
                    "type", "entity_verified",
                    "message", "Audit chain verification completed",
                    "timestamp", java.time.LocalDateTime.now().minusMinutes(10).toString(),
                    "severity", "success"
                )
            );

            return ResponseEntity.ok(Map.of(
                "events", events.stream().limit(limit).toList(),
                "total", events.size()
            ));

        } catch (Exception e) {
            logger.error("Error retrieving recent events", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to retrieve recent events",
                "events", java.util.Collections.emptyList()
            ));
        }
    }

    // Helper methods

    private double calculateAverageRiskScore(AuditReportService.ActivityReport activityReport) {
        // Simplified risk calculation based on activity patterns
        long totalActivities = activityReport.getTotalActivities();

        if (totalActivities == 0) return 0.0;

        // Calculate risk based on action types that indicate issues
        long riskyActions = activityReport.getActivityByAction().entrySet().stream()
            .filter(entry -> entry.getKey().contains("BLOCK") || entry.getKey().contains("REJECT"))
            .mapToLong(Map.Entry::getValue)
            .sum();

        double riskRate = (double) riskyActions / totalActivities;
        return Math.min(10.0, riskRate * 10.0); // Scale to 0-10 range
    }

    private int getEstimatedQueueDepth() {
        // In production, this would query RabbitMQ management API
        // For now, return a simulated value based on system activity
        try {
            Map<String, Object> simStatus = trafficSimulatorService.getSimulationStatus();
            Boolean running = (Boolean) simStatus.get("running");
            if (Boolean.TRUE.equals(running)) {
                // Simulate queue depth when simulation is running
                return 1500 + (int)(Math.random() * 1000);
            }
        } catch (Exception e) {
            logger.warn("Error getting simulation status for queue depth", e);
        }
        return 450; // Default value matching the original mock
    }

}
