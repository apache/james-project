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

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import spark.Request;
import spark.Response;
import spark.Service;

@Api(tags = "GlobalQuota")
@Path(GlobalQuotaRoutes.QUOTA_ENDPOINT)
@Produces("application/json")
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

    @PUT
    @ApiOperation(value = "Updating count and size at the same time")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataTypeClass = QuotaDTO.class, paramType = "body")
    })
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK. The value has been updated."),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "The body is not a positive integer or not unlimited value (-1)."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
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

    @GET
    @ApiOperation(
        value = "Reading count and size at the same time",
        notes = "If there is no limitation for count and/or size, the returned value will be -1"
    )
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = QuotaDTO.class),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineGetQuota() {
        service.get(QUOTA_ENDPOINT, (request, response) -> globalQuotaService.getQuota(), jsonTransformer);
    }

    @DELETE
    @Path("/size")
    @ApiOperation(value = "Removing per quotaroot mail size limitation by updating to unlimited value")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "The value is updated to unlimited value."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineDeleteQuotaSize() {
        service.delete(SIZE_ENDPOINT, (request, response) -> {
            globalQuotaService.deleteMaxSizeQuota();
            return Responses.returnNoContent(response);
        });
    }

    @PUT
    @Path("/size")
    @ApiOperation(value = "Updating per quotaroot mail size limitation")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "integer", paramType = "body")
    })
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK. The value has been updated."),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "The body is not a positive integer."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineUpdateQuotaSize() {
        service.put(SIZE_ENDPOINT, (request, response) -> {
            QuotaSize quotaSize = Quotas.quotaSize(request.body());
            globalQuotaService.defineMaxSizeQuota(quotaSize);
            return Responses.returnNoContent(response);
        });
    }

    @GET
    @Path("/size")
    @ApiOperation(value = "Reading per quotaroot mail size limitation")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = Long.class),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineGetQuotaSize() {
        service.get(SIZE_ENDPOINT, this::getQuotaSize, jsonTransformer);
    }

    private QuotaSize getQuotaSize(Request request, Response response) throws MailboxException {
        Optional<QuotaSize> maxSizeQuota = globalQuotaService.getMaxSizeQuota();
        if (maxSizeQuota.isPresent()) {
            return maxSizeQuota.get();
        }
        response.status(HttpStatus.NO_CONTENT_204);
        return null;
    }

    @DELETE
    @Path("/count")
    @ApiOperation(value = "Removing per quotaroot mail count limitation by updating to unlimited value")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "The value is updated to unlimited value."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineDeleteQuotaCount() {
        service.delete(COUNT_ENDPOINT, (request, response) -> {
            globalQuotaService.deleteMaxCountQuota();
            return Responses.returnNoContent(response);
        });
    }

    @PUT
    @Path("/count")
    @ApiOperation(value = "Updating per quotaroot mail count limitation")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "integer", paramType = "body")
    })
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK. The value has been updated."),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "The body is not a positive integer."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineUpdateQuotaCount() {
        service.put(COUNT_ENDPOINT, (request, response) -> {
            QuotaCount quotaRequest = Quotas.quotaCount(request.body());
            globalQuotaService.defineMaxCountQuota(quotaRequest);
            return Responses.returnNoContent(response);
        });
    }

    @GET
    @Path("/count")
    @ApiOperation(value = "Reading per quotaroot mail count limitation")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = Long.class),
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "Quota is not defined"),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineGetQuotaCount() {
        service.get(COUNT_ENDPOINT, this::getQuotaCount, jsonTransformer);
    }

    private QuotaCount getQuotaCount(Request request, Response response) throws MailboxException {
        Optional<QuotaCount> maxCountQuota = globalQuotaService.getMaxCountQuota();
        if (maxCountQuota.isPresent()) {
            return maxCountQuota.get();
        }
        response.status(HttpStatus.NO_CONTENT_204);
        return null;
    }
}
