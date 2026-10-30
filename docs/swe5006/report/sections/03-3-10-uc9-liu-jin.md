<!-- report
rubric: 3.3.10
owner: Liu Jin
plan: #677
sources: backend/docs/design/admin-listings.md; UC9 issue (#32) and team terms extension (#221); AdminListingController, AdminListingAppServiceImpl, ProductStatus, TeamTermsAppService, CatalogErrorCode; frontend admin console (frontend/src/routes/_authenticated/admin/, features/admin/; frontend ADR 0015)
owns: none
status: draft
-->

### 3.3.10 UC9 Admin Listing Management

An administrator prepares, publishes, corrects and withdraws listings, and sets the team terms and team price that let buyers start teams on them. Every change is checked against the listing's lifecycle state and against the version the administrator last read, so two administrators cannot silently overwrite each other and commercial history is never deleted. Administrators work in an admin console in the web application (changes from the proposal: Table 2.5).

**Actors:** Admin (primary; a signed-in account that holds the admin role and the permission to change listings).

**Preconditions:** The admin is signed in (UC2), and an operator has granted the account the admin role. For an existing listing, the admin has read its current version.

**Trigger:** The admin opens the admin console and chooses to create, edit, publish, delist or delete a listing, or to set its team terms.

**Normal flow, prepare and publish a listing:**

1. The admin opens the console. The system checks the admin role and the permission to change listings on every action.
2. The console shows counts of listings per state and of open teams, and a table of listings filtered by state.
3. The admin enters a new listing: title, subtitle, description, images, category, individual price in Singapore dollars and stock, with an optional listing number.
4. The system validates the input and stores the listing as a *draft*, its first version, with a generated number when none was given. Drafts are invisible to buyers.
5. The admin edits the draft and saves it with the version they read. The system checks that the version is still current, applies the change and records a new version.
6. The admin publishes the listing. The listing's state allows draft → *on sale*; the listing now appears in UC1.
7. The admin sets the team terms, a target size of at least two and a team lifetime between 40 minutes and 24 hours, and optionally a team price. The system validates terms and price as one rule set and saves both together, or neither; the terms change is recorded for audit and the price becomes a new revision of the listing's team price policy.
8. The listing can now start teams (UC3).

**Alternative flows:**

- 5a. Edit a published listing: content such as text and images may change, but the price, currency and stock may not, because changing them would reprice or restock accepted purchases.
- 6a. Delist: the admin withdraws an on-sale listing. It becomes *off sale*: hidden from browsing and from new quotes, while its teams, orders and history stay. Publishing it again returns it to *on sale* with the same identity.
- 7a. Change the terms of a listing whose teams have already started: the started teams keep the terms they were started with; only new teams use the new terms.
- D1. Delete: the admin deletes a draft that was never published and that no team price revision, quote or team refers to. The system marks it deleted; nothing is erased.

**Exceptional flows:**

- 1a. The account is signed in but is not an admin: every admin action is refused, and the console shows "Access denied". Without a sign-in the action is refused and the console sends the visitor to sign in.
- 4a. A field is missing or out of range: the system lists the field errors and stores nothing.
- 4b. The listing number given is already used by another listing: the system refuses it and stores nothing.
- 5b. Another admin saved the listing after this admin read it: the version check fails and nothing changes (Optimistic Offline Lock, section 3.5.5). The console shows a conflict banner whose only action is to reload the current version.
- 5c. The edit of a published listing changes its price, currency or stock: the system refuses it as not allowed for an on-sale listing.
- 6b. The change of state is not allowed in the current state, for example publishing a listing that is already on sale, delisting a draft or returning a published listing to draft: the system refuses it and the state does not change.
- 7b. The terms are out of range, or the team price is not below the individual price or its validity window is too short: the system lists the field errors. A team price for a listing that is not on sale is refused. Terms or a price saved by someone else after this admin read them are refused as stale. Any rejection changes nothing.
- D1a. The listing to delete has been published or is referred to by a team price revision, quote or team: the system refuses and deletes nothing.
- A1. The listing does not exist: the system answers "not found".

**Postconditions:** Each accepted change records exactly one new version of the listing. Buyers see a listing only while it is on sale. A rejected change leaves the stored version unchanged. No published listing, and no listing that history refers to, is ever deleted.

**Analysis objects:** admin console (listing table, listing editor, team terms panel) «boundary»; listing management control and team terms control «control»; listing with its lifecycle state, team terms, team price policy revision and audit record «entity».
