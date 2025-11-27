'use client';

import ProtectedRoute from '../components/ProtectedRoute';
import { WebSocketProvider } from '../contexts/WebSocketContext';
import LedgerDashboard from '../components/ledger/LedgerDashboard';

export default function LedgerPage() {
  return (
    <ProtectedRoute>
      <WebSocketProvider>
        <LedgerDashboard />
      </WebSocketProvider>
    </ProtectedRoute>
  );
}
