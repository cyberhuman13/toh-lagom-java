package com.chariotsolutions.tohlagom.impl;

import org.junit.Test;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import java.util.stream.Collectors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;

import com.chariotsolutions.tohlagom.api.Hero;
import com.chariotsolutions.tohlagom.api.NewHero;
import com.chariotsolutions.tohlagom.api.TourOfHeroesService;
import static com.chariotsolutions.tohlagom.common.Helpers.*;
import com.lightbend.lagom.javadsl.testkit.ServiceTest.TestServer;
import static com.lightbend.lagom.javadsl.testkit.ServiceTest.defaultSetup;
import static com.lightbend.lagom.javadsl.testkit.ServiceTest.startServer;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.*;

public class TourOfHeroesServiceTest {
    private static TestServer server;
    private static TourOfHeroesService service;

    private final String name1 = "aLiCe";
    private final String name2 = "bOrIs";
    private final String name3 = "aNnA";
    private final String newName = "cOrInNe";
    private final String prefix = "A";

    @BeforeClass
    public static void setUp() {
        server = startServer(defaultSetup().withCassandra());
        service = server.client(TourOfHeroesService.class);
    }

    @AfterClass
    public static void tearDown() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Test
    public void createHeroes() throws InterruptedException, ExecutionException, TimeoutException {
        final Hero hero1 = service.createHero().invoke(new NewHero(name1))
                .toCompletableFuture().get(5, SECONDS);
        final Hero hero2 = service.createHero().invoke(new NewHero(name2))
                .toCompletableFuture().get(5, SECONDS);
        final Hero hero3 = service.createHero().invoke(new NewHero(name3))
                .toCompletableFuture().get(5, SECONDS);

        assertEquals(toTitleCase(name1), hero1.name);
        assertEquals(toTitleCase(name2), hero2.name);
        assertEquals(toTitleCase(name3), hero3.name);
    }

    @Test
    public void fetchHeroes() throws InterruptedException {
        Thread.sleep(5000);
        service.heroes().invoke().thenApply(heroes -> {
            assertEquals(3, heroes.size());
            return heroes.stream().map(hero -> hero.name).collect(Collectors.toSet());
        }).thenAccept(names -> {
            assertTrue(names.contains(toTitleCase(name1)));
            assertTrue(names.contains(toTitleCase(name2)));
            assertTrue(names.contains(toTitleCase(name3)));
        });
    }

    @Test
    public void searchHeroes() {
        service.search(prefix).invoke().thenAccept(heroes -> {
            assertEquals(2, heroes.size());
            heroes.forEach(hero -> assertEquals(toTitleCase(prefix), hero.name.substring(0, prefix.length())));
        });
    }

    @Test
    public void fetchHero() {
        service.heroes().invoke().thenAccept(heroes -> {
            final String heroId = heroes.get(0).id;
            final String heroName = heroes.get(0).name;
            service.fetchHero(heroId).invoke().thenAccept(hero -> {
                assertEquals(heroId, hero.id);
                assertEquals(toTitleCase(heroName), hero.name);
            });
        });
    }

    @Test
    public void changeHero() {
        service.heroes().invoke().thenAccept(heroes -> {
            final String heroId = heroes.get(0).id;
            service.changeHero().invoke(new Hero(heroId, newName)).thenAccept(__ -> {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                service.fetchHero(heroId).invoke().thenAccept(hero -> {
                    assertEquals(heroId, hero.id);
                    assertEquals(toTitleCase(newName), hero.name);
                });
            });
        });
    }

    @Test
    public void deleteHero() {
        service.heroes().invoke().thenAccept(heroes -> {
            final String heroId = heroes.get(0).id;
            service.deleteHero(heroId).invoke().thenAccept(__ -> {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                service.heroes().invoke().thenAccept(remaining -> {
                    assertEquals(2, remaining.size());
                    assertFalse(remaining.stream().anyMatch(hero -> hero.id.equals(heroId)));
                });
            });
        });
    }
}
