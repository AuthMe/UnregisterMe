package fr.xephi.unregisterme;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;

class Sha256 {

    private static final char[] CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final Random RANDOM = new SecureRandom();

    static String computeHash(String password) {
        String salt = generateSalt();
        return "$SHA$" + salt + "$" + sha256(sha256(password) + salt);
    }

    private static String generateSalt() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; ++i) {
            sb.append(CHARS[RANDOM.nextInt(16)]);
        }
        return sb.toString();
    }

    private static String sha256(String message) {
        MessageDigest algorithm;
        try {
            algorithm = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
        algorithm.reset();
        algorithm.update(message.getBytes());
        byte[] digest = algorithm.digest();
        return String.format("%0" + (digest.length << 1) + "x", new BigInteger(1, digest));
    }
}
