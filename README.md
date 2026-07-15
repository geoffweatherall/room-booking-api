# room-booking API

A project that is part of my [Claude Code exploration](https://github.com/geoffweatherall/room-booking).

A GraphQL API for booking meeting rooms. Clients can create rooms, people, and bookings, list each of them, and reset all stored data. The API is serverless: AWS AppSync fronts a set of Java Lambda functions backed by DynamoDB, and every component scales to zero so an idle deployment costs (almost) nothing.

## Data model

The GraphQL schema lives in [api/room-booking.graphql](api/room-booking.graphql). There are three entities:

- **Room** — `id`, `name`, `capacity`. Capacity is the total number of people the room holds (organiser + attendees).
- **Person** — `id`, `name`. Also has a backend-only `cognitoSub` attribute, not exposed over GraphQL: it's set to the Cognito user's `sub` for a Person created automatically on sign-up (see [Sign-up creates a linked Person](#sign-up-creates-a-linked-person)), and left unset for people added directly (e.g. guests with no login), so a future account-deletion flow can find and remove the Person linked to a deleted Cognito user.
- **Booking** — `id`, `room`, `organiser` (a Person), `attendees` (list of Person), `subject`, `startTime`, `endTime`. `subject` must not be null or blank. Times are ISO-8601 local date-times with no time-zone offset (`java.time.LocalDateTime` semantics), e.g. `2026-07-01T14:30:00`, and must fall on a 5-minute boundary.

All `id` values are server-generated UUIDs; clients never supply ids on creation.

### Storage

Each entity has its own DynamoDB table (`room-booking-rooms`, `room-booking-people`, `room-booking-bookings`), keyed by `id`. Bookings are stored **denormalised**: the full room, organiser, and attendee objects are embedded in the booking item at creation time, so a booking is a snapshot — later changes to a room or person do not flow through to existing bookings.

### API operations

| Operation | Kind | Notes |
|---|---|---|
| `rooms`, `people`, `bookings` | Query | List all items of each type |
| `myPerson` | Query | Returns the `Person` linked to the caller's own Cognito account (via `identity.sub`), or `null` if none exists |
| `createRoom(room)` | Mutation | Returns `CreateRoomResult` (room or validation errors) |
| `createPerson(person)` | Mutation | Returns the created `Person`; no validation |
| `createBooking(booking)` | Mutation | Returns `CreateBookingResult` (booking or validation errors) |
| `reset` | Mutation | Deletes all rooms and bookings, and every person except those linked to a Cognito account (see [Reset and real user accounts](#reset-and-real-user-accounts)) |

Sample requests for every operation are in [api/requests.http](api/requests.http). To use them: deploy, run `source authenticate.sh <environment>`, open the file in VS Code (REST Client extension), and run the **"Get an access token"** request first — the other requests reference the returned token via `{{cognitoToken.response.body.$.access_token}}` and send it in the `Authorization` header. Tokens last 1 hour; re-run the token request when one expires.

## How it is implemented

```
Client ──HTTP/GraphQL──▶ AWS AppSync ──direct Lambda resolver──▶ Java Lambda ──▶ DynamoDB
```

- **AWS AppSync** hosts the GraphQL endpoint and validates requests against the schema. Authentication is a **Cognito user pool** — every request must carry a valid JWT (see [Authentication](#authentication)). Every query and mutation field has its own resolver.
- Each resolver is a **direct Lambda resolver**: the request template forwards the whole AppSync context (`$ctx`) as the Lambda payload, and the response template returns the Lambda result as-is. There is no VTL mapping logic — all behaviour lives in Java.
- **One Lambda function per GraphQL field** (8 in total: list-rooms, list-people, list-bookings, create-room, create-person, my-person, create-booking, reset), plus one more that isn't a GraphQL resolver at all: `post-confirmation-create-person`, a Cognito trigger (see below). All are built from a single shaded jar (`impl/target/room-booking-lambda.jar`), differing only in the handler class, e.g. `com.roombooking.handler.CreateBookingHandler`. Runtime is Java 25, 512 MB, 15 s timeout.
- Handlers read the table names from environment variables (`ROOMS_TABLE_NAME`, `PEOPLE_TABLE_NAME`, `BOOKINGS_TABLE_NAME`) set by Terraform, and use the AWS SDK v2 DynamoDB client.
- **DynamoDB** stores the data in three on-demand (`PAY_PER_REQUEST`) tables.
- All resources are named with the `room-booking` prefix and created in `us-east-1` by default (see [deploy/terraform/variables.tf](deploy/terraform/variables.tf)).

## Sign-up creates a linked Person

When a user confirms their email during sign-up (in the [room-booking-webapp](https://github.com/geoffweatherall/room-booking-webapp)), a Cognito **PostConfirmation Lambda trigger** ([PostConfirmationCreatePersonHandler](impl/src/main/java/com/roombooking/handler/PostConfirmationCreatePersonHandler.java), wired up in [cognito.tf](deploy/terraform/cognito.tf)) automatically creates a `Person` using the `name` the user entered on sign-up, and links it to the account via `cognitoSub`.

This runs **after** email confirmation rather than before (a `PreSignUp` trigger would fire while the address is still unverified, risking orphaned Person records for abandoned or typo'd sign-ups) or from the browser (a client-side call after `confirmSignUp()` would leave a confirmed account with no Person if the tab closes or the network drops before that call completes). Cognito also retries `PostConfirmation` invocations on failure, so the handler is idempotent — it checks the `cognitoSub-index` GSI on the People table before writing, and skips creation if a Person already exists for that `sub`. Because Cognito treats an exception thrown here as a failure of the user's confirm-sign-up call (even though the account is already confirmed by that point), the handler logs and swallows any error rather than throwing, so a transient DynamoDB problem never blocks sign-up.

Note: the Terraform-managed e2e test user and demo user ([cognito.tf](deploy/terraform/cognito.tf) `aws_cognito_user.e2e` and `aws_cognito_user.demo`) are created directly rather than through the sign-up/confirm API calls, so neither gets a linked Person this way.

### Reset and real user accounts

`Mutation.reset` ([ResetHandler](impl/src/main/java/com/roombooking/handler/ResetHandler.java)) always deletes every room and booking, but only deletes a person if its `cognitoSub` is unset. A real signed-up user's Person record is their only link back to their Cognito account (nothing recreates it after the fact — see above), so unconditionally wiping it on every reset would silently break their account the next time someone reset a shared, non-production environment. Guests added directly (no `cognitoSub`) have no such link and are always cleared, keeping `reset` useful for acceptance tests and tools like [room-booking-tools](https://github.com/geoffweatherall/room-booking-tools)' sample data generator without ever touching a real account — including the e2e test user and demo user, both exempt for the same reason (they just happen to have no Person at all, per the note above).

### Displaying the signed-in user's name

The webapp shows the signed-in user's name (see [MyPersonHandler](impl/src/main/java/com/roombooking/handler/MyPersonHandler.java)/`Query.myPerson`) by reading it live from the `Person` record rather than from the Cognito JWT, so a future "change my name" feature only has to write one place. The `cognitoSub-index` GSI has `ALL` projection (not `KEYS_ONLY`) specifically so this resolver can read the full item straight off the index in one request.

This was chosen over customizing the `name` claim in the ID token with a Pre Token Generation Lambda trigger. That approach would also work, but a token's claims are only refreshed on next sign-in/token-refresh (up to the token's ~1 hour lifetime), so a rename would appear stale for up to an hour; reading `myPerson` on demand is always current. It also avoids a subtler correctness gap: `ConfirmSignUp` invokes the `PostConfirmation` trigger synchronously and Cognito won't authenticate an unconfirmed user, so the trigger that creates the Person is guaranteed to have *run* before any sign-in — but not guaranteed to have *succeeded* (its DynamoDB write is deliberately swallowed on error, see above) or to be *visible yet* (DynamoDB GSI reads are only eventually consistent, and the webapp signs the user in immediately after confirming). A Pre Token Generation trigger racing that same window could bake a missing/stale name into a token for up to an hour; `myPerson` just returns `null` for that one moment and the webapp's existing email/JWT-name fallback covers it until the next query.

## Authentication

All access to the API is authenticated by an **Amazon Cognito user pool** (`room-booking-users`, created by Terraform in [deploy/terraform/cognito.tf](deploy/terraform/cognito.tf)). Users sign in with an **email address and password**; Cognito emails a verification code on sign-up, and account recovery (forgot password) works the same way — a code emailed to the verified address. There is no API key.

Every GraphQL request must carry a JWT issued by the user pool in the `Authorization` header (the raw token, no `Bearer` prefix). Enforcement happens in two layers:

1. **AppSync** is configured with `AMAZON_COGNITO_USER_POOLS` authentication: it verifies the token's signature, issuer, and expiry against the user pool **before any resolver runs**, and returns HTTP 401 `UnauthorizedException` otherwise.
2. **Every Lambda handler** re-checks, before running any logic, that the AppSync context it received contains an authenticated `identity` ([Identity.requireAuthenticated](impl/src/main/java/com/roombooking/handler/Identity.java)) — defence-in-depth in case the API is ever accidentally exposed without the authoriser.

The user pool has two app clients (plus a hosted domain used only for the OAuth2 token endpoint):

| App client | Kind | Used by |
|---|---|---|
| `room-booking-webapp` | Public (no secret), SRP auth flow | The [room-booking-webapp](https://github.com/geoffweatherall/room-booking-webapp) browser SPA: users sign up / sign in and their id token is sent with each GraphQL call |
| `room-booking-acceptance-tests` | Confidential (client secret), OAuth2 `client_credentials` flow | The [verify/](verify/) acceptance tests and [api/requests.http](api/requests.http) |

### Demo user

This is a demo system rather than a real business, so every deployment — including a "production" one — includes a pre-confirmed, publicly-known demo user (`demo@room-booking.com`, Terraform outputs `demo_user_email` / `demo_user_password`, resources `aws_cognito_user.demo` / `random_password.demo_user` in [cognito.tf](deploy/terraform/cognito.tf)) that anyone can sign in as without creating their own account. Its password is randomly generated at deploy time (like the e2e test user's), but restricted to lowercase letters and digits only, so it's easy to read and type by hand when the webapp shows it on the home page. It is not a secret and its output is not marked `sensitive` — the whole point is that it's shown in the clear. (An earlier version used a fixed password, `demo1234`, which turned out to be on Google's list of known-compromised passwords; it's random now to avoid that.)

The user pool's password policy is set correspondingly loose to match: a minimum of 10 characters with a lowercase letter and a number, and no requirement for uppercase letters or symbols. A real product would want a stricter policy; this one is deliberately weakened so the demo password (and anyone else's) is easy to type.

### Authentication in end-to-end tests

Both projects' end-to-end tests run non-interactively (a dev shell or CI), so neither can prompt a human for credentials. They authenticate differently because they test different things:

- **The API acceptance tests in [verify/](verify/) use machine-to-machine (M2M) auth** — the OAuth2 **client_credentials flow**. [GraphQlClient](verify/src/test/java/com/roombooking/verify/GraphQlClient.java) POSTs the test client's id and secret (read from the `COGNITO_TEST_CLIENT_ID` / `COGNITO_TEST_CLIENT_SECRET` environment variables, which `authenticate.sh` populates from Terraform outputs) to the user pool's token endpoint (`COGNITO_TOKEN_URL`) and receives a short-lived (1 h) JWT access token scoped to `room-booking-api/execute`, which AppSync accepts like any user token. One token is fetched per test run and shared by all test classes.
- **The webapp's Playwright tests sign in as a real user** — a Terraform-managed, pre-confirmed user `e2e-tests@example.com` (outputs `e2e_user_email` / `e2e_user_password`). A browser sign-in form inherently needs a user, and exercising the real sign-in UI is part of what those tests verify.

[AuthenticationAcceptanceIT](verify/src/test/java/com/roombooking/verify/AuthenticationAcceptanceIT.java) proves the API is closed: requests with no token, a malformed token, or a forged JWT all get HTTP 401 and no data, while a client_credentials token succeeds.

## Directory structure

| Path | Contents |
|---|---|
| [api/](api/) | GraphQL schema ([room-booking.graphql](api/room-booking.graphql)) and sample requests ([requests.http](api/requests.http)) |
| [impl/](impl/) | Maven project with the Java Lambda handlers (`com.roombooking.handler.*`), model records (`com.roombooking.model.*`), and unit tests. Builds the shaded jar deployed to Lambda. |
| [deploy/terraform/](deploy/terraform/) | Terraform for all AWS resources: AppSync API, resolvers and data sources ([appsync.tf](deploy/terraform/appsync.tf)), Cognito user pool, app clients, the e2e test user, and the public demo user ([cognito.tf](deploy/terraform/cognito.tf)), Lambda functions ([lambda.tf](deploy/terraform/lambda.tf)), DynamoDB tables ([dynamodb.tf](deploy/terraform/dynamodb.tf)), IAM roles ([iam.tf](deploy/terraform/iam.tf)), outputs (API URL, Cognito ids, test and demo user credentials). All resource names are prefixed with `<environment>-<project_name>` ([locals.tf](deploy/terraform/locals.tf)) so multiple environments can coexist in one AWS account. State is stored remotely in S3, one state file per environment ([backend.hcl](deploy/terraform/backend.hcl) — see the [room-booking-bootstrap-terraform](https://github.com/geoffweatherall/room-booking-bootstrap-terraform) README for how that bucket is set up, and the [room-booking project README](https://github.com/geoffweatherall/room-booking#multi-environment-deployments) for the multi-environment design). |
| [verify/](verify/) | Maven project with JUnit acceptance tests (`*IT.java`, run by failsafe) that exercise the **deployed** API over HTTP. |

### Bash scripts

All scripts live in the project root and are run from there:

| Script | What it does | How to run |
|---|---|---|
| [deploy.sh](deploy.sh) | Builds the Lambda jar (`mvn clean package` in `impl/`), then `terraform init` + `terraform apply -auto-approve` to create/update all AWS resources **for the given environment**. Creates real AWS resources — run deliberately. | `./deploy.sh <environment>` |
| [undeploy.sh](undeploy.sh) | `terraform destroy` — deletes the AppSync API, Lambdas, and DynamoDB tables **including all stored data**, for the given environment. Prompts for confirmation. | `./undeploy.sh <environment>` |
| [authenticate.sh](authenticate.sh) | Reads the given environment's Terraform outputs and exports `GRAPHQL_API_URL`, the `COGNITO_*` variables (user pool id, webapp client id, token URL, test client id/secret/scope) and the `E2E_USER_*` test-user credentials into the current shell. Must be **sourced**, not executed. | `source authenticate.sh <environment>` |
| [verify.sh](verify.sh) | Sources `authenticate.sh <environment>`, then runs the acceptance tests (`mvn clean verify` in `verify/`) against that environment's deployed API. | `./verify.sh <environment>` |

## Build, test, deploy

Prerequisites: Java 25, Maven, Terraform ≥ 1.10, and AWS credentials configured for the target account.

Every deploy/undeploy/authenticate/verify script takes an **environment** name
(e.g. `test`, `production`, or your own name for a personal sandbox) so
multiple independent copies of the API can run in the same AWS account at
once — see the [room-booking project README](https://github.com/geoffweatherall/room-booking#multi-environment-deployments)
for the full multi-environment how-to and the reasoning behind it.

```bash
# Build the Lambda jar and run unit tests
mvn -f impl/pom.xml clean package

# Deploy (build + terraform apply) to an environment, e.g. "test" or your own name
./deploy.sh test

# Run acceptance tests against that environment's deployed API
./verify.sh test

# Tear it down when you're done
./undeploy.sh test
```

The acceptance tests need a deployed API; they read the endpoint and the Cognito client_credentials settings from the environment variables exported by `authenticate.sh`, and fetch a JWT from the token endpoint before calling the API (see [Authentication](#authentication)). Note that `reset` and the acceptance tests delete/modify live data, so don't point them at a deployment you care about.

## Cost model

Every component is configured to scale to zero, so a deployed-but-idle API costs effectively nothing. All costs are **per-use**:

| Resource | Billing | Idle cost |
|---|---|---|
| AppSync | Per query/mutation request | $0 |
| Lambda | Per invocation + GB-seconds of execution | $0 |
| DynamoDB | On-demand (`PAY_PER_REQUEST`): per read/write request unit + storage | ~$0 (storage only, negligible at this scale) |
| Cognito | Per monthly active user (10k free), plus $0.00225 per M2M token issued to the acceptance-test client (no free tier) | $0 |
| CloudWatch Logs | Per GB ingested/stored from Lambda logs | ~$0 when idle |

There are no fixed-price resources (no provisioned DynamoDB capacity, no EC2/containers, no NAT gateways, no provisioned Lambda concurrency). Costs scale linearly with API call volume: each GraphQL call is one AppSync request, one Lambda invocation, and one or more DynamoDB operations.

One scaling caveat: `createBooking` (overlap check) and `reset` **scan** the whole bookings/tables rather than using an index, so their DynamoDB read cost grows with total stored data, not just with call volume. Fine at demo scale; would need a GSI at real scale.

## Validation

### How it works

Validation is implemented entirely in the Java Lambda handlers (not in AppSync/VTL, apart from the type/nullability checks the GraphQL schema itself enforces). The create mutations for rooms and bookings never throw GraphQL errors for rule violations; instead they return a **structured result object**:

- `CreateRoomResult { room, errors: [RoomError!]! }`
- `CreateBookingResult { booking, errors: [BookingError!]! }`

On success the entity field is populated and `errors` is empty. On failure the entity field is `null` and `errors` contains **one enum entry per rule broken** — the handlers collect all violations rather than stopping at the first, so a client gets the complete list in one round trip. Nothing is written to DynamoDB unless validation passes.

### Rules

`createRoom` ([CreateRoomHandler](impl/src/main/java/com/roombooking/handler/CreateRoomHandler.java)):

| Error | Rule |
|---|---|
| `NameRequired` | `name` must not be null or blank |
| `CapacityTooLow` | `capacity` must be ≥ 2 |

`createBooking` ([CreateBookingHandler](impl/src/main/java/com/roombooking/handler/CreateBookingHandler.java)):

| Error | Rule |
|---|---|
| `StartMissaligned` / `EndMissaligned` | Start/end time must parse as an ISO-8601 local date-time and fall exactly on a 5-minute boundary (no seconds/nanos) |
| `RoomRequired` | `roomId` must not be blank |
| `RoomNotFound` | `roomId` must refer to an existing room |
| `OrganiserRequired` | `organiserId` must not be blank |
| `OrganiserNotFound` | `organiserId` must refer to an existing person |
| `AttendeeNotFound` | Every id in `attendeeIds` must refer to an existing person (one error per missing attendee) |
| `SubjectRequired` | `subject` must not be null or blank |
| `InsufficientCapacity` | Room capacity must be ≥ 1 + number of attendees (the organiser counts) |
| `TimeRangeUnavailable` | The room must have no existing booking overlapping the requested `[startTime, endTime)` range (touching end-to-start is allowed) |

`createPerson` performs no validation beyond the schema's non-null `name`. The acceptance tests in [verify/](verify/) cover these rules end-to-end against the deployed API.

## Implementation choices

#### Why M2M was chosen for the API tests

The alternatives considered, and why they lost:

| Approach | Why not |
|---|---|
| **Test user + `USER_PASSWORD_AUTH`** (tests sign in with an email/password from Terraform outputs) | Works, and is marginally cheaper (user sign-ins are covered by the free MAU tier, while M2M tokens cost $0.00225 each with no free tier — pennies per year at this project's scale). But it puts a username/password in the test pipeline and makes the tests impersonate a fake "person", when what is really calling the API is a program. |
| **IAM (SigV4) as a second AppSync auth mode** | Free, but it weakens the security model: the API would no longer have the single invariant "every request carries a user-pool JWT", and the tests would then be exercising a different auth path than real clients use. It also needs AWS-credential signing in the test client. |
| **Keeping an API key for tests only** | Same problem — a second, weaker auth mode that bypasses Cognito entirely, and exactly what this design set out to remove. |

client_credentials won because it is the standard OAuth2 pattern for service-to-service callers: **no username or password exists anywhere in the flow**, the secret is generated by Terraform (never appearing in the repo) and is rotatable/revocable independently of any user, the token's identity honestly says "the acceptance-test client" rather than pretending to be a person, and — crucially — the resulting JWT goes through the **same AppSync user-pool authoriser and the same handler identity check as real user traffic**, so the tests exercise the production auth path. The only extra infrastructure it needs is the hosted domain (for the token endpoint) and the resource-server scope, both free.
