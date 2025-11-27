# Traffic Simulation Engine

This document describes the Fortress-Settlement traffic simulation engine, which generates realistic banking message traffic for testing and demonstration purposes.

## Overview

The traffic simulation engine creates continuous streams of ISO 20022 pacs.008 messages to simulate real-world banking transaction flows. It supports configurable message rates, realistic data generation, and compliance with banking regulations.

## Architecture

### Components

- **TrafficSimulatorService**: Core service managing simulation lifecycle
- **SimulationController**: REST API endpoints for control
- **Dashboard UI**: Web interface for monitoring and control

### Data Flow

```
Frontend UI → API Gateway → TrafficSimulatorService → RabbitMQ → GatewayService → Processing Pipeline
```

## Message Structure

### ISO 20022 pacs.008 Format

All generated messages conform to the ISO 20022 pacs.008 standard for Financial Institution Credit Transfers.

#### XML Structure

```xml
<?xml version="1.0" encoding="UTF-8"?>
<pacs008:Document xmlns:pacs008="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.10">
    <pacs008:FIToFICstmrCdtTrf>
        <pacs008:GrpHdr>
            <pacs008:MsgId>{UUID}</pacs008:MsgId>
            <pacs008:CreDtTm>{ISO8601_TIMESTAMP}</pacs008:CreDtTm>
            <pacs008:NbOfTxs>1</pacs008:NbOfTxs>
            <pacs008:TtlIntrBkSttlmAmt Ccy="{CURRENCY}">{AMOUNT}</pacs008:TtlIntrBkSttlmAmt>
            <pacs008:IntrBkSttlmDt>{DATE}</pacs008:IntrBkSttlmDt>
            <pacs008:SttlmInf>
                <pacs008:SttlmMtd>CLRG</pacs008:SttlmMtd>
            </pacs008:SttlmInf>
        </pacs008:GrpHdr>
        <pacs008:CdtTrfTxInf>
            <pacs008:PmtId>
                <pacs008:EndToEndId>E2E-{SEQUENCE}</pacs008:EndToEndId>
            </pacs008:PmtId>
            <pacs008:IntrBkSttlmAmt Ccy="{CURRENCY}">{AMOUNT}</pacs008:IntrBkSttlmAmt>
            <pacs008:ChrgBr>SLEV</pacs008:ChrgBr>
            <pacs008:Dbtr>
                <pacs008:Nm>{SENDER_NAME}</pacs008:Nm>
            </pacs008:Dbtr>
            <pacs008:DbtrAgt>
                <pacs008:FinInstnId>
                    <pacs008:Nm>Deutsche Bank AG</pacs008:Nm>
                </pacs008:FinInstnId>
            </pacs008:DbtrAgt>
            <pacs008:DbtrAcct>
                <pacs008:Id>
                    <pacs008:IBAN>{SENDER_IBAN}</pacs008:IBAN>
                </pacs008:Id>
            </pacs008:DbtrAcct>
            <pacs008:Cdtr>
                <pacs008:Nm>{RECEIVER_NAME}</pacs008:Nm>
            </pacs008:Cdtr>
            <pacs008:CdtrAgt>
                <pacs008:FinInstnId>
                    <pacs008:Nm>HSBC Bank PLC</pacs008:Nm>
                </pacs008:FinInstnId>
            </pacs008:CdtrAgt>
            <pacs008:CdtrAcct>
                <pacs008:Id>
                    <pacs008:IBAN>{RECEIVER_IBAN}</pacs008:IBAN>
                </pacs008:Id>
            </pacs008:CdtrAcct>
        </pacs008:CdtTrfTxInf>
    </pacs008:FIToFICstmrCdtTrf>
</pacs008:Document>
```

### Message Fields

| Field | Description | Example |
|-------|-------------|---------|
| `MsgId` | Unique message identifier | `550e8400-e29b-41d4-a716-446655440000` |
| `CreDtTm` | Creation timestamp | `2025-11-27T14:30:15.123Z` |
| `TtlIntrBkSttlmAmt` | Total settlement amount | `1500.00` |
| `Ccy` | Currency code | `EUR`, `USD`, `GBP` |
| `IntrBkSttlmDt` | Settlement date | `2025-11-27` |
| `EndToEndId` | End-to-end identifier | `E2E-0000123` |
| `ChrgBr` | Charge bearer | `SLEV` (SHA) |
| `Dbtr.Nm` | Debtor (sender) name | `Deutsche Bank AG` |
| `Cdtr.Nm` | Creditor (receiver) name | `HSBC Bank PLC` |
| `IBAN` | International Bank Account Number | `DE89370400440532013000` |

## Data Generation

### Amount Distribution

Messages use weighted random amounts to simulate realistic transaction patterns:

- **70%**: Small transactions (€100-€5,000)
- **20%**: Medium transactions (€5,000-€50,000)
- **10%**: Large transactions (€50,000-€1,000,000)

### Currency Distribution

Realistic currency weighting based on Eurozone and international trade:

- **EUR**: 50% (Eurozone dominance)
- **USD**: 20% (International trade)
- **GBP**: 10% (UK financial hub)
- **CHF**: 10% (Swiss banking)
- **Others**: 10% (SEK, NOK, DKK, PLN)

### IBAN Pool

System uses a pool of 230+ realistic IBANs from major European banks:

- **Germany**: 100+ Deutsche Bank, Commerzbank IBANs
- **UK**: Royal Bank of Scotland, Barclays IBANs
- **France**: BNP Paribas, Société Générale IBANs
- **Spain**: Santander, BBVA IBANs
- **Italy**: Unicredit IBANs
- **Netherlands**: ING Group IBANs
- **Switzerland**: UBS, Credit Suisse IBANs

### Sanctions Testing

**1%** of messages include potential sanctions matches for compliance testing:

- Sender names: `"Osama Bin Laden"`, `"Kim Jong Un"`, etc.
- Names trigger fuzzy matching algorithms with 85%+ similarity scores

## API Endpoints

### Control Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/simulation/start?messagesPerSecond=X` | Start simulation |
| `POST` | `/api/v1/simulation/stop` | Stop simulation |
| `GET` | `/api/v1/simulation/status` | Get simulation status |
| `POST` | `/api/v1/simulation/send-test-message` | Send single test message |

### Status Response

```json
{
  "running": true,
  "devMode": true,
  "messagesSent": 150,
  "uptime": 45000
}
```

## Usage Examples

### Start Simulation

```bash
# Start at 5 messages per second
curl -X POST http://localhost:8080/api/v1/simulation/start?messagesPerSecond=5

# Start at 10 messages per second
curl -X POST http://localhost:8080/api/v1/simulation/start?messagesPerSecond=10
```

### Monitor Status

```bash
# Check current status
curl http://localhost:8080/api/v1/simulation/status
```

### Stop Simulation

```bash
# Stop running simulation
curl -X POST http://localhost:8080/api/v1/simulation/stop
```

### Single Message Test

```bash
# Send one test message
curl -X POST http://localhost:8080/api/v1/simulation/send-test-message
```

## Performance Characteristics

### Throughput Targets

- **Target**: 2000 messages/minute (33.33 msg/sec)
- **Tested**: Up to 10 msg/sec sustained
- **Latency**: <50ms per message generation

### Message Validation

All generated messages are automatically validated against:
- ISO 20022 pacs.008 XSD schema
- IBAN format validation
- Amount range validation
- Required field completeness

## Safety Features

### Development Mode Only

- Simulation only works when `app.dev-mode=true`
- Prevents accidental production traffic generation
- Clear UI warnings when disabled

### Rate Limiting

- Configurable maximum rates
- Automatic cleanup on service shutdown
- Resource monitoring and throttling

### Compliance Testing

- Realistic sanctions name injection
- Audit trail generation
- Chain integrity verification

## Monitoring & Troubleshooting

### Logs

Monitor simulation activity in backend logs:

```
INFO  - Traffic simulation: 100 messages sent
INFO  - Starting traffic simulation at 5 messages per second
INFO  - Traffic simulation stopped. Total messages sent: 150
```

### RabbitMQ Monitoring

Check message queue status:

```bash
# View queue status
docker compose exec rabbitmq rabbitmqctl list_queues name messages
```

### Database Verification

Verify processed messages:

```sql
SELECT COUNT(*) as processed_messages FROM transfers;
SELECT status, COUNT(*) FROM transfers GROUP BY status;
```

## Integration Testing

Use the provided test script for comprehensive validation:

```bash
# Run basic tests
./test_simulation.sh basic

# Run load tests
./test_simulation.sh load

# Run full test suite
./test_simulation.sh full
```

## Configuration

### Application Properties

```properties
# Enable development mode
app.dev-mode=true

# Simulation settings (optional)
simulation.default-rate=5
simulation.max-rate=50
```

### Environment Variables

```bash
# Override backend URL
BACKEND_URL=http://my-server:8080 ./test_simulation.sh basic
```
