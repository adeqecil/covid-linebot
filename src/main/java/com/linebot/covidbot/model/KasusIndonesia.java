package com.linebot.covidbot.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;


public class KasusIndonesia {

    private String type, name;
    private List<String> regions;
    private long timestamp, infected, recovered, fatal;
    
    DecimalFormat formatter = new DecimalFormat("#,###");


    @JsonProperty("type")
    public String getType(){
        return type;
    }

    @JsonProperty("name")
    public String getName(){
        return name;
    }

    @JsonProperty("timestamp")
    public long getTimestamp(){
        return timestamp;
    }

    @JsonProperty("numbers")
    private void unpackNameFromNestedObject(Map<String, Integer> numbers) {
        infected = numbers.get("infected");
        recovered = numbers.get("recovered");
        fatal = numbers.get("fatal");
    }

    public String getInfected(){
        return formatter.format(infected);
    }

    public String getRecovered(){
        return formatter.format(recovered);
    }

    public String getFatal(){
        return formatter.format(fatal);
    }

    @JsonProperty("regions")
    public List<String> getRegions(){
        return regions;
    }
}

