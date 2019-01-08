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

import javax.inject.Inject;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.core.MailAddress;
import org.apache.james.core.User;
import org.apache.james.rrt.api.MappingAlreadyExistsException;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.annotations.VisibleForTesting;
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

@Api(tags = "Address Aliases")
@Path(AliasRoutes.ROOT_PATH)
@Produces(Constants.JSON_CONTENT_TYPE)
public class AliasRoutes implements Routes {

    public static final String ROOT_PATH = "address/aliases";

    private static final String ALIAS_DESTINATION_ADDRESS = "aliasDestinationAddress";
    private static final String ALIAS_ADDRESS_PATH = ROOT_PATH + SEPARATOR + ":" + ALIAS_DESTINATION_ADDRESS;
    private static final String ALIAS_SOURCE_ADDRESS = "aliasSourceAddress";
    private static final String USER_IN_ALIAS_SOURCES_ADDRESSES_PATH = ALIAS_ADDRESS_PATH + SEPARATOR +
        "sources" + SEPARATOR + ":" + ALIAS_SOURCE_ADDRESS;
    private static final String MAILADDRESS_ASCII_DISCLAIMER = "Note that email addresses are restricted to ASCII character set. " +
        "Mail addresses not matching this criteria will be rejected.";
    private static final String ADDRESS_TYPE = "alias";

    private final UsersRepository usersRepository;
    private final RecipientRewriteTable recipientRewriteTable;

    @Inject
    @VisibleForTesting
    AliasRoutes(RecipientRewriteTable recipientRewriteTable, UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
        this.recipientRewriteTable = recipientRewriteTable;
    }

    @Override
    public String getBasePath() {
        return ROOT_PATH;
    }

    @Override
    public void define(Service service) {
        service.put(USER_IN_ALIAS_SOURCES_ADDRESSES_PATH, this::addToAliasSources);
    }

    @PUT
    @Path(ROOT_PATH + "/{" + ALIAS_DESTINATION_ADDRESS + "}/sources/{" + ALIAS_SOURCE_ADDRESS + "}")
    @ApiOperation(value = "adding a source address into an alias")
    @ApiImplicitParams({
        @ApiImplicitParam(required = true, dataType = "string", name = ALIAS_DESTINATION_ADDRESS, paramType = "path",
            value = "Destination mail address of the alias. Sending a mail to the alias source address will send it to " +
                "that email address.\n" +
                MAILADDRESS_ASCII_DISCLAIMER),
        @ApiImplicitParam(required = true, dataType = "string", name = ALIAS_SOURCE_ADDRESS, paramType = "path",
            value = "Source mail address of the alias. Sending a mail to that address will send it to " +
                "the email destination address.\n" +
                MAILADDRESS_ASCII_DISCLAIMER)
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK"),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = ALIAS_DESTINATION_ADDRESS + " or alias structure format is not valid"),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "The alias source exists as an user already"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
            message = "Internal server error - Something went bad on the server side.")
    })
    public HaltException addToAliasSources(Request request, Response response) throws UsersRepositoryException, RecipientRewriteTableException {
        MailAddress aliasSourceAddress = MailAddressParser.parseMailAddress(request.params(ALIAS_SOURCE_ADDRESS), ADDRESS_TYPE);
        ensureUserDoesNotExist(aliasSourceAddress);
        MailAddress destinationAddress = MailAddressParser.parseMailAddress(request.params(ALIAS_DESTINATION_ADDRESS), ADDRESS_TYPE);
        MappingSource source = MappingSource.fromUser(User.fromMailAddress(destinationAddress));
        addAlias(source, aliasSourceAddress);
        return halt(HttpStatus.NO_CONTENT_204);
    }

    private void addAlias(MappingSource source, MailAddress aliasSourceAddress) throws RecipientRewriteTableException {
        try {
            recipientRewriteTable.addAliasMapping(source, aliasSourceAddress.asString());
        } catch (MappingAlreadyExistsException e) {
            // ignore
        }
    }

    private void ensureUserDoesNotExist(MailAddress mailAddress) throws UsersRepositoryException {
        String username = usersRepository.getUser(mailAddress);

        if (usersRepository.contains(username)) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("The alias source exists as an user already")
                .haltError();
        }
    }
}
