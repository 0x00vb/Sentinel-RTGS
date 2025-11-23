-- V1__init_schema.sql
-- Initial schema migration for Fortress-Settlement RTGS core banking engine
-- Based on devplan.md section 6.1 Data Model & Schemas

-- accounts table
CREATE TABLE accounts (
  id BIGSERIAL PRIMARY KEY,
  iban VARCHAR(34) UNIQUE NOT NULL,
  balance NUMERIC(36, 6) NOT NULL DEFAULT 0, -- be generous for precision
  currency CHAR(3) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- transfers table
CREATE TABLE transfers (
  id BIGSERIAL PRIMARY KEY,
  msg_id UUID UNIQUE NOT NULL,
  amount NUMERIC(36,6) NOT NULL,
  currency CHAR(3) NOT NULL,
  sender_iban VARCHAR(34),
  receiver_iban VARCHAR(34),
  status VARCHAR(20) NOT NULL, -- PENDING/CLEARED/BLOCKED_AML/REJECTED
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- ledger_entries table
CREATE TABLE ledger_entries (
  id BIGSERIAL PRIMARY KEY,
  transfer_id BIGINT REFERENCES transfers(id),
  account_id BIGINT REFERENCES accounts(id),
  entry_type VARCHAR(6) NOT NULL ENUM('DEBIT', 'CREDIT'), -- DEBIT/CREDIT
  amount NUMERIC(36,6) NOT NULL,  -- positive
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- sanctions_list table
CREATE TABLE sanctions_list (
  id BIGSERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  normalized_name TEXT, -- index-friendly
  risk_score INT DEFAULT 50,
  source VARCHAR(64),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- Create GIN index for fuzzy text search on sanctions
CREATE INDEX idx_sanctions_name ON sanctions_list USING gin (to_tsvector('simple', normalized_name));

-- audit_logs table
CREATE TABLE audit_logs (
  id BIGSERIAL PRIMARY KEY,
  entity_type VARCHAR(50) NOT NULL, -- e.g., 'transfer'
  entity_id BIGINT NOT NULL,
  action VARCHAR(50) NOT NULL, -- CREATED, BLOCKED, CLEARED, REVIEW_APPROVED, ...
  payload JSONB NOT NULL,
  prev_hash VARCHAR(64) NOT NULL,
  curr_hash VARCHAR(64) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- Index for efficient audit chain traversal
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);

-- users table (for dashboard)
CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(64) UNIQUE NOT NULL,
  display_name VARCHAR(128),
  role VARCHAR(32), -- OP_ADMIN, COMPLIANCE, OPS
  password_hash VARCHAR(256),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- Create indexes for performance (additional optimizations)
CREATE INDEX idx_transfers_msg_id ON transfers(msg_id);
CREATE INDEX idx_transfers_status ON transfers(status);
CREATE INDEX idx_transfers_created_at ON transfers(created_at);

CREATE INDEX idx_ledger_entries_transfer_id ON ledger_entries(transfer_id);
CREATE INDEX idx_ledger_entries_account_id ON ledger_entries(account_id);
CREATE INDEX idx_ledger_entries_created_at ON ledger_entries(created_at);

CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);
CREATE INDEX idx_audit_logs_entity_type_id_created ON audit_logs(entity_type, entity_id, created_at);

CREATE INDEX idx_accounts_iban ON accounts(iban);
CREATE INDEX idx_accounts_currency ON accounts(currency);