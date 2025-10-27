package com.iotest.domain.model;

public class Action {
    private String room;
    private String switchUrl;
    private ActionType type;
    private String reason;

    public Action() {}

    public Action(String room, String switchUrl, ActionType type, String reason) {
        this.room = room;
        this.switchUrl = switchUrl;
        this.type = type;
        this.reason = reason;
    }

    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }

    public String getSwitchUrl() { return switchUrl; }
    public void setSwitchUrl(String switchUrl) { this.switchUrl = switchUrl; }

    public ActionType getType() { return type; }
    public void setType(ActionType type) { this.type = type; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    @Override
    public String toString() {
        return "Action{" +
                "room='" + room + '\'' +
                ", switchUrl='" + switchUrl + '\'' +
                ", type=" + type +
                ", reason='" + reason + '\'' +
                '}';
    }
}
