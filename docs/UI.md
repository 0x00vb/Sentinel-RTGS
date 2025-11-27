
**Sentinel RTGS – Professional Operations Dashboard**

**Base Design Language System (Sentinel DLS)**

A professional, high-density financial operations dashboard UI in a dark mode enterprise, 
premium and beautiful style, known as "Sentinel RTGS." The aesthetic is "Bloomberg Terminal meets modern 
banking"—deep charcoal and obsidian backgrounds (#0E1A2B), with data presented in clean white 
and light gray typography (Inter and Roboto Mono fonts). Accent colors are strictly functional: 
electric cyan (#3CE8FF) for primary actions and highlights, muted amber (#FFB648) for warnings, 
stark red (#FF3B30) for blocks/danger, and desaturated green (#2DD88F) for cleared/success 
states. The layout features a persistent, collapsible left-hand navigation sidebar with icons, 
a standardized top header bar with global search, notifications, and user profile. All panels 
have subtle, clean borders and flat, modern components with minimal shadows. The overall feel 
is trustworthy, modern, high-performance, and information-dense.

**Authentication & Navigation**

1. **Login Screen**
   - Clean form with email/password fields and MFA code input
   - "SENTINEL" logo with cyber-grid background
   - "Enter Command Center" button

2. **Main Application Shell**
   - Top bar: UTC time, environment badge (DEV/PROD), user dropdown
   - Left navigation: Dashboard, Live Wire Stream, Investigations, Entity Registry, Risk Engine, General Ledger, System Health

**Core Screens (Non-Overlapping Architecture)**

1. **Dashboard (Mission Control)** – High-level aggregates and system pulse
   - Top KPI cards: "Today's Volume: $14.5B (↑2%)", "Pending Reviews: 4 (Amber)", "Queue Depth: 120ms (Green)", "Active Blocks: 1 (Red)"
   - Wide area chart: "Transaction Velocity (24h)" with cyan line
   - Bottom right: "System Pulse" status indicators for RabbitMQ and Database
   - Focus: Aggregate numbers only, no individual rows

2. **Live Wire Stream (Transactions)** – Real-time pipeline monitor
   - Dominant AG Grid with columns: Timestamp, Payment ID, Sender BIC, Receiver BIC, Amount, Currency, Status Pill
   - "Pipeline Status" column: Horizontal stepper dots (Ingest → Validate → Risk → Ledger → Pacs.002)
   - Completed stages: green dots, current: pulsing cyan, future: gray
   - One row highlighted with cyan glow for new arrival
   - Purpose: Watch traffic, no actions

3. **Investigations (Compliance Cockpit)** – Master-detail AML workbench
   - Left panel: Queue of BLOCKED_AML transactions with red status pills
   - Right panel: Selected transaction details + "Match Evidence" section
   - Evidence: Sender name, watchlist match, similarity score progress bar (e.g., "94%")
   - Action buttons: Red "REJECT & SEIZE", Blue "OVERRIDE & RELEASE"
   - On override: Modal with "Compliance Override Request" header, justification textarea, "Confirm" button
   - Purpose: Human decision-making for blocked transactions

4. **Entity Registry (KYC Center)** – Customer and entity profiles
   - Search interface by name/document/account
   - Entity detail: Risk rating badge, document expiry alerts, linked accounts graph
   - Tabbed sections: Demographics, Documents (passport/addresses), Transaction History, Alerts
   - Network graph: Nodes showing entity relationships and sanctions connections
   - Purpose: 360° view of entities, separate from transactions

5. **Risk Engine (Configuration Hub)** – Rules and model management
   - Rule Configuration table: Active rules like "Velocity > $10k/hr" with enable toggles
   - ML Model Status: ROC Curve chart and "Auto-Block Threshold" slider (set to 85%)
   - Model audit log: History of threshold changes and manual overrides
   - Purpose: Configure automated logic, not view transactions

6. **General Ledger (Financial Core)** – Double-entry accounting SOX
   - Top KPIs: "Total Assets: $42.1B", "Total Liabilities: $38.5B", "Net Worth: $3.7B", "Active Accounts: 1,247"
   - Accounting grid: Transaction ID, Debit Account, Credit Account, Amount, Running Balance, Timestamp
   - T-Account visualizer: Left debits, right credits for selected account with running balance calculations
   - SOX Compliance Dashboard:
     - Real-time audit integrity status with hash chain verification indicators
     - SOX Section 404 compliance reports with tamper detection alerts
     - Entity audit trails showing complete transaction history with cryptographic proof
     - Audit activity reports and compliance metrics
     - System health monitoring for audit subsystem
   - Transaction compliance status: Color-coded AML/SOX compliance indicators per entry
   - Export options: CSV/PDF/JSON (Parquet via API)
   - Audit chain verification: Click any transaction to verify cryptographic integrity
   - Immutable audit logs: Tamper-proof financial records with SHA-256 hash chaining
   - Purpose: SOX-compliant immutable financial truth with real-time compliance monitoring

7. **System Health (Operations Center)** – Infrastructure monitoring
   - Microservice health grid: API Gateway, Risk Service, Ledger Core, Pacs.002 Sender (green/amber/red dots)
   - Live logs console: Scrolling monospaced system events with timestamps
   - RabbitMQ metrics: Queue lengths, consumer status, dead-letter counts
   - Purpose: Technical status, no business logic

**Bonus Features**
- "Neon Trace" transitions showing pipeline stages visually
- Graph-based risk visualization with connected nodes
- Commander shortcuts (Cmd+K global search)
- Dark-mode first interface
- Real-time WebSocket updates with subtle flash animations

**Key UX Principles**
- Strict separation of concerns: Monitoring (Dashboard/Stream), Action (Investigations), Configuration (Risk Engine), Storage (Ledger/Registry), Technical (Health)
- High information density without clutter
- Professional banking aesthetic with functional color coding
- Minimal clicks, quick actions, and intuitive workflows

---