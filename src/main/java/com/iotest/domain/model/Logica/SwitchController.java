package com.iotest.domain.model.Logica;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class SwitchController implements ISwitchController {
    //dejo un controlador vacio publico
    public SwitchController(){}
    // Creo el cliente Http que se comunica con el switch
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();



public String getSwitchStatus(String SwitchURL) throws IOException, InterruptedException{
//Hago un request, le paso la url del switch y pido
    HttpRequest req = HttpRequest.newBuilder(URI.create(SwitchURL))
            .GET().timeout(Duration.ofSeconds(5)).header("Accept", "application/json").build();

    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() == 200) {  //200 es que vino OK
        String SwitchStatus = resp.body(); // Pido el cuerpo del Json si el status es OK
        return SwitchStatus;
    }else{
        throw new IOException("Error HTTP " + resp.statusCode() + " al consultar " + SwitchURL);
    }

}


public String postSwitchStatus(String SwitchURL, boolean estadoDeseado) throws IOException, InterruptedException{
    // Construir JSON según especificación de la API: {"state": true/false}
    String estadoSwitch = "{\"state\":" + estadoDeseado + "}";

    HttpRequest setReq = HttpRequest.newBuilder(URI.create(SwitchURL))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(estadoSwitch, StandardCharsets.UTF_8))
            .build();

    HttpResponse<String> setResp = http.send(setReq, HttpResponse.BodyHandlers.ofString());
    
    // Aceptar códigos de éxito (200-299)
    if (setResp.statusCode() >= 200 && setResp.statusCode() < 300) {
        return ("Respuesta: " + setResp.body());
    } else {
        throw new IOException("Error HTTP " + setResp.statusCode() + " al setear switch " + SwitchURL + ". Respuesta: " + setResp.body());
    }
}

}
