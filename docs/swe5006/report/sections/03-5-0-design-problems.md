<!-- report
rubric: 3.5, G4
owner: Lim Jiechao
plan: #679
sources: RUBRIC.md 3.5 checklist; #463 plan decision D1
owns: none
status: draft
-->

## 3.5 Design Problems and Patterns

Each member identified one design problem in the system and solved it with a design pattern. The five problems are: creating a team without naming its concrete class, solved with Factory Method (3.5.1, Wang Ziang (Eric)); triggering the consequences of a team's outcome without coupling the team to them, solved with Observer (3.5.2, Yu Qianshuo (Yorik)); relaying outbox events over different transports with one algorithm, solved with Template Method (3.5.3, Panpakorn Siripanich (Pan)); varying how a listing is priced, solved with Strategy (3.5.4, Lim Jiechao); and changing what a listing allows as its lifecycle state changes, solved with State and protected against lost updates with Optimistic Offline Lock (3.5.5, Liu Jin).

Each subsection follows the same six parts: the problem, shown with class and sequence diagrams of the design before the pattern; the candidate patterns; the motivation for the choice, including its liability; the class diagram and the sequence diagrams of the solution; and the implementation decisions, naming the classes that implement the pattern and their unit tests. Where the earlier design existed in the code, the "before" diagrams show it as it was committed; where it did not, they are labelled as a brute-force design that was never committed. The implementation of the system follows the design after the pattern is applied.
