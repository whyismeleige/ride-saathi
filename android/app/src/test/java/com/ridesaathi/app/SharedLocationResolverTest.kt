package com.ridesaathi.app

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class SharedLocationResolverTest {
    @Test fun coordinateLinksWorkWithoutNetwork() {
        val resolver = SharedLocationResolver { error("Should not access network") }
        val result = resolver.resolve("https://maps.google.com/?q=17.4,78.5") as SharedDestination.Coordinates
        assertEquals(17.4, result.location.latitude, 0.0)
    }

    @Test fun expandsShortLinkIntoPinRatherThanViewport() {
        val visited = mutableListOf<String>()
        val resolver = SharedLocationResolver { link ->
            visited.add(link)
            "https://www.google.com/maps/place/Clinic/@17.4,78.5,15z/data=!3d17.43!4d78.53"
        }
        val result = resolver.resolve("Clinic\nhttps://maps.app.goo.gl/example?g_st=aw") as SharedDestination.Coordinates
        assertEquals(17.43, result.location.latitude, 0.0)
        assertEquals(1, visited.size)
    }

    @Test fun suppliedCharminarLinkRequiresSearchWithAddressForComparison() {
        val resolver = SharedLocationResolver { link ->
            assertEquals("https://maps.app.goo.gl/jegeJefvQuU79UXz8", link)
            "https://www.google.com/maps/place/Charminar,+Charminar+Rd,+Char+Kaman,+Ghansi+Bazaar,+Hyderabad,+Telangana+500002/data=!4m2!3m1!1s0x3bcb978a6e1a939b:0xcb5a69e4aaf113fb!18m1!1e1?utm_source=mstt_1&entry=gps"
        }
        val result = resolver.resolve("https://maps.app.goo.gl/jegeJefvQuU79UXz8") as SharedDestination.Address
        assertEquals("Charminar", result.query)
        assertEquals("Charminar, Charminar Rd, Char Kaman, Ghansi Bazaar, Hyderabad, Telangana 500002", result.sharedAddress)
    }

    @Test fun followsLegacyAndRelativeRedirects() {
        val visited = mutableListOf<String>()
        val resolver = SharedLocationResolver { link ->
            visited.add(link)
            when (visited.size) {
                1 -> "/maps/second"
                else -> "https://maps.google.com/?q=17.4%2C78.5"
            }
        }
        assertTrue(resolver.resolve("http://goo.gl/maps/first") is SharedDestination.Coordinates)
        assertEquals(listOf("https://goo.gl/maps/first", "https://goo.gl/maps/second"), visited)
    }

    @Test fun rejectsUnsafeRedirectsBeforeFetchingOrParsingThem() {
        listOf("https://example.com/?q=17.4,78.5", "http://maps.google.com/?q=17.4,78.5",
            "https://maps.google.com.evil.test/?q=17.4,78.5", "https://user@maps.google.com/?q=17.4,78.5",
            "https://maps.google.com:8443/?q=17.4,78.5", "geo:17.4,78.5").forEach { target ->
            var requests = 0
            val resolver = SharedLocationResolver { requests++; target }
            assertNull(resolver.resolve("https://maps.app.goo.gl/example"))
            assertEquals(1, requests)
        }
    }

    @Test fun boundsRedirectLoopsAndLongChains() {
        var requests = 0
        assertNull(SharedLocationResolver { requests++; it }.resolve("https://maps.app.goo.gl/example"))
        assertEquals(1, requests)
        requests = 0
        assertNull(SharedLocationResolver { "https://maps.app.goo.gl/hop${++requests}" }
            .resolve("https://maps.app.goo.gl/example"))
        assertEquals(5, requests)
    }

    @Test(expected = IOException::class) fun networkFailureIsDistinctFromUnsupportedInput() {
        SharedLocationResolver { throw IOException("Offline") }.resolve("https://maps.app.goo.gl/example")
    }

    @Test fun unsupportedAndUnresolvableLinksReturnNoDestination() {
        val resolver = SharedLocationResolver { null }
        assertNull(resolver.resolve("not a location"))
        assertNull(resolver.resolve("https://maps.app.goo.gl/deleted"))
    }
}
