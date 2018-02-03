package org.apache.james.karaf.features;

import org.junit.Test;

public class Mime4jTestLive extends KarafLiveTestSupport {

    @Test
    public void testInstallApacheMime4jFeature() throws Exception {
        addJamesFeaturesRepository();
        features.installFeature("apache-james-mime4j");
        assertInstalled("apache-james-mime4j");
        assertBundlesAreActive();
    }
}
