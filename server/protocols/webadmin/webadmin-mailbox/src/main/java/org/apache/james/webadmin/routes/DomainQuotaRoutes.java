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

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.QuotaDTO;
import org.apache.james.webadmin.dto.QuotaDomainDTO;
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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import spark.Request;
import spark.Service;

@Api(tags = "DomainQuota")
@Path(DomainQuotaRoutes.QUOTA_ENDPOINT)
@Produces("application/json")
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

    @PUT
    @ApiOperation(value = "Updating count and size at the same time")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataTypeClass = QuotaDTO.class, paramType = "body")
    })
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK. The value has been updated."),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "The body is not a positive integer or not unlimited value (-1)."),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The requested domain can not be found."),
            @ApiResponse(code = HttpStatus.METHOD_NOT_ALLOWED_405, message = "Domain Quota configuration not supported when virtual hosting is desactivated."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
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

    @GET
    @ApiOperation(
        value = "Reading count and size at the same time",
        notes = "If there is no limitation for count and/or size, the returned value will be -1"
    )
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = QuotaDomainDTO.class),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The requested domain can not be found."),
            @ApiResponse(code = HttpStatus.METHOD_NOT_ALLOWED_405, message = "Domain Quota configuration not supported when virtual hosting is desactivated."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineGetQuota() {
        service.get(QUOTA_ENDPOINT, (request, response) -> {
            Domain domain = checkDomainExist(request);
            return domainQuotaService.getQuota(domain);
        }, jsonTransformer);
    }

    @DELETE
    @Path("/size")
    @ApiOperation(value = "Removing per domain mail size limitation by updating to unlimited value")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "The value is updated to unlimited value."),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The requested domain can not be found."),
            @ApiResponse(code = HttpStatus.METHOD_NOT_ALLOWED_405, message = "Domain Quota configuration not supported when virtual hosting is desactivated."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineDeleteQuotaSize() {
        service.delete(SIZE_ENDPOINT, (request, response) -> {
            Domain domain = checkDomainExist(request);
            domainQuotaService.remoteMaxQuotaSize(domain);
            return Responses.returnNoContent(response);
        });
    }

    @PUT
    @Path("/size")
    @ApiOperation(value = "Updating per domain mail size limitation")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "integer", paramType = "body")
    })
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK. The value has been updated."),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "The body is not a positive integer."),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The requested domain can not be found."),
            @ApiResponse(code = HttpStatus.METHOD_NOT_ALLOWED_405, message = "Domain Quota configuration not supported when virtual hosting is desactivated."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineUpdateQuotaSize() {
        service.put(SIZE_ENDPOINT, (request, response) -> {
            Domain domain = checkDomainExist(request);
            QuotaSize quotaSize = Quotas.quotaSize(request.body());
            domainQuotaService.setMaxSizeQuota(domain, quotaSize);
            return Responses.returnNoContent(response);
        });
    }

    @GET
    @Path("/size")
    @ApiOperation(value = "Reading per domain mail size limitation")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = Long.class),
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "No value defined"),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The requested domain can not be found."),
            @ApiResponse(code = HttpStatus.METHOD_NOT_ALLOWED_405, message = "Domain Quota configuration not supported when virtual hosting is desactivated."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineGetQuotaSize() {
        service.get(SIZE_ENDPOINT, (request, response) -> {
            Domain domain = checkDomainExist(request);
            Optional<QuotaSize> maxSizeQuota = domainQuotaService.getMaxSizeQuota(domain);
            if (maxSizeQuota.isPresent()) {
                return maxSizeQuota;
            }
            return Responses.returnNoContent(response);
        }, jsonTransformer);
    }

    @DELETE
    @Path("/count")
    @ApiOperation(value = "Removing per domain mail count limitation by updating to unlimited value")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "The value is updated to unlimited value."),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The requested domain can not be found."),
            @ApiResponse(code = HttpStatus.METHOD_NOT_ALLOWED_405, message = "Domain Quota configuration not supported when virtual hosting is desactivated."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineDeleteQuotaCount() {
        service.delete(COUNT_ENDPOINT, (request, response) -> {
            Domain domain = checkDomainExist(request);
            domainQuotaService.remoteMaxQuotaCount(domain);
            return Responses.returnNoContent(response);
        });
    }

    @PUT
    @Path("/count")
    @ApiOperation(value = "Updating per domain mail count limitation")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "integer", paramType = "body")
    })
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK. The value has been updated."),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "The body is not a positive integer."),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The requested domain can not be found."),
            @ApiResponse(code = HttpStatus.METHOD_NOT_ALLOWED_405, message = "Domain Quota configuration not supported when virtual hosting is desactivated."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineUpdateQuotaCount() {
        service.put(COUNT_ENDPOINT, (request, response) -> {
            Domain domain = checkDomainExist(request);
            QuotaCount quotaCount = Quotas.quotaCount(request.body());
            domainQuotaService.setMaxCountQuota(domain, quotaCount);
            return Responses.returnNoContent(response);
        });
    }

    @GET
    @Path("/count")
    @ApiOperation(value = "Reading per domain mail count limitation")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = Long.class),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The requested domain can not be found."),
            @ApiResponse(code = HttpStatus.METHOD_NOT_ALLOWED_405, message = "Domain Quota configuration not supported when virtual hosting is desactivated."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineGetQuotaCount() {
        service.get(COUNT_ENDPOINT, (request, response) -> {
            Domain domain = checkDomainExist(request);
            Optional<QuotaCount> maxCountQuota = domainQuotaService.getMaxCountQuota(domain);
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
