# Java 21、面向对象、Spring Boot 与 Maven

编写 Java/Spring 代码、配置 Bean、事务或构建文件时读取。

## Java 与面向对象

- 类和方法保持单一、清晰职责；优先组合而非继承。继承只表达稳定的 is-a 与可替换关系，禁止为复用几行代码制造层级。
- 用领域类型代替基本类型迷恋，例如 `OrderId`、`Money`、`PhoneNumber`；在构造时校验并保持不可变。
- 聚合/实体不使用 Lombok `@Data`；谨慎生成 `equals/hashCode/toString`，避免包含懒加载、敏感字段、可变集合或递归关联。
- 对外暴露集合使用不可变副本；不泄漏内部可变状态。`record` 适合不可变 DTO/值载体，但承载复杂不变量时使用明确类型与工厂。
- `Optional` 仅用于可能缺失的返回值；不用作实体字段、DTO 字段或方法参数，不返回 null Optional。
- 金额用 `BigDecimal` 并显式 scale/rounding/currency；比较用 `compareTo`。标识符禁止用浮点；计数考虑溢出。
- 时间点用 `Instant`，用户/门店日历规则用 `LocalDate`/`ZonedDateTime` 并显式 `ZoneId`；禁止依赖服务器默认时区和裸 `new Date()`。
- 资源使用 try-with-resources；捕获具体异常并保留 cause。禁止空 catch、`printStackTrace`、吞异常或无界重试。
- `switch` 应穷尽业务枚举；未知状态快速失败。不要使用 enum ordinal 持久化或对外传输。
- 并发共享状态尽量消除；使用线程安全组件和有界执行器。Java 21 虚拟线程适合大量阻塞 I/O，但上线前验证连接池、ThreadLocal、限流和监控；CPU 密集任务不因虚拟线程变快。除非项目明确接受，禁用 preview API。
- 方法参数和返回契约明确空值语义；入口校验，内部保持不变量。不要散布重复 null 判断掩盖模型缺陷。
- 注释解释原因、约束和取舍，不复述代码；公共契约和复杂算法写 Javadoc。TODO 必须关联责任或工单，不留模糊占位。

## Spring Bean 与配置

- Spring Bean 使用 `@RequiredArgsConstructor` + `private final` 构造器注入。只有框架强制时允许其他注入方式并写明原因。
- 选择准确 stereotype：Controller、Service/ApplicationService、Repository/Adapter。不要让每个对象都成为 Spring Bean；领域对象由业务代码创建。
- 配置用类型安全 `@ConfigurationProperties` + `@Validated`，集中默认值和单位；禁止在业务代码散落 `@Value`。配置类不承载业务逻辑。
- `@SpringBootApplication` 放在顶层包；显式限制扫描边界，避免误扫测试/其他域。Bean 名冲突不靠允许覆盖解决。
- 外部客户端必须配置连接/读取/响应超时、连接池、请求大小、重试边界和优雅关闭；禁止无超时调用。
- 只有幂等、瞬时失败操作可重试；使用指数退避、抖动和最大次数。不要叠加多层重试造成放大。
- `@Async` 使用命名、有界执行器并传播必要的 trace/security context；不得依赖调用方事务延伸到异步线程。禁止默认无界线程池。

## 事务

- `@Transactional` 通常放应用层公开命令方法；查询使用 `readOnly = true`。事务保持短小，不在事务中做慢网络调用、等待或大文件处理。
- 明确 Spring 代理限制：同类自调用不会经过事务代理；非公开方法/手工 new 的对象也不会生效。不要靠增大事务范围掩盖设计问题。
- 默认只对未检查异常回滚；若使用受检异常必须明确 `rollbackFor`。不要捕获异常后返回成功导致事务提交。
- 选择隔离级别前先写明要防止的并发异常；使用数据库唯一约束、条件更新、行锁或乐观锁维护不变量。死锁/序列化失败的重试必须有界且重跑完整事务。
- 事务提交后才能触发依赖已提交数据的副作用；跨 DB/MQ 使用 Outbox，见消息参考。

## Web 与序列化

- Controller 参数显式使用 `@PathVariable`、`@RequestParam`、`@RequestBody`，请求体加 `@Valid`；禁止实体直接绑定。
- Jackson 对未知/敏感字段、时间和枚举的策略要一致；禁止启用危险多态反序列化。向后兼容地演进 DTO。
- 上传限制类型、大小、数量并使用服务端生成的存储名；不信任文件名/Content-Type，防路径穿越和恶意内容。

## Maven

- 父 POM 使用 `dependencyManagement`/Spring Boot BOM 集中版本；子模块不随意覆盖。提交 Maven Wrapper，构建固定 Java 21 toolchain/release。
- Maven Enforcer 至少约束 Java/Maven 版本、依赖收敛和禁止重复类；锁定插件版本，保证 CI 可复现。
- 依赖最小化，优先官方 starter/client；禁止同时引入功能重叠日志、JSON、HTTP 客户端。定期扫描 CVE 和许可证，升级前跑兼容测试。
- 构建建议包含格式化、静态分析、单元测试、集成测试与架构测试；质量门禁失败不得通过 profile 默认跳过。
- 生产构件不包含测试密钥、开发配置、未使用样例或本地绝对路径。

