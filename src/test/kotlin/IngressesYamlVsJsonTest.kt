import com.google.gson.Gson
import no.nav.saas.proxy.ingresses.IngressSet
import no.nav.saas.proxy.ingresses.Ingresses
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.io.File

class IngressesYamlVsJsonTest {

    private val gson = Gson()
    private val yaml = Yaml()

    // Helper function to fetch nodes from a yaml structure
    fun getNestedValue(yamlMap: Map<String, Any>, vararg keys: Any): Any? {
        var current: Any? = yamlMap
        for (key in keys) {
            current = when (current) {
                is Map<*, *> -> current[key] // Access map by key
                is List<*> -> {
                    if (key is String) {
                        // Map over list of maps to get specific key in each map
                        current.mapNotNull { (it as? Map<*, *>)?.get(key) }
                    } else {
                        return null
                    }
                }
                else -> return null // Return null if the path is invalid
            }
        }
        return current
    }

    @Test
    fun `all ingresses in JSON should match external hosts in YAML`() {
        listOf("dev", "prod").forEach { env ->
            val ingressSet: IngressSet = Ingresses.parse("/ingresses/$env.json")

            val jsonIngressUrls = ingressSet.values.flatMap { it.values }.toSet()

            val yamlFile = File(".nais/$env.yaml")
            val yamlConfig: Map<String, Any> = yaml.load(yamlFile.inputStream())

            val yamlHosts = (
                getNestedValue(
                    yamlConfig,
                    "spec",
                    "accessPolicy",
                    "outbound",
                    "external",
                    "host"
                ) as List<String>
                ).map { "https://$it" }
                .filterNot {
                    it.contains("dokument-test-354.adeo.no") ||
                        it.contains("otds-042.adeo.no") ||
                        it.contains("empower1-q.adeo.no") ||
                        it.contains("dokument1-q.adeo.no")
                }
                .toSet()

            // Check for missing URLs in YAML and JSON
            val missingInYaml = jsonIngressUrls - yamlHosts
            val missingInJson = yamlHosts - jsonIngressUrls

            // Assert that no URLs are missing in either JSON or YAML
            assertTrue(missingInYaml.isEmpty()) {
                "Missing ingress URLs in $env YAML (.nais/$env.yaml): $missingInYaml"
            }
            assertTrue(missingInJson.isEmpty()) {
                "Missing ingress URLs in $env JSON (/ingresses/$env.json): $missingInJson"
            }
        }
    }
}
