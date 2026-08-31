/**
 * Payment domain events (e.g. PaymentSucceeded, PaymentFailed). In-process only — there is no
 * message broker in the stack; cross-aggregate reactions run in the same local transaction or via
 * idempotent scheduled jobs with database state as the backstop.
 */
package com.blocbuy.payment.event;
