package net.justalittlewolf.modpackloaderfabric;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class ModpackLoaderFabric implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("modpackloaderfabric");
    public static final String modSuffix = "_MPLF";
    public static final Map<String, String> modIdCache = new HashMap<>();

    @Override
    public void onInitialize() {
        File toDeleteFile = new File(FabricLoader.getInstance().getConfigDir().toString() + "/ModpackLoaderDeleteOnStartup.json");
        try {
            if (!toDeleteFile.exists()) {
                toDeleteFile.createNewFile();
                FileWriter fileW = new FileWriter(toDeleteFile);
                fileW.write(new JsonArray().toString());
                fileW.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            JsonArray couldntDelete = new JsonArray();
            JsonArray toDelete = new Gson().fromJson(Files.readString(Paths.get(FabricLoader.getInstance().getConfigDir().toString() + "/ModpackLoaderDeleteOnStartup.json")), JsonArray.class);
            String modDir = FabricLoader.getInstance().getGameDir().toString() + "\\mods";
            for (int i = 0; i < toDelete.size(); i++) {
                File deleteMe = new File(modDir + "\\" + toDelete.get(i).getAsString());
                int lastDotIndex = toDelete.get(i).getAsString().lastIndexOf('.');
                if (toDelete.get(i).getAsString().startsWith(modSuffix, lastDotIndex - modSuffix.length())) {
                    deleteMe.delete();
                } else {
                    if (deleteMe.renameTo(new File(modDir + "\\" + toDelete.get(i).getAsString() + ".old"))) {
                        deleteMe.delete();
                        LOGGER.info("[ModpackLoaderFabric] Disabled old mod " + toDelete.get(i).getAsString());
                    } else {
                        couldntDelete.add(new JsonPrimitive(toDelete.get(i).getAsString()));
                        LOGGER.info("[ModpackLoaderFabric] Could not disable old mod " + toDelete.get(i).getAsString());
                    }
                }
            }

            FileWriter fileW = new FileWriter(toDeleteFile);
            fileW.write(couldntDelete.toString());
            fileW.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            updateMods(false);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void updateMods(boolean force) throws IOException {
        Gson gson = new Gson();
        modIdCache.clear();
        File modFolder = new File(FabricLoader.getInstance().getGameDir().toString() + "\\mods");
        for (final File fileEntry : Objects.requireNonNull(modFolder.listFiles())) {
            if (fileEntry.isFile() && fileEntry.getName().endsWith(".jar")) {
                JarFile local = new JarFile(fileEntry.getAbsoluteFile());
                InputStream localJsonInputStream = local.getInputStream(local.getJarEntry("fabric.mod.json"));
                JsonObject fabricModJsonLocal = gson.fromJson(new BufferedReader(new InputStreamReader(localJsonInputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n")), JsonObject.class);
                String modIdlocal = fabricModJsonLocal.get("id").getAsString();
                localJsonInputStream.close();
                local.close();
                modIdCache.put(modIdlocal, fileEntry.getName());
            }
        }

        boolean modPacksLoaded = false;
        try {
            String configPath = FabricLoader.getInstance().getConfigDir().toString() + "/ModpackLoaderConfig.json";
            File file = new File(configPath);
            JsonObject json = gson.fromJson("{\"local\":[],\"host\":[\"default\"],\"url\":[],\"lastUpdate\":0,\"updateInterval\":1,\"updateOnStart\":true}", JsonObject.class);

            File localModPacks = new File(FabricLoader.getInstance().getConfigDir().toString() + "/MPLF_Modpacks");
            localModPacks.mkdirs();

            boolean firstLaunch = false;
            if (!file.exists()) {
                firstLaunch = true;
                file.createNewFile();
                FileWriter fileW = new FileWriter(configPath);
                fileW.write(json.toString());
                fileW.close();
            } else {
                String jsonString = Files.readString(Paths.get(configPath));
                if(isJSONValid(jsonString)) {
                    json = gson.fromJson(Files.readString(Paths.get(configPath)), JsonObject.class);
                    if (!json.has("lastUpdate")) {
                        firstLaunch = true;
                        json = gson.fromJson("{\"local\":[],\"host\":[\"default\"],\"url\":[],\"lastUpdate\":0,\"updateInterval\":1,\"updateOnStart\":true}", JsonObject.class);
                        FileWriter fileW = new FileWriter(configPath);
                        fileW.write(json.toString());
                        fileW.close();
                    }
                }
            }

            File modPackExample = new File(localModPacks + "/ModpackExample.json");
            if (!modPackExample.exists()) {
                ReadableByteChannel readableByteChannel = Channels.newChannel(new URL("https://modpack.wolfii.me/ModPackExample.json").openStream());
                FileOutputStream fileOutputStream = new FileOutputStream(String.valueOf(modPackExample));
                fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
            }

            if (json.has("updateOnStart")) {
                if (json.get("updateOnStart").getAsBoolean()) {
                    long unixTime = Instant.now().getEpochSecond();
                    if ((unixTime - json.get("lastUpdate").getAsInt()) / 86400.0 >= json.get("updateInterval").getAsFloat() || json.get("updateInterval").getAsInt() == 0 || force) {
                        if (!firstLaunch) {
                            json.addProperty("lastUpdate", unixTime);
                            FileWriter fileW = new FileWriter(configPath);
                            fileW.write(json.toString());
                            fileW.close();
                        }
                        JsonObject modPack;
                        if (json.get("host").getAsJsonArray().size() > 0) {
                            for (int i = 0; i < json.get("host").getAsJsonArray().size(); i++) {
                                URL modPackURL = new URL("https://modpack.wolfii.me/packs/" + json.get("host").getAsJsonArray().get(i).getAsString() + ".json");
                                modPack = gson.fromJson(URLReader(modPackURL), JsonObject.class);
                                LOGGER.info("[ModpackLoaderFabric] Loading Modpack from " + modPackURL);
                                modPacksLoaded = loadModPack(modPack) || modPacksLoaded;
                            }
                        }
                        if (json.get("url").getAsJsonArray().size() > 0) {
                            for (int i = 0; i < json.get("url").getAsJsonArray().size(); i++) {
                                URL modPackURL = new URL(json.get("url").getAsJsonArray().get(i).getAsString());
                                modPack = gson.fromJson(URLReader(modPackURL), JsonObject.class);
                                LOGGER.info("[ModpackLoaderFabric] Loading Modpack from " + modPackURL);
                                modPacksLoaded = loadModPack(modPack) || modPacksLoaded;
                            }
                        }
                        if (json.get("local").getAsJsonArray().size() > 0) {
                            for (int i = 0; i < json.get("local").getAsJsonArray().size(); i++) {
                                modPack = gson.fromJson(Files.readString(Paths.get(localModPacks + "/" + json.get("local").getAsJsonArray().get(i).getAsString() + ".json")), JsonObject.class);
                                LOGGER.info("[ModpackLoaderFabric] Loading Modpack from " + localModPacks + "\\" + json.get("local").getAsJsonArray().get(i).getAsString() + ".json");
                                modPacksLoaded = loadModPack(modPack) || modPacksLoaded;
                            }
                        }
                    } else {
                        LOGGER.info("[ModpackLoaderFabric] Upading mods skipped (last update less than " + json.get("updateInterval").getAsInt() + " day(s) ago)");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (modPacksLoaded) {
            LOGGER.info("[ModpackLoaderFabric] All mods up to date");
        }
    }


    public static boolean loadModPack(JsonObject json) throws IOException {
        boolean downloaded = false;
        JsonArray toUpdate = getNewestVersions(json);
        for (int i = 0; i < toUpdate.size(); i++) {
            downloaded = true;
            downloadMod(toUpdate.get(i).getAsJsonObject().get("id").getAsString(), toUpdate.get(i).getAsJsonObject().get("version").getAsString(), new URL(toUpdate.get(i).getAsJsonObject().get("link").getAsString()));
        }
        return downloaded;
    }

    public static void downloadMod(String id, String version, URL downloadURL) throws IOException {
        Gson gson = new Gson();
        String loadedModslistPath = FabricLoader.getInstance().getConfigDir().toString() + "/ModpackLoaderLoadedModlist.json";
        JsonObject loadedMods = new JsonObject();

        if (!new File(loadedModslistPath).exists()) {
            new File(loadedModslistPath).createNewFile();
        } else {
            String jsonString = Files.readString(Paths.get(loadedModslistPath));
            if(isJSONValid(jsonString)) {
                loadedMods = gson.fromJson(jsonString, JsonObject.class);
                if(loadedMods == null) {
                    loadedMods = new JsonObject();
                }
            }
        }

        boolean writeFile = false;
        if (loadedMods.has(id)) {
            if (!version.equals(loadedMods.get(id).getAsJsonObject().get("version").getAsString())) {
                writeFile = true;
                File toDeleteFile = new File(FabricLoader.getInstance().getConfigDir().toString() + "/ModpackLoaderDeleteOnStartup.json");
                JsonArray toDelete = gson.fromJson(Files.readString(Paths.get(FabricLoader.getInstance().getConfigDir().toString() + "/ModpackLoaderDeleteOnStartup.json")), JsonArray.class);
                if (!toDelete.contains(new JsonPrimitive(loadedMods.get(id).getAsJsonObject().get("file").getAsString()))) {
                    toDelete.add(new JsonPrimitive(loadedMods.get(id).getAsJsonObject().get("file").getAsString()));
                    FileWriter fileW = new FileWriter(toDeleteFile);
                    fileW.write(toDelete.toString());
                    fileW.close();
                }
            }
        } else {
            writeFile = true;
        }
        if (writeFile) {
            int lastSlashIndex = downloadURL.toString().lastIndexOf('/');
            String modFileName = downloadURL.toString().substring(lastSlashIndex + 1);
            int lastDotIndex = modFileName.lastIndexOf('.');
            modFileName = modFileName.substring(0, lastDotIndex) + modSuffix + modFileName.substring(lastDotIndex);
            if (loadedMods.has(id)) {
                LOGGER.info("[ModpackLoaderFabric] Updated " + loadedMods.get(id).getAsJsonObject().get("file").getAsString() + " to " + modFileName);
            } else {
                LOGGER.info("[ModpackLoaderFabric] Downloaded mod " + modFileName);
            }
            writeFile(downloadURL, FabricLoader.getInstance().getGameDir().toString() + "\\mods\\" + modFileName);
            JsonObject jsonID = new JsonObject();
            jsonID.addProperty("version", version);
            jsonID.addProperty("file", modFileName);
            loadedMods.add(id, jsonID);
            FileWriter fileW = new FileWriter(loadedModslistPath);
            fileW.write(loadedMods.toString());
            fileW.close();
        }
    }

    public static void writeFile(URL downloadURL, String fileName) throws IOException {
        Gson gson = new Gson();
        ReadableByteChannel readableByteChannel = Channels.newChannel(downloadURL.openStream());
        FileOutputStream fileOutputStream = new FileOutputStream(fileName);
        fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
        JarFile downloaded = new JarFile(fileName);
        InputStream modJsonInputStream = downloaded.getInputStream(downloaded.getJarEntry("fabric.mod.json"));
        JsonObject fabricModJson = gson.fromJson(new BufferedReader(new InputStreamReader(modJsonInputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n")), JsonObject.class);
        String modID = fabricModJson.get("id").getAsString();
        downloaded.close();
        modJsonInputStream.close();
        if(modIdCache.containsKey(modID)) {
            if(!modIdCache.get(modID).equals(fileName)) {
                File toDeleteFile = new File(FabricLoader.getInstance().getConfigDir().toString() + "/ModpackLoaderDeleteOnStartup.json");
                JsonArray toDelete = gson.fromJson(Files.readString(Paths.get(FabricLoader.getInstance().getConfigDir().toString() + "/ModpackLoaderDeleteOnStartup.json")), JsonArray.class);
                if (!toDelete.contains(new JsonPrimitive(modIdCache.get(modID)))) {
                    toDelete.add(new JsonPrimitive(modIdCache.get(modID)));
                    FileWriter fileW = new FileWriter(toDeleteFile);
                    fileW.write(toDelete.toString());
                    fileW.close();
                }
            }
        }
    }

    public static String URLReader(URL url) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;

        try (InputStream in = url.openStream()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            while ((line = reader.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }
        }

        return sb.toString();
    }

    public static JsonArray getNewestVersions(JsonObject modPack) {
        JsonArray jsonResponse = new JsonArray();
        try {
            URL api = new URL("https://modpack.wolfii.me/api.php");
            HttpURLConnection con = (HttpURLConnection) api.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; utf-8");
            con.setRequestProperty("Accept", "application/json");
            con.setDoOutput(true);
            String jsonInputString = modPack.toString();
            try (OutputStream os = con.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                jsonResponse = new Gson().fromJson(response.toString(), JsonArray.class);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return jsonResponse;
    }

    public static boolean isJSONValid(String jsonInString) {
        try {
            new Gson().fromJson(jsonInString, Object.class);
            return true;
        } catch(com.google.gson.JsonSyntaxException ex) {
            return false;
        }
    }
}
