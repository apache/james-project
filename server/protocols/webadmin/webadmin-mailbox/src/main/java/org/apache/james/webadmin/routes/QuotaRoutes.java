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

package org.apache.james.webadmin.routes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaLimit;
import org.apache.james.core.quota.QuotaScope;
import org.apache.james.core.quota.QuotaType;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.service.QuotaService;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import spark.Service;

public class QuotaRoutes implements Routes {

    public static final String BASE_PATH = "/quota";
    public static final String COMPONENT_PATH_PARA = "quotaComponent";
    public static final String SCOPE_PATH_PARA = "quotaScope";
    public static final String IDENTIFIER_PATH_PARA = "identifier";
    public static final String GET_QUOTA_LIMIT_ENDPOINT = BASE_PATH + "/limit/:" + COMPONENT_PATH_PARA + "/:" + SCOPE_PATH_PARA + "/:" + IDENTIFIER_PATH_PARA;
    public static final String UPDATE_QUOTA_LIMIT_ENDPOINT = BASE_PATH + "/limit/:" + COMPONENT_PATH_PARA + "/:" + SCOPE_PATH_PARA + "/:" + IDENTIFIER_PATH_PARA;
    private static final TypeReference<HashMap<String, Long>> TYPE_REF = new TypeReference<HashMap<String, Long>>() {};

    private final QuotaService quotaLimitService;
    private final JsonTransformer jsonTransformer;
    private final ObjectMapper objectMapper;
    private Service service;

    @Inject
    public QuotaRoutes(QuotaService quotaLimitService, JsonTransformer jsonTransformer) {
        this.quotaLimitService = quotaLimitService;
        this.jsonTransformer = jsonTransformer;
        this.objectMapper = new ObjectMapper()
            .registerModule(new Jdk8Module())
            .registerModule(new GuavaModule());
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        this.service = service;
        defineGetQuota();
        defineUpdateQuota();
    }

    public void defineGetQuota() {
        service.get(GET_QUOTA_LIMIT_ENDPOINT,
            (request, response) -> {
                QuotaComponent quotaComponent = QuotaComponent.of(request.params(COMPONENT_PATH_PARA));
                QuotaScope scopeComponent = QuotaScope.of(request.params(SCOPE_PATH_PARA));
                String identifier = request.params(IDENTIFIER_PATH_PARA);
                return quotaLimitService.getQuotaLimits(quotaComponent, scopeComponent, identifier);
            }, jsonTransformer);
    }

    public void defineUpdateQuota() {
        service.put(UPDATE_QUOTA_LIMIT_ENDPOINT, ((request, response) -> {
            try {
                Map<String, Long> map = objectMapper.readValue(request.body(), TYPE_REF);
                validateQuotaLimitMap(map);
                QuotaComponent quotaComponent = QuotaComponent.of(request.params(COMPONENT_PATH_PARA));
                QuotaScope quotaScope = QuotaScope.of(request.params(SCOPE_PATH_PARA));
                String identifier = request.params(IDENTIFIER_PATH_PARA);
                List<QuotaLimit> quotaLimitList = map.entrySet().stream()
                    .map(entry -> QuotaLimit.builder().quotaComponent(quotaComponent).quotaScope(quotaScope)
                        .identifier(identifier).quotaType(QuotaType.of(entry.getKey())).quotaLimit(entry.getValue()).build())
                    .collect(Collectors.toList());
                quotaLimitService.saveQuotaLimits(quotaLimitList);
                return Responses.returnNoContent(response);
            } catch (JsonMappingException ex) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                    .message("Wrong JSON format")
                    .cause(ex)
                    .haltError();
            }
        }));
    }

    private void validateQuotaLimitMap(Map<String, Long> map) {
        for (Long value : map.values()) {
            if (value != null && value < -1) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                    .message("Invalid quota limit. Need to be greater or equal to -1")
                    .haltError();
            }
        }
    }
}
