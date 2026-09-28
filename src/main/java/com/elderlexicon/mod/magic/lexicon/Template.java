package com.elderlexicon.mod.magic.lexicon;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills the texts of the lexicon: {@code {name}} is replaced by the value of {@code name}, {@code {Name}} by the same
 * value with its first letter in upper case, and {@code {name|other}} by the value of {@code other} when {@code name}
 * has none. A name with no value is left out.
 */
public final class Template {

    private static final Pattern SLOT = Pattern.compile("\\{([A-Za-z0-9_.]+)(?:\\|([A-Za-z0-9_.]+))?}");

    private Template() {
    }

    public static String fill(String template, Map<String, String> values) {
        if (template == null) {
            return "";
        }
        Matcher matcher = SLOT.matcher(template);
        StringBuilder filled = new StringBuilder();
        while (matcher.find()) {
            String value = valueOf(matcher.group(1), values);
            if ((value == null || value.isEmpty()) && matcher.group(2) != null) {
                value = valueOf(matcher.group(2), values);
            }
            matcher.appendReplacement(filled, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(filled);
        return filled.toString();
    }

    private static String valueOf(String name, Map<String, String> values) {
        String value = values.get(name);
        if (value != null) {
            return value;
        }
        String lower = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        if (!lower.equals(name)) {
            String base = values.get(lower);
            return base == null || base.isEmpty() ? base : Character.toUpperCase(base.charAt(0)) + base.substring(1);
        }
        return null;
    }
}
