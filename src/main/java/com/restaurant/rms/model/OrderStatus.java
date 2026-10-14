package com.restaurant.rms.model;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    PENDING, PREPARING, READY, COMPLETED, CANCELLED;

    public Set<OrderStatus> nextAllowed() {
        return switch (this) {
            case PENDING -> EnumSet.of(PREPARING, CANCELLED);
            case PREPARING -> EnumSet.of(READY, CANCELLED);
            case READY -> EnumSet.of(COMPLETED);
            case COMPLETED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canMoveTo(OrderStatus target) {
        return nextAllowed().contains(target);
    }

    /** Statuses visible on the kitchen board. */
    public static final Set<OrderStatus> KITCHEN_ACTIVE = EnumSet.of(PENDING, PREPARING, READY);
}
