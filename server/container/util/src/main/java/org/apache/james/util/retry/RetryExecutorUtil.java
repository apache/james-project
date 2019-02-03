/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.util.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nurkiewicz.asyncretry.AsyncRetryExecutor;

public class RetryExecutorUtil {
    private static final Logger LOG = LoggerFactory.getLogger(RetryExecutorUtil.class);

    private static final int INITIAL_DELAY_MILLIS = 500;
    private static final int MULTIPLIER = 2;

    @SafeVarargs
    public static AsyncRetryExecutor retryOnExceptions(AsyncRetryExecutor executor, int maxRetries, int minDelay, Class<? extends Throwable>... clazz) {
        LOG.info("The action should retry when {} and retry to {} times if needed", clazz, maxRetries);
        return executor
            .withExponentialBackoff(INITIAL_DELAY_MILLIS, MULTIPLIER)
            .withProportionalJitter()
            .retryOn(clazz)
            .withMaxRetries(maxRetries)
            .withMinDelay(minDelay);
    }

}
