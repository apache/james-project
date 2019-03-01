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
import java.util.Optional;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.Group;
import org.apache.james.task.Task;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.ActionEvents;
import org.apache.james.webadmin.dto.TaskIdDto;
import org.apache.james.webadmin.service.EventDeadLettersService;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
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
import spark.Service;

@Api(tags = "EventDeadLetter")
@Path("/events/deadLetter")
@Produces("application/json")
public class EventDeadLettersRoutes implements Routes {
    private static final String BASE_PATH = "/events/deadLetter";
    private static final String GROUP_PARAM = ":group";
    private static final String EVENT_ID_PARAM = ":eventId";

    private static final String INTERNAL_SERVER_ERROR = "Internal server error - Something went bad on the server side.";

    private final EventDeadLettersService eventDeadLettersService;
    private final TaskManager taskManager;
    private final JsonTransformer jsonTransformer;

    @Inject
    EventDeadLettersRoutes(EventDeadLettersService eventDeadLettersService, TaskManager taskManager, JsonTransformer jsonTransformer) {
        this.eventDeadLettersService = eventDeadLettersService;
        this.taskManager = taskManager;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.post(BASE_PATH, this::performActionOnAllEvents, jsonTransformer);
        service.get(BASE_PATH + "/groups", this::listGroups, jsonTransformer);
        service.get(BASE_PATH + "/groups/" + GROUP_PARAM, this::listFailedEvents, jsonTransformer);
        service.post(BASE_PATH + "/groups/" + GROUP_PARAM, this::performActionOnGroupEvents, jsonTransformer);
        service.get(BASE_PATH + "/groups/" + GROUP_PARAM + "/" + EVENT_ID_PARAM, this::getEventDetails);
        service.delete(BASE_PATH + "/groups/" + GROUP_PARAM + "/" + EVENT_ID_PARAM, this::deleteEvent);
        service.post(BASE_PATH + "/groups/" + GROUP_PARAM + "/" + EVENT_ID_PARAM, this::performActionOnSingleEvent, jsonTransformer);
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
    public TaskIdDto performActionOnAllEvents(Request request, Response response) {
        assertValidActionParameter(request);

        Task task = eventDeadLettersService.redeliverAllEvents();
        TaskId taskId = taskManager.submit(task);
        return TaskIdDto.respond(response, taskId);
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
        @ApiResponse(code = HttpStatus.OK_200, message = "OK - list of failed eventIds for a given group", response = List.class),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid group name"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = INTERNAL_SERVER_ERROR)
    })
    private Iterable<String> listFailedEvents(Request request, Response response) {
        Group group = parseGroup(request);
        return eventDeadLettersService.listGroupsEventIdsAsStrings(group);
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
    public TaskIdDto performActionOnGroupEvents(Request request, Response response) {
        Group group = parseGroup(request);
        assertValidActionParameter(request);

        Task task = eventDeadLettersService.redeliverGroupEvents(group);
        TaskId taskId = taskManager.submit(task);
        return TaskIdDto.respond(response, taskId);
    }

    @GET
    @Path("/groups/" + GROUP_PARAM + "/" + EVENT_ID_PARAM)
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
            name = "eventId",
            paramType = "path parameter",
            dataType = "String",
            defaultValue = "none",
            value = "Compulsory. Needs to be a valid eventId")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "OK - returns an event detail", response = Event.class),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid group name or event id"),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "No event with this eventId"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = INTERNAL_SERVER_ERROR)
    })
    private String getEventDetails(Request request, Response response) {
        Group group = parseGroup(request);
        Event.EventId eventId = parseEventId(request);

        return eventDeadLettersService.getSerializedEvent(group, eventId);
    }

    @DELETE
    @Path("/groups/" + GROUP_PARAM + "/" + EVENT_ID_PARAM)
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
            name = "eventId",
            paramType = "path parameter",
            dataType = "String",
            defaultValue = "none",
            value = "Compulsory. Needs to be a valid eventId")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK - Event deleted"),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid group name or event id"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = INTERNAL_SERVER_ERROR)
    })
    private Response deleteEvent(Request request, Response response) {
        Group group = parseGroup(request);
        Event.EventId eventId = parseEventId(request);

        eventDeadLettersService.deleteEvent(group, eventId);
        response.status(HttpStatus.NO_CONTENT_204);
        return response;
    }

    @POST
    @Path("/groups/" + GROUP_PARAM + "/" + EVENT_ID_PARAM)
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
            name = "eventId",
            paramType = "path parameter",
            dataType = "String",
            defaultValue = "none",
            value = "Compulsory. Needs to be a valid eventId"),
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
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid group name, event id or action argument"),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "No event with this eventId"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = INTERNAL_SERVER_ERROR)
    })
    public TaskIdDto performActionOnSingleEvent(Request request, Response response) {
        Group group = parseGroup(request);
        Event.EventId eventId = parseEventId(request);
        assertValidActionParameter(request);

        Task task = eventDeadLettersService.redeliverSingleEvent(group, eventId);
        TaskId taskId = taskManager.submit(task);
        return TaskIdDto.respond(response, taskId);
    }

    private void assertValidActionParameter(Request request) {
        String action = request.queryParams("action");
        Optional<ActionEvents> actionEvent = ActionEvents.find(action);

        if (!actionEvent.isPresent()) {
            throw new IllegalArgumentException(action + " is not a supported action");
        }
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

    private Event.EventId parseEventId(Request request) {
        String eventIdAsString = request.params(EVENT_ID_PARAM);
        try {
            return Event.EventId.of(eventIdAsString);
        } catch (Exception e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .message("Can not deserialize the supplied eventId: " + eventIdAsString)
                .cause(e)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .haltError();
        }
    }
}
