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

import java.util.List;
import java.util.stream.Stream;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.jmap.model.Emailer;
import org.apache.james.jmap.model.message.view.MessageFullView;
import org.apache.james.server.core.Envelope;
import org.apache.james.util.StreamUtils;

import com.google.common.collect.ImmutableSet;

public class EnvelopeUtils {
    public static Envelope fromMessage(MessageFullView jmapMessage) {
        MaybeSender sender = MaybeSender.of(jmapMessage.getFrom()
            .map(Emailer::toMailAddress)
            .orElseThrow(() -> new RuntimeException("Sender is mandatory")));

        Stream<MailAddress> to = emailersToMailAddresses(jmapMessage.getTo());
        Stream<MailAddress> cc = emailersToMailAddresses(jmapMessage.getCc());
        Stream<MailAddress> bcc = emailersToMailAddresses(jmapMessage.getBcc());

        return new Envelope(sender,
            StreamUtils.flatten(Stream.of(to, cc, bcc))
                .collect(ImmutableSet.toImmutableSet()));
    }

    private static Stream<MailAddress> emailersToMailAddresses(List<Emailer> emailers) {
        return emailers.stream()
            .map(Emailer::toMailAddress);
    }
}
