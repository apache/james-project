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
import java.util.stream.Collectors;

import javax.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

public class Envelope {
    private static final Logger LOGGER = LoggerFactory.getLogger(Envelope.class);

    public static Envelope fromMessage(Message jmapMessage) {
        MailAddress sender = jmapMessage.getFrom()
            .map(Envelope::emailerToMailAddress)
            .orElseThrow(() -> new RuntimeException("Sender is mandatory"));
        Set<MailAddress> to = emailersToMailAddressSet(jmapMessage.getTo());
        Set<MailAddress> cc = emailersToMailAddressSet(jmapMessage.getCc());
        Set<MailAddress> bcc = emailersToMailAddressSet(jmapMessage.getBcc());

        return new Envelope(sender, to, cc, bcc);
    }

    private static Set<MailAddress> emailersToMailAddressSet(List<Emailer> emailers) {
        return emailers.stream()
            .map(Envelope::emailerToMailAddress)
            .collect(Collectors.toSet());
    }

    private static MailAddress emailerToMailAddress(Emailer emailer) {
        Preconditions.checkArgument(emailer.getEmail().isPresent(), "eMailer mail address should be present when sending a mail using JMAP");
        try {
            return new MailAddress(emailer.getEmail().get());
        } catch (AddressException e) {
            LOGGER.error("Invalid mail address", emailer.getEmail());
            throw Throwables.propagate(e);
        }
    }

    private final MailAddress from;
    private final Set<MailAddress> to;
    private final Set<MailAddress> cc;
    private final Set<MailAddress> bcc;

    private Envelope(MailAddress from, Set<MailAddress> to, Set<MailAddress> cc, Set<MailAddress> bcc) {
        Preconditions.checkNotNull(from);
        Preconditions.checkNotNull(to);
        Preconditions.checkNotNull(cc);
        Preconditions.checkNotNull(bcc);

        this.from = from;
        this.to = to;
        this.cc = cc;
        this.bcc = bcc;
    }

    public MailAddress getFrom() {
        return from;
    }

    public Set<MailAddress> getTo() {
        return to;
    }

    public Set<MailAddress> getCc() {
        return cc;
    }

    public Set<MailAddress> getBcc() {
        return bcc;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Envelope) {
            Envelope envelope = (Envelope) o;

            return Objects.equals(this.from, envelope.from)
                && Objects.equals(this.to, envelope.to)
                && Objects.equals(this.cc, envelope.cc)
                && Objects.equals(this.bcc, envelope.bcc);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(from, to, cc, bcc);
    }
}
