package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One side of an object cube.
 */
interface Face {
    String key();

    JsonNode json();
}
