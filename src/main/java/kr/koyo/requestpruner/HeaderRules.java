package kr.koyo.requestpruner;

import burp.api.montoya.persistence.PersistedObject;
import java.util.ArrayList;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The removal list: built-in rules, which can be toggled but not removed, followed by the user's
 * own rules. Changes are saved in the Burp project, so each project keeps its own lists.
 */
final class HeaderRules {
    record Entry(HeaderRule rule, boolean enabled, boolean builtIn, String group, String comment) {
        Entry withEnabled(boolean enabled) {
            return new Entry(rule, enabled, builtIn, group, comment);
        }
    }

    // Kept in header-name order, which is the order the table shows.
    private static final List<Entry> DEFAULTS = List.of(
            header("Accept", true, "Accepted response formats; removing it may change the response format."),
            header("Accept-Encoding", true, "Advertises compression support; removing it may return an uncompressed response."),
            header("Accept-Language", true, "Preferred languages; removing it may change the response language."),
            header("Cache-Control", true, "Controls caching; removing it may allow cached responses."),
            header("Connection", true, "Connection options; removing it may change connection reuse or upgrades."),
            header("Device-Memory", false, "Legacy device memory hint; removing it may change device-specific content."),
            header("DNT", true, "Do Not Track preference; removing it stops sending that preference."),
            header("Downlink", false, "Legacy network speed hint; removing it may change adaptive content."),
            header("DPR", false, "Legacy device pixel ratio hint; removing it may change image selection."),
            header("ECT", false, "Legacy connection type hint; removing it may change adaptive content."),
            header("If-Modified-Since", true, "Cache validator; removing it returns the full response instead of 304 Not Modified."),
            header("If-None-Match", true, "ETag cache validator; removing it returns the full response instead of 304 Not Modified."),
            header("Origin", false, "Request origin; removing it may fail CORS or CSRF checks."),
            header("Pragma", true, "Legacy no-cache directive; removing it may allow cached responses."),
            header("Priority", true, "Request priority hint; removing it may change scheduling."),
            header("Purpose", true, "Legacy prefetch marker; removing it makes a prefetch look like a normal request."),
            header("Referer", false, "Referring page; removing it may fail CSRF or hotlink checks."),
            header("RTT", false, "Legacy round-trip time hint; removing it may change adaptive content."),
            header("Save-Data", false, "Reduced data preference; removing it may return heavier content."),
            header("Sec-CH-*", true, "Browser client hints; removing them may change device-specific content."),
            header("Sec-Fetch-*", true, "Request context; removing it may affect CSRF or bot checks."),
            header("Sec-GPC", true, "Global Privacy Control preference; removing it stops sending that preference."),
            header("Sec-Purpose", true, "Prefetch or prerender marker; removing it makes the request look like a normal one."),
            header("Upgrade-Insecure-Requests", true, "HTTPS upgrade preference; removing it may change redirects."),
            header("Viewport-Width", false, "Legacy viewport width hint; removing it may change image selection."),
            header("Width", false, "Legacy image width hint; removing it may change image selection."),
            header("X-Client-Data", true, "Chrome experiment IDs sent to Google services; removing it has no effect elsewhere."),
            header("X-Requested-With", true, "Marks AJAX requests; removing it may fail AJAX-only or CSRF checks."));

    private static Entry header(String name, boolean enabled, String comment) {
        return new Entry(HeaderRule.parse(name), enabled, true, "", comment);
    }

    // One rule per line, prefixed with '+' (enabled) or '-' (disabled). Built-in rules are saved
    // only when their state differs from the default.
    private static final String CUSTOM_KEY = "customRules";
    private static final String DEFAULT_OVERRIDES_KEY = "defaultOverrides";

    private final PersistedObject store;
    private volatile List<Entry> entries;
    private final List<Entry> defaults;
    final boolean cookies;
    private final String keyPrefix;


    HeaderRules(PersistedObject store) {
        this(store, false);
    }

    HeaderRules(PersistedObject store, boolean cookies) {
        this.store = store;
        this.cookies = cookies;
        defaults = cookies ? CookieDefaults.entries() : DEFAULTS;
        keyPrefix = cookies ? "cookies." : "";
        entries = load();
    }

    List<Entry> entries() {
        return entries;
    }

    List<HeaderRule> active() {
        return entries.stream().filter(Entry::enabled).map(Entry::rule).toList();
    }

    synchronized void add(HeaderRule rule) {
        if (entries.stream().anyMatch(e -> same(e.rule(), rule))) {
            throw new IllegalArgumentException(rule + " is already in the list.");
        }
        var next = new ArrayList<>(entries);
        next.add(new Entry(rule, true, false, "Custom", ""));
        save(next);
    }

    private boolean same(HeaderRule left, HeaderRule right) {
        return cookies ? left.name().equals(right.name()) : left.sameAs(right);
    }

    /** Renames the custom rule at the given index; built-in rules are kept. */
    synchronized void rename(int index, HeaderRule rule) {
        Entry entry = entries.get(index);
        if (entry.builtIn() || entry.rule().name().equals(rule.name())) return;
        for (int i = 0; i < entries.size(); i++) {
            if (i != index && same(entries.get(i).rule(), rule)) {
                throw new IllegalArgumentException(rule + " is already in the list.");
            }
        }
        var next = new ArrayList<>(entries);
        next.set(index, new Entry(rule, entry.enabled(), false, entry.group(), entry.comment()));
        save(next);
    }

    synchronized void setComment(int index, String comment) {
        var next = new ArrayList<>(entries);
        Entry entry = next.get(index);
        if (entry.builtIn()) return;
        next.set(index, new Entry(entry.rule(), entry.enabled(), false, entry.group(), comment));
        save(next);
    }

    synchronized void setGroupEnabled(String group, boolean enabled) {
        save(entries.stream().map(e -> e.group().equals(group) ? e.withEnabled(enabled) : e).toList());
    }

    synchronized void setEnabled(int index, boolean enabled) {
        var next = new ArrayList<>(entries);
        next.set(index, next.get(index).withEnabled(enabled));
        save(next);
    }

    /** Restores the default on/off state of every built-in rule; custom rules are kept. */
    synchronized void resetDefaults() {
        save(Stream.concat(defaults.stream(), entries.stream().filter(e -> !e.builtIn())).toList());
    }

    /** Removes the custom rules at the given indexes; built-in rules are kept. */
    synchronized void removeCustom(Set<Integer> indexes) {
        var next = new ArrayList<Entry>();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.builtIn() || !indexes.contains(i)) next.add(entry);
        }
        save(next);
    }

    private List<Entry> load() {
        Map<String, Boolean> overrides = lines(store.getString(keyPrefix + DEFAULT_OVERRIDES_KEY))
                .collect(Collectors.toMap(line -> line.substring(1), line -> line.charAt(0) == '+', (a, b) -> b));
        var loaded = new ArrayList<Entry>();
        for (Entry entry : defaults) {
            loaded.add(entry.withEnabled(overrides.getOrDefault(entry.rule().toString(), entry.enabled())));
        }
        lines(store.getString(keyPrefix + CUSTOM_KEY)).forEach(line -> {
            try {
                String[] parts = line.substring(1).split("\\t", 2);
                HeaderRule rule = HeaderRule.parse(parts[0]);
                String comment = parts.length == 2
                        ? new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8) : "";
                // Skip duplicates, including custom rules that a later version made built-in.
                if (loaded.stream().noneMatch(e -> same(e.rule(), rule))) {
                    loaded.add(new Entry(rule, line.charAt(0) == '+', false, "Custom", comment));
                }
            } catch (IllegalArgumentException ignored) {
                // An unreadable saved rule is dropped rather than blocking the extension.
            }
        });
        return List.copyOf(loaded);
    }

    private void save(List<Entry> next) {
        entries = List.copyOf(next);
        store.setString(keyPrefix + DEFAULT_OVERRIDES_KEY, encode(next.stream().filter(e -> e.builtIn() && !defaults.contains(e))));
        store.setString(keyPrefix + CUSTOM_KEY, encode(next.stream().filter(e -> !e.builtIn())));
    }

    private static String encode(Stream<Entry> entries) {
        return entries.map(e -> (e.enabled() ? "+" : "-") + e.rule() + (e.builtIn() ? "" : "\t"
                + Base64.getEncoder().encodeToString(e.comment().getBytes(StandardCharsets.UTF_8)))).collect(Collectors.joining("\n"));
    }

    private static Stream<String> lines(String text) {
        return text == null ? Stream.empty() : text.lines().filter(line -> line.length() > 1 && (line.charAt(0) == '+' || line.charAt(0) == '-'));
    }
}
