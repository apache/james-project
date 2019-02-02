/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.rrt.lib;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.rrt.lib.Mapping.Type;

public interface Mappings extends Iterable<Mapping> {

    boolean contains(Mapping mapping);

    int size();

    Mappings remove(Mapping mapping);

    boolean isEmpty();

    Iterable<String> asStrings();
    
    String serialize();

    boolean contains(Type type);
    
    Mappings select(Type type);

    Mappings exclude(Type type);

    Mapping getError();

    Optional<Mappings> toOptional();

    Mappings union(Mappings mappings);

    Stream<Mapping> asStream();
}