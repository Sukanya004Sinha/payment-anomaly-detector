

# Payment Anomaly Detector

AI-powered payment anomaly detection system built using **Spring Boot**, **OpenAI Function Calling**, **Apache Kafka**, and **PostgreSQL/H2**.

The application analyzes payment transactions, detects anomalies using LLM-based reasoning, routes events through Kafka, and stores audit records for investigation and compliance.

---

## Features

* AI-driven transaction analysis using OpenAI Function Calling
* Real-time event routing via Apache Kafka
* Idempotency support to prevent duplicate processing
* Audit trail storage in PostgreSQL or H2
* REST APIs for transaction analysis and reporting
* Docker Compose setup for local infrastructure
*  Unit and integration test support

---

## 🛠️ Tech Stack

| Layer            | Technology                    |
| ---------------- | ----------------------------- |
| Language         | Java 17                       |
| Framework        | Spring Boot 3.2               |
| AI               | OpenAI API (Function Calling) |
| Messaging        | Apache Kafka                  |
| Database         | PostgreSQL / H2               |
| Build Tool       | Maven                         |
| Containerization | Docker Compose                |

---

## 📂 Project Structure

```text
src/main/java/com/wex/anomaly/
├── controller/
│   ├── TransactionController.java
│   └── GlobalExceptionHandler.java
│
├── service/
│   ├── AnomalyDetectionService.java
│   ├── OpenAIService.java
│   └── KafkaPublisherService.java
│
├── model/
│   ├── TransactionRequest.java
│   ├── AnalysisResult.java
│   └── TransactionAnalysis.java
│
├── repository/
│   └── TransactionRepository.java
│
└── config/
    ├── OpenAIConfig.java
    └── KafkaConfig.java
```

---

# ⚙️ Setup Guide

## 1. Prerequisites

Ensure the following are installed:

```bash
java -version
mvn -version
docker -v
```

Required:

* Java 17+
* Maven 3.8+
* Docker Desktop

---

## 2. Create OpenAI API Key

1. Visit: [https://platform.openai.com/api-keys](https://platform.openai.com/api-keys)
2. Generate a new API key
3. Save it securely

---

## 3. Start Infrastructure

Launch Kafka, ZooKeeper, and Kafka UI:

```bash
docker-compose up -d zookeeper kafka kafka-ui
```

Verify services:

```bash
docker-compose ps
```

Expected:

```text
STATUS: Up
```

Kafka UI:

```text
http://localhost:8090
```

---

## 4. Configure OpenAI API Key

### Option A (Recommended)

#### Linux / macOS

```bash
export OPENAI_API_KEY=sk-your-key
```

#### Windows PowerShell

```powershell
$env:OPENAI_API_KEY="sk-your-key"
```

---

### Option B

Update `application.yml`:

```yaml
openai:
  api-key: sk-your-key
```

---

## 5. Run Application

```bash
mvn spring-boot:run
```

Expected startup message:

```text
Started PaymentAnomalyDetectorApplication
```

Application URL:

```text
http://localhost:8080
```

---

## 6. Access H2 Database (Optional)

Open:

```text
http://localhost:8080/h2-console
```

Configuration:

```text
JDBC URL : jdbc:h2:mem:anomalydb
Username : sa
Password : <blank>
```

---

# 🧪 API Testing

## Normal Transaction

**Expected Result:** `NORMAL`

```bash
curl -X POST http://localhost:8080/api/transactions/analyse \
-H "Content-Type: application/json" \
-d '{
  "transactionId":"TXN-001",
  "amount":55.00,
  "merchant":"Shell Fuel Station",
  "merchantCategory":"FUEL",
  "cardHolder":"John Driver",
  "location":"London",
  "usualSpend":60.00,
  "transactionTime":"10:30"
}'
```

Response:

```json
{
  "transactionId": "TXN-001",
  "status": "NORMAL",
  "riskLevel": null,
  "reason": "Amount within expected range, known fuel merchant",
  "kafkaTopic": "normal-transactions"
}
```

---

## Suspicious Transaction

**Expected Result:** `SUSPICIOUS`

```bash
curl -X POST http://localhost:8080/api/transactions/analyse \
-H "Content-Type: application/json" \
-d '{
  "transactionId":"TXN-002",
  "amount":85000.00,
  "merchant":"Unknown Vendor XYZ",
  "merchantCategory":"UNKNOWN",
  "cardHolder":"Jane Driver",
  "location":"Mumbai",
  "usualSpend":3000.00,
  "transactionTime":"03:00"
}'
```

Response:

```json
{
  "transactionId": "TXN-002",
  "status": "SUSPICIOUS",
  "riskLevel": "HIGH",
  "reason": "Amount is 28x above usual spend, unknown merchant, 3AM transaction",
  "kafkaTopic": "suspicious-transactions"
}
```

---

## Needs Review Transaction

**Expected Result:** `NEEDS_REVIEW`

```bash
curl -X POST http://localhost:8080/api/transactions/analyse \
-H "Content-Type: application/json" \
-d '{
  "transactionId":"TXN-003",
  "amount":15000.00,
  "merchant":"EV Charging Network Ltd",
  "merchantCategory":"EV_CHARGING",
  "cardHolder":"Bob Fleet",
  "location":"Manchester",
  "usualSpend":3000.00,
  "transactionTime":"14:00"
}'
```

Response:

```json
{
  "transactionId": "TXN-003",
  "status": "NEEDS_REVIEW",
  "reason": "Amount is 5x above usual spend",
  "department": "OPS",
  "kafkaTopic": "review-transactions"
}
```

---

## Duplicate Transaction Test

Re-submit:

```text
TXN-001
```

Expected behavior:

* Cached response returned
* OpenAI API is not called again
* Reason contains `[CACHED]`

---

## View All Transactions

```bash
curl http://localhost:8080/api/transactions
```

---

## Filter By Status

```bash
curl http://localhost:8080/api/transactions/status/SUSPICIOUS
```

---

# ✅ Running Tests

```bash
mvn test
```

---

# 🐘 PostgreSQL Profile

Start PostgreSQL:

```bash
docker-compose up -d postgres
```

Run application:

```bash
mvn spring-boot:run \
-Dspring-boot.run.profiles=postgres \
-Dspring-boot.run.arguments="--DB_USERNAME=postgres --DB_PASSWORD=postgres"
```

---

# 🏗️ System Architecture

```text
POST /api/transactions/analyse
                │
                ▼
    TransactionController
                │
                ▼
    AnomalyDetectionService
                │
                ▼
         OpenAIService
                │
                ▼
      OpenAI Function Call
                │
 ┌──────────────┼──────────────┐
 ▼              ▼              ▼
NORMAL     NEEDS_REVIEW   SUSPICIOUS
 │              │              │
 ▼              ▼              ▼
Kafka        Kafka         Kafka
Topic        Topic         Topic
 │              │              │
 └──────────────┼──────────────┘
                ▼
      TransactionRepository
                ▼
          PostgreSQL / H2
```

---

# 📨 Kafka Topics

| Topic                     | Purpose                |
| ------------------------- | ---------------------- |
| `normal-transactions`     | Approved transactions  |
| `review-transactions`     | Manual review required |
| `suspicious-transactions` | High-risk fraud alerts |

Kafka UI:

```text
http://localhost:8090



