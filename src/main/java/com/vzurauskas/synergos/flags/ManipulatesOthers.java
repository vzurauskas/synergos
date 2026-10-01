package com.vzurauskas.synergos.flags;

import com.vzurauskas.synergos.scan.Finding;
import com.vzurauskas.synergos.scan.Flag;
import com.vzurauskas.synergos.scan.JavaType;
import com.vzurauskas.synergos.scan.Project;
import com.vzurauskas.synergos.scan.Site;
import com.vzurauskas.synergos.scan.Traffic;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * An object that reaches into another one: calls its setters or assigns to
 * its fields.
 */
public final class ManipulatesOthers implements Flag {
    private static final Pattern SETTER = Pattern.compile("set[A-Z]\\w*");

    @Override
    public List<Finding> findings(JavaType type, Project project, Traffic traffic) {
        List<Finding> findings = new ArrayList<>();
        for (Site site : traffic.from(type.id())) {
            boolean setter = site.is(Site.Kind.MESSAGE) && site.arity() > 0 && SETTER.matcher(site.name()).matches();
            if (setter || site.is(Site.Kind.WRITE)) {
                findings.add(new Finding(
                    "manipulates-others",
                    "red",
                    site.member(),
                    site.line(),
                    (setter ? "Calls " : "Assigns to ") + site.expression(),
                    "An object does not control, manage or manipulate any object other than itself "
                        + "(from the author's design-process notes)."
                ));
            }
        }
        return findings;
    }
}
