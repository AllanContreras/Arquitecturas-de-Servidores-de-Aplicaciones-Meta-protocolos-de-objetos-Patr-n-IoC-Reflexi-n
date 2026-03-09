package co.edu.escuelaing.reflexionlab;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HttpServer {

    public interface HttpHandler {
        void handle(HttpRequest request, OutputStream responseStream) throws IOException;
    }

    private final int port;
    private final HttpHandler handler;

    public HttpServer(int port, HttpHandler handler) {
        this.port = port;
        this.handler = handler;
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Micro servidor escuchando en puerto " + port);
            while (true) { // solicitudes no concurrentes (un hilo)
                try (Socket clientSocket = serverSocket.accept()) {
                    handleClient(clientSocket);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleClient(Socket clientSocket) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
        OutputStream out = clientSocket.getOutputStream();

        String requestLine = in.readLine();
        if (requestLine == null || requestLine.isEmpty()) {
            return;
        }

        String[] parts = requestLine.split(" ");
        if (parts.length < 3) {
            return;
        }

        String method = parts[0];
        String fullPath = parts[1];

        // Consumir el resto de cabeceras (ignoradas en este prototipo)
        String header;
        while ((header = in.readLine()) != null && !header.isEmpty()) {
            // no-op
        }

        String path = fullPath;
        Map<String, String> queryParams = new HashMap<>();

        int questionIndex = fullPath.indexOf('?');
        if (questionIndex != -1) {
            path = fullPath.substring(0, questionIndex);
            String query = fullPath.substring(questionIndex + 1);
            parseQueryParams(query, queryParams);
        }

        HttpRequest request = new HttpRequest(method, path, queryParams);

        handler.handle(request, out);

        out.flush();
    }

    private void parseQueryParams(String query, Map<String, String> queryParams) {
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) continue;
            String[] kv = pair.split("=", 2);
            String key = urlDecode(kv[0]);
            String value = kv.length > 1 ? urlDecode(kv[1]) : "";
            queryParams.put(key, value);
        }
    }

    private String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    public static void writeHttpResponse(OutputStream out, int statusCode, String statusText, String contentType, byte[] body) throws IOException {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writer.printf("HTTP/1.1 %d %s\r\n", statusCode, statusText);
        writer.printf("Content-Type: %s\r\n", contentType);
        writer.printf("Content-Length: %d\r\n", body.length);
        writer.print("Connection: close\r\n");
        writer.print("\r\n");
        writer.flush();
        out.write(body);
    }
}
