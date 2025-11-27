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
import java.util.stream.Collectors;
import com.example.backend.repository.TransferRepository;
import com.example.backend.repository.AuditLogRepository;
import com.example.backend.entity.AuditLog;

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

    @Autowired
    private AuditLogRepository auditLogRepository;

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

            // Current activity report for risk metrics (last 24 hours)
            LocalDate today = LocalDate.now();
            LocalDate yesterday = today.minusDays(1);
            AuditReportService.ActivityReport activityReport = auditReportService.getActivityReport(yesterday, today);
            double averageRiskScore = calculateAverageRiskScore(activityReport);
            logger.debug("Risk score calculation - Total activities: {}, Activity by action: {}, Calculated score: {}", 
                        activityReport.getTotalActivities(), 
                        activityReport.getActivityByAction(), 
                        averageRiskScore);
            metrics.put("averageRiskScore", averageRiskScore);

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
            // Get recent audit logs from the last 24 hours
            LocalDateTime since = LocalDateTime.now().minusHours(24);
            List<AuditLog> recentLogs = auditLogRepository.findByCreatedAtAfterOrderByCreatedAtDesc(since);

            // Convert audit logs to dashboard events
            List<Map<String, Object>> events = recentLogs.stream()
                .limit(limit)
                .map(log -> {
                    String severity = determineSeverity(log.getAction());
                    String message = formatEventMessage(log);
                    
                    Map<String, Object> event = new HashMap<>();
                    event.put("id", String.valueOf(log.getId()));
                    event.put("type", log.getAction().toLowerCase());
                    event.put("message", message);
                    event.put("timestamp", log.getCreatedAt().toString());
                    event.put("severity", severity);
                    return event;
                })
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "events", events,
                "total", events.size()
            ));

        } catch (Exception e) {
            logger.error("Error retrieving recent events", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to retrieve recent events",
                "events", java.util.Collections.emptyList(),
                "total", 0
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
            logger.info("Fetching country heatmap data for last {} hours (since: {})", hours, since);
            
            // Get aggregated country counts from database (ISO_A2 format)
            List<Object[]> countryCountsA2 = transferRepository.getCountryTransactionCounts(since);
            logger.info("Found {} country entries from database", countryCountsA2.size());
            
            // If no data found for the requested time range, try a longer range (7 days)
            if (countryCountsA2.isEmpty() && hours < 168) {
                logger.info("No data found for {} hours, trying 7 days instead", hours);
                since = LocalDateTime.now().minusDays(7);
                countryCountsA2 = transferRepository.getCountryTransactionCounts(since);
                logger.info("Found {} country entries from 7-day range", countryCountsA2.size());
            }
            
            // Map ISO_A2 to ISO_A3 and aggregate
            Map<String, Integer> heatmapData = new HashMap<>();
            int skippedCount = 0;
            for (Object[] row : countryCountsA2) {
                if (row == null || row.length < 2) {
                    logger.warn("Invalid row data in country counts: {}", java.util.Arrays.toString(row));
                    continue;
                }
                
                String isoA2 = (String) row[0];
                Long count = ((Number) row[1]).longValue();
                
                if (isoA2 == null || isoA2.length() != 2) {
                    logger.warn("Invalid ISO_A2 code: {}", isoA2);
                    skippedCount++;
                    continue;
                }
                
                // Convert ISO_A2 to ISO_A3
                String isoA3 = isoA2ToIsoA3(isoA2);
                if (isoA3 != null) {
                    int currentCount = heatmapData.getOrDefault(isoA3, 0);
                    heatmapData.put(isoA3, currentCount + count.intValue());
                    logger.debug("Mapped {} -> {}: {} transactions", isoA2, isoA3, count);
                } else {
                    logger.warn("No ISO_A3 mapping found for ISO_A2: {} ({} transactions)", isoA2, count);
                    skippedCount++;
                }
            }
            
            logger.info("Country heatmap data: {} countries mapped, {} skipped, total transactions: {}", 
                       heatmapData.size(), skippedCount, 
                       heatmapData.values().stream().mapToInt(Integer::intValue).sum());
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

        if (totalActivities == 0) {
            logger.debug("No activities found for risk score calculation, returning 0.0");
            return 0.0;
        }

        // Calculate risk based on action types that indicate issues
        // Check for various risk indicators: BLOCK, REJECT, FAIL, ERROR, etc.
        long riskyActions = activityReport.getActivityByAction().entrySet().stream()
            .filter(entry -> {
                String action = entry.getKey().toUpperCase();
                return action.contains("BLOCK") || 
                       action.contains("REJECT") || 
                       action.contains("FAIL") || 
                       action.contains("ERROR") ||
                       action.contains("DENIED");
            })
            .mapToLong(Map.Entry::getValue)
            .sum();

        double riskRate = (double) riskyActions / totalActivities;
        double riskScore = Math.min(10.0, riskRate * 10.0); // Scale to 0-10 range
        
        logger.debug("Risk score calculation - Total: {}, Risky: {}, Rate: {}, Score: {}", 
                    totalActivities, riskyActions, riskRate, riskScore);
        
        return riskScore;
    }

    /**
     * Determine event severity based on audit log action.
     */
    private String determineSeverity(String action) {
        if (action == null) {
            return "info";
        }
        
        String upperAction = action.toUpperCase();
        if (upperAction.contains("BLOCK") || upperAction.contains("REJECT") || 
            upperAction.contains("FAIL") || upperAction.contains("ERROR")) {
            return "danger";
        } else if (upperAction.contains("REVIEW") || upperAction.contains("WARNING") ||
                   upperAction.contains("PENDING") || upperAction.contains("ALERT")) {
            return "warning";
        } else if (upperAction.contains("COMPLETE") || upperAction.contains("SUCCESS") ||
                   upperAction.contains("VERIFIED") || upperAction.contains("CLEARED")) {
            return "success";
        }
        return "info";
    }

    /**
     * Format audit log into a human-readable event message.
     */
    private String formatEventMessage(AuditLog log) {
        String action = log.getAction();
        String entityType = log.getEntityType();
        Long entityId = log.getEntityId();
        
        // Create a readable message based on action and entity
        String baseMessage = action.replace("_", " ").toLowerCase();
        baseMessage = baseMessage.substring(0, 1).toUpperCase() + baseMessage.substring(1);
        
        if (entityType != null && entityId != null) {
            return String.format("%s: %s #%d", baseMessage, entityType, entityId);
        }
        
        return baseMessage;
    }

}
