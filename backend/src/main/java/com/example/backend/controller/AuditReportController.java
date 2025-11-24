package com.example.backend.controller;

import com.example.backend.service.AuditReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * REST Controller for SOX compliance audit reports.
 * Provides endpoints for accessing audit trails, integrity status, and compliance reports.
 */
@RestController
@RequestMapping("/api/audit/reports")
@CrossOrigin(origins = "*") // Configure appropriately for production
public class AuditReportController {

    @Autowired
    private AuditReportService auditReportService;

    /**
     * Get current integrity status report.
     * Shows the current state of hash chain verification across all audited entities.
     */
    @GetMapping("/integrity-status")
    public ResponseEntity<AuditReportService.IntegrityReport> getIntegrityStatus() {
        AuditReportService.IntegrityReport report = auditReportService.getIntegrityStatusReport();
        return ResponseEntity.ok(report);
    }

    /**
     * Get audit activity report for a date range.
     * Shows audit activity patterns and statistics.
     */
    @GetMapping("/activity")
    public ResponseEntity<AuditReportService.ActivityReport> getActivityReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().build();
        }

        AuditReportService.ActivityReport report = auditReportService.getActivityReport(startDate, endDate);
        return ResponseEntity.ok(report);
    }

    /**
     * Get detailed audit trail for a specific entity.
     * Shows complete audit history with hash chain verification.
     */
    @GetMapping("/entity/{entityType}/{entityId}")
    public ResponseEntity<AuditReportService.EntityAuditTrail> getEntityAuditTrail(
            @PathVariable String entityType,
            @PathVariable Long entityId) {

        AuditReportService.EntityAuditTrail trail = auditReportService.getEntityAuditTrail(entityType, entityId);
        return ResponseEntity.ok(trail);
    }

    /**
     * Get compliance report for SOX audit requirements.
     * Provides daily compliance status and audit metrics.
     */
    @GetMapping("/compliance")
    public ResponseEntity<AuditReportService.ComplianceReport> getComplianceReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate reportDate = date != null ? date : LocalDate.now();
        AuditReportService.ComplianceReport report = auditReportService.getComplianceReport(reportDate);
        return ResponseEntity.ok(report);
    }

    /**
     * Get system health report for audit subsystem.
     * Shows overall health status and any issues with the audit system.
     */
    @GetMapping("/health")
    public ResponseEntity<AuditReportService.SystemHealthReport> getSystemHealth() {
        AuditReportService.SystemHealthReport report = auditReportService.getSystemHealthReport();
        return ResponseEntity.ok(report);
    }

    /**
     * Get current activity report (last 7 days).
     * Convenient endpoint for recent activity overview.
     */
    @GetMapping("/activity/current")
    public ResponseEntity<AuditReportService.ActivityReport> getCurrentActivity() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(7);

        AuditReportService.ActivityReport report = auditReportService.getActivityReport(startDate, endDate);
        return ResponseEntity.ok(report);
    }

    /**
     * Get today's compliance report.
     * Convenient endpoint for daily compliance checking.
     */
    @GetMapping("/compliance/today")
    public ResponseEntity<AuditReportService.ComplianceReport> getTodayCompliance() {
        AuditReportService.ComplianceReport report = auditReportService.getComplianceReport(LocalDate.now());
        return ResponseEntity.ok(report);
    }
}
