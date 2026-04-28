# OTPUtil – OTP Microservice

A production-ready OTP (One-Time Password) microservice built with **Spring Boot 3**, **Apache Cassandra**, and **Redis**.

---

## Features

| Feature | Detail |
|---|---|
| Input sanitisation & validation | Email/phone regex, purpose whitelist |
| Purpose-based OTP gating | Configurable via `otp.valid.purposes` |
| Supported purposes | `resetPasswordWithOtp`, `otpContactUpdateTemplate`, `deleteUserAccountTemplate`, `1107175041055648550` |
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

# OTP Settings
otp.encryption.secret=Trigyn@OTPSecretKey#2024$Secure32B  # 32-byte AES-256 key
otp.valid.purposes=signupOtp,resetPasswordWithOtp,otpContactUpdateTemplate,deleteUserAccountTemplate,1107175041055648550
otp.default.purpose=signupOtp  # Default when purpose not provided
otp.ttl.seconds=60
otp.max.attempts=3

# Rate Limiting (per key, per hour)
otp.ratelimit.max-per-hour=3
otp.ratelimit.window-seconds=3600  # TTL window in seconds (1 hour)
# When this window expires, attemptedCount resets to 0 automatically

# Notification Settings
notification.email.from=your-email@gmail.com

# CDAC SMS Gateway
sms.base-url=https://msdgweb.mgov.gov.in/esms/sendsmsrequestDLT
sms.sender-id=YOURSENDERID
sms.username=your-gateway-username
sms.password=your-gateway-password
sms.dept-secure-key=your-uuid-key

# DLT Template IDs
sms.dlt-templates.signupOtp=1007XXXXXXXXX2
sms.dlt-templates.resetPasswordWithOtp=1007XXXXXXXXXX
sms.dlt-templates.otpContactUpdateTemplate=1007XXXXXXXXXY
sms.dlt-templates.deleteUserAccountPhone=1007XXXXXXXXX0

# Logging Configuration (Optional - for debugging)
logging.level.root=INFO
logging.level.com.trigyn.OTPUtil=WARN
logging.level.com.trigyn.OTPUtil.service.impl.OtpServiceImpl=WARN
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
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
    "key": "devaj04@gmail.com",
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
      "key": "devaj04@gmail.com",
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
| Contact Update | `otpContactUpdateTemplate` | email/phone | Verify email or phone number |
| Delete Account | `deleteUserAccountTemplate` | email | Confirm account deletion |
| Delete Account (Phone) | `1107175041055648550` | phone | Delete account via phone (TRAI-compliant template) |


---

## Supported OTP Purposes

| Code | HTTP | Meaning |
|---|---|---|
| `OTP_200` | 200 | Success |
| `OTP_206` | 206 | OTP generated but notification delivery failed |
| `OTP_400` | 400 | Invalid input / validation failure |
| `OTP_401` | 400 | Invalid or unsupported purpose |
| `OTP_409` | 409 | Active OTP already exists – max generation attempts exceeded within the hour |
| `OTP_410` | 410 | OTP expired (TTL elapsed) |
| `OTP_422` | 422 | OTP is incorrect |
| `OTP_429` | 429 | Max verification attempts exceeded |
| `OTP_500` | 500 | Internal / encryption error |
| `OTP_503` | 503 | Notification delivery failure |

---

## Recent Improvements (v0.0.1)

### ✅ Enhanced Rate Limit Management
- Automatic reset of `attemptedCount` to 0 when TTL window expires
- Improved logging with detailed timestamp and elapsed time information
- Seamless user experience without deletion/recreation overhead

### ✅ Enhanced SMS Gateway Response Parsing
- Comprehensive error detection including: `whitelisted`, `not authorized`, `blocked`, `suspended`, etc.
- Fail-secure approach: ambiguous responses treated as failures
- Accurate `otpSent` status reflecting actual delivery success/failure

### ✅ Improved Logging & Debugging
- Single-line formatted logs for better readability
- Emoji indicators (📤, 📊, 🔄, ❌, ⚠️) for quick visual scanning
- Detailed rate limit check logs showing elapsed time vs TTL window
- SMS delivery status logs with gateway response information

### ✅ Cleaned Dependencies
- Removed duplicate Lombok dependency
- Optimized pom.xml with only actively-used dependencies
- Proper Maven configuration best practices

---

```
src/main/java/com/trigyn/OTPUtil/
├── OtpUtilApplication.java          # Entry point
├── config/
│   ├── CassandraConfig.java         # Cassandra + CassandraOperations beans
│   └── RedisConfig.java             # Typed RedisTemplate<String, OtpCacheEntry>
├── controller/
│   └── OtpController.java           # POST /generate, POST /verify
├── dto/
│   ├── OtpCacheEntry.java           # Redis-cached OTP value object
│   ├── request/
│   │   ├── GenerateOtpRequest.java
│   │   ├── GenerateOtpRequestEnvelope.java
│   │   ├── VerifyOtpRequest.java
│   │   └── VerifyOtpRequestEnvelope.java
│   └── response/
│       ├── ApiResponse.java         # Generic response envelope
│       ├── GenerateOtpResponseBody.java
│       ├── StatusDto.java
│       └── VerifyOtpResponseBody.java
├── email/
│   ├── EmailConnection.java         # SMTP transport lifecycle management
│   ├── EmailTemplateService.java    # Velocity template loader
│   └── SendEmail.java               # SMTP email dispatcher
├── entity/
│   ├── OtpTransaction.java          # Cassandra table: otp_transactions
│   └── OtpRateLimit.java            # Cassandra table: otp_rate_limit (rate limiting)
├── exception/
│   ├── OtpException.java            # Business exceptions with HTTP status
│   └── GlobalExceptionHandler.java  # @RestControllerAdvice for consistent error responses
├── repository/
│   ├── OtpTransactionRepository.java  # Cassandra CRUD for otp_transactions
│   └── OtpRateLimitRepository.java    # Cassandra CRUD for otp_rate_limit
├── service/
│   ├── OtpService.java              # Interface
│   ├── NotificationService.java     # Interface
│   └── impl/
│       ├── OtpServiceImpl.java       # Core OTP logic (generate + verify + rate limiting)
│       └── NotificationServiceImpl.java  # Email (Velocity) + SMS (CDAC gateway)
├── sms/
│   └── CDACGatewaySmsProvider.java   # CDAC NIC SMS gateway integration
└── util/
    ├── EncryptionUtil.java          # AES-256-GCM encrypt/decrypt
    ├── OtpGenerator.java            # SecureRandom OTP generator
    ├── InputValidator.java          # Sanitise + validate email/phone/purpose
    └── ProjectUtil.java             # Velocity context builder
```

---

## Rate Limiting

The OTP service enforces **per-key rate limiting**:

- **Max OTP generations per hour:** 3 per unique key (email/phone)
- **Window:** 1 hour (3600 seconds) - configurable via `otp.ratelimit.window-seconds`
- **TTL Reset:** When rate limit window expires, the `attemptedCount` is **automatically reset to 0**
- **Auto-cleanup:** Rate limit records auto-expire after the window via Cassandra TTL

**Rate Limit Reset Feature:**
When the TTL window expires for a rate-limited key, the counter is intelligently reset on the next OTP generation request:
```
Time 0s:       User generates OTP #1 (count=1)
Time 1800s:    User generates OTP #2 (count=2)
Time 3600s+:   TTL expires, user generates OTP #3
               → System detects TTL expiration
               → Counter reset to 0
               → OTP #3 accepted (count=1 after increment)
```

**Error response when limit exceeded:**
```json
{
  "response": null,
  "status": {
    "code": "OTP_409",
    "message": "An OTP has already been sent. Please verify it or wait 45 seconds for it to expire before requesting a new one."
  }
}
```

**Error response when delivery fails:**
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

---

## Enhanced Logging & Debugging

The service provides comprehensive single-line formatted logs for easy monitoring and debugging.

### Rate Limit Logging

```
⏱️  RATE LIMIT CHECK - key=user@example.com | timestamp=2026-04-28T14:40:00 | current=2026-04-28T14:45:05 | elapsed=305s | window=300s | count=3/3 | expired=✅ YES

🔄 RATE LIMIT RESET TRIGGERED! - key=user@example.com | elapsed=305s | window=300s | cassandra_updated | redis_cleared

📊 RATE LIMIT UPDATED - key=user@example.com | oldCount=0 | newCount=1/3

❌ RATE LIMIT EXCEEDED - key=user@example.com | attempted=3 | max=3
```

### SMS Delivery Logging

```
📤 SMS SENDING - mobile=919876543210 | purpose=signupOtp | template=1107175041055648550

📊 SMS ACCEPTED - mobile=919876543210 | gateway_response='Success'

📊 SMS DELIVERY FAILED - mobile=919876543210 | gateway_response='IP not Whitelisted'

❌ SMS ERROR - mobile=919876543210 | type=SSL/Crypto | error=TLS error
```

### Enable Debug Logging

Add to `application.properties`:
```properties
logging.level.com.trigyn.OTPUtil.service.impl.OtpServiceImpl=WARN
logging.level.com.trigyn.OTPUtil.sms.CDACGatewaySmsProvider=WARN
```

The logs use emoji indicators for quick visual scanning:
- 📤 SMS/OTP sending
- 📊 Status updates and checks
- 🔄 Reset/reload operations
- ❌ Errors and failures
- ⚠️  Warnings

---

## SMS Gateway Response Handling

The service intelligently parses CDAC gateway responses to accurately report delivery status:

### Success Indicators (returns `otpSent: "yes"`):
- `success`, `accepted`, `submitted`
- `"1"` or `<status>1</status>`
- `ok`

### Failure Indicators (returns `otpSent: "no"`):
- `error`, `failed`, `invalid`
- `"0"` or `<status>0</status>`
- `whitelisted` (IP not whitelisted)
- `not authorized`, `authentication failed`
- `blocked`, `suspended`, `invalid template`

### Example Gateway Error Handling:

**When gateway returns:** `"IP not Whitelisted"`
```json
{
  "response": {
    "otpSent": "no",               
    "referenceId": "...",
    "generatedTs": "...",
    "validFor": 60
  },
  "status": {
    "code": "OTP_206",
    "message": "OTP generated but failed to send notification. Please retry."
  }
}
```

---

All timestamps are stored and returned in **Indian Standard Time (IST, UTC+5:30)**:

| Field | Storage | Example |
|---|---|---|
| `generatedTs` (API response) | ISO-8601 with IST offset | `2026-04-28T13:26:53+05:30` |
| `createdOn` (Cassandra) | ISO-8601 with IST offset | `2026-04-28T13:26:53+05:30` |
| `lastModifiedTime` (Cassandra) | ISO-8601 with IST offset | `2026-04-28T13:26:53+05:30` |
| `timestamp` (Rate Limit) | ISO-8601 with IST offset | `2026-04-28T13:26:53+05:30` |

---

## Email Templates

All email templates use **Velocity templating** and follow a consistent **DIKSHA-branded format**:

```
OTP to verify your email ID on DIKSHA is {OTP}. This is valid for {seconds} seconds only.
```

**Templates:**
- `signup-otp.vm` – Signup/registration OTP
- `reset-password-otp.vm` – Password reset OTP
- `contact-update-otp.vm` – Contact verification OTP
- `delete-account-otp.vm` – Account deletion confirmation OTP

---

## SMS Integration with CDAC NIC Gateway

SMS is delivered via the **CDAC NIC msdgweb gateway** (`https://msdgweb.mgov.gov.in/esms/sendsmsrequestDLT`).

### Configuration

Set these values in `application.properties`:

| Property | Description |
|---|---|
| `sms.base-url` | Gateway endpoint URL (default: `https://msdgweb.mgov.gov.in/esms/sendsmsrequestDLT`) |
| `sms.sender-id` | Registered DLT sender-id |
| `sms.username` | Gateway login username |
| `sms.password` | Gateway password (SHA-1 hashed before transmission) |
| `sms.dept-secure-key` | Department secret key (UUID) used for SHA-512 hash |

### DLT Template IDs

Every outbound SMS must carry a DLT-registered template-id (TRAI mandate).  
Register all OTP message templates on your telecom operator's DLT portal, then map them per purpose:

```properties
sms.dlt-templates.signupOtp=1007XXXXXXXXX2
sms.dlt-templates.resetPasswordWithOtp=1007XXXXXXXXXX
sms.dlt-templates.otpContactUpdateTemplate=1007XXXXXXXXXY
sms.dlt-templates.deleteUserAccountPhone=1007XXXXXXXXX0
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
  - `"no"` = notification delivery failed (SMTP error, SMS provider error, IP not whitelisted, etc.)

**The service correctly differentiates between:**
- ✅ OTP_200: OTP generated AND notification sent successfully
- ⚠️ OTP_206: OTP generated BUT notification delivery failed

**Example: Gateway IP Not Whitelisted**
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
4. Check logs with gateway response details for debugging

**Common SMS Gateway Failures (Logged with Details):**
- `IP not Whitelisted` → Configure IP in gateway
- `Authentication failed` → Verify credentials
- `Invalid template` → Check DLT template registration
- `Blocked/Suspended` → Check account status

---

## Security Notes

- OTP is encrypted with **AES-256-GCM** before being persisted.
- Change `otp.encryption.secret` to a 32-byte random key in production and store it in a secrets manager (Vault, AWS SM, etc.).
- Redis `OTP:<key>` entries auto-expire after `otp.ttl.seconds` (default 60 s).
- Cassandra rows also carry a native TTL so they self-purge without a cron job.
- All timestamps use **IST (Indian Standard Time, UTC+5:30)** – configured via `spring.jackson.time-zone=Asia/Kolkata`
- Rate limit records auto-expire after 1 hour via Cassandra TTL – no background cleanup job needed.
- SMTP connection is intelligently recycled to prevent stale connection issues.

### Monitoring & Debugging

- Enable debug logging in `application.properties` to monitor rate limiting and SMS delivery
- Check logs for emoji-prefixed messages: 📤 (sending), 📊 (status), 🔄 (reset), ❌ (error), ⚠️ (warning)
- Each operation logs key details: elapsed time, window size, counts, and gateway responses
- Failed deliveries are logged with detailed error information for troubleshooting
- Fail-secure approach: ambiguous gateway responses are treated as failures to prevent incorrect success reporting