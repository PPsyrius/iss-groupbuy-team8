<!-- report
rubric: 3.4, G4
owner: Lim Jiechao
plan: #678
sources: RUBRIC.md 3.4 checklist; #463 ownership decisions (24 Oct)
status: draft
-->

## 3.4 Analysis & Design Models

Each member takes their own use cases from analysis to design. The subsections follow the order in which the team presented its design work: 3.4.1 Wang Ziang (Eric) for UC3 Initiate a team, UC4 Join a team and the included Pay upfront; 3.4.2 Yu Qianshuo (Yorik) for UC6 Refund on team expiry; 3.4.3 Panpakorn Siripanich (Pan) for UC1 Browse listings and UC2 Register and sign in; 3.4.4 Lim Jiechao for UC8 View my orders and teams and UC7 Share attribution; and 3.4.5 Liu Jin for UC9 Admin listing management and UC5 Team lifecycle resolution.

Every subsection has the same five parts. Part A is the analysis class diagram, drawn only with «boundary», «control» and «entity» classes and free of platform types. Part B gives one analysis sequence diagram for each major flow. Part C is the design class diagram, with the real classes, interfaces and layers of the code. Part D gives one design sequence diagram for each flow in part B. Part E is a compliance table: for each transition strategy of section 3.2 that applies, it names the analysis object the strategy transformed and the design element that resulted. The statecharts that more than one use case depends on sit with the use case that drives them: the order and checkout payment statecharts in 3.4.1, the refund obligation statechart in 3.4.2, and the team statechart in 3.4.5. The listing statechart belongs to the State pattern in 3.5.5.
