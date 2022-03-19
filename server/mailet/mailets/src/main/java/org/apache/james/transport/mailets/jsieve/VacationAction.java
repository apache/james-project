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

import java.time.temporal.ChronoUnit;
import java.util.Enumeration;
import java.util.Set;
import java.util.stream.Stream;

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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class VacationAction implements MailAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(VacationAction.class);

    @Override
    public void execute(Action action, Mail mail, ActionContext context) throws MessagingException {
        ActionVacation actionVacation = (ActionVacation) action;
        int dayDifference = Long.valueOf(
            ChronoUnit.DAYS.between(
                context.getScriptActivationDate().toLocalDate(),
                context.getScriptInterpretationDate().toLocalDate()))
            .intValue();
        if (isStillInVacation(actionVacation, dayDifference)
                && isValidForReply(mail, actionVacation, context)
                && !isMailingList(mail)) {
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

    private Stream<MailAddress> retrieveAddressFromString(String address) {
        try {
            return Stream.of(new MailAddress(address));
        } catch (AddressException e) {
            LOGGER.warn("Mail address {} was not well formatted : {}", address, e.getLocalizedMessage());
            return Stream.empty();
        }
    }

    private boolean isMailingList(Mail mail) throws MessagingException {
        Enumeration<String> enumeration = mail.getMessage().getAllHeaderLines();
        while (enumeration.hasMoreElements()) {
            String headerName = enumeration.nextElement();
            if (headerName.startsWith("List-")) {
                return true;
            }
        }
        return false;
    }
}
