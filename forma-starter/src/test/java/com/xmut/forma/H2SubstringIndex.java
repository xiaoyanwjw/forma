package com.xmut.forma;

/**
 * H2 stand-in for MySQL {@code SUBSTRING_INDEX} (session logical-run paging in tests).
 */
public final class H2SubstringIndex {

    private H2SubstringIndex() {
    }

    public static String substringIndex(String str, String delim, Integer count) {
        if (str == null || delim == null || count == null || count.intValue() <= 0) {
            return str;
        }
        int remaining = count.intValue();
        int from = 0;
        while (remaining > 0) {
            int idx = str.indexOf(delim, from);
            if (idx < 0) {
                return str;
            }
            remaining--;
            if (remaining == 0) {
                return str.substring(0, idx);
            }
            from = idx + delim.length();
        }
        return str;
    }
}
