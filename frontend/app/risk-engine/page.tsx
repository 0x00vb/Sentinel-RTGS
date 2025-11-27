'use client';

import ProtectedRoute from '../components/ProtectedRoute';

export default function RiskEnginePage() {
  return (
    <ProtectedRoute>
      <div className="p-6">
        <div className="mb-6">
          <h1 className="text-2xl font-semibold text-sentinel-text-primary mb-2">
            Risk Engine
          </h1>
          <p className="text-sentinel-text-secondary">
            Rules configuration and automated risk assessment
          </p>
        </div>
        <div className="bg-sentinel-bg-secondary border border-sentinel-border rounded-lg p-8 text-center">
          <p className="text-sentinel-text-muted">Risk Engine interface will be implemented in Phase 2</p>
        </div>
      </div>
    </ProtectedRoute>
  );
}


