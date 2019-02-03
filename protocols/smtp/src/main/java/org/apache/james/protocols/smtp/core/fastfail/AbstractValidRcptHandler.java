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

package org.apache.james.protocols.smtp.core.fastfail;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.RcptHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler which want to do a recipient check should extend this
 */
public abstract class AbstractValidRcptHandler implements RcptHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractValidRcptHandler.class);

    @Override
    public HookResult doRcpt(SMTPSession session, MaybeSender sender, MailAddress rcpt) {
        if (!isLocalDomain(session, rcpt.getDomain())) {
            return HookResult.DECLINED;
        }
        if (!isValidRecipient(session, rcpt)) {
            return reject(rcpt);
        }
        return HookResult.DECLINED;
    }

    public HookResult reject(MailAddress rcpt) {
        LOGGER.info("Rejected message. Unknown user: {}", rcpt);
        return HookResult.builder()
            .hookReturnCode(HookReturnCode.deny())
            .smtpReturnCode(SMTPRetCode.MAILBOX_PERM_UNAVAILABLE)
            .smtpDescription(DSNStatus.getStatus(DSNStatus.PERMANENT,DSNStatus.ADDRESS_MAILBOX) + " Unknown user: " + rcpt.asString())
            .build();
    }

    /**
     * Return true if email for the given recipient should get accepted
     */
    protected abstract boolean isValidRecipient(SMTPSession session, MailAddress recipient);
    
    /**
     * Return true if the domain is local
     */
    protected abstract boolean isLocalDomain(SMTPSession session, Domain domain);
}
