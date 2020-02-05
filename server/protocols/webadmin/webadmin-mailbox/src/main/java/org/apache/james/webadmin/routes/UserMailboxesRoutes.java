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

import javax.inject.Inject;
import javax.mail.internet.MimeMessage;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.server.core.MailImpl;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.service.UserMailboxesService;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.MailboxHaveChildrenException;
import org.apache.james.webadmin.utils.ParametersExtractor;
import org.apache.james.webadmin.utils.Responses;
import org.apache.james.webadmin.validation.MailboxName;
import org.apache.mailet.Mail;
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

@Api(tags = "User's Mailbox")
@Path("/users/{username}/mailboxes")
@Produces("application/json")
public class UserMailboxesRoutes implements Routes {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserMailboxesRoutes.class);

    public static final String MAILBOX_NAME = ":mailboxName";
    public static final String MAILBOXES = "mailboxes";
    public static final String MAILS = "mails";
    public static final String MAIL_ID = ":mailID";
    private static final String USER_NAME = ":userName";
    public static final String USERS_BASE = "/users";
    public static final String USER_MAILBOXES_BASE = USERS_BASE + Constants.SEPARATOR + USER_NAME + Constants.SEPARATOR
            + MAILBOXES;
    public static final String SPECIFIC_MAILBOX = USER_MAILBOXES_BASE + Constants.SEPARATOR + MAILBOX_NAME;
    public static final String MAILBOX_MAILS = SPECIFIC_MAILBOX + Constants.SEPARATOR + MAILS;
    public static final String SPECIFIC_MAIL = MAILBOX_MAILS + Constants.SEPARATOR + MAIL_ID;

    private final UserMailboxesService userMailboxesService;
    private final JsonTransformer jsonTransformer;
    private Service service;

    @Inject
    public UserMailboxesRoutes(UserMailboxesService userMailboxesService, JsonTransformer jsonTransformer) {
        this.userMailboxesService = userMailboxesService;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return USER_MAILBOXES_BASE;
    }

    @Override
    public void define(Service service) {
        this.service = service;

        defineMailboxExists();

        defineGetUserMailboxes();

        defineCreateUserMailbox();

        defineDeleteUserMailbox();

        defineDeleteUserMailboxes();
    }

    @GET
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path") })
    @ApiOperation(value = "Listing all mailboxes of user.")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "The list of mailboxes", response = String.class),
            @ApiResponse(code = HttpStatus.UNAUTHORIZED_401, message = "Unauthorized. The user is not authenticated on the platform", response = String.class),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The user name does not exist."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.") })
    public void defineGetUserMailboxes() {
        service.get(USER_MAILBOXES_BASE, (request, response) -> {
            response.status(HttpStatus.OK_200);
            try {
                return userMailboxesService.listMailboxes(request.params(USER_NAME));
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid get on user mailboxes", e);
                throw ErrorResponder.builder().statusCode(HttpStatus.NOT_FOUND_404).type(ErrorType.INVALID_ARGUMENT)
                        .message("Invalid get on user mailboxes").cause(e).haltError();
            }
        }, jsonTransformer);
    }

    @DELETE
    @Path("/{mailboxName}")
    @ApiImplicitParams({ @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path"),
            @ApiImplicitParam(required = true, dataType = "string", name = "mailboxName", paramType = "path") })
    @ApiOperation(value = "Deleting a mailbox and its children")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "The mailbox now does not exist on the server", response = String.class),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid mailbox name"),
            @ApiResponse(code = HttpStatus.UNAUTHORIZED_401, message = "Unauthorized. The user is not authenticated on the platform"),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The user name does not exist."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.") })
    public void defineDeleteUserMailbox() {
        service.delete(SPECIFIC_MAILBOX, (request, response) -> {
            try {
                userMailboxesService.deleteMailbox(request.params(USER_NAME),
                        new MailboxName(request.params(MAILBOX_NAME)));
                return Responses.returnNoContent(response);
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid delete on user mailbox", e);
                throw ErrorResponder.builder().statusCode(HttpStatus.NOT_FOUND_404).type(ErrorType.INVALID_ARGUMENT)
                        .message("Invalid delete on user mailboxes").cause(e).haltError();
            } catch (MailboxHaveChildrenException e) {
                LOGGER.info("Attempt to delete a mailbox with children");
                throw ErrorResponder.builder().statusCode(HttpStatus.CONFLICT_409).type(ErrorType.INVALID_ARGUMENT)
                        .message("Attempt to delete a mailbox with children").cause(e).haltError();
            } catch (IllegalArgumentException e) {
                LOGGER.info("Attempt to create an invalid mailbox");
                throw ErrorResponder.builder().statusCode(HttpStatus.BAD_REQUEST_400).type(ErrorType.INVALID_ARGUMENT)
                        .message("Attempt to create an invalid mailbox").cause(e).haltError();
            }
        });
    }

    @DELETE
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path") })
    @ApiOperation(value = "Deleting user mailboxes.")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "The user does not have any mailbox", response = String.class),
            @ApiResponse(code = HttpStatus.UNAUTHORIZED_401, message = "Unauthorized. The user is not authenticated on the platform"),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The user name does not exist."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.") })
    public void defineDeleteUserMailboxes() {
        service.delete(USER_MAILBOXES_BASE, (request, response) -> {
            try {
                userMailboxesService.deleteMailboxes(request.params(USER_NAME));
                return Responses.returnNoContent(response);
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid delete on user mailboxes", e);
                throw ErrorResponder.builder().statusCode(HttpStatus.NOT_FOUND_404).type(ErrorType.INVALID_ARGUMENT)
                        .message("Invalid delete on user mailboxes").cause(e).haltError();
            }
        });
    }

    @GET
    @Path("/{mailboxName}")
    @ApiImplicitParams({ @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path"),
            @ApiImplicitParam(required = true, dataType = "string", name = "mailboxName", paramType = "path") })
    @ApiOperation(value = "Testing existence of a mailbox.")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "The mailbox exists", response = String.class),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid mailbox name"),
            @ApiResponse(code = HttpStatus.UNAUTHORIZED_401, message = "Unauthorized. The user is not authenticated on the platform"),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The user name does not exist."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.") })
    public void defineMailboxExists() {
        service.get(SPECIFIC_MAILBOX, (request, response) -> {
            try {
                if (userMailboxesService.testMailboxExists(request.params(USER_NAME),
                        new MailboxName(request.params(MAILBOX_NAME)))) {
                    return Responses.returnNoContent(response);
                } else {
                    throw ErrorResponder.builder().statusCode(HttpStatus.NOT_FOUND_404).type(ErrorType.INVALID_ARGUMENT)
                            .message("Invalid get on user mailboxes").haltError();
                }
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid get on user mailbox", e);
                throw ErrorResponder.builder().statusCode(HttpStatus.NOT_FOUND_404).type(ErrorType.INVALID_ARGUMENT)
                        .message("Invalid get on user mailboxes").cause(e).haltError();
            } catch (IllegalArgumentException e) {
                LOGGER.info("Attempt to create an invalid mailbox");
                throw ErrorResponder.builder().statusCode(HttpStatus.BAD_REQUEST_400).type(ErrorType.INVALID_ARGUMENT)
                        .message("Attempt to create an invalid mailbox").cause(e).haltError();
            }
        });
    }

    @PUT
    @Path("/{mailboxName}")
    @ApiOperation(value = "Create a mailbox of the selected user.", nickname = "CreateUserMailbox")
    @ApiImplicitParams({ @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path"),
            @ApiImplicitParam(required = true, dataType = "string", name = "mailboxName", paramType = "path") })
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK. The mailbox now exists on the server.", response = String.class),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid mailbox name"),
            @ApiResponse(code = HttpStatus.UNAUTHORIZED_401, message = "Unauthorized. The user is not authenticated on the platform"),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The user name does not exist."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.") })
    public void defineCreateUserMailbox() {
        service.put(SPECIFIC_MAILBOX, (request, response) -> {
            try {
                userMailboxesService.createMailbox(request.params(USER_NAME),
                        new MailboxName(request.params(MAILBOX_NAME)));
                return Responses.returnNoContent(response);
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid put on user mailbox", e);
                throw ErrorResponder.builder().statusCode(HttpStatus.NOT_FOUND_404).type(ErrorType.INVALID_ARGUMENT)
                        .message("Invalid get on user mailboxes").cause(e).haltError();
            } catch (IllegalArgumentException e) {
                LOGGER.info("Attempt to create an invalid mailbox");
                throw ErrorResponder.builder().statusCode(HttpStatus.BAD_REQUEST_400).type(ErrorType.INVALID_ARGUMENT)
                        .message("Attempt to create an invalid mailbox").cause(e).haltError();
            }
        });
    }

    // TODO: Same as below
    @GET
    @Path("/{mailboxName}/mails")
    @ApiOperation(value = "List mails from a specific Mailbox", nickname = "ReadMailboxMails")
    @ApiImplicitParams({ @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path"),
            @ApiImplicitParam(required = true, dataType = "string", name = "mailboxName", paramType = "path"),
            @ApiImplicitParam(required = false, name = "offset", paramType = "query parameter", dataType = "Integer", defaultValue = "0", example = "?offset=100", value = "If present, skips the given number of key in the output."),
            @ApiImplicitParam(required = false, paramType = "query parameter", name = "limit", dataType = "Integer", defaultValue = "25", example = "?limit=100", value = "If present, fixes the maximal number of key returned in that call. Must be more than zero if specified.") })
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.OK_200, message = "The list of all specific mails in a mailbox", response = List.class),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid mailbox name"),
            @ApiResponse(code = HttpStatus.UNAUTHORIZED_401, message = "Unauthorized. The user is not authenticated on the platform"),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The user name does not exist."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.") })
    public void defineGetEmailsInMailbox() {
        service.get(MAILBOX_MAILS, (request, response) -> {
            Long offset = Long.valueOf(ParametersExtractor.extractPositiveInteger(request, "offset").get());
            Long limit = Long.valueOf(ParametersExtractor.extractPositiveInteger(request, "limit").get());

            try {
                return userMailboxesService.listMailsFromMailbox(request.params(USER_NAME),
                        new MailboxName(request.params(MAILBOX_NAME)), offset, limit);
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid get on user mails in mailbox", e);
                throw ErrorResponder.builder().statusCode(HttpStatus.NOT_FOUND_404).type(ErrorType.INVALID_ARGUMENT)
                        .message("Invalid get on user mails in mailbox").cause(e).haltError();
            } catch (IllegalArgumentException e) {
                LOGGER.info("Attempt to list an invalid mailbox mails");
                throw ErrorResponder.builder().statusCode(HttpStatus.BAD_REQUEST_400).type(ErrorType.INVALID_ARGUMENT)
                        .message("Attempt to list an invalid mailbox mails").cause(e).haltError();
            } catch (Exception e) {
                LOGGER.info("Error occured while reading emails of mailbox in REST API");
                throw ErrorResponder.builder().statusCode(HttpStatus.BAD_REQUEST_400).type(ErrorType.SERVER_ERROR)
                        .message("Error occured while reading emails").cause(e).haltError();
            }
        }, jsonTransformer);
    }

    // TODO: Sending mail over MailDto because MessageResult can not be json
    @GET
    @Path("/{mailboxName}/mails/{mailID}")
    @ApiOperation(value = "Reads data of a specific mail", nickname = "ReadMailboxMail")
    @ApiImplicitParams({ @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path"),
            @ApiImplicitParam(required = true, dataType = "string", name = "mailboxName", paramType = "path"),
            @ApiImplicitParam(required = true, dataType = "string", name = "mailID", paramType = "path") })
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.OK_200, message = "A mail in the mailbox", response = MessageResult.class),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid mailbox name"),
            @ApiResponse(code = HttpStatus.UNAUTHORIZED_401, message = "Unauthorized. The user is not authenticated on the platform"),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The user name does not exist."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.") })
    public void defineReadEmail() {
        service.get(SPECIFIC_MAIL, (request, response) -> {
            MessageUid uid = MessageUid.of(Long.valueOf(request.params(MAIL_ID)));

            try {
                return userMailboxesService.getMail(request.params(USER_NAME),
                        new MailboxName(request.params(MAILBOX_NAME)), uid);
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid get on user mail", e);
                throw ErrorResponder.builder().statusCode(HttpStatus.NOT_FOUND_404).type(ErrorType.INVALID_ARGUMENT)
                        .message("Invalid get on user mail").cause(e).haltError();
            } catch (IllegalArgumentException e) {
                LOGGER.info("Attempt to read an invalid mail");
                throw ErrorResponder.builder().statusCode(HttpStatus.BAD_REQUEST_400).type(ErrorType.INVALID_ARGUMENT)
                        .message("Attempt to read an invalid mail").cause(e).haltError();
            } catch (Exception e) {
                LOGGER.info("Error occured while reading email in REST API mailbox");
                throw ErrorResponder.builder().statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                        .type(ErrorType.SERVER_ERROR).message("Error occured while reading email").cause(e).haltError();
            }
        }, jsonTransformer);
    }

    // TODO: To be implemented
    @DELETE
    @Path("/{mailboxName}/mails/{mailID}")
    @ApiOperation(value = "Reads data of a specific mail", nickname = "DeleteMailboxMail")
    @ApiImplicitParams({ @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path"),
            @ApiImplicitParam(required = true, dataType = "string", name = "mailboxName", paramType = "path"),
            @ApiImplicitParam(required = true, dataType = "string", name = "mailID", paramType = "path") })
    @ApiResponses(value = { @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "Email was deleted"),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid mailbox name"),
            @ApiResponse(code = HttpStatus.UNAUTHORIZED_401, message = "Unauthorized. The user is not authenticated on the platform"),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The user name does not exist."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.") })
    public void defineDeleteMail() {
        service.delete(SPECIFIC_MAIL, (request, response) -> {
            MessageUid uid = MessageUid.of(Long.valueOf(request.params(MAIL_ID)));

            try {
                userMailboxesService.deleteMail(request.params(USER_NAME),
                        new MailboxName(request.params(MAILBOX_NAME)), uid);
                return Responses.returnNoContent(response);
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid get on user mail", e);
                throw ErrorResponder.builder().statusCode(HttpStatus.NOT_FOUND_404).type(ErrorType.INVALID_ARGUMENT)
                        .message("Invalid get on user mail").cause(e).haltError();
            } catch (IllegalArgumentException e) {
                LOGGER.info("Attempt to read an invalid mail");
                throw ErrorResponder.builder().statusCode(HttpStatus.BAD_REQUEST_400).type(ErrorType.INVALID_ARGUMENT)
                        .message("Attempt to read an invalid mail").cause(e).haltError();
            } catch (Exception e) {
                LOGGER.info("Error occured while reading email in REST API mailbox");
                throw ErrorResponder.builder().statusCode(HttpStatus.BAD_REQUEST_400).type(ErrorType.SERVER_ERROR)
                        .message("Error occured while reading email").cause(e).haltError();
            }
        }, jsonTransformer);
    }

    @POST
    @ApiOperation(value = "Sends an email to the recipient", nickname = "SendMail")
    @ApiImplicitParams({ @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path"),
            @ApiImplicitParam(required = true, dataType = "string", name = "mailName", paramType = "body"),
            @ApiImplicitParam(required = true, dataType = "string", name = "mailText", paramType = "body"),
            @ApiImplicitParam(required = true, dataType = "string", name = "recipient", paramType = "body") })
    @ApiResponses(value = { @ApiResponse(code = HttpStatus.OK_200, message = "Email sent"),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid mailbox name"),
            @ApiResponse(code = HttpStatus.UNAUTHORIZED_401, message = "Unauthorized. The user is not authenticated on the platform"),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The user name does not exist."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.") })
    public void defineSendEMail() {
        service.post(USER_MAILBOXES_BASE, (request, response) -> {
            String mailText = request.params("mailText");
            String mailName = request.params("mailName");
            String recipient = request.params("recipient");
            String sender = request.params("username");

            MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder().setText(mailText).build();
            Mail mail = MailImpl.builder().name(mailName).mimeMessage(mimeMessage)
                    .addRecipient(new MailAddress(recipient)).sender(new MailAddress(sender)).build();

            try {
                userMailboxesService.sendMail(mail);
                return null;
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid get on user mail", e);
                throw ErrorResponder.builder().statusCode(HttpStatus.NOT_FOUND_404).type(ErrorType.INVALID_ARGUMENT)
                        .message("Invalid get on user mail").cause(e).haltError();
            } catch (IllegalArgumentException e) {
                LOGGER.info("Attempt to read an invalid mail");
                throw ErrorResponder.builder().statusCode(HttpStatus.BAD_REQUEST_400).type(ErrorType.INVALID_ARGUMENT)
                        .message("Attempt to read an invalid mail").cause(e).haltError();
            } catch (Exception e) {
                LOGGER.info("Error occured while reading email in REST API mailbox");
                throw ErrorResponder.builder().statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                        .type(ErrorType.SERVER_ERROR).message("Error occured while reading email").cause(e).haltError();
            }
        }, jsonTransformer);
    }
}
