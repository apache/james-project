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
    public static class RecordedRecipients {
        public static final RecordedRecipients NO_RECORDED_RECIPIENTS = new RecordedRecipients(ImmutableSet.of());

        public static RecordedRecipients fromMail(Mail mail) {
            return mail.getAttribute(RECORDED_RECIPIENTS_ATTRIBUTE_NAME)
                .map(RecordedRecipients::fromAttribute)
                .orElse(NO_RECORDED_RECIPIENTS);
        }

        public static RecordedRecipients fromAttribute(Attribute attribute) {
            Collection<AttributeValue> attributeValues = (Collection<AttributeValue>) attribute.getValue().getValue();
            return new RecordedRecipients(attributeValues
                .stream()
                .map(Throwing.function(attributeValue -> new MailAddress((String) attributeValue.getValue())))
                .collect(ImmutableSet.toImmutableSet()));
        }

        private final Set<MailAddress> recipients;

        public RecordedRecipients(Set<MailAddress> recipients) {
            this.recipients = recipients;
        }

        public RecordedRecipients(MailAddress... recipients) {
            this.recipients = ImmutableSet.copyOf(recipients);
        }

        public Set<MailAddress> getRecipients() {
            return recipients;
        }

        public Set<MailAddress> nonRecordedRecipients(Collection<MailAddress> recipients) {
            return Sets.difference(ImmutableSet.copyOf(recipients), this.recipients);
        }

        public Set<MailAddress> nonRecordedRecipients(MailAddress... recipients) {
            return nonRecordedRecipients(ImmutableSet.copyOf(recipients));
        }

        public RecordedRecipients merge(RecordedRecipients other) {
            return new RecordedRecipients(ImmutableSet.<MailAddress>builder()
                .addAll(recipients)
                .addAll(other.recipients)
                .build());
        }

        public RecordedRecipients merge(Collection<MailAddress> other) {
            return merge(new RecordedRecipients(ImmutableSet.copyOf(other)));
        }

        public RecordedRecipients merge(MailAddress... other) {
            return merge(ImmutableSet.copyOf(other));
        }

        public Attribute asAttribute() {
            return new Attribute(RECORDED_RECIPIENTS_ATTRIBUTE_NAME,
                AttributeValue.of(recipients.stream()
                    .map(mailAddress -> AttributeValue.of(mailAddress.asString()))
                    .collect(ImmutableList.toImmutableList())));
        }

        public void recordOn(Mail mail) {
            mail.setAttribute(asAttribute());
        }
    }

    public static final AttributeName RECORDED_RECIPIENTS_ATTRIBUTE_NAME = AttributeName.of("loop.prevention.recorded.recipients");

}
