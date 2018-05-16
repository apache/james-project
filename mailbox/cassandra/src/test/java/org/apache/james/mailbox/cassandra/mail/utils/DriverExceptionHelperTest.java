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

import static org.apache.james.mailbox.cassandra.mail.utils.DriverExceptionHelper.CLUSTERING_COLUMNS_IS_TOO_LONG;
import static org.apache.james.mailbox.cassandra.mail.utils.DriverExceptionHelper.VALUES_MAY_NOT_BE_LARGER_THAN_64_K;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletionException;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;
import org.junit.Test;

import com.datastax.driver.core.exceptions.InvalidQueryException;

public class DriverExceptionHelperTest {

    @Test
    public void handleStorageExceptionShouldPropagateWhenNoCause() {
        CompletionException completionException = new CompletionException() {};

        assertThatThrownBy(() ->
            DriverExceptionHelper.handleStorageException(completionException))
                .isEqualTo(completionException);
    }

    @Test
    public void handleStorageExceptionShouldPropagateWhenCauseIsNotInvalidQuery() {
        CompletionException exception = new CompletionException("message", new RuntimeException());

        assertThatThrownBy(() ->
            DriverExceptionHelper.handleStorageException(exception))
                .isEqualTo(exception);
    }

    @Test
    public void handleStorageExceptionShouldUnwrapWhenCauseIsInvalidQuery() {
        InvalidQueryException invalidQueryException = new InvalidQueryException("message");
        CompletionException exception = new CompletionException("message", invalidQueryException);

        assertThatThrownBy(() ->
            DriverExceptionHelper.handleStorageException(exception))
                .isInstanceOf(MailboxException.class)
                .hasCause(invalidQueryException);
    }

    @Test
    public void handleStorageExceptionShouldThrowTooLongWhenClusteringColumnsTooLong() {
        InvalidQueryException invalidQueryException = new InvalidQueryException(CLUSTERING_COLUMNS_IS_TOO_LONG);
        CompletionException exception = new CompletionException("message", invalidQueryException);

        assertThatThrownBy(() ->
            DriverExceptionHelper.handleStorageException(exception))
                .isInstanceOf(TooLongMailboxNameException.class);
    }

    @Test
    public void handleStorageExceptionShouldThrowTooLongWhenValueMoreThan64K() {
        InvalidQueryException invalidQueryException = new InvalidQueryException(VALUES_MAY_NOT_BE_LARGER_THAN_64_K);
        CompletionException exception = new CompletionException("message", invalidQueryException);

        assertThatThrownBy(() ->
            DriverExceptionHelper.handleStorageException(exception))
                .isInstanceOf(TooLongMailboxNameException.class);
    }

}