'use client';

import ProtectedRoute from '../components/ProtectedRoute';
import { useState, useEffect, useCallback } from 'react';
import { complianceApi } from '../services/api';

// Type definition for AML transaction
type AMLTransaction = {
  id: string;
  transferId: number;
  timestamp: string;
  senderBIC: string;
  receiverBIC: string;
  amount: number;
  currency: string;
  status: string;
  pipelineStage: string;
  riskScore: number;
  senderName: string;
  watchlistMatch: string;
  evidence: Array<{ type: string; value: string; description: string }>;
  urgency: string;
};

// Mock AML transaction data with BLOCKED_AML status - Multiple scenarios for testing
// This is kept as fallback but will be replaced by real data
const mockAMLTransactions: AMLTransaction[] = [
  // High-risk scenario: Sanctions evasion attempt
  {
    id: 'PAY-2025-001244',
    timestamp: '2025-11-25T14:30:57Z',
    senderBIC: 'UBSWCHZH',
    receiverBIC: 'SCBLUS33',
    amount: 5000000.00,
    currency: 'CHF',
    status: 'blocked_aml',
    pipelineStage: 'risk_check',
    riskScore: 94,
    senderName: 'UBS Wealth Management Switzerland AG',
    watchlistMatch: 'Potential Sanctions List Match',
    evidence: [
      { type: 'name_similarity', value: '94%', description: 'High similarity to sanctioned entity "UBS Global Asset Management"' },
      { type: 'geographic_risk', value: 'High', description: 'Transaction involves sanctioned jurisdictions' },
      { type: 'amount_threshold', value: 'Exceeded', description: 'Amount exceeds AML threshold of $2.5M' },
      { type: 'velocity_check', value: 'Flagged', description: 'Unusual transaction velocity detected' }
    ],
    urgency: 'high'
  },

  // Medium-risk scenario: PEP (Politically Exposed Person) transaction
  {
    id: 'PAY-2025-001250',
    timestamp: '2025-11-25T14:35:22Z',
    senderBIC: 'DBSSGB2L',
    receiverBIC: 'CITIUS33',
    amount: 2500000.00,
    currency: 'GBP',
    status: 'blocked_aml',
    pipelineStage: 'risk_check',
    riskScore: 78,
    senderName: 'DBS Bank Ltd (London Branch)',
    watchlistMatch: 'PEP Transaction Alert',
    evidence: [
      { type: 'pep_association', value: 'Direct', description: 'Sender identified as Politically Exposed Person' },
      { type: 'source_of_wealth', value: 'Unverified', description: 'Source of funds not adequately documented' },
      { type: 'enhanced_due_diligence', value: 'Required', description: 'Transaction requires enhanced due diligence review' },
      { type: 'jurisdiction_risk', value: 'Medium', description: 'Involves medium-risk jurisdictions' }
    ],
    urgency: 'medium'
  },

  // Low-risk scenario: False positive name match
  {
    id: 'PAY-2025-001255',
    timestamp: '2025-11-25T14:40:15Z',
    senderBIC: 'HSBCGB2L',
    receiverBIC: 'BNPAFRPP',
    amount: 150000.00,
    currency: 'EUR',
    status: 'blocked_aml',
    pipelineStage: 'risk_check',
    riskScore: 45,
    senderName: 'HSBC France SA',
    watchlistMatch: 'Name Similarity Alert',
    evidence: [
      { type: 'name_similarity', value: '45%', description: 'Moderate similarity to watchlist entity' },
      { type: 'false_positive_likelihood', value: 'High', description: 'High probability of false positive match' },
      { type: 'customer_history', value: 'Clean', description: 'Customer has clean transaction history' },
      { type: 'amount_threshold', value: 'Below', description: 'Amount below standard AML thresholds' }
    ],
    urgency: 'low'
  },

  // High-risk scenario: Money laundering pattern
  {
    id: 'PAY-2025-001260',
    timestamp: '2025-11-25T14:45:33Z',
    senderBIC: 'JPMOUS33',
    receiverBIC: 'DEUTDEFF',
    amount: 8750000.00,
    currency: 'USD',
    status: 'blocked_aml',
    pipelineStage: 'risk_check',
    riskScore: 91,
    senderName: 'JPMorgan Chase Bank NA',
    watchlistMatch: 'Suspicious Transaction Pattern',
    evidence: [
      { type: 'structuring_pattern', value: 'Detected', description: 'Transaction shows structuring behavior' },
      { type: 'round_number_amount', value: 'Flagged', description: 'Round number amounts suggest money laundering' },
      { type: 'frequency_analysis', value: 'Abnormal', description: 'Unusual frequency of similar transactions' },
      { type: 'network_analysis', value: 'High Risk', description: 'Connected to multiple high-risk entities' }
    ],
    urgency: 'high'
  },
  {
    id: 'PAY-2025-001236',
    timestamp: '2025-11-25T14:26:22Z',
    senderBIC: 'MHCBJPJT',
    receiverBIC: 'ICBKCNBJ',
    amount: 4500000.00,
    currency: 'JPY',
    status: 'blocked_aml',
    pipelineStage: 'risk_check',
    riskScore: 87,
    senderName: 'Mizuho Corporate Bank Ltd',
    watchlistMatch: 'OFAC Specially Designated Nationals List',
    evidence: [
      { type: 'name_similarity', value: '87%', description: 'Name matches SDN list entry for similar entity' },
      { type: 'network_analysis', value: 'Medium', description: 'Connected to previously flagged entities' },
      { type: 'amount_threshold', value: 'Exceeded', description: 'Large value transaction pattern detected' }
    ],
    urgency: 'high'
  },
  {
    id: 'PAY-2025-001230',
    timestamp: '2025-11-25T14:23:04Z',
    senderBIC: 'RZOOAT2L',
    receiverBIC: 'BAWAATWW',
    amount: 1450000.00,
    currency: 'EUR',
    status: 'blocked_aml',
    pipelineStage: 'risk_check',
    riskScore: 76,
    senderName: 'Raiffeisen Zentralbank Österreich AG',
    watchlistMatch: 'EU Sanctions List',
    evidence: [
      { type: 'geographic_risk', value: 'High', description: 'Transaction route involves high-risk corridors' },
      { type: 'behavioral_pattern', value: 'Suspicious', description: 'Deviates from normal transaction patterns' },
      { type: 'name_similarity', value: '76%', description: 'Partial match with restricted entities' }
    ],
    urgency: 'medium'
  },
  {
    id: 'PAY-2025-001225',
    timestamp: '2025-11-25T14:20:19Z',
    senderBIC: 'NBADAEAA',
    receiverBIC: 'SABBAEAA',
    amount: 1200000.00,
    currency: 'AED',
    status: 'blocked_aml',
    pipelineStage: 'risk_check',
    riskScore: 82,
    senderName: 'National Bank of Abu Dhabi PJSC',
    watchlistMatch: 'FATF High-Risk Jurisdiction',
    evidence: [
      { type: 'jurisdiction_risk', value: 'High', description: 'Originating from high-risk jurisdiction' },
      { type: 'name_similarity', value: '82%', description: 'Matches entities with similar naming patterns' },
      { type: 'transaction_type', value: 'Flagged', description: 'Unusual transaction type for this entity' }
    ],
    urgency: 'medium'
  },
  {
    id: 'PAY-2025-001218',
    timestamp: '2025-11-25T14:15:33Z',
    senderBIC: 'VNBNVNVX',
    receiverBIC: 'BKTRTRIS',
    amount: 890000.00,
    currency: 'VND',
    status: 'blocked_aml',
    pipelineStage: 'risk_check',
    riskScore: 91,
    senderName: 'Vietnam National Bank for Agriculture and Rural Development',
    watchlistMatch: 'UN Sanctions List',
    evidence: [
      { type: 'name_similarity', value: '91%', description: 'Strong match with sanctioned financial institution' },
      { type: 'geographic_risk', value: 'Critical', description: 'Involves embargoed country operations' },
      { type: 'amount_threshold', value: 'Exceeded', description: 'Exceeds regulatory reporting thresholds' },
      { type: 'historical_flags', value: 'Multiple', description: 'Entity has previous compliance incidents' }
    ],
    urgency: 'high'
  },
  {
    id: 'PAY-2025-001212',
    timestamp: '2025-11-25T14:10:47Z',
    senderBIC: 'BKCHCNBJ',
    receiverBIC: 'HSBCJPJT',
    amount: 3200000.00,
    currency: 'CNY',
    status: 'blocked_aml',
    pipelineStage: 'risk_check',
    riskScore: 68,
    senderName: 'Bank of China Hong Kong Branch',
    watchlistMatch: 'Commercial Watchlist',
    evidence: [
      { type: 'name_similarity', value: '68%', description: 'Moderate similarity to flagged entities' },
      { type: 'network_analysis', value: 'Low', description: 'Indirect connections to restricted networks' },
      { type: 'amount_threshold', value: 'Approaching', description: 'Close to threshold limits' }
    ],
    urgency: 'low'
  },
  {
    id: 'PAY-2025-001205',
    timestamp: '2025-11-25T14:05:12Z',
    senderBIC: 'ALFRFRPP',
    receiverBIC: 'DEUTDEFF',
    amount: 2100000.00,
    currency: 'EUR',
    status: 'blocked_aml',
    pipelineStage: 'risk_check',
    riskScore: 73,
    senderName: 'Société Générale Corporate and Investment Banking',
    watchlistMatch: 'Regulatory Watchlist',
    evidence: [
      { type: 'behavioral_pattern', value: 'Unusual', description: 'Transaction timing deviates from norms' },
      { type: 'name_similarity', value: '73%', description: 'Partial match with monitored entities' },
      { type: 'geographic_risk', value: 'Medium', description: 'Cross-border transaction patterns flagged' }
    ],
    urgency: 'medium'
  },
  {
    id: 'PAY-2025-001198',
    timestamp: '2025-11-25T13:58:28Z',
    senderBIC: 'CITIGB2L',
    receiverBIC: 'NWBKGB2L',
    amount: 6750000.00,
    currency: 'GBP',
    status: 'blocked_aml',
    pipelineStage: 'risk_check',
    riskScore: 89,
    senderName: 'Citibank N.A. London Branch',
    watchlistMatch: 'OFAC Sectoral Sanctions',
    evidence: [
      { type: 'sectoral_sanctions', value: 'Applicable', description: 'Transaction involves sanctioned sectors' },
      { type: 'name_similarity', value: '89%', description: 'High confidence match with restricted entities' },
      { type: 'amount_threshold', value: 'Significantly Exceeded', description: 'Well above reporting thresholds' },
      { type: 'velocity_check', value: 'Critical', description: 'Extremely high transaction frequency detected' }
    ],
    urgency: 'high'
  },

  // Critical-risk scenario: Terrorist financing indicators
  {
    id: 'PAY-2025-001265',
    timestamp: '2025-11-25T14:50:47Z',
    senderBIC: 'BARCAESMM',
    receiverBIC: 'NWBKGB2L',
    amount: 125000.00,
    currency: 'EUR',
    status: 'blocked_aml',
    pipelineStage: 'risk_check',
    riskScore: 98,
    senderName: 'Banco Bilbao Vizcaya Argentaria SA',
    watchlistMatch: 'Terrorist Financing Indicators',
    evidence: [
      { type: 'terrorism_finance_keywords', value: 'Detected', description: 'Transaction contains terrorism-related keywords' },
      { type: 'beneficial_owner_risk', value: 'Critical', description: 'Beneficial owner linked to high-risk networks' },
      { type: 'charity_funding_pattern', value: 'Suspected', description: 'Pattern matches known terrorist funding methods' },
      { type: 'urgency_flags', value: 'Multiple', description: 'Multiple urgent risk indicators present' }
    ],
    urgency: 'high'
  },

  // Medium-risk scenario: Corporate sanctions violation
  {
    id: 'PAY-2025-001270',
    timestamp: '2025-11-25T14:55:12Z',
    senderBIC: 'GSCMGGSP',
    receiverBIC: 'SBININBB',
    amount: 3200000.00,
    currency: 'USD',
    status: 'blocked_aml',
    pipelineStage: 'risk_check',
    riskScore: 72,
    senderName: 'Butterfield Bank (Cayman) Limited',
    watchlistMatch: 'Corporate Sanctions Exposure',
    evidence: [
      { type: 'corporate_exposure', value: 'Direct', description: 'Company directly exposed to sanctioned entities' },
      { type: 'supply_chain_risk', value: 'Medium', description: 'Supply chain involves sanctioned goods/services' },
      { type: 'license_verification', value: 'Failed', description: 'Required licenses not verified' },
      { type: 'geographic_exposure', value: 'High', description: 'Operations in high-risk geographic areas' }
    ],
    urgency: 'medium'
  },

  // Low-risk scenario: False positive with clean history
  {
    id: 'PAY-2025-001275',
    timestamp: '2025-11-25T15:00:28Z',
    senderBIC: 'NORDEA',
    receiverBIC: 'SWEDSESS',
    amount: 85000.00,
    currency: 'SEK',
    status: 'blocked_aml',
    pipelineStage: 'risk_check',
    riskScore: 35,
    senderName: 'Nordea Bank AB',
    watchlistMatch: 'Name Similarity Alert',
    evidence: [
      { type: 'name_similarity', value: '35%', description: 'Low similarity to watchlist entity' },
      { type: 'false_positive_likelihood', value: 'Very High', description: 'Very high probability of false positive match' },
      { type: 'customer_history', value: 'Excellent', description: 'Customer has excellent compliance history' },
      { type: 'amount_threshold', value: 'Well Below', description: 'Amount significantly below AML thresholds' }
    ],
    urgency: 'low'
  }
];

// Status Pill Component for AML transactions
function AMLStatusPill({ status, urgency }: { status: string; urgency: string }) {
  const getStatusConfig = () => {
    return {
      text: 'BLOCKED_AML',
      bgColor: 'bg-sentinel-accent-danger/20',
      textColor: 'text-sentinel-accent-danger',
      borderColor: 'border-sentinel-accent-danger/30',
      urgencyColor: urgency === 'high' ? 'ring-2 ring-sentinel-accent-danger/50' :
                   urgency === 'medium' ? 'ring-2 ring-sentinel-accent-warning/50' : ''
    };
  };

  const config = getStatusConfig();

  return (
    <span className={`px-2 py-1 text-xs font-bold rounded-full border ${config.bgColor} ${config.textColor} ${config.borderColor} ${config.urgencyColor} uppercase tracking-wide`}>
      {config.text}
    </span>
  );
}

// Evidence Progress Bar Component
function EvidenceProgressBar({ value }: { value: string }) {
  const percentage = parseInt(value.replace('%', ''));
  const getColor = (pct: number) => {
    if (pct >= 85) return 'bg-sentinel-accent-danger';
    if (pct >= 70) return 'bg-sentinel-accent-warning';
    return 'bg-sentinel-accent-primary';
  };

  return (
    <div className="w-full bg-sentinel-bg-tertiary rounded-full h-2">
      <div
        className={`h-2 rounded-full transition-all duration-300 ${getColor(percentage)}`}
        style={{ width: `${percentage}%` }}
      />
    </div>
  );
}

// Left Panel - Transaction Queue
function TransactionQueue({
  transactions,
  selectedTransaction,
  onSelectTransaction,
  currentPage,
  pageSize,
  totalPages,
  onPageChange,
  onPageSizeChange
}: {
  transactions: AMLTransaction[];
  selectedTransaction: AMLTransaction | null;
  onSelectTransaction: (transaction: AMLTransaction) => void;
  currentPage: number;
  pageSize: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
}) {
  // Note: transactions are already paginated from the API
  const currentTransactions = transactions;

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="border-b border-sentinel-border px-4 py-3">
        <h3 className="text-sm font-medium text-sentinel-text-primary uppercase tracking-wide">
          AML Transaction Queue
        </h3>
        <p className="text-xs text-sentinel-text-muted mt-1">
          {transactions.length > 0 ? `${transactions.length} transactions requiring review` : 'No transactions'}
        </p>
      </div>

      {/* Transaction List */}
      <div className="flex-1 overflow-y-auto">
        <div className="divide-y divide-sentinel-border">
          {currentTransactions.map((transaction) => (
            <div
              key={transaction.id}
              onClick={() => onSelectTransaction(transaction)}
              className={`px-4 py-3 cursor-pointer transition-colors hover:bg-sentinel-bg-tertiary/30 ${
                selectedTransaction?.id === transaction.id ? 'bg-sentinel-accent-primary/10 border-l-4 border-sentinel-accent-primary' : ''
              }`}
            >
              <div className="flex items-start justify-between mb-2">
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-sentinel-text-primary truncate">
                    {transaction.senderName}
                  </p>
                  <p className="text-xs font-mono text-sentinel-text-secondary">
                    {transaction.id}
                  </p>
                </div>
                <div className="ml-2">
                  <AMLStatusPill status={transaction.status} urgency={transaction.urgency} />
                </div>
              </div>

              <div className="flex items-center justify-between text-xs">
                <span className="text-sentinel-text-muted">
                  {transaction.amount.toLocaleString('en-US', {
                    style: 'currency',
                    currency: transaction.currency
                  })}
                </span>
                <span className="text-sentinel-text-muted">
                  Risk: {transaction.riskScore}%
                </span>
              </div>

              <div className="mt-2">
                <p className="text-xs text-sentinel-accent-danger font-medium truncate">
                  {transaction.watchlistMatch}
                </p>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Pagination Footer */}
      <div className="border-t border-sentinel-border px-4 py-3">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center space-x-2 text-xs text-sentinel-text-muted">
            <span>Show</span>
            <select
              value={pageSize}
              onChange={(e) => onPageSizeChange(Number(e.target.value))}
              className="bg-sentinel-bg-tertiary border border-sentinel-border rounded px-2 py-1 text-xs focus:border-sentinel-accent-primary focus:outline-none"
            >
              <option value={3}>3</option>
              <option value={5}>5</option>
              <option value={10}>10</option>
              <option value={15}>15</option>
            </select>
            <span>per page</span>
          </div>
            <span className="text-xs text-sentinel-text-muted">
              Showing {transactions.length} transaction{transactions.length !== 1 ? 's' : ''}
            </span>
        </div>

        {/* Page Navigation */}
        <div className="flex items-center justify-center space-x-1">
          <button
            onClick={() => onPageChange(currentPage - 1)}
            disabled={currentPage <= 1}
            className="px-2 py-1 text-xs border border-sentinel-border rounded hover:bg-sentinel-bg-tertiary disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            ‹
          </button>

          {/* Page Numbers - Simplified for now */}
          <span className="text-xs text-sentinel-text-muted">
            Page {currentPage} {totalPages > 0 && `of ${totalPages}`}
          </span>

          <button
            onClick={() => onPageChange(currentPage + 1)}
            disabled={currentPage >= totalPages || totalPages === 0}
            className="px-2 py-1 text-xs border border-sentinel-border rounded hover:bg-sentinel-bg-tertiary disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            ›
          </button>
        </div>
      </div>
    </div>
  );
}

// Compliance Override Modal
function ComplianceOverrideModal({
  isOpen,
  transaction,
  justification,
  onJustificationChange,
  onConfirm,
  onCancel,
  loading = false
}: {
  isOpen: boolean;
  transaction: AMLTransaction | null;
  justification: string;
  onJustificationChange: (value: string) => void;
  onConfirm: () => void;
  onCancel: () => void;
  loading?: boolean;
}) {
  if (!isOpen || !transaction) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-sentinel-bg-secondary border border-sentinel-border rounded-lg max-w-lg w-full mx-4">
        {/* Modal Header */}
        <div className="border-b border-sentinel-border px-6 py-4">
          <h3 className="text-lg font-medium text-sentinel-text-primary">
            Compliance Override Request
          </h3>
          <p className="text-sm text-sentinel-text-secondary mt-1">
            Transaction: {transaction.id}
          </p>
        </div>

        {/* Modal Body */}
        <div className="px-6 py-4">
          <div className="mb-4">
            <p className="text-sm text-sentinel-text-primary mb-2">
              Please provide justification for overriding this AML block:
            </p>
            <textarea
              value={justification}
              onChange={(e) => onJustificationChange(e.target.value)}
              placeholder="Enter detailed justification for the compliance override..."
              className="w-full h-32 bg-sentinel-bg-tertiary border border-sentinel-border rounded px-3 py-2 text-sm text-sentinel-text-primary placeholder-sentinel-text-muted focus:border-sentinel-accent-primary focus:outline-none resize-none"
              maxLength={500}
            />
            <div className="text-xs text-sentinel-text-muted mt-1">
              {justification.length}/500 characters
            </div>
          </div>

          {/* Warning Section */}
          <div className="bg-sentinel-accent-danger/10 border border-sentinel-accent-danger/30 rounded p-3 mb-4">
            <div className="flex items-start space-x-2">
              <svg className="w-5 h-5 text-sentinel-accent-danger mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
              </svg>
              <div>
                <p className="text-sm font-medium text-sentinel-accent-danger">
                  High-Risk Override
                </p>
                <p className="text-xs text-sentinel-text-secondary mt-1">
                  This transaction has a risk score of {transaction.riskScore}%. Override decisions will be logged and may require additional approval.
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* Modal Footer */}
        <div className="border-t border-sentinel-border px-6 py-4 flex justify-end space-x-3">
          <button
            onClick={onCancel}
            className="px-4 py-2 text-sm border border-sentinel-border rounded hover:bg-sentinel-bg-tertiary transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={onConfirm}
            disabled={!justification.trim() || loading}
            className="px-4 py-2 text-sm bg-sentinel-accent-primary hover:bg-sentinel-accent-primary/90 disabled:opacity-50 disabled:cursor-not-allowed text-sentinel-bg-primary rounded transition-colors"
          >
            {loading ? 'Processing...' : 'Confirm Override'}
          </button>
        </div>
      </div>
    </div>
  );
}

// Right Panel - Transaction Details
function TransactionDetails({
  transaction,
  onOverrideClick
}: {
  transaction: AMLTransaction | null;
  onOverrideClick: () => void;
}) {
  if (!transaction) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="text-center">
          <div className="w-16 h-16 mx-auto mb-4 bg-sentinel-bg-tertiary rounded-full flex items-center justify-center">
            <svg className="w-8 h-8 text-sentinel-text-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
          </div>
          <p className="text-sentinel-text-muted">Select a transaction to view details</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full">
      {/* Transaction Header */}
      <div className="border-b border-sentinel-border px-6 py-4">
        <div className="flex items-start justify-between mb-4">
          <div>
            <h3 className="text-lg font-medium text-sentinel-text-primary">
              Transaction Details
            </h3>
            <p className="text-sm font-mono text-sentinel-text-secondary mt-1">
              {transaction.id}
            </p>
          </div>
          <AMLStatusPill status={transaction.status} urgency={transaction.urgency} />
        </div>

        {/* Transaction Summary */}
        <div className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <p className="text-sentinel-text-muted">Amount</p>
            <p className="font-medium text-sentinel-text-primary">
              {transaction.amount.toLocaleString('en-US', {
                style: 'currency',
                currency: transaction.currency
              })}
            </p>
          </div>
          <div>
            <p className="text-sentinel-text-muted">Risk Score</p>
            <p className="font-medium text-sentinel-accent-danger">
              {transaction.riskScore}%
            </p>
          </div>
          <div>
            <p className="text-sentinel-text-muted">Sender BIC</p>
            <p className="font-mono text-sentinel-text-primary">{transaction.senderBIC}</p>
          </div>
          <div>
            <p className="text-sentinel-text-muted">Receiver BIC</p>
            <p className="font-mono text-sentinel-text-primary">{transaction.receiverBIC}</p>
          </div>
        </div>
      </div>

      {/* Match Evidence Section */}
      <div className="flex-1 overflow-y-auto px-6 py-4">
        <h4 className="text-md font-medium text-sentinel-text-primary mb-4">
          Match Evidence
        </h4>

        {/* Primary Match */}
        <div className="mb-6 p-4 bg-sentinel-bg-tertiary/50 rounded-lg border border-sentinel-border">
          <div className="flex items-center justify-between mb-2">
            <h5 className="text-sm font-medium text-sentinel-text-primary">
              Primary Match: {transaction.watchlistMatch}
            </h5>
            <span className="text-xs text-sentinel-text-muted">
              {new Date(transaction.timestamp).toLocaleString()}
            </span>
          </div>
          <div className="space-y-3">
            {transaction.evidence.map((evidence, index) => (
              <div key={index} className="flex items-center justify-between">
                <div className="flex-1">
                  <p className="text-sm text-sentinel-text-primary">{evidence.description}</p>
                  <p className="text-xs text-sentinel-text-muted mt-1">{evidence.type.replace('_', ' ').toUpperCase()}</p>
                </div>
                <div className="ml-4 min-w-[80px]">
                  {evidence.type === 'name_similarity' ? (
                    <div className="text-center">
                      <div className="text-sm font-medium text-sentinel-text-primary mb-1">
                        {evidence.value}
                      </div>
                      <EvidenceProgressBar value={evidence.value} />
                    </div>
                  ) : (
                    <span className={`text-xs px-2 py-1 rounded ${
                      evidence.value === 'High' || evidence.value === 'Critical' || evidence.value === 'Exceeded'
                        ? 'bg-sentinel-accent-danger/20 text-sentinel-accent-danger'
                        : evidence.value === 'Medium' || evidence.value === 'Approaching'
                        ? 'bg-sentinel-accent-warning/20 text-sentinel-accent-warning'
                        : 'bg-sentinel-accent-primary/20 text-sentinel-accent-primary'
                    }`}>
                      {evidence.value}
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Action Buttons */}
      <div className="border-t border-sentinel-border px-6 py-4">
        <div className="flex space-x-3">
          <button className="flex-1 bg-sentinel-accent-danger hover:bg-sentinel-accent-danger/90 text-sentinel-bg-primary font-medium py-2 px-4 rounded transition-colors">
            REJECT & SEIZE
          </button>
          <button
            onClick={onOverrideClick}
            className="flex-1 bg-sentinel-accent-primary hover:bg-sentinel-accent-primary/90 text-sentinel-bg-primary font-medium py-2 px-4 rounded transition-colors"
          >
            OVERRIDE & RELEASE
          </button>
        </div>
      </div>
    </div>
  );
}

export default function InvestigationsPage() {
  const [transactions, setTransactions] = useState<AMLTransaction[]>([]);
  const [selectedTransaction, setSelectedTransaction] = useState<AMLTransaction | null>(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize] = useState(5);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showOverrideModal, setShowOverrideModal] = useState(false);
  const [overrideJustification, setOverrideJustification] = useState('');
  const [overrideLoading, setOverrideLoading] = useState(false);

  // Fetch transactions from API
  const fetchTransactions = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await complianceApi.getWorklist({
        page: currentPage,
        size: pageSize,
        sortBy: 'createdAt',
        sortDir: 'desc'
      });
      
      // Map backend DTO to frontend format
      const mappedTransactions: AMLTransaction[] = data.content.map((item: any) => ({
        id: item.id || `PAY-${item.transferId}`,
        transferId: item.transferId,
        timestamp: item.timestamp,
        senderBIC: item.senderBIC || 'UNKNOWN',
        receiverBIC: item.receiverBIC || 'UNKNOWN',
        amount: item.amount || 0,
        currency: item.currency || 'USD',
        status: item.status || 'blocked_aml',
        pipelineStage: item.pipelineStage || 'risk_check',
        riskScore: item.riskScore || 75,
        senderName: item.senderName || 'Unknown Sender',
        watchlistMatch: item.watchlistMatch || 'Compliance Review Required',
        evidence: item.evidence || [],
        urgency: item.urgency || 'medium'
      }));

      setTransactions(mappedTransactions);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
    } catch (err) {
      console.error('Error fetching transactions:', err);
      setError(err instanceof Error ? err.message : 'Failed to fetch transactions');
      // Fallback to empty array on error
      setTransactions([]);
    } finally {
      setLoading(false);
    }
  }, [currentPage, pageSize]);

  // Fetch transactions on mount and when page/size changes
  useEffect(() => {
    fetchTransactions();
  }, [fetchTransactions]);

  const goToPage = (page: number) => {
    setCurrentPage(Math.max(0, Math.min(page - 1, totalPages - 1)));
  };

  const changePageSize = (newPageSize: number) => {
    setPageSize(newPageSize);
    setCurrentPage(0);
  };

  const handleOverrideClick = () => {
    setShowOverrideModal(true);
  };

  const handleOverrideConfirm = async () => {
    if (!selectedTransaction || !overrideJustification.trim()) {
      return;
    }

    setOverrideLoading(true);
    try {
      await complianceApi.reviewTransaction(
        selectedTransaction.transferId,
        'APPROVE',
        overrideJustification
      );

      // Refresh the transactions list
      await fetchTransactions();
      
      // Clear selection and close modal
      setSelectedTransaction(null);
      setShowOverrideModal(false);
      setOverrideJustification('');
      
      // Show success message (you could add a toast notification here)
      alert('Transaction override successful');
    } catch (err) {
      console.error('Error overriding transaction:', err);
      alert(`Failed to override transaction: ${err instanceof Error ? err.message : 'Unknown error'}`);
    } finally {
      setOverrideLoading(false);
    }
  };

  const handleOverrideCancel = () => {
    setShowOverrideModal(false);
    setOverrideJustification('');
  };

  return (
    <ProtectedRoute>
      <div className="p-6 space-y-6">
        <div className="mb-6">
          <h1 className="text-2xl font-semibold text-sentinel-text-primary mb-2">
            Investigations
          </h1>
          <p className="text-sentinel-text-secondary">
            AML compliance workbench and decision-making interface
          </p>
        </div>

        {/* Error Message */}
        {error && (
          <div className="bg-sentinel-accent-danger/10 border border-sentinel-accent-danger/30 rounded-lg p-4">
            <p className="text-sentinel-accent-danger text-sm">
              Error: {error}
            </p>
          </div>
        )}

        {/* Loading State */}
        {loading && transactions.length === 0 && (
          <div className="flex items-center justify-center h-[calc(100vh-200px)]">
            <div className="text-center">
              <div className="w-16 h-16 mx-auto mb-4 bg-sentinel-bg-tertiary rounded-full flex items-center justify-center animate-pulse">
                <svg className="w-8 h-8 text-sentinel-text-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                </svg>
              </div>
              <p className="text-sentinel-text-muted">Loading transactions...</p>
            </div>
          </div>
        )}

        {/* Empty State */}
        {!loading && transactions.length === 0 && !error && (
          <div className="flex items-center justify-center h-[calc(100vh-200px)]">
            <div className="text-center">
              <div className="w-16 h-16 mx-auto mb-4 bg-sentinel-bg-tertiary rounded-full flex items-center justify-center">
                <svg className="w-8 h-8 text-sentinel-text-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                </svg>
              </div>
              <p className="text-sentinel-text-muted">No blocked transactions found</p>
            </div>
          </div>
        )}

        {/* Main Compliance Cockpit Layout */}
        {(!loading || transactions.length > 0) && (
          <div className={`grid gap-6 h-[calc(100vh-200px)] ${selectedTransaction ? 'grid-cols-12' : 'grid-cols-1'}`}>
            {/* Left Panel - Transaction Queue */}
            <div className={`${selectedTransaction ? 'col-span-4' : 'col-span-1'} bg-sentinel-bg-secondary border border-sentinel-border rounded-lg overflow-hidden`}>
              <TransactionQueue
                transactions={transactions}
                selectedTransaction={selectedTransaction}
                onSelectTransaction={setSelectedTransaction}
                currentPage={currentPage + 1}
                pageSize={pageSize}
                totalPages={totalPages}
                onPageChange={goToPage}
                onPageSizeChange={changePageSize}
              />
            </div>

            {/* Right Panel - Transaction Details (only show when transaction selected) */}
            {selectedTransaction && (
              <div className="col-span-8 bg-sentinel-bg-secondary border border-sentinel-border rounded-lg overflow-hidden">
                <TransactionDetails
                  transaction={selectedTransaction}
                  onOverrideClick={handleOverrideClick}
                />
              </div>
            )}
          </div>
        )}

        {/* Compliance Override Modal */}
        <ComplianceOverrideModal
          isOpen={showOverrideModal}
          transaction={selectedTransaction}
          justification={overrideJustification}
          onJustificationChange={setOverrideJustification}
          onConfirm={handleOverrideConfirm}
          onCancel={handleOverrideCancel}
          loading={overrideLoading}
        />
      </div>
    </ProtectedRoute>
  );
}

