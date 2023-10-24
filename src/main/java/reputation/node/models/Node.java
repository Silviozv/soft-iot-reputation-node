package reputation.node.models;

import br.uefs.larsid.extended.mapping.devices.services.IDevicePropertiesManager;
import br.ufba.dcc.wiser.soft_iot.entities.Device;
import br.ufba.dcc.wiser.soft_iot.entities.Sensor;
import dlt.client.tangle.hornet.enums.TransactionType;
import dlt.client.tangle.hornet.model.transactions.IndexTransaction;
import dlt.client.tangle.hornet.model.transactions.TargetedTransaction;
import dlt.client.tangle.hornet.model.transactions.Transaction;
import dlt.client.tangle.hornet.model.transactions.reputation.ReputationService;
import dlt.client.tangle.hornet.model.transactions.reputation.ReputationServiceReply;
import dlt.client.tangle.hornet.model.transactions.reputation.ReputationServiceRequest;
import dlt.client.tangle.hornet.model.transactions.reputation.ReputationServiceResponse;
import dlt.client.tangle.hornet.services.ILedgerSubscriber;
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
import reputation.node.tasks.NeedServiceTask;
import reputation.node.tasks.RequestDataTask;
import reputation.node.tasks.WaitDeviceResponseTask;
import reputation.node.tasks.WaitNodeResponseTask;
import reputation.node.utils.MQTTClient;

/**
 *
 * @author Allan Capistrano
 * @version 1.2.0
 */
public class Node implements NodeTypeService, ILedgerSubscriber {

  private MQTTClient MQTTClient;
  private INodeType nodeType;
  private int checkDeviceTaskTime;
  private int requestDataTaskTime;
  private int requestServiceTaskTime;
  private int waitDeviceResponseTaskTime;
  private int waitNodeResponseTaskTime;
  private List<Device> devices;
  private LedgerConnector ledgerConnector;
  private int amountDevices = 0;
  private IDevicePropertiesManager deviceManager;
  private ListenerDevices listenerDevices;
  private TimerTask waitDeviceResponseTask;
  private TimerTask waitNodeResponseTask;
  private ReentrantLock mutex = new ReentrantLock();
  private boolean requestingService;
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

    // TODO: Colocar todos os subscribes em um método.
    this.ledgerConnector.subscribe(TransactionType.REP_SVC.toString(), this);
    // this.ledgerConnector.subscribe(TransactionType.REP_SVC_REPLY.name(), this);
    // this.ledgerConnector.subscribe(
    //     TransactionType.REP_SVC_REQ.name(),
    //     this
    //   );
    // this.ledgerConnector.subscribe(
    //     TransactionType.REP_SVC_RES.name(),
    //     this
    //   );
    // this.ledgerConnector.subscribe(
    //     TransactionType.REP_EVALUATION.name(),
    //     this
    //   );

    new Timer()
      .scheduleAtFixedRate(
        new NeedServiceTask(this),
        0,
        this.requestServiceTaskTime * 1000
      );

    this.requestingService = false;
  }

  /**
   * Executa o que foi definido na função quando o bundle for finalizado.
   */
  public void stop() {
    this.devices.forEach(d -> this.listenerDevices.unsubscribe(d.getId()));

    this.ledgerConnector.unsubscribe(TransactionType.REP_SVC.name(), this);
    // this.ledgerConnector.unsubscribe(TransactionType.REP_SVC_REPLY.name(), this);
    // this.ledgerConnector.unsubscribe(
    //     TransactionType.REP_SVC_REQ.name(),
    //     this
    //   );
    // this.ledgerConnector.unsubscribe(
    //     TransactionType.REP_SVC_RES.name(),
    //     this
    //   );
    // this.ledgerConnector.unsubscribe(
    //     TransactionType.REP_EVALUATION.name(),
    //     this
    //   );

    this.MQTTClient.disconnect();
  }

  @Override
  public void update(Object object, Object object2) {
    String sourceReceivedTransaction = ((Transaction) object).getSource();

    if (!sourceReceivedTransaction.equals(this.getNodeType().getNodeId())) {
      Transaction receivedTransaction = (Transaction) object;
      String nodeId = this.getNodeType().getNodeId();
      String nodeGroup = this.getNodeType().getNodeGroup();

      Transaction transaction = null;
      String transactionType = null;

      if (receivedTransaction.getType() == TransactionType.REP_SVC) {
        /* Só responde se tiver pelo menos um dispositivo conectado ao nó. */
        if (this.amountDevices > 0) {
          transaction =
            new ReputationServiceReply(
              nodeId,
              sourceReceivedTransaction,
              nodeGroup,
              TransactionType.REP_SVC_REPLY
            );

          transactionType = TransactionType.REP_SVC_REPLY.name();
        }
      } else {
        TargetedTransaction receivedTargetedTransaction = (TargetedTransaction) object;

        if (receivedTargetedTransaction.getTarget().equals(nodeId)) {
          switch (receivedTargetedTransaction.getType()) {
            case REP_SVC_REPLY:
              if (!this.requestingService) {
                transaction =
                  new ReputationServiceRequest(
                    nodeId,
                    sourceReceivedTransaction,
                    nodeGroup,
                    TransactionType.REP_SVC_REQ,
                    NodeServiceType.RND_DEVICE_ID.name()
                  );

                transactionType = TransactionType.REP_SVC_REQ.name();

                /* Timer para esperar a resposta do serviço requisitado. */
                this.waitNodeResponseTask =
                  new WaitNodeResponseTask(
                    sourceReceivedTransaction,
                    (this.waitNodeResponseTaskTime * 1000),
                    this
                  );

                new Timer()
                  .scheduleAtFixedRate(this.waitNodeResponseTask, 0, 1000);

                requestingService = true;
              }

              break;
            case REP_SVC_REQ:
              String deviceId = this.getRandomDeviceId();

              if (deviceId != null) {
                transaction =
                  new ReputationServiceResponse(
                    nodeId,
                    sourceReceivedTransaction,
                    nodeGroup,
                    TransactionType.REP_SVC_RES,
                    deviceId
                  );

                transactionType = TransactionType.REP_SVC_RES.name();
              }

              break;
            case REP_SVC_RES:
              /* Como recebeu o serviço, finaliza o timer, e avalia o nó 
              prestador. */
              if (this.waitNodeResponseTask != null) {
                this.waitNodeResponseTask.cancel();
              }

              try {
                this.getNodeType()
                  .getNode()
                  .evaluateServiceProvider(sourceReceivedTransaction, 1);
              } catch (InterruptedException e) {
                logger.warning("Could not add transaction on tangle network.");
              }

              requestingService = false;

              break;
            default:
              break;
          }
        }
      }

      if (transaction != null && transactionType != null) {
        try {
          /* Enviando a transação. */
          this.ledgerConnector.put(
              new IndexTransaction(transactionType, transaction)
            );
        } catch (InterruptedException ie) {
          logger.warning(
            "Could not sent the" + transactionType + " transaction."
          );
          logger.warning(ie.getStackTrace().toString());
        }
      }
    }
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
   * Retorna o ID de um dispositivo aleatório que está conectado ao nó.
   *
   * @return String
   */
  private String getRandomDeviceId() {
    String deviceId = null;

    if (this.amountDevices > 0) {
      try {
        this.mutex.lock();

        int randomIndex = new Random().nextInt(this.amountDevices);

        deviceId = this.devices.get(randomIndex).getId();
      } finally {
        this.mutex.unlock();
      }
    }

    return deviceId;
  }

  /**
   * Envia uma transação indicando que o nó precisa do serviço de um outro nó.
   *
   * @throws InterruptedException
   */
  public void requestServiceFromNode() throws InterruptedException {
    Transaction transaction = new ReputationService(
      this.getNodeType().getNodeId(),
      this.getNodeType().getNodeGroup(),
      TransactionType.REP_SVC
    );

    this.ledgerConnector.put(
        new IndexTransaction(TransactionType.REP_SVC.name(), transaction)
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

  public TimerTask getWaitNodeResponseTask() {
    return waitNodeResponseTask;
  }

  public void setWaitNodeResponseTask(TimerTask waitNodeResponseTask) {
    this.waitNodeResponseTask = waitNodeResponseTask;
  }
}
