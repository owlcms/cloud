package app.owlcms.fly.flydata;

import java.util.HashSet;
import java.util.Set;

public class App implements Comparable<App> {

    // private static final Logger logger=LoggerFactory.getLogger(App.class);
    public AppType appType;
    public String name;
    public boolean created;
    public String regionCode;
    private VersionInfo versionInfo;
    private String deploymentVersion;
    private final Set<String> secretNames = new HashSet<>();
    public boolean stopped;
    public String machine;

    public App(String s, AppType appType, String region, String version, String machine, String status) {
        this.name = s;
        this.appType = appType;
        this.regionCode = region;
        if (appType != null && appType != AppType.DB && appType.releaseApiUrl != null
            && !appType.releaseApiUrl.isBlank()) {
            this.versionInfo = new VersionInfo(version, appType.releaseApiUrl, status != null,
                    appType.fallbackReleaseUrls);
        }
        this.machine = machine;
        if (status == null) {
            this.stopped = true;
        } else {
            String normalizedStatus = status.trim().toLowerCase();
            this.stopped = normalizedStatus.equals("stopped") || normalizedStatus.equals("suspended");
        }
    }

    @Override
    public int compareTo(App o) {
        return this.appType.compareTo(o.appType);
    }

    @Override
    public String toString() {
        String versionDescription = versionInfo == null ? "n/a"
                : versionInfo.getCurrentVersionString() + "/" + versionInfo.getCachedReferenceVersionString();
        return "App [appType=" + appType + ", name=" + name + ", regionCode=" + regionCode + ", versionInfo="
                + versionDescription + ", stopped=" + stopped + ", machine=" + machine + "]";
    }

    public String getCurrentVersion() {
        return versionInfo == null ? "unknown" : versionInfo.getCurrentVersionString();
    }

    public String getReferenceVersion() {
        return versionInfo == null ? "unknown" : versionInfo.getReferenceVersionString();
    }

    public String getDeploymentVersion() {
        return deploymentVersion == null ? getReferenceVersion() : deploymentVersion;
    }

    public void setDeploymentVersion(String deploymentVersion) {
        this.deploymentVersion = deploymentVersion;
    }

    public void addSecretName(String secretName) {
        secretNames.add(secretName);
    }

    public boolean hasSecretName(String secretName) {
        return secretNames.contains(secretName);
    }

    public Set<String> getSecretNames() {
        return Set.copyOf(secretNames);
    }

    public boolean isUpdateRequired() {
        return versionInfo != null && versionInfo.getComparison() != null && versionInfo.getComparison() < 0;
    }

    public VersionInfo getVersionInfo() {
        return versionInfo;
    }

    public void setVersionInfo(VersionInfo versionInfo) {
        this.versionInfo = versionInfo;
    }

}
