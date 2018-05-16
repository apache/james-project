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

import java.util.concurrent.CompletionException;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;

import com.datastax.driver.core.exceptions.InvalidQueryException;

public class DriverExceptionHelper {
    public static final String VALUES_MAY_NOT_BE_LARGER_THAN_64_K = "Index expression values may not be larger than 64K";
    public static final String CLUSTERING_COLUMNS_IS_TOO_LONG = "The sum of all clustering columns is too long";

    public static MailboxException handleStorageException(CompletionException e) throws MailboxException {
        if (e.getCause() instanceof InvalidQueryException) {
            return handleInvalidQuery((InvalidQueryException) e.getCause());
        }
        throw e;
    }

    public static MailboxException handleInvalidQuery(InvalidQueryException cause) throws MailboxException {
        if (isTooLong(cause)) {
            throw new TooLongMailboxNameException("too long mailbox name");
        }
        throw new MailboxException("Error while interacting with cassandra storage", cause);
    }

    public static boolean isTooLong(InvalidQueryException e) {
        String errorMessage = e.getMessage();
        return StringUtils.containsIgnoreCase(errorMessage, VALUES_MAY_NOT_BE_LARGER_THAN_64_K)
            || StringUtils.containsIgnoreCase(errorMessage, CLUSTERING_COLUMNS_IS_TOO_LONG);
    }

}
