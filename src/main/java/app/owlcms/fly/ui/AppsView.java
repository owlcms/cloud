package app.owlcms.fly.ui;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinServletRequest;

import app.owlcms.fly.commands.CreationErrorException;
import app.owlcms.fly.commands.FlyCtlCommands;
import app.owlcms.fly.commands.NameTakenException;
import app.owlcms.fly.flydata.App;
import app.owlcms.fly.flydata.AppType;
import app.owlcms.fly.flydata.EarthLocation;
import app.owlcms.fly.flydata.GeoLocator;
import app.owlcms.fly.flydata.VersionInfo;
import jakarta.servlet.http.HttpServletRequest;

/**
 * The main view contains a text field for getting the user name and a button
 * that shows a greeting message in a
 * notification.
 */
@Route("apps")
public class AppsView extends VerticalLayout {

	private static final String LEFT_LABEL_WIDTH = "14em";
	private static final String APP_DETAILS_WIDTH = "20em";
	private static final String APP_CONTROLS_WIDTH = "42em";
	private static final String CLOUD_OWLCMS_SELECTOR_WIDTH = "12em";
	private static final boolean SHOW_PUBLICRESULTS = false;
	private long lastClick = 0;
	@SuppressWarnings("unused")
	final private Logger logger = LoggerFactory.getLogger(AppsView.class);
	private LogDialog logDialog;
	private FlyCtlCommands flyCommands;
	private VerticalLayout intro;
	private VerticalLayout apps;
	private String regionCode;
	private String clientIpString;
	
	public AppsView() {
		clientIpString = getClientIp();
		logDialog = new LogDialog();
		flyCommands = new FlyCtlCommands(UI.getCurrent(), logDialog);
		if (flyCommands.getToken() == null) {
			Login loginOverlay = new Login(flyCommands);
			loginOverlay.setCallback(() -> {
				getServerLocations();
				loginOverlay.setOpened(false);
				this.removeAll();
				showApplicationView();
			});
			loginOverlay.setOpened(true);
			add(loginOverlay);
		} else {
			getServerLocations();
			showApplicationView();
		}
	}

	private void getServerLocations() {
		EarthLocation clientIpLocation;
		clientIpLocation = GeoLocator.locate(clientIpString);
		// list is sorted with closest region at the top
		serverLocations = flyCommands.getServerLocations(clientIpLocation);
	}

	private void showApplicationView() {
		this.setSpacing(false);
		this.setHeightFull();
		H2 title = new H2("owlcms Cloud Applications - " + flyCommands.getUserName());
		Button logoutButton = new Button("Logout", (e) -> {
			flyCommands.setToken(null);
			Notification.show("Logged out", 1000, Position.TOP_END);
			UI.getCurrent().navigate("");
		});

		intro = buildIntro();
		apps = buildAppsPlaceholder();
		add(new HorizontalLayout(title, logoutButton), intro, apps);
		UI ui = UI.getCurrent();
		doListApplications(apps, ui);
	}

	private VerticalLayout buildAppsPlaceholder() {
		VerticalLayout apps = new VerticalLayout();
		apps.setMargin(false);
		apps.setPadding(false);
		return apps;
	}

	private ConfirmDialog buildDeletionDialog(App app, App database, Runnable callback) {
		ConfirmDialog deletionDialog = new ConfirmDialog();
		deletionDialog.setHeader("Deletion Confirmation Required");
		if (app.appType == AppType.OWLCMS) {
			deletionDialog.setText(new Html(
					"""
							<div>
							   This will remove the application and make the name available again.
							   <br />
							   NOTE: the database will also be deleted; make sure you have
							   exported the database if you need to keep the information.
							</div>
							"""));
		} else {
			deletionDialog.setText(new Html(
					"""
							<div>
							   This will remove the application and make the name available again.
							</div>
							"""));
		}

		deletionDialog.setConfirmText("Delete");
		deletionDialog.setConfirmButtonTheme("error primary");
		deletionDialog.setCancelButtonTheme("primary");
		deletionDialog.setCancelable(true);
		deletionDialog.setCancelText("Cancel");
		deletionDialog.addConfirmListener(e -> {

			if (app.appType == AppType.OWLCMS) {
				if (database != null) {
				logDialog.append("Deleting OWLCMS " + app.name, UI.getCurrent());
				flyCommands.appDestroy(app, null);
				logDialog.append("Deleting OWLCMS database " + database.name, UI.getCurrent());
				flyCommands.appDestroy(database, callback);
			} else {
				logDialog.append("Deleting OWLCMS - no database " + app.name, UI.getCurrent());
					flyCommands.appDestroy(app, callback);
				}
			} else {
				logDialog.append("Deleting PUBLICRESULTS " + app.name, UI.getCurrent());
				flyCommands.appDestroy(app, callback);
			}
		});
		deletionDialog.addCancelListener(e -> {
			deletionDialog.close();
		});
		return deletionDialog;
	}

	private ConfirmDialog buildStopDialog(App app, App database, Runnable callback) {
		ConfirmDialog stopDialog = new ConfirmDialog();
		stopDialog.setHeader("Stop Confirmation Required");
		if (app.appType == AppType.OWLCMS) {
			stopDialog.setText(new Html(
					"""
							<div>
							   This will stop the application and minimize further billing.
							   <br />
							   You will only be billed a very small amount for disk space.
							   NOTE: the database will also be stopped, but not deleted.
							   <br />
							   To completely stop billing, export your database and delete the application.
							</div>
							"""));
		} else {
			stopDialog.setText(new Html(
					"""
							<div>
							   This will stop the application and stop further billing.
							   <br />
							   You will only be billed a very small amount for disk space.
							   <br />
							   To completely stop billing, delete the application.
							</div>
							"""));
		}

		stopDialog.setConfirmText("Stop");
		stopDialog.setConfirmButtonTheme("error primary");
		stopDialog.setCancelButtonTheme("primary");
		stopDialog.setCancelable(true);
		stopDialog.setCancelText("Cancel");
		stopDialog.addConfirmListener(e -> {

			if (app.appType == AppType.OWLCMS) {
				if (database != null) {
				logDialog.append("Suspending OWLCMS " + app.name, UI.getCurrent());
				flyCommands.appStop(app, null);
				logDialog.append("Suspending OWLCMS database " + database.name, UI.getCurrent());
				flyCommands.appStop(database, callback);
			} else {
				logDialog.append("Suspending OWLCMS - no database " + app.name, UI.getCurrent());
					flyCommands.appStop(app, callback);
				}
			} else if (app.appType == AppType.TRACKER) {
				logDialog.append("Suspending TRACKER " + app.name, UI.getCurrent());
				flyCommands.appStop(app, callback);
			} else {
				logDialog.append("Suspending PUBLICRESULTS " + app.name, UI.getCurrent());
				flyCommands.appStop(app, callback);
			}
		});
		stopDialog.addCancelListener(e -> {
			stopDialog.close();
		});
		return stopDialog;
	}

	private VerticalLayout buildIntro() {
		Html p1 = new Html(
				"""
						<div style="width: 60em">
						This page creates and manages your owlcms applications on the fly.io cloud.
						</div>
						""");
		VerticalLayout intro = new VerticalLayout(p1);
		intro.setSpacing(false);
		intro.setPadding(false);
		intro.setMargin(false);
		intro.getStyle().set("margin-top", "1em");
		return intro;
	}

	public String getClientIp() {
		HttpServletRequest request;
		VaadinServletRequest current = VaadinServletRequest.getCurrent();
		request = current.getHttpServletRequest();
		String remoteAddr = "";
		if (request != null) {
			remoteAddr = request.getHeader("X-FORWARDED-FOR");
			if (remoteAddr == null || "".equals(remoteAddr)) {
				remoteAddr = request.getRemoteAddr();
			} else if (remoteAddr.contains(", ")) {
				String[] remoteAddresses = remoteAddr.split(", ");
				remoteAddr = remoteAddresses[0];
			}
		}
		return remoteAddr;
	}

	EarthLocation serverLoc = null;
	private List<EarthLocation> serverLocations;

	private void doListApplications(VerticalLayout appsArea, UI ui) {
		long timeMillis = System.currentTimeMillis();
		if (timeMillis - lastClick < 100) {
			lastClick = timeMillis;
			return;
		}
		lastClick = timeMillis;

		appsArea.removeAll();
		logDialog.clear(ui);
		logDialog.show();
		logDialog.append("Retrieving your application configurations. Please wait.", ui);
		ui.push();

		new Thread(() -> {
			// ui.access(() -> {
			// this also retrieves the region for the applications if available
			List<App> appsList = flyCommands.getApps();
			regionCode = getRegionCode(appsList);
			preloadStableVersions();

			ui.access(() -> {
				showApps(appsList, appsArea);
				logDialog.clear(ui);
				logDialog.hide();
			});
		}).start();
	}

	private void doSilentListRefresh(VerticalLayout appsArea, UI ui) {
		new Thread(() -> {
			List<App> appsList = flyCommands.getApps();
			regionCode = getRegionCode(appsList);

			ui.access(() -> {
				appsArea.removeAll();
				showApps(appsList, appsArea);
				logDialog.hide();
			});
		}).start();
	}

	private Div showApplication(App app, App database, List<App> appList, boolean showExplanation,
			boolean showLabel) {
		HorizontalLayout appSection = new HorizontalLayout();
		appSection.setMargin(false);
		appSection.setPadding(false);
		appSection.setAlignItems(Alignment.START);

		// Left column: label
		NativeLabel label = new NativeLabel(showLabel ? app.appType.toString() : "");
		label.setWidth(LEFT_LABEL_WIDTH);
		appSection.add(label);

		// Right column: all content (explanation, version, controls)
		VerticalLayout contentDiv = new VerticalLayout();
		contentDiv.setMargin(false);
		contentDiv.setPadding(false);
		contentDiv.setSpacing(false);

		if (showExplanation) {
			contentDiv.add(new Html(getExplanationForAppType(app.appType)));
		}

		UI ui = UI.getCurrent();

		if (app.created) {
			showExistingApplication(app, database, contentDiv, appList, ui);
		} else {
			showNewApplication(app, contentDiv, ui);
		}
		appSection.add(contentDiv);

		Div wrapper = new Div(appSection);
		wrapper.getStyle().set("margin-bottom", "0");
		return wrapper;
	}

	private String getExplanationForAppType(AppType appType) {
		return switch (appType) {
			case OWLCMS ->
				"""
					<ul style="line-height: 1.4; width: 45em; margin: 0; padding-left: 1em;">
						<li>OWLCMS runs the competition and drives the screens used at the site.
						<li><u>You don't need OWLCMS in the cloud if you are running OWLCMS on a laptop at the site</u> and only want remote scoreboards.
					</ul>
				""";
				case TRACKER ->
				"""
					<ul style="line-height: 1.4; width: 45em; margin: 0; padding-left: 1em;">
						<li>When running in the cloud, TRACKER is used to show scoreboards to anyone on the internet, or to produce custom documents and displays
						<li><u>You don't need TRACKER in the cloud if you don't want remote scoreboards or custom outputs.</u>
						<li>Select a cloud OWLCMS from the ones shown above to connect it to this tracker
						<li>If using a competition-site laptop, just set the key.
					</ul>
				""";
			case PUBLICRESULTS ->
				"""
					<ul style="line-height: 1.4; width: 45em; margin: 0; padding-left: 1em;">
						<li>PUBLICRESULTS is the legacy way is used to view scoreboards from the internet.
						<li>PUBLICRESULTS is being replaced by TRACKER, which provides more features.</li>
						<li>The Shared Key set at the bottom of this page protects the communications between OWLCMS and PUBLICRESULTS.
					</ul>
				""";
			default -> "";
		};
	}

	private void showNewApplication(App app, VerticalLayout contentDiv, UI ui) {
		HorizontalLayout newApplicationLayout = new HorizontalLayout();
		newApplicationLayout.setMargin(false);
		newApplicationLayout.setPadding(false);
		newApplicationLayout.setAlignItems(Alignment.END);

		VerticalLayout details = new VerticalLayout();
		details.setMargin(false);
		details.setPadding(false);
		details.setSpacing(false);
		details.setWidth(APP_DETAILS_WIDTH);

		ComboBox<String> versionSelector = new ComboBox<>("Version to install");
		versionSelector.setWidth("20em");
		VerticalLayout versionControls = createVersionControls(app, ui, versionSelector);

		TextField nameField = new TextField("Application Name (without .fly.dev)");
		nameField.setAllowedCharPattern("[A-Za-z0-9-]");
		nameField.setValue(app.name);
		nameField.setPlaceholder("Letters, numbers and hyphens");
		nameField.setWidth("20em");
		nameField.setRequired(true);
		nameField.setRequiredIndicatorVisible(true);

		ComboBox<EarthLocation> serverCombo = new ComboBox<>();

		serverCombo.setRenderer(new TextRenderer<>(EarthLocation::getFullName));
		serverCombo.setItemLabelGenerator(EarthLocation::getFullName);
		serverCombo.setLabel("Select a server location");
		serverCombo.setWidth("20em");
		serverCombo.setItems(serverLocations);

		if (regionCode != null) {
			serverLoc = serverLocations.stream().filter(l -> regionCode.contentEquals(l.getCode())).findAny()
					.orElse(null);
		} else {
			serverLoc = serverLocations.get(0);
		}
		serverCombo.setValue(serverLoc);
		details.add(nameField, serverCombo);

		Button creationButton = new Button("Create",
				e -> {
					String value = nameField.getValue();
					if (value == null || value.isBlank()) {
						nameField.setErrorMessage("You must provide a value");
						nameField.setInvalid(true);
					} else {
						String siteName = value.toLowerCase() + ".fly.dev";

						try {
							flyCommands.createApp(value.toLowerCase());
							nameField.setInvalid(false);
							app.name = value.toLowerCase();
							app.regionCode = serverCombo.getValue().getCode();
							app.setDeploymentVersion(versionSelector.getValue());
						flyCommands.appCreate(app, () -> doSilentListRefresh(apps, ui));
						} catch (NameTakenException e1) {
							nameField.setErrorMessage(siteName + " is already taken.");
							nameField.setInvalid(true);
						} catch (CreationErrorException e1) {
							nameField.setErrorMessage(e1.getMessage());
							nameField.setInvalid(true);
						}
					}
				});
		HorizontalLayout deploymentControls = new HorizontalLayout(versionControls, creationButton);
		deploymentControls.setMargin(false);
		deploymentControls.setPadding(false);
		deploymentControls.setAlignItems(Alignment.END);
		newApplicationLayout.add(details, deploymentControls);
		contentDiv.add(newApplicationLayout);
	}

	private void showExistingApplication(App app, App database, VerticalLayout contentDiv, List<App> appList, UI ui) {
		HorizontalLayout existingLayout = new HorizontalLayout();
		existingLayout.addClassName("existingApp");
		existingLayout.setMargin(false);
		existingLayout.setPadding(false);
		existingLayout.setAlignItems(Alignment.START);
		existingLayout.getStyle().set("margin-top", "1em");
		Anchor a = new Anchor("https://" + app.name + ".fly.dev", app.name + ".fly.dev", AnchorTarget.BLANK);
		a.getStyle().set("text-decoration", "underline");
		String websocketUrl = "wss://" + app.name + ".fly.dev/ws";
		String rawVersion = app.getCurrentVersion();
		String displayVersion = rawVersion + (rawVersion.matches("^[0-9].*$") ? "" : " (version number unknown)");
		List<String> stableVersions = getCachedSelectableVersions(app, false);
		String latestVersion = stableVersions.isEmpty() ? "unknown" : stableVersions.get(0);
		boolean updateRequired = app.isUpdateRequired();
		VerticalLayout versionInfo = new VerticalLayout(a);
		if (app.appType == AppType.TRACKER) {
			versionInfo.add(new NativeLabel("websocket: " + websocketUrl));
		}
		if (app.appType == AppType.OWLCMS) {
			versionInfo.add(new NativeLabel(database == null ? "database: not found" : "database: " + database.name));
		}
		versionInfo.add(
				new Html(
						"""
								<div>your version: %s<br />latest version: %s<span style="color:red">%s</span><br/> region: %s</div>
								"""
								.formatted(
										displayVersion,
										latestVersion,
										updateRequired ? " Please Update" : "",
										app.regionCode)));
		versionInfo.setMargin(false);
		versionInfo.setPadding(false);
		versionInfo.setSpacing(false);
		versionInfo.setWidth(APP_DETAILS_WIDTH);

		VerticalLayout rightControls = new VerticalLayout();
		rightControls.setMargin(false);
		rightControls.setPadding(false);
		rightControls.setSpacing(false);
		rightControls.setWidth(APP_CONTROLS_WIDTH);
		ComboBox<String> versionSelector = new ComboBox<>("Version to install");
		versionSelector.setWidth("10em");
		VerticalLayout versionControls = createVersionControls(app, ui, versionSelector);

		HorizontalLayout actionControls = new HorizontalLayout();
		actionControls.setMargin(false);
		actionControls.setPadding(false);
		actionControls.setAlignItems(Alignment.CENTER);

		Button updateButton = new Button("Update",
			e -> {
				app.setDeploymentVersion(versionSelector.getValue());
				flyCommands.appDeploy(app, () -> doSilentListRefresh(apps, ui));
			});
		if (updateRequired) {
			updateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		}
		actionControls.add(updateButton);

		Button restartButton = new Button("Restart",
				e -> {
					flyCommands.appRestart(app, database);
			});
		actionControls.add(restartButton);

		ConfirmDialog deletionDialog = buildDeletionDialog(app, database,
				() -> doListApplications(apps, ui));
		Button deleteButton = new Button("Delete");
		deleteButton.addClickListener(event -> {
			deletionDialog.open();
		});
		actionControls.add(deleteButton);

		if (!app.stopped) {
			ConfirmDialog stopDialog = buildStopDialog(app, database,
					() -> {
						try {
							Thread.sleep(2000);
						} catch (InterruptedException e1) {
						}
						doListApplications(apps, ui);
					});
			Button stopButton = new Button("Stop");
			stopButton.addClickListener(event -> {
				stopDialog.open();
			});
			actionControls.add(stopButton);
		}
		HorizontalLayout deploymentControls = new HorizontalLayout(versionControls, actionControls);
		deploymentControls.setMargin(false);
		deploymentControls.setPadding(false);
		deploymentControls.setAlignItems(Alignment.END);
		rightControls.add(deploymentControls);
		if (app.appType == AppType.TRACKER) {
			rightControls.add(showTrackerKeyControls(app, appList));
		}
		existingLayout.add(versionInfo, rightControls);
		contentDiv.add(existingLayout);
	}

	private void showApps(List<App> appList, VerticalLayout apps) {
		if (intro != null) {
			intro.setVisible(false);
		}
		apps.add(new Hr());
		showApplications(apps, appList, AppType.OWLCMS);
		apps.add(new Hr());
		showApplications(apps, appList, AppType.TRACKER);

		if (SHOW_PUBLICRESULTS) {
			apps.add(new Hr());
			showApplications(apps, appList, AppType.PUBLICRESULTS);
		}
	}

	private HorizontalLayout showTrackerKeyControls(App tracker, List<App> appList) {
		List<App> owlcmsApps = appList.stream().filter(app -> app.appType == AppType.OWLCMS)
				.sorted(Comparator.comparing(app -> app.name)).toList();
		HorizontalLayout keyControls = new HorizontalLayout();
		keyControls.setAlignItems(Alignment.END);
		keyControls.setPadding(false);
		keyControls.setMargin(false);
		keyControls.getStyle().set("margin-top", "0.5em");

		ComboBox<App> owlcmsSelector = new ComboBox<>("Cloud OWLCMS (optional)");
		owlcmsSelector.setItems(owlcmsApps);
		owlcmsSelector.setItemLabelGenerator(owlcms -> owlcms.name);
		owlcmsSelector.setClearButtonVisible(true);
		owlcmsSelector.setWidth(CLOUD_OWLCMS_SELECTOR_WIDTH);

		TextField sharedKeyField = new TextField("Shared Key");
		sharedKeyField.setWidth("15em");
		sharedKeyField.setPlaceholder("Enter a shared string");
		Button generateKeyButton = new Button("Generate Key", event -> sharedKeyField.setValue(generateRandomString(20)));
		Button keyButton = new Button("Apply Key", event -> {
			App owlcms = owlcmsSelector.getValue();
			String sharedKey = sharedKeyField.getValue();
			if (sharedKey == null || sharedKey.isBlank()) {
				sharedKeyField.setErrorMessage("A shared key is required for this Tracker");
				sharedKeyField.setInvalid(true);
				return;
			}
			ConfirmDialog confirmation = new ConfirmDialog();
			confirmation.setHeader("Apply Tracker key?");
			confirmation.setText(owlcms == null
					? "Configure " + tracker.name + " to expect this key."
					: owlcms.name + " will now connect to this tracker.");
			confirmation.setConfirmText("Apply Key");
			confirmation.setCancelText("Cancel");
			confirmation.setCancelable(true);
			confirmation.addConfirmListener(confirm -> flyCommands.configureTrackerConnection(tracker, owlcms, sharedKey,
					() -> doSilentListRefresh(apps, UI.getCurrent())));
			confirmation.open();
		});
		Button disconnectButton = new Button("Disconnect", event -> {
			App owlcms = owlcmsSelector.getValue();
			if (owlcms == null) {
				return;
			}
			ConfirmDialog confirmation = new ConfirmDialog();
			confirmation.setHeader("Disconnect OWLCMS?");
			confirmation.setText(owlcms.name + " will no longer connect to this tracker.");
			confirmation.setConfirmText("Disconnect");
			confirmation.setCancelText("Cancel");
			confirmation.setCancelable(true);
			confirmation.addConfirmListener(confirm -> flyCommands.disconnectTrackerConnection(tracker, owlcms,
					() -> doSilentListRefresh(apps, UI.getCurrent())));
			confirmation.open();
		});
		disconnectButton.setVisible(false);
		Runnable updateConnectionActions = () -> {
			App owlcms = owlcmsSelector.getValue();
			boolean connected = owlcms != null && flyCommands.hasTrackerConnection(tracker, owlcms);
			boolean replacementKeyProvided = !sharedKeyField.isEmpty();
			sharedKeyField.setPlaceholder(connected ? "(hidden)" : "Enter a shared string");
			keyButton.setText(owlcms == null ? "Apply Key" : "Connect");
			keyButton.setVisible(!connected || replacementKeyProvided);
			disconnectButton.setVisible(connected && !replacementKeyProvided);
		};
		boolean[] restoringOwlcmsSelection = {false};
		owlcmsSelector.addValueChangeListener(event -> {
			if (!restoringOwlcmsSelection[0] && event.getValue() == null && event.getOldValue() != null
					&& flyCommands.hasTrackerConnection(tracker, event.getOldValue())) {
				App connectedOwlcms = event.getOldValue();
				ConfirmDialog confirmation = new ConfirmDialog();
				confirmation.setHeader("Disconnect OWLCMS?");
				confirmation.setText(connectedOwlcms.name + " will no longer connect to this tracker.");
				confirmation.setConfirmText("Disconnect");
				confirmation.setCancelText("Cancel");
				confirmation.setCancelable(true);
				confirmation.addConfirmListener(confirm -> flyCommands.disconnectTrackerConnection(tracker, connectedOwlcms,
						() -> doSilentListRefresh(apps, UI.getCurrent())));
				confirmation.addCancelListener(cancel -> {
					restoringOwlcmsSelection[0] = true;
					owlcmsSelector.setValue(connectedOwlcms);
					restoringOwlcmsSelection[0] = false;
				});
				confirmation.open();
			}
			updateConnectionActions.run();
		});
		sharedKeyField.addValueChangeListener(event -> updateConnectionActions.run());
		owlcmsApps.stream().filter(owlcms -> flyCommands.hasTrackerConnection(tracker, owlcms)).findFirst()
				.ifPresent(owlcmsSelector::setValue);
		updateConnectionActions.run();
		keyControls.add(owlcmsSelector, sharedKeyField, generateKeyButton, keyButton, disconnectButton);
		return keyControls;
	}

	private void showApplications(VerticalLayout apps, List<App> appList, AppType appType) {
		VerticalLayout section = new VerticalLayout();
		section.setMargin(false);
		section.setPadding(false);
		section.setSpacing(false);

		List<App> appsOfType = appList.stream().filter(app -> app.appType == appType)
				.sorted(Comparator.comparing(app -> app.name)).toList();
		for (int index = 0; index < appsOfType.size(); index++) {
			App app = appsOfType.get(index);
			boolean showExplanation = appType != AppType.OWLCMS || index == 0;
			section.add(showApplication(app, findDatabase(app, appList), appList, showExplanation, index == 0));
		}

		Button addButton = new Button("+ Add Another", event -> {
			App newApp = new App("", appType, getCurrentRegion(), "stable", null, null);
			boolean showExplanation = appType != AppType.OWLCMS || section.getComponentCount() == 1;
			section.addComponentAtIndex(section.getComponentCount() - 1,
					showApplication(newApp, null, appList, showExplanation, section.getComponentCount() == 1));
		});
		section.add(addButton);
		apps.add(section);
	}

	private App findDatabase(App app, List<App> appList) {
		if (app.appType != AppType.OWLCMS) {
			return null;
		}
		return appList.stream().filter(candidate -> candidate.appType == AppType.DB)
				.filter(candidate -> candidate.name.equals(app.name + "-db")).findFirst().orElse(null);
	}

	private VerticalLayout createVersionControls(App app, UI ui, ComboBox<String> versionSelector) {
		setSelectableVersions(versionSelector, getCachedSelectableVersions(app, false), app.getCurrentVersion());
		Checkbox showPrereleases = new Checkbox("Show Prereleases");
		showPrereleases.addValueChangeListener(event -> loadSelectableVersions(app, event.getValue(), versionSelector,
				showPrereleases, ui));
		VerticalLayout versionControls = new VerticalLayout(versionSelector, showPrereleases);
		versionControls.setMargin(false);
		versionControls.setPadding(false);
		versionControls.setSpacing(false);
		return versionControls;
	}

	private void loadSelectableVersions(App app, boolean showPrereleases, ComboBox<String> versionSelector,
			Checkbox showPrereleasesCheckbox, UI ui) {
		versionSelector.setEnabled(false);
		new Thread(() -> {
			VersionInfo.fetchReleaseVersions(app.appType.releaseApiUrl, app.appType.preReleaseApiUrl, showPrereleases,
					app.appType.fallbackReleaseUrls);
			List<String> versions = getCachedSelectableVersions(app, showPrereleases);
			ui.access(() -> {
				if (showPrereleasesCheckbox.getValue() == showPrereleases) {
					setSelectableVersions(versionSelector, versions, app.getCurrentVersion());
				}
				versionSelector.setEnabled(true);
			});
		}).start();
	}

	private List<String> getCachedSelectableVersions(App app, boolean showPrereleases) {
		return VersionInfo.getCachedReleaseVersions(app.appType.releaseApiUrl, showPrereleases);
	}

	private void setSelectableVersions(ComboBox<String> versionSelector, List<String> versions,
			String fallbackVersion) {
		String selectedVersion = versions.isEmpty() ? fallbackVersion : versions.get(0);
		versionSelector.setItems(versions.isEmpty() ? List.of(selectedVersion) : versions);
		versionSelector.setValue(selectedVersion);
	}

	private void preloadStableVersions() {
		for (AppType appType : List.of(AppType.OWLCMS, AppType.TRACKER)) {
			VersionInfo.fetchReleaseVersions(appType.releaseApiUrl, appType.preReleaseApiUrl, false,
					appType.fallbackReleaseUrls);
		}
	}

	private String getRegionCode(List<App> appList) {
		return appList.stream().filter(app -> app.appType != AppType.DB).map(app -> app.regionCode)
				.filter(region -> region != null && !region.isBlank()).findFirst().orElse(null);
	}

	private String getCurrentRegion() {
		return null;
	}

	// Define printable characters
	private static final String PRINTABLE_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()-_=+[]{}|;:,.<>?";

	public String generateRandomString(int length) {
		SecureRandom random = new SecureRandom();
		StringBuilder stringBuilder = new StringBuilder(length);

		for (int i = 0; i < length; i++) {
			int randomIndex = random.nextInt(PRINTABLE_CHARACTERS.length());
			char randomChar = PRINTABLE_CHARACTERS.charAt(randomIndex);
			stringBuilder.append(randomChar);
		}

		return stringBuilder.toString();
	}

}
