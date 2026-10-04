# AGENTS.md

## Назначение репозитория

Это учебный проект по gRPC в Quarkus. Пользователь пишет production- и test-код. Ассистент объясняет необходимую теорию, выдаёт ТЗ по одному этапу, проверяет изменения и ведёт контрольные точки.

## Источники требований

- Дизайн: `docs/superpowers/specs/2026-10-03-shipment-tracker-grpc-learning-design.md`
- План: `docs/superpowers/plans/2026-10-03-shipment-tracker-grpc-learning.md`
- Текущее ТЗ: `docs/stages/02-validation-status-errors.md`

При расхождении конкретное ТЗ текущего этапа уточняет план, а дизайн определяет границы проекта. Новое решение, меняющее дизайн, сначала согласовывается с пользователем и фиксируется в документации.

## Текущее состояние

- Current stage: `Stage 2 — Validation and status errors`
- Status: `READY`
- Owner of next action: `USER`
- Current assignment: `docs/stages/02-validation-status-errors.md`
- Next action: закоммитить принятый Stage 1, затем выполнить `docs/stages/02-validation-status-errors.md`.
- Last completed checkpoint: Stage 1 — unary create/get через in-memory repository.
- Last updated: `2026-10-04`

Допустимые статусы этапа: `READY`, `IN_PROGRESS`, `IN_REVIEW`, `CHANGES_REQUESTED`, `DONE`.

## Контрольные точки

| Stage | Результат | Статус |
|---:|---|---|
| 0 | Quarkus build и генерация кода из первого proto | DONE |
| 1 | Unary create/get через in-memory repository | DONE |
| 2 | Validation, `AddCheckpoint` и gRPC statuses | READY |
| 3 | Server streaming и cancellation cleanup | BLOCKED_BY_STAGE_2 |
| 4 | Client streaming и partial rejection | BLOCKED_BY_STAGE_3 |
| 5 | Bidirectional courier session | BLOCKED_BY_STAGE_4 |
| 6 | Metadata, deadlines и cancellation propagation | BLOCKED_BY_STAGE_5 |
| 7 | Сквозной journey test и README | BLOCKED_BY_STAGE_6 |

Опциональные PostgreSQL, TLS и отдельный client module не входят в основной трек и не получают статус до отдельного согласования.

## Правила работы ассистента

### Перед реализацией этапа

- Убедиться, что предыдущий checkpoint имеет статус `DONE`.
- Дать только теорию, ссылки и ТЗ, нужные текущему этапу.
- Не раскрывать готовую реализацию задания без прямого запроса пользователя.
- Не переходить к следующему этапу до закрытия текущего.

### Во время ревью

- Сначала прочитать пользовательский diff и связанные файлы.
- Запустить минимальный набор релевантных проверок, затем `mvn test` или `mvn verify`, если это нужно для checkpoint.
- Сообщать findings в порядке серьёзности: blocker, major, minor.
- Для каждого finding указывать файл, строку, наблюдаемое поведение и ожидаемое исправление.
- Проверять контракт, корректность Quarkus/Mutiny, concurrency, ошибки, тесты и читаемость.
- Не изменять production- или test-код пользователя, пока пользователь прямо не попросит исправить его.
- Если существенных замечаний нет, явно отметить checkpoint как принятый.

### После ревью

- При наличии blocker/major установить `CHANGES_REQUESTED` и сохранить текущий этап.
- После успешного ревью установить текущему этапу `DONE`, следующему `READY`, обновить Current stage, Next action и Current assignment.
- Для нового этапа создать отдельный файл `docs/stages/NN-<name>.md`.
- Перед каждым коммитом `AGENTS.md`, плана или спецификации получить явное согласование пользователя.
- Коммиты с пользовательским кодом не создавать без прямого запроса пользователя.

## Технические ограничения

- Java 21.
- Maven и Quarkus 3.40.1 LTS.
- Один модуль в основном треке.
- Quarkus extensions получают версии через BOM.
- Proto source: `src/main/proto`.
- Generated sources: `target`; их нельзя редактировать или коммитить.
- Основной API: generated Mutiny API (`Uni`, `Multi`).
- До опционального persistence stage используется потокобезопасное in-memory storage.
- Blocking I/O нельзя выполнять на event-loop thread.
- Integration tests используют generated `@GrpcClient`, а не прямой вызов service class.

## Основные команды

```powershell
mvn clean compile
mvn test
mvn clean verify
mvn quarkus:dev
```

Выбирать команду нужно пропорционально текущему изменению. Успех нельзя заявлять без свежего вывода соответствующей команды.

## Git

- Сохранять пользовательские изменения и не трогать несвязанные файлы.
- Не добавлять `target`, IDE metadata, secrets или локальные сертификаты.
- Рекомендуемый commit после принятия Stage 0: `build: configure Quarkus gRPC code generation`.
- Для следующих stages использовать короткие сообщения, описывающие принятый checkpoint.
