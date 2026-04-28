# OTPUtil API - Complete Testing Guide

**Base URL**: `http://localhost:8080`  
**API Version**: v1  
**Content-Type**: `application/json`

---

## 📋 Quick Reference

| # | Endpoint | Method | Purpose |
|----|----------|--------|---------|
| 1 | `/api/v1/otp/generate` | POST | Generate OTP for email/phone |
| 2 | `/api/v1/otp/verify` | POST | Verify OTP provided by user |

---

## 🔐 Endpoint 1: Generate OTP

### 1.1 Generate OTP for Email (Default - Signup)
**Purpose**: Generate a 6-digit OTP and send via email for user signup  
**Status Code**: 200 (Success) or 206 (Generated but delivery failed)

```bash
curl -X POST http://localhost:8080/api/v1/otp/generate \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "key": "user@example.com",
      "type": "email"
    }
  }'
```

**Expected Success Response (200)**:
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

### 1.2 Generate OTP for Phone (SMS)
**Purpose**: Generate a 6-digit OTP and send via SMS to phone number  
**Status Code**: 200 (Success) or 206 (Generated but delivery failed)

```bash
curl -X POST http://localhost:8080/api/v1/otp/generate \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "key": "9876543210",
      "type": "phone"
    }
  }'
```

**Expected Success Response (200)**:
```json
{
  "response": {
    "otpSent": "yes",
    "referenceId": "550e8400-e29b-41d4-a716-446655440001",
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

### 1.3 Generate OTP for Password Reset
**Purpose**: Generate OTP specifically for password reset functionality  
**Purpose Parameter**: `resetPasswordWithOtp`  
**Status Code**: 200 (Success)

```bash
curl -X POST http://localhost:8080/api/v1/otp/generate \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "key": "user@example.com",
      "type": "email",
      "purpose": "resetPasswordWithOtp"
    }
  }'
```

---

### 1.4 Generate OTP for Contact Update
**Purpose**: Generate OTP for user to update contact information  
**Purpose Parameter**: `otpContactUpdateTemplate`  
**Status Code**: 200 (Success)

```bash
curl -X POST http://localhost:8080/api/v1/otp/generate \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "key": "newemail@example.com",
      "type": "email",
      "purpose": "otpContactUpdateTemplate"
    }
  }'
```

---

### 1.5 Generate OTP for Account Deletion
**Purpose**: Generate OTP to verify account deletion request  
**Purpose Parameter**: `deleteUserAccountTemplate`  
**Status Code**: 200 (Success)

```bash
curl -X POST http://localhost:8080/api/v1/otp/generate \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "key": "user@example.com",
      "type": "email",
      "purpose": "deleteUserAccountTemplate"
    }
  }'
```

---

### 1.6 Generate OTP - Validation Error (Missing Field)
**Purpose**: Test validation - missing required 'type' field  
**Status Code**: 400 (Bad Request)

```bash
curl -X POST http://localhost:8080/api/v1/otp/generate \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "key": "user@example.com"
    }
  }'
```

**Expected Response (400)**:
```json
{
  "status": {
    "code": "OTP_400",
    "message": "type: Must not be null"
  }
}
```

---

### 1.7 Generate OTP - Empty Request
**Purpose**: Test validation - empty request body  
**Status Code**: 400 (Bad Request)

```bash
curl -X POST http://localhost:8080/api/v1/otp/generate \
  -H "Content-Type: application/json" \
  -d '{}'
```

---

### 1.8 Generate OTP - Rate Limit Exceeded
**Purpose**: Test rate limiting - exceed 3 requests per hour  
**Status Code**: 409 (Conflict - Rate Limited)

```bash
# Run 4 times in succession for same key
for i in {1..4}; do
  echo "Attempt $i:"
  curl -s -X POST http://localhost:8080/api/v1/otp/generate \
    -H "Content-Type: application/json" \
    -d '{
      "request": {
        "key": "ratelimit@example.com",
        "type": "email"
      }
    }' | jq '.status'
  sleep 1
done
```

**Expected**: First 3 return 200, 4th returns 409
```json
{
  "status": {
    "code": "OTP_409",
    "message": "Rate limit exceeded"
  }
}
```

---

### 1.9 Generate OTP - Invalid Email Type
**Purpose**: Test with invalid email format  
**Status Code**: 400 (Bad Request)

```bash
curl -X POST http://localhost:8080/api/v1/otp/generate \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "key": "invalid-email-format",
      "type": "email"
    }
  }'
```

---

### 1.10 Generate OTP - Delivery Failure (206)
**Purpose**: Demonstrates scenario where OTP generates but delivery fails  
**Status Code**: 206 (Partial Content - Generated but not sent)

```bash
curl -X POST http://localhost:8080/api/v1/otp/generate \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "key": "nonexistent@example.com",
      "type": "email"
    }
  }'
```

**Expected Response (206)**:
```json
{
  "response": {
    "otpSent": "no",
    "referenceId": "...",
    "generatedTs": 1714200000000,
    "validFor": 60
  },
  "status": {
    "code": "OTP_206",
    "message": "OTP generated but failed to send notification."
  }
}
```

---

## ✅ Endpoint 2: Verify OTP

### 2.1 Verify OTP - Success
**Purpose**: Verify the 6-digit OTP provided by user (correct OTP)  
**Status Code**: 200 (Success)

```bash
curl -X POST http://localhost:8080/api/v1/otp/verify \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "key": "user@example.com",
      "type": "email",
      "otp": "123456",
      "referenceId": "550e8400-e29b-41d4-a716-446655440000"
    }
  }'
```

**Expected Success Response (200)**:
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

### 2.2 Verify OTP - Incorrect OTP
**Purpose**: Test verification with incorrect/wrong OTP  
**Status Code**: 422 (Unprocessable Entity)

```bash
curl -X POST http://localhost:8080/api/v1/otp/verify \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "key": "user@example.com",
      "type": "email",
      "otp": "999999",
      "referenceId": "550e8400-e29b-41d4-a716-446655440000"
    }
  }'
```

**Expected Response (422)**:
```json
{
  "status": {
    "code": "OTP_422",
    "message": "OTP incorrect"
  }
}
```

---

### 2.3 Verify OTP - Expired OTP
**Purpose**: Test verification with expired OTP (> 60 seconds old)  
**Status Code**: 410 (Gone)

```bash
curl -X POST http://localhost:8080/api/v1/otp/verify \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "key": "user@example.com",
      "type": "email",
      "otp": "123456",
      "referenceId": "expired-reference-id"
    }
  }'
```

**Expected Response (410)**:
```json
{
  "status": {
    "code": "OTP_410",
    "message": "OTP expired"
  }
}
```

---

### 2.4 Verify OTP - Max Attempts Exceeded
**Purpose**: Test max verification attempts exceeded (max 3 attempts)  
**Status Code**: 429 (Too Many Requests)

```bash
# Try verifying 4 times with wrong OTP
for i in {1..4}; do
  echo "Verification Attempt $i:"
  curl -s -X POST http://localhost:8080/api/v1/otp/verify \
    -H "Content-Type: application/json" \
    -d '{
      "request": {
        "key": "user@example.com",
        "type": "email",
        "otp": "999999",
        "referenceId": "550e8400-e29b-41d4-a716-446655440000"
      }
    }' | jq '.status'
  sleep 1
done
```

**Expected**: First 3 return 422 (Wrong OTP), 4th returns 429
```json
{
  "status": {
    "code": "OTP_429",
    "message": "Max verification attempts exceeded"
  }
}
```

---

### 2.5 Verify OTP - Missing Reference ID
**Purpose**: Test validation - missing required 'referenceId' field  
**Status Code**: 400 (Bad Request)

```bash
curl -X POST http://localhost:8080/api/v1/otp/verify \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "key": "user@example.com",
      "type": "email",
      "otp": "123456"
    }
  }'
```

---

### 2.6 Verify OTP - Invalid OTP Format
**Purpose**: Test with invalid OTP format (not 6 digits)  
**Status Code**: 400 (Bad Request)

```bash
curl -X POST http://localhost:8080/api/v1/otp/verify \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "key": "user@example.com",
      "type": "email",
      "otp": "abc",
      "referenceId": "550e8400-e29b-41d4-a716-446655440000"
    }
  }'
```

---

## 🎯 Advanced Testing Scenarios

### Scenario 1: Complete OTP Flow (Generate → Verify)
**Purpose**: Test the complete OTP lifecycle

```bash
#!/bin/bash

# Step 1: Generate OTP
echo "=== Step 1: Generating OTP ==="
RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/otp/generate \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "key": "test@example.com",
      "type": "email"
    }
  }')

echo "$RESPONSE" | jq '.'

# Extract referenceId
REF_ID=$(echo "$RESPONSE" | jq -r '.response.referenceId')
STATUS=$(echo "$RESPONSE" | jq -r '.status.code')

echo ""
echo "Generated Reference ID: $REF_ID"
echo "Status: $STATUS"
echo ""

# Step 2: Verify OTP (use actual OTP from email)
echo "=== Step 2: Verifying OTP ==="
VERIFY_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/otp/verify \
  -H "Content-Type: application/json" \
  -d "{
    \"request\": {
      \"key\": \"test@example.com\",
      \"type\": \"email\",
      \"otp\": \"123456\",
      \"referenceId\": \"$REF_ID\"
    }
  }")

echo "$VERIFY_RESPONSE" | jq '.'
```

---

### Scenario 2: Test Multiple Purposes
**Purpose**: Generate OTPs for all different purposes

```bash
#!/bin/bash

# Purpose 1: Signup (default)
echo "1. Signup OTP:"
curl -s -X POST http://localhost:8080/api/v1/otp/generate \
  -H "Content-Type: application/json" \
  -d '{"request":{"key":"user1@example.com","type":"email"}}' | jq '.status'

# Purpose 2: Password Reset
echo "2. Password Reset OTP:"
curl -s -X POST http://localhost:8080/api/v1/otp/generate \
  -H "Content-Type: application/json" \
  -d '{"request":{"key":"user2@example.com","type":"email","purpose":"resetPasswordWithOtp"}}' | jq '.status'

# Purpose 3: Contact Update
echo "3. Contact Update OTP:"
curl -s -X POST http://localhost:8080/api/v1/otp/generate \
  -H "Content-Type: application/json" \
  -d '{"request":{"key":"user3@example.com","type":"email","purpose":"otpContactUpdateTemplate"}}' | jq '.status'

# Purpose 4: Account Deletion
echo "4. Account Deletion OTP:"
curl -s -X POST http://localhost:8080/api/v1/otp/generate \
  -H "Content-Type: application/json" \
  -d '{"request":{"key":"user4@example.com","type":"email","purpose":"deleteUserAccountTemplate"}}' | jq '.status'
```

---

### Scenario 3: Pretty Print JSON Response
**Purpose**: Generate OTP and pretty-print the JSON output

```bash
curl -X POST http://localhost:8080/api/v1/otp/generate \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "key": "user@example.com",
      "type": "email"
    }
  }' | jq '.'
```

---

### Scenario 4: Save Response to File
**Purpose**: Save API response to a file for later analysis

```bash
curl -X POST http://localhost:8080/api/v1/otp/generate \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "key": "user@example.com",
      "type": "email"
    }
  }' > otp_response.json

# View the file
cat otp_response.json | jq '.'
```

---

### Scenario 5: Send Request from File
**Purpose**: Send OTP request from a JSON file

**Create file** `otp_request.json`:
```json
{
  "request": {
    "key": "user@example.com",
    "type": "email",
    "purpose": "resetPasswordWithOtp"
  }
}
```

**Use with curl**:
```bash
curl -X POST http://localhost:8080/api/v1/otp/generate \
  -H "Content-Type: application/json" \
  -d @otp_request.json
```

---

### Scenario 6: Check Response Headers
**Purpose**: View HTTP response headers (status, content-type, etc.)

```bash
curl -i -X POST http://localhost:8080/api/v1/otp/generate \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "key": "user@example.com",
      "type": "email"
    }
  }'
```

---

### Scenario 7: Verbose Mode (Debug)
**Purpose**: See all request/response details for debugging

```bash
curl -v -X POST http://localhost:8080/api/v1/otp/generate \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "key": "user@example.com",
      "type": "email"
    }
  }'
```

---

### Scenario 8: Add Custom Headers
**Purpose**: Add custom tracking headers to requests

```bash
curl -X POST http://localhost:8080/api/v1/otp/generate \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: 12345" \
  -H "X-Client-Version: 1.0" \
  -H "X-Timestamp: $(date +%s)" \
  -d '{
    "request": {
      "key": "user@example.com",
      "type": "email"
    }
  }'
```

---

## 📊 Response Status Codes Reference

| HTTP Code | Meaning | Error Code | When Used |
|-----------|---------|-----------|-----------|
| **200** | OK | `OTP_200` | OTP generated/verified successfully |
| **206** | Partial Content | `OTP_206` | OTP generated but delivery failed |
| **400** | Bad Request | `OTP_400` | Invalid input/validation error |
| **409** | Conflict | `OTP_409` | Rate limit exceeded (max 3/hour) |
| **410** | Gone | `OTP_410` | OTP expired (> 60 seconds) |
| **422** | Unprocessable Entity | `OTP_422` | OTP incorrect/doesn't match |
| **429** | Too Many Requests | `OTP_429` | Max verification attempts exceeded (3 times) |
| **500** | Internal Server Error | `OTP_500` | Internal/encryption error |
| **503** | Service Unavailable | `OTP_503` | Notification service failure |

---

## 🔧 Configuration Reference

All these settings are in `application.properties`:

```properties
# OTP Settings
otp.ttl.seconds=60                          # OTP validity period
otp.max.attempts=3                          # Max verification attempts
otp.length=6                                # OTP digit length

# Rate Limiting
otp.rateLimit.max-per-hour=3                # Max generations per hour
otp.rateLimit.window-seconds=3600           # Rate limit window

# Valid Purposes
otp.valid.purposes=signupOtp,resetPasswordWithOtp,otpContactUpdateTemplate,deleteUserAccountTemplate
otp.default.purpose=signupOtp               # Default when not specified
```

---

## 📝 Important Notes

- **Timestamps**: All `generatedTs` values are in **milliseconds** (Unix epoch)
- **OTP Validity**: OTPs expire after **60 seconds** by default
- **Rate Limiting**: Maximum **3 OTP generations per hour** per key
- **Verification Attempts**: Maximum **3 attempts** per OTP
- **OTP Length**: **6 digits** (000000 - 999999)
- **Encryption**: OTPs stored with **AES-256-GCM** encryption
- **Request Envelope**: All requests must have a `request` wrapper object

---

## 🔒 Security Best Practices

1. ✅ Always use HTTPS in production (not HTTP)
2. ✅ Never hardcode credentials in curl commands
3. ✅ Don't expose `referenceId` in logs unnecessarily
4. ✅ Implement client-side rate limiting
5. ✅ Log all OTP verification attempts
6. ✅ Use strong encryption for OTP storage
7. ✅ Monitor for suspicious OTP patterns
8. ✅ Implement CAPTCHA for repeated failures

---

## 🎯 Quick Copy-Paste Commands

**Generate Email OTP**:
```bash
curl -X POST http://localhost:8080/api/v1/otp/generate -H "Content-Type: application/json" -d '{"request":{"key":"user@example.com","type":"email"}}'
```

**Generate Phone OTP**:
```bash
curl -X POST http://localhost:8080/api/v1/otp/generate -H "Content-Type: application/json" -d '{"request":{"key":"9876543210","type":"phone"}}'
```

**Verify OTP**:
```bash
curl -X POST http://localhost:8080/api/v1/otp/verify -H "Content-Type: application/json" -d '{"request":{"key":"user@example.com","type":"email","otp":"123456","referenceId":"ref-id-here"}}'
```

---

## 📞 Testing Checklist

- [ ] Generate OTP for email
- [ ] Generate OTP for phone
- [ ] Generate OTP with custom purpose
- [ ] Verify OTP with correct code
- [ ] Verify OTP with incorrect code (should return 422)
- [ ] Test OTP expiration (wait > 60 seconds)
- [ ] Test rate limiting (generate 4 times, 4th should return 409)
- [ ] Test max attempts (verify 4 times, 4th should return 429)
- [ ] Test validation errors (missing fields)
- [ ] Test delivery failure scenario (206)

---

**End of API Testing Guide**

Last Updated: April 28, 2026

