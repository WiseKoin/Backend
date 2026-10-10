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
- `POST /api/auth/register` — Register a new user
- `POST /api/auth/login` — Login and receive JWT token

### Users
- `GET /api/users/me` — Get current user profile
- `PUT /api/users/me` — Update current user profile

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
  "categoryId": 3,
  "amount": 250000,
  "type": "TRANSFER",
  "note": "Move cash to bank"
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
- `POST /api/receipts/extract` — Upload a receipt image; returns extracted store, category, and amount via Gemini AI

### Voice
- `POST /api/voice/extract` — Upload an audio recording; returns extracted transaction data via Gemini AI

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
VISION_LANGUAGE_API_KEY=your_gemini_api_key
```

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

The Docker publishing workflow requires repository secrets named
`DOCKER_USERNAME` and `DOCKER_PASSWORD`. Pushes to `main` publish `latest`,
`main`, and commit-SHA tags; `v*` Git tags also publish semantic-version tags.

## Database

The application uses `spring.jpa.hibernate.ddl-auto=update` for development. A Flyway migration (`V1__fix_categories_type_check.sql`) is included for schema fixes.

## Testing

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=ReceiptServiceTest
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
