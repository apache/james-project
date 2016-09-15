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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

public class MailetConfiguration implements SerializableAsXml {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String match;
        private String clazz;
        private ImmutableMap.Builder<String, String> properties;

        private Builder() {
            properties = ImmutableMap.builder();
        }

        public Builder match(String match) {
            this.match = match;
            return this;
        }

        public Builder clazz(String clazz) {
            this.clazz = clazz;
            return this;
        }

        public Builder addProperty(String key, String value) {
            this.properties.put(key, value);
            return this;
        }
        
        public MailetConfiguration build() {
            Preconditions.checkState(!Strings.isNullOrEmpty(match), "'match' is mandatory");
            Preconditions.checkState(!Strings.isNullOrEmpty(clazz), "'class' is mandatory");
            return new MailetConfiguration(match, clazz, properties.build());
        }
    }

    private final String match;
    private final String clazz;
    private final Map<String, String> properties;

    private MailetConfiguration(String match, String clazz, ImmutableMap<String, String> properties) {
        this.match = match;
        this.clazz = clazz;
        this.properties = properties;
    }

    public String getMatch() {
        return match;
    }

    public String getClazz() {
        return clazz;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public String serializeAsXml() {
        StringBuilder builder = new StringBuilder();
        builder.append("<mailet match=\"").append(getMatch()).append("\" class=\"").append(getClazz()).append("\">\n");
        for (Map.Entry<String, String> entry : getProperties().entrySet()) {
            builder.append("<").append(entry.getKey()).append(">").append(entry.getValue()).append("</").append(entry.getKey()).append(">\n");
        }
        builder.append("</mailet>\n");
        return builder.toString();
    }
}
