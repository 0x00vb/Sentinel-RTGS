                                                                                                                                                                              'use client';

import ProtectedRoute from '../components/ProtectedRoute';
import WorldMap from '../components/WorldMap';

export default function DashboardPage() {
  return (
    <ProtectedRoute>
      <div className="p-6 space-y-6">
        <div className="mb-6">
          <h1 className="text-2xl font-semibold text-sentinel-text-primary mb-2">
            Mission Control
          </h1>
          <p className="text-sentinel-text-secondary">
            High-level aggregates and system pulse monitoring
          </p>
        </div>

        {/* Top Section - Metrics Grid and Map */}
        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
          {/* Top Metrics Grid (4 Cards - 2x2) */}
          <div className="lg:col-span-2">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
              {/* Card 1 - Total transfers today */}
              <div className="bg-sentinel-bg-secondary border border-sentinel-border rounded-lg p-6 h-48 flex flex-col justify-between">
                <div>
                  <p className="text-sentinel-text-secondary text-sm mb-2">Total transfers today</p>
                  <p className="text-3xl font-bold text-sentinel-text-primary mb-1">12,450</p>
                  <p className="text-sentinel-text-muted text-xs">Total transfer</p>
                </div>
                {/* Mini line chart placeholder */}
                <div className="h-8 flex items-end space-x-1">
                  <div className="w-1 bg-sentinel-accent-primary opacity-60 h-2"></div>
                  <div className="w-1 bg-sentinel-accent-primary opacity-70 h-3"></div>
                  <div className="w-1 bg-sentinel-accent-primary opacity-50 h-1"></div>
                  <div className="w-1 bg-sentinel-accent-primary opacity-80 h-4"></div>
                  <div className="w-1 bg-sentinel-accent-primary opacity-90 h-5"></div>
                  <div className="w-1 bg-sentinel-accent-primary h-6"></div>
                  <div className="w-1 bg-sentinel-accent-primary opacity-80 h-4"></div>
                  <div className="w-1 bg-sentinel-accent-primary opacity-70 h-3"></div>
                </div>
              </div>

              {/* Card 2 - Pending compliance reviews */}
              <div className="bg-sentinel-bg-secondary border border-sentinel-border rounded-lg p-6 h-48 flex flex-col justify-between">
                <div>
                  <p className="text-sentinel-text-secondary text-sm mb-2">Pending compliance reviews</p>
                  <p className="text-3xl font-bold text-sentinel-text-primary mb-1">15</p>
                  <p className="text-sentinel-text-muted text-xs">Compliance sub</p>
                </div>
                {/* Small red line chart trending upward */}
                <div className="h-8 flex items-end space-x-1">
                  <div className="w-1 bg-sentinel-accent-danger opacity-60 h-1"></div>
                  <div className="w-1 bg-sentinel-accent-danger opacity-70 h-2"></div>
                  <div className="w-1 bg-sentinel-accent-danger opacity-50 h-1"></div>
                  <div className="w-1 bg-sentinel-accent-danger opacity-80 h-3"></div>
                  <div className="w-1 bg-sentinel-accent-danger opacity-90 h-4"></div>
                  <div className="w-1 bg-sentinel-accent-danger h-5"></div>
                  <div className="w-1 bg-sentinel-accent-danger opacity-80 h-6"></div>
                  <div className="w-1 bg-sentinel-accent-danger opacity-70 h-7"></div>
                </div>
              </div>

              {/* Card 3 - RabbitMQ Queue Depth */}
              <div className="bg-sentinel-bg-secondary border border-sentinel-border rounded-lg p-6 h-48 flex flex-col justify-between">
                <div>
                  <p className="text-sentinel-text-secondary text-sm mb-2">RabbitMQ Queue Depth</p>
                  <p className="text-3xl font-bold text-sentinel-text-primary mb-1">4,500</p>
                  <p className="text-sentinel-text-muted text-xs">Risk Engine E2H</p>
                </div>
                {/* Bar chart (cyan/teal) */}
                <div className="h-8 flex items-end space-x-1">
                  <div className="w-2 bg-sentinel-accent-primary opacity-80 h-6"></div>
                  <div className="w-2 bg-sentinel-accent-primary opacity-90 h-7"></div>
                  <div className="w-2 bg-sentinel-accent-primary h-8"></div>
                  <div className="w-2 bg-sentinel-accent-primary opacity-70 h-5"></div>
                  <div className="w-2 bg-sentinel-accent-primary opacity-60 h-4"></div>
                  <div className="w-2 bg-sentinel-accent-primary opacity-85 h-6"></div>
                  <div className="w-2 bg-sentinel-accent-primary opacity-75 h-5"></div>
                  <div className="w-2 bg-sentinel-accent-primary opacity-95 h-7"></div>
                </div>
              </div>

              {/* Card 4 - Average Risk Score (24h) */}
              <div className="bg-sentinel-bg-secondary border border-sentinel-border rounded-lg p-6 h-48 flex flex-col justify-between">
                <div>
                  <p className="text-sentinel-text-secondary text-sm mb-2">Average Risk Score (24h)</p>
                  <p className="text-3xl font-bold text-sentinel-text-primary mb-1">3.2</p>
                  <p className="text-sentinel-accent-warning text-xs">Medium</p>
                </div>
                {/* Gauge bar indicating medium level (yellow/orange) */}
                <div className="space-y-2">
                  <div className="w-full bg-sentinel-bg-tertiary rounded-full h-2">
                    <div className="bg-gradient-to-r from-sentinel-accent-warning to-sentinel-accent-warning/60 h-2 rounded-full" style={{width: '65%'}}></div>
                  </div>
                  <div className="flex justify-between text-xs text-sentinel-text-muted">
                    <span>Low</span>
                    <span>High</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Map Card (Top Right) */}
          <div className="lg:col-span-2">
            <div className="bg-sentinel-bg-secondary border border-sentinel-accent-primary/30 rounded-lg p-6 h-full">
              <h3 className="text-lg font-semibold text-sentinel-text-primary mb-4">
                Origin vs. destination countries
              </h3>
              {/* Real World Map Heatmap */}
              <div className="h-80 bg-sentinel-bg-tertiary rounded-lg relative overflow-hidden">
                <WorldMap className="w-full h-full" />
              </div>
            </div>
          </div>
        </div>

        {/* Bottom Section - Event Stream */}
        <div className="bg-sentinel-bg-secondary border border-sentinel-border rounded-lg">
          {/* Tabs */}
          <div className="border-b border-sentinel-border px-6 py-4">
            <div className="flex space-x-6">
              <button className="text-sentinel-accent-primary border-b-2 border-sentinel-accent-primary pb-2 font-medium">
                Live
              </button>
              <button className="text-sentinel-text-secondary hover:text-sentinel-text-primary pb-2 font-medium">
                Timeline
              </button>
              <button className="text-sentinel-text-secondary hover:text-sentinel-text-primary pb-2 font-medium">
                Hotline
              </button>
            </div>
          </div>

          {/* Event Stream */}
          <div className="p-6 space-y-3 max-h-96 overflow-y-auto">
            {/* Event Pills */}
            <div className="flex items-center space-x-3 p-3 bg-sentinel-bg-tertiary/50 rounded-lg border border-sentinel-accent-success/20">
              <div className="w-3 h-3 bg-sentinel-accent-success rounded-full flex-shrink-0"></div>
              <div className="flex items-center space-x-2 flex-1 min-w-0">
                <svg className="w-4 h-4 text-sentinel-accent-success flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <span className="text-sentinel-text-primary font-medium truncate">transfer_completed</span>
              </div>
              <span className="text-sentinel-text-muted text-xs font-mono">14:32:15 UTC</span>
            </div>

            <div className="flex items-center space-x-3 p-3 bg-sentinel-bg-tertiary/50 rounded-lg border border-sentinel-accent-warning/20">
              <div className="w-3 h-3 bg-sentinel-accent-warning rounded-full flex-shrink-0"></div>
              <div className="flex items-center space-x-2 flex-1 min-w-0">
                <svg className="w-4 h-4 text-sentinel-accent-warning flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <span className="text-sentinel-text-primary font-medium truncate">compliance_review_required</span>
              </div>
              <span className="text-sentinel-text-muted text-xs font-mono">14:31:42 UTC</span>
            </div>

            <div className="flex items-center space-x-3 p-3 bg-sentinel-bg-tertiary/50 rounded-lg border border-sentinel-accent-success/20">
              <div className="w-3 h-3 bg-sentinel-accent-success rounded-full flex-shrink-0"></div>
              <div className="flex items-center space-x-2 flex-1 min-w-0">
                <svg className="w-4 h-4 text-sentinel-accent-success flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <span className="text-sentinel-text-primary font-medium truncate">entity_verified</span>
              </div>
              <span className="text-sentinel-text-muted text-xs font-mono">14:31:18 UTC</span>
            </div>

            <div className="flex items-center space-x-3 p-3 bg-sentinel-bg-tertiary/50 rounded-lg border border-sentinel-accent-danger/20">
              <div className="w-3 h-3 bg-sentinel-accent-danger rounded-full flex-shrink-0"></div>
              <div className="flex items-center space-x-2 flex-1 min-w-0">
                <svg className="w-4 h-4 text-sentinel-accent-danger flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
                </svg>
                <span className="text-sentinel-text-primary font-medium truncate">high_risk_transaction_blocked</span>
              </div>
              <span className="text-sentinel-text-muted text-xs font-mono">14:30:57 UTC</span>
            </div>

            <div className="flex items-center space-x-3 p-3 bg-sentinel-bg-tertiary/50 rounded-lg border border-sentinel-accent-success/20">
              <div className="w-3 h-3 bg-sentinel-accent-success rounded-full flex-shrink-0"></div>
              <div className="flex items-center space-x-2 flex-1 min-w-0">
                <svg className="w-4 h-4 text-sentinel-accent-success flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <span className="text-sentinel-text-primary font-medium truncate">pac002_generated</span>
              </div>
              <span className="text-sentinel-text-muted text-xs font-mono">14:30:33 UTC</span>
            </div>

            <div className="flex items-center space-x-3 p-3 bg-sentinel-bg-tertiary/50 rounded-lg border border-sentinel-accent-warning/20">
              <div className="w-3 h-3 bg-sentinel-accent-warning rounded-full flex-shrink-0"></div>
              <div className="flex items-center space-x-2 flex-1 min-w-0">
                <svg className="w-4 h-4 text-sentinel-accent-warning flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <span className="text-sentinel-text-primary font-medium truncate">queue_depth_warning</span>
              </div>
              <span className="text-sentinel-text-muted text-xs font-mono">14:29:45 UTC</span>
            </div>
          </div>
        </div>
      </div>
    </ProtectedRoute>
  );
}
