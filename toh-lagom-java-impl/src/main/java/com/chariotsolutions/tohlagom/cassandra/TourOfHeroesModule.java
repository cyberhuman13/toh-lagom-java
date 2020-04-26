package com.chariotsolutions.tohlagom.cassandra;

import play.Environment;
import com.typesafe.config.Config;
import com.chariotsolutions.tohlagom.api.TourOfHeroesService;

/**
 * The module that binds the TourOfHeroesService so that it can be served.
 */
public class TourOfHeroesModule extends com.chariotsolutions.tohlagom.common.TourOfHeroesModule {
    public TourOfHeroesModule(Environment environment, Config config) {
        super(environment);
    }

    @Override
    protected void configure() {
        super.configure();
        bindService(TourOfHeroesService.class, TourOfHeroesServiceImpl.class);
    }
}
