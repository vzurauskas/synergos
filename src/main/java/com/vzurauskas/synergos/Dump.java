package com.vzurauskas.synergos;

import com.vzurauskas.synergos.scan.Society;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Dump implements Action {
    private final Society society;
    private final Path target;
    private final PrintStream log;

    public Dump(Society society, Path target, PrintStream log) {
        this.society = society;
        this.target = target;
        this.log = log;
    }

    @Override
    public void run() {
        try {
            Files.writeString(target, society.json());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        log.println("Model written to " + target.toAbsolutePath());
    }
}
