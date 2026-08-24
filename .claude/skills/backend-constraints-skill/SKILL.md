---
name: backend-constraints-skill
description: 在 Claude Code 中实施或审查 BlocBuy 的 Java 21、Spring Boot、MyBatis、PostgreSQL、Redis、Elasticsearch、RabbitMQ、Maven 后端代码。用于新功能、重构、代码审查和架构检查，强制模块化单体、DDD、COLA 四层、REST API、异常状态码、Sa-Token、安全、数据访问、消息一致性、测试与可观测性；不用于前端或非 Java 服务。
---

# BlocBuy 后端开发约束

把本技能作为项目级门禁，而不是通用教程。Claude Code 可在相关任务中自动加载，也可由用户通过 `/backend-constraints-skill` 显式调用。加载后，以下约束在当前任务后续回合持续适用。

先读取当前工作目录及相关子目录适用的 `CLAUDE.md`。尊重仓库已有且不冲突的约定；若 `CLAUDE.md`、现有实现与本技能存在冲突，明确指出冲突及影响，不得静默降低安全、数据一致性或业务正确性约束。只修改用户授权范围内的代码。

## 工作方式

1. 先检查仓库结构、构建文件和相关实现，再识别本次改动所属限界上下文、用例、聚合、四层位置及同步/异步边界；不要仅凭文件名或用户描述假设现状。
2. 检查项目基线。若缺少 `ErrorCode` 状态码抽象或领域状态码枚举，先提示并推荐优先建设；若当前任务包含业务异常实现，则先补齐最小可用状态码体系。若缺少统一 `@RestControllerAdvice`，采用同样处理。不要为绕过缺失基线而临时抛出裸 `RuntimeException`。
3. 只读取本次任务相关的参考文件并实施：
   - 架构、目录、DDD、模块依赖：读 [references/architecture-and-ddd.md](references/architecture-and-ddd.md)。
   - REST、状态码、异常、Sa-Token：读 [references/api-errors-security.md](references/api-errors-security.md)。
   - Java 21、面向对象、Spring Boot、Maven：读 [references/java-spring.md](references/java-spring.md)。
   - MyBatis、PostgreSQL、Redis、Elasticsearch：读 [references/data-access.md](references/data-access.md)。
   - RabbitMQ、事务一致性、幂等：读 [references/messaging-consistency.md](references/messaging-consistency.md)。
   - 测试、日志、监控、性能与交付：读 [references/quality-operations.md](references/quality-operations.md)。
   - 遇到版本相关行为或需要进一步核实时：只查 [references/authoritative-sources.md](references/authoritative-sources.md) 中的官方来源，不凭印象写死依赖版本。
4. 实施后运行仓库已有且与改动相关的编译、测试、静态分析和架构检查，再按“硬性门禁”自检；不得为让验证通过而删除测试、放宽门禁或隐藏失败。审查任务按严重度输出可定位的问题、影响和最小修复建议。

## 硬性门禁

以下规则除非用户明确批准例外，否则不得违反：

- 采用模块化单体、DDD 和 COLA。代码先按限界上下文/业务域划分，再落入 `adapter`、`application`、`domain`、`infrastructure` 四层；依赖只指向业务核心，不允许跨域访问对方 Mapper、DO、Repository 实现或内部聚合。
- `domain` 不依赖 Spring、MyBatis、Redis、Elasticsearch、RabbitMQ、Web DTO 或基础设施实现；仓储和外部能力端口定义在领域侧，实现在基础设施层。
- Controller/Consumer 只做协议适配、鉴权入口、校验和结果转换；应用层编排用例与事务；领域层承载不变量；基础设施层处理持久化和外部系统。
- 严禁在任何循环、Stream 回调、递归或逐项异步任务中执行 Mapper/Repository/远程查询。先收集并去重 ID，执行批量查询，再构造 `Map<ID, Entity>` 回填；批量写同理。发现疑似 N+1 必须提示并修正或说明阻塞原因。
- REST API 使用资源名词、正确 HTTP 方法和状态码、明确幂等与分页语义。禁止用 `POST /getXxx`、全量列表、永远返回 HTTP 200 或直接暴露 DO/聚合。
- Spring Bean 依赖必须为 `private final` 并通过构造器注入；Lombok 使用真实注解 `@RequiredArgsConstructor`。`@RequiredConstructorArgs` 不存在，禁止照抄该拼写。禁止字段注入和一般业务代码中的 `ApplicationContext.getBean()`。
- 需要日志的类统一使用 Lombok `@Slf4j`；禁止 `System.out/err`、字符串拼接日志、记录口令/token/完整手机号/身份证/银行卡或大对象。异常日志不重复打印。
- 业务异常只能由状态码枚举构造，例如 `throw new BizException(OrderErrorCode.ORDER_NOT_FOUND)`；禁止自由字符串、魔法数字或裸运行时异常表达业务失败。
- 所有入站请求异常必须由统一全局异常处理转换；禁止 Controller 内散落 `try/catch` 组装错误响应，禁止把堆栈、SQL、内部类名返回客户端。
- 认证与授权统一使用 Sa-Token；禁止并存自研 token 解析器、第二套鉴权框架或仅靠前端隐藏按钮授权。对象级权限必须在服务端检查。
- PostgreSQL 是交易事实源；Redis 和 Elasticsearch 不作为订单、库存、支付等强一致事实的唯一来源。数据库与 RabbitMQ 之间使用 Outbox/可靠事件方案，不宣称普通本地事务能原子提交两者。
- 所有外部输入使用 Bean Validation 和业务校验；SQL 使用参数绑定；不得提交密钥、默认口令或生产连接信息。

## 决策优先级

遇到冲突时依次保护：业务正确性与资金/库存安全 > 授权与数据安全 > 一致性和幂等 > 模块边界 > 可维护性 > 性能微优化。任何影响订单、支付、库存或退款的并发设计都必须说明数据库约束、锁/版本策略、幂等键和失败恢复路径。

## 输出要求

- 实现任务：交付代码，并说明所属业务域、分层位置、事务/一致性方案、验证结果和未解决风险。
- 审查任务：优先报告门禁违规，给出文件与行号；区分“必须修复”和“建议改进”。
- 基线缺失：明确写出“缺少状态码枚举体系”或“缺少全局异常处理”，解释它阻塞了什么，并把建设该基线列为第一建议。
- 不为追求规范大规模改写无关代码；新增抽象必须由当前用例或清晰复用点驱动。
