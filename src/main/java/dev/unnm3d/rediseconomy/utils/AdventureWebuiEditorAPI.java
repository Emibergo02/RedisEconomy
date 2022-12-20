package dev.unnm3d.rediseconomy.utils;
/*
 * This file is part of adventure-webui, licensed under the MIT License.
 *
 * Copyright (c) 2021 KyoriPowered
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The adventure-webui editor API.
 */
public final class AdventureWebuiEditorAPI {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"(.*?)\"");

    private final URI root;
    private final HttpClient client;

    /**
     * Creates a new instance of the editor API with the given root URI.
     *
     * @param root the root URI
     */
    public AdventureWebuiEditorAPI(final @NotNull URI root) {
        this(root, HttpClient.newHttpClient());
    }
    /**
     * Creates a new instance of the editor API.
     *
     */
    public AdventureWebuiEditorAPI() {
        this(URI.create("https://webui.adventure.kyori.net"), HttpClient.newHttpClient());
    }

    /**
     * Creates a new instance of the editor API with the given root URI and a client.
     *
     * @param root the root URI
     * @param client the client
     */
    public AdventureWebuiEditorAPI(final @NotNull URI root, final @NotNull HttpClient client) {
        this.root = Objects.requireNonNull(root, "root");
        this.client = Objects.requireNonNull(client, "client");
    }

    /**
     * Starts a session, returning the token.
     *
     * @param input the input
     * @param command the command
     * @param application the application name
     * @return a completable future that will provide the token
     */
    public @NotNull CompletableFuture<String> startSession(final @NotNull String input, final @NotNull String command, final @NotNull String application) {
        final HttpRequest request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(constructBody(input, command, application)))
                .uri(root.resolve(URI.create("/api/editor/input")))
                .build();
        final CompletableFuture<String> result = new CompletableFuture<>();

        this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).exceptionally(throwable -> {
            throwable.printStackTrace();
            return null;
        }).thenApply(stringHttpResponse -> {
            if (stringHttpResponse.statusCode() != 200) {
                result.completeExceptionally(new IOException("The server could not handle the request."));
            } else {
                final String body = stringHttpResponse.body();
                final Matcher matcher = TOKEN_PATTERN.matcher(body);

                while (matcher.find()) {
                    final String group = matcher.group(0);
                    if (group.equalsIgnoreCase("token")) {
                        result.complete(group);
                    }
                }

                result.completeExceptionally(new IOException("The result did not contain a token."));
            }
            return null;
        });

        return result;
    }

    /**
     * Retrieves the result of a session, given a token.
     *
     * @param token the token
     * @return the resulting MiniMessage string in a completable future
     */
    public @NotNull CompletableFuture<String> retrieveSession(final @NotNull String token) {
        final HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(root.resolve(URI.create("/api/editor/output?token=" + token)))
                .build();
        final CompletableFuture<String> result = new CompletableFuture<>();

        this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(stringHttpResponse -> {
            final int statusCode = stringHttpResponse.statusCode();
            if (statusCode == 404) {
                result.complete(null);
            } else if (statusCode != 200) {
                result.completeExceptionally(new IOException("The server could not handle the request."));
            } else {
                result.complete(stringHttpResponse.body());
            }
            return null;
        });

        return result;
    }

    private @NotNull String constructBody(final @NotNull String input, final @NotNull String command, final @NotNull String application) {
        return String.format("{\"input\":\"%s\",\"command\":\"%s\",\"application\":\"%s\"}", input, command, application);
    }
    public static void main(String[] args) {
        AdventureWebuiEditorAPI api = new AdventureWebuiEditorAPI();
        api.startSession("Hello World", "/sjm applyedits {token}", "RedisEconomy").exceptionally(throwable -> {
            throwable.printStackTrace();
            return null;
        }).thenAccept(token -> {
            System.out.println("Token: " + token);
            api.retrieveSession(token).thenAccept(result -> {
                System.out.println("Result: " + result);
            });
        });
    }
}