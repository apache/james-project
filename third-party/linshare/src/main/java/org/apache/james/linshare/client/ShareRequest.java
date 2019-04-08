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

package org.apache.james.linshare.client;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.apache.james.core.MailAddress;
import org.apache.james.linshare.client.Document.DocumentId;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

class ShareRequest {

    static class Recipient {
        private final MailAddress mail;

        @VisibleForTesting
        Recipient(MailAddress mail) {
            Preconditions.checkNotNull(mail);
            Preconditions.checkArgument(!MailAddress.nullSender().equals(mail), "nullSender is not allowed");

            this.mail = mail;
        }

        public String getMail() {
            return mail.asString();
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Recipient) {
                Recipient recipient = (Recipient) o;

                return Objects.equals(this.mail, recipient.mail);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(mail);
        }
    }

    static class Builder {
        private final ImmutableList.Builder<Recipient> recipientsBuilder;
        private final ImmutableList.Builder<DocumentId> documentIdsBuilder;

        Builder() {
            this.recipientsBuilder = new ImmutableList.Builder<>();
            this.documentIdsBuilder = new ImmutableList.Builder<>();
        }

        Builder addRecipient(MailAddress recipient) {
            recipientsBuilder.add(new Recipient(recipient));
            return this;
        }

        Builder addDocumentId(DocumentId documentId) {
            documentIdsBuilder.add(documentId);
            return this;
        }

        ShareRequest build() {
            return new ShareRequest(recipientsBuilder.build(), documentIdsBuilder.build());
        }
    }

    static Builder builder() {
        return new Builder();
    }

    private final List<Recipient> recipients;
    private final List<DocumentId> documentIds;

    private ShareRequest(List<Recipient> recipients, List<DocumentId> documentIds) {
        Preconditions.checkNotNull(recipients);
        Preconditions.checkNotNull(documentIds);
        Preconditions.checkArgument(!recipients.isEmpty(), "recipients cannot be empty");
        Preconditions.checkArgument(!documentIds.isEmpty(), "documents cannot be empty");

        this.recipients = recipients;
        this.documentIds = documentIds;
    }

    public List<Recipient> getRecipients() {
        return recipients;
    }

    public List<String> getDocuments() {
        return documentIds
            .stream()
            .map(DocumentId::getId)
            .map(UUID::toString)
            .collect(Guavate.toImmutableList());
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ShareRequest) {
            ShareRequest that = (ShareRequest) o;

            return Objects.equals(this.recipients, that.recipients)
                && Objects.equals(this.documentIds, that.documentIds);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(recipients, documentIds);
    }
}
