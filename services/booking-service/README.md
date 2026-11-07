# Booking Service

Booking Service owns reservations and per-ticket-tier inventory. Event Service remains the
source of truth for events and offers; Booking Service communicates with its internal
read-only HTTP API and stores only event/tier identifiers and immutable name/price
snapshots. No Event Service database or JPA entities are shared.

## Booking lifecycle and inventory

`PENDING_PAYMENT` holds stock until payment confirmation, cancellation, payment failure,
or expiration. A successful trusted payment callback changes the reservation to
`CONFIRMED` and atomically transfers every line's quantity from reserved to sold.
Cancellation, payment failure and expiration release held stock once. Confirmed bookings
are not refundable through this API. A row lock on the booking serializes payment,
cancellation and expiration transitions.

Inventory is tracked per ticket tier:

```
availableQuantity = totalQuantity - reservedQuantity - soldQuantity
```

Reservation uses a conditional database `UPDATE` against the inventory row, so simultaneous
requests cannot oversell. Inventory sync updates reference quantities without resetting
reserved/sold counts, and refuses a catalog decrease below already reserved or sold units.
The inventory version is incremented with each atomic update. Each booking snapshots the
event/tier names and unit price; the backend calculates line subtotals and the total.

Idempotency is scoped to `(customerId, idempotencyKey)` with a database unique constraint.
The request hash is based on the event, customer and normalized ticket lines. Replaying the
same request returns the original booking; reusing the key for a different request returns
409.

## REST API

All endpoints are under `/api/v1`.

| Method | Path | Purpose and responses |
|---|---|---|
| POST | `/bookings` | Create temporary reservation; `201`, `400`, `404`, `409`, `503` |
| GET | `/bookings/{id}` | Booking detail by ID; `200`, `404` |
| GET | `/bookings/reference/{reference}` | Booking detail by non-predictable reference; `200`, `404` |
| GET | `/bookings/customer/{customerId}?page=0&size=20` | Paginated customer history; `200`, `400` |
| PATCH | `/bookings/{id}/cancel` | Cancel pending reservation and release holds; `200`, `404`, `409` |
| PATCH | `/internal/bookings/{id}/confirm` | Trusted payment callback; `200`, `404`, `409` |
| PATCH | `/internal/bookings/{id}/payment-failed` | Trusted payment failure callback; `200`, `404`, `409` |
| GET | `/inventory/events/{eventId}` | Inventory by tier for an event |
| GET | `/inventory/events/{eventId}/ticket-tiers/{ticketTierId}` | Inventory for one tier |

`customerId` is accepted in the development API until authentication is integrated.
Payment callback routes and Event Service internal routes are not authenticated in this
development stage: restrict them to trusted service-to-service network traffic or add
service authentication before production. Swagger UI is at `/swagger-ui.html`; OpenAPI
JSON is at `/v3/api-docs`.

## Event Service contract and resilience

Booking uses the existing internal catalogue contracts:

* `GET /api/v1/internal/events/{eventId}`
* `GET /api/v1/internal/events/{eventId}/ticket-tiers/{ticketTierId}`

Event Service provides these internal routes using its existing event/tier DTOs. Public
catalogue GET routes continue to hide drafts. Booking refuses creation when either
catalogue lookup fails, the event is not published or has started, the tier belongs to a
different event, or its sale window is closed.

OpenFeign routes through Eureka (`event-service`) or `EVENT_SERVICE_URL` for local runs.
Connect/read timeouts, Resilience4j circuit breaker/retry/TimeLimiter, temporary hold
duration, currency, quantity limit and expiration scan settings are configurable in
`src/main/resources/application.yml`. Retries apply only to idempotent catalogue reads,
never to booking creation or inventory mutations. Catalogue failures are returned as
503; no local fallback fabricates event or price information.

## Example

Create a booking (the event and ticket tier must exist and be published/in sale):

```powershell
curl.exe -i -X POST http://localhost:8083/api/v1/bookings `
  -H "Content-Type: application/json" `
  -d '{"eventId":12,"customerId":42,"lines":[{"ticketTierId":31,"quantity":2}],"idempotencyKey":"checkout-42-a81c4f"}'
```

When payment has been independently verified, the trusted Payment Service will call
`PATCH /api/v1/internal/bookings/{id}/confirm`. Creating a booking does not imply payment
success. Pending holds are expired by the scheduled expiration worker.

## Run and test

From the repository root:

```powershell
.\mvnw.cmd -f services/booking-service/pom.xml spring-boot:run
.\mvnw.cmd -f services/booking-service/pom.xml test
```

The service uses H2 in-memory by default, listens on port `8083`, and can start without
Eureka or Config Server. For a local Event Service, set `EVENT_SERVICE_URL=http://localhost:8082`.
Tests exercise idempotency, lifecycle transitions, expiration, the actual Feign HTTP
contract, and concurrent requests for the last tier units.
