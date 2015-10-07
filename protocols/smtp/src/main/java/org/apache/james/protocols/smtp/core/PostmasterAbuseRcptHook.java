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

import org.apache.james.protocols.smtp.MailAddress;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.RcptHook;

/**
 * Handler which whitelist "postmaster" and "abuse" recipients.
 */
public class PostmasterAbuseRcptHook implements RcptHook {
    
    /**
     * @see org.apache.james.protocols.smtp.hook.RcptHook#doRcpt(org.apache.james.protocols.smtp.SMTPSession, org.apache.mailet.MailAddress, org.apache.mailet.MailAddress)
     */
    public HookResult doRcpt(SMTPSession session, MailAddress sender, MailAddress rcpt) {
        if (rcpt.getLocalPart().equalsIgnoreCase("postmaster") || rcpt.getLocalPart().equalsIgnoreCase("abuse")) {
            session.getLogger().debug("Sender allowed");
            return HookResult.ok();
        } else {
            return HookResult.declined();
        }
    }

}
