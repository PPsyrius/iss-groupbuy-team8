# UC10 Order 分支对接 Stripe 分支 — 冲突处理与改造计划(Liu Jin)

日期:2026-09-21。分支:`feature/order-deal-20260919`。对方:`stripe`(PR #126,尚未合入 `dev`)。

**总原则(本次确定)**:重合部分一律以 `stripe` 分支的实现为准。Order 侧只做改号、删除、对接,不反向要求 Stripe 侧让步。理由:Stripe 侧的 V9–V11 已在 PR #126 走完 order 侧 reviewer 的评审并整合进 `db/migration`,`V11__refund_execution.sql` 依赖 `V9__stripe_checkout.sql` 建的 `biz_refund`,让它重排的代价远高于本分支。

## 0. 现状基线(2026-09-21 实测)

- 本分支已同步 `dev`(`92e5ca9`),合并提交 `2a72b23`。唯一冲突是 `docs/agile/sprint-02/log.md`(双方都在同一段追加条目),按日期保留两侧。`dev` 自本分支 merge-base 以来**没有任何后端源码改动**(仅 `backend/README.md`),前端改动本分支一个都没碰,所以除 log 外全部自动合并。
- `stripe` 分支本地已合入 `dev`(`450e9df`)并修掉前端遗留(`6a90d05`),等待推送更新 PR #126。
- 两分支的 API path 完全不相交:本分支只加 `GET /api/v1/orders/me`,Stripe 只加 `/api/v1/payments/*`。**没有接口级冲突**。

`git merge-tree stripe feature/order-deal-20260919` 只报两个文本冲突:`contracts/openapi.json`、`docs/agile/sprint-02/log.md`。**真正的风险 git 报不出来**,全部在 Flyway 和测试夹具里,见 §1.2。

## 1. 冲突清单

### 1.1 文本冲突(git 可见,2 处)

| 文件 | 性质 | 处理 |
|---|---|---|
| `contracts/openapi.json` | 双方各自 regenerate 出的 JSON 文本打架,内容不相交 | 不手工合,合完代码重新导出,见 §6 |
| `docs/agile/sprint-02/log.md` | 双方在同一段追加条目,append-only | 两侧条目都留,按日期排序 |

### 1.2 语义冲突(git 不可见,必须人工处理)

**(1) Flyway 版本号三连撞 —— 最高优先级**

两边都占用了 V9/V10/V11,文件名不同但版本号相同。Flyway 启动即失败:

```
Found more than one migration with version 9
```

| 版本 | 本分支 | Stripe 分支 |
|---|---|---|
| V9 | `V9__order.sql` — `biz_order` + 5 索引 | `V9__stripe_checkout.sql` — `biz_payment` 加 13 列/3 唯一索引、建 `biz_refund`、建 `log_payment_review`、`log_payment_webhook_event` 加列 |
| V10 | `V10__payment_refund.sql` — `biz_refund` + `ALTER biz_payment order_id DROP NOT NULL` | `V10__payment_review_case.sql` — `biz_payment_review_case` |
| V11 | `V11__scheduler_lock.sql` — `sys_scheduler_lock` | `V11__refund_execution.sql` — `biz_refund` 加 10 个执行列、建 `log_refund_attempt` |

**(2) `biz_refund` 被建两次,且两份 schema 不兼容**

- Stripe 独有列:`intent_id`(NOT NULL)、`reason`、`source_event`、`provider_refund_id`、`first_attempt_at`、`next_attempt_at`、`lease_until`、`last_result`
- 本分支独有列:`order_id`、`user_id`
- `deal_id`:Stripe 可空,本分支 NOT NULL

本分支**没有任何 Java 代码引用 `biz_refund`**(已 grep 确认,无 repository / mapper / service),该表纯属 Sprint 3 预埋。且按 `uc10-impl-plan-20260919.md` §1 的交接备忘,`V10__payment_refund.sql` 原本就该归 Yorik(UC6),是误落本分支(`16ab0d2`)。因此整块删除不损失任何在用功能。

**(3) `ALTER TABLE biz_payment ALTER COLUMN order_id DROP NOT NULL` 重复**

两边各写了一次。PostgreSQL 对已经可空的列再 DROP NOT NULL 不报错,所以不会炸,但纯属冗余,且会让"谁放松了这个约束"在迁移史上出现两个答案。Stripe `V9__stripe_checkout.sql` 保留,本分支删除。

**(4) Stripe 测试夹具自建了 `biz_order`,会与本分支的真表相撞**

`StripeLocalIntegrationTest` 显式设置:

```java
"spring.flyway.locations=classpath:db/migration,classpath:db/stripe-fixture"
```

而 `backend/blocbuy-bootstrap/src/test/resources/db/stripe-fixture/V2026092003__local_business_adapter.sql` 里有 `CREATE TABLE biz_order (...)`。Stripe 分支上 `biz_order` 不存在于任何正式迁移,所以夹具自己造了一张;本分支的 `V12__order.sql` 落地后,同一个库里先由 `db/migration` 建出真表,再由夹具建一次 → `relation "biz_order" already exists`,`StripeLocalIntegrationTest` 直接挂。

好消息是列可兼容:夹具版是真表的子集(缺 `product_image`、`receiver_name`、`receiver_phone`、`shipping_address`、`cancel_time`、`finish_time`、`create_by`、`update_by`,这些在真表里全部可空),夹具的 `INSERT INTO biz_order(...)` 用到的列真表全有。所以删掉夹具的建表语句即可,INSERT 和 SELECT 不用改。

Stripe 侧文档 `backend/docs/payment/stripe-local-integration-20260920.md` 已经预告了这一步:"团队完成订单模块后,需替换这个适配并共同核对字段、事务和事件接口。"

**(5) `backend/docs/db/payment.sql` 的改动整块作废**

本分支改了两处,在 Stripe 为准的前提下两处都错:
- 新增的 `biz_refund` 段落写的是本分支版 schema,与 Stripe 落地的不是同一张表
- `biz_payment.order_id` 注释写 "(V10 relaxed NOT NULL)",在 Stripe 下是 V9 干的

payment 域文档归 Stripe。本分支回退该文件。

### 1.3 明确不冲突(勿误删)

- 全部 `com/blocbuy/order/**` Java:`OrderController`、`OrderAppService(Impl)`、`OrderSummaryView`、`Order`、`OrderStatus`、`OrderErrorCode`、`BizOrderDO`/`BizOrderMapper`/`OrderConverter`/`OrderRepositoryImpl`、`mapper/order/BizOrderMapper.xml` 及全部单测/IT。Stripe 分支上 order 包一个类都没有——它自己那套 `OrderHistory*`(controller/service/repository/mapper)在 `0b1f14d` 已全部自删,净变更为零。
- `V9__order.sql` 的 `biz_order` 表体与 5 个索引(只改号,内容不动)。
- `V11__scheduler_lock.sql` 的 `sys_scheduler_lock`(Stripe 无对应物;注意本分支同样没有代码在用,是 Sprint 3 预埋,保留但不算已验证)。
- `backend/docs/db/order.sql`、`docs/agile/sprint-02/uc10-impl-plan-20260919.md`、`docs/SWE5006-Team8-Project-Sprint2-Plan.docx`。
- MyBatis mapper 资源路径:本分支 `mapper/order/`,Stripe `mapper/payment/`,不撞。
- Maven POM:Stripe 改了 `blocbuy-application/pom.xml` 和 `blocbuy-bootstrap/pom.xml`(Stripe SDK 依赖),本分支不碰 POM。

## 2. 合并顺序(前提条件)

**必须 `stripe` 先进 `dev`,本分支再从 `dev` 拉。** 不接受反向顺序。

- 顺序正确时:本分支的全部改造退化为一次普通的 `merge dev` + 本文档 §3–§6 的本地修改,不需要在两个 feature 分支之间对冲突。
- 顺序颠倒时:Stripe 侧要重排 V9–V11,而它的 V11 依赖 V9 建的 `biz_refund`,代价高得多,且 PR #126 已评审通过的迁移要重开评审。

阻塞条件:PR #126 合入 `dev`。在那之前本分支**不要**开始 §3 的改号——否则 `dev` 上的最终版本号一旦与预期不同(例如别的分支插队占了 V12/V13),要返工。

## 3. 步骤 A — Flyway 迁移改号

Stripe 占死 V9/V10/V11,本分支顺延 V12/V13。

```bash
cd backend/blocbuy-bootstrap/src/main/resources/db/migration
git mv V9__order.sql           V12__order.sql
git mv V11__scheduler_lock.sql V13__scheduler_lock.sql
git rm V10__payment_refund.sql
```

- `V12__order.sql`:文件内容一字不改,只改文件名。
- `V13__scheduler_lock.sql`:同上。
- `V10__payment_refund.sql`:整个文件删除,理由见 §1.2(2)(3)。

改号前先确认 `dev` 上的最高版本号确实是 V11:

```bash
ls backend/blocbuy-bootstrap/src/main/resources/db/migration/ | sort -V | tail -5
```

若已有别的分支占了 V12/V13,顺延到下一对空号,并同步更新本文档与 `uc10-impl-plan-20260919.md` 里的版本号引用。

## 4. 步骤 B — 丢弃 payment 侧改动

```bash
git checkout origin/dev -- backend/docs/db/payment.sql
```

回退后确认该文件不含 `biz_refund` 段落,且 `biz_payment.order_id` 的注释不提 V10。

> 注:`dev` 上的 `payment.sql` 对 Stripe 的 V9 新列(`intent_id`、`operation`、`business_id`、`request_key`、`checkout_session_id` 等 13 列)也是过期的,但那是 Stripe 侧的文档欠账,不由本分支补。合并后如需要,在 PR 里提一句请 Yorik 更新。

## 5. 步骤 C — 对接 Stripe 测试夹具

改 `backend/blocbuy-bootstrap/src/test/resources/db/stripe-fixture/V2026092003__local_business_adapter.sql`:删除其中的 `CREATE TABLE biz_order (...)` 整段,替换为一行注释,说明该表自 `V12__order.sql` 起由正式迁移提供。

同文件里的 `stripe_local_intent`、`stripe_local_product_terms`、`stripe_local_expiry_request` 三张表保持不动——那是 Stripe 的本地测试替身,与 order 无关。

`PaymentBusinessFixture.java` 里的 `INSERT INTO biz_order(...)` 与三处 SELECT 不需要改(列在真表里全部存在),但**必须实跑验证**,因为夹具插入时不带 `product_image` / `receiver_*` / `create_by` / `update_by`,依赖真表这些列可空。

跨模块确认项(需与 Yorik 对齐,不要单方面改):
- 夹具 INSERT 写死 `status='paid'`,与 `OrderStatus` 枚举的小写约定一致,确认无误后即可。
- 夹具的 `biz_order.payment_id NOT NULL UNIQUE` 与本分支的 `uq_biz_order_payment_id`(partial unique,`WHERE deleted = false AND payment_id IS NOT NULL`)语义一致。
- 夹具 JOIN 了 `biz_group_member.order_id`;该列 V5 已存在且恒为 null,由 order 侧回填,不在本次改造范围。

## 6. 步骤 D — 重新生成契约

**不要手工解 `contracts/openapi.json` 的冲突。** 两边 path 不相交,重新导出即可得到并集:

```bash
cd backend && ./mvnw -B verify -Popenapi-export -DskipITs
```

导出后确认 `contracts/openapi.json` 同时含有:
- `/api/v1/orders/me`(本分支)
- `/api/v1/payments/capabilities`、`/api/v1/payments/intents`、`/api/v1/payments/checkout-sessions`、`/api/v1/payments/{id}`(Stripe)

并确认相对 `dev` 是**纯新增**(无既有 path/schema 被改),否则 `contract-check` 会要求 `contract-change-approved` 标签。

前端侧:`contracts/openapi.json` 变了之后,`frontend` 的生成客户端需要重跑,否则本地 typecheck 会用旧产物报错(`stripe` 分支上已经踩过一次):

```bash
cd frontend && pnpm gen:api && pnpm check
```

## 7. 验证清单(改造完成后逐条跑,缺一不可)

1. **迁移版本唯一**:`ls db/migration | sort -V` 无重复版本号;V1–V13 连续。
2. **后端全量**:`cd backend && ./mvnw -B clean verify`(JDK 21;默认 JDK 26 会被 enforcer 的 `[21,22)` 拒绝)。必须 `clean`——Stripe 侧踩过 target 里残留旧 mapper XML 的坑。
3. **Flyway 真迁移**:上一步里 `StripeCheckoutFlowTest`、`StripeLocalIntegrationTest`、`OrderRepositoryIT` 三个 Testcontainers 测试必须绿——它们分别覆盖 Stripe 迁移、Stripe 夹具 + 真 `biz_order`、order 持久化切片。§5 的改动风险全部落在第二个上。
4. **契约**:`contracts/openapi.json` 已 regenerate、已 staged、无 unstaged drift;`openapi-diff` 相对 `dev` 只有新增。
5. **前端**:`cd frontend && pnpm gen:api && pnpm check && pnpm build`,两个命令都 exit 0。
6. **质量门禁**:SpotBugs 0、Checkstyle 0、JaCoCo 新代码 ≥ 80%(`docs/quality/quality-gates.md`)。

## 8. 风险与回滚

| 风险 | 概率 | 影响 | 处置 |
|---|---|---|---|
| 本地开发库跑过旧版本号(V9/V10/V11 的 order 版) | 高 | Flyway 校验和不匹配,应用起不来 | 手工清 `flyway_schema_history` 对应行,或直接重建本地库。CI 用全新 testcontainer,不受影响 |
| 删 `V10__payment_refund.sql` 后,Sprint 3 order 侧需要 `biz_refund.order_id` / `user_id` | 中 | 退款作业拿不到订单/用户维度 | **另开** `V14__refund_order_link.sql` 走 `ALTER TABLE biz_refund ADD COLUMN order_id uuid, ADD COLUMN user_id uuid`,绝不回头改 Stripe 的 V9/V11 |
| Stripe 的 `biz_refund.intent_id NOT NULL` 挡住 order 侧写入 | 中 | order 侧发起退款时无 intent 可填 | Sprint 3 议题,与 Yorik 约定 intent 来源;本次不动 schema |
| §5 的夹具改动导致 `StripeLocalIntegrationTest` 失败 | 中 | Stripe 的本地集成测试红 | 该测试归 Stripe 侧;改动前在 PR 里 @Yorik,失败时优先按他的字段口径调整夹具,而非改 `V12__order.sql` |
| `sys_scheduler_lock` 无代码在用,被 reviewer 质疑 | 低 | 评审来回 | PR 描述里写明是 Sprint 3 调度预埋,并引用 `uc10-impl-plan-20260919.md` §2.3 |

回滚:§3–§6 全部是单分支本地改动,无外部副作用。任一步出问题,`git reset --hard` 回到合 `dev` 之后的提交重来即可。

## 9. 证据义务(按 agile-evidence-capture 惯例,与改造同 PR 提交)

- `docs/agile/sprint-02/log.md`:追加一条本次改造的条目,写明改号映射(V9→V12、V11→V13、V10 删除)、删 `biz_refund` 的理由、夹具对接结果,以及 §7 每条的实测输出。
- `docs/quality/nfr-register.md`:Stripe 的 NFR-24(迁移经由默认 `db/migration` 按版本序执行、无重复索引)这次被实际考验了一轮,在其 Evidence 格补一句本分支迁移并入后的实测结果。**注意本分支当前看到的 NFR 编号是合 Stripe 之前的**,合入后以 `dev` 上的编号为准,勿沿用本分支旧号。
- `backend/docs/payment/stripe-release-cleanup-20260920.md`:该文件的 pre-live 清单第 3 条("本地夹具只能是测试专用")在 §5 之后语义变了——夹具不再自建 `biz_order`。合并后请 Yorik 更新,或在本 PR 里代改并请他确认。

## 10. 与本计划无关、但同在工作区的未跟踪文件

`scripts/`(含 `create_sprint2_issues.py`)与 `backend/blocbuy-bootstrap/src/main/resources/META-INF/additional-spring-configuration-metadata.json` 在本分支未跟踪,属 B3 #96 范围,按 `uc10-impl-plan-20260919.md` 的既定说明继续排除在本 PR 之外。
