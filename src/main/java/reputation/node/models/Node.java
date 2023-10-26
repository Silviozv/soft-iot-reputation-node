package reputation.node.models;

import br.uefs.larsid.extended.mapping.devices.services.IDevicePropertiesManager;
import br.ufba.dcc.wiser.soft_iot.entities.Device;
import br.ufba.dcc.wiser.soft_iot.entities.Sensor;
import dlt.client.tangle.hornet.enums.TransactionType;
import dlt.client.tangle.hornet.model.DeviceSensorId;
import dlt.client.tangle.hornet.model.transactions.IndexTransaction;
import dlt.client.tangle.hornet.model.transactions.Transaction;
import dlt.client.tangle.hornet.model.transactions.reputation.ReputationService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import node.type.services.INodeType;
import reputation.node.enums.NodeServiceType;
import reputation.node.mqtt.ListenerDevices;
import reputation.node.services.NodeTypeService;
import reputation.node.tangle.LedgerConnector;
import reputation.node.tasks.CheckDevicesTask;
import reputation.node.tasks.RequestDataTask;
import reputation.node.tasks.WaitDeviceResponseTask;
import reputation.node.utils.MQTTClient;

/**
 *
 * @author Allan Capistrano
 * @version 1.2.0
 */
public class Node implements NodeTypeService {

  private MQTTClient MQTTClient;
  private INodeType nodeType;
  private int checkDeviceTaskTime;
  private int requestDataTaskTime;
  private int requestServiceTaskTime; // TODO: Verificar se ainda vai precisar
  private int waitDeviceResponseTaskTime;
  private int waitNodeResponseTaskTime; // TODO: Verificar se ainda vai precisar
  private List<Device> devices;
  private LedgerConnector ledgerConnector;
  private int amountDevices = 0;
  private IDevicePropertiesManager deviceManager;
  private ListenerDevices listenerDevices;
  private TimerTask waitDeviceResponseTask;
  private TimerTask waitNodeResponseTask; // TODO: Verificar se ainda vai precisar
  private ReentrantLock mutex = new ReentrantLock();
  private boolean requestingService; // TODO: Verificar se ainda vai precisar
  private static final Logger logger = Logger.getLogger(Node.class.getName());

  public Node() {}

  /**
   * Executa o que foi definido na função quando o bundle for inicializado.
   */
  public void start() {
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

    this.requestingService = false;
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

    // TODO: Remover
    if (this.amountDevices > 0) {
      try {
        this.publishNodeServices();
      } catch (InterruptedException ie) {
        logger.warning("Could not sent the transaction.");
        logger.warning(ie.getStackTrace().toString());
      }
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

  // TODO: Está escrevendo errado o deviceSensorIdList
  public void publishNodeServices() throws InterruptedException {
    if (this.amountDevices > 0) {
      List<DeviceSensorId> deviceSensorIdList = new ArrayList<>();
      Transaction transaction = null;
      String transactionType = null;

      try {
        this.mutex.lock();

        for (NodeServiceType serviceType : NodeServiceType.values()) {
          deviceSensorIdList.clear();
          logger.info(serviceType.getDescription()); // TODO: Remover

          for (Device d : this.devices) {
            d
              .getSensors()
              .stream()
              .filter(s -> s.getType().equals(serviceType.getDescription()))
              .forEach(s ->
                deviceSensorIdList.add(new DeviceSensorId(d.getId(), s.getId()))
              );
          }

          deviceSensorIdList.forEach(d -> logger.info(d.getSensorId()));

          switch (serviceType) {
            case HUMIDITY_SENSOR:
              logger.info("1"); // TODO: Remover

              transaction =
                new ReputationService(
                  this.nodeType.getNodeId(),
                  this.nodeType.getNodeGroup(),
                  deviceSensorIdList,
                  TransactionType.REP_SVC_HUMIDITY_SENSOR
                );

              logger.info(
                ((ReputationService) transaction).getServices()
                  .get(0)
                  .getSensorId()
              ); // TODO: Remover

              transactionType = TransactionType.REP_SVC_HUMIDITY_SENSOR.name();

              if (transaction != null && transactionType != null) {
                this.ledgerConnector.put(
                    new IndexTransaction(transactionType, transaction)
                  );
              }

              break;
            case PULSE_OXYMETER:
              logger.info("2"); // TODO: Remover

              transaction =
                new ReputationService(
                  this.nodeType.getNodeId(),
                  this.nodeType.getNodeGroup(),
                  deviceSensorIdList,
                  TransactionType.REP_SVC_PULSE_OXYMETER
                );

              logger.info(
                ((ReputationService) transaction).getServices()
                  .get(0)
                  .getSensorId()
              ); // TODO: Remover

              transactionType = TransactionType.REP_SVC_PULSE_OXYMETER.name();

              if (transaction != null && transactionType != null) {
                this.ledgerConnector.put(
                    new IndexTransaction(transactionType, transaction)
                  );
              }

              break;
            case THERMOMETER:
              logger.info("3"); // TODO: Remover

              transaction =
                new ReputationService(
                  this.nodeType.getNodeId(),
                  this.nodeType.getNodeGroup(),
                  deviceSensorIdList,
                  TransactionType.REP_SVC_THERMOMETER
                );

              logger.info(
                ((ReputationService) transaction).getServices()
                  .get(0)
                  .getSensorId()
              ); // TODO: Remover

              transactionType = TransactionType.REP_SVC_THERMOMETER.name();

              if (transaction != null && transactionType != null) {
                this.ledgerConnector.put(
                    new IndexTransaction(transactionType, transaction)
                  );
              }

              break;
            case WIND_DIRECTION_SENSOR:
              logger.info("4"); // TODO: Remover

              transaction =
                new ReputationService(
                  this.nodeType.getNodeId(),
                  this.nodeType.getNodeGroup(),
                  deviceSensorIdList,
                  TransactionType.REP_SVC_WIND_DIRECTION_SENSOR
                );

              logger.info(
                ((ReputationService) transaction).getServices()
                  .get(0)
                  .getSensorId()
              ); // TODO: Remover

              transactionType =
                TransactionType.REP_SVC_WIND_DIRECTION_SENSOR.name();

              if (transaction != null && transactionType != null) {
                this.ledgerConnector.put(
                    new IndexTransaction(transactionType, transaction)
                  );
              }

              break;
            default:
              logger.severe("Unknown service type.");
              break;
          }

          // if (transaction != null && transactionType != null) {
          //   this.ledgerConnector.put(
          //       new IndexTransaction(transactionType, transaction)
          //     );
          // }

          logger.info("-----"); // TODO: Remover
        }
      } finally {
        this.mutex.unlock();
      }
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

  public INodeType getNodeType() {
    return nodeType;
  }

  public void setNodeType(INodeType nodeType) {
    this.nodeType = nodeType;
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

  public int getRequestServiceTaskTime() {
    return requestServiceTaskTime;
  }

  public void setRequestServiceTaskTime(int requestServiceTaskTime) {
    this.requestServiceTaskTime = requestServiceTaskTime;
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

  public int getWaitNodeResponseTaskTime() {
    return waitNodeResponseTaskTime;
  }

  public void setWaitNodeResponseTaskTime(int waitNodeResponseTaskTime) {
    this.waitNodeResponseTaskTime = waitNodeResponseTaskTime;
  }

  public boolean isRequestingService() {
    return requestingService;
  }

  public void setRequestingService(boolean requestingService) {
    this.requestingService = requestingService;
  }

  public TimerTask getWaitNodeResponseTask() {
    return waitNodeResponseTask;
  }

  public void setWaitNodeResponseTask(TimerTask waitNodeResponseTask) {
    this.waitNodeResponseTask = waitNodeResponseTask;
  }
}
