package com.chariotsolutions.tohlagom.common;

import play.Environment;
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.api.ServiceLocator;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import com.lightbend.lagom.javadsl.akka.discovery.AkkaDiscoveryServiceLocator;

public class TourOfHeroesModule extends AbstractModule implements ServiceGuiceSupport {
    private final Environment environment;

    public TourOfHeroesModule(Environment environment) {
        this.environment = environment;
    }

    @Override
    protected void configure() {
        if (environment.isProd()) {
            bind(ServiceLocator.class).to(AkkaDiscoveryServiceLocator.class);
        }
    }
}
