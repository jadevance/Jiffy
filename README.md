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
| `TimerCollection Timers` property | — | not exposed; rarely used by callers |
| `PrivateData`          | —     | not yet |
| `CustomTimestamp`      | —     | not yet |
| `FieldName` lookup     | —     | not yet |

## License

MIT, matching Spiffy.
