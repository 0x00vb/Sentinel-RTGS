import { useState, useEffect, useCallback } from 'react';

export interface DashboardMetrics {
  totalTransfersToday: number;
  pendingComplianceReviews: number;
  averageRiskScore: number;
  queueDepth: number;
  simulationStatus: {
    running: boolean;
    devMode: boolean;
    messagesSent: number;
  };
  systemHealth: {
    status: string;
    integrityIntact: boolean;
  };
}

export interface DashboardEvent {
  id: string;
  type: string;
  message: string;
  timestamp: string;
  severity: 'success' | 'warning' | 'danger' | 'info';
}

export interface EventsResponse {
  events: DashboardEvent[];
  total: number;
}

export function useDashboardMetrics() {
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchMetrics = useCallback(async () => {
    try {
      const response = await fetch('/api/v1/dashboard/metrics');
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      const data = await response.json();
      setMetrics(data);
      setError(null);
    } catch (err) {
      console.error('Failed to fetch dashboard metrics:', err);
      setError(err instanceof Error ? err.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchMetrics();
    // Refresh every 30 seconds
    const interval = setInterval(fetchMetrics, 30000);
    return () => clearInterval(interval);
  }, [fetchMetrics]);

  return { metrics, loading, error, refetch: fetchMetrics };
}

export function useDashboardEvents(limit: number = 20) {
  const [events, setEvents] = useState<DashboardEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchEvents = useCallback(async () => {
    try {
      const response = await fetch(`/api/v1/dashboard/events/recent?limit=${limit}`);
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      const data: EventsResponse = await response.json();
      setEvents(data.events);
      setError(null);
    } catch (err) {
      console.error('Failed to fetch dashboard events:', err);
      setError(err instanceof Error ? err.message : 'Unknown error');
      setEvents([]);
    } finally {
      setLoading(false);
    }
  }, [limit]);

  useEffect(() => {
    fetchEvents();
    // Refresh every 10 seconds for real-time feel
    const interval = setInterval(fetchEvents, 10000);
    return () => clearInterval(interval);
  }, [fetchEvents]);

  return { events, loading, error, refetch: fetchEvents };
}

export function useLiveMetrics() {
  const [liveMetrics, setLiveMetrics] = useState<{
    pendingComplianceReviews: number;
    simulationStatus: {
      running: boolean;
      devMode: boolean;
      messagesSent: number;
    };
  } | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchLiveMetrics = useCallback(async () => {
    try {
      const response = await fetch('/api/v1/dashboard/metrics/live');
      if (response.ok) {
        const data = await response.json();
        setLiveMetrics(data);
      }
    } catch (err) {
      console.error('Failed to fetch live metrics:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchLiveMetrics();
    // Refresh every 5 seconds for very live updates
    const interval = setInterval(fetchLiveMetrics, 5000);
    return () => clearInterval(interval);
  }, [fetchLiveMetrics]);

  return { liveMetrics, loading };
}

export function useCountryHeatmap(hours: number = 24) {
  const [countryData, setCountryData] = useState<{ [key: string]: number }>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchHeatmap = useCallback(async () => {
    try {
      const response = await fetch(`/api/v1/dashboard/heatmap/countries?hours=${hours}`);
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      const data = await response.json();
      setCountryData(data);
      setError(null);
    } catch (err) {
      console.error('Failed to fetch country heatmap data:', err);
      setError(err instanceof Error ? err.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  }, [hours]);

  useEffect(() => {
    fetchHeatmap();
    // Refresh every 30 seconds
    const interval = setInterval(fetchHeatmap, 30000);
    return () => clearInterval(interval);
  }, [fetchHeatmap]);

  return { countryData, loading, error, refetch: fetchHeatmap };
}
