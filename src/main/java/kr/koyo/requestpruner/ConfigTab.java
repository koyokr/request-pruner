package kr.koyo.requestpruner;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

/** Header and cookie settings side by side, sharing the same rule editor. */
final class ConfigTab {
    private final JPanel component = new JPanel(new BorderLayout(0, 8));

    ConfigTab(HeaderRules headers, HeaderRules cookies) {
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new RulePanel(headers).component(), new RulePanel(cookies).component());
        split.setResizeWeight(0.5);
        split.setContinuousLayout(true);
        component.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        component.add(new JLabel("Right-click a request in Repeater or Proxy Intercept → Extensions › Request Pruner"
                + " to remove enabled headers and cookies."), BorderLayout.NORTH);
        component.add(split);
    }

    JPanel component() {
        return component;
    }
}
