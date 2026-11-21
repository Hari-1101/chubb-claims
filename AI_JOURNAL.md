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