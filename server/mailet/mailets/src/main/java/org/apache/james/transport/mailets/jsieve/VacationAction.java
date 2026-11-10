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
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import jakarta.mail.Header;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.server.core.MailImpl;
import org.apache.jsieve.mail.Action;
import org.apache.jsieve.mail.optional.ActionVacation;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;

public class VacationAction implements MailAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(VacationAction.class);
    private static final String LIST_HEADER_PREFIX = "List-";
    private static final String AUTO_SUBMITTED_HEADER = "Auto-Submitted";
    private static final String PRECEDENCE_HEADER = "Precedence";
    private static final Set<String> BULK_PRECEDENCES = ImmutableSet.of("bulk", "list", "junk");
    private static final Set<String> AUTOMATED_LOCAL_PARTS = ImmutableSet.of("mailer-daemon", "listserv", "majordomo");
    private static final long MAXIMUM_TRACKED_REPLIES = 100_000;

    /**
     * Who was answered, and when (RFC 5230 section 4.2): one reply per user, sender and handle within :days.
     *
     * Kept in memory: it is lost on restart and not shared between nodes, so it bounds a reply loop
     * rather than strictly enforcing the interval.
     */
    private final Cache<ReplyKey, ZonedDateTime> lastReplies = CacheBuilder.newBuilder()
        .maximumSize(MAXIMUM_TRACKED_REPLIES)
        .build();

    private record ReplyKey(MailAddress user, MailAddress sender, String handle) {
    }

    @Override
    public void execute(Action action, Mail mail, ActionContext context) throws MessagingException {
        ActionVacation actionVacation = (ActionVacation) action;
        int dayDifference = Long.valueOf(
            ChronoUnit.DAYS.between(
                context.getScriptActivationDate().toLocalDate(),
                context.getScriptInterpretationDate().toLocalDate()))
            .intValue();
        // RFC 5230 section 4.6: no reply to a message with a null envelope sender
        if (mail.hasSender()
                && isStillInVacation(actionVacation, dayDifference)
                && isValidForReply(mail, actionVacation, context)
                && !isFromUser(mail, actionVacation, context)
                && !isMailingList(mail)
                && !isAutomaticallySent(mail)
                && firstReplyWithinInterval(mail, actionVacation, context)) {
            sendVacationNotification(mail, actionVacation, context);
        }
    }

    private void sendVacationNotification(Mail mail, ActionVacation actionVacation, ActionContext context) throws MessagingException {
        VacationReply vacationReply = VacationReply.builder(mail, context)
            .from(actionVacation.getFrom())
            .mime(actionVacation.getMime())
            .reason(actionVacation.getReason())
            .subject(actionVacation.getSubject())
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

    private boolean isStillInVacation(ActionVacation actionVacation, int dayDifference) {
        return dayDifference >= 0 && dayDifference <= actionVacation.getDuration();
    }

    private boolean isValidForReply(final Mail mail, ActionVacation actionVacation, final ActionContext context) {
        Set<MailAddress> currentMailAddresses = ImmutableSet.copyOf(mail.getRecipients());
        Set<MailAddress> allowedMailAddresses = Stream
            .concat(
                actionVacation.getAddresses().stream().flatMap(this::retrieveAddressFromString),
                Stream.of(context.getRecipient()))
            .collect(ImmutableSet.toImmutableSet());
        return !Sets.intersection(currentMailAddresses, allowedMailAddresses).isEmpty();
    }

    // RFC 5230 section 4.5: never answer the user's own addresses
    private boolean isFromUser(Mail mail, ActionVacation actionVacation, ActionContext context) {
        MailAddress sender = mail.getMaybeSender().get();
        return sender.equals(context.getRecipient())
            || actionVacation.getAddresses().stream()
                .flatMap(this::retrieveAddressFromString)
                .anyMatch(sender::equals);
    }

    // RFC 5230 section 4.6 and RFC 3834 section 2: do not answer automated processes
    private boolean isAutomaticallySent(Mail mail) throws MessagingException {
        return isAutoSubmitted(mail)
            || hasBulkPrecedence(mail)
            || isAutomatedSender(mail.getMaybeSender().get());
    }

    private boolean isAutoSubmitted(Mail mail) throws MessagingException {
        return headerValues(mail, AUTO_SUBMITTED_HEADER)
            .anyMatch(value -> !value.equals("no"));
    }

    private boolean hasBulkPrecedence(Mail mail) throws MessagingException {
        return headerValues(mail, PRECEDENCE_HEADER)
            .anyMatch(BULK_PRECEDENCES::contains);
    }

    // The keyword of each field, without its parameters nor comments, lower-cased
    private Stream<String> headerValues(Mail mail, String headerName) throws MessagingException {
        return Optional.ofNullable(mail.getMessage().getHeader(headerName))
            .stream()
            .flatMap(Arrays::stream)
            .map(value -> value.replaceAll("\\([^)]*\\)", " "))
            .map(value -> value.split(";", 2)[0])
            .map(value -> value.trim().toLowerCase(Locale.US));
    }

    private boolean isAutomatedSender(MailAddress sender) {
        String localPart = sender.getLocalPart().toLowerCase(Locale.US);
        return AUTOMATED_LOCAL_PARTS.contains(localPart)
            || localPart.startsWith("owner-")
            || localPart.endsWith("-request");
    }

    private boolean firstReplyWithinInterval(Mail mail, ActionVacation actionVacation, ActionContext context) {
        ReplyKey key = new ReplyKey(context.getRecipient(), mail.getMaybeSender().get(), handle(actionVacation));
        ZonedDateTime now = context.getScriptInterpretationDate();
        AtomicBoolean firstReply = new AtomicBoolean(false);
        lastReplies.asMap().compute(key, (any, lastReply) -> {
            if (lastReply != null && now.isBefore(lastReply.plusDays(actionVacation.getDuration()))) {
                return lastReply;
            }
            firstReply.set(true);
            return now;
        });
        if (!firstReply.get()) {
            LOGGER.debug("{} was already answered by the vacation of {}", key.sender(), key.user());
        }
        return firstReply.get();
    }

    // RFC 5230 section 4.2: without :handle, the reply is identified by its content
    private String handle(ActionVacation actionVacation) {
        return Optional.ofNullable(actionVacation.getHandle())
            .map(handle -> "handle:" + handle)
            .orElseGet(() -> "content:" + Hashing.sha256()
                .hashString(Stream.of(actionVacation.getSubject(), actionVacation.getFrom(), actionVacation.getReason(), actionVacation.getMime())
                    .map(value -> Objects.toString(value, ""))
                    .map(value -> value.length() + ":" + value)
                    .reduce("", String::concat), StandardCharsets.UTF_8));
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
