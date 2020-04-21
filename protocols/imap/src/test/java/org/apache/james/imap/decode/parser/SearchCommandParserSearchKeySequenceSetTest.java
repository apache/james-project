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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.api.message.request.SearchKey;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.apache.james.mailbox.MessageUid;
import org.junit.Before;
import org.junit.Test;

public class SearchCommandParserSearchKeySequenceSetTest {
    SearchCommandParser parser;

    @Before
    public void setUp() throws Exception {
        parser = new SearchCommandParser(mock(StatusResponseFactory.class));
    }
    
    @Test
    public void testAllNumbersSequence() throws Exception {

        IdRange[] range = { new IdRange(2), new IdRange(4), new IdRange(9),
                new IdRange(16), new IdRange(25), new IdRange(36),
                new IdRange(49), new IdRange(64), new IdRange(81),
                new IdRange(100) };
        checkSequence("2,4,9,16,25,36,49,64,81,100", IdRange.mergeRanges(Arrays.asList(range)).toArray(IdRange[]::new));
    }

    @Test
    public void testEndStarSequence() throws Exception {
        IdRange[] range = { new IdRange(8), new IdRange(10,11),
                new IdRange(17), new IdRange(100, Long.MAX_VALUE) };
        checkSequence("8,10:11,17,100:*", IdRange.mergeRanges(Arrays.asList(range)).toArray(IdRange[]::new));
    }

    @Test
    public void testStartStarSequence() throws Exception {
        IdRange[] range = { 
                new IdRange(9,Long.MAX_VALUE), 
                new IdRange(15),
                new IdRange(799, 820) 
                };
        checkSequence("*:9,15,799:820", IdRange.mergeRanges(Arrays.asList(range)).toArray(IdRange[]::new));
    }


    @Test
    public void testAllNumbersUids() throws Exception {

        UidRange[] range = { 
                new UidRange(MessageUid.of(2)),
                new UidRange(MessageUid.of(4)),
                new UidRange(MessageUid.of(9)),
                new UidRange(MessageUid.of(16)), 
                new UidRange(MessageUid.of(25)), 
                new UidRange(MessageUid.of(36)),
                new UidRange(MessageUid.of(49)), 
                new UidRange(MessageUid.of(64)), 
                new UidRange(MessageUid.of(81)),
                new UidRange(MessageUid.of(100)) };
        checkUid("2,4,9,16,25,36,49,64,81,100", UidRange.mergeRanges(Arrays.asList(range)).toArray(UidRange[]::new));
    }

    @Test
    public void testEndStarUids() throws Exception {
        UidRange[] range = { 
                new UidRange(MessageUid.of(8)), 
                new UidRange(MessageUid.of(10),MessageUid.of(11)),
                new UidRange(MessageUid.of(17)), 
                new UidRange(MessageUid.of(100), MessageUid.MAX_VALUE) };
        checkUid("8,10:11,17,100:*", UidRange.mergeRanges(Arrays.asList(range)).toArray(UidRange[]::new));
    }

    @Test
    public void testStartStarUids() throws Exception {
        UidRange[] range = { 
                new UidRange(MessageUid.of(9), MessageUid.MAX_VALUE), 
                new UidRange(MessageUid.of(15)),
                new UidRange(MessageUid.of(799), MessageUid.of(820)) 
                };
        checkUid("*:9,15,799:820", UidRange.mergeRanges(Arrays.asList(range)).toArray(UidRange[]::new));
    }

    
    private void checkUid(String sequence, UidRange[] range) throws Exception {
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

    private void checkValid(String input, SearchKey key) throws Exception {
        input = input + "\r\n";
        ImapRequestLineReader reader = new ImapRequestStreamLineReader(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.US_ASCII)),
                new ByteArrayOutputStream());

        final SearchKey searchKey = parser.searchKey(null, reader, null, false);
        assertThat(searchKey).isEqualTo(key);
    }
}
