package org.apache.james.mpt.api;

import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;

public class ImapFeatures {
    
    public enum Feature {
        NAMESPACE_SUPPORT
    }

    public static ImapFeatures of(Feature... features) {
        return new ImapFeatures(ImmutableSet.copyOf(features));
    }

    private final ImmutableSet<Feature> supportedFeatures;
    
    private ImapFeatures(ImmutableSet<Feature> supportedFeatures) {
        this.supportedFeatures = supportedFeatures;
    }
    
    public Set<Feature> supportedFeatures() {
        return supportedFeatures;
    }
    
    public boolean supports(Feature... features) {
        Preconditions.checkNotNull(features);
        ImmutableSet<Feature> requestedFeatures = ImmutableSet.copyOf(features);
        return FluentIterable.from(requestedFeatures).allMatch(Predicates.in(supportedFeatures));
    }
    
}
