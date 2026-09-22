# 订单/支付集成端口 — 实施计划(Liu Jin)

日期:2026-09-22。分支:`feature/order-deal-20260919`。规范:`backend-constraints-skill` + `pgsql-ddl`。
前置:`order-stripe-merge-plan-20260921.md` 已执行完毕(合并 `d6de139`、IT 修复 `f3d1f4f`、存储切片 `c84868d`)。

## 0. 现状基线(2026-09-22 实测)

支付切片进 `dev` 后留下一个**没有生产实现的集成契约**。`PaymentBusinessPort` 的类注释原文:

> Integration contract owned jointly with groupbuy/order. No production stub is supplied.

实测确认(`grep` 全量 main 源码):`PaymentBusinessPort` 与 `PaymentIntentPort` 在生产代码里零实现,只有测试夹具 `PaymentBusinessFixture`(`blocbuy-bootstrap/src/test`)。后果:

- `StripeCheckoutConfiguration` 挂 `@Profile("stripe-sprint2")`,注释写明 "startup fails if the team has not supplied the real business collaborator"。默认 profile 下 `StripeCheckoutService` 根本不注册。
- 即 dev 上支付链路与订单/团购**没有接通**,`/api/v1/payments/*` 在默认 profile 下由 `StripeFeatureSwitch` 关闭。

已就绪(`c84868d`):
- `V14__product_team_terms.sql` — `biz_product.team_target_size` / `team_lifetime_seconds`
- `V15__purchase_intent.sql` — `biz_purchase_intent` + `uq_biz_purchase_intent_fulfilled_payment`
- 领域 `PurchaseIntent` / `PurchaseOperation` / `PurchaseIntentRepository`、4 个 `OrderErrorCode`
- 基础设施四件套 + 29 个单测

可直接复用,不重造:
- `PaidGroupDealService.startConfirmed / joinConfirmed` — 已实现"付款后建团/入团",内部用 `reserveAndLock` 的 fingerprint 做幂等
- `PaidGroupDealRepository.lockDeal / hasActiveMember / currentTime / complete`
- `OrderRepository.save / findByPaymentId / confirmByDealId / cancelByDealId`
- `ProductRepository.lockById`、`TeamPriceRepository.findLatestPolicy`

## 1. 边界与分层落点

硬门禁:**跨域只能走对方的应用层接口/查询门面,禁止跨域访问别的模块的 Mapper、DO、RepositoryImpl 或内部聚合**(`architecture-and-ddd.md` L51)。所以:

| 需求 | 错误做法(已否决) | 本计划做法 |
|---|---|---|
| START 要读商品与团条款 | order 侧开 mapper 直读 `biz_product` | catalog 暴露应用层门面 `CatalogPurchaseSupport`,order 调它 |
| 付款成功要扣库存 | order 侧 `UPDATE biz_product` | 同上,门面出 `consumeOneUnit` |
| 建团/入团 | order 侧写 `biz_group_member` | 调既有 `PaidGroupDealService` |

`biz_product` 的两列(V14)归 catalog。**不动 `Product.reconstitute`** —— 它有 14 处调用方,为两列改签名波及面过大、收益为零。改为 catalog 内部新增一个小值对象 `ProductTeamTerms` 与一条自己的 mapper 查询,聚合根不变。

分层落点一览:

```
catalog / application  CatalogPurchaseSupport(接口) + CatalogPurchaseSupportImpl
catalog / domain       ProductTeamTerms(值对象) + ProductRepository 增两个方法
catalog / infra        ProductRepositoryImpl 实现 + BizProductMapper 新增两条语句
order   / application  PurchaseIntentAppService  implements PaymentIntentPort
order   / application  OrderFulfilmentAdapter    implements PaymentBusinessPort
bootstrap / config     PaymentIntegrationConfiguration(装配,替掉夹具)
```

> 2026-09-22 实现时的修正:原计划给团条款单开一个 `ProductTeamTermsRepository`,实际落地改为在既有
> `ProductRepository` 上加 `findTeamTerms` / `consumeOneUnit` 两个方法。团条款属于 Product 聚合,
> "一个聚合根一个仓储"的约束下不该为同一个聚合开第二个仓储。

适配器放 application 而非 infrastructure:它编排用例与事务、调三个域的应用/端口,不碰 SQL;`application_uses_ports_not_tech` 这条 ArchUnit 规则只禁 MyBatis/Redis 直依赖,满足。

## 2. PR 切分

| PR | 内容 | 依赖 | 备注 |
|---|---|---|---|
| PR-A | catalog 门面:`ProductTeamTerms` + 端口 + impl + `CatalogPurchaseSupport` + 单测 | 无 | 只加不改,catalog 现有行为零变化 |
| PR-B | `PurchaseIntentAppService`(`PaymentIntentPort`)+ 单测 | PR-A | START/JOIN 定价与出 intent |
| PR-C | `OrderFulfilmentAdapter`(`PaymentBusinessPort` 六方法)+ 单测 | PR-B | 核心,含锁序与幂等 |
| PR-D | bootstrap 装配 + `StripeLocalIntegrationTest` 迁到真实现 + ADR + NFR 行 | PR-C | 夹具退场 |

建议 PR-A 先合,catalog owner 只需 review 一个只加不改的门面。PR-C 最重,单独 review。

## 3. 实现规格

### 3.1 PR-A — catalog 购买支持门面

`ProductTeamTerms`(domain,`com.blocbuy.catalog.model`):

```java
public record ProductTeamTerms(UUID productId, Integer targetSize, Integer lifetimeSeconds) {
    public boolean startable() { ... }   // 两列非空且 targetSize>=2 且 lifetime 在 [2400,86400]
}
```

范围校验放领域,不放 CHECK 约束(pgsql-ddl:约束由应用管)。`V14` 的注释已写明同一组边界,两处必须一致。

`CatalogPurchaseSupport`(application,catalog 对外唯一入口):

```java
public interface CatalogPurchaseSupport {
    /** 商品行锁 + 最新价格策略 + 团条款;START 定价用。调用方持有事务。 */
    StartQuote lockStartQuote(UUID productId, Instant now);

    /** 只读校验:上架、有货、币种;JOIN 用团自己的价,不需要策略。 */
    PurchasableProduct requirePurchasable(UUID productId);

    /** 付款成功后扣一件并累加销量;条件更新,库存不足返回 false。 */
    boolean consumeOneUnit(UUID productId);
}
```

`StartQuote` 至少带:`productId / title / coverImage / currency / amount(策略价) / targetSize / lifetimeSeconds`。

定价校验(照搬夹具已验证过的规则,写进 catalog 门面):
- 策略未软删、`now >= valid_from`、`now + 2400s < valid_until`(报价有效期必须完整落在策略有效期内)
- `currency = 'sgd'`、`amount > 0`、`amount < product.originalPrice`
- 商品 `status = on_sale`、`stock >= 1`、`currency = 'sgd'`
- 团条款 `startable()`

任一不满足抛 `BizException`,用 catalog 自己的 `ErrorCode`(缺则新增,禁止裸 `RuntimeException`)。

`consumeOneUnit` 用条件更新,禁止"先读后写":

```sql
UPDATE biz_product SET stock = stock - 1, sold_count = sold_count + 1,
       version = version + 1, update_time = clock_timestamp()
WHERE id = #{productId} AND deleted = false AND stock > 0
```

### 3.2 PR-B — `PurchaseIntentAppService implements PaymentIntentPort`

```java
UUID createIntent(UUID user, String operation, UUID businessId)
```

契约(来自端口注释):只收 ID,价格与团条款全部服务端取;运行在调用方的短事务内;不做远程 I/O。

- `START`:`businessId` 是 productId。`lockStartQuote` 拿价与条款 → `dealId = UuidV7.next()`(**预留**,团还不存在,但后续锁序需要一个目标)→ `quoteValidUntil = now + 2400s`、`businessDeadline = now + lifetimeSeconds`
- `JOIN`:`businessId` 是 dealId。`lockDeal` 拿团 → 校验 `status = OPEN`、`now < expireTime`、`currentSize < targetSize`、该用户不是已有成员(`hasActiveMember`) → `requirePurchasable(deal.productId())` → `amount = deal.dealPrice()`、`targetSize = deal.targetSize()`、`businessDeadline = deal.expireTime()`、`quoteValidUntil = min(now + 2400s, deal.expireTime())`
- 其他 operation:`BizException(PURCHASE_INTENT_TERMS_INVALID)`

写 `PurchaseIntentRepository.save`,返回 intent id。

> 注意 `CheckoutQuote` 的不变式:`quoteValidUntil`/`businessDeadline` 都会被截断到微秒,`currency` 只接受 `sgd`,`amount` 必须 scale=2。`PurchaseIntent` 已经用同一组规则,两边不会打架。

### 3.3 PR-C — `OrderFulfilmentAdapter implements PaymentBusinessPort`

**锁序(全局唯一,不可变更)**:`biz_group_deal` → `biz_purchase_intent` → `biz_payment`。
任何其它顺序会与并发 JOIN 死锁。`PurchaseIntentRepository` 的 Javadoc 已写死这条,适配器每个方法都按它来。

| 方法 | 实现要点 |
|---|---|
| `lockQuote(user, intentId)` | 先 `lockDeal(intent.dealId())`(需先无锁读一次 intent 拿 dealId,再加锁重读)→ `lockById(intentId)` → `requireOwner(user)` → 未 fulfilled → JOIN 还要再验一次团仍可加入 → 转 `CheckoutQuote` |
| `lockForSettlement(quote)` | 同样锁序重读,把重读结果转成 `CheckoutQuote` 与入参 `equals` 比较;不等则 `IllegalStateException`(端口语义:技术故障必须抛,不是业务拒绝) |
| `fulfil(event)` | 见下 |
| `fulfilmentEvidence(event)` | 按 paymentId 查 `OrderRepository.findByPaymentId` 与团成员;都无 → `NONE`;都有且 user/deal/金额/币种/状态一致 → `COMPLETE`;其余 → `INCONSISTENT` |
| `lockExpiredTeam(dealId)` | `lockDeal` → `status != EXPIRED` 抛 `IllegalStateException` |

`fulfil(PaymentSucceeded)` 的顺序(每一步都必须幂等):

1. 锁 intent(调用方已持有支付锁之前的团锁)
2. 已 fulfilled:同一 payment → 查回既有 order,返回 `Outcome(dealId, orderId, null)`;不同 payment → `Outcome(null, null, "INTENT_ALREADY_FULFILLED")`
3. 买家非 active → `Outcome(null, null, "BUYER_INACTIVE")`
4. `requirePurchasable` 失败 → `Outcome(null, null, "PRODUCT_UNAVAILABLE")`
5. `START` → `PaidGroupDealService.startConfirmed(NewDealTerms(...), paid)`;`JOIN` → `joinConfirmed(dealId, paid)`。结果非 `applied` → `Outcome(dealId, null, "GROUP_REJECTED")`
6. `OrderRepository.save(Order.create(terms, event.confirmedAt()))`
7. 回填 `biz_group_member.order_id`
8. `PurchaseIntentRepository.markFulfilled(intent, paymentId)`
9. `consumeOneUnit` 失败 → 抛异常回滚整笔(库存是资金安全级别,不做业务拒绝)

`Outcome` 的 rejection 码必须匹配 `[A-Z_]{1,64}`(record 构造器已强制),且不得携带敏感信息。

**订单初始状态 = `PENDING`**,不是夹具用的 `'paid'`。`OrderStatus` 只有 `pending/confirmed/cancelled`,`pending → confirmed` 由 TeamSucceeded、`pending → cancelled` 由 TeamExpired 驱动。夹具那边的 `status='paid'` 插入与 `fulfilmentEvidence` 里的 `o.status='paid'` 检查要一起改成 `pending`,属 PR-D,改的是 Yorik 的文件,PR 里 @ 他。

### 3.4 PR-D — 装配与夹具退场

新增 `PaymentIntegrationConfiguration`(bootstrap,**不带 profile**),把两个适配器注册为 `PaymentBusinessPort` / `PaymentIntentPort` bean。`StripeCheckoutConfiguration` 的 `@Profile("stripe-sprint2")` 是否可以摘掉由 Yorik 定 —— 摘掉意味着支付在默认 profile 下也装配,`StripeFeatureSwitch` 仍然是运行时开关,不影响默认关闭的行为。

`StripeLocalIntegrationTest` 改为跑真实现;`PaymentBusinessFixture` 只保留 `stripe_local_*` 那部分本地替身,或整体删除 —— 取决于 Yorik 是否还需要脱离 order 模块单跑支付。

## 4. 并发与幂等设计(硬门禁要求逐条说明)

| 维度 | 设计 |
|---|---|
| 数据库约束 | `uq_biz_purchase_intent_fulfilled_payment`(一个 payment 只消费一个 intent)、`uq_biz_order_payment_id`(一个支付一张单)、`uq_biz_order_deal_user`(一人一团一单)、`biz_group_member` 既有唯一键 |
| 锁/版本 | 行锁固定序 group → intent → payment;`biz_purchase_intent.markFulfilled` 在 `version` + `fulfilled_payment_id IS NULL` 双守卫下做 CAS;库存走条件更新 `stock > 0`;订单状态迁移走 `version` CAS |
| 幂等键 | provider event id(`log_payment_webhook_event`,支付侧已有)→ `paymentId`(本侧全部幂等入口)→ `PaidGroupDealService` 的 fingerprint |
| 失败恢复 | 全流程在调用方一个本地事务内,任一步抛异常整体回滚,不留半成品;支付侧 `fulfilmentEvidence` 提供对账入口,`INCONSISTENT` 由人工/恢复端点处理 |

Redis 不参与:订单、团进度、支付都是强一致事实,PostgreSQL 是唯一真相源。无消息中间件,跨聚合一致性靠同一本地事务内的进程内调用。

## 5. 契约影响

**无。** 本次不新增、不修改任何 controller、端点、DTO 或 springdoc 注解 —— 补的是内部集成端口。`contracts/openapi.json` 应当零 diff,提交前用下面这条确认:

```bash
git diff --exit-code -- contracts/
```

若出现 diff,说明误改了 HTTP 面,按 skill 第 6 条重新导出并说明原因。

## 6. 测试与覆盖率自查

- 领域:`ProductTeamTerms` 边界值(2400/86400 两端、null、targetSize=1)
- 应用:两个适配器全部用 Mockito 打桩三个协作方,**每条失败分支都要有用例** —— `Outcome` 的四个 rejection 码、`lockForSettlement` 的不等分支、`fulfilmentEvidence` 的三种结果
- 集成:`StripeLocalIntegrationTest` 走真实现;新增一条并发用例证明重放 webhook 只产生一份成员 + 一张单
- 覆盖率:每个 PR 提交前跑 skill 的 JaCoCo 聚合自查脚本,touched 文件目标 100%,不是 80% 底线:

```bash
cd backend && ./mvnw -B verify
python3 - <<'EOF'
import xml.etree.ElementTree as ET
t = ET.parse('blocbuy-bootstrap/target/site/jacoco-aggregate/jacoco.xml')
for sf in t.iter('sourcefile'):
    missed = [l.get('nr') for l in sf.findall('line')
              if int(l.get('mi')) > 0 or int(l.get('mb')) > 0]
    if missed:
        print(sf.get('name'), '-> missed lines:', ','.join(missed))
EOF
```

JDK 必须是 21(`export JAVA_HOME=$(/usr/libexec/java_home -v 21)`),默认的 26 会被 enforcer 的 `[21,22)` 拒掉。

## 7. 风险

| 风险 | 概率 | 影响 | 处置 |
|---|---|---|---|
| START 预留的 `dealId` 在建团前就写进 intent,若最终未成团则是悬空引用 | 高(设计如此) | 无实际损害:`deal_id` 无 FK,未 fulfilled 的 intent 可按 deadline 清理 | V15 注释已写明;清理作业列 Sprint 3 |
| 锁序写错导致与并发 JOIN 死锁 | 中 | 生产偶发超时 | 锁序写进端口 Javadoc 与本文档;PR-C 必须有并发 IT |
| `biz_product` 两列对现存商品全为 null,START 全部拒绝 | 高 | 开团路径在数据补齐前不可用 | 预期行为,V14 注释写明;管理后台补值不在本次范围,PR-D 的 NFR 行要记这个未满足项 |
| 夹具 `status='paid'` 与 `OrderStatus` 不一致 | 已确认存在 | 真适配器走枚举会 `fromCode` 抛异常 | PR-D 一并改,改的是支付侧文件,需 Yorik 确认 |
| catalog owner 不接受新门面 | 中 | PR-A 卡住 | 门面只加不改,且是硬门禁要求的唯一合规路径;若被拒,替代方案是 catalog 侧自己实现 `PaymentIntentPort` 的定价部分,届时重议 |

## 8. 证据义务

每个 PR 内同步:`sprint-02/log.md` 一条;PR-D 追加 NFR 行(资金安全/幂等:一次付款至多一份成员与一张订单,重放不产生第二份)与一份 ADR(购买意图为何落在 order 模块、团条款为何落在 `biz_product`)。`docs/db/catalog.sql` 的两列同步由 PR-A 带上。
