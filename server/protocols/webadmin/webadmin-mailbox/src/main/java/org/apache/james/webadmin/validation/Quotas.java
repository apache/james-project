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

package org.apache.james.webadmin.validation;

import java.util.Optional;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.eclipse.jetty.http.HttpStatus;

public abstract class Quotas {

    public static QuotaCountLimit quotaCount(String serialized) {
        return minusOneToEmpty(parseToLong(serialized))
                .map(QuotaCountLimit::count)
                .orElse(QuotaCountLimit.unlimited());
    }

    public static QuotaSizeLimit quotaSize(String serialized) {
        return minusOneToEmpty(parseToLong(serialized))
            .map(QuotaSizeLimit::size)
            .orElse(QuotaSizeLimit.unlimited());
    }

    private static Optional<Long> minusOneToEmpty(long value) {
        if (value < -1) {
            throw generateInvalidInputError().haltError();
        }
        if (value == -1) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private static long parseToLong(String serialized) {
        try {
            return Long.parseLong(serialized);
        } catch (IllegalArgumentException e) {
            throw generateInvalidInputError()
                .cause(e)
                .haltError();
        }
    }

    private static ErrorResponder generateInvalidInputError() {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
            .message("Invalid quota. Need to be an integer value greater or equal to -1");
    }

}
