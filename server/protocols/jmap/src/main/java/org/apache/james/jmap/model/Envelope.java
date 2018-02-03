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

package org.apache.james.jmap.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.james.core.MailAddress;
import org.apache.james.util.StreamUtils;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;

public class Envelope {

    public static Envelope fromMessage(Message jmapMessage) {
        MailAddress sender = jmapMessage.getFrom()
            .map(Emailer::toMailAddress)
            .orElseThrow(() -> new RuntimeException("Sender is mandatory"));

        Stream<MailAddress> to = emailersToMailAddresses(jmapMessage.getTo());
        Stream<MailAddress> cc = emailersToMailAddresses(jmapMessage.getCc());
        Stream<MailAddress> bcc = emailersToMailAddresses(jmapMessage.getBcc());

        return new Envelope(sender,
            StreamUtils.flatten(Stream.of(to, cc, bcc))
                .collect(Guavate.toImmutableSet()));
    }

    private static Stream<MailAddress> emailersToMailAddresses(List<Emailer> emailers) {
        return emailers.stream()
            .map(Emailer::toMailAddress);
    }


    private final MailAddress from;
    private final Set<MailAddress> recipients;

    private Envelope(MailAddress from, Set<MailAddress> recipients) {
        Preconditions.checkNotNull(from);
        Preconditions.checkNotNull(recipients);

        this.from = from;
        this.recipients = recipients;
    }

    public MailAddress getFrom() {
        return from;
    }

    public Set<MailAddress> getRecipients() {
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
