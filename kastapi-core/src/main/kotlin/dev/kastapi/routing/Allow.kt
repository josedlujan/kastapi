package dev.kastapi.routing

import io.ktor.http.HttpMethod

/** Methods in the order they are listed in an `Allow` header. */
private val ALLOW_ORDER =
    listOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete)

/** Matches a `{name}` segment, optionally with a `?` or `...` suffix. */
private val PARAMETER = Regex("\\{[^}]+}")

/**
 * The methods that KastAPI routes accept for the request [path]: those of every registered route whose path template
 * matches it.
 */
internal fun allowedMethods(
    routes: List<RouteDefinition>,
    path: String,
): List<HttpMethod> = routes.filter { matchesTemplate(it.path, path) }.map { it.method }

/**
 * The value of an `Allow` header for [methods], or `null` if there are none. Methods come in a fixed order and
 * `HEAD` is added after `GET`, because every `GET` route also answers `HEAD`.
 */
internal fun allowHeader(methods: Collection<HttpMethod>): String? =
    ALLOW_ORDER.filter { it in withImpliedHead(methods) }.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.value }

/**
 * Whether a request with [method] reached a path that KastAPI knows ([registered] is not empty) but with a method that
 * path does not accept. `HEAD` counts as accepted when `GET` is.
 */
internal fun isMethodMismatch(
    registered: Collection<HttpMethod>,
    method: HttpMethod,
): Boolean = registered.isNotEmpty() && method !in withImpliedHead(registered)

private fun withImpliedHead(methods: Collection<HttpMethod>): Set<HttpMethod> =
    methods.toSet().let { if (HttpMethod.Get in it) it + HttpMethod.Head else it }

/**
 * Whether the route [template] (`/users/{id}`, `/a/{id?}`, `/files/{path...}`) matches the request [path], the way
 * Ktor's routing would: literals are case sensitive, `{name}` matches exactly one non-empty segment, `{name?}` zero or
 * one trailing segment and `{name...}` any number of trailing segments.
 */
internal fun matchesTemplate(
    template: String,
    path: String,
): Boolean {
    val wanted = segments(template)
    val actual = segments(path)
    var index = 0
    for (segment in wanted) {
        when {
            segment.startsWith("{") && segment.endsWith("...}") -> {
                return true
            }

            segment.startsWith("{") && segment.endsWith("?}") -> {
                return actual.size == index || actual.size == index + 1
            }

            else -> {
                if (index >= actual.size || !segmentMatches(segment, actual[index])) return false
                index++
            }
        }
    }
    return index == actual.size
}

private fun segments(path: String): List<String> =
    path.trim('/').let {
        if (it.isEmpty()) emptyList() else it.split('/')
    }

private fun segmentMatches(
    template: String,
    actual: String,
): Boolean =
    when {
        template == "*" -> {
            actual.isNotEmpty()
        }

        '{' !in template -> {
            template == actual
        }

        // A segment that mixes literals and parameters, such as `user-{id}`.
        else -> {
            Regex(template.split(PARAMETER).joinToString("[^/]+") { Regex.escape(it) }).matches(actual)
        }
    }
