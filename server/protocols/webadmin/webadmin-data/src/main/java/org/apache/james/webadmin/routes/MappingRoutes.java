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

import static spark.Spark.halt;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.MappingsModule;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.fge.lambdas.Throwing;

import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Service;

public class MappingRoutes implements Routes {

    static final String BASE_PATH = "/mappings";
    static final String USER_MAPPING_PATH = "/mappings/user/";
    static final String SOURCE_MAPPING_PATH = "/mappings/sources/";
    static final String USER = "user";
    static final String SOURCE = "source";

    private final JsonTransformer jsonTransformer;
    private final JsonExtractor<Map<MappingSource, Mappings>> jsonExtractor;
    private final RecipientRewriteTable recipientRewriteTable;

    @Inject
    MappingRoutes(JsonTransformer jsonTransformer, RecipientRewriteTable recipientRewriteTable) {
        this.jsonTransformer = jsonTransformer;
        TypeReference<Map<MappingSource, Mappings>> typeRef = new TypeReference<>() {};
        this.jsonExtractor = new JsonExtractor<>(typeRef, new MappingsModule().asJacksonModule());
        this.recipientRewriteTable = recipientRewriteTable;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.get(BASE_PATH, this::getMappings, jsonTransformer);
        service.put(BASE_PATH, this::addMappings);
        service.get(USER_MAPPING_PATH + ":" + USER, this::getUserMappings, jsonTransformer);
        service.get(SOURCE_MAPPING_PATH + ":" + SOURCE, this::getMappingSources, jsonTransformer);
        service.delete(SOURCE_MAPPING_PATH + ":" + SOURCE, this::deleteMappingSources, jsonTransformer);
    }

    private Map<MappingSource, Mappings> getMappings(Request request, Response response) {
        try {
            return recipientRewriteTable.getAllMappings();
        } catch (RecipientRewriteTableException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .type(ErrorResponder.ErrorType.SERVER_ERROR)
                .message(e.getMessage())
                .haltError();
        }
    }

    public String addMappings(Request request, Response response) {
        try {
            Map<MappingSource, Mappings> mappings = jsonExtractor.parse(request.body());
            for (Map.Entry<MappingSource, Mappings> entry : mappings.entrySet()) {
                for (Mapping mapping : entry.getValue()) {
                    recipientRewriteTable.addMapping(entry.getKey(), mapping);
                }
            }
            return Responses.returnNoContent(response);
        } catch (JsonExtractException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("error parsing mappings")
                .cause(e)
                .haltError();
        } catch (RecipientRewriteTableException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("error adding mappings")
                .cause(e)
                .haltError();
        }
    }

    private Mappings getUserMappings(Request request, Response response) throws RecipientRewriteTableException {
        Username username = Username.of(request.params(USER).toLowerCase());
        return recipientRewriteTable.getStoredMappings(MappingSource.fromUser(username));
    }

    private List<String> getMappingSources(Request request, Response response) {
        Mapping mapping = Mapping.of(extractType(request), request.params(SOURCE));

        return recipientRewriteTable.listSourcesReactive(mapping)
            .map(MappingSource::asMailAddressString)
            .collectList()
            .block();
    }

    private HaltException deleteMappingSources(Request request, Response response) {
        Mapping mapping = Mapping.of(extractType(request), request.params(SOURCE));

        recipientRewriteTable.listSourcesReactive(mapping)
            .collectList()
            .block()
            .forEach(Throwing.consumer(source -> recipientRewriteTable.removeMapping(source, mapping)));

        return halt(HttpStatus.NO_CONTENT_204);
    }

    private static Mapping.Type extractType(Request request) {
        return Optional.ofNullable(request.queryParams("type")).map(name -> Arrays.stream(Mapping.Type.values())
                .filter(v -> v.name().equalsIgnoreCase(name))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("Invalid 'type' query parameter: " + name)))
            .orElseThrow(() -> new IllegalArgumentException("On reversed resolution 'type' is compulsory"));
    }
}
