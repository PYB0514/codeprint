package com.codeprint.domain.user;

public enum UserPlan {
    FREE, PRO;

    public int maxProjects() {
        return switch (this) {
            case FREE -> 3;
            case PRO -> Integer.MAX_VALUE;
        };
    }
}
