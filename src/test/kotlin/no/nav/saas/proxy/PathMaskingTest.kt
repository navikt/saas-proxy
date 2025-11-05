package no.nav.saas.proxy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PathMaskingTest {
    // Function to mask variable segments in a path
    private fun maskPath(path: String): String {
        // Regex pattern to detect numeric variable segments (e.g., /0602/, /0702/)
        // Modify the regex pattern to suit the variable format you need to mask
        return path
            .replace(Regex("/\\d+"), "/{id}")
            .replace(Regex("/[A-Z]\\d{4,}"), "/{ident}")
            .replace(Regex("/[^/]+\\.(xml|pdf)$"), "/{filename}")
            .replace(Regex("/[A-Z]{3}(?=/|$)"), "/{code}")
    }

    @Test
    fun `should mask variable segments in paths`() {
        // Given paths with variable segments
        val paths =
            listOf(
                "norg2/api/v1/enhet/0602/kontaktinformasjon",
                "norg2/api/v1/enhet/0702/kontaktinformasjon",
            )

        // Expected masked path
        val expectedMaskedPath = "norg2/api/v1/enhet/{id}/kontaktinformasjon"

        // When masking the paths
        val maskedPaths = paths.map { maskPath(it) }

        // Then each path should match the expected masked pattern
        maskedPaths.forEach { maskedPath ->
            assertEquals(expectedMaskedPath, maskedPath)
        }
    }
}
