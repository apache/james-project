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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.store.streaming.PartContentBuilder;
import org.junit.Before;
import org.junit.Test;

public class PartContentBuilderMultipartAlternativeTest {

    private static final String CONTENT_TYPE_PLAIN = "text/plain;charset=us-ascii";

    private static final String CONTENT_TYPE_HTML = "text/html;charset=us-ascii";

    private static final String CONTENT_TYPE_XHTML = "application/xhtml;charset=us-ascii";

    private static final String CONTENT_TYPE = "Content-Type";

    private static final String ALT_PLAIN_BODY = "Rhubarb!Rhubard!Rhubard!\r\n";

    private static final String ALT_XHTML_BODY = "<!DOCTYPE html PUBLIC '-//W3C//DTD XHTML 1.0 Strict//EN' 'http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd'>"
            + "<html><head><title>Rhubard!</title></head><body><p>Rhubarb!Rhubard!Rhubard!</p></body></html>\r\n";

    private static final String ALT_HTML_BODY = "<html><head><title>Rhubard!</title></head><body><p>Rhubarb!Rhubard!Rhubard!</p></body></html>\r\n";

    private static final String ALT_PART_XHTML = CONTENT_TYPE + ": "
            + CONTENT_TYPE_XHTML + "\r\n" + "\r\n" + ALT_XHTML_BODY;

    private static final String ALT_PART_HTML = CONTENT_TYPE + ": "
            + CONTENT_TYPE_HTML + "\r\n" + "\r\n" + ALT_HTML_BODY;

    private static final String ALT_PART_PLAIN = CONTENT_TYPE + ": "
            + CONTENT_TYPE_PLAIN + "\r\n" + "\r\n" + ALT_PLAIN_BODY;

    private static final String MULTIPART_ALTERNATIVE = "From: Samual Smith <samual@example.org>\r\n"
            + "To: John Smith <john@example.org>\r\n"
            + "Date: Sun, 10 Feb 2008 08:00:00 -0800 (PST)\r\n"
            + "Subject: Rhubarb!\r\n"
            + "Content-Type: multipart/alternative;boundary=4242\r\n"
            + "\r\n"
            + "--4242\r\n"
            + ALT_PART_PLAIN
            + "\r\n--4242\r\n"
            + ALT_PART_HTML
            + "\r\n--4242\r\n" + ALT_PART_XHTML + "\r\n--4242\r\n";

    PartContentBuilder builder;

    @Before
    public void setUp() throws Exception {
        builder = new PartContentBuilder();
    }

    @Test
    public void testShouldLocatePartsOfMultipartAlterative() throws Exception {
        assertThat(bodyContent(MULTIPART_ALTERNATIVE, 1)).isEqualTo(ALT_PLAIN_BODY);
        assertThat(bodyContent(MULTIPART_ALTERNATIVE, 2)).isEqualTo(ALT_HTML_BODY);
        assertThat(bodyContent(MULTIPART_ALTERNATIVE, 3)).isEqualTo(ALT_XHTML_BODY);
    }

    @Test
    public void testShouldLocateHeadersOfMultipartAlterative() throws Exception {
        checkContentType(CONTENT_TYPE_PLAIN, MULTIPART_ALTERNATIVE, 1);
        checkContentType(CONTENT_TYPE_HTML, MULTIPART_ALTERNATIVE, 2);
        checkContentType(CONTENT_TYPE_XHTML, MULTIPART_ALTERNATIVE, 3);
    }

    @Test
    public void testShouldLocateFullContentOfMultipartAlterative()
            throws Exception {
        assertThat(fullContent(MULTIPART_ALTERNATIVE, 1)).isEqualTo(ALT_PART_PLAIN);
        assertThat(fullContent(MULTIPART_ALTERNATIVE, 2)).isEqualTo(ALT_PART_HTML);
        assertThat(fullContent(MULTIPART_ALTERNATIVE, 3)).isEqualTo(ALT_PART_XHTML);
    }

    private String fullContent(String mail, int position) throws Exception {
        InputStream in = new ByteArrayInputStream(Charset.forName("us-ascii")
                .encode(mail).array());
        builder.parse(in);
        builder.to(position);
        return IOUtils.toString(builder.getFullContent().getInputStream(), StandardCharsets.UTF_8);
    }

    private String bodyContent(String mail, int position) throws Exception {
        InputStream in = new ByteArrayInputStream(Charset.forName("us-ascii")
                .encode(mail).array());
        builder.parse(in);
        builder.to(position);
        return IOUtils.toString(builder.getMimeBodyContent().getInputStream(), StandardCharsets.UTF_8);
    }

    private void checkContentType(String contentType, String mail, int position)
            throws Exception {
        List<Header> headers = headers(mail, position);
        assertThat(headers.size()).isEqualTo(1);
        Header header = (Header) headers.get(0);
        assertThat(header.getName()).isEqualTo(CONTENT_TYPE);
        assertThat(header.getValue()).isEqualTo(contentType);
    }

    private List<Header> headers(String mail, int position) throws Exception {
        InputStream in = new ByteArrayInputStream(Charset.forName("us-ascii")
                .encode(mail).array());
        builder.parse(in);
        builder.to(position);
        return builder.getMimeHeaders();
    }
}
