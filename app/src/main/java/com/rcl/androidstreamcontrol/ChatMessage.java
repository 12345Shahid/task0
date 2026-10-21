package com.rcl.androidstreamcontrol;

public class ChatMessage {
    private String text;
    private String time;
    private boolean isUser;

    public ChatMessage(String text, String time, boolean isUser) {
        this.text = text;
        this.time = time;
        this.isUser = isUser;
    }

    public String getText() { return text; }
    public String getTime() { return time; }
    public boolean isUser() { return isUser; }
}
