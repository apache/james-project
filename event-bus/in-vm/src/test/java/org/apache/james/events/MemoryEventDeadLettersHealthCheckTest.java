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

package org.apache.james.events;

import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class MemoryEventDeadLettersHealthCheckTest implements EventDeadLettersHealthCheckContract {

    private MemoryEventDeadLetters eventDeadLetters = new MemoryEventDeadLetters();
    private EventDeadLettersHealthCheck testee = new EventDeadLettersHealthCheck(eventDeadLetters);

    @Override
    public EventDeadLettersHealthCheck testee() {
        return testee;
    }

    @Override
    public EventDeadLetters eventDeadLetters() {
        return eventDeadLetters;
    }

    @Override
    public void createErrorWhenDoingHealthCheck() {
        throw new NotImplementedException("We can not instrument implementation this test case.");
    }

    @Override
    public void resolveErrorWhenDoingHealthCheck() {
        throw new NotImplementedException("We can not instrument implementation this test case.");
    }

    @Override
    @Test
    @Disabled("Unable to trigger the EventDeadLetter error with memory version")
    public void checkShouldReturnUnHealthyWhenEventDeadLetterError() {

    }
}
