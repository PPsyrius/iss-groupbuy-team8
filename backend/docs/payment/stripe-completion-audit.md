# Stripe Sprint 2 完成情况核对

> Historical audit. See [release cleanup](stripe-release-cleanup-20260920.md) for the latest
> removal of local endpoints and the remaining shared-environment integration requirements.

Current acceptance: [refund execution, order history and remaining environment prerequisites](stripe-refunds-orders-20260920.md). The audit below is historical and must not be used as the current completion checklist.

> 历史核对：下文是本轮 ZIP 和本地适配授权之前的记录。最新来源、已解除的接口／启动阻塞和验证结果见 [2026-09-20 本地适配记录](stripe-local-integration-20260920.md)。现在已能启动本地应用，剩余主要验收是实际 Stripe Sandbox 联调。

核对日期：2026-09-20。此文件记录未完成状态，不是验收通过证明。

依据：`SWE5006-Team8-Project-Sprint2-Plan.docx` 第 3.1 成员分工、第 3.2.2 UC6、相关验收标准，以及 `Stripe-Sprint2-Design-Review.md` v1.1。范围仍是 Yorik 的 Sprint 2 支付及待退款义务，实际退款执行属于 Sprint 3。

## 按要求核对

| 要求 | 当前证据 | 结论及缺口 |
| --- | --- | --- |
| 服务端按团购条款报价，不信任浏览器金额 | `CheckoutQuote` 保存冻结报价；金额、所有者、请求键及供应商返回值校验有测试 | 部分完成。真实购买意图和商品／团购报价入口尚未接入，测试中的报价由协作者替身提供 |
| Hosted Checkout 创建、查询和跳转 | `StripePaymentGateway` 使用官方 SDK；参数映射及请求幂等已有组件测试 | 部分完成。没有真实 Sandbox 创建、支付及浏览器返回证据 |
| 原文验签、未知事件 2xx | 完整 Spring 请求链测试；含中文、换行、篡改和缺失签名；HTTP 到真实 PostgreSQL 的测试 | 本地边界已验证。Stripe 的实际投递、代理和公网 HTTPS 链路未联调 |
| 事件去重与付款状态 | 同一事件并发、跨事件重放、乐观锁、事务回滚和恢复已测试 | 部分完成。当前状态只有 CREATED／SUCCEEDED／CANCELED；FAILED 的可信终止判据及状态路径尚未实现，不能把可重试的卡失败直接当成终止 |
| PaymentSucceeded 驱动真实团购和订单 | 已有 `PaymentSucceeded` 记录和 `PaymentBusinessPort` 契约 | 未完成。没有真实端口实现或团队 DomainEventPublisher 接入；不能以测试表代替真实成员／订单 |
| TeamExpired 留下唯一 PENDING 退款义务 | `recordExpiredTeam` 和 `biz_refund` 唯一约束通过数据库组件测试；与到期调用方共用事务 | 部分完成。真实 TeamExpired 事件、订阅者和团购到期任务未接入。实际退款未做，也不纳入本次范围 |
| 有效期及失效处理 | 冻结报价、业务截止时间、Session 期限校验及显式关闭 Session 的核心方法 | 部分完成。持久化失效请求、自动清理调度及业务提前关闭的真实接口仍未完成 |
| 对账、观察、人工恢复 | 只读扫描／预览、处理单分派、下次检查时间、恢复审计、结案证据检查；丢失 Session 的候选关联恢复 | 部分完成。打包后操作命令、实际 Stripe 查询及项目负责人通知流程尚未联调；缺失下游记录会保持未解决，并未自动重写他人模块修复 |
| 正式数据库迁移和协作者契约 | 支付分支有两个显式启用的本地迁移，原 V1–V8 未改 | 部分完成。需要与团队订单／退款迁移编号、字段、锁顺序及事件事务阶段共同对齐 |
| 可复现验证 | 43 项专项测试通过；常规本地检查通过，371 项常规测试实际执行，另有 27 项独立数据库流程测试 | 组件验证完成。Failsafe、真实业务端到端和 Sandbox 验收未完成；不能用总测试数推导用例已完成 |
| 对齐最新可读 dev | 浏览器确认 dev `211cfb7`，并对比本地 `88a7fb4` 到该提交的 11 次提交、216 个文件差异 | 部分完成。Git 拉取认证失败，浏览器 ZIP 和补丁被客户端阻止；尚未完整同步源码 |

Word 中“插入回执后崩溃应当被视为已完成”的表述，与可靠处理要求有冲突。实现遵循后续安全设计：回执、付款和必要业务效果必须同事务提交；失败一起回滚，不把未处理的付款吞掉。Word 的 AFTER_COMMIT 退款记录安排也需要按安全设计与事件负责人确认，不能直接套用而留下已到期却没有退款义务的窗口。

## 已验证的实际启动阻塞

使用本地构建的 `backend/blocbuy-bootstrap/target/blocbuy-backend.jar` 启用 `local,stripe-sprint2`，关闭 Web 服务和 Flyway，把 PostgreSQL／Redis 显式指向 `127.0.0.1:1` 的无效测试端口，并清空 Stripe 配置。没有运行数据库迁移或向共享环境写入。

进程退出码为 1，启动失败的原因是：

```text
No qualifying bean of type 'com.blocbuy.payment.gateway.PaymentBusinessPort' available
```

日志：`/tmp/blocbuy-stripe-production-wiring-audit.log`。

当前代码进一步确认：

- `PaymentBusinessPort` 只有测试中的 `TestBusiness` 实现。
- `OrderAppService` 是空接口；`OrderRepositoryImpl.findById` 仍返回 `Optional.empty()`。
- 当前本地生产代码没有 `DomainEventPublisher`、`TeamExpired` 或 `RefundRequiredRecorder` 接入。
- 曾建议的仓库外配置路径 `/Users/spiral/Documents/BlocBuy-Stripe-Local/stripe.env` 不存在，也尚未收到其他私有测试配置路径。

## 解除阻塞所需材料或决定

1. 提供 Stripe Sandbox 私有配置文件路径。不要把密钥再次发到聊天，也不要使用之前泄露的云凭据。
2. 提供组员最新的购买意图、团购事件、订单和迁移实现；或者明确允许在本地补充最小适配范围。当前目标禁止重写他人模块，尚未获得扩展范围的答复。
3. 提供可读取的最新 dev ZIP，或由用户恢复只读拉取权限。不会代为调整 GitHub 权限、浏览器安全保护或向仓库提交任何内容。

恢复工作时，先接入真实协作者并验证应用启动，再完成上表剩余状态／清理路径，运行真实支付、重复投递、到期退款义务和人工恢复流程，最后制作 Sandbox 证据包。现有组件测试可作为回归基础，但不替代这些验收。
