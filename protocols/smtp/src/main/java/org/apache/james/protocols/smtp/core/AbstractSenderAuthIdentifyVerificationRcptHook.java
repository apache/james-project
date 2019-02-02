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
package org.apache.james.protocols.smtp.core;

import java.util.Locale;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.RcptHook;

import com.google.common.base.Preconditions;

/**
 * Handler which check if the authenticated user is the same as the one used as MAIL FROM
 */
public abstract class AbstractSenderAuthIdentifyVerificationRcptHook implements RcptHook {  
    private static final HookResult INVALID_AUTH = HookResult.builder()
        .hookReturnCode(HookReturnCode.deny())
        .smtpReturnCode(SMTPRetCode.BAD_SEQUENCE)
        .smtpDescription(DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.SECURITY_AUTH)
            + " Incorrect Authentication for Specified Email Address")
        .build();
    
    @Override
    public HookResult doRcpt(SMTPSession session, MaybeSender sender, MailAddress rcpt) {
        if (session.getUser() != null) {
            MaybeSender senderAddress = (MaybeSender) session.getAttachment(SMTPSession.SENDER, ProtocolSession.State.Transaction);
            
            // Check if the sender address is the same as the user which was used to authenticate.
            // Its important to ignore case here to fix JAMES-837. This is save todo because if the handler is called
            // the user was already authenticated
            if (isAnonymous(sender)
                || !senderMatchSessionUser(sender, session)
                || !belongsToLocalDomain(senderAddress)) {
                return INVALID_AUTH;
            }
        }
        return HookResult.DECLINED;
    }

    private boolean isAnonymous(MaybeSender maybeSender) {
        return maybeSender == null || maybeSender.isNullSender();
    }

    private boolean senderMatchSessionUser(MaybeSender maybeSender, SMTPSession session) {
        Preconditions.checkArgument(!maybeSender.isNullSender());

        String authUser = session.getUser().toLowerCase(Locale.US);
        String username = getUser(maybeSender.get());

        return username.equals(authUser);
    }

    private boolean belongsToLocalDomain(MaybeSender maybeSender) {
        Preconditions.checkArgument(!maybeSender.isNullSender());
        return isLocalDomain(maybeSender.get().getDomain());
    }

    /**
     * Return true if the given domain is a local domain for this server
     * 
     * @param domain
     * @return isLocal
     */
    protected abstract boolean isLocalDomain(Domain domain);
    
    /**
     * Return the username corresponding to the given mail address.
     * 
     * @return username corresponding to the mail address
     */
    protected abstract String getUser(MailAddress mailAddress);

}
