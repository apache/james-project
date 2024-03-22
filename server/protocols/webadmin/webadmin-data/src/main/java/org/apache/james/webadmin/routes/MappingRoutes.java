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

import java.util.List;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Username;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.MappingValueDTO;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;

import spark.Request;
import spark.Response;
import spark.Service;

public class MappingRoutes implements Routes {

    static final String BASE_PATH = "/mappings";
    static final String USER_MAPPING_PATH = "/mappings/user/";
    static final String USER = "user";

    private final JsonTransformer jsonTransformer;
    private final RecipientRewriteTable recipientRewriteTable;

    @Inject
    MappingRoutes(JsonTransformer jsonTransformer, RecipientRewriteTable recipientRewriteTable) {
        this.jsonTransformer = jsonTransformer;
        this.recipientRewriteTable = recipientRewriteTable;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.get(BASE_PATH, this::getMappings, jsonTransformer);
        service.get(USER_MAPPING_PATH + ":" + USER, this::getUserMappings, jsonTransformer);
    }

    private ImmutableListMultimap<String, MappingValueDTO> getMappings(Request request, Response response) {
        try {
            return recipientRewriteTable.getAllMappings()
                .entrySet()
                .stream()
                .flatMap(entry -> entry.getValue().asStream()
                    .map(mapping -> Pair.of(
                        entry.getKey().asString(),
                        MappingValueDTO.fromMapping(mapping))))
                .collect(ImmutableListMultimap.toImmutableListMultimap(Pair::getLeft, Pair::getRight));
        } catch (RecipientRewriteTableException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .type(ErrorResponder.ErrorType.SERVER_ERROR)
                .message(e.getMessage())
                .haltError();
        }
    }

    private List<MappingValueDTO> getUserMappings(Request request, Response response) throws RecipientRewriteTableException {
        Username username = Username.of(request.params(USER).toLowerCase());

        return recipientRewriteTable.getStoredMappings(MappingSource.fromUser(username))
            .asStream()
            .map(MappingValueDTO::fromMapping)
            .collect(ImmutableList.toImmutableList());
    }
}

