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

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HeloHook;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.MailHook;
import org.apache.james.protocols.smtp.hook.RcptHook;


/**
 * This CommandHandler can be used to reject not resolvable EHLO/HELO
 */
public class ResolvableEhloHeloHandler implements HeloHook, MailHook, RcptHook {

    public static final ProtocolSession.AttachmentKey<Boolean> BAD_EHLO_HELO = ProtocolSession.AttachmentKey.of("BAD_EHLO_HELO", Boolean.class);

    /**
     * Check if EHLO/HELO is resolvable
     * 
     * @param session
     *            The SMTPSession
     * @param argument
     *            The argument
     */
    protected void checkEhloHelo(SMTPSession session, String argument) {
        if (isBadHelo(argument)) {
            session.setAttachment(BAD_EHLO_HELO, true, State.Transaction);
        }
    }
    
    protected String resolve(String host) throws UnknownHostException {
        return InetAddress.getByName(host).getHostName();
    }

    protected boolean isBadHelo(String argument) {
        // try to resolv the provided helo. If it can not resolved do not
        // accept it.
        try {
            resolve(argument);
        } catch (UnknownHostException e) {
            return true;
        }
        return false;
        
    }

    protected HookResult doCheck(SMTPSession session) {
        if (session.getAttachment(BAD_EHLO_HELO, State.Transaction).isPresent()) {
            return HookResult.builder()
                .hookReturnCode(HookReturnCode.deny())
                .smtpReturnCode(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS)
                .smtpDescription(DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_ARG)
                    + " Provided EHLO/HELO " + session.getAttachment(SMTPSession.CURRENT_HELO_NAME, State.Connection) + " can not resolved.")
                .build();
        } else {
            return HookResult.DECLINED;
        }
    }

    @Override
    public HookResult doMail(SMTPSession session, MaybeSender sender) {
        return doCheck(session);
    }

    @Override
    public HookResult doRcpt(SMTPSession session, MaybeSender sender, MailAddress rcpt) {
        return doCheck(session);
    }

    @Override
    public HookResult doHelo(SMTPSession session, String helo) {
        checkEhloHelo(session, helo);
        return HookResult.DECLINED;
    }

}
