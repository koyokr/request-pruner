package kr.koyo.requestpruner;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.persistence.PersistedObject;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestPrunerTest {
    private PersistedObject store(HashMap<String, String> values) {
        PersistedObject prefs = mock(PersistedObject.class);
        when(prefs.getString(anyString())).thenAnswer(i -> values.get(i.getArgument(0)));
        doAnswer(i -> { values.put(i.getArgument(0), i.getArgument(1)); return null; })
                .when(prefs).setString(anyString(), anyString());
        return prefs;
    }

    @Test void legacySettingsAndCommentsRoundTripIndependently() {
        var values = new HashMap<String, String>();
        values.put("customRules", "+X-Old\n-X-Disabled");
        values.put("defaultOverrides", "-Accept-Encoding\n+Accept");
        var prefs = store(values);
        var headers = new HeaderRules(prefs);
        assertFalse(headers.entries().get(1).enabled());
        int custom = headers.entries().size() - 2;
        headers.setComment(custom, "설명\twith\nnewlines");
        var cookies = new HeaderRules(prefs, true);
        cookies.add(HeaderRule.parse("my_cookie*"));
        cookies.setComment(cookies.entries().size() - 1, "Cookie note");
        assertEquals("설명\twith\nnewlines", new HeaderRules(prefs).entries().get(custom).comment());
        assertEquals("Cookie note", new HeaderRules(prefs, true).entries().getLast().comment());
        assertFalse(new HeaderRules(prefs).entries().getLast().enabled());
        headers.removeCustom(Set.of(0, custom));
        assertEquals("Accept", headers.entries().getFirst().rule().name());
        assertFalse(headers.entries().stream().anyMatch(e -> e.rule().name().equals("X-Old")));
    }

    @Test void defaultHeadersAreSortedAndCustomRulesCanBeRenamed() {
        var prefs = store(new HashMap<>());
        var headers = new HeaderRules(prefs);
        var names = headers.entries().stream().map(e -> e.rule().name()).toList();
        assertEquals(names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), names);
        assertTrue(names.containsAll(List.of("If-Modified-Since", "If-None-Match", "X-Requested-With", "Sec-CH-*", "Pragma")));
        assertTrue(HeaderRule.parse("Sec-CH-*").matchesName("sec-ch-prefers-color-scheme", true));

        headers.add(HeaderRule.parse("X-Old"));
        int custom = headers.entries().size() - 1;
        headers.setComment(custom, "note");
        headers.rename(custom, HeaderRule.parse("X-New"));
        var renamed = new HeaderRules(prefs).entries().getLast();
        assertEquals("X-New", renamed.rule().name());
        assertEquals("note", renamed.comment());
        assertThrows(IllegalArgumentException.class, () -> headers.rename(custom, HeaderRule.parse("accept")));
        headers.rename(0, HeaderRule.parse("X-Other"));
        assertEquals("Accept", headers.entries().getFirst().rule().name());

        headers.setEnabled(0, false);
        headers.setEnabled(custom, false);
        headers.resetDefaults();
        var reset = new HeaderRules(prefs).entries();
        assertTrue(reset.getFirst().enabled());
        assertEquals("X-New", reset.getLast().rule().name());
        assertFalse(reset.getLast().enabled());
    }

    @Test void cookieVendorsAreSortedAndKeepTheirCookiesTogether() {
        var groups = CookieDefaults.entries().stream().map(HeaderRules.Entry::group).toList();
        assertEquals(groups.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), groups);
        var cookies = new HeaderRules(store(new HashMap<>()), true);
        assertEquals("NA_CO=keep; ch-veil-id=keep", RequestCleaner.cleanCookies(
                "_pk_id.1.abcd=x; NA_SA=y; NA_CO=keep; ch-veil-id=keep; wcs_bt=z", cookies.active()));
    }

    @Test void defaultCookiesProtectIdentityAndEdgeState() {
        var prefs = store(new HashMap<>());
        var cookies = new HeaderRules(prefs, true);
        String protectedValue = "session=abc==; cf_clearance=ok; __cf_bm=x; _abck=y; OptanonConsent=z; hubspotutk=h; mp_123_mixpanel=m";
        assertEquals(protectedValue, RequestCleaner.cleanCookies("_ga=x; " + protectedValue + "; _fbp=y", cookies.active()));
        cookies.setGroupEnabled("Mixpanel", true);
        assertEquals("mp_other=x", RequestCleaner.cleanCookies("mp_123_mixpanel=m; mp_other=x", cookies.active()));
        cookies.setGroupEnabled("Google Analytics", false);
        assertEquals("_ga=x", RequestCleaner.cleanCookies("_ga=x", new HeaderRules(prefs, true).active()));
    }

    @Test void cloudflareDefaultsRemoveNonChallengeCookiesAndKeepSecurityState() {
        var prefs = store(new HashMap<>());
        var cookies = new HeaderRules(prefs, true);
        var enabled = List.of("__cflb", "cf_ob_info", "cf_use_ob", "__cfwaitingroom");
        var disabled = List.of("cf_clearance", "__cf_bm", "_cfuvid", "__cfruid", "__cfseq",
                "cf_chl_rc_i", "cf_chl_rc_ni", "cf_chl_rc_m");
        for (String name : enabled) {
            assertTrue(cookies.entries().stream().anyMatch(e -> e.group().equals("Cloudflare")
                    && e.rule().name().equals(name) && e.enabled() && !e.comment().isBlank()), name);
            assertEquals("session=ok", RequestCleaner.cleanCookies(name + "=x; session=ok", cookies.active()));
        }
        for (String name : disabled) {
            assertTrue(cookies.entries().stream().anyMatch(e -> e.group().equals("Cloudflare")
                    && e.rule().name().equals(name) && !e.enabled() && !e.comment().isBlank()), name);
            assertEquals(name + "=x", RequestCleaner.cleanCookies(name + "=x", cookies.active()));
        }
        int clearance = java.util.stream.IntStream.range(0, cookies.entries().size())
                .filter(i -> cookies.entries().get(i).rule().name().equals("cf_clearance")).findFirst().orElseThrow();
        cookies.setEnabled(clearance, true);
        assertEquals("", RequestCleaner.cleanCookies("cf_clearance=x", new HeaderRules(prefs, true).active()));
        cookies.setGroupEnabled("Cloudflare", false);
        assertEquals("__cflb=x", RequestCleaner.cleanCookies("__cflb=x", new HeaderRules(prefs, true).active()));
    }

    @Test void cookieParsingPreservesValuesAndMatchesCaseSensitively() {
        var rules = List.of(HeaderRule.parse("_ga*"));
        assertEquals("SID=a=b==; _GA=keep; malformed", RequestCleaner.cleanCookies(
                "_ga=1; SID=a=b==; _ga_id=2; _GA=keep; malformed", rules));
        assertEquals("", RequestCleaner.cleanCookies("_ga=1; _ga=2", rules));
        assertEquals(" untouched = a== ; malformed ", RequestCleaner.cleanCookies(" untouched = a== ; malformed ", rules));
        assertThrows(IllegalArgumentException.class, () -> HeaderRule.parse("*"));
        assertThrows(IllegalArgumentException.class, () -> HeaderRule.parse("a*b"));
    }

    @Test void duplicateRulesUseTheCorrectCaseSemantics() {
        var prefs = store(new HashMap<>());
        var headers = new HeaderRules(prefs);
        assertThrows(IllegalArgumentException.class, () -> headers.add(HeaderRule.parse("ACCEPT")));
        var cookies = new HeaderRules(prefs, true);
        cookies.add(HeaderRule.parse("_GA"));
        assertThrows(IllegalArgumentException.class, () -> cookies.add(HeaderRule.parse("_ga")));
    }

    private HttpHeader header(String name, String value) {
        HttpHeader header = mock(HttpHeader.class);
        when(header.name()).thenReturn(name);
        when(header.value()).thenReturn(value);
        return header;
    }

    @Test void cleansMultipleCookieHeadersAndPreservesUnrelatedHeaders() {
        var request = mock(HttpRequest.class);
        var removed = mock(HttpRequest.class);
        var result = mock(HttpRequest.class);
        var auth = header("Authorization", "Bearer abc");
        var first = header("Cookie", "_ga=x; session=abc==");
        var second = header("cookie", "_fbp=y");
        var cleaned = header("Cookie", "session=abc==");
        var original = List.of(auth, first, second, header("ACCEPT-ENCODING", "gzip"));
        when(request.headers()).thenReturn(original);
        when(request.withRemovedHeaders(original)).thenReturn(removed);
        when(removed.withAddedHeaders(List.of(auth, cleaned))).thenReturn(result);
        try (var factory = mockStatic(HttpHeader.class)) {
            factory.when(() -> HttpHeader.httpHeader("Cookie", "session=abc==")).thenReturn(cleaned);
            var outcome = RequestCleaner.clean(request, List.of(HeaderRule.parse("Accept-Encoding")),
                    List.of(HeaderRule.parse("_ga"), HeaderRule.parse("_fbp")));
            assertSame(result, outcome.request());
            assertEquals(1, outcome.headers());
            assertEquals(2, outcome.cookies());
        }
        assertSame(request, RequestCleaner.clean(request, List.of(), List.of()).request());
    }

    private <T> T component(java.awt.Container parent, Class<T> type) {
        for (var child : parent.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof java.awt.Container container) {
                T found = component(container, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test void vendorFilterRevealsCollapsedCookies() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new RulePanel(new HeaderRules(store(new HashMap<>()), true)).component();
            var filter = component(panel, javax.swing.JTextField.class);
            var table = component(panel, javax.swing.JTable.class);
            filter.setText("Google Analytics");
            assertTrue(table.getRowCount() > 1);
            assertEquals("_ga", table.getValueAt(1, 1));
        });
    }

    @Test void renamingRuleUpdatesFilteredRows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var rules = new HeaderRules(store(new HashMap<>()));
            rules.add(HeaderRule.parse("X-Custom"));
            var panel = new RulePanel(rules).component();
            var filter = component(panel, javax.swing.JTextField.class);
            var table = component(panel, javax.swing.JTable.class);
            filter.setText("X-Custom");
            assertEquals(1, table.getRowCount());
            table.setValueAt("X-Renamed", 0, 1);
            assertEquals(0, table.getRowCount());
            assertEquals("X-Renamed", rules.entries().getLast().rule().name());
        });
    }

    @Test void invalidSavedPrefixesAreIgnored() {
        var values = new HashMap<String, String>();
        values.put("customRules", "?X-Bad\n+X-Good");
        values.put("defaultOverrides", "?Accept");
        var rules = new HeaderRules(store(values));
        assertTrue(rules.entries().getFirst().enabled());
        assertFalse(rules.entries().stream().anyMatch(e -> e.rule().name().equals("X-Bad")));
        assertEquals("X-Good", rules.entries().getLast().rule().name());
    }
}
