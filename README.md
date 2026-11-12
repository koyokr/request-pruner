# Request Pruner

Remove unwanted headers and cookies from Burp Suite requests with one click. Requests are edited locally; the extension sends no traffic.

## Install

1. Download `request-pruner.jar` from [Releases](https://github.com/koyokr/request-pruner/releases/latest).
2. In Burp Suite, open **Extensions → Installed → Add**, select **Java**, and load the JAR.

Requires Java 21 or newer. Works with Burp Suite Professional and Community Edition.

## Use

In **Repeater** or **Proxy Intercept**, right-click a request and choose **Extensions → Request Pruner → Remove specified headers and cookies**. Review the changes, then send or forward the request yourself. Read-only views do not offer this action.

Common browser headers and analytics cookies are enabled for removal by default. The method, URL and body are retained. Removing headers or cookies can change server behavior; check the rule comments before enabling additional entries.

## Configure

Open the **Request Pruner** tab to toggle rules or add your own.

- Header names ignore case; cookie names are case-sensitive. Custom rules accept exact names or a trailing `*` wildcard.
- Filter by name or cookie vendor. Vendor checkboxes toggle the whole group.
- Double-click a custom rule's name or comment to edit it. Built-in rules can only be toggled.
- **Reset to defaults** restores built-in toggles and keeps custom rules. Settings are saved in the Burp project.

Identity, consent, A/B testing and Cloudflare challenge cookies are off by default. Some other Cloudflare cookies are enabled; removing them may affect routing, caching or queue position. See each rule's comment for details.

## Build

With JDK 21 or newer:

```sh
./gradlew clean build
```

On Windows, use `./gradlew.bat clean build`. Tests run as part of the build. The JAR is written to `build/libs/request-pruner.jar`.

Originally based on [PacketPurifier](https://github.com/PortSwigger/packet-purifier) by Isacaya.
