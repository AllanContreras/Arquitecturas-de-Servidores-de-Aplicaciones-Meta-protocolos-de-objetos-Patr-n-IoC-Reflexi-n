# Taller: Arquitecturas de Servidores de Aplicaciones, Meta-protocolos de Objetos, Patrón IoC y Reflexión

Este proyecto implementa un **micro servidor Web tipo Apache en Java**, capaz de:

- Servir páginas **HTML** y recursos estáticos (por ahora, **imágenes PNG** y otros HTML) desde el classpath.
- Exponer servicios Web construidos a partir de **POJOs anotados**, usando un mini framework tipo *Spring Boot* que soporta:
  - `@RestController`
  - `@GetMapping`
  - `@RequestParam`
- Atender múltiples solicitudes **no concurrentes** (un solo hilo atiende una petición a la vez).

El objetivo del taller es demostrar las capacidades de **reflexión en Java** y un **mini framework IoC** que descubre componentes a partir de anotaciones y deriva una aplicación Web a partir de ellos.

---

## 1. Estructura del proyecto

Proyecto manejado con **Maven**, con la siguiente estructura relevante:

- Código fuente Java de la aplicación:
  - [src/main/java/co/edu/escuelaing/reflexionlab/MicroSpringBoot.java](src/main/java/co/edu/escuelaing/reflexionlab/MicroSpringBoot.java)
  - [src/main/java/co/edu/escuelaing/reflexionlab/HttpServer.java](src/main/java/co/edu/escuelaing/reflexionlab/HttpServer.java)
  - [src/main/java/co/edu/escuelaing/reflexionlab/HttpRequest.java](src/main/java/co/edu/escuelaing/reflexionlab/HttpRequest.java)
- Anotaciones del mini framework IoC:
  - [src/main/java/co/edu/escuelaing/reflexionlab/RestController.java](src/main/java/co/edu/escuelaing/reflexionlab/RestController.java)
  - [src/main/java/co/edu/escuelaing/reflexionlab/GetMapping.java](src/main/java/co/edu/escuelaing/reflexionlab/GetMapping.java)
  - [src/main/java/co/edu/escuelaing/reflexionlab/RequestParam.java](src/main/java/co/edu/escuelaing/reflexionlab/RequestParam.java)
- Controlador de ejemplo (componente requerido en el enunciado):
  - [src/main/java/co/edu/escuelaing/reflexionlab/GreetingController.java](src/main/java/co/edu/escuelaing/reflexionlab/GreetingController.java)
- Recursos estáticos (HTML / imágenes):
  - [src/main/resources/public/index.html](src/main/resources/public/index.html)

Archivo de configuración Maven:

- [pom.xml](pom.xml)

---

## 2. Funcionamiento del micro servidor e IoC

### 2.1. Micro servidor HTTP

La clase `HttpServer` implementa un servidor HTTP muy sencillo:

- Escucha en un puerto (por defecto, **8080**).
- Atiende **una petición a la vez** (no hay hilos concurrentes, cumpliendo el requisito del taller).
- Parsea la primera línea de la petición, por ejemplo: `GET /greeting?name=Allan HTTP/1.1`.
- Extrae:
  - Método HTTP (`GET`).
  - Ruta (`/greeting`).
  - Parámetros de consulta (`name=Allan`).
- Construye un objeto `HttpRequest` y lo pasa a un `HttpHandler` (implementado por `MicroSpringBoot`).
- Envía la respuesta formateando el protocolo HTTP (línea de estado, cabeceras y cuerpo).

### 2.2. Mini framework IoC y reflexión

La clase `MicroSpringBoot` actúa como un mini *framework* estilo Spring Boot:

1. **Carga de componentes vía reflexión**
   - En la **versión mínima**, puede recibir por línea de comandos el nombre completo de un controlador, por ejemplo:
     - `co.edu.escuelaing.reflexionlab.GreetingController`.
   - Usa `Class.forName(...)` para cargar la clase y `getDeclaredConstructor().newInstance()` para crear una instancia.

2. **Descubrimiento automático de componentes `@RestController`**
   - En la **versión más completa**, utiliza la librería **Reflections** para escanear el paquete base `co.edu.escuelaing.reflexionlab` y encontrar todas las clases anotadas con `@RestController`.
   - Por cada clase encontrada, crea una instancia y busca métodos anotados con `@GetMapping`.

3. **Registro de rutas `@GetMapping`**
   - Cada método con `@GetMapping("/ruta")` se registra en un mapa interno `path -> HandlerMethod`.
   - Al llegar una petición GET a esa ruta, el framework invoca el método correspondiente.

4. **Inyección de parámetros `@RequestParam`**
   - Para cada parámetro del método anotado, si está anotado con `@RequestParam`, el framework:
     - Lee el nombre y el `defaultValue` de la anotación.
     - Busca el valor en `request.getQueryParams()`.
     - Si no existe, usa el valor por defecto.
   - Actualmente el prototipo se limita a parámetros de tipo `String`.

5. **Tipos de retorno soportados**
   - Por ahora, solo se soporta tipo de retorno `String` en los métodos anotados con `@GetMapping`.
   - El framework toma ese `String` y lo envía como cuerpo de la respuesta HTTP (texto/HTML básico).

### 2.3. Controlador de ejemplo: `GreetingController`

El componente de ejemplo implementa exactamente lo solicitado en el enunciado:

```java
@RestController
public class GreetingController {

    private static final String template = "Hello, %s!";
    private final AtomicLong counter = new AtomicLong();

    @GetMapping("/greeting")
    public String greeting(@RequestParam(value = "name", defaultValue = "World") String name) {
        return "Hola " + name;
    }
}
```

- Endpoint: `GET /greeting`.
- Parámetro: `name` vía query string.
  - Ejemplo: `/greeting?name=Allan` → `Hola Allan`.
  - Si no se envía `name`, se usa el valor por defecto `World`.

---

## 3. Compilación y ejecución local

### 3.1. Requisitos previos

- **Java 17** (o compatible, según configuración del `pom.xml`).
- **Maven 3.x**.

### 3.2. Compilar el proyecto

En la raíz del proyecto (donde está `pom.xml`):

```bash
mvn -DskipTests package
```

Esto compila el código y genera:

- Clases compiladas en `target/classes`.
- JAR del proyecto: `target/reflexionlab-1.0-SNAPSHOT.jar`.

### 3.3. Ejecutar usando el `classpath` (versión tipo línea de comandos del taller)

El enunciado propone una invocación del estilo:

```bash
java -cp target/classes co.edu.escuelaing.reflexionlab.MicroSpringBoot co.edu.escuelaing.reflexionlab.FirstWebService
```

En nuestro caso, el controlador de ejemplo es `GreetingController` y además se requiere incluir la dependencia de la librería **Reflections** en el classpath.

#### Windows (PowerShell / CMD)

```bash
mvn dependency:copy-dependencies
java -cp "target/classes;target/dependency/*" co.edu.escuelaing.reflexionlab.MicroSpringBoot co.edu.escuelaing.reflexionlab.GreetingController
```

#### Linux / macOS

```bash
mvn dependency:copy-dependencies
java -cp "target/classes:target/dependency/*" co.edu.escuelaing.reflexionlab.MicroSpringBoot co.edu.escuelaing.reflexionlab.GreetingController
```

Una vez el servidor está corriendo en el puerto **8080**, se pueden probar los endpoints desde el navegador:

- `http://localhost:8080/` → página HTML de ejemplo (`index.html`).
- `http://localhost:8080/greeting` → responde `Hola World`.
- `http://localhost:8080/greeting?name=Allan` → responde `Hola Allan`.

---

## 4. Rutas soportadas y recursos estáticos

### 4.1. Rutas dinámicas (controladores)

- `GET /greeting` (controlador `GreetingController`).
  - Parámetros soportados:
    - `name` (opcional, `String`, valor por defecto `"World"`).

### 4.2. Recursos estáticos

Los recursos estáticos se sirven desde el classpath bajo `src/main/resources/public`:

- `/` → `public/index.html`.
- Cualquier recurso `*.html` bajo `public` se puede servir como `http://host:8080/ruta.html`.
- Cualquier recurso `*.png` bajo `public` se sirve como imagen, por ejemplo:
  - `src/main/resources/public/img/logo.png` → `http://host:8080/img/logo.png`.

---

## 5. Despliegue en AWS (guía)

A continuación se describe una guía general para desplegar el micro servidor en AWS usando una instancia **EC2**. Esta sección sirve también como base para las evidencias solicitadas en el taller.

### 5.1. Crear instancia EC2

1. Ingresar a la consola de AWS → EC2.
2. Crear una nueva instancia, por ejemplo:
   - AMI: **Amazon Linux 2**.
   - Tipo de instancia: `t2.micro` (o similar, dentro del free tier si aplica).
3. Configurar el **Security Group** para permitir tráfico al puerto **8080/TCP** (y 22/TCP para SSH):
   - Regla para puerto 8080: origen puede ser `0.0.0.0/0` (para pruebas) o la IP de la universidad.

### 5.2. Instalar Java, Maven y Git en la instancia

Conectarse por SSH:

```bash
ssh -i tu_llave.pem ec2-user@IP_PUBLICA_EC2
```

Instalar dependencias (ejemplo Amazon Linux):

```bash
sudo yum update -y
sudo yum install -y java-17-amazon-corretto maven git
```

### 5.3. Obtener el proyecto desde GitHub

En la instancia EC2:

```bash
git clone https://github.com/AllanContreras/Arquitecturas-de-Servidores-de-Aplicaciones-Meta-protocolos-de-objetos-Patr-n-IoC-Reflexi-n.git
cd Arquitecturas-de-Servidores-de-Aplicaciones-Meta-protocolos-de-objetos-Patr-n-IoC-Reflexi-n
```

### 5.4. Compilar y ejecutar en EC2

Compilar:

```bash
mvn -DskipTests package
mvn dependency:copy-dependencies
```

Ejecutar el servidor:

```bash
java -cp "target/classes:target/dependency/*" co.edu.escuelaing.reflexionlab.MicroSpringBoot co.edu.escuelaing.reflexionlab.GreetingController
```

> Nota: se puede usar `nohup` o `tmux`/`screen` para dejar el servidor corriendo en segundo plano mientras se cierra la sesión SSH.

Probar desde el navegador local:

- `http://IP_PUBLICA_EC2:8080/` → página HTML.
- `http://IP_PUBLICA_EC2:8080/greeting?name=AWS` → saludo desde el servidor en la nube.

---

## 6. Evidencias

Esta sección está pensada para agregar capturas de pantalla y/o enlaces como evidencia del funcionamiento del servidor según lo solicitado en el taller.

### 6.1. Evidencias de ejecución local

**Capturas sugeridas:**

- Consola mostrando `mvn package` exitoso.
- Consola mostrando la ejecución del servidor (`MicroSpringBoot` escuchando en el puerto 8080).
- Navegador accediendo a:
  - `http://localhost:8080/`.
  - `http://localhost:8080/greeting`.
  - `http://localhost:8080/greeting?name=<TU_NOMBRE>`.



- <img width="1440" height="934" alt="image" src="https://github.com/user-attachments/assets/2d5330ce-d221-44cc-b033-147ac1d29826" />
  

- <img width="1906" height="974" alt="image" src="https://github.com/user-attachments/assets/fe7e4732-f89f-44e6-9069-f595417d02e8" />


- <img width="1767" height="1035" alt="image" src="https://github.com/user-attachments/assets/6af31eae-afec-4b40-ad22-647a470d9799" />

- <img width="1724" height="1040" alt="image" src="https://github.com/user-attachments/assets/189af5b0-12c8-402a-8a43-f9d5a40d0188" />

 


### 6.2. Evidencias de despliegue en AWS

**Capturas sugeridas:**
<img width="1918" height="957" alt="image" src="https://github.com/user-attachments/assets/806f7b40-696b-46cf-9443-49715f785401" />
<img width="1802" height="395" alt="image" src="https://github.com/user-attachments/assets/88c1690c-8a68-413c-af6c-60f10e10a1e6" />
<img width="1917" height="223" alt="image" src="https://github.com/user-attachments/assets/3eb55c68-cb36-40c6-89cf-8439b3a406e9" />

<img width="1735" height="668" alt="image" src="https://github.com/user-attachments/assets/67670aee-2f25-4de0-ac4f-2ac383d94f6c" />
<img width="1899" height="893" alt="image" src="https://github.com/user-attachments/assets/4270a699-3dcf-46e5-895f-fc6ff688703b" />

<img width="1775" height="672" alt="image" src="https://github.com/user-attachments/assets/d7e9feef-5b6f-4e8b-ba02-d2cef20e1191" />

<img width="1504" height="820" alt="image" src="https://github.com/user-attachments/assets/09f58463-54e8-4651-9e96-346664703f33" />

<img width="1899" height="332" alt="image" src="https://github.com/user-attachments/assets/261c9377-2a05-4662-a925-b47a6dea5a68" />
<img width="1800" height="144" alt="image" src="https://github.com/user-attachments/assets/e3723588-c502-4baa-b511-c12e5ba4a37b" />

<img width="1917" height="746" alt="image" src="https://github.com/user-attachments/assets/df99a0c7-5804-4400-a1cf-9db7faac3977" />
<img width="1919" height="825" alt="image" src="https://github.com/user-attachments/assets/eb8f6fa2-ac88-4287-b54e-265efb182d6a" />
<img width="1919" height="784" alt="image" src="https://github.com/user-attachments/assets/be0cabb9-47fe-402a-9711-abc6cb41fd76" />

<img width="1905" height="867" alt="image" src="https://github.com/user-attachments/assets/f45c5da8-eefa-477f-9012-5d37f3e6d06e" />
<img width="1897" height="156" alt="image" src="https://github.com/user-attachments/assets/aad27f1e-9d90-4c32-868c-c4e4080e0ecd" />

<img width="1705" height="372" alt="image" src="https://github.com/user-attachments/assets/1d31ce18-1a75-4a6d-a77b-44e76777a7f7" />
<img width="1910" height="966" alt="image" src="https://github.com/user-attachments/assets/4ceeb8e6-e693-48b1-b453-7f4aaacffcf7" />
<img width="1916" height="911" alt="image" src="https://github.com/user-attachments/assets/52cc0970-8564-4dac-a5dc-5792a3e40d9f" />
<img width="1903" height="711" alt="image" src="https://github.com/user-attachments/assets/4907f9b0-bb1c-4a3e-9d7e-2ea8976bf575" />

- Vista de la consola de AWS con la instancia EC2 en ejecución.
- Terminal conectada por SSH mostrando la ejecución del servidor.
- Navegador accediendo a:
  - `http://IP_PUBLICA_EC2:8080/`.
  - `http://IP_PUBLICA_EC2:8080/greeting?name=<TU_NOMBRE>`.

**Espacio para insertar imágenes (Markdown):**

- ![AWS - instancia EC2](docs/img/aws-ec2-instancia.png) <!-- TODO: agregar captura -->
- ![AWS - servidor corriendo](docs/img/aws-servidor-corriendo.png) <!-- TODO: agregar captura -->
- ![AWS - navegador index](docs/img/aws-index.png) <!-- TODO: agregar captura -->
- ![AWS - navegador greeting](docs/img/aws-greeting.png) <!-- TODO: agregar captura -->

