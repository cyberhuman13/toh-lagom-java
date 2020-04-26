package com.chariotsolutions.tohlagom.impl;

import akka.actor.ActorSystem;
import java.net.InetSocketAddress;
import com.typesafe.config.Config;
import scala.collection.immutable.Seq;
import scala.collection.immutable.Nil$;
import akka.persistence.cassandra.ConfigSessionProvider;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

public class AmazonSessionProvider extends ConfigSessionProvider {
    private final Config config;

    public AmazonSessionProvider(ActorSystem system, Config config) {
        super(system, config);
        this.config = config;
    }

    @Override
    public Future<Seq<InetSocketAddress>> lookupContactPoints(String clusterId, ExecutionContext ec) {
        final String region = this.config.getString("aws-region");
        final InetSocketAddress address = InetSocketAddress.createUnresolved(
                "cassandra." + region + ".amazonaws.com", 9142);
        return Future.successful(Nil$.MODULE$.$colon$colon(address));
    }
}
