package dev.kastapi.errors

/**
 * Todos los textos de error de KastAPI (en español en v0.1) viven aquí.
 *
 * Centralizarlos facilita la internacionalización posterior (ver ROADMAP, v0.2).
 */
internal object Messages {
    // Mensajes de validación para el cliente de la API.
    const val REQUIRED = "es requerido"
    const val NOT_NULL = "no puede ser nulo"
    const val NOT_BLANK = "no puede estar vacío"
    const val EMAIL = "debe ser un correo electrónico válido"
    const val PATTERN = "no cumple el formato requerido"
    const val UNKNOWN_FIELD = "campo desconocido"
    const val UNIT_CHARACTERS = "caracteres"
    const val UNIT_ITEMS = "elementos"
    const val EXPECTED_TEXT = "debe ser un texto"
    const val EXPECTED_INTEGER = "debe ser un número entero"
    const val EXPECTED_DECIMAL = "debe ser un número decimal"
    const val EXPECTED_BOOLEAN = "debe ser true o false"
    const val EXPECTED_OBJECT = "debe ser un objeto"
    const val EXPECTED_ARRAY = "debe ser una lista"
    const val INTERNAL_ERROR = "Error interno del servidor"
    const val MALFORMED_JSON = "El cuerpo de la petición no es un JSON válido"
    const val INVALID_BODY = "El cuerpo de la petición no se pudo interpretar"

    fun payloadTooLarge(maxBodyBytes: Long): String = "El cuerpo de la petición supera el máximo de $maxBodyBytes bytes"

    fun expectedOneOf(values: Collection<String>): String = "debe ser uno de: ${values.joinToString(", ")}"

    fun min(value: Long): String = "debe ser mayor o igual a $value"

    fun max(value: Long): String = "debe ser menor o igual a $value"

    /** [unit] es "caracteres" o "elementos". */
    fun length(
        min: Int,
        max: Int,
        unit: String,
    ): String =
        when {
            min > 0 && max < Int.MAX_VALUE -> "debe tener entre $min y $max $unit"
            min > 0 -> "debe tener al menos $min $unit"
            else -> "debe tener como máximo $max $unit"
        }

    fun notFound(
        method: String,
        path: String,
    ): String = "La ruta $method $path no existe"

    fun methodNotAllowed(
        method: String,
        path: String,
    ): String = "El método $method no está permitido en $path"

    fun validationSummary(count: Int): String =
        if (count == 1) {
            "La petición tiene 1 error de validación"
        } else {
            "La petición tiene $count errores de validación"
        }

    // Mensajes de configuración para quien desarrolla con KastAPI (se reportan al arrancar, ver StartupValidator).
    // Cada problema se muestra después de "METHOD /ruta: ", que añade el validador.
    const val RESERVED_ROUTE = "la ruta está reservada para la documentación de la API."
    const val APPLIES_TO_NUMBER = "tipos numéricos"
    const val APPLIES_TO_TEXT = "String"
    const val APPLIES_TO_TEXT_OR_LIST = "String y List"

    fun configurationProblems(problems: List<String>): String {
        val header =
            if (problems.size == 1) {
                "KastAPI encontró 1 problema de configuración:"
            } else {
                "KastAPI encontró ${problems.size} problemas de configuración:"
            }
        return problems.mapIndexed { index, problem -> " ${index + 1}. $problem" }.joinToString("\n", "$header\n")
    }

    fun inputMustBeClass(typeName: String): String =
        "la entrada '$typeName' debe ser una data class @Serializable o NoInput."

    fun pathParameterWithoutProperty(
        name: String,
        typeName: String,
    ): String = "el parámetro {$name} de la ruta no existe como propiedad de '$typeName'."

    fun pathPropertyNullable(name: String): String = "la propiedad de path '$name' no puede ser nullable."

    fun pathPropertyHasDefault(name: String): String = "la propiedad de path '$name' no puede tener valor por defecto."

    fun duplicateRoute(first: String): String = "la ruta está duplicada: ya se declaró como $first."

    fun incompatibleAnnotation(
        annotation: String,
        property: String,
        typeName: String,
        appliesTo: String,
    ): String = "la anotación @$annotation en '$property' (tipo '$typeName') solo aplica a $appliesTo."

    fun invalidHttpErrorStatus(status: Int): String =
        "El status de HttpError debe estar entre 400 y 599, pero fue $status"

    // Mensaje para el log del servidor (no se envía al cliente).
    fun unhandledException(
        method: String,
        path: String,
    ): String = "Excepción no controlada en $method $path"

    fun invalidPattern(
        property: String,
        regex: String,
    ): String = "la anotación @Pattern de '$property' tiene una expresión regular inválida: $regex"

    fun unsupportedParameter(
        property: String,
        typeName: String,
    ): String = "la propiedad '$property' (tipo '$typeName') no se puede enlazar desde el path o el query."
}
