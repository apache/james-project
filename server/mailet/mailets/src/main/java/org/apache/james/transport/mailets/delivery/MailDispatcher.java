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
package org.apache.james.transport.mailets.delivery;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.server.core.MailImpl;
import org.apache.james.util.AuditTrail;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.apache.mailet.PerRecipientHeaders.Header;
import org.apache.mailet.base.RFC2822Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

public class MailDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailDispatcher.class);
    private static final String[] NO_HEADERS = {};
    public static final int RETRIES = 3;
    private static final Duration FIRST_BACKOFF = Duration.ofMillis(200);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(1);

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        static final boolean DEFAULT_CONSUME = true;
        static final String DEFAULT_ERROR_PROCESSOR = Mail.ERROR;
        private MailStore mailStore;
        private Boolean consume;
        private MailetContext mailetContext;
        private String onMailetException;
        private Optional<Integer> retries = Optional.empty();

        public Builder consume(boolean consume) {
            this.consume = consume;
            return this;
        }

        public Builder mailStore(MailStore mailStore) {
            this.mailStore = mailStore;
            return this;
        }

        public Builder mailetContext(MailetContext mailetContext) {
            this.mailetContext = mailetContext;
            return this;
        }

        public Builder onMailetException(String onMailetException) {
            this.onMailetException = onMailetException;
            return this;
        }

        public Builder retries(int retries) {
            if (retries > 0) {
                this.retries = Optional.of(retries);
            }
            return this;
        }

        public MailDispatcher build() {
            Preconditions.checkNotNull(mailStore);
            Preconditions.checkNotNull(mailetContext);
            return new MailDispatcher(mailStore, mailetContext,
                Optional.ofNullable(consume).orElse(DEFAULT_CONSUME),
                retries, Optional.ofNullable(onMailetException).orElse(DEFAULT_ERROR_PROCESSOR));
        }
    }

    private final MailStore mailStore;
    private final MailetContext mailetContext;
    private final boolean consume;
    private final boolean ignoreError;
    private final boolean propagate;
    private final Optional<Integer> retries;
    private final String errorProcessor;

    private MailDispatcher(MailStore mailStore, MailetContext mailetContext, boolean consume, Optional<Integer> retries, String onMailetException) {
        this.mailStore = mailStore;
        this.consume = consume;
        this.mailetContext = mailetContext;
        this.retries = retries;
        this.errorProcessor = onMailetException;
        this.ignoreError = onMailetException.equalsIgnoreCase("ignore");
        this.propagate = onMailetException.equalsIgnoreCase("propagate");
    }

    public void dispatch(Mail mail) throws MessagingException {
        List<MailAddress> errors = customizeHeadersAndDeliver(mail);
        if (!errors.isEmpty() && !ignoreError) {
            // If there were errors, we redirect the email to the ERROR
            // processor.
            // In order for this server to meet the requirements of the SMTP
            // specification, mails on the ERROR processor must be returned to
            // the sender. Note that this email doesn't include any details
            // regarding the details of the failure(s).
            // In the future we may wish to address this.
            Mail newMail = MailImpl.builder()
                .name("error-" + mail.getName())
                .sender(mail.getMaybeSender())
                .addRecipients(errors)
                .mimeMessage(mail.getMessage())
                .state(errorProcessor)
                .build();
            try {
                mailetContext.sendMail(newMail);
            } finally {
                LifecycleUtil.dispose(newMail);
            }
        }
        if (consume) {
            // Consume this message
            mail.setState(Mail.GHOST);
        }
    }

    private List<MailAddress> customizeHeadersAndDeliver(Mail mail) throws MessagingException {
        MimeMessage message = mail.getMessage();
        // Set Return-Path and remove all other Return-Path headers from the message
        // This only works because there is a placeholder inserted by MimeMessageWrapper
        message.setHeader(RFC2822Headers.RETURN_PATH, mail.getMaybeSender().asPrettyString());

        return deliver(mail, message);
    }

    private List<MailAddress> deliver(Mail mail, MimeMessage message) {
        return Flux.fromIterable(mail.getRecipients())
            .concatMap(recipient ->
                Mono.using(
                    () -> saveHeaders(mail, recipient),
                    Throwing.function(any -> {
                        addSpecificHeadersForRecipient(mail, message, recipient);
                        return storeMailWithRetry(mail, recipient)
                            .doOnSuccess(Throwing.consumer(success -> AuditTrail.entry()
                                .protocol("mailetcontainer")
                                .action("LocalDelivery")
                                .username(recipient::asString)
                                .parameters(Throwing.supplier(() -> ImmutableMap.of("mailId", mail.getName(),
                                    "mimeMessageId", Optional.ofNullable(mail.getMessage())
                                        .map(Throwing.function(MimeMessage::getMessageID))
                                        .orElse(""),
                                    "sender", mail.getMaybeSender().asString(),
                                    "recipient", recipient.asString())))
                                .log("Local delivered mail.")))
                            .then(Mono.<MailAddress>empty());
                    }),
                    Throwing.consumer(savedHeaders -> restoreHeaders(mail.getMessage(), savedHeaders)))
                    .onErrorResume(ex -> {
                        LOGGER.error("Error while storing mail. This is a final exception.", ex);
                        if (propagate) {
                            return Mono.error(ex);
                        }
                        return Mono.just(recipient);
                    }))
            .collectList()
            .block();
    }

    private Mono<Void> storeMailWithRetry(Mail mail, MailAddress recipient) {
        AtomicInteger remainRetries = new AtomicInteger(retries.orElse(0));

        Mono<Void> operation = Mono.from(mailStore.storeMail(recipient, mail))
            .doOnError(error -> LOGGER.warn("Error While storing mail. This error will be retried for {} more times.", remainRetries.getAndDecrement(), error));

        return retries.map(count ->
            operation
                .retryWhen(Retry.backoff(count, FIRST_BACKOFF).maxBackoff(MAX_BACKOFF).scheduler(Schedulers.parallel()))
                .then())
            .orElse(operation);
    }

    private Map<String, List<String>> saveHeaders(Mail mail, MailAddress recipient) throws MessagingException {
        ImmutableMap.Builder<String, List<String>> backup = ImmutableMap.builder();
        Collection<String> headersToSave = mail.getPerRecipientSpecificHeaders().getHeaderNamesForRecipient(recipient);
        for (String headerName: headersToSave) {
            List<String> values = ImmutableList.copyOf(
                        Optional.ofNullable(mail.getMessage().getHeader(headerName))
                            .orElse(NO_HEADERS));
            backup.put(headerName, values);
        }
        return backup.build();
    }

    private void restoreHeaders(MimeMessage mimeMessage, Map<String, List<String>> savedHeaders) throws MessagingException {
        for (Map.Entry<String, List<String>> header: savedHeaders.entrySet()) {
            String name = header.getKey();
            mimeMessage.removeHeader(name);
            for (String value: header.getValue()) {
                mimeMessage.addHeader(name, value);
            }
        }
    }

    private void addSpecificHeadersForRecipient(Mail mail, MimeMessage message, MailAddress recipient) throws MessagingException {
        for (Header header: mail.getPerRecipientSpecificHeaders().getHeadersForRecipient(recipient)) {
            message.addHeader(header.getName(), header.getValue());
        }
    }
}
