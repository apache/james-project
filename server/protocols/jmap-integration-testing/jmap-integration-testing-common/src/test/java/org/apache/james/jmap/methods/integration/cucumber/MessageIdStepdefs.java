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

package org.apache.james.jmap.methods.integration.cucumber;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.apache.james.mailbox.model.MessageId;

import cucumber.runtime.java.guice.ScenarioScoped;

@ScenarioScoped
public class MessageIdStepdefs {

    private final Map<String, MessageId> messageIdsByName;

    @Inject
    private MessageIdStepdefs() {
        this.messageIdsByName = new HashMap<>();
    }

    public MessageId getMessageId(String name) {
        return messageIdsByName.get(name);
    }

    public MessageId addMessageId(String name, MessageId messageId) {
        return messageIdsByName.put(name, messageId);
    }

}
