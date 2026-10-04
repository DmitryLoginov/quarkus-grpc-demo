# Shipment Tracker gRPC Learning Plan

> **For agentic workers:** This is a learner-led plan. Do not implement learner assignments unless the user explicitly asks. If implementation is requested, use `superpowers:executing-plans` and preserve the current checkpoint in `AGENTS.md`.

**Goal:** Научиться проектировать, реализовывать и тестировать все четыре вида gRPC-вызовов в Quarkus на примере сервиса отслеживания отправлений.

**Architecture:** Один Quarkus-модуль публикует `ShipmentService`, отделяет gRPC-адаптер от прикладных правил и использует потокобезопасный in-memory repository. Интеграционные тесты вызывают приложение через сгенерированный Mutiny-клиент.

**Tech Stack:** Java 21, Maven, Quarkus 3.40.1 LTS, `quarkus-grpc`, Mutiny, Protocol Buffers, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-03-shipment-tracker-grpc-learning-design.md`

## Global Constraints

- Один Maven-модуль до завершения основного трека.
- Версия Quarkus: `3.40.1`, управляется через Quarkus BOM.
- Версия Java: `21`.
- Основной API реализации и клиента: сгенерированный Mutiny API (`Uni` и `Multi`).
- Proto-файлы находятся в `src/main/proto`; сгенерированные файлы в `target` не редактируются и не коммитятся.
- Основное хранилище до завершения трека работает в памяти и не выполняет blocking I/O.
- Интеграционные тесты обращаются к реальному gRPC-серверу через `@GrpcClient`.
- Код пишет пользователь. Ассистент даёт теорию, ТЗ и ревью; правки кода делает только по прямому запросу.
- Новое ТЗ выдаётся после закрытия предыдущей контрольной точки.
- Перед коммитами документации ассистент получает явное согласование пользователя.

## Review Focus

- Proto default values: `SHIPMENT_STATUS_UNSPECIFIED` не должен становиться допустимым доменным статусом.
- Конкурентные обновления одного отправления не должны терять checkpoint или нарушать порядок переходов.
- Отмена streaming-вызова должна освобождать подписку без ожидания следующего события.
- Ошибка одного элемента client stream не должна завершать весь поток, если сам stream остаётся корректным.
- Ошибки сервера не должны передавать клиенту stack trace, имя внутреннего исключения или детали хранилища.

---

## Ритм каждого этапа

1. Ассистент публикует краткую теорию, официальные ссылки, ТЗ и критерии приёмки.
2. Пользователь реализует этап и сообщает, что код готов к ревью.
3. Ассистент изучает diff, запускает релевантные проверки и сообщает замечания по приоритету.
4. Пользователь исправляет существенные замечания либо явно откладывает их.
5. Ассистент обновляет `AGENTS.md`: отмечает checkpoint и открывает следующий этап.

## Task 0: Основа Quarkus gRPC-проекта

**Files:**

- Modify: `pom.xml`
- Create: `src/main/proto/shipment.proto`
- Create: `src/main/resources/application.properties`
- Reference: `docs/stages/00-project-foundation.md`

**Interfaces:**

- Produces: Quarkus build с генерацией `dev.ldv.shipment.grpc.ShipmentService` и protobuf message types.
- Produces: proto RPC `CreateShipment(CreateShipmentRequest) returns (Shipment)`.
- Produces: proto RPC `GetShipment(GetShipmentRequest) returns (Shipment)`.

- [ ] Прочитать теорию и официальные разделы из задания этапа.
- [ ] Подключить Quarkus BOM `3.40.1`, Maven plugin и минимальные зависимости gRPC/test.
- [ ] Создать первый proto-контракт с unary RPC и назначенными в ТЗ field numbers.
- [ ] Настроить Vert.x gRPC server на общем HTTP-сервере.
- [ ] Выполнить `mvn clean compile` и изучить сгенерированные Mutiny-типы.
- [ ] Зафиксировать короткое объяснение generated API в сообщении к ревью.
- [ ] Пройти ревью конфигурации и контракта.

**Acceptance:** `mvn clean compile` завершается успешно; нужные типы появляются в `target/generated-sources/grpc`; generated-код не попадает в Git.

## Task 1: Unary создание и чтение

**Files:**

- Create: `src/main/java/dev/ldv/shipment/domain/Shipment.java`
- Create: `src/main/java/dev/ldv/shipment/domain/ShipmentStatus.java`
- Create: `src/main/java/dev/ldv/shipment/application/ShipmentApplicationService.java`
- Create: `src/main/java/dev/ldv/shipment/repository/ShipmentRepository.java`
- Create: `src/main/java/dev/ldv/shipment/repository/InMemoryShipmentRepository.java`
- Create: `src/main/java/dev/ldv/shipment/grpc/ShipmentGrpcService.java`
- Create: `src/main/java/dev/ldv/shipment/grpc/ShipmentGrpcMapper.java`
- Create: `src/test/java/dev/ldv/shipment/grpc/ShipmentUnaryGrpcTest.java`

**Interfaces:**

- `ShipmentRepository.save(Shipment shipment) -> Shipment`
- `ShipmentRepository.findById(String id) -> Optional<Shipment>`
- `ShipmentApplicationService.create(String trackingNumber, String origin, String destination) -> Shipment`
- `ShipmentApplicationService.getById(String id) -> Shipment`
- `ShipmentGrpcService implements dev.ldv.shipment.grpc.ShipmentService`

- [ ] Получить теорию и ТЗ Stage 1 после закрытия Task 0.
- [ ] Написать failing integration tests для создания и получения отправления.
- [ ] Реализовать минимальную доменную модель и потокобезопасный repository.
- [ ] Реализовать application service без зависимости от protobuf-классов.
- [ ] Реализовать mapper и unary-методы gRPC-сервиса.
- [ ] Запустить `mvn test` и пройти ревью.

**Acceptance:** клиент создаёт отправление со статусом `CREATED`, затем получает тот же snapshot по ID; transport types не проникают в domain/repository API.

## Task 2: Валидация, checkpoint и gRPC statuses

**Files:**

- Modify: `src/main/proto/shipment.proto`
- Modify: application, repository, mapper and gRPC service files from Task 1
- Create: `src/main/java/dev/ldv/shipment/domain/Checkpoint.java`
- Create: `src/main/java/dev/ldv/shipment/domain/ShipmentUpdate.java`
- Create: `src/main/java/dev/ldv/shipment/domain/ShipmentException.java`
- Create: `src/test/java/dev/ldv/shipment/grpc/ShipmentValidationGrpcTest.java`
- Create: `src/test/java/dev/ldv/shipment/application/ShipmentTransitionsTest.java`

**Interfaces:**

- Adds RPC: `AddCheckpoint(AddCheckpointRequest) returns (ShipmentEvent)`.
- Adds an atomic repository operation that saves only when the tracking number is absent.
- Adds an atomic repository update operation for one shipment ID.
- `ShipmentUpdate` contains the updated domain `Shipment` and its accepted `Checkpoint`.
- `ShipmentApplicationService.addCheckpoint(String shipmentId, ShipmentStatus status, String location, Instant occurredAt) -> ShipmentUpdate`
- Repository update for one shipment is atomic.

- [ ] Получить ТЗ с точной таблицей validation/status mapping.
- [ ] Написать failing tests для blank fields, duplicate tracking number, missing ID и illegal transition.
- [ ] Реализовать переходы статусов как доменное правило.
- [ ] Реализовать `AddCheckpoint` и отображение ожидаемых ошибок в gRPC status codes.
- [ ] Проверить happy path и terminal states.
- [ ] Запустить `mvn test` и пройти ревью.

**Acceptance:** `INVALID_ARGUMENT`, `NOT_FOUND`, `ALREADY_EXISTS` и `FAILED_PRECONDITION` наблюдаются клиентом в заданных случаях; внутренние детали не утекают.

## Task 3: Server streaming

**Files:**

- Modify: `src/main/proto/shipment.proto`
- Modify: repository, application service and gRPC service
- Create: `src/main/java/dev/ldv/shipment/application/ShipmentEventPublisher.java`
- Create: `src/test/java/dev/ldv/shipment/grpc/WatchShipmentGrpcTest.java`

**Interfaces:**

- Adds RPC: `WatchShipment(WatchShipmentRequest) returns (stream ShipmentEvent)`.
- `ShipmentEventPublisher.watch(String shipmentId) -> Multi<ShipmentUpdate>`.
- `ShipmentEventPublisher.publish(ShipmentUpdate update) -> void`.

- [ ] Получить теорию о `Multi`, backpressure, completion и cancellation.
- [ ] Написать failing tests для initial snapshot, ordered events и terminal completion.
- [ ] Реализовать publisher с отдельным lifecycle подписки для каждого клиента.
- [ ] Добавить тест отмены и проверку удаления подписчика без `sleep`.
- [ ] Запустить `mvn test` и пройти ревью утечек и race conditions.

**Acceptance:** watcher получает текущее состояние, затем события в порядке принятия; terminal status завершает поток; cancellation освобождает подписку.

## Task 4: Client streaming

**Files:**

- Modify: `src/main/proto/shipment.proto`
- Modify: application service and gRPC service
- Create: `src/test/java/dev/ldv/shipment/grpc/ImportCheckpointsGrpcTest.java`

**Interfaces:**

- Adds RPC: `ImportCheckpoints(stream ImportCheckpointRequest) returns (ImportCheckpointsResponse)`.
- Response exposes `accepted_count` and `rejected_count`.

- [ ] Получить теорию о входном stream и агрегации результата.
- [ ] Написать failing tests для mixed batch, empty stream и неизвестного shipment ID.
- [ ] Переиспользовать единственную application operation добавления checkpoint.
- [ ] Разделить item-level rejection и protocol-level RPC failure.
- [ ] Запустить `mvn test` и пройти ревью поведения потока.

**Acceptance:** валидные элементы сохраняются, невалидные учитываются как rejected, итоговые counters точны, empty stream возвращает нули.

## Task 5: Bidirectional streaming

**Files:**

- Modify: `src/main/proto/shipment.proto`
- Modify: application service and gRPC service
- Create: `src/test/java/dev/ldv/shipment/grpc/CourierSessionGrpcTest.java`

**Interfaces:**

- Adds RPC: `CourierSession(stream CourierUpdate) returns (stream CourierInstruction)`.
- Every update and instruction carries the same non-blank `correlation_id`.

- [ ] Получить теорию о независимых lifecycle входного и выходного потоков.
- [ ] Написать failing tests для нескольких обновлений и correlation IDs.
- [ ] Реализовать acknowledgement/instruction для каждого принятого update.
- [ ] Проверить normal completion, invalid update и cancellation.
- [ ] Запустить `mvn test` и пройти ревью.

**Acceptance:** ответы сопоставляются с запросами, порядок одного client stream сохраняется, завершение и отмена освобождают ресурсы.

## Task 6: Metadata, deadlines и cancellation

**Files:**

- Create: `src/main/java/dev/ldv/shipment/grpc/CorrelationIdInterceptor.java`
- Create: `src/main/java/dev/ldv/shipment/grpc/CallContext.java`
- Create: `src/test/java/dev/ldv/shipment/grpc/GrpcCallContextTest.java`
- Modify: gRPC service where context is consumed

**Interfaces:**

- Request metadata key: `x-correlation-id`.
- Missing correlation ID is generated server-side; a supplied valid value is retained.
- Cancellation is visible to long-lived operations and terminates their work.

- [ ] Получить теорию о metadata, interceptors, deadlines и cancellation propagation.
- [ ] Написать failing tests для supplied/generated correlation ID.
- [ ] Добавить interceptor и request-scoped call context.
- [ ] Добавить deterministic deadline/cancellation test без arbitrary sleep.
- [ ] Запустить `mvn test` и пройти ревью cleanup и thread usage.

**Acceptance:** correlation ID доступен обработчику каждого вызова; истёкший deadline виден клиенту; отменённая работа прекращается.

## Task 7: Сквозной сценарий и документация

**Files:**

- Create: `src/test/java/dev/ldv/shipment/grpc/ShipmentJourneyGrpcTest.java`
- Create: `README.md`
- Modify: `.gitignore` if generated artifacts are not already excluded

**Interfaces:**

- The journey test uses only generated client APIs and public gRPC behavior.
- README documents build, test, dev mode, Dev UI/gRPC CLI inspection, ports and in-memory limitations.

- [ ] Написать end-to-end journey от `CREATED` до `DELIVERED` с watcher.
- [ ] Проверить все четыре RPC shapes в полном test suite.
- [ ] Описать запуск и ручную проверку без обращения к внутренним Java-классам.
- [ ] Выполнить `mvn clean verify`.
- [ ] Пройти итоговое ревью контракта, concurrency и документации.

**Acceptance:** `mvn clean verify` проходит с чистого checkout; README позволяет повторить демонстрацию; все checkpoints в `AGENTS.md` закрыты.

## Optional follow-ups

Каждое расширение требует отдельной короткой спецификации:

- PostgreSQL repository с выбранной blocking или reactive моделью;
- TLS для сервера и клиента;
- отдельный Maven-модуль клиента;
- contract compatibility exercise с эволюцией proto без нарушения field numbers.
