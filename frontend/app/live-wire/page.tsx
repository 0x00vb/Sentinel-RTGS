'use client';

import ProtectedRoute from '../components/ProtectedRoute';
import { useState } from 'react';

// Mock transaction data - expanded for overflow testing
const mockTransactions = [
  {
    id: 'PAY-2025-001247',
    timestamp: '2025-11-25T14:32:15Z',
    senderBIC: 'CHASUS33',
    receiverBIC: 'DEUTDEFF',
    amount: 2500000.00,
    currency: 'USD',
    status: 'completed',
    pipelineStage: 'pac002_generated',
    isNew: true
  },
  {
    id: 'PAY-2025-001246',
    timestamp: '2025-11-25T14:31:42Z',
    senderBIC: 'HSBCGB2L',
    receiverBIC: 'BNPAFRPP',
    amount: 150000.00,
    currency: 'EUR',
    status: 'review_required',
    pipelineStage: 'risk_check',
    isNew: false
  },
  {
    id: 'PAY-2025-001245',
    timestamp: '2025-11-25T14:31:18Z',
    senderBIC: 'RABONL2U',
    receiverBIC: 'CITIUS33',
    amount: 750000.00,
    currency: 'USD',
    status: 'processing',
    pipelineStage: 'ledger_entry',
    isNew: false
  },
  {
    id: 'PAY-2025-001244',
    timestamp: '2025-11-25T14:30:57Z',
    senderBIC: 'UBSWCHZH',
    receiverBIC: 'SCBLUS33',
    amount: 5000000.00,
    currency: 'CHF',
    status: 'blocked',
    pipelineStage: 'risk_check',
    isNew: false
  },
  {
    id: 'PAY-2025-001243',
    timestamp: '2025-11-25T14:30:33Z',
    senderBIC: 'NWBKGB2L',
    receiverBIC: 'INGBNL2A',
    amount: 1250000.00,
    currency: 'GBP',
    status: 'completed',
    pipelineStage: 'pac002_generated',
    isNew: false
  },
  {
    id: 'PAY-2025-001242',
    timestamp: '2025-11-25T14:29:45Z',
    senderBIC: 'BKCHCNBJ',
    receiverBIC: 'HSBCJPJT',
    amount: 3000000.00,
    currency: 'CNY',
    status: 'processing',
    pipelineStage: 'validation',
    isNew: false
  },
  {
    id: 'PAY-2025-001241',
    timestamp: '2025-11-25T14:29:12Z',
    senderBIC: 'CRESCHZZ',
    receiverBIC: 'ABNABE33',
    amount: 890000.00,
    currency: 'EUR',
    status: 'completed',
    pipelineStage: 'pac002_generated',
    isNew: false
  },
  {
    id: 'PAY-2025-001240',
    timestamp: '2025-11-25T14:28:33Z',
    senderBIC: 'BOFAUS3N',
    receiverBIC: 'BARCAESMM',
    amount: 4200000.00,
    currency: 'USD',
    status: 'processing',
    pipelineStage: 'ledger_entry',
    isNew: false
  },
  // Additional transactions for overflow testing
  {
    id: 'PAY-2025-001239',
    timestamp: '2025-11-25T14:28:01Z',
    senderBIC: 'SANFFEFF',
    receiverBIC: 'UNCRITMM',
    amount: 950000.00,
    currency: 'EUR',
    status: 'completed',
    pipelineStage: 'pac002_generated',
    isNew: false
  },
  {
    id: 'PAY-2025-001238',
    timestamp: '2025-11-25T14:27:28Z',
    senderBIC: 'NDEAFIHH',
    receiverBIC: 'DABADKKK',
    amount: 1800000.00,
    currency: 'DKK',
    status: 'processing',
    pipelineStage: 'ledger_entry',
    isNew: false
  },
  {
    id: 'PAY-2025-001237',
    timestamp: '2025-11-25T14:26:55Z',
    senderBIC: 'ESSESESS',
    receiverBIC: 'SBININBB',
    amount: 2750000.00,
    currency: 'INR',
    status: 'review_required',
    pipelineStage: 'risk_check',
    isNew: false
  },
  {
    id: 'PAY-2025-001236',
    timestamp: '2025-11-25T14:26:22Z',
    senderBIC: 'MHCBJPJT',
    receiverBIC: 'ICBKCNBJ',
    amount: 4500000.00,
    currency: 'JPY',
    status: 'blocked',
    pipelineStage: 'risk_check',
    isNew: false
  },
  {
    id: 'PAY-2025-001235',
    timestamp: '2025-11-25T14:25:49Z',
    senderBIC: 'RBOSGB2L',
    receiverBIC: 'ANZBAU3M',
    amount: 3200000.00,
    currency: 'AUD',
    status: 'processing',
    pipelineStage: 'validation',
    isNew: false
  },
  {
    id: 'PAY-2025-001234',
    timestamp: '2025-11-25T14:25:16Z',
    senderBIC: 'TDOMCAT1',
    receiverBIC: 'BMOCCAT2',
    amount: 680000.00,
    currency: 'CAD',
    status: 'completed',
    pipelineStage: 'pac002_generated',
    isNew: false
  },
  {
    id: 'PAY-2025-001233',
    timestamp: '2025-11-25T14:24:43Z',
    senderBIC: 'BARCGB22',
    receiverBIC: 'LOYDGB2L',
    amount: 1100000.00,
    currency: 'GBP',
    status: 'processing',
    pipelineStage: 'ledger_entry',
    isNew: false
  },
  {
    id: 'PAY-2025-001232',
    timestamp: '2025-11-25T14:24:10Z',
    senderBIC: 'NORDEA',
    receiverBIC: 'SWEDSESS',
    amount: 2100000.00,
    currency: 'SEK',
    status: 'review_required',
    pipelineStage: 'risk_check',
    isNew: false
  },
  {
    id: 'PAY-2025-001231',
    timestamp: '2025-11-25T14:23:37Z',
    senderBIC: 'PKOPPLPW',
    receiverBIC: 'BREXPLPW',
    amount: 780000.00,
    currency: 'PLN',
    status: 'completed',
    pipelineStage: 'pac002_generated',
    isNew: false
  },
  {
    id: 'PAY-2025-001230',
    timestamp: '2025-11-25T14:23:04Z',
    senderBIC: 'RZOOAT2L',
    receiverBIC: 'BAWAATWW',
    amount: 1450000.00,
    currency: 'EUR',
    status: 'blocked',
    pipelineStage: 'risk_check',
    isNew: false
  },
  {
    id: 'PAY-2025-001229',
    timestamp: '2025-11-25T14:22:31Z',
    senderBIC: 'BNLIIFI1',
    receiverBIC: 'UNCRITMM',
    amount: 3950000.00,
    currency: 'EUR',
    status: 'processing',
    pipelineStage: 'validation',
    isNew: false
  },
  {
    id: 'PAY-2025-001228',
    timestamp: '2025-11-25T14:21:58Z',
    senderBIC: 'SEBALT22',
    receiverBIC: 'LHVBEE22',
    amount: 560000.00,
    currency: 'EUR',
    status: 'completed',
    pipelineStage: 'pac002_generated',
    isNew: false
  },
  {
    id: 'PAY-2025-001227',
    timestamp: '2025-11-25T14:21:25Z',
    senderBIC: 'SPDBCNBS',
    receiverBIC: 'CMBCCNBS',
    amount: 8900000.00,
    currency: 'CNY',
    status: 'processing',
    pipelineStage: 'ledger_entry',
    isNew: false
  },
  {
    id: 'PAY-2025-001226',
    timestamp: '2025-11-25T14:20:52Z',
    senderBIC: 'BKTRTRIS',
    receiverBIC: 'TGBATRIS',
    amount: 2300000.00,
    currency: 'TRY',
    status: 'review_required',
    pipelineStage: 'risk_check',
    isNew: false
  },
  {
    id: 'PAY-2025-001225',
    timestamp: '2025-11-25T14:20:19Z',
    senderBIC: 'NBADAEAA',
    receiverBIC: 'SABBAEAA',
    amount: 1200000.00,
    currency: 'AED',
    status: 'blocked',
    pipelineStage: 'risk_check',
    isNew: false
  },
  {
    id: 'PAY-2025-001224',
    timestamp: '2025-11-25T14:19:46Z',
    senderBIC: 'RBOSGGSX',
    receiverBIC: 'GSCMGGSP',
    amount: 3400000.00,
    currency: 'GGP',
    status: 'processing',
    pipelineStage: 'validation',
    isNew: false
  }
];

// Pipeline stages in order
const pipelineStages = ['ingest', 'validate', 'risk', 'ledger', 'pac002'];

function PipelineStepper({ currentStage }: { currentStage: string }) {
  const getStageIndex = (stage: string) => pipelineStages.indexOf(stage);
  const currentIndex = getStageIndex(currentStage);

  return (
    <div className="flex items-center space-x-1">
      {pipelineStages.map((stage, index) => {
        let dotClass = 'w-2 h-2 rounded-full ';

        if (index < currentIndex) {
          // Completed stages - green
          dotClass += 'bg-sentinel-accent-success';
        } else if (index === currentIndex) {
          // Current stage - pulsing cyan
          dotClass += 'bg-sentinel-accent-primary animate-pulse';
        } else {
          // Future stages - gray
          dotClass += 'bg-sentinel-text-muted';
        }

        return (
          <div key={stage} className={dotClass} />
        );
      })}
    </div>
  );
}

function StatusPill({ status }: { status: string }) {
  const getStatusConfig = (status: string) => {
    switch (status) {
      case 'completed':
        return {
          text: 'COMPLETED',
          bgColor: 'bg-sentinel-accent-success/20',
          textColor: 'text-sentinel-accent-success',
          borderColor: 'border-sentinel-accent-success/30'
        };
      case 'processing':
        return {
          text: 'PROCESSING',
          bgColor: 'bg-sentinel-accent-primary/20',
          textColor: 'text-sentinel-accent-primary',
          borderColor: 'border-sentinel-accent-primary/30'
        };
      case 'review_required':
        return {
          text: 'REVIEW',
          bgColor: 'bg-sentinel-accent-warning/20',
          textColor: 'text-sentinel-accent-warning',
          borderColor: 'border-sentinel-accent-warning/30'
        };
      case 'blocked':
        return {
          text: 'BLOCKED',
          bgColor: 'bg-sentinel-accent-danger/20',
          textColor: 'text-sentinel-accent-danger',
          borderColor: 'border-sentinel-accent-danger/30'
        };
      default:
        return {
          text: status.toUpperCase(),
          bgColor: 'bg-sentinel-bg-tertiary',
          textColor: 'text-sentinel-text-secondary',
          borderColor: 'border-sentinel-border'
        };
    }
  };

  const config = getStatusConfig(status);

  return (
    <span className={`px-2 py-1 text-xs font-medium rounded-full border ${config.bgColor} ${config.textColor} ${config.borderColor}`}>
      {config.text}
    </span>
  );
}

function PaginationControls({
  currentPage,
  totalPages,
  pageSize,
  totalItems,
  onPageChange,
  onPageSizeChange
}: {
  currentPage: number;
  totalPages: number;
  pageSize: number;
  totalItems: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
}) {
  const startItem = (currentPage - 1) * pageSize + 1;
  const endItem = Math.min(currentPage * pageSize, totalItems);

  const getPageNumbers = () => {
    const pages = [];
    const maxVisiblePages = 5;

    if (totalPages <= maxVisiblePages) {
      for (let i = 1; i <= totalPages; i++) {
        pages.push(i);
      }
    } else {
      if (currentPage <= 3) {
        pages.push(1, 2, 3, 4, 5);
      } else if (currentPage >= totalPages - 2) {
        pages.push(totalPages - 4, totalPages - 3, totalPages - 2, totalPages - 1, totalPages);
      } else {
        pages.push(currentPage - 2, currentPage - 1, currentPage, currentPage + 1, currentPage + 2);
      }
    }

    return pages;
  };

  return (
    <div className="flex items-center justify-between pt-4 border-t border-sentinel-border">
      {/* Page size selector */}
      <div className="flex items-center space-x-2 text-sm text-sentinel-text-secondary">
        <span>Show</span>
        <select
          value={pageSize}
          onChange={(e) => onPageSizeChange(Number(e.target.value))}
          className="bg-sentinel-bg-secondary border border-sentinel-border rounded px-2 py-1 text-sm focus:border-sentinel-accent-primary focus:outline-none"
        >
          <option value={5}>5</option>
          <option value={10}>10</option>
          <option value={15}>15</option>
          <option value={25}>25</option>
        </select>
        <span>per page</span>
      </div>

      {/* Page info */}
      <div className="text-sm text-sentinel-text-secondary">
        Showing {startItem} to {endItem} of {totalItems} transactions
      </div>

      {/* Pagination buttons */}
      <div className="flex items-center space-x-1">
        <button
          onClick={() => onPageChange(currentPage - 1)}
          disabled={currentPage === 1}
          className="px-3 py-1 text-sm border border-sentinel-border rounded hover:bg-sentinel-bg-tertiary disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-transparent transition-colors"
        >
          ‹
        </button>

        {getPageNumbers().map((page) => (
          <button
            key={page}
            onClick={() => onPageChange(page)}
            className={`px-3 py-1 text-sm border rounded transition-colors ${
              page === currentPage
                ? 'bg-sentinel-accent-primary text-sentinel-bg-primary border-sentinel-accent-primary'
                : 'border-sentinel-border hover:bg-sentinel-bg-tertiary'
            }`}
          >
            {page}
          </button>
        ))}

        <button
          onClick={() => onPageChange(currentPage + 1)}
          disabled={currentPage === totalPages}
          className="px-3 py-1 text-sm border border-sentinel-border rounded hover:bg-sentinel-bg-tertiary disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-transparent transition-colors"
        >
          ›
        </button>
      </div>
    </div>
  );
}

export default function LiveWirePage() {
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // Calculate pagination
  const totalTransactions = mockTransactions.length;
  const totalPages = Math.ceil(totalTransactions / pageSize);
  const startIndex = (currentPage - 1) * pageSize;
  const endIndex = startIndex + pageSize;
  const currentTransactions = mockTransactions.slice(startIndex, endIndex);

  const goToPage = (page: number) => {
    setCurrentPage(Math.max(1, Math.min(page, totalPages)));
  };

  const changePageSize = (newPageSize: number) => {
    setPageSize(newPageSize);
    setCurrentPage(1); // Reset to first page when changing page size
  };

  return (
    <ProtectedRoute>
      <div className="p-6 space-y-6">
        <div className="mb-6">
          <h1 className="text-2xl font-semibold text-sentinel-text-primary mb-2">
            Live Wire Stream
          </h1>
          <p className="text-sentinel-text-secondary">
            Real-time pipeline monitor and transaction processing
          </p>
        </div>

        {/* Transaction Table */}
        <div className="bg-sentinel-bg-secondary border border-sentinel-border rounded-lg overflow-hidden">
          {/* Table Header */}
          <div className="border-b border-sentinel-border px-6 py-4">
            <h3 className="text-lg font-medium text-sentinel-text-primary">
              Transaction Pipeline Monitor
            </h3>
          </div>

          {/* Table */}
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-sentinel-bg-tertiary/50">
                <tr>
                  <th className="text-left px-6 py-3 text-xs font-medium text-sentinel-text-secondary uppercase tracking-wider">
                    Timestamp
                  </th>
                  <th className="text-left px-6 py-3 text-xs font-medium text-sentinel-text-secondary uppercase tracking-wider">
                    Payment ID
                  </th>
                  <th className="text-left px-6 py-3 text-xs font-medium text-sentinel-text-secondary uppercase tracking-wider">
                    Sender BIC
                  </th>
                  <th className="text-left px-6 py-3 text-xs font-medium text-sentinel-text-secondary uppercase tracking-wider">
                    Receiver BIC
                  </th>
                  <th className="text-left px-6 py-3 text-xs font-medium text-sentinel-text-secondary uppercase tracking-wider">
                    Amount
                  </th>
                  <th className="text-left px-6 py-3 text-xs font-medium text-sentinel-text-secondary uppercase tracking-wider">
                    Currency
                  </th>
                  <th className="text-left px-6 py-3 text-xs font-medium text-sentinel-text-secondary uppercase tracking-wider">
                    Pipeline Status
                  </th>
                  <th className="text-left px-6 py-3 text-xs font-medium text-sentinel-text-secondary uppercase tracking-wider">
                    Status
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-sentinel-border">
                {currentTransactions.map((transaction, index) => (
                  <tr
                    key={transaction.id}
                    className={`${
                      transaction.isNew
                        ? 'bg-sentinel-accent-primary/10 border-l-4 border-sentinel-accent-primary animate-pulse'
                        : 'hover:bg-sentinel-bg-tertiary/30'
                    } transition-colors`}
                  >
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-mono text-sentinel-text-primary">
                      {new Date(transaction.timestamp).toLocaleTimeString('en-US', {
                        hour12: false,
                        timeZone: 'UTC'
                      })}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-sentinel-text-primary">
                      {transaction.id}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-mono text-sentinel-text-primary">
                      {transaction.senderBIC}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-mono text-sentinel-text-primary">
                      {transaction.receiverBIC}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-sentinel-text-primary">
                      {transaction.amount.toLocaleString('en-US', {
                        minimumFractionDigits: 2,
                        maximumFractionDigits: 2
                      })}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-sentinel-text-secondary">
                      {transaction.currency}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <PipelineStepper currentStage={transaction.pipelineStage} />
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <StatusPill status={transaction.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Table Footer with Legend */}
          <div className="border-t border-sentinel-border px-6 py-4 bg-sentinel-bg-tertiary/30">
            <div className="flex items-center justify-between text-sm text-sentinel-text-muted mb-4">
              <div className="flex items-center space-x-4">
                <span className="flex items-center space-x-2">
                  <div className="w-2 h-2 bg-sentinel-accent-success rounded-full"></div>
                  <span>Completed</span>
                </span>
                <span className="flex items-center space-x-2">
                  <div className="w-2 h-2 bg-sentinel-accent-primary rounded-full animate-pulse"></div>
                  <span>Current</span>
                </span>
                <span className="flex items-center space-x-2">
                  <div className="w-2 h-2 bg-sentinel-text-muted rounded-full"></div>
                  <span>Pending</span>
                </span>
              </div>
            </div>

            {/* Pagination Controls */}
            <PaginationControls
              currentPage={currentPage}
              totalPages={totalPages}
              pageSize={pageSize}
              totalItems={totalTransactions}
              onPageChange={goToPage}
              onPageSizeChange={changePageSize}
            />
          </div>
        </div>
      </div>
    </ProtectedRoute>
  );
}
