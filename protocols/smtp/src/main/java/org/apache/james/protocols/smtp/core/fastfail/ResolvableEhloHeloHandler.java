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


import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.smtp.MailAddress;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HeloHook;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.RcptHook;


/**
 * This CommandHandler can be used to reject not resolvable EHLO/HELO
 */
public class ResolvableEhloHeloHandler implements RcptHook, HeloHook {

    public final static String BAD_EHLO_HELO = "BAD_EHLO_HELO";

    /**
     * Check if EHLO/HELO is resolvable
     * 
     * @param session
     *            The SMTPSession
     * @param argument
     *            The argument
     */
    protected void checkEhloHelo(SMTPSession session, String argument) {
        
        if (isBadHelo(session, argument)) {
            session.setAttachment(BAD_EHLO_HELO, "true", State.Transaction);
        }
    }
    
    protected String resolve(String host) throws UnknownHostException {
        return InetAddress.getByName(host).getHostName();
    }
    /**
     * @param session the SMTPSession
     * @param argument the argument
     * @return true if the helo is bad.
     */
    protected boolean isBadHelo(SMTPSession session, String argument) {
        // try to resolv the provided helo. If it can not resolved do not
        // accept it.
        try {
            resolve(argument);
        } catch (UnknownHostException e) {
            return true;
        }
        return false;
        
    }

    protected boolean check(SMTPSession session,MailAddress rcpt) {
        // not reject it
        if (session.getAttachment(BAD_EHLO_HELO, State.Transaction) == null) {
            return false;
        }

        return true;
    }

    /**
     * @see org.apache.james.protocols.smtp.hook.RcptHook#doRcpt(org.apache.james.protocols.smtp.SMTPSession, org.apache.mailet.MailAddress, org.apache.mailet.MailAddress)
     */
    public HookResult doRcpt(SMTPSession session, MailAddress sender, MailAddress rcpt) {
        if (check(session,rcpt)) {
            return new HookResult(HookReturnCode.DENY,SMTPRetCode.SYNTAX_ERROR_ARGUMENTS,DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_ARG)
                    + " Provided EHLO/HELO " + session.getAttachment(SMTPSession.CURRENT_HELO_NAME, State.Connection) + " can not resolved.");
        } else {
            return HookResult.declined();
        }
    }

    /**
     * @see org.apache.james.protocols.smtp.hook.HeloHook#doHelo(org.apache.james.protocols.smtp.SMTPSession, java.lang.String)
     */
    public HookResult doHelo(SMTPSession session, String helo) {
        checkEhloHelo(session, helo);
        return HookResult.declined();
    }

}
