package com.example.myphotos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class User {

    private String userLogin;
    private int userId;

    @JsonCreator
    public User(@JsonProperty("user_id") int id, @JsonProperty("login") String login){
        this.userId = id;
        this.userLogin = login;
    }

    @JsonProperty("user_id")
    public int getUserId() {
        return userId;
    }

    @JsonProperty("user_id")
    public void setUserId(int id){
        this.userId = id;
    }

    @JsonProperty("login")
    public String getUserLogin() {
        return userLogin;
    }

    @JsonProperty("login")
    public void setUserLogin(String userLogin) {
        this.userLogin = userLogin;
    }
}
