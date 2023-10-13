package node.type.models;

import br.uefs.larsid.extended.mapping.devices.services.IDevicePropertiesManager;
import br.ufba.dcc.wiser.soft_iot.entities.Device;
import br.ufba.dcc.wiser.soft_iot.entities.Sensor;
import dlt.id.manager.services.IIDManagerService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import node.type.models.conducts.Conduct;
import node.type.models.conducts.Honest;
import node.type.models.conducts.Malicious;
import node.type.models.conducts.Selfish;
import node.type.models.tangle.LedgerConnector;
import node.type.mqtt.ListenerDevices;
import node.type.services.NodeTypeService;
import node.type.tasks.CheckDevicesTask;
import node.type.tasks.RequestDataTask;
import node.type.tasks.WaitDeviceResponseTask;
import node.type.utils.MQTTClient;

public class NodeType implements NodeTypeService {

  private MQTTClient MQTTClient;
  private int nodeType;
  private float honestyRate;
  private Conduct node;
  private int checkDeviceTaskTime;
  private int requestDataTaskTime;
  private int waitDeviceResponseTaskTime;
  private List<Device> devices;
  private int amountDevices = 0;
  private IDevicePropertiesManager deviceManager;
  private ListenerDevices listenerDevices;
  private TimerTask waitDeviceResponseTask;
  private ReentrantLock mutex = new ReentrantLock();
  private LedgerConnector ledgerConnector;
  private IIDManagerService idManager;
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
        node = new Honest(this.ledgerConnector, this.idManager.getID());
        break;
      case 2:
        node =
          new Malicious(
            this.ledgerConnector,
            this.idManager.getID(),
            this.honestyRate
          );
        logger.info(
          "Malicious node behavior: " + node.getConductType().toString()
        );
        break;
      case 3:
        node = new Selfish(ledgerConnector, this.idManager.getID());
        break;
      default:
        logger.severe("Error. No node type for this option.");
        this.stop();
        break;
    }

    this.MQTTClient.connect();

    this.devices = new ArrayList<>();
    this.listenerDevices = new ListenerDevices(this.MQTTClient, this);
    new Timer()
      .scheduleAtFixedRate(
        new CheckDevicesTask(this),
        0,
        this.checkDeviceTaskTime * 1000
      );
    new Timer()
      .scheduleAtFixedRate(
        new RequestDataTask(this),
        0,
        this.requestDataTaskTime * 1000
      );
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
        this.waitDeviceResponseTask =
          new WaitDeviceResponseTask(
            deviceId,
            (this.waitDeviceResponseTaskTime * 1000),
            this
          );

        new Timer().scheduleAtFixedRate(this.waitDeviceResponseTask, 0, 1000);
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
      this.listenerDevices.subscribe(deviceId);

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

  public TimerTask getWaitDeviceResponseTask() {
    return waitDeviceResponseTask;
  }

  public void setWaitDeviceResponseTask(TimerTask waitDeviceResponseTask) {
    this.waitDeviceResponseTask = waitDeviceResponseTask;
  }

  public int getWaitDeviceResponseTaskTime() {
    return waitDeviceResponseTaskTime;
  }

  public void setWaitDeviceResponseTaskTime(int waitDeviceResponseTaskTime) {
    this.waitDeviceResponseTaskTime = waitDeviceResponseTaskTime;
  }

  public LedgerConnector getLedgerConnector() {
    return ledgerConnector;
  }

  public void setLedgerConnector(LedgerConnector ledgerConnector) {
    this.ledgerConnector = ledgerConnector;
  }

  public IIDManagerService getIdManager() {
    return idManager;
  }

  public void setIdManager(IIDManagerService idManager) {
    this.idManager = idManager;
  }
}
