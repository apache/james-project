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

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.rrt.api.LoopDetectedException;
import org.apache.james.rrt.api.MappingAlreadyExistsException;
import org.apache.james.rrt.api.MappingConflictException;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.api.SourceDomainIsNotInDomainListException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;

import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Service;

public class GroupsRoutes implements Routes {

    public static final String ROOT_PATH = "address/groups";

    private static final String GROUP_ADDRESS = "groupAddress";
    private static final String GROUP_ADDRESS_PATH = ROOT_PATH + SEPARATOR + ":" + GROUP_ADDRESS;
    private static final String USER_ADDRESS = "userAddress";
    private static final String USER_IN_GROUP_ADDRESS_PATH = GROUP_ADDRESS_PATH + SEPARATOR + ":" + USER_ADDRESS;
    private static final String GROUP_ADDRESS_TYPE = "group";
    private static final String USER_ADDRESS_TYPE = "group member";

    private final JsonTransformer jsonTransformer;
    private final RecipientRewriteTable recipientRewriteTable;

    @Inject
    @VisibleForTesting
    GroupsRoutes(RecipientRewriteTable recipientRewriteTable, JsonTransformer jsonTransformer) {
        this.jsonTransformer = jsonTransformer;
        this.recipientRewriteTable = recipientRewriteTable;
    }

    @Override
    public String getBasePath() {
        return ROOT_PATH;
    }

    @Override
    public void define(Service service) {
        service.get(ROOT_PATH, this::listGroups, jsonTransformer);
        service.get(GROUP_ADDRESS_PATH, this::listGroupMembers, jsonTransformer);
        service.put(GROUP_ADDRESS_PATH, (request, response) -> halt(HttpStatus.BAD_REQUEST_400));
        service.put(USER_IN_GROUP_ADDRESS_PATH, this::addToGroup);
        service.delete(GROUP_ADDRESS_PATH, (request, response) -> halt(HttpStatus.BAD_REQUEST_400));
        service.delete(USER_IN_GROUP_ADDRESS_PATH, this::removeFromGroup);
    }

    public List<MappingSource> listGroups(Request request, Response response) throws RecipientRewriteTableException {
        return recipientRewriteTable.getSourcesForType(Mapping.Type.Group).collect(ImmutableList.toImmutableList());
    }

    public HaltException addToGroup(Request request, Response response) {
        MailAddress groupAddress = MailAddressParser.parseMailAddress(request.params(GROUP_ADDRESS), GROUP_ADDRESS_TYPE);
        Domain domain = groupAddress.getDomain();
        MailAddress userAddress = MailAddressParser.parseMailAddress(request.params(USER_ADDRESS), USER_ADDRESS_TYPE);
        MappingSource source = MappingSource.fromUser(Username.fromLocalPartWithDomain(groupAddress.getLocalPart(), domain));
        addGroupMember(source, userAddress);
        return halt(HttpStatus.NO_CONTENT_204);
    }

    private void addGroupMember(MappingSource source, MailAddress userAddress) {
        try {
            recipientRewriteTable.addGroupMapping(source, userAddress.asString());
        } catch (MappingAlreadyExistsException e) {
            // do nothing
        } catch (MappingConflictException | LoopDetectedException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.CONFLICT_409)
                .type(ErrorType.WRONG_STATE)
                .message(e.getMessage())
                .haltError();
        } catch (SourceDomainIsNotInDomainListException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message(e.getMessage())
                .haltError();
        } catch (RecipientRewriteTableException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .type(ErrorType.SERVER_ERROR)
                .message(e.getMessage())
                .haltError();
        }
    }

    public HaltException removeFromGroup(Request request, Response response) throws RecipientRewriteTableException {
        MailAddress groupAddress = MailAddressParser.parseMailAddress(request.params(GROUP_ADDRESS), GROUP_ADDRESS_TYPE);
        MailAddress userAddress = MailAddressParser.parseMailAddress(request.params(USER_ADDRESS), USER_ADDRESS_TYPE);
        MappingSource source = MappingSource
            .fromUser(
                Username.fromLocalPartWithDomain(groupAddress.getLocalPart(), groupAddress.getDomain()));
        recipientRewriteTable.removeGroupMapping(source, userAddress.asString());
        return halt(HttpStatus.NO_CONTENT_204);
    }

    public ImmutableSortedSet<String> listGroupMembers(Request request, Response response) throws RecipientRewriteTableException {
        MailAddress groupAddress = MailAddressParser.parseMailAddress(request.params(GROUP_ADDRESS), GROUP_ADDRESS_TYPE);
        Mappings mappings = recipientRewriteTable.getStoredMappings(MappingSource.fromMailAddress(groupAddress))
            .select(Mapping.Type.Group);

        ensureNonEmptyMappings(mappings);

        return mappings
                .asStream()
                .map(Mapping::asMailAddress)
                .flatMap(Optional::stream)
                .map(MailAddress::asString)
                .collect(ImmutableSortedSet.toImmutableSortedSet(String::compareTo));
    }

    private void ensureNonEmptyMappings(Mappings mappings) {
        if (mappings == null || mappings.isEmpty()) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("The group does not exist")
                .haltError();
        }
    }
}
