package org.x04g.carouseldemo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Tests the production helper with synthetic objects; no Android or OEM code. */
public final class CarouselOrderFixture {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("order_check_" + checks);
    }
    private static final class EqualObject {
        @Override public boolean equals(Object ignored) { return true; }
        @Override public int hashCode() { return 1; }
    }
    private static void expectInvalid(List<Object> models, Object clock, Object page) {
        boolean rejected = false;
        try { CarouselOrder.afterClock(models, clock, page); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected);
    }
    private static void caseOrder(List<Object> input, Object clock, Object page, Object... expected) {
        List<Object> before = new ArrayList<Object>(input);
        List<Object> result = CarouselOrder.afterClock(input, clock, page);
        check(result != input);
        check(result.size() == expected.length);
        for (int i = 0; i < expected.length; i++) check(result.get(i) == expected[i]);
        check(input.size() == before.size());
        for (int i = 0; i < before.size(); i++) check(input.get(i) == before.get(i));
    }
    public static void main(String[] ignored) {
        Object clock = new EqualObject(), page = new EqualObject();
        Object first = new EqualObject(), last = new EqualObject();
        // equals() intentionally aliases every object; only identity is safe.
        caseOrder(Arrays.asList(clock, first, last, page), clock, page, clock, page, first, last);
        caseOrder(Arrays.asList(page, first, clock, last), clock, page, first, clock, page, last);
        caseOrder(Arrays.asList(first, clock, page, last), clock, page, first, clock, page, last);
        caseOrder(Arrays.asList(first, page, last, clock), clock, page, first, last, clock, page);
        caseOrder(Arrays.asList(clock, page), clock, page, clock, page);
        // The same foreign object may be referenced twice; preserve both entries.
        caseOrder(Arrays.asList(first, clock, first, page, last), clock, page, first, clock, page, first, last);
        expectInvalid(null, clock, page);
        expectInvalid(Arrays.asList(clock, page), null, page);
        expectInvalid(Arrays.asList(clock, page), clock, null);
        expectInvalid(Arrays.asList(clock, page), clock, clock);
        expectInvalid(Arrays.asList(first, page), clock, page);
        expectInvalid(Arrays.asList(clock, first), clock, page);
        expectInvalid(Arrays.asList(clock, page, clock), clock, page);
        expectInvalid(Arrays.asList(clock, page, page), clock, page);
        expectInvalid(Arrays.asList(clock, null, page), clock, page);
        expectInvalid(new ArrayList<Object>(), clock, page);
        System.out.println("{\"pass\":true,\"checks\":" + checks + ",\"scope\":\"production ordering helper; synthetic identity models; no Android\"}");
    }
}
