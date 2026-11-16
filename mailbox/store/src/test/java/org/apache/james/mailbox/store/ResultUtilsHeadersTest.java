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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.junit.jupiter.api.Test;

class ResultUtilsHeadersTest {
    @Test
    void createHeadersShouldKeepTheStoredBytesOfRawUtf8Values() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.getHeaderContent()).thenReturn(new ByteArrayInputStream(
            "Subject: utf8 é\r\nFrom: Ålice <alice@example.org>\r\n\r\n".getBytes(StandardCharsets.UTF_8)));

        List<Header> headers = ResultUtils.createHeaders(message);

        // RFC 6532: FETCH BODY[HEADER.FIELDS] returns the stored header bytes
        assertThat(headers).hasSize(2);
        assertThat(headers.get(0).getValueBytes()).isEqualTo("utf8 é".getBytes(StandardCharsets.UTF_8));
        assertThat(headers.get(0).size()).isEqualTo("Subject".length() + "utf8 é".getBytes(StandardCharsets.UTF_8).length + 2);
        assertThat(headers.get(1).getValueBytes()).isEqualTo("Ålice <alice@example.org>".getBytes(StandardCharsets.UTF_8));
    }
}
