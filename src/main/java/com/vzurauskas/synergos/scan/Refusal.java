package com.vzurauskas.synergos.scan;

/**
 * A change the viewer won't make, with the HTTP status that says why: 404
 * nothing to change, 409 the text changed on disk meanwhile, 400 or 422 a
 * request or a file it can't work with.
 */
public final class Refusal extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int status;

    public Refusal(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
