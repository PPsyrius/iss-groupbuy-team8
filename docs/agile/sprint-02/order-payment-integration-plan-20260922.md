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
- `PaidGroupDealRepository.lockDeal / hasActiveMember / currentTime / complete` —— **仅限 groupbuy 模块内部
  复用**;order 只能经 §3.2 修正 1 的 `GroupDealPurchaseSupport` 间接使用
- `OrderRepository.save / findByPaymentId / confirmByDealId / cancelByDealId`
- `ProductRepository.lockById`、`TeamPriceRepository.findLatestPolicy`

## 1. 边界与分层落点

硬门禁:**跨域只能走对方的应用层接口/查询门面,禁止跨域访问别的模块的 Mapper、DO、RepositoryImpl 或内部聚合**(`architecture-and-ddd.md` L51)。所以:

| 需求 | 错误做法(已否决) | 本计划做法 |
|---|---|---|
| START 要读商品与团条款 | order 侧开 mapper 直读 `biz_product` | catalog 暴露应用层门面 `CatalogPurchaseSupport`,order 调它 |
| 付款成功要扣库存 | order 侧 `UPDATE biz_product` | 同上,门面出 `consumeOneUnit` |
| JOIN 要读团的价与名额 | order 侧调 `PaidGroupDealRepository.lockDeal` 拿 `GroupDeal` | groupbuy 暴露应用层门面 `GroupDealPurchaseSupport`,order 只收值对象 |
| 建团/入团 | order 侧写 `biz_group_member` | 调既有 `PaidGroupDealService` |

`biz_product` 的两列(V14)归 catalog。**不动 `Product.reconstitute`** —— 它有 14 处调用方,为两列改签名波及面过大、收益为零。改为 catalog 内部新增一个小值对象 `ProductTeamTerms` 与一条自己的 mapper 查询,聚合根不变。

分层落点一览:

```
catalog  / application  CatalogPurchaseSupport(接口) + CatalogPurchaseSupportImpl
catalog  / domain       ProductTeamTerms(值对象) + ProductRepository 增两个方法
catalog  / infra        ProductRepositoryImpl 实现 + BizProductMapper 新增两条语句
groupbuy / application  GroupDealPurchaseSupport(未付) + GroupDealSettlementSupport(已付) + 两个 Impl
groupbuy / domain       PaidMember(值对象) + PaidGroupDealRepository 增 linkOrder / findPaidMember
order    / domain       PurchaseIntentRepository 增 currentTime
order    / application  PurchaseIntentAppService  implements PaymentIntentPort
order    / application  OrderFulfilmentAdapter    implements PaymentBusinessPort
bootstrap / config      PaymentIntegrationConfiguration(装配,替掉夹具)
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

四个 PR 于 2026-09-22 全部完成:`d24f8c6`(A)、`0dc6fe1`(B)、`ace9c24`(C)、PR-D 本次。
每个 PR 的实现偏离都记在对应的 §3.x 小节里。

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

> 2026-09-22 实现时的修正(PR-B 落地,3 条):
>
> 1. **JOIN 不直接调 `PaidGroupDealRepository`。** §0 原本把 `lockDeal / hasActiveMember / currentTime`
>    列为"可直接复用",但 `lockDeal` 返回的是 groupbuy 的聚合根 `GroupDeal`,order 拿到它就违反了
>    同一条硬门禁 —— 与 PR-A 否决"order 直读 `biz_product`"是同一个理由。改为 groupbuy 侧新增应用层门面
>    `GroupDealPurchaseSupport.lockJoinTerms(dealId, buyerId)`,在团行锁内做完"OPEN / 未过期 / 未满 /
>    非团长且非已有成员"四项校验,只把值对象 `JoinTerms` 交出来。校验规则刻意与 `GroupDeal.joinPaid`
>    重复:付款前拒绝零成本,付款后拒绝要退款。
> 2. **时钟来自 order 自己的端口。** `PurchaseIntentRepository` 增 `currentTime()`(`clock_timestamp()`),
>    与 `PaidGroupDealRepository` / `TeamPriceRepository` 同一套写法。不用应用服务器时钟:intent 冻结的
>    两个 deadline 将来都要和库里的 `timestamptz` 比大小。
> 3. **`PurchaseOperation.parse` 替代 `fromCode` 处理请求输入。** `operation` 来自 HTTP 请求体,未知值是
>    用户输入错误而不是编程错误,`fromCode` 的 `IllegalArgumentException` 语义不对。新增返回
>    `Optional` 的 `parse`,调用方转成自己的 `BizException`。`ORDER_409_004`(`TEAM_NOT_JOINABLE`)因此
>    永久空缺 —— 可加入性是 groupbuy 的规则,由它自己的错误码表达,号段退休不复用。

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

> 2026-09-22 实现时的修正(PR-C 落地,5 条):
>
> 1. **groupbuy 再出一个门面 `GroupDealSettlementSupport`。** 与 PR-B 同一条硬门禁:回填
>    `biz_group_member.order_id`、按 paymentId 读成员、锁团行、校验团已过期,四件事全部落在 groupbuy 的表上,
>    order 不能自己写。已付一侧与未付一侧分开两个门面:`GroupDealPurchaseSupport` 判可加入并拒绝,
>    `GroupDealSettlementSupport` **只锁不判** —— 结算时团可能已满/已成/已过期,那是
>    `PaidGroupDealService` 拿到钱之后才有资格裁决的事。领域侧 `PaidGroupDealRepository` 相应新增
>    `linkOrder` / `findPaidMember`,后者返回新值对象 `PaidMember`。
> 2. **拒绝必须先于第一次写。** 原 9 步顺序里,`consumeOneUnit`(第 9 步)失败要抛异常 —— 这条保留,
>    但要补一条更硬的规则:`Outcome` 带 rejection 时调用方是**提交**事务再去退款的,所以任何写之后返回
>    rejection 都会把那次写和退款一起提交。因此第 2/3/4 步(已 fulfilled、买家、商品)全部是只读检查,
>    第 5 步之后只许抛不许拒。`linkOrder` 返回 false 同理:抛。
> 3. **START 没有团行锁可加。** `lockTeam` 对预留 dealId 调 `lockDeal` 返回空即结束。不补夹具那套
>    `pg_advisory_xact_lock`:预留 id 对一个 intent 唯一,能碰到它的并发写者都会先卡在 intent 行锁上,
>    外加 `PaidGroupDealService` 自己按 paymentId 锁回执,咨询锁没有增加任何保护。
> 4. **`fulfilmentEvidence` 不检查订单状态。** 夹具查的是 `o.status='paid'`;真实现下订单建出来是 `pending`,
>    团成后变 `confirmed`、团过期变 `cancelled`,这些生命周期迁移都不会取消"履约发生过"这个事实。
>    改为核对成员 ↔ 订单 ↔ intent 三方的 id、金额、币种与 `member.status='paid'`。
> 5. **单号自己生成。** 仓库里没有订单号/团号生成器。采用 `前缀 + UTC 日期 + UUID 低 12 位十六进制`,
>    从 paymentId / dealId 推导而非取序列 —— 重放同一笔付款不会造出第二个号。
>
> **锁序补充说明(评审要看)**:本适配器按 团 → intent 加锁,而 `PaidGroupDealService` 内部是
> 回执 → 团。合起来是 团 → intent → 回执 → 团(可重入),理论上成环。实际不会死锁:回执按 paymentId 唯一,
> 两个并发事务不可能争同一行回执。PR-D 的并发 IT 要覆盖这条。

### 3.4 PR-D — 装配与夹具退场

新增 `PaymentIntegrationConfiguration`(bootstrap,**不带 profile**),把两个适配器注册为 `PaymentBusinessPort` / `PaymentIntentPort` bean。`StripeCheckoutConfiguration` 的 `@Profile("stripe-sprint2")` 是否可以摘掉由 Yorik 定 —— 摘掉意味着支付在默认 profile 下也装配,`StripeFeatureSwitch` 仍然是运行时开关,不影响默认关闭的行为。

`StripeLocalIntegrationTest` 改为跑真实现;`PaymentBusinessFixture` 只保留 `stripe_local_*` 那部分本地替身,或整体删除 —— 取决于 Yorik 是否还需要脱离 order 模块单跑支付。

> 2026-09-22 实现时的修正(PR-D 落地,计划到此执行完毕):
>
> 1. **`StripeCheckoutConfiguration` 的 `@Profile` 保留。** 原计划说"摘不摘由 Yorik 定"。实测否决:
>    `blocbuy.payment.checkout.success-url` / `cancel-url` 在**任何** `application*.yml` 里都没有,
>    只由 IT 的 `@SpringBootTest(properties=…)` 注入。摘掉 profile 会让默认 profile 下的每个上下文
>    启动即失败。而且这个 profile 名就是 `StripeFeatureSwitch` 读的那个,它本来就是灰度开关。
>    只改注释:从"没有业务协作方就启动失败"改成"这是灰度开关"。
> 2. **`PaymentBusinessFixture` 整体删除**,不保留任何一半。它的 `createIntent` / 六个端口方法全部由真
>    实现接管;`lockGroup` 换成 `GroupDealSettlementSupport.lockTeam`;`PaymentLifecycleFixture` 只
>    剩下生产代码里没有的两件事 —— 把团强行推过截止时间、驱动本地会话清理。
> 3. **夹具表删两张。** `stripe_local_intent` 由 `biz_purchase_intent`(V15)接管,
>    `stripe_local_product_terms` 由 `biz_product` 两列(V14)接管。只留 `stripe_local_expiry_request` ——
>    它替代的是本切片不发布的调度器,没有生产对应物。
> 4. **`status='paid'` 那处不一致自动消失。** 它只存在于夹具的 `INSERT INTO biz_order(... 'paid' ...)`
>    与 `fulfilmentEvidence` 的 `o.status='paid'` 检查里,两者随夹具一起删除。真实现建单即 `pending`,
>    `fulfilmentEvidence` 不看订单状态。不需要任何人确认。
> 5. **并发用例落在 `StripeLocalIntegrationTest`。** 新增
>    `eightConcurrentWebhookReplaysStillProduceOneMemberAndOneOrder`:8 线程同时重放同一笔付款,
>    断言 1 个团、1 名成员、1 张单、0 笔退款、库存恰好减 1、intent 只被消费一次。

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
