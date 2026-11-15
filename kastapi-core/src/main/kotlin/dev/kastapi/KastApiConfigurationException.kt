package dev.kastapi

/**
 * Se lanza al instalar KastAPI cuando la configuración de las rutas tiene problemas.
 *
 * El mensaje lista **todos** los problemas encontrados, cada uno con la ruta a la que afecta. Con
 * [KastApiConfig.validateOnStartup] en `true` se comprueba, entre otras cosas, que:
 * - cada `{param}` de la ruta exista como propiedad de la entrada;
 * - las propiedades de path no sean nullable ni tengan valor por defecto;
 * - las propiedades de path y query tengan un tipo soportado;
 * - no haya dos rutas con el mismo método y la misma ruta;
 * - las anotaciones de validación estén en tipos compatibles.
 */
public class KastApiConfigurationException internal constructor(
    message: String,
) : RuntimeException(message)
