package org.x04g.carouseldemo;

import java.util.ArrayList;
import java.util.List;

/** Pure ordering step. OEM model identity and the order of foreign models survive. */
final class CarouselOrder {
    private CarouselOrder() {}

    static <T> List<T> afterClock(List<T> models, T clock, T page) {
        if (models == null || clock == null || page == null || clock == page)
            throw new IllegalArgumentException();
        int clocks = 0, pages = 0;
        for (T model : models) {
            if (model == null) throw new IllegalArgumentException();
            if (model == clock) clocks++;
            if (model == page) pages++;
        }
        if (clocks != 1 || pages != 1) throw new IllegalArgumentException();
        List<T> result = new ArrayList<T>(models.size());
        for (T model : models) {
            if (model == page) continue;
            result.add(model);
            if (model == clock) result.add(page);
        }
        return result;
    }
}
