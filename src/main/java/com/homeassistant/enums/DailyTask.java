package com.homeassistant.enums;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum DailyTask {
    HERB_BOXES("herb_boxes"),
    STAVES("staves"),
    ESSENCE("essence"),
    RUNES("runes"),
    SAND("sand"),
    FLAX("flax"),
    ARROWS("arrows"),
    DYNAMITE("dynamite");

    private final String id;
}
