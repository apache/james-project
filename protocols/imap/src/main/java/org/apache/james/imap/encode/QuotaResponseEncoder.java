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

import java.io.IOException;
import java.util.Locale;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.message.response.QuotaResponse;
import org.apache.james.mailbox.model.Quota;

/**
 * Quota response encoder
 */
public class QuotaResponseEncoder implements ImapResponseEncoder<QuotaResponse> {
    private static final long BYTES_PER_KIB = 1024;

    @Override
    public Class<QuotaResponse> acceptableMessages() {
        return QuotaResponse.class;
    }

    @Override
    public void encode(QuotaResponse quotaResponse, ImapResponseComposer composer) throws IOException {
        String quotaRoot = quotaResponse.getQuotaRoot();

        composer.untagged();
        composer.message(ImapConstants.QUOTA_RESPONSE_NAME);
        composer.message(quotaRoot == null ? "" : quotaRoot);
        composer.openParen();
        for (QuotaResponse.Resource resource : quotaResponse.getResources()) {
            encode(resource, composer);
        }
        composer.closeParen();

        composer.end();
    }

    private void encode(QuotaResponse.Resource resource, ImapResponseComposer composer) throws IOException {
        composer.message(resource.name());
        // See RFC 9208 : response for STORAGE should be in KB. For more accuracy, we stores B, so conversion should be made
        switch (resource.name().toUpperCase(Locale.US)) {
            case ImapConstants.STORAGE_QUOTA_RESOURCE:
                writeMessagesSize(composer, resource.quota());
                break;
            case ImapConstants.MESSAGE_QUOTA_RESOURCE:
                writeMessagesCount(composer, resource.quota());
                break;
        }
    }

    private void writeMessagesSize(ImapResponseComposer composer, Quota<?, ?> quota) throws IOException {
        composer.message(quota.getUsed().asLong() / BYTES_PER_KIB);
        composer.message(quota.getLimit().asLong() / BYTES_PER_KIB);
    }

    private void writeMessagesCount(ImapResponseComposer composer, Quota<?, ?> quota) throws IOException {
        composer.message(quota.getUsed().asLong());
        composer.message(quota.getLimit().asLong());
    }
}
