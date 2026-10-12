package com.example.fairnesstracker.auth;

/** Where a refresh token was issued, for the user's session list and investigations. Both may be null. */
public record ClientInfo(String ip, String userAgent) {

    public ClientInfo {
        ip = truncate(ip, 45);
        userAgent = truncate(userAgent, 512);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
