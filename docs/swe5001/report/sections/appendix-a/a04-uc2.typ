#import "lib-a.typ": *

== UC2 Sign In with Google <sec-a-uc02>

UC2 lets a visitor sign in with a Google account. The first sign-in registers a local buyer account; later sign-ins reuse it. Every sign-in opens one session that all three services read, because the roles and permissions are copied into it at sign-in. Google is the only sign-in channel, so BlocBuy stores no passwords. In the SWE5001 design, identity-svc owns the whole use case and its tables; commerce-svc and notification-svc never ask identity-svc for roles, they read the session (AD-05). This part traces UC2 from its flows of events to the code. UC2 is designed and built; its status on AWS is given by capability in @tbl-status.

=== Use Case and Flows of Events <sec-a-uc02-flows>

- *Actors.* Buyer or platform administrator (primary: a visitor not yet signed in). Google (secondary: verifies the identity).
- *Trigger.* The visitor presses "Continue with Google", or tries an action that needs a session (start or join a team, the account page, the admin console).
- *Services.* identity-svc owns B1–B9, A1–A3 and B9's current-user read. Every service checks the session on later requests (A4, A5); in commerce-svc that check reads only the session.
- *Preconditions.* The visitor has a Google account; BlocBuy is registered with Google as a sign-in client.
- *Postconditions on success.* Exactly one active local account is bound to the Google identity; a new account holds the buyer role only; the browser holds a session whose roles and permissions were read at sign-in.
- *Postconditions on failure.* No session cookie is issued, no two accounts are merged, and the account's other sessions are untouched.

#atable(
  [UC2 flows of events. B = basic flow, A = alternate flow, E = exception flow; "At" is the step where the flow branches off.],
  <tbl-a-uc02-flows>,
  columns: (auto, auto, 1fr),
  header: ([Step], [At], [What happens]),
  [*B1*], [], [The visitor chooses to sign in with Google. The page remembers where to return.],
  [*B2*], [], [The system issues a one-time sign-in state, binds it to this browser for five minutes, and sends the browser to Google.],
  [*B3*], [], [The visitor signs in at Google and consents.],
  [*B4*], [], [Google sends the browser back with an authorisation code and the state.],
  [*B5*], [], [The system consumes the state. It must exist, be unused and match this browser's binding.],
  [*B6*], [], [The system exchanges the code with Google and verifies the identity Google returns, including a verified e-mail address.],
  [*B7*], [], [The system finds the account bound to this Google identity, checks that it is active and records the sign-in.],
  [*B8*], [], [The system reads the account's roles and permissions and saves them with a new session that every service can read. Only then does the browser receive the session.],
  [*B9*], [], [The browser lands on the callback page, which reads the current user and returns the visitor to the page remembered in B1.],
  [*A1*], [B7], [No account is bound to this identity: the system registers an active account, binds the Google identity to it and grants the buyer role.],
  [*A2*], [A1], [Two first sign-ins of the same identity race: one registration wins; the other finds the binding and signs in to the same account as in B7.],
  [*A3*], [B9], [Later, the user signs out: this browser's session ends; sessions on other devices stay open.],
  [*A4*], [B9], [Later, a request without a session reaches a protected operation: it is refused as not signed in, and the page sends the visitor to sign in and back.],
  [*A5*], [B9], [Later, a signed-in user calls an operation that needs a role or permission the session does not hold: it is refused as access denied. The admin role is granted by an operator, never by a screen.],
  [*E1*], [B3], [The visitor cancels at Google, or Google returns an error: no session; the sign-in page shows the error.],
  [*E2*], [B5], [The state is missing, already used, expired or from another browser: no session.],
  [*E3*], [B6], [Google is unreachable, or the identity cannot be verified: no session.],
  [*E4*], [A1], [Another account already uses the same e-mail address: the sign-in is refused and the two accounts are never merged.],
  [*E5*], [B7], [The account is disabled: no session.],
  [*E6*], [B8], [The session cannot be saved, or a newer sign-in of the same account replaced this one: no session is issued, and the account's other sessions stay as they were.],
)

A3–A5 start "Later" because each is a separate use-case instance, not a branch of this scenario. They are drawn here because they consume the session that B8 creates, and no other use case owns them.

=== Analysis Class Diagram <sec-a-uc02-ac>

@fig-a-uc02-ac is the analysis model. The visitor uses `SignInUI` and, on later requests, `MemberAreaUI`; Google is reached only through the gateway boundary `IdentityProvider`. `SignInWithGoogleControl` realises UC2 and is the only object that talks to the boundaries and the entities. `Account`, `OAuthBinding` and `Role` are the system-level entities (@sec-a-reading). The one-time sign-in state and the session are not entities: they hold nothing beyond one interaction that the business needs, so the control only issues and checks the state (`issueState`, `checkState`) and opens the session (`openSession`). S6 introduces the stores that realise them. B8 and A5 ask `Role` the same question, `grantedTo(account)`; S6 answers A5 from a copy taken at B8, so that A5 needs no call to identity-svc. Compared with the SWE5006 model, `AuthControl` is renamed after the use case, `User` becomes `Account`, and `Role` gains `grantedTo` for B8.

#afig("a-uc02-ac", float: true, [UC2 analysis class diagram (UML class notation, BCE stereotypes). The visitor uses `SignInUI` and `MemberAreaUI`; Google is reached only through `IdentityProvider`. The control holds the sign-in state and the session itself; only `Account`, `OAuthBinding` and `Role` outlive the interaction.])

=== Analysis Sequence Diagrams <sec-a-uc02-as>

The basic flow is drawn in two parts: before the account is known (@fig-a-uc02-as-basic-1) and after (@fig-a-uc02-as-basic-2). Google sends the browser back, so the callback enters through `SignInUI` (`returned`), which hands it to the control (`completeSignIn`). The state is checked before the code is trusted, and no session exists until the state, Google's answer and the account have all been checked. @fig-a-uc02-as-basic-2 creates the new account and binding on lifelines of their own (A1); E4 refuses that creation when the e-mail address is already used, and the lost race (A2) is a note, because it repeats the "binding found" branch. @fig-a-uc02-as-access shows the three later flows A3–A5.

#afig("a-uc02-as-basic-1", float: true, [UC2 analysis sequence diagram, basic flow part 1 (B1–B6) with E1–E3. Each `break` ends the attempt without a session.])

#afig("a-uc02-as-basic-2", float: true, [UC2 analysis sequence diagram, basic flow part 2 (B7–B9) with A1, A2 and E4–E6. A first sign-in creates the account and its binding; the session is opened last, with the roles read in B8.])

#afig("a-uc02-as-access", float: true, [UC2 analysis sequence diagram, alternate flows A3–A5. Sign-out ends one session; access checks ask the roles, which the design reads from the session (S6).])

=== Transition: Strategies and Mapping <sec-a-uc02-transition>

Seven of the nine strategies of @sec-a-strategies change UC2; S7 and S9 do not:

- *S1 Language and framework (AD-01, AD-18; NFR-05, 06, 26):* impacted `Account`, `OAuthBinding` → resultant two aggregate roots, `User` and `OAuthIdentity` (which refers to its `User` by id), the enumerations `UserStatus`, `RegisterSource`, `OAuthProvider`, and nested records such as `OAuthIdentityGateway.OAuthProfile`; dynamics @fig-a-uc02-ds-register.
- *S2 Presentation (AD-09; NFR-07, 12, 16, 25):* impacted `SignInUI`, `MemberAreaUI` → resultant the routes `/login` and `/auth/callback`, the `_authenticated` guard, `GoogleSignInButton` and `GatedActionSheet`; not drawn.
- *S3 Distribution (AD-02, AD-06, AD-09; NFR-05, 07, 64, 66):* impacted `SignInWithGoogleControl` → resultant `AuthController` over `OAuthLoginAppService` in identity-svc, and three Sa-Token interfaces shared by all services. Roles cross the service boundary inside the session, not by a call: commerce-svc's `SessionStpInterface` reads them from Valkey, so A5 works while identity-svc is down; dynamics @fig-a-uc02-ds-access.
- *S4 Persistence (AD-03, AD-11; NFR-05, 13):* impacted `Account`, `OAuthBinding`, `Role` → resultant `UserRepository`, `UserOAuthRepository` → `UserOAuthRepositoryImpl` → `SysUserOAuthMapper`, `RoleRepository`; tables `sys_user`, `sys_user_oauth`, `rel_user_role` in identity's own database; one short transaction per sign-in in `OAuthUserTxService`, never across the Google call; dynamics @fig-a-uc02-ds-login, @fig-a-uc02-ds-register.
- *S5 External interface (backend ADRs 0001, 0004, 0005; NFR-21, NFR-22, NFR-23, NFR-31, NFR-50, NFR-66):* impacted `IdentityProvider` → resultant the port `OAuthIdentityGateway` and the adapter `GoogleOAuthGateway`, the only class that knows Google: it exchanges the code with bounded timeouts (3 s, 5 s) and verifies the ID token's signature, issuer, audience, expiry and verified e-mail; dynamics @fig-a-uc02-ds-signin.
- *S6 Security (AD-05, AD-09; NFR-01, 46, 47, 58, 68):* impacted `issueState`, `checkState`, `openSession`, `Role`, `checkAccess` → resultant the state store `OAuthStateStore` / `RedisOAuthStateStore` (300 s, `GETDEL`) with the `blocbuy-oauth-state` cookie; the session snapshot: `LoginSessionPublisher`, `LoginAuthorizationService` and `LoginSnapshotGateway` / `RedisLoginSnapshotGateway`, which publish the role snapshot before the `blocbuy-token` cookie (HttpOnly, SameSite=Lax, Secure on HTTPS deployments) is set; `SaTokenConfig` with `SaInterceptor`, and `StpInterface` realised by `StpInterfaceImpl` (identity-svc, database) or `SessionStpInterface` (other services, snapshot), chosen by `RbacAutoConfiguration` (@fig-a-str-s6-rbac); dynamics @fig-a-uc02-ds-session, @fig-a-uc02-ds-access.
- *S7 Scheduling (AD-16; NFR-15, 32, 61):* not applied; state and session expire by Valkey time-to-live, not by a job.
- *S8 Concurrency (AD-02, AD-03; NFR-13, 21, 27, 30):* impacted `OAuthBinding`, `Account`, `openSession` → resultant the unique indexes `uq_sys_user_oauth_provider_subject` and `uq_sys_user_email`, with `DuplicateKeyException` re-read as a login (A2); the refresh fence of `RedisLoginSnapshotGateway`, an atomic Valkey script that lets only the newest sign-in publish (E6). No row lock is taken. `registerNewUser` writes two aggregates, `User` and `OAuthIdentity`, plus the buyer role in one local transaction, so a half-registered account cannot exist; this follows the justification of @sec-a-s8 (one database, no lock, the unique indexes decide); dynamics @fig-a-uc02-ds-register, @fig-a-uc02-ds-session.
- *S9 Domain and integration events (AD-04; NFR-30, 39, 59, 60, 61, 63):* not applied; UC2 publishes no event.

#atable(
  [UC2 analysis → design mapping. Every analysis object of @fig-a-uc02-ac, and every design class of @fig-a-uc02-dc-signin to @fig-a-uc02-dc-access.],
  <tbl-a-uc02-map>,
  columns: (1.15fr, 0.6fr, 2.6fr, 0.55fr, 0.75fr),
  header: ([Analysis object], [Stereotype], [Design elements (layer, service)], [Strategies], [Figures]),
  [`SignInUI`], [boundary], [Frontend `/login`, `/auth/callback`, `GoogleSignInButton` (not drawn, S2). Adapter `AuthController`: `googleAuthorize`, `googleCallback`, `me`, `logout` (identity-svc).], [S2, S3], [@fig-a-uc02-dc-signin, @fig-a-uc02-dc-access],
  [`MemberAreaUI`], [boundary], [Frontend `_authenticated` guard and `GatedActionSheet` (not drawn). Every protected controller, here `AdminListingController` (commerce-svc).], [S2, S6], [@fig-a-uc02-dc-access],
  [`IdentityProvider`], [boundary], [Port `OAuthIdentityGateway`; adapter `GoogleOAuthGateway` (identity-svc).], [S5], [@fig-a-uc02-dc-signin],
  [`SignInWithGoogleControl` (sign-in, B1–B7, A1, A2)], [control], [Use case controller `OAuthLoginAppServiceImpl.createGoogleAuthorizeUrl` and `loginWithGoogle` behind `OAuthLoginAppService`; transactions in `OAuthUserTxService`.], [S1, S3, S4], [@fig-a-uc02-dc-signin],
  [`SignInWithGoogleControl` (session, B8)], [control], [Web-layer helper `LoginSessionPublisher.login` («utility») with `LoginAuthorizationService.snapshot` and `StpLogic`.], [S6], [@fig-a-uc02-dc-session],
  [`SignInWithGoogleControl` (access, A3–A5)], [control], [`StpUtil.checkLogin`, `checkRole`, `logout`, called by `SaInterceptor` (registered by `SaTokenConfig`) and controllers.], [S3, S6], [@fig-a-uc02-dc-access],
  [`OAuthBinding`], [entity], [Aggregate root `OAuthIdentity`, which refers to `User` by id; port `UserOAuthRepository`, adapter `UserOAuthRepositoryImpl`, mapper `SysUserOAuthMapper`, table `sys_user_oauth`.], [S1, S4, S8], [@fig-a-uc02-dc-account],
  [`Account`], [entity], [Aggregate root `User` with `UserStatus`; port `UserRepository`, table `sys_user`. Read by commerce-svc only through `UserAppService` (UC3, UC4).], [S1, S4, S8], [@fig-a-uc02-dc-account],
  [`Role`], [entity], [Port `RoleRepository`; tables `sys_role`, `sys_permission`, `rel_user_role`, `rel_role_permission`.], [S4], [@fig-a-uc02-dc-account, @fig-a-uc02-dc-session],
  [(none)], [—], [State store (S6, for `issueState` and `checkState`): port `OAuthStateStore`, adapter `RedisOAuthStateStore`; one Valkey key per state, cookie `blocbuy-oauth-state`.], [S6], [@fig-a-uc02-dc-signin],
  [(none)], [—], [Session snapshot (S6, for `openSession`, `signOut` and A5): the Sa-Token session in Valkey plus the role snapshot written by `LoginSnapshotGateway` / `RedisLoginSnapshotGateway`; read through `StpLogic`, `StpInterface` (`StpInterfaceImpl`, `SessionStpInterface`) and `SaTokenDao`.], [S3, S6, S8], [@fig-a-uc02-dc-session, @fig-a-uc02-dc-access],
  [(none)], [—], [Introduced by a strategy: `UserAppService` (S3, the current user for `me`). Not drawn: the nested records `OAuthLoginAppService.GoogleAuthorizeUrl`, `OAuthLoginAppService.OAuthLoginResult`, `OAuthStateStore.IssuedState`, `OAuthIdentityGateway.OAuthProfile`, `LoginAuthorizationService.Snapshot`, `UserAppService.CurrentUser`; `RbacAutoConfiguration` (in @fig-a-str-s6-rbac); `GlobalExceptionHandler`.], [S1, S3, S6], [@fig-a-uc02-dc-access],
)

`SignInWithGoogleControl` is realised in two phases: `OAuthLoginAppService` (B5–B7, application layer) and `LoginSessionPublisher` (B8, a «utility» in the web layer of `blocbuy-adapter`, because issuing the cookie through `StpLogic.setTokenValue` needs the HTTP response). `AuthController.googleCallback` orders the two phases and owns the redirect contract; it is the one piece of flow logic in the boundary, accepted so that no application class depends on the servlet API (S2, S6). `LoginSessionPublisher` implements no port, so it is drawn as a «utility», a web-layer helper.

=== Design Class Diagrams <sec-a-uc02-dc>

The design is drawn in four slices: the sign-in in identity-svc (@fig-a-uc02-dc-signin), the account and its binding (@fig-a-uc02-dc-account), the opening of the shared session (@fig-a-uc02-dc-session) and the checks on later requests in every service (@fig-a-uc02-dc-access). `UserOAuthRepository` → `UserOAuthRepositoryImpl` → `SysUserOAuthMapper` is the part's representative persistence chain; `UserRepository` and `RoleRepository` follow it. Five Sa-Token types are drawn because S6 realises the session through them: `StpUtil` (a framework class of static operations that delegates to `StpLogic`, so it carries only the tag «Sec», as the notation key in @sec-a-reading-notation prescribes), `StpLogic`, `StpInterface`, `SaTokenDao` and `SaInterceptor`; Spring and other library types stay out. `User` and `OAuthIdentity` are separate aggregates, each with its own repository. «static» marks a static operation. A class in two slices shows only the members that slice uses.

#afig("a-uc02-dc-signin", float: true, [UC2 design class diagram, slice 1: sign-in (UML, identity-svc). Google and Valkey sit behind two domain ports; the application service is not transactional and leaves account writes to `OAuthUserTxService`.])

#afig("a-uc02-dc-account", float: true, [UC2 design class diagram, slice 2: account and binding (UML, identity-svc). `OAuthUserTxService` is the only class that opens a database transaction, one per sign-in, never around a Google call.])

#afig("a-uc02-dc-session", float: true, [UC2 design class diagram, slice 3: opening the shared session (UML, identity-svc writing to the shared Valkey). `LoginSessionPublisher` sets the browser's token only after the role snapshot is published.])

#afig("a-uc02-dc-access", float: true, [UC2 design class diagram, slice 4: checks on later requests (UML, every service). `StpUtil` delegates to `StpLogic`, which asks the configured `StpInterface`: identity-svc reads the database, commerce-svc and notification-svc read the session snapshot.])

=== Design Sequence Diagrams <sec-a-uc02-ds>

Each design sequence retells part of @tbl-a-uc02-flows; the notes carry the step tags, and a `critical` fragment is a database transaction. Both OAuth endpoints always answer with a redirect: success goes to `/auth/callback`, every failure to `/login?error=<code>`. @fig-a-uc02-ds-signin covers the redirect, the state and Google, outside any transaction. @fig-a-uc02-ds-login and @fig-a-uc02-ds-register are the two branches of B7: a returning account and a first sign-in with its race. @fig-a-uc02-ds-session publishes the roles before the cookie, and @fig-a-uc02-ds-access shows the current user, sign-out and the two refusals.

#afig("a-uc02-ds-signin", [UC2 design sequence diagram, B1–B6 with E1–E3 (identity-svc, Google). The state is consumed before Google is called, and no transaction is open during the call.])

#afig("a-uc02-ds-login", [UC2 design sequence diagram, B7 with E5: a returning account (identity-svc). The account check and both login stamps run in one short transaction.])

#afig("a-uc02-ds-register", [UC2 design sequence diagram, A1 with A2 and E4: a first sign-in (identity-svc). The unique indexes decide a race; the loser re-reads the binding and signs in, or is refused if only the e-mail clashes.])

#afig("a-uc02-ds-session", [UC2 design sequence diagram, B8 with E6 (identity-svc, Valkey). The refresh fence lets only the newest sign-in publish; a failed publish removes its own token and leaves the account's other sessions alone.])

#afig("a-uc02-ds-access", [UC2 design sequence diagram, B9 and A3–A5 (identity-svc and commerce-svc). In commerce-svc the role check reads the snapshot from the shared session store and never calls identity-svc.])

=== Operation Specifications <sec-a-uc02-ops>

#atable(
  [UC2 operation specifications (identity-svc unless stated). Errors are `BizException` error codes unless named as another exception; every error of the callback reaches the browser as a 302 to `/login?error=<code>`.],
  <tbl-a-uc02-ops>,
  columns: (1.45fr, 0.8fr, 0.85fr, 1.3fr, 1fr),
  header: ([Operation; transaction], [Purpose], [Preconditions], [Postconditions and effects], [Errors]),
  [`AuthController.googleCallback(code: String, state: String, error: String, stateBinding: String): ResponseEntity<Void>`. No transaction.], [Entry for B4–B9.], [Public path; `stateBinding` from the cookie.], [302 to `/auth/callback` with the session cookie, or 302 to `/login?error=<code>` with none.], [`OAUTH_ACCESS_DENIED` (`IDENTITY_401_003`) or `OAUTH_PROVIDER_ERROR` for `error`; `BAD_REQUEST` (`COMMON_400_001`); `INTERNAL_ERROR` (`COMMON_500_001`) for any other exception.],
  [`OAuthLoginAppServiceImpl.loginWithGoogle(code: String, state: String, stateBinding: String): OAuthLoginAppService.OAuthLoginResult`. Not transactional.], [B5–B7, A1, A2.], [—], [State consumed; account found or registered; result carries the user id.], [`OAUTH_STATE_INVALID` (`IDENTITY_401_001`); `OAUTH_PROVIDER_ERROR`, `OAUTH_TOKEN_INVALID`; `ACCOUNT_DISABLED`; `IDENTITY_MISCONFIGURED`; `EMAIL_ALREADY_REGISTERED` (`IDENTITY_409_001`); the `DuplicateKeyException` rethrown when neither index explains the conflict.],
  [`GoogleOAuthGateway.exchangeCodeForProfile(code: String): OAuthIdentityGateway.OAuthProfile`. Outside any transaction.], [B6.], [Client configured.], [Profile of a verified Google identity with a verified e-mail.], [`OAUTH_PROVIDER_ERROR` (`IDENTITY_502_001`) for network or configuration; `OAUTH_TOKEN_INVALID` (`IDENTITY_401_002`).],
  [`OAuthUserTxService.registerNewUser(profile: OAuthIdentityGateway.OAuthProfile): OAuthLoginAppService.OAuthLoginResult`. `@Transactional` (REQUIRED).], [A1. Its sibling `recordLogin(identity: OAuthIdentity, profile: OAuthIdentityGateway.OAuthProfile)` serves B7 the same way.], [No binding for the subject.], [One `sys_user`, one `sys_user_oauth`, one `rel_user_role` (buyer) row, or none. `recordLogin`: login times stamped.], [`IDENTITY_MISCONFIGURED` (`IDENTITY_500_001`); `DuplicateKeyException` on either unique index; `recordLogin`: `ACCOUNT_DISABLED` (`IDENTITY_403_001`).],
  [`LoginSessionPublisher.login(userId: UUID): void`. Snapshot in a read-only `REPEATABLE READ` transaction, 3 s.], [B8.], [Account verified active.], [Token session and role snapshot in Valkey; then the token cookie. On failure only this token is removed.], [`IllegalStateException` when the fence was superseded or expired (E6); Valkey errors propagate.],
  [`SessionStpInterface.getRoleList(loginId: Object, loginType: String): List<String>` (commerce-svc, notification-svc). No database.], [A5.], [—], [Roles of the snapshot, cached per request so role and permission checks see one snapshot.], [None: a missing or malformed snapshot returns an empty list, so `checkRole` denies (`FORBIDDEN`, `COMMON_403_001`).],
)

=== State Machines <sec-a-uc02-states>

No statechart: `UserStatus` has two values, registration creates `ACTIVE` and UC2 only checks it (`User.ensureActive`); the sign-in state and the session are S6 mechanisms that exist until consumed, ended or expired.

=== Patterns Applied <sec-a-uc02-patterns>

`OAuthStateStore`, `LoginSnapshotGateway` and the repositories follow Ports and Adapters, as every use case does (@sec-a-strategies); two further patterns answer UC2's own problems.

#atable(
  [Patterns in the UC2 design: the problem first, then the roles mapped to real classes, then the consequences.],
  <tbl-a-uc02-patterns>,
  columns: (0.8fr, 1fr, 1.2fr, 1fr),
  header: ([Pattern (kind)], [Problem in UC2], [Roles → classes], [Consequences]),
  [Anti-Corruption Layer (DDD, architectural)], [Google's model (ID tokens, `invalid_grant`, SDK types) must not leak into the identity domain.], [Domain port: `OAuthIdentityGateway`, speaking `OAuthIdentityGateway.OAuthProfile`. Translator: `GoogleOAuthGateway` (`exchangeCode`, `verify`). Collaboration: @fig-a-uc02-dc-signin, @fig-a-uc02-ds-signin.], [One class changes if Google changes or a provider is added. Liability: a new claim needs a new profile field.],
  [Strategy (GoF, behavioural), realised by configuration], [Only identity-svc may read the role tables (AD-03), yet one `checkRole` must work in every service.], [Context: Sa-Token's `StpLogic`, reached through the static facade `StpUtil`. Strategy: `StpInterface`. Concrete strategies: `StpInterfaceImpl` (database) and `SessionStpInterface` (session snapshot). `RbacAutoConfiguration` selects one at start-up, and Sa-Token's `SaManager` holds it. Collaboration: @fig-a-uc02-dc-access, @fig-a-uc02-ds-access.], [One check in both topologies. Liability: outside identity-svc a new role takes effect at the next sign-in.],
)

=== Flow-to-Design Trace <sec-a-uc02-trace>

#atable(
  [UC2 flow-to-design trace: each flow step, its analysis message and the design operations that realise it.],
  <tbl-a-uc02-trace>,
  columns: (0.5fr, 1fr, 2.4fr, 0.8fr),
  header: ([Step], [Analysis message], [Design operations], [Figures]),
  [B1], [`signIn`, `startSignIn`], [`AuthController.googleAuthorize` → `OAuthLoginAppServiceImpl.createGoogleAuthorizeUrl`], [@fig-a-uc02-ds-signin],
  [B2], [`issueState`; `authorize`], [`OAuthStateStore.issueState`; `OAuthIdentityGateway.buildAuthorizeUrl`; 302 with `blocbuy-oauth-state`], [@fig-a-uc02-ds-signin],
  [B3, B4], [`returned`, `completeSignIn`], [Google; `AuthController.googleCallback` → `loginWithGoogle`], [@fig-a-uc02-ds-signin],
  [B5], [`checkState`], [`OAuthStateStore.consume` (`GETDEL`)], [@fig-a-uc02-ds-signin],
  [B6], [`IdentityProvider.verify`], [`GoogleOAuthGateway.exchangeCodeForProfile`, `exchangeCode`, `verify`], [@fig-a-uc02-ds-signin],
  [B7], [`findBySubject`, `ensureActive`, `touchSignIn`], [`UserOAuthRepository.findActiveByProviderAndSubject`; `OAuthUserTxService.recordLogin` → `User.ensureActive`, `touchLogin`, `updateLastLogin`, `OAuthIdentity.touchLogin`, `updateLoginSnapshot`], [@fig-a-uc02-ds-login],
  [B8], [`Role.grantedTo`; `openSession`], [`LoginSessionPublisher.login` → `beginRefresh`, `LoginAuthorizationService.snapshot` (two `RoleRepository` reads), `StpLogic.createTokenValue`, `createLoginSession`, `publish`, `setTokenValue`], [@fig-a-uc02-ds-session],
  [B9], [`showSignedIn`], [302 `/auth/callback`; `AuthController.me` → `StpUtil.getLoginIdAsString`, `UserAppService.currentUser`, `StpUtil.getRoleList`], [@fig-a-uc02-ds-session, @fig-a-uc02-ds-access],
  [A1], [«create» `Account`, «create» `OAuthBinding`, `grantTo`], [`OAuthUserTxService.registerNewUser` → `User.registerViaOAuth`, `OAuthIdentity.bind`, two `saveNew`, `RoleRepository.assignRole`], [@fig-a-uc02-ds-register],
  [A2], [as B7], [`DuplicateKeyException` → `resolveRegisterRace` → `findActiveByProviderAndSubject` → `recordLogin`], [@fig-a-uc02-ds-register],
  [A3], [`signOut`], [`AuthController.logout` → `StpUtil.logout`], [@fig-a-uc02-ds-access],
  [A4], [`checkAccess`, `offerSignIn`], [`SaInterceptor.preHandle` → `StpUtil.checkLogin` → `NotLoginException` → `UNAUTHENTICATED` (`COMMON_401_001`)], [@fig-a-uc02-ds-access],
  [A5], [`Role.grantedTo`, `showAccessDenied`], [`AdminListingController.administrator` → `StpUtil.checkRole` → `StpLogic.checkRole` → `SessionStpInterface.getRoleList` → `SaTokenDao.getSession`; `NotRoleException` → `FORBIDDEN` (`COMMON_403_001`)], [@fig-a-uc02-ds-access],
  [E1–E3], [`showFailure`], [E1: `googleCallback` with `error` or without code or state. E2: `consume` false → `OAUTH_STATE_INVALID`. E3: `OAUTH_PROVIDER_ERROR` or `OAUTH_TOKEN_INVALID` from `GoogleOAuthGateway`], [@fig-a-uc02-ds-signin],
  [E4–E6], [`showFailure`], [E4: insert refused by `uq_sys_user_email` → `resolveRegisterRace` → `findActiveByEmail` present → `EMAIL_ALREADY_REGISTERED`. E5: `User.ensureActive` → `ACCOUNT_DISABLED`, rolled back. E6: `publish` false or an exception → `StpLogic.logoutByTokenValue` → `INTERNAL_ERROR`], [@fig-a-uc02-ds-register, @fig-a-uc02-ds-login, @fig-a-uc02-ds-session],
)
