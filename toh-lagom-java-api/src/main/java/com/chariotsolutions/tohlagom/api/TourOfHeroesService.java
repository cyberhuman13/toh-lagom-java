package com.chariotsolutions.tohlagom.api;

import akka.Done;
import akka.NotUsed;
import java.util.List;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;
import static com.lightbend.lagom.javadsl.api.Service.restCall;

public interface TourOfHeroesService extends Service {
    /**
     * GET http://localhost:9000/api/heroes
     */
    ServiceCall<NotUsed, List<Hero>> heroes();

    /**
     * GET http://localhost:9000/api/heroes?name=[name]
     */
    ServiceCall<NotUsed, List<Hero>> search(String name);

    /**
     * GET http://localhost:9000/api/heroes/[id]
     */
    ServiceCall<NotUsed, Hero> fetchHero(String id);

    /**
     * POST http://localhost:9000/api/heroes
     */
    ServiceCall<NewHero, Hero> createHero();

    /**
     * PUT http://localhost:9000/api/heroes
     */
    ServiceCall<Hero, Done> changeHero();

    /**
     * DELETE http://localhost:9000/api/heroes/[id]
     */
    ServiceCall<NotUsed, Done> deleteHero(String heroId);

    @Override
    default Descriptor descriptor() {
        return Service.named("toh-lagom-java").withCalls(
                restCall(Method.GET, "/api/heroes", this::heroes),
                restCall(Method.PUT, "/api/heroes", this::changeHero),
                restCall(Method.POST, "/api/heroes", this::createHero),
                restCall(Method.GET, "/api/heroes/?name", this::search),
                restCall(Method.GET, "/api/heroes/:heroId", this::fetchHero),
                restCall(Method.DELETE, "/api/heroes/:heroId", this::deleteHero)
        ).withAutoAcl(true);
    }
}
