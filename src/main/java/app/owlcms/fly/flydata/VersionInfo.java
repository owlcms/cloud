package app.owlcms.fly.flydata;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vdurmont.semver4j.Semver;

import ch.qos.logback.classic.Logger;

public class VersionInfo {
	private String referenceVersionString;
	private String currentVersionString;
	private String apiUrl;
	final private static Logger logger = (Logger) LoggerFactory.getLogger(VersionInfo.class);
	private Integer comparison;
	
	// Static cache to avoid repeated fetches for the same API URL (expires after 2 minutes)
	private static final Map<String, String> versionCache = new HashMap<>();
	private static final Map<String, Long> cacheTimestamps = new HashMap<>();
	private static final long CACHE_EXPIRY_MS = 2 * 60 * 1000; // 2 minutes (GitHub allows 60 requests/hour)

	public VersionInfo(String currentVersionString) {
		this(currentVersionString, "https://api.github.com/repos/owlcms/owlcms4/releases");
	}

	public VersionInfo(String currentVersionString, String apiUrl) {
		this.currentVersionString = currentVersionString;
		this.apiUrl = apiUrl;
		this.updateReferenceVersionString();
	}

	public void updateReferenceVersionString(boolean preRelease) {
		this.referenceVersionString = fastFetchLatestReleaseVersion(apiUrl);

		if (!"latest".equals(currentVersionString)) {
			ComparableVersion currentVersion = new ComparableVersion(this.currentVersionString);
			ComparableVersion referenceVersion = new ComparableVersion(this.referenceVersionString);
			this.comparison = currentVersion.compareTo(referenceVersion);
		} else {
			this.comparison = -1;
		}
	}

	public void updateReferenceVersionString() {
		updateReferenceVersionString(
				this.currentVersionString.contains("-") || this.currentVersionString.contentEquals("prerelease"));
	}

	public String getReferenceVersionString() {
		if (referenceVersionString == null) {
			updateReferenceVersionString();
		}
		return referenceVersionString;
	}

	public Integer getComparison() {
		return comparison;
	}

	public String getCurrentVersionString() {
		return currentVersionString;
	}

	public static String fullFetchLatestReleaseVersion(String apiUrl) {
		long now = System.currentTimeMillis();
		try {
			URL url = new URL(apiUrl);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

			if (conn.getResponseCode() != 200) {
			logger.info("Failed to fetch /releases from {}, HTTP error code: {}", apiUrl, conn.getResponseCode());
			return "unknown";
		}

		Scanner scanner = new Scanner(url.openStream());
			String inline = "";
			while (scanner.hasNext()) {
				inline += scanner.nextLine();
			}
			scanner.close();

			JsonParser parser = new JsonParser();
			List<JsonObject> releases = new ArrayList<>();
			parser.parse(inline).getAsJsonArray().forEach(jsonElement -> releases.add(jsonElement.getAsJsonObject()));

			List<Semver> versions = new ArrayList<>();
			for (JsonObject release : releases) {
				String tagName = release.get("tag_name").getAsString();
				try {
					versions.add(new Semver(tagName));
				} catch (Exception e) {
					logger.debug("Skipping invalid semver tag: {}", tagName);
				}
			}

			if (versions.isEmpty()) {
			logger.debug("No valid semantic versions found in releases from {}", apiUrl);
			return "unknown";
		}

		Collections.sort(versions, Comparator.reverseOrder());
		logger.info("fullFetchLatestReleaseVersion took {} ms for {} valid versions", System.currentTimeMillis() - now, versions.size());
		return versions.get(0).getValue();

	} catch (IOException e) {
		logger.debug("Error fetching latest release version from {}: {}", apiUrl, e.getMessage());
		return "unknown";
	} catch (Exception e) {
		logger.debug("Unexpected error fetching latest release version from {}: {}", apiUrl, e.getMessage());
		return "unknown";
		}
	}

	public static String fastFetchLatestReleaseVersion(String apiUrl) {
		// Check cache first (with expiry)
		Long cachedTime = cacheTimestamps.get(apiUrl);
		if (cachedTime != null && (System.currentTimeMillis() - cachedTime) < CACHE_EXPIRY_MS) {
			return versionCache.get(apiUrl);
		}
		
		long now = System.currentTimeMillis();
		try {
			URL url = new URL(apiUrl + "/latest");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

			if (conn.getResponseCode() != 200) {
				logger.debug("Failed to fetch /latest from {}, falling back to fullFetchLatestReleaseVersion", apiUrl);
				String result = fullFetchLatestReleaseVersion(apiUrl);
				versionCache.put(apiUrl, result);
				cacheTimestamps.put(apiUrl, System.currentTimeMillis());
				return result;
			}

			Scanner scanner = new Scanner(url.openStream());
			String inline = "";
			while (scanner.hasNext()) {
				inline += scanner.nextLine();
			}
			scanner.close();

			JsonParser parser = new JsonParser();
			JsonObject release = parser.parse(inline).getAsJsonObject();
			String latestVersion = release.get("tag_name").getAsString();

			logger.info("fastFetchLatestReleaseVersion took {} ms", System.currentTimeMillis() - now);
			versionCache.put(apiUrl, latestVersion);
			cacheTimestamps.put(apiUrl, System.currentTimeMillis());
			return latestVersion;

		} catch (IOException e) {
			logger.debug("Error fetching /latest from {}, falling back to fullFetchLatestReleaseVersion: {}", apiUrl, e.getMessage());
			String result = fullFetchLatestReleaseVersion(apiUrl);
			versionCache.put(apiUrl, result);
			cacheTimestamps.put(apiUrl, System.currentTimeMillis());
			return result;
		} catch (Exception e) {
			logger.debug("Unexpected error fetching /latest from {}, falling back to fullFetchLatestReleaseVersion: {}", apiUrl, e.getMessage());
			String result = fullFetchLatestReleaseVersion(apiUrl);
			versionCache.put(apiUrl, result);
			cacheTimestamps.put(apiUrl, System.currentTimeMillis());
			return result;
		}
	}
}
