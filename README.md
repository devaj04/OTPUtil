# OTPUtil – OTP Microservice

A production-ready OTP (One-Time Password) microservice built with **Spring Boot 3**, **Apache Cassandra**, and **Redis**.

---

## Features

| Feature | Detail |
|---|---|
| Input sanitisation & validation | Email/phone regex, purpose whitelist |
| Purpose-based OTP gating | Configurable via `otp.valid.purposes` |
| Supported purposes | `signupOtp`, `resetPasswordWithOtp`, `otpContactUpdateTemplate`, `deleteUserAccountTemplate`, `1307171619784284292` |
| OTP generation | Cryptographically-secure, configurable length (default 6 digits) |
| Delivery | Email (Spring Mail / SMTP) · SMS (CDAC NIC gateway) |
| Encryption | AES-256-GCM – OTP stored encrypted in Cassandra |
| Persistence | Apache Cassandra – `sunbird.otp_transactions` (with Cassandra-native TTL) |
| Fast cache / TTL | Redis – OTP cached with 60-second TTL |
| Attempt tracking | Max-attempt enforcement (default 3); OTP invalidated on breach |
| Auto-delete | Deleted from both Cassandra + Redis on success or TTL expiry |

---

## Prerequisites

| Dependency | Version |
|---|---|
| Java | 17+ |
| Apache Cassandra | 4.x |
| Redis | 7.x |
| SMTP server | Any (Gmail, SES, etc.) |

---

## Quick Start

### 1 · Configure Cassandra Keyspace

Run `src/main/resources/cassandra-schema.cql` against your Cassandra cluster:

```cql
CREATE KEYSPACE IF NOT EXISTS sunbird
    WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE IF NOT EXISTS sunbird.otp_transactions (
    key              text,
    type             text,
    otp              text,
    referencenumber  text,
    purpose          text,
    attemptedcount   int,
    maxcount         int,
    createdon        timestamp,
    lastmodifiedtime timestamp,
    PRIMARY KEY (key)
);
```

### 2 · Update `application.properties`

**Configure Cassandra, Redis, and notification channels:**

```properties
# Cassandra
spring.cassandra.keyspace-name=sunbird
spring.cassandra.contact-points=127.0.0.1
spring.cassandra.local-datacenter=datacenter1
spring.cassandra.username=cassandra
spring.cassandra.password=cassandra

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Mail (SMTP) - example using Gmail App Password
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your-email@gmail.com
spring.mail.password=your-16-char-app-password
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true

# OTP
otp.encryption.secret=Trigyn@OTPSecretKey#2024$Secure32B  # 32-byte AES-256 key
otp.valid.purposes=signupOtp,resetPasswordWithOtp,otpContactUpdateTemplate,deleteUserAccountTemplate,1307171619784284292
otp.default.purpose=signupOtp  # Default when purpose not provided

# Notification Settings
notification.email.from=your-email@gmail.com
notification.email.subject=Your OTP Code

# CDAC SMS Gateway (see SMS Integration section below for details)
cdac.sms.base-url=https://msdgweb.mgov.gov.in/esms/sendsmsrequestDLT
cdac.sms.sender-id=YOURSENDERID
cdac.sms.username=your-gateway-username
cdac.sms.password=your-gateway-password
cdac.sms.dept-secure-key=your-uuid-key
```

**Note on Gmail SMTP:**
- Enable "Less secure app access" OR use "App Passwords"
- For App Passwords: Generate a 16-character password in Google Account settings
- Set that as `spring.mail.password` (NOT your regular Gmail password)

### 3 · Build & Run

```bash
./mvnw clean package
java -jar target/OTPUtil-0.0.1-SNAPSHOT.jar
```

---

## API Reference

Base URL: `http://localhost:8080/api/v1/otp`

---

### Generate OTP

**POST** `/api/v1/otp/generate`

**Request with explicit purpose:**
```json
{
  "request": {
    "key": "user@example.com",
    "type": "email",
    "purpose": "resetPasswordWithOtp"
  }
}
```

**Request for signup (no purpose field):**
```json
{
  "request": {
    "key": "devajdildar1@gmail.com",
    "type": "email"
  }
}
```

**Request for signup via phone (no purpose field):**
```json
{
  "request": {
    "key": "1234567890",
    "type": "phone"
  }
}
```

**Parameters:**
- `type` must be `email` or `phone`  
- `purpose` is **optional** – if not provided (null/empty), request is treated as **signup OTP**  
- Valid purposes: `resetPasswordWithOtp`, `otpContactUpdateTemplate`, `deleteUserAccountTemplate`, `1307171619784284292`

**Response (200 OK):**
```json
{
  "response": {
    "otpSent": "yes",
    "referenceId": "550e8400-e29b-41d4-a716-446655440000",
    "generatedTs": 1714200000000,
    "validFor": 60
  },
  "status": {
    "code": "OTP_200",
    "message": "OTP generated and sent successfully."
  }
}
```

---

### Verify OTP

**POST** `/api/v1/otp/verify`

**Request:**
```json
{
  "request": {
    "key": "user@example.com",
    "type": "email",
    "otp": "123456",
    "referenceId": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

**Response (200 OK – success):**
```json
{
  "response": {
    "otpValidated": "yes",
    "referenceId": "550e8400-e29b-41d4-a716-446655440000"
  },
  "status": {
    "code": "OTP_200",
    "message": "OTP verified successfully."
  }
}
```

---

## Default Purpose (Signup OTP)

If `purpose` is **NOT provided** in the generate request, the service treats it as a **signup/registration OTP** and automatically defaults to the configured signup purpose.

The default is configurable via `otp.default.purpose` in `application.properties`.

**Signup Flow Examples:**

```bash
# Email signup - no purpose needed
curl -X POST http://localhost:8080/api/v1/otp/generate \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "key": "devajdildar1@gmail.com",
      "type": "email"
    }
  }'

# Phone signup - no purpose needed  
curl -X POST http://localhost:8080/api/v1/otp/generate \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "key": "1234567890",
      "type": "phone"
    }
  }'
```

Both requests automatically use the configured signup purpose from `otp.default.purpose`.

---

## OTP Purpose Reference

| Purpose | ID | Type | Use Case |
|---|---|---|---|
| Signup/Registration | `signupOtp` | email/phone | User signup/registration – **no purpose field needed** |
| Password Reset | `resetPasswordWithOtp` | email/phone | Reset user password |
| Contact Update | `otpContactUpdateTemplate` | email/phone | Verify new email or phone number |
| Delete Account | `deleteUserAccountTemplate` | email | Confirm account deletion |
| Delete Account (Phone) | `1307171619784284292` | phone | Delete account via phone (TRAI-compliant template) |


---

## Supported OTP Purposes

| Code | HTTP | Meaning |
|---|---|---|
| `OTP_200` | 200 | Success |
| `OTP_400` | 400 | Invalid input / validation failure |
| `OTP_401` | 400 | Invalid or unsupported purpose |
| `OTP_410` | 410 | OTP expired (TTL elapsed) |
| `OTP_422` | 422 | OTP is incorrect |
| `OTP_429` | 429 | Max attempts exceeded |
| `OTP_500` | 500 | Internal / encryption error |
| `OTP_503` | 503 | Notification delivery failure |

---

## Project Structure

```
src/main/java/com/trigyn/OTPUtil/
├── OtpUtilApplication.java          # Entry point
├── config/
│   ├── CassandraConfig.java         # Cassandra + CassandraOperations beans
│   └── RedisConfig.java             # Typed RedisTemplate<String, OtpCacheEntry>
├── controller/
│   └── OtpController.java           # /generate  /verify
├── dto/
│   ├── OtpCacheEntry.java           # Redis-cached OTP value object
│   ├── request/
│   │   ├── GenerateOtpRequest.java
│   │   ├── GenerateOtpRequestEnvelope.java
│   │   ├── VerifyOtpRequest.java
│   │   └── VerifyOtpRequestEnvelope.java
│   └── response/
│       ├── ApiResponse.java
│       ├── GenerateOtpResponseBody.java
│       ├── StatusDto.java
│       └── VerifyOtpResponseBody.java
├── entity/
│   └── OtpTransaction.java          # Cassandra @Table entity
├── exception/
│   ├── OtpException.java            # Business exceptions with HTTP status
│   └── GlobalExceptionHandler.java  # @RestControllerAdvice
├── repository/
│   └── OtpTransactionRepository.java
├── service/
│   ├── OtpService.java
│   ├── NotificationService.java
│   └── impl/
│       ├── OtpServiceImpl.java      # Core OTP logic
│       └── NotificationServiceImpl.java  # Email + SMS stub
└── util/
    ├── EncryptionUtil.java          # AES-256-GCM encrypt/decrypt
    ├── OtpGenerator.java            # SecureRandom OTP
    └── InputValidator.java          # Sanitise + validate inputs
```

---

## SMS Integration – CDAC NIC Gateway

SMS is delivered via the **CDAC NIC msdgweb gateway** (`https://msdgweb.mgov.gov.in/esms/sendsmsrequestDLT`).

### Configuration

Set these values in `application.properties` **or** as OS environment variables (env vars take precedence):

| Property | Env Variable | Description |
|---|---|---|
| `cdac.sms.base-url` | `cdac_sms_gateway_provider_base_url` | Gateway endpoint URL |
| `cdac.sms.sender-id` | `cdac_sms_gateway_provider_senderid` | Registered DLT sender-id |
| `cdac.sms.username` | `cdac_sms_gateway_provider_username` | Gateway login username |
| `cdac.sms.password` | `cdac_sms_gateway_provider_password` | Gateway password (SHA-1 hashed before transmission) |
| `cdac.sms.dept-secure-key` | `cdac_sms_gateway_provider_secure_key` | Department secret key (UUID) used for SHA-512 hash |

### DLT Template IDs

Every outbound SMS must carry a DLT-registered template-id (TRAI mandate).  
Register all OTP message templates on your telecom operator's DLT portal, then map them per purpose:

```properties
cdac.sms.dlt-templates.signupOtp=1007XXXXXXXXX2
cdac.sms.dlt-templates.resetPasswordWithOtp=1007XXXXXXXXXX
cdac.sms.dlt-templates.otpContactUpdateTemplate=1007XXXXXXXXXY
cdac.sms.dlt-templates.deleteUserAccountTemplate=1007XXXXXXXXXZ
cdac.sms.dlt-templates.1307171619784284292=1007XXXXXXXXX0
cdac.sms.dlt-templates.DEFAULT=1007XXXXXXXXX1  # fallback for unmapped purposes
```

### Security – request signing

Each SMS request is signed with two hashes:

| Hash | Algorithm | Used for |
|---|---|---|
| Password hash | SHA-1 (ISO-8859-1) | Gateway authentication |
| Request hash key | SHA-512 of `userName + senderId + content + secureKey` | Per-message integrity |

---

## Graceful Notification Failures

**Key Design:** OTP generation is **separated from notification delivery**.

- ✅ OTP is **always generated and stored** in Cassandra + Redis, regardless of notification success
- ⚠️ Notification failures (email/SMS) are **logged but do NOT fail the request**
- The response field `otpSent` indicates the **actual delivery status**:
  - `"yes"` = notification was successfully sent
  - `"no"` = notification delivery failed (SMTP error, SMS provider error, etc.)

**Example failure response:**
```json
{
  "response": {
    "otpSent": "no",
    "referenceId": "550e8400-e29b-41d4-a716-446655440000",
    "generatedTs": 1714200000000,
    "validFor": 60
  },
  "status": {
    "code": "OTP_206",
    "message": "OTP generated but failed to send notification. Please retry."
  }
}
```

**Client should:**
1. Check `otpSent` field in the response
2. If `"no"` → retry `/generate` again, or use alternate delivery method
3. OTP remains valid in the system for the full TTL period

---

## Error Codes and Status Handling

- OTP is encrypted with **AES-256-GCM** before being persisted.
- Change `otp.encryption.secret` to a 32-byte random key in production and store it in a secrets manager (Vault, AWS SM, etc.).
- Redis `OTP:<key>` entries auto-expire after `otp.ttl.seconds` (default 60 s).
- Cassandra rows also carry a native TTL so they self-purge without a cron job.

