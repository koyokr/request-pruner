package kr.koyo.requestpruner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Curated cookie rules with per-entry removal effects and default states. */
final class CookieDefaults {
    static List<HeaderRules.Entry> entries() {
        var entries = new ArrayList<HeaderRules.Entry>();
        // https://developers.cloudflare.com/fundamentals/reference/policies-compliances/cloudflare-cookies/
        // Non-challenge cookies are enabled; challenge and security-rule state is opt-in.
        add(entries, "Cloudflare", true, "Load-balancer affinity; removing it may route to a different origin and change session behavior.", "__cflb");
        add(entries, "Cloudflare", true, "Always Online failure metadata; removing it may change offline-cache handling.", "cf_ob_info");
        add(entries, "Cloudflare", true, "Selects the Always Online cache port; removing it may change the cached response.", "cf_use_ob");
        add(entries, "Cloudflare", true, "Waiting Room state; removing it may lose queue position or return the request to the waiting room.", "__cfwaitingroom");
        add(entries, "Cloudflare", false, "Proof of challenge completion and JavaScript detection state; removing it may bring challenges back.", "cf_clearance");
        add(entries, "Cloudflare", false, "Bot Management score/session state; removing it may change bot detection or trigger challenges.", "__cf_bm");
        add(entries, "Cloudflare", false, "Rate-limit visitor identity; removing it may group users sharing an IP and cause rate limiting.", "_cfuvid __cfruid");
        add(entries, "Cloudflare", false, "Request sequence state used by security rules; removing it may change rule decisions.", "__cfseq");
        add(entries, "Cloudflare", false, "Challenge Platform diagnostics; retained by default because it belongs to challenge processing.", "cf_chl_rc_i cf_chl_rc_ni cf_chl_rc_m");
        add(entries, "Google Analytics", true, "Analytics IDs and session data; server-side tagging may recreate them.",
                "_ga _ga_* _gid _gat _gat_* __utma __utmb __utmc __utmt __utmz __utmv");
        add(entries, "Google Ads", true, "Ad attribution; removing it may lose conversion attribution.", "_gcl_* _gac_*");
        add(entries, "Meta Pixel", true, "Ad attribution; server-side tagging may read or recreate it.", "_fbp _fbc");
        add(entries, "Microsoft Clarity", true, "Analytics session tracking; removing it may reset tracking.", "_clck _clsk");
        add(entries, "Microsoft Advertising", true, "Ad attribution and session tracking; attribution may be lost.", "_uetsid _uetvid _uetmsclkid");
        add(entries, "Hotjar", true, "Behavior analytics; removing it may reset tracking.", "_hj*");
        add(entries, "HubSpot analytics", true, "Analytics session tracking; removing it may reset tracking.", "__hstc __hssc __hssrc");
        add(entries, "Yandex Metrica", true, "Analytics tracking; removing it may reset tracking.", "_ym_*");
        add(entries, "TikTok Pixel", true, "Ad attribution and tracking preference; attribution may be lost.", "_ttp ttcsid* ttclid _tt_enable_cookie");
        add(entries, "LinkedIn Insight Tag", true, "Ad attribution and analytics; attribution may be lost.", "li_fat_id li_giant oribili_user_guid oribi_cookie_test ln_or");
        add(entries, "Pinterest Tag", true, "Ad attribution; removing it may lose attribution.", "_pin_unauth _epik");
        add(entries, "Reddit Pixel", true, "Ad tracking ID; removing it may reset tracking.", "_rdt_uuid");
        add(entries, "Amplitude", true, "Analytics identity; removing it may reset tracking.", "AMP_* amplitude_id_*");
        add(entries, "Adobe Analytics", true, "Analytics capability and click data; tracking may change.", "s_cc s_sq");
        add(entries, "HubSpot forms", false, "Contact identity; removing it may break form contact deduplication.", "hubspotutk");
        add(entries, "Snap Pixel", false, "Tracking ID; server usage has not been confirmed.", "_scid");
        add(entries, "Mixpanel", false, "Analytics identity; the app may read it on the server.", "mp_*_mixpanel");
        add(entries, "Segment", false, "User identity; the app may read it on the server.", "ajs_anonymous_id ajs_user_id");
        add(entries, "Adobe identity", false, "Visitor identity; the app may read it on the server.", "s_vi s_fid s_ecid AMCV_* AMCVS_*");
        add(entries, "Consent", false, "Consent state; removing it may change banners or rendered tracking scripts.",
                "OptanonConsent OptanonAlertBoxClosed CookieConsent euconsent-v2 __hs_cookie_cat_pref");
        add(entries, "A/B testing", false, "Experiment assignment; removing it may select a different page variant.", "hs_ab_test _vwo_* optimizelyEndUserId");
        // https://matomo.org/faq/general/faq_146/ (names end with a site ID and hash)
        add(entries, "Matomo", true, "Analytics visitor, session and referrer data; removing it may reset tracking.",
                "_pk_id* _pk_ses* _pk_ref* _pk_cvar* _pk_hsr* _pk_testcookie*");
        // https://naver.github.io/conversion-tracking/
        add(entries, "Naver Ads", true, "Ad attribution and analytics; attribution may be lost.", "NA_SA NA_DA wcs_bt");
        add(entries, "Naver inflow", false, "Naver inflow IDs; the site may pass them to Naver Pay orders.", "NA_CO NVADID CPAValidator");
        // https://docs.channel.io/help/en/articles/b2b4aab2
        add(entries, "Channel Talk", false, "Chat visitor identity and session; removing it may reset the chat user.", "ch-veil-id ch-session*");
        // Stable sort: vendors in name order, each vendor's cookies in the order written above.
        entries.sort(Comparator.comparing(HeaderRules.Entry::group, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(entries);
    }

    /** The comment for a vendor row: a summary where its cookies differ, otherwise the shared comment. */
    static String groupComment(String group, String fallback) {
        return switch (group) {
            case "Cloudflare" -> "Edge cookies; routing and cache cookies are on, challenge and security state is off. Expand for details.";
            case "Custom" -> "Entries you added.";
            default -> fallback;
        };
    }

    private static void add(List<HeaderRules.Entry> entries, String group, boolean enabled, String comment, String names) {
        for (String name : names.split(" ")) {
            entries.add(new HeaderRules.Entry(new HeaderRule(name), enabled, true, group, comment));
        }
    }
}
