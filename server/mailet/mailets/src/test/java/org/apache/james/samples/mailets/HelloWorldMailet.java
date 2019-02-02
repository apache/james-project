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
package org.apache.james.samples.mailets;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simply logs a message.
 */
public class HelloWorldMailet implements Mailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(HelloWorldMailet.class);

    private MailetConfig config;

    @Override
    public void destroy() {
    }

    @Override
    public String getMailetInfo() {
        return "Example mailet";
    }

    @Override
    public MailetConfig getMailetConfig() {
        return config;
    }

    @Override
    public void init(MailetConfig config) {
        this.config = config;
    }

    @Override
    public void service(Mail mail) {
        LOGGER.info("Hello, World!");
        LOGGER.info("You have mail from {}", mail.getMaybeSender().asOptional().map(MailAddress::getLocalPart));
    }
}
