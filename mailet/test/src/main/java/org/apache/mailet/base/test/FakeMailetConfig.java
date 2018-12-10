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

import java.util.*;

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
        private Map<String, Object> properties;

        private Builder() {
            mailetName = Optional.empty();
            mailetContext = Optional.empty();
            properties = new HashMap<>();
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

        public <T> Builder setProperty(String key, T value) {
            this.properties.put(key, value);
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
    private final Map<String, Object> properties;

    private FakeMailetConfig(String mailetName, MailetContext mailetContext, Map properties) {
        this.mailetName = mailetName;
        this.mailetContext = mailetContext;
        this.properties = properties;
    }

    @Override
    public <T> T getInitParameter(String name, Class<T> clazz) {
        Object o = properties.get(name);
        if (o != null) {
            if (clazz.isInstance(o)) {
                return (T) properties.get(name);
            } else {
                throw new ClassCastException(name + " property is not of type " + clazz.getCanonicalName());
            }
        } else {
            return null;
        }
    }

    @Override
    public String getInitParameter(String name) {
        return (String) properties.get(name);
    }


    @Override
    public Iterator<String> getInitParameterNames() {
        return properties.keySet().iterator();
    }

    @Override
    public MailetContext getMailetContext() {
        return mailetContext;
    }

    @Override
    public String getMailetName() {
        return mailetName;
    }
}
