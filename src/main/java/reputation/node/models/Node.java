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
import dlt.client.tangle.hornet.model.transactions.reputation.Credibility;
import dlt.client.tangle.hornet.model.transactions.reputation.Evaluation;
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
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import node.type.services.INodeType;
import python.to.java.services.IKMeans;
import reputation.node.enums.NodeServiceType;
import reputation.node.mqtt.ListenerDevices;
import reputation.node.reputation.IReputationCalc;
import reputation.node.reputation.ReputationCalc;
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
 * @version 1.3.0
 */
public class Node implements NodeTypeService, ILedgerSubscriber {

  private MQTTClient MQTTClient;
  private INodeType nodeType;
  private IKMeans kMeans;
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

    this.createTasks();
    this.subscribeToTransactionsTopics();

    // TODO: Temporário
    logger.info("AAAAA"); // TODO: Remover
    String targetId = "e0635c77-effe-4d57-9ea6-7d5c4e63ff28";
    int test =
      this.getLastEvaluation(
          this.ledgerConnector.getLedgerReader()
            .getTransactionsByIndex(targetId, false),
          this.nodeType.getNodeId(),
          targetId
        );
    logger.info(String.valueOf(test)); // TODO: Remover
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
      TransactionType transactionType = null;
      String transactionTypeInString = null;
      List<Device> tempDevices = new ArrayList<>();
      List<DeviceSensorId> deviceSensorIdList = new ArrayList<>();

      try {
        this.mutex.lock();
        /* Cópia temporária para liberar a lista de dispositivos. */
        tempDevices.addAll(this.devices);
      } finally {
        this.mutex.unlock();
      }

      for (Device d : tempDevices) {
        d
          .getSensors()
          .stream()
          .filter(s -> s.getType().equals(serviceType))
          .forEach(s ->
            deviceSensorIdList.add(new DeviceSensorId(d.getId(), s.getId()))
          );
      }

      if (
        serviceType.equals(NodeServiceType.HUMIDITY_SENSOR.getDescription())
      ) {
        transactionType = TransactionType.REP_SVC_HUMIDITY_SENSOR;
      } else if (
        serviceType.equals(NodeServiceType.PULSE_OXYMETER.getDescription())
      ) {
        transactionType = TransactionType.REP_SVC_PULSE_OXYMETER;
      } else if (
        serviceType.equals(NodeServiceType.THERMOMETER.getDescription())
      ) {
        transactionType = TransactionType.REP_SVC_THERMOMETER;
      } else if (
        serviceType.equals(
          NodeServiceType.WIND_DIRECTION_SENSOR.getDescription()
        )
      ) {
        transactionType = TransactionType.REP_SVC_WIND_DIRECTION_SENSOR;
      } else {
        logger.severe("Unknown service type.");
      }

      if (transactionType != null) {
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
   * Faz uso do serviço do nó com a maior reputação dentre aqueles que
   * responderam a requisição, e ao final avalia-o.
   */
  public void useNodeService() {
    if (this.nodesWithServices.isEmpty()) {
      this.setRequestingNodeServices(false);
      this.setLastNodeServiceTransactionType(null);
    } else {
      String highestReputationNodeId = this.getNodeIdWithHighestReputation();

      if (highestReputationNodeId != null) {
        final String innerHighestReputationNodeId = String.valueOf(
          highestReputationNodeId
        );

        /**
         * Obtendo a transação do nó com a maior reputação.
         */
        ReputationService nodeWithService = (ReputationService) this.nodesWithServices.stream()
          .filter(nws -> nws.getSource().equals(innerHighestReputationNodeId))
          .collect(Collectors.toList())
          .get(0);

        DeviceSensorId deviceSensorId =
          this.getDeviceWithHighestReputation(nodeWithService.getServices());

        this.requestAndEvaluateNodeService(
            nodeWithService.getSource(),
            nodeWithService.getSourceIp(),
            deviceSensorId.getDeviceId(),
            deviceSensorId.getSensorId()
          );
      } else {
        this.setRequestingNodeServices(false);
        this.setLastNodeServiceTransactionType(null);
      }
    }
  }

  /**
   * Obtém o ID do nó com a maior reputação dentre aqueles que reponderam a
   * requisição.
   *
   * @return String
   */
  private String getNodeIdWithHighestReputation() {
    List<ThingReputation> nodesReputations = new ArrayList<>();
    Double reputation;
    Double highestReputation = 0.0;
    String highestReputationNodeId = null;

    /**
     * Salvando a reputação de cada nó em uma lista.
     */
    for (Transaction nodeWithService : this.nodesWithServices) {
      String nodeId = nodeWithService.getSource();

      List<Transaction> evaluationTransactions =
        this.ledgerConnector.getLedgerReader()
          .getTransactionsByIndex(nodeId, false);

      if (evaluationTransactions.isEmpty()) {
        reputation = 0.5;
      } else {
        IReputationCalc reputationCalc = new ReputationCalc();
        reputation = reputationCalc.calc(evaluationTransactions);
      }

      nodesReputations.add(new ThingReputation(nodeId, reputation));

      if (reputation > highestReputation) {
        highestReputation = reputation;
      }
    }

    final Double innerHighestReputation = Double.valueOf(highestReputation);

    /**
     * Verificando quais nós possuem a maior reputação.
     */
    List<ThingReputation> temp = nodesReputations
      .stream()
      .filter(nr -> nr.getReputation().equals(innerHighestReputation))
      .collect(Collectors.toList());

    /**
     * Obtendo o ID de um dos nós com a maior reputação.
     */
    if (temp.size() == 1) {
      highestReputationNodeId = temp.get(0).getThingId();
    } else if (temp.size() > 1) {
      int randomIndex = new Random().nextInt(temp.size());

      highestReputationNodeId = temp.get(randomIndex).getThingId();
    } else {
      logger.severe("Invalid amount of nodes with the highest reputation.");
    }

    return highestReputationNodeId;
  }

  /**
   * Obtém os IDs do dispositivo e do sensor, com a maior reputação.
   *
   * @param deviceSensorIdList List<DeviceSensorId> - Lista com os IDs do
   * dispositivo e sensor que se deseja obter o maior.
   * @return DeviceSensorId
   */
  private DeviceSensorId getDeviceWithHighestReputation(
    List<DeviceSensorId> deviceSensorIdList
  ) {
    List<ThingReputation> devicesReputations = new ArrayList<>();
    Double reputation;
    Double highestReputation = 0.0;
    DeviceSensorId highestReputationDeviceSensorId = null;

    if (deviceSensorIdList.size() > 1) {
      /**
       * Salvando a reputação de cada dispositivo em uma lista.
       */
      for (DeviceSensorId deviceSensorId : deviceSensorIdList) {
        List<Transaction> evaluationTransactions =
          this.ledgerConnector.getLedgerReader()
            .getTransactionsByIndex(deviceSensorId.getDeviceId(), false);

        if (evaluationTransactions.isEmpty()) {
          reputation = 0.5;
        } else {
          IReputationCalc reputationCalc = new ReputationCalc();
          reputation = reputationCalc.calc(evaluationTransactions);
        }

        devicesReputations.add(
          new ThingReputation(
            String.format(
              "%s@%s", // Formato: deviceId@sensorId
              deviceSensorId.getDeviceId(),
              deviceSensorId.getSensorId()
            ),
            reputation
          )
        );

        if (reputation > highestReputation) {
          highestReputation = reputation;
        }
      }

      final Double innerHighestReputation = Double.valueOf(highestReputation);

      /**
       * Verificando quais dispositivos possuem a maior reputação.
       */
      List<ThingReputation> temp = devicesReputations
        .stream()
        .filter(nr -> nr.getReputation().equals(innerHighestReputation))
        .collect(Collectors.toList());

      int index = -1;

      /**
       * Obtendo o ID de um dos dispositivos com a maior reputação.
       */
      if (temp.size() == 1) {
        index = 0;
      } else if (temp.size() > 1) {
        index = new Random().nextInt(temp.size());
      } else {
        logger.severe("Invalid amount of devices with the highest reputation.");
      }

      if (index != -1) {
        String[] tempIds = temp.get(index).getThingId().split("@");

        highestReputationDeviceSensorId =
          new DeviceSensorId(tempIds[0], tempIds[1]);
      }
    } else if (deviceSensorIdList.size() == 1) {
      highestReputationDeviceSensorId = deviceSensorIdList.get(0);
    } else {
      logger.severe("Invalid amount of devices.");
    }

    return highestReputationDeviceSensorId;
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
   * @param nodeId - ID do nó para o qual irá requisitar o serviço.
   * @param nodeIp - IP do nó para o qual irá requisitar o serviço.
   * @param deviceId - ID do dispositivo que fornecerá o serviço.
   * @param sensorId - ID do sensor que fornecerá o serviço.
   */
  private void requestAndEvaluateNodeService(
    String nodeId,
    String nodeIp,
    String deviceId,
    String sensorId
  ) {
    boolean isNullable = false;
    String response = null;
    String sensorValue = null;
    int serviceEvaluation = 0;

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
      serviceEvaluation = 1;
    }

    /* Calculando a credibilidade deste nó */
    this.calculateCredibility(
        this.nodeType.getNodeId(),
        nodeId,
        serviceEvaluation
      );
    // TODO: Usar cálculo da credibilidade no valor da avaliação

    /**
     * Avaliando o serviço prestado pelo nó.
     */
    try {
      // TODO: Alterar para evaluationValue que é calculado com serviceEvaluation e credibility
      this.nodeType.getNode()
        .evaluateServiceProvider(nodeId, serviceEvaluation);
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
   * Cria todas as tasks que serão usadas pelo bundle.
   */
  private void createTasks() {
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

  // TODO: Documentar
  // TODO: Talvez alterar o retorno do método

  /**
   * Calcula a credibilidade do nó avaliador que será utilizada no cálculo da
   * avaliação.
   *
   * @param sourceId String - ID do nó avaliador.
   * @param targetId String - ID do nó que prestou o serviço.
   * @param currentServiceEvaluation int - Nota do serviço atual.
   */
  private void calculateCredibility(
    String sourceId,
    String targetId,
    int currentServiceEvaluation
  ) {
    List<Transaction> serviceProviderEvaluationTransactions =
      this.ledgerConnector.getLedgerReader()
        .getTransactionsByIndex(targetId, false);

    /**
     * Calculando a consistência do nó (C(n)).
     */
    float consistency =
      this.calculateConsistency(
          serviceProviderEvaluationTransactions,
          sourceId,
          targetId,
          currentServiceEvaluation
        );

    logger.info("CONSISTENCY"); // TODO: Remover
    logger.info(String.valueOf(consistency)); // TODO: Remover

    /**
     * Calculando a confiabilidade do nó (Tr(n)).
     */
    float reliability =
      this.calculateReliability(
          serviceProviderEvaluationTransactions,
          currentServiceEvaluation
        );

    logger.info("RELIABILITY"); // TODO: Remover
    logger.info(String.valueOf(reliability)); // TODO: Remover
    // TODO: Publicar a credibilidade do nó na Blockchain
  }

  /**
   * Calcula a consistência do nó avaliador.
   *
   * @param serviceProviderEvaluationTransactions List<Transaction> - Lista com
   * as transações de avaliação que do nó prestador de serviço.
   * @param sourceId String - ID do nó avaliador.
   * @param targetId String - ID do nó que prestou o serviço.
   * @param currentServiceEvaluation int - Nota do serviço atual.
   * @return
   */
  private float calculateConsistency(
    List<Transaction> serviceProviderEvaluationTransactions,
    String sourceId,
    String targetId,
    int currentServiceEvaluation
  ) {
    /* r(t-1) */
    int lastEvaluation =
      this.getLastEvaluation(
          serviceProviderEvaluationTransactions,
          sourceId,
          targetId
        );

    return Math.abs(currentServiceEvaluation - lastEvaluation);
  }

  /**
   * Calcula a confiabilidade do nó avaliador.
   *
   * @param serviceProviderEvaluationTransactions List<Transaction> - Lista com
   * as transações de avaliação que do nó prestador de serviço.
   * @param currentServiceEvaluation int - Nota do serviço atual.
   * @return float
   */
  private float calculateReliability(
    List<Transaction> serviceProviderEvaluationTransactions,
    int currentServiceEvaluation
  ) {
    /* Inicializando o valor de R */
    float R = (float) 0.0;

    /* Obtendo a credibilidade dos nós que já avaliaram o provedor do serviço. */
    List<SourceCredibility> nodesCredibilityWithSource =
      this.getNodesCredibility(serviceProviderEvaluationTransactions);

    if (!nodesCredibilityWithSource.isEmpty()) {
      logger.info("CREDIBILITIES"); // TODO: Remover
      logger.info(nodesCredibilityWithSource.toString()); // TODO: Remover

      /* Obtendo somente o valor da credibilidade dos nós avaliadores. */
      List<Float> nodesCredibility = nodesCredibilityWithSource
        .stream()
        .map(SourceCredibility::getCredibility)
        .collect(Collectors.toList());

      /* Executando o algoritmo KMeans. */
      List<Float> kMeansResult = kMeans.execute(nodesCredibility);

      logger.info("K-MEANS RESULT"); // TODO: Remover
      logger.info(kMeansResult.toString()); // TODO: Remover

      /* Obtendo somente os nós que possuem as credibilidades calculadas pelo algoritmo KMeans. */
      List<SourceCredibility> nodesWithHighestCredibilities = nodesCredibilityWithSource
        .stream()
        .filter(node -> kMeansResult.contains(node.getCredibility()))
        .collect(Collectors.toList());

      logger.info("NODES"); // TODO: Remover
      logger.info(nodesWithHighestCredibilities.toString()); // TODO: Remover

      /* Calculando a média das avaliações dos nós calculadas pelo algoritmo KMeans. */
      OptionalDouble temp = serviceProviderEvaluationTransactions
        .stream()
        .filter(nodeEvaluation ->
          nodesWithHighestCredibilities
            .stream()
            .anyMatch(sourceCredibility ->
              nodeEvaluation.getSource().equals(sourceCredibility.getSource())
            )
        )
        .mapToDouble(nodeEvaluation -> ((Evaluation) nodeEvaluation).getValue())
        .average();

      /* Caso existam transações de avaliação, atualiza o valor de R como a média dessas avaliações. */
      if (temp.isPresent()) {
        R = (float) temp.getAsDouble();
      }

      logger.info("R VALUE"); // TODO: Remover
      logger.info(String.valueOf(R)); // TODO: Remover
    }

    return Math.abs(currentServiceEvaluation - R);
  }

  /**
   * Obtém o valor da avaliação mais recente enviada por um nó para um
   * prestador de serviço. Caso não exista nenhuma avaliação, o retorno é 0.
   *
   * @param serviceProviderEvaluationTransactions List<Transaction> - Lista de
   * transações de avaliação do prestador do serviço
   * @param sourceId String - ID do nó avaliador.
   * @param targetId String - ID do prestador do serviço.
   * @return int
   */
  private int getLastEvaluation( // TODO: Provavelmente o retorno vai ser alterado para 'float' ou 'double'
    List<Transaction> serviceProviderEvaluationTransactions,
    String sourceId,
    String targetId
  ) {
    return Optional
      .ofNullable(serviceProviderEvaluationTransactions)
      .map(transactions ->
        transactions
          .stream()
          .filter(t ->/* Filtrando somente as transações com source e target informadas por parâmetro. */
            t.getSource().equals(sourceId) &&
            ((Evaluation) t).getTarget().equals(targetId)
          )
          .sorted((t1, t2) ->/* Ordenando em ordem decrescente. */
            Long.compare(t2.getCreatedAt(), t1.getCreatedAt())
          )
          .collect(Collectors.toList())
      )
      .filter(list -> !list.isEmpty())
      .map(list -> ((Evaluation) list.get(0)).getValue())
      .orElse(0);/* Caso não exista nenhuma avaliação. */
  }

  /**
   * Obtém e adiciona em uma lista, os valores da credibilidade mais recente dos
   * nós avaliadores cujos IDs foram informados pela lista de transações de
   * avaliações.
   *
   * @param serviceProviderEvaluationTransactions List<Transaction> - Lista de
   * transações de avaliações.
   * @return List<SourceCredibility>
   */
  private List<SourceCredibility> getNodesCredibility(
    List<Transaction> serviceProviderEvaluationTransactions
  ) {
    List<SourceCredibility> nodesCredibility = new ArrayList<>();

    if (
      Optional.ofNullable(serviceProviderEvaluationTransactions).isPresent()
    ) {
      /* Filtrando somente uma avaliação por nó avaliador. */
      List<Transaction> uniqueServiceProviderEvaluationTransactions = serviceProviderEvaluationTransactions
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
        .collect(Collectors.toList());

      for (Transaction transaction : uniqueServiceProviderEvaluationTransactions) {
        /* O index para pegar a credibilidade segue o formato: cred_<id do nó>. */
        String source = transaction.getSource();

        List<Transaction> tempCredibility =
          this.ledgerConnector.getLedgerReader()
            .getTransactionsByIndex("cred_" + source, false);

        float nodeCredibility = Optional
          .ofNullable(tempCredibility)
          .map(transactions ->
            transactions
              .stream()
              .sorted((t1, t2) ->/* Ordenando em ordem decrescente. */
                Long.compare(t2.getCreatedAt(), t1.getCreatedAt())
              )
              .collect(Collectors.toList())
          )
          .filter(list -> !list.isEmpty())
          /* Obtendo a credibilidade mais recente calculada pelo nó */
          .map(list -> ((Credibility) list.get(0)).getValue())
          /* Caso o nó ainda não tenha calculado a sua credibilidade, por padrão é 0.5. */
          .orElse((float) 0.5);

        nodesCredibility.add(new SourceCredibility(source, nodeCredibility));
      }
    }

    return nodesCredibility;
  }

  /**
   * Responsável por lidar com as mensagens recebidas pelo ZMQ (MQTT).
   */
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

  /*------------------------- Getters e Setters -----------------------------*/

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

  public IKMeans getkMeans() {
    return kMeans;
  }

  public void setkMeans(IKMeans kMeans) {
    this.kMeans = kMeans;
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
