package co.edu.escuelaing.reflexionlab;

import org.reflections.Reflections;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MicroSpringBoot {

    private final Map<String, HandlerMethod> getRoutes = new HashMap<>();

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Uso: java -cp target/classes co.edu.escuelaing.reflexionlab.MicroSpringBoot <ControllerClassOptional>");
            System.out.println("Ejemplo: java -cp target/classes co.edu.escuelaing.reflexionlab.MicroSpringBoot co.edu.escuelaing.reflexionlab.GreetingController");
        }

        MicroSpringBoot app = new MicroSpringBoot();

        // Versión 1: si se pasa una clase por línea de comandos, se carga explícitamente
        if (args.length >= 1) {
            String controllerClassName = args[0];
            app.registerController(controllerClassName);
        }

        // Versión "final": escanear el classpath buscando @RestController
        app.scanAndRegisterControllers("co.edu.escuelaing.reflexionlab");

        HttpServer server = new HttpServer(8080, app::handleRequest);
        server.start();
    }

    private void scanAndRegisterControllers(String basePackage) {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> controllerClasses = reflections.getTypesAnnotatedWith(RestController.class);
        for (Class<?> clazz : controllerClasses) {
            registerController(clazz);
        }
    }

    private void registerController(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            registerController(clazz);
        } catch (ClassNotFoundException e) {
            System.err.println("No se encontró la clase controlador: " + className);
        }
    }

    private void registerController(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(RestController.class)) {
            return;
        }
        try {
            Object instance = clazz.getDeclaredConstructor().newInstance();
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(GetMapping.class)) {
                    GetMapping mapping = method.getAnnotation(GetMapping.class);
                    String path = mapping.value();
                    getRoutes.put(path, new HandlerMethod(instance, method));
                    System.out.println("Registrado GET " + path + " -> " + clazz.getSimpleName() + "." + method.getName());
                }
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    private void handleRequest(HttpRequest request, OutputStream responseStream) throws IOException {
        // Solo soportamos GET en este prototipo
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            sendText(responseStream, 405, "Method Not Allowed", "Método no soportado");
            return;
        }

        // Primero intentar rutas dinámicas (@GetMapping)
        HandlerMethod handlerMethod = getRoutes.get(request.getPath());
        if (handlerMethod != null) {
            invokeHandlerMethod(handlerMethod, request, responseStream);
            return;
        }

        // Si no hay handler, intentar servir recursos estáticos (html, png)
        if (serveStaticResource(request, responseStream)) {
            return;
        }

        // Si no se encontró nada
        sendText(responseStream, 404, "Not Found", "Ruta no encontrada");
    }

    private void invokeHandlerMethod(HandlerMethod handlerMethod, HttpRequest request, OutputStream responseStream) throws IOException {
        Method method = handlerMethod.method;
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        Map<String, String> queryParams = request.getQueryParams();

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
            if (requestParam != null) {
                String name = requestParam.value();
                String defaultValue = requestParam.defaultValue();
                String value = queryParams.getOrDefault(name, defaultValue);
                // Solo soportamos parámetros String en este prototipo
                args[i] = value;
            } else {
                args[i] = null;
            }
        }

        try {
            Object result = method.invoke(handlerMethod.instance, args);
            if (result instanceof String) {
                sendText(responseStream, 200, "OK", (String) result);
            } else {
                sendText(responseStream, 500, "Internal Server Error", "Tipo de retorno no soportado");
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            sendText(responseStream, 500, "Internal Server Error", "Error ejecutando el controlador");
        }
    }

    private boolean serveStaticResource(HttpRequest request, OutputStream responseStream) throws IOException {
        String path = request.getPath();
        if ("/".equals(path)) {
            path = "/index.html";
        }

        String resourcePath = "public" + path; // src/main/resources/public
        String contentType;
        if (path.endsWith(".html")) {
            contentType = "text/html; charset=UTF-8";
        } else if (path.endsWith(".png")) {
            contentType = "image/png";
        } else {
            return false;
        }

        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                return false;
            }
            byte[] bytes = in.readAllBytes();
            HttpServer.writeHttpResponse(responseStream, 200, "OK", contentType, bytes);
            return true;
        }
    }

    private void sendText(OutputStream responseStream, int statusCode, String statusText, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpServer.writeHttpResponse(responseStream, statusCode, statusText, "text/html; charset=UTF-8", bytes);
    }

    private static class HandlerMethod {
        private final Object instance;
        private final Method method;

        HandlerMethod(Object instance, Method method) {
            this.instance = instance;
            this.method = method;
        }
    }
}
