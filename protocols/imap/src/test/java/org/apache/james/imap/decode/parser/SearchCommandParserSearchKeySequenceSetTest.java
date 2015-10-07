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

package org.apache.james.imap.decode.parser;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.request.SearchKey;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class SearchCommandParserSearchKeySequenceSetTest {

    SearchCommandParser parser;


    ImapCommand command;

    ImapMessage message;

    private Mockery mockery = new JUnit4Mockery();
    
    @Before
    public void setUp() throws Exception {
        parser = new SearchCommandParser();
        command = ImapCommand.anyStateCommand("Command");
        message = mockery.mock(ImapMessage.class);
    }
    
    @Test
    public void testAllNumbers() throws Exception {

        IdRange[] range = { new IdRange(2), new IdRange(4), new IdRange(9),
                new IdRange(16), new IdRange(25), new IdRange(36),
                new IdRange(49), new IdRange(64), new IdRange(81),
                new IdRange(100) };
        check("2,4,9,16,25,36,49,64,81,100", IdRange.mergeRanges(Arrays.asList(range)).toArray(new IdRange[0]));
    }

    @Test
    public void testEndStar() throws Exception {
        IdRange[] range = { new IdRange(8), new IdRange(10,11),
                new IdRange(17), new IdRange(100, Long.MAX_VALUE) };
        check("8,10:11,17,100:*", IdRange.mergeRanges(Arrays.asList(range)).toArray(new IdRange[0]));
    }

    @Test
    public void testStartStar() throws Exception {
        IdRange[] range = { new IdRange(9,Long.MAX_VALUE), new IdRange(15),
                new IdRange(799, 820) };
        check("*:9,15,799:820", IdRange.mergeRanges(Arrays.asList(range)).toArray(new IdRange[0]));
    }

    private void check(String sequence, IdRange[] range) throws Exception {
        checkUid(sequence, range);
        checkSequence(sequence, range);
    }

    private void checkUid(String sequence, IdRange[] range) throws Exception {
        SearchKey key = SearchKey.buildUidSet(range);
        checkValid("UID " + sequence, key);
        checkValid("uid " + sequence, key);
        checkValid("Uid " + sequence, key);
    }

    private void checkSequence(String sequence, IdRange[] range)
            throws Exception {
        SearchKey key = SearchKey.buildSequenceSet(range);
        checkValid(sequence, key);
        checkValid(sequence, key);
        checkValid(sequence, key);
    }

    private void checkValid(String input, final SearchKey key) throws Exception {
        input = input + "\r\n";
        ImapRequestLineReader reader = new ImapRequestStreamLineReader(
                new ByteArrayInputStream(input.getBytes("US-ASCII")),
                new ByteArrayOutputStream());

        final SearchKey searchKey = parser.searchKey(null, reader, null, false);
        assertEquals(key, searchKey);
    }
}
