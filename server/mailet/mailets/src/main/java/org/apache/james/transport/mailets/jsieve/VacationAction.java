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

package org.apache.james.transport.mailets.jsieve;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.mail.Header;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.server.core.MailImpl;
import org.apache.james.vacation.api.AccountId;
import org.apache.james.vacation.api.NotificationRegistry;
import org.apache.james.vacation.api.RecipientId;
import org.apache.jsieve.mail.Action;
import org.apache.jsieve.mail.optional.ActionVacation;
import org.apache.mailet.Mail;
import org.apache.mailet.base.AutomaticallySentMailDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class VacationAction implements MailAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(VacationAction.class);
    private static final String LIST_HEADER_PREFIX = "List-";
    private static final String SIEVE_ACCOUNT_PREFIX = "sieve:";
    // RFC 5230 section 4.1 lets sites bound :days; this also keeps the expiry date storable
    private static final int MAXIMUM_DAYS_BETWEEN_REPLIES = 365;
    private static final ImmutableList<String> RECIPIENT_HEADERS = ImmutableList.of("To", "Cc", "Bcc", "Resent-To", "Resent-Cc", "Resent-Bcc");

    /**
     * Who was answered (RFC 5230 section 4.2): one reply per user, response and sender within :days.
     */
    private final NotificationRegistry notificationRegistry;
    private final AutomaticallySentMailDetector automaticallySentMailDetector;
    private final UserAddresses userAddresses;

    public VacationAction(NotificationRegistry notificationRegistry, AutomaticallySentMailDetector automaticallySentMailDetector) {
        this(notificationRegistry, automaticallySentMailDetector, UserAddresses.NONE);
    }

    public VacationAction(NotificationRegistry notificationRegistry, AutomaticallySentMailDetector automaticallySentMailDetector,
                          UserAddresses userAddresses) {
        this.notificationRegistry = notificationRegistry;
        this.automaticallySentMailDetector = automaticallySentMailDetector;
        this.userAddresses = userAddresses;
    }

    @Override
    public void execute(Action action, Mail mail, ActionContext context) throws MessagingException {
        ActionVacation actionVacation = (ActionVacation) action;

        // RFC 5230 section 4.6: no reply to a message with a null envelope sender
        if (!mail.hasSender()) {
            return;
        }

        // Known addresses are used for authorization decisions. We can not use all addresses for that
        // because the user can specify arbitrary addresses in :addresses.
        Set<MailAddress> knownAddresses = getKnownAddresses(actionVacation, context);
        // RFC 5230 section 4.5: The user's addresses are the envelope recipient, the user-specified :addresses,
        // and email addresses known by James to be associated with the recipient (e.g. their aliases).
        Set<MailAddress> allAddresses = Stream.concat(
            knownAddresses.stream(),
            actionVacation.getAddresses().stream().flatMap(this::retrieveAddressFromString)
        ).collect(ImmutableSet.toImmutableSet());

        if (isValidForReply(mail, allAddresses)
                && !isFromUser(mail, allAddresses)
                && !isMailingList(mail)
                && !automaticallySentMailDetector.isAutomaticallySent(mail)
                && !isNoReplySender(mail)
                && !alreadyAnswered(mail, actionVacation, context)) {
            sendVacationNotification(mail, actionVacation, context, knownAddresses);
            registerNotification(mail, actionVacation, context);
        }
    }

    private void sendVacationNotification(Mail mail, ActionVacation actionVacation, ActionContext context, Set<MailAddress> allowedFromAddresses) throws MessagingException {
        VacationReply vacationReply = VacationReply.builder(mail, context)
            .from(actionVacation.getFrom())
            .mime(actionVacation.getMime())
            .reason(actionVacation.getReason())
            .subject(actionVacation.getSubject())
            .allowedFromAddresses(allowedFromAddresses)
            .build();

        MailImpl replyMail = MailImpl.builder()
            .name(MailImpl.getId())
            .sender(vacationReply.getSender())
            .addRecipients(vacationReply.getRecipients())
            .mimeMessage(vacationReply.getMimeMessage())
            .build();
        try {
            context.post(replyMail);
        } finally {
            LifecycleUtil.dispose(replyMail);
        }
    }

    private Set<MailAddress> getKnownAddresses(ActionVacation actionVacation, ActionContext context) {
        MailAddress recipient = context.getRecipient();
        Set<MailAddress> addresses = Flux.from(userAddresses.of(recipient))
            .onErrorResume(e -> {
                LOGGER.warn("Cannot list the addresses of {}, relying only on the vacation :addresses and the recipient itself", recipient.asString(), e);
                return Flux.empty();
            })
            .collect(Collectors.toSet())
            .block();
        addresses.add(recipient);
        return addresses;
    }

    // RFC 5230 section 4.5: MUST NOT respond unless one of the user's addresses is in a To, Cc, Bcc, Resent-To,
    // Resent-Cc or Resent-Bcc header of the message. The envelope recipient is always the user, Bcc copies, bulk mails
    // and mails with a forged To are not addressed to them.
    private boolean isValidForReply(Mail mail, Set<MailAddress> userMailAddresses) throws MessagingException {
        return headerRecipients(mail.getMessage())
            .anyMatch(userMailAddresses::contains);
    }

    private Stream<MailAddress> headerRecipients(MimeMessage message) throws MessagingException {
        ImmutableList.Builder<String> headerValues = ImmutableList.builder();
        for (String headerName : RECIPIENT_HEADERS) {
            Optional.ofNullable(message.getHeader(headerName, ","))
                .ifPresent(headerValues::add);
        }
        return headerValues.build().stream()
            .flatMap(this::parseAddresses)
            .flatMap(this::expandGroup)
            .flatMap(address -> retrieveAddressFromString(address.getAddress()));
    }

    private Stream<InternetAddress> parseAddresses(String headerValue) {
        try {
            return Arrays.stream(InternetAddress.parseHeader(headerValue, false));
        } catch (AddressException e) {
            LOGGER.debug("Cannot parse addresses of {}", headerValue, e);
            return Stream.empty();
        }
    }

    private Stream<InternetAddress> expandGroup(InternetAddress address) {
        if (!address.isGroup()) {
            return Stream.of(address);
        }
        try {
            return Arrays.stream(address.getGroup(false));
        } catch (AddressException e) {
            LOGGER.debug("Cannot parse the group {}", address, e);
            return Stream.empty();
        }
    }

    // RFC 5230 section 4.5: never answer the user's own addresses
    private boolean isFromUser(Mail mail, Set<MailAddress> userMailAddresses) {
        return userMailAddresses.contains(mail.getMaybeSender().get());
    }

    private boolean isNoReplySender(Mail mail) {
        return mail.getMaybeSender().get()
            .getLocalPart()
            .toLowerCase(Locale.US)
            .endsWith("-noreply");
    }

    private boolean alreadyAnswered(Mail mail, ActionVacation actionVacation, ActionContext context) {
        // Not knowing whether the sender was answered must not answer it again, nor cancel the other actions
        boolean alreadyAnswered = Mono.defer(() -> notificationRegistry.isRegistered(accountId(actionVacation, context), recipientId(mail)))
            .onErrorResume(e -> {
                LOGGER.error("Cannot know whether {} was answered by the vacation of {}, not answering",
                    mail.getMaybeSender().asString(), context.getRecipient().asString(), e);
                return Mono.just(true);
            })
            .blockOptional()
            .orElse(false);
        if (alreadyAnswered) {
            LOGGER.debug("{} was already answered by the vacation of {}", mail.getMaybeSender().asString(), context.getRecipient().asString());
        }
        return alreadyAnswered;
    }

    private void registerNotification(Mail mail, ActionVacation actionVacation, ActionContext context) {
        ZonedDateTime expiryDate = context.getScriptInterpretationDate()
            .plusDays(Math.min(actionVacation.getDuration(), MAXIMUM_DAYS_BETWEEN_REPLIES));
        Mono.defer(() -> notificationRegistry.register(accountId(actionVacation, context), recipientId(mail), Optional.of(expiryDate)))
            .onErrorResume(e -> {
                LOGGER.error("Cannot record that the vacation of {} answered {}",
                    context.getRecipient().asString(), mail.getMaybeSender().asString(), e);
                return Mono.empty();
            })
            .block();
    }

    // Kept apart from the JMAP vacation, which flushes its own records whenever it is modified
    private AccountId accountId(ActionVacation actionVacation, ActionContext context) {
        return AccountId.fromString(SIEVE_ACCOUNT_PREFIX + responseId(actionVacation) + ":" + context.getRecipient().toString());
    }

    // RFC 5230 section 4.2: a response is identified by its :handle, else by its :subject, :from, :mime and reason.
    // Hashed, so that the account id keeps the user's domain last whatever the script writes.
    private String responseId(ActionVacation actionVacation) {
        String response = Optional.ofNullable(actionVacation.getHandle())
            .map(handle -> Joiner.on('\0').join("handle", handle))
            .orElseGet(() -> Joiner.on('\0').useForNull("").join("content", actionVacation.getSubject(), actionVacation.getFrom(),
                actionVacation.getMime(), actionVacation.getReason()));
        return Hashing.sha256().hashString(response, StandardCharsets.UTF_8).toString().substring(0, 16);
    }

    private RecipientId recipientId(Mail mail) {
        return RecipientId.fromMailAddress(mail.getMaybeSender().get());
    }

    private Stream<MailAddress> retrieveAddressFromString(String address) {
        try {
            return Stream.of(new MailAddress(address));
        } catch (AddressException e) {
            LOGGER.warn("Mail address {} was not well formatted : {}", address, e.getLocalizedMessage());
            return Stream.empty();
        }
    }

    private boolean isMailingList(Mail mail) throws MessagingException {
        // RFC 5230 4.5: do not answer List-* fields (RFC 2369, RFC 2919), whose names are case-insensitive
        Enumeration<Header> headers = mail.getMessage().getAllHeaders();
        while (headers.hasMoreElements()) {
            String headerName = headers.nextElement().getName();
            if (headerName.regionMatches(true, 0, LIST_HEADER_PREFIX, 0, LIST_HEADER_PREFIX.length())) {
                return true;
            }
        }
        return false;
    }
}
