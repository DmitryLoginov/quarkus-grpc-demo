# Stage 0 — Основа Quarkus gRPC-проекта

## Цель

Превратить пустой Maven-проект в минимальное Quarkus-приложение, которое генерирует Java и Mutiny API из `shipment.proto`. На этом этапе реализацию сервиса писать не нужно.

## Краткая теория

`.proto` — это исходный контракт. Из него `protoc` и gRPC plugins генерируют immutable message classes, service descriptors, обычные gRPC stubs и Mutiny API. В Quarkus генерация включается в стандартный Maven lifecycle: исходники лежат в `src/main/proto`, а результат появляется в `target/generated-sources/grpc`.

Quarkus BOM фиксирует совместимый набор версий расширений. Поэтому `quarkus-grpc`, `quarkus-junit5` и другие Quarkus dependencies не должны получать собственные версии.

Для сервиса Quarkus создаёт Mutiny-интерфейс по имени proto service. Позднее класс `ShipmentGrpcService` реализует этот интерфейс и получает `@GrpcService`. Unary RPC представлен методом, который принимает generated request и возвращает `Uni<GeneratedResponse>`.

Мы используем Vert.x gRPC server поверх общего HTTP-сервера. Это рекомендуемый Quarkus вариант и позволяет использовать единый порт и интеграцию с Dev UI.

## Материалы

Прочитай только указанные разделы:

- [Quarkus: Configuring your project](https://quarkus.io/guides/grpc-getting-started/#configuring-your-project)
- [Quarkus: Implementing a gRPC service — Generated Code](https://quarkus.io/guides/grpc-service-implementation/#generated-code)
- [Quarkus: Enabling gRPC code generation](https://quarkus.io/guides/grpc-generation-reference/#enabling-grpc-code-generation)
- [Quarkus: Selecting a gRPC server](https://quarkus.io/guides/grpc-reference/#selecting-a-grpc-server)
- [Protobuf: Java generated code — Messages](https://protobuf.dev/reference/java/java-generated/)

## Техническое задание

### 1. Maven build

Доработай существующий `pom.xml`:

- сохрани координаты `dev.ldv:quarkus-grpc-demo:1.0-SNAPSHOT`;
- сохрани Java 21;
- добавь импорт BOM `io.quarkus.platform:quarkus-bom:3.40.1`;
- добавь `io.quarkus:quarkus-grpc`;
- добавь `io.quarkus:quarkus-rest`, необходимый выбранному общему HTTP/gRPC server;
- добавь test dependency `io.quarkus:quarkus-junit5`;
- настрой `io.quarkus.platform:quarkus-maven-plugin` версии `${quarkus.platform.version}` как build extension с goals `build`, `generate-code` и `generate-code-tests`;
- настрой Maven Compiler Plugin с параметром `parameters=true`;
- настрой Maven Surefire Plugin для JUnit 5.

Не добавляй отдельные версии Quarkus dependencies, protobuf plugin или вручную выбранную версию `protoc`.

### 2. Proto contract

Создай `src/main/proto/shipment.proto` со следующими настройками:

- `syntax = "proto3"`;
- proto package: `shipment.v1`;
- `java_multiple_files = true`;
- Java package: `dev.ldv.shipment.grpc`;
- outer classname: `ShipmentProto`.

Определи enum `ShipmentStatus`:

| Name | Number |
|---|---:|
| `SHIPMENT_STATUS_UNSPECIFIED` | 0 |
| `CREATED` | 1 |
| `IN_TRANSIT` | 2 |
| `OUT_FOR_DELIVERY` | 3 |
| `DELIVERED` | 4 |
| `CANCELLED` | 5 |

Определи сообщения:

| Message | Fields |
|---|---|
| `CreateShipmentRequest` | `tracking_number`: string/1; `origin`: string/2; `destination`: string/3 |
| `GetShipmentRequest` | `shipment_id`: string/1 |
| `Shipment` | `id`: string/1; `tracking_number`: string/2; `origin`: string/3; `destination`: string/4; `status`: `ShipmentStatus`/5 |

Определи `ShipmentService` с двумя unary-методами:

- `CreateShipment(CreateShipmentRequest) returns (Shipment)`;
- `GetShipment(GetShipmentRequest) returns (Shipment)`.

Комментарии к service, RPC и messages приветствуются, но не обязательны. `java_generic_services` добавлять нельзя.

### 3. Server configuration

Создай `src/main/resources/application.properties` и выбери общий Vert.x HTTP/gRPC server через:

```properties
quarkus.grpc.server.use-separate-server=false
```

Другие параметры пока не добавляй.

### 4. Исследование generated API

Выполни:

```powershell
mvn clean compile
```

Найди в `target/generated-sources/grpc`:

- Mutiny-интерфейс `ShipmentService`;
- request/response types;
- обычный gRPC descriptor/stub class;
- descriptor container `ShipmentProto`.

Generated-файлы не редактируй и не добавляй в Git.

## Что прислать на ревью

Напиши, что Stage 0 готов, и своими словами ответь на три вопроса:

1. Какой generated type будет реализовывать наш будущий `ShipmentGrpcService`?
2. Как выглядит Java-сигнатура generated unary-метода `createShipment`?
3. Почему у Quarkus dependencies в `pom.xml` нет собственных версий?

Я проверю diff, выполню сборку и дам замечания с привязкой к файлам и строкам.

## Критерии приёмки

- `mvn clean compile` завершается с exit code `0`;
- проект использует Quarkus `3.40.1` и Java 21;
- generated `ShipmentService` содержит оба unary-метода;
- generated message fields соответствуют таблице и field numbers;
- server работает в unified Vert.x mode;
- в Git отсутствуют файлы из `target`;
- пользователь может объяснить назначение BOM и generated Mutiny service interface.

## Вне рамок этапа

Не реализуй Java service, repository, mapper, validation или тесты. Они начнутся на Stage 1 после ревью этой основы.
