package com.scoring.backend.domain.vo;

import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;

import java.util.List;

public class TournamentBracketVO {

    private String id;
    private String name;
    private String location;
    private Integer status;
    private List<Player> players;
    private List<MatchRecord> matches;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public void setPlayers(List<Player> players) {
        this.players = players;
    }

    public List<MatchRecord> getMatches() {
        return matches;
    }

    public void setMatches(List<MatchRecord> matches) {
        this.matches = matches;
    }
}
