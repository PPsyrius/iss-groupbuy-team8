// Appendix A: the two-buyer walkthrough, screen by screen, from the 24 October recording (#657).
// Included after chapter 6. The heading is unnumbered (the running head reads "Appendix A | ...");
// the set rules below number its sections "A.1" ... and its figures and tables "A.1" ..., and
// apply only inside this file. lib.typ resets the figure and table counters at every level-1
// heading, this one included.
#import "../lib.typ": *

// A cross-reference to a label elsewhere in the report. The appendix preview
// (appendix-preview.typ) builds this file alone, where those labels do not exist, so it prints
// the fallback text there; in the full report it is an ordinary reference.
#let xref(target, fallback) = context {
  if query(target).len() > 0 { ref(target) } else { fallback }
}

// A screenshot at the text width, or narrower when that would make it taller than max-height,
// so that two figures fit on one page.
#let shot(file, alt, max-height: 8.4cm) = layout(size => {
  let path = "/docs/swe5001/report/figures/walkthrough/" + file
  let natural = measure(image(path))
  let width = calc.min(size.width, max-height * (natural.width / natural.height))
  image(path, width: width, alt: alt)
})

#heading(level: 1, numbering: none, outlined: true, "Appendix A: Walking through BlocBuy as two buyers")

// The unnumbered heading above does not step the heading counter, so the sections below would
// continue chapter 6's count; keep only its first level, then number them "A.1", "A.2" ...
#counter(heading).update((..n) => (n.pos().at(0, default: 0),))
#set heading(numbering: (..n) => "A." + str(n.pos().last()))
#set figure(numbering: (..n) => "A." + n.pos().map(str).join("."))

This appendix walks through the journey of §3.2.4 one screen at a time: a leader starts a team for a two-seat listing, a member joins through the invite link and pays, and the team fills. Every step has a row in @tbl-a-journey, and a step with no screen says so.

== How to read this appendix

The screens come from a recording of 24 October 2026 (#iss(657)). Both payments ran in Stripe test mode, so no real money moved, and the two test accounts' names are blurred.

Each caption names the buyer and the use-case step. UC4 steps are those of #xref(<tbl-uc4-flow>)[the UC4 flow-of-events table in §3.2.4]\; other steps (B1, B2 and so on) are from the detailed design source book. @tbl-a-journey also gives each screen's row of the two-buyer table (#xref(<tbl-two-buyer-walkthrough>)[the two-buyer walkthrough table], §3.2.4) and marks steps the recording does not show as "Not recorded".

#figure(
  table(
    columns: (0.35fr, 0.65fr, 2.2fr, 1.05fr, 0.75fr, 1.05fr),
    table.header([Step], [Buyer], [What happens], [Use-case step], [§3.2.4 row], [Screen]),
    [1], [Leader], [Signs in with Google and opens the two-seat listing], [UC3 precondition (UC2)], [Start], [Not recorded; the listing as the member saw it later in @fig-a-listing],
    [2], [Leader], [Presses "Start a bloc · S\$22.00" and sees the quote with its countdown], [UC3 B1–B4], [Start], [Not recorded; the button is in @fig-a-listing],
    [3], [Leader], [Pays S\$22.00 on Stripe Checkout in test mode], [UC3 B5–B8], [Start], [Not recorded; same page in @fig-a-stripe-checkout],
    [4], [Leader], [Sees "Payment received", paid at 19:10 SGT, with the "Bloc reference"], [UC3 B13], [Start], [@fig-a-leader-paid],
    [5], [Member], [Sees the team open with one of its two seats taken: "1 of 2 joined", "1 seat left"], [UC3 B10], [Start], [@fig-a-listing],
    [6], [Leader], [Copies or shares the invite link], [UC3 B13, UC7 B1–B4], [Invite], [Not recorded; result in @fig-a-invite-signed-out],
    [7], [Member], [Opens the invite link while signed out; the banner names the leader and offers "Join this bloc · S\$22.00"], [UC7 B5–B6, UC4 step 1 (opening the link)], [Invite], [@fig-a-invite-signed-out],
    [8], [Member], [Is asked to sign in with Google, then chooses a Google account], [UC7 B7 (UC2)], [Join], [@fig-a-sign-in; the account chooser: not recorded],
    [9], [Member], [Returns signed in to the listing and presses "Join this bloc · S\$22.00"], [UC4 steps 1–2, UC7 B7], [Join], [@fig-a-invite-signed-in],
    [10], [Member], [Pays "SGD 22.00" on Stripe Checkout in test mode with the test card 4242], [UC4 step 3], [Join], [@fig-a-stripe-checkout],
    [11], [Member], [Sees "Payment received", paid at 19:17 SGT, with the same "Bloc reference" as step 4], [UC4 steps 3–8], [Join], [@fig-a-member-paid],
    [12], [Member], [Sees the team "2 of 2 joined", "Filled" and their own order "Confirmed"], [UC4 step 8, UC8 B4], [Join, Own views], [@fig-a-member-my-blocs],
    [13], [Leader], [Sees the team "2 of 2 joined" under Completed and their own order "Confirmed" (watching it fill was not recorded)], [UC4 step 8, UC8 B4], [Join, Own views], [@fig-a-leader-my-blocs; the fill itself: not recorded],
    [14], [Leader], [Two other teams, closed unfilled on 23 Oct: the Refunded tab and a "Refund sent" receipt from the same account], [UC5 B7–B9, UC6 B9, UC8 B4], [None], [@fig-a-refunded-tab, @fig-a-refund-sent],
  ),
  kind: table,
  caption: [Steps of the two-buyer journey with their use-case steps, §3.2.4 rows and screens. Source: the 24 October recording (\#657); use-case steps from the detailed design source book and the UC4 flow-of-events table.],
) <tbl-a-journey>

== The leader starts a team

The leader's part of the Start row (UC3) shows only its result. The leader's sign-in, the press of "Start a bloc · S\$22.00" and the quote with its countdown (UC3 B1–B4) were not recorded. Nor was the leader's Stripe page (UC3 B8); it is the same Stripe Checkout page that the member used (@fig-a-stripe-checkout). @fig-a-listing shows the listing as the member saw it before signing in, while the leader's team was open, and @fig-a-leader-paid shows the leader's receipt.

#figure(
  kind: image,
  shot("a01-listing.png", "BlocBuy listing page for the Universal Travel Adapter: the team price S$22.00 next to S$29.00 struck through, 2 seats per bloc, a Start a bloc button and one open team with 1 of 2 joined and 1 seat left."),
  caption: [The listing page while the leader's team was open, seen by the member before signing in. The team price "S\$22.00" sits against "S\$29.00" struck through, with "2 seats per bloc", "Start a bloc" and the open team's "1 of 2 joined" and "1 seat left".],
) <fig-a-listing>

#figure(
  kind: image,
  shot("a02-leader-paid.png", "The leader's receipt page: Paid chip, Payment received, S$22.00 paid, Paid 24 Oct, 7.10 pm, and the payment, order and bloc references."),
  caption: [The leader's receipt after paying to start the team (UC3 B13). It reads "Payment received", "S\$22.00 paid" and "Paid · 24 Oct, 7.10 pm", with the "Bloc reference" that @fig-a-member-paid repeats.],
) <fig-a-leader-paid>

== The leader invites the member

The leader copying or sharing the invite link (UC3 B13, UC7 B1–B4) was not recorded, and the link itself never appears on screen. @fig-a-invite-signed-out shows its result in the Invite row. The member opens the link while signed out (UC7 B5–B6), and the listing page greets them with a banner that names the leader.

#figure(
  kind: image,
  shot("a03-member-invite-signed-out.png", "The listing page opened from the invite link while signed out: a banner says the leader started this bloc and invited you to join it, with a Join this bloc S$22.00 button; the leader's name and avatar are blurred."),
  caption: [The member opens the invite link while signed out (UC7 B5–B6; UC4 step 1, opening the link). The banner reads "… started this bloc and invited you to join it", with the leader's name blurred, and offers "Join this bloc · S\$22.00".],
) <fig-a-invite-signed-out>

== The member joins and pays

The member's part of the Join row (UC4) was recorded from the invite banner to the receipt. Joining asks the member to sign in first (@fig-a-sign-in); the Google account chooser that follows is cut from the clip. Back on the listing, signed in, the member presses "Join this bloc · S\$22.00" (@fig-a-invite-signed-in) and pays on Stripe Checkout (@fig-a-stripe-checkout). The receipt (@fig-a-member-paid) carries the "Bloc reference" `01a16d99-f41b-7307-90cb-c8667dc3cc4c`, the same as the leader's in @fig-a-leader-paid. The member therefore joined the leader's team rather than starting a new one.

#figure(
  kind: image,
  shot("a04-member-sign-in.png", "Sign-in page: Back to the listing, Sign in to hold your seat, Continue with Google, and a note on why BlocBuy asks the buyer to sign in."),
  caption: [The member is asked to sign in before joining (UC7 B7). The page reads "Sign in to hold your seat" and offers "Continue with Google"; the Google account chooser was not recorded.],
) <fig-a-sign-in>

#figure(
  kind: image,
  shot("a05-member-invite-signed-in.png", "The same listing page with the invite banner after sign-in: the banner now says You'll pay the bloc price now, with the Join this bloc S$22.00 button; the leader's name and avatar in the banner are blurred."),
  caption: [The member returns signed in to the invite banner (UC4 steps 1–2). The banner now reads "You'll pay the bloc price now. If the bloc doesn't fill, you're refunded in full."],
) <fig-a-invite-signed-in>

#figure(
  kind: image,
  shot("a06-stripe-checkout.png", "Stripe Checkout in the Chinese locale with the test-mode badge, showing BlocBuy group purchase, SGD 22.00, a masked email and the Stripe test card number 4242 4242 4242 4242."),
  caption: [The member pays on Stripe Checkout in test mode (UC4 step 3). Stripe shows the page in the Chinese locale, where a Chinese-language badge marks test mode, with "SGD 22.00" and the public test card 4242 4242 4242 4242.],
) <fig-a-stripe-checkout>

#figure(
  kind: image,
  shot("a07-member-paid.png", "The member's receipt page: Paid chip, Payment received, S$22.00 paid, Paid 24 Oct, 7.17 pm, and the payment, order and bloc references."),
  caption: [The member's receipt after paying to join (UC4 steps 3–8). It reads "Payment received" and "Paid · 24 Oct, 7.17 pm", and its "Bloc reference" matches @fig-a-leader-paid, so the member joined the leader's team.],
) <fig-a-member-paid>

== The team fills and each buyer sees their own view

The member's payment filled the last seat (UC4 step 8), and each buyer's "My blocs" page (UC8 B4) then showed the result. @fig-a-member-my-blocs is a screenshot of the member's page taken after the fill and shown in the recording. @fig-a-leader-my-blocs is the leader's page, recorded live. The leader watching the team fill was not recorded.

#figure(
  kind: image,
  shot("a08-member-my-blocs.png", "The member's My blocs page: the Universal Travel Adapter team with 2 of 2 joined and Filled 24 Oct, 7.17 pm, and, below a grey band that replaces two other teams' cards, the member's order under My orders marked Confirmed."),
  caption: [The member's "My blocs" page after the team filled (UC4 step 8, UC8 B4). The team reads "2 of 2 joined" and "Filled 24 Oct, 7.17 pm" and the order "Confirmed", in two parts of one screenshot joined at a grey band that replaces two other teams' cards.],
) <fig-a-member-my-blocs>

#figure(
  kind: image,
  shot("a09-leader-my-blocs-completed.png", "The leader's My blocs page on the Completed tab: the Universal Travel Adapter team marked You started this, 2 of 2 joined and Filled 24 Oct, 7.17 pm, and under My orders the leader's order marked Confirmed."),
  caption: [The leader's "My blocs" page on the Completed tab (UC4 step 8, UC8 B4). The team marked "Filled 24 Oct, 7.17 pm" reads "2 of 2 joined", and the leader's own order, paid at 19:10 SGT, reads "Confirmed".],
) <fig-a-leader-my-blocs>

Both pages show the team's shared status, "2 of 2 joined", and each buyer's own order "Confirmed". Neither "My orders" list shows the other buyer's order: the member's carries order BB202610240563C8F61150 and the leader's BB2026102489EBCD2C4819. These screens support the two claims of the two-buyer table that rested on the UI: both orders are confirmed (row Join), and neither buyer's page shows the other's order (row Own views).

== When a team does not fill

When a team's deadline passes before it fills, UC5 closes it and UC6 refunds every paid member; this path is not a row of the two-buyer table. The leader's Refunded tab shows two other teams of the same product that closed on 23 October with "refund due" (@fig-a-refunded-tab), and the same account holds a "Refund sent" receipt (@fig-a-refund-sent). The screens show neither which team that receipt belongs to nor whether the refunds due were paid.

#figure(
  kind: image,
  shot("a10-refunded-tab.png", "The leader's My blocs page on the Refunded tab: two Universal Travel Adapter teams closed on 23 Oct with 1 of 2 joined when it closed and refund due."),
  caption: [The leader's Refunded tab for two other teams that closed unfilled on 23 October (UC5 B7–B9, UC8 B4). Each reads "1 of 2 joined when it closed" and "refund due".],
) <fig-a-refunded-tab>

#figure(
  kind: image,
  shot("a11-refund-sent.png", "The leader's receipt page for a refunded payment: Refunded chip, Refund sent, Refund sent to your original payment method, S$22.00 refunded, and the payment, order and bloc references.", max-height: 7cm),
  caption: [The leader's receipt for a refunded payment (UC6 B9). It reads "Refund sent to your original payment method" and "S\$22.00 refunded"; its "Bloc reference" is not the 24 October team's, and it shows no date.],
) <fig-a-refund-sent>
