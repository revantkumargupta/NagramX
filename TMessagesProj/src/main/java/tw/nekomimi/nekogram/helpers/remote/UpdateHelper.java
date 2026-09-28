package tw.nekomimi.nekogram.helpers.remote;

import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import xyz.nextalone.nagram.NaConfig;

public class UpdateHelper extends BaseRemoteHelper {

    public static final int UPDATE_OFF = 0;
    public static final int UPDATE_CHANNEL_RELEASE = 1;
    public static final int UPDATE_CHANNEL_BETA = 2;
    private static final String GITHUB_RELEASES_API = "https://api.github.com/repos/revantkumargupta/NagramX/releases";
    private static final Pattern VERSION_CODE_IN_PARENTHESES = Pattern.compile("\\((\\d+)\\)");
    private static final Pattern VERSION_CODE_FIELD = Pattern.compile("(?i)version[_ -]?code\\s*[:=]\\s*(\\d+)");
    private static final Pattern VERSION_CODE_IN_RELEASE_NAME = Pattern.compile("(?i)(?:^|[^0-9])v?\\d+\\.\\d+\\.\\d+[-_.](\\d+)(?=$|[^0-9])");

    public static UpdateHelper getInstance() {
        return InstanceHolder.instance;
    }

    public static void cleanAppUpdate() {
        if (SharedConfig.pendingAppUpdate != null && SharedConfig.pendingAppUpdate.document != null) {
            File path = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(SharedConfig.pendingAppUpdate.document, true);
            if (path != null && path.exists()) {
                Utilities.globalQueue.postRunnable(() -> {
                    try {
                        if (!path.delete()) path.deleteOnExit();
                    } catch (Exception ignored) {
                    }
                });
            }
        }
        SharedConfig.pendingAppUpdate = null;
        SharedConfig.saveConfig();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
    }

    @Override
    protected void onError(String text, Delegate delegate) {
        delegate.onTLResponse(null, text);
    }

    @Override
    protected String getTag() {
        if (BuildConfig.DEBUG) return "updateDebug";
        return NaConfig.INSTANCE.getAutoUpdateChannel().Int() == UPDATE_CHANNEL_RELEASE ? "updateRelease" : "updateBeta";
    }

    @SuppressWarnings("ConstantConditions")
    private int getPreferredAbiFile(Map<String, Integer> files) {
        for (String abi : Build.SUPPORTED_ABIS) {
            if (files.containsKey(abi)) {
                return files.get(abi);
            }
        }
        return files.getOrDefault("universal", files.get("arm64-v8a"));
    }

    private Map<String, Integer> jsonToMap(JSONObject obj) {
        Map<String, Integer> map = new HashMap<>();
        List<String> abis = new ArrayList<>();
        abis.add("arm64-v8a");
        abis.add("universal");
        try {
            for (var abi : abis) {
                map.put(abi, obj.getInt(abi));
            }
        } catch (JSONException ignored) {
        }
        return map;
    }

    private Update getShouldUpdateVersion(List<JSONObject> responses) {
        int currentVersion = BuildConfig.VERSION_CODE;
        long buildTimestamp = BuildConfig.BUILD_TIMESTAMP;
        Update ref = null;
        for (var string : responses) {
            try {
                int remoteVersion = string.getInt("version_code");
                long remoteBuildTimestamp = string.optLong("build_timestamp", 0L);
                boolean shouldUpdate = false;
                if (remoteVersion > currentVersion) {
                    shouldUpdate = true;
                } else if (remoteVersion == currentVersion && remoteBuildTimestamp > buildTimestamp) {
                    shouldUpdate = true;
                }
                if (shouldUpdate) {
                    ref = new Update(
                            string.getBoolean("can_not_skip"),
                            string.getString("version"),
                            remoteVersion,
                            string.getInt("sticker"),
                            string.getInt("message"),
                            jsonToMap(string.getJSONObject("document")),
                            string.getString("url")
                    );
                    break;
                }
            } catch (JSONException ignored) {
            }
        }
        return ref;
    }

    private void getNewVersionMessagesCallback(Delegate delegate, Update json, HashMap<String, Integer> ids, TLObject response) {
        var update = new TLRPC.TL_help_appUpdate();
        update.version = json.version;
        update.can_not_skip = json.canNotSkip;
        if (json.url != null) {
            update.url = json.url;
            update.flags |= 4;
        }
        if (NaConfig.INSTANCE.getAutoUpdateChannel().Int() == UPDATE_OFF && !update.can_not_skip) {
            delegate.onTLResponse(null, null);
            return;
        }
        if (response != null) {
            var res = (TLRPC.messages_Messages) response;
            getMessagesController().removeDeletedMessagesFromArray(CHANNEL_METADATA_ID, res.messages);
            var messages = new HashMap<Integer, TLRPC.Message>();
            for (var message : res.messages) {
                messages.put(message.id, message);
            }
            if (ids.containsKey("sticker")) {
                var sticker = messages.get(ids.get("sticker"));
                if (sticker != null && sticker.media != null) {
                    update.sticker = sticker.media.document;
                    update.flags |= 8;
                }
            }
            if (ids.containsKey("message")) {
                var message = messages.get(ids.get("message"));
                if (message != null) {
                    update.text = message.message;
                    update.entities = message.entities;
                }
            }
            if (ids.containsKey("document")) {
                var file = messages.get(ids.get("document"));
                if (file != null && file.media != null) {
                    update.document = file.media.document;
                    update.flags |= 2;
                }
            }
        }
        delegate.onTLResponse(update, null);
    }

    @Override
    protected void onLoadSuccess(ArrayList<JSONObject> responses, Delegate delegate) {
        var update = getShouldUpdateVersion(responses);
        if (update == null) {
            delegate.onTLResponse(null, null);
            return;
        }
        var ids = new HashMap<String, Integer>();
        if (update.sticker != null) {
            ids.put("sticker", update.sticker);
        }
        if (update.message != null) {
            ids.put("message", update.message);
        }
        if (update.document != null) {
            ids.put("document", getPreferredAbiFile(update.document));
        }
        if (ids.isEmpty()) {
            getNewVersionMessagesCallback(delegate, update, null, null);
        } else {
            var req = new TLRPC.TL_channels_getMessages();
            req.channel = getMessagesController().getInputChannel(CHANNEL_METADATA_ID);
            req.id = new ArrayList<>(ids.values());
            getConnectionsManager().sendRequest(req, (response1, error1) -> {
                if (error1 == null) {
                    getNewVersionMessagesCallback(delegate, update, ids, response1);
                } else {
                    delegate.onTLResponse(null, error1.text);
                }
            });
        }
    }

    public void checkNewVersionAvailable(Delegate delegate) {
        checkNewVersionAvailable(delegate, false);
    }

    public void checkNewVersionAvailable(Delegate delegate, boolean updateAlways) {
        checkNewVersionAvailable(delegate, false, updateAlways);
    }

    public void checkNewVersionAvailable(Delegate delegate, boolean force, boolean updateAlways) {
        loadGitHubRelease(delegate, force, updateAlways);
    }

    private void loadGitHubRelease(Delegate delegate, boolean force, boolean updateAlways) {
        if (NaConfig.INSTANCE.getAutoUpdateChannel().Int() == UPDATE_OFF && !force && !updateAlways) {
            delegate.onTLResponse(null, null);
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                JSONObject release = fetchGitHubRelease(updateAlways);
                TLRPC.TL_help_appUpdate update = buildGitHubUpdate(release, updateAlways);
                delegate.onTLResponse(update, null);
            } catch (Exception e) {
                FileLog.e(e);
                delegate.onTLResponse(null, e.getMessage());
            }
        });
    }

    private JSONObject fetchGitHubRelease(boolean updateAlways) throws Exception {
        String json = httpGet(GITHUB_RELEASES_API + "?per_page=20");
        JSONArray releases = new JSONArray(json);
        JSONObject bestRelease = null;
        int bestVersion = 0;
        int channel = NaConfig.INSTANCE.getAutoUpdateChannel().Int();
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.getJSONObject(i);
            if (channel != UPDATE_CHANNEL_BETA && release.optBoolean("prerelease", false)) {
                continue;
            }
            JSONObject asset = chooseGitHubApkAsset(release.optJSONArray("assets"));
            if (asset == null) {
                continue;
            }
            int remoteVersion = parseVersionCode(release, asset);
            if (remoteVersion <= 0) {
                continue;
            }
            if (!updateAlways && remoteVersion <= BuildConfig.VERSION_CODE) {
                continue;
            }
            if (bestRelease == null || remoteVersion > bestVersion) {
                bestRelease = release;
                bestVersion = remoteVersion;
            }
        }
        if (bestRelease == null) {
            return null;
        }
        return bestRelease;
    }

    private String httpGet(String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "NagramX/" + BuildConfig.VERSION_NAME);
        int code = connection.getResponseCode();
        try (InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream()) {
            if (stream == null) {
                throw new IllegalStateException("GitHub update check failed: HTTP " + code);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            byte[] bytes = output.toByteArray();
            String body = new String(bytes, StandardCharsets.UTF_8);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("GitHub update check failed: HTTP " + code + " " + body);
            }
            return body;
        } finally {
            connection.disconnect();
        }
    }

    private TLRPC.TL_help_appUpdate buildGitHubUpdate(JSONObject release, boolean updateAlways) throws Exception {
        if (release == null) {
            return null;
        }
        JSONObject asset = chooseGitHubApkAsset(release.optJSONArray("assets"));
        if (asset == null) {
            throw new JSONException("No compatible GitHub release APK asset found");
        }
        int remoteVersion = parseVersionCode(release, asset);
        if (remoteVersion <= 0) {
            throw new JSONException("GitHub release is missing a parseable version code");
        }
        if (!updateAlways && remoteVersion <= BuildConfig.VERSION_CODE) {
            return null;
        }

        String name = release.optString("name", release.optString("tag_name", asset.optString("name", "")));
        String body = release.optString("body", "").trim();
        TLRPC.TL_help_appUpdate update = new TLRPC.TL_help_appUpdate();
        update.version = name.isEmpty() ? BuildConfig.VERSION_NAME : name;
        update.can_not_skip = false;
        update.text = body.isEmpty() ? "Download the latest NagramX release from GitHub." : body;
        update.entities = new ArrayList<>();
        update.url = asset.getString("browser_download_url");
        update.flags |= 4;
        return update;
    }

    private JSONObject chooseGitHubApkAsset(JSONArray assets) {
        if (assets == null) {
            return null;
        }
        JSONObject universalApk = null;
        try {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String name = asset.optString("name", "").toLowerCase(Locale.US);
                if (!name.endsWith(".apk")) {
                    continue;
                }
                if (name.contains("universal")) {
                    universalApk = asset;
                }
                for (String abi : Build.SUPPORTED_ABIS) {
                    if (assetNameContainsAbi(name, abi.toLowerCase(Locale.US))) {
                        return asset;
                    }
                }
            }
        } catch (JSONException e) {
            FileLog.e(e);
        }
        return universalApk;
    }

    private boolean assetNameContainsAbi(String name, String abi) {
        int index = name.indexOf(abi);
        while (index >= 0) {
            int end = index + abi.length();
            boolean before = index == 0 || !isAbiNameChar(name.charAt(index - 1));
            boolean after = end == name.length() || !isAbiNameChar(name.charAt(end));
            if (before && after) {
                return true;
            }
            index = name.indexOf(abi, index + 1);
        }
        return false;
    }

    private boolean isAbiNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private int parseVersionCode(JSONObject release, JSONObject asset) {
        String[] explicitCandidates = new String[]{
                release.optString("body", ""),
                release.optString("name", ""),
                release.optString("tag_name", ""),
                asset.optString("name", "")
        };
        for (String candidate : explicitCandidates) {
            int version = parseVersionCodeField(candidate);
            if (version > 0) {
                return version;
            }
        }
        String[] structuredCandidates = new String[]{
                asset.optString("name", ""),
                release.optString("tag_name", ""),
                release.optString("name", "")
        };
        for (String candidate : structuredCandidates) {
            int version = parseStructuredVersionCode(candidate);
            if (version > 0) {
                return version;
            }
        }
        return 0;
    }

    private int parseVersionCodeField(String value) {
        if (value == null) {
            return 0;
        }
        Matcher fieldMatcher = VERSION_CODE_FIELD.matcher(value);
        if (fieldMatcher.find()) {
            return Utilities.parseInt(fieldMatcher.group(1));
        }
        return 0;
    }

    private int parseStructuredVersionCode(String value) {
        if (value == null) {
            return 0;
        }
        Matcher releaseMatcher = VERSION_CODE_IN_RELEASE_NAME.matcher(value);
        if (releaseMatcher.find()) {
            return Utilities.parseInt(releaseMatcher.group(1));
        }
        Matcher parenthesesMatcher = VERSION_CODE_IN_PARENTHESES.matcher(value);
        int parsed = 0;
        while (parenthesesMatcher.find()) {
            parsed = Utilities.parseInt(parenthesesMatcher.group(1));
        }
        return parsed;
    }

    private static final class InstanceHolder {
        private static final UpdateHelper instance = new UpdateHelper();
    }

    public static class Update {
        public Boolean canNotSkip;
        public String version;
        public Integer versionCode;
        public Integer sticker;
        public Integer message;
        public Map<String, Integer> document;
        public String url;

        public Update(Boolean canNotSkip, String version, int versionCode, int sticker, int message, Map<String, Integer> document, String url) {
            this.canNotSkip = canNotSkip;
            this.version = version;
            this.versionCode = versionCode;
            this.sticker = sticker;
            this.message = message;
            this.document = document;
            this.url = url;
        }
    }
}
