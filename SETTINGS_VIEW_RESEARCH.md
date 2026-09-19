# RuneLite settings view research — 2026-09-18

Scope: the standard searchable plugin gear screen, not a custom PluginPanel. Checked current official source and the locally used RuneLite 1.12.39 ConfigItem API. Supported UI behavior is not a guarantee of Plugin Hub approval.

## Native layout and controls

RuneLite owns the header, toggle, scrolling, Reset and Back. Items follow a fixed vertical layout; plugins supply descriptors rather than arbitrary Swing components. Sections have orange bold headings, separator borders, expandable contents and a default-open/closed choice. Item position, section and hidden status control organization. Descriptions appear as tooltips.

Built-in editors include booleans, integer/double spinners, strings/password fields, colors, dimensions, enums, keybinds, enum collections, notifications and font settings. Numeric ranges/units refine existing controls. Void informational items currently render their label without an editor. No native arbitrary-action button, image picker for branding, custom card, live-status component or hyperlink handler is exposed through ConfigItem. Do not add pretend settings solely to act as buttons.

Evidence: [ConfigPanel renderer](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/config/ConfigPanel.java), [ConfigItem](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/config/ConfigItem.java), [ConfigSection](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/config/ConfigSection.java).

## Text and image limits

Labels use Swing HTML, not a browser. Bold/italic, line breaks, basic colors and simple layout are practical. Browser JavaScript and modern browser styling are not available. Font size, wrapping and special characters require visual confirmation. An HTML anchor in a JLabel is not an interactive browser link.

HTML image rendering and a supported packaged-logo extension are different things. An img tag needs a resolvable image URL; ConfigItem has no icon field or runtime resource setter. A fixed local file path would not be portable. Remote image markup introduces network loading and must not be assumed to satisfy RuneLite's HTTP/privacy requirements. We have not implemented a remote logo or modified the core panel to inject one. Earlier claims that all images were impossible, and that our packaged logo would trivially work, were both too broad.

Evidence: [Oracle Swing HTML documentation](https://docs.oracle.com/javase/tutorial/uiswing/components/html.html), [ConfigItem annotation fields](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/config/ConfigItem.java).

## Links and branding elsewhere

Right-clicking a Hub plugin's settings title exposes Support, which opens its Plugin Hub page. The support URL is constructed by RuneLite; it is not a configurable direct Discord link. A linked Discord invite can live in the repository README reached through the plugin information. The Plugin Hub supports a separate repository-root icon.png no larger than 48x72 pixels; this brands the listing, not the settings body. We did not change Hub metadata or add an icon in this task.

Evidence: [Support menu implementation](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/config/PluginConfigurationDescriptor.java), [Plugin Hub packaging instructions](https://github.com/runelite/plugin-hub#creating-new-plugins).

## Applied choice

One open native section, concise purpose, community heading/invite and muted tagline. The existing bosscapeInformation key is unchanged. No sidebar, remote asset loading, reflection, core-panel modifications or tracking changes. Java checks cannot confirm appearance; the owner must inspect the gear screen after restart.
