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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;
import javax.mail.internet.AddressException;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.core.MailAddress;
import org.apache.james.core.User;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.util.OptionalUtils;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.ForwardDestinationResponse;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
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

@Api(tags = "Address Forwards")
@Path(ForwardRoutes.ROOT_PATH)
@Produces(Constants.JSON_CONTENT_TYPE)
public class ForwardRoutes implements Routes {

    public static final String ROOT_PATH = "address/forwards";

    private static final Logger LOGGER = LoggerFactory.getLogger(ForwardRoutes.class);

    private static final String FORWARD_BASE_ADDRESS = "forwardBaseAddress";
    private static final String FORWARD_ADDRESS_PATH = ROOT_PATH + SEPARATOR + ":" + FORWARD_BASE_ADDRESS;
    private static final String FORWARD_DESTINATION_ADDRESS = "forwardDestinationAddress";
    private static final String USER_IN_FORWARD_DESTINATION_ADDRESSES_PATH = FORWARD_ADDRESS_PATH + SEPARATOR +
        "targets" + SEPARATOR + ":" + FORWARD_DESTINATION_ADDRESS;
    private static final String MAILADDRESS_ASCII_DISCLAIMER = "Note that email addresses are restricted to ASCII character set. " +
        "Mail addresses not matching this criteria will be rejected.";

    private final UsersRepository usersRepository;
    private final JsonTransformer jsonTransformer;
    private final RecipientRewriteTable recipientRewriteTable;

    @Inject
    @VisibleForTesting
    ForwardRoutes(RecipientRewriteTable recipientRewriteTable, UsersRepository usersRepository, JsonTransformer jsonTransformer) {
        this.usersRepository = usersRepository;
        this.jsonTransformer = jsonTransformer;
        this.recipientRewriteTable = recipientRewriteTable;
    }

    @Override
    public void define(Service service) {
        service.get(ROOT_PATH, this::listForwards, jsonTransformer);
        service.get(FORWARD_ADDRESS_PATH, this::listForwardDestinations, jsonTransformer);
        service.put(FORWARD_ADDRESS_PATH, this::throwUnknownPath);
        service.put(USER_IN_FORWARD_DESTINATION_ADDRESSES_PATH, this::addToForwardDestinations);
        service.delete(FORWARD_ADDRESS_PATH, this::throwUnknownPath);
        service.delete(USER_IN_FORWARD_DESTINATION_ADDRESSES_PATH, this::removeFromForwardDestination);
    }

    public Object throwUnknownPath(Request request, Response response) {
        throw ErrorResponder.builder()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .type(ErrorType.INVALID_ARGUMENT)
            .message("A destination address needs to be specified in the path")
            .haltError();
    }

    @GET
    @Path(ROOT_PATH)
    @ApiOperation(value = "getting forwards list")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = List.class),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
            message = "Internal server error - Something went bad on the server side.")
    })
    public Set<String> listForwards(Request request, Response response) throws RecipientRewriteTableException {
        return Optional.ofNullable(recipientRewriteTable.getAllMappings())
            .map(mappings ->
                mappings.entrySet().stream()
                    .filter(e -> e.getValue().contains(Mapping.Type.Forward))
                    .map(Map.Entry::getKey)
                    .flatMap(source -> OptionalUtils.toStream(source.asMailAddress()))
                    .map(MailAddress::asString)
                    .collect(Guavate.toImmutableSortedSet()))
            .orElse(ImmutableSortedSet.of());
    }

    @PUT
    @Path(ROOT_PATH + "/{" + FORWARD_BASE_ADDRESS + "}/targets/{" + FORWARD_DESTINATION_ADDRESS + "}")
    @ApiOperation(value = "adding a destination address into a forward")
    @ApiImplicitParams({
        @ApiImplicitParam(required = true, dataType = "string", name = FORWARD_BASE_ADDRESS, paramType = "path",
            value = "Base mail address of the forward. Sending a mail to that address will send it to all forward destinations.\n" +
            MAILADDRESS_ASCII_DISCLAIMER),
        @ApiImplicitParam(required = true, dataType = "string", name = FORWARD_DESTINATION_ADDRESS, paramType = "path",
            value = "A destination mail address of the forward. Sending a mail to the base address will send an email to " +
                "that email address (as well as other destinations).\n" +
                MAILADDRESS_ASCII_DISCLAIMER)
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = List.class),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = FORWARD_BASE_ADDRESS + " or forward structure format is not valid"),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "requested base forward address does not match a user"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
            message = "Internal server error - Something went bad on the server side.")
    })
    public HaltException addToForwardDestinations(Request request, Response response) throws JsonExtractException, AddressException, RecipientRewriteTableException, UsersRepositoryException, DomainListException {
        MailAddress forwardBaseAddress = parseMailAddress(request.params(FORWARD_BASE_ADDRESS));
        ensureUserExist(forwardBaseAddress);
        MailAddress destinationAddress = parseMailAddress(request.params(FORWARD_DESTINATION_ADDRESS));
        MappingSource source = MappingSource.fromUser(User.fromLocalPartWithDomain(forwardBaseAddress.getLocalPart(), forwardBaseAddress.getDomain()));
        recipientRewriteTable.addForwardMapping(source, destinationAddress.asString());
        return halt(HttpStatus.CREATED_201);
    }

    private void ensureUserExist(MailAddress mailAddress) throws UsersRepositoryException {
        if (!usersRepository.contains(mailAddress.asString())) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("Requested base forward address does not correspond to a user")
                .haltError();
        }
    }


    @DELETE
    @Path(ROOT_PATH + "/{" + FORWARD_BASE_ADDRESS + "}/targets/{" + FORWARD_DESTINATION_ADDRESS + "}")
    @ApiOperation(value = "remove a destination address from a forward")
    @ApiImplicitParams({
        @ApiImplicitParam(required = true, dataType = "string", name = FORWARD_BASE_ADDRESS, paramType = "path"),
        @ApiImplicitParam(required = true, dataType = "string", name = FORWARD_DESTINATION_ADDRESS, paramType = "path")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = List.class),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400,
            message = FORWARD_BASE_ADDRESS + " or forward structure format is not valid"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
            message = "Internal server error - Something went bad on the server side.")
    })
    public HaltException removeFromForwardDestination(Request request, Response response) throws JsonExtractException, AddressException, RecipientRewriteTableException {
        MailAddress baseAddress = parseMailAddress(request.params(FORWARD_BASE_ADDRESS));
        MailAddress destinationAddressToBeRemoved = parseMailAddress(request.params(FORWARD_DESTINATION_ADDRESS));
        MappingSource source = MappingSource.fromUser(User.fromLocalPartWithDomain(baseAddress.getLocalPart(), baseAddress.getDomain()));
        recipientRewriteTable.removeForwardMapping(source, destinationAddressToBeRemoved.asString());
        return halt(HttpStatus.OK_200);
    }

    @GET
    @Path(ROOT_PATH + "/{" + FORWARD_BASE_ADDRESS + "}")
    @ApiOperation(value = "listing forward destinations")
    @ApiImplicitParams({
        @ApiImplicitParam(required = true, dataType = "string", name = FORWARD_BASE_ADDRESS, paramType = "path")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = List.class),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "The forward is not an address"),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The forward does not exist"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
            message = "Internal server error - Something went bad on the server side.")
    })
    public ImmutableSet<ForwardDestinationResponse> listForwardDestinations(Request request, Response response) throws RecipientRewriteTable.ErrorMappingException, RecipientRewriteTableException {
        MailAddress baseAddress = parseMailAddress(request.params(FORWARD_BASE_ADDRESS));
        Mappings mappings = recipientRewriteTable.getMappings(baseAddress.getLocalPart(), baseAddress.getDomain());

        ensureNonEmptyMappings(mappings);

        return mappings.select(Mapping.Type.Forward)
                .asStream()
                .map(mapping -> mapping.asMailAddress()
                        .orElseThrow(() -> new IllegalStateException(String.format("Can not compute address for mapping %s", mapping.asString()))))
                .map(MailAddress::asString)
                .sorted()
                .map(ForwardDestinationResponse::new)
                .collect(Guavate.toImmutableSet());
    }

    private MailAddress parseMailAddress(String address) {
        try {
            String decodedAddress = URLDecoder.decode(address, StandardCharsets.UTF_8.displayName());
            return new MailAddress(decodedAddress);
        } catch (AddressException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("The forward is not an email address")
                .cause(e)
                .haltError();
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("UTF-8 should be a valid encoding");
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .type(ErrorType.SERVER_ERROR)
                .message("Internal server error - Something went bad on the server side.")
                .cause(e)
                .haltError();
        }
    }

    private void ensureNonEmptyMappings(Mappings mappings) {
        if (mappings == null || mappings.isEmpty()) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("The forward does not exist")
                .haltError();
        }
    }
}
