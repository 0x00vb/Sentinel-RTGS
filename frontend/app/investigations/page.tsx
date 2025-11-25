'use client';

import ProtectedRoute from '../components/ProtectedRoute';

export default function InvestigationsPage() {
  return (
    <ProtectedRoute>
      <div className="p-6">
        <div className="mb-6">
          <h1 className="text-2xl font-semibold text-sentinel-text-primary mb-2">
            Investigations
          </h1>
          <p className="text-sentinel-text-secondary">
            AML compliance workbench and decision-making interface
          </p>
        </div>
        <div className="bg-sentinel-bg-secondary border border-sentinel-border rounded-lg p-8 text-center">
          <p className="text-sentinel-text-muted">Investigations interface will be implemented in Phase 2</p>
        </div>
      </div>
    </ProtectedRoute>
  );
}
