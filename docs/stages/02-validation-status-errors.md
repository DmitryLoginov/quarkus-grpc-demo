# Stage 2 — Validation, checkpoint и gRPC statuses

## Цель

Добавить первый изменяющий статус RPC, историю checkpoint, валидацию и устойчивый публичный контракт ошибок. После этапа клиент должен отличать неправильный запрос, отсутствующее отправление, дубликат tracking number и запрещённый переход статуса.

## Краткая теория

Успешный protobuf response описан в `.proto`, а ошибка gRPC передаётся отдельно как status. Основные части ошибки — `Status.Code`, короткое безопасное description и необязательные metadata/details. На этом этапе используем code и description.

Разделяй виды ошибок:

- `INVALID_ARGUMENT`: запрос некорректен независимо от состояния сервера;
- `NOT_FOUND`: корректно указанный ресурс отсутствует;
- `ALREADY_EXISTS`: уникальный ресурс уже существует;
- `FAILED_PRECONDITION`: запрос корректен сам по себе, но текущее состояние не разрешает операцию.

Исключение, выброшенное внутри supplier, становится failure-сигналом `Uni`. Это позволяет построить pipeline: лениво выполнить application operation, преобразовать ожидаемое domain failure в `StatusRuntimeException`, передать его gRPC runtime.

Потокобезопасная коллекция гарантирует безопасность отдельного `get` или `put`. Проверка состояния и последующая запись — составная операция. Для перехода статуса она должна быть атомарной относительно одного shipment ID; иначе два запроса могут проверить одно старое состояние и оба записать несовместимые результаты.

Proto field numbers являются частью wire contract. Существующие номера менять или переиспользовать нельзя.

## Материалы

- [gRPC: Status codes](https://grpc.io/docs/guides/status-codes/)
- [Quarkus: Handling errors](https://quarkus.io/guides/grpc-service-implementation/#handling-errors)
- [SmallRye Mutiny: How to handle failures](https://smallrye.io/smallrye-mutiny/latest/tutorials/handling-failures/)
- [Protobuf: Updating a message type](https://protobuf.dev/programming-guides/proto3/#updating)
- [Java `ConcurrentHashMap.compute`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#compute(K,java.util.function.BiFunction))

## Техническое задание

### 1. Расширение proto-контракта

В `shipment.proto` импортируй `google/protobuf/timestamp.proto` и добавь:

```proto
message Checkpoint {
  string shipment_id = 1;
  ShipmentStatus status = 2;
  string location = 3;
  google.protobuf.Timestamp occurred_at = 4;
}

message AddCheckpointRequest {
  Checkpoint checkpoint = 1;
}

message ShipmentEvent {
  Shipment shipment = 1;
  Checkpoint checkpoint = 2;
}
```

В существующий `Shipment` добавь, не меняя прежние номера:

```proto
repeated Checkpoint checkpoints = 6;
```

В `ShipmentService` добавь unary RPC:

```proto
rpc AddCheckpoint(AddCheckpointRequest) returns (ShipmentEvent);
```

Не переименовывай и не перенумеровывай поля Stage 0.

### 2. Domain model

Расширь domain `ShipmentStatus` значениями:

- `IN_TRANSIT`;
- `OUT_FOR_DELIVERY`;
- `DELIVERED`;
- `CANCELLED`.

Создай immutable `Checkpoint`:

```java
String shipmentId
ShipmentStatus status
String location
Instant occurredAt
```

Создай immutable `ShipmentUpdate`, содержащий обновлённый `Shipment` и принятый `Checkpoint`.

Добавь в domain `Shipment` immutable ordered history `List<Checkpoint>`. Не возвращай наружу изменяемый список.

Создание shipment должно сразу добавлять checkpoint `CREATED`: shipment ID, location равен origin, timestamp создаётся сервером.

Разрешённые переходы:

| Current | Allowed next |
|---|---|
| `CREATED` | `IN_TRANSIT`, `CANCELLED` |
| `IN_TRANSIT` | `OUT_FOR_DELIVERY`, `CANCELLED` |
| `OUT_FOR_DELIVERY` | `DELIVERED`, `CANCELLED` |
| `DELIVERED` | — |
| `CANCELLED` | — |

Повтор текущего статуса также запрещён.

### 3. Domain/application failures

Введи отдельные exception types либо один тип с явным category для четырёх случаев:

- invalid input;
- shipment not found;
- duplicate tracking number;
- invalid status transition.

Исключения не должны зависеть от `io.grpc.Status`. Application layer сообщает смысл ошибки, gRPC layer выбирает transport code.

Descriptions должны быть безопасными и стабильными. Минимально включай имя проблемного поля или операции; не включай stack trace и внутренние имена repository classes.

### 4. Application validation

Для `create`:

- `trackingNumber`, `origin`, `destination` не `null` и не blank;
- tracking number уникален;
- создание и регистрация tracking number атомарны.

Для `getById`:

- ID не `null` и не blank;
- неизвестный ID приводит к domain `not found` failure.

Добавь operation:

```java
ShipmentUpdate addCheckpoint(
    String shipmentId,
    ShipmentStatus status,
    String location,
    Instant occurredAt)
```

Она проверяет обязательные данные, существование shipment и разрешённость перехода, затем атомарно сохраняет новый status и добавляет checkpoint в конец истории.

### 5. Repository atomicity

Расширь repository двумя операциями с чёткой семантикой:

- сохранить shipment, только если его tracking number ещё не зарегистрирован;
- атомарно обновить существующий shipment по ID с помощью переданной update function.

Выбери подходящие Java-сигнатуры самостоятельно и объясни их на ревью. Не реализуй гарантию через последовательность `exists` → `save`: между этими вызовами другой поток может сохранить тот же tracking number.

Для небольшого in-memory repository допустима короткая синхронизированная секция при согласованном обновлении индекса tracking numbers и основной map. Обновление одного существующего shipment должно использовать атомарную операцию map, например `compute`/`computeIfPresent`.

### 6. gRPC mapping

Расширь mapper:

- явно отобрази все domain statuses в proto statuses;
- преобразуй `Instant` ↔ `google.protobuf.Timestamp`;
- преобразуй domain checkpoint, history и `ShipmentUpdate` в generated messages;
- не используй `Enum.valueOf` и совпадение имён как контракт.

Transport-level invalid input:

- `checkpoint` отсутствует в `AddCheckpointRequest`;
- status равен `SHIPMENT_STATUS_UNSPECIFIED` или `UNRECOGNIZED`;
- `occurred_at` отсутствует или не преобразуется в допустимый `Instant`.

Эти случаи должны завершаться `INVALID_ARGUMENT`, не доходя до repository.

### 7. Error mapping

Отобрази ожидаемые failures:

| Application result | gRPC code |
|---|---|
| invalid input | `INVALID_ARGUMENT` |
| shipment not found | `NOT_FOUND` |
| duplicate tracking number | `ALREADY_EXISTS` |
| invalid status transition | `FAILED_PRECONDITION` |

Можно использовать отдельный mapper/helper и Mutiny `onFailure().transform(...)`. Не перехватывай все `Throwable` как ожидаемую ошибку: неожиданный дефект не должен маскироваться под `INVALID_ARGUMENT`.

### 8. Tests

Создай unit test `ShipmentTransitionsTest` для полной таблицы переходов, включая terminal states и повтор статуса.

Создай integration test `ShipmentValidationGrpcTest`, использующий `@GrpcClient`. Покрой минимум:

- blank tracking number → `INVALID_ARGUMENT`;
- duplicate tracking number → `ALREADY_EXISTS`;
- blank shipment ID → `INVALID_ARGUMENT`;
- unknown shipment ID → `NOT_FOUND`;
- успешный `AddCheckpoint` возвращает `ShipmentEvent`, новый status и историю `[CREATED, IN_TRANSIT]`;
- запрещённый переход `CREATED → DELIVERED` → `FAILED_PRECONDITION`;
- отсутствующий checkpoint → `INVALID_ARGUMENT`;
- `SHIPMENT_STATUS_UNSPECIFIED` → `INVALID_ARGUMENT`;
- неизвестный shipment в checkpoint → `NOT_FOUND`;
- terminal shipment отклоняет дальнейший переход;
- два конкурентных одинаковых перехода `CREATED → IN_TRANSIT`: ровно один успешен, второй получает `FAILED_PRECONDITION`, в истории только один `IN_TRANSIT` checkpoint.

Для проверки ошибки перехватывай `StatusRuntimeException` и сравнивай `exception.getStatus().getCode()`. Каждый reactive вызов ограничивай конечным timeout. Не используй `Thread.sleep`.

## Рекомендуемый порядок работы

1. Расширь proto и выполни `mvn compile`.
2. Напиши failing unit tests переходов.
3. Реализуй domain history и правила переходов.
4. Напиши failing integration tests error contract.
5. Добавь repository atomic operations и application validation.
6. Расширь mapper и реализуй `AddCheckpoint`.
7. Добавь gRPC error mapping.
8. Выполни `mvn test`.

## Архитектурные ограничения

- Domain/application/repository не импортируют protobuf, gRPC или Mutiny.
- gRPC codes выбираются только в transport package.
- Нельзя хранить generated protobuf objects в repository.
- Нельзя полагаться на одинаковые имена domain/proto enum.
- Нельзя выполнять составной read-check-write без атомарной repository operation.
- Не добавляй server streaming: он относится к Stage 3.

## Что прислать на ревью

Напиши, что Stage 2 готов, приложи результат `mvn test` и ответь:

1. Почему duplicate check нельзя надёжно реализовать как `existsByTrackingNumber()` перед `save()`?
2. Где проходит граница между `INVALID_ARGUMENT` и `FAILED_PRECONDITION`?
3. Каким образом repository гарантирует атомарность двух конкурентных переходов одного shipment?

## Критерии приёмки

- полный `mvn test` проходит;
- happy path Stage 1 остаётся зелёным;
- все четыре ожидаемых gRPC status codes доказаны integration tests;
- история начинается с `CREATED` и сохраняет порядок checkpoint;
- все разрешённые и запрещённые переходы доказаны unit tests;
- duplicate registration и status update атомарны;
- concurrent transition test детерминирован и не использует sleep;
- public descriptions не раскрывают внутренние детали;
- generated files из `target` не отслеживаются;
- server streaming ещё не реализован.
