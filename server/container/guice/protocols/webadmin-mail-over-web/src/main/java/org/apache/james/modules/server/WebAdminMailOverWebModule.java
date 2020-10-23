package org.apache.james.modules.server;

import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.routes.ReceiveMailOverWebRoutes;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

public class WebAdminMailOverWebModule extends AbstractModule {

    @Override
    protected void configure() {
        Multibinder<Routes> routesMultibinder = Multibinder.newSetBinder(binder(), Routes.class);
        routesMultibinder.addBinding().to(ReceiveMailOverWebRoutes.class);
    }
}
