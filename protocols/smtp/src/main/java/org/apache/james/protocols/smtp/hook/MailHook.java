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

package org.apache.james.protocols.smtp.hook;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.protocols.smtp.SMTPSession;

/**
 * Implement this interfaces to hook in the MAIL Command
 * 
 */
public interface MailHook extends Hook {

    /**
     * Return the HookResult after run the hook
     *
     * @Deprecated use {@link #doMail(SMTPSession, MaybeSender)} instead
     * 
     * @param session the SMTPSession
     * @param sender the sender MailAddress
     * @return HockResult
     */
    @Deprecated
    default HookResult doMail(SMTPSession session, MailAddress sender) {
        return doMail(session, MaybeSender.of(sender));
    }

    /**
     * Return the HookResult after run the hook
     *
     * This strongly typed version of do mail is safer to use.
     *
     * @since James 3.2.0
     */
    default HookResult doMail(SMTPSession session, MaybeSender sender) {
        return doMail(session, sender.asOptional().orElse(MailAddress.nullSender()));
    }
}
