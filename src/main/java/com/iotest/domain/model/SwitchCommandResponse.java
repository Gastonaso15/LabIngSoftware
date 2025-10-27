package com.iotest.domain.model;

public class SwitchCommandResponse {
    private int id;
    private String src;
    private Params params;

    public static class Params {
        private boolean was_on;

        public boolean isWas_on() { return was_on; }
        public void setWas_on(boolean was_on) { this.was_on = was_on; }
    }

    public SwitchCommandResponse() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getSrc() { return src; }
    public void setSrc(String src) { this.src = src; }

    public Params getParams() { return params; }
    public void setParams(Params params) { this.params = params; }

    @Override
    public String toString() {
        return "SwitchCommandResponse{" +
                "id=" + id +
                ", src='" + src + '\'' +
                ", params.was_on=" + (params != null ? params.was_on : null) +
                '}';
    }
}
