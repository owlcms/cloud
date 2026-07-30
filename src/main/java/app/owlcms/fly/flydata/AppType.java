package app.owlcms.fly.flydata;

import java.util.HashMap;
import java.util.Map;

public enum AppType {
    OWLCMS("owlcms/owlcms", "scripts/createOwlcms.sh", "https://api.github.com/repos/owlcms/releases/releases",
            "https://api.github.com/repos/owlcms/prereleases/releases", "owlcms.toml",
            "https://github.com/owlcms/releases/releases/latest",
        "https://github.com/owlcms/prereleases/releases/latest"),
    PUBLICRESULTS("owlcms/publicresults", "scripts/createPublicResults.sh",
            "https://api.github.com/repos/owlcms/releases/releases",
            "https://api.github.com/repos/owlcms/prereleases/releases", "publicresults.toml",
        "https://github.com/owlcms/releases/releases/latest",
        "https://github.com/owlcms/prereleases/releases/latest"),
    TRACKER("owlcms/tracker", "scripts/createTracker.sh", "https://api.github.com/repos/owlcms/tracker/releases",
            null, "tracker.toml", "https://github.com/owlcms/tracker/releases/latest"),
    DB("flyio/postgres-flex", null, null, null, null);

    public final String image;
    public final String create;
    public final String releaseApiUrl;
    public final String preReleaseApiUrl;
    public final String configFile;
    public final String[] fallbackReleaseUrls;

    private static final Map<String, AppType> BY_IMAGE = new HashMap<>();
    static {
        for (AppType e : values()) {
            BY_IMAGE.put(e.image, e);
        }
    }

    private AppType(String image, String create, String releaseApiUrl, String preReleaseApiUrl, String configFile,
            String... fallbackReleaseUrls) {
        this.image = image;
        this.create = create;
        this.releaseApiUrl = releaseApiUrl;
        this.preReleaseApiUrl = preReleaseApiUrl;
        this.configFile = configFile;
        this.fallbackReleaseUrls = fallbackReleaseUrls;
    }

    public String getConfigFile() {
        // toml files are at /app in the Docker container, or user.dir locally
        String projectRoot = System.getProperty("user.dir", "/app");
        if (!projectRoot.startsWith("/app") && new java.io.File("/app/" + configFile).exists()) {
            projectRoot = "/app";
        }
        return projectRoot + "/" + configFile;
    }

    public static AppType byImage(String image) {
        if (image == null) {
            return null;
        }
        AppType exact = BY_IMAGE.get(image);
        if (exact != null) {
            return exact;
        }
        String normalized = image.toLowerCase();
        // Check for tracker/fhq first (more specific)
        if (normalized.contains("tracker") || normalized.contains("fhq")) {
            return TRACKER;
        }
        // Check for publicresults before owlcms (publicresults contains "owlcms" prefix)
        if (normalized.contains("publicresults")) {
            return PUBLICRESULTS;
        }
        // Check for owlcms (most common)
        if (normalized.contains("owlcms")) {
            return OWLCMS;
        }
        // Check for postgres
        if (normalized.contains("postgres")) {
            return DB;
        }
        return null;
    }
}
