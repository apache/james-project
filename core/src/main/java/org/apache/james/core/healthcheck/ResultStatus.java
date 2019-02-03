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
package org.apache.james.core.healthcheck;

import com.google.common.base.Preconditions;

public enum ResultStatus {
    HEALTHY("healthy"), 
    DEGRADED("degraded"), 
    UNHEALTHY("unhealthy");
    
    private final String value;
    
    ResultStatus(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }

    public static ResultStatus merge(ResultStatus resultStatus1, ResultStatus resultStatus2) {
        Preconditions.checkNotNull(resultStatus1);
        Preconditions.checkNotNull(resultStatus2);
        if (resultStatus1 == UNHEALTHY || resultStatus2 == UNHEALTHY) {
            return UNHEALTHY;
        }
        if (resultStatus1 == DEGRADED || resultStatus2 == DEGRADED) {
            return DEGRADED;
        }
        return HEALTHY;
    }
}