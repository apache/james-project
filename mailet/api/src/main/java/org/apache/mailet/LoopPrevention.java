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

package org.apache.mailet;

import java.util.Collection;
import java.util.Set;

import org.apache.james.core.MailAddress;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class LoopPrevention {
    public static final AttributeName RECORDED_RECIPIENTS_ATTRIBUTE_NAME = AttributeName.of("loop.prevention.recorded.recipients");

    public static Set<MailAddress> nonRecordedRecipients(Set<MailAddress> recipients, Set<MailAddress> recordedRecipients) {
        return Sets.difference(recipients, recordedRecipients);
    }

    public static void recordRecipients(Mail mail, Set<MailAddress> recordedRecipients, Set<MailAddress> recipients) {
        mail.setAttribute(createAttribute(ImmutableSet.<MailAddress>builder()
            .addAll(recordedRecipients)
            .addAll(recipients)
            .build()));
    }

    public static Set<MailAddress> retrieveRecordedRecipients(Mail mail) {
        return mail.getAttribute(RECORDED_RECIPIENTS_ATTRIBUTE_NAME)
            .map(LoopPrevention::retrieveRecordedRecipients)
            .orElse(ImmutableSet.of());
    }

    private static Attribute createAttribute(Collection<MailAddress> mailAddresses) {
        return new Attribute(RECORDED_RECIPIENTS_ATTRIBUTE_NAME,
            AttributeValue.of(mailAddresses.stream()
                .map(mailAddress -> AttributeValue.of(mailAddress.asString()))
                .collect(ImmutableList.toImmutableList())));
    }

    private static Set<MailAddress> retrieveRecordedRecipients(Attribute attribute) {
        Collection<AttributeValue> attributeValues = (Collection<AttributeValue>) attribute.getValue().getValue();
        return attributeValues
            .stream()
            .map(Throwing.function(attributeValue -> new MailAddress((String) attributeValue.getValue())))
            .collect(ImmutableSet.toImmutableSet());
    }
}
