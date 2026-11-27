# 7. Logging and request tracing

## What KastAPI logs

KastAPI writes only two kinds of log entries, both on the server and never in a response body:

| When | Level | Logger | Message |
| --- | --- | --- | --- |
| A handler throws an unhandled exception (the client gets a `500`) | `ERROR` | the application's logger (`io.ktor.server.Application`) | `Unhandled exception in GET /path`, with the stack trace |
| A `@Pattern` match is stopped by `patternTimeoutMillis` | `WARN` | `dev.kastapi.validation.Pattern` | the property name and the time limit; never the value |

Nothing else is logged: not a successful request, not a `404` or `405`, and not a validation error (`400`, `413`, `415`, `422`). Those are answers to a wrong request, not a server problem. An `HttpError` that you throw is not logged either.

## Choose a logging implementation

`kastapi-core` does not ship an SLF4J implementation. Without one on the classpath, the two entries above are not written anywhere. Add one, for example Logback:

```kotlin
runtimeOnly("ch.qos.logback:logback-classic:1.6.8")
```

With Logback, `src/main/resources/logback.xml` sets the output and the levels. This one also prints a request id (see below) and silences Netty's debug output:

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level [%X{call-id}] %logger{20} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="INFO"><appender-ref ref="STDOUT"/></root>
</configuration>
```

To silence the `@Pattern` warning, set the level of its logger to `OFF`:

```xml
<logger name="dev.kastapi.validation.Pattern" level="OFF"/>
```

## Log every request

KastAPI does not log requests, but Ktor has a plugin for that. Add `io.ktor:ktor-server-call-logging` and install `CallLogging` **in your own server**, before `installKastApi`:

```kotlin
embeddedServer(Netty, port = 8080) {
    install(CallLogging) { level = Level.INFO }   // org.slf4j.event.Level
    installKastApi {
        get<NoInput, Health>("/health") { Health("ok") }
    }
}.start(wait = true)
```

You need your own `embeddedServer` for this, so add an engine such as `io.ktor:ktor-server-netty`. `kastApi(...)` starts its own server and does not let you install plugins first (see [Integrating with Ktor](06-ktor-integration.md)).

The output has one line per request, with the status, the method, the path and the time, including the `4xx` that KastAPI itself answers:

```
INFO  Application - 200 OK: GET - /ok in 31ms
INFO  Application - 404 Not Found: GET - /nope in 3ms
INFO  Application - 422 Unprocessable Entity: POST - /evil in 9ms
```

## Follow a request with an id

Add `io.ktor:ktor-server-call-id` to give each request an id, take it from a header when the caller sends one, and copy it into the log:

```kotlin
embeddedServer(Netty, port = 8080) {
    install(CallId) {
        header("X-Request-Id")                          // use the caller's id if it sends one
        generate { java.util.UUID.randomUUID().toString() }  // otherwise create one
        verify { it.isNotEmpty() }
    }
    install(CallLogging) {
        level = Level.INFO
        callIdMdc("call-id")                            // makes %X{call-id} work in the pattern
    }
    installKastApi { /* routes */ }
}.start(wait = true)
```

With the Logback pattern above, the id appears on every line of the request, including the entries KastAPI writes:

```
INFO  [abc-123] Application - 200 OK: GET - /ok in 1ms
ERROR [boom-1] Application - Unhandled exception in GET /boom
INFO  [boom-1] Application - 500 Internal Server Error: GET - /boom in 4ms
WARN  [evil-1] Pattern - the @Pattern annotation on 'Evil.text' took longer than 100 ms and the value was rejected
```

So a `500` that a user reports can be found with the id they have, if you also return it. KastAPI does not add the id to its responses; use Ktor's `CallId` option to send it back in a header if you need that.

## Metrics

KastAPI does not collect metrics. Ktor has plugins for that, for example the Micrometer one. Install them on your own server in the same way; see the [Ktor documentation](https://ktor.io/docs/server-metrics-micrometer.html).
