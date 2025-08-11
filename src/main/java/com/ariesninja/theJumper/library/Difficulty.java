package com.ariesninja.theJumper.library;

public enum Difficulty {
    I(1),
    II(2),
    III(3),
    IV(4),
    V(5),
    VI(6),
    VII(7),
    VIII(8),
    IX(9);

    private final int index;

    Difficulty(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    public String getDisplayName() {
        return name();
    }

    public static Difficulty fromId(String id) {
        if (id == null) return null;
        String k = id.trim().toUpperCase();
        for (Difficulty d : values()) {
            if (d.name().equals(k) || ("" + d.index).equals(k)) {
                return d;
            }
        }
        return null;
    }

    public Difficulty previousOrNull() {
        if (index <= 1) return null;
        return values()[index - 2];
    }

    public Difficulty nextOrNull() {
        if (index >= values().length) return null;
        return values()[index];
    }
}


