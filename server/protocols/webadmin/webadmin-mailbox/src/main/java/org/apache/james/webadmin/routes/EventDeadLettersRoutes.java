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

import java.util.UUID;

import javax.inject.Inject;

import org.apache.james.event.json.EventSerializer;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.EventDeadLetters;
import org.apache.james.mailbox.events.Group;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.github.steveash.guavate.Guavate;

import spark.Request;
import spark.Response;
import spark.Service;

public class EventDeadLettersRoutes implements Routes {
    private static final String BASE_PATH = "/events/deadLetter";
    private static final String GROUP_PARAM = ":group";
    private static final String EVENT_ID_PARAM = ":eventId";

    private final EventDeadLetters deadLetters;
    private final JsonTransformer jsonTransformer;
    private final EventSerializer eventSerializer;

    @Inject
    EventDeadLettersRoutes(EventDeadLetters deadLetters, JsonTransformer jsonTransformer, EventSerializer eventSerializer) {
        this.deadLetters = deadLetters;
        this.jsonTransformer = jsonTransformer;
        this.eventSerializer = eventSerializer;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.get(BASE_PATH + "/groups", this::listGroups, jsonTransformer);
        service.get(BASE_PATH + "/groups/" + GROUP_PARAM + "/events", this::listFailedEvents, jsonTransformer);
        service.get(BASE_PATH + "/groups/" + GROUP_PARAM + "/events/" + EVENT_ID_PARAM, this::getEventDetails);
        service.delete(BASE_PATH + "/groups/" + GROUP_PARAM + "/events/" + EVENT_ID_PARAM, this::deleteEvent);
    }

    private Iterable<String> listGroups(Request request, Response response) {
        return deadLetters.groupsWithFailedEvents()
            .map(Group::asString)
            .collect(Guavate.toImmutableList())
            .block();
    }

    private Iterable<String> listFailedEvents(Request request, Response response) {
        Group group = parseGroup(request);
        return deadLetters.failedEventIds(group)
            .map(Event.EventId::getId)
            .map(UUID::toString)
            .collect(Guavate.toImmutableList())
            .block();
    }

    private String getEventDetails(Request request, Response response) {
        Group group = parseGroup(request);
        Event.EventId eventId = parseEventId(request);

        return deadLetters.failedEvent(group, eventId)
            .map(eventSerializer::toJson)
            .block();
    }

    private Response deleteEvent(Request request, Response response) {
        Group group = parseGroup(request);
        Event.EventId eventId = parseEventId(request);

        deadLetters.remove(group, eventId).block();
        response.status(HttpStatus.NO_CONTENT_204);
        return response;
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
