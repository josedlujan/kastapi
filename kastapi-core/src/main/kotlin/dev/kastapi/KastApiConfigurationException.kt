package dev.kastapi

/**
 * Thrown when installing KastAPI if the route configuration has problems.
 *
 * The message lists **all** the problems found, each with the route it affects. With
 * [KastApiConfig.validateOnStartup] set to `true`, it checks, among other things, that:
 * - each `{param}` of the route exists as a property of the input;
 * - path properties are neither nullable nor have a default value;
 * - path and query properties have a supported type;
 * - there are no two routes with the same method and the same path;
 * - validation annotations are on compatible types.
 */
public class KastApiConfigurationException internal constructor(
    message: String,
) : RuntimeException(message)
