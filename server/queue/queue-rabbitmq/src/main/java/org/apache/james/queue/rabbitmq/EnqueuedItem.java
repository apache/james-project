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

package org.apache.james.queue.rabbitmq;

import java.time.Instant;
import java.util.Objects;

import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.mailet.Mail;

import com.google.common.base.Preconditions;

public class EnqueuedItem {

    interface Builder {

        @FunctionalInterface
        interface RequireEnqueueId {
            RequireMailQueueName enqueueId(EnqueueId id);
        }

        @FunctionalInterface
        interface RequireMailQueueName {
            RequireMail mailQueueName(MailQueueName mailQueueName);
        }

        @FunctionalInterface
        interface RequireMail {
            RequireEnqueuedTime mail(Mail mail);
        }

        @FunctionalInterface
        interface RequireEnqueuedTime {
            RequireMimeMessagePartsId enqueuedTime(Instant clock);
        }

        @FunctionalInterface
        interface RequireMimeMessagePartsId {
            ReadyToBuild mimeMessagePartsId(MimeMessagePartsId partsId);
        }

        class ReadyToBuild {
            private final EnqueueId enqueueId;
            private final MailQueueName mailQueueName;
            private final Mail mail;
            private final Instant enqueuedTime;
            private final MimeMessagePartsId partsId;

            ReadyToBuild(EnqueueId enqueueId, MailQueueName mailQueueName, Mail mail, Instant enqueuedTime, MimeMessagePartsId partsId) {
                Preconditions.checkNotNull(enqueueId, "'enqueueId' is mandatory");
                Preconditions.checkNotNull(mailQueueName, "'mailQueueName' is mandatory");
                Preconditions.checkNotNull(mail, "'mail' is mandatory");
                Preconditions.checkNotNull(enqueuedTime, "'enqueuedTime' is mandatory");
                Preconditions.checkNotNull(partsId, "'partsId' is mandatory");

                this.enqueueId = enqueueId;
                this.mailQueueName = mailQueueName;
                this.mail = mail;
                this.enqueuedTime = enqueuedTime;
                this.partsId = partsId;
            }

            public EnqueuedItem build() {
                return new EnqueuedItem(enqueueId, mailQueueName, mail, enqueuedTime, partsId);
            }
        }
    }

    public static Builder.RequireEnqueueId builder() {
        return enqueueId -> queueName -> mail -> enqueuedTime -> partsId -> new Builder.ReadyToBuild(enqueueId, queueName, mail, enqueuedTime, partsId);
    }

    private final EnqueueId enqueueId;
    private final MailQueueName mailQueueName;
    private final Mail mail;
    private final Instant enqueuedTime;
    private final MimeMessagePartsId partsId;

    EnqueuedItem(EnqueueId enqueueId, MailQueueName mailQueueName, Mail mail, Instant enqueuedTime, MimeMessagePartsId partsId) {
        this.enqueueId = enqueueId;
        this.mailQueueName = mailQueueName;
        this.mail = mail;
        this.enqueuedTime = enqueuedTime;
        this.partsId = partsId;
    }

    public EnqueueId getEnqueueId() {
        return enqueueId;
    }

    public MailQueueName getMailQueueName() {
        return mailQueueName;
    }

    public Mail getMail() {
        return mail;
    }

    public Instant getEnqueuedTime() {
        return enqueuedTime;
    }

    public MimeMessagePartsId getPartsId() {
        return partsId;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof EnqueuedItem) {
            EnqueuedItem that = (EnqueuedItem) o;

            return Objects.equals(this.enqueueId, that.enqueueId)
                && Objects.equals(this.mailQueueName, that.mailQueueName)
                && Objects.equals(this.mail, that.mail)
                && Objects.equals(this.enqueuedTime, that.enqueuedTime)
                && Objects.equals(this.partsId, that.partsId);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(enqueueId, mailQueueName, mail, enqueuedTime, partsId);
    }
}
