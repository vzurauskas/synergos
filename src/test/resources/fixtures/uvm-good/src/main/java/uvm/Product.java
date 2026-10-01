package uvm;

import java.util.ArrayList;
import java.util.List;

public final class Product {
    private final List<Runnable> mourners;

    public Product() {
        this.mourners = new ArrayList<>();
    }

    public void place(SystemClockCalendar calendar, int daysToLive) {
        calendar.register(new EventRegistration(this, daysToLive));
    }

    public void registerForDeath(Runnable mourner) {
        mourners.add(mourner);
    }

    public void die() {
        mourners.forEach(Runnable::run);
    }
}
