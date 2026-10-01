package uvm;

public final class PriceUtils {
    private PriceUtils() {
    }

    public static int withTax(int price) {
        return price * 121 / 100;
    }

    public static int discounted(int price, int percent) {
        return price * (100 - percent) / 100;
    }
}
