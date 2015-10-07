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

import org.apache.james.protocols.smtp.MailAddress;
import org.apache.james.protocols.smtp.MailEnvelope;
import org.apache.james.protocols.smtp.SMTPSession;


/**
 * Simple {@link Hook} implementation which can be used as base class when writing simple {@link Hook}'s
 * 
 * The SMTP-Server will just accept email with this {@link Hook} in place and discard it
 *
 */
public class SimpleHook implements HeloHook, MailHook, RcptHook, MessageHook {

    /**
     * Return {@link HookResult} with {@link HookReturnCode#OK}
     */
    public HookResult onMessage(SMTPSession session, MailEnvelope mail) {
        return new HookResult(HookReturnCode.OK);
    }

    /**
     * Return {@link HookResult} with {@link HookReturnCode#DECLINED}
     */
    public HookResult doRcpt(SMTPSession session, MailAddress sender, MailAddress rcpt) {
        return new HookResult(HookReturnCode.DECLINED);

    }

    /**
     * Return {@link HookResult} with {@link HookReturnCode#DECLINED}
     */
    public HookResult doMail(SMTPSession session, MailAddress sender) {
        return new HookResult(HookReturnCode.DECLINED);

    }

    /**
     * Return {@link HookResult} with {@link HookReturnCode#DECLINED}
     */
    public HookResult doHelo(SMTPSession session, String helo) {
        return new HookResult(HookReturnCode.DECLINED);
    }

}
