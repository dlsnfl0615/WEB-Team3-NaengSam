package com.naengsam.quick.domain.boormi.entity;

public enum ItemSizeCd {
    S,
    M;

    public static double multiplier(ItemSizeCd itemSizeCd) {
        return switch (itemSizeCd) {
            case S -> 1.0;
            case M -> 1.5;
        };
    }
}
