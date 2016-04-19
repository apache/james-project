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

package org.apache.james.jmap.mailet;

import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.jmap.api.vacation.AccountId;
import org.apache.james.jmap.api.vacation.Vacation;
import org.apache.james.jmap.api.vacation.VacationRepository;
import org.apache.james.util.date.ZonedDateTimeProvider;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.AutomaticallySentMailDetector;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VacationMailet extends GenericMailet {

    private static final Logger LOGGER = LoggerFactory.getLogger(VacationMailet.class);

    private final VacationRepository vacationRepository;
    private final ZonedDateTimeProvider zonedDateTimeProvider;
    private final AutomaticallySentMailDetector automaticallySentMailDetector;

    @Inject
    public VacationMailet(VacationRepository vacationRepository, ZonedDateTimeProvider zonedDateTimeProvider, AutomaticallySentMailDetector automaticallySentMailDetector) {
        this.vacationRepository = vacationRepository;
        this.zonedDateTimeProvider = zonedDateTimeProvider;
        this.automaticallySentMailDetector = automaticallySentMailDetector;
    }

    @Override
    public void service(Mail mail) {
        try {
            ZonedDateTime processingDate = zonedDateTimeProvider.get();
            mail.getRecipients()
                .stream()
                .map(mailAddress -> manageVacation(mailAddress, mail, processingDate))
                .forEach(CompletableFuture::join);
        } catch (Throwable e) {
            LOGGER.warn("Can not process vacation for one or more recipients in {}", mail.getRecipients(), e);
        }
    }

    public CompletableFuture<Void> manageVacation(MailAddress recipient, Mail processedMail, ZonedDateTime processingDate) {
        AccountId accountId = AccountId.fromString(recipient.toString());
        CompletableFuture<Vacation> vacationFuture = vacationRepository.retrieveVacation(accountId);
        return vacationFuture.thenAccept(vacation -> {
            if (shouldSendNotification(vacation, processedMail, recipient, processingDate)) {
                sendNotification(recipient, processedMail, vacation);
            }
        });
    }

    private boolean shouldSendNotification(Vacation vacation, Mail processedMail, MailAddress recipient, ZonedDateTime processingDate) {
        try {
            return vacation.isActiveAtDate(processingDate)
                && ! automaticallySentMailDetector.isAutomaticallySent(processedMail);
        } catch (MessagingException e) {
            LOGGER.warn("Failed detect automatic response in a mail from {} to {}", processedMail.getSender(), recipient, e);
            return false;
        }
    }

    private void sendNotification(MailAddress recipient, Mail processedMail, Vacation vacation) {
        try {
            VacationReply vacationReply = VacationReply.builder(processedMail)
                .receivedMailRecipient(recipient)
                .reason(vacation.getTextBody())
                .build();
            sendNotification(vacationReply);
        } catch (MessagingException e) {
            LOGGER.warn("Failed to send JMAP vacation notification from {} to {}", recipient, processedMail.getSender(), e);
        }
    }

    private void sendNotification(VacationReply vacationReply) throws MessagingException {
        getMailetContext().sendMail(vacationReply.getSender(),
            vacationReply.getRecipients(),
            vacationReply.getMimeMessage());
    }
}
