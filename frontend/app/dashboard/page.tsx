                                                                                                                                                                              'use client';

import ProtectedRoute from '../components/ProtectedRoute';
import WorldMap from '../components/WorldMap';
import { useState } from 'react';
import { useDashboardMetrics, useDashboardEvents, useLiveMetrics } from '../hooks/useDashboardData';

export default function DashboardPage() {
  const { metrics, loading: metricsLoading, error: metricsError } = useDashboardMetrics();
  const { events, loading: eventsLoading } = useDashboardEvents();
  const { liveMetrics } = useLiveMetrics();
  const [isLoading, setIsLoading] = useState(false);

  const startSimulation = async (messagesPerSecond = 5) => {
    setIsLoading(true);
    try {
      const response = await fetch(`/api/v1/simulation/start?messagesPerSecond=${messagesPerSecond}`, {
        method: 'POST'
      });
      if (!response.ok) {
        console.error('Failed to start simulation:', response.status, response.statusText);
        alert(`Failed to start simulation: ${response.status} ${response.statusText}`);
      }
    } catch (error: any) {
      console.error('Failed to start simulation:', error);
      alert(`Failed to start simulation: ${error.message}`);
    }
    setIsLoading(false);
  };

  const stopSimulation = async () => {
    setIsLoading(true);
    try {
      const response = await fetch('/api/v1/simulation/stop', {
        method: 'POST'
      });
      if (!response.ok) {
        console.error('Failed to stop simulation:', response.status, response.statusText);
        alert(`Failed to stop simulation: ${response.status} ${response.statusText}`);
      }
    } catch (error: any) {
      console.error('Failed to stop simulation:', error);
      alert(`Failed to stop simulation: ${error.message}`);
    }
    setIsLoading(false);
  };

  const performIntegrityTest = async () => {
    setIsLoading(true);
    try {
      const response = await fetch('/api/v1/compliance/integrity/test', {
        method: 'POST'
      });
      if (response.ok) {
        const result = await response.json();
        alert(result.message);
      } else {
        console.error('Failed to perform integrity test:', response.status, response.statusText);
        alert(`Failed to perform integrity test: ${response.status} ${response.statusText}`);
      }
    } catch (error: any) {
      console.error('Failed to perform integrity test:', error);
      alert(`Failed to perform integrity test: ${error.message}`);
    }
    setIsLoading(false);
  };

  const getRiskLevel = (score: number): string => {
    if (score < 2) return 'Low';
    if (score < 5) return 'Medium';
    if (score < 8) return 'High';
    return 'Critical';
  };

  const getRiskBarColor = (score: number): string => {
    if (score < 2) return 'bg-sentinel-accent-success';
    if (score < 5) return 'bg-sentinel-accent-warning';
    if (score < 8) return 'bg-sentinel-accent-danger';
    return 'bg-sentinel-accent-danger';
  };

  const EventIcon = ({ type, severity }: { type: string; severity: string }) => {
    const getIconColor = () => {
      switch (severity) {
        case 'success': return 'text-sentinel-accent-success';
        case 'warning': return 'text-sentinel-accent-warning';
        case 'danger': return 'text-sentinel-accent-danger';
        default: return 'text-sentinel-text-secondary';
      }
    };

    // Choose icon based on event type
    if (type.includes('blocked') || type.includes('high_risk')) {
      return (
        <svg className={`w-4 h-4 ${getIconColor()} flex-shrink-0`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
        </svg>
      );
    } else if (type.includes('review') || type.includes('warning')) {
      return (
        <svg className={`w-4 h-4 ${getIconColor()} flex-shrink-0`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      );
    } else {
      return (
        <svg className={`w-4 h-4 ${getIconColor()} flex-shrink-0`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      );
    }
  };

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
                  <p className="text-3xl font-bold text-sentinel-text-primary mb-1">
                    {metricsLoading ? '...' : (metrics?.totalTransfersToday || 0).toLocaleString()}
                  </p>
                  <p className="text-sentinel-text-muted text-xs">Audit entries today</p>
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
                  <p className="text-3xl font-bold text-sentinel-text-primary mb-1">
                    {liveMetrics?.pendingComplianceReviews !== undefined ? liveMetrics.pendingComplianceReviews : (metrics?.pendingComplianceReviews || 0)}
                  </p>
                  <p className="text-sentinel-text-muted text-xs">Blocked transactions</p>
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
                  <p className="text-sentinel-text-secondary text-sm mb-2">Message Queue Depth</p>
                  <p className="text-3xl font-bold text-sentinel-text-primary mb-1">
                    {metricsLoading ? '...' : (metrics?.queueDepth || 0).toLocaleString()}
                  </p>
                  <p className="text-sentinel-text-muted text-xs">Pending messages</p>
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
                  <p className="text-3xl font-bold text-sentinel-text-primary mb-1">
                    {metricsLoading ? '...' : (metrics?.averageRiskScore || 0).toFixed(1)}
                  </p>
                  <p className="text-sentinel-accent-warning text-xs">
                    {getRiskLevel(metrics?.averageRiskScore || 0)}
                  </p>
                </div>
                {/* Gauge bar indicating risk level */}
                <div className="space-y-2">
                  <div className="w-full bg-sentinel-bg-tertiary rounded-full h-2">
                    <div
                      className={`h-2 rounded-full ${getRiskBarColor(metrics?.averageRiskScore || 0)}`}
                      style={{width: `${Math.min(100, ((metrics?.averageRiskScore || 0) / 10) * 100)}%`}}
                    ></div>
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

        {/* Traffic Simulation Controls */}
        <div className="bg-sentinel-bg-secondary border border-sentinel-border rounded-lg p-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="text-lg font-semibold text-sentinel-text-primary mb-1">
                Traffic Simulation Controls
              </h3>
              <p className="text-sentinel-text-secondary text-sm">
                Generate realistic banking traffic for testing and demonstration
              </p>
            </div>
            {liveMetrics?.simulationStatus && (
              <div className="flex items-center space-x-2">
                <div className={`w-3 h-3 rounded-full ${liveMetrics.simulationStatus.running ? 'bg-sentinel-accent-success animate-pulse' : 'bg-sentinel-text-muted'}`}></div>
                <span className="text-sm text-sentinel-text-secondary">
                  {liveMetrics.simulationStatus.running ? 'Running' : 'Stopped'}
                </span>
              </div>
            )}
          </div>

          <div className="flex flex-wrap items-center gap-4 mb-4">
            {/* Start/Stop Controls */}
            <div className="flex space-x-2">
              {!liveMetrics?.simulationStatus?.running ? (
                <>
                  <button
                    onClick={() => startSimulation(2)}
                    disabled={isLoading || !liveMetrics?.simulationStatus?.devMode}
                    className="px-4 py-2 bg-sentinel-accent-primary text-sentinel-bg-primary rounded-lg hover:bg-sentinel-accent-primary/90 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                  >
                    Start (2/sec)
                  </button>
                  <button
                    onClick={() => startSimulation(5)}
                    disabled={isLoading || !liveMetrics?.simulationStatus?.devMode}
                    className="px-4 py-2 bg-sentinel-accent-primary text-sentinel-bg-primary rounded-lg hover:bg-sentinel-accent-primary/90 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                  >
                    Start (5/sec)
                  </button>
                  <button
                    onClick={() => startSimulation(10)}
                    disabled={isLoading || !liveMetrics?.simulationStatus?.devMode}
                    className="px-4 py-2 bg-sentinel-accent-primary text-sentinel-bg-primary rounded-lg hover:bg-sentinel-accent-primary/90 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                  >
                    Start (10/sec)
                  </button>
                </>
              ) : (
                <button
                  onClick={stopSimulation}
                  disabled={isLoading}
                  className="px-4 py-2 bg-sentinel-accent-danger text-sentinel-bg-primary rounded-lg hover:bg-sentinel-accent-danger/90 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  Stop Simulation
                </button>
              )}
            </div>

            {/* Send Single Message */}
            <button
              onClick={async () => {
                setIsLoading(true);
                try {
                  const response = await fetch('/api/v1/simulation/send-test-message', {
                    method: 'POST'
                  });
                  if (response.ok) {
                    alert('Test message sent successfully');
                  } else {
                    alert(`Failed to send test message: ${response.status}`);
                  }
                } catch (error: any ) {
                  console.error('Failed to send test message:', error);
                  alert(`Failed to send test message: ${error.message}`);
                }
                setIsLoading(false);
              }}
              disabled={isLoading || !liveMetrics?.simulationStatus?.devMode}
              className="px-4 py-2 border border-sentinel-border text-sentinel-text-primary rounded-lg hover:bg-sentinel-bg-tertiary disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              Send Test Message
            </button>

            {/* Integrity Test */}
            <button
              onClick={performIntegrityTest}
              disabled={isLoading || !liveMetrics?.simulationStatus?.devMode}
              className="px-4 py-2 border border-sentinel-accent-warning text-sentinel-accent-warning rounded-lg hover:bg-sentinel-accent-warning/10 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              Integrity Test
            </button>
          </div>

          {/* Status Information */}
          {liveMetrics?.simulationStatus && (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-sm">
              <div className="bg-sentinel-bg-tertiary/50 rounded-lg p-3">
                <div className="text-sentinel-text-secondary mb-1">Messages Sent</div>
                <div className="text-xl font-semibold text-sentinel-text-primary">
                  {liveMetrics.simulationStatus.messagesSent?.toLocaleString() || '0'}
                </div>
              </div>
              <div className="bg-sentinel-bg-tertiary/50 rounded-lg p-3">
                <div className="text-sentinel-text-secondary mb-1">Development Mode</div>
                <div className={`text-xl font-semibold ${liveMetrics.simulationStatus.devMode ? 'text-sentinel-accent-success' : 'text-sentinel-accent-danger'}`}>
                  {liveMetrics.simulationStatus.devMode ? 'Enabled' : 'Disabled'}
                </div>
              </div>
              <div className="bg-sentinel-bg-tertiary/50 rounded-lg p-3">
                <div className="text-sentinel-text-secondary mb-1">Status</div>
                <div className={`text-xl font-semibold ${liveMetrics.simulationStatus.running ? 'text-sentinel-accent-success' : 'text-sentinel-text-muted'}`}>
                  {liveMetrics.simulationStatus.running ? 'Active' : 'Inactive'}
                </div>
              </div>
            </div>
          )}

          {liveMetrics?.simulationStatus && !liveMetrics.simulationStatus.devMode && (
            <div className="mt-4 p-3 bg-sentinel-accent-warning/10 border border-sentinel-accent-warning/30 rounded-lg">
              <div className="flex items-center space-x-2">
                <svg className="w-5 h-5 text-sentinel-accent-warning" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <span className="text-sentinel-accent-warning font-medium">Development Mode Required</span>
              </div>
              <p className="text-sentinel-text-secondary text-sm mt-1">
                Traffic simulation is only available in development mode for security and compliance reasons.
              </p>
            </div>
          )}
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
            {eventsLoading ? (
              <div className="text-center text-sentinel-text-secondary py-4">
                Loading events...
              </div>
            ) : events.length === 0 ? (
              <div className="text-center text-sentinel-text-secondary py-4">
                No recent events. Start the simulation to see activity.
              </div>
            ) : (
              events.map((event) => (
                <div
                  key={event.id}
                  className={`flex items-center space-x-3 p-3 bg-sentinel-bg-tertiary/50 rounded-lg border ${
                    event.severity === 'success' ? 'border-sentinel-accent-success/20' :
                    event.severity === 'warning' ? 'border-sentinel-accent-warning/20' :
                    event.severity === 'danger' ? 'border-sentinel-accent-danger/20' :
                    'border-sentinel-border/20'
                  }`}
                >
                  <div className={`w-3 h-3 rounded-full flex-shrink-0 ${
                    event.severity === 'success' ? 'bg-sentinel-accent-success' :
                    event.severity === 'warning' ? 'bg-sentinel-accent-warning' :
                    event.severity === 'danger' ? 'bg-sentinel-accent-danger' :
                    'bg-sentinel-text-muted'
                  }`}></div>
                  <div className="flex items-center space-x-2 flex-1 min-w-0">
                    <EventIcon type={event.type} severity={event.severity} />
                    <span className="text-sentinel-text-primary font-medium truncate">
                      {event.message}
                    </span>
                  </div>
                  <span className="text-sentinel-text-muted text-xs font-mono">
                    {new Date(event.timestamp).toLocaleTimeString('en-US', {
                      hour12: false,
                      timeZone: 'UTC'
                    })} UTC
                  </span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </ProtectedRoute>
  );
}
