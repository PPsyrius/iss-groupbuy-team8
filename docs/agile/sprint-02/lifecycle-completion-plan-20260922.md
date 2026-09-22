# 团生命周期与退款收尾 — 实施计划(Liu Jin)

日期:2026-09-22。分支:`feature/order-deal-20260919`。规范:`backend-constraints-skill` + `pgsql-ddl`。
前置:`order-payment-integration-plan-20260922.md` 四个 PR 已执行完毕(`d24f8c6` / `0dc6fe1` / `ace9c24` / `d1c9d7e`)。

## 0. 需求确认(2026-09-22 实测)

结算主干已通:浏览 → `POST /payments/intents` → `POST /payments/checkout-sessions` → Stripe → `POST /webhooks/stripe` → 建团 + 建单,全程生产代码。**但团的生命周期没有终点**:团满了订单不确认,团过期了订单不取消、钱不退。

### 0.1 缺口对照 issue

| 缺口 | 归属 issue | 负责人 | 实测证据 |
|---|---|---|---|
| 团成功/过期的状态解析与领域事件 | #28 UC5 | @l0sgAi(我) | `DealStatus` 无转移矩阵;`GroupDeal` 无 `expire`;`DealStatus.EXPIRED` 生产代码只有读没有写 |
| 订单 pending → confirmed / cancelled | #124 UC10 剩余项 | @l0sgAi(我) | `OrderRepository.confirmByDealId` / `cancelByDealId` 已实现已测,**应用层零调用方** |
| 过期退款的触发与执行 | #29 UC6 | @Yorick-Yu(已移交) | `StripeCheckoutService.recordExpiredTeam` 与 `RefundService.processNext` 生产侧无任何调用方;全仓 `src/main` 零个 `@Scheduled` |
| 退款 worker 接线前的两个缺陷 | #132 / #133 | @Yorick-Yu(已移交) | 见 §3.2 |
| 商品团条款无写入面 | NFR-28 / 后端 ADR 0001 "Open" | 未指派 | `grep team_target_size` 全仓 main 只有读,`AdminListingController.ListingContent` 无此二字段 |
| `GET /orders/{id}` | #31 UC8 后端半边 | @limjiechao | 契约只有 `/orders/me` |
| 未知 payment 的 webhook 返 500 | #139 | @Yorick-Yu(已移交) | `apply` 的 `UUID.fromString` 与 `settle` 的 `require(id)` 路径上无人捕获 |
| sandbox 闸门七处重复 | #130 | @Yorick-Yu(已移交) | 三个文件七处;`StripePaymentGateway` 则一处都没有 |

最后两行不是生命周期缺口,是 2026-09-22 决定并入本迭代的支付切片加固,理由见 §0.4。

`docs/payment/stripe-release-cleanup-20260920.md` 预发清单第 2 条 "Wire and monitor the refund worker and expiry
notification path" 与上表第 3、4 行是同一件事。第 1 条已由上一计划关闭。

### 0.2 UC5 的验收条件逐条落点

issue #28 明示两个设计问题,课程交付要看:

- **State**:每个生命周期状态拥有自己的合法转移,非法转移由聚合拒绝 → `DealStatus.canTransitionTo` 矩阵 + `GroupDeal.expire`,与 `OrderStatus.canTransitionTo` 同构。
- **Observer(发布侧)**:状态解析发布 `TeamSucceeded` / `TeamExpired`,后果由订阅者承担而非内联调用 → §3.1。

"Scheduled/timeout resolution path covered" → §3.3 的扫描器。

### 0.3 一个已经不成立的 issue

**#138 已被 `0dc6fe1` 消除,建议关单。** 该 issue 的前提是 `businessDeadline` 可能早于 `quoteValidUntil`,于是
`prepare` 只校验后者会签出一个活过团截止的会话。实际 `PurchaseIntentAppService` 两条路径都保证
`quoteValidUntil ≤ businessDeadline`:

- JOIN:`quoteValidUntil = earlier(now+2400s, team.expireTime())`,`businessDeadline = team.expireTime()`
- START:`quoteValidUntil = now+2400s`,`businessDeadline = now+lifetimeSeconds`,而 `lifetimeSeconds ≥ 2400`(V14 注释与 `ProductTeamTerms.startable()` 共同保证)

`prepare` 现有的 `expires.isAfter(quote.quoteValidUntil())` 因此恒等于或严于 issue 要求的检查。可选在
`prepare` 里改成 `quote.expiredAt(expires)` 作为防回归,一行,放进 PR-F 附带。

### 0.4 并入本迭代的两个支付切片 issue(2026-09-22 决定)

#139 与 #130 本来不属于生命周期收尾。并进来的理由各不相同,分别记下:

- **#139(未知 payment 的 webhook 返 500)** —— 它会在本迭代的联调过程中持续发作。PR-G 把扫描器和退款
  worker 接上调度之后,本地和共享服务器共用同一个 Stripe sandbox endpoint 就是常态;每一条不属于本库的
  事件都会 500,而 Stripe 对非 2xx 重试最长 3 天。留着它,PR-G 的日志会被别人环境的重试淹掉,判不出
  自己的作业有没有正常跑。**它是本迭代可观测性的前置条件,不是顺手。**
- **#130(sandbox 闸门七处重复)** —— 纯重构,没有行为收益,唯一价值是让"只走测试模式"这条不变式有一个
  唯一事实源。并进来是因为本迭代之后支付切片的代码面就定型了,再往后改的成本只会更高;而且它的安全网
  (两个网关现有的 live key / 未配置拒绝测试)已经存在,重构可验证。**排在最后一个 PR,任何时候砍掉都不
  影响其它六个。**

两者都不动 HTTP 契约,不动数据库 schema。

## 1. 边界与分层落点

硬门禁不变:跨域只能走对方的应用层接口/门面。本计划新增的跨域方向只有一条 —— **groupbuy 发布事实,
order 与 payment 订阅**。领域事件是显式发布的契约(`com.blocbuy.<domain>.event` 已有 package-info 声明
"Published in-process; no message broker in v1"),订阅者只消费不可变 record,不触碰 `GroupDeal` 聚合、
mapper、DO 或 RepositoryImpl,**不构成跨域越界**。

```
groupbuy / domain       DealStatus 转移矩阵 + GroupDeal.expire + TeamSucceeded / TeamExpired(record)
groupbuy / domain       PaidGroupDealRepository 增 dueForExpiry / expire
groupbuy / application  PaidGroupDealService 发布 TeamSucceeded;GroupDealExpiryService 解析过期并发布 TeamExpired
order    / application  OrderLifecycleSubscriber(订阅两个事件 → confirmByDealId / cancelByDealId)
payment  / application  TeamExpiryRefundSubscriber(订阅 TeamExpired → recordExpiredTeam)
common   / domain       SchedulerLeaseRepository(端口)
common   / infra        SchedulerLeaseRepositoryImpl + SysSchedulerLockMapper.xml
bootstrap / config      SchedulingConfiguration(@EnableScheduling + 两个 @Scheduled 驱动)
catalog  / *            团条款写入面(PR-H)
order    / adapter      GET /orders/{id}(PR-I)
payment  / application  StripeCheckoutService.apply / settle 的未知 payment 分支(PR-J)
payment  / infra        StripeProperties.sandbox + StripeClientProvider 闸门(PR-K)
```

PR-J、PR-K 全部落在 payment 切片内部,不新增任何跨域方向。

事件用 Spring `ApplicationEventPublisher`,**发布点与订阅点都在 application 层**;事件 record 本身留在
domain 且不带任何注解,`domain_is_framework_free` 因此照常通过。

## 2. PR 划分

| 序 | PR | 内容 | 依赖 | 契约变更 |
|---|---|---|---|---|
| 1 | PR-E | 团生命周期解析 + 领域事件 + 订单订阅者(UC5 主体) | — | 无 |
| 2 | PR-F | 退款 worker 接线前置修复(#132 / #133)+ #138 防回归 | — | 无 |
| 3 | PR-J | 未知 payment 的 webhook 记录并确认(#139) | — | 无 |
| 4 | PR-G | 调度基座(租约)+ 团过期扫描 + 退款 worker | PR-E、PR-F | 无 |
| 5 | PR-H | 商品团条款管理面(NFR-28) | — | **有**(additive) |
| 6 | PR-I | `GET /orders/{id}` | — | **有**(additive) |
| 7 | PR-K | sandbox 闸门收口到 `StripeClientProvider`(#130) | PR-J | 无 |


**PR-J 必须先于 PR-K,且 PR-K 排在最后。** 两者都动 `StripeCheckoutService`:PR-J 改
`apply` / `settle` 的行为,PR-K 只搬运 sandbox 判据的位置。重构叠在已定型的行为之上,不是反过来;
否则 PR-J 的行为变更会带着一个刚搬过家的闸门一起评审,两件事混在一个 diff 里说不清。

**PR-F 必须先于 PR-G 落地。** 两个缺陷在 worker 没有调度器时是潜伏的;一旦 PR-G 把 worker 接上调度,
#133 让每笔成功退款每小时重查 Stripe 一次、连查 31 天(约 744 次 provider 读 + 1488 行审计),#132 让一个
永久性 `PAYMENT_STATE_INVALID` 每 30 秒重试一次、重试 23 小时(约 2760 轮)。接线即事故。

## 3. 各 PR 详细

### 3.1 PR-E:团生命周期解析与事件

**领域(groupbuy)**

- `DealStatus` 增 `canTransitionTo(DealStatus target)`:仅 `OPEN` 可动,可去
  `SUCCEEDED`/`EXPIRED`/`FAILED`/`CANCELED`;终态不可再转,不可转向自身。矩阵写在枚举里,服务层禁止
  `if/else` 判状态 —— 与 `OrderStatus` 同一写法。
- `GroupDeal.expire(Instant now)`:返回 `EXPIRED` 快照。守卫两条 —— `status.canTransitionTo(EXPIRED)`
  否则 `BizException(GroupbuyErrorCode.DEAL_CLOSED)`;`now.isBefore(expireTime)` 否则
  `IllegalStateException`(调度器自身的 bug,不是用户输入)。
- `joinPaid` 内现有的 `succeeded ? SUCCEEDED : OPEN` 改为走 `canTransitionTo`,让矩阵成为唯一事实源。
- 新增两个 record(`com.blocbuy.groupbuy.event`,纯 JDK):
  - `TeamSucceeded(UUID dealId, UUID productId, int size, Instant at)`
  - `TeamExpired(UUID dealId, UUID productId, Instant at)`
- `PaidGroupDealRepository` 增两个方法:
  - `List<UUID> dueForExpiry(Instant now, int limit)` —— 走既有 `idx_biz_group_deal_status_expire`
    (V5 已建),`WHERE deleted=false AND status='open' AND expire_time <= #{now} ORDER BY expire_time LIMIT`
  - `void expire(GroupDeal before, GroupDeal after, Instant now)` —— 状态守卫式 UPDATE
    (`WHERE id=? AND status='open' AND deleted=false`),0 行视为并发已解析,抛
    `BizException(GroupbuyErrorCode.DEAL_CLOSED)` 由调用方吞掉当作幂等重放

**应用层**

- `PaidGroupDealService`:注入 `ApplicationEventPublisher`,在 `joinConfirmed` 成功写入且
  `after.status() == SUCCEEDED` 时 `publishEvent(new TeamSucceeded(...))`。
- 新建 `GroupDealExpiryService`(groupbuy application):`boolean expireOne(UUID dealId)` —— 一个团一个
  短事务,`lockDeal` → `currentTime` → `deal.expire(now)` → `repository.expire(...)` →
  `publishEvent(new TeamExpired(...))`。扫描循环不在这里(见 PR-G),这里只负责单团解析,便于单测。
- 新建 `OrderLifecycleSubscriber`(order application):
  ```java
  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  @Order(10)
  void onTeamSucceeded(TeamSucceeded event) { orders.confirmByDealId(event.dealId()); }
  ```
  `TeamExpired` 同构,调 `cancelByDealId(dealId, event.at())`。
- `TeamExpiryRefundSubscriber`(payment application):`@Order(20)`,调
  `StripeCheckoutService.recordExpiredTeam(dealId)`。该 bean 注册在既有的
  `StripeCheckoutConfiguration`(`@Profile("stripe-sprint2")`)里 —— 没开支付就没有钱,自然也没有退款义务,
  而订单取消照常发生。

**三个必须讲清楚的时序决定**

1. **为什么是 `BEFORE_COMMIT` 而不是 `AFTER_COMMIT`。**`recordExpiredTeam` 的 Javadoc 原文:
   "Run inside the caller's team-expiry transaction; never AFTER_COMMIT."。退款义务与团的过期事实必须同生共死,
   提交后再补会留下"团已过期但无退款义务"的窗口。`BEFORE_COMMIT` 同步、同事务、同连接,订阅者抛异常即整体回滚,
   正是想要的语义。
2. **为什么不是普通 `@EventListener`。**`TeamSucceeded` 由 `OrderFulfilmentAdapter.fulfil` 内部的
   `admit(...)` 间接触发,而**那一笔订单在 `admit` 之后才 `orders.save(...)`**。普通监听器会在最后一单还是
   pending 时就跑 `confirmByDealId`,导致成团的最后一位买家订单永远停在 pending。`BEFORE_COMMIT` 推迟到该事务
   所有写入完成之后,顺序自然正确。这是本 PR 最容易写错的一处。
3. **两个订阅者的顺序由 `@Order` 固定为 order(10) → payment(20)**,与全局锁序
   团 → intent → 订单 → 支付 一致。
4. `@TransactionalEventListener` 在无事务上下文中静默丢弃事件。两个发布点(`joinConfirmed`、`expireOne`)
   都在 `@Transactional` 内,IT 必须正面断言"团满后订单确实是 confirmed",而不是只断言监听器被调用过。

**测试**

- 单测:`DealStatus` 矩阵全组合;`GroupDeal.expire` 合法/未到期/终态三条;`GroupDealExpiryService`
  三条(正常、并发已解析 0 行、锁不到);两个订阅者各两条。
- IT(`StripeLocalIntegrationTest` 追加):
  - 两人成团 → 两张订单都是 `confirmed`,`biz_group_deal.status='succeeded'`
  - 一人开团后过期 → 订单 `cancelled`、`biz_refund` 一行 `pending`、团 `expired`
  - 同一团重复 `expireOne` → 第二次无副作用(0 新退款、0 状态变化)

### 3.2 PR-F:退款 worker 接线前置修复

**#132 永久错误不该 30 秒重试一次。** `RefundService.processNext` 现在用一个
`catch (BizException)` 吞掉所有 provider 异常并一律记 `processing`/`PROVIDER_RETRY`。改为按
`errorCode()` 分流:

| errorCode | 落库状态 | result |
|---|---|---|
| `PAYMENT_STATE_INVALID`(PaymentIntent 非 succeeded / 币种或金额不符 / live 模式) | `review` | `PROVIDER_STATE_MISMATCH` |
| `PAYMENT_PROVIDER_ERROR`(StripeException) | `processing` | `PROVIDER_RETRY` |

`reconcile` 同样分流(`OPERATOR_PROVIDER_STATE_MISMATCH`)。

**#133 成功的退款不该再被 claim。** 三处一起改,少一处都不生效:

1. `RefundMapper.claimNext` 的 `status IN (...)` 去掉 `'succeeded'`
2. `RefundMapper.finish` 对 `succeeded` 不再 `next_attempt_at = clock_timestamp() + 1 hour`
3. 新迁移 `V16__refund_due_index.sql`:重建 `idx_biz_refund_due` 的部分索引谓词为
   `status IN ('pending','processing')`。**`DROP INDEX` + `CREATE INDEX`,不用 `CONCURRENTLY`**
   —— Flyway 默认事务内执行,`CONCURRENTLY` 会失败;该表在 Sprint 2 数据量可忽略。

**#138 防回归(可选,一行)**:`prepare` 的
`expires.isAfter(quote.quoteValidUntil())` → `quote.expiredAt(expires)`。行为等价(§0.3),但把不变式
写死在 `CheckoutQuote` 一处。

**测试**:按两个 issue 的 AC 写 —— `RefundServiceTest` 两条(状态错配进 review 且不再调 gateway;瞬时错误仍按
同一幂等键重试);`StripeLocalIntegrationTest` 一条(`finish(..., "succeeded", ...)` 之后 `claimNext` 返回 null)。

### 3.3 PR-G:调度基座与两个作业

**租约(`sys_scheduler_lock`,V13 已建,至今零使用)**

- 端口 `com.blocbuy.common.repository.SchedulerLeaseRepository`(domain 模块,纯 JDK):
  ```java
  boolean acquire(String lockKey, String owner, Duration lease);
  void release(String lockKey, String owner);
  ```
- 实现 `com.blocbuy.common.persistence.SchedulerLeaseRepositoryImpl` + `SysSchedulerLockMapper.xml`,
  严格照 V13 的 "Application obligations" 注释:`INSERT ... ON CONFLICT DO NOTHING` 建行 →
  `SELECT ... FOR UPDATE NOWAIT`,拿不到锁说明别的实例在跑,**快速跳过本轮而不是排队**;
  `locked_until` 已过期则接管(同一行锁内改 `owner` 与 `locked_until`)。
- Redis 不参与:决定钱退不退的锁是业务状态,PostgreSQL 是唯一记录系统(V13 注释已写明)。

**团过期扫描**

- `GroupDealExpirySweeper`(groupbuy application):`int sweep(int limit)` —— **先一次批量**
  `dueForExpiry(now, limit)` 取 ID 列表,再逐个调 `GroupDealExpiryService.expireOne(id)`。
- **关于"禁止循环内查询"的显式例外说明**:循环内确实每团一次 `lockDeal` + UPDATE。这不是 N+1 读放大,
  而是按聚合逐个加锁的写作业 —— 合并成一个大事务会(a)让一个团的失败回滚整批,(b)把锁持有时间拉长到
  与 webhook 结算争用,(c)破坏"一个聚合一个事务"的边界。因此保留逐团短事务,并以**批量取 ID + 每团
  try/catch 失败隔离**限制代价;失败只记 WARN 并继续,下一轮自然重试(`status` 仍是 `open`)。
- 驱动放 bootstrap:`SchedulingConfiguration`(`@EnableScheduling`),
  `@Scheduled(fixedDelayString = "${blocbuy.scheduling.team-expiry.interval:PT1M}")`,先 `acquire` 租约,
  跑完 `release`,每轮一行 INFO 日志带解析团数。

**退款 worker**

- 同一个 `SchedulingConfiguration`,`@Profile("stripe-sprint2")` 的那半:
  `@Scheduled(fixedDelayString = "${blocbuy.scheduling.refund-worker.interval:PT30S}")`,租约内
  `while (processed < batch && refunds.processNext()) processed++;`,`batch` 默认 20 —— 有界,不让一轮
  占满租约。
- `RefundService.processNext` 自己管事务(`TransactionTemplate`,5 秒超时),驱动方**不得**包
  `@Transactional`。

**并发与锁序论证(必须进 ADR)**

- 扫描器与 webhook 争同一个团:两边都先锁 `biz_group_deal`,锁序一致,不死锁。扫描器先到则团变
  `expired`,后到的 JOIN 结算读到 `expired` → `BUSINESS_EXPIRED` → 退款义务,买家不会被"收了钱没进团"。
  webhook 先到则团可能变 `succeeded`,扫描器的 `expire` 因状态守卫影响 0 行,当作幂等重放跳过。
- 退款 worker 与 `recordExpiredTeam` 并发:前者只 claim `biz_refund` 行,后者插入 `biz_refund`;
  `biz_refund.payment_id` 的 `UNIQUE`(V9)是重复退款义务的最终防线。
- 租约丢失(实例假死后另一实例接管)仍不会双退:上面那条唯一约束 + 退款行的 `version` 围栏令牌。

**测试**

- 单测:租约获取/接管/拿不到;`sweep` 的失败隔离(三个团第二个抛异常,另两个仍解析,返回 2)。
- IT:两个"实例"并发 `acquire` 同一 key,恰好一个成功;过期团经调度器一轮后订单 `cancelled` + 退款义务
  `pending`;再跑一轮无新副作用。

### 3.4 PR-H:商品团条款管理面(NFR-28)

`AdminListingController.ListingContent` 增两个**可选**字段:

```java
@Min(2) @Max(100) Integer teamTargetSize,
@Min(2400) @Max(86400) Integer teamLifetimeSeconds
```

两者要么同时给、要么同时为空(跨字段校验放 application 层,给 `CatalogErrorCode` 一个新码而不是靠 Bean
Validation 表达);边界值与 `ProductTeamTerms.startable()` 完全一致,避免"管理端存得进、下单端用不了"。

写入不动 `Product` 聚合根 —— 沿用 ADR 0001 已确立的读侧做法(`ProductTeamTerms` 值对象 + 自己的 mapper
语句),在 `ProductRepository` 上加 `saveTeamTerms(UUID productId, ProductTeamTerms terms, UUID actorId)`,
由 `AdminListingAppServiceImpl.create` / `revise` 在**同一事务、同一 `expectedVersion` CAS 之后**调用。
同一行两次 UPDATE 是刻意的:改聚合根签名会波及 14 处 `reconstitute` 调用方,收益为零。

契约:additive,`./mvnw -Popenapi-export` 重生成并提交。NFR-28 转 `implemented`。

### 3.5 PR-I:`GET /orders/{id}`

owner-scoped:买家不是本人一律 404(不是 403 —— 不泄露订单是否存在)。响应沿用
`OrderSummaryResponse` 的字段集合,不加 `paymentId`、不加收件人 PII。`OrderAppService` 增
`Optional<OrderSummaryView> findMine(UUID orderId, UUID buyer)`。契约 additive。

**UC8 的"我的团"半边不在本计划内** —— `GET /api/v1/group-deals/me` 属 groupbuy,issue #31 挂在
@limjiechao 名下,需要先确认接口形状归谁定。`GroupDealController` 现在是空壳,随时可接。

### 3.6 PR-J:未知 payment 的 webhook 记录并确认(#139)

**现状**:`StripeCheckoutService.apply` 第一行就是 `UUID.fromString(session.paymentId())`,`settle` 紧接着
`require(id)` —— 前者对非 UUID 的 provider metadata 抛 `IllegalArgumentException`,后者对本库没有的 id 抛
`IllegalStateException("CheckoutPayment not yet available")`。两者路径上无人捕获,`StripeRequestExceptionHandler`
只映射 `MissingRequestHeaderException`,最终落到 `GlobalExceptionHandler.handleUnknown` 出 **500**。
Stripe 对非 2xx 事件重试最长 3 天,每次重试撞同一条路径,而 `claimEvent` 随事务一起回滚,所以去重也不生效。

**为什么必须在本次迭代修**:一台开发机和共享服务器指向同一个 Stripe sandbox endpoint 就会稳定触发,数据库
重置后同样触发 —— 也就是本迭代做 PR-G 联调时的常态。`StripeWebhookController` 的类注释写的是
"Always answers 2xx quickly for handled/ignored events so the provider does not retry storms";
metadata 里的 `payment_id` 不属于本库,就是一个**被忽略的事件**,应当记录并确认,而不是 500。

**改法**

1. `apply` 里把 `UUID.fromString` 换成安全解析;解析失败时开一个短事务 `claimEvent` +
   `finishEvent(eventId, null, "UNKNOWN_PAYMENT")`,WARN 一行带 session id,直接返回。
2. `settle` 里把 `require(id)` 换成 `findById(id)`;为空时同样记 `UNKNOWN_PAYMENT` 后返回,**不触碰**
   `business`、`lock`、`save`。此处已在事务内且 `claimEvent` 已成功,所以这条路径天然去重。
3. `require(UUID)` 原样保留给 `preview` / `inspect` / `recover` —— 操作员对着一个不存在的 payment 下命令
   是操作错误,必须炸。
4. **`finishEvent` 要能写 null payment_id**。列本身可空(V9 `ALTER TABLE log_payment_webhook_event ADD
   COLUMN payment_id uuid`,没有 NOT NULL),但 `PaymentMapper.finishEvent` 现在写的是裸 `#{paymentId}`,
   传 null 时 MyBatis 推不出 JDBC 类型、对 PostgreSQL `uuid` 列会报错。改成
   `#{paymentId,jdbcType=OTHER}` —— 与本仓库其它 uuid 参数的写法一致。

**测试**(按 issue AC)

- `StripeCheckoutServiceTest`:metadata 的 `payment_id` 不是 UUID、以及是 UUID 但本库没有,两条;各断言
  不抛异常、写了一条 `UNKNOWN_PAYMENT`、`business`/`lock`/`save` 零交互。
- `StripeWebhookBoundaryTest`:MockMvc 对这类事件返回 200 `COMMON_200_000`。

### 3.7 PR-K:sandbox 闸门收口(#130)

**现状**:"只走 Stripe 测试模式"这条不变式在三个文件七处各写一遍 —— `StripeCheckoutGateway.requireSandbox`
(调用于 38/87/105 行)、`StripeRefundGateway.requireSandbox`(逐字节相同的副本,调用于 31/65/75 行)、
以及 `StripeCheckoutService` 里两处内联的 `cs_test_[A-Za-z0-9_-]{1,240}` 正则(`inspect`、`recover`)。
`StripeClientProvider.client()` 的注释写着 "every caller must pass the sandbox gate first",但它自己不强制。
将来开 live 时,漏改一处就是那条路径静默留在 sandbox。

**改法**

1. `StripeProperties` 增 `sandbox()`(已配置且 `apiKey` 以 `sk_test_` 开头);`StripeClientProvider.client()`
   在 `sandbox()` 为假时抛 `BizException(PAYMENT_PROVIDER_ERROR)`;删掉两个 `requireSandbox()`。
2. `cs_test_` 正则收成一个具名判据供 `inspect` / `recover` 共用。
3. `liveMode` 的检查(webhook 事件、取回的 session、取回的 PaymentIntent)**保留** —— 它们校验的是 provider
   回来的数据,不是本地配置;但统一走一个具名谓词,让这条规则在各处读起来一样。

**两条实施前已核实的事实**(决定这个重构是否安全)

- 六个 `requireSandbox()` 调用点**紧接着**就是 `clients.client()`,且 `verifyCheckoutWebhook` 不调
  `requireSandbox`(它用的是 webhook secret,不需要 client)。把闸门移进 `client()` 在这六处行为完全等价,
  也不会给 webhook 验签新加一道闸。
- 两个网关的 `catch` 全是 `catch (StripeException)`,`BizException` 不会被吞 —— 闸门从 `try` 外移到 `try`
  内不改变异常出口。

**顺带关掉一个 TODO(扩大范围,需明说)**:`StripePaymentGateway`(旧 `PaymentGateway` 契约)不经
`StripeClientProvider`,每次调用 `new StripeClient(properties.apiKey())`,注释写着
"TODO: hoist to a singleton bean once credentials are provisioned per env" —— 它**完全没有 sandbox 闸门**。
issue #130 的 grep 型 AC 抓不到它(那里本来就没有 `sk_test_` 字面量)。本 PR 把它改为走
`StripeClientProvider`,一并纳入闸门。风险:未配置或 live key 下它现在会更早抛 `PAYMENT_PROVIDER_ERROR`;
`StripeDevCompatibilityTest` 断言了这条旧链路的装配,须一并跑通。若该测试暴露出不该改的兼容约束,就退回
只做 1–3 步,并在 PR 描述里写明这条路径仍未设防。

**测试**:`StripeCheckoutGatewayTest` / `StripeRefundGatewayTest` 原有的"`sk_live_` 与未配置在任何 Stripe
调用前被拒"的断言必须原样通过(它们正是这次重构的安全网);`StripeCheckoutServiceTest` 仍拒绝 live 模式事件
与非 `cs_test_` 提示。AC 的 grep 复核:

```bash
grep -rn "sk_test_\|cs_test_\[" backend/*/src/main
```

每个字面量至多命中一处生产代码。

## 4. 已否决的做法

| 做法 | 否决理由 |
|---|---|
| 订阅者用 `@TransactionalEventListener(AFTER_COMMIT)` | `recordExpiredTeam` 明确禁止;且会留下"团过期无退款义务"窗口 |
| 订阅者用普通 `@EventListener` | 成团的最后一张订单尚未 insert,`confirmByDealId` 会漏掉它(§3.1 决定 2) |
| groupbuy 直接调 `OrderRepository` / `StripeCheckoutService` | 跨域硬门禁;且 UC5 验收条件明确要求 "consequences handled by subscribers, not inline" |
| 用 Redis 做调度单飞锁 | 决定钱退不退的锁是业务状态,PostgreSQL 唯一记录系统 |
| 扫描器一个大事务批量过期 | 单团失败回滚整批;锁持有时间与 webhook 结算争用 |
| 把 `expireDueSessions` 也接上调度 | #134 未修(评审行挤占 + 头阻塞),接线即劣化;继续留作 `stripe-ops` 手工命令 |
| 为团条款改 `Product.reconstitute` 签名 | 14 处调用方,收益为零(ADR 0001 已有结论) |

## 5. 验收门禁(每个 PR 都要过)

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
cd backend && ./mvnw -B verify
```

- Spotless / Checkstyle(0 violations)/ SpotBugs / ArchUnit 8 条全绿
- JaCoCo **聚合**报告(`blocbuy-bootstrap/target/site/jacoco-aggregate/jacoco.xml`,仅 `verify` 后生成)
  中本 PR 触碰的文件 **0 missed line、0 missed branch**
- PR-H / PR-I:`./mvnw -B verify -Popenapi-export -DskipTests ...` 重生成后
  `git diff --exit-code -- contracts/` 干净
- 每个 PR 一条 `docs/agile/sprint-02/log.md` 记录;PR-E / PR-G 各补 NFR 行,PR-H 把 NFR-28 转
  `implemented`;PR-E + PR-G 的时序与锁序论证写进后端 ADR 0002
- PR-K 额外跑一次 issue #130 的 grep 型 AC,把输出贴进 PR 描述

本迭代结束时应当可关的 issue:#27、#28、#29、#124、#130、#132、#133、#138、#139 —— 九个,全部在
合并进 `dev` 之后关闭。

## 6. 不在本计划范围

| issue | 为什么不在 |
|---|---|
| #136 receipt 四次读同一行 | 效率问题,非正确性;回执响应形状不变,随时可做 |
| #134 `expireDueSessions` 挤占与头阻塞 | 只在把会话过期接上调度时才变实害,本计划不接(见 §4) |
| #128 / #135 / #131 / #137 | 前端 |
| `DevPaymentRepositoryAdapter` 移除(预发清单第 1 条末句) | 它实现的是**旧** `PaymentRepository` 契约,不是本次接通的两个端口;归属待与 Yorick 确认 |
