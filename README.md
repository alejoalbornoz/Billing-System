# 🧾 AFIP Billing System

A hybrid REST + SOAP billing system inspired by AFIP (Argentina's tax authority), built with two independent Spring Boot applications. A client company submits invoices via REST, which are then authorized by a mock AFIP SOAP server that validates the data and generates a CAE code.

---

## 🛠️ Tech Stack

- **Java 17**
- **Spring Boot 3.4.5**
- **Spring Web Services (SOAP)**
- **Spring Security + JWT (jjwt)**
- **Spring Data JPA + Hibernate**
- **springdoc-openapi (Swagger UI)**
- **PostgreSQL 15**
- **JAXB (XML Binding)**
- **Lombok**
- **Docker + Docker Compose**
- **Maven**

---

## 🏗️ Architecture

Two independent Spring Boot applications communicate via SOAP:

```
REST Client (Postman)
        ↓ POST /api/v1/invoices
billing-client  :8080
        ↓ SOAP XML Request
afip-soap-server :8081
        ↓ CAE + Authorization Response
billing-client
        ↓ Saves to PostgreSQL
        ↓ Returns JSON response
REST Client (Postman)
```

### Project 1 — `billing-client`
Simulates a company that wants to issue invoices.

Responsibilities:
- Authenticate users via JWT and enforce role-based access
- Receive REST requests
- Calculate net, VAT and total amounts from the items
- Save invoice as `PENDING` in PostgreSQL
- Build and send SOAP XML to AFIP
- Update invoice with AFIP response
- Return approved or rejected invoice
- Resend invoices left `PENDING` when AFIP was unreachable

### Project 2 — `afip-soap-server`
Simulates the AFIP SOAP server.

Responsibilities:
- Expose a SOAP endpoint
- Validate invoice data (CUIT format, items, amounts recalculated from the items)
- Generate a fictional CAE code
- Return XML authorization response

---

## ⚙️ Configuration

### `billing-client` — application.properties

```properties
spring.application.name=billing-client
server.port=8080

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/billing_db?TimeZone=UTC
spring.datasource.username=billing_user
spring.datasource.password=billing_pass
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.jdbc.time_zone=UTC

# AFIP SOAP
afip.soap.url=http://localhost:8081/ws/invoice
afip.soap.wsdl=http://localhost:8081/ws/invoice.wsdl
afip.soap.connect-timeout-ms=5000
afip.soap.read-timeout-ms=10000

# AFIP retry — PENDING invoices are resent every interval
# (only those older than min-age, which must exceed connect + read timeout)
afip.retry.interval-ms=60000
afip.retry.min-age-ms=120000

# JWT
jwt.secret=your_secret_key_min_64_characters_long_replace_in_production
jwt.expiration=86400000

# Swagger
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.api-docs.path=/v3/api-docs
```

### `afip-soap-server` — application.properties

```properties
spring.application.name=afip-soap-server
server.port=8081

# CAE Generator
afip.cae.prefix=CAE
afip.cae.length=14
```

---

## 🚀 Running the Project

### Prerequisites
- Java 17
- Maven
- Docker Desktop

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/alejoalbornoz/Billing-System

# 2. Start PostgreSQL and pgAdmin containers
cd Billing-System/billing-client
docker-compose up -d

# 3. Run afip-soap-server first (port 8081)
cd ../afip-soap-server
./mvnw spring-boot:run

# 4. In another terminal, run billing-client (port 8080)
cd Billing-System/billing-client
./mvnw spring-boot:run
```

On first startup `billing-client` creates an admin user (see [Authentication](#-authentication)).

### Stop containers

```bash
# Stop but keep data
docker-compose stop

# Stop and remove containers
docker-compose down

# Stop and remove everything including data
docker-compose down -v
```

---

## 🗄️ Database

Tables are created automatically by Hibernate on startup (`ddl-auto=update`).

Access pgAdmin at `http://localhost:5050`

```
Email:    admin@billing.com
Password: admin123
```

Connect to the PostgreSQL server with:

```
Host:     postgres
Port:     5432
Database: billing_db
Username: billing_user
Password: billing_pass
```

---

## 🔐 Authentication

The API uses **JWT (JSON Web Tokens)** for authentication. Tokens expire after **24 hours**.
All `/api/v1/**` endpoints require the token sent as `Authorization: Bearer <token>`.

### Roles

| Role | Access |
|------|--------|
| `ADMIN` | Full access — submit invoices, view all invoices, register new users |
| `ACCOUNTANT` | Submit invoices and view invoice details |

### Initial admin

Since only an `ADMIN` can register users, `billing-client` creates one on startup when no admin exists yet:

```
Email:    admin@billing.com
Password: admin12345
```

Override them with the `ADMIN_EMAIL` and `ADMIN_PASSWORD` environment variables.

### Login

```
POST /api/auth/login
Content-Type: application/json

{
    "email": "admin@billing.com",
    "password": "admin12345"
}
```

Response:

```json
{
    "token": "eyJhbGciOiJIUzM4NCJ9...",
    "type": "Bearer",
    "userId": 1,
    "firstName": "System",
    "lastName": "Administrator",
    "email": "admin@billing.com",
    "role": "ADMIN"
}
```

Use the token in every subsequent request:
```
Authorization: Bearer eyJhbGciOiJIUzM4NCJ9...
```

### Register (ADMIN only)

```
POST /api/auth/register
Authorization: Bearer eyJhbGciOiJIUzM4NCJ9...
Content-Type: application/json

{
    "firstName": "Juan",
    "lastName": "Pérez",
    "email": "juan@billing.com",
    "password": "securepassword",
    "role": "ACCOUNTANT"
}
```

### Security errors

| Status | When |
|--------|------|
| `401 Unauthorized` | Missing, invalid or expired token — or wrong email/password on login |
| `403 Forbidden` | Valid token but the role is not allowed (e.g. `ACCOUNTANT` calling `/register`) |

---

## 🔗 SOAP Contract

The SOAP contract is defined in `afip-invoice.xsd` and shared between both projects. Spring generates the WSDL automatically from the XSD.

WSDL available at:
```
http://localhost:8081/ws/invoice.wsdl
```

### Supported Invoice Types

| Value | Description |
|-------|-------------|
| `INVOICE_A` | Factura A — registered taxpayers |
| `INVOICE_B` | Factura B — end consumers |
| `INVOICE_C` | Factura C — non-registered taxpayers |
| `CREDIT_NOTE` | Nota de crédito |
| `DEBIT_NOTE` | Nota de débito |

### VAT Rates

| Value | Rate |
|-------|------|
| `EXEMPT` | 0% — Exento |
| `ZERO` | 0% — No gravado |
| `VAT_10_5` | 10.5% |
| `VAT_21` | 21% |
| `VAT_27` | 27% |

---

## 📋 API Endpoints

### Auth

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| `POST` | `/api/auth/login` | Public | Login and get JWT token |
| `POST` | `/api/auth/register` | ADMIN only | Register a new user (`ADMIN` or `ACCOUNTANT`) |

### Invoices

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| `POST` | `/api/v1/invoices` | ADMIN, ACCOUNTANT | Submit invoice to AFIP for authorization |
| `GET` | `/api/v1/invoices` | ADMIN | Get all invoices |
| `GET` | `/api/v1/invoices/{id}` | ADMIN, ACCOUNTANT | Get invoice by internal ID |
| `GET` | `/api/v1/invoices/external/{externalInvoiceId}` | ADMIN, ACCOUNTANT | Get invoice by AFIP external ID |

### Create Invoice — Request Body

```json
{
    "invoiceType": "INVOICE_B",
    "issueDate": "2026-05-19",
    "pointOfSale": 1,
    "issuerCuit": "30-71234567-0",
    "issuerBusinessName": "Mi Empresa SRL",
    "issuerAddress": "Av. Corrientes 1234, CABA",
    "receiverCuit": "20-12345678-9",
    "receiverBusinessName": "Cliente SA",
    "receiverAddress": "Av. Santa Fe 5678, CABA",
    "items": [
        {
            "description": "Servicio de desarrollo de software",
            "quantity": 1,
            "unitPrice": 1000.00,
            "vatRate": "VAT_21"
        }
    ]
}
```

Amounts are **not** sent by the client — `billing-client` calculates them from the items:

- `subtotal = quantity × unitPrice`, rounded to 2 decimals
- `netAmount` = sum of subtotals
- `vatAmount` = VAT calculated **once per rate** over the subtotals with that rate (not item by item), rounded to 2 decimals
- `totalAmount = netAmount + vatAmount`

`quantity` and `unitPrice` accept up to 2 decimals.

### Approved Response — 201 Created

```json
{
    "id": 1,
    "externalInvoiceId": "c0d3d060-d7a1-4219-a277-25fd24befb0a",
    "invoiceType": "INVOICE_B",
    "issueDate": "2026-05-19",
    "pointOfSale": 1,
    "issuerCuit": "30-71234567-0",
    "issuerBusinessName": "Mi Empresa SRL",
    "receiverCuit": "20-12345678-9",
    "receiverBusinessName": "Cliente SA",
    "netAmount": 1000.00,
    "vatAmount": 210.00,
    "totalAmount": 1210.00,
    "status": "APPROVED",
    "cae": "43969379396831",
    "caeExpirationDate": "2026-06-04",
    "afipInvoiceNumber": 2799044,
    "afipMessage": "Invoice authorized successfully by AFIP.",
    "items": [...],
    "createdAt": "2026-05-25T18:34:52"
}
```

### Invoice Status Values

| Status | HTTP | Description |
|--------|------|-------------|
| `APPROVED` | `201 Created` | CAE issued, invoice is valid |
| `REJECTED` | `201 Created` | AFIP validation failed — `afipMessage` lists the error codes |
| `PENDING` | `202 Accepted` | Saved, but AFIP could not be reached. A background job resends it automatically every minute |

Rejected example:

```json
{
    "status": "REJECTED",
    "afipMessage": "Invoice rejected. Please review the errors. ERR_004: Issuer CUIT format is invalid. Expected format: XX-XXXXXXXX-X",
    "cae": null
}
```

---

## 🛡️ Validation

The system has two independent validation layers:

**Layer 1 — Spring `@Valid` (billing-client)**
Validates the REST request before it reaches the service layer.
- Required fields
- CUIT format (`XX-XXXXXXXX-X`)
- Positive quantities and non-negative prices, with up to 2 decimals
- At least one item

Returns `400 BAD REQUEST` on failure:

```json
{
    "status": 400,
    "error": "Validation Failed",
    "message": "One or more fields are invalid.",
    "fieldErrors": {
        "issuerCuit": "Issuer CUIT format must be XX-XXXXXXXX-X"
    },
    "timestamp": "2026-05-25T18:39:17"
}
```

A body that can't be parsed (malformed JSON, unknown enum value, text in a numeric field) also returns `400`, pointing at the field:

```json
{
    "status": 400,
    "error": "Malformed Request",
    "message": "Invalid value for field 'invoiceType'. Accepted values: [INVOICE_A, INVOICE_B, INVOICE_C, CREDIT_NOTE, DEBIT_NOTE]",
    "timestamp": "2026-05-25T18:40:02"
}
```

**Layer 2 — `InvoiceValidator` (afip-soap-server)**
Validates business rules on the SOAP server side.
- CUIT format on both issuer and receiver
- Item quantities and prices
- Recalculates every amount from the items instead of trusting the client: item subtotals, net amount and VAT per rate
- Cross-check: `totalAmount == netAmount + vatAmount`

Returns `201` with `status: REJECTED` and a list of error codes on failure.

### AFIP Error Codes

| Code | Description |
|------|-------------|
| `ERR_001` | External invoice ID is required |
| `ERR_002` | Issuer information is required |
| `ERR_003` | Issuer CUIT is required |
| `ERR_004` | Issuer CUIT format invalid |
| `ERR_005` | Issuer business name is required |
| `ERR_006` | Receiver information is required |
| `ERR_007` | Receiver CUIT is required |
| `ERR_008` | Receiver CUIT format invalid |
| `ERR_009` | Receiver business name is required |
| `ERR_010` | Invoice must contain at least one item |
| `ERR_011` | Item description is required |
| `ERR_012` | Item quantity must be greater than zero |
| `ERR_013` | Item unit price cannot be negative |
| `ERR_014` | Item VAT rate is required |
| `ERR_015` | Net amount must be greater than zero |
| `ERR_016` | VAT amount cannot be negative |
| `ERR_017` | Total amount must be greater than zero |
| `ERR_018` | Total amount does not match netAmount + vatAmount |
| `ERR_019` | Item subtotal does not match quantity × unitPrice |
| `ERR_020` | Net amount does not match the sum of item subtotals |
| `ERR_021` | VAT amount does not match the VAT calculated from the items |

---

## 🧪 Testing

```bash
# Run inside each project folder (afip-soap-server / billing-client)
./mvnw test
```

| Project | Test Class | Coverage |
|---------|-----------|----------|
| `afip-soap-server` | `InvoiceValidatorTest` | 17 unit tests — all validation rules, including amounts vs items |
| `afip-soap-server` | `CaeGeneratorServiceImplTest` | 8 unit tests — approved and rejected flows |
| `billing-client` | `InvoiceControllerTest` | 15 integration tests — REST endpoints, validation, 201/202 and error responses with MockMvc |
| `billing-client` | `AuthControllerTest` | 7 integration tests — login, register and 401/403/409 responses |
| `billing-client` | `InvoiceServiceImplTest` | 7 unit tests — PENDING → AFIP flow, rejection details, AFIP unreachable and retries |
| `billing-client` | `InvoiceTest` | 4 unit tests — amount calculation and VAT rounding per rate |
| `billing-client` | `AdminInitializerTest` | 2 unit tests — initial admin creation |
| `billing-client` | `PendingInvoiceRetryJobTest` | 1 unit test — retry cutoff |

`BillingClientApplicationTests` loads the full context, so it needs PostgreSQL running (`docker-compose up -d`).

---

## 📖 API Documentation

Swagger UI is available at:
```
http://localhost:8080/swagger-ui.html
```

Call `POST /api/auth/login`, click **Authorize** and paste the token to try the protected endpoints.

---

## 🧠 Design Decisions

**Two independent applications** — Separating the client and the AFIP mock into two Spring Boot apps makes the architecture realistic and professional. Each app has its own responsibility and can be deployed independently.

**XSD as the single source of truth** — The SOAP contract is defined once in `afip-invoice.xsd` and shared between both projects. JAXB generates Java classes automatically from it. Any contract change only requires updating the XSD.

**Save before calling AFIP** — The invoice is persisted and committed as `PENDING` before the SOAP call, and the call itself runs outside any DB transaction. A record exists even if AFIP is unreachable, no DB connection is held while waiting for AFIP, and an unexpected error can't roll back the invoice.

**PENDING is not REJECTED** — If AFIP can't be reached (connection error or timeout), the invoice stays `PENDING` and the API answers `202 Accepted`. `PendingInvoiceRetryJob` resends those invoices periodically, only once they are older than the SOAP timeouts so an in-flight call is never duplicated.

**Amounts calculated from the items** — The client never sends totals. `billing-client` calculates them, and `afip-soap-server` recalculates them independently and rejects any mismatch, like real AFIP does.

**Interface + Implementation pattern on services** — Every service is defined as an interface (`InvoiceService`) and implemented separately (`InvoiceServiceImpl`). Controllers always inject the interface, making the implementation swappable without touching the controller layer.

**CUIT format validation in both layers** — The REST layer validates format with `@Pattern` before the request reaches the service. The SOAP server re-validates independently, simulating real AFIP behavior where the server enforces its own rules regardless of the client.

**JWT stateless authentication** — Spring Security is configured as stateless — no HTTP sessions are created. Every request must carry its own JWT token, which is validated by the `JwtAuthFilter` before reaching any controller.

**XMLGregorianCalendar for SOAP dates** — JAXB maps `xs:date` and `xs:dateTime` to `XMLGregorianCalendar`. Conversion helpers centralize the `LocalDate`/`LocalDateTime` → `XMLGregorianCalendar` logic to keep the service code clean.

---

## 📄 License

This project is for educational purposes.
