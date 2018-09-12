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

package org.apache.james.queue.rabbitmq.view.cassandra.model;

import java.time.Instant;
import java.util.Objects;

import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.mailet.Mail;

public class EnqueuedMail {

    public interface Builder {

        @FunctionalInterface
        interface RequireMail {
            RequireBucketId mail(Mail mail);
        }

        @FunctionalInterface
        interface RequireBucketId {
            RequireTimeRangeStart bucketId(BucketedSlices.BucketId bucketId);
        }

        @FunctionalInterface
        interface RequireTimeRangeStart {
            RequireEnqueuedTime timeRangeStart(Instant timeRangeStart);
        }

        @FunctionalInterface
        interface RequireEnqueuedTime {
            RequireMailKey enqueuedTime(Instant enqueuedTime);
        }

        @FunctionalInterface
        interface RequireMailKey {
            RequireMailQueueName mailKey(MailKey mailKey);
        }

        @FunctionalInterface
        interface RequireMailQueueName {
            LastStage mailQueueName(MailQueueName mailQueueName);
        }

        class LastStage {
            private Mail mail;
            private BucketedSlices.BucketId bucketId;
            private Instant timeRangeStart;
            private Instant enqueuedTime;
            private MailKey mailKey;
            private MailQueueName mailQueueName;

            private LastStage(Mail mail, BucketedSlices.BucketId bucketId,
                              Instant timeRangeStart, Instant enqueuedTime,
                              MailKey mailKey, MailQueueName mailQueueName) {
                this.mail = mail;
                this.bucketId = bucketId;
                this.timeRangeStart = timeRangeStart;
                this.enqueuedTime = enqueuedTime;
                this.mailKey = mailKey;
                this.mailQueueName = mailQueueName;
            }

            public EnqueuedMail build() {
                return new EnqueuedMail(mail, bucketId, timeRangeStart, enqueuedTime, mailKey, mailQueueName);
            }
        }
    }

    public static Builder.RequireMail builder() {
        return mail -> bucketId -> timeRangeStart -> enqueuedTime -> mailKey -> mailQueueName ->
            new Builder.LastStage(mail, bucketId, timeRangeStart, enqueuedTime, mailKey, mailQueueName);
    }

    private final Mail mail;
    private final BucketedSlices.BucketId bucketId;
    private final Instant timeRangeStart;
    private final Instant enqueuedTime;
    private final MailKey mailKey;
    private final MailQueueName mailQueueName;

    private EnqueuedMail(Mail mail, BucketedSlices.BucketId bucketId, Instant timeRangeStart,
                         Instant enqueuedTime, MailKey mailKey, MailQueueName mailQueueName) {
        this.mail = mail;
        this.bucketId = bucketId;
        this.timeRangeStart = timeRangeStart;
        this.enqueuedTime = enqueuedTime;
        this.mailKey = mailKey;
        this.mailQueueName = mailQueueName;
    }

    public Mail getMail() {
        return mail;
    }

    public BucketedSlices.BucketId getBucketId() {
        return bucketId;
    }

    public MailKey getMailKey() {
        return mailKey;
    }

    public MailQueueName getMailQueueName() {
        return mailQueueName;
    }

    public Instant getTimeRangeStart() {
        return timeRangeStart;
    }

    public Instant getEnqueuedTime() {
        return enqueuedTime;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof EnqueuedMail) {
            EnqueuedMail that = (EnqueuedMail) o;

            return Objects.equals(this.bucketId, that.bucketId)
                    && Objects.equals(this.mail, that.mail)
                    && Objects.equals(this.timeRangeStart, that.timeRangeStart)
                    && Objects.equals(this.enqueuedTime, that.enqueuedTime)
                    && Objects.equals(this.mailKey, that.mailKey)
                    && Objects.equals(this.mailQueueName, that.mailQueueName);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(mail, bucketId, timeRangeStart, enqueuedTime, mailKey, mailQueueName);
    }
}
