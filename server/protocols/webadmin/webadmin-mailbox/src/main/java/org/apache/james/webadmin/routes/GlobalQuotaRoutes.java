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

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.QuotaDTO;
import org.apache.james.webadmin.dto.QuotaRequest;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import spark.Service;

@Api(tags = "GlobalQuota")
@Path(GlobalQuotaRoutes.QUOTA_ENDPOINT)
@Produces("application/json")
public class GlobalQuotaRoutes implements Routes {

    public static final String QUOTA_ENDPOINT = "/quota";
    public static final String COUNT_ENDPOINT = QUOTA_ENDPOINT + "/count";
    public static final String SIZE_ENDPOINT = QUOTA_ENDPOINT + "/size";
    private static final Logger LOGGER = LoggerFactory.getLogger(Routes.class);

    private final MaxQuotaManager maxQuotaManager;
    private final JsonTransformer jsonTransformer;
    private final JsonExtractor<QuotaDTO> jsonExtractor;
    private Service service;

    @Inject
    public GlobalQuotaRoutes(MaxQuotaManager maxQuotaManager, JsonTransformer jsonTransformer) {
        this.maxQuotaManager = maxQuotaManager;
        this.jsonTransformer = jsonTransformer;
        this.jsonExtractor = new JsonExtractor<>(QuotaDTO.class);
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
            @ApiImplicitParam(required = true, dataType = "org.apache.james.webadmin.dto.QuotaDTO", paramType = "body")
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
                maxQuotaManager.setDefaultMaxMessage(quotaDTO.getCount());
                maxQuotaManager.setDefaultMaxStorage(quotaDTO.getSize());
                response.status(HttpStatus.NO_CONTENT_204);
            } catch (JsonExtractException e) {
                LOGGER.info("Malformed JSON", e);
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Malformed JSON input")
                    .cause(e)
                    .haltError();
            } catch (IllegalArgumentException e) {
                LOGGER.info("Quota should be positive or unlimited (-1)", e);
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Quota should be positive or unlimited (-1)")
                    .cause(e)
                    .haltError();
            }
            return Constants.EMPTY_BODY;
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
        service.get(QUOTA_ENDPOINT, (request, response) -> {
            QuotaDTO quotaDTO = QuotaDTO.builder()
                .count(maxQuotaManager.getDefaultMaxMessage())
                .size(maxQuotaManager.getDefaultMaxStorage()).build();
            response.status(HttpStatus.OK_200);
            return quotaDTO;
        }, jsonTransformer);
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
            maxQuotaManager.setDefaultMaxStorage(Quota.UNLIMITED);
            response.status(HttpStatus.NO_CONTENT_204);
            return Constants.EMPTY_BODY;
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
            try {
                QuotaRequest quotaRequest = QuotaRequest.parse(request.body());
                maxQuotaManager.setDefaultMaxStorage(quotaRequest.getValue());
                response.status(HttpStatus.NO_CONTENT_204);
            } catch (IllegalArgumentException e) {
                LOGGER.info("Invalid quota. Need to be an integer value greater than 0");
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Invalid quota. Need to be an integer value greater than 0")
                    .cause(e)
                    .haltError();
            }
            return Constants.EMPTY_BODY;
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
        service.get(SIZE_ENDPOINT, (request, response) -> {
            long value = maxQuotaManager.getDefaultMaxStorage();
            response.status(HttpStatus.OK_200);
            return value;
        }, jsonTransformer);
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
            maxQuotaManager.setDefaultMaxMessage(Quota.UNLIMITED);
            response.status(HttpStatus.NO_CONTENT_204);
            return Constants.EMPTY_BODY;
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
            try {
                QuotaRequest quotaRequest = QuotaRequest.parse(request.body());
                maxQuotaManager.setDefaultMaxMessage(quotaRequest.getValue());
                response.status(HttpStatus.NO_CONTENT_204);
            } catch (IllegalArgumentException e) {
                LOGGER.info("Invalid quota. Need to be an integer value greater than 0");
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Invalid quota. Need to be an integer value greater than 0")
                    .cause(e)
                    .haltError();
            }
            return Constants.EMPTY_BODY;
        });
    }

    @GET
    @Path("/count")
    @ApiOperation(value = "Reading per quotaroot mail count limitation")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = Long.class),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineGetQuotaCount() {
        service.get(COUNT_ENDPOINT, (request, response) -> {
            long value = maxQuotaManager.getDefaultMaxMessage();
            response.status(HttpStatus.OK_200);
            return value;
        }, jsonTransformer);
    }
}
