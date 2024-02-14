package reputation.node.tasks;

import dlt.client.tangle.hornet.model.transactions.Transaction;
import java.util.List;
import java.util.TimerTask;
import reputation.node.models.Node;
import reputation.node.reputation.IReputation;

/**
 * Task para verificar de tempos em tempos a reputação do próprio nó.
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public class ChangeDisturbingNodeBehaviorTask extends TimerTask { // TODO: Renomear classe para ChangeDisturbingNodeBehaviorTask

  private static final double REPUTATION_THRESHOLD = 0.9;

  private final Node node;
  private final IReputation reputation;
  private boolean changeBehaviorFlag = true;

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
    /* Somente se um nó do tipo perturbador. */
    if (
      this.node.getNodeType()
        .getNode()
        .getConductType()
        .toString()
        .equals("DISTURBING")
    ) {
      List<Transaction> evaluationTransactions =
        this.node.getLedgerConnector()
          .getLedgerReader()
          .getTransactionsByIndex(this.node.getNodeType().getNodeId(), false);

      double reputationValue =
        this.reputation.calculate(
            evaluationTransactions,
            this.node.isUseLatestCredibility()
          );

      if (this.changeBehaviorFlag && reputationValue > REPUTATION_THRESHOLD) {
        this.node.getNodeType().getNode().defineConduct();
        this.changeBehaviorFlag = false;
      }

      /**
       * Quando o nó alcançar uma reputação negativa, ele está habilitado a 
       * poder alterar novamente seu comportamento.
       */
      if (reputationValue <= 0) {
        this.changeBehaviorFlag = true;
      }
    }
  }
}
