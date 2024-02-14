package reputation.node.reputation;

import dlt.client.tangle.hornet.model.transactions.Transaction;
import dlt.client.tangle.hornet.model.transactions.reputation.Evaluation;
import java.util.List;

/**
 * Responsável por calcular a reputação de uma coisa.
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public class Reputation implements IReputation {

  /**
   * Calcula a reputação de uma coisa.
   *
   * @param evaluationTransactions List<Transaction> - Lista com as transações
   * de avaliação da coisa.
   * @param useLatestCredibility boolean - Indica se é para usar ou não a
   * credibilidade mais recente para o cálculo da reputação.
   * @param useCredibility boolean - Indica se é para usar ou não a
   * credibilidade no cálculo da reputação.
   * @return Double
   */
  @Override
  public Double calculate(
    List<Transaction> evaluationTransactions,
    boolean useLatestCredibility,
    boolean useCredibility
  ) {
    return evaluationTransactions
      .stream()
      .mapToDouble(et -> ((Evaluation) et).getServiceEvaluation())
      .average()
      .orElse(0.0);
  }
}
