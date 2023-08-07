package br.uefs.larsid.iot.soft.models;

import br.uefs.larsid.extended.mapping.devices.services.IDevicePropertiesManager;
import br.uefs.larsid.iot.soft.models.conducts.Conduct;
import br.uefs.larsid.iot.soft.models.conducts.Honest;
import br.uefs.larsid.iot.soft.models.conducts.Malicious;
import br.uefs.larsid.iot.soft.mqtt.ListenerDevices;
import br.uefs.larsid.iot.soft.services.NodeTypeService;
import br.uefs.larsid.iot.soft.tasks.CheckDevicesTask;
import br.uefs.larsid.iot.soft.tasks.RequestDataTask;
import br.uefs.larsid.iot.soft.utils.MQTTClient;
import br.ufba.dcc.wiser.soft_iot.entities.Device;
import br.ufba.dcc.wiser.soft_iot.entities.Sensor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public class NodeType implements NodeTypeService {

  private MQTTClient MQTTClient;
  private int nodeType;
  private float honestyRate;
  private Conduct node;
  private int checkDeviceTaskTime;
  private int requestDataTaskTime;
  private List<Device> devices;
  private int amountDevices = 0;
  private IDevicePropertiesManager deviceManager;
  private ListenerDevices listenerDevices;
  private ReentrantLock mutex = new ReentrantLock();
  private static final Logger logger = Logger.getLogger(
    NodeType.class.getName()
  );

  public NodeType() {}

  /**
   * Executa o que foi definido na função quando o bundle for inicializado.
   */
  public void start() {
    // TODO: Adicioanr os demais tipos de nós.
    switch (nodeType) {
      case 1:
        node = new Honest();
        break;
      case 2:
        node = new Malicious(honestyRate);
        break;
      default:
        logger.severe("Error. No node type for this option.");
        this.stop();
        break;
    }

    this.MQTTClient.connect();

    devices = new ArrayList<>();
    listenerDevices = new ListenerDevices(this.MQTTClient);
    new Timer()
      .scheduleAtFixedRate(
        new CheckDevicesTask(this),
        0,
        checkDeviceTaskTime * 1000
      );
    new Timer()
      .scheduleAtFixedRate(
        new RequestDataTask(this),
        0,
        requestDataTaskTime * 1000
      );

    // TODO: Remover depois, apenas para testes.
    this.node.evaluateDevice();
  }

  /**
   * Executa o que foi definido na função quando o bundle for finalizado.
   */
  public void stop() {
    this.devices.forEach(d -> this.listenerDevices.unsubscribe(d.getId()));

    this.MQTTClient.disconnect();
  }

  /**
   * Atualiza a lista de dispositivos conectados.
   *
   * @throws IOException
   */
  public void updateDeviceList() throws IOException {
    try {
      this.mutex.lock();
      this.devices.clear();
      this.devices.addAll(deviceManager.getAllDevices());

      if (this.amountDevices < this.devices.size()) {
        this.subscribeToDevicesTopics(this.devices.size() - this.amountDevices);
        this.amountDevices = this.devices.size();
      }
    } finally {
      this.mutex.unlock();
    }
  }

  /**
   * Requisita dados de um dos sensores de um dispositivo aleatório que está
   * conectado ao nó.
   */
  public void requestDataFromRandomDevice() {
    if (this.amountDevices > 0) {
      try {
        this.mutex.lock();

        int randomIndex = new Random().nextInt(this.amountDevices);
        String deviceId = this.devices.get(randomIndex).getId();
        String topic = String.format("dev/%s", deviceId);

        List<Sensor> sensors = this.devices.get(randomIndex).getSensors();
        randomIndex = new Random().nextInt(sensors.size());

        byte[] payload = String
          .format("GET VALUE %s", sensors.get(randomIndex).getId())
          .getBytes();

        this.MQTTClient.publish(topic, payload, 1);
      } finally {
        this.mutex.unlock();
      }
    } else {
      logger.warning("There are no devices connected to request data.");
    }
  }

  /**
   * Se inscreve nos tópicos dos novos dispositivos.
   *
   * @param amountNewDevices int - Número de novos dispositivos que se
   * conectaram ao nó.
   */
  private void subscribeToDevicesTopics(int amountNewDevices) {
    int offset = 1;

    for (int i = 0; i < amountNewDevices; i++) {
      String deviceId = this.devices.get(this.devices.size() - offset).getId();
      listenerDevices.subscribe(deviceId);

      offset++;
    }
  }

  public MQTTClient getMQTTClient() {
    return MQTTClient;
  }

  public void setMQTTClient(MQTTClient mQTTClient) {
    MQTTClient = mQTTClient;
  }

  public int getNodeType() {
    return nodeType;
  }

  public void setNodeType(int nodeType) {
    this.nodeType = nodeType;
  }

  public float getHonestyRate() {
    return honestyRate;
  }

  public void setHonestyRate(float honestyRate) {
    this.honestyRate = honestyRate;
  }

  public Conduct getNode() {
    return node;
  }

  public void setNode(Conduct node) {
    this.node = node;
  }

  public IDevicePropertiesManager getDeviceManager() {
    return deviceManager;
  }

  public void setDeviceManager(IDevicePropertiesManager deviceManager) {
    this.deviceManager = deviceManager;
  }

  public int getCheckDeviceTaskTime() {
    return checkDeviceTaskTime;
  }

  public void setCheckDeviceTaskTime(int checkDeviceTaskTime) {
    this.checkDeviceTaskTime = checkDeviceTaskTime;
  }

  public int getRequestDataTaskTime() {
    return requestDataTaskTime;
  }

  public void setRequestDataTaskTime(int requestDataTaskTime) {
    this.requestDataTaskTime = requestDataTaskTime;
  }

  public int getAmountDevices() {
    return amountDevices;
  }
}
