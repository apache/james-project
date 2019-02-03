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

import org.apache.james.protocols.imap.DecodingException;
import org.junit.Before;
import org.junit.Test;


public class FetchPartPathDecoderTest {

    FetchPartPathDecoder decoder;

    @Before
    public void setUp() throws Exception {
        decoder = new FetchPartPathDecoder();
    }

    @Test
    public void testShouldDetectText() throws Exception {
        assertThat(decoder.decode("TEXT")).isEqualTo(FetchPartPathDecoder.TEXT);
        assertThat(decoder.decode("3.TEXT")).isEqualTo(FetchPartPathDecoder.TEXT);
        assertThat(decoder.decode("3.1.TEXT")).isEqualTo(FetchPartPathDecoder.TEXT);
        assertThat(decoder
                .decode("3.2.5.7.8.TEXT")).isEqualTo(FetchPartPathDecoder.TEXT);
    }

    @Test
    public void testShouldDetectHeader() throws Exception {
        assertThat(decoder.decode("HEADER")).isEqualTo(FetchPartPathDecoder.HEADER);
        assertThat(decoder.decode("4.HEADER")).isEqualTo(FetchPartPathDecoder.HEADER);
        assertThat(decoder.decode("10.1.HEADER")).isEqualTo(FetchPartPathDecoder.HEADER);
        assertThat(decoder
                .decode("8.3.5.11.HEADER")).isEqualTo(FetchPartPathDecoder.HEADER);
    }

    @Test
    public void testShouldDetectHeaderFields() throws Exception {
        assertThat(decoder
                .decode("HEADER.FIELDS ()")).isEqualTo(FetchPartPathDecoder.HEADER_FIELDS);
        assertThat(decoder
                .decode("4.HEADER.FIELDS ()")).isEqualTo(FetchPartPathDecoder.HEADER_FIELDS);
        assertThat(decoder
                .decode("10.1.HEADER.FIELDS ()")).isEqualTo(FetchPartPathDecoder.HEADER_FIELDS);
        assertThat(decoder
                .decode("8.3.5.11.HEADER.FIELDS ()")).isEqualTo(FetchPartPathDecoder.HEADER_FIELDS);
    }

    @Test
    public void testShouldDetectHeaderFieldsNot() throws Exception {
        assertThat(decoder
                .decode("HEADER.FIELDS.NOT ()")).isEqualTo(FetchPartPathDecoder.HEADER_NOT_FIELDS);
        assertThat(decoder
                .decode("4.HEADER.FIELDS.NOT ()")).isEqualTo(FetchPartPathDecoder.HEADER_NOT_FIELDS);
        assertThat(decoder
                .decode("10.1.HEADER.FIELDS.NOT ()")).isEqualTo(FetchPartPathDecoder.HEADER_NOT_FIELDS);
        assertThat(decoder
                .decode("8.3.5.11.HEADER.FIELDS.NOT ()")).isEqualTo(FetchPartPathDecoder.HEADER_NOT_FIELDS);
    }

    @Test
    public void testShouldDetectMime() throws Exception {
        assertThat(decoder.decode("MIME")).isEqualTo(FetchPartPathDecoder.MIME);
        assertThat(decoder.decode("6.MIME")).isEqualTo(FetchPartPathDecoder.MIME);
        assertThat(decoder.decode("2.88.MIME")).isEqualTo(FetchPartPathDecoder.MIME);
        assertThat(decoder
                .decode("32.3.15.11.MIME")).isEqualTo(FetchPartPathDecoder.MIME);
    }

    @Test
    public void testShouldDetectContent() throws Exception {
        assertThat(decoder.decode("34")).isEqualTo(FetchPartPathDecoder.CONTENT);
        assertThat(decoder.decode("6")).isEqualTo(FetchPartPathDecoder.CONTENT);
        assertThat(decoder.decode("9.88")).isEqualTo(FetchPartPathDecoder.CONTENT);
        assertThat(decoder.decode("17.3.15.11")).isEqualTo(FetchPartPathDecoder.CONTENT);
    }

    @Test
    public void testShouldIgnoreCase() throws Exception {
        assertThat(decoder.decode("6.MIME")).isEqualTo(FetchPartPathDecoder.MIME);
        assertThat(decoder.decode("6.mime")).isEqualTo(FetchPartPathDecoder.MIME);
        assertThat(decoder.decode("6.miME")).isEqualTo(FetchPartPathDecoder.MIME);
        assertThat(decoder.decode("6.MIme")).isEqualTo(FetchPartPathDecoder.MIME);
        assertThat(decoder.decode("6.HEADER")).isEqualTo(FetchPartPathDecoder.HEADER);
        assertThat(decoder.decode("6.header")).isEqualTo(FetchPartPathDecoder.HEADER);
        assertThat(decoder.decode("6.HEadER")).isEqualTo(FetchPartPathDecoder.HEADER);
        assertThat(decoder.decode("6.heADEr")).isEqualTo(FetchPartPathDecoder.HEADER);
        assertThat(decoder.decode("6.TEXT")).isEqualTo(FetchPartPathDecoder.TEXT);
        assertThat(decoder.decode("6.text")).isEqualTo(FetchPartPathDecoder.TEXT);
        assertThat(decoder.decode("6.TExt")).isEqualTo(FetchPartPathDecoder.TEXT);
        assertThat(decoder.decode("6.teXT")).isEqualTo(FetchPartPathDecoder.TEXT);
    }

    @Test
    public void testMimimalPath() throws Exception {
        int[] values = { 6 };
        checkEndingPermutations(values);
    }

    @Test
    public void testLongPath() throws Exception {
        int[] values = { 3, 11, 345, 231, 11, 3, 1, 1, 1, 3, 8, 8 };
        checkEndingPermutations(values);
    }

    @Test
    public void testShouldThrowProtocolExceptionWhenSpecifierBogus() {
        try {
            decoder.decode("1.34.BOGUS");
            fail("Expected protocol exception to be thrown");
        } catch (DecodingException e) {
            // expected
        }
    }

    @Test
    public void testShouldThrowProtocolExceptionWhenPathBogus() {
        try {
            decoder.decode("1.34.BOGUS.44.34234.324");
            fail("Expected protocol exception to be thrown");
        } catch (DecodingException e) {
            // expected
        }
    }

    @Test
    public void testShouldReadShortFieldNames() throws Exception {
        String[] names = { "A", "B", "C", "D", "E", "F" };
        checkReadNames("1.8.HEADER.FIELDS", names);
    }

    @Test
    public void testShouldReadShortFieldNotNames() throws Exception {
        String[] names = { "A", "B", "C", "D", "E", "F" };
        checkReadNames("1.8.9.HEADER.FIELDS.NOT", names);
    }

    @Test
    public void testShouldReadOneFieldNames() throws Exception {
        String[] names = { "AFieldName" };
        checkReadNames("1.8.HEADER.FIELDS", names);
    }

    @Test
    public void testShouldReadOneFieldNotNames() throws Exception {
        String[] names = { "AFieldName" };
        checkReadNames("1.8.9.HEADER.FIELDS.NOT", names);
    }

    @Test
    public void testShouldReadManyFieldNames() throws Exception {
        String[] names = { "ONE", "TWO", "THREE", "FOUR", "FIVE", "345345" };
        checkReadNames("1.8.HEADER.FIELDS", names);
    }

    @Test
    public void testShouldReadManyFieldNotNames() throws Exception {
        String[] names = { "ONE", "TWO", "THREE", "FOUR", "FIVE", "345345" };
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
