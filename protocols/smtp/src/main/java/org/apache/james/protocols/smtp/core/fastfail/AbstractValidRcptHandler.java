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

import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.Username;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.RcptHook;
import org.apache.james.util.AuditTrail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableMap;

/**
 * Handler which want to do a recipient check should extend this
 */
public abstract class AbstractValidRcptHandler implements RcptHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractValidRcptHandler.class);

    @Override
    public HookResult doRcpt(SMTPSession session, MaybeSender sender, MailAddress rcpt) {
        try {
            if (!isLocalDomain(session, rcpt.getDomain())) {
                return HookResult.DECLINED;
            }
            if (!isValidRecipient(session, rcpt)) {
                return reject(session, rcpt);
            }
            return HookResult.DECLINED;
        } catch (IllegalArgumentException e) {
            LOGGER.info("Encounter an error upon RCPT validation ({}), deny", rcpt.asString());
            return HookResult.builder()
                .hookReturnCode(HookReturnCode.deny())
                .smtpReturnCode(SMTPRetCode.MAILBOX_PERM_UNAVAILABLE)
                .smtpDescription(DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.ADDRESS_MAILBOX) + " Unknown user: " + rcpt.asString())
                .build();
        } catch (Exception e) {
            LOGGER.error("Encounter an error upon RCPT validation ({}), deny-soft", rcpt.asString(), e);
            return HookResult.builder()
                .hookReturnCode(HookReturnCode.denySoft())
                .smtpReturnCode(SMTPRetCode.LOCAL_ERROR)
                .smtpDescription(DSNStatus.getStatus(DSNStatus.TRANSIENT, DSNStatus.UNDEFINED_STATUS) + " Unexpected error for " + rcpt.asString())
                .build();
        }
    }

    public HookResult reject(SMTPSession session, MailAddress rcpt) {
        MaybeSender sender = session.getAttachment(SMTPSession.SENDER, ProtocolSession.State.Transaction).orElse(MaybeSender.nullSender());
        AuditTrail.entry()
            .username(() -> Optional.ofNullable(session.getUsername())
                .map(Username::asString)
                .orElse(""))
            .remoteIP(() -> Optional.ofNullable(session.getRemoteAddress()))
            .sessionId(session::getSessionID)
            .protocol("SMTP")
            .action("SPOOL")
            .parameters(Throwing.supplier(() -> ImmutableMap.of(
                "sender", sender.asString(),
                "recipient",  rcpt.asString())))
            .log("Rejected message. Unknown user: " + rcpt.asString());

        return HookResult.builder()
            .hookReturnCode(HookReturnCode.deny())
            .smtpReturnCode(SMTPRetCode.MAILBOX_PERM_UNAVAILABLE)
            .smtpDescription(DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.ADDRESS_MAILBOX) + " Unknown user: " + rcpt.asString())
            .build();
    }

    /**
     * Return true if email for the given recipient should get accepted
     */
    protected abstract boolean isValidRecipient(SMTPSession session, MailAddress recipient) throws Exception;
    
    /**
     * Return true if the domain is local
     */
    protected abstract boolean isLocalDomain(SMTPSession session, Domain domain) throws Exception;
}
