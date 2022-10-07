package com.deac.misc;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class StringSearchHelper {

    private static final Map<String, String> nonAsciiCharMap = Map.of(
            "á", "a",
            "é", "e",
            "í", "i",
            "óöő", "o",
            "úüű", "u"
    );

    private static final List<String> mostCommonWords = List.of(
            "a", "az", "es", "egy", "van", "volt", "meg", "mar", "meg", "mint", "nem", "kell",
            "ez", "igy", "ugy", "most", "itt", "ott", "nincs", "igen", "nagy", "majd", "most", "fog", "lesz"
    );

    public static String[] normalizeSearchTerm(String term) {
        String tmp = term.toLowerCase().replaceAll("[.,:;\\-_]", "");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < tmp.length(); i++) {
            String c = String.valueOf(tmp.charAt(i));
            boolean didSwap = false;
            for (Map.Entry<String, String> entry : nonAsciiCharMap.entrySet()) {
                if (entry.getKey().contains(c)) {
                    builder.append(entry.getValue());
                    didSwap = true;
                    break;
                }
            }
            if (!didSwap) {
                builder.append(c);
            }
        }
        tmp = builder.toString();
        return Arrays.stream(tmp.trim().split("\\s+"))
                .filter(s -> !mostCommonWords.contains(s))
                .toArray(String[]::new);
    }

}
