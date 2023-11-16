package reputation.node.tasks;

import dlt.client.tangle.hornet.enums.TransactionType;
import dlt.client.tangle.hornet.model.transactions.IndexTransaction;
import dlt.client.tangle.hornet.model.transactions.Transaction;
import dlt.client.tangle.hornet.model.transactions.reputation.HasReputationService;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;
import reputation.node.enums.NodeServiceType;
import reputation.node.models.Node;

/**
 * Classe responsável pela tarefa de obter os nós que prestam um determinado
 * serviço
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public class CheckNodesServicesTask extends TimerTask {

  private final Node node;
  private static final Logger logger = Logger.getLogger(
    CheckDevicesTask.class.getName()
  );

  /**
   * Método construtor.
   *
   * @param node NodeType - Nó que verificará os serviços dos outros nós.
   */
  public CheckNodesServicesTask(Node node) {
    this.node = node;
  }

  @Override
  public void run() {
    if (!this.node.isRequestingNodeServices()) {
      /**
       * Serviço sendo escolhido de maneira aleatório.
       */
      int randomIndex = new Random().nextInt(NodeServiceType.values().length);

      NodeServiceType nodeServiceType = NodeServiceType.values()[randomIndex];

      /**
       * Salvando qual foi o último tipo de serviço requisitado.
       */
      this.node.setLastNodeServiceTransactionType(
          nodeServiceType.getDescription()
        );

      /**
       * Permitindo o nó a receber as respostas dos outros nós.
       */
      this.node.setCanReceiveNodesResponse(true);

      logger.info(
        "Checking nodes with " + nodeServiceType.getDescription() + " service."
      );

      Transaction transaction = new HasReputationService(
        this.node.getNodeType().getNodeId(),
        this.node.getNodeType().getNodeGroup(),
        nodeServiceType.getDescription(),
        TransactionType.REP_HAS_SVC
      );

      String transactionTypeString = TransactionType.REP_HAS_SVC.name();

      this.node.setRequestingNodeServices(true);

      try {
        this.node.getLedgerConnector()
          .getLedgerWriter()
          .put(new IndexTransaction(transactionTypeString, transaction));

        /**
         * Timer para aguardar as aceitar as respostas dos nós.
         */
        new Timer()
          .scheduleAtFixedRate(
            new WaitNodesResponsesTask(
              (this.node.getWaitNodesResponsesTaskTime() * 1000),
              this.node
            ),
            0,
            1000
          );
      } catch (InterruptedException ie) {
        logger.warning(
          "Error trying to create a " + transactionTypeString + " transaction."
        );
        logger.warning(ie.getStackTrace().toString());
      }
    }
  }
}
