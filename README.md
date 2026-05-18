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
    implementation("io.github.jadevance:jiffy-core:0.1.0")
}
```

Maven:
```xml
<dependency>
    <groupId>io.github.jadevance</groupId>
    <artifactId>jiffy-core</artifactId>
    <version>0.1.0</version>
</dependency>
```

sbt:
```scala
libraryDependencies += "io.github.jadevance" % "jiffy-core" % "0.1.0"
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

Emits one structured event on close:

```
[2026-05-15 14:23:01.234Z] Application=my-app Level=Info Component=UserService Operation=CreateUser TimeElapsed=42.7 UserId=abc123 TimeElapsed_DbInsert=38.1
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
| `Time(key)`            | done  | returns `AutoCloseable`; emits `TimeElapsed_<key>` |
| `IncludeException`     | done  | escalates Level to Error, adds `ErrorReason="An exception has occurred"` (typo fixed from Spiffy original) |
| `SetToInfo/Warning/Error` | done | |
| `Suppress / SuppressFields` | done | |
| `Count(key)`           | done  | emits `Count_<key>` |
| `AppendToValue`        | done  | |
| `Configuration.Initialize` | done | `Configuration.initialize(...)` |
| `GlobalEventContext.Instance` | done | `GlobalEventContext.instance()` |
| `TimerCollection Timers` property | done | `timers()` returns an unmodifiable `Map<String, TimedScope>`; each scope exposes `elapsedMilliseconds()` and `isRunning()` |
| `PrivateData`          | done  | `setPrivate / getPrivate / containsPrivate / privateData()`; never emitted |
| `CustomTimestamp`      | done  | `setCustomTimestamp(Instant)`; overrides the event timestamp at emit time |
| `FieldName` lookup     | done  | Replaced by pluggable [`NamingConvention`](#naming-conventions); `NamingConvention.SPIFFY` (default) returns Spiffy canonical names, `NamingConvention.JAVA` returns camelCase |

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

## Naming conventions

Jiffy ships two naming conventions for **library-emitted standard fields** (`Level`, `Component`, `Operation`, `TimeElapsed`, `ErrorReason`, `Exception_Type`, …):

- `NamingConvention.SPIFFY` — **default**. Matches the .NET Spiffy library exactly so Splunk dashboards, alerts, and queries written against a Spiffy stack work unchanged.
- `NamingConvention.JAVA` — idiomatic Java camelCase (`level`, `component`, `timeElapsed`, `errorReason`, `exceptionType`, `timeElapsed_DbInsert`, `count_Hits`). For projects that don't share Splunk infrastructure with a Spiffy-based service and prefer Java conventions in their log output.

Select at configuration time:

```java
Configuration.initialize(c -> c
    .naming(NamingConvention.JAVA)
    .providers().slf4j()
);
```

**Scope**: only library-emitted standard fields go through the convention. User-provided keys passed to `set(...)` are emitted verbatim — your `ctx.set("UserId", 42)` always emits `UserId=42` regardless of the active convention. This keeps the convention boundary explicit and lets you adopt your own house style for domain fields.

Custom conventions: implement `NamingConvention` directly if you need snake_case, SCREAMING_SNAKE, or anything else.

## License

MIT, matching Spiffy.
