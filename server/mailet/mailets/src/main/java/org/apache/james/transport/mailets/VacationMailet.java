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

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.MailAddress;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.transport.util.MimeMessageBodyGenerator;
import org.apache.james.util.StreamUtils;
import org.apache.james.util.date.ZonedDateTimeProvider;
import org.apache.james.vacation.api.AccountId;
import org.apache.james.vacation.api.RecipientId;
import org.apache.james.vacation.api.Vacation;
import org.apache.james.vacation.api.VacationService;
import org.apache.mailet.Mail;
import org.apache.mailet.base.AutomaticallySentMailDetector;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class VacationMailet extends GenericMailet {

    private static final Logger LOGGER = LoggerFactory.getLogger(VacationMailet.class);

    private final VacationService vacationService;
    private final ZonedDateTimeProvider zonedDateTimeProvider;
    private final AutomaticallySentMailDetector automaticallySentMailDetector;
    private final MimeMessageBodyGenerator mimeMessageBodyGenerator;

    @Inject
    public VacationMailet(VacationService vacationService, ZonedDateTimeProvider zonedDateTimeProvider,
                          AutomaticallySentMailDetector automaticallySentMailDetector,
                          MimeMessageBodyGenerator mimeMessageBodyGenerator) {
        this.vacationService = vacationService;
        this.zonedDateTimeProvider = zonedDateTimeProvider;
        this.automaticallySentMailDetector = automaticallySentMailDetector;
        this.mimeMessageBodyGenerator = mimeMessageBodyGenerator;
    }

    @Override
    public void service(Mail mail) {
        try {
            if (!mail.hasSender()) {
                return;
            }
            boolean hasReplyToHeaderField = Optional.ofNullable(getReplyTo(mail))
                .map(replyToFields -> replyToFields.length > 0)
                .orElse(false);
            if (!automaticallySentMailDetector.isAutomaticallySent(mail) && hasReplyToHeaderField && !isNoReplySender(mail)) {
                ZonedDateTime processingDate = zonedDateTimeProvider.get();
                mail.getRecipients()
                    .forEach(mailAddress -> manageVacation(mailAddress, mail, processingDate));
            }
        } catch (AddressException e) {
            if (!e.getMessage().equals("Empty address")) {
                LOGGER.warn("Can not process vacation for one or more recipients in {}", mail.getRecipients(), e);
            }
        } catch (Exception e) {
            LOGGER.warn("Can not process vacation for one or more recipients in {}", mail.getRecipients(), e);
        }
    }

    private static Address[] getReplyTo(Mail mail) throws MessagingException {
        try {
            return mail.getMessage().getReplyTo();
        } catch (AddressException e) {
            InternetAddress[] replyTo = StreamUtils.ofNullable(mail.getMessage().getHeader("Reply-To"))
                .map(LenientAddressParser.DEFAULT::parseAddressList)
                .flatMap(Collection::stream)
                .filter(Mailbox.class::isInstance)
                .map(Mailbox.class::cast)
                .map(Mailbox::getAddress)
                .map(Throwing.function(InternetAddress::new))
                .toArray(InternetAddress[]::new);

            if (replyTo.length > 0) {
                LOGGER.info("Recovering from badly formatted Reply-To. Original value {}, deduced value {}",
                    ImmutableList.copyOf(mail.getMessage().getHeader("Reply-To")), ImmutableList.copyOf(replyTo), e);
                mail.getMessage().setReplyTo(replyTo);
                return replyTo;
            } else {
                throw e;
            }
        }
    }

    private void manageVacation(MailAddress recipient, Mail processedMail, ZonedDateTime processingDate) {
        if (isSentToSelf(processedMail.getMaybeSender().asOptional(), recipient)) {
            return;
        }

        AccountId accountId = AccountId.fromString(recipient.toString());

        Mono<Vacation> vacation = vacationService.retrieveVacation(accountId);
        Mono<Boolean> alreadySent = vacationService.isNotificationRegistered(
                AccountId.fromString(recipient.toString()),
                RecipientId.fromMailAddress(processedMail.getMaybeSender().get()));
        Pair<Vacation, Boolean> pair = Flux.combineLatest(vacation, alreadySent, Pair::of)
            .blockFirst();

        sendNotificationIfRequired(recipient, processedMail, processingDate, pair.getKey(), pair.getValue());
    }

    private void sendNotificationIfRequired(MailAddress recipient, Mail processedMail, ZonedDateTime processingDate, Vacation vacation, Boolean alreadySent) {
        if (shouldSendNotification(vacation, processingDate, alreadySent)) {
            sendNotification(recipient, processedMail, vacation);
        }
    }

    private boolean shouldSendNotification(Vacation vacation, ZonedDateTime processingDate, boolean alreadySent) {
        return vacation.isActiveAtDate(processingDate) && !alreadySent;
    }

    private boolean isNoReplySender(Mail processedMail) {
        return processedMail.getMaybeSender()
            .asOptional()
            .map(address -> address.getLocalPart()
                .toLowerCase(Locale.US)
                .endsWith("-noreply"))
            .orElse(true);
    }

    private boolean isSentToSelf(Optional<MailAddress> maybeSender, MailAddress recipient) {
        return maybeSender
            .map(sender -> sender.equals(recipient))
            .orElse(false);
    }

    private void sendNotification(MailAddress recipient, Mail processedMail, Vacation vacation) {
        try {
            VacationReply vacationReply = VacationReply.builder(processedMail)
                .receivedMailRecipient(recipient)
                .vacation(vacation)
                .build(mimeMessageBodyGenerator);

            sendNotification(vacationReply);

            vacationService.registerNotification(AccountId.fromString(recipient.toString()),
                RecipientId.fromMailAddress(processedMail.getMaybeSender().get()),
                vacation.getToDate())
                .block();
        } catch (MessagingException e) {
            LOGGER.warn("Failed to send JMAP vacation notification from {} to {}", recipient, processedMail.getMaybeSender(), e);
        }
    }

    private void sendNotification(VacationReply vacationReply) throws MessagingException {
        getMailetContext().sendMail(MailAddress.nullSender(),
            vacationReply.getRecipients(),
            vacationReply.getMimeMessage());
    }
}
