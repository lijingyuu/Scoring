package com.scoring.backend.security;

public final class AuthContext {

    private static final ThreadLocal<String> USER_ID_HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void setUserId(String userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static String getUserId() {
        return USER_ID_HOLDER.get();
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}
