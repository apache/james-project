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

import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.smtp.MailAddress;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.RcptHook;

/**
 * Handler which check if the authenticated user is the same as the one used as MAIL FROM
 */
public abstract class AbstractSenderAuthIdentifyVerificationRcptHook implements RcptHook {  
    private static final HookResult INVALID_AUTH =  new HookResult(HookReturnCode.DENY, 
            SMTPRetCode.BAD_SEQUENCE,
            DSNStatus.getStatus(DSNStatus.PERMANENT,
                    DSNStatus.SECURITY_AUTH)
                    + " Incorrect Authentication for Specified Email Address");
    /**
     * @see org.apache.james.protocols.smtp.hook.RcptHook#doRcpt(org.apache.james.protocols.smtp.SMTPSession,
     *      org.apache.mailet.MailAddress, org.apache.mailet.MailAddress)
     */
    public HookResult doRcpt(SMTPSession session, MailAddress sender,
            MailAddress rcpt) {
        if (session.getUser() != null) {
            String authUser = (session.getUser()).toLowerCase(Locale.US);
            MailAddress senderAddress = (MailAddress) session.getAttachment(
                    SMTPSession.SENDER, ProtocolSession.State.Transaction);
            String username= null;

            if (senderAddress != null) {
                if (useVirtualHosting()) {
                    username = senderAddress.toString();
                } else {
                    username = senderAddress.getLocalPart();
                }
            }
            
            // Check if the sender address is the same as the user which was used to authenticate.
            // Its important to ignore case here to fix JAMES-837. This is save todo because if the handler is called
            // the user was already authenticated
            if ((senderAddress == null)
                    || (!authUser.equalsIgnoreCase(username))
                    || (!isLocalDomain(senderAddress.getDomain()))) {
                return INVALID_AUTH;
            }
        }
        return HookResult.declined();
    }
    
    
    /**
     * Return true if the given domain is a local domain for this server
     * 
     * @param domain
     * @return isLocal
     */
    protected abstract boolean isLocalDomain(String domain);
    
    /**
     * Return true if virtualHosting should get used. If so the full email address will get used to 
     * match against the supplied auth username
     * 
     * @return useVirtualHosting
     */
    protected abstract boolean useVirtualHosting();

}
