package com.example.backend.controller;

import com.example.backend.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import com.example.backend.repository.TransferRepository;

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

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private RabbitMQQueueService rabbitMQQueueService;

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

            // Queue depth from RabbitMQ
            int queueDepth = rabbitMQQueueService.getInboundQueueDepth();
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

    /**
     * Get transaction heatmap data by country (ISO_A3 format for world map).
     * Optimized to aggregate in database and map ISO_A2 (IBAN) to ISO_A3 (map) codes.
     */
    @GetMapping("/heatmap/countries")
    public ResponseEntity<Map<String, Integer>> getCountryHeatmapData(
            @RequestParam(defaultValue = "24") int hours) {
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(hours);
            
            // Get aggregated country counts from database (ISO_A2 format)
            List<Object[]> countryCountsA2 = transferRepository.getCountryTransactionCounts(since);
            
            // Map ISO_A2 to ISO_A3 and aggregate
            Map<String, Integer> heatmapData = new HashMap<>();
            for (Object[] row : countryCountsA2) {
                String isoA2 = (String) row[0];
                Long count = ((Number) row[1]).longValue();
                
                // Convert ISO_A2 to ISO_A3
                String isoA3 = isoA2ToIsoA3(isoA2);
                if (isoA3 != null) {
                    heatmapData.put(isoA3, heatmapData.getOrDefault(isoA3, 0) + count.intValue());
                }
            }
            
            logger.debug("Country heatmap data: {} countries from {} hours", heatmapData.size(), hours);
            return ResponseEntity.ok(heatmapData);
            
        } catch (Exception e) {
            logger.error("Error retrieving country heatmap data", e);
            return ResponseEntity.internalServerError().body(Map.of());
        }
    }

    /**
     * Maps ISO 3166-1 alpha-2 (IBAN country codes) to ISO 3166-1 alpha-3 (map country codes).
     * This is a subset of common banking countries. For production, use a full mapping library.
     */
    private String isoA2ToIsoA3(String isoA2) {
        if (isoA2 == null || isoA2.length() != 2) {
            return null;
        }
        
        // Common banking countries mapping
        return switch (isoA2.toUpperCase()) {
            case "DE" -> "DEU"; // Germany
            case "GB" -> "GBR"; // United Kingdom
            case "FR" -> "FRA"; // France
            case "ES" -> "ESP"; // Spain
            case "IT" -> "ITA"; // Italy
            case "NL" -> "NLD"; // Netherlands
            case "CH" -> "CHE"; // Switzerland
            case "BE" -> "BEL"; // Belgium
            case "AT" -> "AUT"; // Austria
            case "SE" -> "SWE"; // Sweden
            case "NO" -> "NOR"; // Norway
            case "DK" -> "DNK"; // Denmark
            case "FI" -> "FIN"; // Finland
            case "PL" -> "POL"; // Poland
            case "PT" -> "PRT"; // Portugal
            case "IE" -> "IRL"; // Ireland
            case "GR" -> "GRC"; // Greece
            case "CZ" -> "CZE"; // Czech Republic
            case "HU" -> "HUN"; // Hungary
            case "RO" -> "ROU"; // Romania
            case "US" -> "USA"; // United States
            case "CA" -> "CAN"; // Canada
            case "AU" -> "AUS"; // Australia
            case "NZ" -> "NZL"; // New Zealand
            case "JP" -> "JPN"; // Japan
            case "CN" -> "CHN"; // China
            case "KR" -> "KOR"; // South Korea
            case "SG" -> "SGP"; // Singapore
            case "HK" -> "HKG"; // Hong Kong
            case "IN" -> "IND"; // India
            case "BR" -> "BRA"; // Brazil
            case "MX" -> "MEX"; // Mexico
            case "AR" -> "ARG"; // Argentina
            case "ZA" -> "ZAF"; // South Africa
            case "RU" -> "RUS"; // Russia
            case "TR" -> "TUR"; // Turkey
            case "AE" -> "ARE"; // United Arab Emirates
            case "SA" -> "SAU"; // Saudi Arabia
            case "TH" -> "THA"; // Thailand
            case "MY" -> "MYS"; // Malaysia
            case "ID" -> "IDN"; // Indonesia
            case "PH" -> "PHL"; // Philippines
            default -> null; // Unknown country code
        };
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


}
