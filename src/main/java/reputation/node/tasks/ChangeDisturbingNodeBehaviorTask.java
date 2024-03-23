package reputation.node.tasks;

import dlt.client.tangle.hornet.model.transactions.Transaction;
import java.util.List;
import java.util.TimerTask;
import java.util.logging.Logger;
import reputation.node.models.Node;
import reputation.node.reputation.IReputation;

/**
 * Task para alterar o comportamento de um nó do tipo Perturbador.
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public class ChangeDisturbingNodeBehaviorTask extends TimerTask {

  private static final double REPUTATION_THRESHOLD = 0.9;

  private final Node node;
  private final IReputation reputation;
  private boolean changeBehaviorFlag = true;

  private static final Logger logger = Logger.getLogger(
    ChangeDisturbingNodeBehaviorTask.class.getName()
  );

  /**
   * Método construtor.
   * @param node Node - O nó que verificará a própria reputação.
   * @param reputation IReputation - Objeto para calcular a reputação.
   */
  public ChangeDisturbingNodeBehaviorTask(Node node, IReputation reputation) {
    this.node = node;
    this.reputation = reputation;
  }

  @Override
  public void run() {
    List<Transaction> evaluationTransactions =
      this.node.getLedgerConnector()
        .getLedgerReader()
        .getTransactionsByIndex(this.node.getNodeType().getNodeId(), false);

    double reputationValue =
      this.reputation.calculate(
          evaluationTransactions,
          this.node.isUseLatestCredibility(),
          this.node.isUseCredibility()
        );

    logger.info(
      String.format("Disturbing node reputation: %f", reputationValue)
    );

    if (this.changeBehaviorFlag && reputationValue > REPUTATION_THRESHOLD) {
      this.node.getNodeType().getNode().defineConduct();
      this.changeBehaviorFlag = false;
    }
  }
}
