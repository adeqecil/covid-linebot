package com.linebot.covidbot.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Map;


public class KasusIndonesia {

    private String type, name;
    private List<String> regions;
    private int timestamp, infected, recovered, fatal;

    @JsonProperty("type")
    public String getType(){
        return type;
    }

    @JsonProperty("name")
    public String getName(){
        return name;
    }

    @JsonProperty("timestamp")
    public int getTimestamp(){
        return timestamp;
    }

    @JsonProperty("numbers")
    private void unpackNameFromNestedObject(Map<String, Integer> numbers) {
        infected = numbers.get("infected");
        recovered = numbers.get("recovered");
        fatal = numbers.get("fatal");
    }

    public int getInfected(){
        return infected;
    }

    public int getRecovered(){
        return recovered;
    }

    public int getFatal(){
        return fatal;
    }

    @JsonProperty("regions")
    public List<String> getRegions(){
        return regions;
    }
}

