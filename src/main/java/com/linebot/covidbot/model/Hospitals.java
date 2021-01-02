package com.linebot.covidbot.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "name",
        "address",
        "region",
        "phone",
        "province"
})


public class Hospitals {

    @JsonProperty("name")
    private String name;
    @JsonProperty("address")
    private String address;
    @JsonProperty("region")
    private String region;
    @JsonProperty("phone")
    private String phone;
    @JsonProperty("province")
    private String province;

    @JsonProperty("name")
    public String getName(){
        return name;
    }

    @JsonProperty("address")
    public String getAddress(){
        return address;
    }

    @JsonProperty("region")
    public String getRegion(){
        return region;
    }

    @JsonProperty("phone")
    public String getPhone(){
        return phone;
    }

    @JsonProperty("province")
    public String getProvince(){
        return province;
    }
}
