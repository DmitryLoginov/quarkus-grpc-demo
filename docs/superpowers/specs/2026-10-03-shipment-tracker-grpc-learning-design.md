# Shipment Tracker gRPC Learning Design

## Purpose

Build a small shipment-tracking application as a sequence of practical exercises. The learner writes the production code; the assistant provides concise theory, official references, a precise assignment for each stage, and code review after the learner submits a solution.

The project is intended for a developer who can build a basic CRUD application with Quarkus, understands proto syntax, and has not yet implemented a gRPC system.

## Learning outcomes

By the end of the core track, the learner should be able to:

- explain how a proto contract becomes Java and Mutiny APIs in Quarkus;
- implement and call unary, server-streaming, client-streaming, and bidirectional-streaming RPCs;
- model expected failures with gRPC status codes and validation details;
- test a gRPC service through a real generated client;
- handle deadlines, cancellation, and request metadata;
- keep transport, application rules, and storage responsibilities separate;
- make a deliberate choice between reactive and blocking work in a Quarkus gRPC service.

## Project boundaries

The core track uses one Maven module and one Quarkus application. The application exposes a gRPC API and uses an in-memory repository. Tests act as the client through Quarkus `@GrpcClient` injection.

The core track does not require REST endpoints, a browser UI, authentication, service discovery, Kubernetes, native-image builds, or a production database. A standalone client, PostgreSQL persistence, and TLS are optional follow-up stages. These additions must not block completion of the core track.

Java 21 is retained. The project will use the current stable Quarkus 3.x platform selected when Stage 1 begins, the `quarkus-grpc` extension, JUnit 5, and the Mutiny-generated API. Dependency versions must be managed through the Quarkus BOM rather than declared individually.

## Domain model

A shipment has:

- a server-generated identifier;
- a non-blank tracking number supplied by the client;
- an origin and destination;
- a current status;
- an ordered history of checkpoints.

The core statuses are `CREATED`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`, `DELIVERED`, and `CANCELLED`. `SHIPMENT_STATUS_UNSPECIFIED` is reserved as the proto default and is never stored as a valid domain status.

A checkpoint records the shipment identifier, the new status, a non-blank location, and an event timestamp. The server owns the order in which accepted checkpoints appear in shipment history.

The allowed transitions are:

- `CREATED` to `IN_TRANSIT` or `CANCELLED`;
- `IN_TRANSIT` to `OUT_FOR_DELIVERY` or `CANCELLED`;
- `OUT_FOR_DELIVERY` to `DELIVERED` or `CANCELLED`;
- `DELIVERED` and `CANCELLED` are terminal.

Tracking numbers are unique. IDs and timestamps are opaque API data: clients must not infer sequencing or storage behavior from their representation.

## gRPC contract

The proto contract uses `proto3`, `java_multiple_files = true`, and a dedicated Java package under `dev.ldv.shipment.grpc`.

The `ShipmentService` evolves with the learning stages rather than being delivered all at once:

```proto
service ShipmentService {
  rpc CreateShipment(CreateShipmentRequest) returns (Shipment);
  rpc GetShipment(GetShipmentRequest) returns (Shipment);
  rpc AddCheckpoint(AddCheckpointRequest) returns (ShipmentEvent);
  rpc WatchShipment(WatchShipmentRequest) returns (stream ShipmentEvent);
  rpc ImportCheckpoints(stream ImportCheckpointRequest)
      returns (ImportCheckpointsResponse);
  rpc CourierSession(stream CourierUpdate) returns (stream CourierInstruction);
}
```

Contract expectations:

- `CreateShipment` creates the initial `CREATED` event and returns the complete shipment.
- `GetShipment` returns the current snapshot by shipment ID.
- `AddCheckpoint` applies one valid status transition and returns the accepted event.
- `WatchShipment` first emits the current snapshot as an event and then emits accepted changes until the shipment reaches a terminal status or the client cancels.
- `ImportCheckpoints` accepts checkpoints for one or more existing shipments and returns accepted and rejected counts. Individual invalid entries are rejected without aborting the stream; a malformed stream-level request may terminate the RPC.
- `CourierSession` receives courier updates and emits an acknowledgement or instruction for every accepted update. It remains open until the client completes or cancels the stream, or a protocol-level failure terminates it.

Message fields and exact numbering will be introduced in the assignment for the stage that first needs them. Once a field number has been used, it must not be reused; removed fields must be marked `reserved`.

## Application structure

Production code is split by responsibility:

- `src/main/proto/shipment.proto` owns the public wire contract;
- the gRPC service adapts generated messages to application operations and maps failures to gRPC statuses;
- a shipment application service owns validation and status-transition rules;
- a repository interface owns storage operations;
- an in-memory repository provides the core-track implementation;
- interceptors own cross-cutting metadata behavior introduced late in the track.

Generated code under `target/` is inspected for learning purposes but is never edited or committed.

The service uses Mutiny `Uni` and `Multi`. Blocking calls must not run on an event-loop thread. The in-memory implementation should remain non-blocking; a later persistence exercise must explicitly choose an appropriate execution model.

## Data flow and concurrency

For a unary write, the gRPC service validates the transport shape, delegates to the application service, persists the result atomically, and maps the domain result back to a generated protobuf message.

For streaming calls, backpressure and cancellation are treated as part of the behavior rather than hidden implementation details. Each watcher receives events in repository acceptance order. Concurrent updates to one shipment must not lose checkpoints or produce an invalid transition. A slow or cancelled subscriber must be removed so it does not leak resources.

The in-memory repository is process-local and resets between application runs. That limitation is intentional and documented in the README once the core track is complete.

## Errors

Expected failures use gRPC statuses consistently:

- malformed or missing request data: `INVALID_ARGUMENT`;
- unknown shipment ID: `NOT_FOUND`;
- duplicate tracking number or invalid status transition: `ALREADY_EXISTS` or `FAILED_PRECONDITION`, respectively;
- expired client deadline: observed by the caller as `DEADLINE_EXCEEDED`;
- client cancellation: propagated through the reactive stream and used to release subscriptions;
- unexpected server failure: `INTERNAL` without leaking stack traces or storage details.

Tests assert status codes and stable descriptions where the wording is part of the exercise. Internal exception class names are not part of the public contract.

## Testing strategy

Each stage begins with a failing behavior-focused test when practical. Integration tests run the Quarkus application and call it through a generated `@GrpcClient`; they do not invoke the gRPC service class directly.

The core test suite covers:

- successful unary creation and lookup;
- validation, duplicate tracking numbers, missing shipments, and invalid transitions;
- ordered server-stream events and cancellation cleanup;
- mixed accepted and rejected client-stream entries;
- bidirectional request-response correlation and client completion;
- deadlines and metadata propagation;
- concurrent updates to the same shipment.

Small unit tests may cover transition rules where they give faster and clearer feedback than an integration test. Tests must be deterministic and must not depend on arbitrary sleeps.

## Learning stages and review gates

### Stage 0: Project foundation

Learn the Quarkus BOM and Maven plugin roles, gRPC code generation, generated service shapes, and Dev UI inspection. Configure a minimal Quarkus gRPC project and compile one proto contract. The checkpoint is a clean build plus an explanation of which generated type the service will implement.

### Stage 1: Unary RPCs

Learn request/response RPC semantics, protobuf builders and defaults, CDI registration with `@GrpcService`, and `Uni`. Implement shipment creation and lookup with in-memory storage. The checkpoint is passing happy-path integration tests.

### Stage 2: Validation and status errors

Learn the gRPC status model and the boundary between transport validation and domain rules. Add `AddCheckpoint` together with invalid-input, duplicate, missing-resource, and illegal-transition behavior. The checkpoint is a stable public error contract proven by tests.

### Stage 3: Server streaming

Learn `Multi`, subscription lifetime, backpressure at a conceptual level, and cancellation. Implement `WatchShipment`. The checkpoint proves initial state, ordered updates, terminal completion, and cleanup after cancellation.

### Stage 4: Client streaming

Learn aggregation of an inbound stream and the difference between item-level rejection and RPC failure. Implement `ImportCheckpoints`. The checkpoint proves mixed batches, an empty stream, and invalid shipment references.

### Stage 5: Bidirectional streaming

Learn independent inbound and outbound lifecycles and correlation IDs. Implement `CourierSession`. The checkpoint proves multiple interleaved updates, matching responses, normal completion, and cancellation.

### Stage 6: Call context and resilience

Learn deadlines, cancellation propagation, metadata, and server interceptors. Add a correlation ID interceptor and deadline-oriented tests. The checkpoint proves metadata availability and timely cleanup of cancelled work.

### Stage 7: Consolidation scenario

Build one end-to-end scenario test that creates a shipment, watches it, sends updates through the generated client, and observes terminal completion without direct access to the service implementation. Review contract compatibility and document how to run and inspect the application through Quarkus Dev UI or a gRPC CLI. The checkpoint is a repeatable demonstration and a green full test suite.

### Optional extensions

Replace the in-memory repository with PostgreSQL, enable TLS, or extract a standalone client into a second Maven module. Each optional extension receives its own small specification before implementation.

## Per-stage collaboration protocol

For every stage, the assistant supplies:

1. a short theory note focused on concepts needed immediately;
2. links to official Quarkus, gRPC, or protobuf documentation;
3. a technical assignment with explicit functional requirements and acceptance criteria;
4. suggested verification commands without production implementation code.

The learner implements the assignment and asks for review. The assistant reviews correctness, contract design, Quarkus and reactive usage, tests, and clarity. Findings are reported by severity with file and line references. The assistant does not modify learner-written production code unless explicitly asked.

A stage is complete only after its acceptance criteria pass and material review findings are resolved or consciously deferred. The current stage and checkpoint status are tracked in the repository-root `AGENTS.md`.

## Documentation sources

Primary references:

- [Quarkus: Getting started with gRPC](https://quarkus.io/guides/grpc-getting-started/)
- [Quarkus: gRPC reference guide](https://quarkus.io/guides/grpc-reference/)
- [Quarkus: gRPC code generation reference](https://quarkus.io/guides/grpc-generation-reference/)
- [Quarkus: Implementing a gRPC service](https://quarkus.io/guides/grpc-service-implementation/)
- [Quarkus: Consuming a gRPC service](https://quarkus.io/guides/grpc-service-consumption/)
- [gRPC: Core concepts](https://grpc.io/docs/what-is-grpc/core-concepts/)
- [Protocol Buffers: Programming guides](https://protobuf.dev/programming-guides/)

References in stage assignments should point to the smallest relevant section and should prefer current official documentation.

## Completion criteria

The core learning track is complete when all Stage 0 through Stage 7 acceptance criteria pass, the full Maven test suite is green, the application can be exercised through a generated client, and the learner can explain the lifecycle and failure behavior of each RPC type without relying on implementation details.
