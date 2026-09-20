# Stripe local-interface removal

20 September 2026. Requested by Yorick before merging into `dev`.

## Removed from the application

- `/local/stripe/*`, including fixture login, operator checkout and refund reconciliation HTTP routes.
- The `stripe-local-adapter` configuration, expiry scheduler and local refund worker.
- Local runtime, seeding, browser/Sandbox verification and operator scripts that called those routes.
- The extra Vite proxy and `dev:stripe-local` command. The team's original proxy remains unchanged.

The order/intent and expiry substitutes now live under bootstrap `src/test/java/.../fixture`.
Their SQL lives under `src/test/resources/db/stripe-fixture`. Tests import the configuration explicitly;
there are no HTTP controllers or scheduled jobs in these fixtures, and Maven excludes them from the
application JAR. Existing payment, concurrency, rollback and refund tests still exercise them.
This retains integration evidence without shipping replacement order/intent implementations.

The production `PaymentBusinessPort` and `PaymentIntentPort` contracts remain. Removing them would
break Checkout orchestration. Team implementations must supply them before `stripe-sprint2` can be
enabled. Payment remains off by default; this cleanup does not complete shared-environment integration.
Refund execution remains in `RefundService`; the team must wire its worker and expiry notification.

## Preservation and verification

The source/contract guard checks the original 192 Java/configuration/DDL files, 13 HTTP paths and
23 schemas against the supplied dev snapshot `211cfb7`. No original configuration or DDL was edited.
The browser also showed newer dev commit `4d25dea`: logout changes in six frontend/evidence files.
Its merge result and CI must still be verified before merging; an old-baseline guard alone is not enough.

Publication is blocked: Git CLI has no credentials, the connector cannot access the private repository,
and Chrome rejected the file-chooser upload (`Not allowed`). No files were committed to GitHub and
no merge was performed. Browser upload permission or an authenticated Git client is required.

Before removal, `bash scripts/test-stripe-sprint2.sh` passed. Post-removal verification is recorded
in the sprint log. The regression checks that no `/local/stripe` handler is registered, both with
the default profile and when payment is enabled for the isolated database tests.

Older files in this directory describe historical local Sandbox runs. Their commands were retired
by this change and must not be used as current deployment instructions.

## Recovery

The local runtime was stopped before removing its launch scripts; its database and private logs
were retained outside the repository. A source backup was saved outside the repository before
the removal. Restoring a test harness must not enable it in shared deployment.
