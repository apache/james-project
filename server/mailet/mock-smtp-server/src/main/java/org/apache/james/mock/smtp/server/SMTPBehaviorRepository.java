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

package org.apache.james.mock.smtp.server;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.apache.james.mock.smtp.server.model.MockSMTPBehavior;
import org.apache.james.mock.smtp.server.model.MockSmtpBehaviors;

public class SMTPBehaviorRepository {
    private final AtomicReference<MockSmtpBehaviors> behaviors;

    public SMTPBehaviorRepository() {
        this.behaviors = new AtomicReference<>();
    }

    public Optional<MockSmtpBehaviors> getBehaviors() {
        return Optional.ofNullable(this.behaviors.get());
    }

    public void clearBehaviors() {
        this.behaviors.set(null);
    }

    public void setBehaviors(MockSmtpBehaviors behaviors) {
        this.behaviors.set(behaviors);
    }

    public void setBehaviors(MockSMTPBehavior... behaviors) {
        setBehaviors(new MockSmtpBehaviors(Arrays.asList(behaviors)));
    }

    public Stream<MockSMTPBehavior> allBehaviors() {
        return Optional.ofNullable(behaviors.get())
            .map(MockSmtpBehaviors::getBehaviorList)
            .map(List::stream)
            .orElseGet(Stream::empty);
    }
}
