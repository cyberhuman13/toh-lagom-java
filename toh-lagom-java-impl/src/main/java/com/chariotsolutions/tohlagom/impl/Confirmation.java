package com.chariotsolutions.tohlagom.impl;

import lombok.Value;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public interface Confirmation {
    @Value
    @JsonDeserialize
    final class Accepted implements Confirmation {
        @JsonCreator
        public Accepted() {}
    }

    @Value
    @JsonDeserialize
    final class Rejected implements Confirmation {
        public final String reason;

        @JsonCreator
        public Rejected(@JsonProperty("reason") String reason) {
            this.reason = reason;
        }
    }
}
