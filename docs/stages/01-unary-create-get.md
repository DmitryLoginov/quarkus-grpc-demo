# Stage 1 — Unary create/get

## Цель

Реализовать два unary RPC из существующего контракта: создать отправление и получить его по ID. В конце этапа интеграционный тест должен обращаться к запущенному Quarkus gRPC-серверу через generated Mutiny client.

На этом этапе проверяем только happy path. Валидация, duplicate tracking number, `NOT_FOUND` и другие публичные ошибки относятся к Stage 2.

## Краткая теория

Unary RPC принимает один request и завершает вызов одним response либо failure. В generated Mutiny API этот результат представлен `Uni<T>`: он ленивый и при подписке выдаёт не более одного item или failure.

Класс с `@GrpcService` становится серверной реализацией. Он реализует generated `ShipmentService`, принимает protobuf messages и возвращает protobuf messages. Generated-классы относятся к transport boundary: protobuf хорошо описывает wire contract, но не должен становиться внутренней доменной моделью.

Поэтому поток вызова будет таким:

```text
generated request
  -> ShipmentGrpcService
  -> ShipmentApplicationService
  -> ShipmentRepository
  -> domain Shipment
  -> ShipmentGrpcMapper
  -> generated response
```

Generated protobuf messages immutable. Их создают через builder. Domain model можно представить Java record: он не зависит от gRPC, Mutiny или protobuf.

In-memory repository вызывается синхронно и не блокирует поток на I/O. Его состояние разделяется между запросами, поэтому используемые коллекции должны быть потокобезопасными.

## Материалы

Прочитай разделы, необходимые для этого этапа:

- [Quarkus: Implementing a service with the Mutiny API](https://quarkus.io/guides/grpc-service-implementation/#implementing-a-service-with-the-mutiny-api)
- [Quarkus: Consuming a gRPC service](https://quarkus.io/guides/grpc-service-consumption/)
- [Quarkus: Testing your services](https://quarkus.io/guides/grpc-service-implementation/#testing-your-services)
- [SmallRye Mutiny: Uni and Multi](https://smallrye.io/smallrye-mutiny/latest/reference/uni-and-multi/)
- [SmallRye Mutiny: Creating Uni pipelines](https://smallrye.io/smallrye-mutiny/latest/tutorials/creating-uni-pipelines/)

## Техническое задание

### 1. Domain model

Создай package `dev.ldv.shipment.domain`.

`ShipmentStatus` должен быть внутренним enum. Пока ему достаточно одного значения `CREATED`. Не используй generated `dev.ldv.shipment.grpc.ShipmentStatus` за пределами gRPC mapper/service.

`Shipment` должен быть immutable record со следующими компонентами:

```java
String id
String trackingNumber
String origin
String destination
ShipmentStatus status
```

На этом этапе domain model не выполняет валидацию и не содержит методов перехода статуса.

### 2. Repository

Создай package `dev.ldv.shipment.repository`.

Интерфейс `ShipmentRepository` объявляет:

```java
Shipment save(Shipment shipment)
Optional<Shipment> findById(String id)
```

`InMemoryShipmentRepository`:

- является CDI bean;
- использует потокобезопасное хранилище;
- сохраняет и возвращает immutable domain objects;
- не зависит от generated protobuf types;
- не реализует duplicate check или очистку между тестами.

### 3. Application service

Создай package `dev.ldv.shipment.application` и CDI bean `ShipmentApplicationService`.

Публичные операции:

```java
Shipment create(String trackingNumber, String origin, String destination)
Shipment getById(String id)
```

`create`:

- генерирует непустой уникальный ID на стороне сервера;
- создаёт domain shipment со статусом `CREATED`;
- сохраняет его через repository;
- возвращает сохранённый объект.

`getById` возвращает существующий объект. Конкретный публичный gRPC status для отсутствующего ID пока не определяй: этот контракт появится в Stage 2.

Application service не импортирует классы из package `dev.ldv.shipment.grpc` и не возвращает `Uni`.

### 4. Mapper

Создай `dev.ldv.shipment.grpc.ShipmentGrpcMapper`.

Он преобразует domain `Shipment` в generated protobuf `Shipment`, включая явное отображение domain status в proto status. Обратное преобразование целого `Shipment` сейчас не требуется.

Mapper не генерирует ID, не обращается к repository и не содержит business rules.

### 5. gRPC service

Создай `dev.ldv.shipment.grpc.ShipmentGrpcService`.

Требования:

- класс помечен `@GrpcService`;
- реализует generated `dev.ldv.shipment.grpc.ShipmentService`;
- получает application service и mapper через CDI;
- `createShipment` делегирует создание application service и возвращает mapped response в `Uni`;
- `getShipment` делегирует чтение application service и возвращает mapped response в `Uni`;
- синхронная операция выполняется лениво при подписке на `Uni`;
- service не хранит отправления и не генерирует ID самостоятельно.

Не добавляй `@Blocking`: все операции этого этапа выполняются в памяти и не используют blocking I/O.

### 6. Integration tests

Создай `src/test/java/dev/ldv/shipment/grpc/ShipmentUnaryGrpcTest.java`.

Тестовый класс:

- использует `@QuarkusTest`;
- получает generated `ShipmentService` через `@GrpcClient`;
- обращается к gRPC API, а не создаёт `ShipmentGrpcService` вручную;
- ограничивает ожидание каждого `Uni` конечным timeout, например пятью секундами;
- не использует `Thread.sleep`.

Покрой два независимых сценария:

1. `createShipmentReturnsCreatedShipment`
   - отправить tracking number, origin и destination;
   - проверить непустой ID;
   - проверить все исходные поля;
   - проверить status `CREATED`.
2. `getShipmentReturnsPreviouslyCreatedShipment`
   - создать новое отправление через gRPC client;
   - получить его по возвращённому ID;
   - проверить равенство полученного и созданного protobuf message.

Используй разные tracking numbers в тестах. Repository reset hook пока не нужен.

## Рекомендуемый порядок работы

1. Напиши integration tests: сначала они не скомпилируются из-за отсутствующей реализации.
2. Добавь domain model и repository.
3. Добавь application service.
4. Добавь mapper и gRPC service.
5. Добейся прохождения только `ShipmentUnaryGrpcTest`.
6. Запусти полный `mvn test`.

## Архитектурные ограничения

- Generated protobuf classes используются только в package `dev.ldv.shipment.grpc` и в integration tests.
- Domain, application и repository packages не импортируют gRPC, protobuf или Mutiny.
- gRPC service содержит только transport adaptation и orchestration.
- Состояние хранится только в repository.
- Не реализуй validation и error mapping заранее.

## Что прислать на ревью

Напиши, что Stage 1 готов, приложи результат `mvn test` и ответь своими словами:

1. Почему generated protobuf `Shipment` не используется как domain entity?
2. Почему синхронный вызов application service помещён в supplier для `Uni`, а не выполнен до создания `Uni`?
3. Как выбранная структура repository защищает данные от конкурентного доступа?

## Критерии приёмки

- `mvn test` завершается с exit code `0`;
- оба тестовых сценария вызывают настоящий gRPC server через generated client;
- returned shipment имеет server-generated ID и статус `CREATED`;
- повторное чтение возвращает созданный snapshot;
- domain/application/repository не зависят от generated types;
- repository использует потокобезопасное хранилище;
- service реализует generated Mutiny interface и не блокирует event loop;
- в diff нет generated files из `target`;
- Stage 2 validation и error mapping не реализованы заранее.
