# Modular Monolith, DDD, and COLA

Read this only when adding modules, moving code, designing domain models, or reviewing dependency boundaries.

## Standard Structure

BlocBuy is a single-process, single-deployment-unit modular monolith. The recommended Maven aggregate structure is shown below; `client` and `bootstrap` are contract/assembly modules and do not count as one of the four business layers:

```text
blocbuy-parent/
├── blocbuy-client/          # External DTOs, API/event contracts; no business implementation
├── blocbuy-adapter/         # Inbound adapters: Web, schedulers
├── blocbuy-application/     # command/query, use-case services, transaction orchestration, assemblers
├── blocbuy-domain/          # Aggregates, entities, value objects, domain services/events, ports
├── blocbuy-infrastructure/  # MyBatis, PostgreSQL, Redis, Sa-Token RBAC impl, third-party implementations
└── blocbuy-bootstrap/       # Spring Boot startup, configuration, and final assembly
```

Every module must be organized first by business domain. A global `controller/service/mapper/entity` grab-bag is forbidden. The confirmed v1 domains (user decision 2026-08-30) are:

```text
com.blocbuy
├── identity
├── groupbuy
├── order
├── catalog
├── notification
└── payment        (added 2026-08-31; Stripe-backed)
```

Do not create packages for inventory, merchant, marketing, fulfillment, or search — they are out of scope for v1 and there is no MQ/ES in the stack to back them. New domains require explicit user approval.

Example: `blocbuy-domain/.../groupbuy/model/aggregate/GroupDeal.java`, `blocbuy-application/.../groupbuy/command/CreateGroupDealCommand.java`. The actual business domains are decided by domain modeling; do not create empty modules in advance just because they appear in this list.

## Dependency Direction

Allowed core dependencies:

```text
adapter -> application -> domain
infrastructure ---------> domain
bootstrap -> adapter + application + infrastructure
client <- adapter/application (contracts only)
domain -> JDK + a minimal amount of framework-free generic types
```

- Outer layers depend on inner layers; the domain layer must never depend back on outer layers.
- The application layer must not depend directly on MyBatis Mappers or `RedisTemplate`; it depends only on domain ports / application ports.
- External-capability ports (e.g. payment `PaymentGateway`) are defined on the domain side (`com.blocbuy.{domain}.gateway`), because the infrastructure module must not depend on the application module. Port signatures use only provider-free types; SDK classes (e.g. `com.stripe.*`) appear exclusively in the infrastructure implementation.
- The infrastructure layer implements ports and converts DOs/POs to domain objects; persistence annotations and table structures must not leak into aggregates.
- Cross-domain calls use the other domain's public application interface, query facade, or domain events; cross-domain joins/Mapper calls to reuse "convenient data" are forbidden. When a read model genuinely needs cross-domain aggregation, build an explicit query model and record its ownership.
- Enforce boundaries with both Maven module dependencies and ArchUnit; verbal agreements are not enough.

## Four-Layer Responsibilities

### Adapter

- Controllers convert HTTP requests into Commands/Queries; Consumers convert messages into application commands.
- Perform protocol-level validation, Sa-Token authentication entry points, header/path/query parsing, and DTO conversion.
- Do not start business transactions, write core business rules, call Mappers, assemble SQL, or manipulate caches directly.

### Application

- Every public method corresponds to a clear use case; commands and queries may be modeled separately without forcing a heavyweight CQRS framework.
- Orchestrate aggregates, domain services, repositories, events, and external ports; the transaction boundary is usually the public method of a command use-case service.
- Do not carry rules that belong to a single aggregate; do not return persistence DOs.

### Domain

- The aggregate root is the only entry point for modifying internal aggregate state; constructors/factory methods ensure objects are valid from birth.
- Entities compare by stable identity; value objects compare by value and are immutable where possible; monetary amounts use `BigDecimal`/a Money value object — `double` is forbidden.
- Rules go into aggregates; rules that span entities with no natural single home go into domain services; external dependencies use Gateway/Repository ports.
- Domain events use past-tense semantics for what has already happened, e.g. `OrderCreated`; events carry only stable, minimal business facts.
- Aggregate boundaries are decided by consistency constraints; do not cram entire orders, products, merchants, and memberships into one giant aggregate; across aggregates hold only IDs/value objects.

### Infrastructure

- Implements repositories, queries, caching, and technical configuration; message publishing and search clients exist only if the user later approves MQ/ES.
- `Mapper + DO/PO + RepositoryImpl + Converter` all belong to infrastructure; Mappers must not be exposed across layers. Converters are hand-written — MapStruct is not in the stack (user decision 2026-08-30).
- Handle timeouts, retries, circuit breaking, protocol error conversion, and resource cleanup; convert technical exceptions into project-defined system exceptions instead of disguising them as business failures.

## Domain Modeling Constraints

- Define terms and invariants first, then tables and interfaces; one concept uses exactly one name within a business domain.
- Commands express intent (`ConfirmOrderCommand`); queries express the required view (`OrderDetailQuery`); catch-all `save/update/process` names are forbidden.
- State transitions go through behavioral methods that validate the current state; Controllers/Application code must not call `setStatus` directly.
- Domain objects avoid public setters; provide semantic methods for necessary changes. Do not use Lombok `@Data` to break encapsulation or generate incorrect `equals/hashCode/toString` on aggregates.
- Design repositories per aggregate root; do not mechanically create one repository per table. Query read models may use a dedicated QueryGateway.
- `common` holds only a truly stable, business-free minimal shared kernel; do not dump code with no clear home into `common/utils`.
- Prefer clear in-process application calls within a local transaction for cross-domain consistency; use events only when decoupling, long-running work, or eventual consistency genuinely pay off — avoid "events for events' sake".

## BlocBuy Key Boundaries

- Order placement: price snapshots, promotion results, and shipping-info snapshots must be traceable; referencing only current values that can change is not allowed.
- Group-buy participation counts: updates must be protected by atomic mechanisms such as database conditional updates/version constraints; "read count first, then update unconditionally" is forbidden.
- Group-buy completion: define a single state machine for headcount calculation, seat reservation, timeout, cancellation, and refund; duplicate triggers (schedulers, retries) must not cause duplicate group completion or refunds.
- Payments (domain added 2026-08-31, Stripe): uniqueness on the provider intent id and the business payment number (`uq_biz_payment_provider_intent`, `uq_biz_payment_payment_no`); callbacks verify the signature first (raw body forwarded untouched), deduplicate on the provider event id (`log_payment_webhook_event`), match amount/currency against the persisted row, and are idempotent; the payment row is persisted before the provider call so callbacks always find local state.
- Any future search/recommendation feature is a derived view; price, availability, and sellable status are re-verified against the source of truth at critical transaction steps.
