/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.user.lib.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import nl.jqno.equalsverifier.EqualsVerifier;

@SuppressWarnings("checkstyle:methodname")
class AlgorithmTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(Algorithm.class)
            .withIgnoredFields("hasher")
            .verify();
    }

    @Test
    void ofShouldParseRawHash() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(Algorithm.of("SHA-1").getName()).isEqualTo("SHA-1");
            softly.assertThat(Algorithm.of("SHA-1").asString()).isEqualTo("SHA-1/plain");
            softly.assertThat(Algorithm.of("SHA-1").isLegacy()).isFalse();
            softly.assertThat(Algorithm.of("SHA-1").isSalted()).isFalse();
        });
    }

    @Test
    void ofShouldParseRawHashWithFallback() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(Algorithm.of("SHA-1", "plain").getName()).isEqualTo("SHA-1");
            softly.assertThat(Algorithm.of("SHA-1", "plain").asString()).isEqualTo("SHA-1/plain");
            softly.assertThat(Algorithm.of("SHA-1", "plain").isLegacy()).isFalse();
            softly.assertThat(Algorithm.of("SHA-1", "plain").isSalted()).isFalse();
        });
    }

    @Test
    void ofShouldParseLegacy() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(Algorithm.of("SHA-1/legacy").getName()).isEqualTo("SHA-1");
            softly.assertThat(Algorithm.of("SHA-1/legacy").asString()).isEqualTo("SHA-1/legacy");
            softly.assertThat(Algorithm.of("SHA-1/legacy").isLegacy()).isTrue();
            softly.assertThat(Algorithm.of("SHA-1/legacy").isSalted()).isFalse();
        });
    }

    @Test
    void ofShouldParseLegacyWithFallback() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(Algorithm.of("SHA-1", "legacy").getName()).isEqualTo("SHA-1");
            softly.assertThat(Algorithm.of("SHA-1", "legacy").asString()).isEqualTo("SHA-1/legacy");
            softly.assertThat(Algorithm.of("SHA-1", "legacy").isLegacy()).isTrue();
            softly.assertThat(Algorithm.of("SHA-1", "legacy").isSalted()).isFalse();
        });
    }

    @Test
    void ofShouldParseLegacyIgnoringFallback() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(Algorithm.of("SHA-1/legacy", "plain").getName()).isEqualTo("SHA-1");
            softly.assertThat(Algorithm.of("SHA-1/legacy", "plain").asString()).isEqualTo("SHA-1/legacy");
            softly.assertThat(Algorithm.of("SHA-1/legacy", "plain").isLegacy()).isTrue();
            softly.assertThat(Algorithm.of("SHA-1/legacy", "plain").isSalted()).isFalse();
        });
    }

    @Test
    void ofShouldParseSalted() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(Algorithm.of("SHA-1/salted").getName()).isEqualTo("SHA-1");
            softly.assertThat(Algorithm.of("SHA-1/salted").asString()).isEqualTo("SHA-1/salted");
            softly.assertThat(Algorithm.of("SHA-1/salted").isLegacy()).isFalse();
            softly.assertThat(Algorithm.of("SHA-1/salted").isSalted()).isTrue();
        });
    }

    @Test
    void ofShouldParseSaltedWithFallback() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(Algorithm.of("SHA-1", "salted").getName()).isEqualTo("SHA-1");
            softly.assertThat(Algorithm.of("SHA-1", "salted").asString()).isEqualTo("SHA-1/salted");
            softly.assertThat(Algorithm.of("SHA-1", "salted").isLegacy()).isFalse();
            softly.assertThat(Algorithm.of("SHA-1", "salted").isSalted()).isTrue();
        });
    }

    @Test
    void ofShouldParseSaltedIgnoringFallback() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(Algorithm.of("SHA-1/salted", "plain").getName()).isEqualTo("SHA-1");
            softly.assertThat(Algorithm.of("SHA-1/salted", "plain").asString()).isEqualTo("SHA-1/salted");
            softly.assertThat(Algorithm.of("SHA-1/salted", "plain").isLegacy()).isFalse();
            softly.assertThat(Algorithm.of("SHA-1/salted", "plain").isSalted()).isTrue();
        });
    }

    @Test
    void ofShouldParseLegacySalted() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(Algorithm.of("SHA-1/legacy_salted").getName()).isEqualTo("SHA-1");
            softly.assertThat(Algorithm.of("SHA-1/legacy_salted").asString()).isEqualTo("SHA-1/legacy_salted");
            softly.assertThat(Algorithm.of("SHA-1/legacy_salted").isLegacy()).isTrue();
            softly.assertThat(Algorithm.of("SHA-1/legacy_salted").isSalted()).isTrue();
        });
    }

    @Test
    void ofShouldParseLegacySaltedWithFallback() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(Algorithm.of("SHA-1", "legacy_salted").getName()).isEqualTo("SHA-1");
            softly.assertThat(Algorithm.of("SHA-1", "legacy_salted").asString()).isEqualTo("SHA-1/legacy_salted");
            softly.assertThat(Algorithm.of("SHA-1", "legacy_salted").isLegacy()).isTrue();
            softly.assertThat(Algorithm.of("SHA-1", "legacy_salted").isSalted()).isTrue();
        });
    }

    @Test
    void ofShouldParseLegacySaltedIgnoringFallback() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(Algorithm.of("SHA-1/legacy_salted", "plain").getName()).isEqualTo("SHA-1");
            softly.assertThat(Algorithm.of("SHA-1/legacy_salted", "plain").asString()).isEqualTo("SHA-1/legacy_salted");
            softly.assertThat(Algorithm.of("SHA-1/legacy_salted", "plain").isLegacy()).isTrue();
            softly.assertThat(Algorithm.of("SHA-1/legacy_salted", "plain").isSalted()).isTrue();
        });
    }

    @Test
    void ofShouldParseIterationAndKeySize() {
        assertThat(Algorithm.of("PBKDF2-10-20", "plain").hasher())
            .isEqualTo(new Algorithm.LegacyPBKDF2Hasher(10, 20));
    }

    @Test
    void ofShouldParseIteration() {
        assertThat(Algorithm.of("PBKDF2-10", "plain").hasher())
            .isEqualTo(new Algorithm.LegacyPBKDF2Hasher(10, 512));
    }

    @Test
    void ofShouldAcceptDefaultPBKDF2() {
        assertThat(Algorithm.of("PBKDF2", "plain").hasher())
            .isEqualTo(new Algorithm.LegacyPBKDF2Hasher(1000, 512));
    }

    @Test
    void ofShouldThrowOnBadIteration() {
        assertThatThrownBy(() -> Algorithm.of("PBKDF2-bad", "plain"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofShouldThrowOnEmptyIteration() {
        assertThatThrownBy(() -> Algorithm.of("PBKDF2-", "plain"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofShouldThrowOnNegativeIteration() {
        assertThatThrownBy(() -> Algorithm.of("PBKDF2--1", "plain"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofShouldThrowOnEmptyKeySize() {
        assertThatThrownBy(() -> Algorithm.of("PBKDF2-1-", "plain"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofShouldThrowOnBadKeySize() {
        assertThatThrownBy(() -> Algorithm.of("PBKDF2-1-bad", "plain"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofShouldThrowOnZeroIteration() {
        assertThatThrownBy(() -> Algorithm.of("PBKDF2-0", "plain"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofShouldThrowOnZeroKeySize() {
        assertThatThrownBy(() -> Algorithm.of("PBKDF2-1-0", "plain"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofShouldThrowOnNegativeKeySize() {
        assertThatThrownBy(() -> Algorithm.of("PBKDF2-1--1", "plain"))
            .isInstanceOf(IllegalArgumentException.class);
    }


    private static Stream<Arguments> sha1LegacyTestBed() {
        return Stream.of(
            Arguments.of("myPassword", "VBPuJHI7uixaa6LQGWx4s+5G"),
            Arguments.of("otherPassword", "ks40t+AjBnHsMaC1Is/6+mtb"),
            Arguments.of("", "2jmj7l5rSw0yVb/vlWAYkK/Y"),
            Arguments.of("a", "hvfkN/qlp/zhXR3cuerq6jd2"));
    }

    @ParameterizedTest
    @MethodSource("sha1LegacyTestBed")
    void testSha1Legacy(String password, String expectedHash) {
        assertThat(Algorithm.of("SHA-1", "legacy").digest(password, "salt"))
            .isEqualTo(expectedHash);
    }

    private static Stream<Arguments> sha512LegacyTestBed() {
        return Stream.of(
            Arguments.of("myPassword", "RQrQPbk5XfzLXgMGb9fxbPuith4j1RY3NxRHFFkFLskKmkvzoVHmAOqKrtNuO4who9OKsXBYOXSd\r\nEw2kOA8U"),
            Arguments.of("otherPassword", "6S2kG/b6oHgWBXQjKDKTayXWu2cs9374lxFrL9uVpmYUlq0lw/ZFU9svMtYVDV5aVjJqRbLWZ/df\r\neaaJwYxk"),
            Arguments.of("", "z4PhNX7vuL3xVChQ1m2AB9Yg5AULVxXcg/SpIdNs6c5H0NE8XYXysP+DGNKHfuwvY7kxvUdBeoGl\r\nODJ6+Sfa"),
            Arguments.of("a", "H0D8ktokFpR1CXnubPWC8tXX0o4YM13gWrxU0FYOD1MChgxlK/CNVgJSql50IQVG82n7u86MEs/H\r\nlXsmUv6a"));
    }

    @ParameterizedTest
    @MethodSource("sha512LegacyTestBed")
    void testSha512Legacy(String password, String expectedHash) {
        assertThat(Algorithm.of("SHA-512", "legacy").digest(password, "salt"))
            .isEqualTo(expectedHash);
    }

    private static Stream<Arguments> sha1TestBed() {
        return Stream.of(
            Arguments.of("myPassword", "VBPuJHI7uixaa6LQGWx4s+5GKNE=\r\n"),
            Arguments.of("otherPassword", "ks40t+AjBnHsMaC1Is/6+mtb05s=\r\n"),
            Arguments.of("", "2jmj7l5rSw0yVb/vlWAYkK/YBwk=\r\n"),
            Arguments.of("a", "hvfkN/qlp/zhXR3cuerq6jd2Z7g=\r\n"));
    }

    @ParameterizedTest
    @MethodSource("sha1TestBed")
    void testSha1(String password, String expectedHash) {
        assertThat(Algorithm.of("SHA-1").digest(password, "salt"))
            .isEqualTo(expectedHash);
    }

    private static Stream<Arguments> sha512TestBed() {
        return Stream.of(
            Arguments.of("myPassword", "RQrQPbk5XfzLXgMGb9fxbPuith4j1RY3NxRHFFkFLskKmkvzoVHmAOqKrtNuO4who9OKsXBYOXSd\r\nEw2kOA8USA==\r\n"),
            Arguments.of("otherPassword", "6S2kG/b6oHgWBXQjKDKTayXWu2cs9374lxFrL9uVpmYUlq0lw/ZFU9svMtYVDV5aVjJqRbLWZ/df\r\neaaJwYxkhQ==\r\n"),
            Arguments.of("", "z4PhNX7vuL3xVChQ1m2AB9Yg5AULVxXcg/SpIdNs6c5H0NE8XYXysP+DGNKHfuwvY7kxvUdBeoGl\r\nODJ6+SfaPg==\r\n"),
            Arguments.of("a", "H0D8ktokFpR1CXnubPWC8tXX0o4YM13gWrxU0FYOD1MChgxlK/CNVgJSql50IQVG82n7u86MEs/H\r\nlXsmUv6adQ==\r\n"));
    }

    @ParameterizedTest
    @MethodSource("sha512TestBed")
    void testSha512(String password, String expectedHash) {
        assertThat(Algorithm.of("SHA-512").digest(password, "salt"))
            .isEqualTo(expectedHash);
    }

    private static Stream<Arguments> PBKDF2TestBed() {
        return Stream.of(
            Arguments.of("myPassword", "Ag2g49zxor0w11yguLUMQ7EKBokU81LkvLyDqubWtQq7R5V21HVqZ+CEjEQxBLGfi35RFyesJtxb\r\n" +
                "L5/VRCpI3g==\r\n"),
            Arguments.of("otherPassword", "4KFfGIjbZqhaqZfr1rKWcoY5vkeps3/+x5BwU342kUbGGoW30kaP98R5iY6SNGg0yOaPBcB8EWqJ\r\n" +
                "96RtIMnIYQ==\r\n"),
            Arguments.of("", "6grdNX1hpxA5wJPXhBUJhz4qUoUSRZE0F3rqoPR+PYedDklDomJ0LPRV5f1SMNAX0fRgmQ8WDe6k\r\n" +
                "2qr1Nc/orA==\r\n"),
            Arguments.of("a", "WxpwqV5V9L3QR8xi8D8INuH0UH5oLeq+ZuXb6J1bAfhHp3urVOtAr+bwksC3JQRyC7QHE9MLfn61\r\n" +
                "nTXo5johrQ==\r\n"));
    }

    @ParameterizedTest
    @MethodSource("PBKDF2TestBed")
    void testPBKDF2(String password, String expectedHash) {
        assertThat(Algorithm.of("PBKDF2").digest(password, "salt"))
            .isEqualTo(expectedHash);
    }

    private static Stream<Arguments> PBKDF210IterationTestBed() {
        return Stream.of(
            Arguments.of("myPassword", "AoNuFZ7ZI6vHU8obYASuuLPaQcr8fGentKWsOawBNTIc7MNJMbo4yNjo0pcCVK6J/XAfbISEKugt\r\n" +
                "HwDeSUA10A==\r\n"),
            Arguments.of("otherPassword", "e4+swbwo1s3X665INoRVsENXrgJtC7SMws9G3Y0GBoLZBkqZQzE2aT2WLd+hOlf3s/wwQe10MA0Q\r\n" +
                "xMJQIcIosQ==\r\n"),
            Arguments.of("", "ZBXj9rrLc4L9hHXOBPpDd5ot9DDB6qaq1g2mbAMOivpZe3eYw1ehdFXbU9pwpI4y/+MZlLkG3E1S\r\n" +
                "WRQXuUZqag==\r\n"),
            Arguments.of("a", "i1iWZzuaqsFotT998+stRqyrcyUrZ0diBJf9RJ52mUo0a074ykh8joWdrxhEsyd2Fh2DNO38TWxC\r\n" +
                "KkIK6taLxA==\r\n"));

    }

    @ParameterizedTest
    @MethodSource("PBKDF210IterationTestBed")
    void testPBKDF210Iteration(String password, String expectedHash) {
        assertThat(Algorithm.of("PBKDF2-10").digest(password, "salt"))
            .isEqualTo(expectedHash);
    }

    private static Stream<Arguments> PBKDF210Iteration128KeySizeTestBed() {
        return Stream.of(
            Arguments.of("myPassword", "AoNuFZ7ZI6vHU8obYASuuA==\r\n"),
            Arguments.of("otherPassword", "e4+swbwo1s3X665INoRVsA==\r\n"),
            Arguments.of("", "ZBXj9rrLc4L9hHXOBPpDdw==\r\n"),
            Arguments.of("a", "i1iWZzuaqsFotT998+stRg==\r\n"));
    }

    @ParameterizedTest
    @MethodSource("PBKDF210Iteration128KeySizeTestBed")
    void testPBKDF210Iteration128KeySize(String password, String expectedHash) {
        assertThat(Algorithm.of("PBKDF2-10-128").digest(password, "salt"))
            .isEqualTo(expectedHash);
    }

    private static Stream<Arguments> PBKDF2Sha512TestBed() {
        return Stream.of(
            Arguments.of("myPassword", "uakKCXTKBGN1JxbE0bAuLKn8Bzkf+NU9J+2l50tqJlZYzXNe86EJ2jEXkI1Fp1uy2sXLfOrSxCFT\r\n" +
                "/fQRPxgZmw==\r\n"),
            Arguments.of("otherPassword", "ayj9bcHNoTUe8Q9sXA/TLHMuL1TUKPSEQ8gjWzotOMaZl70yiST2e0yhZklkppVAl9PdODJRNIgz\r\n" +
                "bKzcw/JWPw==\r\n"),
            Arguments.of("", "+TC9GqSNLoOwICQGAw7WSxYWHE4lKwjRm1RTHnKxfcOA66mou8G8/h2sCDiLLFxqBU7E2wHh2bo4\r\n" +
                "LEqh8dwFdQ==\r\n"),
            Arguments.of("a", "q/vf4fTefNDayyTm9Vcc8AKU4lzuDgbG/nV+Dlkh9pGpfXI0qkJxFCnwxLt4WeKGKywgGAYx4LRX\r\n" +
                "q0STMJiH2A==\r\n"));
    }

    @ParameterizedTest
    @MethodSource("PBKDF2Sha512TestBed")
    void testPBKDF2Sha512(String password, String expectedHash) {
        assertThat(Algorithm.of("PBKDF2-SHA512").digest(password, "salt"))
            .isEqualTo(expectedHash);
    }

    private static Stream<Arguments> PBKDFSha512210IterationTestBed() {
        return Stream.of(
            Arguments.of("myPassword", "AoNuFZ7ZI6vHU8obYASuuLPaQcr8fGentKWsOawBNTIc7MNJMbo4yNjo0pcCVK6J/XAfbISEKugt\r\n" +
                "HwDeSUA10A==\r\n"),
            Arguments.of("otherPassword", "e4+swbwo1s3X665INoRVsENXrgJtC7SMws9G3Y0GBoLZBkqZQzE2aT2WLd+hOlf3s/wwQe10MA0Q\r\n" +
                "xMJQIcIosQ==\r\n"),
            Arguments.of("", "ZBXj9rrLc4L9hHXOBPpDd5ot9DDB6qaq1g2mbAMOivpZe3eYw1ehdFXbU9pwpI4y/+MZlLkG3E1S\r\n" +
                "WRQXuUZqag==\r\n"),
            Arguments.of("a", "i1iWZzuaqsFotT998+stRqyrcyUrZ0diBJf9RJ52mUo0a074ykh8joWdrxhEsyd2Fh2DNO38TWxC\r\n" +
                "KkIK6taLxA==\r\n"));

    }

    @ParameterizedTest
    @MethodSource("PBKDFSha512210IterationTestBed")
    void testPBKDF2Sha51210Iteration(String password, String expectedHash) {
        assertThat(Algorithm.of("PBKDF2-10").digest(password, "salt"))
            .isEqualTo(expectedHash);
    }

    private static Stream<Arguments> PBKDF2Sha51210Iteration128KeySizeTestBed() {
        return Stream.of(
            Arguments.of("myPassword", "86eS500HIhM0zy0pMOZS2Q==\r\n"),
            Arguments.of("otherPassword", "Ano/+SYcHd9QBIYGu8b4Ig==\r\n"),
            Arguments.of("", "H3pIRxZ0vA3Nw3NbLdw1CQ==\r\n"),
            Arguments.of("a", "RomeJpaEOcfopuKUOLE5+Q==\r\n"));
    }

    @ParameterizedTest
    @MethodSource("PBKDF2Sha51210Iteration128KeySizeTestBed")
    void testPBKDF2Sha51210Iteration128KeySize(String password, String expectedHash) {
        assertThat(Algorithm.of("PBKDF2-SHA512-10-128").digest(password, "salt"))
            .isEqualTo(expectedHash);
    }
}