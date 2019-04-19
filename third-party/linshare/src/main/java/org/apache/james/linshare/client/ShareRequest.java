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

public class ShareRequest {

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
    
    @FunctionalInterface
    public interface RequireMessage {
        Builder message(String message);
    }

    public static class Builder {
        private final ImmutableList.Builder<Recipient> recipientsBuilder;
        private final ImmutableList.Builder<DocumentId> documentIdsBuilder;
        private final String message;

        Builder(String message) {
            this.message = message;
            this.recipientsBuilder = new ImmutableList.Builder<>();
            this.documentIdsBuilder = new ImmutableList.Builder<>();
        }

        public Builder addRecipient(MailAddress recipient) {
            recipientsBuilder.add(new Recipient(recipient));
            return this;
        }

        public Builder addDocumentId(DocumentId documentId) {
            documentIdsBuilder.add(documentId);
            return this;
        }

        public ShareRequest build() {
            return new ShareRequest(recipientsBuilder.build(), documentIdsBuilder.build(), message);
        }
    }

    public static RequireMessage builder() {
        return Builder::new;
    }

    private final List<Recipient> recipients;
    private final List<DocumentId> documentIds;
    private final String message;

    private ShareRequest(List<Recipient> recipients, List<DocumentId> documentIds, String message) {
        Preconditions.checkNotNull(message);
        Preconditions.checkNotNull(recipients);
        Preconditions.checkNotNull(documentIds);
        Preconditions.checkArgument(!recipients.isEmpty(), "recipients cannot be empty");
        Preconditions.checkArgument(!documentIds.isEmpty(), "documents cannot be empty");

        this.message = message;
        this.recipients = recipients;
        this.documentIds = documentIds;
    }

    public String getMessage() {
        return message;
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
                && Objects.equals(this.documentIds, that.documentIds)
                && Objects.equals(this.message, that.message);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(recipients, documentIds, message);
    }
}
