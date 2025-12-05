package com.example.backend.controller;

import com.example.backend.service.LedgerService;
import com.example.backend.service.AuditReportService;
import com.example.backend.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for General Ledger operations.
 * Provides endpoints for financial KPIs, ledger entries, T-account visualization,
 * and SOX compliance dashboard data.
 */
@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private AuditReportService auditReportService;

    @Autowired
    private AuditService auditService;

    /**
     * Get financial KPIs: Total Assets, Total Liabilities, Net Worth, Active Accounts.
     */
    @GetMapping("/kpis")
    public ResponseEntity<LedgerService.FinancialKPIs> getFinancialKPIs() {
        LedgerService.FinancialKPIs kpis = ledgerService.calculateFinancialKPIs();
        return ResponseEntity.ok(kpis);
    }

    /**
     * Get paginated ledger entries with running balance.
     * Query parameters: page (default 0), size (default 20), sortBy (default "createdAt"), sortDir (default "desc").
     */
    @GetMapping("/entries")
    public ResponseEntity<Page<LedgerService.LedgerEntryDTO>> getLedgerEntries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        Page<LedgerService.LedgerEntryDTO> entries = ledgerService.getLedgerEntries(page, size, sortBy, sortDir);
        return ResponseEntity.ok(entries);
    }

    /**
     * Get T-account data for a specific account.
     * Shows debits and credits with running balance calculations.
     */
    @GetMapping("/t-account/{accountId}")
    public ResponseEntity<LedgerService.TAccountData> getTAccountData(@PathVariable Long accountId) {
        try {
            LedgerService.TAccountData data = ledgerService.getTAccountData(accountId);
            return ResponseEntity.ok(data);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get all accounts for selection.
     */
    @GetMapping("/accounts")
    public ResponseEntity<java.util.List<LedgerService.AccountSummaryDTO>> getAllAccounts() {
        java.util.List<LedgerService.AccountSummaryDTO> accounts = ledgerService.getAllAccounts();
        return ResponseEntity.ok(accounts);
    }

    /**
     * Get SOX Compliance Dashboard data.
     * Combines integrity status, compliance reports, and audit metrics.
     */
    @GetMapping("/sox-compliance")
    public ResponseEntity<Map<String, Object>> getSOXComplianceDashboard() {
        Map<String, Object> dashboard = new HashMap<>();
        
        // Integrity status
        AuditReportService.IntegrityReport integrityReport = auditReportService.getIntegrityStatusReport();
        dashboard.put("integrityStatus", Map.of(
            "totalVerifications", integrityReport.getTotalVerifications(),
            "totalBreaches", integrityReport.getTotalBreaches(),
            "lastVerificationTime", integrityReport.getLastVerificationTime(),
            "hasBreaches", !integrityReport.isSystemIntegrityIntact()
        ));
        
        // Today's compliance report
        AuditReportService.ComplianceReport complianceReport = auditReportService.getComplianceReport(LocalDate.now());
        dashboard.put("complianceReport", Map.of(
            "date", complianceReport.getReportDate(),
            "dailyAuditEntries", complianceReport.getDailyAuditEntries(),
            "complianceStatus", complianceReport.isIntegrityIntact() ? "COMPLIANT" : "NON_COMPLIANT"
        ));
        
        // System health
        AuditReportService.SystemHealthReport healthReport = auditReportService.getSystemHealthReport();
        dashboard.put("systemHealth", Map.of(
            "healthStatus", healthReport.getHealthStatus(),
            "integrityIntact", !healthReport.getIntegrityStatus().hasIntegrityBreaches()
        ));
        
        return ResponseEntity.ok(dashboard);
    }

    /**
     * Verify audit chain for a specific transaction/entity.
     * Returns verification result with breach detection.
     */
    @GetMapping("/audit/verify/{entityType}/{entityId}")
    public ResponseEntity<Map<String, Object>> verifyAuditChain(
            @PathVariable String entityType,
            @PathVariable Long entityId) {
        
        try {
            boolean isValid = auditService.verifyChain(entityType, entityId);
            AuditReportService.EntityAuditTrail trail = auditReportService.getEntityAuditTrail(entityType, entityId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("entityType", entityType);
            result.put("entityId", entityId);
            result.put("isValid", isValid);
            result.put("chainLength", trail.getTotalEntries());
            result.put("hasBreaches", !isValid);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to verify audit chain",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Get entity audit trail for detailed inspection.
     */
    @GetMapping("/audit/trail/{entityType}/{entityId}")
    public ResponseEntity<AuditReportService.EntityAuditTrail> getAuditTrail(
            @PathVariable String entityType,
            @PathVariable Long entityId) {
        
        AuditReportService.EntityAuditTrail trail = auditReportService.getEntityAuditTrail(entityType, entityId);
        return ResponseEntity.ok(trail);
    }

    /**
     * Export ledger entries as CSV (basic implementation).
     * In production, use a proper CSV library.
     */
    @GetMapping("/export/csv")
    public ResponseEntity<String> exportLedgerEntriesAsCSV(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "1000") int size) {
        
        Page<LedgerService.LedgerEntryDTO> entries = ledgerService.getLedgerEntries(page, size, "createdAt", "desc");
        
        StringBuilder csv = new StringBuilder();
        csv.append("Transaction ID,Debit Account,Credit Account,Amount,Currency,Running Balance,Timestamp,Status\n");
        
        for (LedgerService.LedgerEntryDTO entry : entries.getContent()) {
            csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s\n",
                entry.getTransactionId(),
                entry.getDebitAccount(),
                entry.getCreditAccount(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getRunningBalance(),
                entry.getTimestamp(),
                entry.getComplianceStatus()
            ));
        }
        
        return ResponseEntity.ok()
            .header("Content-Type", "text/csv")
            .header("Content-Disposition", "attachment; filename=ledger_entries.csv")
            .body(csv.toString());
    }
}

