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

package org.apache.james.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class ClassNameTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(ClassName.class)
            .verify();
    }

    @Test
    void constructorShouldThrowWhenNull() {
        assertThatThrownBy(() -> new ClassName(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorShouldThrowWhenEmpty() {
        assertThatThrownBy(() -> new ClassName(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorShouldThrowWhenStartWithDot() {
        assertThatThrownBy(() -> new ClassName(".MyClass"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorShouldThrowWhenEndWithDot() {
        assertThatThrownBy(() -> new ClassName("MyClass."))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorShouldThrowWhenEmptyPackagePart() {
        assertThatThrownBy(() -> new ClassName("part..MyClass"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getNameShouldReturnSuppliedValue() {
        String name = "org.apache.MyClass";
        assertThat(new ClassName(name).getName())
            .isEqualTo(name);
    }

    @Test
    void getNameShouldReturnSuppliedValueWhenNoPackage() {
        String name = "MyClass";
        assertThat(new ClassName(name).getName())
            .isEqualTo(name);
    }

    @Test
    void asFullyQualifiedShouldReturnCorrespondingFullyQualifiedClassName() {
        String name = "org.apache.MyClass";
        assertThat(new ClassName(name).asFullyQualified())
            .isEqualTo(new FullyQualifiedClassName(name));
    }

    @Test
    void appendPackageShouldAddPackageInFullyQualifiedClassName() {
        String name = "MyClass";
        String packageName = "org.apache";
        assertThat(new ClassName(name).appendPackage(PackageName.of(packageName)))
            .isEqualTo(new FullyQualifiedClassName("org.apache.MyClass"));
    }

    @Test
    void appendPackageShouldAddPackageInFullyQualifiedClassNameWhenAPackagePartAlreadyExists() {
        String name = "part.MyClass";
        String packageName = "org.apache";
        assertThat(new ClassName(name).appendPackage(PackageName.of(packageName)))
            .isEqualTo(new FullyQualifiedClassName("org.apache.part.MyClass"));
    }
}