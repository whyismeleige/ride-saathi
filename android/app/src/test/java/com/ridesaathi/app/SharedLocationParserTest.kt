package com.ridesaathi.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedLocationParserTest {
    @Test fun parsesWhatsAppGoogleMapsLocation() {
        val location = SharedLocationParser.parse("https://maps.google.com/?q=17.385044,78.486671")!!
        assertEquals(17.385044, location.latitude, 0.0)
        assertEquals(78.486671, location.longitude, 0.0)
    }

    @Test fun parsesGoogleMapsSearchAndGeoLinks() {
        val search = SharedLocationParser.parse(
            "Meet here https://www.google.com/maps/search/?api=1&query=17.4%2C78.5"
        )!!
        assertEquals(17.4, search.latitude, 0.0)
        assertEquals(78.5, search.longitude, 0.0)

        val geo = SharedLocationParser.parse("geo:0,0?q=17.41,78.51(Clinic)")!!
        assertEquals("Clinic", geo.label)
    }

    @Test fun parsesCoordinatesEmbeddedInGoogleMapsPath() {
        val location = SharedLocationParser.parse("https://www.google.com/maps/place/Test/@17.42,78.52,17z")!!
        assertEquals(17.42, location.latitude, 0.0)
        assertEquals(78.52, location.longitude, 0.0)

        val dataLocation = SharedLocationParser.parse(
            "https://www.google.com/maps/place/Test/data=!4m2!3d17.43!4d78.53"
        )!!
        assertEquals(17.43, dataLocation.latitude, 0.0)
        assertEquals(78.53, dataLocation.longitude, 0.0)
    }

    @Test fun rejectsMessagesAndUnsupportedLinks() {
        assertNull(SharedLocationParser.parse("Meet me near the clinic"))
        assertNull(SharedLocationParser.parse("https://example.com/?q=17.4,78.5"))
        assertNull(SharedLocationParser.parse("https://maps.google.com/?q=91,78"))
        assertNull(SharedLocationParser.parse("geo:0,0"))
    }
    @Test fun prefersPlacePinToViewportCenterAndDoesNotUseZoomAsLabel() {
        val pin = SharedLocationParser.parse(
            "https://www.google.com/maps/place/Clinic/@17.4,78.5,15z/data=!4m2!3d17.43!4d78.53"
        )!!
        assertEquals(17.43, pin.latitude, 0.0)
        assertEquals(78.53, pin.longitude, 0.0)
        assertNull(pin.label)
        assertNull(SharedLocationParser.parse("https://www.google.com/maps/@17.4,78.5,15z")!!.label)
    }

    @Test fun rejectsOutOfRangeCoordinatesWithoutMatchingTheirSuffix() {
        listOf("117.4,78.5", "-117.4,78.5", "17.4,181.5", "17.4,1234.5").forEach {
            assertNull(SharedLocationParser.parse("https://maps.google.com/?q=$it"))
            assertNull(SharedLocationParser.parse("https://www.google.com/maps/@$it,15z"))
        }
    }

    @Test fun skipsUnresolvableLinkAndParsesNextCoordinateLink() {
        val location = SharedLocationParser.parse(
            "https://maps.app.goo.gl/example https://maps.google.com/?q=17.4,78.5"
        )!!
        assertEquals(17.4, location.latitude, 0.0)
    }

    @Test fun acceptsShortMapsLinksButRejectsOtherRedirectHosts() {
        assertEquals(listOf("https://maps.app.goo.gl/example", "https://goo.gl/maps/example"),
            SharedLocationParser.supportedUris(
                "Place (https://maps.app.goo.gl/example). https://goo.gl/maps/example"
            ))
        listOf("https://maps.app.goo.gl.evil.test/example", "https://goo.gl/other/example",
            "https://www.google.com/search?q=17.4,78.5", "https://user@maps.google.com/?q=17.4,78.5")
            .forEach { assertEquals(emptyList<String>(), SharedLocationParser.supportedUris(it)) }
    }

    @Test fun decodesSharedPlaceAddress() {
        assertEquals("Charminar, Hyderabad", SharedLocationParser.addressQuery(
            "https://www.google.com/maps/place/Charminar,+Hyderabad/data=!4m2!3m1!1splace-id"
        ))
        assertNull(SharedLocationParser.addressQuery("https://maps.google.com/?q=place_id:abc"))
        assertNull(SharedLocationParser.addressQuery("https://maps.google.com/?q=117.4,78.5"))
    }
}
