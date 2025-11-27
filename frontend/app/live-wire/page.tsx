'use client';

import ProtectedRoute from '../components/ProtectedRoute';
import { useState, useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

// Backend DTO shape
interface TransferEvent {
  id: number;
  msgId: string;
  status: 'PENDING' | 'CLEARED' | 'BLOCKED_AML' | 'REJECTED';
  amount: number;
  sourceIban: string;
  destIban: string;
  timestamp: string;
}

// UI Model
interface Transaction {
  id: string;
  timestamp: string;
  senderBIC: string; // Using this field for IBAN to minimize refactoring churn, or we can rename column
  receiverBIC: string;
  amount: number;
  currency: string;
  status: string;
  pipelineStage: string;
  isNew: boolean;
}

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
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const stompClientRef = useRef<Client | null>(null);

  useEffect(() => {
    // Initialize Stomp Client
    const client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
      debug: (str) => {
        console.log(str);
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    client.onConnect = (frame) => {
      console.log('Connected: ' + frame);
      client.subscribe('/topic/transfers', (message) => {
        if (message.body) {
          const event: TransferEvent = JSON.parse(message.body);
          
          // Map Backend DTO to Frontend Model
          let status = 'processing';
          let pipelineStage = 'ingest';

          switch (event.status) {
            case 'PENDING':
              status = 'processing';
              pipelineStage = 'validate';
              break;
            case 'CLEARED':
              status = 'completed';
              pipelineStage = 'pac002_generated'; // Final stage
              break;
            case 'BLOCKED_AML':
              status = 'blocked';
              pipelineStage = 'risk_check';
              break;
            case 'REJECTED':
              status = 'review_required';
              pipelineStage = 'ledger_entry'; // Failed somewhere
              break;
          }

          const newTransaction: Transaction = {
            id: event.msgId || `TX-${event.id}`, // Prefer UUID, fallback to ID
            timestamp: event.timestamp,
            senderBIC: event.sourceIban, // Displaying IBAN in this column for now
            receiverBIC: event.destIban, // Displaying IBAN in this column for now
            amount: event.amount,
            currency: 'EUR', // Defaulting to EUR as currency isn't in event yet
            status: status,
            pipelineStage: pipelineStage,
            isNew: true
          };

          setTransactions(prev => {
            // Keep list from growing indefinitely, cap at 200
            // Handle case where prev might be undefined (defensive programming)
            const prevList = prev || [];
            const updated = [newTransaction, ...prevList];
            return updated.slice(0, 200);
          });
          
          // Remove 'isNew' flag after animation (e.g., 3 seconds)
          // This is a simplified approach; strictly in React we might need a separate state or effect
          // but for a high-frequency feed, just letting it be 'isNew' until it scrolls off or is replaced is often fine.
          // However, to stop the pulsing, we'd need to update the state again.
          // For now, let's leave it pulsing as "new" until it's pushed down.
          setTimeout(() => {
             setTransactions(currentList => {
               // Handle case where currentList might be undefined (defensive programming)
               const list = currentList || [];
               return list.map(t => t.id === newTransaction.id ? { ...t, isNew: false } : t);
             });
          }, 3000);
        }
      });
    };

    client.onStompError = (frame) => {
      console.error('Broker reported error: ' + frame.headers['message']);
      console.error('Additional details: ' + frame.body);
    };

    client.activate();
    stompClientRef.current = client;

    return () => {
      if (stompClientRef.current) {
        stompClientRef.current.deactivate();
      }
    };
  }, []);

  // Calculate pagination
  const totalTransactions = transactions?.length || 0;
  const totalPages = Math.ceil(totalTransactions / pageSize);
  const startIndex = (currentPage - 1) * pageSize;
  const endIndex = startIndex + pageSize;
  const currentTransactions = transactions?.slice(startIndex, endIndex) || [];

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
                    Transaction ID
                  </th>
                  <th className="text-left px-6 py-3 text-xs font-medium text-sentinel-text-secondary uppercase tracking-wider">
                    Sender IBAN
                  </th>
                  <th className="text-left px-6 py-3 text-xs font-medium text-sentinel-text-secondary uppercase tracking-wider">
                    Receiver IBAN
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
