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
package org.apache.james.smtpserver;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Mail;

/**
 * This hook adds the default attributes to the just created Mail
 */
public class AddDefaultAttributesMessageHook implements JamesMessageHook {

    /**
     * The mail attribute which get set if the client is allowed to relay
     */
    public static final String SMTP_AUTH_NETWORK_NAME = "org.apache.james.SMTPIsAuthNetwork";

    @Override
    public void init(Configuration config) throws ConfigurationException {

    }

    @Override
    public void destroy() {

    }

    public HookResult onMessage(SMTPSession session, Mail mail) {
        if (mail instanceof MailImpl) {

            final MailImpl mailImpl = (MailImpl) mail;
            mailImpl.setRemoteHost(session.getRemoteAddress().getHostName());
            mailImpl.setRemoteAddr(session.getRemoteAddress().getAddress().getHostAddress());
            if (session.getUser() != null) {
                mail.setAttribute(Mail.SMTP_AUTH_USER_ATTRIBUTE_NAME, session.getUser());
            }

            if (session.isRelayingAllowed()) {
                mail.setAttribute(SMTP_AUTH_NETWORK_NAME, "true");
            }
        }
        return new HookResult(HookReturnCode.DECLINED);
    }

}
