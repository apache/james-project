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

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.RcptHook;

/**
 * Handler which check for authenticated users
 */
public abstract class AbstractAuthRequiredToRelayRcptHook implements RcptHook {

    private static final HookResult AUTH_REQUIRED = new HookResult(HookReturnCode.deny(),
            SMTPRetCode.AUTH_REQUIRED, DSNStatus.getStatus(
                    DSNStatus.PERMANENT,
                    DSNStatus.SECURITY_AUTH)
                    + " Authentication Required");
    private static final HookResult RELAYING_DENIED = new HookResult(
            HookReturnCode.deny(),
            // sendmail returns 554 (SMTPRetCode.TRANSACTION_FAILED).
            // it is not clear in RFC wether it is better to use 550 or 554.
            SMTPRetCode.MAILBOX_PERM_UNAVAILABLE,
            DSNStatus.getStatus(DSNStatus.PERMANENT,
                    DSNStatus.SECURITY_AUTH)
                    + " Requested action not taken: relaying denied");
    
    @Override
    public HookResult doRcpt(SMTPSession session, MailAddress sender,
                             MailAddress rcpt) {
        if (!session.isRelayingAllowed()) {
            Domain toDomain = rcpt.getDomain();
            if (!isLocalDomain(toDomain)) {
                if (session.isAuthSupported()) {
                    return AUTH_REQUIRED;
                } else {
                    return RELAYING_DENIED;
                }
            }

        }
        return HookResult.DECLINED;
    }

    
    /**
     * Return true if the given domain is a local domain for this server
     * 
     * @param domain
     * @return isLocal
     */
    protected abstract boolean isLocalDomain(Domain domain);
    
}
