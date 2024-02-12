package reputation.node.reputation;

import dlt.client.tangle.hornet.model.transactions.Transaction;
import java.util.List;

/**
 *
 * @author Allan Capistrano
 * @version 1.1.0
 */
public interface IReputation {
  /**
   * Calcula a reputação de uma coisa.
   *
   * @param evaluationTransactions List<Transaction> - Lista com as transações
   * de avaliação da coisa.
   * @param useLatestCredibility boolean - Indica se é para usar ou não a
   * credibilidade mais recente para o cálculo da reputação
   * @return Double
   */
  Double calculate(
    List<Transaction> evaluationTransactions,
    boolean useLatestCredibility
  );
}
