package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Side 3: contracts. Left empty until its meaning is settled.
 */
final class ContractsFace implements Face {
    @Override
    public String key() {
        return "contracts";
    }

    @Override
    public JsonNode json() {
        return JsonNodeFactory.instance.arrayNode();
    }
}
