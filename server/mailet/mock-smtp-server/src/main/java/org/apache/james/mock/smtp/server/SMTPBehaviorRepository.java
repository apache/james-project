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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

import org.apache.james.mock.smtp.server.model.MockSMTPBehavior;
import org.apache.james.mock.smtp.server.model.MockSMTPBehaviorInformation;
import org.apache.james.mock.smtp.server.model.MockSmtpBehaviors;
import org.apache.james.mock.smtp.server.model.SMTPExtension;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

class SMTPBehaviorRepository {

    private final ConcurrentLinkedQueue<MockSMTPBehaviorInformation> behaviorsInformation;
    private final ConcurrentLinkedQueue<SMTPExtension> smtpExtensions;

    SMTPBehaviorRepository() {
        this.behaviorsInformation = new ConcurrentLinkedQueue<>();
        this.smtpExtensions = new ConcurrentLinkedQueue<>();
    }

    void clearBehaviors() {
        synchronized (behaviorsInformation) {
            this.behaviorsInformation.clear();
        }
    }

    void clearExtensions() {
        synchronized (smtpExtensions) {
            this.smtpExtensions.clear();
        }
    }

    void setSmtpExtensions(Collection<SMTPExtension> extensions) {
        synchronized (smtpExtensions) {
            clearExtensions();

            smtpExtensions.addAll(extensions);
        }
    }

    List<SMTPExtension> getSMTPExtensions() {
        synchronized (smtpExtensions) {
            return ImmutableList.copyOf(smtpExtensions);
        }
    }

    void setBehaviors(MockSmtpBehaviors behaviors) {
        synchronized (behaviorsInformation) {
            clearBehaviors();

            behaviorsInformation.addAll(behaviors
                .getBehaviorList()
                .stream()
                .map(MockSMTPBehaviorInformation::from)
                .collect(Guavate.toImmutableList()));
        }
    }

    void setBehaviors(MockSMTPBehavior... behaviors) {
        setBehaviors(new MockSmtpBehaviors(Arrays.asList(behaviors)));
    }

    Stream<MockSMTPBehaviorInformation> remainingBehaviors() {
        synchronized (behaviorsInformation) {
            return behaviorsInformation.stream()
                .filter(MockSMTPBehaviorInformation::hasRemainingAnswers);
        }
    }

    void decreaseRemainingAnswers(MockSMTPBehavior behavior) {
        getBehaviorInformation(behavior)
            .decreaseRemainingAnswers();
    }

    @VisibleForTesting
    MockSMTPBehaviorInformation getBehaviorInformation(MockSMTPBehavior behavior) {
        synchronized (behaviorsInformation) {
            return behaviorsInformation.stream()
                .filter(behaviorInformation -> behaviorInformation.getBehavior().equals(behavior))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("behavior " + behavior + " not found"));
        }
    }
}
