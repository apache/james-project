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

package org.apache.james.smtpserver.netty;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.ConnectHandler;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.util.DurationParser;

public class MaxConnectionLifespanHandler implements ConnectHandler<SMTPSession> {
    public static final Response NOOP = new Response() {

        @Override
        public String getRetCode() {
            return "";
        }

        @Override
        public List<CharSequence> getLines() {
            return Collections.emptyList();
        }

        @Override
        public boolean isEndSession() {
            return false;
        }

    };

    private Optional<Duration> connectionLifespan = Optional.empty();

    @Override
    public void init(Configuration config) throws ConfigurationException {
        connectionLifespan = Optional.of(DurationParser.parse(Optional.ofNullable(config.getString("duration", null))
            .orElseThrow(() -> new ConfigurationRuntimeException("'duration' configuration property is compulsary"))));
    }

    @Override
    public Response onConnect(SMTPSession session) {
        connectionLifespan.ifPresent(duration ->
            session.schedule(session::close, duration));

        return NOOP;
    }
}
