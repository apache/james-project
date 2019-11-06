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

import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.webadmin.dto.QuotaDTO;
import org.apache.james.webadmin.dto.ValidatedQuotaDTO;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.eclipse.jetty.http.HttpStatus;

public class QuotaDTOValidator {

    private static final int UNLIMITED = -1;

    public ValidatedQuotaDTO validatedQuotaDTO(QuotaDTO quotaDTO) {
        try {
            Optional<QuotaCount> count = quotaDTO.getCount()
                .map(this::getQuotaCount);
            Optional<QuotaSize> size = quotaDTO.getSize()
                .map(this::getQuotaSize);

            return ValidatedQuotaDTO.builder()
                .count(count)
                .size(size)
                .build();
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid quota. Need to be an integer value greater or equal to -1")
                .cause(e)
                .haltError();
        }
    }

    private QuotaSize getQuotaSize(Long quotaValue) {
        if (quotaValue == UNLIMITED) {
            return QuotaSize.unlimited();
        } else {
            return QuotaSize.size(quotaValue);
        }
    }

    private QuotaCount getQuotaCount(Long quotaValue) {
        if (quotaValue == UNLIMITED) {
            return QuotaCount.unlimited();
        } else {
            return QuotaCount.count(quotaValue);
        }
    }
}
