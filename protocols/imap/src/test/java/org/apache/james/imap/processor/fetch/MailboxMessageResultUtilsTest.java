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

package org.apache.james.imap.processor.fetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.james.mailbox.model.MessageResult;
import org.junit.Before;
import org.junit.Test;

public class MailboxMessageResultUtilsTest {

    private static final String[] NAMES = { "One", "Three" };

    Header headerOne;

    Header headerTwo;

    Header headerThree;

    List<MessageResult.Header> headers;

    private static class Header implements MessageResult.Header {

        public String name;

        public String value;

        public Header(String name) {
            this.name = name;
            value = "Value";
        }

        public long size() {
            return 0;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        
        public InputStream getInputStream() throws IOException {
            return null;
        }


    }

    @Before
    public void setUp() throws Exception {
        headerOne = new Header("One");
        headerTwo = new Header("Two");
        headerThree = new Header("Three");
        headers = new ArrayList<>();
        headers.add(headerOne);
        headers.add(headerTwo);
        headers.add(headerThree);
    }

    @Test
    public void testGetAllContent() throws Exception {
        List<MessageResult.Header> results = MessageResultUtils.getAll(headers.iterator());
        assertEquals(3, results.size());
        assertEquals(headerOne, results.get(0));
        assertEquals(headerTwo, results.get(1));
        assertEquals(headerThree, results.get(2));
    }

    @Test
    public void testGetMatching() throws Exception {

        List<MessageResult.Header> results = MessageResultUtils
                .getMatching(NAMES, headers.iterator());
        assertEquals(2, results.size());
        assertEquals(headerOne, results.get(0));
        assertEquals(headerThree, results.get(1));
    }

    @Test
    public void testGetNotMatching() throws Exception {

        List<MessageResult.Header> results = MessageResultUtils.getNotMatching(NAMES, headers
                .iterator());
        assertEquals(1, results.size());
        assertEquals(headerTwo, results.get(0));
    }

    @Test
    public void testGetMatchingSingle() throws Exception {
        assertEquals(headerOne, MessageResultUtils.getMatching("One", headers
                .iterator()));
        assertEquals(headerThree, MessageResultUtils.getMatching("Three",
                headers.iterator()));
        assertNull(MessageResultUtils
                .getMatching("Missing", headers.iterator()));
    }

}
