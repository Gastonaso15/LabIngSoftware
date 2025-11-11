# 🧊 Sistema de Control de Temperatura

## 📝 Guía de Ejecución del Sistema

Este documento describe los pasos necesarios para levantar y ejecutar el **Sistema de Control de Temperatura**, incluyendo el **simulador (Caja Negra)** y el **sistema principal (LabIngSoftware)**.

---

## ⚙️ Prerrequisitos

Antes de ejecutar el sistema, asegurate de tener instalados los siguientes componentes:

- 🐳 **Docker** y **Docker Compose**
- ☕ **Java 17**
- 🧱 **Maven**
- 📂 **Repositorios clonados:**
    - [`cajaNegra/blackBox`](https://github.com/RamosMariano/cajaNegra/tree/main) → *Simulador*
    - [`LabIngSoftware`](hhttps://github.com/Gastonaso15/LabIngSoftware) → *Sistema de control*

---

## 🚀 Pasos para Ejecutar el Sistema

### 🖥️ Terminal 1: Levantar el Simulador (Caja Negra)

```bash
# Abrir terminal en la carpeta del simulador
cd /home/usuario/cajaNegra/blackBox

# Construir la imagen del simulador (sin cache)
docker compose build --no-cache simulator

# Levantar los servicios (broker MQTT + simulador)
docker compose up -d
```

Esto inicia:
- Broker MQTT en localhost:1883
- Simulador de switches HTTP en http://localhost:8080

### 💻 Terminal 2: Levantar LabIngSoftware

```bash
# Abrir nueva terminal en el proyecto LabIngSoftware
cd /home/usuario/LabIngSoftware

# Compilar el proyecto (saltando tests)
mvn clean package -DskipTests

# Ejecutar la aplicación
mvn spring-boot:run
```
Esto inicia:

- API REST en http://localhost:8081
- Cliente MQTT conectado al broker
- Sistema de control de temperatura operativo

### 🧩 Terminal 3: Verificar el Sistema

```bash
# Health check - Verificar que el sistema está funcionando
curl http://localhost:8081/api/health

# Ver estado completo del sistema
curl http://localhost:8081/api/system/status | jq

# Ver estado de todas las habitaciones
curl http://localhost:8081/api/rooms | jq

# Ver estado de una habitación específica
curl http://localhost:8081/api/rooms/sim/ht/1 | jq
```

## 📊 Respuestas Esperadas

### ✅ Health Check
```bash
{
"status": "UP",
"message": "Temperature Control System is running"
}
```

### 🏠 Estado del Sistema
```bash
{
"max_energy": 14.0,
"current_energy_consumption": 0.0,
"available_energy": 14.0,
"rooms": [...]
}
```
