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

package org.apache.james.healthcheck;

import static reactor.core.publisher.Sinks.EmitFailureHandler.FAIL_FAST;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;

import jakarta.mail.internet.InternetAddress;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.events.Event;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventListener;
import org.apache.james.events.Registration;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.server.core.MailImpl;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.DurationParser;
import org.apache.mailet.MailetContext;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

public class MailReceptionCheck implements HealthCheck {

    public static final ComponentName COMPONENT_NAME = new ComponentName("MailReceptionCheck");

    public static class Configuration {
        private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1);
        public static final Configuration DEFAULT = new Configuration(Optional.empty(), DEFAULT_TIMEOUT);

        public static Configuration from(org.apache.commons.configuration2.Configuration configuration) {
            Optional<Username> username = Optional.ofNullable(configuration.getString("reception.check.user", null))
                .map(Username::of);
            Duration timeout = Optional.ofNullable(configuration.getString("reception.check.timeout", null))
                .map(s -> DurationParser.parse(s, ChronoUnit.SECONDS))
                .orElse(DEFAULT_TIMEOUT);

            return new Configuration(username, timeout);
        }

        private final Optional<Username> checkUser;
        private final Duration timeout;

        public Configuration(Optional<Username> checkUser, Duration timeout) {
            this.checkUser = checkUser;
            this.timeout = timeout;
        }

        public Optional<Username> getCheckUser() {
            return checkUser;
        }

        public Duration getTimeout() {
            return timeout;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Configuration) {
                Configuration that = (Configuration) o;
                return Objects.equals(checkUser, that.checkUser)
                    && Objects.equals(timeout, that.timeout);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(checkUser, timeout);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("checkUser", checkUser)
                .add("timeout", timeout)
                .toString();
        }
    }

    public static class Content {
        public static Content generate() {
            return new Content(UUID.randomUUID());
        }

        private final UUID uuid;

        private Content(UUID uuid) {
            this.uuid = uuid;
        }

        public String asString() {
            return uuid.toString();
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Content) {
                Content that = (Content) o;
                return Objects.equals(uuid, that.uuid);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(uuid);
        }

        @Override
        public String toString() {
            return asString();
        }
    }

    public static class AwaitReceptionListener implements EventListener.ReactiveEventListener {
        private final Sinks.Many<Result> sink;
        private final MessageManager mailbox;
        private final Content content;
        private final MailboxSession session;

        public AwaitReceptionListener(MessageManager mailbox, Content content, MailboxSession session) {
            this.mailbox = mailbox;
            this.content = content;
            this.session = session;
            this.sink = Sinks.many().multicast().onBackpressureBuffer();
        }

        @Override
        public Publisher<Void> reactiveEvent(Event event) {
            if (event instanceof Added) {
                Added added = (Added) event;
                return checkReceived(added)
                    .flatMap(result -> Mono.fromRunnable(() -> sink.emitNext(result, FAIL_FAST))
                        .subscribeOn(Schedulers.boundedElastic()))
                    .then();
            }
            return Mono.empty();
        }

        private Mono<Result> checkReceived(Added added) {
            return Flux.fromIterable(added.getUids())
                .flatMap(uid -> Flux.from(mailbox.getMessagesReactive(MessageRange.one(uid), FetchGroup.FULL_CONTENT, session)))
                .filter(Throwing.predicate(messageResult -> IOUtils.toString(messageResult.getBody().getInputStream(), StandardCharsets.US_ASCII)
                    .contains(content.asString())))
                // Cleanup our testing mail
                .concatMap(messageResult -> Mono.from(mailbox.deleteReactive(ImmutableList.of(messageResult.getUid()), session))
                    .onErrorResume(e -> {
                        LOGGER.warn("Failed to delete Health check testing email", e);
                        return Mono.empty();
                    })
                    .thenReturn(messageResult))
                .map(any -> Result.healthy(COMPONENT_NAME))
                .next();
        }

        public Flux<Result> results() {
            return sink.asFlux();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MailReceptionCheck.class);

    private final MailetContext mailetContext;
    private final MailboxManager mailboxManager;
    private final EventBus eventBus;
    private final UsersRepository usersRepository;
    private final Configuration configuration;

    @Inject
    public MailReceptionCheck(MailetContext mailetContext, MailboxManager mailboxManager, EventBus eventBus, UsersRepository usersRepository, Configuration configuration) {
        this.mailetContext = mailetContext;
        this.mailboxManager = mailboxManager;
        this.eventBus = eventBus;
        this.usersRepository = usersRepository;
        this.configuration = configuration;
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Publisher<Result> check() {
        return configuration.getCheckUser()
            .map(this::check)
            .orElse(Mono.just(Result.healthy(componentName())));
    }

    private Mono<Result> check(Username username) {
        Content content = Content.generate();
        MailboxSession session = mailboxManager.createSystemSession(username);

        return retrieveInbox(username, session)
            .flatMap(mailbox -> {
                AwaitReceptionListener listener = new AwaitReceptionListener(mailbox, content, session);
                return Mono.usingWhen(
                    Mono.from(eventBus.register(listener, new MailboxIdRegistrationKey(mailbox.getId()))),
                    registration -> sendMail(username, content)
                        .flatMap(any -> checkReceived(listener)),
                    Registration::unregister);
            })
            .timeout(configuration.getTimeout(), Mono.error(() -> new RuntimeException("HealthCheck email was not received after " + configuration.getTimeout().toMillis() + "ms")))
            .onErrorResume(e -> {
                LOGGER.error("Mail reception check failed", e);
                return Mono.just(Result.unhealthy(componentName(), e.getMessage()));
            })
            .doFinally(any -> mailboxManager.endProcessingRequest(session));
    }

    private Mono<MessageManager> retrieveInbox(Username username, MailboxSession session) {
        MailboxPath mailboxPath = MailboxPath.inbox(username);
        return Mono.from(mailboxManager.getMailboxReactive(mailboxPath, session))
            .onErrorResume(MailboxNotFoundException.class, e -> Mono.from(mailboxManager.createMailboxReactive(mailboxPath, session))
                .then(Mono.from(mailboxManager.getMailboxReactive(mailboxPath, session))));
    }

    private Mono<Result> checkReceived(AwaitReceptionListener listener) {
        return listener.results()
            .map(any -> Result.healthy(componentName()))
            .next();
    }

    private Mono<Content> sendMail(Username username, Content content) {

        return Mono.fromCallable(() -> usersRepository.getMailAddressFor(username))
            .flatMap(address ->
                Mono.using(() -> MailImpl.builder()
                    .name(content.asString())
                    .sender(address)
                    .addRecipient(address)
                    .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                        .addFrom(new InternetAddress(address.asString()))
                        .addToRecipient(address.asString())
                        .setSubject(content.asString())
                        .setText(content.asString()))
                    .build(),
                    mail -> Mono.fromRunnable(Throwing.runnable(() -> mailetContext.sendMail(mail))),
                    LifecycleUtil::dispose))
            .thenReturn(content)
            .subscribeOn(Schedulers.boundedElastic());
    }
}
