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

import jakarta.inject.Singleton;

@Singleton
public class MultiLifeCycleObject {
    private final StringBuilder str = new StringBuilder();

    @TestAnnotationC
    public void foo() {
        str.append("c");
    }

    @TestAnnotationA
    public void aaa() {
        str.append("a");
    }

    @TestAnnotationB
    public void bbb() {
        str.append("b");
    }

    @TestAnnotationA
    public void mmm() {
        str.append("a");
    }

    @TestAnnotationB
    public void nnn() {
        str.append("b");
    }

    @TestAnnotationB
    public void qqq() {
        str.append("b");
    }

    @TestAnnotationA
    public void zzz() {
        str.append("a");
    }

    @Override
    public String toString() {
        return str.toString();
    }
}
