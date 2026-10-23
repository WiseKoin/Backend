# WiseKoin Backend

Spring Boot REST API for the WiseKoin financial management application.

## Group Members

| Student ID | Full Name             | Role                                                                                   |
|------------|-----------------------|----------------------------------------------------------------------------------------|
| 24125076   | Tran Thien Phuc       | Backend: Auth, Wallet, Transaction, Category, Goal, Debt, Receipt modules; JWT security; event-driven balance updates (33.33%) |
| 24125016   | Hoang Duc Hung Phat   | Backend: Receipt modules; Gemini API integration; database schema and migrations (33.33%)                              |
| 24125053   | Nguyen Van Le Bao     | Deploy database on Neon & backend on Render; API integration documentation (33.33%)                                    |

## Project Title

**WiseKoin** — Financial Tracking and Planning Mobile Application

## Demo Video

[Demo Video](#) <!-- Replace with actual link -->

## Tech Stack

- **Framework:** Spring Boot 4.1.1 (Java 21)
- **Database:** PostgreSQL (Neon cloud-hosted)
- **Authentication:** JWT (jjwt 0.13.0) + Spring Security + BCrypt
- **ORM:** Spring Data JPA + Hibernate
- **AI Service:** Google Gemini 3.1 Flash Lite (via AI Studio)
- **API Docs:** SpringDoc OpenAPI 3.1.0 (Swagger UI)
- **Build Tool:** Maven

## REST API Endpoints

### Authentication
- `POST /api/auth/register` — Register and receive an access/refresh token pair
- `POST /api/auth/login` — Login and receive an access/refresh token pair
- `POST /api/auth/refresh` — Rotate a refresh token and receive a new token pair
- `POST /api/auth/logout` — Revoke a refresh token

### Users
- `GET /api/users/me` — Get current user profile
- `PUT /api/users/me` — Update current user profile
- `PUT /api/users/me/password` — Change password and revoke all refresh sessions
- `POST /api/users/me/otp/request` — Email a purpose-bound OTP (resend cooldown and hourly cap apply)
- `POST /api/users/me/otp/verify` — Verify the OTP and issue a short-lived, one-time verification token
- `PUT /api/users/me/base-currency` — Update the ISO 4217 base currency
- `DELETE /api/users/me` — Permanently delete the user and all owned data

### Wallets
- `GET /api/wallets` — List all wallets for current user
- `POST /api/wallets` — Create a new wallet
- `PUT/PATCH /api/wallets/{id}` — Update a wallet
- `DELETE /api/wallets/{id}` — Delete an unused wallet

### Categories
- `GET /api/categories` — List categories for current user
- `POST /api/categories` — Create a custom category
- `PUT/PATCH /api/categories/{id}` — Update a category
- `DELETE /api/categories/{id}` — Delete a category

### Transactions
- `GET /api/transactions` — List transactions (paginated, filterable)
- `POST /api/transactions` — Create a new transaction
- `PUT/PATCH /api/transactions/{id}` — Update a transaction and recalculate linked balances
- `DELETE /api/transactions/{id}` — Delete a transaction and reverse its linked balances
- `GET /api/transactions/monthly?year={year}` — Get monthly income/expense stats

Wallet-to-wallet transfers use one `TRANSFER` transaction with the source in `walletId`
and the receiving wallet in `destinationWalletId`:

```json
{
  "walletId": 1,
  "destinationWalletId": 2,
  "amount": 250000,
  "type": "TRANSFER",
  "note": "Move cash to bank"
}
```

### Recurring Transactions

- `GET /api/recurring-transactions` — List monthly expense templates
- `GET /api/recurring-transactions/{id}` — Get one template
- `POST /api/recurring-transactions` — Schedule a monthly expense
- `PUT/PATCH /api/recurring-transactions/{id}` — Update, pause, or resume a template
- `DELETE /api/recurring-transactions/{id}` — Delete a template

The daily scheduler creates normal `EXPENSE` transactions for every due date,
so wallet balance listeners apply the same validation and updates as manual
transactions. A day such as `31` is clamped to the last day of shorter months.

```json
{
  "walletId": 1,
  "categoryId": 2,
  "amount": 149000,
  "note": "Netflix",
  "dayOfMonth": 12,
  "startDate": "2026-10-12",
  "active": true
}
```

### Debts
- `GET /api/debts` — List debts for current user
- `POST /api/debts` — Create a new debt (lend or borrow)
- `PUT/PATCH /api/debts/{id}` — Update a debt
- `DELETE /api/debts/{id}` — Delete a debt without repayment transactions
- `POST /api/debts/{id}/repay` — Repay part or all of a debt

### Goals
- `GET /api/goals` — List savings goals
- `POST /api/goals` — Create a new savings goal
- `PUT/PATCH /api/goals/{id}` — Update a savings goal
- `DELETE /api/goals/{id}` — Delete a goal without funding transactions
- `POST /api/goals/{id}/fund` — Add funds to a goal

### Receipts
- `POST /api/receipts/extract` — Upload a receipt image with optional `locale` and `time_zone` multipart fields; returns typed facts, ranked owned-category candidates, validation-derived confidence, provider/prompt metadata, and a traceable `extraction_id`. An unresolved category has a `null` ID and the display name `Unknown`.

### Voice
- `POST /api/voice/extract` — Upload an audio recording with optional `locale` and `time_zone` multipart fields; returns one transcript and a `transactions` array supporting multiple entries, stable owned wallet IDs, ranked categories, validation-derived confidence, and per-item `extraction_id` values. Compatibility fields at the response root mirror the first transaction.

When a transaction is created from an AI suggestion, send its `aiExtractionId` in the normal transaction request. Set `rememberAiCategory=true` only after an explicit user opt-in; this stores the confirmation feedback and updates that user's normalized merchant/category rule. Raw images, audio, transcripts, and provider payloads are not stored by the adaptive layer.

See [AI_FEATURE_AUDIT.md](AI_FEATURE_AUDIT.md) for the AI reliability audit, layered suggestion-engine design, and the notification/bank-integration roadmap.

## API Base URL

Production: `https://wisekoin-api.example.com` (replace with actual URL)  
Local: `http://localhost:8080`

Swagger UI is available at `http://localhost:8080/swagger-ui.html` when running locally.

## Environment Variables

Create a `.env` file in the project root with the following variables:

```
DB_URL=jdbc:postgresql://host:port/dbname?sslmode=require
DB_USERNAME=your_db_user
DB_PASSWORD=your_db_password
JWT_SECRET=your_jwt_secret_min_256_bits
JWT_EXPIRATION=900000
JWT_REFRESH_EXPIRATION=2592000000
VISION_LANGUAGE_API_KEY=your_gemini_api_key
GEMINI_MODEL=gemini-3.1-flash-lite
GEMINI_URL=https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent
GEMINI_FALLBACK_URL=
GEMINI_FALLBACK_MODEL=
AI_DEFAULT_LOCALE=vi-VN
AI_RECEIPT_PROMPT_RESOURCE=classpath:ai/prompts/receipt-v2.txt
AI_RECEIPT_PROMPT_VERSION=receipt-v2
AI_VOICE_PROMPT_RESOURCE=classpath:ai/prompts/voice-v2.txt
AI_VOICE_PROMPT_VERSION=voice-v2
AI_CONFIDENCE_PROFILE=baseline-v1
AI_CONFIDENCE_CALIBRATED=false
AI_CONFIDENCE_HIGH_THRESHOLD=0.80
AI_CONFIDENCE_MEDIUM_THRESHOLD=0.55
APP_TIME_ZONE=Asia/Ho_Chi_Minh
FORWARD_HEADERS_STRATEGY=native
RECURRING_TRANSACTIONS_CRON=0 5 0 * * *
RATE_LIMIT_ENABLED=true
RATE_LIMIT_WINDOW_SECONDS=60
RATE_LIMIT_AI_REQUESTS=10
RATE_LIMIT_AUTH_REQUESTS=20
RATE_LIMIT_API_REQUESTS=120
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=no-reply@example.com
MAIL_PASSWORD=your_smtp_password
MAIL_SMTP_AUTH=true
MAIL_STARTTLS_ENABLE=true
MAIL_STARTTLS_REQUIRED=true
OTP_FROM_EMAIL=no-reply@example.com
OTP_EXPIRATION_SECONDS=600
OTP_VERIFICATION_EXPIRATION_SECONDS=600
OTP_RESEND_COOLDOWN_SECONDS=60
OTP_MAX_SENDS_PER_HOUR=3
OTP_MAX_ATTEMPTS=5
```

Password changes and account deletion require a purpose-bound OTP. Request a code with
`CHANGE_PASSWORD` or `DELETE_ACCOUNT`, verify the six-digit code, then pass the returned
one-time `verificationToken` together with the current password to the sensitive endpoint.
Codes and verification tokens are hashed in the database and are never logged.

> **Note:** The `.env` file is not committed to version control. Copy `.env.example` if available.

## Build and Run

```bash
# Build
mvn clean package

# Run
mvnw spring-boot:run
```

The server starts on port 8080 by default.

### Docker development with Neon Local

Copy `.env.development.example` to `.env.development`, provide the Neon API,
project, and parent branch values, then run:

```bash
docker compose --env-file .env.development up --build
```

Neon Local creates an ephemeral child branch and exposes its PostgreSQL proxy on
localhost port `5432`. The application connects to the proxy over the Compose
network. By default, the ephemeral branch is deleted when Neon Local stops.

### Docker production with Neon Cloud

Copy `.env.production.example` to `.env.production`, set the published Docker
Hub image and Neon cloud JDBC credentials, then run:

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml up -d
```

The production configuration connects directly to Neon Cloud and does not run
the Neon Local proxy.

### Rate limits

Rate limits use authenticated user IDs where available and client IP addresses
for anonymous authentication requests. Defaults are 10 AI requests, 20 auth
requests, and 120 other API requests per 60-second window. Rejected calls return
HTTP `429` with structured JSON plus `Retry-After` and `X-RateLimit-*` headers.
The counters are held in-process; use a shared Redis-backed limiter if the API is
deployed with multiple application replicas.

### Android client sessions

Native Android networking is not subject to browser CORS. The API accepts
cross-origin bearer-token requests without credentialed browser cookies, so no
frontend origin allowlist is required. When testing from a physical device, use
the development machine's LAN address instead of `localhost`, for example
`http://192.168.1.10:8080`.

Login and registration return a short-lived access token and a rotating refresh
token:

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

Store the refresh token in encrypted application storage backed by the Android
Keystore. Send it to `POST /api/auth/refresh` as
`{"refreshToken":"..."}` when the access token expires, and replace both local
tokens with the returned pair. Refresh tokens are stored as hashes by the API
and invalidated immediately when rotated or logged out.

The Docker publishing workflow requires repository secrets named
`DOCKER_USERNAME` and `DOCKER_PASSWORD`. Pushes to `main` publish `latest`,
`main`, and commit-SHA tags; `v*` Git tags also publish semantic-version tags.

## Database

Database schema changes are applied by Flyway from `src/main/resources/db/migration`, while Hibernate validates the resulting schema. Existing databases created before Flyway was enabled are baselined at version 7 so that pending migrations, including removal of the obsolete debt wallet column, run on startup.

## Testing

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=ReceiptServiceTest
```

## Formatting

```bash
# Check formatting (also runs during mvn verify)
mvn spotless:check

# Format Java sources
mvn spotless:apply
```

## Test Account

To create a test account, use the register endpoint:

```json
POST /api/auth/register
{
  "username": "testuser",
  "email": "test@wisekoin.com",
  "password": "testpass123",
  "phone": "0901234567"
}
```
