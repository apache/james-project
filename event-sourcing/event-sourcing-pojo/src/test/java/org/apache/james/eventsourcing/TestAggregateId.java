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

package org.apache.james.eventsourcing;

import java.util.Objects;

import com.google.common.base.MoreObjects;
import org.apache.james.eventsourcing.AggregateId;

public class TestAggregateId implements AggregateId {

    public static TestAggregateId testId(int id) {
        return new TestAggregateId(id);
    }

    private final int id;

    private TestAggregateId(int id) {
        this.id = id;
    }

    @Override
    public String asAggregateKey() {
        return "TestAggregateId-" + id;
    }

    public int getId() {
        return id;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof TestAggregateId) {
            TestAggregateId that = (TestAggregateId) o;

            return Objects.equals(this.id, that.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("id", id)
            .toString();
    }
}
