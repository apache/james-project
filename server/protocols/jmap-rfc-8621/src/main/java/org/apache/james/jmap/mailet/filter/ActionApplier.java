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

package org.apache.james.jmap.mailet.filter;

import static org.apache.james.jmap.mailet.filter.JMAPFiltering.RRT_ERROR;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.server.core.MailImpl;
import org.apache.james.util.AuditTrail;
import org.apache.mailet.LoopPrevention;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.apache.mailet.StorageDirective;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class ActionApplier {
    public static final Logger LOGGER = LoggerFactory.getLogger(ActionApplier.class);

    @VisibleForTesting
    static class Factory {
        private final MailboxManager mailboxManager;
        private final MailboxId.Factory mailboxIdFactory;

        @Inject
        Factory(MailboxManager mailboxManager, MailboxId.Factory mailboxIdFactory) {
            this.mailboxManager = mailboxManager;
            this.mailboxIdFactory = mailboxIdFactory;
        }

        public RequireUser forMail(Mail mail) {
            return new RequireUser(mail);
        }

        public class RequireUser {
            private final Mail mail;

            RequireUser(Mail mail) {
                this.mail = mail;
            }

            public ActionApplier forRecipient(MailetContext mailetContext, MailAddress mailAddress, Username username) {
                return new ActionApplier(mailboxManager, mailboxIdFactory, mailetContext, mail, mailAddress, username);
            }
        }
    }

    private final MailboxManager mailboxManager;
    private final MailboxId.Factory mailboxIdFactory;
    private final MailetContext mailetContext;
    private final Mail mail;
    private final MailAddress mailAddress;
    private final Username username;

    @VisibleForTesting
    public static Factory factory(MailboxManager mailboxManager, MailboxId.Factory mailboxIdFactory) {
        return new Factory(mailboxManager, mailboxIdFactory);
    }

    private ActionApplier(MailboxManager mailboxManager, MailboxId.Factory mailboxIdFactory, MailetContext mailetContext,
                          Mail mail, MailAddress mailAddress, Username username) {
        this.mailboxManager = mailboxManager;
        this.mailboxIdFactory = mailboxIdFactory;
        this.mailetContext = mailetContext;
        this.mail = mail;
        this.mailAddress = mailAddress;
        this.username = username;
    }

    public void apply(Stream<Rule.Action> actions) {
        actions.forEach(this::applyAction);
    }

    private boolean shouldProcessingContinue() {
        return mail.getRecipients().contains(mailAddress);
    }

    private void applyAction(Rule.Action action) {
        applyReject(action);
        applyForward(action);
        applyStorageDirective(action);
    }

    private void applyReject(Rule.Action action) {
        if (action.isReject()) {
            removeFromRecipients();
        }
    }

    /**
     * @return a boolean value to show if a local copy should be kept or not
     */
    private void applyForward(Rule.Action action) {
        action.getForward()
            .filter(any -> shouldProcessingContinue())
            .ifPresent(Throwing.consumer(forward -> {
                LoopPrevention.RecordedRecipients recordedRecipients = LoopPrevention.RecordedRecipients.fromMail(mail);

                if (recordedRecipients.getRecipients().contains(mailAddress)) {
                    // Forward is already processed. Do not do it again.
                    return;
                }

                Set<MailAddress> newRecipients = getNewRecipients(forward, recordedRecipients);
                Set<MailAddress> forwardRecipients = Sets.difference(newRecipients, ImmutableSet.of(mailAddress));

                if (!forwardRecipients.isEmpty()) {
                    sendACopy(recordedRecipients, forwardRecipients);
                }
                boolean localCopy = newRecipients.contains(mailAddress);
                if (!localCopy) {
                    removeFromRecipients();
                }
                boolean emailIsDropped = !forward.getAddresses().isEmpty() && forwardRecipients.isEmpty() && !localCopy;
                if (emailIsDropped) {
                    recordLoop();
                }
            }));
    }


    private Set<MailAddress> getNewRecipients(Rule.Action.Forward forward, LoopPrevention.RecordedRecipients recordedRecipients) {
        ImmutableSet.Builder<MailAddress> newRecipientsBuilder = ImmutableSet.<MailAddress>builder()
            .addAll(recordedRecipients.nonRecordedRecipients(forward.getAddresses()));
        if (forward.isKeepACopy()) {
            newRecipientsBuilder.add(mailAddress);
        }
        return newRecipientsBuilder.build();
    }

    private void removeFromRecipients() {
        mail.setRecipients(mail.getRecipients().stream()
            .filter(recipient -> !recipient.equals(mailAddress))
            .collect(ImmutableList.toImmutableList()));
    }

    private void applyStorageDirective(Rule.Action action) {
        if (shouldProcessingContinue()) {
            Optional<ImmutableList<String>> targetMailboxes = computeTargetMailboxes(action);

            StorageDirective.Builder storageDirective = StorageDirective.builder();
            targetMailboxes.ifPresent(storageDirective::targetFolders);
            storageDirective
                .seen(Optional.of(action.isMarkAsSeen()).filter(seen -> seen))
                .important(Optional.of(action.isMarkAsImportant()).filter(seen -> seen))
                .keywords(Optional.of(action.getWithKeywords()).filter(c -> !c.isEmpty()))
                .buildOptional()
                .map(a -> a.encodeAsAttributes(username))
                .orElse(Stream.of())
                .forEach(mail::setAttribute);
        }
    }

    private Optional<ImmutableList<String>> computeTargetMailboxes(Rule.Action action) {
        return Optional.of(action.getAppendInMailboxes().getMailboxIds()
                .stream()
                .flatMap(this::asMailboxName)
                .collect(ImmutableList.toImmutableList()))
            .filter(mailboxes -> !mailboxes.isEmpty());
    }

    private Stream<String> asMailboxName(String mailboxIdString) {
        try {
            MailboxId mailboxId = mailboxIdFactory.fromString(mailboxIdString);
            MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
            MessageManager messageManager = mailboxManager.getMailbox(mailboxId, mailboxSession);
            mailboxManager.endProcessingRequest(mailboxSession);

            return Stream.of(messageManager.getMailboxPath().getName());
        } catch (MailboxNotFoundException e) {
            LOGGER.info("Mailbox {} does not exist, but it was mentioned in a JMAP filtering rule", mailboxIdString, e);
            return Stream.empty();
        } catch (Exception e) {
            LOGGER.error("Unexpected failure while resolving mailbox name for {}", mailboxIdString, e);
            return Stream.empty();
        }
    }

    private void sendACopy(LoopPrevention.RecordedRecipients recordedRecipients,
                           Set<MailAddress> newRecipients) throws MessagingException {
        MailImpl copy = MailImpl.duplicate(mail);
        try {
            copy.setSender(mailAddress);
            copy.setRecipients(newRecipients);
            recordedRecipients.merge(mailAddress).recordOn(copy);

            mailetContext.sendMail(copy);

            recordInAuditTrail(copy);
        } finally {
            LifecycleUtil.dispose(copy);
        }
    }

    private void recordLoop() throws MessagingException {
        MailImpl copy = MailImpl.duplicate(mail);
        try {
            copy.setRecipients(ImmutableList.of(mailAddress));
            copy.setState(RRT_ERROR.getValue());

            mailetContext.sendMail(copy);
        } finally {
            LifecycleUtil.dispose(copy);
        }
    }

    private void recordInAuditTrail(MailImpl copy) {
        AuditTrail.entry()
            .protocol("mailetcontainer")
            .action("JMAPFiltering")
            .parameters(Throwing.supplier(() -> ImmutableMap.of("mailId", mail.getName(),
                "mimeMessageId", Optional.ofNullable(mail.getMessage())
                    .map(Throwing.function(MimeMessage::getMessageID))
                    .orElse(""),
                "sender", mail.getMaybeSender().asString(),
                "forwardedMailId", copy.getName(),
                "forwardedMailSender", copy.getMaybeSender().asString(),
                "forwardedMailRecipient", StringUtils.join(copy.getRecipients()))))
            .log("Mail forwarded.");
    }
}
