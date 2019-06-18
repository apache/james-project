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

package org.apache.james.webadmin.dto;

import java.util.Optional;

import org.apache.james.core.healthcheck.Result;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.net.UrlEscapers;

@JsonPropertyOrder({"componentName", "escapedComponentName", "status", "cause"})
public class HealthCheckExecutionResultDto {

    private final Result healthCheckResult;

    public HealthCheckExecutionResultDto(Result healthCheckResult) {
        this.healthCheckResult = healthCheckResult;
    }
    
    public String getComponentName() {
        return healthCheckResult.getComponentName().getName();
    }
    
    public String getEscapedComponentName() {
        return UrlEscapers.urlPathSegmentEscaper().escape(
                healthCheckResult.getComponentName().getName());
    }
    
    public String getStatus() {
        return healthCheckResult.getStatus().getValue();
    }
    
    public Optional<String> getCause() {
        return healthCheckResult.getCause();
    }
    
}
