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

package org.apache.james.smtpserver.fastfail;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.smtpserver.JamesMessageHook;
import org.apache.james.util.scanner.SpamAssassinInvoker;
import org.apache.mailet.Mail;

/**
 * <p>
 * This MessageHandler could be used to check message against spamd before
 * accept the email. So its possible to reject a message on smtplevel if a
 * configured hits amount is reached. The handler add the follow attributes to
 * the mail object:
 * 
 * <pre>
 * <code>
 * org.apache.james.spamassassin.status - Holds the status
 * org.apache.james.spamassassin.flag - Holds the flag
 * </code>
 * </pre>
 * 
 * </p>
 * <p>
 * Sample Configuration:
 * 
 * <pre>
 * &lt;handler class="org.apache.james.smtpserver.SpamAssassinHandler"&gt;
 *   &lt;spamdHost&gt;localhost&lt;/spamdHost&gt;
 *   &lt;spamdPort&gt;783&lt;/spamdPort&gt; <br>
 *   &lt;spamdRejectionHits&gt;15.0&lt;/spamdRejectionHits&gt;
 *   &lt;checkAuthNetworks&gt;false&lt;/checkAuthNetworks&gt;
 * &lt;/handler&gt;
 * </pre>
 * 
 * </p>
 */
public class SpamAssassinHandler implements JamesMessageHook, ProtocolHandler {

    /** The port spamd is listen on */
    private int spamdPort = 783;

    /** The host spamd is running on */
    private String spamdHost = "localhost";

    /** The hits on which the message get rejected */
    private double spamdRejectionHits = 0.0;

    /**
     * Set the host the spamd daemon is running at
     * 
     * @param spamdHost
     *            The spamdHost
     */
    public void setSpamdHost(String spamdHost) {
        this.spamdHost = spamdHost;
    }

    /**
     * Set the port the spamd daemon is listen on
     * 
     * @param spamdPort
     *            the spamdPort
     */
    public void setSpamdPort(int spamdPort) {
        this.spamdPort = spamdPort;
    }

    /**
     * Set the hits on which the message will be rejected.
     * 
     * @param spamdRejectionHits
     *            The hits
     */
    public void setSpamdRejectionHits(double spamdRejectionHits) {
        this.spamdRejectionHits = spamdRejectionHits;

    }

    /**
     * @see org.apache.james.smtpserver.JamesMessageHook#onMessage(org.apache.james.protocols.smtp.SMTPSession,
     *      org.apache.mailet.Mail)
     */
    public HookResult onMessage(SMTPSession session, Mail mail) {

        try {
            MimeMessage message = mail.getMessage();
            SpamAssassinInvoker sa = new SpamAssassinInvoker(spamdHost, spamdPort);
            sa.scanMail(message);

            // Add the headers
            for (String key : sa.getHeadersAsAttribute().keySet()) {
                mail.setAttribute(key, sa.getHeadersAsAttribute().get(key));
            }

            // Check if rejectionHits was configured
            if (spamdRejectionHits > 0) {
                try {
                    double hits = Double.parseDouble(sa.getHits());

                    // if the hits are bigger the rejectionHits reject the
                    // message
                    if (spamdRejectionHits <= hits) {
                        String buffer = "Rejected message from " + session.getAttachment(SMTPSession.SENDER, State.Transaction).toString() + " from host " + session.getRemoteAddress().getHostName() + " (" + session.getRemoteAddress().getAddress().getHostAddress() + ") This message reach the spam hits treshold. Required rejection hits: " + spamdRejectionHits + " hits: " + hits;
                        session.getLogger().info(buffer);

                        // Message reject .. abort it!
                        return new HookResult(HookReturnCode.DENY, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.SECURITY_OTHER) + " This message reach the spam hits treshold. Please contact the Postmaster if the email is not SPAM. Message rejected");
                    }
                } catch (NumberFormatException e) {
                    // hits unknown
                }
            }
        } catch (MessagingException e) {
            session.getLogger().error(e.getMessage());
        }
        return new HookResult(HookReturnCode.DECLINED);
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {
        setSpamdHost(config.getString("spamdHost", "localhost"));
        setSpamdPort(config.getInt("spamdPort", 783));
        setSpamdRejectionHits(config.getDouble("spamdRejectionHits", 0.0));        
    }

    @Override
    public void destroy() {
        // nothing to-do
    }
}
