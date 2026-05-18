<p align="center">
  <img src="docs/jiffy-logo.png" alt="Jiffy logo" width="280">
</p>

# Jiffy

[![Build](https://github.com/jadevance/Jiffy/actions/workflows/build.yml/badge.svg)](https://github.com/jadevance/Jiffy/actions/workflows/build.yml)

A Java port of [Spiffy.Monitoring](https://github.com/chris-peterson/Spiffy) — structured logging for the JVM with field names, output format, and idioms that match Spiffy line-for-line.

The goal is cross-stack fluency: a team running .NET services on Spiffy and JVM services on Jiffy can share Splunk dashboards, query patterns, and mental models with no translation step.

## Install

Published to [Maven Central](https://central.sonatype.com/artifact/io.github.jadevance/jiffy-core) as `io.github.jadevance:jiffy-core`.

Gradle:
```kotlin
dependencies {
    implementation("io.github.jadevance:jiffy-core:0.1.3")
}
```

Maven:
```xml
<dependency>
    <groupId>io.github.jadevance</groupId>
    <artifactId>jiffy-core</artifactId>
    <version>0.1.3</version>
</dependency>
```

sbt:
```scala
libraryDependencies += "io.github.jadevance" % "jiffy-core" % "0.1.3"
```

## Usage

```java
try (var ctx = new EventContext("UserService", "CreateUser")) {
    ctx.set("UserId", userId);
    try (var t = ctx.time("DbInsert")) {
        userRepository.insert(user);
    }
}
```

Emits one structured event on close (default `SpiffyNamingConvention`, matching Spiffy 7.x short field names):

```
[2026-05-15 14:23:01.234Z] Application=my-app l=Info c=UserService o=CreateUser ms=42.7 UserId=abc123 ms_DbInsert=38.1
```

## Configuration

```java
GlobalEventContext.instance().set("Application", "my-app");

Configuration.initialize(c -> c
    .providers().slf4j()        // default; routes via SLF4J at Level-appropriate severity
);
```

## Modules

- `jiffy-core` — `EventContext`, `GlobalEventContext`, `Configuration`, SLF4J + Console sinks
- (future) `jiffy-splunk` — direct HEC sink
- (future) `jiffy-micrometer` — counters/timers bridge

## Parity status

| Spiffy member          | Jiffy | Notes |
|------------------------|:-----:|-------|
| `EventContext(component, operation)` | done  | |
| `Set(key, value)`      | done  | `set(...)` |
| `Time(key)`            | done  | returns `AutoCloseable`; emits `<timeElapsed()>_<key>` (e.g. `ms_DbInsert` under default short convention) |
| `IncludeException`     | done  | escalates Level to Error, sets the reason to `"An exception has occurred"` (typo fixed from Spiffy original); emitted as `msg=` under short / `ErrorReason=` under legacy |
| `SetToInfo/Warning/Error` | done | |
| `Suppress / SuppressFields` | done | |
| `Count(key)`           | done  | emits `Count_<key>` |
| `AppendToValue`        | done  | |
| `Configuration.Initialize` | done | `Configuration.initialize(...)` |
| `GlobalEventContext.Instance` | done | `GlobalEventContext.instance()` |
| `TimerCollection Timers` property | done | `timers()` returns an unmodifiable `Map<String, TimedScope>`; each scope exposes `elapsedMilliseconds()` and `isRunning()` |
| `PrivateData`          | done  | `setPrivate / getPrivate / containsPrivate / privateData()`; never emitted |
| `CustomTimestamp`      | done  | `setCustomTimestamp(Instant)`; overrides the event timestamp at emit time |
| `IFieldNameLookup`     | done  | Pluggable [`NamingConvention`](#naming-conventions): `SPIFFY` (default, matches Spiffy 7.x `ShortFieldNameLookup`), `LEGACY` (matches Spiffy 6.x `LegacyFieldNameLookup`), `JAVA` (camelCase) |
| `Component` / `Operation` setters | done | `setComponent(String)` / `setOperation(String)` |
| `this[key]` indexer (read) | done | `get(String)` |
| `TrySet(key, Func, FieldConflict)` | done | overload available |
| `AddValues(KVP[])` / `AddValues(IEnumerable<KVP>)` | done | `addValues(Map<String, Object>)` |
| `IncludeException(t, keyPrefix)` | done | overload; default null uses the active `NamingConvention` |
| `SetToError()` / `SetToWarning()` no-reason overloads | done | parameterless variants |
| `IncludeStructure(obj, keyPrefix, includeNulls)` | done | `includeStructure(...)`; handles records via `RecordComponent`, POJOs via `BeanInfo` |
| `AutoTimer` (`ITimedContext`) | done | standalone `AutoTimer` class with `count()`, `resume()`, `startOver()`, `close()` |
| `Callbacks().BeforeLogging(...)` | done | `Configuration.callbacks().beforeLogging(Consumer<EventContext>)` |
| `Formatting()` API (timestamp / newlines / null value / special value / deprioritize) | done | `Configuration.formatting()` with `timestamp(...)`, `newlines(...)`, `nullValue(...)`, `specialValue(...)`, `deprioritizeValueLength(...)` |
| `UseLogfmt()` preset | done | `Configuration.useLogfmt()` |

## Advanced usage

**Private data** — stash values you want available during the event but kept out of the emitted log:

```java
try (var ctx = new EventContext("UserService", "CreateUser")) {
    ctx.setPrivate("RawToken", token);   // not emitted
    ctx.set("UserId", userId);           // emitted
}
```

**Custom timestamp** — useful when backfilling events from an upstream stream:

```java
try (var ctx = new EventContext("Replay", "Ingest")) {
    ctx.setCustomTimestamp(record.originalTimestamp());
}
```

**Timers collection** — introspect timers mid-event (e.g., to make routing decisions while a sub-operation is in flight):

```java
try (var ctx = new EventContext("Pipeline", "Run")) {
    var t = ctx.time("Stage1");
    // ... work ...
    if (ctx.timers().get("Stage1").elapsedMilliseconds() > 500) {
        ctx.set("SlowPath", true);
    }
    t.close();
}
```

**Include structure** — extract a record or POJO's public properties as fields in one call:

```java
record OrderEvent(String orderId, int items, BigDecimal total) {}

try (var ctx = new EventContext("Checkout", "Submit")) {
    ctx.includeStructure(order);                // orderId=..., items=..., total=...
    ctx.includeStructure(payment, "payment");   // payment_id=..., payment_amount=...
}
```

**Before-logging callback** — auto-attach cross-cutting fields without sprinkling code at every event site:

```java
Configuration.initialize(c -> {
    c.callbacks().beforeLogging(ctx -> {
        ctx.set("request_id", RequestScope.currentId());
    });
    c.providers().slf4j();
});
```

**AutoTimer** — a stopwatch for measurements outside an `EventContext` lifecycle (e.g., totalling time across N event-scoped operations):

```java
var timer = new AutoTimer();
for (var batch : batches) {
    timer.resume();
    process(batch);
    timer.close();
}
LOG.info("Processed {} batches in {} ms", timer.count(), timer.elapsedMilliseconds());
```

## Naming conventions

Jiffy ships three naming conventions for **library-emitted standard fields**:

| Convention | Level | Component | Operation | TimeElapsed | Reason | Per-timer | Count |
|---|---|---|---|---|---|---|---|
| `SPIFFY` *(default — matches Spiffy 7.x `ShortFieldNameLookup`)* | `l` | `c` | `o` | `ms` | `msg` *(error & warning collapse)* | `ms_<key>` | `Count_<key>` |
| `LEGACY` *(matches Spiffy 6.x `LegacyFieldNameLookup`)* | `Level` | `Component` | `Operation` | `TimeElapsed` | `ErrorReason` / `WarningReason` | `TimeElapsed_<key>` | `Count_<key>` |
| `JAVA` *(idiomatic camelCase for OSS Java users)* | `level` | `component` | `operation` | `timeElapsed` | `message` *(collapse)* | `timeElapsed_<key>` | `count_<key>` |

Exception fields under `SPIFFY` and `LEGACY` are hardcoded as in Spiffy itself: `Exception_Type`, `Exception_Message`, `Exception_StackTrace`, `InnermostException_*`. Under `JAVA` they are `exceptionType`, `exceptionMessage`, `exceptionStackTrace`, `innermostException*`.

Select at configuration time:

```java
Configuration.initialize(c -> c
    .naming(NamingConvention.LEGACY)   // or .SPIFFY (default) / .JAVA
    .providers().slf4j()
);
```

**Scope**: only library-emitted standard fields go through the convention. User-provided keys passed to `set(...)` are emitted verbatim — your `ctx.set("UserId", 42)` always emits `UserId=42` regardless of the active convention. This keeps the convention boundary explicit and lets you adopt your own house style for domain fields.

**msg-field collapse**: under `SPIFFY` and `JAVA`, error and warning reasons share one field (`msg` / `message`) — matching Spiffy's design. Switching the level via `setToInfo()` / `setToWarning()` / `setToError()` automatically clears any prior reason so the emitted `msg` always agrees with `l`.

Custom conventions: implement `NamingConvention` directly if you need snake_case, SCREAMING_SNAKE, or anything else.

## Output formatting

The wire format is configurable via `Configuration.formatting()`:

```java
Configuration.initialize(c -> {
    c.formatting()
        .timestamp("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")  // ISO 8601 with offset
        .nullValue("(null)")
        .newlines(NewlineFormatting.REPLACE_WITH_SPACE)
        .specialValue(SpecialValueFormatting.QUOTE)
        .deprioritizeValueLength(1000);              // long values move to end of line
    c.providers().slf4j();
});
```

`Configuration.useLogfmt()` is a preset that bundles logfmt-friendly defaults (these match Jiffy's defaults already, so it's documentation of intent more than configuration).

## License

MIT, matching Spiffy.
