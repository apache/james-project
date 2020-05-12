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

import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.junit.Test;

public class ImapFeaturesTest {

    @Test
    public void supportedFeaturesShouldReturnEmptySetWhenNoFeatures() {
        assertThat(ImapFeatures.of().supportedFeatures()).isEmpty();
    }

    @Test
    public void supportedFeaturesShouldReturnNamespaceInSetWhenNamespaceSupported() {
        assertThat(ImapFeatures.of(Feature.NAMESPACE_SUPPORT).supportedFeatures()).containsExactly(Feature.NAMESPACE_SUPPORT);
    }

    @Test
    public void supportsShouldReturnFalseOnNamespaceWhenNamespaceIsNotSupported() {
        assertThat(ImapFeatures.of().supports(Feature.NAMESPACE_SUPPORT)).isFalse();
    }

    @Test
    public void supportsShouldReturnTrueOnNamespaceWhenNamespaceIsSupported() {
        assertThat(ImapFeatures.of(Feature.NAMESPACE_SUPPORT).supports(Feature.NAMESPACE_SUPPORT)).isTrue();
    }

    @Test
    public void supportsShouldReturnTrueOnDuplicateNamespaceEntryWhenNamespaceIsSupported() {
        assertThat(ImapFeatures.of(Feature.NAMESPACE_SUPPORT).supports(Feature.NAMESPACE_SUPPORT, Feature.NAMESPACE_SUPPORT)).isTrue();
    }

    
    @Test
    public void supportsShouldReturnTrueOnEmptyListWhenNamespaceIsSupported() {
        assertThat(ImapFeatures.of(Feature.NAMESPACE_SUPPORT).supports()).isTrue();
    }

    @Test
    public void supportsShouldReturnTrueOnEmptyListWhenNoFeatures() {
        assertThat(ImapFeatures.of().supports()).isTrue();
    }

    @Test(expected = NullPointerException.class)
    @SuppressWarnings("CheckReturnValue")
    public void supportsShouldThrowOnNullFeature() {
        ImapFeatures.of().supports((Feature)null);
    }

    @Test(expected = NullPointerException.class)
    @SuppressWarnings("CheckReturnValue")
    public void supportsShouldThrowOnNullFeatureArray() {
        ImapFeatures.of().supports((Feature[])null);
    }

    
    @Test(expected = NullPointerException.class)
    public void ofShouldThrowOnNullFeature() {
        ImapFeatures.of((Feature)null);
    }

    @Test(expected = NullPointerException.class)
    public void ofShouldThrowOnNullFeatureArray() {
        ImapFeatures.of((Feature[])null);
    }
}
