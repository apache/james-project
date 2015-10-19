package org.apache.james.mpt.api;

import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test(expected=NullPointerException.class)
    public void supportsShouldThrowOnNullFeature() {
        assertThat(ImapFeatures.of().supports((Feature)null));
    }

    @Test(expected=NullPointerException.class)
    public void supportsShouldThrowOnNullFeatureArray() {
        assertThat(ImapFeatures.of().supports((Feature[])null));
    }

    
    @Test(expected=NullPointerException.class)
    public void ofShouldThrowOnNullFeature() {
        ImapFeatures.of((Feature)null);
    }

    @Test(expected=NullPointerException.class)
    public void ofShouldThrowOnNullFeatureArray() {
        ImapFeatures.of((Feature[])null);
    }
}
