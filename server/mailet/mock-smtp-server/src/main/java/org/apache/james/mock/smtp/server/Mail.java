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

package org.apache.james.mock.smtp.server;

import java.util.List;
import java.util.Objects;

import org.apache.james.core.MailAddress;

import com.google.common.base.Preconditions;

class Mail {
    static class Envelope {
        private final MailAddress from;
        private final List<MailAddress> recipients;

        Envelope(MailAddress from, List<MailAddress> recipients) {
            Preconditions.checkNotNull(from);
            Preconditions.checkNotNull(recipients);
            Preconditions.checkArgument(!recipients.isEmpty(), "'recipients' field should not be empty");

            this.from = from;
            this.recipients = recipients;
        }

        public MailAddress getFrom() {
            return from;
        }

        public List<MailAddress> getRecipients() {
            return recipients;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Envelope) {
                Envelope envelope = (Envelope) o;

                return Objects.equals(this.from, envelope.from)
                    && Objects.equals(this.recipients, envelope.recipients);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(from, recipients);
        }
    }

    private final Envelope envelope;
    private final String message;

    Mail(Envelope envelope, String message) {
        Preconditions.checkNotNull(envelope);
        Preconditions.checkNotNull(message);

        this.envelope = envelope;
        this.message = message;
    }

    public Envelope getEnvelope() {
        return envelope;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Mail) {
            Mail mail = (Mail) o;

            return Objects.equals(this.envelope, mail.envelope)
                && Objects.equals(this.message, mail.message);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(envelope, message);
    }
}
