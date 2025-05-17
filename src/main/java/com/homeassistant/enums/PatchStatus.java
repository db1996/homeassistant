package com.homeassistant.enums;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum PatchStatus {
    IN_PROGRESS("in_progress", 1),
    READY("ready", 0),
    OTHER("other", -1),
    NEVER_PLANTED("not_planted", -2);


    private final String name;
    private final int itemID;
}
