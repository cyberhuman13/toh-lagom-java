package com.chariotsolutions.tohlagom.mixed;

import play.Environment;
import com.typesafe.config.Config;
import com.lightbend.lagom.javadsl.persistence.jdbc.JdbcSession;
import com.lightbend.lagom.javadsl.persistence.jdbc.JdbcReadSide;
import com.lightbend.lagom.internal.persistence.jdbc.SlickOffsetStore;
import com.lightbend.lagom.javadsl.persistence.jdbc.GuiceSlickProvider;
import com.lightbend.lagom.internal.javadsl.persistence.jdbc.SlickProvider;
import com.lightbend.lagom.internal.javadsl.persistence.jdbc.JdbcSessionImpl;
import com.lightbend.lagom.internal.javadsl.persistence.jdbc.JdbcReadSideImpl;
import com.lightbend.lagom.internal.javadsl.persistence.jdbc.JavadslJdbcOffsetStore;
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
        // JdbcPersistenceModule is disabled in application.conf to
        // avoid conflicts with CassandraPersistenceModule.
        bind(SlickProvider.class).toProvider(GuiceSlickProvider.class);
        bind(SlickOffsetStore.class).to(JavadslJdbcOffsetStore.class);
        bind(JdbcReadSide.class).to(JdbcReadSideImpl.class);
        bind(JdbcSession.class).to(JdbcSessionImpl.class);
    }
}
