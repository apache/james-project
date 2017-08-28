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

package org.apache.james.mdn.fields;

import java.util.Optional;

import com.google.common.base.Preconditions;

/**
 * Implements optional Reporting-UA header field
 *
 * https://tools.ietf.org/html/rfc8098#section-3.2.1
 */
public class ReportingUserAgent implements Field {
    private static final String FIELD_NAME = "Reporting-UA";
    private final String userAgentName;
    private final Optional<String> userAgentProduct;

    public ReportingUserAgent(String userAgentName, Optional<String> userAgentProduct) {
        Preconditions.checkNotNull(userAgentName);
        Preconditions.checkNotNull(userAgentProduct);
        this.userAgentName = userAgentName;
        this.userAgentProduct = userAgentProduct;
    }

    public String getUserAgentName() {
        return userAgentName;
    }

    public Optional<String> getUserAgentProduct() {
        return userAgentProduct;
    }

    @Override
    public String formattedValue() {
        return FIELD_NAME + ": " + userAgentName + "; "
            + userAgentProduct.orElse("");
    }
}
