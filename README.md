# room-booking API

A project that is part of my [Claude Code exploration](https://github.com/geoffweatherall/room-booking).

A GraphQL API for booking meeting rooms. Clients can create rooms, people, and bookings, list each of them, and reset all stored data. The API is serverless: AWS AppSync fronts a set of Java Lambda functions backed by DynamoDB, and every component scales to zero so an idle deployment costs (almost) nothing.

## Data model

The GraphQL schema lives in [api/room-booking.graphql](api/room-booking.graphql). There are three entities:

- **Room** — `id`, `name`, `capacity`. Capacity is the total number of people the room holds (organiser + attendees).
- **Person** — `id`, `name`.
- **Booking** — `id`, `room`, `organiser` (a Person), `attendees` (list of Person), `startTime`, `endTime`. Times are ISO-8601 local date-times with no time-zone offset (`java.time.LocalDateTime` semantics), e.g. `2026-07-01T14:30:00`, and must fall on a 5-minute boundary.

All `id` values are server-generated UUIDs; clients never supply ids on creation.

### Storage

Each entity has its own DynamoDB table (`room-booking-rooms`, `room-booking-people`, `room-booking-bookings`), keyed by `id`. Bookings are stored **denormalised**: the full room, organiser, and attendee objects are embedded in the booking item at creation time, so a booking is a snapshot — later changes to a room or person do not flow through to existing bookings.

### API operations

| Operation | Kind | Notes |
|---|---|---|
| `rooms`, `people`, `bookings` | Query | List all items of each type |
| `createRoom(room)` | Mutation | Returns `CreateRoomResult` (room or validation errors) |
| `createPerson(person)` | Mutation | Returns the created `Person`; no validation |
| `createBooking(booking)` | Mutation | Returns `CreateBookingResult` (booking or validation errors) |
| `reset` | Mutation | Deletes all rooms, people and bookings |

Sample requests for every operation are in [api/requests.http](api/requests.http). To use them: deploy, run `source authenticate.sh`, open the file in VS Code (REST Client extension), and run the **"Get an access token"** request first — the other requests reference the returned token via `{{cognitoToken.response.body.$.access_token}}` and send it in the `Authorization` header. Tokens last 1 hour; re-run the token request when one expires.

## How it is implemented

```
Client ──HTTP/GraphQL──▶ AWS AppSync ──direct Lambda resolver──▶ Java Lambda ──▶ DynamoDB
```

- **AWS AppSync** hosts the GraphQL endpoint and validates requests against the schema. Authentication is a **Cognito user pool** — every request must carry a valid JWT (see [Authentication](#authentication)). Every query and mutation field has its own resolver.
- Each resolver is a **direct Lambda resolver**: the request template forwards the whole AppSync context (`$ctx`) as the Lambda payload, and the response template returns the Lambda result as-is. There is no VTL mapping logic — all behaviour lives in Java.
- **One Lambda function per GraphQL field** (7 in total: list-rooms, list-people, list-bookings, create-room, create-person, create-booking, reset). All are built from a single shaded jar (`impl/target/room-booking-lambda.jar`), differing only in the handler class, e.g. `com.roombooking.handler.CreateBookingHandler`. Runtime is Java 25, 512 MB, 15 s timeout.
- Handlers read the table names from environment variables (`ROOMS_TABLE_NAME`, `PEOPLE_TABLE_NAME`, `BOOKINGS_TABLE_NAME`) set by Terraform, and use the AWS SDK v2 DynamoDB client.
- **DynamoDB** stores the data in three on-demand (`PAY_PER_REQUEST`) tables.
- All resources are named with the `room-booking` prefix and created in `us-east-1` by default (see [deploy/terraform/variables.tf](deploy/terraform/variables.tf)).

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

### Authentication in end-to-end tests

Both projects' end-to-end tests run non-interactively (a dev shell or CI), so neither can prompt a human for credentials. They authenticate differently because they test different things:

- **The API acceptance tests in [verify/](verify/) use machine-to-machine (M2M) auth** — the OAuth2 **client_credentials flow**. [GraphQlClient](verify/src/test/java/com/roombooking/verify/GraphQlClient.java) POSTs the test client's id and secret (read from the `COGNITO_TEST_CLIENT_ID` / `COGNITO_TEST_CLIENT_SECRET` environment variables, which `authenticate.sh` populates from Terraform outputs) to the user pool's token endpoint (`COGNITO_TOKEN_URL`) and receives a short-lived (1 h) JWT access token scoped to `room-booking-api/execute`, which AppSync accepts like any user token. One token is fetched per test run and shared by all test classes.
- **The webapp's Playwright tests sign in as a real user** — a Terraform-managed, pre-confirmed user `e2e-tests@example.com` (outputs `e2e_user_email` / `e2e_user_password`). A browser sign-in form inherently needs a user, and exercising the real sign-in UI is part of what those tests verify.

[AuthenticationAcceptanceIT](verify/src/test/java/com/roombooking/verify/AuthenticationAcceptanceIT.java) proves the API is closed: requests with no token, a malformed token, or a forged JWT all get HTTP 401 and no data, while a client_credentials token succeeds.

#### Why M2M was chosen for the API tests

The alternatives considered, and why they lost:

| Approach | Why not |
|---|---|
| **Test user + `USER_PASSWORD_AUTH`** (tests sign in with an email/password from Terraform outputs) | Works, and is marginally cheaper (user sign-ins are covered by the free MAU tier, while M2M tokens cost $0.00225 each with no free tier — pennies per year at this project's scale). But it puts a username/password in the test pipeline and makes the tests impersonate a fake "person", when what is really calling the API is a program. |
| **IAM (SigV4) as a second AppSync auth mode** | Free, but it weakens the security model: the API would no longer have the single invariant "every request carries a user-pool JWT", and the tests would then be exercising a different auth path than real clients use. It also needs AWS-credential signing in the test client. |
| **Keeping an API key for tests only** | Same problem — a second, weaker auth mode that bypasses Cognito entirely, and exactly what this design set out to remove. |

client_credentials won because it is the standard OAuth2 pattern for service-to-service callers: **no username or password exists anywhere in the flow**, the secret is generated by Terraform (never appearing in the repo) and is rotatable/revocable independently of any user, the token's identity honestly says "the acceptance-test client" rather than pretending to be a person, and — crucially — the resulting JWT goes through the **same AppSync user-pool authoriser and the same handler identity check as real user traffic**, so the tests exercise the production auth path. The only extra infrastructure it needs is the hosted domain (for the token endpoint) and the resource-server scope, both free.

## Directory structure

| Path | Contents |
|---|---|
| [api/](api/) | GraphQL schema ([room-booking.graphql](api/room-booking.graphql)) and sample requests ([requests.http](api/requests.http)) |
| [impl/](impl/) | Maven project with the Java Lambda handlers (`com.roombooking.handler.*`), model records (`com.roombooking.model.*`), and unit tests. Builds the shaded jar deployed to Lambda. |
| [deploy/terraform/](deploy/terraform/) | Terraform for all AWS resources: AppSync API, resolvers and data sources ([appsync.tf](deploy/terraform/appsync.tf)), Cognito user pool, app clients and test user ([cognito.tf](deploy/terraform/cognito.tf)), Lambda functions ([lambda.tf](deploy/terraform/lambda.tf)), DynamoDB tables ([dynamodb.tf](deploy/terraform/dynamodb.tf)), IAM roles ([iam.tf](deploy/terraform/iam.tf)), outputs (API URL, Cognito ids, test credentials). |
| [verify/](verify/) | Maven project with JUnit acceptance tests (`*IT.java`, run by failsafe) that exercise the **deployed** API over HTTP. |

### Bash scripts

All scripts live in the project root and are run from there:

| Script | What it does | How to run |
|---|---|---|
| [deploy.sh](deploy.sh) | Builds the Lambda jar (`mvn clean package` in `impl/`), then `terraform init` + `terraform apply -auto-approve` to create/update all AWS resources. Creates real AWS resources — run deliberately. | `./deploy.sh` |
| [undeploy.sh](undeploy.sh) | `terraform destroy` — deletes the AppSync API, Lambdas, and DynamoDB tables **including all stored data**. Prompts for confirmation. | `./undeploy.sh` |
| [authenticate.sh](authenticate.sh) | Reads the Terraform outputs and exports `GRAPHQL_API_URL`, the `COGNITO_*` variables (user pool id, webapp client id, token URL, test client id/secret/scope) and the `E2E_USER_*` test-user credentials into the current shell. Must be **sourced**, not executed. | `source authenticate.sh` |
| [verify.sh](verify.sh) | Sources `authenticate.sh`, then runs the acceptance tests (`mvn clean verify` in `verify/`) against the deployed API. | `./verify.sh` |

## Build, test, deploy

Prerequisites: Java 25, Maven, Terraform ≥ 1.5, and AWS credentials configured for the target account.

```bash
# Build the Lambda jar and run unit tests
mvn -f impl/pom.xml clean package

# Deploy (build + terraform apply)
./deploy.sh

# Run acceptance tests against the deployed API
./verify.sh

# Tear everything down
./undeploy.sh
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
| `InsufficientCapacity` | Room capacity must be ≥ 1 + number of attendees (the organiser counts) |
| `TimeRangeUnavailable` | The room must have no existing booking overlapping the requested `[startTime, endTime)` range (touching end-to-start is allowed) |

`createPerson` performs no validation beyond the schema's non-null `name`. The acceptance tests in [verify/](verify/) cover these rules end-to-end against the deployed API.
