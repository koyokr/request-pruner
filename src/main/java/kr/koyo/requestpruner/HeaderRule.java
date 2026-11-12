package kr.koyo.requestpruner;

import burp.api.montoya.http.message.HttpHeader;

/** A header to remove, written as {@code Name} or {@code Prefix-*}. Names match case-insensitively. */
record HeaderRule(String name) {
    private static final String TOKEN_CHARS = "!#$%&'*+-.^_`|~";

    static HeaderRule parse(String text) {
        String name = text.strip();
        if (name.isEmpty()) throw new IllegalArgumentException("Enter a header name.");
        int star = name.indexOf('*');
        if (star == 0) throw new IllegalArgumentException("A wildcard needs a prefix, such as Sec-Fetch-*.");
        if (star >= 0 && star != name.length() - 1) {
            throw new IllegalArgumentException("* is allowed only at the end of the name.");
        }
        for (char c : name.toCharArray()) {
            if (!(c < 128 && Character.isLetterOrDigit(c)) && TOKEN_CHARS.indexOf(c) < 0) {
                throw new IllegalArgumentException("Invalid character in header name: '" + c + "'.");
            }
        }
        return new HeaderRule(name);
    }

    boolean matches(HttpHeader header) {
        return matchesName(header.name(), true);
    }

    boolean matchesName(String headerName, boolean ignoreCase) {
        return name.endsWith("*")
                ? headerName.regionMatches(ignoreCase, 0, name, 0, name.length() - 1)
                : (ignoreCase ? headerName.equalsIgnoreCase(name) : headerName.equals(name));
    }

    /** Whether both rules remove the same headers. */
    boolean sameAs(HeaderRule other) {
        return name.equalsIgnoreCase(other.name);
    }

    @Override
    public String toString() {
        return name;
    }
}
