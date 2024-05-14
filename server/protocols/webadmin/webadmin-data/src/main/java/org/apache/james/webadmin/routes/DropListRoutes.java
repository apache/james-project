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

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.internet.AddressException;

import org.apache.commons.lang3.EnumUtils;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.droplists.api.DeniedEntityType;
import org.apache.james.droplists.api.DropList;
import org.apache.james.droplists.api.DropListEntry;
import org.apache.james.droplists.api.OwnerScope;
import org.apache.james.util.ReactorUtils;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import spark.Request;
import spark.Response;
import spark.Service;

public class DropListRoutes implements Routes {
    public static final String DROP_LIST = "/droplist";
    public static final String OWNER_SCOPE = ":ownerScope";
    public static final String OWNER = ":owner";
    public static final String DENIED_ENTITY = ":deniedEntity";
    public static final String DENIED_ENTITY_TYPE = "deniedEntityType";

    private final DropList dropList;
    private final JsonTransformer jsonTransformer;

    @Inject
    public DropListRoutes(DropList dropList, JsonTransformer jsonTransformer) {
        this.dropList = dropList;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return DROP_LIST;
    }

    @Override
    public void define(Service service) {
        service.get(DROP_LIST + SEPARATOR + OWNER_SCOPE, this::getDropList, jsonTransformer);
        service.get(DROP_LIST + SEPARATOR + OWNER_SCOPE + SEPARATOR + OWNER, this::getDropList, jsonTransformer);
        service.put(DROP_LIST + SEPARATOR + OWNER_SCOPE + SEPARATOR + OWNER + SEPARATOR + DENIED_ENTITY, this::addDropListEntry);
        service.put(DROP_LIST + SEPARATOR + OWNER_SCOPE + SEPARATOR + DENIED_ENTITY, this::addDropListEntry);
        service.head(DROP_LIST + SEPARATOR + OWNER_SCOPE + SEPARATOR + OWNER + SEPARATOR + DENIED_ENTITY, this::dropListEntryExist);
        service.head(DROP_LIST + SEPARATOR + OWNER_SCOPE + SEPARATOR + DENIED_ENTITY, this::dropListEntryExist);
        service.delete(DROP_LIST + SEPARATOR + OWNER_SCOPE + SEPARATOR + OWNER + SEPARATOR + DENIED_ENTITY, this::removeDropListEntry);
        service.delete(DROP_LIST + SEPARATOR + OWNER_SCOPE + SEPARATOR + DENIED_ENTITY, this::removeDropListEntry);
    }

    public ImmutableSet<String> getDropList(Request request, Response response) {
        OwnerScope ownerScope = checkValidOwnerScope(request.params(OWNER_SCOPE));
        String owner = Optional.ofNullable(request.params(OWNER)).orElse("");
        Optional<DeniedEntityType> deniedEntityType = checkValidDeniedEntityType(request.queryParams(DENIED_ENTITY_TYPE));
        if (deniedEntityType.isPresent()) {
            return dropList.list(ownerScope, owner)
                .filter(deniedEntry -> deniedEntry.getDeniedEntityType().equals(deniedEntityType.get()))
                .map(DropListEntry::getDeniedEntity)
                .collect(ImmutableSet.toImmutableSet())
                .block();
        } else {
            return dropList.list(ownerScope, owner)
                .map(DropListEntry::getDeniedEntity)
                .collect(ImmutableSet.toImmutableSet())
                .block();
        }
    }

    public String addDropListEntry(Request request, Response response) {
        OwnerScope ownerScope = checkValidOwnerScope(request.params(OWNER_SCOPE));
        String owner = Optional.ofNullable(request.params(OWNER)).orElse("");
        String deniedEntity = request.params(DENIED_ENTITY);
        DropListEntry dropListEntry = getDropListEntry(ownerScope, owner, deniedEntity);
        dropList.add(dropListEntry).block();
        return Responses.returnNoContent(response);
    }

    public String removeDropListEntry(Request request, Response response) {
        OwnerScope ownerScope = checkValidOwnerScope(request.params(OWNER_SCOPE));
        String owner = Optional.ofNullable(request.params(OWNER)).orElse("");
        String deniedEntity = request.params(DENIED_ENTITY);
        dropList.list(ownerScope, owner)
            .filter(dropListEntry -> dropListEntry.getDeniedEntity().equals(deniedEntity))
            .collectList()
            .doOnNext(this::deleteDropListEntry)
            .block();
        return Responses.returnNoContent(response);
    }

    public String dropListEntryExist(Request request, Response response) {
        OwnerScope ownerScope = checkValidOwnerScope(request.params(OWNER_SCOPE));
        String owner = Optional.ofNullable(request.params(OWNER)).orElse("");
        String deniedEntity = request.params(DENIED_ENTITY);
        boolean entryExists = dropList.list(ownerScope, owner)
            .any(dropListEntry -> dropListEntry.getDeniedEntity().equals(deniedEntity))
            .block();
        if (entryExists) {
            response.status(HttpStatus.NO_CONTENT_204);
        } else {
            response.status(HttpStatus.NOT_FOUND_404);
        }
        return Constants.EMPTY_BODY;
    }

    private Optional<DeniedEntityType> checkValidDeniedEntityType(String deniedEntityType) {
        try {
            if (deniedEntityType == null || deniedEntityType.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(DeniedEntityType.valueOf(deniedEntityType.toUpperCase()));
            }
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid DeniedEntityType")
                .cause(new IllegalArgumentException("DeniedEntityType '" + deniedEntityType + "' is invalid. Supported values are " +
                    EnumUtils.getEnumList(DeniedEntityType.class)))
                .haltError();
        }
    }

    private DropListEntry getDropListEntry(OwnerScope ownerScope, String owner, String deniedEntity) {
        DropListEntry.Builder dropListEntryBuilder = DropListEntry.builder();
        switch (ownerScope) {
            case GLOBAL -> dropListEntryBuilder = dropListEntryBuilder.forAll();
            case DOMAIN -> dropListEntryBuilder = dropListEntryBuilder.domainOwner(checkValidDomain(owner));
            case USER -> dropListEntryBuilder = dropListEntryBuilder.userOwner(checkValidMailAddress(owner));
        }
        if (deniedEntity.contains("@")) {
            dropListEntryBuilder.denyAddress(checkValidMailAddress(deniedEntity));
        } else {
            dropListEntryBuilder.denyDomain(checkValidDomain(deniedEntity));
        }
        return dropListEntryBuilder.build();
    }

    private OwnerScope checkValidOwnerScope(String ownerScope) {
        try {
            return OwnerScope.valueOf(ownerScope.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid OwnerScope")
                .cause(new IllegalArgumentException("OwnerScope '" + ownerScope + "' is invalid. Supported values are " +
                    EnumUtils.getEnumList(OwnerScope.class)))
                .haltError();
        }
    }

    private static MailAddress checkValidMailAddress(String address) {
        try {
            return new MailAddress(address);
        } catch (AddressException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid mail address %s", address)
                .cause(e)
                .haltError();
        }
    }

    private Domain checkValidDomain(String domainName) {
        try {
            return Domain.of(domainName);
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid domain %s", domainName)
                .cause(e)
                .haltError();
        }
    }

    private void deleteDropListEntry(List<DropListEntry> dropListEntries) {
        Flux.fromIterable(dropListEntries)
            .flatMap(dropList::remove, ReactorUtils.DEFAULT_CONCURRENCY)
            .then()
            .block();
    }
}