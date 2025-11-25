[7 tools called]

# **📋 Entity Registry (KYC Center) - Implementation Gap Analysis & Plan**

## **🔍 Current State Assessment**

### **What We Have:**
- ✅ **Basic Account entity** with `iban`, `ownerName`, `balance`, `currency`
- ✅ **Transfer relationships** via sender/receiver IBANs
- ✅ **Audit logging** infrastructure for compliance tracking
- ✅ **User authentication** with role-based access
- ✅ **WebSocket support** for real-time updates

### **What's Missing (Critical Gaps):**

## **🚨 Major Architecture Gaps**

### **1. Entity/Customer Data Model**
**Missing:** Rich customer profiles beyond basic account ownership
```sql
-- Current: accounts table has only ownerName
-- Needed: customers, entities, demographics, risk_profiles
```

### **2. Document Management System**
**Missing:** Secure KYC document storage and management
```sql
-- Needed: documents table, file storage, expiry tracking
```

### **3. Entity Relationships & Networks**
**Missing:** Entity-entity and entity-account relationship mapping
```sql
-- Needed: entity_relationships, account_ownership tables
```

### **4. Advanced Search & Indexing**
**Missing:** Full-text search across multiple entity attributes
```sql
-- Needed: Elasticsearch or PostgreSQL full-text search
```

### **5. Risk Profiling Engine**
**Missing:** Entity-level risk scoring and monitoring
```sql
-- Needed: risk_profiles, risk_scoring_rules, alerts
```

---

## **📊 Detailed Requirements vs Current Capabilities**

| **UI Feature** | **Current Backend** | **Missing Components** | **Complexity** |
|---------------|-------------------|----------------------|---------------|
| **Search by name/document/account** | Basic IBAN search | Full-text search, document search | 🔴 High |
| **Entity details with risk rating** | Basic account data | Risk profiles, scoring engine | 🟡 Medium |
| **Document expiry alerts** | None | Document management, scheduling | 🟡 Medium |
| **Linked accounts graph** | Basic account ownership | Relationship modeling, graph queries | 🔴 High |
| **Demographics tab** | Only `ownerName` | Customer entity with full profile | 🟢 Low |
| **Documents tab** | None | Document storage, retrieval | 🟡 Medium |
| **Transaction History** | Transfer queries by IBAN | Entity-centric transaction views | 🟢 Low |
| **Alerts tab** | None | Alert system, notification engine | 🟡 Medium |
| **Network graph** | None | Graph database, relationship analysis | 🔴 High |

---

## **🚀 Implementation Plan - 3 Phases**

## **Phase 1: Core Entity Data Model (4-6 weeks)**

### **1.1 New Database Tables**
```sql
-- Customer/Entity master table
CREATE TABLE customers (
  id BIGSERIAL PRIMARY KEY,
  customer_id VARCHAR(50) UNIQUE NOT NULL, -- Business identifier
  full_name VARCHAR(255) NOT NULL,
  date_of_birth DATE,
  nationality VARCHAR(3), -- ISO country code
  tax_id VARCHAR(50),
  email VARCHAR(255),
  phone VARCHAR(50),
  address_street VARCHAR(255),
  address_city VARCHAR(100),
  address_country VARCHAR(3),
  address_postal_code VARCHAR(20),
  customer_type VARCHAR(20) NOT NULL, -- INDIVIDUAL, BUSINESS, TRUST
  risk_rating VARCHAR(10) DEFAULT 'LOW', -- LOW, MEDIUM, HIGH, CRITICAL
  kyc_status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED, EXPIRED
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  last_kyc_review DATE
);

-- Document management
CREATE TABLE customer_documents (
  id BIGSERIAL PRIMARY KEY,
  customer_id BIGINT REFERENCES customers(id),
  document_type VARCHAR(50) NOT NULL, -- PASSPORT, ID_CARD, PROOF_OF_ADDRESS, etc.
  document_number VARCHAR(100),
  issuing_country VARCHAR(3),
  issue_date DATE,
  expiry_date DATE,
  file_path VARCHAR(500), -- Secure file storage path
  verification_status VARCHAR(20) DEFAULT 'PENDING',
  verified_by BIGINT REFERENCES users(id),
  verified_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- Account ownership mapping
CREATE TABLE account_ownership (
  id BIGSERIAL PRIMARY KEY,
  account_id BIGINT REFERENCES accounts(id),
  customer_id BIGINT REFERENCES customers(id),
  ownership_type VARCHAR(20) NOT NULL, -- PRIMARY, JOINT, AUTHORIZED_SIGNER
  ownership_percentage DECIMAL(5,2) DEFAULT 100.00,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  UNIQUE(account_id, customer_id)
);

-- Risk profiles
CREATE TABLE risk_profiles (
  id BIGSERIAL PRIMARY KEY,
  customer_id BIGINT REFERENCES customers(id),
  overall_risk_score INTEGER DEFAULT 0, -- 0-100 scale
  risk_factors JSONB, -- Structured risk assessment data
  last_assessment DATE,
  next_review_date DATE,
  assessed_by BIGINT REFERENCES users(id),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);
```

### **1.2 Backend Components**
- `Customer` entity with full profile
- `CustomerDocument` entity with file handling
- `AccountOwnership` entity for relationship mapping
- `RiskProfile` entity for scoring
- Repository classes for all new entities
- Basic CRUD services

### **1.3 API Endpoints**
```java
// CustomerController
GET /api/v1/customers/search?name={name}&document={doc}&account={iban}
GET /api/v1/customers/{id}
POST /api/v1/customers
PUT /api/v1/customers/{id}
GET /api/v1/customers/{id}/accounts
GET /api/v1/customers/{id}/documents
GET /api/v1/customers/{id}/transactions
GET /api/v1/customers/{id}/risk-profile
```

## **Phase 2: Search & Document Management (3-4 weeks)**

### **2.1 Advanced Search Infrastructure**
- PostgreSQL full-text search on customer data
- Elasticsearch integration for complex queries
- Search by name, document number, account, email, phone

### **2.2 Document Management**
- Secure file storage (local/cloud)
- Document upload/verification workflows
- Expiry date monitoring and alerts
- Document type classification

### **2.3 Alert System**
- Document expiry notifications
- Risk threshold alerts
- KYC renewal reminders

## **Phase 3: Analytics & Relationships (4-6 weeks)**

### **3.1 Network Graph Engine**
- Entity-entity relationship mapping
- Graph database integration (Neo4j or PostgreSQL recursive queries)
- Relationship strength scoring
- Sanctions network analysis

### **3.2 Risk Analytics**
- Transaction pattern analysis
- Geographic risk assessment
- Peer group analysis
- Machine learning risk scoring

### **3.3 Advanced Visualizations**
- Interactive network graphs
- Risk heatmaps
- Transaction flow diagrams

---

## **📈 Effort Estimation & Dependencies**

### **Total Timeline: 11-16 weeks**

| **Component** | **Effort (weeks)** | **Dependencies** | **Risk Level** |
|---------------|-------------------|------------------|----------------|
| Core Data Model | 4-6 | Database migration | 🟢 Low |
| Entity Services | 2-3 | Data model | 🟢 Low |
| Search Engine | 2-3 | Entity services | 🟡 Medium |
| Document Management | 2-3 | File storage | 🟡 Medium |
| Alert System | 1-2 | Email/SMS service | 🟢 Low |
| Network Graph | 4-6 | Graph database | 🔴 High |
| Risk Analytics | 3-4 | ML infrastructure | 🔴 High |

### **Technology Stack Additions:**
- **Document Storage:** MinIO/S3 for secure file storage
- **Search:** Elasticsearch for advanced search capabilities
- **Graph DB:** Neo4j or PostgreSQL with graph extensions
- **ML:** Basic scoring algorithms (can start with rules-based)

---

## **🎯 Success Criteria**

### **Phase 1 (MVP)**
- ✅ Create/edit customer profiles with basic demographics
- ✅ Link customers to accounts
- ✅ View customer transaction history
- ✅ Basic search by name/account

### **Phase 2 (Enhanced)**
- ✅ Document upload and management
- ✅ Advanced search capabilities
- ✅ Expiry alerts and notifications
- ✅ Risk profile basic scoring

### **Phase 3 (Full KYC Center)**
- ✅ Interactive network graphs
- ✅ Advanced risk analytics
- ✅ Relationship mapping and analysis
- ✅ ML-enhanced risk scoring

---

## **⚡ Quick Wins for Demo (1-2 weeks)**

If you need a working demo quickly:

1. **Extend Account entity** with additional customer fields
2. **Add basic customer search** using existing account data
3. **Create simple document placeholder** (file paths only)
4. **Basic transaction history** by account ownership

This gives 70% of the core functionality with minimal changes to existing architecture.

---

## **🔧 Integration Points**

- **Transfer Processing:** Link to customer risk profiles during AML checks
- **Compliance Engine:** Use customer data for enhanced sanctions screening  
- **Audit System:** Track all customer data changes
- **Dashboard:** Show customer onboarding metrics

**Priority:** Start with Phase 1 to establish the core data model, then expand to search and advanced features. The graph analytics can be Phase 3 as it's the most complex.