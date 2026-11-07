# Event Service

Event Service owns the TicketFlow event catalogue, venues, categories and ticket-offer
metadata. Booking Service remains responsible for reservations and transactional inventory.

## Architecture

The service uses controllers for HTTP/DTO mapping, transactional services for business
rules, Spring Data repositories for persistence, and JPA entities for event lifecycle
transitions. Public endpoints return response DTOs only. The H2 database is in-memory;
the service registers with Eureka and imports optional central configuration.

## API

All endpoints are under `/api/v1`.

| Method | Path | Purpose |
|---|---|---|
| GET | `/events` | Search published events; supports `keyword`, `categoryId`, `city`, `venue`, `dateFrom`, `dateTo`, `minPrice`, `maxPrice`, `page`, `size`, `sort`, `direction` |
| GET | `/events/{id}` | Read a published event |
| GET | `/management/events/{id}` | Read an event including a draft |
| POST/PUT/DELETE | `/events[/{id}]` | Create, update or delete a draft |
| PATCH | `/events/{id}/publish` | Publish a valid draft with ticket offers |
| PATCH | `/events/{id}/close` | Close a published event |
| PATCH | `/events/{id}/cancel` | Cancel a draft or published event |
| GET/POST | `/venues` | List or create venues |
| GET/PUT/DELETE | `/venues/{id}` | Read, update or delete a venue |
| GET/POST | `/categories` | List or create categories |
| GET/PUT/DELETE | `/categories/{id}` | Read, update or delete a category |
| GET | `/events/{eventId}/ticket-tiers` | List offers for a published event |
| POST | `/events/{eventId}/ticket-tiers` | Add an offer to a draft |
| GET/PUT/DELETE | `/ticket-tiers/{id}` | Read, update or delete an offer |

Search page size is restricted to 1–100. Sort fields are restricted to `startDateTime`,
`title`, `createdAt` and `updatedAt`. A public event response includes venue location
details and ticket-tier DTOs; it never includes a draft, closed or cancelled event.

Event and ticket-tier edits are restricted to drafts so that published catalogue data
cannot silently conflict with ongoing sales. Event capacity cannot exceed venue capacity,
ticket quantities cannot exceed event capacity, and used venues/categories cannot be
deleted. Error responses include `timestamp`, `status`, `error`, `message` and `path`.

## Run and test

From the repository root:

```powershell
.\mvnw.cmd -pl services/event-service -am spring-boot:run
.\mvnw.cmd -pl services/event-service -am test
```

Port: `8082`. The API remains usable without Eureka or Config Server. Booking Service
reads published event data from `GET /api/v1/events/{id}`; its compact client view uses
the least expensive published ticket tier as the compatibility `price` field.
