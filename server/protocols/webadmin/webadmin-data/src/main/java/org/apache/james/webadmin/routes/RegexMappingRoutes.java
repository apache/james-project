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

import static org.apache.james.webadmin.Constants.SEPARATOR;
import static spark.Spark.halt;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.core.User;
import org.apache.james.rrt.api.InvalidRegexException;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.annotations.VisibleForTesting;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Service;

@Api(tags = "Regex Mapping")
@Path(RegexMappingRoutes.BASE_PATH)
@Produces(Constants.JSON_CONTENT_TYPE)
public class RegexMappingRoutes implements Routes {

    static final String BASE_PATH = "/mappings/regex";
    static final String MAPPING_SOURCE_PARAM = ":mappingSource";
    static final String REGEX_PARAM = ":regex";
    static final String ADD_ADDRESS_MAPPING_PATH = BASE_PATH + SEPARATOR
        + MAPPING_SOURCE_PARAM + "/targets/" + REGEX_PARAM;

    private final RecipientRewriteTable recipientRewriteTable;

    @Inject
    @VisibleForTesting
    RegexMappingRoutes(RecipientRewriteTable recipientRewriteTable) {
        this.recipientRewriteTable = recipientRewriteTable;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.post(ADD_ADDRESS_MAPPING_PATH, this::addRegexMapping);
    }

    @POST
    @Path(ADD_ADDRESS_MAPPING_PATH)
    @ApiOperation(value = "adding address-regex mappings to RecipientRewriteTable")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "Mapping successfully added"),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid parameter")
    })
    private HaltException addRegexMapping(Request request, Response response) throws Exception {
        try {
            MappingSource mappingSource = extractMappingSource(request);
            String regex = URLDecoder.decode(request.params(REGEX_PARAM), StandardCharsets.UTF_8.toString());
            recipientRewriteTable.addRegexMapping(mappingSource, regex);
        } catch (InvalidRegexException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message(e.getMessage())
                .haltError();
        }
        return halt(HttpStatus.NO_CONTENT_204);
    }

    private MappingSource extractMappingSource(Request request) {
        try {
            return MappingSource
                .fromUser(User.fromUsername(request.params(MAPPING_SOURCE_PARAM)));
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid `source` field.")
                .haltError();
        }
    }
}