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

package org.apache.james.jmap.draft.model;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import org.apache.james.jmap.draft.methods.ValidationResult;
import org.apache.james.jmap.draft.model.MessageProperties.MessageProperty;
import org.apache.james.jmap.draft.model.message.view.SubMessage;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.util.StreamUtils;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@JsonDeserialize(builder = CreationMessage.Builder.class)
public class CreationMessage {

    private static final String RECIPIENT_PROPERTY_NAMES = ImmutableList.of(MessageProperty.to, MessageProperty.cc, MessageProperty.bcc).stream()
            .map(MessageProperty::asFieldName)
            .collect(Collectors.joining(", "));

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private ImmutableList<String> mailboxIds;
        private String inReplyToMessageId;
        private final OldKeyword.Builder oldKeywordBuilder;
        private final ImmutableMap.Builder<String, String> headers;
        private Optional<DraftEmailer> from = Optional.empty();
        private final ImmutableList.Builder<DraftEmailer> to;
        private final ImmutableList.Builder<DraftEmailer> cc;
        private final ImmutableList.Builder<DraftEmailer> bcc;
        private final ImmutableList.Builder<DraftEmailer> replyTo;
        private String subject;
        private ZonedDateTime date;
        private String textBody;
        private String htmlBody;
        private final ImmutableList.Builder<Attachment> attachments;
        private final ImmutableMap.Builder<BlobId, SubMessage> attachedMessages;
        private Optional<Map<String, Boolean>> keywords = Optional.empty();

        private Builder() {
            to = ImmutableList.builder();
            cc = ImmutableList.builder();
            bcc = ImmutableList.builder();
            replyTo = ImmutableList.builder();
            attachments = ImmutableList.builder();
            attachedMessages = ImmutableMap.builder();
            headers = ImmutableMap.builder();
            oldKeywordBuilder = OldKeyword.builder();
        }

        public Builder mailboxId(String... mailboxIds) {
            return mailboxIds(Arrays.asList(mailboxIds));
        }

        @JsonDeserialize
        public Builder mailboxIds(List<String> mailboxIds) {
            this.mailboxIds = ImmutableList.copyOf(mailboxIds);
            return this;
        }

        public Builder inReplyToMessageId(String inReplyToMessageId) {
            this.inReplyToMessageId = inReplyToMessageId;
            return this;
        }

        public Builder isUnread(Optional<Boolean> isUnread) {
            oldKeywordBuilder.isUnread(isUnread);
            return this;
        }

        public Builder isFlagged(Optional<Boolean> isFlagged) {
            oldKeywordBuilder.isFlagged(isFlagged);
            return this;
        }

        public Builder isAnswered(Optional<Boolean> isAnswered) {
            oldKeywordBuilder.isAnswered(isAnswered);
            return this;
        }

        public Builder isDraft(Optional<Boolean> isDraft) {
            oldKeywordBuilder.isDraft(isDraft);
            return this;
        }

        public Builder isForwarded(Optional<Boolean> isForwarded) {
            oldKeywordBuilder.isForwarded(isForwarded);
            return this;
        }

        public Builder headers(ImmutableMap<String, String> headers) {
            this.headers.putAll(headers);
            return this;
        }

        public Builder from(DraftEmailer from) {
            this.from = Optional.ofNullable(from);
            return this;
        }

        public Builder to(List<DraftEmailer> to) {
            this.to.addAll(to);
            return this;
        }

        public Builder cc(List<DraftEmailer> cc) {
            this.cc.addAll(cc);
            return this;
        }

        public Builder bcc(List<DraftEmailer> bcc) {
            this.bcc.addAll(bcc);
            return this;
        }

        public Builder replyTo(List<DraftEmailer> replyTo) {
            this.replyTo.addAll(replyTo);
            return this;
        }

        public Builder subject(String subject) {
            this.subject = Strings.nullToEmpty(subject);
            return this;
        }

        public Builder date(ZonedDateTime date) {
            this.date = date;
            return this;
        }

        public Builder textBody(String textBody) {
            this.textBody = textBody;
            return this;
        }

        public Builder htmlBody(String htmlBody) {
            this.htmlBody = htmlBody;
            return this;
        }

        public Builder attachments(Attachment... attachments) {
            return attachments(Arrays.asList(attachments));
        }
        
        @JsonDeserialize
        public Builder attachments(List<Attachment> attachments) {
            this.attachments.addAll(attachments);
            return this;
        }

        public Builder attachedMessages(Map<BlobId, SubMessage> attachedMessages) {
            this.attachedMessages.putAll(attachedMessages);
            return this;
        }

        public Builder keywords(Map<String, Boolean> keywords) {
            this.keywords = Optional.of(ImmutableMap.copyOf(keywords));
            return this;
        }

        private static boolean areAttachedMessagesKeysInAttachments(ImmutableList<Attachment> attachments, ImmutableMap<BlobId, SubMessage> attachedMessages) {
            return attachedMessages.isEmpty() || attachedMessages.keySet().stream()
                    .anyMatch(inAttachments(attachments));
        }

        private static Predicate<BlobId> inAttachments(ImmutableList<Attachment> attachments) {
            return (key) -> attachments.stream()
                .map(Attachment::getBlobId)
                .anyMatch(blobId -> blobId.equals(key));
        }

        public CreationMessage build() {
            Preconditions.checkState(mailboxIds != null, "'mailboxIds' is mandatory");
            Preconditions.checkState(headers != null, "'headers' is mandatory");
            ImmutableList<Attachment> attachments = this.attachments.build();
            ImmutableMap<BlobId, SubMessage> attachedMessages = this.attachedMessages.build();
            Preconditions.checkState(areAttachedMessagesKeysInAttachments(attachments, attachedMessages), "'attachedMessages' keys must be in 'attachments'");

            if (date == null) {
                date = ZonedDateTime.now();
            }

            Optional<Keywords> maybeKeywords = creationKeywords();
            Optional<OldKeyword> oldKeywords = oldKeywordBuilder.computeOldKeyword();

            return new CreationMessage(mailboxIds, Optional.ofNullable(inReplyToMessageId), headers.build(), from,
                    to.build(), cc.build(), bcc.build(), replyTo.build(), subject, date, Optional.ofNullable(textBody), Optional.ofNullable(htmlBody),
                    attachments, attachedMessages, computeKeywords(maybeKeywords, oldKeywords));
        }

        private Optional<Keywords> creationKeywords() {
            return keywords.map(map -> Keywords.strictFactory()
                    .fromMap(map));
        }

        public Keywords computeKeywords(Optional<Keywords> keywords, Optional<OldKeyword> oldKeywords) {
            Preconditions.checkArgument(!(keywords.isPresent() && oldKeywords.isPresent()), "Does not support keyword and is* at the same time");
            return keywords
                .or(() -> oldKeywords.map(OldKeyword::asKeywords))
                .orElse(Keywords.DEFAULT_VALUE);
        }

    }

    private final ImmutableList<String> mailboxIds;
    private final Optional<String> inReplyToMessageId;
    private final ImmutableMap<String, String> headers;
    private final Optional<DraftEmailer> from;
    private final ImmutableList<DraftEmailer> to;
    private final ImmutableList<DraftEmailer> cc;
    private final ImmutableList<DraftEmailer> bcc;
    private final ImmutableList<DraftEmailer> replyTo;
    private final String subject;
    private final ZonedDateTime date;
    private final Optional<String> textBody;
    private final Optional<String> htmlBody;
    private final ImmutableList<Attachment> attachments;
    private final ImmutableMap<BlobId, SubMessage> attachedMessages;
    private final Keywords keywords;

    @VisibleForTesting
    CreationMessage(ImmutableList<String> mailboxIds, Optional<String> inReplyToMessageId, ImmutableMap<String, String> headers, Optional<DraftEmailer> from,
                    ImmutableList<DraftEmailer> to, ImmutableList<DraftEmailer> cc, ImmutableList<DraftEmailer> bcc, ImmutableList<DraftEmailer> replyTo, String subject, ZonedDateTime date, Optional<String> textBody, Optional<String> htmlBody, ImmutableList<Attachment> attachments,
                    ImmutableMap<BlobId, SubMessage> attachedMessages, Keywords keywords) {
        this.mailboxIds = mailboxIds;
        this.inReplyToMessageId = inReplyToMessageId;
        this.headers = headers;
        this.from = from;
        this.to = to;
        this.cc = cc;
        this.bcc = bcc;
        this.replyTo = replyTo;
        this.subject = subject;
        this.date = date;
        this.textBody = textBody;
        this.htmlBody = htmlBody;
        this.attachments = attachments;
        this.attachedMessages = attachedMessages;
        this.keywords = keywords;
    }

    public Keywords getKeywords() {
        return keywords;
    }

    public ImmutableList<String> getMailboxIds() {
        return mailboxIds;
    }

    public Optional<String> getInReplyToMessageId() {
        return inReplyToMessageId;
    }

    public ImmutableMap<String, String> getHeaders() {
        return headers;
    }

    public Optional<DraftEmailer> getFrom() {
        return from;
    }

    public ImmutableList<DraftEmailer> getTo() {
        return to;
    }

    public ImmutableList<DraftEmailer> getCc() {
        return cc;
    }

    public ImmutableList<DraftEmailer> getBcc() {
        return bcc;
    }

    public ImmutableList<DraftEmailer> getReplyTo() {
        return replyTo;
    }

    public String getSubject() {
        return subject;
    }

    public ZonedDateTime getDate() {
        return date;
    }

    public Optional<String> getTextBody() {
        return textBody;
    }

    public Optional<String> getHtmlBody() {
        return htmlBody;
    }

    public ImmutableList<Attachment> getAttachments() {
        return attachments;
    }

    public ImmutableMap<BlobId, SubMessage> getAttachedMessages() {
        return attachedMessages;
    }

    public boolean isValid() {
        return validate().isEmpty();
    }

    public boolean isDraft() {
        return keywords.contains(Keyword.DRAFT);
    }

    public List<ValidationResult> validate() {
        ImmutableList.Builder<ValidationResult> errors = ImmutableList.builder();
        assertValidFromProvided(errors);
        assertAtLeastOneValidRecipient(errors);
        return errors.build();
    }

    private void assertAtLeastOneValidRecipient(ImmutableList.Builder<ValidationResult> errors) {
        Stream<DraftEmailer> recipients = StreamUtils.flatten(to.stream(), cc.stream(), bcc.stream());
        boolean hasAtLeastOneAddressToSendTo = recipients.anyMatch(DraftEmailer::hasValidEmail);
        if (!hasAtLeastOneAddressToSendTo) {
            errors.add(ValidationResult.builder().message("no recipient address set").property(RECIPIENT_PROPERTY_NAMES).build());
        }
    }

    private void assertValidFromProvided(ImmutableList.Builder<ValidationResult> errors) {
        ValidationResult invalidPropertyFrom = ValidationResult.builder()
                .property(MessageProperty.from.asFieldName())
                .message("'from' address is mandatory")
                .build();
        if (!from.isPresent()) {
            errors.add(invalidPropertyFrom);
        }
        from.filter(f -> !f.hasValidEmail()).ifPresent(f -> errors.add(invalidPropertyFrom));
    }

    public boolean isIn(MessageManager mailbox) {
        return mailboxIds.contains(mailbox.getId().serialize());
    }

    public boolean isOnlyIn(MessageManager mailbox) {
        return isIn(mailbox)
            && mailboxIds.size() == 1;
    }
    
    @JsonDeserialize(builder = DraftEmailer.Builder.class)
    public static class DraftEmailer {

        public static Builder builder() {
            return new Builder();
        }

        @JsonPOJOBuilder(withPrefix = "")
        public static class Builder {
            private Optional<String> name = Optional.empty();
            private Optional<String> email = Optional.empty();

            public Builder name(String name) {
                this.name = Optional.ofNullable(name);
                return this;
            }

            public Builder email(String email) {
                this.email = Optional.ofNullable(email);
                return this;
            }

            public DraftEmailer build() {
                return new DraftEmailer(name, email);
            }
        }

        private final Optional<String> name;
        private final Optional<String> email;
        private final EmailValidator emailValidator;

        @VisibleForTesting
        DraftEmailer(Optional<String> name, Optional<String> email) {
            this.name = name;
            this.email = email;
            this.emailValidator = new EmailValidator();
        }

        public Optional<String> getName() {
            return name;
        }

        public Optional<String> getEmail() {
            return email;
        }

        public boolean hasValidEmail() {
            return getEmail().isPresent() && emailValidator.isValid(getEmail().get());
        }

        public EmailUserAndDomain getEmailUserAndDomain() {
            int atIndex = email.get().indexOf('@');
            if (atIndex < 0 || atIndex == email.get().length() - 1) {
                return new EmailUserAndDomain(Optional.of(email.get()), Optional.empty());
            }
            String user = email.get().substring(0, atIndex);
            String domain = email.get().substring(atIndex + 1);

            return new EmailUserAndDomain(Optional.of(user), Optional.of(domain));
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof DraftEmailer) {
                DraftEmailer otherEMailer = (DraftEmailer) o;
                return Objects.equals(name, otherEMailer.name)
                        && Objects.equals(email.orElse("<unset>"), otherEMailer.email.orElse("<unset>"));
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, email);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("name", name)
                    .add("email", email.orElse("<unset>"))
                    .toString();
        }
    }

    public static class EmailUserAndDomain {
        private final Optional<String> user;
        private final Optional<String> domain;

        public EmailUserAndDomain(Optional<String> user, Optional<String> domain) {
            this.user = user;
            this.domain = domain;
        }

        public Optional<String> getUser() {
            return user;
        }

        public Optional<String> getDomain() {
            return domain;
        }
    }

    public static class EmailValidator {

        public boolean isValid(String email) {
            boolean result = true;
            try {
                InternetAddress emailAddress = new InternetAddress(email);
                // verrrry permissive validator !
                emailAddress.validate();
            } catch (AddressException ex) {
                result = false;
            }
            return result;
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("mailboxIds", mailboxIds)
            .toString();
    }
}
