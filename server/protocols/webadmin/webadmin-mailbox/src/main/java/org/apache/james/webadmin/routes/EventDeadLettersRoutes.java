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

import static org.apache.james.webadmin.service.EventDeadLettersRedeliverService.RunningOptions;

import jakarta.inject.Inject;

import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.EventSerializer;
import org.apache.james.events.Group;
import org.apache.james.task.TaskManager;
import org.apache.james.util.streams.Limit;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.service.EventDeadLettersService;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.ParametersExtractor;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;

import spark.Request;
import spark.Response;
import spark.Route;
import spark.Service;

public class EventDeadLettersRoutes implements Routes {

    public static final String BASE_PATH = "/events/deadLetter";
    private static final String GROUP_PARAM = ":group";
    private static final String INSERTION_ID_PARAMETER = ":insertionId";
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
        service.delete(BASE_PATH + "/groups/" + GROUP_PARAM, this::deleteEventsOfAGroup);
        service.post(BASE_PATH + "/groups/" + GROUP_PARAM + "/" + INSERTION_ID_PARAMETER, performActionOnSingleEvent(), jsonTransformer);
    }

    public Route performActionOnAllEvents() {
        return TaskFromRequestRegistry.of(RE_DELIVER, request -> eventDeadLettersService.redeliverAllEvents(parseRunningOptions(request)))
            .asRoute(taskManager);
    }

    private Iterable<String> listGroups(Request request, Response response) {
        return eventDeadLettersService.listGroupsAsStrings();
    }

    private Iterable<String> listFailedEvents(Request request, Response response) {
        Group group = parseGroup(request);
        return eventDeadLettersService.listGroupsInsertionIdsAsStrings(group);
    }

    public Route performActionOnGroupEvents() {
        return TaskFromRequestRegistry.of(RE_DELIVER, request -> eventDeadLettersService.redeliverGroupEvents(parseGroup(request), parseRunningOptions(request)))
            .asRoute(taskManager);
    }

    private String getEventDetails(Request request, Response response) {
        Group group = parseGroup(request);
        EventDeadLetters.InsertionId insertionId = parseInsertionId(request);

        return eventDeadLettersService.getEvent(group, insertionId)
            .map(eventSerializer::toJson)
            .block();
    }

    private String deleteEvent(Request request, Response response) {
        Group group = parseGroup(request);
        EventDeadLetters.InsertionId insertionId = parseInsertionId(request);

        eventDeadLettersService.deleteEvent(group, insertionId);
        return Responses.returnNoContent(response);
    }

    private String deleteEventsOfAGroup(Request request, Response response) {
        Group group = parseGroup(request);

        eventDeadLettersService.deleteEvents(group);
        return Responses.returnNoContent(response);
    }

    public Route performActionOnSingleEvent() {
        return TaskFromRequestRegistry.of(RE_DELIVER,
                request -> eventDeadLettersService.redeliverSingleEvent(parseGroup(request), parseInsertionId(request)))
            .asRoute(taskManager);
    }

    private Group parseGroup(Request request) {
        String groupAsString = request.params(GROUP_PARAM);
        try {
            return Group.deserialize(groupAsString);
        } catch (Group.GroupDeserializationException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .message("Can not deserialize the supplied group: %s", groupAsString)
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
                .message("Can not deserialize the supplied insertionId: %s", insertionIdAsString)
                .cause(e)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .haltError();
        }
    }

    private RunningOptions parseRunningOptions(Request request) {
        return ParametersExtractor.extractPositiveInteger(request, "limit")
            .map(limit -> new RunningOptions(Limit.from(limit)))
            .orElse(RunningOptions.DEFAULT);
    }
}
