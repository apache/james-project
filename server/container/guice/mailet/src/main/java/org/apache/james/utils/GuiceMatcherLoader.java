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

package org.apache.james.utils;

import jakarta.mail.MessagingException;

import org.apache.james.mailetcontainer.api.MatcherLoader;
import org.apache.mailet.Matcher;
import org.apache.mailet.MatcherConfig;

import com.google.inject.Inject;

public class GuiceMatcherLoader implements MatcherLoader {
    private static final PackageName STANDARD_PACKAGE = PackageName.of("org.apache.james.transport.matchers.");
    private static final NamingScheme MATCHER_NAMING_SCHEME = new NamingScheme.OptionalPackagePrefix(STANDARD_PACKAGE);

    private final GuiceLoader guiceLoader;

    @Inject
    public GuiceMatcherLoader(GuiceLoader guiceLoader) {
        this.guiceLoader = guiceLoader;
    }

    @Override
    public Matcher getMatcher(MatcherConfig config) throws MessagingException {
        try {
            ClassName className = new ClassName(config.getMatcherName());
            Matcher result = guiceLoader.<Matcher>withNamingSheme(MATCHER_NAMING_SCHEME)
                .instantiate(className);
            result.init(config);
            return result;
        } catch (Exception e) {
            throw new MessagingException("Can not load matcher " + config.getMatcherName(), e);
        }
    }
}
