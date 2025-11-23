package com.example.backend.dto;

public class ComplianceDecision {
    private Long transferId;
    private DecisionType decision; // APPROVE, REJECT
    private String reviewer;
    private String notes;

    public enum DecisionType {
        APPROVE,
        REJECT
    }

    public Long getTransferId() {
        return transferId;
    }
    public DecisionType getDecision() {
        return decision;
    }
    public String getReviewer() {
        return reviewer;
    }
    public String getNotes() {
        return notes;
    }
}
