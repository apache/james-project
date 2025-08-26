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
package org.apache.james.mailbox.store.mail.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.apache.james.mime4j.dom.Header;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.junit.jupiter.api.Test;

class MimeMessageHeadersUtilTest {
    @Test
    void parseInReplyToShouldHandleHeaderWithCRLFLineBreaks() throws Exception {
        Header header = parse("References:\r\n" +
            " <CANKoxfUNSvbjBuUaQSQiFavvDH-OvVig772-dKMrAbsisoZa1A@mail.gmail.com>\r\n" +
            " <Mime4j.70b.9be3d6cb7586c526.198e07cfc57@linagora.com>\r\n" +
            "In-Reply-To:\r\n" +
            " <CANKoxfUNSvbjBuUaQSQiFavvDH-OvVig772-dKMrAbsisoZa1A@mail.gmail.com>\r\n").getHeader();

        assertThat(MimeMessageHeadersUtil.parseInReplyTo(header))
            .contains(new MimeMessageId("<CANKoxfUNSvbjBuUaQSQiFavvDH-OvVig772-dKMrAbsisoZa1A@mail.gmail.com>"));
    }

    @Test
    void parseReferencesShouldHandleHeaderWithCRLFLineBreaks() throws Exception {
        Header header = parse("References:\r\n" +
            " <CANKoxfUNSvbjBuUaQSQiFavvDH-OvVig772-dKMrAbsisoZa1A@mail.gmail.com>\r\n" +
            " <Mime4j.70b.9be3d6cb7586c526.198e07cfc57@linagora.com>\r\n" +
            "In-Reply-To:\r\n" +
            " <CANKoxfUNSvbjBuUaQSQiFavvDH-OvVig772-dKMrAbsisoZa1A@mail.gmail.com>\r\n").getHeader();

        assertThat(MimeMessageHeadersUtil.parseReferences(header).get())
            .containsOnly(new MimeMessageId("<CANKoxfUNSvbjBuUaQSQiFavvDH-OvVig772-dKMrAbsisoZa1A@mail.gmail.com>"),
                new MimeMessageId("<Mime4j.70b.9be3d6cb7586c526.198e07cfc57@linagora.com>"));
    }


    private Message parse(String s) throws IOException {
        DefaultMessageBuilder defaultMessageBuilder = new DefaultMessageBuilder();
        defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
        return defaultMessageBuilder.parseMessage(new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII)));
    }
}