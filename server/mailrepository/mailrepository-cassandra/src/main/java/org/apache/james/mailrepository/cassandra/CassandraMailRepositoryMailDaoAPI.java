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

package org.apache.james.mailrepository.cassandra;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.mail.MessagingException;

import org.apache.james.blob.api.BlobId;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Mail;

import reactor.core.publisher.Mono;

public interface CassandraMailRepositoryMailDaoAPI {
    CompletableFuture<Void> store(MailRepositoryUrl url, Mail mail, BlobId headerId, BlobId bodyId) throws MessagingException;

    Mono<Void> remove(MailRepositoryUrl url, MailKey key);

    CompletableFuture<Optional<MailDTO>> read(MailRepositoryUrl url, MailKey key);

    class MailDTO {
        private final MailImpl.Builder mailBuilder;
        private final BlobId headerBlobId;
        private final BlobId bodyBlobId;

        public MailDTO(MailImpl.Builder mailBuilder, BlobId headerBlobId, BlobId bodyBlobId) {
            this.mailBuilder = mailBuilder;
            this.headerBlobId = headerBlobId;
            this.bodyBlobId = bodyBlobId;
        }

        public MailImpl.Builder getMailBuilder() {
            return mailBuilder;
        }

        public BlobId getHeaderBlobId() {
            return headerBlobId;
        }

        public BlobId getBodyBlobId() {
            return bodyBlobId;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof MailDTO) {
                MailDTO mailDTO = (MailDTO) o;

                return Objects.equals(this.mailBuilder.build(), mailDTO.mailBuilder.build())
                    && Objects.equals(this.headerBlobId, mailDTO.headerBlobId)
                    && Objects.equals(this.bodyBlobId, mailDTO.bodyBlobId);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(mailBuilder.build(), headerBlobId, bodyBlobId);
        }
    }
}
