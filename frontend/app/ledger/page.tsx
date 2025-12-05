'use client';

import ProtectedRoute from '../components/ProtectedRoute';
import { useState, useEffect, useCallback } from 'react';
import { ledgerApi } from '../services/api';

// Types
interface FinancialKPIs {
  totalAssets: number;
  totalLiabilities: number;
  netWorth: number;
  activeAccounts: number;
}

interface LedgerEntry {
  id: number;
  transferId: number;
  transactionId: string;
  accountId: number;
  accountIban: string;
  accountOwner: string;
  debitAccount: string;
  creditAccount: string;
  entryType: 'DEBIT' | 'CREDIT';
  amount: number;
  currency: string;
  runningBalance: number;
  timestamp: string;
  complianceStatus: 'PENDING' | 'CLEARED' | 'BLOCKED_AML' | 'REJECTED';
}

interface AccountSummary {
  id: number;
  iban: string;
  ownerName: string;
  currency: string;
  balance: number;
}

interface TAccountData {
  accountId: number;
  iban: string;
  ownerName: string;
  currency: string;
  accountBalance: number;
  calculatedBalance: number;
  debits: TAccountEntry[];
  credits: TAccountEntry[];
}

interface TAccountEntry {
  entryId: number;
  transferId: number;
  transactionId: string;
  amount: number;
  timestamp: string;
  status: string;
  runningBalance: number;
}

interface SOXCompliance {
  integrityStatus: {
    totalVerifications: number;
    totalBreaches: number;
    lastVerificationTime: number;
    hasBreaches: boolean;
  };
  complianceReport: {
    date: string;
    dailyAuditEntries: number;
    complianceStatus: string;
  };
  systemHealth: {
    healthStatus: string;
    integrityIntact: boolean;
  };
}

export default function LedgerPage() {
  const [kpis, setKpis] = useState<FinancialKPIs | null>(null);
  const [ledgerEntries, setLedgerEntries] = useState<LedgerEntry[]>([]);
  const [selectedAccount, setSelectedAccount] = useState<number | null>(null);
  const [tAccountData, setTAccountData] = useState<TAccountData | null>(null);
  const [accounts, setAccounts] = useState<AccountSummary[]>([]);
  const [soxCompliance, setSoxCompliance] = useState<SOXCompliance | null>(null);
  
  // Loading states
  const [loadingKPIs, setLoadingKPIs] = useState(true);
  const [loadingEntries, setLoadingEntries] = useState(true);
  const [loadingAccounts, setLoadingAccounts] = useState(true);
  const [loadingSOX, setLoadingSOX] = useState(true);
  const [loadingTAccount, setLoadingTAccount] = useState(false);
  
  // Error states
  const [errorKPIs, setErrorKPIs] = useState<string | null>(null);
  const [errorEntries, setErrorEntries] = useState<string | null>(null);
  const [errorAccounts, setErrorAccounts] = useState<string | null>(null);
  const [errorSOX, setErrorSOX] = useState<string | null>(null);
  
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [verifyingChain, setVerifyingChain] = useState(false);
  const [verificationResult, setVerificationResult] = useState<any>(null);
  const [selectedEntry, setSelectedEntry] = useState<LedgerEntry | null>(null);
  const [isTAccountModalOpen, setIsTAccountModalOpen] = useState(false);
  const [isSOXModalOpen, setIsSOXModalOpen] = useState(false);

  // Fetch KPIs
  const fetchKPIs = useCallback(async () => {
    setLoadingKPIs(true);
    setErrorKPIs(null);
    try {
      const data = await ledgerApi.getKPIs();
      setKpis({
        totalAssets: parseFloat(data.totalAssets) || 0,
        totalLiabilities: parseFloat(data.totalLiabilities) || 0,
        netWorth: parseFloat(data.netWorth) || 0,
        activeAccounts: data.activeAccounts || 0
      });
    } catch (error) {
      console.error('Failed to fetch KPIs:', error);
      setErrorKPIs(error instanceof Error ? error.message : 'Unknown error');
    } finally {
      setLoadingKPIs(false);
    }
  }, []);

  // Fetch ledger entries
  const fetchLedgerEntries = useCallback(async () => {
    setLoadingEntries(true);
    setErrorEntries(null);
    try {
      const data = await ledgerApi.getEntries({
        page: currentPage,
        size: pageSize,
        sortBy: 'createdAt',
        sortDir: 'desc'
      });
      setLedgerEntries(data.content || []);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
    } catch (error) {
      console.error('Failed to fetch ledger entries:', error);
      setErrorEntries(error instanceof Error ? error.message : 'Unknown error');
      setLedgerEntries([]);
    } finally {
      setLoadingEntries(false);
    }
  }, [currentPage, pageSize]);

  // Fetch accounts
  const fetchAccounts = useCallback(async () => {
    setLoadingAccounts(true);
    setErrorAccounts(null);
    try {
      const data = await ledgerApi.getAccounts();
      setAccounts(data || []);
    } catch (error) {
      console.error('Failed to fetch accounts:', error);
      setErrorAccounts(error instanceof Error ? error.message : 'Unknown error');
      setAccounts([]);
    } finally {
      setLoadingAccounts(false);
    }
  }, []);

  // Fetch T-account data
  const fetchTAccountData = useCallback(async (accountId: number) => {
    setLoadingTAccount(true);
    try {
      const data = await ledgerApi.getTAccount(accountId);
      setTAccountData(data);
    } catch (error) {
      console.error('Failed to fetch T-account data:', error);
      setTAccountData(null);
      alert(`Failed to load T-account data: ${error instanceof Error ? error.message : 'Unknown error'}`);
    } finally {
      setLoadingTAccount(false);
    }
  }, []);

  // Fetch SOX compliance
  const fetchSOXCompliance = useCallback(async () => {
    setLoadingSOX(true);
    setErrorSOX(null);
    try {
      const data = await ledgerApi.getSOXCompliance();
      setSoxCompliance(data);
    } catch (error) {
      console.error('Failed to fetch SOX compliance:', error);
      setErrorSOX(error instanceof Error ? error.message : 'Unknown error');
      setSoxCompliance(null);
    } finally {
      setLoadingSOX(false);
    }
  }, []);

  // Initial data fetch
  useEffect(() => {
    fetchKPIs();
    fetchAccounts();
    fetchSOXCompliance();
  }, [fetchKPIs, fetchAccounts, fetchSOXCompliance]);

  // Fetch ledger entries when page/size changes
  useEffect(() => {
    fetchLedgerEntries();
  }, [fetchLedgerEntries]);

  // Fetch T-account data when account is selected
  useEffect(() => {
    if (selectedAccount) {
      fetchTAccountData(selectedAccount);
    } else {
      setTAccountData(null);
    }
  }, [selectedAccount, fetchTAccountData]);

  // Auto-refresh every 30 seconds
  useEffect(() => {
    const interval = setInterval(() => {
      fetchKPIs();
      fetchLedgerEntries();
      fetchSOXCompliance();
    }, 30000);
    return () => clearInterval(interval);
  }, [fetchKPIs, fetchLedgerEntries, fetchSOXCompliance]);

  const verifyAuditChain = async (entityType: string, entityId: number) => {
    setVerifyingChain(true);
    try {
      const data = await ledgerApi.verifyAuditChain(entityType, entityId);
      setVerificationResult(data);
      alert(`Audit Chain Verification: ${data.isValid ? '✓ VALID' : '✗ BREACH DETECTED'}\nChain Length: ${data.chainLength}`);
    } catch (error) {
      console.error('Failed to verify audit chain:', error);
      alert(`Failed to verify audit chain: ${error instanceof Error ? error.message : 'Unknown error'}`);
    } finally {
      setVerifyingChain(false);
    }
  };

  const exportCSV = async () => {
    try {
      const blob = await ledgerApi.exportCSV({ page: 0, size: 10000 });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `ledger_entries_${new Date().toISOString().split('T')[0]}.csv`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (error) {
      console.error('Failed to export CSV:', error);
      alert(`Failed to export CSV: ${error instanceof Error ? error.message : 'Unknown error'}`);
    }
  };

  const formatCurrency = (amount: number, currency: string = 'USD') => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(amount);
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'CLEARED':
        return 'text-sentinel-accent-success';
      case 'BLOCKED_AML':
        return 'text-sentinel-accent-danger';
      case 'REJECTED':
        return 'text-sentinel-accent-danger';
      case 'PENDING':
        return 'text-sentinel-accent-warning';
      default:
        return 'text-sentinel-text-secondary';
    }
  };

  const getStatusBg = (status: string) => {
    switch (status) {
      case 'CLEARED':
        return 'bg-sentinel-accent-success/20 border-sentinel-accent-success/30';
      case 'BLOCKED_AML':
        return 'bg-sentinel-accent-danger/20 border-sentinel-accent-danger/30';
      case 'REJECTED':
        return 'bg-sentinel-accent-danger/20 border-sentinel-accent-danger/30';
      case 'PENDING':
        return 'bg-sentinel-accent-warning/20 border-sentinel-accent-warning/30';
      default:
        return 'bg-sentinel-bg-tertiary border-sentinel-border';
    }
  };

  const LoadingSpinner = () => (
    <div className="flex items-center justify-center p-8">
      <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-sentinel-accent-primary"></div>
    </div>
  );

  const ErrorMessage = ({ message, onRetry }: { message: string; onRetry: () => void }) => (
    <div className="p-4 bg-sentinel-accent-danger/10 border border-sentinel-accent-danger/30 rounded-lg">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <svg className="w-5 h-5 text-sentinel-accent-danger" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span className="text-sentinel-accent-danger text-sm">{message}</span>
        </div>
        <button
          onClick={onRetry}
          className="px-3 py-1 text-xs bg-sentinel-accent-danger/20 text-sentinel-accent-danger border border-sentinel-accent-danger/30 rounded hover:bg-sentinel-accent-danger/30 transition-colors"
        >
          Retry
        </button>
      </div>
    </div>
  );

  return (
    <ProtectedRoute>
      <div className="min-h-screen bg-sentinel-bg-primary text-sentinel-text-primary p-6">
        <div className="max-w-7xl mx-auto">
          {/* Header */}
          <div className="mb-6 flex items-center justify-between">
            <div>
              <h1 className="text-3xl font-semibold text-sentinel-text-primary mb-2">
                General Ledger
              </h1>
              <p className="text-sentinel-text-secondary">
                SOX-compliant immutable financial truth with real-time compliance monitoring
              </p>
            </div>
            <div className="flex items-center gap-3">
              {/* SOX Compliance Widget */}
              {soxCompliance && (
                <div className="px-4 py-2 bg-sentinel-bg-secondary border border-sentinel-border rounded-lg flex items-center gap-3">
                  <div className={`w-2 h-2 rounded-full ${
                    !soxCompliance.integrityStatus.hasBreaches
                      ? 'bg-sentinel-accent-success animate-pulse'
                      : 'bg-sentinel-accent-danger animate-pulse'
                  }`}></div>
                  <div className="flex flex-col">
                    <span className="text-xs text-sentinel-text-secondary">SOX Status</span>
                    <span className={`text-sm font-semibold ${
                      !soxCompliance.integrityStatus.hasBreaches
                        ? 'text-sentinel-accent-success'
                        : 'text-sentinel-accent-danger'
                    }`}>
                      {!soxCompliance.integrityStatus.hasBreaches ? 'COMPLIANT' : 'BREACH DETECTED'}
                    </span>
                  </div>
                  <button
                    onClick={() => {
                      setIsSOXModalOpen(true);
                    }}
                    className="text-xs text-sentinel-accent-primary hover:underline"
                    title="View SOX Compliance Details"
                  >
                    View Details
                  </button>
                </div>
              )}
              <button
                onClick={() => {
                  fetchKPIs();
                  fetchLedgerEntries();
                  fetchSOXCompliance();
                }}
                className="px-4 py-2 bg-sentinel-bg-secondary border border-sentinel-border rounded-lg text-sentinel-text-primary hover:bg-sentinel-bg-tertiary transition-colors flex items-center gap-2"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                </svg>
                Refresh
              </button>
            </div>
          </div>

          {/* KPI Cards */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
            {loadingKPIs ? (
              Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="bg-sentinel-bg-secondary border border-sentinel-border rounded-lg p-6">
                  <LoadingSpinner />
                </div>
              ))
            ) : errorKPIs ? (
              <div className="col-span-4">
                <ErrorMessage message={errorKPIs} onRetry={fetchKPIs} />
              </div>
            ) : kpis ? (
              <>
                <div className="bg-sentinel-bg-secondary border border-sentinel-border rounded-lg p-6 hover:border-sentinel-accent-primary/50 transition-colors">
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-sentinel-text-secondary text-sm">Total Assets</p>
                    <svg className="w-5 h-5 text-sentinel-accent-success" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                  </div>
                  <p className="text-3xl font-bold text-sentinel-text-primary mb-1">
                    {formatCurrency(kpis.totalAssets)}
                  </p>
                  <p className="text-sentinel-text-muted text-xs">All positive balances</p>
                </div>

                <div className="bg-sentinel-bg-secondary border border-sentinel-border rounded-lg p-6 hover:border-sentinel-accent-warning/50 transition-colors">
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-sentinel-text-secondary text-sm">Total Liabilities</p>
                    <svg className="w-5 h-5 text-sentinel-accent-warning" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                  </div>
                  <p className="text-3xl font-bold text-sentinel-text-primary mb-1">
                    {formatCurrency(kpis.totalLiabilities)}
                  </p>
                  <p className="text-sentinel-text-muted text-xs">All negative balances</p>
                </div>

                <div className={`bg-sentinel-bg-secondary border rounded-lg p-6 transition-colors ${
                  kpis.netWorth >= 0 
                    ? 'border-sentinel-accent-success/50 hover:border-sentinel-accent-success' 
                    : 'border-sentinel-accent-danger/50 hover:border-sentinel-accent-danger'
                }`}>
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-sentinel-text-secondary text-sm">Net Worth</p>
                    <svg className={`w-5 h-5 ${kpis.netWorth >= 0 ? 'text-sentinel-accent-success' : 'text-sentinel-accent-danger'}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
                    </svg>
                  </div>
                  <p className={`text-3xl font-bold mb-1 ${
                    kpis.netWorth >= 0 ? 'text-sentinel-accent-success' : 'text-sentinel-accent-danger'
                  }`}>
                    {formatCurrency(kpis.netWorth)}
                  </p>
                  <p className="text-sentinel-text-muted text-xs">Assets - Liabilities</p>
                </div>

                <div className="bg-sentinel-bg-secondary border border-sentinel-border rounded-lg p-6 hover:border-sentinel-accent-primary/50 transition-colors">
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-sentinel-text-secondary text-sm">Active Accounts</p>
                    <svg className="w-5 h-5 text-sentinel-accent-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
                    </svg>
                  </div>
                  <p className="text-3xl font-bold text-sentinel-text-primary mb-1">
                    {kpis.activeAccounts.toLocaleString()}
                  </p>
                  <p className="text-sentinel-text-muted text-xs">Accounts with activity</p>
                </div>
              </>
            ) : null}
          </div>

          {/* Accounting Grid */}
          <div className="bg-sentinel-bg-secondary border border-sentinel-border rounded-lg mb-6">
            <div className="p-4 border-b border-sentinel-border flex justify-between items-center">
              <h2 className="text-xl font-semibold text-sentinel-text-primary">
                Accounting Grid
              </h2>
              <div className="flex gap-2">
                <button
                  onClick={exportCSV}
                  disabled={loadingEntries || ledgerEntries.length === 0}
                  className="px-4 py-2 bg-sentinel-bg-tertiary border border-sentinel-border rounded text-sentinel-text-primary hover:bg-sentinel-bg-primary transition-colors text-sm disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
                >
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                  </svg>
                  Export CSV
                </button>
                <select
                  value={pageSize}
                  onChange={(e) => {
                    setPageSize(Number(e.target.value));
                    setCurrentPage(0);
                  }}
                  className="px-3 py-2 bg-sentinel-bg-tertiary border border-sentinel-border rounded text-sentinel-text-primary text-sm"
                >
                  <option value={20}>20 per page</option>
                  <option value={50}>50 per page</option>
                  <option value={100}>100 per page</option>
                </select>
              </div>
            </div>

            {loadingEntries ? (
              <LoadingSpinner />
            ) : errorEntries ? (
              <div className="p-4">
                <ErrorMessage message={errorEntries} onRetry={fetchLedgerEntries} />
              </div>
            ) : ledgerEntries.length === 0 ? (
              <div className="p-8 text-center text-sentinel-text-muted">
                <svg className="w-16 h-16 mx-auto mb-4 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                </svg>
                <p className="text-lg mb-2">No ledger entries found</p>
                <p className="text-sm">Start processing transactions to see ledger entries here</p>
              </div>
            ) : (
              <>
                <div className="overflow-x-auto">
                  <table className="w-full">
                    <thead>
                      <tr className="border-b border-sentinel-border bg-sentinel-bg-tertiary/50">
                        <th className="px-4 py-3 text-left text-sm font-semibold text-sentinel-text-secondary">Transaction ID</th>
                        <th className="px-4 py-3 text-left text-sm font-semibold text-sentinel-text-secondary">Debit Account</th>
                        <th className="px-4 py-3 text-left text-sm font-semibold text-sentinel-text-secondary">Credit Account</th>
                        <th className="px-4 py-3 text-right text-sm font-semibold text-sentinel-text-secondary">Amount</th>
                        <th className="px-4 py-3 text-right text-sm font-semibold text-sentinel-text-secondary">Running Balance</th>
                        <th className="px-4 py-3 text-left text-sm font-semibold text-sentinel-text-secondary">Timestamp</th>
                        <th className="px-4 py-3 text-left text-sm font-semibold text-sentinel-text-secondary">Status</th>
                        <th className="px-4 py-3 text-center text-sm font-semibold text-sentinel-text-secondary">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {ledgerEntries.map((entry, index) => (
                        <tr
                          key={entry.id}
                          onClick={() => {
                            setSelectedEntry(entry);
                            setSelectedAccount(entry.accountId);
                            setIsTAccountModalOpen(true);
                          }}
                          className={`border-b border-sentinel-border hover:bg-sentinel-bg-tertiary/50 transition-colors cursor-pointer ${
                            index % 2 === 0 ? 'bg-sentinel-bg-secondary/30' : ''
                          }`}
                        >
                          <td className="px-4 py-3 text-sm font-mono text-sentinel-accent-primary">
                            {entry.transactionId.substring(0, 8)}...
                          </td>
                          <td className="px-4 py-3 text-sm text-sentinel-text-secondary font-mono">
                            {entry.debitAccount}
                          </td>
                          <td className="px-4 py-3 text-sm text-sentinel-text-secondary font-mono">
                            {entry.creditAccount}
                          </td>
                          <td className={`px-4 py-3 text-sm text-right font-mono font-semibold ${
                            entry.entryType === 'DEBIT' ? 'text-sentinel-accent-danger' : 'text-sentinel-accent-success'
                          }`}>
                            {entry.entryType === 'DEBIT' ? '−' : '+'}
                            {formatCurrency(entry.amount, entry.currency)}
                          </td>
                          <td className="px-4 py-3 text-sm text-right font-mono text-sentinel-text-primary font-semibold">
                            {formatCurrency(entry.runningBalance, entry.currency)}
                          </td>
                          <td className="px-4 py-3 text-sm text-sentinel-text-secondary">
                            {formatDate(entry.timestamp)}
                          </td>
                          <td className="px-4 py-3">
                            <span className={`px-2 py-1 rounded text-xs border ${getStatusBg(entry.complianceStatus)} ${getStatusColor(entry.complianceStatus)}`}>
                              {entry.complianceStatus}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-center">
                            <button
                              onClick={(e) => {
                                e.stopPropagation(); // Prevent row click
                                verifyAuditChain('transfer', entry.transferId);
                              }}
                              disabled={verifyingChain}
                              className="px-3 py-1 text-xs bg-sentinel-accent-primary/20 text-sentinel-accent-primary border border-sentinel-accent-primary/30 rounded hover:bg-sentinel-accent-primary/30 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                            >
                              {verifyingChain ? 'Verifying...' : 'Verify Chain'}
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* Pagination */}
                <div className="p-4 border-t border-sentinel-border flex justify-between items-center bg-sentinel-bg-tertiary/30">
                  <p className="text-sm text-sentinel-text-secondary">
                    Showing {ledgerEntries.length} of {totalElements} entries
                  </p>
                  <div className="flex gap-2">
                    <button
                      onClick={() => setCurrentPage(Math.max(0, currentPage - 1))}
                      disabled={currentPage === 0 || loadingEntries}
                      className="px-4 py-2 bg-sentinel-bg-tertiary border border-sentinel-border rounded text-sentinel-text-primary hover:bg-sentinel-bg-primary transition-colors disabled:opacity-50 disabled:cursor-not-allowed text-sm"
                    >
                      Previous
                    </button>
                    <span className="px-4 py-2 text-sentinel-text-secondary text-sm flex items-center">
                      Page {currentPage + 1} of {Math.max(1, totalPages)}
                    </span>
                    <button
                      onClick={() => setCurrentPage(Math.min(totalPages - 1, currentPage + 1))}
                      disabled={currentPage >= totalPages - 1 || loadingEntries}
                      className="px-4 py-2 bg-sentinel-bg-tertiary border border-sentinel-border rounded text-sentinel-text-primary hover:bg-sentinel-bg-primary transition-colors disabled:opacity-50 disabled:cursor-not-allowed text-sm"
                    >
                      Next
                    </button>
                  </div>
                </div>
              </>
            )}
          </div>

          {/* T-Account Visualizer Modal */}
          {isTAccountModalOpen && (
            <div className="fixed inset-0 z-50 flex">
              {/* Backdrop */}
              <div 
                className="fixed inset-0 bg-black/50"
                onClick={() => {
                  setIsTAccountModalOpen(false);
                  setSelectedEntry(null);
                }}
              ></div>
              
              {/* Modal Panel - slides in from right */}
              <div className="fixed right-0 top-0 h-full w-full max-w-3xl bg-sentinel-bg-primary border-l border-sentinel-border shadow-2xl overflow-y-auto transform transition-transform duration-300 ease-out">
                <div className="sticky top-0 bg-sentinel-bg-secondary border-b border-sentinel-border p-4 flex items-center justify-between z-10">
                  <div>
                    <h2 className="text-xl font-semibold text-sentinel-text-primary">
                      T-Account Visualizer
                    </h2>
                    {selectedEntry && (
                      <p className="text-sm text-sentinel-text-secondary mt-1">
                        {selectedEntry.transactionId} • {selectedEntry.accountIban} - {selectedEntry.accountOwner}
                      </p>
                    )}
                  </div>
                  <button
                    onClick={() => {
                      setIsTAccountModalOpen(false);
                      setSelectedEntry(null);
                    }}
                    className="p-2 hover:bg-sentinel-bg-tertiary rounded transition-colors"
                  >
                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>

                <div className="p-6 space-y-6">
                  {/* Transaction Info */}
                  {selectedEntry && (
                    <div className="bg-sentinel-bg-secondary border border-sentinel-border rounded-lg p-4">
                      <h3 className="text-sm font-semibold text-sentinel-text-primary mb-3">Transaction Information</h3>
                      <div className="grid grid-cols-2 gap-4 text-sm">
                        <div>
                          <span className="text-sentinel-text-secondary">Transaction ID:</span>
                          <p className="text-sentinel-text-primary font-mono">{selectedEntry.transactionId}</p>
                        </div>
                        <div>
                          <span className="text-sentinel-text-secondary">Type:</span>
                          <p className={`font-semibold ${selectedEntry.entryType === 'DEBIT' ? 'text-sentinel-accent-danger' : 'text-sentinel-accent-success'}`}>
                            {selectedEntry.entryType}
                          </p>
                        </div>
                        <div>
                          <span className="text-sentinel-text-secondary">Amount:</span>
                          <p className="text-sentinel-text-primary font-semibold">
                            {formatCurrency(selectedEntry.amount, selectedEntry.currency)}
                          </p>
                        </div>
                        <div>
                          <span className="text-sentinel-text-secondary">Status:</span>
                          <span className={`px-2 py-1 rounded text-xs border ${getStatusBg(selectedEntry.complianceStatus)} ${getStatusColor(selectedEntry.complianceStatus)}`}>
                            {selectedEntry.complianceStatus}
                          </span>
                        </div>
                        <div>
                          <span className="text-sentinel-text-secondary">Debit Account:</span>
                          <p className="text-sentinel-text-primary font-mono text-xs">{selectedEntry.debitAccount}</p>
                        </div>
                        <div>
                          <span className="text-sentinel-text-secondary">Credit Account:</span>
                          <p className="text-sentinel-text-primary font-mono text-xs">{selectedEntry.creditAccount}</p>
                        </div>
                        <div>
                          <span className="text-sentinel-text-secondary">Timestamp:</span>
                          <p className="text-sentinel-text-primary">{formatDate(selectedEntry.timestamp)}</p>
                        </div>
                        <div>
                          <span className="text-sentinel-text-secondary">Running Balance:</span>
                          <p className="text-sentinel-text-primary font-semibold">
                            {formatCurrency(selectedEntry.runningBalance, selectedEntry.currency)}
                          </p>
                        </div>
                      </div>
                      <div className="mt-4 pt-4 border-t border-sentinel-border">
                        <button
                          onClick={() => verifyAuditChain('transfer', selectedEntry.transferId)}
                          disabled={verifyingChain}
                          className="px-4 py-2 bg-sentinel-accent-primary/20 text-sentinel-accent-primary border border-sentinel-accent-primary/30 rounded hover:bg-sentinel-accent-primary/30 transition-colors disabled:opacity-50 disabled:cursor-not-allowed text-sm"
                        >
                          {verifyingChain ? 'Verifying...' : 'Verify Audit Chain'}
                        </button>
                      </div>
                    </div>
                  )}

                  {/* T-Account Visualizer */}
                  <div className="bg-sentinel-bg-secondary border border-sentinel-border rounded-lg">
                    <div className="p-4 border-b border-sentinel-border">
                      <h3 className="text-lg font-semibold text-sentinel-text-primary">
                        T-Account Visualizer
                      </h3>
                      {selectedEntry && (
                        <p className="text-sm text-sentinel-text-secondary mt-1">
                          Account: {selectedEntry.accountIban} - {selectedEntry.accountOwner}
                        </p>
                      )}
                    </div>

                    {loadingTAccount ? (
                      <div className="p-8">
                        <LoadingSpinner />
                      </div>
                    ) : tAccountData ? (
                      <div className="p-4">
                        <div className="mb-4 p-4 bg-sentinel-bg-tertiary rounded-lg border border-sentinel-border">
                          <div className="flex items-center justify-between mb-2">
                            <p className="text-sm font-semibold text-sentinel-text-primary">Account Details</p>
                            <span className={`px-2 py-1 rounded text-xs ${
                              Math.abs(tAccountData.accountBalance - tAccountData.calculatedBalance) < 0.01
                                ? 'bg-sentinel-accent-success/20 text-sentinel-accent-success'
                                : 'bg-sentinel-accent-danger/20 text-sentinel-accent-danger'
                            }`}>
                              {Math.abs(tAccountData.accountBalance - tAccountData.calculatedBalance) < 0.01 ? 'Balanced' : 'Mismatch'}
                            </span>
                          </div>
                          <p className="text-xs text-sentinel-text-secondary mb-1">IBAN: <span className="font-mono">{tAccountData.iban}</span></p>
                          <p className="text-xs text-sentinel-text-secondary mb-1">Owner: {tAccountData.ownerName}</p>
                          <div className="flex justify-between mt-2 pt-2 border-t border-sentinel-border">
                            <span className="text-xs text-sentinel-text-secondary">Account Balance:</span>
                            <span className="text-sm font-semibold text-sentinel-text-primary">{formatCurrency(tAccountData.accountBalance, tAccountData.currency)}</span>
                          </div>
                          <div className="flex justify-between">
                            <span className="text-xs text-sentinel-text-secondary">Calculated Balance:</span>
                            <span className="text-sm font-semibold text-sentinel-text-primary">{formatCurrency(tAccountData.calculatedBalance, tAccountData.currency)}</span>
                          </div>
                        </div>

                        <div className="grid grid-cols-2 gap-4">
                          {/* Debits */}
                          <div>
                            <h4 className="text-sm font-semibold text-sentinel-accent-danger mb-2 flex items-center gap-2">
                              <span>DEBITS</span>
                              <span className="text-xs bg-sentinel-accent-danger/20 text-sentinel-accent-danger px-2 py-0.5 rounded">
                                {tAccountData.debits.length}
                              </span>
                            </h4>
                            <div className="space-y-2 max-h-96 overflow-y-auto">
                              {tAccountData.debits.length === 0 ? (
                                <p className="text-sentinel-text-muted text-xs text-center py-4">No debits</p>
                              ) : (
                                tAccountData.debits.map((entry) => (
                                  <div
                                    key={entry.entryId}
                                    className="p-2 bg-sentinel-bg-tertiary rounded border border-sentinel-accent-danger/20 text-xs"
                                  >
                                    <p className="text-sentinel-accent-danger font-mono font-semibold">
                                      −{formatCurrency(entry.amount, tAccountData.currency)}
                                    </p>
                                    <p className="text-sentinel-text-muted text-xs mt-1">
                                      {formatDate(entry.timestamp)}
                                    </p>
                                    <p className="text-sentinel-text-secondary text-xs mt-1">
                                      Balance: <span className="font-mono">{formatCurrency(entry.runningBalance, tAccountData.currency)}</span>
                                    </p>
                                  </div>
                                ))
                              )}
                            </div>
                          </div>

                          {/* Credits */}
                          <div>
                            <h4 className="text-sm font-semibold text-sentinel-accent-success mb-2 flex items-center gap-2">
                              <span>CREDITS</span>
                              <span className="text-xs bg-sentinel-accent-success/20 text-sentinel-accent-success px-2 py-0.5 rounded">
                                {tAccountData.credits.length}
                              </span>
                            </h4>
                            <div className="space-y-2 max-h-96 overflow-y-auto">
                              {tAccountData.credits.length === 0 ? (
                                <p className="text-sentinel-text-muted text-xs text-center py-4">No credits</p>
                              ) : (
                                tAccountData.credits.map((entry) => (
                                  <div
                                    key={entry.entryId}
                                    className="p-2 bg-sentinel-bg-tertiary rounded border border-sentinel-accent-success/20 text-xs"
                                  >
                                    <p className="text-sentinel-accent-success font-mono font-semibold">
                                      +{formatCurrency(entry.amount, tAccountData.currency)}
                                    </p>
                                    <p className="text-sentinel-text-muted text-xs mt-1">
                                      {formatDate(entry.timestamp)}
                                    </p>
                                    <p className="text-sentinel-text-secondary text-xs mt-1">
                                      Balance: <span className="font-mono">{formatCurrency(entry.runningBalance, tAccountData.currency)}</span>
                                    </p>
                                  </div>
                                ))
                              )}
                            </div>
                          </div>
                        </div>
                      </div>
                    ) : (
                      <div className="p-8 text-center text-sentinel-text-muted">
                        <svg className="w-16 h-16 mx-auto mb-4 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                        </svg>
                        <p className="text-sm">No account data available</p>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* SOX Compliance Modal */}
          {isSOXModalOpen && (
            <div className="fixed inset-0 z-50 flex">
              {/* Backdrop */}
              <div 
                className="fixed inset-0 bg-black/50"
                onClick={() => setIsSOXModalOpen(false)}
              ></div>
              
              {/* Modal Panel - slides in from right */}
              <div className="fixed right-0 top-0 h-full w-full max-w-3xl bg-sentinel-bg-primary border-l border-sentinel-border shadow-2xl overflow-y-auto transform transition-transform duration-300 ease-out">
                <div className="sticky top-0 bg-sentinel-bg-secondary border-b border-sentinel-border p-4 flex items-center justify-between z-10">
                  <div>
                    <h2 className="text-xl font-semibold text-sentinel-text-primary">
                      SOX Compliance Dashboard
                    </h2>
                    <p className="text-sm text-sentinel-text-secondary mt-1">
                      Real-time compliance monitoring and audit integrity
                    </p>
                  </div>
                  <button
                    onClick={() => setIsSOXModalOpen(false)}
                    className="p-2 hover:bg-sentinel-bg-tertiary rounded transition-colors"
                  >
                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>

                <div className="p-6 space-y-6">
                  {loadingSOX ? (
                    <div className="p-8">
                      <LoadingSpinner />
                    </div>
                  ) : errorSOX ? (
                    <div className="p-4">
                      <ErrorMessage message={errorSOX} onRetry={fetchSOXCompliance} />
                    </div>
                  ) : soxCompliance ? (
                    <>
                      {/* Integrity Status */}
                      <div className="bg-sentinel-bg-secondary border border-sentinel-border rounded-lg p-4">
                        <div className="flex items-center justify-between mb-3">
                          <h3 className="text-lg font-semibold text-sentinel-text-primary">
                            Audit Integrity Status
                          </h3>
                          <div className={`w-3 h-3 rounded-full ${
                            !soxCompliance.integrityStatus.hasBreaches
                              ? 'bg-sentinel-accent-success animate-pulse'
                              : 'bg-sentinel-accent-danger animate-pulse'
                          }`}></div>
                        </div>
                        <div className="space-y-2 text-sm">
                          <div className="flex justify-between">
                            <span className="text-sentinel-text-secondary">Total Verifications:</span>
                            <span className="text-sentinel-text-primary font-mono">
                              {soxCompliance.integrityStatus.totalVerifications.toLocaleString()}
                            </span>
                          </div>
                          <div className="flex justify-between">
                            <span className="text-sentinel-text-secondary">Total Breaches:</span>
                            <span className={`font-mono font-semibold ${
                              soxCompliance.integrityStatus.totalBreaches > 0
                                ? 'text-sentinel-accent-danger'
                                : 'text-sentinel-accent-success'
                            }`}>
                              {soxCompliance.integrityStatus.totalBreaches}
                            </span>
                          </div>
                          <div className="flex justify-between items-center mt-3 pt-2 border-t border-sentinel-border">
                            <span className="text-sentinel-text-secondary">Status:</span>
                            <span className={`px-3 py-1 rounded text-xs font-semibold ${
                              soxCompliance.integrityStatus.hasBreaches
                                ? 'bg-sentinel-accent-danger/20 text-sentinel-accent-danger border border-sentinel-accent-danger/30'
                                : 'bg-sentinel-accent-success/20 text-sentinel-accent-success border border-sentinel-accent-success/30'
                            }`}>
                              {soxCompliance.integrityStatus.hasBreaches ? '✗ BREACH DETECTED' : '✓ INTEGRITY INTACT'}
                            </span>
                          </div>
                        </div>
                      </div>

                      {/* Compliance Report */}
                      <div className="bg-sentinel-bg-secondary border border-sentinel-border rounded-lg p-4">
                        <h3 className="text-lg font-semibold text-sentinel-text-primary mb-3">
                          SOX Section 404 Compliance
                        </h3>
                        <div className="space-y-2 text-sm">
                          <div className="flex justify-between">
                            <span className="text-sentinel-text-secondary">Date:</span>
                            <span className="text-sentinel-text-primary font-mono">
                              {new Date(soxCompliance.complianceReport.date).toLocaleDateString()}
                            </span>
                          </div>
                          <div className="flex justify-between">
                            <span className="text-sentinel-text-secondary">Daily Audit Entries:</span>
                            <span className="text-sentinel-text-primary font-mono">
                              {soxCompliance.complianceReport.dailyAuditEntries.toLocaleString()}
                            </span>
                          </div>
                          <div className="flex justify-between items-center mt-3 pt-2 border-t border-sentinel-border">
                            <span className="text-sentinel-text-secondary">Compliance Status:</span>
                            <span className={`px-3 py-1 rounded text-xs font-semibold ${
                              soxCompliance.complianceReport.complianceStatus === 'COMPLIANT'
                                ? 'bg-sentinel-accent-success/20 text-sentinel-accent-success border border-sentinel-accent-success/30'
                                : 'bg-sentinel-accent-danger/20 text-sentinel-accent-danger border border-sentinel-accent-danger/30'
                            }`}>
                              {soxCompliance.complianceReport.complianceStatus === 'COMPLIANT' ? '✓ COMPLIANT' : '✗ NON-COMPLIANT'}
                            </span>
                          </div>
                        </div>
                      </div>

                      {/* System Health */}
                      <div className="bg-sentinel-bg-secondary border border-sentinel-border rounded-lg p-4">
                        <h3 className="text-lg font-semibold text-sentinel-text-primary mb-3">
                          System Health
                        </h3>
                        <div className="space-y-2 text-sm">
                          <div className="flex justify-between items-center">
                            <span className="text-sentinel-text-secondary">Health Status:</span>
                            <span className={`px-3 py-1 rounded text-xs font-semibold ${
                              soxCompliance.systemHealth.healthStatus === 'HEALTHY'
                                ? 'bg-sentinel-accent-success/20 text-sentinel-accent-success border border-sentinel-accent-success/30'
                                : 'bg-sentinel-accent-warning/20 text-sentinel-accent-warning border border-sentinel-accent-warning/30'
                            }`}>
                              {soxCompliance.systemHealth.healthStatus}
                            </span>
                          </div>
                          <div className="flex justify-between items-center mt-3 pt-2 border-t border-sentinel-border">
                            <span className="text-sentinel-text-secondary">Integrity:</span>
                            <span className={`px-3 py-1 rounded text-xs font-semibold ${
                              soxCompliance.systemHealth.integrityIntact
                                ? 'bg-sentinel-accent-success/20 text-sentinel-accent-success border border-sentinel-accent-success/30'
                                : 'bg-sentinel-accent-danger/20 text-sentinel-accent-danger border border-sentinel-accent-danger/30'
                            }`}>
                              {soxCompliance.systemHealth.integrityIntact ? '✓ INTACT' : '✗ COMPROMISED'}
                            </span>
                          </div>
                        </div>
                      </div>
                    </>
                  ) : null}
                </div>
              </div>
            </div>
          )}

        </div>
      </div>
    </ProtectedRoute>
  );
}
