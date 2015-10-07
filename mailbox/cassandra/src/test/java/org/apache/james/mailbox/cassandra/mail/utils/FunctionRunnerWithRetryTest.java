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

package org.apache.james.mailbox.cassandra.mail.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang.mutable.MutableInt;
import org.apache.james.mailbox.exception.MailboxException;
import org.junit.Test;

public class FunctionRunnerWithRetryTest {
    
    private final static int MAX_RETRY = 10;

    @Test(expected = IllegalArgumentException.class)
    public void functionRunnerWithInvalidMaxRetryShouldFail() throws Exception {
        new FunctionRunnerWithRetry(-1);
    }

    @Test(expected = MailboxException.class)
    public void functionRunnerShouldFailIfTransactionCanNotBePerformed() throws Exception {
        final MutableInt value = new MutableInt(0);
        new FunctionRunnerWithRetry(MAX_RETRY).execute(
            () -> {
                value.increment();
                return false;
            }
        );
        assertThat(value.getValue()).isEqualTo(MAX_RETRY);
    }
    
    @Test
    public void functionRunnerShouldWorkOnFirstTry() throws Exception {
        final MutableInt value = new MutableInt(0);
        new FunctionRunnerWithRetry(MAX_RETRY).execute(
            () -> {
                value.increment();
                return true;
            }
        );
        assertThat(value.getValue()).isEqualTo(1);
    }

    @Test
    public void functionRunnerShouldWorkIfNotSucceededOnFirstTry() throws Exception {
        final MutableInt value = new MutableInt(0);
        new FunctionRunnerWithRetry(MAX_RETRY).execute(
            () -> {
                value.increment();
                return (Integer) value.getValue() == MAX_RETRY / 2;
            }
        );
        assertThat(value.getValue()).isEqualTo(MAX_RETRY / 2);
    }

    @Test
    public void functionRunnerShouldWorkIfNotSucceededOnMaxRetryReached() throws Exception {
        final MutableInt value = new MutableInt(0);
        new FunctionRunnerWithRetry(MAX_RETRY).execute(
                () -> {
                    value.increment();
                    return (Integer) value.getValue() == MAX_RETRY;
                }
        );
        assertThat(value.getValue()).isEqualTo(MAX_RETRY);
    }
    
}
