package com.lm.android.tv.player;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;

/** HTTP server for the fixture tree, including the byte ranges used by ExoPlayer. */
final class MockMediaServer implements Closeable {
    private final MockWebServer server = new MockWebServer();
    private final Set<String> requestedPaths = Collections.synchronizedSet(new HashSet<>());

    MockMediaServer() throws IOException {
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                if (request.getRequestUrl() == null) return new MockResponse().setResponseCode(400);
                StringBuilder path = new StringBuilder();
                for (String segment : request.getRequestUrl().pathSegments()) {
                    if (segment.equals(".") || segment.equals("..") || segment.contains("/")) {
                        return new MockResponse().setResponseCode(400);
                    }
                    if (path.length() > 0) path.append('/');
                    path.append(segment);
                }
                String name = path.toString();
                requestedPaths.add("/" + name);
                byte[] bytes;
                try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext()
                        .getAssets().open("mock-library/" + name);
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    bytes = output.toByteArray();
                } catch (IOException e) {
                    return new MockResponse().setResponseCode(404);
                }
                int start = 0;
                int end = bytes.length - 1;
                String range = request.getHeader("Range");
                if (range != null) {
                    try {
                        if (!range.startsWith("bytes=") || range.contains(",")) throw new IllegalArgumentException();
                        String[] bounds = range.substring(6).split("-", -1);
                        if (bounds.length != 2 || bounds[0].isEmpty()) throw new IllegalArgumentException();
                        start = Integer.parseInt(bounds[0]);
                        if (!bounds[1].isEmpty()) end = Math.min(end, Integer.parseInt(bounds[1]));
                        if (start < 0 || start > end) throw new IllegalArgumentException();
                    } catch (IllegalArgumentException e) {
                        return new MockResponse().setResponseCode(416)
                                .setHeader("Content-Range", "bytes */" + bytes.length);
                    }
                }
                MockResponse response = new MockResponse().setHeader("Accept-Ranges", "bytes")
                        .setHeader("Content-Type", name.endsWith(".json") ? "application/json; charset=utf-8"
                                : name.endsWith(".png") ? "image/png" : "video/mp4")
                        .setBody(new Buffer().write(bytes, start, end - start + 1));
                if (range != null) response.setResponseCode(206)
                        .setHeader("Content-Range", "bytes " + start + "-" + end + "/" + bytes.length);
                return response;
            }
        });
        server.start();
    }

    String baseUrl() { return server.url("/").toString().replaceAll("/$", ""); }
    boolean requested(String path) { return requestedPaths.contains(path); }
    @Override public void close() throws IOException { server.close(); }
}
