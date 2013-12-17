package net.qldarch.web.util;

import java.util.Iterator;

public class CollectionUtils {
    public static <E> Iterable<E> asIterable(final Iterator<E> i) {
        return new Iterable<E>() {
            @Override
            public Iterator<E> iterator() {
                return i;
            }
        };
    }
}
