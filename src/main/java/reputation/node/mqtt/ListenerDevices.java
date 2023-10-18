package reputation.node.mqtt;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.logging.Logger;

import reputation.node.models.Node;
import reputation.node.utils.MQTTClient;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public class ListenerDevices implements IMqttMessageListener {

  /*-------------------------Constantes---------------------------------------*/
  private static final int QOS = 1;
  /*--------------------------------------------------------------------------*/

  private MQTTClient MQTTClient;
  private final Node node;
  private static final Logger logger = Logger.getLogger(
    ListenerDevices.class.getName()
  );

  /**
   * Método construtor.
   *
   * @param MQTTClient MQTTClient -  Cliente MQTT.
   * @param node NodeType - Nó que executa o ListenerDevice.
   */
  public ListenerDevices(MQTTClient MQTTClient, Node node) {
    this.MQTTClient = MQTTClient;
    this.node = node;
  }

  /**
   * Se inscreve no tópico no qual o dispositivo irá responder às publicações
   * do nó.
   
   * @param deviceId String - ID do dispositivo para inscrever no tópico.
   */
  public void subscribe(String deviceId) {
    String topic = String.format("dev/%s/RES", deviceId);
    logger.info("Subscribing to: " + topic);

    this.MQTTClient.subscribe(QOS, this, topic);
  }

  /**
   * Se desinscreve do tópico de um dispositivo.
   *
   * @param deviceId String - ID do dispositivo para desinscrever do tópico.
   */
  public void unsubscribe(String deviceId) {
    String topic = String.format("dev/%s/RES", deviceId);
    logger.info("Unsubscribing from: " + topic);

    this.MQTTClient.unsubscribe(topic);
  }

  @Override
  public void messageArrived(String topic, MqttMessage message)
    throws Exception {
    final String mqttMessage = new String(message.getPayload());

    logger.info(mqttMessage);

    JsonObject jsonResponse = new Gson()
      .fromJson(mqttMessage, JsonObject.class);
    String deviceId = jsonResponse
      .get("HEADER")
      .getAsJsonObject()
      .get("NAME")
      .getAsString();

    logger.info("Device Id: " + deviceId);

    if (this.node.getWaitDeviceResponseTask() != null) {
      this.node.getWaitDeviceResponseTask().cancel();
    }
    // Avaliação de serviço prestado corretamente.
    try {
      this.node.getNodeType().getNode().evaluateDevice(deviceId, 1);
    } catch (Exception e) {
      logger.warning("Could not add transaction on tangle network.");
    }
  }
}
