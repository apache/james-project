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

package org.apache.james.backends.cassandra.utils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import com.datastax.oss.driver.api.core.RequestThrottlingException;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfig;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.session.throttling.Throttled;

class LockLessConcurrencyLimitingRequestThrottlerTest {
    @Test
    void registerShouldRejectRequestsFromOtherThreadsAfterClose() {
        DriverExecutionProfile profile = mock(DriverExecutionProfile.class);
        when(profile.getInt(DefaultDriverOption.REQUEST_THROTTLER_MAX_CONCURRENT_REQUESTS)).thenReturn(10);
        when(profile.getInt(DefaultDriverOption.REQUEST_THROTTLER_MAX_QUEUE_SIZE)).thenReturn(10);
        DriverConfig config = mock(DriverConfig.class);
        when(config.getDefaultProfile()).thenReturn(profile);
        DriverContext context = mock(DriverContext.class);
        when(context.getSessionName()).thenReturn("test");
        when(context.getConfig()).thenReturn(config);
        LockLessConcurrencyLimitingRequestThrottler testee = new LockLessConcurrencyLimitingRequestThrottler(context);
        Throttled request = mock(Throttled.class);

        testee.close();
        CompletableFuture.runAsync(() -> testee.register(request)).join();

        verify(request).onThrottleFailure(any(RequestThrottlingException.class));
        verify(request, never()).onThrottleReady(any(Boolean.class));
    }
}
