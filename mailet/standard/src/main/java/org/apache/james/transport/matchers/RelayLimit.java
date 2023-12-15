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



package org.apache.james.transport.matchers;

import java.util.Collection;
import java.util.Enumeration;

import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;
import org.apache.mailet.base.MailetUtil;
import org.apache.mailet.base.RFC2822Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Matches mail which has been relayed more than a given number of times.
 * @version 1.0.0, 1/5/2000
 */
public class RelayLimit extends GenericMatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(RelayLimit.class);
    int limit = 30;

    @Override
    public void init() throws MessagingException {
        limit = MailetUtil.getInitParameterAsStrictlyPositiveInteger(getCondition());
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws javax.mail.MessagingException {
        MimeMessage mm = mail.getMessage();
        int count = 0;
        for (Enumeration<Header> e = mm.getAllHeaders(); e.hasMoreElements();) {
            Header hdr = e.nextElement();
            if (hdr.getName().equals(RFC2822Headers.RECEIVED)) {
                count++;
            }
        }
        if (count >= limit) {
            LOGGER.error("{} with Message-ID {} exceeded relay limit: {} Received headers", mail.getName(), mail.getMessage().getMessageID(), count);
            return mail.getRecipients();
        } else {
            return null;
        }
    }
}
