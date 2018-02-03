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

package org.apache.mailet.base.test;

import java.util.Iterator;
import java.util.Optional;
import java.util.Properties;

import org.apache.mailet.MailetConfig;
import org.apache.mailet.MailetContext;

/**
 * MailetConfig over Properties
 */
public class FakeMailetConfig implements MailetConfig {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private static final String DEFAULT_MAILET_NAME = "A Mailet";
        private Optional<String> mailetName;
        private Optional<MailetContext> mailetContext;
        private Properties properties;

        private Builder() {
            mailetName = Optional.empty();
            mailetContext = Optional.empty();
            properties = new Properties();
        }

        public Builder mailetName(String mailetName) {
            this.mailetName = Optional.ofNullable(mailetName);
            return this;
        }

        public Builder mailetContext(MailetContext mailetContext) {
            this.mailetContext = Optional.ofNullable(mailetContext);
            return this;
        }

        public Builder mailetContext(FakeMailContext.Builder mailetContext) {
            return mailetContext(mailetContext.build());
        }

        public Builder setProperty(String key, String value) {
            this.properties.setProperty(key, value);
            return this;
        }

        public FakeMailetConfig build() {
            return new FakeMailetConfig(mailetName.orElse(DEFAULT_MAILET_NAME),
                    mailetContext.orElse(FakeMailContext.defaultContext()),
                    properties);
        }
    }

    private final String mailetName;
    private final MailetContext mailetContext;
    private final Properties properties;

    private FakeMailetConfig(String mailetName, MailetContext mailetContext, Properties properties) {
        this.mailetName = mailetName;
        this.mailetContext = mailetContext;
        this.properties = properties;
    }

    public String getInitParameter(String name) {
        return properties.getProperty(name);
    }

    public Iterator<String> getInitParameterNames() {
        return properties.stringPropertyNames().iterator();
    }

    public MailetContext getMailetContext() {
        return mailetContext;
    }

    public String getMailetName() {
        return mailetName;
    }
}
