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
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.QuotaDTO;
import org.apache.james.webadmin.dto.ValidatedQuotaDTO;
import org.apache.james.webadmin.service.DomainQuotaService;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.JsonTransformerModule;
import org.apache.james.webadmin.utils.Responses;
import org.apache.james.webadmin.validation.QuotaDTOValidator;
import org.apache.james.webadmin.validation.Quotas;
import org.eclipse.jetty.http.HttpStatus;

import spark.Request;
import spark.Service;

public class DomainQuotaRoutes implements Routes {

    private static final String DOMAIN = "domain";
    public static final String BASE_PATH = "/quota/domains";
    static final String QUOTA_ENDPOINT = BASE_PATH + "/:" + DOMAIN;
    private static final String COUNT_ENDPOINT = QUOTA_ENDPOINT + "/count";
    private static final String SIZE_ENDPOINT = QUOTA_ENDPOINT + "/size";

    private final DomainList domainList;
    private final DomainQuotaService domainQuotaService;
    private final UsersRepository usersRepository;
    private final JsonTransformer jsonTransformer;
    private final JsonExtractor<QuotaDTO> jsonExtractor;
    private final QuotaDTOValidator quotaDTOValidator;
    private Service service;

    @Inject
    public DomainQuotaRoutes(DomainList domainList, DomainQuotaService domainQuotaService, UsersRepository usersRepository, JsonTransformer jsonTransformer, Set<JsonTransformerModule> modules) {
        this.domainList = domainList;
        this.domainQuotaService = domainQuotaService;
        this.usersRepository = usersRepository;
        this.jsonTransformer = jsonTransformer;
        this.jsonExtractor = new JsonExtractor<>(QuotaDTO.class, modules.stream().map(JsonTransformerModule::asJacksonModule).collect(Collectors.toList()));
        this.quotaDTOValidator = new QuotaDTOValidator();
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

    public boolean isVirtualHostingSupported() {
        try {
            return usersRepository.supportVirtualHosting();
        } catch (UsersRepositoryException e) {
            throw new RuntimeException(e);
        }
    }

    public void defineUpdateQuota() {
        service.put(QUOTA_ENDPOINT, ((request, response) -> {
            try {
                Domain domain = checkDomainExist(request);
                QuotaDTO quotaDTO = jsonExtractor.parse(request.body());
                ValidatedQuotaDTO validatedQuotaDTO = quotaDTOValidator.validatedQuotaDTO(quotaDTO);
                domainQuotaService.defineQuota(domain, validatedQuotaDTO);
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
        service.get(QUOTA_ENDPOINT, (request, response) -> {
            Domain domain = checkDomainExist(request);
            return domainQuotaService.getQuota(domain);
        }, jsonTransformer);
    }

    public void defineDeleteQuotaSize() {
        service.delete(SIZE_ENDPOINT, (request, response) -> {
            Domain domain = checkDomainExist(request);
            domainQuotaService.remoteMaxQuotaSize(domain);
            return Responses.returnNoContent(response);
        });
    }

    public void defineUpdateQuotaSize() {
        service.put(SIZE_ENDPOINT, (request, response) -> {
            Domain domain = checkDomainExist(request);
            QuotaSizeLimit quotaSize = Quotas.quotaSize(request.body());
            domainQuotaService.setMaxSizeQuota(domain, quotaSize);
            return Responses.returnNoContent(response);
        });
    }

    public void defineGetQuotaSize() {
        service.get(SIZE_ENDPOINT, (request, response) -> {
            Domain domain = checkDomainExist(request);
            Optional<QuotaSizeLimit> maxSizeQuota = domainQuotaService.getMaxSizeQuota(domain);
            if (maxSizeQuota.isPresent()) {
                return maxSizeQuota;
            }
            return Responses.returnNoContent(response);
        }, jsonTransformer);
    }

    public void defineDeleteQuotaCount() {
        service.delete(COUNT_ENDPOINT, (request, response) -> {
            Domain domain = checkDomainExist(request);
            domainQuotaService.remoteMaxQuotaCount(domain);
            return Responses.returnNoContent(response);
        });
    }

    public void defineUpdateQuotaCount() {
        service.put(COUNT_ENDPOINT, (request, response) -> {
            Domain domain = checkDomainExist(request);
            QuotaCountLimit quotaCount = Quotas.quotaCount(request.body());
            domainQuotaService.setMaxCountQuota(domain, quotaCount);
            return Responses.returnNoContent(response);
        });
    }

    public void defineGetQuotaCount() {
        service.get(COUNT_ENDPOINT, (request, response) -> {
            Domain domain = checkDomainExist(request);
            Optional<QuotaCountLimit> maxCountQuota = domainQuotaService.getMaxCountQuota(domain);
            if (maxCountQuota.isPresent()) {
                return maxCountQuota;
            }
            return Responses.returnNoContent(response);
        }, jsonTransformer);
    }

    private Domain checkDomainExist(Request request) {
        if (!isVirtualHostingSupported()) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.METHOD_NOT_ALLOWED_405)
                .type(ErrorType.WRONG_STATE)
                .message("Domain Quota configuration not supported when virtual hosting is desactivated. Please use global quota configuration instead")
                .haltError();
        }
        try {
            Domain domain = Domain.of(request.params(DOMAIN));
            if (!domainList.containsDomain(domain)) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorType.NOT_FOUND)
                    .message("Domain not found")
                    .haltError();
            }
            return domain;
        } catch (DomainListException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorType.NOT_FOUND)
                .cause(e)
                .haltError();
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("Invalid domain")
                .cause(e)
                .haltError();
        }
    }
}
