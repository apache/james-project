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

package org.apache.james.jmap.draft.methods;

import java.io.IOException;

import jakarta.inject.Inject;

import org.apache.james.jmap.draft.model.UpdateMessagePatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;

public class UpdateMessagePatchConverter {

    private final ObjectMapper jsonParser;
    private final UpdateMessagePatchValidator validator;

    @Inject
    @VisibleForTesting
    UpdateMessagePatchConverter(ObjectMapper jsonParser, UpdateMessagePatchValidator validator) {
        this.jsonParser = jsonParser;
        this.validator = validator;
    }

    public UpdateMessagePatch fromJsonNode(ObjectNode updatePatchNode) {
        if (updatePatchNode == null || updatePatchNode.isNull() || updatePatchNode.isMissingNode()) {
            throw new IllegalArgumentException("updatePatchNode");
        }
        if (! validator.isValid(updatePatchNode)) {
            return UpdateMessagePatch.builder()
                    .validationResult(validator.validate(updatePatchNode))
                    .build();
        }
        try {
            return jsonParser.readerFor(UpdateMessagePatch.class).<UpdateMessagePatch>readValue(updatePatchNode);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
