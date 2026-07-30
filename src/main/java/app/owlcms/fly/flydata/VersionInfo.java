package app.owlcms.fly.flydata;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

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
	private String[] fallbackReleaseUrls;
	final private static Logger logger = (Logger) LoggerFactory.getLogger(VersionInfo.class);
	private Integer comparison;
	
	// Static cache shared by all users of this Fly instance. GitHub's unauthenticated
	// API rate limit is 60 requests/hour, so release metadata must be refreshed sparingly.
	private static final ConcurrentHashMap<String, String> versionCache = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<String, List<String>> releaseCache = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<String, Long> releaseCacheTimestamps = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<String, Boolean> inFlightFetches = new ConcurrentHashMap<>();
	private static final long CACHE_EXPIRY_MS = 60 * 60 * 1000;
	private static final int FETCH_ATTEMPTS = 2;
	private static final int FETCH_TIMEOUT_MS = 5000;
	private static final long RETRY_DELAY_MS = 500;
	private static final boolean FORCE_RELEASE_FALLBACK = false;
	private static final int MAX_SELECTOR_VERSIONS = 30;

	public VersionInfo(String currentVersionString) {
		this(currentVersionString, "https://api.github.com/repos/owlcms/owlcms4/releases");
	}

	public VersionInfo(String currentVersionString, String apiUrl) {
		this(currentVersionString, apiUrl, new String[0]);
	}

	public VersionInfo(String currentVersionString, String apiUrl, String... fallbackReleaseUrls) {
		this(currentVersionString, apiUrl, true, fallbackReleaseUrls);
	}

	public VersionInfo(String currentVersionString, String apiUrl, boolean fetchReferenceVersion,
			String... fallbackReleaseUrls) {
		this.currentVersionString = currentVersionString;
		this.apiUrl = apiUrl;
		this.fallbackReleaseUrls = fallbackReleaseUrls;
		if (fetchReferenceVersion) {
			this.updateReferenceVersionString();
		}
	}

	public void updateReferenceVersionString(boolean preRelease) {
		if (apiUrl == null || apiUrl.isBlank()) {
			this.referenceVersionString = "unknown";
			this.comparison = 0;
			return;
		}
		this.referenceVersionString = fastFetchLatestReleaseVersion(apiUrl, fallbackReleaseUrls);

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

	public String getCachedReferenceVersionString() {
		return referenceVersionString == null ? "not loaded" : referenceVersionString;
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
			URL url = URI.create(apiUrl).toURL();
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(FETCH_TIMEOUT_MS);
			conn.setReadTimeout(FETCH_TIMEOUT_MS);
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

			if (conn.getResponseCode() != 200) {
			logger.info("Failed to fetch /releases from {}, HTTP error code: {}", apiUrl, conn.getResponseCode());
			return "unknown";
		}

		Scanner scanner = new Scanner(conn.getInputStream());
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

	public static List<String> fetchReleaseVersions(String apiUrl) {
		return fetchReleaseVersions(apiUrl, new String[0]);
	}

	public static List<String> fetchReleaseVersions(String apiUrl, String... fallbackReleaseUrls) {
		return fetchReleaseVersions(apiUrl, null, false, fallbackReleaseUrls);
	}

	public static List<String> fetchReleaseVersions(String apiUrl, String preReleaseApiUrl,
			boolean showPrereleases, String... fallbackReleaseUrls) {
		if (FORCE_RELEASE_FALLBACK) {
			return fetchFallbackReleaseVersions(showPrereleases, fallbackReleaseUrls);
		}
		String cacheKey = apiUrl + "|" + showPrereleases;
		Long cachedTime = releaseCacheTimestamps.get(cacheKey);
		if (cachedTime != null && (System.currentTimeMillis() - cachedTime) < CACHE_EXPIRY_MS) {
			return releaseCache.get(cacheKey);
		}

		try {
			List<String> stableVersions = fetchApiReleaseVersions(apiUrl, false);
			List<String> versions = new ArrayList<>(stableVersions);
			if (showPrereleases && preReleaseApiUrl != null) {
				List<String> prereleaseVersions = fetchApiReleaseVersions(preReleaseApiUrl, true);
				versions.addAll(prereleaseVersions);
				logger.info("Fetched {} stable and {} prerelease versions", stableVersions.size(),
						prereleaseVersions.size());
			}
			List<String> result = limitSelectorVersions(filterCloudAlphaVersions(orderVersions(versions)));
			releaseCache.put(cacheKey, result);
			releaseCacheTimestamps.put(cacheKey, System.currentTimeMillis());
			return result;
		} catch (Exception e) {
			logger.warn("Unable to fetch releases from {}: {}", apiUrl, e.getMessage());
			return releaseCache.getOrDefault(cacheKey, fetchFallbackReleaseVersions(showPrereleases, fallbackReleaseUrls));
		}
	}

	public static List<String> getCachedReleaseVersions(String apiUrl, boolean showPrereleases) {
		String cacheKey = apiUrl + "|" + showPrereleases;
		Long cachedTime = releaseCacheTimestamps.get(cacheKey);
		if (cachedTime == null || System.currentTimeMillis() - cachedTime >= CACHE_EXPIRY_MS) {
			return List.of();
		}
		return releaseCache.getOrDefault(cacheKey, List.of());
	}

	private static List<String> fetchApiReleaseVersions(String apiUrl, boolean releaseChannelIncludesPrereleases)
			throws IOException {
		URL url = URI.create(apiUrl + "?per_page=" + MAX_SELECTOR_VERSIONS).toURL();
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setConnectTimeout(FETCH_TIMEOUT_MS);
		conn.setReadTimeout(FETCH_TIMEOUT_MS);
		conn.setRequestMethod("GET");
		conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
		if (conn.getResponseCode() != 200) {
			throw new IOException("HTTP " + conn.getResponseCode() + " (rate limit remaining: "
					+ conn.getHeaderField("X-RateLimit-Remaining") + ")");
		}

		Scanner scanner = new Scanner(conn.getInputStream());
		StringBuilder response = new StringBuilder();
		while (scanner.hasNextLine()) {
			response.append(scanner.nextLine());
		}
		scanner.close();

		List<String> versions = new ArrayList<>();
		JsonParser parser = new JsonParser();
		parser.parse(response.toString()).getAsJsonArray().forEach(release -> {
			JsonObject releaseObject = release.getAsJsonObject();
			boolean isPrerelease = releaseObject.get("prerelease").getAsBoolean();
			if (!releaseObject.get("draft").getAsBoolean()
					&& (releaseChannelIncludesPrereleases || !isPrerelease)) {
				versions.add(releaseObject.get("tag_name").getAsString());
			}
		});
		return versions;
	}

	public static String fastFetchLatestReleaseVersion(String apiUrl) {
		return fastFetchLatestReleaseVersion(apiUrl, new String[0]);
	}

	public static String fastFetchLatestReleaseVersion(String apiUrl, String... fallbackReleaseUrls) {
		if (apiUrl == null || apiUrl.isBlank()) {
			return "unknown";
		}
		if (FORCE_RELEASE_FALLBACK) {
			List<String> fallbackVersions = fetchFallbackReleaseVersions(false, fallbackReleaseUrls);
			return fallbackVersions.isEmpty() ? "unknown" : fallbackVersions.get(0);
		}
		Long cachedTime = cacheTimestamps.get(apiUrl);
		if (cachedTime != null && (System.currentTimeMillis() - cachedTime) < CACHE_EXPIRY_MS) {
			return versionCache.get(apiUrl);
		}

		if (inFlightFetches.putIfAbsent(apiUrl, Boolean.TRUE) != null) {
			String cachedVersion = versionCache.get(apiUrl);
			if (cachedVersion != null) {
				logger.debug("Version fetch already in progress for {}, returning cached version {}", apiUrl,
						cachedVersion);
				return cachedVersion;
			}
			List<String> fallbackVersions = fetchFallbackReleaseVersions(false, fallbackReleaseUrls);
			return fallbackVersions.isEmpty() ? "unknown" : fallbackVersions.get(0);
		}

		try {
			long now = System.currentTimeMillis();
			for (int attempt = 1; attempt <= FETCH_ATTEMPTS; attempt++) {
				try {
					@SuppressWarnings("deprecation")
					URL url = new URL(apiUrl + "/latest");
					HttpURLConnection conn = (HttpURLConnection) url.openConnection();
					conn.setConnectTimeout(FETCH_TIMEOUT_MS);
					conn.setReadTimeout(FETCH_TIMEOUT_MS);
					conn.setRequestMethod("GET");
					conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

					if (conn.getResponseCode() != 200) {
						logger.debug("Attempt {}/{} to fetch /latest from {} returned HTTP {}", attempt,
								FETCH_ATTEMPTS, apiUrl, conn.getResponseCode());
						continue;
					}

					Scanner scanner = new Scanner(conn.getInputStream());
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
				} catch (Exception e) {
					logger.debug("Attempt {}/{} to fetch /latest from {} failed: {}", attempt,
							FETCH_ATTEMPTS, apiUrl, e.getMessage());
				}

				if (attempt < FETCH_ATTEMPTS) {
					try {
						Thread.sleep(RETRY_DELAY_MS * attempt);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return "unknown";
					}
				}
			}

			String cachedVersion = versionCache.get(apiUrl);
			if (cachedVersion != null) {
				logger.warn("Unable to refresh latest release from {}; keeping cached version {}", apiUrl,
						cachedVersion);
				return cachedVersion;
			}
			List<String> fallbackVersions = fetchFallbackReleaseVersions(false, fallbackReleaseUrls);
			if (!fallbackVersions.isEmpty()) {
				String fallbackVersion = fallbackVersions.get(0);
				logger.warn("Unable to refresh latest release from {}; using fallback version {}", apiUrl,
						fallbackVersion);
				versionCache.put(apiUrl, fallbackVersion);
				cacheTimestamps.put(apiUrl, System.currentTimeMillis());
				return fallbackVersion;
			}
			logger.warn("No version found after {} attempts for {}, returning unknown", FETCH_ATTEMPTS, apiUrl);
			return "unknown";
		} finally {
			inFlightFetches.remove(apiUrl);
		}
	}

	private static List<String> fetchFallbackReleaseVersions(boolean includePrereleases, String... fallbackReleaseUrls) {
		List<String> versions = new ArrayList<>();
		for (String fallbackReleaseUrl : fallbackReleaseUrls) {
			try {
				HttpURLConnection conn = (HttpURLConnection) URI.create(fallbackReleaseUrl).toURL().openConnection();
				conn.setConnectTimeout(FETCH_TIMEOUT_MS);
				conn.setReadTimeout(FETCH_TIMEOUT_MS);
				conn.setInstanceFollowRedirects(false);
				conn.setRequestMethod("GET");
				int status = conn.getResponseCode();
				if (status >= 300 && status < 400) {
					String location = conn.getHeaderField("Location");
					String version = extractReleaseVersion(location);
					if (version != null && (includePrereleases || isStableVersion(version))) {
						versions.add(version);
					}
				} else {
					logger.warn("Unable to fetch fallback release from {}: HTTP {}", fallbackReleaseUrl, status);
				}
			} catch (Exception e) {
				logger.warn("Unable to fetch fallback release from {}: {}", fallbackReleaseUrl, e.getMessage());
			}
		}
		return filterCloudAlphaVersions(orderVersions(versions));
	}

	private static String extractReleaseVersion(String location) {
		if (location == null) {
			return null;
		}
		int tagStart = location.indexOf("/releases/tag/");
		if (tagStart < 0) {
			return null;
		}
		String version = location.substring(tagStart + "/releases/tag/".length());
		int queryStart = version.indexOf('?');
		return queryStart < 0 ? version : version.substring(0, queryStart);
	}

	private static List<String> orderVersions(List<String> versions) {
		List<String> ordered = new ArrayList<>(new LinkedHashSet<>(versions));
		ordered.sort((left, right) -> new ComparableVersion(right).compareTo(new ComparableVersion(left)));
		ordered.stream().filter(VersionInfo::isStableVersion).findFirst().ifPresent(stableVersion -> {
			ordered.remove(stableVersion);
			ordered.add(0, stableVersion);
		});
		return List.copyOf(ordered);
	}

	private static List<String> limitSelectorVersions(List<String> versions) {
		return versions.size() <= MAX_SELECTOR_VERSIONS ? versions
				: List.copyOf(versions.subList(0, MAX_SELECTOR_VERSIONS));
	}

	private static List<String> filterCloudAlphaVersions(List<String> versions) {
		if (System.getenv("FLY_APP_NAME") == null) {
			return versions;
		}
		return versions.stream().filter(version -> !version.contains("-alpha")).toList();
	}

	private static boolean isStableVersion(String version) {
		return !version.contains("-");
	}
}
