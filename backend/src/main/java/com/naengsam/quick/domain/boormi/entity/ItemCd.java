package com.naengsam.quick.domain.boormi.entity;

public enum ItemCd {
    DOCUMENT,
    SAMPLE,
    PACKAGE,
    ETC;

    public static double multiplier(ItemCd itemCd) {
        return switch (itemCd) {
            case DOCUMENT -> 1.0;
            case SAMPLE -> 1.3;
            case PACKAGE -> 1.5;
            case ETC -> 1.2;
        };
    }
}
