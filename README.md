# 🧾 AFIP Billing System

A hybrid REST + SOAP billing system inspired by AFIP (Argentina's tax authority), built with two independent Spring Boot applications. A client company submits invoices via REST, which are then authorized by a mock AFIP SOAP server that validates the data and generates a CAE code.

---

## 🛠️ Tech Stack

- **Java 17**
- **Spring Boot 3.4.5**
- **Spring Web Services (SOAP)**
- **Spring Data JPA + Hibernate**
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
- Receive REST requests
- Save invoice as `PENDING` in PostgreSQL
- Build and send SOAP XML to AFIP
- Update invoice with AFIP response
- Return approved or rejected invoice

### Project 2 — `afip-soap-server`
Simulates the AFIP SOAP server.

Responsibilities:
- Expose a SOAP endpoint
- Validate invoice data (CUIT format, amounts, items)
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

# 2. Start PostgreSQL and pgAdmin containers (inside billing-client folder)
cd billing-client
docker-compose up -d

# 3. Run afip-soap-server first (port 8081)
cd afip-soap-server
./mvnw spring-boot:run

# 4. Run billing-client (port 8080)
cd billing-client
./mvnw spring-boot:run
```

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
Host:     localhost
Port:     5432
Database: billing_db
Username: billing_user
Password: billing_pass
```

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

### Invoices

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/invoices` | Submit invoice to AFIP for authorization |
| `GET` | `/api/v1/invoices` | Get all invoices |
| `GET` | `/api/v1/invoices/{id}` | Get invoice by internal ID |
| `GET` | `/api/v1/invoices/external/{externalInvoiceId}` | Get invoice by AFIP external ID |

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
    ],
    "netAmount": 1000.00,
    "vatAmount": 210.00,
    "totalAmount": 1210.00
}
```

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

| Status | Description |
|--------|-------------|
| `PENDING` | Saved locally, AFIP call in progress |
| `APPROVED` | CAE issued, invoice is valid |
| `REJECTED` | AFIP validation failed |

---

## 🛡️ Validation

The system has two independent validation layers:

**Layer 1 — Spring `@Valid` (billing-client)**
Validates the REST request before it reaches the service layer.
- Required fields
- CUIT format (`XX-XXXXXXXX-X`)
- Positive amounts
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

**Layer 2 — `InvoiceValidator` (afip-soap-server)**
Validates business rules on the SOAP server side.
- CUIT format on both issuer and receiver
- Item quantities and prices
- Cross-check: `totalAmount == netAmount + vatAmount`

Returns `201` with `status: REJECTED` and a list of error codes on failure.

### AFIP Error Codes

| Code | Description |
|------|-------------|
| `ERR_001` | External invoice ID is required |
| `ERR_003` | Issuer CUIT is required |
| `ERR_004` | Issuer CUIT format invalid |
| `ERR_007` | Receiver CUIT is required |
| `ERR_008` | Receiver CUIT format invalid |
| `ERR_010` | Invoice must contain at least one item |
| `ERR_015` | Net amount must be greater than zero |
| `ERR_018` | Total amount does not match netAmount + vatAmount |

---

## 🧠 Design Decisions

**Two independent applications** — Separating the client and the AFIP mock into two Spring Boot apps makes the architecture realistic and professional. Each app has its own responsibility and can be deployed independently.

**XSD as the single source of truth** — The SOAP contract is defined once in `afip-invoice.xsd` and shared between both projects. JAXB generates Java classes automatically from it. Any contract change only requires updating the XSD.

**Save before calling AFIP** — The invoice is persisted as `PENDING` before the SOAP call. This guarantees a database record exists even if the SOAP server is unreachable, enabling retry logic in the future.

**Interface + Implementation pattern on services** — Every service is defined as an interface (`InvoiceService`) and implemented separately (`InvoiceServiceImpl`). Controllers always inject the interface, making the implementation swappable without touching the controller layer.

**CUIT format validation in both layers** — The REST layer validates format with `@Pattern` before the request reaches the service. The SOAP server re-validates independently, simulating real AFIP behavior where the server enforces its own rules regardless of the client.

**XMLGregorianCalendar for SOAP dates** — JAXB maps `xs:date` and `xs:dateTime` to `XMLGregorianCalendar`. Conversion helpers centralize the `LocalDate`/`LocalDateTime` → `XMLGregorianCalendar` logic to keep the service code clean.

---

## 📄 License

This project is for educational purposes.
