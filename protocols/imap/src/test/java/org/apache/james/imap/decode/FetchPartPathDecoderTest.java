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

import static org.junit.Assert.*;

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
        assertEquals(FetchPartPathDecoder.TEXT, decoder.decode("TEXT"));
        assertEquals(FetchPartPathDecoder.TEXT, decoder.decode("3.TEXT"));
        assertEquals(FetchPartPathDecoder.TEXT, decoder.decode("3.1.TEXT"));
        assertEquals(FetchPartPathDecoder.TEXT, decoder
                .decode("3.2.5.7.8.TEXT"));
    }

    @Test
    public void testShouldDetectHeader() throws Exception {
        assertEquals(FetchPartPathDecoder.HEADER, decoder.decode("HEADER"));
        assertEquals(FetchPartPathDecoder.HEADER, decoder.decode("4.HEADER"));
        assertEquals(FetchPartPathDecoder.HEADER, decoder.decode("10.1.HEADER"));
        assertEquals(FetchPartPathDecoder.HEADER, decoder
                .decode("8.3.5.11.HEADER"));
    }

    @Test
    public void testShouldDetectHeaderFields() throws Exception {
        assertEquals(FetchPartPathDecoder.HEADER_FIELDS, decoder
                .decode("HEADER.FIELDS ()"));
        assertEquals(FetchPartPathDecoder.HEADER_FIELDS, decoder
                .decode("4.HEADER.FIELDS ()"));
        assertEquals(FetchPartPathDecoder.HEADER_FIELDS, decoder
                .decode("10.1.HEADER.FIELDS ()"));
        assertEquals(FetchPartPathDecoder.HEADER_FIELDS, decoder
                .decode("8.3.5.11.HEADER.FIELDS ()"));
    }

    @Test
    public void testShouldDetectHeaderFieldsNot() throws Exception {
        assertEquals(FetchPartPathDecoder.HEADER_NOT_FIELDS, decoder
                .decode("HEADER.FIELDS.NOT ()"));
        assertEquals(FetchPartPathDecoder.HEADER_NOT_FIELDS, decoder
                .decode("4.HEADER.FIELDS.NOT ()"));
        assertEquals(FetchPartPathDecoder.HEADER_NOT_FIELDS, decoder
                .decode("10.1.HEADER.FIELDS.NOT ()"));
        assertEquals(FetchPartPathDecoder.HEADER_NOT_FIELDS, decoder
                .decode("8.3.5.11.HEADER.FIELDS.NOT ()"));
    }

    @Test
    public void testShouldDetectMime() throws Exception {
        assertEquals(FetchPartPathDecoder.MIME, decoder.decode("MIME"));
        assertEquals(FetchPartPathDecoder.MIME, decoder.decode("6.MIME"));
        assertEquals(FetchPartPathDecoder.MIME, decoder.decode("2.88.MIME"));
        assertEquals(FetchPartPathDecoder.MIME, decoder
                .decode("32.3.15.11.MIME"));
    }

    @Test
    public void testShouldDetectContent() throws Exception {
        assertEquals(FetchPartPathDecoder.CONTENT, decoder.decode("34"));
        assertEquals(FetchPartPathDecoder.CONTENT, decoder.decode("6"));
        assertEquals(FetchPartPathDecoder.CONTENT, decoder.decode("9.88"));
        assertEquals(FetchPartPathDecoder.CONTENT, decoder.decode("17.3.15.11"));
    }

    @Test
    public void testShouldIgnoreCase() throws Exception {
        assertEquals(FetchPartPathDecoder.MIME, decoder.decode("6.MIME"));
        assertEquals(FetchPartPathDecoder.MIME, decoder.decode("6.mime"));
        assertEquals(FetchPartPathDecoder.MIME, decoder.decode("6.miME"));
        assertEquals(FetchPartPathDecoder.MIME, decoder.decode("6.MIme"));
        assertEquals(FetchPartPathDecoder.HEADER, decoder.decode("6.HEADER"));
        assertEquals(FetchPartPathDecoder.HEADER, decoder.decode("6.header"));
        assertEquals(FetchPartPathDecoder.HEADER, decoder.decode("6.HEadER"));
        assertEquals(FetchPartPathDecoder.HEADER, decoder.decode("6.heADEr"));
        assertEquals(FetchPartPathDecoder.TEXT, decoder.decode("6.TEXT"));
        assertEquals(FetchPartPathDecoder.TEXT, decoder.decode("6.text"));
        assertEquals(FetchPartPathDecoder.TEXT, decoder.decode("6.TExt"));
        assertEquals(FetchPartPathDecoder.TEXT, decoder.decode("6.teXT"));
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
    public void testShouldThrowProtocolExceptionWhenSpecifierBogus()
            throws Exception {
        try {
            decoder.decode("1.34.BOGUS");
            fail("Expected protocol exception to be thrown");
        } catch (DecodingException e) {
            // expected
        }
    }

    @Test
    public void testShouldThrowProtocolExceptionWhenPathBogus()
            throws Exception {
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
        for (int i = 0; i < names.length; i++) {
            base = base + ' ' + names[i];
        }
        base = base + ')';
        decoder.decode(base);
        Collection<String> results = decoder.getNames();
        assertNotNull(results);
        Iterator<String> it = results.iterator();
        for (int i = 0; i < names.length; i++) {
            assertEquals(names[i], it.next());
        }
    }

    private void checkEndingPermutations(int[] values) throws Exception {
        String base = "";
        boolean first = true;
        for (int i = 0; i < values.length; i++) {
            if (first) {
                first = false;
            } else {
                base = base + ".";
            }
            base = base + values[i];
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
        assertNotNull(path);
        assertEquals(expected.length, path.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], path[i]);
        }
    }
}
