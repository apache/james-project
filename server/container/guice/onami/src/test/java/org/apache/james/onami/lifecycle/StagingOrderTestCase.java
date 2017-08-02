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

package org.apache.james.onami.lifecycle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class StagingOrderTestCase {
    @Test
    public void testFifo() {
        List<Integer> order = new ArrayList<>();
        DefaultStager<TestAnnotationA> stager = makeStager(order, DefaultStager.Order.FIRST_IN_FIRST_OUT);
        stager.stage();

        Assert.assertEquals(Arrays.asList(1, 2, 3), order);
    }

    @Test
    public void testFilo() {
        List<Integer> order = new ArrayList<>();
        DefaultStager<TestAnnotationA> stager = makeStager(order, DefaultStager.Order.FIRST_IN_LAST_OUT);
        stager.stage();

        Assert.assertEquals(Arrays.asList(3, 2, 1), order);
    }

    private DefaultStager<TestAnnotationA> makeStager(final List<Integer> order, DefaultStager.Order stagingOrder) {
        Stageable stageable1 = stageHandler -> order.add(1);
        Stageable stageable2 = stageHandler -> order.add(2);
        Stageable stageable3 = stageHandler -> order.add(3);

        DefaultStager<TestAnnotationA> stager = new DefaultStager<>(TestAnnotationA.class, stagingOrder);
        stager.register(stageable1);
        stager.register(stageable2);
        stager.register(stageable3);
        return stager;
    }
}
