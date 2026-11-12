#import "lib-a.typ": *

== UC1 Browse and Search Team-Priced Products <sec-a-uc01>

UC1 is BlocBuy's front door. Anyone, signed in or not, can page through the products on sale, narrow them by category or keyword and open one. Each product shows two prices: the individual price and, when a team is filling for it, the team price with the seats filled so far. The use case only reads. It creates no team, intent, payment or order, and it never calls identity-svc. This part traces UC1 from its flows of events to the code, including the keyword search and the optional re-ranking by meaning, which the SWE5006 report did not model. UC1 is designed and built; its status on AWS is given by capability in the status table of report §2.2.

=== Use Case and Flows of Events <sec-a-uc01-flows>

- *Actors.* Visitor (primary: an anonymous or signed-in buyer). The re-ranking service (secondary; reached only in A2).
- *Trigger.* The visitor opens the home page, changes the category or the search box, turns a page, or opens a product.
- *Services.* commerce-svc owns every step (its catalog and group-buy modules).
- *Preconditions.* None. Signing in is not required.
- *Postconditions.* The visitor has seen only products on sale, with prices worked out by the system. Nothing has changed.

#atable(
  [UC1 flows of events. B = basic flow, A = alternate flow, E = exception flow; "At" is the step where the flow branches off.],
  <tbl-a-uc01-flows>,
  columns: (auto, auto, 1fr),
  header: ([Step], [At], [What happens]),
  [*B1*], [], [The visitor asks for a page of products, optionally for one category.],
  [*B2*], [], [The system selects one page of products on sale, newest first.],
  [*B3*], [], [For each product on the page the system finds its best open team: open, before its deadline and with a free seat; the cheapest wins, and ties go to the lowest team identity.],
  [*B4*], [], [For each product the system works out the two prices with the rule of the product's pricing mode. The individual price is the product's own price; the team price is the best open team's price.],
  [*B5*], [], [The system shows each product as a card: title, image, both prices and the seats filled.],
  [*B6*], [], [The visitor opens one product.],
  [*B7*], [], [The system checks that the product exists and is on sale.],
  [*B8*], [], [The system finds the product's best open team and works out its two prices as in B3 and B4.],
  [*B9*], [], [The system shows the detail page: images, description, stock and number sold, both prices, and the open team with its seats and deadline. To start a team (UC3) or join this one (UC4) the visitor must sign in (UC2).],
  [*A1*], [B1], [The visitor types a keyword of up to 64 characters. A product matches when every word of the keyword appears in its title, subtitle or description, ignoring case. The matching products are shown a page at a time, newest first, and the flow goes on at B3.],
  [*A2*], [A1], [The request asks for re-ordering by meaning and the service is switched on. The system asks the re-ranking service to order this page's products by how well they fit the keyword. If the service is off, slow, unreachable or gives an unusable answer, the page keeps its order. The web page does not ask for re-ordering.],
  [*A3*], [B2], [No product matches the category or keyword: the visitor sees an empty page, not an error.],
  [*A4*], [B4], [A product has no open team, or its team uses another currency: only the individual price is shown, never a zero or invented team price.],
  [*A5*], [B7], [The product does not exist, was deleted, is a draft or is delisted: the visitor sees "not found". A hidden product gets the same answer as a missing one.],
  [*E1*], [B1], [The page number, page size or keyword is out of bounds: the request is refused and no products are shown.],
  [*E2*], [B4], [A product's pricing mode has no rule: this is a configuration fault, and the request fails rather than price the product by another rule.],
  [*E3*], [B3], [The quick store of best teams is unavailable: the system reads the teams from the database instead, and the visitor sees the same prices.],
)

=== Analysis Class Diagram <sec-a-uc01-ac>

@fig-a-uc01-ac is the analysis model. The visitor works through `CatalogUI`, and the re-ranking service sits behind the gateway boundary `SemanticRanker`. `BrowseListingsControl` realises UC1 and is the only object that talks to both boundaries and to the entities. `Product` owns the rule that turns its own price and a team into the displayed prices (`priceFor`), because that rule depends on the product's pricing mode. `Team` answers which of a product's teams is the best open one. `ProductStatus`, `PricingMode` and `TeamStatus` are enumerations. Compared with the SWE5006 model, the entity `Listing` is renamed `Product` to match the system-level view (@sec-a-reading), and `SemanticRanker` and the operation `matchOnSale` are new, for A1 and A2.

#afig("a-uc01-ac", [UC1 analysis class diagram (UML class notation, BCE stereotypes). The Visitor uses `CatalogUI`; the re-ranking service is reached only through `SemanticRanker`. The displayed prices come from `Product.priceFor`, not from the control.])

=== Analysis Sequence Diagrams <sec-a-uc01-as>

@fig-a-uc01-as-browse is the browse flow with the keyword search, the re-ordering and the empty page. The best open teams are fetched once for the whole page, before the price loop. @fig-a-uc01-as-detail is the detail flow; the not-found check comes before any team lookup. E1 (bounds), E2 (missing rule) and E3 (cache outage) need design concepts and appear only in the design sequences.

#afig("a-uc01-as-browse", [UC1 analysis sequence diagram, B1–B5 with A1–A4. The `alt` fragment chooses between browsing and searching; both continue at the same team lookup and price loop.])

#afig("a-uc01-as-detail", [UC1 analysis sequence diagram, B6–B9 with A5. The `break` fragment refuses a missing or hidden product before any team is read.])

=== Transition: Strategies and Mapping <sec-a-uc01-transition>

The strategies of @sec-a-strategies turn the analysis model into the design model; the tags on the design classes name them. Seven of the nine change UC1:

- *S1 Language and framework (AD-01, AD-18; NFR-05, 06, 26):* impacted `Product`, `Team` and the price pair → resultant the aggregate `Product`, the records `ListingQuery`, `ListingView`, `ListingDetailView`, `ListingPrice`, `GroupDealOffer`, `OpenDealOffer` and the enumeration `PricingMode`; dynamics @fig-a-uc01-ds-browse.
- *S2 Presentation (AD-09; NFR-07, 12, 16, 25):* impacted `CatalogUI` → resultant the routes `/` and `/products/$productId`, which call the generated API client and never compute a price; not drawn.
- *S3 Distribution (AD-02, AD-06, AD-09; NFR-05, 07, 64, 66):* impacted `BrowseListingsControl`, `Team` → resultant `ProductController` (REST, `Result` envelope) over `ProductAppService`; the catalog reaches teams only through the group-buy query port `GroupDealQueryService`, in the same process. UC1 makes no call to identity-svc, so browsing stays up while identity-svc is down; dynamics @fig-a-uc01-ds-browse.
- *S4 Persistence (AD-03, AD-11; NFR-05, 13):* impacted `Product`, `Team` → resultant `ProductRepository` → `ProductRepositoryImpl` → `BizProductMapper` (table `biz_product`), `GroupDealRepository` (table `biz_group_deal`) and the cache port `OpenDealOfferCache` → `RedisOpenDealOfferCache` (Valkey, entries live at most 25 s, NFR-41). A page costs one page query, one count and at most one batch team query, never one query per product, which keeps browse inside NFR-15. The read-only transaction commits before the cache is touched, so a slow cache never holds a database connection; dynamics @fig-a-uc01-ds-browse, @fig-a-uc01-ds-detail.
- *S5 External interface (backend ADRs 0001, 0004, 0005; NFR-21, NFR-22, NFR-23, NFR-31, NFR-50, NFR-66):* impacted `SemanticRanker` → resultant the port `ListingSearchReranker` and the adapter `TypeSafeJevListingSearchReranker`, off unless configured, time-boxed (1 s connect, 2 s read), limited to 20 candidates and failing open to the database order, so a ranker failure never loses the results. It does not bound latency: with ranking on, a slow ranker can add up to 3 s, more than NFR-15's 2 s target, so that target does not cover optional ranking; dynamics @fig-a-uc01-ds-search.
- *S6 Security (AD-05, AD-09; NFR-01, 46, 47, 58, 68):* impacted `CatalogUI` → resultant the interceptor in `SaTokenConfig`, which lets anonymous `GET` through on `/api/v1/products/**` (and on `/api/v1/invites/*` for UC7); Bean Validation bounds on page, size and keyword; `LIKE` wildcards escaped by `ProductRepositoryImpl.toLikePattern` and bound as parameters; one 404 for missing and hidden products; anonymous browse is one of the public routes that NFR-68's rule leaves open, with UC7's invite resolve and the sign-in callback; dynamics @fig-a-uc01-ds-search, @fig-a-uc01-ds-detail.
- *S7 Scheduling (AD-16; NFR-15, 32, 61):* not applied; nothing in UC1 runs on a timer.
- *S8 Concurrency (AD-02, AD-03; NFR-13, 21, 27, 30):* not applied; UC1 takes no lock and writes nothing. Each statement reads its own `READ COMMITTED` snapshot. A team shown can fill a moment later; UC4 re-checks it under its lock.
- *S9 Domain and integration events (AD-04; NFR-30, 39, 59, 60, 61, 63):* UC1 publishes nothing. It relies on `OpenDealOfferCacheSubscriber`, which evicts a product's cached team after `MemberJoined`, `TeamSucceeded` or `TeamExpired` commits (@fig-a-uc04-ds-events, @sec-a-uc05). The expiry and seat check on every cache hit covers a missed eviction.

#atable(
  [UC1 analysis → design mapping. Every analysis object of @fig-a-uc01-ac, and every design class of @fig-a-uc01-dc-read and @fig-a-uc01-dc-price.],
  <tbl-a-uc01-map>,
  columns: (1.05fr, 0.6fr, 2.6fr, 0.55fr, 0.7fr),
  header: ([Analysis object], [Stereotype], [Design elements (layer, module)], [Strategies], [Figures]),
  [`CatalogUI`], [boundary], [Frontend routes `/` and `/products/$productId` (not drawn, S2). Adapter: `ProductController` (`GET /api/v1/products`, `GET /api/v1/products/{id}`).], [S2, S3, S6], [@fig-a-uc01-dc-read],
  [`SemanticRanker`], [boundary], [Port `ListingSearchReranker` (domain); adapter `TypeSafeJevListingSearchReranker` (infrastructure).], [S5], [@fig-a-uc01-dc-read],
  [`BrowseListingsControl` (browse, B1–B5)], [control], [Use case controller `ProductAppServiceImpl.pageListings`, behind `ProductAppService` (application, catalog).], [S1, S3, S4], [@fig-a-uc01-dc-read],
  [`BrowseListingsControl` (search, A1–A2)], [control], [Use case controller `ProductAppServiceImpl.search`, with its private `rerank`.], [S3, S5], [@fig-a-uc01-dc-read],
  [`BrowseListingsControl` (view, B6–B9)], [control], [Use case controller `ProductAppServiceImpl.getListingDetail`.], [S3, S4], [@fig-a-uc01-dc-read],
  [`Product`], [entity], [Aggregate root `Product` with `ProductStatus` (not drawn; lifecycle in @sec-a-uc09); port `ProductRepository`, adapter `ProductRepositoryImpl`, mapper `BizProductMapper`, table `biz_product`. `priceFor` → `PricingStrategyResolver.strategyFor` and `PricingStrategy.resolve`, implemented by `DualPricingStrategy`.], [S1, S4], [@fig-a-uc01-dc-read, @fig-a-uc01-dc-price],
  [`Team`], [entity], [Aggregate root `GroupDeal` (group-buy), read only through `GroupDealQueryService` / `GroupDealQueryServiceImpl` and `GroupDealRepository`; table `biz_group_deal`. `bestOpenFor` → `findBestOpenDealsByProductIds`.], [S3, S4], [@fig-a-uc01-dc-price],
  [(none)], [—], [Introduced by a strategy: records `ListingQuery`, `ListingView`, `ListingDetailView`, `ListingPrice`, `GroupDealOffer`, `OpenDealOffer` and enumeration `PricingMode` (S1); cache port `OpenDealOfferCache` and adapter `RedisOpenDealOfferCache` (S4). Not drawn: `ListingView`, `ListingDetailView`, `ActiveDealView`, `OpenDealOfferCache.Lookup`, `PricingMode`, `PricingConfig` (which registers `DualPricingStrategy`), the response records of `ProductController`, and `SaTokenConfig` (S6, @sec-a-uc02).], [S1, S4, S6], [@fig-a-uc01-dc-read, @fig-a-uc01-dc-price],
)

=== Design Class Diagrams <sec-a-uc01-dc>

The design is drawn in two slices, both in commerce-svc: the read path from the controller to the table (@fig-a-uc01-dc-read), and the displayed prices with the best open team (@fig-a-uc01-dc-price). `ProductRepository` → `ProductRepositoryImpl` → `BizProductMapper` is the part's representative persistence chain; `GroupDealRepository` follows the same shape. A class that appears in two slices shows only the members that slice uses. «static» marks a static operation, because the diagram kit cannot underline. An association means a field reference; a dependency means a parameter, a return value or a created object.

#afig("a-uc01-dc-read", orient: "landscape", [UC1 design class diagram, slice 1: the catalog read path (UML, commerce-svc). The application service depends only on ports: the product repository, the re-ranking gateway and the group-buy query service.])

#afig("a-uc01-dc-price", [UC1 design class diagram, slice 2: displayed prices and the best open team (UML, commerce-svc). The left half is the Strategy: the service never computes a price itself. The right half is the cache-aside lookup: the repository is read only for products the cache missed.])

=== Design Sequence Diagrams <sec-a-uc01-ds>

Each design sequence retells part of @tbl-a-uc01-flows; the notes carry the step tags. A `critical` fragment is a database transaction. @fig-a-uc01-ds-browse is the basic browse flow: its read-only transaction ends before the team lookup, and the price loop makes no database call. @fig-a-uc01-ds-search is the search: the repository adapter escapes each word, and the re-ranking can change only the order of the page, never its contents. @fig-a-uc01-ds-detail is the detail flow; it also opens up the cache-aside team lookup that B3 and B8 share.

#afig("a-uc01-ds-browse", [UC1 design sequence diagram, B1–B5 with A3, A4, E1 and E2 (commerce-svc). The product page is read in a short read-only transaction; teams and prices are added after it commits, with one batch call and no query per product.])

#afig("a-uc01-ds-search", [UC1 design sequence diagram, A1 and A2: keyword search and optional re-ranking (commerce-svc, external re-ranking service). Every failure of the re-ranking returns the database order, so A2 can only reorder the page.])

#afig("a-uc01-ds-detail", [UC1 design sequence diagram, B6–B9 with A5 and E3 (commerce-svc, Valkey). The on-sale check runs before the team lookup, and four reasons give one 404. A cache outage turns into one database read; only an available cache is refilled.])

=== Operation Specifications <sec-a-uc01-ops>

#atable(
  [UC1 operation specifications (commerce-svc). Errors are `BizException` error codes unless named as another exception. The entry `ProductController.pageListings` adds only Bean Validation: `VALIDATION_FAILED` (`COMMON_400_002`) for page < 1, size outside 1–100 or a keyword over 64 characters (E1).],
  <tbl-a-uc01-ops>,
  columns: (1.9fr, 0.6fr, 0.75fr, 1.2fr, 0.95fr),
  header: ([Operation; transaction], [Purpose], [Preconditions], [Postconditions and effects], [Errors]),
  [`ProductAppServiceImpl.pageListings(page: Integer, size: Integer, category: String): PageResult<ListingView>`. Read-only transaction around the product reads only.], [Browse, B2–B4.], [—], [Transaction committed before the one batch team lookup; nothing stored.], [`PRICING_MODE_UNSUPPORTED` (`CATALOG_500_001`).],
  [`ProductAppServiceImpl.search(query: ListingQuery): PageResult<ListingView>`. No transaction: two statements.], [Search, A1–A2.], [Keyword non-blank.], [Same items as without re-ranking, possibly in another order.], [As `pageListings`; re-ranking errors are swallowed.],
  [`ProductAppServiceImpl.getListingDetail(productId: UUID): ListingDetailView`. Read-only transaction for `findById` only.], [Detail, B7–B8.], [—], [View of an on-sale product.], [`PRODUCT_NOT_FOUND` (`CATALOG_404_001`) for missing, deleted, draft or delisted.],
  [`GroupDealQueryServiceImpl.findBestOpenDealsByProductIds(productIds: Collection<UUID>): Map<UUID, OpenDealOffer>`. No transaction.], [Best open team, B3 and B8.], [—], [Misses read in one query and written back only to an available cache (TTL 15–25 s; 5–10 s for no team). Empty input does no I/O.], [None: a cache failure is logged and read through (E3).],
  [`DualPricingStrategy.resolve(product: Product, activeDeal: GroupDealOffer): ListingPrice`, chosen by `PricingStrategyResolver.strategyFor(mode: PricingMode): PricingStrategy`. Pure.], [Price pair, B4.], [One strategy per mode, indexed at start-up.], [`individualPrice` = `originalPrice`; `groupPrice` = `dealPrice` only for a deal in the same currency (A4).], [`PRICING_MODE_UNSUPPORTED`; `IllegalArgumentException` for a null product.],
  [`TypeSafeJevListingSearchReranker.rerank(keyword: String, candidates: List<ListingSearchReranker.Candidate>): List<UUID>`. Outside any transaction.], [Re-ranking, A2.], [Enabled; two or more candidates.], [Every candidate id once: the first 20 in the service's order, the rest appended.], [None thrown: an HTTP error or malformed answer returns the original order.],
)

=== State Machines <sec-a-uc01-states>

UC1 changes no state, so it has no statechart. It reads two lifecycles drawn elsewhere: only products in `ProductStatus.ON_SALE` are visible (statechart in @sec-a-uc09), and only teams in `DealStatus.OPEN`, before their deadline and with a free seat, are offered (statechart in @sec-a-uc05).

=== Patterns Applied <sec-a-uc01-patterns>

Two patterns carry UC1's design problems. The ports `ProductRepository`, `OpenDealOfferCache` and `ListingSearchReranker` also follow Ports and Adapters, as every use case does (@sec-a-strategies).

#atable(
  [Patterns in the UC1 design: the problem first, then the roles mapped to real classes, then the consequences.],
  <tbl-a-uc01-patterns>,
  columns: (0.6fr, 0.95fr, 1.3fr, 1.15fr),
  header: ([Pattern (kind)], [Problem in UC1], [Roles → classes], [Consequences]),
  [Strategy (GoF, behavioural)], [How a product's two prices are worked out depends on its pricing mode. An `if` on the mode inside the browse service would grow with every new mode and be repeated by the team-price quote of UC3.], [Context: `ProductAppServiceImpl`, which gets its strategy from `PricingStrategyResolver` by `PricingMode`. Strategy: `PricingStrategy`. Concrete strategy: `DualPricingStrategy`, registered as a bean by `PricingConfig`. Collaboration: @fig-a-uc01-dc-price, @fig-a-uc01-ds-browse.], [A new mode is a new strategy bean and an enumeration value; the services do not change (open–closed). Liability: with one strategy the indirection costs more than it saves today, and a mode without a strategy fails only at run time (E2).],
  [Cache-aside (architectural)], [Every card needs its best open team, and the browse page must stay fast at 100 users (NFR-15) without a query per card.], [Application: `GroupDealQueryServiceImpl`. Cache port: `OpenDealOfferCache`; adapter `RedisOpenDealOfferCache`. Source of truth: `GroupDealRepository`. Invalidation: `OpenDealOfferCacheSubscriber` after commit (UC4, UC5). Collaboration: @fig-a-uc01-dc-price, @fig-a-uc01-ds-detail.], [Hits avoid SQL; an outage degrades to one query. Liability: a card can be up to 25 s stale, so UC4 re-checks the team under its lock.],
)

=== Flow-to-Design Trace <sec-a-uc01-trace>

#atable(
  [UC1 flow-to-design trace: each flow step, its analysis message and the design operations that realise it.],
  <tbl-a-uc01-trace>,
  columns: (0.38fr, 1.15fr, 2.6fr, 0.62fr),
  header: ([Step], [Analysis message], [Design operations], [Figures]),
  [B1], [`browse`], [`ProductController.pageListings` → `ProductAppServiceImpl.pageListings`], [@fig-a-uc01-ds-browse],
  [B2], [`Product.selectOnSale`], [`ProductRepository.pageOnSale` (read-only transaction)], [@fig-a-uc01-ds-browse],
  [B3], [`Team.bestOpenFor`], [`GroupDealQueryService.findBestOpenDealsByProductIds` → `OpenDealOfferCache.findByProductIds`, `GroupDealRepository.findBestOpenDealsByProductIds`, `putAll`], [@fig-a-uc01-ds-browse, @fig-a-uc01-ds-detail],
  [B4], [`Product.priceFor` (loop)], [`ProductAppServiceImpl.resolvePrice` → `PricingStrategyResolver.strategyFor`, `ProductAppServiceImpl.toOffer`, `PricingStrategy.resolve`], [@fig-a-uc01-ds-browse],
  [B5], [`showListings`], [`ProductAppServiceImpl.toPageResult`; `ProductController.toPageResult`; `Result<PageResult<ProductSummaryResponse>>`], [@fig-a-uc01-ds-browse],
  [B6], [`open`, `viewListing`], [`ProductController.getListing` → `ProductAppServiceImpl.getListingDetail`], [@fig-a-uc01-ds-detail],
  [B7], [`Product.isOnSale`], [`ProductRepository.findById`; `Product.isOnSale`], [@fig-a-uc01-ds-detail],
  [B8], [`bestOpenFor`, `priceFor`], [As B3 and B4, for one product], [@fig-a-uc01-ds-detail],
  [B9], [`showListing`], [`ListingDetailView` → `ProductDetailResponse`], [@fig-a-uc01-ds-detail],
  [A1], [`search`, `matchOnSale`], [`ListingQuery.of`, `keywordTokens`; `ProductAppServiceImpl.search`; `ProductRepositoryImpl.searchOnSale`, `toLikePattern`; `BizProductMapper.selectVisibleSearchPage`, `countVisibleSearch`], [@fig-a-uc01-ds-search],
  [A2], [`SemanticRanker.rank`], [`ProductAppServiceImpl.rerank` → `ListingSearchReranker.rerank` (`TypeSafeJevListingSearchReranker`)], [@fig-a-uc01-ds-search],
  [A3], [`showListings` (empty)], [`PageResult` with no items, HTTP 200], [@fig-a-uc01-ds-browse],
  [A4], [`priceFor` without a team], [`DualPricingStrategy.resolve` returns `groupPrice` null], [@fig-a-uc01-ds-browse],
  [A5], [`showNotFound`], [`isOnSale` false or empty `Optional` → `PRODUCT_NOT_FOUND` → 404], [@fig-a-uc01-ds-detail],
  [E1–E3], [—], [E1: Bean Validation on `pageListings` → `VALIDATION_FAILED`. E2: `strategyFor` → `PRICING_MODE_UNSUPPORTED`. E3: `Lookup.available` false → repository read, no `putAll`], [@fig-a-uc01-ds-browse, @fig-a-uc01-ds-search, @fig-a-uc01-ds-detail],
)
