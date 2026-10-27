<!-- report
rubric: 3.4, G4
owner: Lim Jiechao
plan: #678
sources: RUBRIC.md 3.4 checklist; #463 ownership decisions (24 Oct)
owns: R21, R24, R39
status: draft
-->

## 3.4 Analysis & Design Models

Each member takes their own use cases from analysis to design. The subsections follow the order in which we presented our design work: 3.4.1 Wang Ziang (Eric) for UC3 Initiate a team, UC4 Join a team and the included Pay upfront; 3.4.2 Yu Qianshuo (Yorik) for UC6 Refund on team expiry; 3.4.3 Panpakorn Siripanich (Pan) for UC1 Browse listings and UC2 Register and sign in; 3.4.4 Lim Jiechao for UC8 View my orders and teams and UC7 Share attribution; and 3.4.5 Liu Jin for UC9 Admin listing management and UC5 Team lifecycle resolution.

Every subsection has the same five parts. Part A is the analysis class diagram. Part B gives one analysis sequence diagram for each major flow. Part C is the design class diagram, with the real classes, interfaces and layers of the code. Part D gives one design sequence diagram for each flow in part B. Part E is a compliance table: for each transition strategy of section 3.2 that applies, it names the analysis object the strategy transformed and the design element that resulted. The statecharts that more than one use case depends on sit with the use case that drives them: the order and checkout payment statecharts in 3.4.1, the refund obligation statechart in 3.4.2, and the team statechart in 3.4.5. The listing statechart belongs to the State pattern in 3.5.5.

#### Shared modelling conventions

All five subsections draw their models with these conventions.

- **Analysis models.** Analysis class and sequence diagrams use only «boundary», «control» and «entity» classes. Actors talk only to boundaries, and attributes sit only on entities. No platform, framework, table, lock or service appears; design classes such as the purchase intent appear only from part C onwards.
- **Actor and provider lifelines.** In analysis sequences an actor is drawn as a UML actor that talks to one boundary. An external provider that is an actor, such as Stripe or Google, talks to the system only through its gateway boundary (PaymentGateway for Stripe, IdentityProvider for Google); where its lifeline would crowd the figure, it is left out and its part is shown as a note. In design sequences the provider is reached through the port that stands for it, such as `:RefundGateway` or `:OAuthIdentityGateway`, and each lifeline is an anonymous object of the named class (`:Class`).
- **Transactions.** UML has no transaction fragment, so a `critical` fragment marks a database transaction. Its label names the transaction's settings where they matter: isolation level, timeout and propagation, for example "critical settlement transaction, READ COMMITTED, 10 s".
- **Strategy tags.** In a design class diagram each class carries, after any UML stereotype, a tag for the transition strategy of section 3.2 that produced it: Lang 3.2.1 language and framework, Pres 3.2.2 presentation, Dist 3.2.3 distribution, Pers 3.2.4 persistence, Ext 3.2.5 external interface, Sec 3.2.6 security, Sched 3.2.7 scheduling, Conc 3.2.8 concurrency, Evt 3.2.9 domain events. A class shaped by two strategies carries both tags, for example «interface, Pers, Conc». In member lists `#` is protected, `~` is package visibility, and `…` stands for parameters left out to keep a box narrow.
