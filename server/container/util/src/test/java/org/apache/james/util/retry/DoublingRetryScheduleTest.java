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
package org.apache.james.util.retry;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.util.retry.api.RetrySchedule;
import org.junit.jupiter.api.Test;

class DoublingRetryScheduleTest {

    @Test
    void testDoublingRetrySchedule() {
        assertThat(RetrySchedule.class.isAssignableFrom(DoublingRetrySchedule.class)).isTrue();
        assertThat(new DoublingRetrySchedule(0, 0).getInterval(0)).isEqualTo(0);
        assertThat(new DoublingRetrySchedule(-1, -1).getInterval(0)).isEqualTo(0);
        assertThat(new DoublingRetrySchedule(-1, 0).getInterval(0)).isEqualTo(0);
        assertThat(new DoublingRetrySchedule(0, -1).getInterval(0)).isEqualTo(0);
    }

    @Test
    void testGetInterval() {
        assertThat(new DoublingRetrySchedule(0, 8).getInterval(0)).isEqualTo(0);
        assertThat(new DoublingRetrySchedule(0, 8).getInterval(1)).isEqualTo(1);
        assertThat(new DoublingRetrySchedule(0, 8).getInterval(2)).isEqualTo(2);
        assertThat(new DoublingRetrySchedule(0, 8).getInterval(3)).isEqualTo(4);
        assertThat(new DoublingRetrySchedule(0, 8).getInterval(4)).isEqualTo(8);
        assertThat(new DoublingRetrySchedule(0, 8).getInterval(5)).isEqualTo(8);

        assertThat(new DoublingRetrySchedule(1, 8).getInterval(0)).isEqualTo(1);
        assertThat(new DoublingRetrySchedule(1, 8).getInterval(1)).isEqualTo(2);
        assertThat(new DoublingRetrySchedule(1, 8).getInterval(2)).isEqualTo(4);
        assertThat(new DoublingRetrySchedule(1, 8).getInterval(3)).isEqualTo(8);
        assertThat(new DoublingRetrySchedule(1, 8).getInterval(4)).isEqualTo(8);

        assertThat(new DoublingRetrySchedule(3, 12).getInterval(0)).isEqualTo(3);
        assertThat(new DoublingRetrySchedule(3, 12).getInterval(1)).isEqualTo(6);
        assertThat(new DoublingRetrySchedule(3, 12).getInterval(2)).isEqualTo(12);
        assertThat(new DoublingRetrySchedule(3, 12).getInterval(3)).isEqualTo(12);

        assertThat(new DoublingRetrySchedule(0, 8, 1000).getInterval(0)).isEqualTo(0);
        assertThat(new DoublingRetrySchedule(0, 8, 1000).getInterval(1)).isEqualTo(1000);
        assertThat(new DoublingRetrySchedule(0, 8, 1000).getInterval(2)).isEqualTo(2000);
        assertThat(new DoublingRetrySchedule(0, 8, 1000).getInterval(3)).isEqualTo(4000);
        assertThat(new DoublingRetrySchedule(0, 8, 1000).getInterval(4)).isEqualTo(8000);
        assertThat(new DoublingRetrySchedule(0, 8, 1000).getInterval(5)).isEqualTo(8000);
    }

    @Test
    void testToString() {
        assertThat(new DoublingRetrySchedule(0, 1).toString())
            .isEqualTo("DoublingRetrySchedule [startInterval=0, maxInterval=1, multiplier=1]");
    }
}
