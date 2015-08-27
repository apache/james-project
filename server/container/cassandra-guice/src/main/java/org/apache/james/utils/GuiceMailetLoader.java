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

import com.google.inject.Inject;
import com.google.inject.Injector;
import org.apache.james.mailetcontainer.api.MailetLoader;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetConfig;

import javax.mail.MessagingException;

public class GuiceMailetLoader implements MailetLoader {

    private static final String STANDARD_PACKAGE = "org.apache.james.transport.mailets.";

    private final GuiceGenericLoader<Mailet> genericLoader;

    @Inject
    public GuiceMailetLoader(Injector injector) {
        this.genericLoader = new GuiceGenericLoader<>(injector, STANDARD_PACKAGE);
    }

    @Override
    public Mailet getMailet(MailetConfig config) throws MessagingException {
        try {
            Mailet result = genericLoader.instanciate(config.getMailetName());
            result.init(config);
            return result;
        } catch (Exception e) {
            throw new MessagingException("Can not load mailet " + config.getMailetName(), e);
        }
    }

}
