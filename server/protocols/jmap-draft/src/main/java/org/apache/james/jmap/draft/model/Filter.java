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

package org.apache.james.jmap.draft.model;

import java.util.List;
import java.util.stream.Stream;

import org.apache.james.jmap.draft.json.FilterDeserializer;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.github.steveash.guavate.Guavate;

@JsonDeserialize(using = FilterDeserializer.class)
public interface Filter {
    class TooDeepFilterHierarchyException extends IllegalArgumentException {
        TooDeepFilterHierarchyException() {
            super("Filter depth is higher than maximum allowed value " + MAX_FILTER_DEPTH);
        }
    }

    int MAX_FILTER_DEPTH = 10;

    String prettyPrint(String indentation);

    default List<FilterCondition> breadthFirstVisit() {
        return this.breadthFirstVisit(0)
            .collect(Guavate.toImmutableList());
    }

    default Stream<FilterCondition> breadthFirstVisit(int depth) {
        if (depth > MAX_FILTER_DEPTH) {
            throw new TooDeepFilterHierarchyException();
        }
        if (this instanceof FilterOperator) {
            FilterOperator operator = (FilterOperator) this;

            return operator.getConditions().stream()
                .flatMap(filter -> filter.breadthFirstVisit(depth + 1));
        }
        if (this instanceof FilterCondition) {
            return Stream.of((FilterCondition) this);
        }
        throw new RuntimeException("Unsupported Filter implementation " + this);
    }
}
