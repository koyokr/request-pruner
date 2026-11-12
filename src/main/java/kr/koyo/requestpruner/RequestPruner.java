package kr.koyo.requestpruner;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.InvocationType;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse.SelectionContext;
import java.awt.Component;
import java.util.List;
import javax.swing.JMenuItem;

public class RequestPruner implements BurpExtension, ContextMenuItemsProvider {
    private MontoyaApi api;
    private HeaderRules rules;
    private HeaderRules cookies;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        api.extension().setName("Request Pruner");
        var version = RequestPruner.class.getPackage().getImplementationVersion();
        api.logging().logToOutput("Loaded Request Pruner v" + (version != null ? version : "dev"));
        rules = new HeaderRules(api.persistence().extensionData());

        cookies = new HeaderRules(api.persistence().extensionData(), true);
        var tab = new ConfigTab(rules, cookies).component();
        api.userInterface().applyThemeToComponent(tab);
        api.userInterface().registerSuiteTab("Request Pruner", tab);
        api.userInterface().registerContextMenuItemsProvider(this);
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        // Editable request editors only: Repeater and Proxy Intercept. Read-only viewers such as
        // Proxy history report MESSAGE_VIEWER_REQUEST and are skipped.
        if (!event.isFrom(InvocationType.MESSAGE_EDITOR_REQUEST, InvocationType.PROXY_INTERCEPT)) return List.of();
        var editor = event.messageEditorRequestResponse().orElse(null);
        if (editor == null || editor.selectionContext() != SelectionContext.REQUEST) return List.of();

        var item = new JMenuItem("Remove specified headers and cookies");
        item.addActionListener(e -> {
            HttpRequest request = editor.requestResponse().request();
            var result = RequestCleaner.clean(request, rules.active(), cookies.active());
            if (result.request() != request) editor.setRequest(result.request());
            api.logging().logToOutput("Removed " + count(result.headers(), "header") + " and "
                    + count(result.cookies(), "cookie") + ".");
        });
        return List.of(item);
    }

    private static String count(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }
}
