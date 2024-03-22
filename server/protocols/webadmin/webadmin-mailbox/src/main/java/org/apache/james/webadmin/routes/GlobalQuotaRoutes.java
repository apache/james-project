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

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.QuotaDTO;
import org.apache.james.webadmin.dto.ValidatedQuotaDTO;
import org.apache.james.webadmin.jackson.QuotaModule;
import org.apache.james.webadmin.service.GlobalQuotaService;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.Responses;
import org.apache.james.webadmin.validation.QuotaDTOValidator;
import org.apache.james.webadmin.validation.Quotas;
import org.eclipse.jetty.http.HttpStatus;

import spark.Request;
import spark.Response;
import spark.Service;

public class GlobalQuotaRoutes implements Routes {

    public static final String QUOTA_ENDPOINT = "/quota";
    private static final String COUNT_ENDPOINT = QUOTA_ENDPOINT + "/count";
    private static final String SIZE_ENDPOINT = QUOTA_ENDPOINT + "/size";

    private final JsonTransformer jsonTransformer;
    private final JsonExtractor<QuotaDTO> jsonExtractor;
    private final QuotaDTOValidator quotaDTOValidator;
    private final GlobalQuotaService globalQuotaService;
    private Service service;

    @Inject
    public GlobalQuotaRoutes(GlobalQuotaService globalQuotaService, JsonTransformer jsonTransformer) {
        this.globalQuotaService = globalQuotaService;
        this.jsonTransformer = jsonTransformer;
        this.jsonExtractor = new JsonExtractor<>(QuotaDTO.class, new QuotaModule().asJacksonModule());
        quotaDTOValidator = new QuotaDTOValidator();
    }

    @Override
    public String getBasePath() {
        return QUOTA_ENDPOINT;
    }

    @Override
    public void define(Service service) {
        this.service = service;

        defineGetQuotaCount();

        defineDeleteQuotaCount();

        defineUpdateQuotaCount();

        defineGetQuotaSize();

        defineDeleteQuotaSize();

        defineUpdateQuotaSize();

        defineGetQuota();

        defineUpdateQuota();
    }

    public void defineUpdateQuota() {
        service.put(QUOTA_ENDPOINT, ((request, response) -> {
            try {
                QuotaDTO quotaDTO = jsonExtractor.parse(request.body());
                ValidatedQuotaDTO validatedQuotaDTO = quotaDTOValidator.validatedQuotaDTO(quotaDTO);
                globalQuotaService.defineQuota(validatedQuotaDTO);
                return Responses.returnNoContent(response);
            } catch (IllegalArgumentException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Quota should be positive or unlimited (-1)")
                    .cause(e)
                    .haltError();
            }
        }));
    }

    public void defineGetQuota() {
        service.get(QUOTA_ENDPOINT, (request, response) -> globalQuotaService.getQuota(), jsonTransformer);
    }

    public void defineDeleteQuotaSize() {
        service.delete(SIZE_ENDPOINT, (request, response) -> {
            globalQuotaService.deleteMaxSizeQuota();
            return Responses.returnNoContent(response);
        });
    }

    public void defineUpdateQuotaSize() {
        service.put(SIZE_ENDPOINT, (request, response) -> {
            QuotaSizeLimit quotaSize = Quotas.quotaSize(request.body());
            globalQuotaService.defineMaxSizeQuota(quotaSize);
            return Responses.returnNoContent(response);
        });
    }

    public void defineGetQuotaSize() {
        service.get(SIZE_ENDPOINT, this::getQuotaSize, jsonTransformer);
    }

    private QuotaSizeLimit getQuotaSize(Request request, Response response) throws MailboxException {
        Optional<QuotaSizeLimit> maxSizeQuota = globalQuotaService.getMaxSizeQuota();
        if (maxSizeQuota.isPresent()) {
            return maxSizeQuota.get();
        }
        response.status(HttpStatus.NO_CONTENT_204);
        return null;
    }

    public void defineDeleteQuotaCount() {
        service.delete(COUNT_ENDPOINT, (request, response) -> {
            globalQuotaService.deleteMaxCountQuota();
            return Responses.returnNoContent(response);
        });
    }

    public void defineUpdateQuotaCount() {
        service.put(COUNT_ENDPOINT, (request, response) -> {
            QuotaCountLimit quotaRequest = Quotas.quotaCount(request.body());
            globalQuotaService.defineMaxCountQuota(quotaRequest);
            return Responses.returnNoContent(response);
        });
    }

    public void defineGetQuotaCount() {
        service.get(COUNT_ENDPOINT, this::getQuotaCount, jsonTransformer);
    }

    private QuotaCountLimit getQuotaCount(Request request, Response response) throws MailboxException {
        Optional<QuotaCountLimit> maxCountQuota = globalQuotaService.getMaxCountQuota();
        if (maxCountQuota.isPresent()) {
            return maxCountQuota.get();
        }
        response.status(HttpStatus.NO_CONTENT_204);
        return null;
    }
}
