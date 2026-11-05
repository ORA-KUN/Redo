package fr.orakun.redo.core.prompt;

import fr.orakun.redo.core.json.Json;

import javax.net.ssl.SSLException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal client of the Claude Messages API over plain HTTPS.
 *
 * <p>Redo calls the REST endpoint directly instead of bundling the official Java SDK: the mod has to
 * run on the Java 8 runtime of Minecraft 1.8.9 and inside Minecraft's class path, where shading the
 * SDK and its dependencies (Kotlin, OkHttp, Jackson) would conflict with the game's own libraries.
 * One structured-output request is all the prompt feature needs.
 */
public final class ClaudeClient {
    public static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    public static final String API_VERSION = "2026-10-21";
    public static final String DEFAULT_MODEL = "claude-opus-5-5";

    public static final class ApiException extends IOException {
        public final int status;

        public ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private final String apiKey;
    private final String model;
    private final String userAgent;
    private int timeoutMs = 90000;

    public ClaudeClient(String apiKey, String model, String userAgent) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.trim().isEmpty() ? DEFAULT_MODEL : model.trim();
        this.userAgent = userAgent;
    }

    public boolean hasKey() {
        return !apiKey.isEmpty();
    }

    public ClaudeClient timeout(int ms) {
        this.timeoutMs = ms;
        return this;
    }

    /**
     * Sends one user message and returns the JSON object Claude produced, constrained by {@code schema}.
     */
    public Map<String, Object> structured(String system, String user, Map<String, Object> schema, int maxTokens) throws IOException {
        Map<String, Object> body = Json.obj();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("system", system);
        List<Object> messages = new ArrayList<Object>();
        Map<String, Object> msg = Json.obj();
        msg.put("role", "user");
        msg.put("content", user);
        messages.add(msg);
        body.put("messages", messages);
        Map<String, Object> outputConfig = Json.obj();
        outputConfig.put("effort", "low");
        Map<String, Object> format = Json.obj();
        format.put("type", "json_schema");
        format.put("schema", schema);
        outputConfig.put("format", format);
        body.put("output_config", outputConfig);

        Map<String, Object> response = post(Json.write(body));
        String stop = Json.getString(response, "stop_reason", "");
        if ("refusal".equals(stop)) throw new ApiException(200, "Claude a refusé cette demande.");
        if ("max_tokens".equals(stop)) throw new ApiException(200, "Réponse de Claude tronquée (trop de plans demandés).");
        StringBuilder text = new StringBuilder();
        List<Object> content = Json.getList(response, "content");
        if (content != null) {
            for (Map<String, Object> block : Json.objects(content)) {
                if ("text".equals(Json.getString(block, "type", ""))) text.append(Json.getString(block, "text", ""));
            }
        }
        if (text.length() == 0) throw new ApiException(200, "Réponse vide de Claude.");
        return Json.parseObject(text.toString());
    }

    private Map<String, Object> post(String json) throws IOException {
        if (!hasKey()) throw new ApiException(401, "Aucune clé API Claude configurée.");
        HttpURLConnection c;
        try {
            c = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(timeoutMs);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("x-api-key", apiKey);
            c.setRequestProperty("anthropic-version", API_VERSION);
            if (userAgent != null) c.setRequestProperty("User-Agent", userAgent);
            OutputStream out = c.getOutputStream();
            out.write(json.getBytes("UTF-8"));
            out.close();
        } catch (SSLException e) {
            throw new IOException("Connexion sécurisée impossible : votre Java est trop ancien. Lancez Minecraft avec un Java 8 récent (8u261 ou plus) ou utilisez le mode hors ligne.", e);
        }
        int status = c.getResponseCode();
        InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = in == null ? "" : readAll(in);
        if (status >= 400) {
            String message = "Erreur " + status;
            try {
                Map<String, Object> err = Json.getObj(Json.parseObject(text), "error");
                if (err != null) message = Json.getString(err, "message", message);
            } catch (RuntimeException ignored) {
            }
            if (status == 401) message = "Clé API Claude invalide.";
            else if (status == 429) message = "Limite de requêtes Claude atteinte, réessayez dans un instant.";
            else if (status == 529) message = "Claude est surchargé, réessayez dans un instant.";
            throw new ApiException(status, message);
        }
        return Json.parseObject(text);
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        try {
            while ((n = in.read(buf)) > 0) bytes.write(buf, 0, n);
        } finally {
            in.close();
        }
        return new String(bytes.toByteArray(), "UTF-8");
    }
}
