package com.example.duolingomathbot.model;

public enum TopicType {
    OGE("ОГЭ"),
    EGE("ЕГЭ"),
    OBA("ОБА");

    private final String displayName;

    TopicType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
