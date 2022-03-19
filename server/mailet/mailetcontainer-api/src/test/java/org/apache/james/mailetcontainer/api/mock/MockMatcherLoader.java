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
package org.apache.james.mailetcontainer.api.mock;

import jakarta.mail.MessagingException;

import org.apache.james.mailetcontainer.api.MatcherLoader;
import org.apache.mailet.Matcher;
import org.apache.mailet.MatcherConfig;

public class MockMatcherLoader implements MatcherLoader {

    @SuppressWarnings("unchecked")
    @Override
    public Matcher getMatcher(MatcherConfig config) throws MessagingException {

        try {
            Class<Matcher> clazz = (Class<Matcher>) Thread.currentThread().getContextClassLoader().loadClass(
                    config.getMatcherName());
            Matcher m = clazz.getDeclaredConstructor().newInstance();
            m.init(config);
            return m;
        } catch (Exception e) {
            throw new MessagingException("Unable to load matcher " + config.getMatcherName());
        }

    }
}
