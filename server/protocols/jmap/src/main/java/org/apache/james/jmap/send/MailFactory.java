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

package org.apache.james.jmap.send;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;

import org.apache.james.server.core.MailImpl;
import org.apache.james.jmap.model.Emailer;
import org.apache.james.jmap.model.Message;
import org.apache.james.jmap.model.MessageFactory.MetaDataWithContent;
import org.apache.mailet.Mail;
import org.apache.james.core.MailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class MailFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailFactory.class);
    
    @VisibleForTesting MailFactory() {
    }

    public Mail build(MetaDataWithContent message, Message jmapMessage) throws MessagingException, IOException {
        MailAddress sender = jmapMessage.getFrom()
                .map(this::emailerToMailAddress)
                .orElseThrow(() -> new RuntimeException("Sender is mandatory"));
        Set<MailAddress> to = emailersToMailAddressSet(jmapMessage.getTo());
        Set<MailAddress> cc = emailersToMailAddressSet(jmapMessage.getCc());
        Set<MailAddress> bcc = emailersToMailAddressSet(jmapMessage.getBcc());
        ImmutableSet<MailAddress> recipients = Sets.union(
                Sets.union(to, cc),
                bcc).immutableCopy();
        return new MailImpl(jmapMessage.getId().serialize(), sender, recipients, message.getContent());
    }

    private MailAddress emailerToMailAddress(Emailer emailer) {
        Preconditions.checkArgument(emailer.getEmail().isPresent(), "eMailer mail address should be present when sending a mail using JMAP");
        try {
            return new MailAddress(emailer.getEmail().get());
        } catch (AddressException e) {
            LOGGER.error("Invalid mail address", emailer.getEmail());
            throw Throwables.propagate(e);
        }
    }

    private Set<MailAddress> emailersToMailAddressSet(List<Emailer> emailers) {
        return emailers.stream()
            .map(this::emailerToMailAddress)
            .collect(Collectors.toSet());
    }
}
