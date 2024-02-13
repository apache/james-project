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

import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
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
    public HookResult onMessage(SMTPSession session, Mail mail) {
        if (mail instanceof MailImpl) {

            final MailImpl mailImpl = (MailImpl) mail;
            mailImpl.setRemoteHost(session.getRemoteAddress().getHostName());
            mailImpl.setRemoteAddr(session.getRemoteAddress().getAddress().getHostAddress());

            mail.setAttribute(new Attribute(Mail.SMTP_SESSION_ID, AttributeValue.of(session.getSessionID())));

            session.getAttachment(SMTPSession.CURRENT_HELO_NAME, ProtocolSession.State.Connection)
                .ifPresent(helo ->  mail.setAttribute(new Attribute(Mail.SMTP_HELO, AttributeValue.of(helo))));

            session.getSSLSession().ifPresent(sslSession -> {
                mail.setAttribute(new Attribute(Mail.SSL_PROTOCOL, AttributeValue.of(sslSession.getProtocol())));
                mail.setAttribute(new Attribute(Mail.SSL_CIPHER, AttributeValue.of(sslSession.getCipherSuite())));
            });

            if (session.getUsername() != null) {
                mail.setAttribute(new Attribute(Mail.SMTP_AUTH_USER, AttributeValue.of(session.getUsername().asString())));
            }

            if (session.isRelayingAllowed()) {
                mail.setAttribute(Attribute.convertToAttribute(SMTP_AUTH_NETWORK_NAME, true));
            }
        }
        return HookResult.DECLINED;
    }

}
