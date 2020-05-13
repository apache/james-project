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
package org.apache.james.mpt.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.junit.jupiter.api.Test;

public class ImapFeaturesTest {

    @Test
    void supportedFeaturesShouldReturnEmptySetWhenNoFeatures() {
        assertThat(ImapFeatures.of().supportedFeatures()).isEmpty();
    }

    @Test
    void supportedFeaturesShouldReturnNamespaceInSetWhenNamespaceSupported() {
        assertThat(ImapFeatures.of(Feature.NAMESPACE_SUPPORT).supportedFeatures()).containsExactly(Feature.NAMESPACE_SUPPORT);
    }

    @Test
    void supportsShouldReturnFalseOnNamespaceWhenNamespaceIsNotSupported() {
        assertThat(ImapFeatures.of().supports(Feature.NAMESPACE_SUPPORT)).isFalse();
    }

    @Test
    void supportsShouldReturnTrueOnNamespaceWhenNamespaceIsSupported() {
        assertThat(ImapFeatures.of(Feature.NAMESPACE_SUPPORT).supports(Feature.NAMESPACE_SUPPORT)).isTrue();
    }

    @Test
    void supportsShouldReturnTrueOnDuplicateNamespaceEntryWhenNamespaceIsSupported() {
        assertThat(ImapFeatures.of(Feature.NAMESPACE_SUPPORT).supports(Feature.NAMESPACE_SUPPORT, Feature.NAMESPACE_SUPPORT)).isTrue();
    }

    
    @Test
    void supportsShouldReturnTrueOnEmptyListWhenNamespaceIsSupported() {
        assertThat(ImapFeatures.of(Feature.NAMESPACE_SUPPORT).supports()).isTrue();
    }

    @Test
    void supportsShouldReturnTrueOnEmptyListWhenNoFeatures() {
        assertThat(ImapFeatures.of().supports()).isTrue();
    }

    @Test
    void supportsShouldThrowOnNullFeature() {
        assertThatThrownBy(() -> ImapFeatures.of().supports((Feature)null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void supportsShouldThrowOnNullFeatureArray() {
        assertThatThrownBy(() -> ImapFeatures.of().supports((Feature[])null))
            .isInstanceOf(NullPointerException.class);
    }

    
    @Test
    void ofShouldThrowOnNullFeature() {
        assertThatThrownBy(() -> ImapFeatures.of((Feature)null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void ofShouldThrowOnNullFeatureArray() {
        assertThatThrownBy(() -> ImapFeatures.of((Feature[])null))
            .isInstanceOf(NullPointerException.class);
    }
}
