package org.apache.james.managesieveserver.webadmin;

import javax.inject.Inject;

import org.apache.james.managesieveserver.netty.ManageSieveServerFactory;
import org.apache.james.protocols.lib.webadmin.AbstractServerRoutes;

public class ManageSieveRoutes extends AbstractServerRoutes {

    public static final String BASE_PATH = "/sieve";

    @Inject
    public ManageSieveRoutes(ManageSieveServerFactory manageSieveServerFactory) {
        this.serverFactory = manageSieveServerFactory;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }
}