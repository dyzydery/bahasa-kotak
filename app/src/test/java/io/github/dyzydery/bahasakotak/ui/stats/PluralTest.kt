package io.github.dyzydery.bahasakotak.ui.stats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PluralTest {
    @Test
    fun `polish plural forms`() {
        val forms = listOf(0, 1, 2, 4, 5, 12, 14, 22, 25, 101, 112).map { plural(it, "karta", "karty", "kart") }
        assertEquals(
            listOf("0 kart", "1 karta", "2 karty", "4 karty", "5 kart", "12 kart", "14 kart", "22 karty", "25 kart", "101 kart", "112 kart"),
            forms,
        )
    }
}
