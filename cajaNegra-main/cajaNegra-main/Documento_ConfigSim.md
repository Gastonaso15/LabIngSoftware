# 🌡️ Configuración del Simulador

Este documento describe la estructura y el significado de los parámetros utilizados para configurar el simulador.

---

## 🏠 Unidades (`units`)

El parámetro principal es un *array* de objetos, donde cada objeto representa una **unidad** que incluye una **habitación**, un **calefactor** y el **modelo térmico** asociado.

---

### 1. Parámetros de la Habitación (`room`)

| Parámetro | Tipo | Ejemplo | Descripción |
| :--- | :--- | :--- | :--- |
| `id` | Número | `1` | **Identificador único** de la habitación, solo utilizado internamente. |
| `name` | Cadena | `"office1"` | **Nombre descriptivo** de la habitación o zona simulada. |
| `T0` | Número | `18.0` | **Temperatura inicial** (en °C) de la habitación al inicio de la simulación. |
| `T_out` | Número | `8.0` | **Temperatura exterior** (en °C), asumida constante durante toda la simulación. |
| `P_in` | Número | `0.0` | **Potencia de entrada adicional** (en W) por fuentes internas distintas al calefactor (personas, computadoras, etc.). En la mayoría de los casos se deja en `0`. |
| `C` | Número | `1600000.0` | **Capacidad calorífica** (en J/°C) de la habitación (masa térmica). |
| `UA` | Número | `80.0` | **Coeficiente global de transferencia de calor** (en W/°C) entre la habitación y el exterior. |
| `expectedTemp` | Cadena | `"22"` | **Temperatura esperada u objetivo** (en °C). Puede utilizarse para control o monitoreo. |
| `energy` | Cadena | `"2 kWh"` | **Energía contratada o límite de consumo** asociado a la habitación. |
| `switch` | Cadena (URL) | `"http://localhost:8080/switch/1"` | **URL del endpoint HTTP** del interruptor virtual asociado al calefactor. Permite encender o apagar el calefactor mediante peticiones `POST`. |
| `sensor` | Cadena | `"sim/ht"` | **Identificador o tópico del sensor** de temperatura asociado a la habitación. |

---

### 2. Parámetros del Calefactor (`calefactor`)

| Parámetro | Tipo | Ejemplo | Descripción |
| :--- | :--- | :--- | :--- |
| `type` | Cadena | `"loza eléctrica"` | **Tipo de calefactor** instalado en la habitación. Actualmente no modifica el modelo térmico, pero sirve para clasificación. |
| `p_electrica` | Número | `1800` | **Potencia eléctrica nominal** (en W) consumida cuando el calefactor está encendido. |
| `state` | Booleano | `true` | **Estado inicial** del calefactor: `true` (encendido) o `false` (apagado) al inicio de la simulación. |
| `p_entregada` | Número | `2000` | **Potencia térmica entregada** (en W) a la habitación cuando el calefactor está encendido. |

---

### 3. Parámetros del Modelo Térmico (`modeloTermico`)

| Parámetro | Tipo | Ejemplo | Descripción |
| :--- | :--- | :--- | :--- |
| `intervaloSegundos` | Número | `1` | **Intervalo temporal de simulación** (Δt) en segundos. Define la frecuencia de cálculo del modelo térmico. |

---

## ⚙️ Parámetros de la Simulación (`simulacion`)

Estos parámetros definen la ejecución general del simulador.

| Parámetro | Tipo | Ejemplo | Descripción |
| :--- | :--- | :--- | :--- |
| `duracionSegundos` | Número | `10000` | **Duración total** de la simulación en segundos. |
| `escenario` | Número | `1` | **Identificador del escenario** de simulación. Los escenarios pueden activar distintas condiciones o fallas. |
| `warp` | Número | `10.0` | **Factor de aceleración temporal**. Un valor de `10.0` indica que la simulación corre 10 veces más rápido que el tiempo real. |
| `site` | Cadena | `"oficinaA"` | **Nombre o identificador del sitio** donde se ejecuta la simulación. |
| `maxEnergy` | Cadena | `"14 kWh"` | **Energía máxima contratada** o límite total del sitio. |
| `timeSlot` | Objeto | `{ "contractType": "std", "refreshPeriod": "10000 ms" }` | **Configuración del contrato energético o ventana temporal de actualización.** <br>• `contractType`: tipo de contrato (ej. `"std"`). <br>• `refreshPeriod`: período de actualización del plan o monitoreo (en ms). |

---

## 💻 Ejemplo Completo de Configuración JSON

```json
{
  "units": [
    {
      "room": {
        "id": 1,
        "name": "office1",
        "T0": 18.0,
        "T_out": 8.0,
        "P_in": 0.0,
        "C": 1600000.0,
        "UA": 80.0,
        "expectedTemp": "22",
        "energy": "2 kWh",
        "switch": "http://localhost:8080/switch/1",
        "sensor": "sim/ht"
      },
      "calefactor": {
        "type": "loza electrica",
        "p_electrica": 1800,
        "state": false,
        "p_entregada": 2000
      },
      "modeloTermico": { "intervaloSegundos": 1 }
    },
    {
      "room": {
        "id": 2,
        "name": "office2",
        "T0": 20.0,
        "T_out": 8.0,
        "P_in": 0.0,
        "C": 1600000.0,
        "UA": 80.0,
        "expectedTemp": "21",
        "energy": "2 kWh",
        "switch": "http://localhost:8080/switch/2",
        "sensor": "sim/ht"
      },
      "calefactor": {
        "type": "loza hidraulica",
        "p_electrica": 1500,
        "state": true,
        "p_entregada": 1500
      },
      "modeloTermico": { "intervaloSegundos": 1 }
    },
    {
      "room": {
        "id": 3,
        "name": "office3",
        "T0": 10.0,
        "T_out": 8.0,
        "P_in": 0.0,
        "C": 1600000.0,
        "UA": 80.0,
        "expectedTemp": "21",
        "energy": "2 kWh",
        "switch": "http://localhost:8080/switch/3",
        "sensor": "sim/ht"
      },
      "calefactor": {
        "type": "loza hidraulica",
        "p_electrica": 1500,
        "state": true,
        "p_entregada": 1500
      },
      "modeloTermico": { "intervaloSegundos": 1 }
    }
  ],
  "simulacion": {
    "duracionSegundos": 10000,
    "escenario": 1,
    "warp": 10.0,
    "site": "oficinaA",
    "maxEnergy": "14 kWh",
    "timeSlot": { "contractType": "std", "refreshPeriod": "10000 ms" }
  }
}
