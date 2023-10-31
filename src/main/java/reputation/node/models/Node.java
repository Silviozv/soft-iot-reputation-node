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
import java.util.stream.Collectors;
import node.type.services.INodeType;
import reputation.node.enums.NodeServiceType;
import reputation.node.mqtt.ListenerDevices;
import reputation.node.services.NodeTypeService;
import reputation.node.tangle.LedgerConnector;
import reputation.node.tasks.CheckDevicesTask;
import reputation.node.tasks.CheckNodesServicesTask;
import reputation.node.tasks.PublishNodeServicesTask;
import reputation.node.tasks.RequestDataTask;
import reputation.node.tasks.WaitDeviceResponseTask;
import reputation.node.utils.MQTTClient;

/**
 *
 * @author Allan Capistrano
 * @version 1.2.0 // TODO: Alterar quando finalizar o fluxo.
 */
public class Node implements NodeTypeService {

  private MQTTClient MQTTClient;
  private INodeType nodeType;
  private int checkDeviceTaskTime;
  private int requestDataTaskTime;
  private int waitDeviceResponseTaskTime;
  private int publishNodeServicesTaskTime;
  private int checkNodesServicesTaskTime;
  private List<Device> devices;
  private List<Transaction> nodesWithServices;
  private LedgerConnector ledgerConnector;
  private int amountDevices = 0;
  private IDevicePropertiesManager deviceManager;
  private ListenerDevices listenerDevices;
  private TimerTask waitDeviceResponseTask;
  private ReentrantLock mutex = new ReentrantLock();
  private ReentrantLock mutexNodesServices = new ReentrantLock();
  private static final Logger logger = Logger.getLogger(Node.class.getName());

  public Node() {}

  /**
   * Executa o que foi definido na função quando o bundle for inicializado.
   */
  public void start() {
    this.MQTTClient.connect();

    this.devices = new ArrayList<>();
    this.nodesWithServices = new ArrayList<>();
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
    new Timer()
      .scheduleAtFixedRate(
        new PublishNodeServicesTask(this),
        0,
        this.publishNodeServicesTaskTime * 1000
      );
    new Timer()
      .scheduleAtFixedRate(
        new CheckNodesServicesTask(this),
        0,
        this.checkNodesServicesTaskTime * 1000
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
   * Publica na blockchain quais são os serviços providos pelo nó.
   *
   * @throws InterruptedException
   */
  public void publishNodeServices() throws InterruptedException {
    if (this.amountDevices > 0) {
      Transaction transaction = null;
      String transactionType = null;

      for (NodeServiceType serviceType : NodeServiceType.values()) {
        List<DeviceSensorId> deviceSensorIdList = new ArrayList<>();

        try {
          this.mutex.lock();
          for (Device d : this.devices) {
            d
              .getSensors()
              .stream()
              .filter(s -> s.getType().equals(serviceType.getDescription()))
              .forEach(s ->
                deviceSensorIdList.add(new DeviceSensorId(d.getId(), s.getId()))
              );
          }
        } finally {
          this.mutex.unlock();
        }

        switch (serviceType) {
          case HUMIDITY_SENSOR:
            transaction =
              new ReputationService(
                this.nodeType.getNodeId(),
                this.nodeType.getNodeGroup(),
                this.nodeType.getNodeIp(),
                deviceSensorIdList,
                TransactionType.REP_SVC_HUMIDITY_SENSOR
              );

            transactionType = TransactionType.REP_SVC_HUMIDITY_SENSOR.name();

            break;
          case PULSE_OXYMETER:
            transaction =
              new ReputationService(
                this.nodeType.getNodeId(),
                this.nodeType.getNodeGroup(),
                this.nodeType.getNodeIp(),
                deviceSensorIdList,
                TransactionType.REP_SVC_PULSE_OXYMETER
              );

            transactionType = TransactionType.REP_SVC_PULSE_OXYMETER.name();

            break;
          case THERMOMETER:
            transaction =
              new ReputationService(
                this.nodeType.getNodeId(),
                this.nodeType.getNodeGroup(),
                this.nodeType.getNodeIp(),
                deviceSensorIdList,
                TransactionType.REP_SVC_THERMOMETER
              );

            transactionType = TransactionType.REP_SVC_THERMOMETER.name();

            break;
          case WIND_DIRECTION_SENSOR:
            transaction =
              new ReputationService(
                this.nodeType.getNodeId(),
                this.nodeType.getNodeGroup(),
                this.nodeType.getNodeIp(),
                deviceSensorIdList,
                TransactionType.REP_SVC_WIND_DIRECTION_SENSOR
              );

            transactionType =
              TransactionType.REP_SVC_WIND_DIRECTION_SENSOR.name();

            break;
          default:
            logger.severe("Unknown service type.");
            break;
        }

        /*
         * Enviando a transação para a blockchain.
         */
        if (transaction != null && transactionType != null) {
          this.ledgerConnector.put(
              new IndexTransaction(transactionType, transaction)
            );
        }
      }
    }
  }

  /**
   * Obtém uma lista com as transações a respeito de um serviço que os demais
   * nós prestam.
   *
   * @param serviceType NodeServiceType - Tipo do serviço.
   */
  public void getNodesServices(NodeServiceType serviceType) {
    try {
      this.mutexNodesServices.lock();

      this.nodesWithServices.clear();

      String index = "";

      switch (serviceType) {
        case HUMIDITY_SENSOR:
          index = TransactionType.REP_SVC_HUMIDITY_SENSOR.name();
          break;
        case PULSE_OXYMETER:
          index = TransactionType.REP_SVC_PULSE_OXYMETER.name();
          break;
        case THERMOMETER:
          index = TransactionType.REP_SVC_THERMOMETER.name();
          break;
        case WIND_DIRECTION_SENSOR:
          index = TransactionType.REP_SVC_WIND_DIRECTION_SENSOR.name();
          break;
        default:
          logger.severe("Unknown service type.");
          break;
      }

      if (!index.equals("")) {
        List<Transaction> transactions =
          this.ledgerConnector.getLedgerReader().getTransactionsByIndex(index);

        /*
         * Ordenação em ordem decrescente com relação a data/hora de criação da
         * transação.
         */
        transactions.sort((t1, t2) ->
          Long.compare(t2.getCreatedAt(), t1.getCreatedAt())
        );

        /**
         * Tempo limite de 1 hora.
         */
        long timeLimit = System.currentTimeMillis() - 1 * 60 * 60 * 1000;

        /**
         * Removendo serviços duplicados de um mesmo nó, e as transações criadas
         * a mais de 1 hora atrás.
         */
        this.nodesWithServices =
          transactions
            .stream()
            .collect(
              Collectors.toMap(
                Transaction::getSource,
                obj -> obj,
                (existing, replacement) -> existing
              )
            )
            .values()
            .stream()
            .filter(obj -> obj.getCreatedAt() >= timeLimit) // TODO: Adicionar filtro para não pegar a transação que o próprio nó enviou
            .collect(Collectors.toList());
      }
    } finally {
      this.mutexNodesServices.unlock();
    }

    logger.info("-----------------"); // TODO: remover

    for (Transaction transaction : this.nodesWithServices) { // TODO: remover
      logger.info(((ReputationService) transaction).getSource());
      logger.info(
        Long.toString(((ReputationService) transaction).getCreatedAt())
      );
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

  public int getPublishNodeServicesTaskTime() {
    return publishNodeServicesTaskTime;
  }

  public void setPublishNodeServicesTaskTime(int publishNodeServicesTaskTime) {
    this.publishNodeServicesTaskTime = publishNodeServicesTaskTime;
  }

  public List<Transaction> getNodesWithServices() {
    return nodesWithServices;
  }

  public void setNodesWithServices(List<Transaction> nodesWithServices) {
    this.nodesWithServices = nodesWithServices;
  }

  public int getCheckNodesServicesTaskTime() {
    return checkNodesServicesTaskTime;
  }

  public void setCheckNodesServicesTaskTime(int checkNodesServicesTaskTime) {
    this.checkNodesServicesTaskTime = checkNodesServicesTaskTime;
  }
}
