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

package org.apache.james.imap.decode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import java.util.Collection;
import java.util.Iterator;
import java.util.stream.IntStream;

import org.apache.james.imap.api.message.SectionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class FetchPartPathDecoderTest {

    FetchPartPathDecoder decoder;

    @BeforeEach
    public void setUp() throws Exception {
        decoder = new FetchPartPathDecoder();
    }

    @Test
    void testShouldDetectText() throws Exception {
        assertThat(decoder.decode("TEXT")).isEqualTo(SectionType.TEXT);
        assertThat(decoder.decode("3.TEXT")).isEqualTo(SectionType.TEXT);
        assertThat(decoder.decode("3.1.TEXT")).isEqualTo(SectionType.TEXT);
        assertThat(decoder
                .decode("3.2.5.7.8.TEXT")).isEqualTo(SectionType.TEXT);
    }

    @Test
    void testShouldDetectHeader() throws Exception {
        assertThat(decoder.decode("HEADER")).isEqualTo(SectionType.HEADER);
        assertThat(decoder.decode("4.HEADER")).isEqualTo(SectionType.HEADER);
        assertThat(decoder.decode("10.1.HEADER")).isEqualTo(SectionType.HEADER);
        assertThat(decoder
                .decode("8.3.5.11.HEADER")).isEqualTo(SectionType.HEADER);
    }

    @Test
    void testShouldDetectHeaderFields() throws Exception {
        assertThat(decoder
                .decode("HEADER.FIELDS ()")).isEqualTo(SectionType.HEADER_FIELDS);
        assertThat(decoder
                .decode("4.HEADER.FIELDS ()")).isEqualTo(SectionType.HEADER_FIELDS);
        assertThat(decoder
                .decode("10.1.HEADER.FIELDS ()")).isEqualTo(SectionType.HEADER_FIELDS);
        assertThat(decoder
                .decode("8.3.5.11.HEADER.FIELDS ()")).isEqualTo(SectionType.HEADER_FIELDS);
    }

    @Test
    void testShouldDetectHeaderFieldsNot() throws Exception {
        assertThat(decoder
                .decode("HEADER.FIELDS.NOT ()")).isEqualTo(SectionType.HEADER_NOT_FIELDS);
        assertThat(decoder
                .decode("4.HEADER.FIELDS.NOT ()")).isEqualTo(SectionType.HEADER_NOT_FIELDS);
        assertThat(decoder
                .decode("10.1.HEADER.FIELDS.NOT ()")).isEqualTo(SectionType.HEADER_NOT_FIELDS);
        assertThat(decoder
                .decode("8.3.5.11.HEADER.FIELDS.NOT ()")).isEqualTo(SectionType.HEADER_NOT_FIELDS);
    }

    @Test
    void testShouldDetectMime() throws Exception {
        assertThat(decoder.decode("MIME")).isEqualTo(SectionType.MIME);
        assertThat(decoder.decode("6.MIME")).isEqualTo(SectionType.MIME);
        assertThat(decoder.decode("2.88.MIME")).isEqualTo(SectionType.MIME);
        assertThat(decoder
                .decode("32.3.15.11.MIME")).isEqualTo(SectionType.MIME);
    }

    @Test
    void testShouldDetectContent() throws Exception {
        assertThat(decoder.decode("34")).isEqualTo(SectionType.CONTENT);
        assertThat(decoder.decode("6")).isEqualTo(SectionType.CONTENT);
        assertThat(decoder.decode("9.88")).isEqualTo(SectionType.CONTENT);
        assertThat(decoder.decode("17.3.15.11")).isEqualTo(SectionType.CONTENT);
    }

    @Test
    void testShouldIgnoreCase() throws Exception {
        assertThat(decoder.decode("6.MIME")).isEqualTo(SectionType.MIME);
        assertThat(decoder.decode("6.mime")).isEqualTo(SectionType.MIME);
        assertThat(decoder.decode("6.miME")).isEqualTo(SectionType.MIME);
        assertThat(decoder.decode("6.MIme")).isEqualTo(SectionType.MIME);
        assertThat(decoder.decode("6.HEADER")).isEqualTo(SectionType.HEADER);
        assertThat(decoder.decode("6.header")).isEqualTo(SectionType.HEADER);
        assertThat(decoder.decode("6.HEadER")).isEqualTo(SectionType.HEADER);
        assertThat(decoder.decode("6.heADEr")).isEqualTo(SectionType.HEADER);
        assertThat(decoder.decode("6.TEXT")).isEqualTo(SectionType.TEXT);
        assertThat(decoder.decode("6.text")).isEqualTo(SectionType.TEXT);
        assertThat(decoder.decode("6.TExt")).isEqualTo(SectionType.TEXT);
        assertThat(decoder.decode("6.teXT")).isEqualTo(SectionType.TEXT);
    }

    @Test
    void testMimimalPath() throws Exception {
        int[] values = { 6 };
        checkEndingPermutations(values);
    }

    @Test
    void testLongPath() throws Exception {
        int[] values = { 3, 11, 345, 231, 11, 3, 1, 1, 1, 3, 8, 8 };
        checkEndingPermutations(values);
    }

    @Test
    void testShouldThrowProtocolExceptionWhenSpecifierBogus() {
        try {
            decoder.decode("1.34.BOGUS");
            fail("Expected protocol exception to be thrown");
        } catch (DecodingException e) {
            // expected
        }
    }

    @Test
    void testShouldThrowProtocolExceptionWhenPathBogus() {
        try {
            decoder.decode("1.34.BOGUS.44.34234.324");
            fail("Expected protocol exception to be thrown");
        } catch (DecodingException e) {
            // expected
        }
    }

    @Test
    void testShouldReadShortFieldNames() throws Exception {
        String[] names = { "A", "B", "C", "D", "E", "F" };
        checkReadNames("1.8.HEADER.FIELDS", names);
    }

    @Test
    void testShouldReadShortFieldNotNames() throws Exception {
        String[] names = { "A", "B", "C", "D", "E", "F" };
        checkReadNames("1.8.9.HEADER.FIELDS.NOT", names);
    }

    @Test
    void testShouldReadOneFieldNames() throws Exception {
        String[] names = { "AFieldName" };
        checkReadNames("1.8.HEADER.FIELDS", names);
    }

    @Test
    void testShouldReadOneFieldNotNames() throws Exception {
        String[] names = { "AFieldName" };
        checkReadNames("1.8.9.HEADER.FIELDS.NOT", names);
    }

    @Test
    void testShouldReadManyFieldNames() throws Exception {
        String[] names = { "ONE", "TWO", "THREE", "FOUR", "FIVE", "345345" };
        checkReadNames("1.8.HEADER.FIELDS", names);
    }

    @Test
    void testShouldReadManyFieldNotNames() throws Exception {
        String[] names = { "ONE", "TWO", "THREE", "FOUR", "FIVE", "345345" };
        checkReadNames("1.8.HEADER.FIELDS.NOT", names);
    }

    @Test
    void manyHeadersShouldNotLeadToStackOverFlow() throws Exception {
        String[] names = IntStream.range(0, 3000)
            .mapToObj(Integer::toString)
            .toArray(String[]::new);
        checkReadNames("1.8.HEADER.FIELDS.NOT", names);
    }

    private void checkReadNames(String base, String[] names) throws Exception {
        base = base + " (";
        for (String name : names) {
            base = base + ' ' + name;
        }
        base = base + ')';
        decoder.decode(base);
        Collection<String> results = decoder.getNames();
        assertThat(results).isNotNull();
        Iterator<String> it = results.iterator();
        for (String name : names) {
            assertThat(it.next()).isEqualTo(name);
        }
    }

    private void checkEndingPermutations(int[] values) throws Exception {
        String base = "";
        boolean first = true;
        for (int value : values) {
            if (first) {
                first = false;
            } else {
                base = base + ".";
            }
            base = base + value;
        }
        checkPath(values, base);
        checkPath(values, base + ".TEXT");
        checkPath(values, base + ".HEADER");
        checkPath(values, base + ".MIME");
        checkPath(values, base + ".HEADER.FIELDS.NOT ()");
        checkPath(values, base + ".HEADER.FIELDS ()");
    }

    private void checkPath(int[] expected, String encoded) throws Exception {
        decoder.decode(encoded);
        final int[] path = decoder.getPath();
        assertThat(path).isNotNull();
        assertThat(path.length).isEqualTo(expected.length);
        for (int i = 0; i < expected.length; i++) {
            assertThat(path[i]).isEqualTo(expected[i]);
        }
    }
}
