package com.chariotsolutions.tohlagom.impl;

import akka.persistence.typed.EventSeq;
import akka.persistence.typed.EventSeq$;
import akka.persistence.typed.EventAdapter;

// This is a workaround for AWS Cassandra not currently supporting empty strings.
public class HeroEventAdapter extends EventAdapter<HeroEvent, HeroEvent> {
    @Override
    public String manifest(HeroEvent event) {
        return " "; // Override the empty string.
    }

    @Override
    public HeroEvent toJournal(HeroEvent event) {
        return event;
    }

    @Override
    public EventSeq<HeroEvent> fromJournal(HeroEvent event, String manifest) {
        return EventSeq$.MODULE$.single(event);
    }
}
