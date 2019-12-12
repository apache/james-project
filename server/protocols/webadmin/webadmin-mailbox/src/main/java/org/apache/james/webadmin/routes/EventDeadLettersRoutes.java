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
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.event.json.EventSerializer;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.EventDeadLetters;
import org.apache.james.mailbox.events.Group;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.service.EventDeadLettersService;
import org.apache.james.webadmin.tasks.TaskFactory;
import org.apache.james.webadmin.tasks.TaskIdDto;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.ResponseHeader;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Service;

@Api(tags = "EventDeadLetter")
@Path("/events/deadLetter")
@Produces("application/json")
public class EventDeadLettersRoutes implements Routes {
    public static final String BASE_PATH = "/events/deadLetter";
    private static final String GROUP_PARAM = ":group";
    private static final String INSERTION_ID_PARAMETER = ":insertionId";

    private static final String INTERNAL_SERVER_ERROR = "Internal server error - Something went bad on the server side.";
    private static final TaskRegistrationKey RE_DELIVER = TaskRegistrationKey.of("reDeliver");

    private final EventDeadLettersService eventDeadLettersService;
    private final EventSerializer eventSerializer;
    private final TaskManager taskManager;
    private final JsonTransformer jsonTransformer;

    @Inject
    EventDeadLettersRoutes(EventDeadLettersService eventDeadLettersService, EventSerializer eventSerializer,
                           TaskManager taskManager, JsonTransformer jsonTransformer) {
        this.eventDeadLettersService = eventDeadLettersService;
        this.eventSerializer = eventSerializer;
        this.taskManager = taskManager;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.post(BASE_PATH, performActionOnAllEvents(), jsonTransformer);
        service.get(BASE_PATH + "/groups", this::listGroups, jsonTransformer);
        service.get(BASE_PATH + "/groups/" + GROUP_PARAM, this::listFailedEvents, jsonTransformer);
        service.post(BASE_PATH + "/groups/" + GROUP_PARAM, performActionOnGroupEvents(), jsonTransformer);
        service.get(BASE_PATH + "/groups/" + GROUP_PARAM + "/" + INSERTION_ID_PARAMETER, this::getEventDetails);
        service.delete(BASE_PATH + "/groups/" + GROUP_PARAM + "/" + INSERTION_ID_PARAMETER, this::deleteEvent);
        service.post(BASE_PATH + "/groups/" + GROUP_PARAM + "/" + INSERTION_ID_PARAMETER, performActionOnSingleEvent(), jsonTransformer);
    }

    @POST
    @Path("")
    @ApiOperation(value = "Performing action on all events")
    @ApiImplicitParams({
        @ApiImplicitParam(
            required = true,
            dataType = "String",
            name = "action",
            paramType = "query",
            example = "?action=reDeliver",
            value = "Specify the action to perform on all events. 'reDeliver' is supported as an action, "
                + "and its purpose is to attempt a redelivery of all events present in dead letter."),
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.CREATED_201, message = "The taskId of the given scheduled task", response = TaskIdDto.class,
            responseHeaders = {
                @ResponseHeader(name = "Location", description = "URL of the resource associated with the scheduled task")
            }),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid action argument"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = INTERNAL_SERVER_ERROR)
    })
    public Route performActionOnAllEvents() {
        return TaskFactory.builder()
            .register(RE_DELIVER, request -> eventDeadLettersService.redeliverAllEvents())
            .buildAsRoute(taskManager);
    }

    @GET
    @Path("/groups")
    @ApiOperation(value = "List groups")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "OK - list group names", response = List.class),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = INTERNAL_SERVER_ERROR)
    })
    private Iterable<String> listGroups(Request request, Response response) {
        return eventDeadLettersService.listGroupsAsStrings();
    }

    @GET
    @Path("/groups/" + GROUP_PARAM)
    @ApiOperation(value = "List failed events for a given group")
    @ApiImplicitParams({
        @ApiImplicitParam(
            required = true,
            name = "group",
            paramType = "path parameter",
            dataType = "String",
            defaultValue = "none",
            value = "Compulsory. Needs to be a valid group name")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "OK - list of insertionIds of failed event for a given group", response = List.class),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid group name"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = INTERNAL_SERVER_ERROR)
    })
    private Iterable<String> listFailedEvents(Request request, Response response) {
        Group group = parseGroup(request);
        return eventDeadLettersService.listGroupsInsertionIdsAsStrings(group);
    }

    @POST
    @Path("/groups/" + GROUP_PARAM)
    @ApiOperation(value = "Performing action on events of a particular group")
    @ApiImplicitParams({
        @ApiImplicitParam(
            required = true,
            name = "group",
            paramType = "path parameter",
            dataType = "String",
            defaultValue = "none",
            value = "Compulsory. Needs to be a valid group name"),
        @ApiImplicitParam(
            required = true,
            dataType = "String",
            name = "action",
            paramType = "query",
            example = "?action=reDeliver",
            value = "Specify the action to perform on all events of a particular group. 'reDeliver' is supported as an action, "
                + "and its purpose is to attempt a redelivery of all events present in dead letter for the specified group."),
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.CREATED_201, message = "The taskId of the given scheduled task", response = TaskIdDto.class,
            responseHeaders = {
                @ResponseHeader(name = "Location", description = "URL of the resource associated with the scheduled task")
            }),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid group name or action argument"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = INTERNAL_SERVER_ERROR)
    })
    public Route performActionOnGroupEvents() {
        return TaskFactory.builder()
            .register(RE_DELIVER, request -> eventDeadLettersService.redeliverGroupEvents(parseGroup(request)))
            .buildAsRoute(taskManager);
    }

    @GET
    @Path("/groups/" + GROUP_PARAM + "/" + INSERTION_ID_PARAMETER)
    @ApiOperation(value = "Returns an event detail")
    @ApiImplicitParams({
        @ApiImplicitParam(
            required = true,
            name = "group",
            paramType = "path parameter",
            dataType = "String",
            defaultValue = "none",
            value = "Compulsory. Needs to be a valid group name"),
        @ApiImplicitParam(
            required = true,
            name = "insertionId",
            paramType = "path parameter",
            dataType = "String",
            defaultValue = "none",
            value = "Compulsory. Needs to be a valid insertionId")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "OK - returns an event detail", response = Event.class),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid group name or insertion id"),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "No event with this insertionId"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = INTERNAL_SERVER_ERROR)
    })
    private String getEventDetails(Request request, Response response) {
        Group group = parseGroup(request);
        EventDeadLetters.InsertionId insertionId = parseInsertionId(request);

        return eventDeadLettersService.getEvent(group, insertionId)
            .map(eventSerializer::toJson)
            .block();
    }

    @DELETE
    @Path("/groups/" + GROUP_PARAM + "/" + INSERTION_ID_PARAMETER)
    @ApiOperation(value = "Deletes an event")
    @ApiImplicitParams({
        @ApiImplicitParam(
            required = true,
            name = "group",
            paramType = "path parameter",
            dataType = "String",
            defaultValue = "none",
            value = "Compulsory. Needs to be a valid group name"),
        @ApiImplicitParam(
            required = true,
            name = "insertionId",
            paramType = "path parameter",
            dataType = "String",
            defaultValue = "none",
            value = "Compulsory. Needs to be a valid insertionId")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK - Event deleted"),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid group name or insertion id"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = INTERNAL_SERVER_ERROR)
    })
    private String deleteEvent(Request request, Response response) {
        Group group = parseGroup(request);
        EventDeadLetters.InsertionId insertionId = parseInsertionId(request);

        eventDeadLettersService.deleteEvent(group, insertionId);
        return Responses.returnNoContent(response);
    }

    @POST
    @Path("/groups/" + GROUP_PARAM + "/" + INSERTION_ID_PARAMETER)
    @ApiOperation(value = "Performing action on an event")
    @ApiImplicitParams({
        @ApiImplicitParam(
            required = true,
            name = "group",
            paramType = "path parameter",
            dataType = "String",
            defaultValue = "none",
            value = "Compulsory. Needs to be a valid group name"),
        @ApiImplicitParam(
            required = true,
            name = "insertionId",
            paramType = "path parameter",
            dataType = "String",
            defaultValue = "none",
            value = "Compulsory. Needs to be a valid insertionId"),
        @ApiImplicitParam(
            required = true,
            dataType = "String",
            name = "action",
            paramType = "query",
            example = "?action=reDeliver",
            value = "Specify the action to perform on an unique event. 'reDeliver' is supported as an action, "
                + "and its purpose is to attempt a redelivery of the specified event."),
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.CREATED_201, message = "The taskId of the given scheduled task", response = TaskIdDto.class,
            responseHeaders = {
                @ResponseHeader(name = "Location", description = "URL of the resource associated with the scheduled task")
            }),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid group name, insertion id or action argument"),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "No event with this insertionId"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = INTERNAL_SERVER_ERROR)
    })
    public Route performActionOnSingleEvent() {
        return TaskFactory.builder()
            .register(RE_DELIVER,
                request -> eventDeadLettersService.redeliverSingleEvent(parseGroup(request), parseInsertionId(request)))
            .buildAsRoute(taskManager);
    }

    private Group parseGroup(Request request) {
        String groupAsString = request.params(GROUP_PARAM);
        try {
            return Group.deserialize(groupAsString);
        } catch (Group.GroupDeserializationException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .message("Can not deserialize the supplied group: " + groupAsString)
                .cause(e)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .haltError();
        }
    }

    private EventDeadLetters.InsertionId parseInsertionId(Request request) {
        String insertionIdAsString = request.params(INSERTION_ID_PARAMETER);
        try {
            return EventDeadLetters.InsertionId.of(insertionIdAsString);
        } catch (Exception e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .message("Can not deserialize the supplied insertionId: " + insertionIdAsString)
                .cause(e)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .haltError();
        }
    }
}
