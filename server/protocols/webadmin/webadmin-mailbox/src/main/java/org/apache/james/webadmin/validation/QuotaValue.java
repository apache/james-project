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

import org.apache.james.webadmin.utils.ErrorResponder;
import org.eclipse.jetty.http.HttpStatus;

public abstract class QuotaValue {

    public static class QuotaSize extends QuotaValue {
        QuotaSize(long value) {
            super(value);
        }
    }

    public static class QuotaCount extends QuotaValue {
        QuotaCount(long value) {
            super(value);
        }
    }

    public static QuotaCount quotaCount(String serialized) {
        return new QuotaCount(parse(serialized));
    }

    public static QuotaSize quotaSize(String serialized) {
        return new QuotaSize(parse(serialized));
    }

    private static long parse(String serialized) {
        try {
            return Long.valueOf(serialized);
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
            .message("Invalid quota. Need to be an integer value greater than 0");
    }

    private final long value;

    private QuotaValue(long value) {
        if (value < 0) {
            throw generateInvalidInputError()
                .haltError();
        }
        this.value = value;
    }

    public long asLong() {
        return value;
    }

}
