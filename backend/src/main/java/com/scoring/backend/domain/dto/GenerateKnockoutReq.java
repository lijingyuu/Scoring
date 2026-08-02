package com.scoring.backend.domain.dto;

import java.util.List;

public class GenerateKnockoutReq {

    private String generationMode;
    private List<String> slots;

    public String getGenerationMode() {
        return generationMode;
    }

    public void setGenerationMode(String generationMode) {
        this.generationMode = generationMode;
    }

    public List<String> getSlots() {
        return slots;
    }

    public void setSlots(List<String> slots) {
        this.slots = slots;
    }
}
