# Software Development Plan (SDP) — *Fortress-Settlement*

**IEEE-style, delivery-ready, version aligned with PRD v1.0**

> Short pitch: an IEEE-compliant SDP tailored to Fortress-Settlement, a Modular-Monolith RTGS core banking engine with strict ACID guarantees, ISO20022 ingestion, OFAC/AML fuzzy matching, cryptographic audit chaining, and an ops dashboard for live demonstration. This plan contains every artifact required for full-scope development and handoff to an engineering team.

---

# 1. Document Control & References

* **Project:** Fortress-Settlement
* **SDP Version:** 1.0 (matches PRD v1.0)
* **Author:** (Generated for: [Your Name])
* **Date:** 2025-11-22
* **Applicable standards / references:** IEEE 1058 (Software Project Management Plan), IEEE 1233 (Requirements Spec), ISO 20022, SOX Section 404 guidance, PCI DSS v4.0 (selected controls), OWASP Top 10, PostgreSQL 16 docs, RabbitMQ architecture guides, Java 21 and Spring Boot 3.2+ best practices.

---

# 2. Executive Summary & Scope

**Goal:** Build a production-quality RTGS core engine to ingest ISO20022 `pacs.008`, run sanctions/fuzzy-matching, and perform ACID double-entry settlement with cryptographic audit chaining for SOX-compliant tamper detection.

**In-scope:**

* Message ingestion (RabbitMQ) and XML validation against `pacs.008` XSD
* Idempotency, duplicate detection
* Compliance Engine with fuzzy (Levenshtein) matching and manual review workflow
* Ledger with PESSIMISTIC_WRITE locking, double-entry postings
* Audit logs with SHA-256 chaining and tamper detection service
* Operations Dashboard (React) with WebSockets and "Hacker Simulation"
* Dockerized local dev and production-ready deployment artifacts (Dockerfile + Compose)
* Automated test suites, CI/CD pipelines, and monitoring instrumentation

**Out-of-scope (for this SDP):**

* Integration to external production SWIFT network (only simulated messages)
* Full multi-datacenter active-active deployment (design notes included)
* Full-fledged KYC, transaction pricing, fees, FX engines (can be added later)

**Constraints & Assumptions**

* Single-region monolithic core (Modular Monolith).
* PostgreSQL as only persistent store for financial records.
* RabbitMQ for messaging and transient buffering.
* System will run on Linux hosts; Docker is available.
* Regulatory datasets (OFAC lists) are provided and periodically updated.

---

# 3. System Architecture — high level

**Pattern:** Event-Driven Modular Monolith.

## 3.1 Architectural Components (top-level)

1. **Gateway (Message Ingest)**

   * RabbitMQ consumer, XML validator (XSD), JAXB parser → Domain DTO
2. **Ingestion Pipeline**

   * Idempotency check → Compliance Engine → Payment Orchestrator
3. **Compliance Engine (Shield)**

   * Sanctions DB access, fuzzy-match service, rule engine, manual review API
4. **Payment Orchestrator / Ledger (Core)**

   * Transactional business logic, PESSIMISTIC_WRITE locking, double-entry ledger writes
5. **Audit Service (Black Box)**

   * JPA entity listener to append audit_logs with hash chaining, tamper-checker service
6. **Operations Dashboard**

   * React + WebSocket for admin workflows and live logs
7. **Support Services**

   * Metrics & Monitoring (Prometheus/Grafana), Logging (ELK or OpenSearch), Backup & Recovery
8. **Admin APIs**

   * Compliance endpoints, admin user management, simulation endpoints

## 3.2 Data Flow (short)

1. RabbitMQ `bank.inbound` → message consumed
2. Validate XSD → convert to domain object
3. Check idempotency (msg_id) → if duplicate, ack and emit `pacs.002` if necessary
4. Run sanctions fuzzy-match → if >85% similarity → mark `BLOCKED_AML` + emit audit log + push to `bank.outbound` `pacs.002` (info)
5. If cleared → PaymentService invoked inside a single `@Transactional` method with `PESSIMISTIC_WRITE` locks → create ledger entries (debit/credit), update account balances, emit audit logs
6. All state changes written to `audit_logs` and hash-chained
7. Dashboard displays logs via WebSockets; Compliance officer uses API to review blocked transactions to `APPROVE`/`REJECT`.

---

# 4. Subsystem breakdown & responsibilities

Each subsystem maps to modules in PRD.

### 4.1 Gateway Module

* RabbitMQ listener
* XML validator (XSD)
* JAXB mapping
* Idempotency detection
* Outbound `pacs.002` sender

### 4.2 Compliance Engine

* Sanctions DB access
* FuzzyMatchService (Levenshtein / configurable threshold)
* Rule Engine (decide block/clear)
* Manual review endpoints + audit trail

### 4.3 Ledger / Payment Orchestrator

* PaymentService — transactional boundary
* AccountRepository, TransferRepository, LedgerEntryRepository
* PESSIMISTIC_WRITE locking strategy
* Double-entry enforcement

### 4.4 Audit / SOX Black Box

* AuditListener (JPA EntityListener)
* HashChain service (compute SHA-256(currPayload + prevHash))
* ChainVerifier service (traverse chain for integrity checks)
* Immutable log retention policy

### 4.5 Dashboard & Ops

* React app (Vite) + WebSocket `/topic/logs`
* Sanctions Worklist view
* Simulation Bot UI + API for "Hacker Simulation"

### 4.6 Infrastructure & Support

* Docker Compose definitions (dev), Dockerfile for services
* CI/CD (GitHub Actions or GitLab CI)
* Monitoring & alerting (Prometheus, Grafana, alert rules for chain mismatch)
* Backup & restore playbooks

---

# 5. Requirements — full formalization

## 5.1 Functional Requirements (formalized)

* **FR-01 (Ingest):** The Gateway shall listen on RabbitMQ queue `bank.inbound` and consume messages encoded as ISO 20022 `pacs.008` XML strings. (Priority: High)
* **FR-02 (XSD validation):** The system shall validate every inbound XML against provided `pacs.008` XSD; invalid messages shall be rejected and a `pacs.002` Status Report must be sent to `bank.outbound` with a specific error code. (Priority: High)
* **FR-03 (Idempotency):** The system shall persist `msg_id` in `transfers` and prevent reprocessing of identical `msg_id`. Duplicate messages must be logged and acknowledged without double posting. (Priority: High)
* **FR-04 (Sanctions lookup):** The system shall match Sender and Recipient names against `sanctions_list` using Levenshtein fuzzy matching and local `sanctions_list` DB. (Priority: High)
* **FR-05 (Fuzzy threshold):** If similarity score > 85% then mark transaction as `BLOCKED_AML`. Adjustable via config. (Priority: High)
* **FR-06 (Manual review):** There shall be REST endpoints for Compliance Officers to `APPROVE` or `REJECT` blocked transactions; actions must be recorded in `audit_logs`. (Priority: High)
* **FR-07 (Ledger locking):** Account updates must use PESSIMISTIC_WRITE to ensure serialized balance updates. (Priority: High)
* **FR-08 (Double-entry):** Each cleared transfer must create two ledger entries (debit & credit) summing to zero in the transaction boundary. (Priority: High)
* **FR-09 (Audit logging):** All state transitions must be recorded in `audit_logs` with `prev_hash` and `curr_hash` fields. (Priority: High)
* **FR-10 (Hash chaining):** Curr_hash = SHA256(serialized_payload + prev_hash); first entry uses prev_hash = 0x0. (Priority: High)
* **FR-11 (Tamper detection):** The system shall have a chain verifier service that traverses the `audit_logs` chain and flags a `Data Integrity Breach` if any mismatch occurs. (Priority: High)
* **FR-12 (Real-time UI):** Ops Dashboard shall show audit logs in real-time via WebSockets `/topic/logs`. (Priority: Medium)
* **FR-13 (Sanctions worklist):** Dashboard provides a "Sanctions Worklist" for blocked wires with actions. (Priority: Medium)
* **FR-14 (Hacker Simulation):** Admin endpoint to corrupt a DB row for demo purposes; action must be itself logged and reversible in dev environments only. (Priority: Low; Dev-only)

## 5.2 Non-functional requirements (NFRs)

* **NFR-01 (ACID):** All financial updates must be atomic, consistent, isolated (serializable via pessimistic locks), durable.
* **NFR-02 (Latency):** End-to-end processing (ingest → settlement) target P95 ≤ 2s under typical load (definition of typical load to be established during load tests). (Tunable)
* **NFR-03 (Throughput):** System must handle burst of `X=2000` messages/min without losing messages (initial target; verify in testing).
* **NFR-04 (Availability):** Target uptime: 99.95% (excluding maintenance). Monitored via heartbeat metrics.
* **NFR-05 (Integrity):** Cryptographic hash chain must detect any DB tampering with probability 1 (assuming secure SHA-256).
* **NFR-06 (Security):** All admin APIs must require mutual TLS or OAuth2 (JWT) and be role-based. Secrets are stored in Vault (or env during dev).
* **NFR-07 (Auditability):** System must be able to generate an audit report with full chain for any transaction ID.
* **NFR-08 (Scalability):** Designed to scale vertically; modularization enables future horizontal split.
* **NFR-09 (Compliance):** System must store and process PII per PCI DSS guidance; sensitive fields encrypted at rest with AES-256.
* **NFR-10 (Testability):** All core flows must be covered by unit & integration tests > 90% critical path code coverage.
* **NFR-11 (Maintainability):** Codebase must follow Java Clean Architecture; modules, interfaces and DTO boundaries documented.

---

# 6. Data Model & Schemas

## 6.1 ER & Schema (concise)

**Tables (DDL-like definitions)** — use PostgreSQL types.

### `accounts`

```sql
CREATE TABLE accounts (
  id BIGSERIAL PRIMARY KEY,
  iban VARCHAR(34) UNIQUE NOT NULL,
  balance NUMERIC(36, 6) NOT NULL DEFAULT 0, -- be generous for precision
  currency CHAR(3) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);
```

### `transfers`

```sql
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
```

### `ledger_entries`

```sql
CREATE TABLE ledger_entries (
  id BIGSERIAL PRIMARY KEY,
  transfer_id BIGINT REFERENCES transfers(id),
  account_id BIGINT REFERENCES accounts(id),
  entry_type VARCHAR(6) NOT NULL, -- DEBIT/CREDIT
  amount NUMERIC(36,6) NOT NULL,  -- positive
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);
```

### `sanctions_list`

```sql
CREATE TABLE sanctions_list (
  id BIGSERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  normalized_name TEXT, -- index-friendly
  risk_score INT DEFAULT 50,
  source VARCHAR(64),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);
CREATE INDEX idx_sanctions_name ON sanctions_list USING gin (to_tsvector('simple', normalized_name));
```

### `audit_logs`

```sql
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
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
```

### `users` (for dashboard)

```sql
CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(64) UNIQUE NOT NULL,
  display_name VARCHAR(128),
  role VARCHAR(32), -- OP_ADMIN, COMPLIANCE, OPS
  password_hash VARCHAR(256),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);
```

## 6.2 Sample JSON payload standard for audit log

```json
{
  "msg_id": "uuid",
  "status": "BLOCKED_AML",
  "reason": "FuzzyMatch(Score=91.2, matchedEntry='Osama B Laden')",
  "actor": "system|user:compliance_jane",
  "timestamp": "2025-11-22T20:00:00Z"
}
```

## 6.3 Index, retention & storage considerations

* `audit_logs` archival policy: keep hot chain for 1 year; archive older entries to cold storage (immutable S3) with signed manifests.
* Use logical partitions for `audit_logs` by month to ease chain traversal (store chain continuity meta).

---

# 7. Use Cases, User Stories, and UML (text-based)

## 7.1 Primary Use Cases (short)

1. **UC-01: Ingest message** — System receives `pacs.008`, validates XSD, converts, and enqueues for processing.
2. **UC-02: Block transaction** — Compliance Engine blocks based on fuzzy match.
3. **UC-03: Approval flow** — Compliance Officer approves/rejects blocked transaction.
4. **UC-04: Settle transaction** — Ledger performs double-entry and adjusts balances.
5. **UC-05: Audit verification** — Admin runs chain verifier and views results.
6. **UC-06: Visual tamper demo** — Admin triggers "Hacker Simulation" and shows audit breach detection.

## 7.2 User Stories (Agile formatted)

* As a Payments System, I want to accept ISO20022 `pacs.008` messages so that downstream clearing can occur. (FR-01, FR-02)
* As a Compliance Officer, I want to see high-similarity hits in a worklist so I can approve or reject them. (FR-05, FR-06)
* As a Ledger worker, I want double-entry ledger guarantees so that assets remain balanced. (FR-07, FR-08)
* As an Auditor, I want a verifiable hash chain so I can prove logs haven’t been tampered with. (FR-09, FR-10, FR-11)

## 7.3 UML Diagrams (text-based PlantUML)

### 7.3.1 Component Diagram (PlantUML)

```
@startuml
package "Fortress-Settlement" {
  [RabbitMQ] --> [Gateway Service]
  [Gateway Service] --> [Compliance Engine]
  [Compliance Engine] --> [Payment Orchestrator]
  [Payment Orchestrator] --> [Postgres DB]
  [Payment Orchestrator] --> [Audit Service]
  [Audit Service] --> [Postgres DB]
  [Dashboard] <--> [Gateway Service]
  [Dashboard] <--> [Audit Service via WebSocket]
}
@enduml
```

### 7.3.2 Sequence Diagram: Ingest -> Settle

```
@startuml
actor RabbitMQ
participant Gateway
participant Compliance
participant PaymentService
participant DB
participant AuditService
RabbitMQ -> Gateway: deliver pacs.008
Gateway -> Gateway: validate XSD
Gateway -> DB: check msg_id (idempotency)
alt duplicate
  Gateway -> RabbitMQ: ack, send pacs.002 (duplicate)
else unique
  Gateway -> Compliance: run fuzzy match
  alt blocked
    Compliance -> DB: mark transfer BLOCKED
    Compliance -> AuditService: log BLOCKED
    AuditService -> DB: insert audit_logs (with prev_hash → curr_hash)
    Compliance -> RabbitMQ: send pacs.002 BLOCKED
  else cleared
    Compliance -> PaymentService: submit
    PaymentService -> DB: lock accounts (PESSIMISTIC_WRITE)
    PaymentService -> DB: create ledger_entries (debit/credit)
    PaymentService -> DB: update balances
    PaymentService -> AuditService: log CLEARED
    AuditService -> DB: insert audit_logs
  end
end
@enduml
```

---

# 8. Detailed Algorithms & Pseudocode

## 8.1 Idempotency logic

```java
@Transactional
public ProcessingResult processInbound(Message msg) {
  Optional<Transfer> existing = transferRepo.findByMsgId(msg.getMsgId());
  if (existing.isPresent()) {
     // Recognize duplicate, log idempotent ack
     audit.log("DUPLICATE", existing.get().getId(), payload);
     return ProcessingResult.DUPLICATE;
  }
  Transfer t = convert(msg);
  transferRepo.save(t);
  return compliance.evaluate(t);
}
```

## 8.2 Levenshtein fuzzy matching (high-level)

* Normalize strings: uppercase, remove punctuation, collapse whitespace, transliterate to ASCII.
* Compute Levenshtein distance; compute similarity = (1 - distance / maxLen) * 100.
* If similarity >= threshold (default 85), return match. Use trigram or BK-tree for scale.

Pseudo:

```
function fuzzyMatch(name, sanctionsList) {
  nameNorm = normalize(name)
  candidates = sanctionsIndex.searchByNgrams(nameNorm)
  for each candidate in candidates:
     score = levenshteinSimilarity(nameNorm, candidate.norm)
     if score >= threshold:
        return { matched: true, score, candidateId }
  return { matched: false }
}
```

## 8.3 Hash chaining for audits

* For each audit log insertion: `curr_hash = SHA256(payload_jsonb_canonical + prev_hash)`
* Implementation detail: canonical JSON (sorted keys) to ensure deterministic hashing.
* First record uses `prev_hash = "000...0"` (64 zeros).

Pseudo:

```
prev_hash = auditRepo.getLastHashForEntity(entity_type, entity_id) ?: ZERO_HASH
payload_str = canonicalize(payload_json)
curr_hash = sha256(payload_str + prev_hash)
insert into audit_logs(..., prev_hash, curr_hash)
```

## 8.4 Chain verification

```
function verifyChain(entity_type, entity_id):
  rows = select * from audit_logs where entity_type=... and entity_id=... order by created_at asc
  prev = ZERO_HASH
  for r in rows:
     expected = sha256(canonicalize(r.payload) + prev)
     if expected != r.curr_hash:
       report breach at r.id
       return false
     prev = r.curr_hash
  return true
```

---

# 9. Work Breakdown Structure (WBS) & Work Packages

Provide a hierarchical WBS for full-scope project. (Durations are indicative — adjust to team capacity.)

**WBS 1.0 — Fortress-Settlement**

* **1.1 Project Setup**

  * 1.1.1 Repo scaffolding & branch strategy
  * 1.1.2 Docker Compose dev environment
  * 1.1.3 CI pipeline skeleton (lint, build, unit test)
* **1.2 Core Domain**

  * 1.2.1 Entities & JPA mapping (accounts, transfers, ledger_entries)
  * 1.2.2 Repositories & DB migrations (Flyway)
  * 1.2.3 PaymentService: transactional core
* **1.3 Gateway & Ingestion**

  * 1.3.1 RabbitMQ consumer
  * 1.3.2 XSD validation & JAXB mappings
  * 1.3.3 Idempotency
  * 1.3.4 Outbound pacs.002 sender
* **1.4 Compliance Engine**

  * 1.4.1 Sanctions DB ingestion
  * 1.4.2 FuzzyMatch service (Levenshtein/BK-tree)
  * 1.4.3 Rule Engine & threshold config
  * 1.4.4 Compliance REST APIs & worklist support
* **1.5 Audit/BlackBox**

  * 1.5.1 JPA AuditListener
  * 1.5.2 HashChain service & canonicalizer
  * 1.5.3 Chain verifier service + scheduled checks
  * 1.5.4 Audit report generator
* **1.6 Dashboard & Simulation**

  * 1.6.1 React app skeleton
  * 1.6.2 WebSocket integration for logs
  * 1.6.3 Sanctions worklist UI & actions
  * 1.6.4 Simulation Bot & Hacker Simulation UI
* **1.7 Security & Compliance**

  * 1.7.1 AES-256 attribute converters for sensitive fields
  * 1.7.2 AuthN/AuthZ (OAuth2 / JWT) config
  * 1.7.3 Secrets management & configuration
* **1.8 Testing & Quality**

  * 1.8.1 Unit tests for core services
  * 1.8.2 Integration tests (RabbitMQ + DB)
  * 1.8.3 Load tests & performance tuning
  * 1.8.4 Security tests (OWASP SCA, dependency scan)
* **1.9 Deployment & Operations**

  * 1.9.1 Production Dockerfile & Compose/Helm charts
  * 1.9.2 Backup & restore scripts
  * 1.9.3 Monitoring dashboards & alerts
* **1.10 Documentation & Handoff**

  * 1.10.1 Developer guide
  * 1.10.2 Runbook & incident playbooks
  * 1.10.3 Compliance report templates

**Suggested sprint plan:** 2-week sprints; MVP (Core + Gateway + Ledger + Audit) in 3 sprints (~6 weeks) as aggressive; PRD roadmap earlier suggests 2 weeks — tailor to team.

---

# 10. Dependencies & Interfaces

## 10.1 Internal dependencies

* Gateway depends on JAXB model classes and idempotency store.
* Compliance depends on `sanctions_list` and string normalization utilities.
* PaymentService depends on AccountRepository & LedgerEntryRepository.
* AuditService is invoked by domain events and by JPA listeners.

## 10.2 External interfaces

* **RabbitMQ**: inbound queue `bank.inbound`, outbound `bank.outbound`.
* **Admin Dashboard**: REST endpoints on `/api/*`, WebSocket endpoint `/ws/logs`.
* **External Sanctions providers (future)**: scheduled import API (CSV/JSON).
* **Monitoring**: metrics endpoint `/actuator/prometheus`.

## 10.3 API Specification (selected endpoints)

* `POST /api/v1/compliance/{transferId}/review` — body: `{ action: "APPROVE"|"REJECT", reviewer: "username", notes: "" }` → 200 OK
* `GET /api/v1/transfers/{msgId}` — get transfer details + audit chain
* `POST /api/v1/simulate/hack` — dev-only: corrupt DB row (requires admin & dev flag)
* `GET /api/v1/audit/{entityType}/{entityId}/verify` — run chain verifier

All endpoints require Authorization header (Bearer token) with RBAC checks.

---

# 11. Risk Assessment, Constraints, & Mitigations

## 11.1 Risk Register (top items)

| ID   |                                                          Risk | Likelihood |    Impact | Mitigation                                                                                                          |
| ---- | ------------------------------------------------------------: | ---------: | --------: | ------------------------------------------------------------------------------------------------------------------- |
| R-01 |                           Double spend due to race conditions | Low-Medium |  Critical | Use PESSIMISTIC_WRITE and transactional boundaries; integration tests for concurrency; DB-level constraints.        |
| R-02 | False positives in fuzzy matching causing business disruption |     Medium |      High | Expose threshold as config; manual review workflow; add confidence scores & reason codes.                           |
| R-03 |        Hash chain canonicalization mismatches across services |        Low |      High | Define canonical JSON serializer; include tests with fixed vectors; store canonical payload in audit row for repro. |
| R-04 |                       Performance bottleneck at DB under load |       High |      High | Benchmark early; optimize indexes; consider sharding or CQRS read replicas for heavy read flows.                    |
| R-05 |                   Insider tampering of DB outside application |        Low | Very High | Hash-chain detection + strict DB access controls, audit logs, retention, offline verification.                      |
| R-06 |                                Secrets leakage (keys for AES) |     Medium |      High | Use Vault; rotate keys; limit plaintext exposure; log key access.                                                   |
| R-07 |                              RabbitMQ single-point-of-failure |     Medium |    Medium | Use mirrored queues (HA) in production; configure QoS and dead-letter queues.                                       |

## 11.2 Regulatory Constraints

* SOX: must retain logs and provide immutable evidence for auditors.
* OFAC: must ensure up-to-date sanctions lists; configurable update cadence (daily/hourly).
* PCI: sensitive fields encrypted; restrict access to decrypted data.

---

# 12. Verification & Validation Plan (V&V)

## 12.1 V&V Strategy

* **Verification** (are we building the system right?): Unit tests, static analysis, code reviews, integration tests, API contract tests, schema validation tests.
* **Validation** (are we building the right system?): End-to-end acceptance tests using realistic `pacs.008` messages; compliance officer demo flows; load acceptance tests.

## 12.2 Test levels & sample cases

### Unit Tests

* PaymentService: concurrent transfers to same account (simulate 100 threads)
* FuzzyMatchService: normalized string matches, boundary scores at 85%
* HashChain: deterministic hashes, negative tests for mismatch

### Integration Tests

* RabbitMQ + App + Postgres in Docker network: end-to-end `pacs.008` ingestion to ledger
* Manual-review flow: block → approve → settle

### System Tests

* Security tests: JWT auth, role restrictions, encrypted fields remain encrypted at rest
* SIT: Full flow with multiple currencies and multiple accounts

### Performance Tests

* Throughput: ramp to 2000 msg/min and measure latency, DB lock contention, and queue backpressure
* Load: P95 latency ≤ 2s target (configurable)

### Acceptance Criteria (sample)

* 100% of critical FRs implemented and passing automated tests.
* Audit chain verification passes for 100% of sample transactions in acceptance suite.
* Compliance officer can approve/reject blocked transactions and that triggers correct downstream state change.
* No lost messages during stress test (all inbound messages either processed or rejected and traced).
* Security scan with zero critical vulnerabilities.

## 12.3 Test Data & Fixtures

* Provide a set of canonical `pacs.008` messages (valid, invalid, duplicates, edge-cases).
* Sanctions dataset including tricky near-matches to exercise fuzzy matching.

---

# 13. Deliverables, Milestones & Acceptance Criteria

## 13.1 Deliverables

* Source repo with code, Dockerfiles, and infra config
* Database migrations (Flyway) and seed data scripts
* CI/CD config with tests and build
* Test suites (unit/integration/performance)
* React Ops Dashboard build
* Runbook and maintenance guide
* Compliance documentation and audit report generator
* Demo script for recruiter / auditor

## 13.2 Milestones (example)

* **M1** — Project Kickoff & repo scaffold (Day 1)
* **M2** — Core domain + DB migrations (Day 3)
* **M3** — Gateway with basic ingestion & XSD validation (Day 6)
* **M4** — Compliance fuzzy match + audit chain (Day 10)
* **M5** — Ledger settlement + double-entry test coverage (Day 12)
* **M6** — Dashboard & simulation + demo scripts (Day 14)
* **M7** — Performance & security testing, final acceptance (Day 21)

*Adjust timeline per team velocity. PRD Phase breakdown provides a 14-day aggressive plan; this SDP adds QA, tests, and documentation for production readiness.*

## 13.3 Acceptance Criteria (concrete)

* All critical FRs pass acceptance suite.
* Chain verifier detects intentional tamper in demo.
* Compliance officer flow correctly transitions transaction state and creates audit entries.
* No financial imbalance after bulk processing (sum debits == sum credits for tested run).
* Security measures implemented: encrypted sensitive fields & role-based access.

---

# 14. Development, Testing, Deployment & Maintenance Processes

## 14.1 Development Practices

* **Branching:** GitFlow-like — `main` (release), `develop`, feature branches `feature/*`.
* **Code reviews:** Mandatory PR reviews, at least one approving reviewer.
* **Static analysis:** SpotBugs, SonarCloud, Checkstyle for Java; ESLint for frontend.
* **Code style:** Google Java style (or agreed team style); apply formatter via pre-commit.

## 14.2 CI/CD Pipeline (GitHub Actions example)

* On PR: compile, unit tests, code coverage, static analysis.
* On merge to `develop`: run integration tests (Docker Compose), build images, push to registry (staging).
* On tag `vX.Y.Z`: run performance tests, security scan, deploy to production via Helm/Ansible.

## 14.3 Deployment

* **Dev:** Docker Compose with local RabbitMQ & Postgres.
* **Staging/Prod:** Container images + Kubernetes (Helm charts) or Docker Compose on VMs; Postgres with WAL archiving & standby replica.
* **Secrets:** Vault or cloud KMS; do not store secrets in repo.

## 14.4 Backup & Recovery

* Daily DB backups via `pg_dump` and WAL shipping.
* Periodic restore drills: monthly restore from latest backup into staging.
* Audit log integrity backup: preserve canonical payloads & signed manifests.

## 14.5 Maintenance & Operational Runbooks

* Incident response for Data Integrity Breach: immediate read-only exposure of DB, run chain verifier, restore from immutable backup if needed, contact compliance/audit.
* Routine operations: sanctions list update (cron job), key rotation, health checks.

---

# 15. Tools, Technologies & Standards

* **Languages & Frameworks:** Java 21, Spring Boot 3.2+, React (Vite), Tailwind/MUI.
* **DB:** PostgreSQL 16; H2 for unit testing.
* **Messaging:** RabbitMQ.
* **Security libs:** Spring Security, BouncyCastle for crypto.
* **Hashing:** SHA-256 (Java MessageDigest).
* **Build & CI:** Maven/Gradle, GitHub Actions or GitLab CI.
* **Containerization:** Docker, Docker Compose; optionally Helm charts for k8s.
* **Monitoring:** Prometheus + Grafana, Loki/OpenSearch for logs.
* **Testing:** JUnit 5, Testcontainers for integration tests, Gatling or JMeter for load tests.
* **Static Analysis & SCA:** SonarQube, OWASP Dependency-Check.
* **Secrets Management:** Vault / AWS KMS.
* **Schema Migrations:** Flyway or Liquibase.
* **Standards:** ISO 20022 validation (XSD), OWASP Top 10 mitigations, PCI DSS controls for encryption at rest.

---

# 16. Security Architecture & Controls

## 16.1 Data protection

* AES-256 encryption of PII fields via JPA AttributeConverter; keys stored in Vault.
* TLS for all service-to-service and external endpoints; prefer mTLS for admin channels.
* Minimal DB role privileges; application uses low-privilege DB user.

## 16.2 Authentication & Authorization

* OAuth2 (authorization server) or external IdP integration; RBAC roles: SYSTEM, OPS, COMPLIANCE, AUDITOR.
* Admin Web UI requires role checks and audit trailing for every admin action.

## 16.3 Secrets & Keys

* Key rotation schedule (e.g., 90 days)
* Master key for AES is stored in Vault; do not log secrets.

## 16.4 Logging & Monitoring

* Immutable audit chain logs in DB; separate operational logs in centralized log system (Loki/ELK).
* Alerts for chain mismatch, failed integrity checks, excessive blocked rates, queue backlogs.

---

# 17. Maintenance, Support & Handoff

## 17.1 Hand-off artifacts

* Full source code & tags
* Database migration scripts & seed datasets
* CI/CD pipeline and environment definitions
* Developer onboarding doc, architecture diagrams, runbooks
* Demo scripts and dataset for recruiter demo

## 17.2 Maintenance schedule

* Weekly: security updates review
* Monthly: performance review and capacity planning
* Quarterly: key rotation & compliance report
* Annual: full DR drill & audit readiness check

---

# 18. Project Plan — Resource estimate & staffing (suggested)

* **1 Tech Lead / Senior Engineer (0.6 FTE)** — architecture, reviews, core domain logic.
* **2 Backend Engineers (1.6 FTE)** — Gateway, Compliance Engine, Ledger, Audit.
* **1 Frontend Engineer (0.6 FTE)** — Dashboard.
* **1 QA Engineer (0.6 FTE)** — automation, integration, perf tests.
* **1 DevOps Engineer (0.4 FTE)** — CI/CD, infra, monitoring.
* **Total:** ~5 engineers (mix of senior/mid) — time to MVP: 4 - 8 weeks depending on focus & parallelism.

---

# 19. Metrics, KPIs & Reporting

## 19.1 Operational Metrics

* **Messages processed per minute** (P95/P99 latency)
* **Avg processing latency (ingest → settle)**
* **Number of blocked transactions per day**
* **Audit chain verification pass rate**
* **DB lock contention rate**
* **Error rate (5xx) for inbound messages**

## 19.2 Business Metrics

* **Settlement volume ($) processed**
* **Number of manual reviews per blocked transaction**
* **Time-to-approve for Compliance Officer**

## 19.3 Monitoring Alerts

* Chain verifier failure → P1
* Message backlog > threshold → P2
* DB replication lag > threshold → P1
* Excess blocked rate (sudden spike) → P2

---

# 20. Appendix: Implementation notes, best practices & demo script

## 20.1 Implementation tips

* Use `@Transactional(propagation = REQUIRED)` on PaymentService; lock accounts via `findByIdForUpdate` PESSIMISTIC_WRITE.
* Canonical JSON: use Jackson with `ORDER_MAP_ENTRIES_BY_KEYS` and stable serialization for hashing.
* For fuzzy matching at scale, pre-index tokens via trigram/pg_trgm or build a BK-tree in memory with periodic reload.
* Keep audit writes sync within the same DB transaction for ordering guarantees; however consider eventual consistency for heavy loads with compensating checks.

## 20.2 Demo (Recruiter) script

1. Start services via Docker Compose (dev): DB, RabbitMQ, App, Dashboard.
2. Send a valid `pacs.008` message to queue; show dashboard: transfer PENDING → CLEARED → ledger entries → audit logs visible.
3. Send a message that fuzzily matches sanctions; show BLOCKED_AML in worklist.
4. As Compliance Officer, APPROVE it; show it moves to CLEARED and ledger entries created.
5. Use Hacker Simulation to corrupt a DB row; run Chain Verifier to show a breach alert and show which audit id failed.
6. Explain the chain hashing formula and show immutability proof.

---

# 21. Final checklist (for handoff)

* [ ] All FRs/NFRs implemented in code
* [ ] Unit, integration, performance tests passing
* [ ] CI/CD configured & tested
* [ ] Documentation (developer + runbook + compliance) complete
* [ ] Demo script validated
* [ ] Monitoring dashboards & alerts set up
* [ ] Backup & restore verified

