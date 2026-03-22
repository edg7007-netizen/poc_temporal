# Temporal.io Lending Engine PoC

A Proof of Concept for a **Lending Engine** built with [Temporal.io](https://temporal.io/), Spring Boot 3.4 and Kotlin 2.1.

## Overview

This PoC demonstrates how Temporal.io workflows can manage the complete lifecycle of financial loans, including:
- 📋 Multiple lending **product types** (Term Loan & Bullet Loan)
- 💰 **Ledger management** for every financial event (disbursements, interest, fees, payments)
- 📈 **Interest accrual** – daily compounding (Term Loan) or fixed upfront (Bullet Loan)
- ⏰ **Late fee accrual** when payments are missed
- 🚫 **Manual loan cancellation** (before disbursement) via Temporal signals
- 🔄 **Full lifecycle management** via long-running Temporal workflows
- 😴 **Cooldown period** enforcement after full repayment

---

## Lending Products

| Feature | Term Loan | Bullet Loan |
|---|---|---|
| Interest accrual | Daily (18% APR) | Fixed upfront (8% flat) |
| Payment cycles | 12 monthly installments | Single payment at maturity |
| Withdrawals | Single | Single |
| Cooldown | 30 days | 15 days |
| Max amount | $50,000 | $10,000 |

---

## Architecture

```
REST API (Spring MVC)
       │
       ▼
LoanService ──────► WorkflowClient (Temporal)
       │                    │
LoanProductService          ▼
       │          LoanLifecycleWorkflow (long-running)
       │                    │
    JPA/H2         ┌────────┴────────┐
                   ▼                 ▼
            LoanActivities    LedgerActivities
            (DB operations)  (Ledger entries)
```

### Temporal Workflow: `LoanLifecycleWorkflow`

The workflow manages the entire loan lifecycle as a state machine:

```
APPROVED ──disburse──► ACTIVE
    │                    │
    │              (daily interest)
    │                    │
    │           payment signal / maturity
    │                    │
    │           ┌────────┴─────────┐
    │           ▼                  ▼
    │     PAID_OFF           DELINQUENT
    │         │                    │ (late fee + grace period)
    │    cooldown sleep       PAID_OFF (if paid during grace)
    │         │
    │    cooldown end
    │
    └─ cancelLoan signal ──► CANCELLED (if not yet disbursed)
```

**Temporal features used:**
- `@WorkflowMethod` – main lifecycle execution
- `@SignalMethod` – `receivePayment`, `cancelLoan`
- `@QueryMethod` – `getLoanStatus`, `getOutstandingBalance`, `isInCooldown`
- `Workflow.await()` – durable timer for payment windows
- `Workflow.sleep()` – durable sleep for cooldown periods

---

## REST API

### Loan Products
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/products` | List all active products |
| `GET` | `/api/products/{id}` | Get product details |

### Loans
| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/loans` | Create & disburse a new loan |
| `GET` | `/api/loans/{id}` | Get loan details |
| `GET` | `/api/loans/{id}/status` | Real-time status (from Temporal) |
| `GET` | `/api/loans/borrower/{id}` | All loans for a borrower |
| `POST` | `/api/loans/{id}/payments` | Submit a payment |
| `POST` | `/api/loans/{id}/cancel` | Cancel (pre-disbursement only) |
| `GET` | `/api/loans/{id}/ledger` | Full ledger history |
| `GET` | `/api/loans/{id}/payments` | Payment history |

---

## Running Locally

### Prerequisites
- Java 17+
- [Temporal CLI](https://docs.temporal.io/cli) (for local Temporal server)

### Start Temporal development server
```bash
temporal server start-dev
```

### Start the application
```bash
gradle bootRun
```

The application starts on `http://localhost:8080`. The H2 console is available at `http://localhost:8080/h2-console`.

Two products are pre-seeded:
- `Personal Term Loan` (id=1)
- `Short-Term Bullet Loan` (id=2)

### Example: Create a term loan
```bash
curl -X POST http://localhost:8080/api/loans \
  -H "Content-Type: application/json" \
  -d '{"borrowerId": "alice", "productId": 1, "principalAmount": 5000.00}'
```

### Example: Submit a payment
```bash
curl -X POST http://localhost:8080/api/loans/1/payments \
  -H "Content-Type: application/json" \
  -d '{"amount": 450.00, "referenceNumber": "PAY-001"}'
```

### Example: Cancel a loan
```bash
curl -X POST http://localhost:8080/api/loans/1/cancel \
  -H "Content-Type: application/json" \
  -d '{"reason": "Customer request"}'
```

---

## Running Tests
```bash
gradle test
```
23 tests covering workflows, activities, and REST endpoints.
