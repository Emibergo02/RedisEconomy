package dev.unnm3d.rediseconomy.utils;

import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The adventure-webui editor API.
 */
public final class AdventureWebuiEditorAPI {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("(\\w{32})");
    private final URI root;
    private HttpURLConnection client;


    /**
     * Creates a new instance of the editor API.
     */

    public AdventureWebuiEditorAPI(String root) throws IOException {
        this(URI.create(root), (HttpURLConnection) new URL(root).openConnection());
    }

    /**
     * Creates a new instance of the editor API with the given root URI and a client.
     *
     * @param root   the root URI
     * @param client the client
     */
    public AdventureWebuiEditorAPI(final @NotNull URI root, final @NotNull HttpURLConnection client) {
        this.root = Objects.requireNonNull(root, "root");
        this.client = Objects.requireNonNull(client, "client");
    }

    /**
     * Starts a session, returning the token.
     *
     * @param input       the input
     * @param command     the command
     * @param application the application name
     * @return a completable future that will provide the token
     */
    @SneakyThrows
    public @NotNull CompletableFuture<String> startSession(final @NotNull String input, final @NotNull String command, final @NotNull String application) {

        final CompletableFuture<String> result = new CompletableFuture<>();

        client = (HttpURLConnection) new URL(root + "/api/editor/input").openConnection();
        client.setRequestMethod("POST");
        client.setDoOutput(true);
        client.setConnectTimeout(6000);
        client.setReadTimeout(6000);

        OutputStream os = client.getOutputStream();
        os.write(constructBody(input, command, application).getBytes());
        os.flush();
        os.close();


        int responseCode = client.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) { //success
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            final Matcher matcher = TOKEN_PATTERN.matcher(response.toString());
            if (matcher.find()) {
                final String group = matcher.group(0);
                result.complete(group);
            }
        } else {
            result.completeExceptionally(new IOException("The server could not handle the request."));
        }

        return result;
    }

    /**
     * Retrieves the result of a session, given a token.
     *
     * @param token the token
     * @return the resulting MiniMessage string in a completable future
     */

    public @NotNull CompletableFuture<String> retrieveSession(final @NotNull String token) {

        final CompletableFuture<String> result = new CompletableFuture<>();

        try {
            client = (HttpURLConnection) new URL(root + "/api/editor/output?token=" + token).openConnection();
            client.setRequestMethod("GET");
            client.setConnectTimeout(6000);
            client.setReadTimeout(6000);
            int responseCode = client.getResponseCode();

            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            if (responseCode == 404) {
                result.complete(null);
            } else if (responseCode != 200) {
                result.completeExceptionally(new IOException("The server could not handle the request."));
            } else {
                result.complete(response.toString());
            }
        } catch (IOException e) {
            result.completeExceptionally(new IOException("The server does not have a session with the given token."));
        }


        return result;
    }

    private @NotNull String constructBody(final @NotNull String input, final @NotNull String command, final @NotNull String application) {
        return String.format("{\"input\":\"%s\",\"command\":\"%s\",\"application\":\"%s\"}", input, command, application);
    }

    public String getEditorUrl(String token) {
        return root.toString() + "?token=" + token;
    }
}