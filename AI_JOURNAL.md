# AI Working Journal

Tools: Claude (chat) for understanding the brief, planning and writing prompts.
Claude Code (in VS Code) for building each step.

Note: I used Claude to help me draft the first few entries from our chat
history, then edited them. From Step 2 onwards I'm writing them as I go.

**Work started: Sat 21 Nov, 4:00pm**

(Before that: on Friday night I read the brief and asked Claude to explain it,
whether Claude Code was allowed, and how to submit. Understanding only, no
building, so I didn't count it in my time.)

---

## Sat 21 Nov

### 16:00 Understanding the task + starting
The brief is very open, so I went back over what it's actually asking. Two
user types (claimants and claims staff), one claim lifecycle, and they're
grading my decisions and how I use AI more than how much I build.
- My take: At first I thought I had to build the whole platform, which felt
  impossible in 3 hours. Once I understood they want a small slice done well
  and care more about my decisions, it felt manageable. Main worry is
  explaining things like Kafka that I haven't used much before.

### 16:13 Roadmap + backend choice
Asked Claude to act as my roadmap and give me prompts step by step that I
paste into Claude Code, so I can review each step before moving on.
I'm not strong in either .NET or Spring Boot, so I asked it to pick. It
suggested Spring Boot.
- Accepted. Reason: Spring Boot was easier to set up quickly, has a lot of
  tutorials and examples, and good Kafka support built in. Since I'm new to
  both, I picked the one where help is easiest to find if I get stuck.

### 16:20 to 16:50 Setup pain
Not really AI decisions but it took time: Java wasn't on PATH after install,
Docker needed WSL, and Claude Code installed but wasn't on PATH either.
Fixed each one by pasting the errors to Claude.

### 16:30 Checked the plan against the brief myself
Went back to the "Technology" section of the brief and asked if the plan
covered everything. Caching and API tooling weren't decided.
- Added Swagger (springdoc) so the API has a clear contract and I can demo
  endpoints easily.
- Decided on NO caching on purpose. Small data, Postgres is fine, and caching
  claim status could show stale info. Would consider Redis later for the
  exposure dashboard.
- My take: Agreed with both. Swagger is useful for me too, since I can test
  the API without the frontend. Not adding caching felt odd at first, but
  leaving it out and explaining why is better than adding something I don't
  need and can't defend.

### 16:56 Changed the prompt wording
The first prompt said it was a take-home assessment. I removed that and
described it as the actual project instead. I wanted Claude Code to treat this as a real project and focus
on building the product properly, not on "doing a test". The context it
needs is the domain and my decisions, not the assessment itself.

### 17:00 Step 1: scaffold (Claude Code)
Claude Code set up Spring Boot, Postgres + Kafka in Docker, Swagger, and the
docs (CLAUDE.md, ARCHITECTURE.md, README). Checked health endpoint and Swagger
both worked.

Things it flagged that I had to decide:
- It noticed this journal file was empty and didn't make up entries. Good.
- Spring Boot 3.x is no longer on Initializr, so it hand-wrote the pom on
  3.5.16 and asked if I want 4.x. **Stayed on 3.5.** Stable, more examples
  online, and upgrading mid-task is a risk I don't need. Would plan a 4.x
  upgrade before production.
- It chose `ddl-auto: validate` and `open-in-view: false` on its own. Asked
  what they do before accepting:
  - validate = Flyway owns the tables, Hibernate just checks they match.
  - open-in-view off = DB access stays in the service layer, no hidden queries.
  - Accepted both.
- It didn't run tests because they need Docker. Fine for now, proper unit
  tests come in Step 2.

### 17:05 Design gap before Step 2
When I asked for Step 2, Claude pointed out a gap in our own design: a
claimant "logs in" with an X-User-Id header, but there was no claimant user
linked to policies, so no way to know whose claims are whose.
- Changed StaffUser into one AppUser table with a role (CLAIMANT / STAFF),
  and policies link to their holder.
- My take: I missed this when reading the first plan. It was obvious once
  pointed out: if the system can't tell whose claim is whose, a claimant
  could see other people's claims. Lesson: check that every user action can
  be traced back in the data model before building.

### 17:20 Step 2: domain model + status rules
Claude Code built the database schema (V1), seed data (V2), the entities and
the claim status rules. 23 tests pass, and it made two commits like I asked.

Checked myself:
- Opened ClaimStatus. The allowed transitions match the lifecycle I planned
  (SUBMITTED → UNDER_REVIEW → INFO_REQUESTED/APPROVED/REJECTED → SETTLED).
- The transition rules are in one place (ClaimStatus), not spread around,
  which was what I wanted so they're easy to test and explain.
- Tests cover every allowed and blocked move, plus the final states.

It came back with 3 questions:
- **Estimate above coverage limit got rejected.** Two of our own rules
  clashed (reserve = estimate, reserve can't exceed the limit).
  **Overrode:** accept the claim and cap the reserve at the limit instead.
  The estimate is the customer's guess at the damage, the reserve is what
  Chubb expects to pay. Rejecting a claim because someone guessed high would
  be bad service.
- **It added time zones to Market without me asking.** Accepted. "Incident
  can't be in the future" depends on what "today" is. A same-day claim in
  Sydney could get rejected because the server's date is still yesterday.
  Good catch.
- **One shared claim number sequence for all markets.** Accepted. Still
  unique and readable, and per-market counters add complexity I don't need
  right now. Could change it in production if the business wants it.

Other things it added on its own (extra indexes, a BusinessRuleException,
text length limits). Read through them; they made sense, so I kept them.

- My take: Didn't expect our own rules to contradict each other. Good reminder
  that the AI should flag things instead of silently picking one, and that I
  should read its "decisions for you" section carefully, not just skim the
  summary.

  ### 17:50 Step 3: REST API
Claude Code built the claimant and staff endpoints, fake header login, error
handling and Swagger. 45 tests pass. It also tested against the real running
app, not just the tests.

[Tried the happy path myself in Swagger: submit as C001, assign as S001,
request info, respond, approve, settle. Also tried approving as S002 → 403.]

It asked 4 questions:
- **Reserve change reason wasn't saved anywhere.** Decided to store it in a
  table and send a Kafka event. The reserve is what exposure is based on, so
  every change needs who/when/why. That's an audit thing in insurance.
- **Claimants could see the reserve.** Hid it. It's Chubb's internal number
  and showing it could affect negotiations with the claimant.
- **API didn't allow reserve = 0.** Changed to allow 0. Expecting to pay
  nothing is a valid case, and the API should match the domain rule.
- **Swagger header fields on every call.** Switched to the Authorize button.
  Easier for testing and the demo.

Also changed: work queue now shows only open claims by default. Closed claims
don't belong in a to-do list. Spotted this in its assumptions list.

Rules it added on its own (must assign before acting, reserve only changes on
open claims, 409 if already assigned). Read them, all made sense, kept them.

- My take: The code itself worked well, but the 4 questions it raised were
  things I hadn't thought about, especially hiding the reserve from claimants.
  I only knew to hide it after thinking about how insurance works, not from
  the code. That's where my judgment matters most: the AI can build the
  endpoints, but deciding what a claimant should and shouldn't see is a
  business decision. I'm also learning to read the "assumptions" section
  properly. The open-only queue default was hidden in there, and I'd have
  missed it if I'd just skimmed.

  ### 18:20 Step 4: Kafka, notifications, exposure, workload
Claude Code added Kafka events after every claim change, claimant
notifications, and the exposure + workload reports. 59 tests pass. It also
ran a full claim through the real Kafka and checked all 7 events arrived in
order.

Key design choices (mine, from the plan):
- Events are only sent AFTER the database saves, so we never announce
  something that didn't happen. The gap: if Kafka fails right after the save,
  the event is lost. It logs "EVENT LOST". Real fix in production is the
  outbox pattern. Wrote this in ARCHITECTURE.md.
- Reporting builds its own table from events only and never reads the claim
  tables. That keeps it independent so it could become its own service later.
  Trade-off: reports can be a second behind, so they show lastUpdated.
- Each event has a claimVersion, so duplicate or late messages get ignored.
  There's a test for this.

Accepted its assumptions (notifications store the claim number instead of a
link to the claim table, event classes live in common so modules don't depend
on each other, "decided" = approved/rejected).

Noticed 2 problems to fix later:
- Integration tests leave real data in my dev database, so the reports
  include test claims. Will give tests their own DB in the final step.
- Kafka-down isn't tested. Will list it as a known gap in the README.

- My take: Honestly, Kafka was the part I was most worried about. I hadn't
  really used it before, and "eventual consistency" and "idempotent consumers"
  sounded scary. I made myself ask the dumb questions: why not just query the
  claims table for reports? Why does every event need a version number? Once
  I got the answers it actually made sense. Version number = ignore anything
  old or repeated. Separate reporting table = reports don't slow down claims.
  The test data thing was buried at the bottom of its summary and I almost
  scrolled past it. If I hadn't read that far, my demo dashboard would've
  shown random test claims and I wouldn't have known why.

  ### 18:35 Step 5: Angular frontend
Claude Code built the Angular app: demo login, claimant screens (my claims,
new claim, claim detail, notifications) and staff screens (work queue, claim
detail with actions, dashboard). 11 tests pass. No backend changes.

It told me straight that it only tested through API calls, not by clicking
the screens. So I decided to click through the whole flow myself before
moving on.

Things I noted:
- The rules for which buttons to show are copied into the frontend. Accepted:
  it's just so users don't see buttons that won't work. The backend still
  blocks anything invalid, so it's the real source of truth.
- Demo users are hard-coded in the frontend and copied from the seed data.
  Fine for a demo. A real system would get users from a proper login.
- Dashboard refreshes every 10 seconds and shows "last updated", because
  reports come from Kafka events and can be a moment behind.
- Not built: read/unread notifications, sorting in the queue, reserve history
  screen. Not important enough for the time I have.

### 18:50 Testing the UI myself: found a real bug
Started clicking through as C001 then S001. Submitting, assigning and
changing the reserve all worked. Then I used "Request information" as staff
and nothing happened. No error, status stayed "Under review", no question
saved. Tried again, same thing.

Gave Claude Code the exact steps to reproduce. Root cause: 4 forms (request
info, approve, reject, claimant answer) used an Angular submit event that only
works when the form is set up a certain way. They weren't, so clicking submit
just reloaded the page and no request was ever sent. That's why there was no
error either.

It wrote tests that failed on the old code first (proving the bug), then fixed
it and checked the other forms too. 16 frontend tests now.

[Challenged the fix: it used a workaround (preventDefault) on 4 forms while
the other 2 forms use proper Angular reactive forms. Asked it to make them
all the same pattern so it's consistent and easier to explain.]

Re-tested the whole flow in the browser after the fix with a new claim
(Test 1–7): submit, assign, change reserve, request info, claimant answer,
wrong officer blocked, error banner, approve, settle, dashboard, timeline and
notifications. All worked.

- My take: Probably the most useful thing I did today. "All tests pass" didn't
  mean the app worked. The tests just never clicked the button. If I hadn't
  tested it myself I would've found this live in front of the panel. Lesson:
  don't accept "done" until I've actually used the feature myself.