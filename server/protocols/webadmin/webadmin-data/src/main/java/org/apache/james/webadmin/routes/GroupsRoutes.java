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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.internet.AddressException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.rrt.api.LoopDetectedException;
import org.apache.james.rrt.api.MappingAlreadyExistsException;
import org.apache.james.rrt.api.MappingConflictException;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.api.SourceDomainIsNotInDomainListException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;

import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Service;


public class GroupsRoutes implements Routes {

    public static final String ROOT_PATH = "address/groups";

    private static final String GROUP_ADDRESS = "groupAddress";
    private static final String GROUP_ADDRESS_PATH = ROOT_PATH + SEPARATOR + ":" + GROUP_ADDRESS;

    private static final String GROUP_MULTIPLE_PATH = "address/groups";
    private static final String GROUP_MULTIPLE_PATH_IS_EXIST = "address/groups/isExist";
    private static final String USER_ADDRESS = "userAddress";
    private static final String USER_IN_GROUP_ADDRESS_PATH = GROUP_ADDRESS_PATH + SEPARATOR + ":" + USER_ADDRESS;
    private static final String GROUP_ADDRESS_TYPE = "group";
    private static final String USER_ADDRESS_TYPE = "group member";

    private final JsonTransformer jsonTransformer;
    private final RecipientRewriteTable recipientRewriteTable;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final MailAddress dummyUser = new MailAddress("fc8f9dc08044a0c0ff9528fe997","fc8f9dc08044a0c0a8c23c68");

    @Inject
    @VisibleForTesting
    GroupsRoutes(RecipientRewriteTable recipientRewriteTable, JsonTransformer jsonTransformer) throws AddressException {
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
        service.get(GROUP_MULTIPLE_PATH_IS_EXIST, this::isExist);
        service.get(GROUP_ADDRESS_PATH, this::listGroupMembers, jsonTransformer);
        service.put(GROUP_ADDRESS_PATH, (request, response) -> halt(HttpStatus.BAD_REQUEST_400));
        service.post(GROUP_ADDRESS_PATH, this::createGroupWithDummyUser);
        service.put(USER_IN_GROUP_ADDRESS_PATH, this::addToGroup);
        //service.delete(GROUP_ADDRESS_PATH, (request, response) -> halt(HttpStatus.BAD_REQUEST_400));
        service.delete(USER_IN_GROUP_ADDRESS_PATH, this::removeFromGroup);
        service.delete(GROUP_MULTIPLE_PATH, this::removeMultipleGroup);
        service.delete(GROUP_ADDRESS_PATH, this::removeGroup);
    }

    public List<MappingSource> listGroups(Request request, Response response) throws RecipientRewriteTableException, JsonProcessingException {
        return recipientRewriteTable.getSourcesForType(Mapping.Type.Group).collect(ImmutableList.toImmutableList());
    }

    public HaltException createGroupWithDummyUser(Request request, Response response) {
        MailAddress groupAddress = MailAddressParser.parseMailAddress(request.params(GROUP_ADDRESS), GROUP_ADDRESS_TYPE);
        Domain domain = groupAddress.getDomain();
        MappingSource source = MappingSource.fromUser(Username.fromLocalPartWithDomain(groupAddress.getLocalPart(), domain));
        addGroupMember(source, dummyUser);
        return halt(HttpStatus.NO_CONTENT_204);
    }

    public HaltException addToGroup(Request request, Response response) {
        MailAddress groupAddress = MailAddressParser.parseMailAddress(request.params(GROUP_ADDRESS), GROUP_ADDRESS_TYPE);
        Domain domain = groupAddress.getDomain();
        MailAddress userAddress = MailAddressParser.parseMailAddress(request.params(USER_ADDRESS), USER_ADDRESS_TYPE);
        MappingSource source = MappingSource.fromUser(Username.fromLocalPartWithDomain(groupAddress.getLocalPart(), domain));
        addGroupMember(source, userAddress);
        return halt(HttpStatus.NO_CONTENT_204);
    }

    private void addGroupMember(MappingSource source, MailAddress userAddress) {
        try {
            recipientRewriteTable.addGroupMapping(source, userAddress.asString());
        } catch (MappingAlreadyExistsException e) {
            // do nothing
        } catch (MappingConflictException | LoopDetectedException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.CONFLICT_409)
                .type(ErrorType.WRONG_STATE)
                .message(e.getMessage())
                .haltError();
        } catch (SourceDomainIsNotInDomainListException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message(e.getMessage())
                .haltError();
        } catch (RecipientRewriteTableException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .type(ErrorType.SERVER_ERROR)
                .message(e.getMessage())
                .haltError();
        }
    }

    public HaltException removeMultipleGroup(Request request, Response response) throws RecipientRewriteTableException, JsonProcessingException {
        String jsonString = request.body();
        List<String> groups = objectMapper.readValue(jsonString, new TypeReference<List<String>>() {});

        //checking is this group correct or not. If not through an exception
        for (int i = 0; i < groups.size(); i++) {
            String group = groups.get(i);//todo
            MailAddress groupAddress;
            try {
                groupAddress = new MailAddress(group);
            } catch (AddressException e) {
                throw new RuntimeException(e);
            }
            Mappings mappings = recipientRewriteTable.getStoredMappings(MappingSource.fromMailAddress(groupAddress))
                    .select(Mapping.Type.Group);

            ensureNonEmptyMappings(mappings, group);
        }

        // Iterate through the array and print each element
        for (int i = 0; i < groups.size(); i++) {
            String group = groups.get(i);//todo
            // Have to check is this group correct or not
            MailAddress groupAddress;
            try {
                groupAddress = new MailAddress(group);
            } catch (AddressException e) {
                throw new RuntimeException(e);
            }

            Mappings mappings = recipientRewriteTable.getStoredMappings(MappingSource.fromMailAddress(groupAddress))
                    .select(Mapping.Type.Group);

            ensureNonEmptyMappings(mappings, group);

            var list = mappings
                    .asStream()
                    .map(Mapping::asMailAddress)
                    .flatMap(Optional::stream)
                    .map(MailAddress::asString)
                    .collect(ImmutableSortedSet.toImmutableSortedSet(String::compareTo));

            for (var userAddress : list) {
                MappingSource source = MappingSource
                        .fromUser(
                                Username.fromLocalPartWithDomain(groupAddress.getLocalPart(), groupAddress.getDomain()));
                recipientRewriteTable.removeGroupMapping(source, userAddress.toString());
            }
        }
        return halt(HttpStatus.NO_CONTENT_204);
    }

    public static class GroupStatusInfo {
        // Fields
        public String address;
        public String status;
        public String reason;

        // Constructor
        public GroupStatusInfo(String address, String status, String reason) {
            this.address = address;
            this.status = status;
            this.reason = reason;
        }

        @Override

        public String toString() {
            return "Address: " + address + "\nStatus: " + status + "\nReason: " + reason;
        }
    }

    public String isExist(Request request, Response response) throws RecipientRewriteTableException, JsonProcessingException {

        List<MappingSource> currentGroupList = recipientRewriteTable.getSourcesForType(Mapping.Type.Group).collect(ImmutableList.toImmutableList());
        Map<String, Boolean> mp = new HashMap<>();
        for (int i = 0; i < currentGroupList.size(); i++) {
            mp.put(currentGroupList.get(i).asMailAddressString(), true);
        }
        String jsonString = request.body();
        List<String> groups = objectMapper.readValue(jsonString, new TypeReference<List<String>>() {});

        //checking is this group exist or not.
        GroupStatusInfo[] result = new GroupStatusInfo[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            String group = groups.get(i);
            MailAddress groupAddress;
            try {
                groupAddress = new MailAddress(group);
                if (mp.containsKey(groupAddress)) {
                    result[i] = new GroupStatusInfo(group, "Exists", "");
                } else {
                    result[i] = new GroupStatusInfo(group,  "DoesNotExists", "");
                }
            } catch (AddressException e) {
                result[i] = new GroupStatusInfo(group, "Error", e.toString());
            }
        }

        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        String jsonResult = ow.writeValueAsString(result);

        return jsonResult;
    }


    public HaltException removeGroup(Request request, Response response) throws RecipientRewriteTableException {
        MailAddress groupAddress = MailAddressParser.parseMailAddress(request.params(GROUP_ADDRESS), GROUP_ADDRESS_TYPE);
        Mappings mappings = recipientRewriteTable.getStoredMappings(MappingSource.fromMailAddress(groupAddress))
                .select(Mapping.Type.Group);

        ensureNonEmptyMappings(mappings, groupAddress.toString());

        var list = mappings
                .asStream()
                .map(Mapping::asMailAddress)
                .flatMap(Optional::stream)
                .map(MailAddress::asString)
                .collect(ImmutableSortedSet.toImmutableSortedSet(String::compareTo));

        for (var userAddress : list) {
            MappingSource source = MappingSource
                    .fromUser(
                            Username.fromLocalPartWithDomain(groupAddress.getLocalPart(), groupAddress.getDomain()));
            recipientRewriteTable.removeGroupMapping(source, userAddress.toString());
        }
        return halt(HttpStatus.NO_CONTENT_204);
    }

    public HaltException removeFromGroup(Request request, Response response) throws RecipientRewriteTableException {
        MailAddress groupAddress = MailAddressParser.parseMailAddress(request.params(GROUP_ADDRESS), GROUP_ADDRESS_TYPE);
        MailAddress userAddress = MailAddressParser.parseMailAddress(request.params(USER_ADDRESS), USER_ADDRESS_TYPE);
        MappingSource source = MappingSource
            .fromUser(
                Username.fromLocalPartWithDomain(groupAddress.getLocalPart(), groupAddress.getDomain()));
        recipientRewriteTable.removeGroupMapping(source, userAddress.asString());
        return halt(HttpStatus.NO_CONTENT_204);
    }

    public ImmutableSortedSet<String> listGroupMembers(Request request, Response response) throws RecipientRewriteTableException {
        MailAddress groupAddress = MailAddressParser.parseMailAddress(request.params(GROUP_ADDRESS), GROUP_ADDRESS_TYPE);
        Mappings mappings = recipientRewriteTable.getStoredMappings(MappingSource.fromMailAddress(groupAddress))
            .select(Mapping.Type.Group);

        ensureNonEmptyMappings(mappings, groupAddress.toString());

        var list = mappings
                .asStream()
                .map(Mapping::asMailAddress)
                .flatMap(Optional::stream)
                .map(MailAddress::asString)
                .collect(ImmutableSortedSet.toImmutableSortedSet(String::compareTo));

        for (var s : list) {
            System.out.println(s);
        }

        return mappings
                .asStream()
                .map(Mapping::asMailAddress)
                .flatMap(Optional::stream)
                .map(MailAddress::asString)
                .collect(ImmutableSortedSet.toImmutableSortedSet(String::compareTo));
    }

    private void ensureNonEmptyMappings(Mappings mappings, String group) {
        if (mappings == null || mappings.isEmpty()) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorType.INVALID_ARGUMENT)
                .message(group + " does not exist")
                .haltError();
        }
    }
}
