package br.uefs.larsid.iot.soft.models;

import br.uefs.larsid.extended.mapping.devices.services.IDevicePropertiesManager;
import br.uefs.larsid.iot.soft.models.conducts.Conduct;
import br.uefs.larsid.iot.soft.models.conducts.Honest;
import br.uefs.larsid.iot.soft.models.conducts.Malicious;
import br.uefs.larsid.iot.soft.services.NodeTypeService;
import br.uefs.larsid.iot.soft.tasks.CheckDevicesTask;
import br.uefs.larsid.iot.soft.utils.MQTTClient;
import br.ufba.dcc.wiser.soft_iot.entities.Device;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.logging.Logger;

public class NodeType implements NodeTypeService {

  private MQTTClient MQTTClient;
  private int nodeType;
  private float honestyRate;
  private Conduct node;
  private int checkDeviceTaskTime;
  private List<Device> devices;
  private int amountDevices;
  private IDevicePropertiesManager deviceManager;
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

    devices = new ArrayList<>();

    this.MQTTClient.connect();
    // Tarefa para atualização da lista de dispositivos em um tempo configurável.
    new Timer()
      .scheduleAtFixedRate(
        new CheckDevicesTask(this),
        0,
        checkDeviceTaskTime * 1000
      );
    // TODO: Se inscrever nos tópicos de respostas dos dispositivos.
    // TODO: Requisitar de tempos em tempos (ter como base o load-balancer) o valor de um tipo (aleatório) de sensor.

    // Apenas para testes.
    this.node.evaluateDevice();
  }

  /**
   * Executa o que foi definido na função quando o bundle for finalizado.
   */
  public void stop() {
    this.MQTTClient.disconnect();
    // TODO: Desinscrever dos tópicos
  }

  /**
   * Atualiza a lista de dispositivos conectados.
   *
   * @throws IOException
   */
  public void updateDeviceList() throws IOException {
    devices.clear();
    devices.addAll(deviceManager.getAllDevices());
    this.amountDevices = devices.size();
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
}
