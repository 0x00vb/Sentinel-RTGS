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

    // Getters
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

    // Setters
    public void setTransferId(Long transferId) {
        this.transferId = transferId;
    }

    public void setDecision(DecisionType decision) {
        this.decision = decision;
    }

    public void setReviewer(String reviewer) {
        this.reviewer = reviewer;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
