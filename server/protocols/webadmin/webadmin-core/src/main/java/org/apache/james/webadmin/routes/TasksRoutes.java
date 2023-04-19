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

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.json.DTOConverter;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.task.TaskNotFoundException;
import org.apache.james.task.TaskType;
import org.apache.james.util.DurationParser;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.DTOModuleInjections;
import org.apache.james.webadmin.dto.ExecutionDetailsDto;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.base.Preconditions;

import spark.Request;
import spark.Response;
import spark.Service;

public class TasksRoutes implements Routes {
    private static final Duration MAXIMUM_AWAIT_TIMEOUT = Duration.ofDays(365);
    public static final String BASE = "/tasks";

    interface TaskListTransformation extends Function<Stream<TaskExecutionDetails>, Stream<TaskExecutionDetails>> {

    }

    static class TypeTaskListTransformation implements TaskListTransformation {
        private final TaskType taskType;

        TypeTaskListTransformation(TaskType taskType) {
            this.taskType = taskType;
        }

        @Override
        public Stream<TaskExecutionDetails> apply(Stream<TaskExecutionDetails> stream) {
            return stream.filter(taskExecutionDetails -> taskExecutionDetails.getType().equals(taskType));
        }
    }

    static class SubmittedBeforeTaskListTransformation implements TaskListTransformation {
        private final ZonedDateTime time;

        public SubmittedBeforeTaskListTransformation(ZonedDateTime time) {
            this.time = time;
        }

        @Override
        public Stream<TaskExecutionDetails> apply(Stream<TaskExecutionDetails> stream) {
            return stream.filter(taskExecutionDetails -> taskExecutionDetails.getSubmittedDate().isBefore(time));
        }
    }

    static class StartedBeforeTaskListTransformation implements TaskListTransformation {
        private final ZonedDateTime time;

        public StartedBeforeTaskListTransformation(ZonedDateTime time) {
            this.time = time;
        }

        @Override
        public Stream<TaskExecutionDetails> apply(Stream<TaskExecutionDetails> stream) {
            return stream.filter(taskExecutionDetails -> taskExecutionDetails.getStartedDate()
                .map(started -> started.isBefore(time))
                .orElse(false));
        }
    }

    static class FailedBeforeTaskListTransformation implements TaskListTransformation {
        private final ZonedDateTime time;

        public FailedBeforeTaskListTransformation(ZonedDateTime time) {
            this.time = time;
        }

        @Override
        public Stream<TaskExecutionDetails> apply(Stream<TaskExecutionDetails> stream) {
            return stream.filter(taskExecutionDetails -> taskExecutionDetails.getFailedDate()
                .map(started -> started.isBefore(time))
                .orElse(false));
        }
    }

    static class CompletedBeforeTaskListTransformation implements TaskListTransformation {
        private final ZonedDateTime time;

        public CompletedBeforeTaskListTransformation(ZonedDateTime time) {
            this.time = time;
        }

        @Override
        public Stream<TaskExecutionDetails> apply(Stream<TaskExecutionDetails> stream) {
            return stream.filter(taskExecutionDetails -> taskExecutionDetails.getCompletedDate()
                .map(started -> started.isBefore(time))
                .orElse(false));
        }
    }

    static class SubmittedAfterTaskListTransformation implements TaskListTransformation {
        private final ZonedDateTime time;

        public SubmittedAfterTaskListTransformation(ZonedDateTime time) {
            this.time = time;
        }

        @Override
        public Stream<TaskExecutionDetails> apply(Stream<TaskExecutionDetails> stream) {
            return stream.filter(taskExecutionDetails -> taskExecutionDetails.getSubmittedDate().isAfter(time));
        }
    }

    static class StartedAfterTaskListTransformation implements TaskListTransformation {
        private final ZonedDateTime time;

        public StartedAfterTaskListTransformation(ZonedDateTime time) {
            this.time = time;
        }

        @Override
        public Stream<TaskExecutionDetails> apply(Stream<TaskExecutionDetails> stream) {
            return stream.filter(taskExecutionDetails -> taskExecutionDetails.getStartedDate()
                .map(started -> started.isAfter(time))
                .orElse(false));
        }
    }

    static class FailedAfterTaskListTransformation implements TaskListTransformation {
        private final ZonedDateTime time;

        public FailedAfterTaskListTransformation(ZonedDateTime time) {
            this.time = time;
        }

        @Override
        public Stream<TaskExecutionDetails> apply(Stream<TaskExecutionDetails> stream) {
            return stream.filter(taskExecutionDetails -> taskExecutionDetails.getFailedDate()
                .map(started -> started.isAfter(time))
                .orElse(false));
        }
    }

    static class CompletedAfterTaskListTransformation implements TaskListTransformation {
        private final ZonedDateTime time;

        public CompletedAfterTaskListTransformation(ZonedDateTime time) {
            this.time = time;
        }

        @Override
        public Stream<TaskExecutionDetails> apply(Stream<TaskExecutionDetails> stream) {
            return stream.filter(taskExecutionDetails -> taskExecutionDetails.getCompletedDate()
                .map(started -> started.isAfter(time))
                .orElse(false));
        }
    }

    static class OffsetTaskListTransformation implements TaskListTransformation {
        private final int offset;

        OffsetTaskListTransformation(int offset) {
            Preconditions.checkArgument(offset >= 0, "'offset' should be positive");
            this.offset = offset;
        }

        @Override
        public Stream<TaskExecutionDetails> apply(Stream<TaskExecutionDetails> stream) {
            return stream.skip(offset);
        }
    }

    static class LimitTaskListTransformation implements TaskListTransformation {
        private final int limit;

        LimitTaskListTransformation(int limit) {
            Preconditions.checkArgument(limit >= 0, "'limit' should be positive");
            this.limit = limit;
        }

        @Override
        public Stream<TaskExecutionDetails> apply(Stream<TaskExecutionDetails> stream) {
            return stream.limit(limit);
        }
    }

    private final TaskManager taskManager;
    private final JsonTransformer jsonTransformer;
    private final DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationDTOConverter;

    @Inject
    public TasksRoutes(TaskManager taskManager, JsonTransformer jsonTransformer,
                       @Named(DTOModuleInjections.WEBADMIN_DTO) DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationDTOConverter) {
        this.taskManager = taskManager;
        this.jsonTransformer = jsonTransformer;
        this.additionalInformationDTOConverter = additionalInformationDTOConverter;
    }

    @Override
    public String getBasePath() {
        return BASE;
    }

    @Override
    public void define(Service service) {
        service.get(BASE + "/:id", this::getStatus, jsonTransformer);

        service.get(BASE + "/:id/await", this::await, jsonTransformer);

        service.delete(BASE + "/:id", this::cancel, jsonTransformer);

        service.get(BASE, this::list, jsonTransformer);
    }

    public Object list(Request req, Response response) {
        try {
            return ExecutionDetailsDto.from(additionalInformationDTOConverter, listTasks(req));
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .cause(e)
                .message("Invalid status query parameter")
                .haltError();
        }
    }

    private Stream<TaskExecutionDetails> listTasks(Request req) {
        Stream<TaskExecutionDetails> stream = Optional.ofNullable(req.queryParams("status"))
            .map(TaskManager.Status::fromString)
            .map(taskManager::list)
            .orElse(taskManager.list())
            .stream()
            .sorted(Comparator.comparing(TaskExecutionDetails::getSubmittedDate).reversed());

        return taskListTransformations(req)
            .reduce(stream, (s, tc) -> tc.apply(s), Stream::concat);
    }

    Stream<TaskListTransformation> taskListTransformations(Request req) {
        return Stream.of(Optional.ofNullable(req.queryParams("type")).map(TaskType::of).map(TypeTaskListTransformation::new),
            Optional.ofNullable(req.queryParams("failedBefore")).map(this::parseDate).map(FailedBeforeTaskListTransformation::new),
            Optional.ofNullable(req.queryParams("failedAfter")).map(this::parseDate).map(FailedAfterTaskListTransformation::new),
            Optional.ofNullable(req.queryParams("completedBefore")).map(this::parseDate).map(CompletedBeforeTaskListTransformation::new),
            Optional.ofNullable(req.queryParams("completedAfter")).map(this::parseDate).map(CompletedAfterTaskListTransformation::new),
            Optional.ofNullable(req.queryParams("startedBefore")).map(this::parseDate).map(StartedBeforeTaskListTransformation::new),
            Optional.ofNullable(req.queryParams("startedAfter")).map(this::parseDate).map(StartedAfterTaskListTransformation::new),
            Optional.ofNullable(req.queryParams("submittedBefore")).map(this::parseDate).map(SubmittedBeforeTaskListTransformation::new),
            Optional.ofNullable(req.queryParams("submittedAfter")).map(this::parseDate).map(SubmittedAfterTaskListTransformation::new),
            Optional.ofNullable(req.queryParams("offset")).map(Integer::valueOf).map(OffsetTaskListTransformation::new),
            Optional.ofNullable(req.queryParams("limit")).map(Integer::valueOf).map(LimitTaskListTransformation::new))
            .flatMap(Optional::stream);
    }

    private ZonedDateTime parseDate(String s) {
        try {
            return ZonedDateTime.parse(s);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Object getStatus(Request req, Response response) {
        TaskId taskId = getTaskId(req);
        return respondStatus(taskId,
            () -> taskManager.getExecutionDetails(getTaskId(req)));
    }

    public Object await(Request req, Response response) {
        TaskId taskId = getTaskId(req);
        Duration timeout = getTimeout(req);
        return respondStatus(taskId,
            () -> awaitTask(taskId, timeout));
    }

    private Object respondStatus(TaskId taskId, Supplier<TaskExecutionDetails> executionDetailsSupplier) {
        try {
            TaskExecutionDetails executionDetails = executionDetailsSupplier.get();
            return ExecutionDetailsDto.from(additionalInformationDTOConverter, executionDetails);
        } catch (TaskNotFoundException e) {
            throw ErrorResponder.builder()
                .message("%s can not be found", taskId.getValue())
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.NOT_FOUND)
                .haltError();
        }
    }

    public Object cancel(Request req, Response response) {
        TaskId taskId = getTaskId(req);
        taskManager.cancel(taskId);
        return Responses.returnNoContent(response);
    }

    private TaskId getTaskId(Request req) {
        try {
            String id = req.params("id");
            return TaskId.fromString(id);
        } catch (Exception e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .cause(e)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid task id")
                .haltError();
        }
    }

    private Duration getTimeout(Request req) {
        try {
            Duration timeout =  Optional.ofNullable(req.queryParams("timeout"))
                .filter(Predicate.not(String::isEmpty))
                .map(rawString -> DurationParser.parse(rawString, ChronoUnit.SECONDS))
                .orElse(MAXIMUM_AWAIT_TIMEOUT);

            assertDoesNotExceedMaximumTimeout(timeout);
            return timeout;
        } catch (Exception e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .cause(e)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid timeout")
                .haltError();
        }
    }

    private void assertDoesNotExceedMaximumTimeout(Duration timeout) {
        Preconditions.checkState(timeout.compareTo(MAXIMUM_AWAIT_TIMEOUT) <= 0, "Timeout should not exceed 365 days");
    }

    private TaskExecutionDetails awaitTask(TaskId taskId, Duration timeout) {
        try {
            return taskManager.await(taskId, timeout);
        } catch (TaskManager.ReachedTimeoutException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.REQUEST_TIMEOUT_408)
                .type(ErrorResponder.ErrorType.SERVER_ERROR)
                .message("The timeout has been reached")
                .haltError();
        } catch (TaskNotFoundException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.SERVER_ERROR)
                .message("The taskId is not found")
                .haltError();
        }
    }
}
