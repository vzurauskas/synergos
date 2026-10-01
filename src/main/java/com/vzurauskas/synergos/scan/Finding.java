package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * One red flag raised on one type.
 *
 * @param flag short id of the flag, e.g. "refused-message"
 * @param severity "red" for a clear break of a principle, "amber" for a hint
 * @param where the method or name the flag points at
 * @param line source line
 * @param why what was found
 * @param principle the principle it departs from, and where it comes from
 */
public record Finding(String flag, String severity, String where, int line, String why, String principle) {
    public ObjectNode json() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("flag", flag);
        node.put("severity", severity);
        node.put("where", where);
        node.put("line", line);
        node.put("why", why);
        node.put("principle", principle);
        return node;
    }
}
