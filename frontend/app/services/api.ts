/**
 * Centralized API client for all backend API calls
 * Abstracts fetch logic from components
 */

const API_BASE = '/api/v1';

class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public statusText: string
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function apiRequest<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const url = `${API_BASE}${endpoint}`;
  
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  });

  if (!response.ok) {
    throw new ApiError(
      `API request failed: ${response.statusText}`,
      response.status,
      response.statusText
    );
  }

  return response.json();
}

// Ledger API
export const ledgerApi = {
  getKPIs: () => apiRequest<{
    totalAssets: string;
    totalLiabilities: string;
    netWorth: string;
    activeAccounts: number;
  }>('/ledger/kpis'),

  getEntries: (params: {
    page?: number;
    size?: number;
    sortBy?: string;
    sortDir?: string;
  }) => {
    const query = new URLSearchParams();
    if (params.page !== undefined) query.append('page', params.page.toString());
    if (params.size !== undefined) query.append('size', params.size.toString());
    if (params.sortBy) query.append('sortBy', params.sortBy);
    if (params.sortDir) query.append('sortDir', params.sortDir);
    
    return apiRequest<{
      content: any[];
      totalPages: number;
      totalElements: number;
    }>(`/ledger/entries?${query.toString()}`);
  },

  getAccounts: () => apiRequest<any[]>('/ledger/accounts'),

  getTAccount: (accountId: number) => 
    apiRequest<any>(`/ledger/t-account/${accountId}`),

  getSOXCompliance: () => apiRequest<any>('/ledger/sox-compliance'),

  verifyAuditChain: (entityType: string, entityId: number) =>
    apiRequest<any>(`/ledger/audit/verify/${entityType}/${entityId}`),

  exportCSV: (params: { page?: number; size?: number } = {}) => {
    const query = new URLSearchParams();
    if (params.page !== undefined) query.append('page', params.page.toString());
    if (params.size !== undefined) query.append('size', params.size.toString());
    
    return fetch(`${API_BASE}/ledger/export/csv?${query.toString()}`)
      .then(res => {
        if (!res.ok) throw new ApiError('Export failed', res.status, res.statusText);
        return res.blob();
      });
  },
};

// Dashboard API
export const dashboardApi = {
  getMetrics: () => apiRequest<any>('/dashboard/metrics'),

  getLiveMetrics: () => apiRequest<any>('/dashboard/metrics/live'),

  getEvents: (limit: number = 20) =>
    apiRequest<{ events: any[]; total: number }>(`/dashboard/events/recent?limit=${limit}`),

  getCountryHeatmap: (hours: number = 24) =>
    apiRequest<{ [key: string]: number }>(`/dashboard/heatmap/countries?hours=${hours}`),
};

// Compliance API
export const complianceApi = {
  getWorklist: (params: {
    page?: number;
    size?: number;
    sortBy?: string;
    sortDir?: string;
  }) => {
    const query = new URLSearchParams();
    if (params.page !== undefined) query.append('page', params.page.toString());
    if (params.size !== undefined) query.append('size', params.size.toString());
    if (params.sortBy) query.append('sortBy', params.sortBy);
    if (params.sortDir) query.append('sortDir', params.sortDir);
    
    return apiRequest<any>(`/compliance/worklist?${query.toString()}`);
  },

  reviewTransaction: (transferId: number, decision: 'APPROVE' | 'REJECT', notes?: string) =>
    apiRequest<any>(`/compliance/${transferId}/review`, {
      method: 'POST',
      body: JSON.stringify({ decision, notes }),
    }),

  testIntegrity: () =>
    apiRequest<any>('/compliance/integrity/test', { method: 'POST' }),
};

// Simulation API
export const simulationApi = {
  getStatus: () => apiRequest<{
    running: boolean;
    devMode: boolean;
    messagesSent: number;
  }>('/simulation/status'),

  start: (messagesPerSecond: number = 5) =>
    apiRequest<any>(`/simulation/start?messagesPerSecond=${messagesPerSecond}`, {
      method: 'POST',
    }),

  stop: () =>
    apiRequest<any>('/simulation/stop', { method: 'POST' }),

  sendTestMessage: () =>
    apiRequest<any>('/simulation/send-test-message', { method: 'POST' }),
};

export { ApiError };

