package kr.koyo.requestpruner;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import java.util.ArrayList;
import java.util.List;

final class RequestCleaner {
    /** The cleaned request, or the original when nothing matched, with the number of removals. */
    record Result(HttpRequest request, int headers, int cookies) {}

    static Result clean(HttpRequest request, List<HeaderRule> headers, List<HeaderRule> cookies) {
        var kept = new ArrayList<HttpHeader>();
        int removedHeaders = 0;
        int[] removedCookies = {0};
        for (HttpHeader header : request.headers()) {
            if (headers.stream().anyMatch(rule -> rule.matches(header))) {
                removedHeaders++;
                continue;
            }
            if (header.name().equalsIgnoreCase("Cookie")) {
                String value = cleanCookies(header.value(), cookies, removedCookies);
                if (!value.equals(header.value())) {
                    if (!value.isEmpty()) kept.add(HttpHeader.httpHeader(header.name(), value));
                    continue;
                }
            }
            kept.add(header);
        }
        boolean changed = removedHeaders > 0 || removedCookies[0] > 0;
        return new Result(changed ? request.withRemovedHeaders(request.headers()).withAddedHeaders(kept) : request,
                removedHeaders, removedCookies[0]);
    }

    static String cleanCookies(String value, List<HeaderRule> rules) {
        return cleanCookies(value, rules, new int[1]);
    }

    private static String cleanCookies(String value, List<HeaderRule> rules, int[] removed) {
        if (rules.isEmpty()) return value;
        var kept = new ArrayList<String>();
        boolean changed = false;
        for (String part : value.split(";", -1)) {
            int equals = part.indexOf('=');
            String name = equals < 0 ? "" : part.substring(0, equals).strip();
            if (equals >= 0 && rules.stream().anyMatch(rule -> matchesCookie(rule.name(), name))) {
                changed = true;
                removed[0]++;
            } else {
                kept.add(part);
            }
        }
        // Preserve surviving values (including embedded '=') and original spacing wherever possible.
        return changed ? String.join(";", kept).stripLeading() : value;
    }

    private static boolean matchesCookie(String pattern, String name) {
        int star = pattern.indexOf('*');
        return star < 0 ? pattern.equals(name)
                : name.length() >= pattern.length() - 1 && name.startsWith(pattern.substring(0, star))
                && name.endsWith(pattern.substring(star + 1));
    }
}
