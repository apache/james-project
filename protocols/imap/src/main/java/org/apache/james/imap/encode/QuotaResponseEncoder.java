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

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.encode.base.AbstractChainedImapEncoder;
import org.apache.james.imap.message.response.QuotaResponse;
import org.apache.james.mailbox.model.Quota;

/**
 * Quota response encoder
 */
public class QuotaResponseEncoder extends AbstractChainedImapEncoder {

    public QuotaResponseEncoder(ImapEncoder next) {
        super(next);
    }

    protected void doEncode(ImapMessage acceptableMessage, ImapResponseComposer composer, ImapSession session) throws IOException {

        QuotaResponse quotaResponse = (QuotaResponse) acceptableMessage;

        String quotaRoot = quotaResponse.getQuotaRoot();
        Quota quota = quotaResponse.getQuota();

        composer.untagged();
        composer.commandName(ImapConstants.QUOTA_RESPONSE_NAME);
        composer.message(quotaRoot == null ? "" : quotaRoot);
        composer.openParen();
        composer.message(quotaResponse.getResourceName());
        // See RFC 2087 : response for STORAGE should be in KB. For more accuracy, we stores B, so conversion should be made
        if (quotaResponse.getResourceName().equalsIgnoreCase(ImapConstants.STORAGE_QUOTA_RESOURCE)) {
            composer.message(quota.getUsed() / 1024);
            composer.message(quota.getMax() / 1024);
        } else {
            composer.message(quota.getUsed());
            composer.message(quota.getMax());
        }
        composer.closeParen();

        composer.end();
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.apache.james.imap.encode.base.AbstractChainedImapEncoder#isAcceptable
     * (org.apache.james.imap.api.ImapMessage)
     */
    public boolean isAcceptable(ImapMessage message) {
        return message instanceof QuotaResponse;
    }

}
