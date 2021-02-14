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


package org.apache.james.mailets.configuration;

import java.util.Map;
import java.util.Optional;

import org.apache.james.transport.mailets.LocalDelivery;
import org.apache.james.transport.mailets.RemoteDelivery;
import org.apache.james.transport.mailets.RemoveMimeHeader;
import org.apache.james.transport.mailets.ToProcessor;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RecipientIsLocal;
import org.apache.mailet.Mailet;
import org.apache.mailet.Matcher;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

public class MailetConfiguration implements SerializableAsXml {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Class<? extends Matcher> matcher;
        private Optional<String> matcherCondition;
        private Class<? extends Mailet> mailet;
        private ImmutableMap.Builder<String, String> properties;

        private Builder() {
            properties = ImmutableMap.builder();
            matcherCondition = Optional.empty();
        }

        public Builder matcher(Class<? extends Matcher> matcher) {
            this.matcher = matcher;
            return this;
        }

        public Builder matcherCondition(String condition) {
            this.matcherCondition = Optional.ofNullable(condition);
            return this;
        }

        public Builder mailet(Class<? extends Mailet> mailet) {
            this.mailet = mailet;
            return this;
        }

        public Builder addProperty(String key, String value) {
            this.properties.put(key, value);
            return this;
        }
        
        public MailetConfiguration build() {
            Preconditions.checkState(matcher != null, "'matcher' is mandatory");
            Preconditions.checkState(mailet != null, "'mailet' is mandatory");
            return new MailetConfiguration(matcher, matcherCondition, mailet, properties.build());
        }
    }

    public static final MailetConfiguration BCC_STRIPPER = MailetConfiguration.builder()
        .matcher(All.class)
        .mailet(RemoveMimeHeader.class)
        .addProperty("name", "bcc")
        .build();

    public static final MailetConfiguration LOCAL_DELIVERY = MailetConfiguration.builder()
        .matcher(RecipientIsLocal.class)
        .mailet(LocalDelivery.class)
        .build();

    public static final MailetConfiguration TO_TRANSPORT = MailetConfiguration.builder()
        .matcher(All.class)
        .mailet(ToProcessor.class)
        .addProperty("processor", ProcessorConfiguration.TRANSPORT_PROCESSOR)
        .build();

    public static final MailetConfiguration TO_BOUNCE = MailetConfiguration.builder()
        .matcher(All.class)
        .mailet(ToProcessor.class)
        .addProperty("processor", ProcessorConfiguration.STATE_BOUNCES)
        .build();

    public static MailetConfiguration.Builder remoteDeliveryBuilder() {
        return remoteDeliveryBuilderNoBounces()
            .addProperty("bounceProcessor", ProcessorConfiguration.STATE_ERROR);
    }

    public static MailetConfiguration.Builder remoteDeliveryBuilderNoBounces() {
        return MailetConfiguration.builder()
            .mailet(RemoteDelivery.class)
            .addProperty("outgoingQueue", "outgoing")
            .addProperty("delayTime", "5000, 100000, 500000")
            .addProperty("maxRetries", "2")
            .addProperty("maxDnsProblemRetries", "0")
            .addProperty("deliveryThreads", "2")
            .addProperty("sendpartial", "true");
    }

    private final Class<? extends Matcher> matcher;
    private final Optional<String> matcherCondition;
    private final Class<? extends Mailet> mailet;
    private final Map<String, String> properties;

    private MailetConfiguration(Class<? extends Matcher> matcher, Optional<String> matcherCondition, Class<? extends Mailet> mailet, ImmutableMap<String, String> properties) {
        this.matcher = matcher;
        this.matcherCondition = matcherCondition;
        this.mailet = mailet;
        this.properties = properties;
    }

    public Class<? extends Matcher> getMatcher() {
        return matcher;
    }

    public Optional<String> getMatcherCondition() {
        return matcherCondition;
    }

    public Class<? extends Mailet> getMailet() {
        return mailet;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public String serializeAsXml() {
        StringBuilder builder = new StringBuilder();
        builder
            .append("<mailet match=\"").append(matcherWithCondition())
            .append("\" class=\"").append(getMailet().getCanonicalName())
            .append("\">\n");
        for (Map.Entry<String, String> entry : getProperties().entrySet()) {
            builder.append("<").append(entry.getKey()).append(">").append(entry.getValue()).append("</").append(entry.getKey()).append(">\n");
        }
        builder.append("</mailet>\n");
        return builder.toString();
    }

    @VisibleForTesting String matcherWithCondition() {
        StringBuilder match = new StringBuilder().append(matcher.getCanonicalName());
        matcherCondition.ifPresent(condition -> match.append("=").append(condition));
        return match.toString();
    }
}
