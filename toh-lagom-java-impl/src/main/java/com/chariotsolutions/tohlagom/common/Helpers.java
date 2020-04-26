package com.chariotsolutions.tohlagom.common;

import java.time.Duration;
import java.util.Random;
import java.util.stream.Stream;

public class Helpers {
    private static final Random random = new Random();
    public static final Duration askTimeout = Duration.ofSeconds(5);

    public static String id2str(int id) {
        return String.format("%10d", id);
    }

    public static String newId() {
        return id2str(random.nextInt());
    }

    public static String convertId(String id) {
        return id2str(Integer.parseInt(id));
    }

    public static String toTitleCase(String str) {
        if (null == str) {
            return null;
        }

        if (str.length() == 0) {
            return "";
        }

        if (str.length() == 1) {
            return str.toUpperCase();
        }

        StringBuffer buffer = new StringBuffer(str.length());

        Stream.of(str.split(" ")).forEach(stringPart -> {
            char[] charArray = stringPart.toLowerCase().toCharArray();
            charArray[0] = Character.toUpperCase(charArray[0]);
            buffer.append(new String(charArray)).append(" ");
        });

        return buffer.toString().trim();
    }
}
