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

package org.apache.james.transport.mailets;

import java.util.Optional;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.util.Port;
import org.apache.james.util.scanner.SpamAssassinInvoker;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.MailetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

/**
 * Sends the message through daemonized SpamAssassin (spamd), visit <a
 * href="http://spamassassin.apache.org/">spamassassin.apache.org/</a> for info
 * on configuration. The header X-Spam-Status is added to every message, this
 * contains the score and the threshold score for spam (usually 5.0). If the
 * message exceeds the threshold, the header X-Spam-Flag will be added with the
 * value of YES. The default host for spamd is localhost and the default port is
 * 783.
 * 
 * <pre>
 * <code>
 *  org.apache.james.spamassassin.status - Holds the status
 *  org.apache.james.spamassassin.flag   - Holds the flag
 * </code>
 * </pre>
 * 
 * Sample Configuration:
 * 
 * <pre>
 * &lt;mailet notmatch="SenderHostIsLocal" class="SpamAssassin"&gt;
 * &lt;spamdHost&gt;localhost&lt;/spamdHost&gt;
 * &lt;spamdPort&gt;783&lt;/spamdPort&gt;
 * </pre>
 */
public class SpamAssassin extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpamAssassin.class);
    public static final String SPAMD_HOST = "spamdHost";
    public static final String SPAMD_PORT = "spamdPort";
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 783;

    private String spamdHost;
    private int spamdPort;

    /**
     * @see org.apache.mailet.base.GenericMailet#init()
     */
    public void init() throws MessagingException {
        spamdHost = Optional.ofNullable(getInitParameter(SPAMD_HOST))
            .filter(s -> !Strings.isNullOrEmpty(s))
            .orElse(DEFAULT_HOST);

        spamdPort = MailetUtil.getInitParameterAsStrictlyPositiveInteger(getInitParameter(SPAMD_PORT), DEFAULT_PORT);
        Port.assertValid(spamdPort);
    }

    /**
     * @see org.apache.mailet.base.GenericMailet#service(Mail)
     */
    public void service(Mail mail) throws MessagingException {
        MimeMessage message = mail.getMessage();

        // Invoke SpamAssassin connection and scan the message
        SpamAssassinInvoker sa = new SpamAssassinInvoker(spamdHost, spamdPort);
        sa.scanMail(message);

        // Add headers as attribute to mail object
        for (String key : sa.getHeadersAsAttribute().keySet()) {
            mail.setAttribute(key, sa.getHeadersAsAttribute().get(key));
        }

        message.saveChanges();
    }

    /**
     * @see org.apache.mailet.base.GenericMailet#getMailetInfo()
     */
    public String getMailetInfo() {
        return "Checks message against SpamAssassin";
    }

    @VisibleForTesting
    String getSpamdHost() {
        return spamdHost;
    }

    @VisibleForTesting
    int getSpamdPort() {
        return spamdPort;
    }
}
