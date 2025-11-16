package com.iotest.domain.model.Logica;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class SwitchController implements ISwitchController {
    private static final Logger logger = LoggerFactory.getLogger(SwitchController.class);
    
    //dejo un controlador vacio publico
    public SwitchController(){}
    // Creo el cliente Http que se comunica con el switch
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();



public String getSwitchStatus(String SwitchURL) throws IOException, InterruptedException{
    logger.debug("Consultando estado del switch: {}", SwitchURL);
    //Hago un request, le paso la url del switch y pido
    HttpRequest req = HttpRequest.newBuilder(URI.create(SwitchURL))
            .GET().timeout(Duration.ofSeconds(5)).header("Accept", "application/json").build();

    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    logger.debug("Respuesta GET switch {}: código {}, cuerpo: {}", SwitchURL, resp.statusCode(), resp.body());
    
    if (resp.statusCode() == 200) {  //200 es que vino OK
        String SwitchStatus = resp.body(); // Pido el cuerpo del Json si el status es OK
        return SwitchStatus;
    }else{
        logger.error("Error HTTP {} al consultar switch {}", resp.statusCode(), SwitchURL);
        throw new IOException("Error HTTP " + resp.statusCode() + " al consultar " + SwitchURL);
    }

}


public String postSwitchStatus(String SwitchURL, boolean estadoDeseado) throws IOException, InterruptedException{
    logger.info("Intentando {} switch en URL: {}", estadoDeseado ? "encender" : "apagar", SwitchURL);
    
    // Construir JSON según especificación de la API: {"state": true/false}
    String estadoSwitch = "{\"state\":" + estadoDeseado + "}";
    logger.debug("Enviando JSON: {} a {}", estadoSwitch, SwitchURL);

    HttpRequest setReq = HttpRequest.newBuilder(URI.create(SwitchURL))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(estadoSwitch, StandardCharsets.UTF_8))
            .build();

    HttpResponse<String> setResp = http.send(setReq, HttpResponse.BodyHandlers.ofString());
    logger.info("Respuesta POST switch {}: código {}, cuerpo: {}", SwitchURL, setResp.statusCode(), setResp.body());
    
    // Aceptar códigos de éxito (200-299)
    if (setResp.statusCode() >= 200 && setResp.statusCode() < 300) {
        // Verificar que el switch realmente cambió de estado
        try {
            // Esperar un momento para que el switch procese el cambio
            Thread.sleep(100);
            String actualStatus = getSwitchStatus(SwitchURL);
            logger.info("Estado actual del switch {} después de la operación: {}", SwitchURL, actualStatus);
            
            // Verificar que el estado en la respuesta coincide con lo deseado
            // La respuesta del switch devuelve un campo "state" (boolean)
            // Formato: {"id":1,"state":true/false}
            boolean estadoReal = actualStatus.contains("\"state\":true") || actualStatus.contains("\"state\": true");
            if (estadoReal != estadoDeseado) {
                logger.error("⚠️  DESINCRONIZACIÓN: Switch {} debería estar {} pero está {}. Respuesta completa: {}", 
                    SwitchURL, estadoDeseado ? "encendido" : "apagado", estadoReal ? "encendido" : "apagado", actualStatus);
                throw new IOException("El switch no cambió de estado correctamente. Estado deseado: " + estadoDeseado + ", Estado real: " + estadoReal + ". Respuesta: " + actualStatus);
            }
            
            logger.info("✅ Switch {} {} exitosamente", SwitchURL, estadoDeseado ? "encendido" : "apagado");
            return ("Respuesta: " + setResp.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupción al verificar estado del switch", e);
        } catch (IOException e) {
            logger.error("Error al verificar estado del switch después de la operación: {}", e.getMessage());
            throw e;
        }
    } else {
        logger.error("❌ Error HTTP {} al setear switch {}. Respuesta: {}", setResp.statusCode(), SwitchURL, setResp.body());
        throw new IOException("Error HTTP " + setResp.statusCode() + " al setear switch " + SwitchURL + ". Respuesta: " + setResp.body());
    }
}

}
