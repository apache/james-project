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

package org.apache.james.jmap.utils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.james.jmap.utils.DependencyGraph.CycleDetectedException;

import com.google.common.collect.Lists;

public class SortingHierarchicalCollections<T, Id> {

    private final Function<T, Id> index;
    private final Function<T, Optional<Id>> parentId;

    public SortingHierarchicalCollections(Function<T, Id> index,
                                  Function<T, Optional<Id>> parentId) {
        this.index = index;
        this.parentId = parentId;
    }

    public List<T> sortFromRootToLeaf(Collection<T> elements) throws CycleDetectedException {

        Map<Id, T> mapOfElementsById = indexElementsById(elements);

        DependencyGraph<T> graph = new DependencyGraph<>(m ->
                parentId.apply(m).map(mapOfElementsById::get));

        elements.forEach(graph::registerItem);

        return graph.getBuildChain().collect(Collectors.toList());
    }

    private Map<Id, T> indexElementsById(Collection<T> elements) {
        return elements.stream()
                .collect(Collectors.toMap(index, Function.identity()));
    }

    public List<T> sortFromLeafToRoot(Collection<T> elements) throws CycleDetectedException {
        return Lists.reverse(sortFromRootToLeaf(elements));
    }
}
