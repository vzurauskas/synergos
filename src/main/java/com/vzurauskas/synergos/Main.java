package com.vzurauskas.synergos;

public final class Main {
    private Main() {
    }

    public static void main(String... args) {
        new Args(args).action().run();
    }
}
