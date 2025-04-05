package com.homeassistant.enums;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum PatchStatus {
    IN_PROGRESS("In progress", 1),
    READY("Ready", 0),
    OTHER("Other", -1),
    NEVER_PLANTED("Never planted", -2);


    private final String name;
    private final int itemID;
}
