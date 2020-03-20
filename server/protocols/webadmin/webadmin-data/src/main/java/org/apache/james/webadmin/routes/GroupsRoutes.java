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

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.rrt.api.MappingAlreadyExistsException;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.api.SourceDomainIsNotInDomainListException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.util.OptionalUtils;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSortedSet;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Service;

@Api(tags = "Address Groups")
@Path(GroupsRoutes.ROOT_PATH)
@Produces(Constants.JSON_CONTENT_TYPE)
public class GroupsRoutes implements Routes {

    public static final String ROOT_PATH = "address/groups";

    private static final String GROUP_ADDRESS = "groupAddress";
    private static final String GROUP_ADDRESS_PATH = ROOT_PATH + SEPARATOR + ":" + GROUP_ADDRESS;
    private static final String USER_ADDRESS = "userAddress";
    private static final String USER_IN_GROUP_ADDRESS_PATH = GROUP_ADDRESS_PATH + SEPARATOR + ":" + USER_ADDRESS;
    private static final String MAILADDRESS_ASCII_DISCLAIMER = "Note that email addresses are restricted to ASCII character set. " +
        "Mail addresses not matching this criteria will be rejected.";
    private static final String GROUP_ADDRESS_TYPE = "group";
    private static final String USER_ADDRESS_TYPE = "group member";

    private final UsersRepository usersRepository;
    private final JsonTransformer jsonTransformer;
    private final RecipientRewriteTable recipientRewriteTable;

    @Inject
    @VisibleForTesting
    GroupsRoutes(RecipientRewriteTable recipientRewriteTable, UsersRepository usersRepository,
                 JsonTransformer jsonTransformer) {
        this.usersRepository = usersRepository;
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

    @GET
    @Path(ROOT_PATH)
    @ApiOperation(value = "getting groups list")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = List.class),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
            message = "Internal server error - Something went bad on the server side.")
    })
    public List<MappingSource> listGroups(Request request, Response response) throws RecipientRewriteTableException {
        return recipientRewriteTable.getSourcesForType(Mapping.Type.Group).collect(Guavate.toImmutableList());
    }

    @PUT
    @Path(ROOT_PATH + "/{" + GROUP_ADDRESS + "}/{" + USER_ADDRESS + "}")
    @ApiOperation(value = "adding a member into a group")
    @ApiImplicitParams({
        @ApiImplicitParam(required = true, dataType = "string", name = GROUP_ADDRESS, paramType = "path",
            value = "Mail address of the group. Sending a mail to that address will send it to all group members.\n" +
            MAILADDRESS_ASCII_DISCLAIMER),
        @ApiImplicitParam(required = true, dataType = "string", name = USER_ADDRESS, paramType = "path",
            value = "Mail address of the group. Sending a mail to the group mail address will send an email to " +
                "that email address (as well as other members).\n" +
                MAILADDRESS_ASCII_DISCLAIMER)
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK", response = List.class),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = GROUP_ADDRESS + " or " + USER_ADDRESS + " format is not valid"),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Domain in the source is not managed by the DomainList"),
        @ApiResponse(code = HttpStatus.CONFLICT_409, message = "requested group address is already used for another purpose"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
            message = "Internal server error - Something went bad on the server side.")
    })
    public HaltException addToGroup(Request request, Response response) throws RecipientRewriteTableException, UsersRepositoryException, DomainListException {
        MailAddress groupAddress = MailAddressParser.parseMailAddress(request.params(GROUP_ADDRESS), GROUP_ADDRESS_TYPE);
        Domain domain = groupAddress.getDomain();
        ensureNotShadowingAnotherAddress(groupAddress);
        MailAddress userAddress = MailAddressParser.parseMailAddress(request.params(USER_ADDRESS), USER_ADDRESS_TYPE);
        MappingSource source = MappingSource.fromUser(Username.fromLocalPartWithDomain(groupAddress.getLocalPart(), domain));
        addGroupMember(source, userAddress);
        return halt(HttpStatus.NO_CONTENT_204);
    }

    private void addGroupMember(MappingSource source, MailAddress userAddress) throws RecipientRewriteTableException {
        try {
            recipientRewriteTable.addGroupMapping(source, userAddress.asString());
        } catch (MappingAlreadyExistsException e) {
            // do nothing
        } catch (SourceDomainIsNotInDomainListException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message(e.getMessage())
                .haltError();
        }
    }

    private void ensureNotShadowingAnotherAddress(MailAddress groupAddress) throws UsersRepositoryException {
        if (usersRepository.contains(usersRepository.getUsername(groupAddress))) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.CONFLICT_409)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("Requested group address is already used for another purpose")
                .haltError();
        }
    }


    @DELETE
    @Path(ROOT_PATH + "/{" + GROUP_ADDRESS + "}/{" + USER_ADDRESS + "}")
    @ApiOperation(value = "remove a member from a group")
    @ApiImplicitParams({
        @ApiImplicitParam(required = true, dataType = "string", name = GROUP_ADDRESS, paramType = "path"),
        @ApiImplicitParam(required = true, dataType = "string", name = USER_ADDRESS, paramType = "path")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = List.class),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400,
            message = GROUP_ADDRESS + " or " + USER_ADDRESS + " format is not valid"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
            message = "Internal server error - Something went bad on the server side.")
    })
    public HaltException removeFromGroup(Request request, Response response) throws RecipientRewriteTableException {
        MailAddress groupAddress = MailAddressParser.parseMailAddress(request.params(GROUP_ADDRESS), GROUP_ADDRESS_TYPE);
        MailAddress userAddress = MailAddressParser.parseMailAddress(request.params(USER_ADDRESS), USER_ADDRESS_TYPE);
        MappingSource source = MappingSource
            .fromUser(
                Username.fromLocalPartWithDomain(groupAddress.getLocalPart(), groupAddress.getDomain()));
        recipientRewriteTable.removeGroupMapping(source, userAddress.asString());
        return halt(HttpStatus.NO_CONTENT_204);
    }

    @GET
    @Path(ROOT_PATH + "/{" + GROUP_ADDRESS + "}")
    @ApiOperation(value = "listing group members")
    @ApiImplicitParams({
        @ApiImplicitParam(required = true, dataType = "string", name = GROUP_ADDRESS, paramType = "path")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = List.class),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "The group is not an address"),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The group does not exist"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
            message = "Internal server error - Something went bad on the server side.")
    })
    public ImmutableSortedSet<String> listGroupMembers(Request request, Response response) throws RecipientRewriteTableException {
        MailAddress groupAddress = MailAddressParser.parseMailAddress(request.params(GROUP_ADDRESS), GROUP_ADDRESS_TYPE);
        Mappings mappings = recipientRewriteTable.getStoredMappings(MappingSource.fromMailAddress(groupAddress))
            .select(Mapping.Type.Group);

        ensureNonEmptyMappings(mappings);

        return mappings
                .asStream()
                .map(Mapping::asMailAddress)
                .flatMap(OptionalUtils::toStream)
                .map(MailAddress::asString)
                .collect(Guavate.toImmutableSortedSet());
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
