package uvm;

import java.util.ArrayList;
import java.util.List;

public final class SystemClockCalendar {
    private final List<EventRegistration> registrations;

    public SystemClockCalendar() {
        this.registrations = new ArrayList<>();
    }

    public void register(EventRegistration registration) {
        registrations.add(registration);
    }

    public void tick() {
        registrations.forEach(EventRegistration::newDay);
    }
}
