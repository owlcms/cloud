package app.owlcms.fly.ui;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.lumo.Lumo;

/**
 * Use the @PWA annotation make the application installable on phones, tablets
 * and some desktop browsers.
 */
@Push
// Styles are loaded in this order: the Lumo theme, then the application styles
// from src/main/resources/META-INF/resources/styles.css
@StyleSheet(Lumo.STYLESHEET)
@StyleSheet("styles.css")
public class AppShell implements AppShellConfigurator {
}
