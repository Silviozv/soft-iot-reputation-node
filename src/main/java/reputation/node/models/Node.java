package reputation.node.models;

import br.uefs.larsid.extended.mapping.devices.services.IDevicePropertiesManager;
import br.ufba.dcc.wiser.soft_iot.entities.Device;
import br.ufba.dcc.wiser.soft_iot.entities.Sensor;
import com.google.gson.JsonObject;
import dlt.client.tangle.hornet.enums.TransactionType;
import dlt.client.tangle.hornet.model.DeviceSensorId;
import dlt.client.tangle.hornet.model.transactions.IndexTransaction;
import dlt.client.tangle.hornet.model.transactions.TargetedTransaction;
import dlt.client.tangle.hornet.model.transactions.Transaction;
import dlt.client.tangle.hornet.model.transactions.reputation.HasReputationService;
import dlt.client.tangle.hornet.model.transactions.reputation.ReputationService;
import dlt.client.tangle.hornet.services.ILedgerSubscriber;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
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
import reputation.node.tasks.RequestDataTask;
import reputation.node.tasks.WaitDeviceResponseTask;
import reputation.node.utils.JsonStringToJsonObject;
import reputation.node.utils.MQTTClient;

/**
 *
 * @author Allan Capistrano
 * @version 1.2.0 // TODO: Alterar quando finalizar o fluxo.
 */
public class Node implements NodeTypeService, ILedgerSubscriber {

  private MQTTClient MQTTClient;
  private INodeType nodeType;
  private int checkDeviceTaskTime;
  private int requestDataTaskTime;
  private int waitDeviceResponseTaskTime;
  private int checkNodesServicesTaskTime;
  private int waitNodesResponsesTaskTime;
  private List<Device> devices;
  private List<Transaction> nodesWithServices;
  private LedgerConnector ledgerConnector;
  private int amountDevices = 0;
  private IDevicePropertiesManager deviceManager;
  private ListenerDevices listenerDevices;
  private TimerTask waitDeviceResponseTask;
  private ReentrantLock mutex = new ReentrantLock();
  private ReentrantLock mutexNodesServices = new ReentrantLock();
  private String lastNodeServiceTransactionType = null;
  private boolean isRequestingNodeServices = false;
  private boolean canReceiveNodesResponse = false;
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
        new CheckNodesServicesTask(this),
        0,
        this.checkNodesServicesTaskTime * 1000
      );

    this.subscribeToTransactionsTopics();
  }

  /**
   * Executa o que foi definido na função quando o bundle for finalizado.
   */
  public void stop() {
    this.devices.forEach(d -> this.listenerDevices.unsubscribe(d.getId()));

    this.unsubscribeToTransactionsTopics();

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
   * Publica na blockchain quais são os serviços prestados pelo nó.
   *
   * @throws InterruptedException
   */
  private void publishNodeServices(String serviceType, String target) {
    /* Só responde se tiver pelo menos um dispositivo conectado ao nó. */
    if (this.amountDevices > 0) {
      logger.info("Requested service type: " + serviceType);

      Transaction transaction = null;
      TransactionType transactionType;
      String transactionTypeInString = null;

      List<DeviceSensorId> deviceSensorIdList = new ArrayList<>();

      try {
        this.mutex.lock();
        for (Device d : this.devices) {
          d
            .getSensors()
            .stream()
            .filter(s -> s.getType().equals(serviceType))
            .forEach(s ->
              deviceSensorIdList.add(new DeviceSensorId(d.getId(), s.getId()))
            );
        }
      } finally {
        this.mutex.unlock();
      }

      if (
        serviceType.equals(NodeServiceType.HUMIDITY_SENSOR.getDescription())
      ) {
        transactionType = TransactionType.REP_SVC_HUMIDITY_SENSOR;

        transaction =
          new ReputationService(
            this.nodeType.getNodeId(),
            this.nodeType.getNodeIp(),
            target,
            deviceSensorIdList,
            this.nodeType.getNodeGroup(),
            transactionType
          );

        transactionTypeInString = transactionType.name();
      } else if (
        serviceType.equals(NodeServiceType.PULSE_OXYMETER.getDescription())
      ) {
        transactionType = TransactionType.REP_SVC_PULSE_OXYMETER;

        transaction =
          new ReputationService(
            this.nodeType.getNodeId(),
            this.nodeType.getNodeIp(),
            target,
            deviceSensorIdList,
            this.nodeType.getNodeGroup(),
            transactionType
          );

        transactionTypeInString = transactionType.name();
      } else if (
        serviceType.equals(NodeServiceType.THERMOMETER.getDescription())
      ) {
        transactionType = TransactionType.REP_SVC_THERMOMETER;

        transaction =
          new ReputationService(
            this.nodeType.getNodeId(),
            this.nodeType.getNodeIp(),
            target,
            deviceSensorIdList,
            this.nodeType.getNodeGroup(),
            transactionType
          );

        transactionTypeInString = transactionType.name();
      } else if (
        serviceType.equals(
          NodeServiceType.WIND_DIRECTION_SENSOR.getDescription()
        )
      ) {
        transactionType = TransactionType.REP_SVC_WIND_DIRECTION_SENSOR;

        transaction =
          new ReputationService(
            this.nodeType.getNodeId(),
            this.nodeType.getNodeIp(),
            target,
            deviceSensorIdList,
            this.nodeType.getNodeGroup(),
            transactionType
          );

        transactionTypeInString = transactionType.name();
      } else {
        logger.severe("Unknown service type.");
      }

      /*
       * Enviando a transação para a blockchain.
       */
      if (transaction != null && transactionTypeInString != null) {
        try {
          this.ledgerConnector.put(
              new IndexTransaction(transactionTypeInString, transaction)
            );
        } catch (InterruptedException ie) {
          logger.warning(
            "Error trying to create a " +
            transactionTypeInString +
            " transaction."
          );
          logger.warning(ie.getStackTrace().toString());
        }
      }
    }
  }

  /**
   * Verifica a reputação dos nós que responderam a requisição de serviço.
   */
  public void checkNodesReputation() {
    List<NodeReputation> nodesReputations = new ArrayList<>();
    Double reputation;
    Double highestReputation = 0.0;
    String highestReputationNodeId = null;

    if (this.nodesWithServices.isEmpty()) {
      this.setRequestingNodeServices(false);
      this.setLastNodeServiceTransactionType(null);
    } else {
      /**
       * Salvando a reputação de cada nó em uma lista.
       */
      for (Transaction nodeWithService : this.nodesWithServices) {
        String nodeId = nodeWithService.getSource();

        List<Transaction> evaluationTransactions =
          this.ledgerConnector.getLedgerReader().getTransactionsByIndex(nodeId);

        if (evaluationTransactions.isEmpty()) {
          reputation = 0.5;
        } else {
          reputation = 0.5; // TODO: Implementar o cáculo da reputação e modificar essa variável.
        }

        nodesReputations.add(new NodeReputation(nodeId, reputation));

        if (reputation > highestReputation) {
          highestReputation = reputation;
        }
      }

      final Double innerHighestReputation = Double.valueOf(highestReputation);

      /**
       * Verificando quais nós possuem a maior reputação.
       */
      List<NodeReputation> temp = nodesReputations
        .stream()
        .filter(nr -> nr.getReputation().equals(innerHighestReputation))
        .collect(Collectors.toList());

      /**
       * Obtendo o ID de um dos nós com a maior reputação.
       */
      if (temp.size() == 0) {
        highestReputationNodeId = temp.get(0).getNodeId();
      } else if (temp.size() > 0) {
        int randomIndex = new Random().nextInt(temp.size());

        highestReputationNodeId = temp.get(randomIndex).getNodeId();
      } else {
        logger.severe("Invalid amount of nodes with the highest reputation.");
      }

      if (highestReputationNodeId != null) {
        final String innerHighestReputationNodeId = String.valueOf(
          highestReputationNodeId
        );

        /**
         * Pegando a transação do nó com a maior reputação.
         */
        ReputationService nodeWithService = (ReputationService) this.nodesWithServices.stream()
          .filter(nws -> nws.getSource().equals(innerHighestReputationNodeId))
          .collect(Collectors.toList())
          .get(0);

        // TODO: Calcular a reputação de todos os dispositivos, e enviar as informações do dispositivo com a maior reputação
        this.requestNodeService(
            nodeWithService.getSource(),
            nodeWithService.getSourceIp(),
            nodeWithService.getServices().get(0).getDeviceId(),
            nodeWithService.getServices().get(0).getSensorId()
          );
      } else {
        this.setRequestingNodeServices(false);
        this.setLastNodeServiceTransactionType(null);
      }
    }
  }

  /**
   * Habilita a página dos dispositivos faz uma requisição para a mesma.
   *
   * @param nodeIp String - IP do nó em que o dispositivo está conectado.
   * @param deviceId String - ID do dispositivo.
   * @param sensorId String - ID do sensor.
   */
  private void enableDevicesPage(
    String nodeIp,
    String deviceId,
    String sensorId
  ) {
    try {
      URL url = new URL(
        String.format(
          "http://%s:8181/cxf/iot-service/devices/%s/%s",
          nodeIp,
          deviceId,
          sensorId
        )
      );

      HttpURLConnection conn = (HttpURLConnection) url.openConnection();

      if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
        conn.disconnect();

        return;
      }

      conn.disconnect();
    } catch (MalformedURLException mue) {
      logger.severe(mue.getMessage());
    } catch (IOException ioe) {
      logger.severe(ioe.getMessage());
    }
  }

  /**
   * Requisita e avalia o serviço de um determinado nó.
   *
   * @param nodeId - ID do nó que irá requisitar o serviço.
   * @param nodeIp - IP do nó que irá requisitar o serviço.
   * @param deviceId - ID do dispositivo que fornecerá o serviço.
   * @param sensorId - ID do sensor que fornecerá o serviço.
   */
  private void requestNodeService(
    String nodeId,
    String nodeIp,
    String deviceId,
    String sensorId
  ) {
    boolean isNullable = false;
    String response = null;
    String sensorValue = null;
    int evaluationValue = 0;

    this.enableDevicesPage(nodeIp, deviceId, sensorId);

    try {
      URL url = new URL(
        String.format(
          "http://%s:8181/cxf/iot-service/devices/%s/%s",
          nodeIp,
          deviceId,
          sensorId
        )
      );
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();

      if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
        logger.severe("HTTP error code : " + conn.getResponseCode());

        conn.disconnect();

        return;
      }

      BufferedReader br = new BufferedReader(
        new InputStreamReader((conn.getInputStream()))
      );

      String temp = null;

      while ((temp = br.readLine()) != null) {
        if (temp.equals("null")) {
          isNullable = true;

          break;
        }

        response = temp;
      }

      conn.disconnect();

      JsonObject jsonObject = JsonStringToJsonObject.convert(response);

      sensorValue = jsonObject.get("value").getAsString();
    } catch (MalformedURLException mue) {
      logger.severe(mue.getMessage());
    } catch (IOException ioe) {
      logger.severe(ioe.getMessage());
    }

    if (!isNullable && sensorValue != null) { // Prestou o serviço.
      evaluationValue = 1;
    }

    /**
     * Avaliando o serviço prestado pelo nó.
     */
    try {
      this.nodeType.getNode().evaluateServiceProvider(nodeId, evaluationValue);
    } catch (InterruptedException ie) {
      logger.severe(ie.getStackTrace().toString());
    }

    this.setRequestingNodeServices(false);
    this.setLastNodeServiceTransactionType(null);
  }

  /**
   * Se inscreve nos tópicos ZMQ das transações .
   */
  private void subscribeToTransactionsTopics() {
    this.ledgerConnector.subscribe(TransactionType.REP_HAS_SVC.name(), this);
    this.ledgerConnector.subscribe(
        TransactionType.REP_SVC_HUMIDITY_SENSOR.name(),
        this
      );
    this.ledgerConnector.subscribe(
        TransactionType.REP_SVC_PULSE_OXYMETER.name(),
        this
      );
    this.ledgerConnector.subscribe(
        TransactionType.REP_SVC_THERMOMETER.name(),
        this
      );
    this.ledgerConnector.subscribe(
        TransactionType.REP_SVC_WIND_DIRECTION_SENSOR.name(),
        this
      );
  }

  /**
   * Se desinscreve dos tópicos ZMQ das transações .
   */
  private void unsubscribeToTransactionsTopics() {
    this.ledgerConnector.unsubscribe(TransactionType.REP_HAS_SVC.name(), this);
    this.ledgerConnector.unsubscribe(
        TransactionType.REP_SVC_HUMIDITY_SENSOR.name(),
        this
      );
    this.ledgerConnector.unsubscribe(
        TransactionType.REP_SVC_PULSE_OXYMETER.name(),
        this
      );
    this.ledgerConnector.unsubscribe(
        TransactionType.REP_SVC_THERMOMETER.name(),
        this
      );
    this.ledgerConnector.unsubscribe(
        TransactionType.REP_SVC_WIND_DIRECTION_SENSOR.name(),
        this
      );
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

  @Override
  public void update(Object object, Object object2) {
    /**
     * Somente caso a transação não tenha sido enviada pelo próprio nó.
     */
    if (
      !((Transaction) object).getSource().equals(this.getNodeType().getNodeId())
    ) {
      if (((Transaction) object).getType() == TransactionType.REP_HAS_SVC) {
        HasReputationService receivedTransaction = (HasReputationService) object;

        this.publishNodeServices(
            receivedTransaction.getService(),
            receivedTransaction.getSource()
          );
      } else {
        TargetedTransaction receivedTransaction = (TargetedTransaction) object;

        /**
         * Somente se o destino da transação for este nó.
         */
        if (
          receivedTransaction.getTarget().equals(this.nodeType.getNodeId()) &&
          this.isRequestingNodeServices &&
          this.canReceiveNodesResponse
        ) {
          TransactionType expectedTransactionType = null;

          /**
           * Verificando se a transação de serviço que recebeu é do tipo que
           * requisitou.
           */
          if (
            this.lastNodeServiceTransactionType.equals(
                NodeServiceType.HUMIDITY_SENSOR.getDescription()
              )
          ) {
            expectedTransactionType = TransactionType.REP_SVC_HUMIDITY_SENSOR;
          } else if (
            this.lastNodeServiceTransactionType.equals(
                NodeServiceType.PULSE_OXYMETER.getDescription()
              )
          ) {
            expectedTransactionType = TransactionType.REP_SVC_PULSE_OXYMETER;
          } else if (
            this.lastNodeServiceTransactionType.equals(
                NodeServiceType.THERMOMETER.getDescription()
              )
          ) {
            expectedTransactionType = TransactionType.REP_SVC_THERMOMETER;
          } else if (
            this.lastNodeServiceTransactionType.equals(
                NodeServiceType.WIND_DIRECTION_SENSOR.getDescription()
              )
          ) {
            expectedTransactionType =
              TransactionType.REP_SVC_WIND_DIRECTION_SENSOR;
          }

          /**
           * Adicionando na lista as transações referente aos serviços prestados
           * pelos outros nós.
           */
          if (receivedTransaction.getType() == expectedTransactionType) {
            try {
              this.mutexNodesServices.lock();
              this.nodesWithServices.clear();
              this.nodesWithServices.add(receivedTransaction);
            } finally {
              this.mutexNodesServices.unlock();
            }
          }
        }
      }
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

  public String getLastNodeServiceTransactionType() {
    return lastNodeServiceTransactionType;
  }

  public void setLastNodeServiceTransactionType(
    String lastNodeServiceTransactionType
  ) {
    this.lastNodeServiceTransactionType = lastNodeServiceTransactionType;
  }

  public boolean isRequestingNodeServices() {
    return isRequestingNodeServices;
  }

  public void setRequestingNodeServices(boolean isRequestingNodeServices) {
    this.isRequestingNodeServices = isRequestingNodeServices;
  }

  public boolean isCanReceiveNodesResponse() {
    return canReceiveNodesResponse;
  }

  public void setCanReceiveNodesResponse(boolean canReceiveNodesResponse) {
    this.canReceiveNodesResponse = canReceiveNodesResponse;
  }

  public int getWaitNodesResponsesTaskTime() {
    return waitNodesResponsesTaskTime;
  }

  public void setWaitNodesResponsesTaskTime(int waitNodesResponsesTaskTime) {
    this.waitNodesResponsesTaskTime = waitNodesResponsesTaskTime;
  }
}
