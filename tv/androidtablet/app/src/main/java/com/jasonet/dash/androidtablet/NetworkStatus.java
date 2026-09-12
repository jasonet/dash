package com.jasonet.dash.androidtablet;

enum NetworkStatus {
    CHECKING, OFFLINE, LIMITED, ONLINE, UNKNOWN;

    static NetworkStatus from(boolean connected, boolean internet, boolean validated) {
        if(!connected) return OFFLINE;
        return internet && validated ? ONLINE : LIMITED;
    }
}
