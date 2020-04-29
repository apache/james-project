package org.apache.james.wkd.store;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PublicKeyEntryTest {

    @Test
    void testSha1ZBase32JoeDoe() {
        assertEquals("iy9q119eutrkn8s1mk4r39qejnbu3n5q", PublicKeyEntry.sha1ZBase32("Joe.Doe"));
    }

    @Test
    void testSha1ZBase32JoeDoeLower() {
        assertEquals("iy9q119eutrkn8s1mk4r39qejnbu3n5q", PublicKeyEntry.sha1ZBase32("joe.doe"));
    }

}
