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

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.transport.mailets.delivery.MailStore;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.Attribute;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Streams;
import reactor.core.publisher.Mono;

/**
 * Process messages and randomly assign them to 4 to 8 mailboxes.
 */
public class RandomStoring extends GenericMailet {

    private static final int MIN_NUMBER_OF_RECIPIENTS = 4;
    private static final int MAX_NUMBER_OF_RECIPIENTS = 8;
    private static final Duration CACHE_DURATION = Duration.ofMinutes(15);

    private final Mono<List<ReroutingInfos>> reroutingInfos;
    private final UsersRepository usersRepository;
    private final MailboxManager mailboxManager;
    private final Supplier<Integer> randomRecipientsNumbers;

    @Inject
    public RandomStoring(UsersRepository usersRepository, MailboxManager mailboxManager) {
        this.usersRepository = usersRepository;
        this.mailboxManager = mailboxManager;
        this.randomRecipientsNumbers = () -> ThreadLocalRandom.current().nextInt(MIN_NUMBER_OF_RECIPIENTS, MAX_NUMBER_OF_RECIPIENTS + 1);
        this.reroutingInfos = Mono.fromCallable(this::retrieveReroutingInfos).cache(CACHE_DURATION);
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        Collection<ReroutingInfos> reroutingInfos = generateRandomMailboxes();
        Collection<MailAddress> mailAddresses = reroutingInfos
            .stream()
            .map(ReroutingInfos::getMailAddress)
            .collect(Guavate.toImmutableList());

        mail.setRecipients(mailAddresses);
        reroutingInfos.forEach(reroutingInfo ->
            mail.setAttribute(Attribute.convertToAttribute(MailStore.DELIVERY_PATH_PREFIX + reroutingInfo.getUser(), reroutingInfo.getMailbox())));
    }

    @Override
    public String getMailetInfo() {
        return "Random Storing Mailet";
    }

    @Override
    public void init() throws MessagingException {
    }

    private Collection<ReroutingInfos> generateRandomMailboxes() {
        List<ReroutingInfos> reroutingInfos = this.reroutingInfos.block();

        // Replaces Collections.shuffle() which has a too poor statistical distribution
        return ThreadLocalRandom
            .current()
            .ints(0, reroutingInfos.size())
            .mapToObj(reroutingInfos::get)
            .distinct()
            .limit(randomRecipientsNumbers.get())
            .collect(Guavate.toImmutableSet());
    }

    private List<ReroutingInfos> retrieveReroutingInfos() throws UsersRepositoryException {
        return Streams.stream(usersRepository.list())
            .map(Username::fromUsername)
            .flatMap(this::buildReRoutingInfos)
            .collect(Guavate.toImmutableList());
    }

    private Stream<ReroutingInfos> buildReRoutingInfos(Username username) {
        try {
            MailAddress mailAddress = usersRepository.getMailAddressFor(username);

            MailboxSession session = mailboxManager.createSystemSession(username.asString());
            return mailboxManager
                .list(session)
                .stream()
                .map(mailboxPath -> new ReroutingInfos(mailAddress, mailboxPath.getName(), username.asString()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private class ReroutingInfos {
        private final MailAddress mailAddress;
        private final String mailbox;
        private final String user;

        ReroutingInfos(MailAddress mailAddress, String mailbox, String user) {
            this.mailAddress = mailAddress;
            this.mailbox = mailbox;
            this.user = user;
        }

        public MailAddress getMailAddress() {
            return mailAddress;
        }

        public String getMailbox() {
            return mailbox;
        }

        public String getUser() {
            return user;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof ReroutingInfos) {
                ReroutingInfos that = (ReroutingInfos) o;

                return Objects.equals(this.mailAddress, that.mailAddress)
                    && Objects.equals(this.mailbox, that.mailbox)
                    && Objects.equals(this.user, that.user);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mailAddress, mailbox, user);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("user", mailAddress.asString())
                .add("mailbox", mailbox)
                .add("user", user)
                .toString();
        }
    }
}

