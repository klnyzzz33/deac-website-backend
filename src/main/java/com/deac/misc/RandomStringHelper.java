package com.deac.misc;

import java.util.Random;

public class RandomStringHelper {

    private static final String randomStringChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    public static String generateRandomString(int length) {
        Random random = new Random();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append(randomStringChars.charAt(random.nextInt(randomStringChars.length())));
        }
        return builder.toString();
    }

}
