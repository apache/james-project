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

package org.apache.james.jmap.rfc8621.memory;

import java.time.Clock;
import java.time.Instant;

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.jmap.rfc8621.contract.PushServerExtension;
import org.apache.james.jmap.rfc8621.contract.PushSubscriptionProbeModule;
import org.apache.james.jmap.rfc8621.contract.WebPushContract;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.Module;

public class MemoryWebPushTest implements WebPushContract {
    public static class ClockExtension implements GuiceModuleTestExtension {
        private UpdatableTickingClock clock;

        @Override
        public void beforeEach(ExtensionContext extensionContext) {
            clock = new UpdatableTickingClock(Instant.now());
        }

        @Override
        public Module getModule() {
            return binder -> binder.bind(Clock.class).toInstance(clock);
        }

        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            return parameterContext.getParameter().getType() == UpdatableTickingClock.class;
        }

        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            return clock;
        }
    }

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<>(JamesServerBuilder.defaultConfigurationProvider())
        .server(configuration -> MemoryJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule(), new PushSubscriptionProbeModule()))
        .extension(new ClockExtension())
        .build();

    @RegisterExtension
    static PushServerExtension pushServerExtension = new PushServerExtension();
}
