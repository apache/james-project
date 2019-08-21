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

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableList;

public class MockSmtpBehaviors {
    private final List<MockSMTPBehavior> behaviorList;

    @JsonCreator
    public MockSmtpBehaviors(List<MockSMTPBehavior> behaviorList) {
        this.behaviorList = ImmutableList.copyOf(behaviorList);
    }

    @JsonValue
    public List<MockSMTPBehavior> getBehaviorList() {
        return behaviorList;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MockSmtpBehaviors) {
            MockSmtpBehaviors that = (MockSmtpBehaviors) o;

            return Objects.equals(this.behaviorList, that.behaviorList);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(behaviorList);
    }
}
