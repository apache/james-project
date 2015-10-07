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

package org.apache.james.mailbox.store;

import static org.junit.Assert.*;


import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.model.MessageResult.Header;
import org.apache.james.mailbox.store.ResultHeader;
import org.apache.james.mailbox.store.streaming.PartContentBuilder;
import org.apache.james.mailbox.store.streaming.PartContentBuilder.PartNotFoundException;
import org.junit.Before;
import org.junit.Test;

public class PartContentBuilderComplexMultipartTest {

    private static final String PREAMBLE = "This is the preamble";

    private static final String CONTENT_TYPE = "Content-Type";

    private static final String CONTENT_TYPE_HTML = "text/html;charset=us-ascii";

    private static final String CONTENT_TYPE_PLAIN = "text/plain;charset=us-ascii";

    private static final String CONTENT_TYPE_RFC822 = "message/rfc822";

    private static final String OUTER_HTML_BODY = "<html><head><title>Rhubard!</title></head><body><p>Rhubarb!Rhubard!Rhubard!</p></body></html>\r\n";

    private static final String FULL_OUTER_HTML = CONTENT_TYPE + ": "
            + CONTENT_TYPE_HTML + "\r\n\r\n" + OUTER_HTML_BODY;

    private static final String OUTER_PLAIN_BODY = "Rhubarb!Rhubard!Rhubard!\r\n";

    private static final String FULL_OUTER_PLAIN = CONTENT_TYPE + ": "
            + CONTENT_TYPE_PLAIN + "\r\n\r\n" + OUTER_PLAIN_BODY;

    private static final String INNER_HTML_BODY = "<html><head><title>Custard!</title></head><body><p>Custard!Custard!Custard!</p></body></html>\r\n";

    private static final String FULL_INNER_HTML = CONTENT_TYPE + ": "
            + CONTENT_TYPE_HTML + "\r\n\r\n" + INNER_HTML_BODY;

    private static final String INNER_PLAIN_BODY = "Custard!Custard!Custard!\r\n";

    private static final String FULL_INNER_TXT = CONTENT_TYPE + ": "
            + CONTENT_TYPE_PLAIN + "\r\n\r\n" + INNER_PLAIN_BODY;

    private static final String INNERMOST_BODY = "Da!Da!Da!Dah!\r\n";

    private static final String RFC822_PLAIN_MAIL = "From:  Samual Smith <samual@example.org>\r\n"
            + "To: John Smith <john@example.org>\r\n"
            + "Date: Thu, 1 Feb 2007 08:00:00 -0800 (PST)\r\n"
            + "Subject: Rhubard And Custard!\r\n"
            + CONTENT_TYPE
            + ": "
            + CONTENT_TYPE_PLAIN + "\r\n" + "\r\n" + INNERMOST_BODY;

    private static final String FULL_INNERMOST_EMAIL = CONTENT_TYPE + ": "
            + CONTENT_TYPE_RFC822 + "\r\n\r\n" + RFC822_PLAIN_MAIL;

    private static final String INNER_MAIL = "From: John Smith <john@example.org>\r\n"
            + "To: Samual Smith <samual@example.org>\r\n"
            + "Date: Fri, 1 Feb 2008 08:00:00 -0800 (PST)\r\n"
            + "Subject: Custard!\r\n"
            + "Content-Type: multipart/mixed;boundary=1729\r\n\r\n"
            + PREAMBLE
            + "\r\n--1729\r\n"
            + FULL_INNER_TXT
            + "\r\n--1729\r\n"
            + FULL_INNER_HTML
            + "\r\n--1729\r\n"
            + FULL_INNERMOST_EMAIL
            + "\r\n--1729--\r\n";

    private static final String FULL_INNER_MAIL = CONTENT_TYPE + ": "
            + CONTENT_TYPE_RFC822 + "\r\n\r\n" + INNER_MAIL;

    private static final String MULTIPART_MIXED = "From: Samual Smith <samual@example.org>\r\n"
            + "To: John Smith <john@example.org>\r\n"
            + "Date: Sun, 10 Feb 2008 08:00:00 -0800 (PST)\r\n"
            + "Subject: Rhubarb!\r\n"
            + "Content-Type: multipart/mixed;boundary=4242\r\n\r\n"
            + PREAMBLE
            + "\r\n--4242\r\n"
            + FULL_OUTER_HTML
            + "\r\n--4242\r\n"
            + FULL_INNER_MAIL
            + "\r\n--4242\r\n"
            + FULL_OUTER_PLAIN
            + "\r\n--4242--\r\n";

    PartContentBuilder builder;

    @Before
    public void setUp() throws Exception {
        builder = new PartContentBuilder();
    }

    @Test
    public void testShouldNotFoundSubPartOfNonMultiPartTopLevel()
            throws Exception {
        int[] path = { 1, 1 };
        for (int i = 1; i < 10; i++) {
            path[1] = i;
            checkNotPartFound(path);
        }
    }

    @Test
    public void testShouldNotFoundSubPartOfNonMultiInnerPart() throws Exception {
        int[] path = { 2, 2, 1 };
        for (int i = 1; i < 10; i++) {
            path[2] = i;
            checkNotPartFound(path);
        }
    }

    @Test
    public void testShouldLocateOuterHtml() throws Exception {
        int[] path = { 1 };
        check(FULL_OUTER_HTML, OUTER_HTML_BODY, CONTENT_TYPE_HTML, path);
    }

    @Test
    public void testShouldLocateOuterMail() throws Exception {
        int[] path = { 2 };
        check(FULL_INNER_MAIL, INNER_MAIL, CONTENT_TYPE_RFC822, path);
    }

    @Test
    public void testShouldLocateOuterPlain() throws Exception {
        int[] path = { 3 };
        check(FULL_OUTER_PLAIN, OUTER_PLAIN_BODY, CONTENT_TYPE_PLAIN, path);
    }

    @Test
    public void testShouldLocateInnerHtml() throws Exception {
        int[] path = { 2, 2 };
        check(FULL_INNER_HTML, INNER_HTML_BODY, CONTENT_TYPE_HTML, path);
    }

    @Test
    public void testShouldLocateInnerMail() throws Exception {
        int[] path = { 2, 3 };
        check(FULL_INNERMOST_EMAIL, RFC822_PLAIN_MAIL, CONTENT_TYPE_RFC822,
                path);
    }

    @Test
    public void testShouldLocateInnerPlain() throws Exception {
        int[] path = { 2, 1 };
        check(FULL_INNER_TXT, INNER_PLAIN_BODY, CONTENT_TYPE_PLAIN, path);
    }

    private void checkNotPartFound(int[] position) throws Exception {
        try {
            to(position);
            fail("Part does not exist. Expected exception to be thrown.");
        } catch (PartNotFoundException e) {
            // expected
        }
    }

    private void check(String full, String body, String contentType,
            int[] position) throws Exception {
        checkContentType(contentType, position);
        assertEquals(body, bodyContent(position));
        assertEquals(full, fullContent(position));
    }

    private String fullContent(int[] position) throws Exception {
        to(position);
        return IOUtils.toString(builder.getFullContent().getInputStream());
    }

    private String bodyContent(int[] position) throws Exception {
        to(position);
        return IOUtils.toString(builder.getMimeBodyContent().getInputStream());
    }

    private void checkContentType(String contentType, int[] position)
            throws Exception {
        List<Header> headers = headers(position);
        assertEquals(1, headers.size());
        ResultHeader header = (ResultHeader) headers.get(0);
        assertEquals(CONTENT_TYPE, header.getName());
        assertEquals(contentType, header.getValue());
    }

    private List<Header> headers(int[] position) throws Exception {
        to(position);
        return builder.getMimeHeaders();
    }

    private void to(int[] path) throws Exception {
        InputStream in = new ByteArrayInputStream(Charset.forName("us-ascii")
                .encode(MULTIPART_MIXED).array());
        builder.parse(in);
        for (int i = 0; i < path.length; i++) {
            builder.to(path[i]);
        }
    }
}
