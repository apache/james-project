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

package org.apache.james.protocols.webadmin;

import static org.apache.james.DisconnectorNotifier.AllUsersRequest.ALL_USERS_REQUEST;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.apache.james.DisconnectorNotifier;
import org.apache.james.DisconnectorNotifier.MultipleUserRequest;
import org.apache.james.core.ConnectionDescription;
import org.apache.james.core.ConnectionDescriptionSupplier;
import org.apache.james.core.Username;
import org.apache.james.protocols.lib.netty.CertificateReloadable;
import org.apache.james.util.Port;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;

import spark.Request;
import spark.Service;

public class ProtocolServerRoutes implements Routes {
    record ConnectionDescriptionDTO(
        String protocol,
        String endpoint,
        Optional<String> remoteAddress,
        Optional<Instant> connectionDate,
        boolean isActive,
        boolean isOpen,
        boolean isWritable,
        boolean isEncrypted,
        Optional<String> username,
        Map<String, String> protocolSpecificInformation) {

        static ConnectionDescriptionDTO from(ConnectionDescription domainObject) {
            return new ConnectionDescriptionDTO(domainObject.protocol(),
                domainObject.endpoint(),
                domainObject.remoteAddress(),
                domainObject.connectionDate(),
                domainObject.isActive(),
                domainObject.isOpen(),
                domainObject.isWritable(),
                domainObject.isEncrypted(),
                domainObject.username().map(Username::asString),
                domainObject.protocolSpecificInformation());
        }
    }

    public static final String SERVERS = "servers";
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final TypeReference<List<String>> LIST_OF_STRING = new TypeReference<>() {

    };

    static {
        OBJECT_MAPPER.registerModule(new Jdk8Module());
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
        OBJECT_MAPPER.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    private final Set<CertificateReloadable.Factory> servers;
    private final DisconnectorNotifier disconnectorNotifier;
    private final ConnectionDescriptionSupplier connectionDescriptionSupplier;

    @Inject
    public ProtocolServerRoutes(Set<CertificateReloadable.Factory> servers, DisconnectorNotifier disconnectorNotifier, ConnectionDescriptionSupplier connectionDescriptionSupplier) {
        this.servers = servers;
        this.disconnectorNotifier = disconnectorNotifier;
        this.connectionDescriptionSupplier = connectionDescriptionSupplier;
    }

    @Override
    public String getBasePath() {
        return SERVERS;
    }

    @Override
    public void define(Service service) {
        service.post(SERVERS, (request, response) -> {
            Preconditions.checkArgument(request.queryParams().contains("reload-certificate"),
                "'reload-certificate' query parameter shall be specified");

            if (noServerEnabled()) {
                return ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorResponder.ErrorType.NOT_FOUND)
                    .message("No servers configured, nothing to reload")
                    .haltError();
            }

            servers.stream()
                .flatMap(CertificateReloadable.Factory::certificatesReloadable)
                .filter(filters(request))
                .forEach(Throwing.consumer(CertificateReloadable::reloadSSLCertificate));

            return Responses.returnNoContent(response);
        });

        service.delete(SERVERS + "/channels/:user", (request, response) -> {
            Username username = Username.of(request.params("user"));
            disconnectorNotifier.disconnect(MultipleUserRequest.of(username));

            return Responses.returnNoContent(response);
        });

        service.delete(SERVERS + "/channels", (request, response) -> {
            String body = request.body();

            if (Strings.isNullOrEmpty(body)) {
                disconnectorNotifier.disconnect(ALL_USERS_REQUEST);
            } else {
                ImmutableSet<Username> userSet = OBJECT_MAPPER.readValue(body, LIST_OF_STRING)
                    .stream()
                    .map(Username::of)
                    .collect(ImmutableSet.toImmutableSet());
                disconnectorNotifier.disconnect(MultipleUserRequest.of(userSet));
            }

            return Responses.returnNoContent(response);
        });

        service.get(SERVERS + "/channels", (request, response) -> {
            ChannelsQueryParameters params = ChannelsQueryParameters.from(request);
            return OBJECT_MAPPER.writeValueAsString(params.apply(connectionDescriptionSupplier.describeConnections()
                .map(ConnectionDescriptionDTO::from))
                .toList());
        });

        service.get(SERVERS + "/channels/:user", (request, response) -> {
            Username username = Username.of(request.params("user"));
            ChannelsQueryParameters params = ChannelsQueryParameters.from(request);
            return OBJECT_MAPPER.writeValueAsString(params.apply(connectionDescriptionSupplier.describeConnections()
                .filter(connectionDescription -> connectionDescription.username().map(username::equals).orElse(false))
                .map(ConnectionDescriptionDTO::from))
                .toList());
        });

        service.get(SERVERS + "/connectedUsers", (request, response) -> OBJECT_MAPPER.writeValueAsString(connectionDescriptionSupplier.describeConnections()
            .flatMap(connectionDescription -> connectionDescription.username().stream())
            .distinct()
            .map(Username::asString)
            .toList()));
    }

    private static class ChannelsQueryParameters {
        enum SortDirection {
            ASC,
            DESC
        }

        enum SortType {
            NUMERICAL,
            ALPHABETICAL
        }

        private final Optional<Long> limit;
        private final long offset;
        private final Optional<String> sortBy;
        private final SortDirection sortDirection;
        private final SortType sortType;

        private ChannelsQueryParameters(Optional<Long> limit, long offset, Optional<String> sortBy,
                                        SortDirection sortDirection, SortType sortType) {
            this.limit = limit;
            this.offset = offset;
            this.sortBy = sortBy;
            this.sortDirection = sortDirection;
            this.sortType = sortType;
        }

        static ChannelsQueryParameters from(Request request) {
            Optional<Long> limit = Optional.ofNullable(request.queryParams("limit")).map(Long::parseUnsignedLong);
            long offset = Optional.ofNullable(request.queryParams("offset")).map(Long::parseUnsignedLong).orElse(0L);
            Optional<String> sortBy = Optional.ofNullable(request.queryParams("sortBy"));
            SortDirection sortDirection = Optional.ofNullable(request.queryParams("sortDirection"))
                .map(s -> SortDirection.valueOf(s.toUpperCase()))
                .orElse(SortDirection.ASC);
            SortType sortType = Optional.ofNullable(request.queryParams("sortType"))
                .map(s -> SortType.valueOf(s.toUpperCase()))
                .orElse(SortType.ALPHABETICAL);
            return new ChannelsQueryParameters(limit, offset, sortBy, sortDirection, sortType);
        }

        Stream<ConnectionDescriptionDTO> apply(Stream<ConnectionDescriptionDTO> stream) {
            Stream<ConnectionDescriptionDTO> result = stream;
            if (sortBy.isPresent()) {
                result = result.sorted(buildComparator(sortBy.get()));
            }
            if (offset > 0) {
                result = result.skip(offset);
            }
            if (limit.isPresent()) {
                result = result.limit(limit.get());
            }
            return result;
        }

        private Comparator<ConnectionDescriptionDTO> buildComparator(String field) {
            Comparator<ConnectionDescriptionDTO> comparator;
            if (sortType == SortType.NUMERICAL) {
                comparator = Comparator.comparingLong(a -> extractNumeric(a, field));
            } else {
                comparator = Comparator.comparing(a -> extractString(a, field));
            }
            if (sortDirection == SortDirection.DESC) {
                comparator = comparator.reversed();
            }
            return comparator;
        }

        private static long extractNumeric(ConnectionDescriptionDTO dto, String field) {
            try {
                return Long.parseLong(extractString(dto, field));
            } catch (NumberFormatException e) {
                return 0L;
            }
        }

        private static String extractString(ConnectionDescriptionDTO dto, String field) {
            if (field.startsWith("protocolSpecificInformation.")) {
                String key = field.substring("protocolSpecificInformation.".length());
                return Optional.ofNullable(dto.protocolSpecificInformation().get(key)).orElse("");
            }
            return switch (field) {
                case "protocol" -> dto.protocol();
                case "endpoint" -> dto.endpoint();
                case "remoteAddress" -> dto.remoteAddress().orElse("");
                case "connectionDate" -> dto.connectionDate().map(Instant::toString).orElse("");
                case "isActive" -> String.valueOf(dto.isActive());
                case "isOpen" -> String.valueOf(dto.isOpen());
                case "isWritable" -> String.valueOf(dto.isWritable());
                case "isEncrypted" -> String.valueOf(dto.isEncrypted());
                case "username" -> dto.username().orElse("");
                default -> "";
            };
        }
    }

    private Predicate<CertificateReloadable> filters(Request request) {
        Optional<Port> port = Optional.ofNullable(request.queryParams("port")).map(Integer::parseUnsignedInt).map(Port::of);

        return server -> port.map(p -> server.getPort() == p.getValue()).orElse(true);
    }

    private boolean noServerEnabled() {
        return servers.stream()
            .flatMap(CertificateReloadable.Factory::certificatesReloadable)
            .findFirst()
            .isEmpty();
    }
}
