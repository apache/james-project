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
import java.util.Locale;
import java.util.Optional;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.MailAddress;
import org.apache.james.transport.util.MimeMessageBodyGenerator;
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
            boolean hasReplyToHeaderField = Optional.ofNullable(mail.getMessage().getReplyTo())
                .map(replyToFields -> replyToFields.length > 0)
                .orElse(false);
            if (!automaticallySentMailDetector.isAutomaticallySent(mail) && hasReplyToHeaderField) {
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

    private void manageVacation(MailAddress recipient, Mail processedMail, ZonedDateTime processingDate) {
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
        if (shouldSendNotification(processedMail, vacation, processingDate, alreadySent, recipient)) {
            sendNotification(recipient, processedMail, vacation);
        }
    }

    private boolean shouldSendNotification(Mail processedMail, Vacation vacation, ZonedDateTime processingDate, boolean alreadySent, MailAddress recipient) {
        return vacation.isActiveAtDate(processingDate)
            && !alreadySent
            && !isNoReplySender(processedMail)
            && !isSentToSelf(processedMail.getMaybeSender().asOptional(), recipient);
    }

    private Boolean isNoReplySender(Mail processedMail) {
        return processedMail.getMaybeSender()
            .asOptional()
            .map(address -> address.getLocalPart()
                .toLowerCase(Locale.US)
                .endsWith("-noreply"))
            .orElse(true);
    }

    private Boolean isSentToSelf(Optional<MailAddress> maybeSender, MailAddress recipient) {
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
