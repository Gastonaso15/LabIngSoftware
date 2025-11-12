# Simulador de Temperatura

## Descripción
Este proyecto es un simulador de temperatura en Java que modela el comportamiento térmico de distintas habitaciones.
El sistema permite observar cómo varía la temperatura de cada habitación a lo largo del tiempo según las condiciones iniciales y los parámetros configurados, enviando mensajes de la temperatura actual conseguida por los sensores, además de poder interactuar con los switches para alternar cada calefactor entre prendido y apagado.

---

## Documentación relacionada

Para entender como funciona el simulador :  
[Documento de Arquitectura del Simulador](https://docs.google.com/document/d/1PwJ3WEwyra6XNFGx6mo1zSwLo5OBNGMa4aqmlQuKOGU/edit?usp=sharing)

Para aprender a configurar el simulador (Por si quieren hacer pruebas con parámetros iniciales diferentes) :  
[Documento de Configuración del Simulador](./Documento_ConfigSim.md)

Para saber como interactuar con los endpoints del simulador :  
Opción 1 :  
Descargate el proyecto y abrí el .html en el navegador.  
Opción 2 :  
Descargate el proyecto y copia el contenido del archivo .yml en Swagger Editor (https://editor.swagger.io/).  
Opción 3 :  
[Endpoints Swagger](https://redocly.github.io/redoc/?url=https://raw.githubusercontent.com/RamosMariano/cajaNegra/master/IoTEste_Swagger.yml)

---

## Uso del simulador

1 - DESCARGAR ESTE REPOSITORIO

2 - AGREGAR TU CONTENEDOR A LA CARPETA "blackBox"

3 - AGREGAR EL SERVICIO DE TU CONTENEDOR DENTRO DEL DOCKER COMPOSE

### Comandos de ejecución
```bash
# Dentro de la carpeta "blackBox" - Construir el simulador
docker compose build --no-cache simulator

# Dentro de la carpeta "blackBox"
docker compose up -d
