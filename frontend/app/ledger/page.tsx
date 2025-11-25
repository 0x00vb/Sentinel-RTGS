'use client';

import ProtectedRoute from '../components/ProtectedRoute';

export default function LedgerPage() {
  return (
    <ProtectedRoute>
      <div className="p-6">
        <div className="mb-6">
          <h1 className="text-2xl font-semibold text-sentinel-text-primary mb-2">
            General Ledger
          </h1>
          <p className="text-sentinel-text-secondary">
            Double-entry accounting and financial record keeping
          </p>
        </div>
        <div className="bg-sentinel-bg-secondary border border-sentinel-border rounded-lg p-8 text-center">
          <p className="text-sentinel-text-muted">General Ledger interface will be implemented in Phase 2</p>
        </div>
      </div>
    </ProtectedRoute>
  );
}
