# Modular Monolith, DDD, and COLA

Read this only when adding modules, moving code, designing domain models, or reviewing dependency boundaries.

## Standard Structure

BlocBuy is a single-process, single-deployment-unit modular monolith. The recommended Maven aggregate structure is shown below; `client` and `bootstrap` are contract/assembly modules and do not count as one of the four business layers:

```text
blocbuy-parent/
├── blocbuy-client/          # External DTOs, API/event contracts; no business implementation
├── blocbuy-adapter/         # Inbound adapters: Web, RPC, MQ consumers, schedulers
├── blocbuy-application/     # command/query, use-case services, transaction orchestration, assemblers
├── blocbuy-domain/          # Aggregates, entities, value objects, domain services/events, ports
├── blocbuy-infrastructure/  # MyBatis, PostgreSQL, Redis, ES, MQ producers, third-party implementations
└── blocbuy-bootstrap/       # Spring Boot startup, configuration, and final assembly
```

Every module must be organized first by business domain. A global `controller/service/mapper/entity` grab-bag is forbidden:

```text
com.blocbuy
├── identity
├── catalog
├── groupbuy
├── order
├── inventory
├── payment
├── merchant
├── marketing
├── fulfillment
├── search
└── notification
```

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
- The application layer must not depend directly on MyBatis Mappers, `RedisTemplate`, `ElasticsearchClient`, or `RabbitTemplate`; it depends only on domain ports / application ports.
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

- Implements repositories, queries, caching, search, message publishing, third-party clients, and technical configuration.
- `Mapper + DO/PO + RepositoryImpl + Converter` all belong to infrastructure; Mappers must not be exposed across layers.
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
- Inventory: deductions must be protected by atomic mechanisms such as database conditional updates/version constraints; "read stock first, then update unconditionally" is forbidden.
- Payment/refund: establish uniqueness on the payment channel transaction number and the business order number; callbacks must verify signatures, match amount/currency/merchant, and be idempotent.
- Group-buy completion: define a single state machine for headcount calculation, seat reservation, timeout, cancellation, and refund; duplicate events must not cause duplicate group completion or refunds.
- Search and recommendation are derived views; price, inventory, and sellable status are re-verified against the source of truth at critical transaction steps.
