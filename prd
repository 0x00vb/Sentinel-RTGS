# PART 1: PRODUCT REQUIREMENTS DOCUMENT (PRD)

**Project Name:** Fortress-Settlement
**Version:** 1.0
**Type:** RTGS (Real-Time Gross Settlement) Core Banking Engine
**Owner:** [Your Name]

## 1. Executive Summary
Fortress-Settlement is a high-integrity banking backend designed to process high-value international wire transfers. Unlike typical CRUD apps, this system prioritizes **Financial Accuracy (ACID)**, **Regulatory Compliance (SOX/OFAC)**, and **Standardization (ISO 20022)** over ease of development.

It features an event-driven architecture to ingest SWIFT-like messages, passes them through an algorithmic sanctions screen, and settles them into a tamper-proof ledger.

## 2. System Architecture
**Pattern:** Event-Driven Modular Monolith.
**Rationale:** Monolithic core for strict ACID transaction safety; Event-Driven (RabbitMQ) for resilient message handling and traffic smoothing.



### 2.1 Tech Stack
* **Language:** Java 21 (LTS)
* **Framework:** Spring Boot 3.2+ (Web, Data JPA, AMQP, WebSocket)
* **Database:** PostgreSQL 16 (Production), H2 (Test)
* **Message Broker:** RabbitMQ
* **Frontend:** React (Vite) + Tailwind/MUI (Internal Ops Dashboard)
* **Security:** Spring Security, BouncyCastle (Encryption)
* **Containerization:** Docker & Docker Compose

## 3. Functional Requirements

### Module A: The Gateway (Ingestion)
* **FR-01:** System must listen to a RabbitMQ queue `bank.inbound` for ISO 20022 XML messages (`pacs.008` - Financial Institution Credit Transfer).
* **FR-02:** System must validate the XML against the official XSD schema. Invalid messages are rejected with a `pacs.002` (Status Report) sent to `bank.outbound`.
* **FR-03:** Idempotency: Duplicate Message IDs must be detected and discarded to prevent double-spending.

### Module B: The Compliance Engine (The "Shield")
* **FR-04 (OFAC/AML):** Before processing, the Sender and Receiver names must be checked against a local `sanctions_blacklist` table.
* **FR-05 (Fuzzy Matching):** The check must use the **Levenshtein Distance Algorithm**.
    * *Logic:* If Similarity Score > 85%, the transaction state is set to `BLOCKED_AML`.
* **FR-06 (Manual Review):** An API endpoint must exist for a Compliance Officer to `APPROVE` or `REJECT` a blocked transaction.

### Module C: The Ledger (The Core)
* **FR-07:** All successful transfers must update the `accounts` table using `PESSIMISTIC_WRITE` locking to ensure thread safety.
* **FR-08:** Double-Entry Accounting: Every transfer generates two `ledger_entries` (One Debit, One Credit) that sum to zero.

### Module D: The SOX Auditor (The "Black Box")
* **FR-09 (Immutability):** Every state change (Created, Blocked, Cleared) is written to `audit_logs`.
* **FR-10 (Hash Chaining):** Each log entry contains a `current_hash` derived from `SHA256(Payload + Previous_Hash)`.
* **FR-11 (Tamper Detection):** A service must exist to traverse the chain and validate all hashes. If a mismatch is found, the system flags a "Data Integrity Breach."

### Module E: Operations Dashboard (UI)
* **FR-12:** Real-time visualization of the Audit Log via WebSockets (`/topic/logs`).
* **FR-13:** A "Sanctions Worklist" for admins to review blocked wires.
* **FR-14:** A "Hacker Simulation" button that manually corrupts a database row to demonstrate FR-11 in real-time.

## 4. Data Model (Key Tables)

| Table Name | Purpose | Key Columns |
| :--- | :--- | :--- |
| `accounts` | User Balances | `id`, `iban`, `balance` (Safe), `currency` |
| `transfers` | Wire State Machine | `msg_id` (Unique), `amount`, `status` (PENDING/CLEARED/BLOCKED) |
| `sanctions_list` | OFAC Watchlist | `name`, `risk_score` |
| `audit_logs` | SOX Compliance | `entity_id`, `action`, `prev_hash`, `curr_hash` |

---

# PART 2: THE REGULATORY MANUAL (Why you built this)

*Memorize this. This is your dictionary when speaking to the recruiter.*

### 1. SOX (Sarbanes-Oxley Act)
* **The Rule:** Section 404 requires internal controls to ensure financial data is accurate and cannot be tampered with by insiders.
* **Your Implementation:** The **Cryptographic Hash Chain**. It proves that not even the DBA (Database Administrator) has altered the logs, because doing so would break the mathematical chain of hashes.

### 2. OFAC (Office of Foreign Assets Control) / AML
* **The Rule:** Banks are strictly forbidden from processing transactions for sanctioned entities (terrorists, embargoed nations).
* **Your Implementation:** The **Levenshtein Fuzzy Matcher**. You don't just check for "Osama Bin Laden"; you check for "Osama B. Laden" or "Usama Bin Laden" to prevent evasion.

### 3. ISO 20022
* **The Standard:** The global language for electronic data interchange (replacing old MT messages).
* **Your Implementation:** You use **JAXB** to parse standard `pacs.008` XML. This makes your system interoperable with the real SWIFT network.

### 4. PCI DSS (Payment Card Industry Data Security Standard)
* **The Rule:** Protect cardholder/sensitive data.
* **Your Implementation:** **AES-256 Encryption** at rest. Sensitive fields (like Tax IDs) are encrypted before they hit the disk using JPA AttributeConverters.

---

# PART 3: THE PITCH SCRIPT

**Scenario:** You are in an interview. The recruiter asks: *"Tell me about a challenging project."*

### The Hook (15 Seconds)
> "I built **Fortress-Settlement**, a Real-Time Gross Settlement (RTGS) engine designed to handle high-value international wires. I wanted to move beyond simple CRUD apps and tackle the real engineering constraints of banking: **Concurrency, Compliance, and Auditability**."

### The Architecture (30 Seconds)
> "I chose a **Modular Monolith** architecture for strict ACID compliance, but I used **RabbitMQ** to handle message ingestion asynchronously. This allows the system to accept thousands of ISO 20022 XML messages without crashing, even if the ledger is busy."

### The "War Story" (The "Senior" Moment)
> "The hardest part was implementing the **SOX Compliance Layer**. I needed a way to guarantee that no transaction data could be altered after the fact.
>
> I built a **Blockchain-like Hash Chain** inside PostgreSQL. Every audit log contains the SHA-256 hash of the previous log. If someone manually updates a database row to steal money, the chain breaks. I built a monitoring dashboard that visualizes this integrity check in real-time."

### The Call to Action (The Demo)
> "I actually have the Ops Dashboard running on my laptop. I can show you the system processing live XML traffic, block a transaction using the AML algorithm, and then demonstrate the Tamper Detection by manually hacking the database. Want to see it?"

---

# PART 4: DEVELOPMENT ROADMAP (The "How-To")

**Phase 1: The Core (Days 1-3)**
1.  Setup Spring Boot + Postgres + Docker.
2.  Implement `Account` and `Transfer` entities.
3.  Write the `PaymentService` with `@Transactional` and `PESSIMISTIC_WRITE`.

**Phase 2: The Gateway (Days 4-6)**
1.  Setup RabbitMQ in Docker.
2.  Download `pacs.008` XSD schema.
3.  Implement JAXB Parser to convert XML String -> Java Object.
4.  Connect RabbitMQ Listener -> PaymentService.

**Phase 3: The Shield (Days 7-10)**
1.  Implement `FuzzyMatchService` (Levenshtein).
2.  Implement `AuditListener` (JPA) for the Hash Chain.
3.  Implement `CryptoConverter` for PCI encryption.

**Phase 4: The Interface (Days 11-14)**
1.  Build React Dashboard.
2.  Connect WebSocket (`/topic/logs`).
3.  Build the "Simulation Bot" to flood RabbitMQ with traffic.

This is a Senior-grade portfolio piece. It has "Business Value" written all over it. Good luck.