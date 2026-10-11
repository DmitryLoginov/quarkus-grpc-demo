# Stage 3 — Server streaming and cancellation cleanup

## Цель

Добавить server-streaming RPC `WatchShipment`. Подписчик должен сразу получить актуальное состояние shipment, затем — все принятые изменения в правильном порядке. Терминальный статус завершает stream, а отмена со стороны клиента удаляет только его подписку и не оставляет ресурсов в publisher.

## Краткая теория

Unary RPC возвращает один результат или ошибку. Server-streaming RPC принимает один request и возвращает последовательность из нуля или более сообщений. В generated Mutiny API такой метод возвращает `Multi<T>`.

`Multi` ленив: pipeline начинает работу для конкретного клиента при subscription. У каждой подписки собственный lifecycle:

- `item` передаёт очередное событие;
- `failure` аварийно завершает stream;
- `completion` штатно сообщает, что событий больше не будет;
- `cancellation` идёт от downstream к upstream и требует освободить связанные с подписчиком ресурсы.

Поток watcher сочетает два свойства. Первое сообщение является snapshot, доступным на момент подписки. Следующие сообщения поступают из hot source: они появляются независимо от конкретного подписчика и должны рассылаться всем активным watcher этого shipment.

Backpressure означает, что downstream сообщает, сколько элементов готов принять. Hot publisher не должен бесконечно накапливать события для медленного клиента. Для этого этапа достаточно выбрать ограниченную политику: медленный подписчик может получить failure и должен быть удалён; не используй неограниченный буфер.

Регистрация подписчика и выбор initial snapshot образуют составную операцию. Если update попадёт между чтением snapshot и регистрацией, событие нельзя потерять или доставить дважды. Аналогично, события конкурентно принятых updates должны выходить в том же порядке, в каком они представлены в итоговой history shipment.

## Материалы

- [Quarkus: Implementing a gRPC Service — Handling streams](https://quarkus.io/guides/grpc-service-implementation/#handling-streams)
- [SmallRye Mutiny: Creating Multi pipelines](https://smallrye.io/smallrye-mutiny/latest/tutorials/creating-multi-pipelines/)
- [SmallRye Mutiny: Broadcasting to multiple subscribers](https://smallrye.io/smallrye-mutiny/latest/guides/broadcasting-to-multiple-subscribers/)
- [SmallRye Mutiny: Hot streams](https://smallrye.io/smallrye-mutiny/latest/guides/hot-streams/)
- [Reactive Streams specification: backpressure and cancellation](https://github.com/reactive-streams/reactive-streams-jvm/blob/master/README.md)

## Техническое задание

### 1. Proto-контракт

Добавь request:

```proto
message WatchShipmentRequest {
  string shipment_id = 1;
}
```

Добавь в `ShipmentService`:

```proto
rpc WatchShipment(WatchShipmentRequest) returns (stream ShipmentEvent);
```

Не меняй существующие field numbers.

### 2. Семантика stream

Для существующего shipment:

1. Первым элементом watcher получает `ShipmentEvent` с полным актуальным `Shipment` и последним checkpoint этого snapshot.
2. Каждый последующий успешно принятый `AddCheckpoint` создаёт один следующий `ShipmentEvent`.
3. Порядок событий совпадает с порядком checkpoint в сохранённой history.
4. Событие с `DELIVERED` или `CANCELLED` сначала передаётся клиенту, затем stream штатно завершается.

Если shipment уже терминальный при подписке, отправь один initial event и сразу заверши stream.

Для blank `shipment_id` верни `INVALID_ARGUMENT` до первого item. Для неизвестного ID верни `NOT_FOUND` до первого item.

### 3. Application publisher

Создай `ShipmentEventPublisher` в application package со следующим публичным контрактом:

```java
Multi<ShipmentUpdate> watch(String shipmentId);

void publish(ShipmentUpdate update);
```

Транспортные protobuf-типы и gRPC status types в этот класс не передавай.

Publisher должен:

- хранить последнее опубликованное состояние каждого созданного shipment;
- создавать отдельный subscription lifecycle для каждого вызова `watch`;
- атомарно относительно публикации регистрировать watcher и отдавать ему initial snapshot;
- рассылать update всем активным watcher соответствующего shipment;
- сохранять порядок accepted updates;
- после terminal update завершать и удалять все subscriptions shipment;
- при cancellation удалять только отменённого watcher;
- не использовать неограниченный буфер для медленного subscriber.

Вызови `publish` после успешного создания shipment и после каждого успешного `addCheckpoint`. Ошибочный update ничего не публикует.

### 4. gRPC mapping

Реализуй generated Mutiny-метод:

```java
Multi<ShipmentEvent> watchShipment(WatchShipmentRequest request)
```

gRPC слой валидирует transport request, делегирует application publisher и преобразует каждый domain `ShipmentUpdate` через mapper. Не блокируй event-loop и не создавай собственный thread на подписку.

Failure до первого item должен пройти через существующий error mapping. Cancellation не преобразуется в application error и не логируется как server failure.

### 5. Наблюдаемость cleanup

Добавь минимальный потокобезопасный способ проверить количество активных watchers конкретного shipment. Он может быть package-private и предназначаться для unit test publisher; не добавляй его в proto API.

Проверка cleanup должна ожидать конкретный lifecycle-сигнал или условие с конечным timeout. Не используй `Thread.sleep`.

### 6. Тесты

Создай `WatchShipmentGrpcTest` с `@QuarkusTest` и generated `@GrpcClient`. Покрой:

- blank ID → `INVALID_ARGUMENT` до первого item;
- unknown ID → `NOT_FOUND` до первого item;
- initial snapshot содержит `CREATED` и полную history;
- watcher получает последовательные `IN_TRANSIT` и `OUT_FOR_DELIVERY` updates в порядке добавления;
- terminal event доставляется, после чего stream завершается;
- подписка на уже terminal shipment получает snapshot и завершается;
- два watcher одного shipment получают одинаковые updates;
- отмена одного watcher освобождает его subscription и не завершает второго.

Добавь небольшой unit test `ShipmentEventPublisherTest` для детерминированной проверки cancellation cleanup и выбранной политики медленного subscriber. Используй latches, subscriber probes или другие сигналы состояния с timeout, без произвольных задержек.

Сохрани все тесты Stage 0–2 зелёными.

## Архитектурные ограничения

- Domain/application/repository не импортируют protobuf или gRPC API.
- Один publisher не должен создавать отдельный глобальный источник событий для каждого watcher.
- Cancellation одного watcher не завершает остальных.
- После cancellation или terminal completion publisher не удерживает emitter/subscriber.
- Не применяй `@Blocking` к stream только для изменения thread scheduling.
- Не добавляй client streaming или bidirectional streaming: они относятся к следующим этапам.

## Что прислать на ревью

Напиши, что Stage 3 готов, приложи результат `mvn test` и ответь:

1. Чем completion отличается от cancellation и кто инициирует каждый сигнал в твоей реализации?
2. Как исключена потеря update между initial snapshot и регистрацией watcher?
3. Где и как удаляется cancelled subscriber?
4. Что происходит с медленным subscriber, который не успевает запрашивать элементы?

## Критерии приёмки

- полный `mvn test` проходит;
- watcher первым получает актуальный snapshot;
- принятые updates наблюдаются ровно один раз и в порядке shipment history;
- terminal event доставляется до completion;
- blank и unknown ID сохраняют error contract Stage 2;
- cancellation детерминированно освобождает subscription;
- два watcher изолированы по lifecycle;
- publisher не содержит unbounded buffering и не теряет update на границе регистрации.
