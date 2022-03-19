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

package org.apache.james.mailbox.quota.mailing.subscribers;

import java.io.IOException;
import java.util.Optional;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventWithState;
import org.apache.james.eventsourcing.Subscriber;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.quota.mailing.QuotaMailingListenerConfiguration;
import org.apache.james.mailbox.quota.mailing.events.QuotaThresholdChangedEvent;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.MailetContext;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

public class QuotaThresholdMailer implements Subscriber {
    private final MailetContext mailetContext;
    private final UsersRepository usersRepository;
    private final FileSystem fileSystem;
    private final QuotaMailingListenerConfiguration configuration;

    public QuotaThresholdMailer(MailetContext mailetContext, UsersRepository usersRepository, FileSystem fileSystem, QuotaMailingListenerConfiguration configuration) {
        this.mailetContext = mailetContext;
        this.usersRepository = usersRepository;
        this.fileSystem = fileSystem;
        this.configuration = configuration;
    }

    @Override
    public void handle(EventWithState eventWithState) {
        Event event = eventWithState.event();
        if (event instanceof QuotaThresholdChangedEvent) {
            handleEvent((QuotaThresholdChangedEvent) event);
        }
    }

    private void handleEvent(QuotaThresholdChangedEvent event) {
        Optional<QuotaThresholdNotice> maybeNotice = QuotaThresholdNotice.builder()
            .countQuota(event.getCountQuota())
            .sizeQuota(event.getSizeQuota())
            .countThreshold(event.getCountHistoryEvolution())
            .sizeThreshold(event.getSizeHistoryEvolution())
            .withConfiguration(configuration)
            .build();

        maybeNotice.ifPresent(Throwing.consumer(notice -> sendNotice(notice, event.getAggregateId().getUsername())));
    }

    private void sendNotice(QuotaThresholdNotice notice, Username username) throws UsersRepositoryException, MessagingException, IOException {
        MailAddress sender = mailetContext.getPostmaster();
        MailAddress recipient = usersRepository.getMailAddressFor(username);

        mailetContext.sendMail(sender, ImmutableList.of(recipient),
            notice.generateMimeMessage(fileSystem)
                .addToRecipient(recipient.asString())
                .addFrom(sender.asString())
                .build());
    }

}
