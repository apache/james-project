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

package org.apache.james.spamassassin;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.util.Host;
import org.apache.mailet.Attribute;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.base.GenericMailet;

import com.github.fge.lambdas.Throwing;


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
 * Options:
 *
 *  - perUserScans: Boolean. If true spamAssassin is called on a per user basis which comes at a performance cost but
 *  allows per-user feedback. Defaults to true.
 *
 * Sample Configuration:
 *
 * <pre>
 * &lt;mailet notmatch="All" class="SpamAssassin"&gt;
 *   &lt;perUserScans"&gt;false&lt;/perUserScans"&gt;
 * &lt;/mailet&gt;
 * </pre>
 */
public class SpamAssassin extends GenericMailet {
    private final MetricFactory metricFactory;
    private final UsersRepository usersRepository;

    private final Host spamAssassinHost;
    private boolean perUserScans;

    @Inject
    public SpamAssassin(MetricFactory metricFactory, UsersRepository usersRepository, SpamAssassinConfiguration spamAssassinConfiguration) {
        this.metricFactory = metricFactory;
        this.usersRepository = usersRepository;
        this.spamAssassinHost = spamAssassinConfiguration.getHost();
    }


    @Override
    public void init() {
        perUserScans = getBooleanParameter(getInitParameter("perUserScans"), true);
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        MimeMessage message = mail.getMessage();

        // Invoke SpamAssassin connection and scan the message
        SpamAssassinInvoker sa = new SpamAssassinInvoker(metricFactory, spamAssassinHost.getHostName(), spamAssassinHost.getPort());
        if (perUserScans) {
            mail.getRecipients()
                .forEach(
                    Throwing.consumer((MailAddress recipient) -> querySpamAssassin(mail, message, sa, recipient))
                        .sneakyThrow());
        } else {
            querySpamAssassin(mail, message, sa);
        }
    }

    private void querySpamAssassin(Mail mail, MimeMessage message, SpamAssassinInvoker sa, MailAddress recipient) throws MessagingException, UsersRepositoryException {
        SpamAssassinResult result = sa.scanMail(message, usersRepository.getUsername(recipient));

        // Add headers per recipient to mail object
        for (Attribute attribute : result.getHeadersAsAttributes()) {
            mail.addSpecificHeaderForRecipient(PerRecipientHeaders.Header.builder()
                .name(attribute.getName().asString())
                .value((String) attribute.getValue().value())
                .build(), recipient);
        }
    }

    private void querySpamAssassin(Mail mail, MimeMessage message, SpamAssassinInvoker sa) throws MessagingException {
        SpamAssassinResult result = sa.scanMail(message);

        // Add headers per recipient to mail object
        for (Attribute attribute : result.getHeadersAsAttributes()) {
            mail.getMessage().addHeader(attribute.getName().asString(),
                (String) attribute.getValue().value());
        }
    }

    @Override
    public String getMailetInfo() {
        return "Checks message against SpamAssassin";
    }
}
