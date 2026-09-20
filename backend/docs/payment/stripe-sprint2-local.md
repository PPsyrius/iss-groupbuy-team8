# Stripe Sprint 2 本地实现与交接

> Historical design and local-run notes. Local endpoints and launch scripts were removed;
> see [release cleanup](stripe-release-cleanup-20260920.md). The commands below are not current instructions.

> 本文以下为本地适配授权前的历史交接。最新 ZIP 已导入，接口已接上，本地后端／PostgreSQL／Redis 已启动。当前运行命令、51 项专项验证及 Stripe 登录缺口请看 [最新本地适配说明](stripe-local-integration-20260920.md)。

2026-09-20。个人分支 `feature/yorick-stripe-sprint2`，代码基线 `88a7fb4`。

这是支付组件的第一版实现，尚不是可以合并部署的完整 UC6。已实现 Checkout、MyBatis 支付持久化、可信回调处理、同步履约接口、待退款记录及恢复入口。团购/订单协作者仍需接入，真实 Stripe Sandbox 联调尚未执行。

专项测试 43 项通过，范围和证据见 [测试记录](stripe-test-results.md)。不要把测试替身的通过等同于真实业务联调完成。

本次 `git fetch origin` 返回认证失败。9 月 20 日通过已登录浏览器只读检查了 dev、当前列出的 12 个分支，以及 [本地基线到 dev 的差异](https://github.com/PPsyrius/iss-groupbuy-team8/compare/88a7fb49cf813be16b05604db6d02b38015acbdb...211cfb7d95f60bebdf322ab19a83f43cb70c07ad)：最新 dev 是 `211cfb7`，相差 11 次提交、216 个文件。后端改动集中在商品搜索、测试覆盖率和构建配置；这批差异没有增加订单生产实现、团购生产方法或支付事件接入。不能据此判断组员尚未推送的代码。

浏览器下载 ZIP 及读取 compare 补丁均被客户端阻止，尚未取得最新 dev 完整源码。本地仍基于 `88a7fb4`，不是已同步至 `211cfb7`。没有修改 GitHub 权限、浏览器安全设置或远程仓库；后续拿到最新源码还需要重新编译和测试。

## 直接运行支付测试

在仓库根目录执行：

```bash
bash scripts/test-stripe-sprint2.sh
```

需要 Java 21、Maven Wrapper 和 PostgreSQL 18。macOS 默认使用 `/opt/homebrew/opt/postgresql@18/bin`，其他安装位置通过 `STRIPE_PG_BIN` 指定。本机已安装 PostgreSQL 18.6，没有开启 Homebrew 后台服务。

脚本每次新建一个私人临时集群，只监听 `127.0.0.1:55439`，数据库名 `blocbuy_stripe_test`，用户 `stripe_test`，密码随机生成，不输出密码。端口被占用时失败，不回退连接其他服务器。它不读取组员的数据库密码、AWS、Google 或 Stripe 凭据。

测试结束自动停止该集群，打印保留目录。测试数据和日志留在这个仅当前用户可访问的临时目录，便于检查；脚本不删除其他数据库。Homebrew 安装时创建的默认集群也没有被测试使用。

测试中的真实部分：PostgreSQL、Flyway、MyBatis、数据库唯一约束、行锁和事务回滚。测试替身：Stripe 网络响应、团购资格与订单创建。签名测试另行调用真实 Stripe SDK 验签，使用人工构造的测试事件和非真实签名密钥。

## 这几个配置目录分别是什么

| 路径 | 作用 |
| --- | --- |
| `backend/blocbuy-bootstrap/src/main/resources/application-*.yml` | 应用连接数据库、Redis、OAuth 等环境配置 |
| `backend/docs/db/*.sql` | 团队数据库表结构设计；并不等于 Java 数据访问已经实现 |
| `backend/blocbuy-bootstrap/src/main/resources/db/migration` | 应用启动时由 Flyway 执行的正式迁移 |
| `backend/blocbuy-bootstrap/src/main/resources/db/stripe` | 本次支付分支的显式启用迁移，默认不会执行 |

`127.0.0.1` 指运行程序的那台机器自己。在 EC2 上，它可能是 EC2 本机的数据库；在 Mac 上，它就是 Mac。`postgres` 和 `redis` 则是 Docker 网络服务名，不是可以从任意机器直接访问的公网地址。

因此本地开发不需要寻找一个共享服务器地址，而应启动自己的数据库并覆盖连接参数。

## 本地配置不进入合并

团队现有的四个 `application*.yml` 环境配置及 `application.yml`、Docker Compose、EC2 配置均未修改。V1 至 V8 也保持原样。

`backend/docs/payment/stripe-local.env.example` 是没有真实密钥的配置示例。将副本保存在仓库外，例如 `/Users/spiral/Documents/BlocBuy-Stripe-Local/stripe.env`，填自己的本地参数，并限制文件权限。运行应用前在同一个终端加载：

```bash
set -a
source /Users/spiral/Documents/BlocBuy-Stripe-Local/stripe.env
set +a
```

环境变量覆盖 YAML 中的连接值。示例给未来交互开发分配了 PostgreSQL `55440`、Redis `56379`、后端 `18589`；这些不是现成服务器。本次只启动过专项测试的 `55439`，没有启动示例中的常驻数据库、Redis 或浏览器服务。

不要提交私有 env 文件，不使用 `git assume-unchanged` 隐藏对团队文件的修改，也不靠合并前手动恢复地址。提交前检查 `git diff --name-only` 和 `git diff --cached`。

AWS 密钥、Origin Secret 和 Google Client Secret 不属于支付组件测试所需信息。它们不进入代码、文档或测试夹具。真实浏览器登录需在 Google 单独登记 localhost callback，不能修改团队现有 CloudFront callback 来迁就个人开发。

## 已实现的代码

| 内容 | 入口或文件 |
| --- | --- |
| 服务端冻结报价、精确 SGD 金额 | `CheckoutQuote`、`Payment` |
| 创建、查询、失效 Checkout 和原文验签 | `PaymentGateway` 继承 `CheckoutGateway`；`StripePaymentGateway` 实现 |
| 请求重用、短事务与付款流程 | `StripeCheckoutService.prepare` |
| 回调验证与同步事务处理 | `StripeCheckoutService.handleWebhook` |
| 支付读写、版本冲突、事件去重 | `PaymentRepositoryImpl`、`PaymentMapper.xml`、`BizPaymentDO`、`PaymentConverter` |
| 付款成功业务契约 | `PaymentSucceeded`、`PaymentBusinessPort` |
| 到期退款义务 | `StripeCheckoutService.recordExpiredTeam`，持久化到 `biz_refund` |
| 只读检查、供应商查询、人工恢复 | `scan`、`inspect`、`recover` |
| 关闭过期意图的 open Session | `expireDueSessions`，当前为显式调用入口，没有自动调度 |

Checkout 功能目前只允许 `sk_test_` 的测试账户，SGD、银行卡，不支持 Connect 多账户或 live 收款。每个数据库只承载一个测试账户，不能更换账户后继续使用原付款记录。金额换算不使用浮点数，关闭自动货币转换，Session 参数和幂等键在本地保存后不再随重试改变。

创建有效期采用 31 分钟，给 Stripe 的最短 30 分钟要求留出一分钟网络余量，报价承诺必须覆盖这段时间。团购只有五分钟剩余时间仍可准备付款，但不预留名额；付款确认时再检查业务资格。创建结果未知且超过 55 秒时停止自动重建并写核查记录，不擅自换一个幂等键收款。

回调先验签，再用当前测试账户的 API 查询 Session，核对付款编号、业务意图、金额、币种、有效期和供应商引用。只有 `complete` 且 `paid` 才确认付款。事件去重记录、付款变化和同步业务效果共用事务。下游异常会回滚，不能先单独提交去重记录吞掉重试。

确认已收款但入团失败时，保存 `SUCCEEDED` 和 `PENDING` 退款义务；不把付款改成失败。相同付款的退款记录由唯一键防重，冲突金额或原因会使事务失败。实际退款执行仍属于 Sprint 3。

## 与组员连接的位置

生产代码没有提供假的 `PaymentBusinessPort`。启用 `stripe-sprint2` profile 时，如果缺少真实实现，应用启动会失败；默认 profile 下 webhook 返回可重试错误，而不是记录日志后返回成功。

Eric 与 Liu Jin 的协作者需要实现：

1. `lockQuote(userId, intentId)`：从服务端取冻结报价，校验用户和资格，JOIN 先锁团购再锁意图。
2. `lockForSettlement(quote)`：付款锁之前取得团购/意图锁；不能在持有付款锁后反向等待团购。
3. `fulfil(PaymentSucceeded)`：从可信意图重建参数，调用现有 `startConfirmed` 或 `joinConfirmed`，并让成员和真实订单在同一事务完成。成功必须返回真实团购和订单 ID；业务拒绝返回原因，技术异常抛出。
4. `lockExpiredTeam(dealId)`：取得同一团购行锁并确认真正过期，再调用退款义务记录器。团购状态、订单取消、退款记录必须在同一个事务提交。
5. `fulfilmentEvidence(event)`：恢复时检查真实成员、订单、付款关联和业务状态，返回 `NONE`（无有效履约）、`COMPLETE`（完整且一致）或 `INCONSISTENT`（缺失一部分或关联冲突）。不能仅凭付款表的结果字段宣布完成。该接口仍需真实协作者实现，本地测试只验证其调用约束。

当前使用同步返回结果的内部接口传递 `PaymentSucceeded`，没有假装已接上 Pan 的事件发布器。接入团队事件实现时，必须保留事务传播及结果确认；不能改成不可靠的异步或仅 AFTER_COMMIT 通知。

没有新增公网 Checkout HTTP 接口，也没有修改 OpenAPI。按 Word，购买入口仍由团购控制器承接；该控制器取得登录用户并执行 CSRF/请求限制后，调用支付的 `prepare`。这些对外入口、前端跳转和限流尚未完成，不能把内部 Java 方法直接暴露给浏览器。

## 迁移合并前需要确认

新增迁移位于 `db/stripe/V2026092001__stripe_checkout.sql` 和 `V2026092002__payment_review_cases.sql`，只有显式把 `classpath:db/stripe` 加入 Flyway locations 才执行。第二个迁移新增持久化核查处理单，并保守导入旧核查日志中的异常；不改第一个迁移的校验和。

它允许付款前 `order_id` 为空，扩展原 `biz_payment` 和回调表，增加待退款及核查日志表。没有虚构订单 ID。迁移编号、字段和 `biz_refund` 表需与 Liu Jin 正在开发的迁移对齐；不能直接把本文件当作团队正式 V9/V10 合并，尤其不能在已经执行本地高版本迁移的测试库上评估他人的低版本迁移顺序。

## 操作入口与剩余恢复工作

完成真实协作者接入、配置审核后，可使用 `stripe-sprint2,stripe-ops` profile 的命令入口。运行构建后的后端 JAR 时增加以下参数之一；不要对共享环境执行未经授权的写操作：

```text
--payment-command=scan
--payment-command=cases
--payment-command=cases --after-case-id=<上一页最后一个case UUID>
--payment-command=assign --payment-id=<UUID> --operator=Yorik --next-check-at=<未来24小时内的UTC时间>
--payment-command=inspect --payment-id=<UUID>
--payment-command=recover --payment-id=<UUID> --operator=Yorik --confirm-recovery=true
--payment-command=inspect --payment-id=<UUID> --session-id=<cs_test_候选ID>
--payment-command=recover --payment-id=<UUID> --operator=Yorik --confirm-recovery=true --session-id=<cs_test_候选ID>
--payment-command=expire-open-sessions --confirm-expiry=true
```

这些命令需要可启动的完整 Spring 应用和真实协作者，当前只验证了它们调用的核心服务，尚未做打包后命令行联调。inspect 是只读预览，recover 会重新查询供应商，不能接受手工输入“已付款”。运行结果日志不输出付款链接或密码。

`scan` 和 `cases` 只读。付款扫描最多 100 条；处理单通过 `after-case-id` 翻页，不把第一页当成全部结果。异常回调会保存处理单；操作员也可以使用 `assign` 创建／分派处理单并安排下次检查。`inspect` 显示供应商事实及拟执行动作，不修改付款；`recover` 需要明确确认，重新查询后才处理。

`biz_payment_review_case` 保存负责人、状态、最后及下次检查时间、事实摘要和解决时间。恢复失败保留已分派的处理单；仍可支付的 Session 保持观察。只有重新核对资金事实和履约／退款义务后才结案，历史审计日志不删除。付款结果标志与真实下游证据不一致时保持未解决，交给相关模块负责人修复后再次核查；当前没有自动修复他人模块的缺失记录。

`cases` 对未分派、超时和恢复失败输出结构化告警。通知项目负责人仍需人工完成，没有向群聊、GitHub 或云监控发送消息，也没有代替团队确认代理安排。打包后的命令运行、真实协作者接入及告警通知流程尚未验收。

如果 Stripe 已创建 Session，但本地因响应丢失而没有保存 ID，操作员可先在正确的 Sandbox 账户中找到候选 ID，再使用带 `--session-id` 的 inspect／recover。候选 ID 只是查询线索，不能覆盖已有绑定；不能输入 URL、金额或“已付款”状态。恢复会通过配置账户的网关重新查询，核对付款编号、意图、金额、币种、到期时间和已有供应商引用，然后复用原有事务处理。

只读预览不会保存关联。确认恢复后，已付款 Session 才进入付款及履约校验；仍开放的 Session 只补回关联并保持观察，不再创建另一笔 Checkout。不匹配或查询失败会保留处理单和可追溯的查询线索。该路径已通过数据库组件测试，真实 Stripe 账户查询、人工查找操作和命令行运行仍待联调。

## 尚不能宣布完成的部分

- 取得最新 dev 的完整源码并消除与组员代码的重叠；浏览器只读差异核对已完成。
- 真实报价/购买意图、团购、订单协作者，以及共享锁序和事务契约。
- 正式 HTTP 合同、CSRF、限流、前端与真实 Google 登录联调。
- Stripe Sandbox 实际支付证据：需要在本地私有配置中提供测试密钥，不要再贴到聊天里。
- 自动 Session 清理的调度、恢复命令运行与通知流程，以及设计中全部故障场景的验收。
- 云环境逐段 TLS、存储加密、部署安全和团队 CI/SonarQube；本地测试不能证明这些已经配置。

Stripe 参数和验签实现参考 [Checkout 创建参数](https://docs.stripe.com/api/checkout/sessions/create) 与 [webhook 说明](https://docs.stripe.com/webhooks)，核对日期 2026-09-20。
