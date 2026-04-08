# 📦 Delivery Guarantees

 - In distributed systems, messages are not just sent — they are delivered under uncertainty.
 - Networks fail. Services crash. Messages can be duplicated or lost.
 - **Delivery guarantees define what a messaging system promises under these conditions.**

---

## 🧠 Why This Matters

If you don’t understand delivery guarantees:

 - You may lose critical data
 - You may process the same message multiple times
 - Your system may behave unpredictably under failure

---

## 🔁 The Three Delivery Guarantees

## 1. At-most-once

### 📌 Definition

A message is delivered **zero or one time**.

 - It may be lost
 - It will never be duplicated

---

### ⚙️ Behavior

 - Message is sent
 - If failure happens → message is lost
 - No retries

---

### 🧠 Use Case

 - Logging (non-critical)
 - Metrics collection

---

### ⚠️ Risk

> You can lose data.

---

## 2. At-least-once (Most Common)

### 📌 Definition

A message is delivered **one or more times**.

 - It will not be lost
 - It may be duplicated

---

### ⚙️ Behavior

- Message is retried until successful
- If consumer fails → message is reprocessed

---

### 🧠 Use Case

 - Order processing
 - Payment workflows
 - Notifications

---

### ⚠️ Trade-off

> You must handle duplicates.

---

### 💡 Example

```
// Problem: duplicate processing
chargeCustomer(orderId);

// Solution: idempotent handling
if (!alreadyCharged(orderId)) {
    chargeCustomer(orderId);
}
```

---

## 3. Exactly-once (Rarely Used)

### 📌 Definition

A message is delivered exactly once — no loss, no duplicates.

---

### ⚠️ Reality

 - Hard to achieve in distributed systems  
 - Requires transactions and coordination  
 - Adds performance overhead  

---

### 🧠 When It’s Used

 - Financial systems  
 - Critical transactions  

---

### 💡 Insight

| Guarantee     | Meaning                                                                 | Kafka                 | RabbitMQ                | SQS         |
|---------------|-------------------------------------------------------------------------|-----------------------|-------------------------|-------------|
| At-most-once  | Message delivered 0 or 1 times. May be lost.                            | ✅ (configurable)      | ✅ (auto-ACK)            | ✅           |
| At-least-once | Message delivered 1 or more times. Never lost, but duplicates possible. | ✅ (default)           | ✅ (manual ACK, default) | ✅ (default) |
| Exactly-once  | Message delivered exactly once. No loss, no duplicates.                 | ✅ (with transactions) | ❌ (not natively)        | ❌           |

In most real-world systems:

> Exactly-once is avoided in favor of:
> **At-least-once + idempotent consumers**

This approach is simpler and more scalable.