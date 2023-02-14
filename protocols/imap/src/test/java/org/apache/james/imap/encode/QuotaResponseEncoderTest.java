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

package org.apache.james.imap.encode;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.imap.encode.base.ByteImapResponseWriter;
import org.apache.james.imap.encode.base.ImapResponseComposerImpl;
import org.apache.james.imap.message.response.QuotaResponse;
import org.apache.james.mailbox.model.Quota;
import org.junit.jupiter.api.Test;

/**
 * QUOTA Response encoder test
 */
class QuotaResponseEncoderTest {
    @Test
    void quotaMessageResponseShouldBeWellFormatted() throws Exception {
        QuotaResponse response = new QuotaResponse("MESSAGE", "root",
            Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(231)).computedLimit(QuotaCountLimit.count(1024)).build());
        ByteImapResponseWriter byteImapResponseWriter = new ByteImapResponseWriter();
        ImapResponseComposer composer = new ImapResponseComposerImpl(byteImapResponseWriter, 1024);
        QuotaResponseEncoder encoder = new QuotaResponseEncoder();
        encoder.encode(response, composer);
        composer.flush();
        String responseString = byteImapResponseWriter.getString();
        assertThat(responseString).isEqualTo("* QUOTA root (MESSAGE 231 1024)\r\n");
    }

    @Test
    void quotaStorageResponseShouldBeWellFormatted() throws Exception {
        QuotaResponse response = new QuotaResponse("STORAGE", "root",
        Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(231 * 1024)).computedLimit(QuotaSizeLimit.size(1024 * 1024)).build());
        ByteImapResponseWriter byteImapResponseWriter = new ByteImapResponseWriter();
        ImapResponseComposer composer = new ImapResponseComposerImpl(byteImapResponseWriter, 1024);
        QuotaResponseEncoder encoder = new QuotaResponseEncoder();
        encoder.encode(response, composer);
        composer.flush();
        String responseString = byteImapResponseWriter.getString();
        assertThat(responseString).isEqualTo("* QUOTA root (STORAGE 231 1024)\r\n");
    }
}
