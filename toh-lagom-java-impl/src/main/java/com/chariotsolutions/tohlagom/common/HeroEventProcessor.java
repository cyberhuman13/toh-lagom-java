package com.chariotsolutions.tohlagom.common;

import java.util.Collections;

import com.chariotsolutions.tohlagom.impl.HeroEvent;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;

public abstract class HeroEventProcessor extends ReadSideProcessor<HeroEvent> {
    public static final String TABLE = "hero";
    public static final String ID_COLUMN = "id";
    public static final String NAME_COLUMN = "name";
    public static final String EVENT_PROCESSOR_ID = "hero-offset";

    public PSequence<AggregateEventTag<HeroEvent>> aggregateTags() {
        if (HeroEvent.TAG instanceof AggregateEventTag) {
            return TreePVector.from(Collections.singletonList((AggregateEventTag<HeroEvent>) HeroEvent.TAG));
        } else {
            return ((AggregateEventShards<HeroEvent>) HeroEvent.TAG).allTags();
        }
    }
}
